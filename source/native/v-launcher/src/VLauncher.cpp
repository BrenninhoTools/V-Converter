#include "vlauncher/VLauncher.h"

#include <algorithm>
#include <cctype>
#include <cstring>
#include <filesystem>
#include <string>
#include <system_error>

#if defined(_WIN32)
    #ifndef NOMINMAX
        #define NOMINMAX
    #endif
    #include <windows.h>
#else
    #include <cerrno>
    #include <fcntl.h>
    #include <sys/stat.h>
    #include <sys/wait.h>
    #include <unistd.h>
#endif

namespace fs = std::filesystem;

namespace {

#if defined(_WIN32)
const char* const kExecutables[] = {"Funkin.exe"};
#elif defined(__APPLE__)
const char* const kExecutables[] = {"Funkin.app", "Friday Night Funkin'.app", "Funkin"};
#else
const char* const kExecutables[] = {"Funkin"};
#endif

fs::path toPath(const char* text) {
#if defined(_WIN32)
    int size = MultiByteToWideChar(CP_UTF8, 0, text, -1, nullptr, 0);
    if (size <= 1) return fs::path();
    std::wstring wide(static_cast<size_t>(size - 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, text, -1, wide.data(), size);
    return fs::path(wide);
#else
    return fs::path(text);
#endif
}

std::string toUtf8(const fs::path& path) {
#if defined(_WIN32)
    const std::wstring& wide = path.native();
    if (wide.empty()) return std::string();
    int size = WideCharToMultiByte(CP_UTF8, 0, wide.c_str(), static_cast<int>(wide.size()), nullptr, 0, nullptr, nullptr);
    std::string text(static_cast<size_t>(size), '\0');
    WideCharToMultiByte(CP_UTF8, 0, wide.c_str(), static_cast<int>(wide.size()), text.data(), size, nullptr, nullptr);
    return text;
#else
    return path.string();
#endif
}

int32_t copyOut(const fs::path& path, char* out, int32_t capacity) {
    std::string text = toUtf8(path);
    if (out == nullptr || capacity <= 0 || static_cast<int32_t>(text.size()) >= capacity) return VLAUNCHER_TOO_SMALL;
    std::memcpy(out, text.c_str(), text.size() + 1);
    return VLAUNCHER_OK;
}

bool equalsIgnoreCase(const std::string& a, const std::string& b) {
    return a.size() == b.size() && std::equal(a.begin(), a.end(), b.begin(), [](char x, char y) {
        return std::tolower(static_cast<unsigned char>(x)) == std::tolower(static_cast<unsigned char>(y));
    });
}

bool endsWithIgnoreCase(const std::string& text, const std::string& suffix) {
    return text.size() >= suffix.size() && equalsIgnoreCase(text.substr(text.size() - suffix.size()), suffix);
}

bool isDirectory(const fs::path& path) {
    std::error_code error;
    return fs::is_directory(path, error);
}

bool isRegularFile(const fs::path& path) {
    std::error_code error;
    return fs::is_regular_file(path, error);
}

bool isBundle(const fs::path& path) {
    return endsWithIgnoreCase(toUtf8(path.filename()), ".app") && isDirectory(path / "Contents");
}

bool isRunnable(const fs::path& path) {
    if (!isRegularFile(path)) return false;
#if defined(_WIN32)
    return endsWithIgnoreCase(toUtf8(path.filename()), ".exe");
#else
    return access(path.c_str(), X_OK) == 0;
#endif
}

bool isLaunchable(const fs::path& path) {
#if defined(__APPLE__)
    if (isBundle(path)) return true;
#endif
    return isRunnable(path);
}

bool findIn(const fs::path& folder, fs::path& found) {
    for (const char* name : kExecutables) {
        fs::path candidate = folder / toPath(name);
        if (isLaunchable(candidate)) {
            found = candidate;
            return true;
        }
    }
    std::error_code error;
    fs::directory_iterator it(folder, fs::directory_options::skip_permission_denied, error);
    if (error) return false;
    for (const fs::directory_entry& entry : it) {
        std::string name = toUtf8(entry.path().filename());
        for (const char* wanted : kExecutables) {
            if (equalsIgnoreCase(name, wanted) && isLaunchable(entry.path())) {
                found = entry.path();
                return true;
            }
        }
    }
    return false;
}

bool resolvePath(const fs::path& path, fs::path& found) {
    if (path.empty()) return false;
#if defined(__APPLE__)
    if (isBundle(path)) {
        found = path;
        return true;
    }
    for (fs::path parent = path; parent.has_parent_path() && parent != parent.parent_path(); parent = parent.parent_path()) {
        if (isBundle(parent)) {
            found = parent;
            return true;
        }
    }
#endif
    if (isRunnable(path)) {
        found = path;
        return true;
    }
    if (!isDirectory(path)) return false;
    if (findIn(path, found)) return true;
    std::error_code error;
    fs::directory_iterator it(path, fs::directory_options::skip_permission_denied, error);
    if (error) return false;
    for (const fs::directory_entry& entry : it) {
        std::error_code kind;
        if (!entry.is_directory(kind) || isBundle(entry.path())) continue;
        if (findIn(entry.path(), found)) return true;
    }
    return false;
}

fs::path modsFolderOf(const fs::path& executable) {
#if defined(__APPLE__)
    if (isBundle(executable)) return executable / "Contents" / "Resources" / "mods";
#endif
    return executable.parent_path() / "mods";
}

std::string cleanName(const char* name) {
    std::string out;
    for (const char* c = name; *c != '\0'; c++) {
        unsigned char ch = static_cast<unsigned char>(*c);
        if (ch < 32 || std::strchr("<>:\"/\\|?*", ch) != nullptr) out += '_';
        else out += static_cast<char>(ch);
    }
    while (!out.empty() && (out.back() == '.' || out.back() == ' ')) out.pop_back();
    while (!out.empty() && (out.front() == '.' || out.front() == ' ')) out.erase(out.begin());
    return out;
}

int32_t countFiles(const fs::path& folder) {
    int32_t count = 0;
    std::error_code error;
    fs::recursive_directory_iterator it(folder, fs::directory_options::skip_permission_denied, error);
    if (error) return 0;
    for (fs::recursive_directory_iterator end; it != end; it.increment(error)) {
        if (error) break;
        std::error_code kind;
        if (it->is_regular_file(kind)) count++;
    }
    return count;
}

#if !defined(_WIN32)
int32_t spawnDetached(const char* program, char* const* argv, const char* directory) {
    int pipeFds[2];
    if (pipe(pipeFds) != 0) return VLAUNCHER_FAILED;
    fcntl(pipeFds[0], F_SETFD, FD_CLOEXEC);
    fcntl(pipeFds[1], F_SETFD, FD_CLOEXEC);
    pid_t child = fork();
    if (child < 0) {
        close(pipeFds[0]);
        close(pipeFds[1]);
        return VLAUNCHER_FAILED;
    }
    if (child == 0) {
        close(pipeFds[0]);
        setsid();
        pid_t grandchild = fork();
        if (grandchild != 0) {
            if (grandchild < 0) {
                int code = errno;
                ssize_t ignored = write(pipeFds[1], &code, sizeof code);
                (void)ignored;
            }
            _exit(0);
        }
        if (directory != nullptr && chdir(directory) != 0) {
            int code = errno;
            ssize_t ignored = write(pipeFds[1], &code, sizeof code);
            (void)ignored;
            _exit(127);
        }
        execv(program, argv);
        int code = errno;
        ssize_t ignored = write(pipeFds[1], &code, sizeof code);
        (void)ignored;
        _exit(127);
    }
    close(pipeFds[1]);
    int status = 0;
    while (waitpid(child, &status, 0) < 0 && errno == EINTR) {
    }
    int code = 0;
    ssize_t got;
    do {
        got = read(pipeFds[0], &code, sizeof code);
    } while (got < 0 && errno == EINTR);
    close(pipeFds[0]);
    return got > 0 ? VLAUNCHER_FAILED : VLAUNCHER_OK;
}
#endif

}

extern "C" {

const char* vlauncher_version(void) {
    return VLAUNCHER_VERSION_STRING;
}

int32_t vlauncher_resolve(const char* path, char* executable, int32_t capacity) {
    if (path == nullptr || executable == nullptr) return VLAUNCHER_INVALID;
    fs::path found;
    if (!resolvePath(toPath(path), found)) return VLAUNCHER_NOT_FOUND;
    std::error_code error;
    fs::path absolute = fs::absolute(found, error);
    return copyOut(error ? found : absolute, executable, capacity);
}

int32_t vlauncher_search(const char* const* roots, int32_t count, char* executable, int32_t capacity) {
    if (roots == nullptr || executable == nullptr || count < 0) return VLAUNCHER_INVALID;
    for (int32_t i = 0; i < count; i++) {
        if (roots[i] == nullptr || roots[i][0] == '\0') continue;
        int32_t result = vlauncher_resolve(roots[i], executable, capacity);
        if (result != VLAUNCHER_NOT_FOUND) return result;
    }
    return VLAUNCHER_NOT_FOUND;
}

int32_t vlauncher_mods_folder(const char* executable, char* folder, int32_t capacity) {
    if (executable == nullptr || folder == nullptr) return VLAUNCHER_INVALID;
    fs::path game = toPath(executable);
    if (!isLaunchable(game)) return VLAUNCHER_NOT_FOUND;
    fs::path mods = modsFolderOf(game);
    std::error_code error;
    fs::create_directories(mods, error);
    if (!isDirectory(mods)) return VLAUNCHER_FAILED;
    return copyOut(mods, folder, capacity);
}

int32_t vlauncher_install_folder(const char* source, const char* mods, const char* name, int32_t* files) {
    if (files != nullptr) *files = 0;
    if (source == nullptr || mods == nullptr || name == nullptr) return VLAUNCHER_INVALID;
    fs::path from = toPath(source);
    if (!isDirectory(from)) return VLAUNCHER_NOT_FOUND;
    std::string folder = cleanName(name);
    if (folder.empty()) return VLAUNCHER_INVALID;
    fs::path target = toPath(mods) / toPath(folder.c_str());
    std::error_code error;
    if (isDirectory(target) && fs::equivalent(from, target, error)) {
        if (files != nullptr) *files = countFiles(target);
        return VLAUNCHER_OK;
    }
    error.clear();
    fs::path relative = fs::relative(target, from, error);
    if (!error && !relative.empty() && relative.native()[0] != '.') return VLAUNCHER_INVALID;
    error.clear();
    fs::create_directories(target, error);
    if (error) return VLAUNCHER_FAILED;
    fs::copy(from, target, fs::copy_options::recursive | fs::copy_options::overwrite_existing, error);
    if (error) return VLAUNCHER_FAILED;
    if (files != nullptr) *files = countFiles(target);
    return VLAUNCHER_OK;
}

int32_t vlauncher_launch(const char* executable) {
    if (executable == nullptr) return VLAUNCHER_INVALID;
    fs::path game = toPath(executable);
    if (!isLaunchable(game)) return VLAUNCHER_NOT_FOUND;
#if defined(_WIN32)
    std::wstring program = game.native();
    std::wstring directory = game.parent_path().native();
    std::wstring command = L"\"" + program + L"\"";
    STARTUPINFOW startup;
    ZeroMemory(&startup, sizeof startup);
    startup.cb = sizeof startup;
    PROCESS_INFORMATION process;
    ZeroMemory(&process, sizeof process);
    BOOL ok = CreateProcessW(program.c_str(), command.data(), nullptr, nullptr, FALSE,
        DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP, nullptr, directory.c_str(), &startup, &process);
    if (!ok) return VLAUNCHER_FAILED;
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return VLAUNCHER_OK;
#else
    std::string program = game.string();
    std::string directory = game.parent_path().string();
#if defined(__APPLE__)
    if (isBundle(game)) {
        char open[] = "/usr/bin/open";
        char* argv[] = {open, program.data(), nullptr};
        return spawnDetached(open, argv, nullptr);
    }
#endif
    char* argv[] = {program.data(), nullptr};
    return spawnDetached(program.c_str(), argv, directory.empty() ? nullptr : directory.c_str());
#endif
}

}
