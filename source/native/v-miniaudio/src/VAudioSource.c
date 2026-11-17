#include "VAudioInternal.h"

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#define VMA_MAX_PENDING_CALLBACKS 64

void vma_mutex_init(VMAMutex* mutex) {
#if defined(_WIN32)
    InitializeCriticalSection(mutex);
#else
    pthread_mutex_init(mutex, NULL);
#endif
}

void vma_mutex_destroy(VMAMutex* mutex) {
#if defined(_WIN32)
    DeleteCriticalSection(mutex);
#else
    pthread_mutex_destroy(mutex);
#endif
}

void vma_mutex_lock(VMAMutex* mutex) {
#if defined(_WIN32)
    EnterCriticalSection(mutex);
#else
    pthread_mutex_lock(mutex);
#endif
}

void vma_mutex_unlock(VMAMutex* mutex) {
#if defined(_WIN32)
    LeaveCriticalSection(mutex);
#else
    pthread_mutex_unlock(mutex);
#endif
}

static bool vma_context_ready(const VMAContext* context) {
    return context != NULL && context->initialized;
}

static bool vma_audio_ready(const VMAAudio* audio) {
    return audio != NULL && audio->player != NULL && vma_context_ready(audio->context);
}

static uint16_t vma_read_u16(const uint8_t* data) {
    return (uint16_t)(data[0] | (data[1] << 8));
}

static uint32_t vma_read_u32(const uint8_t* data) {
    return (uint32_t)data[0] | ((uint32_t)data[1] << 8) | ((uint32_t)data[2] << 16) | ((uint32_t)data[3] << 24);
}

static size_t vma_sample_bytes(VMASampleFormat format) {
    switch (format) {
        case VMA_SAMPLE_U8:
            return 1;
        case VMA_SAMPLE_S16:
            return 2;
        case VMA_SAMPLE_S24:
            return 3;
        case VMA_SAMPLE_S32:
        case VMA_SAMPLE_F32:
            return 4;
        case VMA_SAMPLE_F64:
            return 8;
        default:
            return 0;
    }
}

static float vma_convert_sample(const uint8_t* data, VMASampleFormat format) {
    switch (format) {
        case VMA_SAMPLE_U8:
            return ((float)data[0] - 128.0f) / 128.0f;
        case VMA_SAMPLE_S16:
            return (float)(int16_t)vma_read_u16(data) / 32768.0f;
        case VMA_SAMPLE_S24: {
            int32_t value = (int32_t)((uint32_t)data[0] << 8 | (uint32_t)data[1] << 16 | (uint32_t)data[2] << 24) >> 8;
            return (float)value / 8388608.0f;
        }
        case VMA_SAMPLE_S32:
            return (float)((double)(int32_t)vma_read_u32(data) / 2147483648.0);
        case VMA_SAMPLE_F32: {
            float value;
            memcpy(&value, data, sizeof(value));
            return isfinite(value) ? value : 0.0f;
        }
        case VMA_SAMPLE_F64: {
            double value;
            memcpy(&value, data, sizeof(value));
            return isfinite(value) ? (float)value : 0.0f;
        }
        default:
            return 0.0f;
    }
}

VMAAudioFormat vma_detect_format(const void* data, size_t size) {
    const uint8_t* bytes = (const uint8_t*)data;

    if (bytes == NULL || size < 4)
        return VMA_FORMAT_UNKNOWN;

    if (size >= 12 && memcmp(bytes, "RIFF", 4) == 0 && memcmp(bytes + 8, "WAVE", 4) == 0)
        return VMA_FORMAT_WAV;

    if (memcmp(bytes, "OggS", 4) == 0)
        return VMA_FORMAT_OGG;

    if (memcmp(bytes, "fLaC", 4) == 0)
        return VMA_FORMAT_FLAC;

    if (memcmp(bytes, "ID3", 3) == 0 || (bytes[0] == 0xFF && (bytes[1] & 0xE0) == 0xE0))
        return VMA_FORMAT_MP3;

    return VMA_FORMAT_UNKNOWN;
}

static VMAResult vma_audio_attach(
    VMAContext* context,
    float* samples,
    uint64_t frames,
    uint32_t channels,
    uint32_t sample_rate,
    VMAAudioFormat format,
    VMASampleFormat sample_format,
    uint64_t file_size,
    VMAAudio** audio
) {
    VMAPlayerConfig config;
    vma_player_config_init(&config);
    config.sample_rate = sample_rate;
    config.channels = channels;

    VMAPlayer* player = NULL;
    VMAResult result = vma_player_create(&config, &player);

    if (result != VMA_SUCCESS)
        return result;

    result = vma_player_load_pcm(player, samples, frames);

    if (result != VMA_SUCCESS) {
        vma_player_destroy(player);
        return result;
    }

    VMAAudio* instance = (VMAAudio*)calloc(1, sizeof(VMAAudio));

    if (instance == NULL) {
        vma_player_destroy(player);
        return VMA_ERROR_OUT_OF_MEMORY;
    }

    instance->context = context;
    instance->player = player;
    instance->info.format = format;
    instance->info.sample_format = sample_format;
    instance->info.sample_rate = sample_rate;
    instance->info.channels = channels;
    instance->info.total_frames = frames;
    instance->info.duration = (double)frames / (double)sample_rate;
    instance->info.file_size = file_size;

    vma_mutex_lock(&context->lock);
    instance->next = context->audios;
    context->audios = instance;
    context->audio_count++;
    vma_mutex_unlock(&context->lock);

    *audio = instance;

    return VMA_SUCCESS;
}

static VMAResult vma_audio_from_samples(
    VMAContext* context,
    const uint8_t* data,
    uint64_t frame_count,
    uint32_t channels,
    uint32_t sample_rate,
    VMASampleFormat format,
    VMAAudioFormat container,
    uint64_t file_size,
    VMAAudio** audio
) {
    size_t width = vma_sample_bytes(format);

    if (width == 0)
        return VMA_ERROR_INVALID_FORMAT;

    if (channels == 0 || channels > 32 || sample_rate < 8000 || sample_rate > 384000 || frame_count == 0)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (frame_count > SIZE_MAX / channels / sizeof(float))
        return VMA_ERROR_OUT_OF_MEMORY;

    size_t count = (size_t)frame_count * channels;
    float* samples = (float*)malloc(count * sizeof(float));

    if (samples == NULL)
        return VMA_ERROR_OUT_OF_MEMORY;

    if (format == VMA_SAMPLE_F32) {
        memcpy(samples, data, count * sizeof(float));
        for (size_t i = 0; i < count; ++i)
            if (!isfinite(samples[i]))
                samples[i] = 0.0f;
    } else {
        for (size_t i = 0; i < count; ++i)
            samples[i] = vma_convert_sample(data + i * width, format);
    }

    VMAResult result = vma_audio_attach(context, samples, frame_count, channels, sample_rate, container, format, file_size, audio);

    free(samples);

    return result;
}

static VMAResult vma_decode_wav(
    VMAContext* context,
    const uint8_t* bytes,
    size_t size,
    VMAAudio** audio
) {
    size_t offset = 12;
    uint16_t tag = 0;
    uint16_t channels = 0;
    uint32_t sample_rate = 0;
    uint16_t bits = 0;
    uint16_t block = 0;
    const uint8_t* data = NULL;
    size_t data_size = 0;

    while (offset + 8 <= size) {
        const uint8_t* chunk = bytes + offset;
        uint32_t length = vma_read_u32(chunk + 4);
        size_t available = size - offset - 8;
        size_t body = length > available ? available : length;

        if (memcmp(chunk, "fmt ", 4) == 0 && body >= 16) {
            tag = vma_read_u16(chunk + 8);
            channels = vma_read_u16(chunk + 10);
            sample_rate = vma_read_u32(chunk + 12);
            block = vma_read_u16(chunk + 20);
            bits = vma_read_u16(chunk + 22);

            if (tag == 0xFFFE && body >= 26)
                tag = vma_read_u16(chunk + 32);
        } else if (memcmp(chunk, "data", 4) == 0) {
            data = chunk + 8;
            data_size = body;
        }

        offset += 8 + (size_t)length + (length & 1u);
    }

    if (data == NULL || channels == 0 || sample_rate == 0)
        return VMA_ERROR_INVALID_FORMAT;

    VMASampleFormat format;

    if (tag == 1 && bits == 8)
        format = VMA_SAMPLE_U8;
    else if (tag == 1 && bits == 16)
        format = VMA_SAMPLE_S16;
    else if (tag == 1 && bits == 24)
        format = VMA_SAMPLE_S24;
    else if (tag == 1 && bits == 32)
        format = VMA_SAMPLE_S32;
    else if (tag == 3 && bits == 32)
        format = VMA_SAMPLE_F32;
    else if (tag == 3 && bits == 64)
        format = VMA_SAMPLE_F64;
    else
        return VMA_ERROR_UNSUPPORTED_FORMAT;

    size_t frame_bytes = vma_sample_bytes(format) * channels;

    if (block != 0 && block != frame_bytes)
        return VMA_ERROR_INVALID_FORMAT;

    uint64_t frames = data_size / frame_bytes;

    if (frames == 0)
        return VMA_ERROR_INVALID_FORMAT;

    return vma_audio_from_samples(context, data, frames, channels, sample_rate, format, VMA_FORMAT_WAV, size, audio);
}

VMAResult vma_audio_load_memory(
    VMAContext* context,
    const void* data,
    size_t size,
    VMAAudio** audio
) {
    if (!vma_context_ready(context))
        return VMA_ERROR_NOT_INITIALIZED;

    if (data == NULL || size == 0 || audio == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    *audio = NULL;

    switch (vma_detect_format(data, size)) {
        case VMA_FORMAT_WAV:
            return vma_decode_wav(context, (const uint8_t*)data, size, audio);
        case VMA_FORMAT_OGG:
        case VMA_FORMAT_MP3:
        case VMA_FORMAT_FLAC:
            return VMA_ERROR_UNSUPPORTED_FORMAT;
        default:
            return VMA_ERROR_INVALID_FORMAT;
    }
}

VMAResult vma_audio_load(
    VMAContext* context,
    const char* path,
    VMAAudio** audio
) {
    if (!vma_context_ready(context))
        return VMA_ERROR_NOT_INITIALIZED;

    if (path == NULL || audio == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    FILE* file = fopen(path, "rb");

    if (file == NULL)
        return VMA_ERROR_FILE_NOT_FOUND;

    if (fseek(file, 0, SEEK_END) != 0) {
        fclose(file);
        return VMA_ERROR_FILE_READ;
    }

    long length = ftell(file);

    if (length <= 0 || fseek(file, 0, SEEK_SET) != 0) {
        fclose(file);
        return VMA_ERROR_FILE_READ;
    }

    uint8_t* bytes = (uint8_t*)malloc((size_t)length);

    if (bytes == NULL) {
        fclose(file);
        return VMA_ERROR_OUT_OF_MEMORY;
    }

    size_t read = fread(bytes, 1, (size_t)length, file);
    fclose(file);

    if (read != (size_t)length) {
        free(bytes);
        return VMA_ERROR_FILE_READ;
    }

    VMAResult result = vma_audio_load_memory(context, bytes, read, audio);
    free(bytes);

    return result;
}

VMAResult vma_audio_create_pcm(
    VMAContext* context,
    const void* samples,
    uint64_t frame_count,
    uint32_t channels,
    uint32_t sample_rate,
    VMASampleFormat format,
    VMAAudio** audio
) {
    if (!vma_context_ready(context))
        return VMA_ERROR_NOT_INITIALIZED;

    if (samples == NULL || audio == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    *audio = NULL;

    return vma_audio_from_samples(context, (const uint8_t*)samples, frame_count, channels, sample_rate, format, VMA_FORMAT_PCM, 0, audio);
}

void vma_audio_destroy(VMAAudio* audio) {
    if (audio == NULL)
        return;

    VMAContext* context = audio->context;

    if (context != NULL) {
        vma_mutex_lock(&context->lock);

        VMAAudio** link = &context->audios;

        while (*link != NULL) {
            if (*link == audio) {
                *link = audio->next;
                context->audio_count--;
                break;
            }

            link = &(*link)->next;
        }

        vma_mutex_unlock(&context->lock);
    }

    vma_player_destroy(audio->player);
    free(audio);
}

void vma_context_release_audios(VMAContext* context) {
    vma_mutex_lock(&context->lock);

    VMAAudio* audio = context->audios;
    context->audios = NULL;
    context->audio_count = 0;

    vma_mutex_unlock(&context->lock);

    while (audio != NULL) {
        VMAAudio* next = audio->next;
        vma_player_destroy(audio->player);
        free(audio);
        audio = next;
    }
}

#define VMA_LOCKED(audio, failure, body)              \
    do {                                               \
        if (!vma_audio_ready(audio))                   \
            return failure;                            \
        vma_mutex_lock(&(audio)->context->lock);       \
        body                                           \
        vma_mutex_unlock(&(audio)->context->lock);     \
    } while (0)

VMAResult vma_audio_play(VMAAudio* audio) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        VMAPlaybackState state = vma_player_get_state(audio->player);
        result = state == VMA_STATE_PAUSED ? vma_player_resume(audio->player) : vma_player_play(audio->player);
    });
    return result;
}

VMAResult vma_audio_pause(VMAAudio* audio) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        result = vma_player_pause(audio->player);
    });
    return result;
}

VMAResult vma_audio_resume(VMAAudio* audio) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        result = vma_player_resume(audio->player);
    });
    return result;
}

VMAResult vma_audio_stop(VMAAudio* audio) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        result = vma_player_stop(audio->player);
    });
    return result;
}

VMAResult vma_audio_restart(VMAAudio* audio) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        result = vma_player_restart(audio->player);
    });
    return result;
}

VMAPlaybackState vma_audio_get_state(const VMAAudio* audio) {
    VMAPlaybackState state = VMA_STATE_STOPPED;
    VMA_LOCKED(audio, VMA_STATE_STOPPED, {
        state = vma_player_get_state(audio->player);
    });
    return state;
}

bool vma_audio_is_playing(const VMAAudio* audio) {
    return vma_audio_get_state(audio) == VMA_STATE_PLAYING;
}

VMAResult vma_audio_set_volume(VMAAudio* audio, float volume) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        result = vma_player_set_volume(audio->player, volume);
    });
    return result;
}

float vma_audio_get_volume(const VMAAudio* audio) {
    float value = 0.0f;
    VMA_LOCKED(audio, 0.0f, {
        value = vma_player_get_volume(audio->player);
    });
    return value;
}

VMAResult vma_audio_set_pan(VMAAudio* audio, float pan) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        result = vma_player_set_pan(audio->player, pan);
    });
    return result;
}

float vma_audio_get_pan(const VMAAudio* audio) {
    float value = 0.0f;
    VMA_LOCKED(audio, 0.0f, {
        value = vma_player_get_pan(audio->player);
    });
    return value;
}

VMAResult vma_audio_set_pitch(VMAAudio* audio, float pitch) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        result = vma_player_set_pitch(audio->player, pitch);
    });
    return result;
}

float vma_audio_get_pitch(const VMAAudio* audio) {
    float value = 0.0f;
    VMA_LOCKED(audio, 0.0f, {
        value = vma_player_get_pitch(audio->player);
    });
    return value;
}

VMAResult vma_audio_set_looping(VMAAudio* audio, bool looping) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        result = vma_player_set_looping(audio->player, looping);
    });
    return result;
}

bool vma_audio_is_looping(const VMAAudio* audio) {
    bool value = false;
    VMA_LOCKED(audio, false, {
        value = vma_player_is_looping(audio->player);
    });
    return value;
}

VMAResult vma_audio_seek(VMAAudio* audio, double seconds) {
    VMAResult result = VMA_ERROR_INVALID_ARGUMENT;
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        VMAPlaybackState state = vma_player_get_state(audio->player);
        result = vma_player_seek(audio->player, seconds);
        if (result == VMA_SUCCESS && state == VMA_STATE_FINISHED && seconds < vma_player_get_duration(audio->player)) {
            vma_player_stop(audio->player);
            vma_player_seek(audio->player, seconds);
        }
    });
    return result;
}

double vma_audio_get_position(const VMAAudio* audio) {
    double value = 0.0;
    VMA_LOCKED(audio, 0.0, {
        value = vma_player_get_position(audio->player);
    });
    return value;
}

double vma_audio_get_duration(const VMAAudio* audio) {
    if (audio == NULL)
        return 0.0;

    return audio->info.duration;
}

VMAResult vma_audio_get_info(const VMAAudio* audio, VMAAudioInfo* info) {
    if (audio == NULL || info == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    *info = audio->info;

    return VMA_SUCCESS;
}

VMAResult vma_audio_set_finished_callback(VMAAudio* audio, VMAFinishedCallback callback, void* user_data) {
    VMA_LOCKED(audio, VMA_ERROR_INVALID_ARGUMENT, {
        audio->finished_callback = callback;
        audio->finished_user_data = user_data;
    });
    return VMA_SUCCESS;
}

uint32_t vma_get_audio_count(const VMAContext* context) {
    if (!vma_context_ready(context))
        return 0;

    return context->audio_count;
}

VMAResult vma_render(VMAContext* context, float* output, uint32_t frames) {
    if (!vma_context_ready(context))
        return VMA_ERROR_NOT_INITIALIZED;

    if (output == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    uint32_t channels = context->config.channels;
    uint32_t rate = context->config.sample_rate;

    if ((size_t)frames > SIZE_MAX / channels / sizeof(float))
        return VMA_ERROR_INVALID_ARGUMENT;

    size_t count = (size_t)frames * channels;

    memset(output, 0, count * sizeof(float));

    if (frames == 0)
        return VMA_SUCCESS;

    VMAAudio* finished[VMA_MAX_PENDING_CALLBACKS];
    VMAFinishedCallback callbacks[VMA_MAX_PENDING_CALLBACKS];
    void* user_data[VMA_MAX_PENDING_CALLBACKS];
    uint32_t pending = 0;

    vma_mutex_lock(&context->lock);

    if (context->scratch_size < count) {
        float* grown = (float*)realloc(context->scratch, count * sizeof(float));

        if (grown == NULL) {
            vma_mutex_unlock(&context->lock);
            return VMA_ERROR_OUT_OF_MEMORY;
        }

        context->scratch = grown;
        context->scratch_size = count;
    }

    for (VMAAudio* audio = context->audios; audio != NULL; audio = audio->next) {
        if (vma_player_get_state(audio->player) != VMA_STATE_PLAYING)
            continue;

        if (vma_player_process(audio->player, context->scratch, frames, channels, rate) != VMA_SUCCESS)
            continue;

        for (size_t i = 0; i < count; ++i)
            output[i] += context->scratch[i];

        if (vma_player_get_state(audio->player) == VMA_STATE_FINISHED &&
            audio->finished_callback != NULL &&
            pending < VMA_MAX_PENDING_CALLBACKS) {
            finished[pending] = audio;
            callbacks[pending] = audio->finished_callback;
            user_data[pending] = audio->finished_user_data;
            pending++;
        }
    }

    if (context->audio_callback != NULL)
        context->audio_callback(output, frames, channels, context->audio_user_data);

    float gain = context->muted ? 0.0f : context->master_volume;

    for (size_t i = 0; i < count; ++i) {
        float sample = output[i] * gain;

        if (!isfinite(sample))
            sample = 0.0f;
        else if (sample > 1.0f)
            sample = 1.0f;
        else if (sample < -1.0f)
            sample = -1.0f;

        output[i] = sample;
    }

    vma_mutex_unlock(&context->lock);

    for (uint32_t i = 0; i < pending; ++i)
        callbacks[i](finished[i], user_data[i]);

    return VMA_SUCCESS;
}

VMAResult vma_device_get_count(VMAContext* context, uint32_t* count) {
    if (!vma_context_ready(context))
        return VMA_ERROR_NOT_INITIALIZED;

    if (count == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    *count = 0;

    return VMA_SUCCESS;
}

VMAResult vma_device_get_info(VMAContext* context, uint32_t index, VMADeviceInfo* info) {
    (void)index;

    if (!vma_context_ready(context))
        return VMA_ERROR_NOT_INITIALIZED;

    if (info == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    return VMA_ERROR_DEVICE_NOT_FOUND;
}

VMAResult vma_device_set_default(VMAContext* context) {
    if (!vma_context_ready(context))
        return VMA_ERROR_NOT_INITIALIZED;

    return VMA_ERROR_NOT_SUPPORTED;
}

VMAResult vma_device_set_by_id(VMAContext* context, const char* device_id) {
    if (!vma_context_ready(context))
        return VMA_ERROR_NOT_INITIALIZED;

    if (device_id == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    return VMA_ERROR_DEVICE_NOT_FOUND;
}
