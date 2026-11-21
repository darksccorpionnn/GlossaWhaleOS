from pathlib import Path

REMOTE = Path("remote/chiaki-ng-androidtv/native/jni")

def replace_once(path: str, old: str, new: str) -> None:
    file = REMOTE / path
    source = file.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact source block, found {count}")
    file.write_text(source.replace(old, new, 1), encoding="utf-8")

# Keep the decoder's lifecycle state under the same mutex as every MediaCodec call.
replace_once(
    "audio-decoder.h",
    """\tstruct AMediaCodec *codec;
\tuint64_t timestamp_cur;
\tChiakiThread output_thread;""",
    """\tstruct AMediaCodec *codec;
\tuint64_t timestamp_cur;
\tChiakiThread output_thread;
\tbool codec_started;
\tbool output_thread_started;
\tbool stopping;"""
)

# Replace the upstream input/output worker implementation. The old output thread
# called MediaCodec concurrently with the input thread and used an infinite wait.
replace_once(
    "audio-decoder.c",
    """#define INPUT_BUFFER_TIMEOUT_MS 10

static void *android_chiaki_audio_decoder_output_thread_func(void *user);
static void android_chiaki_audio_decoder_header(ChiakiAudioHeader *header, void *user);
static void android_chiaki_audio_decoder_frame(uint8_t *buf, size_t buf_size, void *user);""",
    """#define INPUT_BUFFER_TIMEOUT_MS 10
#define OUTPUT_BUFFER_TIMEOUT_US 10000

static void *android_chiaki_audio_decoder_output_thread_func(void *user);
static void android_chiaki_audio_decoder_header(ChiakiAudioHeader *header, void *user);
static void android_chiaki_audio_decoder_frame(uint8_t *buf, size_t buf_size, void *user);
static bool android_chiaki_audio_decoder_queue_frame(AndroidChiakiAudioDecoder *decoder, uint8_t *buf, size_t buf_size);"""
)

replace_once(
    "audio-decoder.c",
    """\tdecoder->codec = NULL;
\tdecoder->timestamp_cur = 0;

\tdecoder->cb_user = NULL;""",
    """\tdecoder->codec = NULL;
\tdecoder->timestamp_cur = 0;
\tdecoder->codec_started = false;
\tdecoder->output_thread_started = false;
\tdecoder->stopping = false;

\tdecoder->cb_user = NULL;"""
)

replace_once(
    "audio-decoder.c",
    """\treturn chiaki_mutex_init(&decoder->codec_mutex, true);""",
    """\t// The decoder no longer relies on recursive locking: every codec call is
\t// serialized explicitly, including calls from the output worker.
\treturn chiaki_mutex_init(&decoder->codec_mutex, false);"""
)

replace_once(
    "audio-decoder.c",
    """void android_chiaki_audio_decoder_shutdown_codec(AndroidChiakiAudioDecoder *decoder)
{
\tchiaki_mutex_lock(&decoder->codec_mutex);
\tssize_t codec_buf_index = AMediaCodec_dequeueInputBuffer(decoder->codec, -1);
\tif(codec_buf_index >= 0)
\t{
\t\tCHIAKI_LOGI(decoder->log, "Audio Decoder sending EOS buffer");
\t\tAMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, 0, decoder->timestamp_cur++, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
\t}
\telse
\t\tCHIAKI_LOGE(decoder->log, "Failed to get input buffer for shutting down Audio Decoder!");
\tchiaki_mutex_unlock(&decoder->codec_mutex);
\tchiaki_thread_join(&decoder->output_thread, NULL);
\tAMediaCodec_delete(decoder->codec);
\tdecoder->codec = NULL;
}""",
    """void android_chiaki_audio_decoder_shutdown_codec(AndroidChiakiAudioDecoder *decoder)
{
\tstruct AMediaCodec *codec = NULL;
\tbool join_output_thread = false;

\tchiaki_mutex_lock(&decoder->codec_mutex);
\tcodec = decoder->codec;
\tif(!codec)
\t{
\t\tdecoder->codec_started = false;
\t\tdecoder->output_thread_started = false;
\t\tdecoder->stopping = false;
\t\tchiaki_mutex_unlock(&decoder->codec_mutex);
\t\treturn;
\t}

\t// Stop accepting input first. The output worker polls with a finite timeout
\t// and checks this flag under the same mutex, so it can exit without a
\t// blocking EOS dequeue or a concurrent AMediaCodec call.
\tdecoder->stopping = true;
\tjoin_output_thread = decoder->output_thread_started;
\tchiaki_mutex_unlock(&decoder->codec_mutex);

\tif(join_output_thread)
\t\tchiaki_thread_join(&decoder->output_thread, NULL);

\tchiaki_mutex_lock(&decoder->codec_mutex);
\tif(decoder->codec == codec)
\t{
\t\tif(decoder->codec_started)
\t\t{
\t\t\tmedia_status_t status = AMediaCodec_stop(codec);
\t\t\tif(status != AMEDIA_OK)
\t\t\t\tCHIAKI_LOGW(decoder->log, "AMediaCodec_stop returned %d", (int)status);
\t\t}
\t\tAMediaCodec_delete(codec);
\t\tdecoder->codec = NULL;
\t}
\tdecoder->codec_started = false;
\tdecoder->output_thread_started = false;
\tdecoder->stopping = false;
\tchiaki_mutex_unlock(&decoder->codec_mutex);
}"""
)

replace_once(
    "audio-decoder.c",
    """void android_chiaki_audio_decoder_fini(AndroidChiakiAudioDecoder *decoder)
{
\tif(decoder->codec)
\t\tandroid_chiaki_audio_decoder_shutdown_codec(decoder);
\tchiaki_mutex_fini(&decoder->codec_mutex);
}""",
    """void android_chiaki_audio_decoder_fini(AndroidChiakiAudioDecoder *decoder)
{
\tandroid_chiaki_audio_decoder_shutdown_codec(decoder);
\tchiaki_mutex_fini(&decoder->codec_mutex);
}"""
)

replace_once(
    "audio-decoder.c",
    """static void *android_chiaki_audio_decoder_output_thread_func(void *user)
{
\tAndroidChiakiAudioDecoder *decoder = user;

\twhile(1)
\t{
\t\tAMediaCodecBufferInfo info;
\t\tssize_t codec_buf_index = AMediaCodec_dequeueOutputBuffer(decoder->codec, &info, -1);
\t\tif(codec_buf_index >= 0)
\t\t{
\t\t\tif(decoder->settings_cb)
\t\t\t{
\t\t\t\tsize_t codec_buf_size;
\t\t\t\tuint8_t *codec_buf = AMediaCodec_getOutputBuffer(decoder->codec, (size_t)codec_buf_index, &codec_buf_size);
\t\t\t\tsize_t samples_count = info.size / sizeof(int16_t);
\t\t\t\t//CHIAKI_LOGD(decoder->log, "Got %llu samples => %f ms of audio", (unsigned long long)samples_count, 1000.0f * (float)(samples_count / 2) / (float)decoder->audio_header.rate);
\t\t\t\tdecoder->frame_cb((int16_t *)codec_buf, samples_count, decoder->cb_user);
\t\t\t}
\t\t\tAMediaCodec_releaseOutputBuffer(decoder->codec, (size_t)codec_buf_index, false);
\t\t\tif(info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM)
\t\t\t{
\t\t\t\tCHIAKI_LOGI(decoder->log, "AMediaCodec for Audio Decoder reported EOS");
\t\t\t\tbreak;
\t\t\t}
\t\t}
\t}

\tCHIAKI_LOGI(decoder->log, "Audio Decoder Output Thread exiting");

\treturn NULL;
}""",
    """static void *android_chiaki_audio_decoder_output_thread_func(void *user)
{
\tAndroidChiakiAudioDecoder *decoder = user;

\twhile(true)
\t{
\t\tAMediaCodecBufferInfo info = { 0 };
\t\tssize_t codec_buf_index = -1;
\t\tbool end_of_stream = false;

\t\t// MediaCodec implementations on several Android 12 devices are not
\t\t// robust when input and output operations are issued concurrently.
\t\t// Serialize every NDK codec call and poll instead of waiting forever.
\t\tchiaki_mutex_lock(&decoder->codec_mutex);
\t\tif(decoder->stopping || !decoder->codec || !decoder->codec_started)
\t\t{
\t\t\tchiaki_mutex_unlock(&decoder->codec_mutex);
\t\t\tbreak;
\t\t}

\t\tstruct AMediaCodec *codec = decoder->codec;
\t\tcodec_buf_index = AMediaCodec_dequeueOutputBuffer(codec, &info, OUTPUT_BUFFER_TIMEOUT_US);
\t\tif(codec_buf_index >= 0)
\t\t{
\t\t\tsize_t codec_buf_size = 0;
\t\t\tuint8_t *codec_buf = AMediaCodec_getOutputBuffer(codec, (size_t)codec_buf_index, &codec_buf_size);

\t\t\tif(codec_buf && info.offset >= 0 && info.size > 0 &&
\t\t\t\t(size_t)info.offset <= codec_buf_size &&
\t\t\t\t(size_t)info.size <= codec_buf_size - (size_t)info.offset)
\t\t\t{
\t\t\t\tif(decoder->frame_cb)
\t\t\t\t{
\t\t\t\t\tsize_t samples_count = (size_t)info.size / sizeof(int16_t);
\t\t\t\t\tdecoder->frame_cb((int16_t *)(codec_buf + info.offset), samples_count, decoder->cb_user);
\t\t\t\t}
\t\t\t}
\t\t\telse if(info.size > 0)
\t\t\t{
\t\t\t\tCHIAKI_LOGW(decoder->log, "Ignoring invalid AMediaCodec output buffer (offset=%d size=%d capacity=%llu)",
\t\t\t\t\tinfo.offset, info.size, (unsigned long long)codec_buf_size);
\t\t\t}

\t\t\tAMediaCodec_releaseOutputBuffer(codec, (size_t)codec_buf_index, false);
\t\t\tend_of_stream = (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) != 0;
\t\t}
\t\telse if(codec_buf_index != AMEDIACODEC_INFO_TRY_AGAIN_LATER &&
\t\t\tcodec_buf_index != AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED &&
\t\t\tcodec_buf_index != AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED)
\t\t{
\t\t\tCHIAKI_LOGW(decoder->log, "AMediaCodec_dequeueOutputBuffer returned %zd", codec_buf_index);
\t\t}

\t\tchiaki_mutex_unlock(&decoder->codec_mutex);
\t\tif(end_of_stream)
\t\t{
\t\t\tCHIAKI_LOGI(decoder->log, "AMediaCodec for Audio Decoder reported EOS");
\t\t\tbreak;
\t\t}
\t}

\tCHIAKI_LOGI(decoder->log, "Audio Decoder Output Thread exiting");
\treturn NULL;
}"""
)

replace_once(
    "audio-decoder.c",
    """static void android_chiaki_audio_decoder_header(ChiakiAudioHeader *header, void *user)
{
\tAndroidChiakiAudioDecoder *decoder = user;
\tchiaki_mutex_lock(&decoder->codec_mutex);
\tmemcpy(&decoder->audio_header, header, sizeof(decoder->audio_header));

\tif(decoder->codec)
\t{
\t\tCHIAKI_LOGI(decoder->log, "Audio decoder already initialized, shutting down the old one");
\t\tandroid_chiaki_audio_decoder_shutdown_codec(decoder);
\t}

\tconst char *mime = "audio/opus";
\tdecoder->codec = AMediaCodec_createDecoderByType(mime);
\tif(!decoder->codec)
\t{
\t\tCHIAKI_LOGE(decoder->log, "Failed to create AMediaCodec for mime type %s", mime);
\t\tgoto beach;
\t}

\tAMediaFormat *format = AMediaFormat_new();
\tAMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
\tAMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, header->channels);
\tAMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, header->rate);
\t// AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_PCM_ENCODING)

\tAMediaCodec_configure(decoder->codec, format, NULL, NULL, 0); // TODO: check result
\tAMediaCodec_start(decoder->codec); // TODO: check result

\tAMediaFormat_delete(format);

\tChiakiErrorCode err = chiaki_thread_create(&decoder->output_thread, android_chiaki_audio_decoder_output_thread_func, decoder);
\tif(err != CHIAKI_ERR_SUCCESS)
\t{
\t\tCHIAKI_LOGE(decoder->log, "Failed to create output thread for AMediaCodec");
\t\tAMediaCodec_delete(decoder->codec);
\t\tdecoder->codec = NULL;
\t}

\tuint8_t opus_id_head[0x13];
\tmemcpy(opus_id_head, "OpusHead", 8);
\topus_id_head[0x8] = 1; // version
\topus_id_head[0x9] = header->channels;
\tuint16_t pre_skip = 3840;
\topus_id_head[0xa] = (uint8_t)(pre_skip & 0xff);
\topus_id_head[0xb] = (uint8_t)(pre_skip >> 8);
\topus_id_head[0xc] = (uint8_t)(header->rate & 0xff);
\topus_id_head[0xd] = (uint8_t)((header->rate >> 0x8) & 0xff);
\topus_id_head[0xe] = (uint8_t)((header->rate >> 0x10) & 0xff);
\topus_id_head[0xf] = (uint8_t)(header->rate >> 0x18);
\tuint16_t output_gain = 0;
\topus_id_head[0x10] = (uint8_t)(output_gain & 0xff);
\topus_id_head[0x11] = (uint8_t)(output_gain >> 8);
\topus_id_head[0x12] = 0; // channel map
\t//AMediaFormat_setBuffer(format, AMEDIAFORMAT_KEY_CSD_0, opus_id_head, sizeof(opus_id_head));
\tandroid_chiaki_audio_decoder_frame(opus_id_head, sizeof(opus_id_head), decoder);

\tuint64_t pre_skip_ns = 0;
\tuint8_t csd1[8] = { (uint8_t)(pre_skip_ns & 0xff), (uint8_t)((pre_skip_ns >> 0x8) & 0xff), (uint8_t)((pre_skip_ns >> 0x10) & 0xff), (uint8_t)((pre_skip_ns >> 0x18) & 0xff),
\t\t\t\t\t\t(uint8_t)((pre_skip_ns >> 0x20) & 0xff), (uint8_t)((pre_skip_ns >> 0x28) & 0xff), (uint8_t)((pre_skip_ns >> 0x30) & 0xff), (uint8_t)(pre_skip_ns >> 0x38)};
\tandroid_chiaki_audio_decoder_frame(csd1, sizeof(csd1), decoder);

\tuint64_t pre_roll_ns = 0;
\tuint8_t csd2[8] = { (uint8_t)(pre_roll_ns & 0xff), (uint8_t)((pre_roll_ns >> 0x8) & 0xff), (uint8_t)((pre_roll_ns >> 0x10) & 0xff), (uint8_t)((pre_roll_ns >> 0x18) & 0xff),
\t\t\t\t\t\t(uint8_t)((pre_roll_ns >> 0x20) & 0xff), (uint8_t)((pre_roll_ns >> 0x28) & 0xff), (uint8_t)((pre_roll_ns >> 0x30) & 0xff), (uint8_t)(pre_roll_ns >> 0x38)};
\tandroid_chiaki_audio_decoder_frame(csd2, sizeof(csd2), decoder);

\tif(decoder->settings_cb)
\t\tdecoder->settings_cb(header->channels, header->rate, decoder->cb_user);

beach:
\tchiaki_mutex_unlock(&decoder->codec_mutex);
}""",
    """static void android_chiaki_audio_decoder_header(ChiakiAudioHeader *header, void *user)
{
\tAndroidChiakiAudioDecoder *decoder = user;
\tconst char *mime = "audio/opus";

\t// Tear down any previous stream without holding codec_mutex across join.
\tandroid_chiaki_audio_decoder_shutdown_codec(decoder);

\tchiaki_mutex_lock(&decoder->codec_mutex);
\tmemcpy(&decoder->audio_header, header, sizeof(decoder->audio_header));
\tdecoder->timestamp_cur = 0;
\tdecoder->stopping = false;

\tdecoder->codec = AMediaCodec_createDecoderByType(mime);
\tif(!decoder->codec)
\t{
\t\tCHIAKI_LOGE(decoder->log, "Failed to create AMediaCodec for mime type %s", mime);
\t\tchiaki_mutex_unlock(&decoder->codec_mutex);
\t\treturn;
\t}

\tAMediaFormat *format = AMediaFormat_new();
\tif(!format)
\t{
\t\tCHIAKI_LOGE(decoder->log, "Failed to allocate AMediaFormat for audio decoder");
\t\tAMediaCodec_delete(decoder->codec);
\t\tdecoder->codec = NULL;
\t\tchiaki_mutex_unlock(&decoder->codec_mutex);
\t\treturn;
\t}

\tAMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
\tAMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, header->channels);
\tAMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, header->rate);

\tmedia_status_t status = AMediaCodec_configure(decoder->codec, format, NULL, NULL, 0);
\tAMediaFormat_delete(format);
\tif(status != AMEDIA_OK)
\t{
\t\tCHIAKI_LOGE(decoder->log, "AMediaCodec_configure failed: %d", (int)status);
\t\tAMediaCodec_delete(decoder->codec);
\t\tdecoder->codec = NULL;
\t\tchiaki_mutex_unlock(&decoder->codec_mutex);
\t\treturn;
\t}

\tstatus = AMediaCodec_start(decoder->codec);
\tif(status != AMEDIA_OK)
\t{
\t\tCHIAKI_LOGE(decoder->log, "AMediaCodec_start failed: %d", (int)status);
\t\tAMediaCodec_delete(decoder->codec);
\t\tdecoder->codec = NULL;
\t\tchiaki_mutex_unlock(&decoder->codec_mutex);
\t\treturn;
\t}
\tdecoder->codec_started = true;

\tChiakiErrorCode err = chiaki_thread_create(&decoder->output_thread, android_chiaki_audio_decoder_output_thread_func, decoder);
\tif(err == CHIAKI_ERR_SUCCESS)
\t\tdecoder->output_thread_started = true;
\tchiaki_mutex_unlock(&decoder->codec_mutex);

\tif(err != CHIAKI_ERR_SUCCESS)
\t{
\t\tCHIAKI_LOGE(decoder->log, "Failed to create output thread for AMediaCodec");
\t\tandroid_chiaki_audio_decoder_shutdown_codec(decoder);
\t\treturn;
\t}

\tuint8_t opus_id_head[0x13];
\tmemcpy(opus_id_head, "OpusHead", 8);
\topus_id_head[0x8] = 1; // version
\topus_id_head[0x9] = header->channels;
\tuint16_t pre_skip = 3840;
\topus_id_head[0xa] = (uint8_t)(pre_skip & 0xff);
\topus_id_head[0xb] = (uint8_t)(pre_skip >> 8);
\topus_id_head[0xc] = (uint8_t)(header->rate & 0xff);
\topus_id_head[0xd] = (uint8_t)((header->rate >> 0x8) & 0xff);
\topus_id_head[0xe] = (uint8_t)((header->rate >> 0x10) & 0xff);
\topus_id_head[0xf] = (uint8_t)(header->rate >> 0x18);
\tuint16_t output_gain = 0;
\topus_id_head[0x10] = (uint8_t)(output_gain & 0xff);
\topus_id_head[0x11] = (uint8_t)(output_gain >> 8);
\topus_id_head[0x12] = 0; // channel map

\tuint64_t pre_skip_ns = 0;
\tuint8_t csd1[8] = {
\t\t(uint8_t)(pre_skip_ns & 0xff), (uint8_t)((pre_skip_ns >> 0x8) & 0xff),
\t\t(uint8_t)((pre_skip_ns >> 0x10) & 0xff), (uint8_t)((pre_skip_ns >> 0x18) & 0xff),
\t\t(uint8_t)((pre_skip_ns >> 0x20) & 0xff), (uint8_t)((pre_skip_ns >> 0x28) & 0xff),
\t\t(uint8_t)((pre_skip_ns >> 0x30) & 0xff), (uint8_t)(pre_skip_ns >> 0x38)
\t};
\tuint64_t pre_roll_ns = 0;
\tuint8_t csd2[8] = {
\t\t(uint8_t)(pre_roll_ns & 0xff), (uint8_t)((pre_roll_ns >> 0x8) & 0xff),
\t\t(uint8_t)((pre_roll_ns >> 0x10) & 0xff), (uint8_t)((pre_roll_ns >> 0x18) & 0xff),
\t\t(uint8_t)((pre_roll_ns >> 0x20) & 0xff), (uint8_t)((pre_roll_ns >> 0x28) & 0xff),
\t\t(uint8_t)((pre_roll_ns >> 0x30) & 0xff), (uint8_t)(pre_roll_ns >> 0x38)
\t};

\t// All three codec-specific headers use the same serialized input path as
\t// live audio packets. The output worker is already polling in short slices.
\tif(!android_chiaki_audio_decoder_queue_frame(decoder, opus_id_head, sizeof(opus_id_head)) ||
\t\t!android_chiaki_audio_decoder_queue_frame(decoder, csd1, sizeof(csd1)) ||
\t\t!android_chiaki_audio_decoder_queue_frame(decoder, csd2, sizeof(csd2)))
\t{
\t\tCHIAKI_LOGE(decoder->log, "Failed to queue Opus decoder configuration buffers");
\t\tandroid_chiaki_audio_decoder_shutdown_codec(decoder);
\t\treturn;
\t}

\tif(decoder->settings_cb)
\t\tdecoder->settings_cb(header->channels, header->rate, decoder->cb_user);
}"""
)

replace_once(
    "audio-decoder.c",
    """static void android_chiaki_audio_decoder_frame(uint8_t *buf, size_t buf_size, void *user)
{
\tAndroidChiakiAudioDecoder *decoder = user;
\tchiaki_mutex_lock(&decoder->codec_mutex);

\tif(!decoder->codec)
\t{
\t\tCHIAKI_LOGE(decoder->log, "Received audio data, but decoder is not initialized!");
\t\tgoto beach;
\t}

\twhile(buf_size > 0)
\t{
\t\tssize_t codec_buf_index = AMediaCodec_dequeueInputBuffer(decoder->codec, INPUT_BUFFER_TIMEOUT_MS * 1000);
\t\tif(codec_buf_index < 0)
\t\t{
\t\t\tCHIAKI_LOGE(decoder->log, "Failed to get input audio buffer");
\t\t\treturn;
\t\t}

\t\tsize_t codec_buf_size;
\t\tuint8_t *codec_buf = AMediaCodec_getInputBuffer(decoder->codec, (size_t)codec_buf_index, &codec_buf_size);
\t\tsize_t codec_sample_size = buf_size;
\t\tif(codec_sample_size > codec_buf_size)
\t\t{
\t\t\tCHIAKI_LOGD(decoder->log, "Sample is bigger than audio buffer, splitting");
\t\t\tcodec_sample_size = codec_buf_size;
\t\t}
\t\tmemcpy(codec_buf, buf, codec_sample_size);
\t\tAMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, codec_sample_size, decoder->timestamp_cur++, 0); // timestamp just raised by 1 for maximum realtime
\t\tbuf += codec_sample_size;
\t\tbuf_size -= codec_sample_size;
\t}

beach:
\tchiaki_mutex_unlock(&decoder->codec_mutex);
}""",
    """static bool android_chiaki_audio_decoder_queue_frame_locked(AndroidChiakiAudioDecoder *decoder, uint8_t *buf, size_t buf_size)
{
\tif(!decoder->codec || !decoder->codec_started || decoder->stopping)
\t\treturn false;

\twhile(buf_size > 0)
\t{
\t\tssize_t codec_buf_index = AMediaCodec_dequeueInputBuffer(
\t\t\tdecoder->codec, INPUT_BUFFER_TIMEOUT_MS * 1000);
\t\tif(codec_buf_index < 0)
\t\t{
\t\t\tif(codec_buf_index != AMEDIACODEC_INFO_TRY_AGAIN_LATER)
\t\t\t\tCHIAKI_LOGW(decoder->log, "AMediaCodec_dequeueInputBuffer returned %zd", codec_buf_index);
\t\t\treturn false;
\t\t}

\t\tsize_t codec_buf_size = 0;
\t\tuint8_t *codec_buf = AMediaCodec_getInputBuffer(
\t\t\tdecoder->codec, (size_t)codec_buf_index, &codec_buf_size);
\t\tif(!codec_buf || codec_buf_size == 0)
\t\t{
\t\t\tCHIAKI_LOGE(decoder->log, "AMediaCodec returned an invalid audio input buffer");
\t\t\t// Return the dequeued slot so the codec is not left waiting on it.
\t\t\tAMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, 0,
\t\t\t\tdecoder->timestamp_cur++, 0);
\t\t\treturn false;
\t\t}

\t\tsize_t codec_sample_size = buf_size;
\t\tif(codec_sample_size > codec_buf_size)
\t\t{
\t\t\tCHIAKI_LOGD(decoder->log, "Audio sample is bigger than codec buffer; splitting");
\t\t\tcodec_sample_size = codec_buf_size;
\t\t}
\t\tif(codec_sample_size == 0)
\t\t\treturn false;

\t\tmemcpy(codec_buf, buf, codec_sample_size);
\t\tmedia_status_t status = AMediaCodec_queueInputBuffer(
\t\t\tdecoder->codec, (size_t)codec_buf_index, 0, codec_sample_size,
\t\t\tdecoder->timestamp_cur++, 0);
\t\tif(status != AMEDIA_OK)
\t\t{
\t\t\tCHIAKI_LOGE(decoder->log, "AMediaCodec_queueInputBuffer failed: %d", (int)status);
\t\t\treturn false;
\t\t}

\t\tbuf += codec_sample_size;
\t\tbuf_size -= codec_sample_size;
\t}
\treturn true;
}

static bool android_chiaki_audio_decoder_queue_frame(AndroidChiakiAudioDecoder *decoder, uint8_t *buf, size_t buf_size)
{
\tbool result;
\tchiaki_mutex_lock(&decoder->codec_mutex);
\tresult = android_chiaki_audio_decoder_queue_frame_locked(decoder, buf, buf_size);
\tchiaki_mutex_unlock(&decoder->codec_mutex);
\treturn result;
}

static void android_chiaki_audio_decoder_frame(uint8_t *buf, size_t buf_size, void *user)
{
\tAndroidChiakiAudioDecoder *decoder = user;
\tif(!android_chiaki_audio_decoder_queue_frame(decoder, buf, buf_size))
\t\tCHIAKI_LOGD(decoder->log, "Audio frame was skipped because the codec had no input slot or was stopping");
}"""
)

print("Alpha 8 serialized MediaCodec audio fix applied.")


# Bump the Android version so Alpha 8 installs as an update over Alpha 7.
app_gradle = REMOTE.parent / "app" / "build.gradle"
gradle_source = app_gradle.read_text(encoding="utf-8")
old_version = 'versionCode 48\n\t\tversionName "1.28.1-glossawhale-alpha7"'
new_version = 'versionCode 49\n\t\tversionName "1.28.1-glossawhale-alpha8"'
if gradle_source.count(old_version) != 1:
    raise SystemExit("Could not uniquely locate the Alpha 7 app version in app/build.gradle")
app_gradle.write_text(gradle_source.replace(old_version, new_version, 1), encoding="utf-8")

print("Alpha 8 versionCode 49 / versionName applied.")
