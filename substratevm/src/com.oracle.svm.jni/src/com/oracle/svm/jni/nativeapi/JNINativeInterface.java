/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.nativeapi;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

@CContext(JNIHeaderDirectives.class)
@CStruct(value = "JNINativeInterface_", addStructKeyword = true)
public interface JNINativeInterface extends PointerBase {

    @CField
    WordPointer reserved0();

    @CField
    WordPointer reserved1();

    @CField
    WordPointer reserved2();

    @CField
    WordPointer reserved3();

    @CField
    CFunctionPointer getGetVersion();

    @CField
    void setGetVersion(CFunctionPointer p);

    @CField
    CFunctionPointer getDefineClass();

    @CField
    void setDefineClass(CFunctionPointer p);

    @CField
    CFunctionPointer getFindClass();

    @CField
    void setFindClass(CFunctionPointer p);

    @CField
    CFunctionPointer getFromReflectedMethod();

    @CField
    void setFromReflectedMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getFromReflectedField();

    @CField
    void setFromReflectedField(CFunctionPointer p);

    @CField
    CFunctionPointer getToReflectedMethod();

    @CField
    void setToReflectedMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getGetSuperclass();

    @CField
    void setGetSuperclass(CFunctionPointer p);

    @CField
    CFunctionPointer getIsAssignableFrom();

    @CField
    void setIsAssignableFrom(CFunctionPointer p);

    @CField
    CFunctionPointer getToReflectedField();

    @CField
    void setToReflectedField(CFunctionPointer p);

    @CField
    CFunctionPointer getThrow();

    @CField
    void setThrow(CFunctionPointer p);

    @CField
    CFunctionPointer getThrowNew();

    @CField
    void setThrowNew(CFunctionPointer p);

    @CField
    CFunctionPointer getExceptionOccurred();

    @CField
    void setExceptionOccurred(CFunctionPointer p);

    @CField
    CFunctionPointer getExceptionDescribe();

    @CField
    void setExceptionDescribe(CFunctionPointer p);

    @CField
    CFunctionPointer getExceptionClear();

    @CField
    void setExceptionClear(CFunctionPointer p);

    @CField
    CFunctionPointer getFatalError();

    @CField
    void setFatalError(CFunctionPointer p);

    @CField
    CFunctionPointer getPushLocalFrame();

    @CField
    void setPushLocalFrame(CFunctionPointer p);

    @CField
    CFunctionPointer getPopLocalFrame();

    @CField
    void setPopLocalFrame(CFunctionPointer p);

    @CField
    CFunctionPointer getNewGlobalRef();

    @CField
    void setNewGlobalRef(CFunctionPointer p);

    @CField
    CFunctionPointer getDeleteGlobalRef();

    @CField
    void setDeleteGlobalRef(CFunctionPointer p);

    @CField
    CFunctionPointer getDeleteLocalRef();

    @CField
    void setDeleteLocalRef(CFunctionPointer p);

    @CField
    CFunctionPointer getIsSameObject();

    @CField
    void setIsSameObject(CFunctionPointer p);

    @CField
    CFunctionPointer getNewLocalRef();

    @CField
    void setNewLocalRef(CFunctionPointer p);

    @CField
    CFunctionPointer getEnsureLocalCapacity();

    @CField
    void setEnsureLocalCapacity(CFunctionPointer p);

    @CField
    CFunctionPointer getAllocObject();

    @CField
    void setAllocObject(CFunctionPointer p);

    @CField
    CFunctionPointer getNewObject();

    @CField
    void setNewObject(CFunctionPointer p);

    @CField
    CFunctionPointer getNewObjectV();

    @CField
    void setNewObjectV(CFunctionPointer p);

    @CField
    CFunctionPointer getNewObjectA();

    @CField
    void setNewObjectA(CFunctionPointer p);

    @CField
    CFunctionPointer getGetObjectClass();

    @CField
    void setGetObjectClass(CFunctionPointer p);

    @CField
    CFunctionPointer getIsInstanceOf();

    @CField
    void setIsInstanceOf(CFunctionPointer p);

    @CField
    CFunctionPointer getGetMethodID();

    @CField
    void setGetMethodID(CFunctionPointer p);

    @CField
    CFunctionPointer getCallObjectMethod();

    @CField
    void setCallObjectMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallObjectMethodV();

    @CField
    void setCallObjectMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallObjectMethodA();

    @CField
    void setCallObjectMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallBooleanMethod();

    @CField
    void setCallBooleanMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallBooleanMethodV();

    @CField
    void setCallBooleanMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallBooleanMethodA();

    @CField
    void setCallBooleanMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallByteMethod();

    @CField
    void setCallByteMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallByteMethodV();

    @CField
    void setCallByteMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallByteMethodA();

    @CField
    void setCallByteMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallCharMethod();

    @CField
    void setCallCharMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallCharMethodV();

    @CField
    void setCallCharMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallCharMethodA();

    @CField
    void setCallCharMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallShortMethod();

    @CField
    void setCallShortMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallShortMethodV();

    @CField
    void setCallShortMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallShortMethodA();

    @CField
    void setCallShortMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallIntMethod();

    @CField
    void setCallIntMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallIntMethodV();

    @CField
    void setCallIntMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallIntMethodA();

    @CField
    void setCallIntMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallLongMethod();

    @CField
    void setCallLongMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallLongMethodV();

    @CField
    void setCallLongMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallLongMethodA();

    @CField
    void setCallLongMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallFloatMethod();

    @CField
    void setCallFloatMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallFloatMethodV();

    @CField
    void setCallFloatMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallFloatMethodA();

    @CField
    void setCallFloatMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallDoubleMethod();

    @CField
    void setCallDoubleMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallDoubleMethodV();

    @CField
    void setCallDoubleMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallDoubleMethodA();

    @CField
    void setCallDoubleMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallVoidMethod();

    @CField
    void setCallVoidMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallVoidMethodV();

    @CField
    void setCallVoidMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallVoidMethodA();

    @CField
    void setCallVoidMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualObjectMethod();

    @CField
    void setCallNonvirtualObjectMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualObjectMethodV();

    @CField
    void setCallNonvirtualObjectMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualObjectMethodA();

    @CField
    void setCallNonvirtualObjectMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualBooleanMethod();

    @CField
    void setCallNonvirtualBooleanMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualBooleanMethodV();

    @CField
    void setCallNonvirtualBooleanMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualBooleanMethodA();

    @CField
    void setCallNonvirtualBooleanMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualByteMethod();

    @CField
    void setCallNonvirtualByteMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualByteMethodV();

    @CField
    void setCallNonvirtualByteMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualByteMethodA();

    @CField
    void setCallNonvirtualByteMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualCharMethod();

    @CField
    void setCallNonvirtualCharMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualCharMethodV();

    @CField
    void setCallNonvirtualCharMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualCharMethodA();

    @CField
    void setCallNonvirtualCharMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualShortMethod();

    @CField
    void setCallNonvirtualShortMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualShortMethodV();

    @CField
    void setCallNonvirtualShortMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualShortMethodA();

    @CField
    void setCallNonvirtualShortMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualIntMethod();

    @CField
    void setCallNonvirtualIntMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualIntMethodV();

    @CField
    void setCallNonvirtualIntMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualIntMethodA();

    @CField
    void setCallNonvirtualIntMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualLongMethod();

    @CField
    void setCallNonvirtualLongMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualLongMethodV();

    @CField
    void setCallNonvirtualLongMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualLongMethodA();

    @CField
    void setCallNonvirtualLongMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualFloatMethod();

    @CField
    void setCallNonvirtualFloatMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualFloatMethodV();

    @CField
    void setCallNonvirtualFloatMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualFloatMethodA();

    @CField
    void setCallNonvirtualFloatMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualDoubleMethod();

    @CField
    void setCallNonvirtualDoubleMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualDoubleMethodV();

    @CField
    void setCallNonvirtualDoubleMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualDoubleMethodA();

    @CField
    void setCallNonvirtualDoubleMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualVoidMethod();

    @CField
    void setCallNonvirtualVoidMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualVoidMethodV();

    @CField
    void setCallNonvirtualVoidMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallNonvirtualVoidMethodA();

    @CField
    void setCallNonvirtualVoidMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getGetFieldID();

    @CField
    void setGetFieldID(CFunctionPointer p);

    @CField
    CFunctionPointer getGetObjectField();

    @CField
    void setGetObjectField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetBooleanField();

    @CField
    void setGetBooleanField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetByteField();

    @CField
    void setGetByteField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetCharField();

    @CField
    void setGetCharField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetShortField();

    @CField
    void setGetShortField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetIntField();

    @CField
    void setGetIntField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetLongField();

    @CField
    void setGetLongField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetFloatField();

    @CField
    void setGetFloatField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetDoubleField();

    @CField
    void setGetDoubleField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetObjectField();

    @CField
    void setSetObjectField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetBooleanField();

    @CField
    void setSetBooleanField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetByteField();

    @CField
    void setSetByteField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetCharField();

    @CField
    void setSetCharField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetShortField();

    @CField
    void setSetShortField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetIntField();

    @CField
    void setSetIntField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetLongField();

    @CField
    void setSetLongField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetFloatField();

    @CField
    void setSetFloatField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetDoubleField();

    @CField
    void setSetDoubleField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticMethodID();

    @CField
    void setGetStaticMethodID(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticObjectMethod();

    @CField
    void setCallStaticObjectMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticObjectMethodV();

    @CField
    void setCallStaticObjectMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticObjectMethodA();

    @CField
    void setCallStaticObjectMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticBooleanMethod();

    @CField
    void setCallStaticBooleanMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticBooleanMethodV();

    @CField
    void setCallStaticBooleanMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticBooleanMethodA();

    @CField
    void setCallStaticBooleanMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticByteMethod();

    @CField
    void setCallStaticByteMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticByteMethodV();

    @CField
    void setCallStaticByteMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticByteMethodA();

    @CField
    void setCallStaticByteMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticCharMethod();

    @CField
    void setCallStaticCharMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticCharMethodV();

    @CField
    void setCallStaticCharMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticCharMethodA();

    @CField
    void setCallStaticCharMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticShortMethod();

    @CField
    void setCallStaticShortMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticShortMethodV();

    @CField
    void setCallStaticShortMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticShortMethodA();

    @CField
    void setCallStaticShortMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticIntMethod();

    @CField
    void setCallStaticIntMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticIntMethodV();

    @CField
    void setCallStaticIntMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticIntMethodA();

    @CField
    void setCallStaticIntMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticLongMethod();

    @CField
    void setCallStaticLongMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticLongMethodV();

    @CField
    void setCallStaticLongMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticLongMethodA();

    @CField
    void setCallStaticLongMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticFloatMethod();

    @CField
    void setCallStaticFloatMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticFloatMethodV();

    @CField
    void setCallStaticFloatMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticFloatMethodA();

    @CField
    void setCallStaticFloatMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticDoubleMethod();

    @CField
    void setCallStaticDoubleMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticDoubleMethodV();

    @CField
    void setCallStaticDoubleMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticDoubleMethodA();

    @CField
    void setCallStaticDoubleMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticVoidMethod();

    @CField
    void setCallStaticVoidMethod(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticVoidMethodV();

    @CField
    void setCallStaticVoidMethodV(CFunctionPointer p);

    @CField
    CFunctionPointer getCallStaticVoidMethodA();

    @CField
    void setCallStaticVoidMethodA(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticFieldID();

    @CField
    void setGetStaticFieldID(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticObjectField();

    @CField
    void setGetStaticObjectField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticBooleanField();

    @CField
    void setGetStaticBooleanField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticByteField();

    @CField
    void setGetStaticByteField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticCharField();

    @CField
    void setGetStaticCharField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticShortField();

    @CField
    void setGetStaticShortField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticIntField();

    @CField
    void setGetStaticIntField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticLongField();

    @CField
    void setGetStaticLongField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticFloatField();

    @CField
    void setGetStaticFloatField(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStaticDoubleField();

    @CField
    void setGetStaticDoubleField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticObjectField();

    @CField
    void setSetStaticObjectField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticBooleanField();

    @CField
    void setSetStaticBooleanField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticByteField();

    @CField
    void setSetStaticByteField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticCharField();

    @CField
    void setSetStaticCharField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticShortField();

    @CField
    void setSetStaticShortField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticIntField();

    @CField
    void setSetStaticIntField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticLongField();

    @CField
    void setSetStaticLongField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticFloatField();

    @CField
    void setSetStaticFloatField(CFunctionPointer p);

    @CField
    CFunctionPointer getSetStaticDoubleField();

    @CField
    void setSetStaticDoubleField(CFunctionPointer p);

    @CField
    CFunctionPointer getNewString();

    @CField
    void setNewString(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStringLength();

    @CField
    void setGetStringLength(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStringChars();

    @CField
    void setGetStringChars(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseStringChars();

    @CField
    void setReleaseStringChars(CFunctionPointer p);

    @CField
    CFunctionPointer getNewStringUTF();

    @CField
    void setNewStringUTF(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStringUTFLength();

    @CField
    void setGetStringUTFLength(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStringUTFChars();

    @CField
    void setGetStringUTFChars(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseStringUTFChars();

    @CField
    void setReleaseStringUTFChars(CFunctionPointer p);

    @CField
    CFunctionPointer getGetArrayLength();

    @CField
    void setGetArrayLength(CFunctionPointer p);

    @CField
    CFunctionPointer getNewObjectArray();

    @CField
    void setNewObjectArray(CFunctionPointer p);

    @CField
    CFunctionPointer getGetObjectArrayElement();

    @CField
    void setGetObjectArrayElement(CFunctionPointer p);

    @CField
    CFunctionPointer getSetObjectArrayElement();

    @CField
    void setSetObjectArrayElement(CFunctionPointer p);

    @CField
    CFunctionPointer getNewBooleanArray();

    @CField
    void setNewBooleanArray(CFunctionPointer p);

    @CField
    CFunctionPointer getNewByteArray();

    @CField
    void setNewByteArray(CFunctionPointer p);

    @CField
    CFunctionPointer getNewCharArray();

    @CField
    void setNewCharArray(CFunctionPointer p);

    @CField
    CFunctionPointer getNewShortArray();

    @CField
    void setNewShortArray(CFunctionPointer p);

    @CField
    CFunctionPointer getNewIntArray();

    @CField
    void setNewIntArray(CFunctionPointer p);

    @CField
    CFunctionPointer getNewLongArray();

    @CField
    void setNewLongArray(CFunctionPointer p);

    @CField
    CFunctionPointer getNewFloatArray();

    @CField
    void setNewFloatArray(CFunctionPointer p);

    @CField
    CFunctionPointer getNewDoubleArray();

    @CField
    void setNewDoubleArray(CFunctionPointer p);

    @CField
    CFunctionPointer getGetBooleanArrayElements();

    @CField
    void setGetBooleanArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getGetByteArrayElements();

    @CField
    void setGetByteArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getGetCharArrayElements();

    @CField
    void setGetCharArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getGetShortArrayElements();

    @CField
    void setGetShortArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getGetIntArrayElements();

    @CField
    void setGetIntArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getGetLongArrayElements();

    @CField
    void setGetLongArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getGetFloatArrayElements();

    @CField
    void setGetFloatArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getGetDoubleArrayElements();

    @CField
    void setGetDoubleArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseBooleanArrayElements();

    @CField
    void setReleaseBooleanArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseByteArrayElements();

    @CField
    void setReleaseByteArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseCharArrayElements();

    @CField
    void setReleaseCharArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseShortArrayElements();

    @CField
    void setReleaseShortArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseIntArrayElements();

    @CField
    void setReleaseIntArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseLongArrayElements();

    @CField
    void setReleaseLongArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseFloatArrayElements();

    @CField
    void setReleaseFloatArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseDoubleArrayElements();

    @CField
    void setReleaseDoubleArrayElements(CFunctionPointer p);

    @CField
    CFunctionPointer getGetBooleanArrayRegion();

    @CField
    void setGetBooleanArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetByteArrayRegion();

    @CField
    void setGetByteArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetCharArrayRegion();

    @CField
    void setGetCharArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetShortArrayRegion();

    @CField
    void setGetShortArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetIntArrayRegion();

    @CField
    void setGetIntArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetLongArrayRegion();

    @CField
    void setGetLongArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetFloatArrayRegion();

    @CField
    void setGetFloatArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetDoubleArrayRegion();

    @CField
    void setGetDoubleArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getSetBooleanArrayRegion();

    @CField
    void setSetBooleanArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getSetByteArrayRegion();

    @CField
    void setSetByteArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getSetCharArrayRegion();

    @CField
    void setSetCharArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getSetShortArrayRegion();

    @CField
    void setSetShortArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getSetIntArrayRegion();

    @CField
    void setSetIntArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getSetLongArrayRegion();

    @CField
    void setSetLongArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getSetFloatArrayRegion();

    @CField
    void setSetFloatArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getSetDoubleArrayRegion();

    @CField
    void setSetDoubleArrayRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getRegisterNatives();

    @CField
    void setRegisterNatives(CFunctionPointer p);

    @CField
    CFunctionPointer getUnregisterNatives();

    @CField
    void setUnregisterNatives(CFunctionPointer p);

    @CField
    CFunctionPointer getMonitorEnter();

    @CField
    void setMonitorEnter(CFunctionPointer p);

    @CField
    CFunctionPointer getMonitorExit();

    @CField
    void setMonitorExit(CFunctionPointer p);

    @CField
    CFunctionPointer getGetJavaVM();

    @CField
    void setGetJavaVM(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStringRegion();

    @CField
    void setGetStringRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStringUTFRegion();

    @CField
    void setGetStringUTFRegion(CFunctionPointer p);

    @CField
    CFunctionPointer getGetPrimitiveArrayCritical();

    @CField
    void setGetPrimitiveArrayCritical(CFunctionPointer p);

    @CField
    CFunctionPointer getReleasePrimitiveArrayCritical();

    @CField
    void setReleasePrimitiveArrayCritical(CFunctionPointer p);

    @CField
    CFunctionPointer getGetStringCritical();

    @CField
    void setGetStringCritical(CFunctionPointer p);

    @CField
    CFunctionPointer getReleaseStringCritical();

    @CField
    void setReleaseStringCritical(CFunctionPointer p);

    @CField
    CFunctionPointer getNewWeakGlobalRef();

    @CField
    void setNewWeakGlobalRef(CFunctionPointer p);

    @CField
    CFunctionPointer getDeleteWeakGlobalRef();

    @CField
    void setDeleteWeakGlobalRef(CFunctionPointer p);

    @CField
    CFunctionPointer getExceptionCheck();

    @CField
    void setExceptionCheck(CFunctionPointer p);

    @CField
    CFunctionPointer getNewDirectByteBuffer();

    @CField
    void setNewDirectByteBuffer(CFunctionPointer p);

    @CField
    CFunctionPointer getGetDirectBufferAddress();

    @CField
    void setGetDirectBufferAddress(CFunctionPointer p);

    @CField
    CFunctionPointer getGetDirectBufferCapacity();

    @CField
    void setGetDirectBufferCapacity(CFunctionPointer p);

    @CField
    CFunctionPointer getGetObjectRefType();

    // JNI 1.6

    @CField
    void setGetObjectRefType(CFunctionPointer p);
}
