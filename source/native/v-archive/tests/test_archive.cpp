#include "varchive/VArchive.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

static int failures = 0;

#define CHECK(condition)                                                        \
    do {                                                                        \
        if (!(condition)) {                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #condition); \
            failures++;                                                         \
        }                                                                       \
    } while (0)

static std::string temp(const char* name) {
    const char* dir = std::getenv("TMPDIR");
    std::string base = dir && *dir ? dir : ".";
    return base + "/" + name;
}

static std::string slurp(const std::string& path) {
    std::string out;
    FILE* f = std::fopen(path.c_str(), "rb");
    if (!f) return out;
    char buffer[4096];
    size_t n;
    while ((n = std::fread(buffer, 1, sizeof(buffer), f)) > 0) out.append(buffer, n);
    std::fclose(f);
    return out;
}

static int64_t find(varchive_reader* reader, const std::string& wanted) {
    int64_t count = varchive_reader_count(reader);
    for (int64_t i = 0; i < count; i++) {
        char name[1024];
        uint64_t size = 0;
        int dir = 0;
        int utf8 = 0;
        if (varchive_reader_entry(reader, i, name, sizeof(name), &size, &dir, &utf8) && wanted == name) return i;
    }
    return -1;
}

int main() {
    std::string big;
    for (int i = 0; i < 200000; i++) big += "line " + std::to_string(i % 97) + "\n";
    std::string loose = temp("varchive_loose.txt");
    {
        FILE* f = std::fopen(loose.c_str(), "wb");
        std::fwrite(big.data(), 1, big.size(), f);
        std::fclose(f);
    }

    std::string first = temp("varchive_first.zip");
    char error[256] = {0};
    varchive_writer* writer = varchive_writer_open(first.c_str(), error, sizeof(error));
    CHECK(writer != nullptr);
    CHECK(varchive_writer_add_bytes(writer, "data/songs/test/test-chart.json", "{\"version\":\"2.0.0\"}", 19, 6));
    CHECK(varchive_writer_add_bytes(writer, "images/empty.png", "", 0, 0));
    CHECK(varchive_writer_add_bytes(writer, "tiny.txt", "ab", 2, 6));
    CHECK(varchive_writer_add_bytes(writer, "empty.json", "", 0, 6));
    CHECK(varchive_writer_add_bytes(writer, "big.txt", big.data(), big.size(), 6));
    CHECK(varchive_writer_add_bytes(writer, "stored.txt", big.data(), 1000, 0));
    CHECK(varchive_writer_add_file(writer, "songs/test/Inst.ogg", loose.c_str(), 0));
    CHECK(varchive_writer_add_bytes(writer, "m\xc3\xbasica/caf\xc3\xa9.txt", "x", 1, 6));
    CHECK(!varchive_writer_add_file(writer, "missing.txt", temp("varchive_missing_file").c_str(), 6));
    CHECK(std::strlen(varchive_writer_error(writer)) > 0);
    CHECK(varchive_writer_close(writer));

    varchive_reader* reader = varchive_reader_open(first.c_str(), error, sizeof(error));
    CHECK(reader != nullptr);
    CHECK(varchive_reader_count(reader) == 8);
    int64_t chart = find(reader, "data/songs/test/test-chart.json");
    CHECK(chart >= 0);
    size_t size = 0;
    unsigned char* data = varchive_reader_read(reader, chart, &size);
    CHECK(data && std::string(reinterpret_cast<char*>(data), size) == "{\"version\":\"2.0.0\"}");
    varchive_free(data);
    int64_t empty = find(reader, "images/empty.png");
    data = varchive_reader_read(reader, empty, &size);
    CHECK(data != nullptr && size == 0);
    varchive_free(data);

    int64_t bigIndex = find(reader, "big.txt");
    varchive_stream* stream = varchive_stream_open(reader, bigIndex);
    CHECK(stream != nullptr);
    std::string streamed;
    char chunk[7777];
    int64_t n;
    while ((n = varchive_stream_read(stream, chunk, sizeof(chunk))) > 0) streamed.append(chunk, static_cast<size_t>(n));
    CHECK(n == 0);
    CHECK(streamed == big);
    varchive_stream_close(stream);

    std::string extracted = temp("varchive_extracted.txt");
    CHECK(varchive_reader_extract(reader, find(reader, "songs/test/Inst.ogg"), extracted.c_str()));
    CHECK(slurp(extracted) == big);

    std::string second = temp("varchive_second.zip");
    writer = varchive_writer_open(second.c_str(), error, sizeof(error));
    CHECK(writer != nullptr);
    const char* names[] = {"data/songs/test/test-chart.json", "images/empty.png", "empty.json", "tiny.txt", "big.txt", "stored.txt", "songs/test/Inst.ogg", "m\xc3\xbasica/caf\xc3\xa9.txt"};
    for (const char* name : names) {
        std::string renamed = std::string("My Mod/") + name;
        CHECK(varchive_writer_copy(writer, renamed.c_str(), reader, find(reader, name)));
    }
    CHECK(!varchive_writer_copy(writer, "nope", reader, 999));
    CHECK(varchive_writer_close(writer));

    varchive_reader* copy = varchive_reader_open(second.c_str(), error, sizeof(error));
    CHECK(copy != nullptr);
    CHECK(varchive_reader_count(copy) == 8);
    for (const char* name : names) {
        size_t a = 0;
        size_t b = 0;
        unsigned char* original = varchive_reader_read(reader, find(reader, name), &a);
        unsigned char* copied = varchive_reader_read(copy, find(copy, std::string("My Mod/") + name), &b);
        CHECK(original && copied && a == b && std::memcmp(original, copied, a) == 0);
        varchive_free(original);
        varchive_free(copied);
    }
    char name[1024];
    uint64_t entrySize = 0;
    int dir = 0;
    int utf8 = 0;
    CHECK(varchive_reader_entry(copy, find(copy, "My Mod/big.txt"), name, sizeof(name), &entrySize, &dir, &utf8));
    CHECK(entrySize == big.size() && dir == 0);
    varchive_reader_close(copy);
    varchive_reader_close(reader);

    CHECK(varchive_reader_open(temp("varchive_missing.zip").c_str(), error, sizeof(error)) == nullptr);
    CHECK(std::strlen(error) > 0);
    CHECK(varchive_reader_open(loose.c_str(), error, sizeof(error)) == nullptr);

    std::remove(loose.c_str());
    std::remove(first.c_str());
    if (!std::getenv("VARCHIVE_KEEP")) std::remove(second.c_str());
    std::remove(extracted.c_str());
    if (failures == 0) std::printf("v-archive: all tests passed\n");
    return failures == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
