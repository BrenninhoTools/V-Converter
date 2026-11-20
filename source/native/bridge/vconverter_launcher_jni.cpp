#include <jni.h>

#include <string>
#include <vector>

#include "vlauncher/VLauncher.h"

#define VCL_FUNCTION(name) Java_com_vconvertercrew_vconverter_launcher_NativeLauncher_##name

namespace {

constexpr int32_t kPathCapacity = 4096;
constexpr jint kFlagNewTask = 0x10000000;
constexpr jint kFlagResetTaskIfNeeded = 0x00200000;

std::string text(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::string();
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring pathOrNull(JNIEnv* env, int32_t result, const char* path) {
    return result == VLAUNCHER_OK ? env->NewStringUTF(path) : nullptr;
}

bool failed(JNIEnv* env) {
    if (!env->ExceptionCheck()) return false;
    env->ExceptionClear();
    return true;
}

jobject launchIntent(JNIEnv* env, jobject context, jstring packageName) {
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageManager = env->GetMethodID(contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    env->DeleteLocalRef(contextClass);
    if (failed(env) || getPackageManager == nullptr) return nullptr;
    jobject manager = env->CallObjectMethod(context, getPackageManager);
    if (failed(env) || manager == nullptr) return nullptr;
    jclass managerClass = env->GetObjectClass(manager);
    jmethodID getLaunchIntent = env->GetMethodID(managerClass, "getLaunchIntentForPackage", "(Ljava/lang/String;)Landroid/content/Intent;");
    env->DeleteLocalRef(managerClass);
    if (failed(env) || getLaunchIntent == nullptr) {
        env->DeleteLocalRef(manager);
        return nullptr;
    }
    jobject intent = env->CallObjectMethod(manager, getLaunchIntent, packageName);
    env->DeleteLocalRef(manager);
    if (failed(env)) return nullptr;
    return intent;
}

jint startActivity(JNIEnv* env, jobject context, jobject intent, jint flags) {
    jclass intentClass = env->GetObjectClass(intent);
    jmethodID addFlags = env->GetMethodID(intentClass, "addFlags", "(I)Landroid/content/Intent;");
    env->DeleteLocalRef(intentClass);
    if (failed(env) || addFlags == nullptr) return VLAUNCHER_FAILED;
    jobject same = env->CallObjectMethod(intent, addFlags, flags);
    if (failed(env)) return VLAUNCHER_FAILED;
    if (same != nullptr) env->DeleteLocalRef(same);
    jclass contextClass = env->GetObjectClass(context);
    jmethodID start = env->GetMethodID(contextClass, "startActivity", "(Landroid/content/Intent;)V");
    env->DeleteLocalRef(contextClass);
    if (failed(env) || start == nullptr) return VLAUNCHER_FAILED;
    env->CallVoidMethod(context, start, intent);
    return failed(env) ? VLAUNCHER_FAILED : VLAUNCHER_OK;
}

jint viewUri(JNIEnv* env, jobject context, const std::string& uri) {
    jclass uriClass = env->FindClass("android/net/Uri");
    if (failed(env) || uriClass == nullptr) return VLAUNCHER_UNSUPPORTED;
    jmethodID parse = env->GetStaticMethodID(uriClass, "parse", "(Ljava/lang/String;)Landroid/net/Uri;");
    if (failed(env) || parse == nullptr) {
        env->DeleteLocalRef(uriClass);
        return VLAUNCHER_FAILED;
    }
    jstring link = env->NewStringUTF(uri.c_str());
    jobject parsed = env->CallStaticObjectMethod(uriClass, parse, link);
    env->DeleteLocalRef(link);
    env->DeleteLocalRef(uriClass);
    if (failed(env) || parsed == nullptr) return VLAUNCHER_FAILED;
    jclass intentClass = env->FindClass("android/content/Intent");
    if (failed(env) || intentClass == nullptr) {
        env->DeleteLocalRef(parsed);
        return VLAUNCHER_FAILED;
    }
    jmethodID create = env->GetMethodID(intentClass, "<init>", "(Ljava/lang/String;Landroid/net/Uri;)V");
    if (failed(env) || create == nullptr) {
        env->DeleteLocalRef(intentClass);
        env->DeleteLocalRef(parsed);
        return VLAUNCHER_FAILED;
    }
    jstring action = env->NewStringUTF("android.intent.action.VIEW");
    jobject intent = env->NewObject(intentClass, create, action, parsed);
    env->DeleteLocalRef(action);
    env->DeleteLocalRef(intentClass);
    env->DeleteLocalRef(parsed);
    if (failed(env) || intent == nullptr) return VLAUNCHER_FAILED;
    jint result = startActivity(env, context, intent, kFlagNewTask);
    env->DeleteLocalRef(intent);
    return result;
}

}

extern "C" {

JNIEXPORT jstring JNICALL VCL_FUNCTION(version)(JNIEnv* env, jclass) {
    return env->NewStringUTF(vlauncher_version());
}

JNIEXPORT jstring JNICALL VCL_FUNCTION(resolve)(JNIEnv* env, jclass, jstring path) {
    std::string input = text(env, path);
    std::vector<char> out(kPathCapacity);
    return pathOrNull(env, vlauncher_resolve(input.c_str(), out.data(), kPathCapacity), out.data());
}

JNIEXPORT jstring JNICALL VCL_FUNCTION(search)(JNIEnv* env, jclass, jobjectArray roots) {
    if (roots == nullptr) return nullptr;
    jsize count = env->GetArrayLength(roots);
    std::vector<std::string> values;
    values.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; i++) {
        jstring root = static_cast<jstring>(env->GetObjectArrayElement(roots, i));
        values.push_back(text(env, root));
        if (root != nullptr) env->DeleteLocalRef(root);
    }
    std::vector<const char*> pointers;
    pointers.reserve(values.size());
    for (const std::string& value : values) pointers.push_back(value.c_str());
    std::vector<char> out(kPathCapacity);
    return pathOrNull(env, vlauncher_search(pointers.data(), static_cast<int32_t>(pointers.size()), out.data(), kPathCapacity), out.data());
}

JNIEXPORT jstring JNICALL VCL_FUNCTION(modsFolder)(JNIEnv* env, jclass, jstring executable) {
    std::string input = text(env, executable);
    std::vector<char> out(kPathCapacity);
    return pathOrNull(env, vlauncher_mods_folder(input.c_str(), out.data(), kPathCapacity), out.data());
}

JNIEXPORT jint JNICALL VCL_FUNCTION(installFolder)(JNIEnv* env, jclass, jstring source, jstring mods, jstring name) {
    std::string from = text(env, source);
    std::string to = text(env, mods);
    std::string folder = text(env, name);
    int32_t files = 0;
    int32_t result = vlauncher_install_folder(from.c_str(), to.c_str(), folder.c_str(), &files);
    return result == VLAUNCHER_OK ? files : -result;
}

JNIEXPORT jint JNICALL VCL_FUNCTION(launch)(JNIEnv* env, jclass, jstring executable) {
    std::string input = text(env, executable);
    return vlauncher_launch(input.c_str());
}

JNIEXPORT jint JNICALL VCL_FUNCTION(launchPackage)(JNIEnv* env, jclass, jobject context, jstring packageName) {
    if (context == nullptr || packageName == nullptr) return VLAUNCHER_INVALID;
    jobject intent = launchIntent(env, context, packageName);
    if (intent == nullptr) return VLAUNCHER_NOT_FOUND;
    jint result = startActivity(env, context, intent, kFlagNewTask | kFlagResetTaskIfNeeded);
    env->DeleteLocalRef(intent);
    return result;
}

JNIEXPORT jboolean JNICALL VCL_FUNCTION(installed)(JNIEnv* env, jclass, jobject context, jstring packageName) {
    if (context == nullptr || packageName == nullptr) return JNI_FALSE;
    jobject intent = launchIntent(env, context, packageName);
    if (intent == nullptr) return JNI_FALSE;
    env->DeleteLocalRef(intent);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL VCL_FUNCTION(openStore)(JNIEnv* env, jclass, jobject context, jstring packageName) {
    if (context == nullptr || packageName == nullptr) return VLAUNCHER_INVALID;
    std::string id = text(env, packageName);
    if (viewUri(env, context, "market://details?id=" + id) == VLAUNCHER_OK) return VLAUNCHER_OK;
    return viewUri(env, context, "https://play.google.com/store/apps/details?id=" + id);
}

}
