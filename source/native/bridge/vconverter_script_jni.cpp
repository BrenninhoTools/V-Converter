#include <jni.h>

#include <string>

#include "vscript/VScript.h"

#define VCS_FUNCTION(name) Java_com_vconvertercrew_vconverter_script_VScriptNative_##name

namespace {

std::string text(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::string();
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring take(JNIEnv* env, char* result) {
    if (result == nullptr) return nullptr;
    jstring out = env->NewStringUTF(result);
    vscript_free(result);
    return out;
}

}

extern "C" {

JNIEXPORT jstring JNICALL VCS_FUNCTION(nativeVersion)(JNIEnv* env, jclass) {
    return env->NewStringUTF(vscript_version());
}

JNIEXPORT jstring JNICALL VCS_FUNCTION(nativeConvertLua)(JNIEnv* env, jclass, jstring source, jstring options) {
    std::string code = text(env, source);
    std::string json = text(env, options);
    return take(env, vscript_convert_lua(code.c_str(), json.c_str()));
}

JNIEXPORT jstring JNICALL VCS_FUNCTION(nativeGenerate)(JNIEnv* env, jclass, jstring spec) {
    std::string json = text(env, spec);
    return take(env, vscript_generate(json.c_str()));
}

JNIEXPORT jstring JNICALL VCS_FUNCTION(nativeBuiltinEvent)(JNIEnv* env, jclass, jstring name, jstring className, jstring id) {
    std::string n = text(env, name);
    std::string c = text(env, className);
    std::string i = text(env, id);
    return take(env, vscript_builtin_event(n.c_str(), c.c_str(), i.c_str()));
}

JNIEXPORT jstring JNICALL VCS_FUNCTION(nativeConvertData)(JNIEnv* env, jclass, jstring kind, jstring input) {
    std::string k = text(env, kind);
    std::string json = text(env, input);
    return take(env, vscript_convert_data(k.c_str(), json.c_str()));
}

}
