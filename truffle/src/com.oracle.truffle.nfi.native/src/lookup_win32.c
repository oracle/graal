/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

#if defined(_WIN32)

#include "native.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "internal.h"

#include <windows.h>

static void throwError(JNIEnv *env, jlong context) {
    DWORD error = GetLastError();
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    LPSTR msg = NULL;
    DWORD lang = MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT);
    DWORD len;

    len = FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS, NULL, error, lang, (LPSTR) &msg, 0,
                         NULL);
    if (len == 0 || msg == NULL) {
        /*
         * FormatMessageA does not have a message string for every error code, and it leaves the
         * output pointer untouched when it fails. Throwing with the raw code is better than
         * dereferencing an uninitialized pointer.
         */
        char fallback[64];
        snprintf(fallback, sizeof(fallback), "windows error %lu", (unsigned long) error);
        (*env)->ThrowNew(env, ctx->UnsatisfiedLinkError, fallback);
    } else {
        (*env)->ThrowNew(env, ctx->UnsatisfiedLinkError, msg);
        LocalFree(msg);
    }
}

/*
 * `dlsym(RTLD_DEFAULT, name)` searches the main executable *and* every shared object loaded into
 * the process. The Windows equivalent used to be `GetProcAddress(GetModuleHandle(NULL), name)`,
 * which searches only the export table of the main executable -- for a JVM launcher that table is
 * essentially empty, so the "default" library resolved almost nothing. Walking every loaded module
 * gives the default library the same meaning it has on POSIX.
 *
 * `EnumProcessModules` lives in psapi.dll on older systems, but is also exported from kernel32.dll
 * as `K32EnumProcessModules` on Windows 7 and later. Resolving it dynamically keeps this file free
 * of an additional link-time dependency.
 */
typedef BOOL(WINAPI *EnumProcessModulesFn)(HANDLE, HMODULE *, DWORD, LPDWORD);

static EnumProcessModulesFn getEnumProcessModules(void) {
    static EnumProcessModulesFn cached = NULL;
    EnumProcessModulesFn fn = cached;
    if (fn == NULL) {
        HMODULE kernel32 = GetModuleHandleW(L"kernel32.dll");
        if (kernel32 != NULL) {
            fn = (EnumProcessModulesFn) (void *) GetProcAddress(kernel32, "K32EnumProcessModules");
        }
        /* Benign race: every thread computes the same value. */
        cached = fn;
    }
    return fn;
}

static FARPROC lookupInLoadedModules(const char *name) {
    EnumProcessModulesFn enumProcessModules = getEnumProcessModules();
    HMODULE staticBuf[128];
    HMODULE *modules = staticBuf;
    DWORD capacity = (DWORD) sizeof(staticBuf);
    DWORD needed = 0;
    HANDLE process = GetCurrentProcess();
    FARPROC ret = NULL;
    DWORD i, count;

    if (enumProcessModules == NULL) {
        return NULL;
    }

    if (!enumProcessModules(process, modules, capacity, &needed)) {
        return NULL;
    }

    if (needed > capacity) {
        /* More modules than fit the stack buffer. Retry once with an exactly sized heap buffer. */
        modules = (HMODULE *) malloc(needed);
        if (modules == NULL) {
            return NULL;
        }
        capacity = needed;
        if (!enumProcessModules(process, modules, capacity, &needed)) {
            free(modules);
            return NULL;
        }
    }

    /* A concurrent load may have grown the set since the call; only trust what was written. */
    count = (needed < capacity ? needed : capacity) / (DWORD) sizeof(HMODULE);
    for (i = 0; i < count; i++) {
        ret = GetProcAddress(modules[i], name);
        if (ret != NULL) {
            break;
        }
    }

    if (modules != staticBuf) {
        free(modules);
    }
    return ret;
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_backend_libffi_LibFFIContext_loadLibrary(JNIEnv *env, jclass self, jlong context, jstring name,
                                                                                             jint flags) {
    jsize nameLen = (*env)->GetStringLength(env, name);
    const jchar *nameChars = (*env)->GetStringChars(env, name, NULL);

    LPWSTR nameStr = alloca(sizeof(*nameStr) * (nameLen + 1));
    HMODULE ret;

    memcpy(nameStr, nameChars, sizeof(*nameStr) * nameLen);
    nameStr[nameLen] = '\0';

    (*env)->ReleaseStringChars(env, name, nameChars);

    ret = LoadLibraryExW(nameStr, NULL, flags);
    if (ret == NULL) {
        throwError(env, context);
    }
    return (jlong) ret;
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_backend_libffi_LibFFIContext_freeLibrary(JNIEnv *env, jclass self, jlong handle) {
    FreeLibrary((HMODULE) handle);
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_backend_libffi_LibFFIContext_lookup(JNIEnv *env, jclass self, jlong context, jlong library,
                                                                                        jstring name) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    const char *utfName = (*env)->GetStringUTFChars(env, name, NULL);
    FARPROC ret;

    if (library == 0) {
        /* The "default" library: mirror dlsym(RTLD_DEFAULT, ...) over the whole process. */
        ret = GetProcAddress(GetModuleHandleW(NULL), utfName);
        if (ret == NULL) {
            ret = lookupInLoadedModules(utfName);
        }
        if (ret == NULL) {
            /*
             * The scan clobbers the thread's last-error with whatever the last GetProcAddress miss
             * set, which is the right code but not guaranteed after the enumeration itself. Pin it
             * so the thrown message names the actual problem.
             */
            SetLastError(ERROR_PROC_NOT_FOUND);
        }
    } else {
        ret = GetProcAddress((HMODULE) library, utfName);
    }

    if (ret == NULL) {
        throwError(env, context);
    }
    (*env)->ReleaseStringUTFChars(env, name, utfName);

    return (jlong) (intptr_t) check_intrinsify(ctx, ret);
}

#endif
