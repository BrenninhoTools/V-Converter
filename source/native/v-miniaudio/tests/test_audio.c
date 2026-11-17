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

int main(void) {
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
    CHECK(vma_audio_load_memory(context, ogg, sizeof(ogg), &none) == VMA_ERROR_UNSUPPORTED_FORMAT);
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
