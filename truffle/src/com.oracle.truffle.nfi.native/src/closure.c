/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
// Workaround for static linking. See comment in ffi.h, line 115.
#define FFI_BUILDING
#endif

#include "trufflenfi.h"
#include "native.h"

#include <errno.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <ffi.h>
#include "internal.h"

enum closure_arg_type {
    ARG_BUFFER,
    ARG_STRING,
    ARG_OBJECT,
    ARG_SKIP
};

/**
 * It is very important that this struct contains no strong references to anything that might
 * indirectly reference a Truffle context or engine. See the comment in ClosureNativePointer.
 */
struct closure_data {
    ffi_closure closure;

    struct __TruffleContextInternal *context;
    int envArgIdx;

    // Weak to break reference cycle, see comment in ClosureNativePointer.java.
    // These references will never actually die as long as this struct is alive.
    jweak callTarget;
    jweak receiver; // may be null, in that case the receiver is a constant in the callTarget

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
    int length = cif->nargs;
    jobjectArray argBuffers;
    int i;

    if (data->receiver) {
        length += 1;
    }
    if (retBuffer) {
        length += 1;
    }

    argBuffers = (*env)->NewObjectArray(env, length, ctx->Object, NULL);
    for (i = 0; i < cif->nargs; i++) {
        switch (data->argTypes[i]) {
            case ARG_BUFFER: {
                    jobject buffer = (*env)->AllocObject(env, ctx->NativeArgumentBuffer_Pointer);
                    (*env)->SetLongField(env, buffer, ctx->NativeArgumentBuffer_Pointer_pointer, (jlong)(intptr_t) args[i]);
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

            case ARG_SKIP:
                break;
        }
    }

    if (data->receiver) {
        (*env)->SetObjectArrayElement(env, argBuffers, i++, data->receiver);
    }
    if (retBuffer) {
        (*env)->SetObjectArrayElement(env, argBuffers, i++, retBuffer);
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
        *((const char **) retPtr) = (const char *)(intptr_t)(*env)->GetLongField(env, ret, ctx->NativeString_nativePointer);
    } else {
        // unsupported type
        *((void **) retPtr) = NULL;
    }
}

static void invoke_closure_buffer_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    struct closure_data *data = (struct closure_data *) user_data;
    JNIEnv *env;
    struct __TruffleContextInternal *ctx;

    jobject retBuffer;
    jobjectArray argBuffers;
    jobject retPatches;

    errnoMirror = errno;

    processEnvArg(data, args, &env, &ctx);

    (*env)->PushLocalFrame(env, 8);

    retBuffer = (*env)->AllocObject(env, ctx->NativeArgumentBuffer_Pointer);
    (*env)->SetLongField(env, retBuffer, ctx->NativeArgumentBuffer_Pointer_pointer, (jlong)(intptr_t) ret);

    argBuffers = create_arg_buffers(ctx, env, data, cif, args, retBuffer);

    retPatches = (*env)->CallObjectMethod(env, data->callTarget, ctx->CallTarget_call, argBuffers);

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
    struct closure_data *data = (struct closure_data *) user_data;
    JNIEnv *env;
    struct __TruffleContextInternal *ctx;

    jobjectArray argBuffers;
    jobject retObj;

    errnoMirror = errno;

    processEnvArg(data, args, &env, &ctx);

    (*env)->PushLocalFrame(env, 4);

    argBuffers = create_arg_buffers(ctx, env, data, cif, args, NULL);
    retObj = (*env)->CallObjectMethod(env, data->callTarget, ctx->CallTarget_call, argBuffers);

    *((jobject *) ret) = (*env)->NewGlobalRef(env, retObj);

    (*env)->PopLocalFrame(env, NULL);

    errno = errnoMirror;
}

static void invoke_closure_string_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    struct closure_data *data = (struct closure_data *) user_data;
    JNIEnv *env;
    struct __TruffleContextInternal *ctx;

    jobjectArray argBuffers;
    jobject retObj;

    errnoMirror = errno;

    processEnvArg(data, args, &env, &ctx);

    (*env)->PushLocalFrame(env, 4);

    argBuffers = create_arg_buffers(ctx, env, data, cif, args, NULL);
    retObj = (*env)->CallObjectMethod(env, data->callTarget, ctx->CallTarget_call, argBuffers);

    serialize_ret_string(ctx, env, retObj, ret);

    (*env)->PopLocalFrame(env, NULL);

    errno = errnoMirror;
}

static void invoke_closure_void_ret(ffi_cif *cif, void *ret, void **args, void *user_data) {
    struct closure_data *data = (struct closure_data *) user_data;
    JNIEnv *env;
    struct __TruffleContextInternal *ctx;

    jobjectArray argBuffers;

    errnoMirror = errno;

    processEnvArg(data, args, &env, &ctx);

    (*env)->PushLocalFrame(env, 4);

    argBuffers = create_arg_buffers(ctx, env, data, cif, args, NULL);
    (*env)->CallObjectMethod(env, data->callTarget, ctx->CallTarget_call, argBuffers);

    (*env)->PopLocalFrame(env, NULL);

    errno = errnoMirror;
}

jobject prepare_closure(JNIEnv *env, jlong context, jobject signature, jobject receiver, jobject callTarget, void (*invoke_closure)(ffi_cif *cif, void *ret, void **args, void *user_data)) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *)(intptr_t)context;
    ffi_cif *cif = (ffi_cif*)(intptr_t)(*env)->GetLongField(env, signature, ctx->LibFFISignature_cif);

    jobject sigInfo;
    jobjectArray argTypes;
    int i;

    void *code;
    struct closure_data *data = (struct closure_data *) ffi_closure_alloc(sizeof(struct closure_data) + cif->nargs * sizeof(enum closure_arg_type), &code);

    data->callTarget = (*env)->NewWeakGlobalRef(env, callTarget);
    if (receiver) {
        data->receiver = (*env)->NewWeakGlobalRef(env, receiver);
    } else {
        data->receiver = NULL;
    }

    data->context = ctx;
    data->envArgIdx = -1;

    sigInfo = (*env)->GetObjectField(env, signature, ctx->LibFFISignature_signatureInfo);
    argTypes = (jobjectArray) (*env)->GetObjectField(env, sigInfo, ctx->CachedSignatureInfo_argTypes);
    for (i = 0; i < cif->nargs; i++) {
        jobject argType = (*env)->GetObjectArrayElement(env, argTypes, i);
        if ((*env)->IsInstanceOf(env, argType, ctx->LibFFIType_StringType)) {
            data->argTypes[i] = ARG_STRING;
        } else if ((*env)->IsInstanceOf(env, argType, ctx->LibFFIType_ObjectType)) {
            data->argTypes[i] = ARG_OBJECT;
        } else if ((*env)->IsInstanceOf(env, argType, ctx->LibFFIType_NullableType)) {
            data->argTypes[i] = ARG_OBJECT;
        } else if ((*env)->IsInstanceOf(env, argType, ctx->LibFFIType_EnvType)) {
            data->argTypes[i] = ARG_SKIP;
            data->envArgIdx = i;
        } else {
            data->argTypes[i] = ARG_BUFFER;
        }
    }

    ffi_prep_closure_loc(&data->closure, cif, invoke_closure, data, code);

    return (*env)->CallObjectMethod(env, ctx->LibFFIContext, ctx->LibFFIContext_createClosureNativePointer, (jlong)(intptr_t)data, (jlong)(intptr_t)code, callTarget, signature, receiver);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_backend_libffi_LibFFIContext_allocateClosureObjectRet(JNIEnv *env, jclass self, jlong nativeContext, jobject signature, jobject callTarget, jobject receiver) {
    return prepare_closure(env, nativeContext, signature, receiver, callTarget, invoke_closure_object_ret);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_backend_libffi_LibFFIContext_allocateClosureStringRet(JNIEnv *env, jclass self, jlong nativeContext, jobject signature, jobject callTarget, jobject receiver) {
    return prepare_closure(env, nativeContext, signature, receiver, callTarget, invoke_closure_string_ret);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_backend_libffi_LibFFIContext_allocateClosureBufferRet(JNIEnv *env, jclass self, jlong nativeContext, jobject signature, jobject callTarget, jobject receiver) {
    return prepare_closure(env, nativeContext, signature, receiver, callTarget, invoke_closure_buffer_ret);
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_backend_libffi_LibFFIContext_allocateClosureVoidRet(JNIEnv *env, jclass self, jlong nativeContext, jobject signature, jobject callTarget, jobject receiver) {
    return prepare_closure(env, nativeContext, signature, receiver, callTarget, invoke_closure_void_ret);
}


JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_backend_libffi_ClosureNativePointer_freeClosure(JNIEnv *env, jclass self, jlong ptr) {
    struct closure_data *data = (struct closure_data *)(intptr_t)ptr;
    (*env)->DeleteWeakGlobalRef(env, data->callTarget);
    if (data->receiver) {
        (*env)->DeleteWeakGlobalRef(env, data->receiver);
    }
    ffi_closure_free(data);
}
