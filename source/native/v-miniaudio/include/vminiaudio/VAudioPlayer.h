#ifndef VMINIAUDIO_VAUDIOPLAYER_H
#define VMINIAUDIO_VAUDIOPLAYER_H

#include "VAudioTypes.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct VMAPlayer VMAPlayer;

typedef struct VMAPlayerConfig {
    uint32_t sample_rate;
    uint32_t channels;
    float volume;
    float pan;
    float pitch;
    bool looping;
} VMAPlayerConfig;

typedef struct VMAPlayerInfo {
    uint64_t total_frames;
    uint64_t position_frames;
    uint32_t sample_rate;
    uint32_t channels;
    double duration;
    double position;
    float volume;
    float pan;
    float pitch;
    bool looping;
    VMAPlaybackState state;
} VMAPlayerInfo;

typedef void (*VMAPlayerFinishedCallback)(
    VMAPlayer* player,
    void* user_data
);

VMA_API void vma_player_config_init(
    VMAPlayerConfig* config
);

VMA_API VMAResult vma_player_create(
    const VMAPlayerConfig* config,
    VMAPlayer** player
);

VMA_API void vma_player_destroy(
    VMAPlayer* player
);

VMA_API VMAResult vma_player_load_pcm(
    VMAPlayer* player,
    const float* samples,
    uint64_t frames
);

VMA_API VMAResult vma_player_unload(
    VMAPlayer* player
);

VMA_API VMAResult vma_player_play(
    VMAPlayer* player
);

VMA_API VMAResult vma_player_pause(
    VMAPlayer* player
);

VMA_API VMAResult vma_player_resume(
    VMAPlayer* player
);

VMA_API VMAResult vma_player_stop(
    VMAPlayer* player
);

VMA_API VMAResult vma_player_restart(
    VMAPlayer* player
);

VMA_API VMAResult vma_player_seek(
    VMAPlayer* player,
    double seconds
);

VMA_API VMAResult vma_player_set_volume(
    VMAPlayer* player,
    float volume
);

VMA_API float vma_player_get_volume(
    const VMAPlayer* player
);

VMA_API VMAResult vma_player_set_pan(
    VMAPlayer* player,
    float pan
);

VMA_API float vma_player_get_pan(
    const VMAPlayer* player
);

VMA_API VMAResult vma_player_set_pitch(
    VMAPlayer* player,
    float pitch
);

VMA_API float vma_player_get_pitch(
    const VMAPlayer* player
);

VMA_API VMAResult vma_player_set_looping(
    VMAPlayer* player,
    bool looping
);

VMA_API bool vma_player_is_looping(
    const VMAPlayer* player
);

VMA_API VMAPlaybackState vma_player_get_state(
    const VMAPlayer* player
);

VMA_API bool vma_player_is_playing(
    const VMAPlayer* player
);

VMA_API double vma_player_get_position(
    const VMAPlayer* player
);

VMA_API double vma_player_get_duration(
    const VMAPlayer* player
);

VMA_API VMAResult vma_player_get_info(
    const VMAPlayer* player,
    VMAPlayerInfo* info
);

VMA_API VMAResult vma_player_set_finished_callback(
    VMAPlayer* player,
    VMAPlayerFinishedCallback callback,
    void* user_data
);

VMA_API VMAResult vma_player_set_interpolation(
    VMAPlayer* player,
    VMAInterpolation interpolation
);

VMA_API VMAResult vma_player_set_pan_law(
    VMAPlayer* player,
    VMAPanLaw law
);

VMA_API VMAResult vma_player_fade(
    VMAPlayer* player,
    float target,
    double seconds,
    VMAFadeEnd end
);

VMA_API float vma_player_get_fade(
    const VMAPlayer* player
);

VMA_API VMAResult vma_player_set_loop_points(
    VMAPlayer* player,
    double start,
    double end
);

VMA_API VMAResult vma_player_play_delayed(
    VMAPlayer* player,
    double seconds
);

VMA_API float vma_player_get_peak(
    const VMAPlayer* player
);

VMA_API VMAResult vma_player_process(
    VMAPlayer* player,
    float* output,
    uint32_t frames,
    uint32_t output_channels,
    uint32_t output_sample_rate
);

#ifdef __cplusplus
}
#endif

#endif
