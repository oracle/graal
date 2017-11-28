/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#define _GNU_SOURCE
#include <dlfcn.h>

#include "native.h"

#include <string.h>
#include "internal.h"

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_loadLibrary(JNIEnv *env, jclass self, jlong context, jstring name, jint flags) {
    const char *utfName = (*env)->GetStringUTFChars(env, name, NULL);
    void *ret = dlopen(utfName, flags);
    if (ret == NULL) {
        const char *error = dlerror();
        struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
        (*env)->ThrowNew(env, ctx->UnsatisfiedLinkError, error);
    }
    (*env)->ReleaseStringUTFChars(env, name, utfName);
    return (jlong) ret;
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_freeLibrary(JNIEnv *env, jclass self, jlong handle) {
    dlclose((void*) handle);
}

static jlong lookup(JNIEnv *env, jlong context, void *handle, jstring name) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    const char *utfName = (*env)->GetStringUTFChars(env, name, NULL);
    // clear previous errors
    dlerror();
    void *ret = dlsym(handle, utfName);
    if (ret == NULL) {
        const char *error = dlerror();
        // if error == NULL, the symbol was found, but really points to NULL
        if (error != NULL) {
            (*env)->ThrowNew(env, ctx->UnsatisfiedLinkError, error);
        }
    }
    (*env)->ReleaseStringUTFChars(env, name, utfName);
    return (jlong) check_intrinsify(ctx, ret);
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_lookup(JNIEnv *env, jclass self, jlong context, jlong library, jstring name) {
    if (library == 0) {
        return lookup(env, context, RTLD_DEFAULT, name);
    } else {
        return lookup(env, context, (void *) library, name);
    }
}
