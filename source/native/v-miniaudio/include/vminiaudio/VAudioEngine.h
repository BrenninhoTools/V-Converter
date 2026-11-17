#ifndef VMINIAUDIO_VAUDIOENGINE_H
#define VMINIAUDIO_VAUDIOENGINE_H

#include "VAudioTypes.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct VMAEngine VMAEngine;

typedef struct VMAEngineConfig {
    uint32_t sample_rate;
    uint32_t channels;
    uint32_t buffer_frames;
    float master_volume;
    bool enable_limiter;
} VMAEngineConfig;

typedef void (*VMAEngineProcessCallback)(
    float* output,
    uint32_t frames,
    uint32_t channels,
    void* user_data
);

VMA_API void vma_engine_config_init(
    VMAEngineConfig* config
);

VMA_API VMAResult vma_engine_create(
    const VMAEngineConfig* config,
    VMAEngine** engine
);

VMA_API void vma_engine_destroy(
    VMAEngine* engine
);

VMA_API VMAResult vma_engine_start(
    VMAEngine* engine
);

VMA_API VMAResult vma_engine_stop(
    VMAEngine* engine
);

VMA_API bool vma_engine_is_running(
    const VMAEngine* engine
);

VMA_API VMAResult vma_engine_set_volume(
    VMAEngine* engine,
    float volume
);

VMA_API float vma_engine_get_volume(
    const VMAEngine* engine
);

VMA_API VMAResult vma_engine_set_callback(
    VMAEngine* engine,
    VMAEngineProcessCallback callback,
    void* user_data
);

VMA_API VMAResult vma_engine_process(
    VMAEngine* engine,
    float* output,
    uint32_t frames
);

VMA_API VMAResult vma_engine_mix(
    VMAEngine* engine,
    float* destination,
    const float* source,
    uint32_t frames,
    float gain
);

VMA_API VMAResult vma_engine_clear(
    VMAEngine* engine,
    float* buffer,
    uint32_t frames
);

VMA_API VMAResult vma_engine_apply_gain(
    VMAEngine* engine,
    float* buffer,
    uint32_t frames,
    float gain
);

VMA_API VMAResult vma_engine_apply_limiter(
    VMAEngine* engine,
    float* buffer,
    uint32_t frames
);

VMA_API uint32_t vma_engine_get_sample_rate(
    const VMAEngine* engine
);

VMA_API uint32_t vma_engine_get_channels(
    const VMAEngine* engine
);

VMA_API uint32_t vma_engine_get_buffer_frames(
    const VMAEngine* engine
);

#ifdef __cplusplus
}
#endif

#endif
