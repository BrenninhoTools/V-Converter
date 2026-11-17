#ifndef VMINIAUDIO_VWAVEFORM_H
#define VMINIAUDIO_VWAVEFORM_H

#include "VAudioTypes.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct VMAWaveform VMAWaveform;

typedef struct VMAWaveformConfig {
    uint32_t channels;
    uint32_t sample_rate;
    uint32_t resolution;
    bool normalize;
} VMAWaveformConfig;

typedef struct VMAWaveformPoint {
    float minimum;
    float maximum;
    float peak;
    float rms;
} VMAWaveformPoint;

typedef struct VMAWaveformInfo {
    uint64_t total_frames;
    uint32_t channels;
    uint32_t sample_rate;
    uint32_t point_count;
    float peak;
    float rms;
    double duration;
} VMAWaveformInfo;

VMA_API void vma_waveform_config_init(
    VMAWaveformConfig* config
);

VMA_API VMAResult vma_waveform_create(
    const VMAWaveformConfig* config,
    VMAWaveform** waveform
);

VMA_API void vma_waveform_destroy(
    VMAWaveform* waveform
);

VMA_API VMAResult vma_waveform_generate(
    VMAWaveform* waveform,
    const float* samples,
    uint64_t frames
);

VMA_API VMAResult vma_waveform_get_point(
    const VMAWaveform* waveform,
    uint32_t index,
    VMAWaveformPoint* point
);

VMA_API VMAResult vma_waveform_get_info(
    const VMAWaveform* waveform,
    VMAWaveformInfo* info
);

VMA_API uint32_t vma_waveform_get_point_count(
    const VMAWaveform* waveform
);

VMA_API float vma_waveform_calculate_peak(
    const float* samples,
    uint64_t sample_count
);

VMA_API float vma_waveform_calculate_rms(
    const float* samples,
    uint64_t sample_count
);

VMA_API VMAResult vma_waveform_normalize(
    float* samples,
    uint64_t sample_count,
    float target_peak
);

VMA_API VMAResult vma_waveform_clear(
    VMAWaveform* waveform
);

#ifdef __cplusplus
}
#endif

#endif
