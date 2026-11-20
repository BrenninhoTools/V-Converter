#include "vlauncher/VLauncher.h"

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <string>
#include <thread>

namespace fs = std::filesystem;

static int failures = 0;

#define CHECK(condition)                                                        \
    do {                                                                        \
        if (!(condition)) {                                                     \
            std::fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #condition); \
            failures++;                                                         \
        }                                                                       \
    } while (0)

static void writeFile(const fs::path& path, const std::string& content) {
    fs::create_directories(path.parent_path());
    std::ofstream out(path, std::ios::binary);
    out << content;
}

static std::string readFile(const fs::path& path) {
    std::ifstream in(path, std::ios::binary);
    return std::string(std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>());
}

#if defined(_WIN32)
static const char* gameName = "Funkin.exe";
#else
static const char* gameName = "Funkin";
#endif

static fs::path makeGame(const fs::path& folder, const fs::path& marker) {
    fs::path game = folder / gameName;
#if defined(_WIN32)
    writeFile(game, "MZ");
    (void)marker;
#else
    writeFile(game, "#!/bin/sh\npwd > \"" + marker.string() + "\"\n");
    fs::permissions(game, fs::perms::owner_all | fs::perms::group_read | fs::perms::others_read);
#endif
    return game;
}

static void testResolve(const fs::path& root) {
    char out[4096];
    CHECK(vlauncher_resolve((root / "nothing").string().c_str(), out, sizeof out) == VLAUNCHER_NOT_FOUND);
    fs::path install = root / "funkin-linux-64bit";
    fs::path game = makeGame(install, root / "marker.txt");
    CHECK(vlauncher_resolve(game.string().c_str(), out, sizeof out) == VLAUNCHER_OK);
    CHECK(fs::equivalent(out, game));
    CHECK(vlauncher_resolve(install.string().c_str(), out, sizeof out) == VLAUNCHER_OK);
    CHECK(fs::equivalent(out, game));
    CHECK(vlauncher_resolve(root.string().c_str(), out, sizeof out) == VLAUNCHER_OK);
    CHECK(fs::equivalent(out, game));
    CHECK(vlauncher_resolve(root.string().c_str(), out, 4) == VLAUNCHER_TOO_SMALL);

    std::string missing = (root / "missing").string();
    std::string top = root.string();
    const char* roots[] = {missing.c_str(), "", top.c_str()};
    CHECK(vlauncher_search(roots, 3, out, sizeof out) == VLAUNCHER_OK);
    CHECK(fs::equivalent(out, game));
    CHECK(vlauncher_search(roots, 2, out, sizeof out) == VLAUNCHER_NOT_FOUND);
}

static void testInstall(const fs::path& root) {
    fs::path game = root / "funkin-linux-64bit" / gameName;
    char mods[4096];
    CHECK(vlauncher_mods_folder(game.string().c_str(), mods, sizeof mods) == VLAUNCHER_OK);
    CHECK(fs::is_directory(mods));
    CHECK(fs::equivalent(mods, game.parent_path() / "mods"));

    fs::path source = root / "converted" / "My Mod";
    writeFile(source / "_polymod_metadata.json", "{\"title\":\"My Mod\"}");
    writeFile(source / "data" / "songs" / "bopeebo" / "bopeebo-chart.json", "{}");
    writeFile(source / "images" / "icons" / "icon-dad.png", "png");
    int32_t files = 0;
    CHECK(vlauncher_install_folder(source.string().c_str(), mods, "My Mod", &files) == VLAUNCHER_OK);
    CHECK(files == 3);
    CHECK(readFile(fs::path(mods) / "My Mod" / "_polymod_metadata.json") == "{\"title\":\"My Mod\"}");

    writeFile(source / "_polymod_metadata.json", "{\"title\":\"Again\"}");
    CHECK(vlauncher_install_folder(source.string().c_str(), mods, "My Mod", &files) == VLAUNCHER_OK);
    CHECK(readFile(fs::path(mods) / "My Mod" / "_polymod_metadata.json") == "{\"title\":\"Again\"}");

    fs::path installed = fs::path(mods) / "My Mod";
    CHECK(vlauncher_install_folder(installed.string().c_str(), mods, "My Mod", &files) == VLAUNCHER_OK);
    CHECK(files == 3);

    CHECK(vlauncher_install_folder(source.string().c_str(), mods, "../../Bad:Name", &files) == VLAUNCHER_OK);
    CHECK(fs::is_directory(fs::path(mods) / ".._.._Bad_Name") || fs::is_directory(fs::path(mods) / "_.._Bad_Name"));
    CHECK(vlauncher_install_folder(source.string().c_str(), mods, "...", &files) == VLAUNCHER_INVALID);
    CHECK(vlauncher_install_folder((root / "nope").string().c_str(), mods, "x", &files) == VLAUNCHER_NOT_FOUND);
    CHECK(vlauncher_install_folder(root.string().c_str(), (root / "converted").string().c_str(), "inside", &files) == VLAUNCHER_INVALID);
}

static void testLaunch(const fs::path& root) {
    CHECK(vlauncher_launch((root / "missing" / gameName).string().c_str()) == VLAUNCHER_NOT_FOUND);
#if !defined(_WIN32)
    fs::path game = root / "funkin-linux-64bit" / gameName;
    fs::path marker = root / "marker.txt";
    CHECK(vlauncher_launch(game.string().c_str()) == VLAUNCHER_OK);
    for (int i = 0; i < 100 && readFile(marker).empty(); i++) std::this_thread::sleep_for(std::chrono::milliseconds(20));
    std::string where = readFile(marker);
    while (!where.empty() && (where.back() == '\n' || where.back() == '\r')) where.pop_back();
    CHECK(!where.empty());
    if (!where.empty()) CHECK(fs::equivalent(where, game.parent_path()));

    fs::path broken = root / "broken" / gameName;
    writeFile(broken, "\x7f" "ELF-not-really");
    fs::permissions(broken, fs::perms::owner_all);
    CHECK(vlauncher_launch(broken.string().c_str()) == VLAUNCHER_FAILED);
#endif
}

int main() {
    fs::path root = fs::temp_directory_path() / ("vlauncher-test-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    fs::create_directories(root);
    root = fs::canonical(root);
    CHECK(std::string(vlauncher_version()) == VLAUNCHER_VERSION_STRING);
    CHECK(vlauncher_resolve(nullptr, nullptr, 0) == VLAUNCHER_INVALID);
    testResolve(root);
    testInstall(root);
    testLaunch(root);
    std::error_code error;
    fs::remove_all(root, error);
    if (failures == 0) std::printf("v-launcher: all tests passed\n");
    return failures == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
