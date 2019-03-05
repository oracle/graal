/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
    void **argPtrs;
    jbyte *primArgValues;
    int primIdx, i;
    jint *patches;

    enum TypeTag *tagsForRelease;
    jobject *objectsForRelease;
    const void **ptrsForRelease;
    int releaseCount;

    truffleEnv.functions = &truffleNativeAPI;
    truffleEnv.context = (struct __TruffleContextInternal *) ctx;
    truffleEnv.jniEnv = env;

    argPtrs = alloca(sizeof(*argPtrs) * cif->nargs);

    primArgValues = (*env)->GetByteArrayElements(env, primArgs, NULL);

    primIdx = 0;
    for (i = 0; i < cif->nargs; i++) {
        primIdx = align_up(primIdx, cif->arg_types[i]->alignment);
        argPtrs[i] = primArgValues + primIdx;
        primIdx += cif->arg_types[i]->size;
    }

    patches = patchCount > 0 ? (*env)->GetIntArrayElements(env, patch, NULL) : NULL;

    tagsForRelease = alloca(sizeof(*tagsForRelease) * patchCount);
    objectsForRelease = alloca(sizeof(*objectsForRelease) * patchCount);
    ptrsForRelease = alloca(sizeof(*ptrsForRelease) * patchCount);
    releaseCount = 0;

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

    ffi_call(cif, (void (*)(void)) address, ret, argPtrs);

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
