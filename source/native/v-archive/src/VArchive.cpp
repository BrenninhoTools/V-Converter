#include "varchive/VArchive.h"

#include "miniz.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <mutex>
#include <new>
#include <string>

struct varchive_reader {
    mz_zip_archive zip;
    std::mutex lock;
};

struct varchive_writer {
    mz_zip_archive zip;
    std::mutex lock;
    std::string error;
    bool open = false;
};

struct varchive_stream {
    varchive_reader* reader;
    mz_zip_reader_extract_iter_state* iter;
};

namespace {

void setError(char* error, size_t size, const char* message) {
    if (!error || size == 0) return;
    std::snprintf(error, size, "%s", message ? message : "Unknown error");
}

bool validIndex(varchive_reader* reader, int64_t index) {
    return reader && index >= 0 && index < static_cast<int64_t>(mz_zip_reader_get_num_files(&reader->zip));
}

int fail(varchive_writer* writer) {
    writer->error = mz_zip_get_error_string(mz_zip_get_last_error(&writer->zip));
    return 0;
}

struct Memory {
    const unsigned char* data;
    size_t size;
};

size_t readMemory(void* opaque, mz_uint64 offset, void* buffer, size_t size) {
    const Memory* memory = static_cast<const Memory*>(opaque);
    if (offset >= memory->size) return 0;
    size_t left = memory->size - static_cast<size_t>(offset);
    size_t count = size < left ? size : left;
    std::memcpy(buffer, memory->data + offset, count);
    return count;
}

bool addMemory(varchive_writer* writer, const char* name, const void* data, size_t size, mz_uint level) {
    Memory memory{static_cast<const unsigned char*>(data), size};
    MZ_TIME_T now = std::time(nullptr);
    return mz_zip_writer_add_read_buf_callback(&writer->zip, name, readMemory, &memory, size, &now, nullptr, 0, level | MZ_ZIP_FLAG_WRITE_HEADER_SET_SIZE, nullptr, 0, nullptr, 0);
}

mz_uint clampLevel(int level) {
    if (level < 0) return MZ_DEFAULT_LEVEL;
    if (level > 10) return 10;
    return static_cast<mz_uint>(level);
}

}

extern "C" {

const char* varchive_version(void) {
    return "1.0.0";
}

varchive_reader* varchive_reader_open(const char* path, char* error, size_t error_size) {
    if (!path) {
        setError(error, error_size, "No archive path");
        return nullptr;
    }
    varchive_reader* reader = new (std::nothrow) varchive_reader();
    if (!reader) {
        setError(error, error_size, "Out of memory");
        return nullptr;
    }
    mz_zip_zero_struct(&reader->zip);
    if (!mz_zip_reader_init_file_v2(&reader->zip, path, 0, 0, 0)) {
        setError(error, error_size, mz_zip_get_error_string(mz_zip_get_last_error(&reader->zip)));
        delete reader;
        return nullptr;
    }
    return reader;
}

void varchive_reader_close(varchive_reader* reader) {
    if (!reader) return;
    mz_zip_reader_end(&reader->zip);
    delete reader;
}

int64_t varchive_reader_count(varchive_reader* reader) {
    if (!reader) return 0;
    return static_cast<int64_t>(mz_zip_reader_get_num_files(&reader->zip));
}

int varchive_reader_entry(varchive_reader* reader, int64_t index, char* name, size_t name_size, uint64_t* size, int* is_directory, int* utf8) {
    if (!validIndex(reader, index)) return 0;
    std::lock_guard<std::mutex> guard(reader->lock);
    mz_zip_archive_file_stat stat;
    if (!mz_zip_reader_file_stat(&reader->zip, static_cast<mz_uint>(index), &stat)) return 0;
    if (name && name_size > 0) {
        mz_uint length = mz_zip_reader_get_filename(&reader->zip, static_cast<mz_uint>(index), name, static_cast<mz_uint>(name_size));
        if (length == 0) name[0] = 0;
    }
    if (size) *size = stat.m_uncomp_size;
    if (is_directory) *is_directory = stat.m_is_directory ? 1 : 0;
    if (utf8) *utf8 = (stat.m_bit_flag & (1 << 11)) ? 1 : 0;
    return 1;
}

unsigned char* varchive_reader_read(varchive_reader* reader, int64_t index, size_t* size) {
    if (size) *size = 0;
    if (!validIndex(reader, index)) return nullptr;
    std::lock_guard<std::mutex> guard(reader->lock);
    size_t length = 0;
    void* data = mz_zip_reader_extract_to_heap(&reader->zip, static_cast<mz_uint>(index), &length, 0);
    if (!data) {
        mz_zip_archive_file_stat stat;
        if (mz_zip_reader_file_stat(&reader->zip, static_cast<mz_uint>(index), &stat) && stat.m_uncomp_size == 0) {
            data = std::malloc(1);
            length = 0;
        }
    }
    if (size) *size = length;
    return static_cast<unsigned char*>(data);
}

int varchive_reader_extract(varchive_reader* reader, int64_t index, const char* path) {
    if (!validIndex(reader, index) || !path) return 0;
    std::lock_guard<std::mutex> guard(reader->lock);
    return mz_zip_reader_extract_to_file(&reader->zip, static_cast<mz_uint>(index), path, 0) ? 1 : 0;
}

varchive_stream* varchive_stream_open(varchive_reader* reader, int64_t index) {
    if (!validIndex(reader, index)) return nullptr;
    std::lock_guard<std::mutex> guard(reader->lock);
    mz_zip_reader_extract_iter_state* iter = mz_zip_reader_extract_iter_new(&reader->zip, static_cast<mz_uint>(index), 0);
    if (!iter) return nullptr;
    varchive_stream* stream = new (std::nothrow) varchive_stream();
    if (!stream) {
        mz_zip_reader_extract_iter_free(iter);
        return nullptr;
    }
    stream->reader = reader;
    stream->iter = iter;
    return stream;
}

int64_t varchive_stream_read(varchive_stream* stream, void* buffer, size_t size) {
    if (!stream || !buffer) return -1;
    if (size == 0) return 0;
    std::lock_guard<std::mutex> guard(stream->reader->lock);
    size_t read = mz_zip_reader_extract_iter_read(stream->iter, buffer, size);
    if (read == 0 && stream->iter->status < 0) return -1;
    return static_cast<int64_t>(read);
}

void varchive_stream_close(varchive_stream* stream) {
    if (!stream) return;
    {
        std::lock_guard<std::mutex> guard(stream->reader->lock);
        mz_zip_reader_extract_iter_free(stream->iter);
    }
    delete stream;
}

varchive_writer* varchive_writer_open(const char* path, char* error, size_t error_size) {
    if (!path) {
        setError(error, error_size, "No archive path");
        return nullptr;
    }
    varchive_writer* writer = new (std::nothrow) varchive_writer();
    if (!writer) {
        setError(error, error_size, "Out of memory");
        return nullptr;
    }
    mz_zip_zero_struct(&writer->zip);
    if (!mz_zip_writer_init_file_v2(&writer->zip, path, 0, 0)) {
        setError(error, error_size, mz_zip_get_error_string(mz_zip_get_last_error(&writer->zip)));
        delete writer;
        return nullptr;
    }
    writer->open = true;
    return writer;
}

int varchive_writer_add_bytes(varchive_writer* writer, const char* name, const void* data, size_t size, int level) {
    if (!writer || !name || (size > 0 && !data)) return 0;
    std::lock_guard<std::mutex> guard(writer->lock);
    if (!writer->open) return 0;
    if (!addMemory(writer, name, data, size, clampLevel(level))) return fail(writer);
    return 1;
}

int varchive_writer_add_file(varchive_writer* writer, const char* name, const char* path, int level) {
    if (!writer || !name || !path) return 0;
    std::lock_guard<std::mutex> guard(writer->lock);
    if (!writer->open) return 0;
    if (!mz_zip_writer_add_file(&writer->zip, name, path, nullptr, 0, clampLevel(level) | MZ_ZIP_FLAG_WRITE_HEADER_SET_SIZE)) return fail(writer);
    return 1;
}

int varchive_writer_copy(varchive_writer* writer, const char* name, varchive_reader* reader, int64_t index) {
    if (!writer || !name || !validIndex(reader, index)) return 0;
    std::lock_guard<std::mutex> guard(writer->lock);
    if (!writer->open) return 0;
    std::lock_guard<std::mutex> source(reader->lock);
    mz_uint file = static_cast<mz_uint>(index);
    mz_zip_archive_file_stat stat;
    if (!mz_zip_reader_file_stat(&reader->zip, file, &stat)) {
        writer->error = "The entry could not be read";
        return 0;
    }
    if (stat.m_is_encrypted || !stat.m_is_supported) {
        writer->error = "The entry is encrypted or uses an unsupported compression";
        return 0;
    }
    if (stat.m_uncomp_size == 0) {
        if (!addMemory(writer, name, nullptr, 0, 0)) return fail(writer);
        return 1;
    }
    bool raw = stat.m_method == MZ_DEFLATED;
    size_t length = 0;
    void* data = mz_zip_reader_extract_to_heap(&reader->zip, file, &length, raw ? MZ_ZIP_FLAG_COMPRESSED_DATA : 0);
    if (!data && (raw ? stat.m_comp_size : stat.m_uncomp_size) > 0) {
        writer->error = mz_zip_get_error_string(mz_zip_get_last_error(&reader->zip));
        return 0;
    }
    mz_bool ok;
    if (raw) {
        ok = mz_zip_writer_add_mem_ex(&writer->zip, name, data, length, nullptr, 0, MZ_DEFAULT_LEVEL | MZ_ZIP_FLAG_COMPRESSED_DATA, stat.m_uncomp_size, stat.m_crc32);
    } else {
        ok = addMemory(writer, name, data, length, 0);
    }
    if (data) mz_free(data);
    if (!ok) return fail(writer);
    return 1;
}

const char* varchive_writer_error(varchive_writer* writer) {
    if (!writer) return "No writer";
    return writer->error.c_str();
}

int varchive_writer_close(varchive_writer* writer) {
    if (!writer) return 0;
    int ok = 1;
    {
        std::lock_guard<std::mutex> guard(writer->lock);
        if (writer->open) {
            if (!mz_zip_writer_finalize_archive(&writer->zip)) ok = 0;
            if (!mz_zip_writer_end(&writer->zip)) ok = 0;
            writer->open = false;
        }
    }
    delete writer;
    return ok;
}

void varchive_free(void* data) {
    std::free(data);
}

}
