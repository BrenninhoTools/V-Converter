#ifndef VLAUNCHER_VLAUNCHER_H
#define VLAUNCHER_VLAUNCHER_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(__GNUC__) || defined(__clang__)
    #define VLAUNCHER_API __attribute__((visibility("default")))
#else
    #define VLAUNCHER_API
#endif

#define VLAUNCHER_VERSION_STRING "1.0.0"

#define VLAUNCHER_OK 0
#define VLAUNCHER_NOT_FOUND 1
#define VLAUNCHER_INVALID 2
#define VLAUNCHER_FAILED 3
#define VLAUNCHER_UNSUPPORTED 4
#define VLAUNCHER_TOO_SMALL 5

VLAUNCHER_API const char* vlauncher_version(void);

VLAUNCHER_API int32_t vlauncher_resolve(const char* path, char* executable, int32_t capacity);

VLAUNCHER_API int32_t vlauncher_search(const char* const* roots, int32_t count, char* executable, int32_t capacity);

VLAUNCHER_API int32_t vlauncher_mods_folder(const char* executable, char* folder, int32_t capacity);

VLAUNCHER_API int32_t vlauncher_install_folder(const char* source, const char* mods, const char* name, int32_t* files);

VLAUNCHER_API int32_t vlauncher_launch(const char* executable);

#ifdef __cplusplus
}
#endif

#endif
