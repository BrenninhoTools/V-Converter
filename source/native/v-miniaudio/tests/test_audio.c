#include "vminiaudio/VAudio.h"
#include "vminiaudio/VAudioEngine.h"
#include "vminiaudio/VWaveform.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int failures = 0;

#define CHECK(condition)                                                   \
    do {                                                                   \
        if (!(condition)) {                                                \
            fprintf(stderr, "%s:%d: check failed: %s\n", __FILE__, __LINE__, #condition); \
            failures++;                                                    \
        }                                                                  \
    } while (0)

static void put_u16(unsigned char* at, unsigned value) {
    at[0] = (unsigned char)(value & 0xFF);
    at[1] = (unsigned char)((value >> 8) & 0xFF);
}

static void put_u32(unsigned char* at, unsigned long value) {
    at[0] = (unsigned char)(value & 0xFF);
    at[1] = (unsigned char)((value >> 8) & 0xFF);
    at[2] = (unsigned char)((value >> 16) & 0xFF);
    at[3] = (unsigned char)((value >> 24) & 0xFF);
}

static unsigned char* make_wav(unsigned frames, unsigned rate, short value, size_t* size) {
    size_t data = (size_t)frames * 4;
    unsigned char* wav = (unsigned char*)calloc(1, 44 + data);
    memcpy(wav, "RIFF", 4);
    put_u32(wav + 4, (unsigned long)(36 + data));
    memcpy(wav + 8, "WAVEfmt ", 8);
    put_u32(wav + 16, 16);
    put_u16(wav + 20, 1);
    put_u16(wav + 22, 2);
    put_u32(wav + 24, rate);
    put_u32(wav + 28, (unsigned long)rate * 4);
    put_u16(wav + 32, 4);
    put_u16(wav + 34, 16);
    memcpy(wav + 36, "data", 4);
    put_u32(wav + 40, (unsigned long)data);
    for (unsigned i = 0; i < frames * 2; ++i)
        put_u16(wav + 44 + i * 2, (unsigned short)value);
    *size = 44 + data;
    return wav;
}

static int finished_calls = 0;

static void on_finished(VMAAudio* audio, void* user_data) {
    (void)audio;
    (void)user_data;
    finished_calls++;
}

static VMAContext* make_context(void) {
    VMAConfig config;
    vma_config_init(&config);
    config.sample_rate = 48000;
    config.channels = 2;
    config.enable_logging = false;
    VMAContext* context = NULL;
    vma_init(&context, &config);
    return context;
}

static VMAAudio* constant(VMAContext* context, unsigned frames, float value) {
    float* pcm = (float*)malloc(sizeof(float) * frames * 2);
    for (unsigned i = 0; i < frames * 2; ++i)
        pcm[i] = value;
    VMAAudio* audio = NULL;
    vma_audio_create_pcm(context, pcm, frames, 2, 48000, VMA_SAMPLE_F32, &audio);
    free(pcm);
    return audio;
}

static void advanced_tests(void) {
    VMAContext* context = make_context();
    float out[4800 * 2];
    CHECK(vma_get_interpolation(context) == VMA_INTERPOLATION_CUBIC);
    CHECK(vma_supports_format(VMA_FORMAT_OGG));

    VMAAudio* tone = constant(context, 48000, 0.5f);
    CHECK(vma_audio_fade(tone, 0.0f, 0.1, VMA_FADE_PAUSE) == VMA_SUCCESS);
    CHECK(vma_audio_play(tone) == VMA_SUCCESS);
    CHECK(vma_render(context, out, 2400) == VMA_SUCCESS);
    CHECK(fabsf(out[0] - 0.5f) < 0.001f);
    CHECK(fabsf(out[2399 * 2] - 0.25f) < 0.01f);
    CHECK(vma_render(context, out, 2400) == VMA_SUCCESS);
    CHECK(vma_audio_get_state(tone) == VMA_STATE_PAUSED);
    CHECK(fabsf(vma_audio_get_fade(tone) - 1.0f) < 1e-6f);
    CHECK(fabs(vma_audio_get_position(tone) - 0.1) < 0.001);
    vma_audio_stop(tone);

    CHECK(vma_audio_play_delayed(tone, 0.05) == VMA_SUCCESS);
    CHECK(vma_render(context, out, 4800) == VMA_SUCCESS);
    CHECK(out[2398 * 2] == 0.0f);
    CHECK(fabsf(out[2402 * 2] - 0.5f) < 0.001f);
    CHECK(fabs(vma_get_time(context) - (4800.0 + 4800.0) / 48000.0) < 1e-9);
    vma_audio_stop(tone);

    float ramp[1000 * 2];
    for (int i = 0; i < 1000; ++i)
        ramp[i * 2] = ramp[i * 2 + 1] = (float)i / 1000.0f;
    VMAAudio* looped = NULL;
    CHECK(vma_audio_create_pcm(context, ramp, 1000, 2, 48000, VMA_SAMPLE_F32, &looped) == VMA_SUCCESS);
    CHECK(vma_audio_set_loop_points(looped, 500.0 / 48000.0, 600.0 / 48000.0) == VMA_SUCCESS);
    CHECK(vma_audio_set_loop_points(looped, 0.5, 0.1) == VMA_ERROR_INVALID_ARGUMENT);
    vma_audio_set_looping(looped, true);
    vma_audio_set_volume(tone, 0.0f);
    vma_audio_play(looped);
    CHECK(vma_render(context, out, 700) == VMA_SUCCESS);
    CHECK(fabsf(out[650 * 2] - 0.55f) < 0.002f);
    CHECK(vma_audio_get_position(looped) <= 600.0 / 48000.0 + 1e-9);
    vma_audio_stop(looped);

    VMAAudio* loud = constant(context, 48000, 0.9f);
    VMAAudio* louder = constant(context, 48000, 0.9f);
    vma_audio_play(loud);
    vma_audio_play(louder);
    CHECK(vma_render(context, out, 480) == VMA_SUCCESS);
    VMAMeter meter;
    CHECK(vma_get_meter(context, &meter) == VMA_SUCCESS);
    CHECK(meter.peak_left <= 1.0f && meter.peak_left > 0.99f);
    CHECK(meter.gain_reduction > 0.4f);
    CHECK(fabsf(vma_audio_get_peak(loud) - 0.9f) < 0.001f);

    CHECK(vma_audio_set_bus(loud, 1) == VMA_SUCCESS);
    CHECK(vma_audio_set_bus(louder, 99) == VMA_ERROR_INVALID_ARGUMENT);
    CHECK(vma_bus_set_volume(context, 1, 0.0f) == VMA_SUCCESS);
    CHECK(vma_set_limiter(context, false, 1.0f) == VMA_SUCCESS);
    CHECK(vma_render(context, out, 480) == VMA_SUCCESS);
    CHECK(fabsf(out[100] - 0.9f) < 0.001f);
    CHECK(vma_bus_get_peak(context, 1) == 0.0f);
    vma_audio_stop(loud);
    vma_audio_stop(louder);

    VMAAudio* buzz = NULL;
    float square[4800 * 2];
    for (int i = 0; i < 4800; ++i)
        square[i * 2] = square[i * 2 + 1] = (i % 2 == 0) ? 0.5f : -0.5f;
    CHECK(vma_audio_create_pcm(context, square, 4800, 2, 48000, VMA_SAMPLE_F32, &buzz) == VMA_SUCCESS);
    vma_audio_set_bus(buzz, 2);
    CHECK(vma_bus_set_lowpass(context, 2, 500.0f) == VMA_SUCCESS);
    vma_audio_play(buzz);
    CHECK(vma_render(context, out, 2400) == VMA_SUCCESS);
    float peak = 0.0f;
    for (int i = 1200; i < 2400; ++i)
        if (fabsf(out[i * 2]) > peak)
            peak = fabsf(out[i * 2]);
    CHECK(peak < 0.01f);
    CHECK(vma_bus_set_lowpass(context, 2, 0.0f) == VMA_SUCCESS);
    vma_audio_stop(buzz);

    VMAAudio* click = constant(context, 48, 0.5f);
    vma_audio_set_bus(click, 3);
    CHECK(vma_bus_set_echo(context, 3, 0.01f, 0.5f, 1.0f) == VMA_SUCCESS);
    vma_audio_play(click);
    CHECK(vma_render(context, out, 1000) == VMA_SUCCESS);
    CHECK(fabsf(out[10 * 2] - 0.5f) < 0.001f);
    CHECK(fabsf(out[490 * 2] - 0.5f) < 0.001f);
    CHECK(fabsf(out[970 * 2] - 0.25f) < 0.001f);
    CHECK(out[300 * 2] == 0.0f);

    vma_shutdown(context);

#ifdef VMA_TEST_OGG
    FILE* file = fopen(VMA_TEST_OGG, "rb");
    CHECK(file != NULL);
    if (file != NULL) {
        fseek(file, 0, SEEK_END);
        long length = ftell(file);
        fseek(file, 0, SEEK_SET);
        unsigned char* bytes = (unsigned char*)malloc((size_t)length);
        size_t got = fread(bytes, 1, (size_t)length, file);
        fclose(file);
        CHECK(got == (size_t)length);
        float* samples = NULL;
        uint64_t frames = 0;
        uint32_t channels = 0;
        uint32_t rate = 0;
        CHECK(vma_decode_memory(bytes, got, &samples, &frames, &channels, &rate) == VMA_SUCCESS);
        CHECK(frames > 1000 && channels >= 1 && rate >= 8000);
        float loudest = 0.0f;
        for (uint64_t i = 0; i < frames * channels; ++i)
            if (fabsf(samples[i]) > loudest)
                loudest = fabsf(samples[i]);
        CHECK(loudest > 0.01f && loudest <= 1.0f);
        vma_free(samples);
        VMAContext* other = make_context();
        VMAAudio* ogg = NULL;
        CHECK(vma_audio_load_memory(other, bytes, got, &ogg) == VMA_SUCCESS);
        VMAAudioInfo info;
        vma_audio_get_info(ogg, &info);
        CHECK(info.format == VMA_FORMAT_OGG && info.total_frames == frames);
        vma_shutdown(other);
        free(bytes);
    }
#endif
}

int main(void) {
    advanced_tests();

    VMAConfig config;
    vma_config_init(&config);
    config.sample_rate = 48000;
    config.channels = 2;
    config.enable_logging = false;

    VMAContext* context = NULL;
    CHECK(vma_init(&context, &config) == VMA_SUCCESS);

    size_t size = 0;
    unsigned char* wav = make_wav(4800, 48000, 16384, &size);
    CHECK(vma_detect_format(wav, size) == VMA_FORMAT_WAV);
    CHECK(vma_detect_format("OggS....", 8) == VMA_FORMAT_OGG);

    VMAAudio* tone = NULL;
    CHECK(vma_audio_load_memory(context, wav, size, &tone) == VMA_SUCCESS);
    CHECK(fabs(vma_audio_get_duration(tone) - 0.1) < 1e-9);

    VMAAudioInfo info;
    CHECK(vma_audio_get_info(tone, &info) == VMA_SUCCESS);
    CHECK(info.channels == 2 && info.sample_rate == 48000 && info.total_frames == 4800);

    float pcm[2400];
    for (int i = 0; i < 2400; ++i)
        pcm[i] = 0.25f;

    VMAAudio* second = NULL;
    CHECK(vma_audio_create_pcm(context, pcm, 2400, 1, 24000, VMA_SAMPLE_F32, &second) == VMA_SUCCESS);
    CHECK(vma_get_audio_count(context) == 2);

    float out[512 * 2];
    CHECK(vma_render(context, out, 512) == VMA_SUCCESS);
    CHECK(out[0] == 0.0f);

    CHECK(vma_audio_play(tone) == VMA_SUCCESS);
    CHECK(vma_audio_play(second) == VMA_SUCCESS);
    CHECK(vma_render(context, out, 512) == VMA_SUCCESS);
    CHECK(fabsf(out[10] - 0.75f) < 0.001f);
    CHECK(fabs(vma_audio_get_position(tone) - 512.0 / 48000.0) < 1e-9);
    CHECK(fabs(vma_audio_get_position(second) - 512.0 / 48000.0) < 1e-6);

    CHECK(vma_set_master_volume(context, 0.5f) == VMA_SUCCESS);
    CHECK(vma_render(context, out, 16) == VMA_SUCCESS);
    CHECK(fabsf(out[0] - 0.375f) < 0.001f);
    CHECK(vma_set_master_volume(context, 1.0f) == VMA_SUCCESS);

    CHECK(vma_audio_pause(tone) == VMA_SUCCESS);
    CHECK(vma_render(context, out, 16) == VMA_SUCCESS);
    CHECK(fabsf(out[0] - 0.25f) < 0.001f);

    CHECK(vma_audio_seek(tone, 0.05) == VMA_SUCCESS);
    CHECK(fabs(vma_audio_get_position(tone) - 0.05) < 1e-9);
    CHECK(vma_audio_play(tone) == VMA_SUCCESS);
    CHECK(vma_audio_get_state(tone) == VMA_STATE_PLAYING);

    CHECK(vma_audio_set_finished_callback(tone, on_finished, NULL) == VMA_SUCCESS);
    for (int i = 0; i < 20; ++i)
        vma_render(context, out, 512);
    CHECK(finished_calls == 1);
    CHECK(vma_audio_get_state(tone) == VMA_STATE_FINISHED);

    CHECK(vma_audio_seek(tone, 0.02) == VMA_SUCCESS);
    CHECK(vma_audio_get_state(tone) == VMA_STATE_STOPPED);
    CHECK(vma_audio_play(tone) == VMA_SUCCESS);
    CHECK(fabs(vma_audio_get_position(tone) - 0.02) < 1e-9);

    CHECK(vma_audio_set_pitch(tone, 2.0f) == VMA_SUCCESS);
    double before = vma_audio_get_position(tone);
    vma_render(context, out, 480);
    CHECK(fabs(vma_audio_get_position(tone) - before - 0.02) < 1e-9);

    short loud[200];
    for (int i = 0; i < 200; ++i)
        loud[i] = 32767;
    VMAAudio* clip = NULL;
    CHECK(vma_audio_create_pcm(context, loud, 100, 2, 48000, VMA_SAMPLE_S16, &clip) == VMA_SUCCESS);
    vma_audio_stop(tone);
    vma_audio_stop(second);
    vma_audio_play(clip);
    vma_render(context, out, 4);
    CHECK(out[0] <= 1.0f && out[0] > 0.99f);

    unsigned char ogg[8] = { 'O', 'g', 'g', 'S', 0, 0, 0, 0 };
    VMAAudio* none = NULL;
    CHECK(vma_audio_load_memory(context, ogg, sizeof(ogg), &none) == VMA_ERROR_DECODING);
    CHECK(none == NULL);

    uint32_t devices = 99;
    CHECK(vma_device_get_count(context, &devices) == VMA_SUCCESS && devices == 0);

    vma_audio_destroy(second);
    CHECK(vma_get_audio_count(context) == 2);

    VMAWaveformConfig wave_config;
    vma_waveform_config_init(&wave_config);
    wave_config.channels = 1;
    wave_config.resolution = 10;
    VMAWaveform* waveform = NULL;
    CHECK(vma_waveform_create(&wave_config, &waveform) == VMA_SUCCESS);
    CHECK(vma_waveform_generate(waveform, pcm, 2400) == VMA_SUCCESS);
    CHECK(vma_waveform_get_point_count(waveform) == 10);
    vma_waveform_destroy(waveform);

    free(wav);
    vma_shutdown(context);

    if (failures == 0)
        printf("V-MiniAudio tests passed\n");

    return failures == 0 ? 0 : 1;
}
