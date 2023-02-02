/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "trufflenfi.h"
#include "internal.h"

__thread int errnoMirror;

static TruffleContext *getTruffleContext(TruffleEnv *tenv) {
    struct __TruffleEnvInternal *env = (struct __TruffleEnvInternal *) tenv;
    return (TruffleContext *) env->context;
}

static TruffleObject newObjectRef(TruffleEnv *tenv, TruffleObject object) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    JNIEnv *env = ienv->jniEnv;
    return (TruffleObject) (*env)->NewGlobalRef(env, (jobject) object);
}

static void releaseObjectRef(TruffleEnv *tenv, TruffleObject object) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    JNIEnv *env = ienv->jniEnv;
    (*env)->DeleteGlobalRef(env, (jobject) object);
}

static TruffleObject releaseAndReturn(TruffleEnv *tenv, TruffleObject object) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    JNIEnv *env = ienv->jniEnv;
    jobject global = (jobject) object;
    jobject local = (*env)->NewLocalRef(env, global);
    (*env)->DeleteGlobalRef(env, global);
    return (TruffleObject) local;
}

static int isSameObject(TruffleEnv *tenv, TruffleObject object1, TruffleObject object2) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    JNIEnv *env = ienv->jniEnv;
    return (*env)->IsSameObject(env, (jobject) object1, (jobject) object2);
}

static void newClosureRef(TruffleEnv *tenv, void *closure) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    struct __TruffleContextInternal *context = ienv->context;
    JNIEnv *env = ienv->jniEnv;
    (*env)->CallVoidMethod(env, context->LibFFIContext, context->LibFFIContext_newClosureRef, (jlong) (intptr_t) closure);
}

static void releaseClosureRef(TruffleEnv *tenv, void *closure) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    struct __TruffleContextInternal *context = ienv->context;
    JNIEnv *env = ienv->jniEnv;
    (*env)->CallVoidMethod(env, context->LibFFIContext, context->LibFFIContext_releaseClosureRef, (jlong) (intptr_t) closure);
}

static TruffleObject getClosureObject(TruffleEnv *tenv, void *closure) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    struct __TruffleContextInternal *context = ienv->context;
    JNIEnv *env = ienv->jniEnv;
    jobject local = (*env)->CallObjectMethod(env, context->LibFFIContext, context->LibFFIContext_getClosureObject, (jlong) (intptr_t) closure);
    jobject global = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    return (TruffleObject) global;
}

const struct __TruffleNativeAPI truffleNativeAPI = { getTruffleContext, newObjectRef,  releaseObjectRef,  releaseAndReturn,
                                                     isSameObject,      newClosureRef, releaseClosureRef, getClosureObject };

static TruffleEnv *lookupTruffleEnvOrError(int status, JNIEnv *env, struct __TruffleContextInternal *ctx) {
    if (status == JNI_OK) {
        struct __TruffleEnvInternal *ret =
            (struct __TruffleEnvInternal *) (intptr_t) (*env)->CallLongMethod(env, ctx->LibFFIContext, ctx->LibFFIContext_getNativeEnv);
        ret->jniEnv = env;
        return (TruffleEnv *) ret;
    } else {
        return NULL;
    }
}

static TruffleEnv *getTruffleEnv(TruffleContext *context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    JavaVM *vm = ctx->javaVM;

    JNIEnv *env;
    int ret = (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6);
    return lookupTruffleEnvOrError(ret, env, ctx);
}

static TruffleEnv *attachCurrentThread(TruffleContext *context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    JavaVM *vm = ctx->javaVM;
    jboolean attachSuccess;

    JNIEnv *env;
    int ret = (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6);
    if (ret == JNI_EDETACHED) {
        ret = (*vm)->AttachCurrentThread(vm, (void **) &env, NULL);
        if (ret == JNI_OK) {
            attachSuccess = (*env)->CallBooleanMethod(env, ctx->LibFFIContext, ctx->LibFFIContext_attachThread);
            if (!attachSuccess) {
                (*vm)->DetachCurrentThread(vm);
                return NULL;
            }
        }
    }
    return lookupTruffleEnvOrError(ret, env, ctx);
}

static void detachCurrentThread(TruffleContext *context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    JavaVM *vm = ctx->javaVM;

    JNIEnv *env;
    int ret = (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6);
    if (ret == JNI_OK) {
        (*env)->CallVoidMethod(env, ctx->LibFFIContext, ctx->LibFFIContext_detachThread);
    }

    (*vm)->DetachCurrentThread(vm);
}

const struct __TruffleThreadAPI truffleThreadAPI = { getTruffleEnv, attachCurrentThread, detachCurrentThread };
