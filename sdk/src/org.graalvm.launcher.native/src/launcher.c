/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

// for pthread_getattr_np() on Linux
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <jni.h>

#include <stdint.h>
#include <string.h>
#include <limits.h>
#include <stdlib.h>

#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>

#include <assert.h>

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

#define STARTS_WITH(ARG, PREFIX) (strncmp(ARG, PREFIX, strlen(PREFIX)) == 0)
#define IS_VM_ARG(ARG) STARTS_WITH(ARG, VM_ARG_PREFIX)
#define IS_VM_CP_ARG(ARG) STARTS_WITH(ARG, VM_CP_ARG_PREFIX)
#define IS_VM_CLASSPATH_ARG(ARG) STARTS_WITH(ARG, VM_CLASSPATH_ARG_PREFIX)
#define IS_VM_P_ARG(ARG) STARTS_WITH(ARG, VM_P_ARG_PREFIX)
#define IS_VM_MODULE_PATH_ARG(ARG) STARTS_WITH(ARG, VM_MODULE_PATH_ARG_PREFIX)
#define IS_VM_LIBRARY_PATH_ARG(ARG) STARTS_WITH(ARG, VM_LIBRARY_PATH_ARG_PREFIX)
#define IS_VM_STACK_SIZE_ARG(ARG) STARTS_WITH(ARG, VM_STACK_SIZE_ARG_PREFIX)
#define IS_VM_ARG_FILE_ARG(ARG) STARTS_WITH(ARG, VM_ARG_FILE_ARG_PREFIX)
#define IS_VM_START_ON_FIRST_THREAD(ARG) (strcmp(ARG, "--vm.XstartOnFirstThread") == 0)

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
    #include <pthread.h>
    #include <sys/types.h>
    #include <sys/stat.h>
#elif defined (__APPLE__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <unistd.h>
    #include <errno.h>
    #include <dirent.h>
    #include <pthread.h>
    #include <mach-o/dyld.h>
    #include <sys/syslimits.h>
    #include <sys/stat.h>

    #ifndef LIBJLI_RELPATH
        #error path to jli library undefined
    #endif
    #define LIBJLI_RELPATH_STR STR(LIBJLI_RELPATH)

    /* Support Cocoa event loop on the main thread */
    #include <CoreFoundation/CoreFoundation.h>

#elif defined (_WIN32)
    #pragma push_macro("NOMINMAX")
    #pragma push_macro("WIN32_LEAN_AND_MEAN")
    #define NOMINMAX
    #define WIN32_LEAN_AND_MEAN
    #include <windows.h>
    #pragma pop_macro("WIN32_LEAN_AND_MEAN")
    #pragma pop_macro("NOMINMAX")
    #include <libloaderapi.h>
    #include <process.h>
    #include <errno.h>
    #include <sys/types.h>
    #include <sys/stat.h>
    #define getpid _getpid
    #define execv _execv
    #define stat _stat
#else
    #error platform not supported or undefined
#endif

// jint JNI_CreateJavaVM(JavaVM **p_vm, void **p_env, void *vm_args);
typedef jint(*CreateJavaVM_type)(JavaVM **, void **, void *);
// jint JNI_GetDefaultJavaVMInitArgs(void *vm_args);
typedef jint(*GetDefaultJavaVMInitArgs_type)(void *);

static bool debug = false;
static bool relaunch = false;
static bool found_switch_to_jvm_flag = false;

/* The x* functions exit with an error message when out of memory, so callers do not need to check for NULL.
 * xstrdup() and xstrndup() are also needed because strdup() is a deprecated name on Windows
 * and strndup() does not exist there. */
static void *xmalloc(size_t size) {
    void *p = malloc(size);
    if (p == NULL) {
        fprintf(stderr, "Out of memory\n");
        exit(EXIT_FAILURE);
    }
    return p;
}

static void *xrealloc(void *ptr, size_t size) {
    void *p = realloc(ptr, size);
    if (p == NULL) {
        fprintf(stderr, "Out of memory\n");
        exit(EXIT_FAILURE);
    }
    return p;
}

/* like strndup(): copies at most len characters of str */
static char *xstrndup(const char *str, size_t len) {
    const char *end = (const char *) memchr(str, '\0', len);
    if (end != NULL) {
        len = end - str;
    }
    char *copy = (char *) xmalloc(len + 1);
    memcpy(copy, str, len);
    copy[len] = '\0';
    return copy;
}

static char *xstrdup(const char *str) {
    return xstrndup(str, strlen(str));
}

/* like sprintf() but returns a newly-allocated string */
static char *str_vformat(const char *format, va_list args) {
    va_list args_copy;
    va_copy(args_copy, args);
    int len = vsnprintf(NULL, 0, format, args_copy);
    va_end(args_copy);
    if (len < 0) {
        fprintf(stderr, "vsnprintf failed\n");
        exit(EXIT_FAILURE);
    }
    char *str = (char *) xmalloc(len + 1);
    vsnprintf(str, len + 1, format, args);
    return str;
}

static char *str_format(const char *format, ...) {
    va_list args;
    va_start(args, format);
    char *str = str_vformat(format, args);
    va_end(args);
    return str;
}

/* a growable string */
struct strbuf {
    char *data; /* always NUL-terminated */
    size_t len;
    size_t capacity;
};

static struct strbuf strbuf_new() {
    struct strbuf buf = { (char *) xmalloc(64), 0, 64 };
    buf.data[0] = '\0';
    return buf;
}

static void strbuf_append(struct strbuf *buf, const char *str, size_t len) {
    if (buf->len + len + 1 > buf->capacity) {
        buf->capacity = (buf->len + len + 1) * 2;
        buf->data = (char *) xrealloc(buf->data, buf->capacity);
    }
    memcpy(buf->data + buf->len, str, len);
    buf->len += len;
    buf->data[buf->len] = '\0';
}

static void strbuf_append_char(struct strbuf *buf, char c) {
    strbuf_append(buf, &c, 1);
}

static void strbuf_appendf(struct strbuf *buf, const char *format, ...) {
    va_list args;
    va_start(args, format);
    char *str = str_vformat(format, args);
    va_end(args);
    strbuf_append(buf, str, strlen(str));
    free(str);
}

/* a list of strings, which owns its strings */
struct strlist {
    char **items;
    size_t len;
    size_t capacity;
};

/* str must be allocated with malloc(), the list takes ownership of it */
static void strlist_push(struct strlist *list, char *str) {
    if (list->len == list->capacity) {
        list->capacity = list->capacity == 0 ? 16 : list->capacity * 2;
        list->items = (char **) xrealloc(list->items, list->capacity * sizeof(char *));
    }
    list->items[list->len++] = str;
}

static void strlist_free(struct strlist *list) {
    for (size_t i = 0; i < list->len; i++) {
        free(list->items[i]);
    }
    free(list->items);
    list->items = NULL;
    list->len = 0;
    list->capacity = 0;
}

/* platform-independent environment setter, use empty value to clear */
static int launcher_setenv(const char *key, const char *value) {
    if (debug) {
        printf("Setting env variable %s=%s\n", key, value);
    }
    #if defined (_WIN32)
        if(_putenv_s(key, value) == -1) {
            fprintf(stderr, "_putenv_s failed\n");
            return -1;
        }
    #else
        if (value[0] == '\0') {
            /* on posix, unsetenv cleares the env variable */
            if (unsetenv(key) == -1) {
                perror("unsetenv failed");
                return -1;
            }
        } else {
            if (setenv(key, value, 1) == -1) {
                perror("setenv failed");
                return -1;
            }
        }
    #endif
    return 0;
}

/* check if file exists */
static bool exists(const char *filename) {
    struct stat buffer;
    return (stat(filename, &buffer) == 0);
}

/* get the path to the current executable */
static char *exe_path() {
    #if defined (__linux__)
        char *realPath = realpath("/proc/self/exe", NULL);
    #elif defined (__APPLE__)
        char path[PATH_MAX];
        uint32_t path_len = PATH_MAX;
        _NSGetExecutablePath(path, &path_len);
        char *realPath = realpath(path, NULL);
    #elif defined (_WIN32)
        char *realPath = (char *)xmalloc(_MAX_PATH);
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
    if (realPath == NULL) {
        fprintf(stderr, "Could not determine the path of the executable\n");
        exit(EXIT_FAILURE);
    }
    return realPath;
}

/* get the directory of the current executable */
static char *exe_directory() {
    char *path = exe_path();
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
    char *exeDir = xstrdup(result);
    free(path);
    return exeDir;
}

static char *canonicalize(const char *path) {
    char *result;
    #ifndef _WIN32
    char real[PATH_MAX];
    result = realpath(path, real);
    #else
    char real[_MAX_PATH];
    result = _fullpath(real, path, _MAX_PATH);
    #endif
    if (result == NULL) {
        fprintf(stderr, "Could not canonicalize %s\n", path);
    }
    return xstrdup(real);
}

#if defined (__APPLE__)
/* Load libjli - this is needed on osx for libawt, which uses JLI_* methods.
 * If the GraalVM libjli is not loaded, the osx linker will look up the symbol
 * via the JavaRuntimeSupport.framework (JRS), which will fall back to the
 * system JRE and fail if none is installed
 */
static void *load_jli_lib(const char *exeDir) {
    char *libjliPath = str_format("%s%s%s", exeDir, DIR_SEP_STR, LIBJLI_RELPATH_STR);
    void *handle = dlopen(libjliPath, RTLD_NOW);
    free(libjliPath);
    return handle;
}
#endif

/* load the language library (either native library or libjvm) and return a
 * pointer to it */
void* load_vm_lib(const char *liblangPath) {
    if (debug) {
        printf("Loading library %s\n", liblangPath);
    }
#if defined (__linux__) || defined (__APPLE__)
        void* jvmHandle = dlopen(liblangPath, RTLD_NOW);
        if (jvmHandle != NULL) {
            return jvmHandle;
        }
        char* errorString = dlerror();
        if (errorString != NULL) {
            fprintf(stderr, "Error while loading %s:\n%s\n", liblangPath, errorString);
        }
#else
        HMODULE jvmHandle = LoadLibraryA(liblangPath);
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

static char *vm_path(const char *exeDir, bool jvmMode) {
    char *liblangPath = NULL;
    if (jvmMode) {
        liblangPath = str_format("%s%s%s", exeDir, DIR_SEP_STR, LIBJVM_RELPATH_STR);
    } else {
#ifdef LIBLANG_RELPATH
        liblangPath = str_format("%s%s%s", exeDir, DIR_SEP_STR, LIBLANG_RELPATH_STR);
#else
        fprintf(stderr, "Should not reach here: native mode with no LIBLANG defined\n");
        exit(EXIT_FAILURE);
#endif
    }
    return liblangPath;
}

static size_t parse_size(const char *str);
static void expand_vm_arg_file(const char *arg_file,
                               struct strlist *vmArgs,
                               struct strbuf *cp,
                               struct strbuf *modulePath,
                               struct strbuf *libraryPath,
                               size_t* stack_size);

static void parse_vm_option(
        struct strlist *vmArgs,
        struct strbuf *cp,
        struct strbuf *modulePath,
        struct strbuf *libraryPath,
        size_t *stack_size,
        bool *startOnFirstThread,
        const char *option) {
    if (IS_VM_CP_ARG(option)) {
        strbuf_appendf(cp, "%s%s", CP_SEP_STR, option + VM_CP_ARG_OFFSET);
    } else if (IS_VM_CLASSPATH_ARG(option)) {
        strbuf_appendf(cp, "%s%s", CP_SEP_STR, option + VM_CLASSPATH_ARG_OFFSET);
    } else if (IS_VM_P_ARG(option)) {
        strbuf_appendf(modulePath, "%s%s", CP_SEP_STR, option + VM_P_ARG_OFFSET);
    } else if (IS_VM_MODULE_PATH_ARG(option)) {
        strbuf_appendf(modulePath, "%s%s", CP_SEP_STR, option + VM_MODULE_PATH_ARG_OFFSET);
    } else if (IS_VM_LIBRARY_PATH_ARG(option)) {
        strbuf_appendf(libraryPath, "%s%s", CP_SEP_STR, option + VM_LIBRARY_PATH_ARG_OFFSET);
    } else if (IS_VM_ARG_FILE_ARG(option)) {
        const char *arg_file = option + VM_ARG_FILE_ARG_OFFSET;
        expand_vm_arg_file(arg_file, vmArgs, cp, modulePath, libraryPath, stack_size);
#if defined (__APPLE__)
    } else if (IS_VM_START_ON_FIRST_THREAD(option)) {
        *startOnFirstThread = true;
#endif
    } else if (IS_VM_ARG(option)) {
        if (IS_VM_STACK_SIZE_ARG(option)) {
            *stack_size = parse_size(option + VM_STACK_SIZE_ARG_OFFSET);
        }
        char *opt = str_format("-%s", option + VM_ARG_OFFSET);
        strlist_push(vmArgs, opt);
    } else if (strcmp(option, "--jvm") == 0) {
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
// Returns NULL if there are no more tokens.
static char *arg_file_next_token(FILE *input) {
    enum ArgFileState state = FIND_NEXT;
    int currentQuoteChar = -1;
    int ch;

    struct strbuf token = strbuf_new();

    while ((ch = fgetc(input)) != EOF) {
        // Skip white space characters
        if (state == FIND_NEXT || state == SKIP_LEAD_WS) {
            while (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f') {
                if ((ch = fgetc(input)) == EOF) {
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
                        strbuf_append_char(&token, '\n');
                        break;
                    case 'r':
                        strbuf_append_char(&token, '\r');
                        break;
                    case 't':
                        strbuf_append_char(&token, '\t');
                        break;
                    case 'f':
                        strbuf_append_char(&token, '\f');
                        break;
                    default:
                        strbuf_append_char(&token, (char) ch);
                        break;
                }
                state = IN_QUOTE;
            }
            continue;
            // ignore comment to EOL
        } else if (state == IN_COMMENT) {
            while (ch != '\n' && ch != '\r') {
                if ((ch = fgetc(input)) == EOF) {
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
                    strbuf_append_char(&token, (char) ch);
                    continue;
                }
                // fallthrough
            case '\n':
            case '\r':
                return token.data;
            case '#':
                if (state == IN_QUOTE) {
                    strbuf_append_char(&token, (char) ch);
                    continue;
                }
                state = IN_COMMENT;
                break;
            case '\\':
                if (state != IN_QUOTE) {
                    strbuf_append_char(&token, (char) ch);
                    continue;
                }
                state = IN_ESCAPE;
                break;
            case '\'':
            case '"':
                if (state == IN_QUOTE && currentQuoteChar != ch) {
                    // not matching quote
                    strbuf_append_char(&token, (char) ch);
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
                strbuf_append_char(&token, (char) ch);
                break;
        }
    }
done:
    if (token.len == 0) {
        free(token.data);
        return NULL;
    }
    return token.data;
}

static void expand_vm_arg_file(const char *arg_file,
                               struct strlist *vmArgs,
                               struct strbuf *cp,
                               struct strbuf *modulePath,
                               struct strbuf *libraryPath,
                               size_t* stack_size) {
    if (debug) {
        printf("Expanding VM arg file %s\n", arg_file);
    }
    FILE *input = fopen(arg_file, "r");
    if (input == NULL) {
        fprintf(stderr, "Error: could not open `%s': %s\n", arg_file, strerror(errno));
        exit(EXIT_FAILURE);
    }

    while (true) {
        char *token = arg_file_next_token(input);
        if (token != NULL) {
            if (STARTS_WITH(token, "--class-path=")) {
                strbuf_appendf(cp, "%s%s", CP_SEP_STR, token + sizeof("--class-path=") - 1);
                free(token);
            } else if (STARTS_WITH(token, "--module-path=")) {
                strbuf_appendf(modulePath, "%s%s", CP_SEP_STR, token + sizeof("--module-path=") - 1);
                free(token);
            } else if (STARTS_WITH(token, "-Djava.library.path=")) {
                strbuf_appendf(libraryPath, "%s%s", CP_SEP_STR, token + sizeof("-Djava.library.path=") - 1);
                free(token);
            } else {
                if (STARTS_WITH(token, "-Xss")) {
                    *stack_size = parse_size(token + sizeof("-Xss") - 1);
                }
                strlist_push(vmArgs, token);
            }
        } else {
            break;
        }
    }
    fclose(input);
}

struct MainThreadArgs {
    int argc;
    char **argv;
    char *exeDir;
    bool jvmMode;
    char *libPath;
    size_t stack_size;
    bool startOnFirstThread;
    struct strlist vmArgs;
    struct strlist optionVarsArgs;
};

/* parse the VM arguments that should be passed to JNI_CreateJavaVM */
static void parse_vm_options(struct MainThreadArgs *parsedArgs) {
    int argc = parsedArgs->argc;
    char **argv = parsedArgs->argv;
    char *exeDir = parsedArgs->exeDir;
    bool jvmMode = parsedArgs->jvmMode;
    size_t *stack_size = &parsedArgs->stack_size;
    bool *startOnFirstThread = &parsedArgs->startOnFirstThread;
    struct strlist *vmArgs = &parsedArgs->vmArgs;
    struct strlist *optionVarsArgs = &parsedArgs->optionVarsArgs;

    /* check if vm args have been set on relaunch already */
    int vmArgCount = 0;
    char *vmArgInfo = getenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS");
    if (vmArgInfo != NULL) {
        relaunch = true;
        vmArgCount = strtol(vmArgInfo, NULL, 10);
        // clear the env variable
        launcher_setenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS", "");
    }

    /* set optional vm args from LanguageLibraryConfig.option_vars */
    #ifdef LAUNCHER_OPTION_VARS
        const char *launcherOptionVars[] = LAUNCHER_OPTION_VARS;
    #endif

    /* set system properties */
    if (jvmMode) {
        /* this is only needed for jvm mode */
        strlist_push(vmArgs, xstrdup("-Dorg.graalvm.launcher.class=" LAUNCHER_CLASS_STR));
    }
    strlist_push(vmArgs, xstrdup("-Dorg.graalvm.version=" GRAALVM_VERSION_STR));

    struct strbuf executablename = strbuf_new();
    strbuf_appendf(&executablename, "-Dorg.graalvm.launcher.executablename=");
    char *executablenameEnv = getenv("GRAALVM_LAUNCHER_EXECUTABLE_NAME");
    if (executablenameEnv) {
        strbuf_appendf(&executablename, "%s", executablenameEnv);
        launcher_setenv("GRAALVM_LAUNCHER_EXECUTABLE_NAME", "");
    } else {
        strbuf_appendf(&executablename, "%s", argv[0]);
    }
    strlist_push(vmArgs, executablename.data);
    if (debug) {
        printf("org.graalvm.launcher.executablename set to '%s'\n", executablename.data);
    }

    /* construct classpath - only needed for jvm mode */
    struct strbuf cp = strbuf_new();

    /* construct module path - only needed for jvm mode */
    struct strbuf modulePath = strbuf_new();
    strbuf_appendf(&modulePath, "--module-path=");
    #ifdef LAUNCHER_MODULE_PATH
    if (jvmMode) {
        /* add the launcher module path */
        const char *launcherModulePathEntries[] = LAUNCHER_MODULE_PATH;
        int launcherModulePathCnt = sizeof(launcherModulePathEntries) / sizeof(*launcherModulePathEntries);
        for (int i = 0; i < launcherModulePathCnt; i++) {
            char *entry = str_format("%s%s%s", exeDir, DIR_SEP_STR, launcherModulePathEntries[i]);
            char *canonicalEntry = canonicalize(entry);
            strbuf_appendf(&modulePath, "%s", canonicalEntry);
            free(canonicalEntry);
            free(entry);
            if (i < launcherModulePathCnt-1) {
                strbuf_appendf(&modulePath, "%s", CP_SEP_STR);
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
            char *absoluteDir = str_format("%s%s%s", exeDir, DIR_SEP_STR, relativeDir);

            #ifndef _WIN32
            DIR* dir = opendir(absoluteDir);
            if (dir) {
                char *canonicalDir = canonicalize(absoluteDir);
                struct dirent* entry;
                while ((entry = readdir(dir))) {
                    char* name = entry->d_name;
                    if (name[0] != '.') {
                        strbuf_appendf(&modulePath, "%s%s%s%s", CP_SEP_STR, canonicalDir, DIR_SEP_STR, name);
                    }
                }
                closedir(dir);
                free(canonicalDir);
            }
            #else
            // From https://learn.microsoft.com/en-us/windows/win32/fileio/listing-the-files-in-a-directory
            // and https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-findfirstfilea
            WIN32_FIND_DATAA entry;
            char *searchDir = str_format("%s\\*", absoluteDir);
            HANDLE dir = FindFirstFileA(searchDir, &entry);
            if (dir != INVALID_HANDLE_VALUE) {
                char *canonicalDir = canonicalize(absoluteDir);
                do {
                    char* name = entry.cFileName;
                    if (name[0] != '.') {
                        strbuf_appendf(&modulePath, "%s%s%s%s", CP_SEP_STR, canonicalDir, DIR_SEP_STR, name);
                    }
                } while (FindNextFileA(dir, &entry));
                FindClose(dir);
                free(canonicalDir);
            }
            free(searchDir);
            #endif
            free(absoluteDir);
        }
    }
    #endif

    /* construct java.library.path - only needed for jvm mode */
    struct strbuf libraryPath = strbuf_new();
    #ifdef LAUNCHER_LIBRARY_PATH
    if (jvmMode) {
        /* add the library path */
        const char *launcherLibraryPathEntries[] = LAUNCHER_LIBRARY_PATH;
        int launcherLibraryPathCnt = sizeof(launcherLibraryPathEntries) / sizeof(*launcherLibraryPathEntries);
        for (int i = 0; i < launcherLibraryPathCnt; i++) {
            strbuf_appendf(&libraryPath, "%s%s%s%s", CP_SEP_STR, exeDir, DIR_SEP_STR, launcherLibraryPathEntries[i]);
        }
    }
    #endif

    #if defined(LAUNCHER_LANG_HOME_NAMES) && defined(LAUNCHER_LANG_HOME_PATHS)
    const char *launcherLangHomeNames[] = LAUNCHER_LANG_HOME_NAMES;
    const char *launcherLangHomePaths[] = LAUNCHER_LANG_HOME_PATHS;
    int launcherLangHomeNamesCnt = sizeof(launcherLangHomeNames) / sizeof(*launcherLangHomeNames);
    for (int i = 0; i < launcherLangHomeNamesCnt; i++) {
        char *relativeHome = str_format("%s%s%s", exeDir, DIR_SEP_STR, launcherLangHomePaths[i]);
        char *home = canonicalize(relativeHome);
        char *ss = str_format("-Dorg.graalvm.language.%s.home=%s", launcherLangHomeNames[i], home);
        strlist_push(vmArgs, ss);
        free(home);
        free(relativeHome);
    }
    #endif

    #if defined(LAUNCHER_EXTRACTED_LIB_NAMES) && defined(LAUNCHER_EXTRACTED_LIB_PATHS)
    if (jvmMode) {
        const char *extractedLibNames[] = LAUNCHER_EXTRACTED_LIB_NAMES;
        const char *extractedLibPaths[] = LAUNCHER_EXTRACTED_LIB_PATHS;
        int extractedLibCnt = sizeof(extractedLibNames) / sizeof(*extractedLibNames);
        for (int i = 0; i < extractedLibCnt; i++) {
            char *relativePath = str_format("%s%s%s", exeDir, DIR_SEP_STR, extractedLibPaths[i]);
            char *path = canonicalize(relativePath);
            char *ss = str_format("-D%s=%s", extractedLibNames[i], path);
            strlist_push(vmArgs, ss);
            free(path);
            free(relativePath);
        }
    }
    #endif

    /* Handle launcher default vm arguments. We apply these first, so they can
       be overridden by explicit arguments on the commandline.
       These should be added even if relaunch is true because they are not passed to preprocessArguments(). */
    #ifdef LAUNCHER_DEFAULT_VM_ARGS
    const char *launcherDefaultVmArgs[] = LAUNCHER_DEFAULT_VM_ARGS;
    for (int i = 0; i < sizeof(launcherDefaultVmArgs)/sizeof(char*); i++) {
        if (IS_VM_ARG(launcherDefaultVmArgs[i])) {
            parse_vm_option(vmArgs, &cp, &modulePath, &libraryPath, stack_size, startOnFirstThread, launcherDefaultVmArgs[i]);
        }
    }
    #endif


    if (!relaunch) {
        /* handle CLI arguments */
        for (int i = 1; i < argc; i++) {
            parse_vm_option(vmArgs, &cp, &modulePath, &libraryPath, stack_size, startOnFirstThread, argv[i]);
        }

        /* handle optional vm args from LanguageLibraryConfig.option_vars */
        #ifdef LAUNCHER_OPTION_VARS
        for (int i = 0; i < sizeof(launcherOptionVars)/sizeof(char*); i++) {
            char *optionVar = getenv(launcherOptionVars[i]);
            if (!optionVar) {
                continue;
            }
            if (debug) {
                printf("Launcher option_var found: %s=%s\n", launcherOptionVars[i], optionVar);
            }
            // we split on spaces
            const char *optionLine = optionVar;
            const char *last = optionLine;
            const char *next = NULL;
            char *option;
            while ((next = strchr(last, ' ')) != NULL) {
                option = xstrndup(last, next-last);
                strlist_push(optionVarsArgs, option);
                parse_vm_option(vmArgs, &cp, &modulePath, &libraryPath, stack_size, startOnFirstThread, option);
                last = next + 1;
            };
            option = xstrdup(last);
            strlist_push(optionVarsArgs, option);
            parse_vm_option(vmArgs, &cp, &modulePath, &libraryPath, stack_size, startOnFirstThread, option);
        }
        #endif
    } else {
        /* Handle relaunch arguments. In that case GRAALVM_LANGUAGE_LAUNCHER_VMARGS_* contain all --vm.* arguments
           returned by preprocessArguments(), so we should not look at CLI args and option_vars as that would cause
           to add extra duplicate --vm.* arguments. */
        if (debug) {
            printf("Relaunch environment variable detected\n");
        }
        for (int i = 0; i < vmArgCount; i++) {
            char *envKey = str_format("GRAALVM_LANGUAGE_LAUNCHER_VMARGS_%d", i);
            char *cur = getenv(envKey);
            if (!cur) {
                free(envKey);
                fprintf(stderr, "VM arguments specified: %d but argument %dmissing\n", vmArgCount, i);
                break;
            }
            parse_vm_option(vmArgs, &cp, &modulePath, &libraryPath, stack_size, startOnFirstThread, cur);
            /* clean up env variable */
            launcher_setenv(envKey, "");
            free(envKey);
        }
    }

    /* set classpath and module path arguments - only needed for jvm mode */
    if (jvmMode) {
        if (cp.len > 0) {
            strlist_push(vmArgs, str_format("-Djava.class.path=%s", cp.data + 1));
        }
        if (libraryPath.len > 0) {
            strlist_push(vmArgs, str_format("-Djava.library.path=%s", libraryPath.data + 1));
        }
#ifdef LAUNCHER_MODULE_PATH
        strlist_push(vmArgs, xstrdup(modulePath.data));
        strlist_push(vmArgs, xstrdup("-Djdk.module.main=" LAUNCHER_MAIN_MODULE_STR));
        strlist_push(vmArgs, xstrdup("-Dgraalvm.locatorDisabled=true"));
#endif

        /* Allow Truffle NFI Panama to use Linker#{downcallHandle,upcallStub} without warnings. */
        strlist_push(vmArgs, xstrdup("--enable-native-access=org.graalvm.truffle"));
    }

    free(cp.data);
    free(modulePath.data);
    free(libraryPath.data);
}

static int jvm_main_thread(struct MainThreadArgs *mainArgs);
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
    struct MainThreadArgs *args = (struct MainThreadArgs*) arg;
    int ret = jvm_main_thread(args);
#if defined(__APPLE__)
    exit(ret);
#endif
    free(args);
    return (void*)(intptr_t)ret;
}
#else /* defined(_WIN32) */
static unsigned __stdcall jvm_main_thread_start(void *arg)
{
    struct MainThreadArgs *args = (struct MainThreadArgs*) arg;
    int ret = jvm_main_thread(args);
    free(args);
    return (unsigned)ret;
}
#endif

int main(int argc, char *argv[]) {
    debug = (getenv("VERBOSE_GRAALVM_LAUNCHERS") != NULL);
    if (debug) {
        /* flush the debug output immediately */
        setvbuf(stdout, NULL, _IONBF, 0);
    }
    char *exeDir = exe_directory();
    char* jvmModeEnv = getenv("GRAALVM_LAUNCHER_FORCE_JVM");
    bool jvmMode = (jvmModeEnv && (strcmp(jvmModeEnv, "true") == 0));
#ifndef LIBLANG_RELPATH
    if (jvmModeEnv && !jvmMode) {
        fprintf(stderr, "Cannot run in native mode from jvm-only launcher\n");
        return -1;
    }
    jvmMode = true;
#endif

    char *libPath = vm_path(exeDir, jvmMode);

    /* check if the VM library exists */
    if (!jvmMode) {
        if (!exists(libPath)) {
            /* switch to JVM mode */
            free(libPath);
            libPath = vm_path(exeDir, true);
            jvmMode = true;
        }
    }


    /* parse VM args */
    struct MainThreadArgs parsedArgs = {argc, argv, exeDir, jvmMode, libPath, 0, false};
    parse_vm_options(&parsedArgs);
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
            fprintf(stderr, "Loading libjli failed.\n");
            return -1;
        }
    }
#endif

    if (use_new_thread) {
        struct MainThreadArgs *threadArgs = (struct MainThreadArgs*) xmalloc(sizeof(struct MainThreadArgs));
        *threadArgs = parsedArgs;
        if (debug) {
            printf("Creating a new thread for the JVM with stack_size=%zu main_thread_stack_size=%zu\n", stack_size, main_thread_stack_size);
        }

#ifndef _WIN32
        pthread_attr_t attr;
        if (pthread_attr_init(&attr) != 0) {
            fprintf(stderr, "Could not initialize pthread attribute structure: %s\n", strerror(errno));
            return -1;
        }
        if (setstacksize(&attr, stack_size) != 0) {
            fprintf(stderr, "Could not set stack size in pthread attribute structure to %zu bytes.\n", stack_size);
            return -1;
        }

        // See: https://github.com/openjdk/jdk/blob/8c1b915c7ef2b3a6e65705b91f4eb464caaec4e7/src/java.base/unix/native/libjli/java_md.c#L685
        pthread_attr_setguardsize(&attr, 0); // no pthread guard page on java threads

        pthread_t main_thr;
        if (pthread_create(&main_thr, &attr, &jvm_main_thread_start, threadArgs) != 0) {
            fprintf(stderr, "Could not create main thread: %s\n", strerror(errno));
            pthread_attr_destroy(&attr);
            free(threadArgs);
            return -1;
        }

        // ownership transferred to new thread
        pthread_attr_destroy(&attr);

#if defined(__APPLE__)
        if (pthread_detach(main_thr)) {
            fprintf(stderr, "pthread_detach() failed: %s\n", strerror(errno));
            return -1;
        }

        ParkEventLoop();
        return 0;
#else
        void* retval;
        if (pthread_join(main_thr, &retval)) {
            fprintf(stderr, "pthread_join() failed: %s\n", strerror(errno));
            return -1;
        }
        return (int)(intptr_t)retval;
#endif
#else /* defined(_WIN32) */
        uintptr_t hThread = _beginthreadex(NULL, stack_size, &jvm_main_thread_start, threadArgs, STACK_SIZE_PARAM_IS_A_RESERVATION, NULL);
        if (hThread == 0) {
            fprintf(stderr, "_beginthreadex() failed: %lu\n", (unsigned long) GetLastError());
            return -1;
        }

        // ownership transferred to new thread

        // join thread
        DWORD exitCode = 0;
        WaitForSingleObject((HANDLE)hThread, INFINITE);
        GetExitCodeThread((HANDLE)hThread, &exitCode);
        CloseHandle((HANDLE)hThread);
        return (int)exitCode;
#endif
    }
    return jvm_main_thread(&parsedArgs);
}

static int jvm_main_thread(struct MainThreadArgs *parsedArgs) {
    int argc = parsedArgs->argc;
    char **argv = parsedArgs->argv;
    bool jvmMode = parsedArgs->jvmMode;
    char *libPath = parsedArgs->libPath;
    struct strlist *vmArgs = &parsedArgs->vmArgs;
    struct strlist *optionVarsArgs = &parsedArgs->optionVarsArgs;

    /* load VM library - after parsing arguments s.t. NMT
     * tracking environment variables are already set */
    void* library = load_vm_lib(libPath);
    if (!library) {
        fprintf(stderr, "Could not load VM library from %s.\n", libPath);
        return -1;
    }
    /* the paths are no longer needed once the VM library is loaded */
    free(parsedArgs->libPath);
    parsedArgs->libPath = NULL;
    free(parsedArgs->exeDir);
    parsedArgs->exeDir = NULL;

    if (jvmMode) {
        GetDefaultJavaVMInitArgs_type getDefaultJavaVMInitArgs = (GetDefaultJavaVMInitArgs_type) get_function(library, "JNI_GetDefaultJavaVMInitArgs");
        if (!getDefaultJavaVMInitArgs) {
            fprintf(stderr, "Could not find JNI_GetDefaultJavaVMInitArgs.\n");
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
            strlist_push(vmArgs, xstrdup("--sun-misc-unsafe-memory-access=allow"));
        }
    }

    // Convert vmArgs to JavaVMInitArgs
    jint nOptions = jvmMode ? vmArgs->len : 1 + vmArgs->len;
    JavaVMOption *options = (JavaVMOption*) calloc(nOptions, sizeof(JavaVMOption));
    JavaVMOption *curOpt = options;

    const char *svm_error = NULL;
    if (!jvmMode) {
        curOpt->optionString = xstrdup("_createvm_errorstr");
        curOpt->extraInfo = &svm_error;
        curOpt++;
    }

    for (size_t i = 0; i < vmArgs->len; i++) {
        const char *arg = vmArgs->items[i];
        if (debug) {
            printf("Setting VM argument %s\n", arg);
        }
        /* env variable for native memory tracking (NMT), obsolete with JDK 18 */
        const char *nmtPos = strstr(arg, NMT_ARG_NAME);
        if (nmtPos != NULL) {
            nmtPos += sizeof(NMT_ARG_NAME) - 1;
            /* skip the '=' */
            const char *val = (*nmtPos == '\0') ? nmtPos : nmtPos + 1;
            char *nmtEnv = str_format("%s%d", NMT_ENV_NAME, (int) getpid());
            launcher_setenv(nmtEnv, val);
            free(nmtEnv);
        }
        curOpt->optionString = xstrdup(arg);
        curOpt++;
    }
    /* the options have their own copy of the VM arguments */
    strlist_free(vmArgs);

    CreateJavaVM_type createVM = (CreateJavaVM_type) get_function(library, "JNI_CreateJavaVM");
    if (!createVM) {
        fprintf(stderr, "Could not find JNI_CreateJavaVM.\n");
        return -1;
    }

    JavaVMInitArgs vmInitArgs;
    vmInitArgs.version = JNI_VERSION_9;
    vmInitArgs.nOptions = nOptions;
    vmInitArgs.options = options;
    /* In general we want to validate VM arguments.
     * But we must disable it for the case there is a native library and we saw a --jvm argument,
     * as the VM arguments are then JVM VM arguments and not SVM VM arguments.
     * In that case we validate them after the execv() when running in --jvm mode. */
    vmInitArgs.ignoreUnrecognized = found_switch_to_jvm_flag && !jvmMode;

    JavaVM *vm;
    JNIEnv *env;
    if (createVM(&vm, (void**)&env, &vmInitArgs) != JNI_OK) {
        if (svm_error != NULL) {
            fprintf(stderr, "%s\n", svm_error);
            free((void*) svm_error);
            svm_error = NULL;
        }
        fprintf(stderr, "JNI_CreateJavaVM() failed.\n");
        return -1;
    }

    /* free the allocated vm arguments */
    for (int i = 0; i < nOptions; i++) {
        free(options[i].optionString);
    }
    free(options);

    jclass byteArrayClass = (*env)->FindClass(env, "[B");
    if (byteArrayClass == NULL) {
        fprintf(stderr, "Byte array class not found.\n");
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }
        return -1;
    }
    jclass relaunchExceptionClass = (*env)->FindClass(env, "org/graalvm/launcher/AbstractLanguageLauncher$RelaunchException");
    if (relaunchExceptionClass == NULL) {
        fprintf(stderr, "RelaunchException class not found.\n");
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }
        return -1;
    }
    jclass launcherClass = (*env)->FindClass(env, "org/graalvm/launcher/AbstractLanguageLauncher");
    if (launcherClass == NULL) {
        fprintf(stderr, "Launcher class not found.\n");
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }
        return -1;
    }
    jmethodID runLauncherMid = (*env)->GetStaticMethodID(env, launcherClass, "runLauncher", "([[B[[BIJZ)V");
    if (runLauncherMid == NULL) {
        fprintf(stderr, "Launcher entry point not found.\n");
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }
        return -1;
    }
    jfieldID vmArgsFid = (*env)->GetFieldID(env, relaunchExceptionClass, "vmArgs", "[Ljava/lang/String;");
    if (vmArgsFid == NULL) {
        fprintf(stderr, "RelaunchException vm args field not found.\n");
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }
        return -1;
    }

    /* backup native args */
    char** argv_native = argv;
    int argc_native = argc;

    argv++;
    argc--;

    /* create args string array */
    jobjectArray args = (*env)->NewObjectArray(env, argc, byteArrayClass, NULL);
    for (int i = 0; i < argc; i++) {
        int arraySize = strlen(argv[i]);
        jbyteArray arg = (*env)->NewByteArray(env, arraySize);
        (*env)->SetByteArrayRegion(env, arg, 0, arraySize, (jbyte *)(argv[i]));
        if ((*env)->ExceptionCheck(env)) {
            fprintf(stderr, "Error in SetByteArrayRegion:\n");
            (*env)->ExceptionDescribe(env);
            return -1;
        }
        (*env)->SetObjectArrayElement(env, args, i, arg);
        if ((*env)->ExceptionCheck(env)) {
            fprintf(stderr, "Error in SetObjectArrayElement:\n");
            (*env)->ExceptionDescribe(env);
            return -1;
        }
    }

    /* create env var args string array */
    jobjectArray optionVarsArgsArray = (*env)->NewObjectArray(env, optionVarsArgs->len, byteArrayClass, NULL);
    for (int i = 0; i < optionVarsArgs->len; i++) {
        const char *argString = optionVarsArgs->items[i];
        jbyteArray arg = (*env)->NewByteArray(env, strlen(argString));
        (*env)->SetByteArrayRegion(env, arg, 0, strlen(argString), (jbyte *)(argString));
        if ((*env)->ExceptionCheck(env)) {
            fprintf(stderr, "Error in SetByteArrayRegion:\n");
            (*env)->ExceptionDescribe(env);
            return -1;
        }
        (*env)->SetObjectArrayElement(env, optionVarsArgsArray, i, arg);
        if ((*env)->ExceptionCheck(env)) {
            fprintf(stderr, "Error in SetObjectArrayElement:\n");
            (*env)->ExceptionDescribe(env);
            return -1;
        }
    }

    strlist_free(optionVarsArgs);

    /* invoke launcher entry point */
    jlong argv_native_long = (jlong)(uintptr_t)(void*)argv_native;
    (*env)->CallStaticVoidMethod(env, launcherClass, runLauncherMid, optionVarsArgsArray, args, argc_native, argv_native_long, relaunch);
    jthrowable t = (*env)->ExceptionOccurred(env);
    if (t) {
        if ((*env)->IsInstanceOf(env, t, relaunchExceptionClass)) {
            if (debug) {
                printf("Relaunch exception has been thrown\n");
            }
            (*env)->ExceptionClear(env);
            // read correct VM arguments from exception
            jobjectArray vmArgs = (jobjectArray)(*env)->GetObjectField(env, t, vmArgsFid);
            if ((*env)->ExceptionCheck(env)) {
                fprintf(stderr, "Error in GetObjectField:\n");
                (*env)->ExceptionDescribe(env);
                return -1;
            }
            jint vmArgCount = (*env)->GetArrayLength(env, vmArgs);
            if ((*env)->ExceptionCheck(env)) {
                fprintf(stderr, "Error in GetArrayLength:\n");
                (*env)->ExceptionDescribe(env);
                return -1;
            }
            if (debug) {
                printf("Relaunch VM arguments read: %d\n", (int) vmArgCount);
            }
            char *ss = str_format("%d", (int) vmArgCount);
            if (launcher_setenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS", ss) == -1) {
                return -1;
            }
            free(ss);
            for (int i = 0; i < vmArgCount; i++) {
                jstring vmArgString = (jstring)(*env)->GetObjectArrayElement(env, vmArgs, i);
                if ((*env)->ExceptionCheck(env)) {
                    fprintf(stderr, "Error in GetObjectArrayElement:\n");
                    (*env)->ExceptionDescribe(env);
                    return -1;
                }
                const char *vmArg = (*env)->GetStringUTFChars(env, vmArgString, NULL);
                if (vmArg == NULL || (*env)->ExceptionCheck(env)) {
                    fprintf(stderr, "Error in GetStringUTFChars:\n");
                    (*env)->ExceptionDescribe(env);
                    return -1;
                }
                /* set environment variables for relaunch vm arguments */
                char *key = str_format("GRAALVM_LANGUAGE_LAUNCHER_VMARGS_%d", i);
                const char *arg = vmArg;
                if (launcher_setenv(key, arg) == -1) {
                    return -1;
                }
                free(key);
                (*env)->ReleaseStringUTFChars(env, vmArgString, vmArg);
            }
            /* relaunch with correct VM arguments */
            char *path = exe_path();
            /* execv() passes the current environment, including the variables set above */
            execv(path, argv_native);
            /* if we reach here, execv failed for sure */
            perror("execv failed");
            return -1;
        }
        (*env)->ExceptionDescribe(env);
        return -1;
    }
	return 0;
}

/**
 * Parses the size part of -Xss, -Xmx, etc. options.
 * Returns the parsed size in bytes or 0 if invalid.
 * Inspired by: https://github.com/openjdk/jdk/blob/8c1b915c7ef2b3a6e65705b91f4eb464caaec4e7/src/java.base/share/native/libjli/java.c#L920-L954
 */
static size_t parse_size(const char *str) {
    size_t size = strlen(str);
    size_t n = 0;
    size_t pos = 0;
    while (pos < size && str[pos] >= '0' && str[pos] <= '9') {
        int digit = str[pos] - '0';
        n = n * 10 + digit;
        pos++;
    }
    if (pos == 0 || size - pos > 1) {
        // invalid
        return 0;
    }
    if (pos == size) {
        return n;
    }
    size_t multiplier = 1;
    switch (str[pos]) {
        case 'T':
        case 't':
            multiplier *= 1024;
            // fallthrough
        case 'G':
        case 'g':
            multiplier *= 1024;
            // fallthrough
        case 'M':
        case 'm':
            multiplier *= 1024;
            // fallthrough
        case 'K':
        case 'k':
            multiplier *= 1024;
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
    MEMORY_BASIC_INFORMATION mbi = {0};
    VirtualQuery(&mbi, &mbi, sizeof(mbi));
    NT_TIB* tib = (NT_TIB*)NtCurrentTeb();
    uintptr_t low = (uintptr_t)mbi.AllocationBase;
    uintptr_t high = (uintptr_t)tib->StackBase;
    current_thread_stack_size = high - low;
#endif
    return current_thread_stack_size;
}
