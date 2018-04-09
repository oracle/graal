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
#include "native.h"
#include "trufflenfi.h"

#include "internal.h"

#include <stdio.h>
#include <stdlib.h>
#include <ffi.h>
#include <dlfcn.h>

static void cacheFFIType(JNIEnv *env, jclass nativeSimpleType, jobject context, jmethodID initializeSimpleType, const char *enumName, ffi_type *type) {
    jfieldID enumField = (*env)->GetStaticFieldID(env, nativeSimpleType, enumName, "Lcom/oracle/truffle/nfi/types/NativeSimpleType;");
    jobject enumValue = (*env)->GetStaticObjectField(env, nativeSimpleType, enumField);

    (*env)->CallVoidMethod(env, context, initializeSimpleType, enumValue, type->size, type->alignment, (jlong) type);
}

static void initializeFlag(JNIEnv *env, jclass NFIContext, jobject context, const char *name, int value) {
    jfieldID field = (*env)->GetFieldID(env, NFIContext, name, "I");
    (*env)->SetIntField(env, context, field, value);
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_initializeNativeContext(JNIEnv *env, jobject context) {
    struct __TruffleContextInternal *ret = (struct __TruffleContextInternal *) malloc(sizeof(*ret));

    (*env)->GetJavaVM(env, &ret->javaVM);
    ret->functions = &truffleThreadAPI;
    ret->NFIContext = (*env)->NewGlobalRef(env, context);

    jclass CallTarget = (*env)->FindClass(env, "com/oracle/truffle/api/CallTarget");
    ret->CallTarget_call = (*env)->GetMethodID(env, CallTarget, "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    jclass LibFFISignature = (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFISignature");
    ret->LibFFISignature_cif = (*env)->GetFieldID(env, LibFFISignature, "cif", "J");
    ret->LibFFISignature_argTypes = (*env)->GetFieldID(env, LibFFISignature, "argTypes", "[Lcom/oracle/truffle/nfi/impl/LibFFIType;");

    jclass LibFFIType = (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType");
    ret->LibFFIType_type = (*env)->GetFieldID(env, LibFFIType, "type", "J");
    ret->LibFFIType_EnvType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType$EnvType"));
    ret->LibFFIType_ObjectType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType$ObjectType"));
    ret->LibFFIType_StringType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType$StringType"));

    ret->NativeString = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/NativeString"));
    ret->NativeString_nativePointer = (*env)->GetFieldID(env, ret->NativeString, "nativePointer", "J");

    jclass NFIContext = (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/NFIContext");
    ret->NFIContext_getNativeEnv = (*env)->GetMethodID(env, NFIContext, "getNativeEnv", "()J");
    ret->NFIContext_createClosureNativePointer = (*env)->GetMethodID(env, NFIContext, "createClosureNativePointer",
            "(JJLcom/oracle/truffle/api/CallTarget;Lcom/oracle/truffle/nfi/impl/LibFFISignature;)Lcom/oracle/truffle/nfi/impl/ClosureNativePointer;");
    ret->NFIContext_newClosureRef = (*env)->GetMethodID(env, NFIContext, "newClosureRef", "(J)V");
    ret->NFIContext_releaseClosureRef = (*env)->GetMethodID(env, NFIContext, "releaseClosureRef", "(J)V");
    ret->NFIContext_getClosureObject = (*env)->GetMethodID(env, NFIContext, "getClosureObject", "(J)Lcom/oracle/truffle/api/interop/TruffleObject;");

    ret->Object = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Object"));
    ret->String = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/String"));
    ret->UnsatisfiedLinkError = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/NFIUnsatisfiedLinkError"));

    jclass LibFFIClosure_RetPatches = (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIClosure$RetPatches");
    ret->RetPatches_count = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "count", "I");
    ret->RetPatches_patches = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "patches", "[I");
    ret->RetPatches_objects = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "objects", "[Ljava/lang/Object;");


    jmethodID initializeSimpleType = (*env)->GetMethodID(env, NFIContext, "initializeSimpleType", "(Lcom/oracle/truffle/nfi/types/NativeSimpleType;IIJ)V");
    jclass NativeSimpleType = (*env)->FindClass(env, "com/oracle/truffle/nfi/types/NativeSimpleType");

    // it's important to initialize "POINTER" first, because the primitive array types depend on it
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "POINTER", &ffi_type_pointer);

    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "VOID",    &ffi_type_void);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "UINT8",   &ffi_type_uint8);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "SINT8",   &ffi_type_sint8);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "UINT16",  &ffi_type_uint16);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "SINT16",  &ffi_type_sint16);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "UINT32",  &ffi_type_uint32);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "SINT32",  &ffi_type_sint32);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "UINT64",  &ffi_type_uint64);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "SINT64",  &ffi_type_sint64);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "FLOAT",   &ffi_type_float);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "DOUBLE",  &ffi_type_double);

    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "STRING", &ffi_type_pointer);
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "OBJECT", &ffi_type_pointer);


    initializeFlag(env, NFIContext, context, "RTLD_GLOBAL", RTLD_GLOBAL);
    initializeFlag(env, NFIContext, context, "RTLD_LOCAL", RTLD_LOCAL);
    initializeFlag(env, NFIContext, context, "RTLD_LAZY", RTLD_LAZY);
    initializeFlag(env, NFIContext, context, "RTLD_NOW", RTLD_NOW);


    initialize_intrinsics(ret);

    return (jlong) ret;
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_disposeNativeContext(JNIEnv *env, jclass clazz, jlong context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;

    (*env)->DeleteGlobalRef(env, ctx->NFIContext);

    (*env)->DeleteGlobalRef(env, ctx->LibFFIType_EnvType);
    (*env)->DeleteGlobalRef(env, ctx->LibFFIType_ObjectType);
    (*env)->DeleteGlobalRef(env, ctx->LibFFIType_StringType);

    (*env)->DeleteGlobalRef(env, ctx->NativeString);

    (*env)->DeleteGlobalRef(env, ctx->Object);
    (*env)->DeleteGlobalRef(env, ctx->String);
    (*env)->DeleteGlobalRef(env, ctx->UnsatisfiedLinkError);

    free(ctx);
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_initializeNativeEnv(JNIEnv *env, jclass clazz, jlong context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;

    struct __TruffleEnvInternal *ret = (struct __TruffleEnvInternal *) malloc(sizeof(*ret));
    ret->functions = &truffleNativeAPI;
    ret->context = ctx;
    ret->jniEnv = env;

    return (jlong) ret;
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_impl_NativeAllocation_free(JNIEnv *env, jclass self, jlong pointer) {
    free((void*) pointer);
}

JNIEXPORT jstring JNICALL Java_com_oracle_truffle_nfi_impl_NativeString_toJavaString(JNIEnv *env, jclass self, jlong pointer) {
    const char *str = (const char *) pointer;
    return (*env)->NewStringUTF(env, str);
}
