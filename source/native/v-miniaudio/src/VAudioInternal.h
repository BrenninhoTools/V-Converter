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

struct VMAAudio {
    VMAContext* context;
    VMAPlayer* player;
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
};

void vma_mutex_init(VMAMutex* mutex);
void vma_mutex_destroy(VMAMutex* mutex);
void vma_mutex_lock(VMAMutex* mutex);
void vma_mutex_unlock(VMAMutex* mutex);

void vma_context_release_audios(VMAContext* context);

#endif
