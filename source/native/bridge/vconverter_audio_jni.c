#include <jni.h>
#include <stdint.h>
#include <math.h>

#include "vminiaudio/VAudio.h"

#define VCA_FUNCTION(name) Java_com_vconvertercrew_vconverter_audio_VMiniAudioNative_##name

static VMAContext* vca_context(jlong handle) {
    return (VMAContext*)(intptr_t)handle;
}

static VMAAudio* vca_audio(jlong handle) {
    return (VMAAudio*)(intptr_t)handle;
}

JNIEXPORT jstring JNICALL VCA_FUNCTION(version)(JNIEnv* env, jclass type) {
    (void)type;
    return (*env)->NewStringUTF(env, vma_get_version());
}

JNIEXPORT jlong JNICALL VCA_FUNCTION(init)(JNIEnv* env, jclass type, jint rate, jint channels) {
    (void)env;
    (void)type;
    VMAConfig config;
    vma_config_init(&config);
    config.sample_rate = (uint32_t)rate;
    config.channels = (uint32_t)channels;
    config.enable_logging = false;
    VMAContext* context = NULL;
    if (vma_init(&context, &config) != VMA_SUCCESS)
        return 0;
    return (jlong)(intptr_t)context;
}

JNIEXPORT void JNICALL VCA_FUNCTION(shutdown)(JNIEnv* env, jclass type, jlong context) {
    (void)env;
    (void)type;
    vma_shutdown(vca_context(context));
}

JNIEXPORT jlong JNICALL VCA_FUNCTION(createPcm)(JNIEnv* env, jclass type, jlong context, jshortArray samples, jint frames, jint channels, jint rate) {
    (void)type;
    if (samples == NULL || frames <= 0 || channels <= 0)
        return 0;
    jsize length = (*env)->GetArrayLength(env, samples);
    if ((jlong)frames * channels > length)
        return 0;
    jshort* data = (*env)->GetShortArrayElements(env, samples, NULL);
    if (data == NULL)
        return 0;
    VMAAudio* audio = NULL;
    VMAResult result = vma_audio_create_pcm(vca_context(context), data, (uint64_t)frames, (uint32_t)channels, (uint32_t)rate, VMA_SAMPLE_S16, &audio);
    (*env)->ReleaseShortArrayElements(env, samples, data, JNI_ABORT);
    return result == VMA_SUCCESS ? (jlong)(intptr_t)audio : 0;
}

JNIEXPORT jlong JNICALL VCA_FUNCTION(loadMemory)(JNIEnv* env, jclass type, jlong context, jbyteArray bytes) {
    (void)type;
    if (bytes == NULL)
        return 0;
    jsize length = (*env)->GetArrayLength(env, bytes);
    jbyte* data = (*env)->GetByteArrayElements(env, bytes, NULL);
    if (data == NULL)
        return 0;
    VMAAudio* audio = NULL;
    VMAResult result = vma_audio_load_memory(vca_context(context), data, (size_t)length, &audio);
    (*env)->ReleaseByteArrayElements(env, bytes, data, JNI_ABORT);
    return result == VMA_SUCCESS ? (jlong)(intptr_t)audio : 0;
}

JNIEXPORT void JNICALL VCA_FUNCTION(destroy)(JNIEnv* env, jclass type, jlong audio) {
    (void)env;
    (void)type;
    vma_audio_destroy(vca_audio(audio));
}

JNIEXPORT jint JNICALL VCA_FUNCTION(play)(JNIEnv* env, jclass type, jlong audio) {
    (void)env;
    (void)type;
    return (jint)vma_audio_play(vca_audio(audio));
}

JNIEXPORT jint JNICALL VCA_FUNCTION(pause)(JNIEnv* env, jclass type, jlong audio) {
    (void)env;
    (void)type;
    return (jint)vma_audio_pause(vca_audio(audio));
}

JNIEXPORT jint JNICALL VCA_FUNCTION(stop)(JNIEnv* env, jclass type, jlong audio) {
    (void)env;
    (void)type;
    return (jint)vma_audio_stop(vca_audio(audio));
}

JNIEXPORT jint JNICALL VCA_FUNCTION(seek)(JNIEnv* env, jclass type, jlong audio, jdouble seconds) {
    (void)env;
    (void)type;
    return (jint)vma_audio_seek(vca_audio(audio), seconds);
}

JNIEXPORT jdouble JNICALL VCA_FUNCTION(position)(JNIEnv* env, jclass type, jlong audio) {
    (void)env;
    (void)type;
    return vma_audio_get_position(vca_audio(audio));
}

JNIEXPORT jdouble JNICALL VCA_FUNCTION(duration)(JNIEnv* env, jclass type, jlong audio) {
    (void)env;
    (void)type;
    return vma_audio_get_duration(vca_audio(audio));
}

JNIEXPORT jint JNICALL VCA_FUNCTION(state)(JNIEnv* env, jclass type, jlong audio) {
    (void)env;
    (void)type;
    return (jint)vma_audio_get_state(vca_audio(audio));
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setVolume)(JNIEnv* env, jclass type, jlong audio, jfloat volume) {
    (void)env;
    (void)type;
    return (jint)vma_audio_set_volume(vca_audio(audio), volume);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setPitch)(JNIEnv* env, jclass type, jlong audio, jfloat pitch) {
    (void)env;
    (void)type;
    return (jint)vma_audio_set_pitch(vca_audio(audio), pitch);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setLooping)(JNIEnv* env, jclass type, jlong audio, jboolean looping) {
    (void)env;
    (void)type;
    return (jint)vma_audio_set_looping(vca_audio(audio), looping == JNI_TRUE);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setMasterVolume)(JNIEnv* env, jclass type, jlong context, jfloat volume) {
    (void)env;
    (void)type;
    return (jint)vma_set_master_volume(vca_context(context), volume);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(render)(JNIEnv* env, jclass type, jlong context, jfloatArray output, jint frames) {
    (void)type;
    VMAContext* handle = vca_context(context);
    if (output == NULL || frames <= 0)
        return (jint)VMA_ERROR_INVALID_ARGUMENT;
    jsize length = (*env)->GetArrayLength(env, output);
    if ((jlong)frames * (jlong)vma_get_channels(handle) > length)
        return (jint)VMA_ERROR_INVALID_ARGUMENT;
    jfloat* data = (jfloat*)(*env)->GetPrimitiveArrayCritical(env, output, NULL);
    if (data == NULL)
        return (jint)VMA_ERROR_OUT_OF_MEMORY;
    VMAResult result = vma_render(handle, data, (uint32_t)frames);
    (*env)->ReleasePrimitiveArrayCritical(env, output, data, 0);
    return (jint)result;
}

JNIEXPORT jshortArray JNICALL VCA_FUNCTION(decode)(JNIEnv* env, jclass type, jbyteArray bytes, jintArray info) {
    (void)type;
    if (bytes == NULL || info == NULL || (*env)->GetArrayLength(env, info) < 2)
        return NULL;
    jsize length = (*env)->GetArrayLength(env, bytes);
    jbyte* data = (*env)->GetByteArrayElements(env, bytes, NULL);
    if (data == NULL)
        return NULL;
    float* samples = NULL;
    uint64_t frames = 0;
    uint32_t channels = 0;
    uint32_t rate = 0;
    VMAResult result = vma_decode_memory(data, (size_t)length, &samples, &frames, &channels, &rate);
    (*env)->ReleaseByteArrayElements(env, bytes, data, JNI_ABORT);
    if (result != VMA_SUCCESS || samples == NULL)
        return NULL;
    uint64_t count = frames * channels;
    if (count > 0x7FFFFFFF) {
        vma_free(samples);
        return NULL;
    }
    jshortArray out = (*env)->NewShortArray(env, (jsize)count);
    if (out != NULL) {
        jshort* target = (*env)->GetShortArrayElements(env, out, NULL);
        if (target != NULL) {
            for (uint64_t i = 0; i < count; ++i) {
                float s = samples[i] * 32767.0f;
                if (s > 32767.0f)
                    s = 32767.0f;
                else if (s < -32768.0f)
                    s = -32768.0f;
                target[i] = (jshort)lrintf(s);
            }
            (*env)->ReleaseShortArrayElements(env, out, target, 0);
        }
        jint meta[2] = { (jint)channels, (jint)rate };
        (*env)->SetIntArrayRegion(env, info, 0, 2, meta);
    }
    vma_free(samples);
    return out;
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setPan)(JNIEnv* env, jclass type, jlong audio, jfloat pan) {
    (void)env;
    (void)type;
    return (jint)vma_audio_set_pan(vca_audio(audio), pan);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(fade)(JNIEnv* env, jclass type, jlong audio, jfloat target, jdouble seconds, jint end) {
    (void)env;
    (void)type;
    return (jint)vma_audio_fade(vca_audio(audio), target, seconds, (VMAFadeEnd)end);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setLoopPoints)(JNIEnv* env, jclass type, jlong audio, jdouble start, jdouble end) {
    (void)env;
    (void)type;
    return (jint)vma_audio_set_loop_points(vca_audio(audio), start, end);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(playDelayed)(JNIEnv* env, jclass type, jlong audio, jdouble seconds) {
    (void)env;
    (void)type;
    return (jint)vma_audio_play_delayed(vca_audio(audio), seconds);
}

JNIEXPORT jfloat JNICALL VCA_FUNCTION(peak)(JNIEnv* env, jclass type, jlong audio) {
    (void)env;
    (void)type;
    return vma_audio_get_peak(vca_audio(audio));
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setBus)(JNIEnv* env, jclass type, jlong audio, jint bus) {
    (void)env;
    (void)type;
    return bus < 0 ? (jint)VMA_ERROR_INVALID_ARGUMENT : (jint)vma_audio_set_bus(vca_audio(audio), (uint32_t)bus);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(busVolume)(JNIEnv* env, jclass type, jlong context, jint bus, jfloat volume) {
    (void)env;
    (void)type;
    return bus < 0 ? (jint)VMA_ERROR_INVALID_ARGUMENT : (jint)vma_bus_set_volume(vca_context(context), (uint32_t)bus, volume);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(busMuted)(JNIEnv* env, jclass type, jlong context, jint bus, jboolean muted) {
    (void)env;
    (void)type;
    return bus < 0 ? (jint)VMA_ERROR_INVALID_ARGUMENT : (jint)vma_bus_set_muted(vca_context(context), (uint32_t)bus, muted == JNI_TRUE);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(busFilters)(JNIEnv* env, jclass type, jlong context, jint bus, jfloat lowpass, jfloat highpass) {
    (void)env;
    (void)type;
    if (bus < 0)
        return (jint)VMA_ERROR_INVALID_ARGUMENT;
    VMAResult result = vma_bus_set_lowpass(vca_context(context), (uint32_t)bus, lowpass);
    if (result != VMA_SUCCESS)
        return (jint)result;
    return (jint)vma_bus_set_highpass(vca_context(context), (uint32_t)bus, highpass);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(busEcho)(JNIEnv* env, jclass type, jlong context, jint bus, jfloat seconds, jfloat feedback, jfloat mix) {
    (void)env;
    (void)type;
    return bus < 0 ? (jint)VMA_ERROR_INVALID_ARGUMENT : (jint)vma_bus_set_echo(vca_context(context), (uint32_t)bus, seconds, feedback, mix);
}

JNIEXPORT jfloat JNICALL VCA_FUNCTION(busPeak)(JNIEnv* env, jclass type, jlong context, jint bus) {
    (void)env;
    (void)type;
    return bus < 0 ? 0.0f : vma_bus_get_peak(vca_context(context), (uint32_t)bus);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setInterpolation)(JNIEnv* env, jclass type, jlong context, jint mode) {
    (void)env;
    (void)type;
    return (jint)vma_set_interpolation(vca_context(context), (VMAInterpolation)mode);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setPanLaw)(JNIEnv* env, jclass type, jlong context, jint law) {
    (void)env;
    (void)type;
    return (jint)vma_set_pan_law(vca_context(context), (VMAPanLaw)law);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(setLimiter)(JNIEnv* env, jclass type, jlong context, jboolean enabled, jfloat ceiling) {
    (void)env;
    (void)type;
    return (jint)vma_set_limiter(vca_context(context), enabled == JNI_TRUE, ceiling);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(meter)(JNIEnv* env, jclass type, jlong context, jfloatArray out) {
    (void)type;
    if (out == NULL || (*env)->GetArrayLength(env, out) < 5)
        return (jint)VMA_ERROR_INVALID_ARGUMENT;
    VMAMeter meter;
    VMAResult result = vma_get_meter(vca_context(context), &meter);
    if (result != VMA_SUCCESS)
        return (jint)result;
    jfloat values[5] = { meter.peak_left, meter.peak_right, meter.rms_left, meter.rms_right, meter.gain_reduction };
    (*env)->SetFloatArrayRegion(env, out, 0, 5, values);
    return (jint)VMA_SUCCESS;
}

JNIEXPORT jdouble JNICALL VCA_FUNCTION(time)(JNIEnv* env, jclass type, jlong context) {
    (void)env;
    (void)type;
    return vma_get_time(vca_context(context));
}
