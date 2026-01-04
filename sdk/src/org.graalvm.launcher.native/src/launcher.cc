/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <jni.h>

#include <cstdint>
#include <cstring>
#include <climits>
#include <cstdlib>

#include <string>
#include <optional>
#include <iostream>
#include <sstream>
#include <fstream>
#include <vector>

#include <cassert>

#define QUOTE(name) #name
#define STR(macro) QUOTE(macro)

#ifndef LAUNCHER_CLASS
    #error launcher class undefined
#endif
#define LAUNCHER_CLASS_STR STR(LAUNCHER_CLASS)
#define LANGUAGES_DIR_STR STR(LANGUAGES_DIR)
#define TOOLS_DIR_STR STR(TOOLS_DIR)
#define LAUNCHER_MAIN_MODULE_STR STR(LAUNCHER_MAIN_MODULE)

#ifndef GRAALVM_VERSION
    #error GRAALVM_VERSION not defined
#endif
#define GRAALVM_VERSION_STR STR(GRAALVM_VERSION)

#ifndef LIBJVM_RELPATH
    #error path to jvm library undefined
#endif

#ifndef DIR_SEP
    #error directory separator undefined
#endif

#ifndef CP_SEP
    #error class path separator undefined
#endif

#ifdef LIBLANG_RELPATH
#define LIBLANG_RELPATH_STR STR(LIBLANG_RELPATH)
#endif

#define LIBJVM_RELPATH_STR STR(LIBJVM_RELPATH)
#define DIR_SEP_STR STR(DIR_SEP)
#define CP_SEP_STR STR(CP_SEP)

#define VM_ARG_PREFIX "--vm."
#define VM_CP_ARG_PREFIX "--vm.cp="
#define VM_CLASSPATH_ARG_PREFIX "--vm.classpath="
#define VM_P_ARG_PREFIX "--vm.p="
#define VM_MODULE_PATH_ARG_PREFIX "--vm.-module-path="
#define VM_LIBRARY_PATH_ARG_PREFIX "--vm.Djava.library.path="
#define VM_STACK_SIZE_ARG_PREFIX "--vm.Xss"
#define VM_ARG_FILE_ARG_PREFIX "--vm.@"

#define VM_ARG_OFFSET (sizeof(VM_ARG_PREFIX)-1)
#define VM_CP_ARG_OFFSET (sizeof(VM_CP_ARG_PREFIX)-1)
#define VM_CLASSPATH_ARG_OFFSET (sizeof(VM_CLASSPATH_ARG_PREFIX)-1)
#define VM_P_ARG_OFFSET (sizeof(VM_P_ARG_PREFIX)-1)
#define VM_MODULE_PATH_ARG_OFFSET (sizeof(VM_MODULE_PATH_ARG_PREFIX)-1)
#define VM_LIBRARY_PATH_ARG_OFFSET (sizeof(VM_LIBRARY_PATH_ARG_PREFIX)-1)
#define VM_STACK_SIZE_ARG_OFFSET (sizeof(VM_STACK_SIZE_ARG_PREFIX)-1)
#define VM_ARG_FILE_ARG_OFFSET (sizeof(VM_ARG_FILE_ARG_PREFIX)-1)

#define STARTS_WITH(ARG, PREFIX) (ARG.rfind(PREFIX, 0) != std::string::npos)
#define IS_VM_ARG(ARG) STARTS_WITH(ARG, VM_ARG_PREFIX)
#define IS_VM_CP_ARG(ARG) STARTS_WITH(ARG, VM_CP_ARG_PREFIX)
#define IS_VM_CLASSPATH_ARG(ARG) STARTS_WITH(ARG, VM_CLASSPATH_ARG_PREFIX)
#define IS_VM_P_ARG(ARG) STARTS_WITH(ARG, VM_P_ARG_PREFIX)
#define IS_VM_MODULE_PATH_ARG(ARG) STARTS_WITH(ARG, VM_MODULE_PATH_ARG_PREFIX)
#define IS_VM_LIBRARY_PATH_ARG(ARG) STARTS_WITH(ARG, VM_LIBRARY_PATH_ARG_PREFIX)
#define IS_VM_STACK_SIZE_ARG(ARG) STARTS_WITH(ARG, VM_STACK_SIZE_ARG_PREFIX)
#define IS_VM_ARG_FILE_ARG(ARG) STARTS_WITH(ARG, VM_ARG_FILE_ARG_PREFIX)
#define IS_VM_START_ON_FIRST_THREAD(ARG) (ARG == "--vm.XstartOnFirstThread")

#define NMT_ARG_NAME "XX:NativeMemoryTracking"
#define NMT_ENV_NAME "NMT_LEVEL_"

#if defined (__linux__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <limits.h>
    #include <unistd.h>
    #include <errno.h>
    #include <dirent.h>
    #include <sys/types.h>
    #include <sys/stat.h>
#elif defined (__APPLE__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <unistd.h>
    #include <errno.h>
    #include <dirent.h>
    #include <mach-o/dyld.h>
    #include <sys/syslimits.h>
    #include <sys/stat.h>

    #ifndef LIBJLI_RELPATH
        #error path to jli library undefined
    #endif
    #define LIBJLI_RELPATH_STR STR(LIBJLI_RELPATH)

    /* Support Cocoa event loop on the main thread */
    #include <Cocoa/Cocoa.h>
    #include <objc/objc-runtime.h>
    #include <objc/objc-auto.h>

#elif defined (_WIN32)
    #pragma push_macro("NOMINMAX")
    #pragma push_macro("WIN32_LEAN_AND_MEAN")
    #define NOMINMAX
    #define WIN32_LEAN_AND_MEAN
    #include <windows.h>
    #pragma pop_macro("WIN32_LEAN_AND_MEAN")
    #pragma pop_macro("NOMINMAX")
    #include <libloaderapi.h>
    #include <cstdlib>
    #include <process.h>
    #include <errno.h>
    #include <sys/types.h>
    #include <sys/stat.h>
    #define getpid _getpid
    #define stat _stat
#else
    #error platform not supported or undefined
#endif

// jint JNI_CreateJavaVM(JavaVM **p_vm, void **p_env, void *vm_args);
typedef jint(*CreateJavaVM_type)(JavaVM **, void **, void *);
// jint JNI_GetDefaultJavaVMInitArgs(void *vm_args);
typedef jint(*GetDefaultJavaVMInitArgs_type)(void *);

extern char **environ;

static bool debug = false;
static bool relaunch = false;
static bool found_switch_to_jvm_flag = false;

/* platform-independent environment setter, use empty value to clear */
static int setenv(std::string key, std::string value) {
    if (debug) {
        std::cout << "Setting env variable " << key << "=" << value << std::endl;
    }
    #if defined (_WIN32)
        if(_putenv_s(key.c_str(), value.c_str()) == -1) {
            std::cerr << "_putenv_s failed" << std::endl;
            return -1;
        }
    #else
        if (value.empty()) {
            /* on posix, unsetenv cleares the env variable */
            if (unsetenv(key.c_str()) == -1) {
                perror("unsetenv failed");
                return -1;
            }
        } else {
            if (setenv(key.c_str(), value.c_str(), 1) == -1) {
                perror("setenv failed");
                return -1;
            }
        }
    #endif
    return 0;
}

/* check if file exists */
static bool exists(std::string filename) {
    struct stat buffer;
    return (stat(filename.c_str(), &buffer) == 0);
}

/* get the path to the current executable */
static std::string exe_path() {
    #if defined (__linux__)
        char *realPath = realpath("/proc/self/exe", NULL);
    #elif defined (__APPLE__)
        char path[PATH_MAX];
        uint32_t path_len = PATH_MAX;
        _NSGetExecutablePath(path, &path_len);
        char *realPath = realpath(path, NULL);
    #elif defined (_WIN32)
        char *realPath = (char *)malloc(_MAX_PATH);
        GetModuleFileNameA(NULL, realPath, _MAX_PATH);
        /* try to do a realpath equivalent */
        HANDLE handle = CreateFile(realPath, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
        if (handle != INVALID_HANDLE_VALUE) {
            const size_t size = _MAX_PATH + 4;
            char *resolvedPath = (char *)malloc(size);
            DWORD ret = GetFinalPathNameByHandleA(handle, resolvedPath, size, 0);
            /*
             * The path returned from GetFinalPathNameByHandleA should always
             * use "\\?\" path syntax. We strip the prefix.
             * See: https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file
             */
            if (ret < size && resolvedPath[0] == '\\' && resolvedPath[1] == '\\' && resolvedPath[2] == '?' && resolvedPath[3] == '\\') {
                strcpy_s(realPath, _MAX_PATH, resolvedPath + 4);
            }
            free(resolvedPath);
            CloseHandle(handle);
        }
    #endif
    std::string p(realPath);
    free(realPath);
    return p;
}

/* get the directory of the current executable */
static std::string exe_directory() {
    char *path = strdup(exe_path().c_str());
    #if defined (_WIN32)
        // get the directory part
        char drive[_MAX_DRIVE];
        char dir[_MAX_DIR];
        char result[_MAX_DRIVE + _MAX_DIR];
        _splitpath_s(path, drive, _MAX_DRIVE, dir, _MAX_DIR, NULL, 0, NULL, 0);
        _makepath_s(result, _MAX_PATH, drive, dir, NULL, NULL);
    #else
        char *result = dirname(path);
    #endif
    std::string exeDir(result);
    free(path);
    return exeDir;
}

static std::string canonicalize(std::string path) {
    char *result;
    #ifndef _WIN32
    char real[PATH_MAX];
    result = realpath(path.c_str(), real);
    #else
    char real[_MAX_PATH];
    result = _fullpath(real, path.c_str(), _MAX_PATH);
    #endif
    if (result == NULL) {
        std::cerr << "Could not canonicalize " << path << std::endl;
    }
    return std::string(real);
}

#if defined (__APPLE__)
/* Load libjli - this is needed on osx for libawt, which uses JLI_* methods.
 * If the GraalVM libjli is not loaded, the osx linker will look up the symbol
 * via the JavaRuntimeSupport.framework (JRS), which will fall back to the
 * system JRE and fail if none is installed
 */
static void *load_jli_lib(std::string exeDir) {
    std::ostringstream libjliPath;
    libjliPath << exeDir << DIR_SEP_STR << LIBJLI_RELPATH_STR;
    return dlopen(libjliPath.str().c_str(), RTLD_NOW);
}
#endif

/* load the language library (either native library or libjvm) and return a
 * pointer to it */
void* load_vm_lib(std::string liblangPath) {
    if (debug) {
        std::cout << "Loading library " << liblangPath << std::endl;
    }
#if defined (__linux__) || defined (__APPLE__)
        void* jvmHandle = dlopen(liblangPath.c_str(), RTLD_NOW);
        if (jvmHandle != NULL) {
            return jvmHandle;
        }
        char* errorString = dlerror();
        if (errorString != NULL) {
            std::cerr << "Error while loading " << liblangPath << ":" << std::endl << errorString << std::endl;
        }
#else
        HMODULE jvmHandle = LoadLibraryA(liblangPath.c_str());
        if (jvmHandle != NULL) {
            return (void*) jvmHandle;
        }
#endif
    return NULL;
}

void* get_function(void* library, const char* function_name) {
    if (!library) {
        return NULL;
    }
#if defined (__linux__) || defined (__APPLE__)
        void* jvmHandle = library;
        return dlsym(jvmHandle, function_name);
#else
        HMODULE jvmHandle = (HMODULE) library;
        return GetProcAddress(jvmHandle, function_name);
#endif
    return NULL;
}

static std::string vm_path(std::string exeDir, bool jvmMode) {
    std::ostringstream liblangPath;
    if (jvmMode) {
        liblangPath << exeDir << DIR_SEP_STR << LIBJVM_RELPATH_STR;
    } else {
#ifdef LIBLANG_RELPATH
        liblangPath << exeDir << DIR_SEP_STR << LIBLANG_RELPATH_STR;
#else
        std::cerr << "Should not reach here: native mode with no LIBLANG defined" << std::endl;
        exit(EXIT_FAILURE);
#endif
    }
    return liblangPath.str();
}

static size_t parse_size(std::string_view str);
static void expand_vm_arg_file(const char *arg_file,
                               std::vector<std::string> *vmArgs,
                               std::ostringstream *cp,
                               std::ostringstream *modulePath,
                               std::ostringstream *libraryPath,
                               size_t* stack_size);

static void parse_vm_option(
        std::vector<std::string> *vmArgs,
        std::ostringstream *cp,
        std::ostringstream *modulePath,
        std::ostringstream *libraryPath,
        size_t *stack_size,
        bool *startOnFirstThread,
        std::string_view option) {
    if (IS_VM_CP_ARG(option)) {
        *cp << CP_SEP_STR << option.substr(VM_CP_ARG_OFFSET);
    } else if (IS_VM_CLASSPATH_ARG(option)) {
        *cp << CP_SEP_STR << option.substr(VM_CLASSPATH_ARG_OFFSET);
    } else if (IS_VM_P_ARG(option)) {
        *modulePath << CP_SEP_STR << option.substr(VM_P_ARG_OFFSET);
    } else if (IS_VM_MODULE_PATH_ARG(option)) {
        *modulePath << CP_SEP_STR << option.substr(VM_MODULE_PATH_ARG_OFFSET);
    } else if (IS_VM_LIBRARY_PATH_ARG(option)) {
        *libraryPath << CP_SEP_STR << option.substr(VM_LIBRARY_PATH_ARG_OFFSET);
    } else if (IS_VM_ARG_FILE_ARG(option)) {
        std::string arg_file(option.substr(VM_ARG_FILE_ARG_OFFSET));
        expand_vm_arg_file(arg_file.c_str(), vmArgs, cp, modulePath, libraryPath, stack_size);
#if defined (__APPLE__)
    } else if (IS_VM_START_ON_FIRST_THREAD(option)) {
        *startOnFirstThread = true;
#endif
    } else if (IS_VM_ARG(option)) {
        if (IS_VM_STACK_SIZE_ARG(option)) {
            *stack_size = parse_size(option.substr(VM_STACK_SIZE_ARG_OFFSET));
        }
        std::ostringstream opt;
        opt << '-' << option.substr(VM_ARG_OFFSET);
        vmArgs->push_back(opt.str());
    } else if (option == "--jvm") {
        found_switch_to_jvm_flag = true;
    }
}

enum ArgFileState {
    FIND_NEXT,
    IN_COMMENT,
    IN_QUOTE,
    IN_ESCAPE,
    SKIP_LEAD_WS,
    IN_TOKEN
};
// Parse @arg-files as handled by libjli. See libjli/args.c.
static std::optional<std::string> arg_file_next_token(std::ifstream &input) {
    ArgFileState state = FIND_NEXT;
    int currentQuoteChar = -1;
    std::istream::int_type ch;

    std::ostringstream token;
    std::ostringstream::pos_type start = token.tellp();

    while ((ch = input.get()) != std::istream::traits_type::eof()) {
        // Skip white space characters
        if (state == FIND_NEXT || state == SKIP_LEAD_WS) {
            while (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f') {
                if ((ch = input.get()) == std::istream::traits_type::eof()) {
                    goto done;
                }
            }
            state = (state == FIND_NEXT) ? IN_TOKEN : IN_QUOTE;
            // Deal with escape sequences
        } else if (state == IN_ESCAPE) {
            // concatenation directive
            if (ch == '\n' || ch == '\r') {
                state = SKIP_LEAD_WS;
            } else {
                // escaped character
                switch (ch) {
                    case 'n':
                        token << '\n';
                        break;
                    case 'r':
                        token << '\r';
                        break;
                    case 't':
                        token << '\t';
                        break;
                    case 'f':
                        token << '\f';
                        break;
                    default:
                        token << (char) ch;
                        break;
                }
                state = IN_QUOTE;
            }
            continue;
            // ignore comment to EOL
        } else if (state == IN_COMMENT) {
            while (ch != '\n' && ch != '\r') {
                if ((ch = input.get()) == std::istream::traits_type::eof()) {
                    goto done;
                }
            }
            state = FIND_NEXT;
            continue;
        }

        assert(state != IN_ESCAPE);
        assert(state != FIND_NEXT);
        assert(state != SKIP_LEAD_WS);
        assert(state != IN_COMMENT);

        switch (ch) {
            case ' ':
            case '\t':
            case '\f':
                if (state == IN_QUOTE) {
                    token << (char) ch;
                    continue;
                }
                // fallthrough
            case '\n':
            case '\r':
                return token.str();
            case '#':
                if (state == IN_QUOTE) {
                    token << (char) ch;
                    continue;
                }
                state = IN_COMMENT;
                break;
            case '\\':
                if (state != IN_QUOTE) {
                    token << (char) ch;
                    continue;
                }
                state = IN_ESCAPE;
                break;
            case '\'':
            case '"':
                if (state == IN_QUOTE && currentQuoteChar != ch) {
                    // not matching quote
                    token << (char) ch;
                    continue;
                }
                if (state == IN_TOKEN) {
                    currentQuoteChar = ch;
                    state = IN_QUOTE;
                } else {
                    state = IN_TOKEN;
                }
                break;
            default:
                token << (char) ch;
                break;
        }
    }
done:
    if (token.tellp() == start) {
        return {};
    }
    return token.str();
}

static void expand_vm_arg_file(const char *arg_file,
                               std::vector<std::string> *vmArgs,
                               std::ostringstream *cp,
                               std::ostringstream *modulePath,
                               std::ostringstream *libraryPath,
                               size_t* stack_size) {
    if (debug) {
        std::cout << "Expanding VM arg file " << arg_file << std::endl;
    }
    std::ifstream input(arg_file);
    if (input.fail()) {
        std::cerr << "Error: could not open `" << arg_file << "': " << strerror(errno) << std::endl;
        exit(EXIT_FAILURE);
    }

    while (true) {
        std::optional<std::string> token = arg_file_next_token(input);
        if (token.has_value()) {
            if (STARTS_WITH(token.value(), "--class-path=")) {
                *cp << CP_SEP_STR << token.value().substr(sizeof("--class-path=") - 1);
            } else if (STARTS_WITH(token.value(), "--module-path=")) {
                *modulePath << CP_SEP_STR << token.value().substr(sizeof("--module-path=") - 1);
            } else if (STARTS_WITH(token.value(), "-Djava.library.path=")) {
                *libraryPath << CP_SEP_STR << token.value().substr(sizeof("-Djava.library.path=") - 1);
            } else {
                if (STARTS_WITH(token.value(), "-Xss")) {
                    *stack_size = parse_size(token.value().substr(sizeof("-Xss") - 1));
                }
                vmArgs->push_back(token.value());
            }
        } else {
            break;
        }
    }
}

struct MainThreadArgs {
    int argc;
    char **argv;
    std::string exeDir;
    bool jvmMode;
    std::string libPath;
    size_t stack_size{};
    bool startOnFirstThread;
    std::vector<std::string> vmArgs;
    std::vector<std::string> optionVarsArgs;
};

/* parse the VM arguments that should be passed to JNI_CreateJavaVM */
static void parse_vm_options(struct MainThreadArgs& parsedArgs) {
    auto& [argc, argv, exeDir, jvmMode, _, stack_size, startOnFirstThread, vmArgs, optionVarsArgs] = parsedArgs;

    /* check if vm args have been set on relaunch already */
    int vmArgCount = 0;
    char *vmArgInfo = getenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS");
    if (vmArgInfo != NULL) {
        relaunch = true;
        vmArgCount = strtol(vmArgInfo, NULL, 10);
        // clear the env variable
        setenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS", "");
    }

    /* set optional vm args from LanguageLibraryConfig.option_vars */
    #ifdef LAUNCHER_OPTION_VARS
        const char *launcherOptionVars[] = LAUNCHER_OPTION_VARS;
    #endif

    /* set system properties */
    if (jvmMode) {
        /* this is only needed for jvm mode */
        vmArgs.push_back("-Dorg.graalvm.launcher.class=" LAUNCHER_CLASS_STR);
    }
    vmArgs.push_back("-Dorg.graalvm.version=" GRAALVM_VERSION_STR);

    std::ostringstream executablename;
    executablename << "-Dorg.graalvm.launcher.executablename=";
    char *executablenameEnv = getenv("GRAALVM_LAUNCHER_EXECUTABLE_NAME");
    if (executablenameEnv) {
        executablename << executablenameEnv;
        setenv("GRAALVM_LAUNCHER_EXECUTABLE_NAME", "");
    } else {
        executablename << argv[0];
    }
    vmArgs.push_back(executablename.str());
    if (debug) {
        std::cout<< "org.graalvm.launcher.executablename set to '" << executablename.str() << '\'' << std::endl;
    }

    /* construct classpath - only needed for jvm mode */
    std::ostringstream cp;

    /* construct module path - only needed for jvm mode */
    std::ostringstream modulePath;
    modulePath << "--module-path=";
    #ifdef LAUNCHER_MODULE_PATH
    if (jvmMode) {
        /* add the launcher module path */
        const char *launcherModulePathEntries[] = LAUNCHER_MODULE_PATH;
        int launcherModulePathCnt = sizeof(launcherModulePathEntries) / sizeof(*launcherModulePathEntries);
        for (int i = 0; i < launcherModulePathCnt; i++) {
            std::ostringstream entry;
            entry << exeDir << DIR_SEP_STR << launcherModulePathEntries[i];
            modulePath << canonicalize(entry.str());
            if (i < launcherModulePathCnt-1) {
                modulePath << CP_SEP_STR;
            }
        }
    }
    #endif


    #if defined(LANGUAGES_DIR) && defined(TOOLS_DIR)
    if (jvmMode) {
        /* Add languages and tools to module path */
        const char* dirs[] = { LANGUAGES_DIR_STR, TOOLS_DIR_STR };
        for (int i = 0; i < 2; i++) {
            const char* relativeDir = dirs[i];
            std::ostringstream absoluteDirStream;
            absoluteDirStream << exeDir << DIR_SEP_STR << relativeDir;
            std::string absoluteDir = absoluteDirStream.str();

            #ifndef _WIN32
            DIR* dir = opendir(absoluteDir.c_str());
            if (dir) {
                std::string canonicalDir = canonicalize(absoluteDir);
                struct dirent* entry;
                while ((entry = readdir(dir))) {
                    char* name = entry->d_name;
                    if (name[0] != '.') {
                        modulePath << CP_SEP_STR << canonicalDir << DIR_SEP_STR << name;
                    }
                }
                closedir(dir);
            }
            #else
            // From https://learn.microsoft.com/en-us/windows/win32/fileio/listing-the-files-in-a-directory
            // and https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-findfirstfilea
            WIN32_FIND_DATAA entry;
            std::ostringstream searchDir;
            searchDir << absoluteDir << "\\*";
            HANDLE dir = FindFirstFileA(searchDir.str().c_str(), &entry);
            if (dir != INVALID_HANDLE_VALUE) {
                std::string canonicalDir = canonicalize(absoluteDir);
                do {
                    char* name = entry.cFileName;
                    if (name[0] != '.') {
                        modulePath << CP_SEP_STR << canonicalDir << DIR_SEP_STR << name;
                    }
                } while (FindNextFileA(dir, &entry));
                FindClose(dir);
            }
            #endif
        }
    }
    #endif

    /* construct java.library.path - only needed for jvm mode */
    std::ostringstream libraryPath;
    #ifdef LAUNCHER_LIBRARY_PATH
    if (jvmMode) {
        /* add the library path */
        const char *launcherLibraryPathEntries[] = LAUNCHER_LIBRARY_PATH;
        int launcherLibraryPathCnt = sizeof(launcherLibraryPathEntries) / sizeof(*launcherLibraryPathEntries);
        for (int i = 0; i < launcherLibraryPathCnt; i++) {
            libraryPath << CP_SEP_STR << exeDir << DIR_SEP_STR << launcherLibraryPathEntries[i];
        }
    }
    #endif

    #if defined(LAUNCHER_LANG_HOME_NAMES) && defined(LAUNCHER_LANG_HOME_PATHS)
    const char *launcherLangHomeNames[] = LAUNCHER_LANG_HOME_NAMES;
    const char *launcherLangHomePaths[] = LAUNCHER_LANG_HOME_PATHS;
    int launcherLangHomeNamesCnt = sizeof(launcherLangHomeNames) / sizeof(*launcherLangHomeNames);
    for (int i = 0; i < launcherLangHomeNamesCnt; i++) {
        std::ostringstream ss;
        std::ostringstream relativeHome;
        relativeHome << exeDir << DIR_SEP_STR << launcherLangHomePaths[i];
        ss << "-Dorg.graalvm.language." << launcherLangHomeNames[i] << ".home=" << canonicalize(relativeHome.str());
        vmArgs.push_back(ss.str());
    }
    #endif

    #if defined(LAUNCHER_EXTRACTED_LIB_NAMES) && defined(LAUNCHER_EXTRACTED_LIB_PATHS)
    if (jvmMode) {
        const char *extractedLibNames[] = LAUNCHER_EXTRACTED_LIB_NAMES;
        const char *extractedLibPaths[] = LAUNCHER_EXTRACTED_LIB_PATHS;
        int extractedLibCnt = sizeof(extractedLibNames) / sizeof(*extractedLibNames);
        for (int i = 0; i < extractedLibCnt; i++) {
            std::ostringstream ss;
            std::ostringstream relativePath;
            relativePath << exeDir << DIR_SEP_STR << extractedLibPaths[i];
            ss << "-D" << extractedLibNames[i] << "=" << canonicalize(relativePath.str());
            vmArgs.push_back(ss.str());
        }
    }
    #endif

    /* Handle launcher default vm arguments. We apply these first, so they can
       be overridden by explicit arguments on the commandline.
       These should be added even if relaunch is true because they are not passed to preprocessArguments(). */
    #ifdef LAUNCHER_DEFAULT_VM_ARGS
    const char *launcherDefaultVmArgs[] = LAUNCHER_DEFAULT_VM_ARGS;
    for (int i = 0; i < sizeof(launcherDefaultVmArgs)/sizeof(char*); i++) {
        if (IS_VM_ARG(std::string(launcherDefaultVmArgs[i]))) {
            parse_vm_option(&vmArgs, &cp, &modulePath, &libraryPath, &stack_size, &startOnFirstThread, launcherDefaultVmArgs[i]);
        }
    }
    #endif


    if (!relaunch) {
        /* handle CLI arguments */
        for (int i = 1; i < argc; i++) {
            parse_vm_option(&vmArgs, &cp, &modulePath, &libraryPath, &stack_size, &startOnFirstThread, argv[i]);
        }

        /* handle optional vm args from LanguageLibraryConfig.option_vars */
        #ifdef LAUNCHER_OPTION_VARS
        for (int i = 0; i < sizeof(launcherOptionVars)/sizeof(char*); i++) {
            char *optionVar = getenv(launcherOptionVars[i]);
            if (!optionVar) {
                continue;
            }
            if (debug) {
                std::cout << "Launcher option_var found: " << launcherOptionVars[i] << "=" << optionVar << std::endl;
            }
            // we split on spaces
            std::string optionLine(optionVar);
            size_t last = 0;
            size_t next = 0;
            std::string option;
            while ((next = optionLine.find(" ", last)) != std::string::npos) {
                option = optionLine.substr(last, next-last);
                optionVarsArgs.push_back(option);
                parse_vm_option(&vmArgs, &cp, &modulePath, &libraryPath, &stack_size, &startOnFirstThread, option);
                last = next + 1;
            };
            option = optionLine.substr(last);
            optionVarsArgs.push_back(option);
            parse_vm_option(&vmArgs, &cp, &modulePath, &libraryPath, &stack_size, &startOnFirstThread, option);
        }
        #endif
    } else {
        /* Handle relaunch arguments. In that case GRAALVM_LANGUAGE_LAUNCHER_VMARGS_* contain all --vm.* arguments
           returned by preprocessArguments(), so we should not look at CLI args and option_vars as that would cause
           to add extra duplicate --vm.* arguments. */
        if (debug) {
            std::cout << "Relaunch environment variable detected" << std::endl;
        }
        for (int i = 0; i < vmArgCount; i++) {
            std::ostringstream ss;
            ss << "GRAALVM_LANGUAGE_LAUNCHER_VMARGS_" << i;
            std::string envKey = ss.str();
            char *cur = getenv(envKey.c_str());
            if (!cur) {
                std::cerr << "VM arguments specified: " << vmArgCount << " but argument " << i << "missing" << std::endl;
                break;
            }
            parse_vm_option(&vmArgs, &cp, &modulePath, &libraryPath, &stack_size, &startOnFirstThread, cur);
            /* clean up env variable */
            setenv(envKey, "");
        }
    }

    /* set classpath and module path arguments - only needed for jvm mode */
    if (jvmMode) {
        if (!cp.str().empty()) {
            vmArgs.push_back("-Djava.class.path=" + cp.str().substr(1));
        }
        if (!libraryPath.str().empty()) {
            vmArgs.push_back("-Djava.library.path=" + libraryPath.str().substr(1));
        }
#ifdef LAUNCHER_MODULE_PATH
        vmArgs.push_back(modulePath.str());
        vmArgs.push_back("-Djdk.module.main=" LAUNCHER_MAIN_MODULE_STR);
        vmArgs.push_back("-Dgraalvm.locatorDisabled=true");
#endif

        /* Allow Truffle NFI Panama to use Linker#{downcallHandle,upcallStub} without warnings. */
        vmArgs.push_back("--enable-native-access=org.graalvm.truffle");
    }
}

static int jvm_main_thread(struct MainThreadArgs& mainArgs);
static size_t current_thread_stack_size();
#ifndef _WIN32
static int setstacksize(pthread_attr_t* attr, size_t stack_size);
#endif

#if defined (__APPLE__)
/*
 * See: https://github.com/openjdk/jdk/blob/8c1b915c7ef2b3a6e65705b91f4eb464caaec4e7/src/java.base/macosx/native/libjli/java_md_macosx.m#L277-L290
 */
static void dummyTimer(CFRunLoopTimerRef timer, void *info) {}

static void ParkEventLoop() {
    // RunLoop needs at least one source, and 1e20 is pretty far into the future
    CFRunLoopTimerRef t = CFRunLoopTimerCreate(kCFAllocatorDefault, 1.0e20, 0.0, 0, 0, dummyTimer, NULL);
    CFRunLoopAddTimer(CFRunLoopGetCurrent(), t, kCFRunLoopDefaultMode);
    CFRelease(t);

    // Park this thread in the main run loop.
    int32_t result;
    do {
        result = CFRunLoopRunInMode(kCFRunLoopDefaultMode, 1.0e20, false);
    } while (result != kCFRunLoopRunFinished);
}
#endif /* __APPLE__ */

#ifndef _WIN32
static void *jvm_main_thread_start(void *arg)
{
    std::unique_ptr<struct MainThreadArgs> args(static_cast<struct MainThreadArgs*>(arg));
    int ret = jvm_main_thread(*args);
#if defined(__APPLE__)
    exit(ret);
#endif
    return (void*)(intptr_t)ret;
}
#else /* defined(_WIN32) */
static unsigned __stdcall jvm_main_thread_start(void *arg)
{
    std::unique_ptr<struct MainThreadArgs> args(static_cast<struct MainThreadArgs*>(arg));
    int ret = jvm_main_thread(*args);
    return (unsigned)ret;
}
#endif

int main(int argc, char *argv[]) {
    debug = (getenv("VERBOSE_GRAALVM_LAUNCHERS") != NULL);
    std::string exeDir = exe_directory();
    char* jvmModeEnv = getenv("GRAALVM_LAUNCHER_FORCE_JVM");
    bool jvmMode = (jvmModeEnv && (strcmp(jvmModeEnv, "true") == 0));
#ifndef LIBLANG_RELPATH
    if (jvmModeEnv && !jvmMode) {
        std::cerr << "Cannot run in native mode from jvm-only launcher" << std::endl;
        return -1;
    }
    jvmMode = true;
#endif

    std::string libPath = vm_path(exeDir, jvmMode);

    /* check if the VM library exists */
    if (!jvmMode) {
        if (!exists(libPath)) {
            /* switch to JVM mode */
            libPath = vm_path(exeDir, true);
            jvmMode = true;
        }
    }


    /* parse VM args */
    struct MainThreadArgs parsedArgs{argc, argv, exeDir, jvmMode, libPath, 0, false};
    parse_vm_options(parsedArgs);
    size_t stack_size = parsedArgs.stack_size;

    /*
     * If -Xss is greater than the os-allocated stack size of the main thread,
     * create a new "main" thread for the JVM with increased stack size.
     *
     * Unlike the `java` launcher, which always creates a new thread for the JVM by default [1],
     * we create a new thread only if needed; otherwise, we attach the JVM to the main thread.
     * On macOS, it is always needed because the actual main thread must run the UI event loop [2].
     *
     * [1] https://github.com/openjdk/jdk/blob/8c1b915c7ef2b3a6e65705b91f4eb464caaec4e7/src/java.base/unix/native/libjli/java_md.c#L114
     * [2] https://github.com/openjdk/jdk/blob/8c1b915c7ef2b3a6e65705b91f4eb464caaec4e7/src/java.base/macosx/native/libjli/java_md_macosx.m#L292-L325
     */
    size_t main_thread_stack_size = current_thread_stack_size();
    bool use_new_thread = stack_size > main_thread_stack_size;
#if defined (__APPLE__)
    /* On macOS, default to creating a dedicated "main" thread for the JVM.
     * The actual main thread must run the UI event loop (needed for AWT).
     * It can be overridden with -XstartOnFirstThread, this is needed to
     * use other UI frameworks that *do* need to run on the main thread.
     */
    use_new_thread = !parsedArgs.startOnFirstThread;

    if (jvmMode) {
        if (!load_jli_lib(exeDir)) {
            std::cerr << "Loading libjli failed." << std::endl;
            return -1;
        }
    }
#endif

    if (use_new_thread) {
        auto threadArgs = std::make_unique<decltype(parsedArgs)>(std::move(parsedArgs));
        if (debug) {
            std::cout << "Creating a new thread for the JVM with stack_size=" << stack_size << " main_thread_stack_size=" << main_thread_stack_size << std::endl;
        }

#ifndef _WIN32
        pthread_attr_t attr;
        if (pthread_attr_init(&attr) != 0) {
            std::cerr << "Could not initialize pthread attribute structure: " << strerror(errno) << std::endl;
            return -1;
        }
        if (setstacksize(&attr, stack_size) != 0) {
            std::cerr << "Could not set stack size in pthread attribute structure to " << stack_size << " bytes." << std::endl;
            return -1;
        }

        // See: https://github.com/openjdk/jdk/blob/8c1b915c7ef2b3a6e65705b91f4eb464caaec4e7/src/java.base/unix/native/libjli/java_md.c#L685
        pthread_attr_setguardsize(&attr, 0); // no pthread guard page on java threads

        pthread_t main_thr;
        if (pthread_create(&main_thr, &attr, &jvm_main_thread_start, threadArgs.get()) != 0) {
            std::cerr << "Could not create main thread: " << strerror(errno) << std::endl;
            pthread_attr_destroy(&attr);
            return -1;
        }

        // ownership transferred to new thread
        threadArgs.release();
        pthread_attr_destroy(&attr);

#if defined(__APPLE__)
        if (pthread_detach(main_thr)) {
            std::cerr << "pthread_detach() failed: " << strerror(errno) << std::endl;
            return -1;
        }

        ParkEventLoop();
        return 0;
#else
        void* retval;
        if (pthread_join(main_thr, &retval)) {
            std::cerr << "pthread_join() failed: " << strerror(errno) << std::endl;
            return -1;
        }
        return (int)(intptr_t)retval;
#endif
#else /* defined(_WIN32) */
        uintptr_t hThread = _beginthreadex(nullptr, stack_size, &jvm_main_thread_start, threadArgs.get(), STACK_SIZE_PARAM_IS_A_RESERVATION, nullptr);
        if (hThread == 0) {
            std::cerr << "_beginthreadex() failed: " << GetLastError() << std::endl;
            return -1;
        }

        // ownership transferred to new thread
        threadArgs.release();

        // join thread
        DWORD exitCode = 0;
        WaitForSingleObject((HANDLE)hThread, INFINITE);
        GetExitCodeThread((HANDLE)hThread, &exitCode);
        CloseHandle((HANDLE)hThread);
        return (int)exitCode;
#endif
    }
    return jvm_main_thread(parsedArgs);
}

static int jvm_main_thread(struct MainThreadArgs& parsedArgs) {
    auto& [argc, argv, _, jvmMode, libPath, stack_size, startOnFirstThread, vmArgs, optionVarsArgs] = parsedArgs;

    /* load VM library - after parsing arguments s.t. NMT
     * tracking environment variables are already set */
    void* library = load_vm_lib(libPath);
    if (!library) {
        std::cerr << "Could not load VM library from " << libPath << "." << std::endl;
        return -1;
    }

    if (jvmMode) {
        GetDefaultJavaVMInitArgs_type getDefaultJavaVMInitArgs = (GetDefaultJavaVMInitArgs_type) get_function(library, "JNI_GetDefaultJavaVMInitArgs");
        if (!getDefaultJavaVMInitArgs) {
            std::cerr << "Could not find JNI_GetDefaultJavaVMInitArgs." << std::endl;
            return -1;
        }

        #ifndef JNI_VERSION_24
        #define JNI_VERSION_24 0x00180000
        #endif

        JavaVMInitArgs defaultArgs;
        defaultArgs.version = JNI_VERSION_24;
        defaultArgs.nOptions = 0;
        defaultArgs.options = NULL;
        defaultArgs.ignoreUnrecognized = false;
        bool jdk24_or_higher = getDefaultJavaVMInitArgs(&defaultArgs) == JNI_OK;

        if (jdk24_or_higher) {
            // GR-59703: Migrate sun.misc.* usages.
            vmArgs.push_back("--sun-misc-unsafe-memory-access=allow");
        }
    }

    // Convert vmArgs to JavaVMInitArgs
    jint nOptions = jvmMode ? vmArgs.size() : 1 + vmArgs.size();
    JavaVMOption *options = (JavaVMOption*) calloc(nOptions, sizeof(JavaVMOption));
    JavaVMOption *curOpt = options;

    const char *svm_error = NULL;
    if (!jvmMode) {
        curOpt->optionString = strdup("_createvm_errorstr");
        curOpt->extraInfo = &svm_error;
        curOpt++;
    }

    for (const auto& arg: vmArgs) {
        if (debug) {
            std::cout << "Setting VM argument " << arg << std::endl;
        }
        /* env variable for native memory tracking (NMT), obsolete with JDK 18 */
        size_t nmtPos = arg.find(NMT_ARG_NAME);
        if (nmtPos != std::string::npos) {
            nmtPos += sizeof(NMT_ARG_NAME);
            std::string val = arg.substr(nmtPos);
            std::string pid = std::to_string(getpid());
            setenv(std::string(NMT_ENV_NAME) + pid, val);
        }
        curOpt->optionString = strdup(arg.c_str());
        curOpt++;
    }

    CreateJavaVM_type createVM = (CreateJavaVM_type) get_function(library, "JNI_CreateJavaVM");
    if (!createVM) {
        std::cerr << "Could not find JNI_CreateJavaVM." << std::endl;
        return -1;
    }

    JavaVMInitArgs vmInitArgs;
    vmInitArgs.version = JNI_VERSION_9;
    vmInitArgs.nOptions = nOptions;
    vmInitArgs.options = options;
    /* In general we want to validate VM arguments.
     * But we must disable it for the case there is a native library and we saw a --jvm argument,
     * as the VM arguments are then JVM VM arguments and not SVM VM arguments.
     * In that case we validate them after the execve() when running in --jvm mode. */
    vmInitArgs.ignoreUnrecognized = found_switch_to_jvm_flag && !jvmMode;

    JavaVM *vm;
    JNIEnv *env;
    if (createVM(&vm, (void**)&env, &vmInitArgs) != JNI_OK) {
        if (svm_error != NULL) {
            std::cerr << svm_error << std::endl;
            free((void*) svm_error);
            svm_error = NULL;
        }
        std::cerr << "JNI_CreateJavaVM() failed." << std::endl;
        return -1;
    }

    /* free the allocated vm arguments */
    for (int i = 0; i < nOptions; i++) {
        free(options[i].optionString);
    }
    free(options);

    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == NULL) {
        std::cerr << "Byte array class not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jclass relaunchExceptionClass = env->FindClass("org/graalvm/launcher/AbstractLanguageLauncher$RelaunchException");
    if (relaunchExceptionClass == NULL) {
        std::cerr << "RelaunchException class not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jclass launcherClass = env->FindClass("org/graalvm/launcher/AbstractLanguageLauncher");
    if (launcherClass == NULL) {
        std::cerr << "Launcher class not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jmethodID runLauncherMid = env->GetStaticMethodID(launcherClass, "runLauncher", "([[B[[BIJZ)V");
    if (runLauncherMid == NULL) {
        std::cerr << "Launcher entry point not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jfieldID vmArgsFid = env->GetFieldID(relaunchExceptionClass, "vmArgs", "[Ljava/lang/String;");
    if (vmArgsFid == NULL) {
        std::cerr << "RelaunchException vm args field not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }

    /* backup native args */
    char** argv_native = argv;
    int argc_native = argc;

    argv++;
    argc--;

    /* create args string array */
    jobjectArray args = env->NewObjectArray(argc, byteArrayClass, NULL);
    for (int i = 0; i < argc; i++) {
        int arraySize = strlen(argv[i]);
        jbyteArray arg = env->NewByteArray(arraySize);
        env->SetByteArrayRegion(arg, 0, arraySize, (jbyte *)(argv[i]));
        if (env->ExceptionCheck()) {
            std::cerr << "Error in SetByteArrayRegion:" << std::endl;
            env->ExceptionDescribe();
            return -1;
        }
        env->SetObjectArrayElement(args, i, arg);
        if (env->ExceptionCheck()) {
            std::cerr << "Error in SetObjectArrayElement:" << std::endl;
            env->ExceptionDescribe();
            return -1;
        }
    }

    /* create env var args string array */
    jobjectArray optionVarsArgsArray = env->NewObjectArray(optionVarsArgs.size(), byteArrayClass, NULL);
    for (int i = 0; i < optionVarsArgs.size(); i++) {
        std::string argString = optionVarsArgs[i];
        jbyteArray arg = env->NewByteArray(argString.length());
        env->SetByteArrayRegion(arg, 0, argString.length(), (jbyte *)(argString.c_str()));
        if (env->ExceptionCheck()) {
            std::cerr << "Error in SetByteArrayRegion:" << std::endl;
            env->ExceptionDescribe();
            return -1;
        }
        env->SetObjectArrayElement(optionVarsArgsArray, i, arg);
        if (env->ExceptionCheck()) {
            std::cerr << "Error in SetObjectArrayElement:" << std::endl;
            env->ExceptionDescribe();
            return -1;
        }
    }

    /* invoke launcher entry point */
    jlong argv_native_long = (jlong)(uintptr_t)(void*)argv_native;
    env->CallStaticVoidMethod(launcherClass, runLauncherMid, optionVarsArgsArray, args, argc_native, argv_native_long, relaunch);
    jthrowable t = env->ExceptionOccurred();
    if (t) {
        if (env->IsInstanceOf(t, relaunchExceptionClass)) {
            if (debug) {
                std::cout << "Relaunch exception has been thrown" << std::endl;
            }
            env->ExceptionClear();
            // read correct VM arguments from exception
            jobjectArray vmArgs = (jobjectArray)env->GetObjectField(t, vmArgsFid);
            if (env->ExceptionCheck()) {
                std::cerr << "Error in GetObjectField:" << std::endl;
                env->ExceptionDescribe();
                return -1;
            }
            jint vmArgCount = env->GetArrayLength(vmArgs);
            if (env->ExceptionCheck()) {
                std::cerr << "Error in GetArrayLength:" << std::endl;
                env->ExceptionDescribe();
                return -1;
            }
            if (debug) {
                std::cout << "Relaunch VM arguments read: " << vmArgCount << std::endl;
            }
            std::ostringstream ss;
            ss << vmArgCount;
            if (setenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS", ss.str()) == -1) {
                return -1;
            }
            for (int i = 0; i < vmArgCount; i++) {
                jstring vmArgString = (jstring)env->GetObjectArrayElement(vmArgs, i);
                if (env->ExceptionCheck()) {
                    std::cerr << "Error in GetObjectArrayElement:" << std::endl;
                    env->ExceptionDescribe();
                    return -1;
                }
                const char *vmArg = env->GetStringUTFChars(vmArgString, NULL);
                if (env->ExceptionCheck()) {
                    std::cerr << "Error in GetStringUTFChars:" << std::endl;
                    env->ExceptionDescribe();
                    return -1;
                }
                /* set environment variables for relaunch vm arguments */
                std::ostringstream key;
                key << "GRAALVM_LANGUAGE_LAUNCHER_VMARGS_" << i;
                std::string arg(vmArg);
                if (setenv(key.str(), arg) == -1) {
                    return -1;
                }
            }
            /* relaunch with correct VM arguments */
            std::string path = exe_path();
            execve(path.c_str(), argv_native, environ);
            /* if we reach here, execve failed for sure */
            perror("execve failed");
            return -1;
        }
        env->ExceptionDescribe();
        return -1;
    }
	return 0;
}

/**
 * Parses the size part of -Xss, -Xmx, etc. options.
 * Returns the parsed size in bytes or 0 if invalid.
 * Inspired by: https://github.com/openjdk/jdk/blob/8c1b915c7ef2b3a6e65705b91f4eb464caaec4e7/src/java.base/share/native/libjli/java.c#L920-L954
 */
static size_t parse_size(std::string_view str) {
    size_t n = 0;
    size_t pos = 0;
    while (pos < str.size() && str[pos] >= '0' && str[pos] <= '9') {
        int digit = str[pos] - '0';
        n = n * 10 + digit;
        pos++;
    }
    if (pos == 0 || str.size() - pos != 1) {
        // invalid
        return 0;
    }
    size_t multiplier = 1;
    switch (str[pos]) {
        case 'T':
        case 't':
            multiplier *= 1024;
            [[fallthrough]];
        case 'G':
        case 'g':
            multiplier *= 1024;
            [[fallthrough]];
        case 'M':
        case 'm':
            multiplier *= 1024;
            [[fallthrough]];
        case 'K':
        case 'k':
            multiplier *= 1024;
            break;
        case '\0':
            break;
        default:
            // invalid
            return 0;
    }
    return n * multiplier;
}

#ifndef _WIN32
static size_t round_to_pagesize(size_t stack_size) {
    long page_size = sysconf(_SC_PAGESIZE);
    size_t remainder = stack_size % page_size;
    if (remainder == 0) {
        return stack_size;
    } else {
        // Round up to the next full page
        return stack_size - remainder + (stack_size <= SIZE_MAX - page_size ? page_size : 0);
    }
}

/**
 * Sets the requested thread stack size (0 = default).
 */
static int setstacksize(pthread_attr_t* attr, size_t stack_size) {
    int result = 0;
    // See: https://github.com/openjdk/jdk/blob/8c1b915c7ef2b3a6e65705b91f4eb464caaec4e7/src/java.base/unix/native/libjli/java_md.c#L675-L684
    if (stack_size > 0) {
        result = pthread_attr_setstacksize(attr, stack_size);
        if (result == EINVAL) {
            // System may require stack size to be multiple of page size
            // Retry with adjusted value
            size_t adjusted_stack_size = round_to_pagesize(stack_size);
            if (adjusted_stack_size != stack_size) {
                result = pthread_attr_setstacksize(attr, adjusted_stack_size);
            }
        }
    } else {
#if defined(__APPLE__)
        /* Inherit stacksize of the main thread. Otherwise pthread_create()
         * defaults to 512K on darwin, while the main thread has usually ~8M.
         */
        result = pthread_attr_setstacksize(attr, current_thread_stack_size());
#endif
    }
    return result;
}
#endif /* !defined(_WIN32) */

/**
 * Returns the stack size of the current thread, or 0 if not supported.
 */
static size_t current_thread_stack_size() {
    size_t current_thread_stack_size = 0;
#if defined(__APPLE__)
    current_thread_stack_size = pthread_get_stacksize_np(pthread_self());
#elif defined(__linux__)
    pthread_attr_t attr;
    void *stack_addr;
    if (pthread_getattr_np(pthread_self(), &attr) == 0) {
        pthread_attr_getstack(&attr, &stack_addr, &current_thread_stack_size);
        pthread_attr_destroy(&attr);
    }
#elif defined(_WIN32)
    // Windows 8+: GetCurrentThreadStackLimits(&low, &high);
    MEMORY_BASIC_INFORMATION mbi{};
    VirtualQuery(&mbi, &mbi, sizeof(mbi));
    NT_TIB* tib = (NT_TIB*)NtCurrentTeb();
    uintptr_t low = (uintptr_t)mbi.AllocationBase;
    uintptr_t high = (uintptr_t)tib->StackBase;
    current_thread_stack_size = high - low;
#endif
    return current_thread_stack_size;
}
