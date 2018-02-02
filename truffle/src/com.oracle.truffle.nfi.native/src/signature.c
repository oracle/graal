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

#include <errno.h>
#include <stdlib.h>
#include <ffi.h>
#include "internal.h"


struct cif_data {
    ffi_cif cif;
    ffi_type *args[0];
};

static int align_up(int index, int alignment) {
    if (index % alignment != 0) {
        index += alignment - (index % alignment);
    }
    return index;
}

static void executeHelper(JNIEnv *env, TruffleContext *ctx, void *ret, ffi_cif *cif, jlong address, jbyteArray primArgs, jint patchCount, jintArray patch, jobjectArray objArgs) {
    struct __TruffleEnvInternal truffleEnv;

    truffleEnv.functions = &truffleNativeAPI;
    truffleEnv.context = (struct __TruffleContextInternal *) ctx;
    truffleEnv.jniEnv = env;

    void *argPtrs[cif->nargs];

    jbyte *primArgValues = (*env)->GetByteArrayElements(env, primArgs, NULL);

    int primIdx = 0;
    int i;
    for (i = 0; i < cif->nargs; i++) {
        primIdx = align_up(primIdx, cif->arg_types[i]->alignment);
        argPtrs[i] = primArgValues + primIdx;
        primIdx += cif->arg_types[i]->size;
    }

    jint *patches = patchCount > 0 ? (*env)->GetIntArrayElements(env, patch, NULL) : NULL;

    enum TypeTag tagsForRelease[patchCount];
    jobject objectsForRelease[patchCount];
    const void *ptrsForRelease[patchCount];
    int releaseCount = 0;

    for (i = 0; i < patchCount; i++) {
        jobject arg = (*env)->GetObjectArrayElement(env, objArgs, i);
        enum TypeTag tag = DECODE_TAG(patches[i]);
        void *argPtr = primArgValues + DECODE_OFFSET(patches[i]);

        switch (tag) {
            case OBJECT:
                *((jobject*) argPtr) = arg;
                break;
            case STRING:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((const char **) argPtr) = (*env)->GetStringUTFChars(env, (jstring) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
            case CLOSURE:
                /*
                 * No need to patch anything, the byte array already contains the executable code from the closure.
                 * The LibFFIClosure object is only stored in the arguments array to make sure it stays alive.
                 */
                break;
            case ENV:
                *((TruffleEnv**) argPtr) = (TruffleEnv*) &truffleEnv;
                break;
            case BOOLEAN_ARRAY:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((jboolean**) argPtr) = (*env)->GetBooleanArrayElements(env, (jbooleanArray) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
            case BYTE_ARRAY:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((jbyte**) argPtr) = (*env)->GetByteArrayElements(env, (jbyteArray) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
            case CHAR_ARRAY:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((jchar**) argPtr) = (*env)->GetCharArrayElements(env, (jcharArray) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
            case SHORT_ARRAY:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((jshort**) argPtr) = (*env)->GetShortArrayElements(env, (jshortArray) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
            case INT_ARRAY:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((jint**) argPtr) = (*env)->GetIntArrayElements(env, (jintArray) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
            case LONG_ARRAY:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((jlong**) argPtr) = (*env)->GetLongArrayElements(env, (jlongArray) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
            case FLOAT_ARRAY:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((jfloat**) argPtr) = (*env)->GetFloatArrayElements(env, (jfloatArray) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
            case DOUBLE_ARRAY:
                objectsForRelease[releaseCount] = arg;
                ptrsForRelease[releaseCount] = *((jdouble**) argPtr) = (*env)->GetDoubleArrayElements(env, (jdoubleArray) arg, NULL);
                tagsForRelease[releaseCount++] = tag;
                break;
        }
    }

    if (patches) {
        (*env)->ReleaseIntArrayElements(env, patch, patches, JNI_ABORT);
    }

    errno = errnoMirror;

    ffi_call(cif, (void (*)()) address, ret, argPtrs);

    errnoMirror = errno;

    (*env)->ReleaseByteArrayElements(env, primArgs, primArgValues, JNI_ABORT);

    for (i = 0; i < releaseCount; i++) {
        switch (tagsForRelease[i]) {
            case OBJECT:
            case CLOSURE:
            case ENV:
                // nothing to do
                break;
            case STRING:
                (*env)->ReleaseStringUTFChars(env, (jstring) objectsForRelease[i], (const char *) ptrsForRelease[i]);
                break;
            case BOOLEAN_ARRAY:
                (*env)->ReleaseBooleanArrayElements(env, (jbooleanArray) objectsForRelease[i], (jboolean*) ptrsForRelease[i], 0);
                break;
            case BYTE_ARRAY:
                (*env)->ReleaseByteArrayElements(env, (jbyteArray) objectsForRelease[i], (jbyte*) ptrsForRelease[i], 0);
                break;
            case CHAR_ARRAY:
                (*env)->ReleaseCharArrayElements(env, (jcharArray) objectsForRelease[i], (jchar*) ptrsForRelease[i], 0);
                break;
            case SHORT_ARRAY:
                (*env)->ReleaseShortArrayElements(env, (jshortArray) objectsForRelease[i], (jshort*) ptrsForRelease[i], 0);
                break;
            case INT_ARRAY:
                (*env)->ReleaseIntArrayElements(env, (jintArray) objectsForRelease[i], (jint*) ptrsForRelease[i], 0);
                break;
            case LONG_ARRAY:
                (*env)->ReleaseLongArrayElements(env, (jlongArray) objectsForRelease[i], (jlong*) ptrsForRelease[i], 0);
                break;
            case FLOAT_ARRAY:
                (*env)->ReleaseFloatArrayElements(env, (jfloatArray) objectsForRelease[i], (jfloat*) ptrsForRelease[i], 0);
                break;
            case DOUBLE_ARRAY:
                (*env)->ReleaseDoubleArrayElements(env, (jdoubleArray) objectsForRelease[i], (jdouble*) ptrsForRelease[i], 0);
                break;
        }
    }
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_executeNative(JNIEnv *env, jclass self, jlong truffleContext, jlong cif, jlong address,
        jbyteArray primArgs, jint patchCount, jintArray patch, jobjectArray objArgs, jbyteArray retArray) {
    jbyte *ret = retArray ? (*env)->GetByteArrayElements(env, retArray, NULL) : NULL;
    executeHelper(env, (TruffleContext*) truffleContext, ret, (ffi_cif*) cif, address, primArgs, patchCount, patch, objArgs);
    if (retArray) {
        (*env)->ReleaseByteArrayElements(env, retArray, ret, 0);
    }
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_executePrimitive(JNIEnv *env, jclass self, jlong truffleContext, jlong cif, jlong address,
        jbyteArray primArgs, jint patchCount, jintArray patch, jobjectArray objArgs) {
    ffi_arg ret;
    executeHelper(env, (TruffleContext*) truffleContext, &ret, (ffi_cif*) cif, address, primArgs, patchCount, patch, objArgs);
    return (jlong) ret;
}

JNIEXPORT jobject JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_executeObject(JNIEnv *env, jclass self, jlong truffleContext, jlong cif, jlong address,
        jbyteArray primArgs, jint patchCount, jintArray patch, jobjectArray objArgs) {
    jobject ret;
    executeHelper(env, (TruffleContext*) truffleContext, &ret, (ffi_cif*) cif, address, primArgs, patchCount, patch, objArgs);
    return ret;
}


static struct cif_data *prepareArgs(JNIEnv *env, struct __TruffleContextInternal *ctx, int nargs, jobjectArray argTypes) {
    struct cif_data *data = (struct cif_data *) malloc(sizeof(struct cif_data) + nargs * sizeof(ffi_type*));
    int i;
    for (i = 0; i < nargs; i++) {
        jobject type = (*env)->GetObjectArrayElement(env, argTypes, i);
        data->args[i] = (ffi_type*) (*env)->GetLongField(env, type, ctx->LibFFIType_type);
    }
    return data;
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_prepareSignature(JNIEnv *env, jclass self, jlong nativeContext, jobject retType, jobjectArray argTypes) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) nativeContext;
    int nargs = (*env)->GetArrayLength(env, argTypes);

    struct cif_data *data = prepareArgs(env, ctx, nargs, argTypes);
    ffi_type *ret = (ffi_type*) (*env)->GetLongField(env, retType, ctx->LibFFIType_type);

    ffi_status result = ffi_prep_cif(&data->cif, FFI_DEFAULT_ABI, nargs, ret, data->args);

    if (result == FFI_OK) {
        return (jlong) data;
    } else {
        free(data);
        return 0;
    }
}

JNIEXPORT jlong JNICALL Java_com_oracle_truffle_nfi_impl_NFIContext_prepareSignatureVarargs(JNIEnv *env, jclass self, jlong nativeContext, jobject retType, jint nFixedArgs, jobjectArray argTypes) {
    struct __TruffleContextInternal *ctx = (struct __TruffleContextInternal *) nativeContext;
    int nargs = (*env)->GetArrayLength(env, argTypes);

    struct cif_data *data = prepareArgs(env, ctx, nargs, argTypes);
    ffi_type *ret = (ffi_type*) (*env)->GetLongField(env, retType, ctx->LibFFIType_type);

    ffi_status result = ffi_prep_cif_var(&data->cif, FFI_DEFAULT_ABI, nFixedArgs, nargs, ret, data->args);

    if (result == FFI_OK) {
        return (jlong) data;
    } else {
        free(data);
        return 0;
    }
}
