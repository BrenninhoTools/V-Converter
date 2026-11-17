#include "vminiaudio/VAudioEngine.h"

#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdint.h>

struct VMAEngine {
    VMAEngineConfig config;
    bool running;
    float master_volume;

    VMAEngineProcessCallback callback;
    void* callback_user_data;
};

static float vma_engine_clamp(
    float value,
    float minimum,
    float maximum
) {
    if (value < minimum)
        return minimum;

    if (value > maximum)
        return maximum;

    return value;
}

static bool vma_engine_valid(
    const VMAEngine* engine
) {
    return engine != NULL;
}

static bool vma_engine_sample_count(
    const VMAEngine* engine,
    uint32_t frames,
    size_t* count
) {
    if (engine == NULL || count == NULL)
        return false;

    if (engine->config.channels == 0)
        return false;

    if ((size_t)frames >
        SIZE_MAX / engine->config.channels)
        return false;

    size_t total =
        (size_t)frames * engine->config.channels;

    if (total > SIZE_MAX / sizeof(float))
        return false;

    *count = total;

    return true;
}

void vma_engine_config_init(
    VMAEngineConfig* config
) {
    if (config == NULL)
        return;

    memset(config, 0, sizeof(*config));

    config->sample_rate =
        VMA_DEFAULT_SAMPLE_RATE;

    config->channels =
        VMA_DEFAULT_CHANNELS;

    config->buffer_frames =
        VMA_DEFAULT_BUFFER_FRAMES;

    config->master_volume = 1.0f;
    config->enable_limiter = true;
}

VMAResult vma_engine_create(
    const VMAEngineConfig* config,
    VMAEngine** engine
) {
    if (engine == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (*engine != NULL)
        return VMA_ERROR_ALREADY_INITIALIZED;

    VMAEngineConfig local;

    if (config == NULL) {
        vma_engine_config_init(&local);
        config = &local;
    }

    if (config->sample_rate < 8000 ||
        config->sample_rate > 384000)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (config->channels == 0 ||
        config->channels > 32)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (config->buffer_frames == 0 ||
        config->buffer_frames > 65536)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (!isfinite(config->master_volume))
        return VMA_ERROR_INVALID_ARGUMENT;

    VMAEngine* instance =
        (VMAEngine*)calloc(1, sizeof(VMAEngine));

    if (instance == NULL)
        return VMA_ERROR_OUT_OF_MEMORY;

    instance->config = *config;

    instance->master_volume = vma_engine_clamp(
        config->master_volume,
        0.0f,
        1.0f
    );

    instance->running = false;
    instance->callback = NULL;
    instance->callback_user_data = NULL;

    *engine = instance;

    return VMA_SUCCESS;
}

void vma_engine_destroy(
    VMAEngine* engine
) {
    if (engine == NULL)
        return;

    engine->running = false;
    engine->callback = NULL;
    engine->callback_user_data = NULL;

    free(engine);
}

VMAResult vma_engine_start(
    VMAEngine* engine
) {
    if (!vma_engine_valid(engine))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (engine->running)
        return VMA_ERROR_ALREADY_INITIALIZED;

    engine->running = true;

    return VMA_SUCCESS;
}

VMAResult vma_engine_stop(
    VMAEngine* engine
) {
    if (!vma_engine_valid(engine))
        return VMA_ERROR_INVALID_ARGUMENT;

    engine->running = false;

    return VMA_SUCCESS;
}

bool vma_engine_is_running(
    const VMAEngine* engine
) {
    if (!vma_engine_valid(engine))
        return false;

    return engine->running;
}

VMAResult vma_engine_set_volume(
    VMAEngine* engine,
    float volume
) {
    if (!vma_engine_valid(engine))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (!isfinite(volume))
        return VMA_ERROR_INVALID_ARGUMENT;

    engine->master_volume =
        vma_engine_clamp(volume, 0.0f, 1.0f);

    return VMA_SUCCESS;
}

float vma_engine_get_volume(
    const VMAEngine* engine
) {
    if (!vma_engine_valid(engine))
        return 0.0f;

    return engine->master_volume;
}

VMAResult vma_engine_set_callback(
    VMAEngine* engine,
    VMAEngineProcessCallback callback,
    void* user_data
) {
    if (!vma_engine_valid(engine))
        return VMA_ERROR_INVALID_ARGUMENT;

    engine->callback = callback;
    engine->callback_user_data = user_data;

    return VMA_SUCCESS;
}

VMAResult vma_engine_clear(
    VMAEngine* engine,
    float* buffer,
    uint32_t frames
) {
    size_t count;

    if (!vma_engine_sample_count(engine, frames, &count))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (count == 0)
        return VMA_SUCCESS;

    if (buffer == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    memset(buffer, 0, count * sizeof(float));

    return VMA_SUCCESS;
}

VMAResult vma_engine_apply_gain(
    VMAEngine* engine,
    float* buffer,
    uint32_t frames,
    float gain
) {
    size_t count;

    if (!vma_engine_sample_count(engine, frames, &count))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (!isfinite(gain) || gain < 0.0f)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (count == 0)
        return VMA_SUCCESS;

    if (buffer == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    for (size_t i = 0; i < count; ++i)
        buffer[i] *= gain;

    return VMA_SUCCESS;
}

VMAResult vma_engine_apply_limiter(
    VMAEngine* engine,
    float* buffer,
    uint32_t frames
) {
    size_t count;

    if (!vma_engine_sample_count(engine, frames, &count))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (count == 0)
        return VMA_SUCCESS;

    if (buffer == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    for (size_t i = 0; i < count; ++i) {
        float sample = buffer[i];

        if (!isfinite(sample))
            sample = 0.0f;

        buffer[i] = vma_engine_clamp(
            sample,
            -1.0f,
            1.0f
        );
    }

    return VMA_SUCCESS;
}

VMAResult vma_engine_mix(
    VMAEngine* engine,
    float* destination,
    const float* source,
    uint32_t frames,
    float gain
) {
    size_t count;

    if (!vma_engine_sample_count(engine, frames, &count))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (!isfinite(gain) || gain < 0.0f)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (count == 0)
        return VMA_SUCCESS;

    if (destination == NULL || source == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    for (size_t i = 0; i < count; ++i)
        destination[i] += source[i] * gain;

    return VMA_SUCCESS;
}

VMAResult vma_engine_process(
    VMAEngine* engine,
    float* output,
    uint32_t frames
) {
    size_t count;

    if (!vma_engine_sample_count(engine, frames, &count))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (count == 0)
        return VMA_SUCCESS;

    if (output == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    memset(output, 0, count * sizeof(float));

    if (!engine->running)
        return VMA_SUCCESS;

    if (engine->callback != NULL) {
        engine->callback(
            output,
            frames,
            engine->config.channels,
            engine->callback_user_data
        );
    }

    VMAResult result = vma_engine_apply_gain(
        engine,
        output,
        frames,
        engine->master_volume
    );

    if (result != VMA_SUCCESS)
        return result;

    if (engine->config.enable_limiter) {
        return vma_engine_apply_limiter(
            engine,
            output,
            frames
        );
    }

    return VMA_SUCCESS;
}

uint32_t vma_engine_get_sample_rate(
    const VMAEngine* engine
) {
    if (!vma_engine_valid(engine))
        return 0;

    return engine->config.sample_rate;
}

uint32_t vma_engine_get_channels(
    const VMAEngine* engine
) {
    if (!vma_engine_valid(engine))
        return 0;

    return engine->config.channels;
}

uint32_t vma_engine_get_buffer_frames(
    const VMAEngine* engine
) {
    if (!vma_engine_valid(engine))
        return 0;

    return engine->config.buffer_frames;
}
