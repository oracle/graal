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
#ifndef __TRUFFLE_INTERNAL_H
#define __TRUFFLE_INTERNAL_H

#include "trufflenfi.h"
#include <ffi.h>
#include <jni.h>

struct __TruffleContextInternal {
    const struct __TruffleThreadAPI *functions;
    JavaVM *javaVM;
    jobject NFIContext;


    jmethodID CallTarget_call;

    jfieldID LibFFISignature_cif;
    jfieldID LibFFISignature_argTypes;

    jfieldID LibFFIType_type;
    jclass LibFFIType_EnvType;
    jclass LibFFIType_ObjectType;
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
    void *__pthreads_errno_location;
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
