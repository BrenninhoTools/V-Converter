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

static float vma_player_sample(
    const VMAPlayer* player,
    uint64_t frame,
    uint32_t channel
) {
    if (frame >= player->total_frames)
        return 0.0f;

    if (channel >= player->config.channels)
        return 0.0f;

    size_t index =
        (size_t)frame * player->config.channels + channel;

    return player->samples[index];
}

static float vma_player_interpolate(
    const VMAPlayer* player,
    uint32_t channel
) {
    uint64_t frame0 = (uint64_t)player->cursor;
    uint64_t frame1 = frame0 + 1;

    if (frame1 >= player->total_frames) {
        frame1 = player->looping ? 0 : frame0;
    }

    float fraction =
        (float)(player->cursor - (double)frame0);

    float a = vma_player_sample(player, frame0, channel);
    float b = vma_player_sample(player, frame1, channel);

    return a + (b - a) * fraction;
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

    return VMA_SUCCESS;
}

VMAResult vma_player_restart(
    VMAPlayer* player
) {
    if (!vma_player_has_audio(player))
        return VMA_ERROR_INVALID_STATE;

    player->cursor = 0.0;
    player->state = VMA_STATE_PLAYING;

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

    if (player->state != VMA_STATE_PLAYING)
        return VMA_SUCCESS;

    if (!vma_player_has_audio(player))
        return VMA_ERROR_INVALID_STATE;

    double step =
        ((double)player->config.sample_rate /
         (double)output_sample_rate) *
        player->pitch;

    float left_gain = player->volume;
    float right_gain = player->volume;

    if (player->pan > 0.0f)
        left_gain *= 1.0f - player->pan;
    else if (player->pan < 0.0f)
        right_gain *= 1.0f + player->pan;

    for (uint32_t i = 0; i < frames; ++i) {
        if (player->cursor >= player->total_frames) {
            if (player->looping) {
                player->cursor = fmod(
                    player->cursor,
                    (double)player->total_frames
                );
            } else {
                vma_player_finish(player);
                break;
            }
        }

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
                    ) * player->volume;
            }
        }

        player->cursor += step;
    }

    return VMA_SUCCESS;
}
