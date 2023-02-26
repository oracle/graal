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

#include <string.h>
#include "internal.h"

#include <windows.h>

static void throwError(JNIEnv *env, jlong context) {
    DWORD error = GetLastError();
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    LPSTR msg;

    FormatMessageA(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS, NULL, error, 0, (LPSTR) &msg, 0,
                   NULL);
    (*env)->ThrowNew(env, ctx->UnsatisfiedLinkError, msg);
    LocalFree(msg);
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
    HMODULE module;
    FARPROC ret;

    if (library == 0) {
        module = GetModuleHandleA(NULL);
    } else {
        module = (HMODULE) library;
    }

    ret = GetProcAddress(module, utfName);
    if (ret == 0) {
        throwError(env, context);
    }
    (*env)->ReleaseStringUTFChars(env, name, utfName);

    return (jlong) (intptr_t) check_intrinsify(ctx, ret);
}

#endif
