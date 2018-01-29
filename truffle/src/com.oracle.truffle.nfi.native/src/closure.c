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

#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <ffi.h>
#include "internal.h"

enum closure_arg_type {
    ARG_BUFFER,
    ARG_STRING,
    ARG_OBJECT,
    ARG_SKIP
};

struct closure_data {
    ffi_closure closure;

    struct __TruffleContextInternal *context;
    int envArgIdx;
    int skippedArgCount;

    jweak callTarget; // weak to break reference cycle, see comment in ClosureNativePointer.java

    enum closure_arg_type argTypes[0];
};


void processEnvArg(struct closure_data *closure, void **args, JNIEnv **jniEnv, struct __TruffleContextInternal **context) {
    if (closure->envArgIdx >= 0) {
        struct __TruffleEnvInternal *env = *(struct __TruffleEnvInternal **) args[closure->envArgIdx];
        *jniEnv = env->jniEnv;
        *context = env->context;
    } else {
        JavaVM *vm = closure->context->javaVM;
        int ret = (*vm)->GetEnv(vm, (void**) jniEnv, JNI_VERSION_1_6);
        if (ret == JNI_EDETACHED) {
            ret = (*vm)->AttachCurrentThread(vm, (void**) jniEnv, NULL);
        }
        *context = closure->context;
    }
}

static jobjectArray create_arg_buffers(struct __TruffleContextInternal *ctx, JNIEnv *env, struct closure_data *data, ffi_cif *cif, void **args, jobject retBuffer) {
    int length = cif->nargs - data->skippedArgCount;
    if (retBuffer) {
        length += 1;
    }

    jobjectArray argBuffers = (*env)->NewObjectArray(env, length, ctx->Object, NULL);
    int argIdx = 0;
    int i;
    for (i = 0; i < cif->nargs; i++) {
        switch (data->argTypes[i]) {
            case ARG_BUFFER: {
                    jobject buffer = (*env)->NewDirectByteBuffer(env, args[i], cif->arg_types[i]->size);
                    (*env)->SetObjectArrayElement(env, argBuffers, argIdx++, buffer);
                    (*env)->DeleteLocalRef(env, buffer);
                }
                break;

            case ARG_STRING: {
                    jstring str = (*env)->NewStringUTF(env, *(const char **) args[i]);
                    (*env)->SetObjectArrayElement(env, argBuffers, argIdx++, str);
                    (*env)->DeleteLocalRef(env, str);
                }
                break;

            case ARG_OBJECT:
                (*env)->SetObjectArrayElement(env, argBuffers, argIdx++, *(jobject *) args[i]);
                break;

            case ARG_SKIP:
                break;
        }
    }

    if (retBuffer) {
        (*env)->SetObjectArrayElement(env, argBuffers, length - 1, retBuffer);
    }

    return argBuffers;
}

static void serialize_ret_string(struct __TruffleContextInternal *ctx, JNIEnv *env, jobject ret, void *retPtr) {
    if (ret == NULL) {
        *((void **) retPtr) = NULL;
    } else if ((*env)->IsInstanceOf(env, ret, ctx->String)) {
        jstring str = (jstring) ret;
        const char *chars = (*env)->GetStringUTFChars(env, str, NULL);
        *((const char **) retPtr) = strdup(chars);
        (*env)->ReleaseStringUTFChars(env, str, chars);
    } else if ((*env)->IsInstanceOf(env, ret, ctx->NativeString)) {
        *((const char **) retPtr) = (const char *) (*env)->GetLongField(env, ret, ctx->NativeString_nativePointer);
    } else {
        // unsupported type
        *((void **) retPtr) = NULL;
    }
}

static void invoke_closure_buffer_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    errnoMirror = errno;

    struct closure_data *data = (struct closure_data *) user_data;

    JNIEnv *env;
    struct __TruffleContextInternal *ctx;
    processEnvArg(data, args, &env, &ctx);

    (*env)->PushLocalFrame(env, 8);

    int retSize = cif->rtype->size;
    if (retSize < sizeof(ffi_arg)) {
        retSize = sizeof(ffi_arg);
    }
    jobject retBuffer = (*env)->NewDirectByteBuffer(env, ret, retSize);

    jobjectArray argBuffers = create_arg_buffers(ctx, env, data, cif, args, retBuffer);

    jobject retPatches = (*env)->CallObjectMethod(env, data->callTarget, ctx->CallTarget_call, argBuffers);

    if (retPatches) {
        int patchCount = (*env)->GetIntField(env, retPatches, ctx->RetPatches_count);
        jintArray patches = (jintArray) (*env)->GetObjectField(env, retPatches, ctx->RetPatches_patches);
        jint *encoded = (*env)->GetIntArrayElements(env, patches, NULL);

        jobjectArray objects = (jobjectArray) (*env)->GetObjectField(env, retPatches, ctx->RetPatches_objects);

        int i;
        for (i = 0; i < patchCount; i++) {
            jobject retObj = (*env)->GetObjectArrayElement(env, objects, i);
            enum TypeTag tag = DECODE_TAG(encoded[i]);
            void *retPtr = ((char *) ret) + DECODE_OFFSET(encoded[i]);

            switch (tag) {
                case OBJECT:
                    *((jobject *) retPtr) = (*env)->NewGlobalRef(env, retObj);
                    break;
                case STRING:
                    serialize_ret_string(ctx, env, retObj, retPtr);
                    break;
                default:
                    // nothing to do
                    break;
            }

            (*env)->DeleteLocalRef(env, retObj);
        }

        (*env)->ReleaseIntArrayElements(env, patches, encoded, JNI_ABORT);
    }

    (*env)->PopLocalFrame(env, NULL);

    errno = errnoMirror;
}

static void invoke_closure_object_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    errnoMirror = errno;

    struct closure_data *data = (struct closure_data *) user_data;

    JNIEnv *env;
    struct __TruffleContextInternal *ctx;
    processEnvArg(data, args, &env, &ctx);

    (*env)->PushLocalFrame(env, 4);

    jobjectArray argBuffers = create_arg_buffers(ctx, env, data, cif, args, NULL);
    jobject retObj = (*env)->CallObjectMethod(env, data->callTarget, ctx->CallTarget_call, argBuffers);

    *((jobject *) ret) = (*env)->NewGlobalRef(env, retObj);

    (*env)->PopLocalFrame(env, NULL);

    errno = errnoMirror;
}

static void invoke_closure_string_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    errnoMirror = errno;

    struct closure_data *data = (struct closure_data *) user_data;

    JNIEnv *env;
    struct __TruffleContextInternal *ctx;
    processEnvArg(data, args, &env, &ctx);

    (*env)->PushLocalFrame(env, 4);

    jobjectArray argBuffers = create_arg_buffers(ctx, env, data, cif, args, NULL);
    jobject retObj = (*env)->CallObjectMethod(env, data->callTarget, ctx->CallTarget_call, argBuffers);

    serialize_ret_string(ctx, env, retObj, ret);

    (*env)->PopLocalFrame(env, NULL);

    errno = errnoMirror;
}

static void invoke_closure_void_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    errnoMirror = errno;

    struct closure_data *data = (struct closure_data *) user_data;

    JNIEnv *env;
    struct __TruffleContextInternal *ctx;
    processEnvArg(data, args, &env, &ctx);

    (*env)->PushLocalFrame(env, 4);

    jobjectArray argBuffers = create_arg_buffers(ctx, env, data, cif, args, NULL);
    (*env)->CallObjectMethod(env, data->callTarget, ctx->CallTarget_call, argBuffers);

    (*env)->PopLocalFrame(env, NULL);

    errno = errnoMirror;
}

jobject prepare_closure(JNIEnv *env, jlong context, jobject signature, jobject callTarget, void (*invoke_closure)(ffi_cif *cif, void *ret, void **args, void *user_data)) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    ffi_cif *cif = (ffi_cif*) (*env)->GetLongField(env, signature, ctx->LibFFISignature_cif);

    void *code;
    struct closure_data *data = (struct closure_data *) ffi_closure_alloc(sizeof(struct closure_data) + cif->nargs * sizeof(enum closure_arg_type), &code);
    data->callTarget = (*env)->NewWeakGlobalRef(env, callTarget);

    data->context = ctx;
    data->envArgIdx = -1;
    data->skippedArgCount = 0;

    jobjectArray argTypes = (jobjectArray) (*env)->GetObjectField(env, signature, ctx->LibFFISignature_argTypes);
    int i;
    for (i = 0; i < cif->nargs; i++) {
        jobject argType = (*env)->GetObjectArrayElement(env, argTypes, i);
        if ((*env)->IsInstanceOf(env, argType, ctx->LibFFIType_StringType)) {
            data->argTypes[i] = ARG_STRING;
        } else if ((*env)->IsInstanceOf(env, argType, ctx->LibFFIType_ObjectType)) {
            data->argTypes[i] = ARG_OBJECT;
        } else if ((*env)->IsInstanceOf(env, argType, ctx->LibFFIType_EnvType)) {
            data->argTypes[i] = ARG_SKIP;
            data->skippedArgCount++;
            data->envArgIdx = i;
        } else {
            data->argTypes[i] = ARG_BUFFER;
        }
    }

    ffi_prep_closure_loc(&data->closure, cif, invoke_closure, data, code);

    return (*env)->CallObjectMethod(env, ctx->NFIContext, ctx->NFIContext_createClosureNativePointer, (jlong) data, (jlong) code, callTarget, signature);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_allocateClosureObjectRet(JNIEnv *env, jclass self, jlong nativeContext, jobject signature, jobject callTarget) {
    return prepare_closure(env, nativeContext, signature, callTarget, invoke_closure_object_ret);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_allocateClosureStringRet(JNIEnv *env, jclass self, jlong nativeContext, jobject signature, jobject callTarget) {
    return prepare_closure(env, nativeContext, signature, callTarget, invoke_closure_string_ret);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_allocateClosureBufferRet(JNIEnv *env, jclass self, jlong nativeContext, jobject signature, jobject callTarget) {
    return prepare_closure(env, nativeContext, signature, callTarget, invoke_closure_buffer_ret);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_allocateClosureVoidRet(JNIEnv *env, jclass self, jlong nativeContext, jobject signature, jobject callTarget) {
    return prepare_closure(env, nativeContext, signature, callTarget, invoke_closure_void_ret);
}


JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_impl_ClosureNativePointer_freeClosure(JNIEnv *env, jclass self, jlong ptr) {
    struct closure_data *data = (struct closure_data *) ptr;
    (*env)->DeleteWeakGlobalRef(env, data->callTarget);
    ffi_closure_free(data);
}
