#include <jni.h>
#include <stdint.h>

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
