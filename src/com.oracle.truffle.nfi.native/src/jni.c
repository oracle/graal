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

#include <stdio.h>
#include <stdlib.h>
#include <ffi.h>
#include "internal.h"

static JavaVM *jvm;

JNIEnv *getEnv() {
    JNIEnv *env;
    int ret = (*jvm)->GetEnv(jvm, (void**) &env, JNI_VERSION_1_6);
    if (ret == JNI_EDETACHED) {
        ret = (*jvm)->AttachCurrentThread(jvm, (void **) &env, NULL);
    }

    if (ret == JNI_OK) {
        return env;
    } else {
        return NULL;
    }
}

static void cacheFFIType(JNIEnv *env, jclass nativeSimpleType, jclass libFFIType, jmethodID initializeSimpleType, jobjectArray simpleTypeMap,
        const char *enumName, ffi_type *type) {
    jfieldID enumField = (*env)->GetStaticFieldID(env, nativeSimpleType, enumName, "Lcom/oracle/truffle/nfi/types/NativeSimpleType;");
    jobject enumValue = (*env)->GetStaticObjectField(env, nativeSimpleType, enumField);

    (*env)->CallStaticVoidMethod(env, libFFIType, initializeSimpleType, enumValue, type->size, type->alignment, (jlong) type);
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_NativeAccess_initialize(JNIEnv *env, jclass self, jstring libName, jobjectArray simpleTypeMap) {
    (*env)->GetJavaVM(env, &jvm);

    jclass nativeSimpleType = (*env)->FindClass(env, "com/oracle/truffle/nfi/types/NativeSimpleType");
    jclass libFFIType = (*env)->FindClass(env, "com/oracle/truffle/nfi/LibFFIType");
    jmethodID initializeSimpleType = (*env)->GetStaticMethodID(env, libFFIType, "initializeSimpleType", "(Lcom/oracle/truffle/nfi/types/NativeSimpleType;IIJ)V");

    // it's important to initialize "POINTER" first, because the primitive array types depend on it
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "POINTER", &ffi_type_pointer);

    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "VOID",    &ffi_type_void);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "UINT8",   &ffi_type_uint8);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "SINT8",   &ffi_type_sint8);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "UINT16",  &ffi_type_uint16);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "SINT16",  &ffi_type_sint16);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "UINT32",  &ffi_type_uint32);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "SINT32",  &ffi_type_sint32);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "UINT64",  &ffi_type_uint64);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "SINT64",  &ffi_type_sint64);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "FLOAT",   &ffi_type_float);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "DOUBLE",  &ffi_type_double);

    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "STRING", &ffi_type_pointer);
    cacheFFIType(env, nativeSimpleType, libFFIType, initializeSimpleType, simpleTypeMap, "OBJECT", &ffi_type_pointer);

    initializeClosure(env);
    initializeSignature(env);
    initializeLookup(env, libName);
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_NativeAllocation_free(JNIEnv *env, jclass self, jlong pointer) {
    free((void*) pointer);
}

JNIEXPORT jstring JNICALL Java_com_oracle_truffle_nfi_NativeString_toJavaString(JNIEnv *env, jclass self, jlong pointer) {
    const char *str = (const char *) pointer;
    return (*env)->NewStringUTF(env, str);
}

TruffleObject newObjectRef(TruffleObject object) {
    JNIEnv *env = getEnv();
    return (TruffleObject) (*env)->NewGlobalRef(env, (jobject) object);
}

void releaseObjectRef(TruffleObject object) {
    JNIEnv *env = getEnv();
    (*env)->DeleteGlobalRef(env, (jobject) object);
}

TruffleObject releaseAndReturn(TruffleObject object) {
    JNIEnv *env = getEnv();
    jobject global = (jobject) object;
    jobject local = (*env)->NewLocalRef(env, global);
    (*env)->DeleteGlobalRef(env, global);
    return (TruffleObject) local;
}
