#ifndef VSCRIPT_VSCRIPT_H
#define VSCRIPT_VSCRIPT_H

#ifdef __cplusplus
extern "C" {
#endif

#if defined(__GNUC__) || defined(__clang__)
    #define VSCRIPT_API __attribute__((visibility("default")))
#else
    #define VSCRIPT_API
#endif

#define VSCRIPT_VERSION_STRING "1.0.0"

VSCRIPT_API const char* vscript_version(void);

VSCRIPT_API char* vscript_convert_lua(const char* source, const char* options_json);

VSCRIPT_API char* vscript_generate(const char* spec_json);

VSCRIPT_API char* vscript_builtin_event(const char* name, const char* class_name, const char* id);

VSCRIPT_API char* vscript_convert_data(const char* kind, const char* input_json);

VSCRIPT_API void vscript_free(char* text);

#ifdef __cplusplus
}
#endif

#endif
