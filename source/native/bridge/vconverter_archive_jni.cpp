#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include "varchive/VArchive.h"

#define VCA_FUNCTION(name) Java_com_vconvertercrew_vconverter_archive_ArchiveNative_##name

namespace {

std::string bytes(JNIEnv* env, jbyteArray value) {
    if (value == nullptr) return std::string();
    jsize length = env->GetArrayLength(value);
    std::string out(static_cast<size_t>(length), '\0');
    if (length > 0) env->GetByteArrayRegion(value, 0, length, reinterpret_cast<jbyte*>(&out[0]));
    return out;
}

void raise(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/io/IOException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

varchive_reader* reader(jlong handle) {
    return reinterpret_cast<varchive_reader*>(static_cast<intptr_t>(handle));
}

varchive_writer* writer(jlong handle) {
    return reinterpret_cast<varchive_writer*>(static_cast<intptr_t>(handle));
}

varchive_stream* stream(jlong handle) {
    return reinterpret_cast<varchive_stream*>(static_cast<intptr_t>(handle));
}

jlong handle(void* pointer) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(pointer));
}

}

extern "C" {

JNIEXPORT jstring JNICALL VCA_FUNCTION(nativeVersion)(JNIEnv* env, jclass) {
    return env->NewStringUTF(varchive_version());
}

JNIEXPORT jlong JNICALL VCA_FUNCTION(nativeOpen)(JNIEnv* env, jclass, jbyteArray path) {
    std::string file = bytes(env, path);
    char error[256] = {0};
    varchive_reader* opened = varchive_reader_open(file.c_str(), error, sizeof(error));
    if (opened == nullptr) {
        raise(env, std::string("Could not open the ZIP: ") + error);
        return 0;
    }
    return handle(opened);
}

JNIEXPORT void JNICALL VCA_FUNCTION(nativeClose)(JNIEnv*, jclass, jlong archive) {
    varchive_reader_close(reader(archive));
}

JNIEXPORT jobjectArray JNICALL VCA_FUNCTION(nativeNames)(JNIEnv* env, jclass, jlong archive, jlongArray sizes, jintArray flags) {
    varchive_reader* source = reader(archive);
    jsize count = static_cast<jsize>(varchive_reader_count(source));
    jclass bytesType = env->FindClass("[B");
    jobjectArray names = env->NewObjectArray(count, bytesType, nullptr);
    if (names == nullptr) return nullptr;
    std::vector<jlong> sizeValues(static_cast<size_t>(count));
    std::vector<jint> flagValues(static_cast<size_t>(count));
    std::vector<char> name(4096);
    for (jsize i = 0; i < count; i++) {
        uint64_t size = 0;
        int directory = 0;
        int utf8 = 0;
        if (!varchive_reader_entry(source, i, name.data(), name.size(), &size, &directory, &utf8)) {
            name[0] = 0;
            flagValues[static_cast<size_t>(i)] = 4;
        } else {
            flagValues[static_cast<size_t>(i)] = (directory ? 1 : 0) | (utf8 ? 2 : 0);
        }
        sizeValues[static_cast<size_t>(i)] = static_cast<jlong>(size);
        jsize length = static_cast<jsize>(std::char_traits<char>::length(name.data()));
        jbyteArray entry = env->NewByteArray(length);
        if (entry == nullptr) return nullptr;
        env->SetByteArrayRegion(entry, 0, length, reinterpret_cast<const jbyte*>(name.data()));
        env->SetObjectArrayElement(names, i, entry);
        env->DeleteLocalRef(entry);
    }
    if (sizes != nullptr && env->GetArrayLength(sizes) >= count) env->SetLongArrayRegion(sizes, 0, count, sizeValues.data());
    if (flags != nullptr && env->GetArrayLength(flags) >= count) env->SetIntArrayRegion(flags, 0, count, flagValues.data());
    return names;
}

JNIEXPORT jint JNICALL VCA_FUNCTION(nativeCount)(JNIEnv*, jclass, jlong archive) {
    return static_cast<jint>(varchive_reader_count(reader(archive)));
}

JNIEXPORT jbyteArray JNICALL VCA_FUNCTION(nativeRead)(JNIEnv* env, jclass, jlong archive, jint index) {
    size_t size = 0;
    unsigned char* data = varchive_reader_read(reader(archive), index, &size);
    if (data == nullptr) {
        raise(env, "Could not read the ZIP entry.");
        return nullptr;
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(size));
    if (out != nullptr && size > 0) env->SetByteArrayRegion(out, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
    varchive_free(data);
    return out;
}

JNIEXPORT jboolean JNICALL VCA_FUNCTION(nativeExtract)(JNIEnv* env, jclass, jlong archive, jint index, jbyteArray path) {
    std::string file = bytes(env, path);
    return varchive_reader_extract(reader(archive), index, file.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL VCA_FUNCTION(nativeStreamOpen)(JNIEnv* env, jclass, jlong archive, jint index) {
    varchive_stream* opened = varchive_stream_open(reader(archive), index);
    if (opened == nullptr) raise(env, "Could not open the ZIP entry.");
    return handle(opened);
}

JNIEXPORT jint JNICALL VCA_FUNCTION(nativeStreamRead)(JNIEnv* env, jclass, jlong entry, jbyteArray buffer, jint offset, jint length) {
    if (length <= 0) return 0;
    size_t chunk = static_cast<size_t>(length > 262144 ? 262144 : length);
    std::vector<unsigned char> data(chunk);
    int64_t read = varchive_stream_read(stream(entry), data.data(), chunk);
    if (read < 0) {
        raise(env, "The ZIP entry is damaged.");
        return -1;
    }
    if (read == 0) return -1;
    env->SetByteArrayRegion(buffer, offset, static_cast<jsize>(read), reinterpret_cast<const jbyte*>(data.data()));
    return static_cast<jint>(read);
}

JNIEXPORT void JNICALL VCA_FUNCTION(nativeStreamClose)(JNIEnv*, jclass, jlong entry) {
    varchive_stream_close(stream(entry));
}

JNIEXPORT jlong JNICALL VCA_FUNCTION(nativeWriterOpen)(JNIEnv* env, jclass, jbyteArray path) {
    std::string file = bytes(env, path);
    char error[256] = {0};
    varchive_writer* opened = varchive_writer_open(file.c_str(), error, sizeof(error));
    if (opened == nullptr) {
        raise(env, std::string("Could not create the ZIP: ") + error);
        return 0;
    }
    return handle(opened);
}

JNIEXPORT jboolean JNICALL VCA_FUNCTION(nativeWriterAddBytes)(JNIEnv* env, jclass, jlong archive, jbyteArray name, jbyteArray data, jint length, jint level) {
    std::string entry = bytes(env, name);
    jbyte* values = data == nullptr ? nullptr : env->GetByteArrayElements(data, nullptr);
    int ok = varchive_writer_add_bytes(writer(archive), entry.c_str(), values, static_cast<size_t>(length), level);
    if (values != nullptr) env->ReleaseByteArrayElements(data, values, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL VCA_FUNCTION(nativeWriterAddFile)(JNIEnv* env, jclass, jlong archive, jbyteArray name, jbyteArray path, jint level) {
    std::string entry = bytes(env, name);
    std::string file = bytes(env, path);
    return varchive_writer_add_file(writer(archive), entry.c_str(), file.c_str(), level) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL VCA_FUNCTION(nativeWriterCopy)(JNIEnv* env, jclass, jlong archive, jbyteArray name, jlong source, jint index) {
    std::string entry = bytes(env, name);
    return varchive_writer_copy(writer(archive), entry.c_str(), reader(source), index) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL VCA_FUNCTION(nativeWriterError)(JNIEnv* env, jclass, jlong archive) {
    return env->NewStringUTF(varchive_writer_error(writer(archive)));
}

JNIEXPORT jboolean JNICALL VCA_FUNCTION(nativeWriterClose)(JNIEnv*, jclass, jlong archive) {
    return varchive_writer_close(writer(archive)) ? JNI_TRUE : JNI_FALSE;
}

}
