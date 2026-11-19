#ifndef VMINIAUDIO_VAUDIO_H
#define VMINIAUDIO_VAUDIO_H

#include "VAudioTypes.h"

#ifdef __cplusplus
extern "C" {
#endif

VMA_API const char* vma_get_version(void);
VMA_API const char* vma_get_name(void);
VMA_API const char* vma_result_string(VMAResult result);

VMA_API void vma_config_init(VMAConfig* config);

VMA_API VMAResult vma_init(
    VMAContext** context,
    const VMAConfig* config
);

VMA_API void vma_shutdown(VMAContext* context);

VMA_API bool vma_is_initialized(
    const VMAContext* context
);

VMA_API VMAResult vma_update(
    VMAContext* context
);

VMA_API VMAResult vma_audio_load(
    VMAContext* context,
    const char* path,
    VMAAudio** audio
);

VMA_API VMAResult vma_audio_load_memory(
    VMAContext* context,
    const void* data,
    size_t size,
    VMAAudio** audio
);

VMA_API VMAResult vma_audio_create_pcm(
    VMAContext* context,
    const void* samples,
    uint64_t frame_count,
    uint32_t channels,
    uint32_t sample_rate,
    VMASampleFormat format,
    VMAAudio** audio
);

VMA_API void vma_audio_destroy(
    VMAAudio* audio
);

VMA_API VMAResult vma_audio_play(
    VMAAudio* audio
);

VMA_API VMAResult vma_audio_pause(
    VMAAudio* audio
);

VMA_API VMAResult vma_audio_resume(
    VMAAudio* audio
);

VMA_API VMAResult vma_audio_stop(
    VMAAudio* audio
);

VMA_API VMAResult vma_audio_restart(
    VMAAudio* audio
);

VMA_API VMAPlaybackState vma_audio_get_state(
    const VMAAudio* audio
);

VMA_API bool vma_audio_is_playing(
    const VMAAudio* audio
);

VMA_API VMAResult vma_audio_set_volume(
    VMAAudio* audio,
    float volume
);

VMA_API float vma_audio_get_volume(
    const VMAAudio* audio
);

VMA_API VMAResult vma_audio_set_pan(
    VMAAudio* audio,
    float pan
);

VMA_API float vma_audio_get_pan(
    const VMAAudio* audio
);

VMA_API VMAResult vma_audio_set_pitch(
    VMAAudio* audio,
    float pitch
);

VMA_API float vma_audio_get_pitch(
    const VMAAudio* audio
);

VMA_API VMAResult vma_audio_set_looping(
    VMAAudio* audio,
    bool looping
);

VMA_API bool vma_audio_is_looping(
    const VMAAudio* audio
);

VMA_API VMAResult vma_audio_seek(
    VMAAudio* audio,
    double seconds
);

VMA_API double vma_audio_get_position(
    const VMAAudio* audio
);

VMA_API double vma_audio_get_duration(
    const VMAAudio* audio
);

VMA_API VMAResult vma_audio_get_info(
    const VMAAudio* audio,
    VMAAudioInfo* info
);

VMA_API VMAResult vma_audio_set_finished_callback(
    VMAAudio* audio,
    VMAFinishedCallback callback,
    void* user_data
);

VMA_API VMAResult vma_set_master_volume(
    VMAContext* context,
    float volume
);

VMA_API float vma_get_master_volume(
    const VMAContext* context
);

VMA_API VMAResult vma_set_muted(
    VMAContext* context,
    bool muted
);

VMA_API bool vma_is_muted(
    const VMAContext* context
);

VMA_API VMAResult vma_device_get_count(
    VMAContext* context,
    uint32_t* count
);

VMA_API VMAResult vma_device_get_info(
    VMAContext* context,
    uint32_t index,
    VMADeviceInfo* info
);

VMA_API VMAResult vma_device_set_default(
    VMAContext* context
);

VMA_API VMAResult vma_device_set_by_id(
    VMAContext* context,
    const char* device_id
);

VMA_API VMAResult vma_set_audio_callback(
    VMAContext* context,
    VMAAudioCallback callback,
    void* user_data
);

VMA_API void vma_set_log_callback(
    VMAContext* context,
    VMALogCallback callback,
    void* user_data
);

VMA_API void vma_set_log_level(
    VMAContext* context,
    VMALogLevel level
);

VMA_API VMALogLevel vma_get_log_level(
    const VMAContext* context
);

VMA_API uint32_t vma_get_sample_rate(
    const VMAContext* context
);

VMA_API uint32_t vma_get_channels(
    const VMAContext* context
);

VMA_API uint32_t vma_get_buffer_frames(
    const VMAContext* context
);

VMA_API VMAResult vma_render(
    VMAContext* context,
    float* output,
    uint32_t frames
);

VMA_API uint32_t vma_get_audio_count(
    const VMAContext* context
);

VMA_API VMAAudioFormat vma_detect_format(
    const void* data,
    size_t size
);

VMA_API VMAResult vma_decode_memory(
    const void* data,
    size_t size,
    float** samples,
    uint64_t* frames,
    uint32_t* channels,
    uint32_t* sample_rate
);

VMA_API void vma_free(
    void* pointer
);

VMA_API bool vma_supports_format(
    VMAAudioFormat format
);

VMA_API VMAResult vma_audio_fade(
    VMAAudio* audio,
    float target,
    double seconds,
    VMAFadeEnd end
);

VMA_API float vma_audio_get_fade(
    const VMAAudio* audio
);

VMA_API VMAResult vma_audio_set_loop_points(
    VMAAudio* audio,
    double start,
    double end
);

VMA_API VMAResult vma_audio_play_delayed(
    VMAAudio* audio,
    double seconds
);

VMA_API float vma_audio_get_peak(
    const VMAAudio* audio
);

VMA_API VMAResult vma_audio_set_bus(
    VMAAudio* audio,
    uint32_t bus
);

VMA_API uint32_t vma_audio_get_bus(
    const VMAAudio* audio
);

VMA_API VMAResult vma_bus_set_volume(
    VMAContext* context,
    uint32_t bus,
    float volume
);

VMA_API float vma_bus_get_volume(
    const VMAContext* context,
    uint32_t bus
);

VMA_API VMAResult vma_bus_set_muted(
    VMAContext* context,
    uint32_t bus,
    bool muted
);

VMA_API VMAResult vma_bus_set_lowpass(
    VMAContext* context,
    uint32_t bus,
    float cutoff
);

VMA_API VMAResult vma_bus_set_highpass(
    VMAContext* context,
    uint32_t bus,
    float cutoff
);

VMA_API VMAResult vma_bus_set_echo(
    VMAContext* context,
    uint32_t bus,
    float seconds,
    float feedback,
    float mix
);

VMA_API float vma_bus_get_peak(
    const VMAContext* context,
    uint32_t bus
);

VMA_API VMAResult vma_set_interpolation(
    VMAContext* context,
    VMAInterpolation interpolation
);

VMA_API VMAInterpolation vma_get_interpolation(
    const VMAContext* context
);

VMA_API VMAResult vma_set_pan_law(
    VMAContext* context,
    VMAPanLaw law
);

VMA_API VMAResult vma_set_limiter(
    VMAContext* context,
    bool enabled,
    float ceiling
);

VMA_API VMAResult vma_get_meter(
    const VMAContext* context,
    VMAMeter* meter
);

VMA_API double vma_get_time(
    const VMAContext* context
);

#ifdef __cplusplus
}
#endif

#endif
