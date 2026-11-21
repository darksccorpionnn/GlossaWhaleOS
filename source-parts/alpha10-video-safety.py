"""Alpha 10: serialize native video MediaCodec calls and surface useful crash diagnostics.

This is a targeted stability experiment for the Scudo heap corruption on the
user's Android/Whale TV. The native tombstone's crashing frames are still needed
to prove root cause; this patch does not claim that all memory corruption is fixed.
"""
from pathlib import Path

REMOTE = Path("remote/chiaki-ng-androidtv")
decoder_path = REMOTE / "native/jni/video-decoder.c"
source = decoder_path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"Alpha10 {label}: expected 1 match; got {n}")
    return text.replace(old, new, 1)


source = replace_once(
    source,
    "#define INPUT_BUFFER_TIMEOUT_MS 5",
    "#define INPUT_BUFFER_TIMEOUT_MS 5\n#define OUTPUT_BUFFER_TIMEOUT_US 10000",
    "finite output timeout",
)

# A real device may temporarily expose an empty/null input buffer. Never memcpy
# to an unchecked pointer and never spin forever on an empty codec buffer.
source = replace_once(
    source,
    """\t\tsize_t codec_buf_size;
\t\tuint8_t *codec_buf = AMediaCodec_getInputBuffer(decoder->codec, (size_t)codec_buf_index, &codec_buf_size);
\t\tsize_t codec_sample_size = buf_size;""",
    """\t\tsize_t codec_buf_size = 0;
\t\tuint8_t *codec_buf = AMediaCodec_getInputBuffer(decoder->codec, (size_t)codec_buf_index, &codec_buf_size);
\t\tif(!codec_buf || codec_buf_size == 0)
\t\t{
\t\t\tCHIAKI_LOGE(decoder->log, "Video Decoder: codec supplied invalid input buffer");
\t\t\tAMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, 0, decoder->timestamp_cur++, 0);
\t\t\tr = false;
\t\t\tgoto beach;
\t\t}
\t\tsize_t codec_sample_size = buf_size;""",
    "video input buffer validation",
)

# The original output worker calls AMediaCodec_dequeueOutputBuffer(codec, -1)
# concurrently with input, stop, delete and setOutputSurface on other threads.
# MediaCodec calls now use the *same* mutex as input + lifecycle calls. Release
# lock after each 10ms output poll; stop/join can now complete predictably.
start = "static void *android_chiaki_video_decoder_output_thread_func(void *user)\n{"
end = "void android_chiaki_video_decoder_get_stats("
if source.count(start) != 1 or source.count(end) != 1:
    raise SystemExit("Alpha10: unexpected video worker layout")
before, rest = source.split(start, 1)
unused_worker, after = rest.split(end, 1)
replacement = r'''static void *android_chiaki_video_decoder_output_thread_func(void *user)
{
    AndroidChiakiVideoDecoder *decoder = user;
    int render_failures = 0;
    int dequeue_errors = 0;

    while(true)
    {
        AMediaCodecBufferInfo info = { 0 };
        ssize_t status;
        bool end_of_stream = false;
        bool should_quit = false;

        chiaki_mutex_lock(&decoder->codec_mutex);
        if(__atomic_load_n(&decoder->shutdown_output, __ATOMIC_ACQUIRE) || !decoder->codec)
        {
            chiaki_mutex_unlock(&decoder->codec_mutex);
            break;
        }

        AMediaCodec *codec = decoder->codec;
        status = AMediaCodec_dequeueOutputBuffer(codec, &info, OUTPUT_BUFFER_TIMEOUT_US);
        if(status >= 0)
        {
            dequeue_errors = 0;
            if(info.size != 0)
            {
                __atomic_store_n(&decoder->frames_since_output, 0, __ATOMIC_RELAXED);
                __atomic_add_fetch(&decoder->frames_output, 1, __ATOMIC_RELAXED);
            }

            bool render = info.size != 0 &&
                __atomic_load_n(&decoder->surface_valid, __ATOMIC_ACQUIRE);
            media_status_t rr = AMediaCodec_releaseOutputBuffer(codec, (size_t)status, render);
            if(render && rr != AMEDIA_OK)
            {
                if(++render_failures > 30)
                {
                    CHIAKI_LOGE(decoder->log,
                        "Video Decoder: repeated render errors (%d), recreation requested",
                        (int)rr);
                    __atomic_store_n(&decoder->needs_recreate, true, __ATOMIC_RELEASE);
                    should_quit = true;
                }
            }
            else if(render)
                render_failures = 0;

            end_of_stream = (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
        }
        else if(status == AMEDIACODEC_INFO_TRY_AGAIN_LATER ||
                status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED ||
                status == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED)
        {
            // A normal finite poll timeout is not a decoder failure.
            dequeue_errors = 0;
        }
        else if(++dequeue_errors > 500)
        {
            CHIAKI_LOGE(decoder->log,
                "Video Decoder: consecutive output errors (%zd), recreating codec", status);
            __atomic_store_n(&decoder->needs_recreate, true, __ATOMIC_RELEASE);
            should_quit = true;
        }
        chiaki_mutex_unlock(&decoder->codec_mutex);

        if(end_of_stream)
        {
            CHIAKI_LOGI(decoder->log, "Video Decoder output reached EOS");
            break;
        }
        if(should_quit)
            break;
    }

    CHIAKI_LOGI(decoder->log, "Video Decoder output worker exited");
    return NULL;
}
'''
source = before + replacement + end + after

# Do not resurrect the old indefinite blocking worker by accident.
if "AMediaCodec_dequeueOutputBuffer(decoder->codec, &info, -1)" in source:
    raise SystemExit("Alpha10 still has an indefinite output dequeue")
if source.count("AMediaCodec_dequeueOutputBuffer(codec, &info, OUTPUT_BUFFER_TIMEOUT_US)") != 1:
    raise SystemExit("Alpha10 serialized worker missing")
decoder_path.write_text(source, encoding="utf-8")

activity = REMOTE / "app/src/main/java/com/chiaking/tv/MainActivity.kt"
kotlin = activity.read_text(encoding="utf-8")
kotlin = replace_once(kotlin, "import androidx.compose.foundation.layout.Box",
    "import androidx.compose.foundation.layout.Box\n"
    "import androidx.compose.foundation.layout.heightIn\n"
    "import androidx.compose.foundation.verticalScroll\n"
    "import androidx.compose.foundation.rememberScrollState\n"
    "import androidx.compose.ui.unit.dp", "crash dialog imports")

# In protobuf3, an ARM32 architecture value of zero is omitted from the wire
# format. The original decoder showed 'unknown(-1)' in this exact case.
kotlin = replace_once(
    kotlin,
    "val arch = first(1)?.varint ?: -1L",
    "val arch = first(1)?.varint ?: 0L",
    "proto3 ARM32 architecture",
)

# Existing Alpha 9 .tombstone.pb files remain on the device across an update.
# Import the newest one once, even if Alpha 9 already marked its exit timestamp
# as seen. This lets the user see crash frames without triggering another crash.
needle = "        collectPreviousExitDiagnostic()"
insertion = """        collectPreviousExitDiagnostic()
        if(previousExitDiagnostic.isNullOrBlank()) {
            try {
                val store = getSharedPreferences("glossa_exit_diagnostics", MODE_PRIVATE)
                val latest = java.io.File(filesDir, "session_logs").listFiles()
                    ?.filter { it.name.endsWith("_native-crash.tombstone.pb") && it.isFile }
                    ?.maxByOrNull { it.lastModified() }
                if(latest != null &&
                    latest.lastModified() > store.getLong("last_alpha10_archived_tombstone", 0L)) {
                    val raw = latest.readBytes()
                    previousExitDiagnostic =
                        "Previous native crash saved on device: " + latest.name + "\\\\n" +
                        decodeGlossaTombstone(raw)
                    store.edit().putLong("last_alpha10_archived_tombstone", latest.lastModified()).apply()
                }
            } catch(error: Exception) {
                android.util.Log.w("GlossaWhaleOS", "Could not reopen native crash file", error)
            }
        }"""
kotlin = replace_once(kotlin, needle, insertion, "import previous tombstone")

# Previously maxLines=28 without scrolling hid the crashing backtrace entirely.
# Give the report a scrollable area with its crash frames visible.
old_view = """                                    androidx.compose.foundation.layout.Column {
                                        androidx.compose.material3.Text(
                                            previousCrashText.take(5000),
                                            maxLines = 28
                                        )
                                    }"""
new_view = """                                    androidx.compose.foundation.layout.Column(
                                        modifier = androidx.compose.ui.Modifier
                                            .heightIn(max = 400.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        androidx.compose.material3.Text(previousCrashText.take(28000))
                                    }"""
kotlin = replace_once(kotlin, old_view, new_view, "scrollable crash report")
activity.write_text(kotlin, encoding="utf-8")

gradle_path = REMOTE / "app/build.gradle"
gradle = gradle_path.read_text(encoding="utf-8")
gradle = replace_once(
    gradle,
    'versionCode 50\n\t\tversionName "1.28.1-glossawhale-alpha9-video-only"',
    'versionCode 51\n\t\tversionName "1.28.1-glossawhale-alpha10-video-safety"',
    "Alpha 10 version",
)
gradle_path.write_text(gradle, encoding="utf-8")

print("Alpha 10: video codec calls serialized; finite output polling; old native tombstone report scrollable; versionCode 51")
