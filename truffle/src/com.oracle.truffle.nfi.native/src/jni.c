/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
// Workaround for static linking. See comment in ffi.h, line 169.
#define FFI_BUILDING
#endif

#include "native.h"
#include "trufflenfi.h"

#include "internal.h"

#include <stdio.h>
#include <stdlib.h>
#include <ffi.h>

#if !defined(_WIN32)
#include <dlfcn.h>
#endif

static void cacheFFIType(JNIEnv *env, jclass nativeSimpleType, jobject context, jmethodID initializeSimpleType, const char *enumName, ffi_type *type) {
    jfieldID enumField = (*env)->GetStaticFieldID(env, nativeSimpleType, enumName, "Lcom/oracle/truffle/nfi/spi/types/NativeSimpleType;");
    jobject enumValue = (*env)->GetStaticObjectField(env, nativeSimpleType, enumField);

    (*env)->CallVoidMethod(env, context, initializeSimpleType, enumValue, type->size, type->alignment, (jlong) type);
}

static void initializeFlag(JNIEnv *env, jclass NFIContext, jobject context, const char *name, int value) {
    jfieldID field = (*env)->GetFieldID(env, NFIContext, name, "I");
    (*env)->SetIntField(env, context, field, value);
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_initializeNativeContext(JNIEnv *env, jobject context) {
    struct __TruffleContextInternal *ret = (struct __TruffleContextInternal *) malloc(sizeof(*ret));

    jclass CallTarget, LibFFISignature, LibFFIType, NFIContext, LibFFIClosure_RetPatches, NativeSimpleType;
    jmethodID initializeSimpleType;

    (*env)->GetJavaVM(env, &ret->javaVM);
    ret->functions = &truffleThreadAPI;
    ret->NFIContext = (*env)->NewGlobalRef(env, context);

    CallTarget = (*env)->FindClass(env, "com/oracle/truffle/api/CallTarget");
    ret->CallTarget_call = (*env)->GetMethodID(env, CallTarget, "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    LibFFISignature = (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFISignature");
    ret->LibFFISignature_cif = (*env)->GetFieldID(env, LibFFISignature, "cif", "J");
    ret->LibFFISignature_argTypes = (*env)->GetFieldID(env, LibFFISignature, "argTypes", "[Lcom/oracle/truffle/nfi/impl/LibFFIType;");

    LibFFIType = (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType");
    ret->LibFFIType_type = (*env)->GetFieldID(env, LibFFIType, "type", "J");
    ret->LibFFIType_EnvType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType$EnvType"));
    ret->LibFFIType_ObjectType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType$ObjectType"));
    ret->LibFFIType_NullableType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType$NullableType"));
    ret->LibFFIType_StringType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIType$StringType"));

    ret->NativeString = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/NativeString"));
    ret->NativeString_nativePointer = (*env)->GetFieldID(env, ret->NativeString, "nativePointer", "J");

    NFIContext = (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/NFIContext");
    ret->NFIContext_getNativeEnv = (*env)->GetMethodID(env, NFIContext, "getNativeEnv", "()J");
    ret->NFIContext_createClosureNativePointer = (*env)->GetMethodID(env, NFIContext, "createClosureNativePointer",
            "(JJLcom/oracle/truffle/api/CallTarget;Lcom/oracle/truffle/nfi/impl/LibFFISignature;)Lcom/oracle/truffle/nfi/impl/ClosureNativePointer;");
    ret->NFIContext_newClosureRef = (*env)->GetMethodID(env, NFIContext, "newClosureRef", "(J)V");
    ret->NFIContext_releaseClosureRef = (*env)->GetMethodID(env, NFIContext, "releaseClosureRef", "(J)V");
    ret->NFIContext_getClosureObject = (*env)->GetMethodID(env, NFIContext, "getClosureObject", "(J)Lcom/oracle/truffle/api/interop/TruffleObject;");

    ret->Object = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Object"));
    ret->String = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/String"));
    ret->UnsatisfiedLinkError = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/NFIUnsatisfiedLinkError"));

    LibFFIClosure_RetPatches = (*env)->FindClass(env, "com/oracle/truffle/nfi/impl/LibFFIClosure$RetPatches");
    ret->RetPatches_count = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "count", "I");
    ret->RetPatches_patches = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "patches", "[I");
    ret->RetPatches_objects = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "objects", "[Ljava/lang/Object;");


    initializeSimpleType = (*env)->GetMethodID(env, NFIContext, "initializeSimpleType", "(Lcom/oracle/truffle/nfi/spi/types/NativeSimpleType;IIJ)V");
    NativeSimpleType = (*env)->FindClass(env, "com/oracle/truffle/nfi/spi/types/NativeSimpleType");

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
    cacheFFIType(env, NativeSimpleType, context, initializeSimpleType, "NULLABLE", &ffi_type_pointer);

#if !defined(_WIN32)
    initializeFlag(env, NFIContext, context, "RTLD_GLOBAL", RTLD_GLOBAL);
    initializeFlag(env, NFIContext, context, "RTLD_LOCAL", RTLD_LOCAL);
    initializeFlag(env, NFIContext, context, "RTLD_LAZY", RTLD_LAZY);
    initializeFlag(env, NFIContext, context, "RTLD_NOW", RTLD_NOW);
#endif
    
#if defined(ENABLE_ISOLATED_NAMESPACE)
    initializeFlag(env, NFIContext, context, "ISOLATED_NAMESPACE", ISOLATED_NAMESPACE);
    ret->NFIContext_isolatedNamespaceId = (*env)->GetFieldID(env, NFIContext, "isolatedNamespaceId", "J");
#endif

    initialize_intrinsics(ret);

    return (jlong) ret;
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_disposeNativeContext(JNIEnv *env, jclass clazz, jlong context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;

    (*env)->DeleteGlobalRef(env, ctx->NFIContext);

    (*env)->DeleteGlobalRef(env, ctx->LibFFIType_EnvType);
    (*env)->DeleteGlobalRef(env, ctx->LibFFIType_ObjectType);
    (*env)->DeleteGlobalRef(env, ctx->LibFFIType_NullableType);
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
