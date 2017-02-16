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
#include "trufflenfi.h"
#include "native.h"

#include <stdlib.h>
#include <string.h>
#include <ffi.h>
#include "internal.h"

static jmethodID CallTarget_call;

static jfieldID LibFFISignature_argTypes;

static jclass LibFFIType_ObjectType;
static jclass LibFFIType_StringType;

static jfieldID RetPatches_count;
static jfieldID RetPatches_patches;
static jfieldID RetPatches_objects;

static jclass Object;
static jclass ByteBuffer;
static jclass String;

static jclass NativeString;
static jfieldID NativeString_nativePointer;

static jclass ClosureNativePointer;
static jmethodID ClosureNativePointer_create;
static jmethodID ClosureNativePointer_newClosureRef;
static jmethodID ClosureNativePointer_releaseClosureRef;

enum closure_arg_type {
    ARG_BUFFER,
    ARG_STRING,
    ARG_OBJECT
};

struct closure_data {
    ffi_closure closure;
    jobject callTarget;

    enum closure_arg_type argTypes[0];
};


void initializeClosure(JNIEnv *env) {
    jclass CallTarget = (*env)->FindClass(env, "com/oracle/truffle/api/CallTarget");
    CallTarget_call = (*env)->GetMethodID(env, CallTarget, "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    jclass LibFFISignature = (*env)->FindClass(env, "com/oracle/truffle/nfi/LibFFISignature");
    LibFFISignature_argTypes = (*env)->GetFieldID(env, LibFFISignature, "argTypes", "[Lcom/oracle/truffle/nfi/LibFFIType;");

    LibFFIType_ObjectType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/LibFFIType$ObjectType"));
    LibFFIType_StringType = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/LibFFIType$StringType"));

    jclass LibFFIClosure_RetPatches = (*env)->FindClass(env, "com/oracle/truffle/nfi/LibFFIClosure$RetPatches");
    RetPatches_count = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "count", "I");
    RetPatches_patches = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "patches", "[I");
    RetPatches_objects = (*env)->GetFieldID(env, LibFFIClosure_RetPatches, "objects", "[Ljava/lang/Object;");

    Object = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/Object"));
    ByteBuffer = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/nio/ByteBuffer"));
    String = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/String"));

    NativeString = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/NativeString"));
    NativeString_nativePointer = (*env)->GetFieldID(env, NativeString, "nativePointer", "J");

    ClosureNativePointer = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/oracle/truffle/nfi/ClosureNativePointer"));
    ClosureNativePointer_create = (*env)->GetStaticMethodID(env, ClosureNativePointer, "create", "(JJ)Lcom/oracle/truffle/nfi/ClosureNativePointer;");
    ClosureNativePointer_newClosureRef = (*env)->GetStaticMethodID(env, ClosureNativePointer, "newClosureRef", "(J)V");
    ClosureNativePointer_releaseClosureRef = (*env)->GetStaticMethodID(env, ClosureNativePointer, "releaseClosureRef", "(J)V");
}

static jobjectArray create_arg_buffers(JNIEnv *env, struct closure_data *data, ffi_cif *cif, void **args, jobject retBuffer) {
    int length = cif->nargs;
    if (retBuffer) {
        length += 1;
    }

    jobjectArray argBuffers = (*env)->NewObjectArray(env, length, Object, NULL);
    int i;
    for (i = 0; i < cif->nargs; i++) {
        switch (data->argTypes[i]) {
            case ARG_BUFFER: {
                    jobject buffer = (*env)->NewDirectByteBuffer(env, args[i], cif->arg_types[i]->size);
                    (*env)->SetObjectArrayElement(env, argBuffers, i, buffer);
                    (*env)->DeleteLocalRef(env, buffer);
                }
                break;

            case ARG_STRING: {
                    jstring str = (*env)->NewStringUTF(env, *(const char **) args[i]);
                    (*env)->SetObjectArrayElement(env, argBuffers, i, str);
                    (*env)->DeleteLocalRef(env, str);
                }
                break;

            case ARG_OBJECT:
                (*env)->SetObjectArrayElement(env, argBuffers, i, *(jobject *) args[i]);
                break;
        }
    }

    if (retBuffer) {
        (*env)->SetObjectArrayElement(env, argBuffers, length - 1, retBuffer);
    }

    return argBuffers;
}

static void serialize_ret_value(JNIEnv *env, jobject ret, void *retPtr) {
    if (ret == NULL) {
        *((void **) retPtr) = NULL;
    } else if ((*env)->IsInstanceOf(env, ret, String)) {
        jstring str = (jstring) ret;
        const char *chars = (*env)->GetStringUTFChars(env, str, NULL);
        *((const char **) retPtr) = strdup(chars);
        (*env)->ReleaseStringUTFChars(env, str, chars);
    } else if ((*env)->IsInstanceOf(env, ret, NativeString)) {
        *((const char **) retPtr) = (const char *) (*env)->GetLongField(env, ret, NativeString_nativePointer);
    } else {
        *((jobject *) retPtr) = (*env)->NewGlobalRef(env, ret);
    }
}

static void invoke_closure_buffer_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    JNIEnv *env = getEnv();
    struct closure_data *data = (struct closure_data *) user_data;

    (*env)->PushLocalFrame(env, 8);

    int retSize = cif->rtype->size;
    if (retSize < sizeof(ffi_arg)) {
        retSize = sizeof(ffi_arg);
    }
    jobject retBuffer = (*env)->NewDirectByteBuffer(env, ret, retSize);

    jobjectArray argBuffers = create_arg_buffers(env, data, cif, args, retBuffer);

    jobject retPatches = (*env)->CallObjectMethod(env, data->callTarget, CallTarget_call, argBuffers);

    if (retPatches) {
        int patchCount = (*env)->GetIntField(env, retPatches, RetPatches_count);
        jintArray patches = (jintArray) (*env)->GetObjectField(env, retPatches, RetPatches_patches);
        jint *encoded = (*env)->GetIntArrayElements(env, patches, NULL);

        jobjectArray objects = (jobjectArray) (*env)->GetObjectField(env, retPatches, RetPatches_objects);

        int i;
        for (i = 0; i < patchCount; i++) {
            jobject retObj = (*env)->GetObjectArrayElement(env, objects, i);
            serialize_ret_value(env, retObj, ((char *) ret) + DECODE_OFFSET(encoded[i]));
            (*env)->DeleteLocalRef(env, retObj);
        }

        (*env)->ReleaseIntArrayElements(env, patches, encoded, JNI_ABORT);
    }

    (*env)->PopLocalFrame(env, NULL);
}

static void invoke_closure_object_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    JNIEnv *env = getEnv();
    struct closure_data *data = (struct closure_data *) user_data;

    (*env)->PushLocalFrame(env, 4);

    jobjectArray argBuffers = create_arg_buffers(env, data, cif, args, NULL);
    jobject retObj = (*env)->CallObjectMethod(env, data->callTarget, CallTarget_call, argBuffers);

    serialize_ret_value(env, retObj, ret);

    (*env)->PopLocalFrame(env, NULL);
}

static void invoke_closure_void_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    JNIEnv *env = getEnv();
    struct closure_data *data = (struct closure_data *) user_data;

    (*env)->PushLocalFrame(env, 4);

    jobjectArray argBuffers = create_arg_buffers(env, data, cif, args, NULL);
    (*env)->CallObjectMethod(env, data->callTarget, CallTarget_call, argBuffers);

    (*env)->PopLocalFrame(env, NULL);
}

jobject prepare_closure(JNIEnv *env, jobject signature, jobject callTarget, void (*invoke_closure)(ffi_cif *cif, void *ret, void **args, void *user_data)) {
    ffi_cif *cif = get_ffi_cif(env, signature);

    void *code;
    struct closure_data *data = (struct closure_data *) ffi_closure_alloc(sizeof(struct closure_data) + cif->nargs * sizeof(enum closure_arg_type), &code);
    data->callTarget = (*env)->NewGlobalRef(env, callTarget);

    jobjectArray argTypes = (jobjectArray) (*env)->GetObjectField(env, signature, LibFFISignature_argTypes);
    int i;
    for (i = 0; i < cif->nargs; i++) {
        jobject argType = (*env)->GetObjectArrayElement(env, argTypes, i);
        if ((*env)->IsInstanceOf(env, argType, LibFFIType_StringType)) {
            data->argTypes[i] = ARG_STRING;
        } else if ((*env)->IsInstanceOf(env, argType, LibFFIType_ObjectType)) {
            data->argTypes[i] = ARG_OBJECT;
        } else {
            data->argTypes[i] = ARG_BUFFER;
        }
    }

    ffi_prep_closure_loc(&data->closure, cif, invoke_closure, data, code);

    return (*env)->CallStaticObjectMethod(env, ClosureNativePointer, ClosureNativePointer_create, (jlong) data, (jlong) code);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_LibFFIClosure_allocateClosureObjectRet(JNIEnv *env, jclass self, jobject signature, jobject callTarget) {
    return prepare_closure(env, signature, callTarget, invoke_closure_object_ret);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_LibFFIClosure_allocateClosureBufferRet(JNIEnv *env, jclass self, jobject signature, jobject callTarget) {
    return prepare_closure(env, signature, callTarget, invoke_closure_buffer_ret);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_LibFFIClosure_allocateClosureVoidRet(JNIEnv *env, jclass self, jobject signature, jobject callTarget) {
    return prepare_closure(env, signature, callTarget, invoke_closure_void_ret);
}


JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_ClosureNativePointer_freeClosure(JNIEnv *env, jclass self, jlong ptr) {
    struct closure_data *data = (struct closure_data *) ptr;
    (*env)->DeleteGlobalRef(env, data->callTarget);
    ffi_closure_free(data);
}

void newClosureRef(void *closure) {
    JNIEnv *env = getEnv();
    (*env)->CallStaticVoidMethod(env, ClosureNativePointer, ClosureNativePointer_newClosureRef, (jlong) closure);
}

void releaseClosureRef(void *closure) {
    JNIEnv *env = getEnv();
    (*env)->CallStaticVoidMethod(env, ClosureNativePointer, ClosureNativePointer_releaseClosureRef, (jlong) closure);
}
