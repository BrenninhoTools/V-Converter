#include "vminiaudio/VAudioPlayer.h"

#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdint.h>

struct VMAPlayer {
    VMAPlayerConfig config;

    float* samples;
    uint64_t total_frames;
    double cursor;

    float volume;
    float pan;
    float pitch;

    bool looping;
    VMAPlaybackState state;

    VMAInterpolation interpolation;
    VMAPanLaw pan_law;

    float fade_gain;
    float fade_target;
    double fade_seconds;
    double fade_elapsed;
    float fade_from;
    VMAFadeEnd fade_end;
    bool fading;

    double loop_start;
    double loop_end;
    double delay;
    uint64_t delay_frames;
    float peak;

    VMAPlayerFinishedCallback finished_callback;
    void* finished_user_data;
};

static float vma_player_clamp(
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

static bool vma_player_valid(
    const VMAPlayer* player
) {
    return player != NULL;
}

static bool vma_player_has_audio(
    const VMAPlayer* player
) {
    return player != NULL &&
           player->samples != NULL &&
           player->total_frames > 0;
}

static double vma_player_region_end(
    const VMAPlayer* player
) {
    double total = (double)player->total_frames;

    if (player->loop_end > player->loop_start && player->loop_end <= total)
        return player->loop_end;

    return total;
}

static double vma_player_region_start(
    const VMAPlayer* player
) {
    double end = vma_player_region_end(player);

    if (player->loop_start > 0.0 && player->loop_start < end)
        return player->loop_start;

    return 0.0;
}

static float vma_player_sample(
    const VMAPlayer* player,
    int64_t frame,
    uint32_t channel
) {
    int64_t total = (int64_t)player->total_frames;

    if (player->looping) {
        int64_t start = (int64_t)vma_player_region_start(player);
        int64_t end = (int64_t)vma_player_region_end(player);
        int64_t length = end - start;

        if (length > 0 && frame >= end)
            frame = start + (frame - end) % length;
    }

    if (frame < 0)
        frame = 0;

    if (frame >= total)
        frame = total - 1;

    if (channel >= player->config.channels)
        channel = 0;

    return player->samples[(size_t)frame * player->config.channels + channel];
}

static float vma_player_interpolate(
    const VMAPlayer* player,
    uint32_t channel
) {
    int64_t frame = (int64_t)player->cursor;
    float t = (float)(player->cursor - (double)frame);

    switch (player->interpolation) {
        case VMA_INTERPOLATION_NEAREST:
            return vma_player_sample(player, t < 0.5f ? frame : frame + 1, channel);

        case VMA_INTERPOLATION_CUBIC: {
            float p0 = vma_player_sample(player, frame - 1, channel);
            float p1 = vma_player_sample(player, frame, channel);
            float p2 = vma_player_sample(player, frame + 1, channel);
            float p3 = vma_player_sample(player, frame + 2, channel);
            float c1 = 0.5f * (p2 - p0);
            float c2 = p0 - 2.5f * p1 + 2.0f * p2 - 0.5f * p3;
            float c3 = 0.5f * (p3 - p0) + 1.5f * (p1 - p2);
            return ((c3 * t + c2) * t + c1) * t + p1;
        }

        default: {
            float a = vma_player_sample(player, frame, channel);
            float b = vma_player_sample(player, frame + 1, channel);
            return a + (b - a) * t;
        }
    }
}

static void vma_player_finish(
    VMAPlayer* player
) {
    player->cursor = (double)player->total_frames;
    player->state = VMA_STATE_FINISHED;

    if (player->finished_callback != NULL) {
        player->finished_callback(
            player,
            player->finished_user_data
        );
    }
}

void vma_player_config_init(
    VMAPlayerConfig* config
) {
    if (config == NULL)
        return;

    memset(config, 0, sizeof(*config));

    config->sample_rate = VMA_DEFAULT_SAMPLE_RATE;
    config->channels = VMA_DEFAULT_CHANNELS;
    config->volume = 1.0f;
    config->pan = 0.0f;
    config->pitch = 1.0f;
    config->looping = false;
}

VMAResult vma_player_create(
    const VMAPlayerConfig* config,
    VMAPlayer** player
) {
    if (player == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (*player != NULL)
        return VMA_ERROR_ALREADY_INITIALIZED;

    VMAPlayerConfig local;

    if (config == NULL) {
        vma_player_config_init(&local);
        config = &local;
    }

    if (config->sample_rate < 8000 ||
        config->sample_rate > 384000 ||
        config->channels == 0 ||
        config->channels > 32)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (!isfinite(config->volume) ||
        !isfinite(config->pan) ||
        !isfinite(config->pitch) ||
        config->pitch <= 0.0f)
        return VMA_ERROR_INVALID_ARGUMENT;

    VMAPlayer* instance =
        (VMAPlayer*)calloc(1, sizeof(VMAPlayer));

    if (instance == NULL)
        return VMA_ERROR_OUT_OF_MEMORY;

    instance->config = *config;
    instance->volume = vma_player_clamp(
        config->volume, 0.0f, 1.0f
    );

    instance->pan = vma_player_clamp(
        config->pan, -1.0f, 1.0f
    );

    instance->pitch = config->pitch;
    instance->looping = config->looping;
    instance->state = VMA_STATE_STOPPED;
    instance->interpolation = VMA_INTERPOLATION_LINEAR;
    instance->pan_law = VMA_PAN_LINEAR;
    instance->fade_gain = 1.0f;
    instance->fade_target = 1.0f;
    instance->fade_end = VMA_FADE_CONTINUE;

    *player = instance;

    return VMA_SUCCESS;
}

void vma_player_destroy(
    VMAPlayer* player
) {
    if (player == NULL)
        return;

    free(player->samples);
    free(player);
}

VMAResult vma_player_load_pcm(
    VMAPlayer* player,
    const float* samples,
    uint64_t frames
) {
    if (!vma_player_valid(player) ||
        samples == NULL ||
        frames == 0)
        return VMA_ERROR_INVALID_ARGUMENT;

    uint32_t channels = player->config.channels;

    if (frames > SIZE_MAX / channels)
        return VMA_ERROR_INVALID_ARGUMENT;

    size_t count = (size_t)frames * channels;

    if (count > SIZE_MAX / sizeof(float))
        return VMA_ERROR_INVALID_ARGUMENT;

    float* copy = (float*)malloc(
        count * sizeof(float)
    );

    if (copy == NULL)
        return VMA_ERROR_OUT_OF_MEMORY;

    memcpy(copy, samples, count * sizeof(float));

    free(player->samples);

    player->samples = copy;
    player->total_frames = frames;
    player->cursor = 0.0;
    player->state = VMA_STATE_STOPPED;

    return VMA_SUCCESS;
}

VMAResult vma_player_unload(
    VMAPlayer* player
) {
    if (!vma_player_valid(player))
        return VMA_ERROR_INVALID_ARGUMENT;

    free(player->samples);

    player->samples = NULL;
    player->total_frames = 0;
    player->cursor = 0.0;
    player->state = VMA_STATE_STOPPED;

    return VMA_SUCCESS;
}

VMAResult vma_player_play(
    VMAPlayer* player
) {
    if (!vma_player_has_audio(player))
        return VMA_ERROR_INVALID_STATE;

    if (player->state == VMA_STATE_FINISHED)
        player->cursor = 0.0;

    player->state = VMA_STATE_PLAYING;

    player->delay = 0.0;
    player->delay_frames = 0;

    return VMA_SUCCESS;
}

VMAResult vma_player_pause(
    VMAPlayer* player
) {
    if (!vma_player_valid(player))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (player->state != VMA_STATE_PLAYING)
        return VMA_ERROR_INVALID_STATE;

    player->state = VMA_STATE_PAUSED;

    return VMA_SUCCESS;
}

VMAResult vma_player_resume(
    VMAPlayer* player
) {
    if (!vma_player_valid(player))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (player->state != VMA_STATE_PAUSED)
        return VMA_ERROR_INVALID_STATE;

    player->state = VMA_STATE_PLAYING;

    return VMA_SUCCESS;
}

VMAResult vma_player_stop(
    VMAPlayer* player
) {
    if (!vma_player_valid(player))
        return VMA_ERROR_INVALID_ARGUMENT;

    player->cursor = 0.0;
    player->state = VMA_STATE_STOPPED;

    player->delay = 0.0;
    player->delay_frames = 0;

    return VMA_SUCCESS;
}

VMAResult vma_player_restart(
    VMAPlayer* player
) {
    if (!vma_player_has_audio(player))
        return VMA_ERROR_INVALID_STATE;

    player->cursor = 0.0;
    player->state = VMA_STATE_PLAYING;

    player->delay = 0.0;
    player->delay_frames = 0;

    return VMA_SUCCESS;
}

VMAResult vma_player_seek(
    VMAPlayer* player,
    double seconds
) {
    if (!vma_player_has_audio(player))
        return VMA_ERROR_INVALID_STATE;

    if (!isfinite(seconds))
        return VMA_ERROR_INVALID_ARGUMENT;

    double duration =
        (double)player->total_frames /
        player->config.sample_rate;

    if (seconds < 0.0)
        seconds = 0.0;

    if (seconds > duration)
        seconds = duration;

    player->cursor =
        seconds * player->config.sample_rate;

    if (player->cursor >= player->total_frames)
        player->state = VMA_STATE_FINISHED;

    return VMA_SUCCESS;
}

VMAResult vma_player_set_volume(
    VMAPlayer* player,
    float volume
) {
    if (!vma_player_valid(player) || !isfinite(volume))
        return VMA_ERROR_INVALID_ARGUMENT;

    player->volume = vma_player_clamp(
        volume, 0.0f, 1.0f
    );

    return VMA_SUCCESS;
}

float vma_player_get_volume(
    const VMAPlayer* player
) {
    return player != NULL ? player->volume : 0.0f;
}

VMAResult vma_player_set_pan(
    VMAPlayer* player,
    float pan
) {
    if (!vma_player_valid(player) || !isfinite(pan))
        return VMA_ERROR_INVALID_ARGUMENT;

    player->pan = vma_player_clamp(
        pan, -1.0f, 1.0f
    );

    return VMA_SUCCESS;
}

float vma_player_get_pan(
    const VMAPlayer* player
) {
    return player != NULL ? player->pan : 0.0f;
}

VMAResult vma_player_set_pitch(
    VMAPlayer* player,
    float pitch
) {
    if (!vma_player_valid(player) ||
        !isfinite(pitch) ||
        pitch <= 0.0f)
        return VMA_ERROR_INVALID_ARGUMENT;

    player->pitch = pitch;

    return VMA_SUCCESS;
}

float vma_player_get_pitch(
    const VMAPlayer* player
) {
    return player != NULL ? player->pitch : 0.0f;
}

VMAResult vma_player_set_looping(
    VMAPlayer* player,
    bool looping
) {
    if (!vma_player_valid(player))
        return VMA_ERROR_INVALID_ARGUMENT;

    player->looping = looping;

    return VMA_SUCCESS;
}

bool vma_player_is_looping(
    const VMAPlayer* player
) {
    return player != NULL && player->looping;
}

VMAPlaybackState vma_player_get_state(
    const VMAPlayer* player
) {
    return player != NULL
        ? player->state
        : VMA_STATE_STOPPED;
}

bool vma_player_is_playing(
    const VMAPlayer* player
) {
    return player != NULL &&
           player->state == VMA_STATE_PLAYING;
}

double vma_player_get_position(
    const VMAPlayer* player
) {
    if (player == NULL || player->config.sample_rate == 0)
        return 0.0;

    return player->cursor / player->config.sample_rate;
}

double vma_player_get_duration(
    const VMAPlayer* player
) {
    if (player == NULL || player->config.sample_rate == 0)
        return 0.0;

    return (double)player->total_frames /
           player->config.sample_rate;
}

VMAResult vma_player_get_info(
    const VMAPlayer* player,
    VMAPlayerInfo* info
) {
    if (player == NULL || info == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    info->total_frames = player->total_frames;
    info->position_frames = (uint64_t)player->cursor;
    info->sample_rate = player->config.sample_rate;
    info->channels = player->config.channels;
    info->duration = vma_player_get_duration(player);
    info->position = vma_player_get_position(player);
    info->volume = player->volume;
    info->pan = player->pan;
    info->pitch = player->pitch;
    info->looping = player->looping;
    info->state = player->state;

    return VMA_SUCCESS;
}

VMAResult vma_player_set_finished_callback(
    VMAPlayer* player,
    VMAPlayerFinishedCallback callback,
    void* user_data
) {
    if (player == NULL)
        return VMA_ERROR_INVALID_ARGUMENT;

    player->finished_callback = callback;
    player->finished_user_data = user_data;

    return VMA_SUCCESS;
}

VMAResult vma_player_set_interpolation(
    VMAPlayer* player,
    VMAInterpolation interpolation
) {
    if (!vma_player_valid(player) || interpolation < VMA_INTERPOLATION_NEAREST || interpolation > VMA_INTERPOLATION_CUBIC)
        return VMA_ERROR_INVALID_ARGUMENT;

    player->interpolation = interpolation;

    return VMA_SUCCESS;
}

VMAResult vma_player_set_pan_law(
    VMAPlayer* player,
    VMAPanLaw law
) {
    if (!vma_player_valid(player) || law < VMA_PAN_LINEAR || law > VMA_PAN_EQUAL_POWER)
        return VMA_ERROR_INVALID_ARGUMENT;

    player->pan_law = law;

    return VMA_SUCCESS;
}

VMAResult vma_player_fade(
    VMAPlayer* player,
    float target,
    double seconds,
    VMAFadeEnd end
) {
    if (!vma_player_valid(player) || !isfinite(target) || !isfinite(seconds))
        return VMA_ERROR_INVALID_ARGUMENT;

    target = vma_player_clamp(target, 0.0f, 1.0f);

    if (seconds <= 0.0) {
        player->fade_gain = target;
        player->fade_target = target;
        player->fading = false;

        if (end == VMA_FADE_PAUSE && player->state == VMA_STATE_PLAYING)
            player->state = VMA_STATE_PAUSED;
        else if (end == VMA_FADE_STOP) {
            player->state = VMA_STATE_STOPPED;
            player->cursor = 0.0;
        }

        if (end != VMA_FADE_CONTINUE) {
            player->fade_gain = 1.0f;
            player->fade_target = 1.0f;
        }

        return VMA_SUCCESS;
    }

    player->fade_from = player->fade_gain;
    player->fade_target = target;
    player->fade_seconds = seconds;
    player->fade_elapsed = 0.0;
    player->fade_end = end;
    player->fading = true;

    return VMA_SUCCESS;
}

float vma_player_get_fade(
    const VMAPlayer* player
) {
    return player != NULL ? player->fade_gain : 0.0f;
}

VMAResult vma_player_set_loop_points(
    VMAPlayer* player,
    double start,
    double end
) {
    if (!vma_player_valid(player) || !isfinite(start) || !isfinite(end))
        return VMA_ERROR_INVALID_ARGUMENT;

    double rate = (double)player->config.sample_rate;
    double total = (double)player->total_frames;
    double from = start * rate;
    double to = end * rate;

    if (from < 0.0)
        from = 0.0;

    if (to <= 0.0 || to > total)
        to = 0.0;

    if (to != 0.0 && to <= from)
        return VMA_ERROR_INVALID_ARGUMENT;

    if (from >= total)
        return VMA_ERROR_INVALID_ARGUMENT;

    player->loop_start = from;
    player->loop_end = to;

    return VMA_SUCCESS;
}

VMAResult vma_player_play_delayed(
    VMAPlayer* player,
    double seconds
) {
    if (!vma_player_has_audio(player) || !isfinite(seconds))
        return VMA_ERROR_INVALID_ARGUMENT;

    if (player->state == VMA_STATE_FINISHED)
        player->cursor = 0.0;

    player->delay = seconds > 0.0 ? seconds : 0.0;
    player->delay_frames = 0;
    player->state = VMA_STATE_PLAYING;

    return VMA_SUCCESS;
}

float vma_player_get_peak(
    const VMAPlayer* player
) {
    return player != NULL ? player->peak : 0.0f;
}

static void vma_player_gains(
    const VMAPlayer* player,
    float gain,
    float* left,
    float* right
) {
    if (player->pan_law == VMA_PAN_EQUAL_POWER) {
        float angle = (player->pan + 1.0f) * 0.78539816f;
        *left = gain * cosf(angle) * 1.41421356f;
        *right = gain * sinf(angle) * 1.41421356f;
        return;
    }

    *left = gain;
    *right = gain;

    if (player->pan > 0.0f)
        *left *= 1.0f - player->pan;
    else if (player->pan < 0.0f)
        *right *= 1.0f + player->pan;
}

static void vma_player_step_fade(
    VMAPlayer* player,
    double dt
) {
    if (!player->fading)
        return;

    player->fade_elapsed += dt;

    if (player->fade_elapsed >= player->fade_seconds - 1e-9) {
        player->fade_gain = player->fade_target;
        player->fading = false;

        if (player->fade_end == VMA_FADE_PAUSE)
            player->state = VMA_STATE_PAUSED;
        else if (player->fade_end == VMA_FADE_STOP) {
            player->state = VMA_STATE_STOPPED;
            player->cursor = 0.0;
        }

        if (player->fade_end != VMA_FADE_CONTINUE) {
            player->fade_gain = 1.0f;
            player->fade_target = 1.0f;
        }

        player->fade_end = VMA_FADE_CONTINUE;
        return;
    }

    float t = (float)(player->fade_elapsed / player->fade_seconds);
    player->fade_gain = player->fade_from + (player->fade_target - player->fade_from) * t;
}

VMAResult vma_player_process(
    VMAPlayer* player,
    float* output,
    uint32_t frames,
    uint32_t output_channels,
    uint32_t output_sample_rate
) {
    if (player == NULL || output == NULL ||
        output_channels == 0 ||
        output_channels > 32 ||
        output_sample_rate == 0)
        return VMA_ERROR_INVALID_ARGUMENT;

    if ((size_t)frames > SIZE_MAX / output_channels)
        return VMA_ERROR_INVALID_ARGUMENT;

    size_t count = (size_t)frames * output_channels;

    if (count > SIZE_MAX / sizeof(float))
        return VMA_ERROR_INVALID_ARGUMENT;

    memset(output, 0, count * sizeof(float));
    player->peak = 0.0f;

    if (player->state != VMA_STATE_PLAYING)
        return VMA_SUCCESS;

    if (!vma_player_has_audio(player))
        return VMA_ERROR_INVALID_STATE;

    double step =
        ((double)player->config.sample_rate /
         (double)output_sample_rate) *
        player->pitch;

    double dt = 1.0 / (double)output_sample_rate;
    double total = (double)player->total_frames;
    float peak = 0.0f;

    for (uint32_t i = 0; i < frames; ++i) {
        if (player->state != VMA_STATE_PLAYING)
            break;

        if (player->delay > 0.0) {
            player->delay_frames = (uint64_t)llround(player->delay * (double)output_sample_rate);
            player->delay = 0.0;
        }

        if (player->delay_frames > 0) {
            player->delay_frames--;
            continue;
        }

        if (player->looping) {
            double end = vma_player_region_end(player);
            double start = vma_player_region_start(player);

            if (player->cursor >= end)
                player->cursor = start + fmod(player->cursor - start, end - start);
        } else if (player->cursor >= total) {
            vma_player_finish(player);
            break;
        }

        float left_gain;
        float right_gain;
        vma_player_gains(player, player->volume * player->fade_gain, &left_gain, &right_gain);

        float left = vma_player_interpolate(player, 0);

        float right = player->config.channels > 1
            ? vma_player_interpolate(player, 1)
            : left;

        size_t offset = (size_t)i * output_channels;

        if (output_channels == 1) {
            output[offset] =
                (left * left_gain + right * right_gain) * 0.5f;
        } else {
            output[offset] = left * left_gain;
            output[offset + 1] = right * right_gain;

            for (uint32_t c = 2; c < output_channels; ++c) {
                uint32_t source_channel =
                    c < player->config.channels ? c : 0;

                output[offset + c] =
                    vma_player_interpolate(
                        player,
                        source_channel
                    ) * player->volume * player->fade_gain;
            }
        }

        for (uint32_t c = 0; c < output_channels; ++c) {
            float magnitude = fabsf(output[offset + c]);
            if (magnitude > peak)
                peak = magnitude;
        }

        player->cursor += step;
        vma_player_step_fade(player, dt);
    }

    player->peak = peak;

    return VMA_SUCCESS;
}

const float* vma_player_samples(
    const VMAPlayer* player
) {
    return player != NULL ? player->samples : NULL;
}
