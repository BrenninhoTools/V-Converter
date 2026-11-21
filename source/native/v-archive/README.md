# V-Archive

V-Archive reads and writes the ZIP files V-Converter converts mods from and to, in C++ with [miniz](https://github.com/richgel999/miniz) (MIT, in `third_party/miniz`).

- A ZIP mod is read in place: the converter lists and opens its entries straight from the archive instead of extracting it to a temporary folder first.
- When a file goes from a ZIP mod into a ZIP output unchanged (songs, images, videos…), its compressed bytes are copied as they are, without being decompressed and compressed again.
- Files from a folder are added to the ZIP by the native code, and folder outputs extract entries straight to disk.
- Stored entries are written without data descriptors, so every unzip tool (including Java's `ZipInputStream`) reads the output.

The C API is in `include/varchive/VArchive.h`; the JNI bridge is `source/native/bridge/vconverter_archive_jni.cpp` and the Kotlin side is in `source/jvm/com/vconvertercrew/vconverter/archive`.

```
cmake -S . -B build -DVARCHIVE_BUILD_TESTS=ON
cmake --build build
ctest --test-dir build
```
