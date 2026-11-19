#include <jni.h>
#include <stdint.h>

#include "vtouch/VTouch.h"

#define VCT_FUNCTION(name) Java_com_vconvertercrew_vtouch_NativeTouch_##name

static VTouchEngine* vct_engine(jlong handle) {
    return (VTouchEngine*)(intptr_t)handle;
}

static void vct_config(VTouchConfig* config, jfloatArray values, JNIEnv* env) {
    vtouch_config_init(config);
    if (values == NULL || (*env)->GetArrayLength(env, values) < 12)
        return;
    jfloat v[12];
    (*env)->GetFloatArrayRegion(env, values, 0, 12, v);
    config->density = v[0];
    config->tap_slop_dp = v[1];
    config->double_tap_ms = v[2];
    config->long_press_ms = v[3];
    config->swipe_back_dp = v[4];
    config->step_px = v[5];
    config->flick_dp_per_second = v[6];
    config->max_flick_steps = (int32_t)v[7];
    config->pad_hit_scale = v[8];
    config->lanes = (int32_t)v[9];
    config->layout = (int32_t)v[10];
    config->features = (uint32_t)v[11];
}

JNIEXPORT jstring JNICALL VCT_FUNCTION(version)(JNIEnv* env, jclass type) {
    (void)type;
    return (*env)->NewStringUTF(env, vtouch_version());
}

JNIEXPORT jlong JNICALL VCT_FUNCTION(create)(JNIEnv* env, jclass type, jfloatArray values) {
    (void)type;
    VTouchConfig config;
    vct_config(&config, values, env);
    return (jlong)(intptr_t)vtouch_create(&config);
}

JNIEXPORT void JNICALL VCT_FUNCTION(destroy)(JNIEnv* env, jclass type, jlong engine) {
    (void)env;
    (void)type;
    vtouch_destroy(vct_engine(engine));
}

JNIEXPORT void JNICALL VCT_FUNCTION(configure)(JNIEnv* env, jclass type, jlong engine, jfloatArray values) {
    (void)type;
    VTouchConfig config;
    vct_config(&config, values, env);
    vtouch_configure(vct_engine(engine), &config);
}

JNIEXPORT void JNICALL VCT_FUNCTION(viewport)(JNIEnv* env, jclass type, jlong engine, jfloat width, jfloat height) {
    (void)env;
    (void)type;
    vtouch_set_viewport(vct_engine(engine), width, height);
}

JNIEXPORT void JNICALL VCT_FUNCTION(step)(JNIEnv* env, jclass type, jlong engine, jfloat pixels) {
    (void)env;
    (void)type;
    vtouch_set_step(vct_engine(engine), pixels);
}

JNIEXPORT void JNICALL VCT_FUNCTION(enabled)(JNIEnv* env, jclass type, jlong engine, jboolean enabled) {
    (void)env;
    (void)type;
    vtouch_set_enabled(vct_engine(engine), enabled == JNI_TRUE ? 1 : 0);
}

JNIEXPORT void JNICALL VCT_FUNCTION(pointer)(JNIEnv* env, jclass type, jlong engine, jlong id, jint action, jfloat x, jfloat y, jdouble time) {
    (void)env;
    (void)type;
    vtouch_pointer(vct_engine(engine), (int64_t)id, (int32_t)action, x, y, time);
}

JNIEXPORT void JNICALL VCT_FUNCTION(tick)(JNIEnv* env, jclass type, jlong engine, jdouble time) {
    (void)env;
    (void)type;
    vtouch_tick(vct_engine(engine), time);
}

JNIEXPORT void JNICALL VCT_FUNCTION(reset)(JNIEnv* env, jclass type, jlong engine) {
    (void)env;
    (void)type;
    vtouch_reset(vct_engine(engine));
}

JNIEXPORT jint JNICALL VCT_FUNCTION(poll)(JNIEnv* env, jclass type, jlong engine, jfloatArray out) {
    (void)type;
    VTouchEvent event;
    if (out == NULL || (*env)->GetArrayLength(env, out) < 6 || !vtouch_poll(vct_engine(engine), &event))
        return 0;
    jfloat values[6] = { (jfloat)event.lane, event.x, event.y, event.value, event.dx, event.dy };
    (*env)->SetFloatArrayRegion(env, out, 0, 6, values);
    return event.type;
}

JNIEXPORT jint JNICALL VCT_FUNCTION(held)(JNIEnv* env, jclass type, jlong engine, jint lane) {
    (void)env;
    (void)type;
    return vtouch_lane_held(vct_engine(engine), lane);
}

JNIEXPORT jint JNICALL VCT_FUNCTION(laneAt)(JNIEnv* env, jclass type, jlong engine, jfloat x, jfloat y) {
    (void)env;
    (void)type;
    return vtouch_lane_at(vct_engine(engine), x, y);
}

JNIEXPORT jboolean JNICALL VCT_FUNCTION(shape)(JNIEnv* env, jclass type, jlong engine, jint lane, jfloatArray out) {
    (void)type;
    float values[4];
    if (out == NULL || (*env)->GetArrayLength(env, out) < 4 || !vtouch_lane_shape(vct_engine(engine), lane, values))
        return JNI_FALSE;
    (*env)->SetFloatArrayRegion(env, out, 0, 4, values);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL VCT_FUNCTION(state)(JNIEnv* env, jclass type, jlong engine) {
    (void)env;
    (void)type;
    VTouchEngine* e = vct_engine(engine);
    return vtouch_pointer_count(e) | (vtouch_is_dragging(e) << 8) | (vtouch_is_pinching(e) << 9);
}
