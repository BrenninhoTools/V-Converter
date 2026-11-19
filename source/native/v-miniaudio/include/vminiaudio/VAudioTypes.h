#ifndef VMINIAUDIO_VAUDIOTYPES_H
#define VMINIAUDIO_VAUDIOTYPES_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define VMA_VERSION_MAJOR 0
#define VMA_VERSION_MINOR 2
#define VMA_VERSION_PATCH 0

#define VMA_VERSION_STRING "0.2.0"
#define VMA_NAME "V-MiniAudio"

#define VMA_DEFAULT_SAMPLE_RATE 48000
#define VMA_DEFAULT_CHANNELS 2
#define VMA_DEFAULT_BUFFER_FRAMES 512

#define VMA_MIN_VOLUME 0.0f
#define VMA_MAX_VOLUME 1.0f

#define VMA_MIN_PAN -1.0f
#define VMA_MAX_PAN 1.0f

#define VMA_MAX_BUSES 8
#define VMA_MAX_ECHO_SECONDS 2.0f

#if defined(_WIN32)
    #if defined(VMA_BUILD_DLL)
        #define VMA_API __declspec(dllexport)
    #elif defined(VMA_USE_DLL)
        #define VMA_API __declspec(dllimport)
    #else
        #define VMA_API
    #endif
#elif defined(__GNUC__) || defined(__clang__)
    #define VMA_API __attribute__((visibility("default")))
#else
    #define VMA_API
#endif

typedef enum VMAResult {
    VMA_SUCCESS = 0,
    VMA_ERROR_UNKNOWN = -1,
    VMA_ERROR_INVALID_ARGUMENT = -2,
    VMA_ERROR_OUT_OF_MEMORY = -3,
    VMA_ERROR_NOT_INITIALIZED = -4,
    VMA_ERROR_ALREADY_INITIALIZED = -5,
    VMA_ERROR_FILE_NOT_FOUND = -6,
    VMA_ERROR_FILE_READ = -7,
    VMA_ERROR_FILE_WRITE = -8,
    VMA_ERROR_UNSUPPORTED_FORMAT = -9,
    VMA_ERROR_INVALID_FORMAT = -10,
    VMA_ERROR_DEVICE_NOT_FOUND = -11,
    VMA_ERROR_DEVICE_INIT = -12,
    VMA_ERROR_DEVICE_LOST = -13,
    VMA_ERROR_PLAYBACK = -14,
    VMA_ERROR_DECODING = -15,
    VMA_ERROR_ENCODING = -16,
    VMA_ERROR_NOT_SUPPORTED = -17,
    VMA_ERROR_INVALID_STATE = -18,
    VMA_ERROR_END_OF_STREAM = -19
} VMAResult;

typedef enum VMAAudioFormat {
    VMA_FORMAT_UNKNOWN = 0,
    VMA_FORMAT_WAV,
    VMA_FORMAT_MP3,
    VMA_FORMAT_OGG,
    VMA_FORMAT_FLAC,
    VMA_FORMAT_PCM
} VMAAudioFormat;

typedef enum VMASampleFormat {
    VMA_SAMPLE_UNKNOWN = 0,
    VMA_SAMPLE_U8,
    VMA_SAMPLE_S16,
    VMA_SAMPLE_S24,
    VMA_SAMPLE_S32,
    VMA_SAMPLE_F32,
    VMA_SAMPLE_F64
} VMASampleFormat;

typedef enum VMAPlaybackState {
    VMA_STATE_STOPPED = 0,
    VMA_STATE_PLAYING,
    VMA_STATE_PAUSED,
    VMA_STATE_FINISHED
} VMAPlaybackState;

typedef enum VMALogLevel {
    VMA_LOG_NONE = 0,
    VMA_LOG_ERROR,
    VMA_LOG_WARNING,
    VMA_LOG_INFO,
    VMA_LOG_DEBUG,
    VMA_LOG_TRACE
} VMALogLevel;

typedef enum VMAInterpolation {
    VMA_INTERPOLATION_NEAREST = 0,
    VMA_INTERPOLATION_LINEAR,
    VMA_INTERPOLATION_CUBIC
} VMAInterpolation;

typedef enum VMAPanLaw {
    VMA_PAN_LINEAR = 0,
    VMA_PAN_EQUAL_POWER
} VMAPanLaw;

typedef enum VMAFadeEnd {
    VMA_FADE_CONTINUE = 0,
    VMA_FADE_PAUSE,
    VMA_FADE_STOP
} VMAFadeEnd;

typedef struct VMAMeter {
    float peak_left;
    float peak_right;
    float rms_left;
    float rms_right;
    float gain_reduction;
} VMAMeter;

typedef struct VMAContext VMAContext;
typedef struct VMAAudio VMAAudio;
typedef struct VMADevice VMADevice;

typedef struct VMAConfig {
    uint32_t sample_rate;
    uint32_t channels;
    uint32_t buffer_frames;
    VMASampleFormat sample_format;
    bool enable_logging;
    bool enable_low_latency;
} VMAConfig;

typedef struct VMAAudioInfo {
    VMAAudioFormat format;
    VMASampleFormat sample_format;
    uint32_t sample_rate;
    uint32_t channels;
    uint64_t total_frames;
    double duration;
    uint64_t file_size;
} VMAAudioInfo;

typedef struct VMADeviceInfo {
    char name[256];
    char id[256];
    uint32_t max_channels;
    uint32_t preferred_sample_rate;
    bool is_default;
} VMADeviceInfo;

typedef void (*VMALogCallback)(
    VMALogLevel level,
    const char* message,
    void* user_data
);

typedef void (*VMAFinishedCallback)(
    VMAAudio* audio,
    void* user_data
);

typedef void (*VMAAudioCallback)(
    float* output,
    uint32_t frames,
    uint32_t channels,
    void* user_data
);

#ifdef __cplusplus
}
#endif

#endif
