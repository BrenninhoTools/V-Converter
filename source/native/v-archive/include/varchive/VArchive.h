#ifndef VARCHIVE_H
#define VARCHIVE_H

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#define VARCHIVE_API
#else
#define VARCHIVE_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct varchive_reader varchive_reader;
typedef struct varchive_writer varchive_writer;
typedef struct varchive_stream varchive_stream;

VARCHIVE_API const char* varchive_version(void);

VARCHIVE_API varchive_reader* varchive_reader_open(const char* path, char* error, size_t error_size);
VARCHIVE_API void varchive_reader_close(varchive_reader* reader);
VARCHIVE_API int64_t varchive_reader_count(varchive_reader* reader);
VARCHIVE_API int varchive_reader_entry(varchive_reader* reader, int64_t index, char* name, size_t name_size, uint64_t* size, int* is_directory, int* utf8);
VARCHIVE_API unsigned char* varchive_reader_read(varchive_reader* reader, int64_t index, size_t* size);
VARCHIVE_API int varchive_reader_extract(varchive_reader* reader, int64_t index, const char* path);
VARCHIVE_API varchive_stream* varchive_stream_open(varchive_reader* reader, int64_t index);
VARCHIVE_API int64_t varchive_stream_read(varchive_stream* stream, void* buffer, size_t size);
VARCHIVE_API void varchive_stream_close(varchive_stream* stream);

VARCHIVE_API varchive_writer* varchive_writer_open(const char* path, char* error, size_t error_size);
VARCHIVE_API int varchive_writer_add_bytes(varchive_writer* writer, const char* name, const void* data, size_t size, int level);
VARCHIVE_API int varchive_writer_add_file(varchive_writer* writer, const char* name, const char* path, int level);
VARCHIVE_API int varchive_writer_copy(varchive_writer* writer, const char* name, varchive_reader* reader, int64_t index);
VARCHIVE_API const char* varchive_writer_error(varchive_writer* writer);
VARCHIVE_API int varchive_writer_close(varchive_writer* writer);


VARCHIVE_API void varchive_free(void* data);

#ifdef __cplusplus
}
#endif

#endif
