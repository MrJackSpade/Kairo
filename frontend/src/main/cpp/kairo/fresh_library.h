#pragma once

// A fresh copy of a native library for each machine, unloaded after it: a core whose emulator
// keeps its state in globals (KairoST's Hatari, KairoDos's DOSBox Staging) starts every machine
// from the library's initial globals. On Android the copy is forced even when one is loaded
// (ANDROID_DLEXT_FORCE_LOAD), from the library's full path: given a bare name, the linker hands
// back any loaded library of that soname before FORCE_LOAD is looked at (KairoX86#196). On Windows
// (the shared PC boot test) each load is a new temporary copy of the DLL beside the executable,
// since Windows hands back a module already loaded from the same path.

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <string>

#if defined(__ANDROID__)
#include <android/dlext.h>
#include <android/log.h>
#include <dlfcn.h>
#elif defined(_WIN32)
#include <windows.h>
#else
#error "A fresh library per machine is loaded on Android and Windows only"
#endif

namespace kairo {

class FreshLibrary {
public:
    // Loads a fresh copy of [file] (a library name, as the app packages it: "libfoo.so" on
    // Android, "libfoo.dll" beside the executable on Windows), or null with [error].
    static std::unique_ptr<FreshLibrary> load(const std::string &file, std::string &error) {
#if defined(__ANDROID__)
        const std::string path = library_folder() + file;
        android_dlextinfo info{};
        info.flags = ANDROID_DLEXT_FORCE_LOAD;
        void *handle = android_dlopen_ext(path.c_str(), RTLD_NOW | RTLD_LOCAL, &info);
        if (!handle) {
            error = "Could not load " + path + ": " + dlerror();
            return nullptr;
        }
        return std::unique_ptr<FreshLibrary>(new FreshLibrary(handle, file, ""));
#else
        static std::atomic<unsigned> copies{0};
        const std::wstring original = module_folder() + widen(file);
        wchar_t temp[MAX_PATH + 1];
        const DWORD length = GetTempPathW(MAX_PATH + 1, temp);
        if (length == 0 || length > MAX_PATH) {
            error = "GetTempPath failed: " + windows_error();
            return nullptr;
        }
        const std::wstring stem = widen(file.substr(0, file.rfind('.')));
        const std::wstring copy = std::wstring(temp, length) + stem + L"-" + std::to_wstring(GetCurrentProcessId()) +
                                  L"-" + std::to_wstring(copies.fetch_add(1)) + L".dll";
        if (!CopyFileW(original.c_str(), copy.c_str(), FALSE)) {
            error = "Could not copy " + narrow(original) + " to " + narrow(copy) + ": " + windows_error();
            return nullptr;
        }
        HMODULE module = LoadLibraryW(copy.c_str());
        if (!module) {
            error = "Could not load " + narrow(copy) + ": " + windows_error();
            DeleteFileW(copy.c_str());
            return nullptr;
        }
        return std::unique_ptr<FreshLibrary>(new FreshLibrary(module, file, narrow(copy)));
#endif
    }

    // The library's exported [name], or null when it has none.
    void *symbol(const char *name) const {
#if defined(__ANDROID__)
        return dlsym(handle_, name);
#else
        return reinterpret_cast<void *>(GetProcAddress(static_cast<HMODULE>(handle_), name));
#endif
    }

    // Unloads the copy. One that cannot be unloaded ends the process: the next machine must not
    // start beside it.
    ~FreshLibrary() {
#if defined(__ANDROID__)
        if (dlclose(handle_) != 0) fail("Could not unload " + file_ + ": " + dlerror());
#else
        if (!FreeLibrary(static_cast<HMODULE>(handle_))) fail("Could not unload " + copy_ + ": " + windows_error());
        if (!DeleteFileW(widen(copy_).c_str())) fail("Could not delete " + copy_ + ": " + windows_error());
#endif
    }

    FreshLibrary(const FreshLibrary &) = delete;
    FreshLibrary &operator=(const FreshLibrary &) = delete;

private:
    FreshLibrary(void *handle, std::string file, std::string copy)
        : handle_(handle), file_(std::move(file)), copy_(std::move(copy)) {}

    [[noreturn]] static void fail(const std::string &message) {
#if defined(__ANDROID__)
        __android_log_print(ANDROID_LOG_FATAL, "Kairo", "%s", message.c_str());
#else
        std::fprintf(stderr, "%s\n", message.c_str());
#endif
        std::abort();
    }

#if defined(__ANDROID__)
    // The folder the app's native libraries are in, with its trailing separator: the one holding
    // the library this code is built into ("<apk>!/lib/<abi>/" when they load from the APK).
    static std::string library_folder() {
        Dl_info info{};
        if (!dladdr(reinterpret_cast<void *>(&FreshLibrary::library_folder), &info) || !info.dli_fname)
            fail("dladdr found no library for the fresh-library loader");
        const std::string self = info.dli_fname;
        const size_t slash = self.rfind('/');
        if (slash == std::string::npos) fail("The fresh-library loader's library has no folder: " + self);
        return self.substr(0, slash + 1);
    }
#endif

#if defined(_WIN32)
    static std::string windows_error() { return "Windows error " + std::to_string(GetLastError()); }

    // The folder of the executable, with its trailing separator.
    static std::wstring module_folder() {
        std::wstring path(MAX_PATH, L'\0');
        for (;;) {
            const DWORD length = GetModuleFileNameW(nullptr, path.data(), DWORD(path.size()));
            if (length == 0) fail("GetModuleFileName failed: " + windows_error());
            if (length < path.size()) {
                path.resize(length);
                break;
            }
            path.resize(path.size() * 2);
        }
        return path.substr(0, path.find_last_of(L"\\/") + 1);
    }

    static std::string narrow(const std::wstring &text) {
        const int size = WideCharToMultiByte(CP_UTF8, 0, text.c_str(), int(text.size()), nullptr, 0, nullptr, nullptr);
        std::string out(size_t(size), '\0');
        WideCharToMultiByte(CP_UTF8, 0, text.c_str(), int(text.size()), out.data(), size, nullptr, nullptr);
        return out;
    }

    static std::wstring widen(const std::string &text) {
        const int size = MultiByteToWideChar(CP_UTF8, 0, text.c_str(), int(text.size()), nullptr, 0);
        std::wstring out(size_t(size), L'\0');
        MultiByteToWideChar(CP_UTF8, 0, text.c_str(), int(text.size()), out.data(), size);
        return out;
    }
#endif

    void *handle_;
    std::string file_;
    std::string copy_;
};

} // namespace kairo
