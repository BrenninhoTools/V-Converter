#include "vminiaudio/VWaveform.h"

#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdint.h>

struct VMAWaveform {
    VMAWaveformConfig config;
    VMAWaveformPoint* points;

    uint32_t point_count;
    uint64_t total_frames;

    float peak;
    float rms;
};

static bool vma_waveform_valid_config(
    const VMAWaveformConfig* config
) {
    return config != NULL &&
           config->channels > 0 &&
           config->channels <= 32 &&
           config->sample_rate >= 8000 &&
           config->sample_rate <= 384000 &&
           config->resolution > 0;
}

static bool vma_waveform_valid_samples(
    const float* samples,
    uint64_t count
) {
    return samples != NULL &&
           count > 0 &&
           count <= SIZE_MAX / sizeof(float);
}

static float vma_waveform_safe_sample(
    float sample
) {
    return isfinite(sample) ? sample : 0.0f;
}

void vma_waveform_config_init(
    VMAWaveformConfig* config
) {
    if (config == NULL)
        return;

    memset(config, 0, sizeof(*config));

    config->channels = VMA_DEFAULT_CHANNELS;
    config->sample_rate = VMA_DEFAULT_SAMPLE_RATE;
    config->resolution = 1024;
    config->normalize = false;
}

VMAResult vma_waveform_create(
    const VMAWaveformConfig* config,
    VMAWaveform** waveform
) {
    if (waveform == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (*waveform != NULL)
        return VMA_ERROR_ALREADY_INITIALIZED;

    VMAWaveformConfig local;

    if (config == NULL) {
        vma_waveform_config_init(&local);
        config = &local;
    }

    if (!vma_waveform_valid_config(config))
        return VMA_ERROR_INVALID_ARGUMENT;

    VMAWaveform* instance =
        (VMAWaveform*)calloc(1, sizeof(VMAWaveform));

    if (instance == NULL)
        return VMA_ERROR_OUT_OF_MEMORY;

    instance->config = *config;

    *waveform = instance;

    return VMA_SUCCESS;
}

void vma_waveform_destroy(
    VMAWaveform* waveform
) {
    if (waveform == NULL)
        return;

    free(waveform->points);
    free(waveform);
}

VMAResult vma_waveform_clear(
    VMAWaveform* waveform
) {
    if (waveform == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    free(waveform->points);

    waveform->points = NULL;
    waveform->point_count = 0;
    waveform->total_frames = 0;
    waveform->peak = 0.0f;
    waveform->rms = 0.0f;

    return VMA_SUCCESS;
}

float vma_waveform_calculate_peak(
    const float* samples,
    uint64_t sample_count
) {
    if (!vma_waveform_valid_samples(samples, sample_count))
        return 0.0f;

    float peak = 0.0f;

    for (uint64_t i = 0; i < sample_count; ++i) {
        float sample = fabsf(
            vma_waveform_safe_sample(samples[i])
        );

        if (sample > peak)
            peak = sample;
    }

    return peak;
}

float vma_waveform_calculate_rms(
    const float* samples,
    uint64_t sample_count
) {
    if (!vma_waveform_valid_samples(samples, sample_count))
        return 0.0f;

    double sum = 0.0;

    for (uint64_t i = 0; i < sample_count; ++i) {
        double sample =
            (double)vma_waveform_safe_sample(samples[i]);

        sum += sample * sample;
    }

    return (float)sqrt(sum / (double)sample_count);
}

VMAResult vma_waveform_normalize(
    float* samples,
    uint64_t sample_count,
    float target_peak
) {
    if (!vma_waveform_valid_samples(samples, sample_count))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (!isfinite(target_peak) ||
        target_peak <= 0.0f ||
        target_peak > 1.0f)
        return VMA_ERROR_INVALID_ARGUMENT;

    float peak = vma_waveform_calculate_peak(
        samples,
        sample_count
    );

    if (peak <= 0.0f)
        return VMA_SUCCESS;

    float gain = target_peak / peak;

    for (uint64_t i = 0; i < sample_count; ++i) {
        float sample =
            vma_waveform_safe_sample(samples[i]);

        samples[i] = sample * gain;
    }

    return VMA_SUCCESS;
}

VMAResult vma_waveform_generate(
    VMAWaveform* waveform,
    const float* samples,
    uint64_t frames
) {
    if (waveform == NULL || samples == NULL || frames == 0)
        return VMA_ERROR_INVALID_ARGUMENT;

    uint32_t channels = waveform->config.channels;

    if (frames > SIZE_MAX / channels)
        return VMA_ERROR_INVALID_ARGUMENT;

    size_t sample_count = (size_t)frames * channels;

    if (sample_count > SIZE_MAX / sizeof(float))
        return VMA_ERROR_INVALID_ARGUMENT;

    uint32_t resolution = waveform->config.resolution;

    uint32_t point_count = resolution;

    if (frames < resolution)
        point_count = (uint32_t)frames;

    if (point_count == 0 ||
        (size_t)point_count >
            SIZE_MAX / sizeof(VMAWaveformPoint))
        return VMA_ERROR_INVALID_ARGUMENT;

    VMAWaveformPoint* points =
        (VMAWaveformPoint*)calloc(
            point_count,
            sizeof(VMAWaveformPoint)
        );

    if (points == NULL)
        return VMA_ERROR_OUT_OF_MEMORY;

    double total_sum = 0.0;
    float global_peak = 0.0f;

    for (uint32_t i = 0; i < point_count; ++i) {
        uint64_t start =
            (frames / point_count) * i +
            ((frames % point_count) * i) / point_count;

        uint64_t end =
            (frames / point_count) * (i + 1) +
            ((frames % point_count) * (i + 1)) / point_count;

        float minimum = INFINITY;
        float maximum = -INFINITY;
        float peak = 0.0f;

        double sum = 0.0;
        uint64_t count = 0;

        for (uint64_t frame = start; frame < end; ++frame) {
            size_t offset = (size_t)frame * channels;

            for (uint32_t channel = 0; channel < channels; ++channel) {
                float sample = vma_waveform_safe_sample(
                    samples[offset + channel]
                );

                if (sample < minimum)
                    minimum = sample;

                if (sample > maximum)
                    maximum = sample;

                float absolute = fabsf(sample);

                if (absolute > peak)
                    peak = absolute;

                sum += (double)sample * sample;
                ++count;
            }
        }

        points[i].minimum = minimum;
        points[i].maximum = maximum;
        points[i].peak = peak;
        points[i].rms = count > 0
            ? (float)sqrt(sum / (double)count)
            : 0.0f;

        total_sum += sum;

        if (peak > global_peak)
            global_peak = peak;
    }

    if (waveform->config.normalize && global_peak > 0.0f) {
        float gain = 1.0f / global_peak;

        for (uint32_t i = 0; i < point_count; ++i) {
            points[i].minimum *= gain;
            points[i].maximum *= gain;
            points[i].peak *= gain;
            points[i].rms *= gain;
        }
    }

    free(waveform->points);

    waveform->points = points;
    waveform->point_count = point_count;
    waveform->total_frames = frames;
    waveform->peak = global_peak;
    waveform->rms = (float)sqrt(
        total_sum / (double)sample_count
    );

    return VMA_SUCCESS;
}

VMAResult vma_waveform_get_point(
    const VMAWaveform* waveform,
    uint32_t index,
    VMAWaveformPoint* point
) {
    if (waveform == NULL || point == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (index >= waveform->point_count)
        return VMA_ERROR_INVALID_ARGUMENT;

    *point = waveform->points[index];

    return VMA_SUCCESS;
}

VMAResult vma_waveform_get_info(
    const VMAWaveform* waveform,
    VMAWaveformInfo* info
) {
    if (waveform == NULL || info == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    info->total_frames = waveform->total_frames;
    info->channels = waveform->config.channels;
    info->sample_rate = waveform->config.sample_rate;
    info->point_count = waveform->point_count;
    info->peak = waveform->peak;
    info->rms = waveform->rms;

    info->duration = waveform->config.sample_rate > 0
        ? (double)waveform->total_frames /
          waveform->config.sample_rate
        : 0.0;

    return VMA_SUCCESS;
}

uint32_t vma_waveform_get_point_count(
    const VMAWaveform* waveform
) {
    return waveform != NULL
        ? waveform->point_count
        : 0;
}
