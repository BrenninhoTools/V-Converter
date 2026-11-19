#include "VAudioInternal.h"

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>


static float vma_clamp(
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

static bool vma_valid_context(
    const VMAContext* context
) {
    return context != NULL && context->initialized;
}

static const char* vma_log_level_string(
    VMALogLevel level
) {
    switch (level) {
        case VMA_LOG_ERROR:
            return "ERROR";

        case VMA_LOG_WARNING:
            return "WARNING";

        case VMA_LOG_INFO:
            return "INFO";

        case VMA_LOG_DEBUG:
            return "DEBUG";

        case VMA_LOG_TRACE:
            return "TRACE";

        default:
            return "NONE";
    }
}

static void vma_log(
    VMAContext* context,
    VMALogLevel level,
    const char* message
) {
    if (!vma_valid_context(context))
        return;

    if (!context->config.enable_logging)
        return;

    if (level == VMA_LOG_NONE)
        return;

    if (level > context->log_level)
        return;

    if (context->log_callback != NULL) {
        context->log_callback(
            level,
            message,
            context->log_user_data
        );

        return;
    }

    fprintf(
        stderr,
        "[V-MiniAudio][%s] %s\n",
        vma_log_level_string(level),
        message
    );
}

const char* vma_get_version(void) {
    return VMA_VERSION_STRING;
}

const char* vma_get_name(void) {
    return VMA_NAME;
}

const char* vma_result_string(
    VMAResult result
) {
    switch (result) {
        case VMA_SUCCESS:
            return "Success";

        case VMA_ERROR_UNKNOWN:
            return "Unknown error";

        case VMA_ERROR_INVALID_ARGUMENT:
            return "Invalid argument";

        case VMA_ERROR_OUT_OF_MEMORY:
            return "Out of memory";

        case VMA_ERROR_NOT_INITIALIZED:
            return "Audio engine not initialized";

        case VMA_ERROR_ALREADY_INITIALIZED:
            return "Audio engine already initialized";

        case VMA_ERROR_FILE_NOT_FOUND:
            return "File not found";

        case VMA_ERROR_FILE_READ:
            return "Failed to read file";

        case VMA_ERROR_FILE_WRITE:
            return "Failed to write file";

        case VMA_ERROR_UNSUPPORTED_FORMAT:
            return "Unsupported audio format";

        case VMA_ERROR_INVALID_FORMAT:
            return "Invalid audio format";

        case VMA_ERROR_DEVICE_NOT_FOUND:
            return "Audio device not found";

        case VMA_ERROR_DEVICE_INIT:
            return "Failed to initialize audio device";

        case VMA_ERROR_DEVICE_LOST:
            return "Audio device disconnected";

        case VMA_ERROR_PLAYBACK:
            return "Audio playback error";

        case VMA_ERROR_DECODING:
            return "Audio decoding error";

        case VMA_ERROR_ENCODING:
            return "Audio encoding error";

        case VMA_ERROR_NOT_SUPPORTED:
            return "Operation not supported";

        case VMA_ERROR_INVALID_STATE:
            return "Invalid audio state";

        case VMA_ERROR_END_OF_STREAM:
            return "End of audio stream";

        default:
            return "Unrecognized error code";
    }
}

void vma_config_init(
    VMAConfig* config
) {
    if (config == NULL)
        return;

    memset(config, 0, sizeof(VMAConfig));

    config->sample_rate =
        VMA_DEFAULT_SAMPLE_RATE;

    config->channels =
        VMA_DEFAULT_CHANNELS;

    config->buffer_frames =
        VMA_DEFAULT_BUFFER_FRAMES;

    config->sample_format =
        VMA_SAMPLE_F32;

    config->enable_logging = true;
    config->enable_low_latency = false;
}

static VMAResult vma_validate_config(
    const VMAConfig* config
) {
    if (config == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (config->sample_rate < 8000 ||
        config->sample_rate > 384000)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (config->channels == 0 ||
        config->channels > 32)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (config->buffer_frames == 0 ||
        config->buffer_frames > 65536)
        return VMA_ERROR_INVALID_ARGUMENT;

    switch (config->sample_format) {
        case VMA_SAMPLE_U8:
        case VMA_SAMPLE_S16:
        case VMA_SAMPLE_S24:
        case VMA_SAMPLE_S32:
        case VMA_SAMPLE_F32:
        case VMA_SAMPLE_F64:
            break;

        default:
            return VMA_ERROR_INVALID_FORMAT;
    }

    return VMA_SUCCESS;
}

VMAResult vma_init(
    VMAContext** context,
    const VMAConfig* config
) {
    if (context == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (*context != NULL)
        return VMA_ERROR_ALREADY_INITIALIZED;

    VMAConfig local_config;

    if (config == NULL) {
        vma_config_init(&local_config);
        config = &local_config;
    }

    VMAResult result =
        vma_validate_config(config);

    if (result != VMA_SUCCESS)
        return result;

    VMAContext* instance =
        (VMAContext*)calloc(
            1,
            sizeof(VMAContext)
        );

    if (instance == NULL)
        return VMA_ERROR_OUT_OF_MEMORY;

    instance->config = *config;

    instance->master_volume = 1.0f;
    instance->muted = false;

    instance->log_level =
        config->enable_logging
            ? VMA_LOG_INFO
            : VMA_LOG_NONE;

    instance->log_callback = NULL;
    instance->log_user_data = NULL;

    instance->audio_callback = NULL;
    instance->audio_user_data = NULL;

    instance->audios = NULL;
    instance->audio_count = 0;
    instance->scratch = NULL;
    instance->scratch_size = 0;
    instance->interpolation = VMA_INTERPOLATION_CUBIC;
    instance->pan_law = VMA_PAN_LINEAR;
    instance->limiter_enabled = true;
    instance->limiter_ceiling = 1.0f;
    instance->limiter_gain = 1.0f;
    instance->limiter_release = (float)exp(-1.0 / (0.08 * (double)config->sample_rate));
    instance->rendered_frames = 0;
    vma_buses_init(instance);

    vma_mutex_init(&instance->lock);

    instance->initialized = true;

    *context = instance;

    vma_log(
        instance,
        VMA_LOG_INFO,
        "V-MiniAudio context initialized"
    );

    return VMA_SUCCESS;
}

void vma_shutdown(
    VMAContext* context
) {
    if (context == NULL)
        return;

    vma_log(
        context,
        VMA_LOG_INFO,
        "Shutting down V-MiniAudio"
    );

    vma_context_release_audios(context);

    context->initialized = false;

    vma_buses_free(context);
    vma_mutex_destroy(&context->lock);
    free(context->scratch);
    free(context);
}

bool vma_is_initialized(
    const VMAContext* context
) {
    return vma_valid_context(context);
}

VMAResult vma_update(
    VMAContext* context
) {
    if (!vma_valid_context(context))
        return VMA_ERROR_NOT_INITIALIZED;

    return VMA_SUCCESS;
}

VMAResult vma_set_master_volume(
    VMAContext* context,
    float volume
) {
    if (!vma_valid_context(context))
        return VMA_ERROR_NOT_INITIALIZED;

    if (!isfinite(volume))
        return VMA_ERROR_INVALID_ARGUMENT;

    context->master_volume = vma_clamp(
        volume,
        VMA_MIN_VOLUME,
        VMA_MAX_VOLUME
    );

    return VMA_SUCCESS;
}

float vma_get_master_volume(
    const VMAContext* context
) {
    if (!vma_valid_context(context))
        return 0.0f;

    return context->master_volume;
}

VMAResult vma_set_muted(
    VMAContext* context,
    bool muted
) {
    if (!vma_valid_context(context))
        return VMA_ERROR_NOT_INITIALIZED;

    context->muted = muted;

    return VMA_SUCCESS;
}

bool vma_is_muted(
    const VMAContext* context
) {
    if (!vma_valid_context(context))
        return false;

    return context->muted;
}

void vma_set_log_callback(
    VMAContext* context,
    VMALogCallback callback,
    void* user_data
) {
    if (!vma_valid_context(context))
        return;

    context->log_callback = callback;
    context->log_user_data = user_data;
}

void vma_set_log_level(
    VMAContext* context,
    VMALogLevel level
) {
    if (!vma_valid_context(context))
        return;

    if (level < VMA_LOG_NONE ||
        level > VMA_LOG_TRACE)
        return;

    context->log_level = level;
}

VMALogLevel vma_get_log_level(
    const VMAContext* context
) {
    if (!vma_valid_context(context))
        return VMA_LOG_NONE;

    return context->log_level;
}

VMAResult vma_set_audio_callback(
    VMAContext* context,
    VMAAudioCallback callback,
    void* user_data
) {
    if (!vma_valid_context(context))
        return VMA_ERROR_NOT_INITIALIZED;

    context->audio_callback = callback;
    context->audio_user_data = user_data;

    return VMA_SUCCESS;
}

uint32_t vma_get_sample_rate(
    const VMAContext* context
) {
    if (!vma_valid_context(context))
        return 0;

    return context->config.sample_rate;
}

uint32_t vma_get_channels(
    const VMAContext* context
) {
    if (!vma_valid_context(context))
        return 0;

    return context->config.channels;
}

uint32_t vma_get_buffer_frames(
    const VMAContext* context
) {
    if (!vma_valid_context(context))
        return 0;

    return context->config.buffer_frames;
}
