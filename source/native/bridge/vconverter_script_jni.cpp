#include <jni.h>

#include <string>
#include <vector>

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

JNIEXPORT jobjectArray JNICALL VCS_FUNCTION(nativeConvertCharts)(JNIEnv* env, jclass, jobjectArray difficulties, jobjectArray texts, jstring options) {
    jsize count = texts == nullptr ? 0 : env->GetArrayLength(texts);
    std::vector<std::string> names(static_cast<size_t>(count));
    std::vector<std::string> bodies(static_cast<size_t>(count));
    std::vector<const char*> namePointers(static_cast<size_t>(count));
    std::vector<const char*> bodyPointers(static_cast<size_t>(count));
    for (jsize i = 0; i < count; i++) {
        jstring name = static_cast<jstring>(env->GetObjectArrayElement(difficulties, i));
        jstring body = static_cast<jstring>(env->GetObjectArrayElement(texts, i));
        names[static_cast<size_t>(i)] = text(env, name);
        bodies[static_cast<size_t>(i)] = text(env, body);
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(body);
        namePointers[static_cast<size_t>(i)] = names[static_cast<size_t>(i)].c_str();
        bodyPointers[static_cast<size_t>(i)] = bodies[static_cast<size_t>(i)].c_str();
    }
    std::string json = text(env, options);
    char** files = nullptr;
    size_t fileCount = 0;
    char* header = vscript_convert_charts(namePointers.data(), bodyPointers.data(), static_cast<size_t>(count), json.c_str(), &files, &fileCount);
    jclass stringType = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(fileCount + 1), stringType, nullptr);
    if (out != nullptr) {
        jstring first = env->NewStringUTF(header ? header : "{}");
        env->SetObjectArrayElement(out, 0, first);
        env->DeleteLocalRef(first);
        for (size_t i = 0; i < fileCount; i++) {
            jstring file = env->NewStringUTF(files[i] ? files[i] : "");
            env->SetObjectArrayElement(out, static_cast<jsize>(i + 1), file);
            env->DeleteLocalRef(file);
        }
    }
    vscript_free(header);
    vscript_free_files(files, fileCount);
    return out;
}

}
