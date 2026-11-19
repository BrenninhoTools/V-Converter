#ifndef VMINIAUDIO_VAUDIOINTERNAL_H
#define VMINIAUDIO_VAUDIOINTERNAL_H

#include "vminiaudio/VAudio.h"
#include "vminiaudio/VAudioPlayer.h"

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
typedef CRITICAL_SECTION VMAMutex;
#else
#include <pthread.h>
typedef pthread_mutex_t VMAMutex;
#endif

typedef struct VMABiquad {
    bool enabled;
    float cutoff;
    float b0, b1, b2, a1, a2;
    float* state;
} VMABiquad;

typedef struct VMABus {
    float volume;
    bool muted;
    VMABiquad lowpass;
    VMABiquad highpass;
    float echo_seconds;
    float echo_feedback;
    float echo_mix;
    float* echo_buffer;
    size_t echo_frames;
    size_t echo_cursor;
    float peak;
    float* mix;
    size_t mix_size;
    bool used;
} VMABus;

struct VMAAudio {
    VMAContext* context;
    VMAPlayer* player;
    uint32_t bus;
    VMAAudioInfo info;
    VMAFinishedCallback finished_callback;
    void* finished_user_data;
    VMAAudio* next;
};

struct VMAContext {
    VMAConfig config;

    bool initialized;
    bool muted;

    float master_volume;

    VMALogLevel log_level;
    VMALogCallback log_callback;
    void* log_user_data;

    VMAAudioCallback audio_callback;
    void* audio_user_data;

    VMAMutex lock;
    VMAAudio* audios;
    uint32_t audio_count;

    float* scratch;
    size_t scratch_size;

    VMAInterpolation interpolation;
    VMAPanLaw pan_law;

    VMABus buses[VMA_MAX_BUSES];

    bool limiter_enabled;
    float limiter_ceiling;
    float limiter_gain;
    float limiter_release;

    VMAMeter meter;
    uint64_t rendered_frames;
};

void vma_mutex_init(VMAMutex* mutex);
void vma_mutex_destroy(VMAMutex* mutex);
void vma_mutex_lock(VMAMutex* mutex);
void vma_mutex_unlock(VMAMutex* mutex);

void vma_context_release_audios(VMAContext* context);

const float* vma_player_samples(const VMAPlayer* player);

void vma_buses_init(VMAContext* context);
void vma_buses_free(VMAContext* context);
bool vma_bus_needs_processing(const VMABus* bus);
VMAResult vma_bus_reserve(VMABus* bus, size_t count);
void vma_bus_process(VMABus* bus, float* buffer, uint32_t frames, uint32_t channels);
void vma_master_process(VMAContext* context, float* output, uint32_t frames);

#endif
