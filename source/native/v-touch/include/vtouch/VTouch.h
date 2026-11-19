#ifndef VTOUCH_VTOUCH_H
#define VTOUCH_VTOUCH_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32)
    #if defined(VTOUCH_BUILD_DLL)
        #define VTOUCH_API __declspec(dllexport)
    #else
        #define VTOUCH_API
    #endif
#elif defined(__GNUC__) || defined(__clang__)
    #define VTOUCH_API __attribute__((visibility("default")))
#else
    #define VTOUCH_API
#endif

#define VTOUCH_VERSION_STRING "1.0.0"
#define VTOUCH_MAX_LANES 9
#define VTOUCH_MAX_POINTERS 10
#define VTOUCH_QUEUE_SIZE 128

typedef struct VTouchEngine VTouchEngine;

typedef enum VTouchLayout {
    VTOUCH_LAYOUT_HITBOX = 0,
    VTOUCH_LAYOUT_PAD = 1,
    VTOUCH_LAYOUT_OFF = 2
} VTouchLayout;

typedef enum VTouchAction {
    VTOUCH_ACTION_DOWN = 0,
    VTOUCH_ACTION_MOVE = 1,
    VTOUCH_ACTION_UP = 2,
    VTOUCH_ACTION_CANCEL = 3
} VTouchAction;

typedef enum VTouchFeature {
    VTOUCH_FEATURE_LANES = 1 << 0,
    VTOUCH_FEATURE_TAP = 1 << 1,
    VTOUCH_FEATURE_STEPS = 1 << 2,
    VTOUCH_FEATURE_SWIPE_BACK = 1 << 3,
    VTOUCH_FEATURE_PINCH = 1 << 4,
    VTOUCH_FEATURE_LONG_PRESS = 1 << 5,
    VTOUCH_FEATURE_FLICK = 1 << 6
} VTouchFeature;

typedef enum VTouchEventType {
    VTOUCH_EVENT_NONE = 0,
    VTOUCH_EVENT_LANE_PRESS,
    VTOUCH_EVENT_LANE_RELEASE,
    VTOUCH_EVENT_TAP,
    VTOUCH_EVENT_DOUBLE_TAP,
    VTOUCH_EVENT_LONG_PRESS,
    VTOUCH_EVENT_STEP,
    VTOUCH_EVENT_SWIPE_BACK,
    VTOUCH_EVENT_ZOOM,
    VTOUCH_EVENT_PAN,
    VTOUCH_EVENT_DRAG_START,
    VTOUCH_EVENT_DRAG_END
} VTouchEventType;

typedef struct VTouchEvent {
    int32_t type;
    int32_t lane;
    float x;
    float y;
    float value;
    float dx;
    float dy;
} VTouchEvent;

typedef struct VTouchConfig {
    float density;
    float tap_slop_dp;
    float double_tap_ms;
    float long_press_ms;
    float swipe_back_dp;
    float step_px;
    float flick_dp_per_second;
    int32_t max_flick_steps;
    float pad_hit_scale;
    int32_t lanes;
    int32_t layout;
    uint32_t features;
} VTouchConfig;

VTOUCH_API const char* vtouch_version(void);
VTOUCH_API void vtouch_config_init(VTouchConfig* config);

VTOUCH_API VTouchEngine* vtouch_create(const VTouchConfig* config);
VTOUCH_API void vtouch_destroy(VTouchEngine* engine);
VTOUCH_API void vtouch_configure(VTouchEngine* engine, const VTouchConfig* config);
VTOUCH_API void vtouch_get_config(const VTouchEngine* engine, VTouchConfig* config);

VTOUCH_API void vtouch_set_viewport(VTouchEngine* engine, float width, float height);
VTOUCH_API void vtouch_set_step(VTouchEngine* engine, float pixels);
VTOUCH_API void vtouch_set_enabled(VTouchEngine* engine, int32_t enabled);

VTOUCH_API void vtouch_pointer(VTouchEngine* engine, int64_t id, int32_t action, float x, float y, double time_ms);
VTOUCH_API void vtouch_tick(VTouchEngine* engine, double time_ms);
VTOUCH_API void vtouch_reset(VTouchEngine* engine);

VTOUCH_API int32_t vtouch_poll(VTouchEngine* engine, VTouchEvent* event);
VTOUCH_API int32_t vtouch_pending(const VTouchEngine* engine);

VTOUCH_API int32_t vtouch_lane_held(const VTouchEngine* engine, int32_t lane);
VTOUCH_API int32_t vtouch_lane_at(const VTouchEngine* engine, float x, float y);
VTOUCH_API int32_t vtouch_lane_shape(const VTouchEngine* engine, int32_t lane, float* shape);
VTOUCH_API int32_t vtouch_pointer_count(const VTouchEngine* engine);
VTOUCH_API int32_t vtouch_is_dragging(const VTouchEngine* engine);
VTOUCH_API int32_t vtouch_is_pinching(const VTouchEngine* engine);

#ifdef __cplusplus
}
#endif

#endif
