"""Alpha 9: bypass Android's Opus MediaCodec path for video-only OCR sessions.

The GlossaWhaleOS Remote Play stream supplies frames to OCR while the TV plays
sound through its direct HDMI source. It does not require remote audio decoding.

Chiaki's audio jitter buffer only frees delivered packets after invoking a
non-null frame callback. A NULL sink.frame_cb would leak one allocation per
audio frame; therefore use a deliberate no-op receiver instead.
"""
from pathlib import Path

REMOTE = Path("remote/chiaki-ng-androidtv")


def replace_once(path: str, old: str, new: str) -> None:
    file = REMOTE / path
    source = file.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact source block, found {count}")
    file.write_text(source.replace(old, new, 1), encoding="utf-8")


# Declare a receiving callback that intentionally discards encoded Opus.
# It must be non-NULL: the pinned Chiaki core's audioreceiver.c returns early
# (without freeing deliver_buf) if the frame callback is NULL.
replace_once(
    "native/jni/chiaki-jni.c",
    """static void android_chiaki_event_cb(ChiakiEvent *event, void *user)
{""",
    """// Video-only OCR build: release incoming audio without starting an Opus codec.
static void glossa_discard_audio_frame(uint8_t *buf, size_t buf_size, void *user)
{
    (void)buf;
    (void)buf_size;
    (void)user;
}

static void android_chiaki_event_cb(ChiakiEvent *event, void *user)
{"""
)

# Keep Chiaki's receiver installed for protocol compatibility, but route its
# packets to the no-op callback instead of the Android MediaCodec decoder.
# The native receiver still frees its packet buffers normally.
replace_once(
    "native/jni/chiaki-jni.c",
    """	ChiakiAudioSink audio_sink;
	android_chiaki_audio_decoder_get_sink(&session->audio_decoder, &audio_sink);
	chiaki_session_set_audio_sink(&session->session, &audio_sink);""",
    """	ChiakiAudioSink audio_sink = { 0 };
	audio_sink.frame_cb = glossa_discard_audio_frame;
	chiaki_session_set_audio_sink(&session->session, &audio_sink);
	CHIAKI_LOGI(log, "GlossaWhaleOS: video-only mode; Opus MediaCodec bypassed");"""
)

# Fail the build if a future upstream code change silently reconnects the
# faulty codec sink; this matters more than a successful compilation alone.
jni = (REMOTE / "native/jni/chiaki-jni.c").read_text(encoding="utf-8")
if "android_chiaki_audio_decoder_get_sink(&session->audio_decoder, &audio_sink)" in jni:
    raise SystemExit("Alpha 9 audio bypass missing: codec sink still attached")
if jni.count("audio_sink.frame_cb = glossa_discard_audio_frame;") != 1:
    raise SystemExit("Alpha 9 audio bypass missing: discard callback not attached")
if jni.count("chiaki_session_set_audio_sink(&session->session, &audio_sink);") != 1:
    raise SystemExit("Unexpected multiple Chiaki audio sink registrations")

# Alpha 8 already changed Alpha 7's version to 49. Increment the version
# for Alpha 9; Android also requires the *same signing certificate* for an
# in-place update, which hosted debug builds may not preserve.
app_gradle = REMOTE / "app/build.gradle"
gradle_source = app_gradle.read_text(encoding="utf-8")
old_version = 'versionCode 49\n\t\tversionName "1.28.1-glossawhale-alpha8"'
new_version = 'versionCode 50\n\t\tversionName "1.28.1-glossawhale-alpha9-video-only"'
if gradle_source.count(old_version) != 1:
    raise SystemExit("Could not uniquely locate the Alpha 8 app version")
app_gradle.write_text(gradle_source.replace(old_version, new_version, 1), encoding="utf-8")

print("Alpha 9 video-only audio bypass applied; codec sink detached, incoming audio discarded safely, versionCode 50.")
