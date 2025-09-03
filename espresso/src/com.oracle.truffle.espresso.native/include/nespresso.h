/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
#ifndef _NESPRESSO_H
#define _NESPRESSO_H

#include <jni.h>

struct Varargs {
    const struct VarargsInterface* functions;
};

JNIEXPORT JNIEnv* JNICALL initializeNativeContext(void* (*fetch_by_name)(const char *));

JNIEXPORT void JNICALL disposeNativeContext(JNIEnv* env, void (*release_closure)(void *));

// varargs support
JNIEXPORT jboolean JNICALL pop_boolean(struct Varargs* varargs);
JNIEXPORT jbyte JNICALL pop_byte(struct Varargs* varargs);
JNIEXPORT jchar JNICALL pop_char(struct Varargs* varargs);
JNIEXPORT jshort JNICALL pop_short(struct Varargs* varargs);
JNIEXPORT jint JNICALL pop_int(struct Varargs* varargs);
JNIEXPORT jfloat JNICALL pop_float(struct Varargs* varargs);
JNIEXPORT jdouble JNICALL pop_double(struct Varargs* varargs);
JNIEXPORT jlong JNICALL pop_long(struct Varargs* varargs);
JNIEXPORT jobject JNICALL pop_object(struct Varargs* varargs);
JNIEXPORT void* JNICALL pop_word(struct Varargs* varargs);

JNIEXPORT void * JNICALL allocateMemory(size_t size);
JNIEXPORT void JNICALL freeMemory(void *ptr);
JNIEXPORT void * JNICALL reallocateMemory(void *ptr, size_t new_size);
JNIEXPORT void JNICALL ctypeInit(void);

#define JNI_FUNCTION_LIST(V) \
    V(GetVersion) \
    V(DefineClass) \
    V(FindClass) \
    V(FromReflectedMethod) \
    V(FromReflectedField) \
    V(ToReflectedMethod) \
    V(GetSuperclass) \
    V(IsAssignableFrom) \
    V(ToReflectedField) \
    V(Throw) \
    V(ThrowNew) \
    V(ExceptionOccurred) \
    V(ExceptionDescribe) \
    V(ExceptionClear) \
    V(FatalError) \
    V(PushLocalFrame) \
    V(PopLocalFrame) \
    V(DeleteLocalRef) \
    V(NewLocalRef) \
    V(EnsureLocalCapacity) \
    V(AllocObject) \
    V(GetObjectClass) \
    V(IsInstanceOf) \
    V(GetMethodID) \
    V(GetFieldID) \
    V(GetObjectField) \
    V(GetBooleanField) \
    V(GetByteField) \
    V(GetCharField) \
    V(GetShortField) \
    V(GetIntField) \
    V(GetLongField) \
    V(GetFloatField) \
    V(GetDoubleField) \
    V(SetObjectField) \
    V(SetBooleanField) \
    V(SetByteField) \
    V(SetCharField) \
    V(SetShortField) \
    V(SetIntField) \
    V(SetLongField) \
    V(SetFloatField) \
    V(SetDoubleField) \
    V(GetStaticMethodID) \
    V(GetStaticFieldID) \
    V(GetStaticObjectField) \
    V(GetStaticBooleanField) \
    V(GetStaticByteField) \
    V(GetStaticCharField) \
    V(GetStaticShortField) \
    V(GetStaticIntField) \
    V(GetStaticLongField) \
    V(GetStaticFloatField) \
    V(GetStaticDoubleField) \
    V(SetStaticObjectField) \
    V(SetStaticBooleanField) \
    V(SetStaticByteField) \
    V(SetStaticCharField) \
    V(SetStaticShortField) \
    V(SetStaticIntField) \
    V(SetStaticLongField) \
    V(SetStaticFloatField) \
    V(SetStaticDoubleField) \
    V(NewString) \
    V(GetStringLength) \
    V(GetStringChars) \
    V(ReleaseStringChars) \
    V(NewStringUTF) \
    V(GetStringUTFLength) \
    V(GetStringUTFChars) \
    V(ReleaseStringUTFChars) \
    V(GetArrayLength) \
    V(NewObjectArray) \
    V(GetObjectArrayElement) \
    V(SetObjectArrayElement) \
    V(NewBooleanArray) \
    V(NewByteArray) \
    V(NewCharArray) \
    V(NewShortArray) \
    V(NewIntArray) \
    V(NewLongArray) \
    V(NewFloatArray) \
    V(NewDoubleArray) \
    V(GetBooleanArrayElements) \
    V(GetByteArrayElements) \
    V(GetCharArrayElements) \
    V(GetShortArrayElements) \
    V(GetIntArrayElements) \
    V(GetLongArrayElements) \
    V(GetFloatArrayElements) \
    V(GetDoubleArrayElements) \
    V(ReleaseBooleanArrayElements) \
    V(ReleaseByteArrayElements) \
    V(ReleaseCharArrayElements) \
    V(ReleaseShortArrayElements) \
    V(ReleaseIntArrayElements) \
    V(ReleaseLongArrayElements) \
    V(ReleaseFloatArrayElements) \
    V(ReleaseDoubleArrayElements) \
    V(GetBooleanArrayRegion) \
    V(GetByteArrayRegion) \
    V(GetCharArrayRegion) \
    V(GetShortArrayRegion) \
    V(GetIntArrayRegion) \
    V(GetLongArrayRegion) \
    V(GetFloatArrayRegion) \
    V(GetDoubleArrayRegion) \
    V(SetBooleanArrayRegion) \
    V(SetByteArrayRegion) \
    V(SetCharArrayRegion) \
    V(SetShortArrayRegion) \
    V(SetIntArrayRegion) \
    V(SetLongArrayRegion) \
    V(SetFloatArrayRegion) \
    V(SetDoubleArrayRegion) \
    V(UnregisterNatives) \
    V(MonitorEnter) \
    V(MonitorExit) \
    V(GetStringRegion) \
    V(GetStringUTFRegion) \
    V(GetPrimitiveArrayCritical) \
    V(ReleasePrimitiveArrayCritical) \
    V(GetStringCritical) \
    V(ReleaseStringCritical) \
    V(ExceptionCheck) \
    V(GetDirectBufferAddress) \
    V(GetDirectBufferCapacity) \
    V(GetObjectRefType) \
    V(IsSameObject) \
    V(NewGlobalRef) \
    V(DeleteGlobalRef) \
    V(NewWeakGlobalRef) \
    V(DeleteWeakGlobalRef) \
    V(NewDirectByteBuffer) \
    V(GetModule) \
    V(IsVirtualThread)


#endif // _NESPRESSO_H


