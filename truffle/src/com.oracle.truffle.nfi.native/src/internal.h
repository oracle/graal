/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef __TRUFFLE_INTERNAL_H
#define __TRUFFLE_INTERNAL_H

#if defined(__linux__) && defined(_GNU_SOURCE)
#define ENABLE_ISOLATED_NAMESPACE
#define ISOLATED_NAMESPACE 0x10000
#include <dlfcn.h>
#endif

#include "native.h"
#include "trufflenfi.h"
#include <ffi.h>
#include <jni.h>

#ifdef _WIN32

#include <malloc.h>

#define __thread __declspec(thread)

#else

#include <alloca.h>

#endif

struct __TruffleContextInternal {
    const struct __TruffleThreadAPI *functions;
    JavaVM *javaVM;
    jobject NFIContext;

#if defined(ENABLE_ISOLATED_NAMESPACE)    
    jfieldID NFIContext_isolatedNamespaceId;
#endif

    jmethodID CallTarget_call;

    jfieldID LibFFISignature_cif;
    jfieldID LibFFISignature_argTypes;

    jfieldID LibFFIType_type;
    jclass LibFFIType_EnvType;
    jclass LibFFIType_ObjectType;
    jclass LibFFIType_NullableType;
    jclass LibFFIType_StringType;

    jclass NativeString;
    jfieldID NativeString_nativePointer;

    jmethodID NFIContext_getNativeEnv;
    jmethodID NFIContext_createClosureNativePointer;
    jmethodID NFIContext_newClosureRef;
    jmethodID NFIContext_releaseClosureRef;
    jmethodID NFIContext_getClosureObject;

    jfieldID RetPatches_count;
    jfieldID RetPatches_patches;
    jfieldID RetPatches_objects;

    jclass Object;
    jclass String;
    jclass UnsatisfiedLinkError;


    void *__libc_errno_location;
#if !defined(_WIN32)
    void *__pthreads_errno_location;
#endif
};

struct __TruffleEnvInternal {
    const struct __TruffleNativeAPI *functions;
    struct __TruffleContextInternal *context;
    JNIEnv *jniEnv;
};

extern const struct __TruffleNativeAPI truffleNativeAPI;
extern const struct __TruffleThreadAPI truffleThreadAPI;

extern __thread int errnoMirror;

// keep this in sync with the code in com.oracle.truffle.nfi.NativeArgumentBuffer$TypeTag
enum TypeTag {
    OBJECT = 0,
    STRING,
    CLOSURE,
    ENV,

    BOOLEAN_ARRAY,
    BYTE_ARRAY,
    CHAR_ARRAY,
    SHORT_ARRAY,
    INT_ARRAY,
    LONG_ARRAY,
    FLOAT_ARRAY,
    DOUBLE_ARRAY
};

#define DECODE_OFFSET(encoded) (((unsigned int) (encoded)) >> 4)
#define DECODE_TAG(encoded) ((enum TypeTag) ((encoded) & 0x0F))


void initialize_intrinsics(struct __TruffleContextInternal *);
void *check_intrinsify(struct __TruffleContextInternal *, void *);

#endif
