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
#include "internal.h"

__thread int errnoMirror;

static TruffleContext *getTruffleContext(TruffleEnv *tenv) {
    struct __TruffleEnvInternal *env = (struct __TruffleEnvInternal *) tenv;
    return (TruffleContext*) env->context;
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
    (*env)->CallVoidMethod(env, context->NFIContext, context->NFIContext_newClosureRef, (jlong) closure);
}

static void releaseClosureRef(TruffleEnv *tenv, void *closure) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    struct __TruffleContextInternal *context = ienv->context;
    JNIEnv *env = ienv->jniEnv;
    (*env)->CallVoidMethod(env, context->NFIContext, context->NFIContext_releaseClosureRef, (jlong) closure);
}

static TruffleObject getClosureObject(TruffleEnv *tenv, void *closure) {
    struct __TruffleEnvInternal *ienv = (struct __TruffleEnvInternal *) tenv;
    struct __TruffleContextInternal *context = ienv->context;
    JNIEnv *env = ienv->jniEnv;
    jobject local = (*env)->CallObjectMethod(env, context->NFIContext, context->NFIContext_getClosureObject, (jlong) closure);
    jobject global = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    return (TruffleObject) global;
}


const struct __TruffleNativeAPI truffleNativeAPI = {
    getTruffleContext,
    newObjectRef,
    releaseObjectRef,
    releaseAndReturn,
    isSameObject,
    newClosureRef,
    releaseClosureRef,
    getClosureObject
};



static TruffleEnv *lookupTruffleEnvOrError(int status, JNIEnv *env, struct __TruffleContextInternal *ctx) {
    if (status == JNI_OK) {
        struct __TruffleEnvInternal *ret = (struct __TruffleEnvInternal *) (*env)->CallLongMethod(env, ctx->NFIContext, ctx->NFIContext_getNativeEnv);
        ret->jniEnv = env;
        return (TruffleEnv*) ret;
    } else {
        return NULL;
    }
}

static TruffleEnv *getTruffleEnv(TruffleContext *context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    JavaVM *vm = ctx->javaVM;

    JNIEnv *env;
    int ret = (*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6);
    return lookupTruffleEnvOrError(ret, env, ctx);
}

static TruffleEnv *attachCurrentThread(TruffleContext *context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    JavaVM *vm = ctx->javaVM;

    JNIEnv *env;
    int ret = (*vm)->AttachCurrentThread(vm, (void**) &env, NULL);
    return lookupTruffleEnvOrError(ret, env, ctx);
}

static void detachCurrentThread(TruffleContext *context) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) context;
    JavaVM *vm = ctx->javaVM;

    (*vm)->DetachCurrentThread(vm);
}

const struct __TruffleThreadAPI truffleThreadAPI = {
    getTruffleEnv,
    attachCurrentThread,
    detachCurrentThread
};
