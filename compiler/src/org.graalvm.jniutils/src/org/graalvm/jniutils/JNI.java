/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.jniutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;

import jdk.vm.ci.services.Services;

public final class JNI {

    public static final int JNI_OK = 0;
    public static final int JNI_ERR = -1; /* unknown error */
    public static final int JNI_EDETACHED = -2; /* thread detached from the VM */
    public static final int JNI_EVERSION = -3; /* JNI version error */
    public static final int JNI_ENOMEM = -4; /* not enough memory */
    public static final int JNI_EEXIST = -5; /* VM already created */
    public static final int JNI_EINVAL = -6; /* invalid arguments */
    public static final int JNI_VERSION_10 = 0x000a0000;

    private JNI() {
        throw new IllegalStateException("No instance allowed");
    }

    public interface JMethodID extends PointerBase {
    }

    public interface JFieldID extends PointerBase {
    }

    public interface JObject extends PointerBase {
    }

    public interface JArray extends JObject {
        int MODE_WRITE_RELEASE = 0;
        int MODE_WRITE = 1;
        int MODE_RELEASE = 2;
    }

    public interface JBooleanArray extends JArray {
    }

    public interface JByteArray extends JArray {
    }

    public interface JCharArray extends JArray {
    }

    public interface JShortArray extends JArray {
    }

    public interface JIntArray extends JArray {
    }

    public interface JLongArray extends JArray {
    }

    public interface JFloatArray extends JArray {
    }

    public interface JDoubleArray extends JArray {
    }

    public interface JObjectArray extends JArray {
    }

    public interface JClass extends JObject {
    }

    public interface JString extends JObject {
    }

    public interface JThrowable extends JObject {
    }

    public interface JWeak extends JObject {
    }

    /**
     * Access to the {@code jvalue} JNI union.
     *
     * <pre>
     * typedef union jvalue {
     *    jboolean z;
     *    jbyte    b;
     *    jchar    c;
     *    jshort   s;
     *    jint     i;
     *    jlong    j;
     *    jfloat   f;
     *    jdouble  d;
     *    jobject  l;
     * } jvalue;
     * </pre>
     */
    @CContext(JNIHeaderDirectives.class)
    @CStruct("jvalue")
    public interface JValue extends PointerBase {
        // @formatter:off
        @CField("z")    boolean getBoolean();
        @CField("b")    byte    getByte();
        @CField("c")    char    getChar();
        @CField("s")    short   getShort();
        @CField("i")    int     getInt();
        @CField("j")    long    getLong();
        @CField("f")    float   getFloat();
        @CField("d")    double  getDouble();
        @CField("l")    JObject getJObject();

        @CField("z")    void setBoolean(boolean b);
        @CField("b")    void setByte(byte b);
        @CField("c")    void setChar(char ch);
        @CField("s")    void setShort(short s);
        @CField("i")    void setInt(int i);
        @CField("j")    void setLong(long l);
        @CField("f")    void setFloat(float f);
        @CField("d")    void setDouble(double d);
        @CField("l")    void setJObject(JObject obj);
        // @formatter:on

        /**
         * Gets JValue in an array of JValues pointed to by this object.
         */
        JValue addressOf(int index);
    }

    @CContext(JNIHeaderDirectives.class)
    @CStruct(value = "JNIEnv_", addStructKeyword = true)
    public interface JNIEnv extends PointerBase {
        @CField("functions")
        JNINativeInterface getFunctions();
    }

    @CPointerTo(JNIEnv.class)
    public interface JNIEnvPointer extends PointerBase {
        JNIEnv readJNIEnv();

        void writeJNIEnv(JNIEnv env);
    }

    @CContext(JNIHeaderDirectives.class)
    @CStruct(value = "JNINativeInterface_", addStructKeyword = true)
    public interface JNINativeInterface extends PointerBase {

        @CField("NewString")
        NewString getNewString();

        @CField("GetStringLength")
        GetStringLength getGetStringLength();

        @CField("GetStringChars")
        GetStringChars getGetStringChars();

        @CField("ReleaseStringChars")
        ReleaseStringChars getReleaseStringChars();

        @CField("NewStringUTF")
        NewStringUTF8 getNewStringUTF();

        @CField("GetStringUTFLength")
        GetStringUTFLength getGetStringUTFLength();

        @CField("GetStringUTFChars")
        GetStringUTFChars getGetStringUTFChars();

        @CField("ReleaseStringUTFChars")
        ReleaseStringUTFChars getReleaseStringUTFChars();

        @CField("GetArrayLength")
        GetArrayLength getGetArrayLength();

        @CField("NewObjectArray")
        NewObjectArray getNewObjectArray();

        @CField("NewBooleanArray")
        NewBooleanArray getNewBooleanArray();

        @CField("NewByteArray")
        NewByteArray getNewByteArray();

        @CField("NewCharArray")
        NewCharArray getNewCharArray();

        @CField("NewShortArray")
        NewShortArray getNewShortArray();

        @CField("NewIntArray")
        NewIntArray getNewIntArray();

        @CField("NewLongArray")
        NewLongArray getNewLongArray();

        @CField("NewFloatArray")
        NewFloatArray getNewFloatArray();

        @CField("NewDoubleArray")
        NewDoubleArray getNewDoubleArray();

        @CField("GetObjectArrayElement")
        GetObjectArrayElement getGetObjectArrayElement();

        @CField("SetObjectArrayElement")
        SetObjectArrayElement getSetObjectArrayElement();

        @CField("GetBooleanArrayElements")
        GetBooleanArrayElements getGetBooleanArrayElements();

        @CField("GetByteArrayElements")
        GetByteArrayElements getGetByteArrayElements();

        @CField("GetCharArrayElements")
        GetCharArrayElements getGetCharArrayElements();

        @CField("GetShortArrayElements")
        GetShortArrayElements getGetShortArrayElements();

        @CField("GetIntArrayElements")
        GetIntArrayElements getGetIntArrayElements();

        @CField("GetLongArrayElements")
        GetLongArrayElements getGetLongArrayElements();

        @CField("GetFloatArrayElements")
        GetFloatArrayElements getGetFloatArrayElements();

        @CField("GetDoubleArrayElements")
        GetDoubleArrayElements getGetDoubleArrayElements();

        @CField("ReleaseBooleanArrayElements")
        ReleaseBooleanArrayElements getReleaseBooleanArrayElements();

        @CField("ReleaseByteArrayElements")
        ReleaseByteArrayElements getReleaseByteArrayElements();

        @CField("ReleaseCharArrayElements")
        ReleaseCharArrayElements getReleaseCharArrayElements();

        @CField("ReleaseShortArrayElements")
        ReleaseShortArrayElements getReleaseShortArrayElements();

        @CField("ReleaseIntArrayElements")
        ReleaseIntArrayElements getReleaseIntArrayElements();

        @CField("ReleaseLongArrayElements")
        ReleaseLongArrayElements getReleaseLongArrayElements();

        @CField("ReleaseFloatArrayElements")
        ReleaseFloatArrayElements getReleaseFloatArrayElements();

        @CField("ReleaseDoubleArrayElements")
        ReleaseDoubleArrayElements getReleaseDoubleArrayElements();

        @CField("GetBooleanArrayRegion")
        GetBooleanArrayRegion getGetBooleanArrayRegion();

        @CField("GetByteArrayRegion")
        GetByteArrayRegion getGetByteArrayRegion();

        @CField("GetCharArrayRegion")
        GetCharArrayRegion getGetCharArrayRegion();

        @CField("GetShortArrayRegion")
        GetShortArrayRegion getGetShortArrayRegion();

        @CField("GetIntArrayRegion")
        GetIntArrayRegion getGetIntArrayRegion();

        @CField("GetLongArrayRegion")
        GetLongArrayRegion getGetLongArrayRegion();

        @CField("GetFloatArrayRegion")
        GetFloatArrayRegion getGetFloatArrayRegion();

        @CField("GetDoubleArrayRegion")
        GetDoubleArrayRegion getGetDoubleArrayRegion();

        @CField("SetBooleanArrayRegion")
        SetBooleanArrayRegion getSetBooleanArrayRegion();

        @CField("SetByteArrayRegion")
        SetByteArrayRegion getSetByteArrayRegion();

        @CField("SetCharArrayRegion")
        SetCharArrayRegion getSetCharArrayRegion();

        @CField("SetShortArrayRegion")
        SetShortArrayRegion getSetShortArrayRegion();

        @CField("SetIntArrayRegion")
        SetIntArrayRegion getSetIntArrayRegion();

        @CField("SetLongArrayRegion")
        SetLongArrayRegion getSetLongArrayRegion();

        @CField("SetFloatArrayRegion")
        SetFloatArrayRegion getSetFloatArrayRegion();

        @CField("SetDoubleArrayRegion")
        SetDoubleArrayRegion getSetDoubleArrayRegion();

        @CField("FindClass")
        FindClass getFindClass();

        @CField("DefineClass")
        DefineClass getDefineClass();

        @CField("IsSameObject")
        IsSameObject getIsSameObject();

        @CField("GetObjectClass")
        GetObjectClass getGetObjectClass();

        @CField("NewGlobalRef")
        NewGlobalRef getNewGlobalRef();

        @CField("DeleteGlobalRef")
        DeleteGlobalRef getDeleteGlobalRef();

        @CField("NewWeakGlobalRef")
        NewWeakGlobalRef getNewWeakGlobalRef();

        @CField("DeleteWeakGlobalRef")
        DeleteWeakGlobalRef getDeleteWeakGlobalRef();

        @CField("DeleteLocalRef")
        DeleteLocalRef getDeleteLocalRef();

        @CField("PushLocalFrame")
        PushLocalFrame getPushLocalFrame();

        @CField("PopLocalFrame")
        PopLocalFrame getPopLocalFrame();

        @CField("NewObjectA")
        NewObjectA getNewObjectA();

        @CField("GetStaticMethodID")
        GetStaticMethodID getGetStaticMethodID();

        @CField("GetMethodID")
        GetMethodID getGetMethodID();

        @CField("GetStaticFieldID")
        GetStaticFieldID getGetStaticFieldID();

        @CField("GetFieldID")
        GetFieldID getGetFieldID();

        @CField("CallStaticBooleanMethodA")
        CallStaticBooleanMethodA getCallStaticBooleanMethodA();

        @CField("CallStaticIntMethodA")
        CallStaticIntMethodA getCallStaticIntMethodA();

        @CField("CallStaticVoidMethodA")
        CallStaticVoidMethodA getCallStaticVoidMethodA();

        @CField("CallStaticObjectMethodA")
        CallStaticObjectMethodA getCallStaticObjectMethodA();

        @CField("CallStaticLongMethodA")
        CallStaticLongMethodA getCallStaticLongMethodA();

        @CField("CallObjectMethodA")
        CallObjectMethodA getCallObjectMethodA();

        @CField("CallVoidMethodA")
        CallVoidMethodA getCallVoidMethodA();

        @CField("CallBooleanMethodA")
        CallBooleanMethodA getCallBooleanMethodA();

        @CField("CallShortMethodA")
        CallShortMethodA getCallShortMethodA();

        @CField("CallIntMethodA")
        CallIntMethodA getCallIntMethodA();

        @CField("CallLongMethodA")
        CallLongMethodA getCallLongMethodA();

        @CField("CallDoubleMethodA")
        CallDoubleMethodA getCallDoubleMethodA();

        @CField("CallFloatMethodA")
        CallFloatMethodA getCallFloatMethodA();

        @CField("CallByteMethodA")
        CallByteMethodA getCallByteMethodA();

        @CField("CallCharMethodA")
        CallCharMethodA getCallCharMethodA();

        @CField("GetStaticObjectField")
        GetStaticObjectField getGetStaticObjectField();

        @CField("GetIntField")
        GetIntField getGetIntField();

        @CField("GetStaticBooleanField")
        GetStaticBooleanField getGetStaticBooleanField();

        @CField("SetStaticBooleanField")
        SetStaticBooleanField getSetStaticBooleanField();

        @CField("ExceptionCheck")
        ExceptionCheck getExceptionCheck();

        @CField("ExceptionOccurred")
        ExceptionOccurred getExceptionOccurred();

        @CField("ExceptionClear")
        ExceptionClear getExceptionClear();

        @CField("ExceptionDescribe")
        ExceptionDescribe getExceptionDescribe();

        @CField("Throw")
        Throw getThrow();

        @CField("GetObjectRefType")
        GetObjectRefType getGetObjectRefType();

        @CField("GetDirectBufferAddress")
        GetDirectBufferAddress getGetDirectBufferAddress();

        @CField("IsInstanceOf")
        IsInstanceOf getIsInstanceOf();

        @CField("GetJavaVM")
        GetJavaVM getGetJavaVM();
    }

    @CContext(JNIHeaderDirectives.class)
    @CStruct(value = "JavaVM_", addStructKeyword = true)
    public interface JavaVM extends PointerBase {
        @CField("functions")
        JNIInvokeInterface getFunctions();
    }

    @CPointerTo(JavaVM.class)
    public interface JavaVMPointer extends PointerBase {
        JavaVM readJavaVM();

        void writeJavaVM(JavaVM javaVM);
    }

    @CContext(JNIHeaderDirectives.class)
    @CStruct(value = "JavaVMAttachArgs", addStructKeyword = true)
    public interface JavaVMAttachArgs extends PointerBase {
        @CField("version")
        int getVersion();

        @CField("version")
        void setVersion(int version);

        @CField("name")
        CCharPointer getName();

        @CField("name")
        void setName(CCharPointer name);

        @CField("group")
        JObject getGroup();

        @CField("group")
        void setGroup(JObject group);
    }

    @CContext(JNIHeaderDirectives.class)
    @CStruct(value = "JNIInvokeInterface_", addStructKeyword = true)
    public interface JNIInvokeInterface extends PointerBase {
        @CField("AttachCurrentThread")
        AttachCurrentThread getAttachCurrentThread();

        @CField("AttachCurrentThreadAsDaemon")
        AttachCurrentThreadAsDaemon getAttachCurrentThreadAsDaemon();

        @CField("DetachCurrentThread")
        DetachCurrentThread getDetachCurrentThread();

        @CField("GetEnv")
        GetEnv getGetEnv();
    }

    public interface CallStaticIntMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    public interface CallStaticBooleanMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    public interface CallStaticVoidMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    public interface CallStaticObjectMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    public interface CallStaticLongMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        long call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    public interface CallObjectMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JObject object, JMethodID methodID, JValue args);
    }

    public interface CallVoidMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface CallBooleanMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface CallShortMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        short call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface CallIntMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface CallLongMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        long call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface CallDoubleMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        double call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface CallFloatMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        float call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface CallByteMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        byte call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface CallCharMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        char call(JNIEnv env, JObject o, JMethodID methodID, JValue args);
    }

    public interface DeleteGlobalRef extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JObject gref);
    }

    public interface DeleteWeakGlobalRef extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JWeak wref);
    }

    public interface DeleteLocalRef extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JObject lref);
    }

    public interface PushLocalFrame extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, int capacity);
    }

    public interface PopLocalFrame extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JObject result);
    }

    public interface ExceptionCheck extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env);

        @InvokeCFunctionPointer(transition = Transition.NO_TRANSITION)
        boolean callNoTransition(JNIEnv env);
    }

    public interface ExceptionClear extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env);
    }

    public interface ExceptionDescribe extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env);

        @InvokeCFunctionPointer(transition = Transition.NO_TRANSITION)
        void callNoTransition(JNIEnv env);
    }

    public interface ExceptionOccurred extends CFunctionPointer {
        @InvokeCFunctionPointer
        JThrowable call(JNIEnv env);
    }

    public interface FindClass extends CFunctionPointer {
        @InvokeCFunctionPointer
        JClass call(JNIEnv env, CCharPointer name);

        @InvokeCFunctionPointer(transition = Transition.NO_TRANSITION)
        JClass callNoTransition(JNIEnv env, CCharPointer name);
    }

    public interface DefineClass extends CFunctionPointer {
        @InvokeCFunctionPointer
        JClass call(JNIEnv env, CCharPointer name, JObject loader, CCharPointer buf, long bufLen);
    }

    public interface GetArrayLength extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JArray array);
    }

    public interface GetBooleanArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CCharPointer call(JNIEnv env, JBooleanArray array, JValue isCopy);
    }

    public interface GetByteArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CCharPointer call(JNIEnv env, JByteArray array, JValue isCopy);
    }

    public interface GetCharArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CShortPointer call(JNIEnv env, JCharArray array, JValue isCopy);
    }

    public interface GetShortArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CShortPointer call(JNIEnv env, JShortArray array, JValue isCopy);
    }

    public interface GetIntArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CIntPointer call(JNIEnv env, JIntArray array, JValue isCopy);
    }

    public interface GetLongArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CLongPointer call(JNIEnv env, JLongArray array, JValue isCopy);
    }

    public interface GetFloatArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CFloatPointer call(JNIEnv env, JFloatArray array, JValue isCopy);
    }

    public interface GetDoubleArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CDoublePointer call(JNIEnv env, JDoubleArray array, JValue isCopy);
    }

    public interface GetMethodID extends CFunctionPointer {
        @InvokeCFunctionPointer
        JMethodID call(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig);

        @InvokeCFunctionPointer(transition = Transition.NO_TRANSITION)
        JMethodID callNoTransition(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig);
    }

    public interface GetObjectArrayElement extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JObjectArray array, int index);
    }

    public interface GetObjectClass extends CFunctionPointer {
        @InvokeCFunctionPointer
        JClass call(JNIEnv env, JObject object);
    }

    public interface GetObjectRefType extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JObject obj);
    }

    public interface GetStaticMethodID extends CFunctionPointer {
        @InvokeCFunctionPointer
        JMethodID call(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig);
    }

    public interface GetStringChars extends CFunctionPointer {
        @InvokeCFunctionPointer
        CShortPointer call(JNIEnv env, JString string, JValue isCopy);
    }

    public interface GetStringLength extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JString string);
    }

    public interface GetStringUTFChars extends CFunctionPointer {
        @InvokeCFunctionPointer
        CCharPointer call(JNIEnv env, JString string, JValue isCopy);
    }

    public interface GetStringUTFLength extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JString str);
    }

    public interface IsSameObject extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env, JObject ref1, JObject ref2);
    }

    public interface NewBooleanArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JBooleanArray call(JNIEnv env, int len);
    }

    public interface NewByteArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JByteArray call(JNIEnv env, int len);
    }

    public interface NewCharArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JCharArray call(JNIEnv env, int len);
    }

    public interface NewShortArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JShortArray call(JNIEnv env, int len);
    }

    public interface NewIntArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JIntArray call(JNIEnv env, int len);
    }

    public interface NewLongArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JLongArray call(JNIEnv env, int len);
    }

    public interface NewFloatArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JFloatArray call(JNIEnv env, int len);
    }

    public interface NewDoubleArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JDoubleArray call(JNIEnv env, int len);
    }

    public interface NewGlobalRef extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JObject lobj);
    }

    public interface NewWeakGlobalRef extends CFunctionPointer {
        @InvokeCFunctionPointer
        JWeak call(JNIEnv env, JObject lobj);
    }

    public interface NewObjectA extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);

        @InvokeCFunctionPointer(transition = Transition.NO_TRANSITION)
        JObject callNoTransition(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    public interface NewObjectArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObjectArray call(JNIEnv env, int len, JClass clazz, JObject init);
    }

    public interface NewString extends CFunctionPointer {
        @InvokeCFunctionPointer
        JString call(JNIEnv env, CShortPointer unicode, int len);
    }

    public interface NewStringUTF8 extends CFunctionPointer {
        @InvokeCFunctionPointer
        JString call(JNIEnv env, CCharPointer bytes);

        @InvokeCFunctionPointer(transition = Transition.NO_TRANSITION)
        JString callNoTransition(JNIEnv env, CCharPointer bytes);
    }

    public interface ReleaseBooleanArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JBooleanArray array, CCharPointer elems, int mode);
    }

    public interface ReleaseByteArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JByteArray array, CCharPointer elems, int mode);
    }

    public interface ReleaseCharArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JCharArray array, CShortPointer elems, int mode);
    }

    public interface ReleaseShortArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JShortArray array, CShortPointer elems, int mode);
    }

    public interface ReleaseIntArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JIntArray array, CIntPointer elems, int mode);
    }

    public interface ReleaseLongArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JLongArray array, CLongPointer elems, int mode);
    }

    public interface ReleaseFloatArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JFloatArray array, CFloatPointer elems, int mode);
    }

    public interface ReleaseDoubleArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JDoubleArray array, CDoublePointer elems, int mode);
    }

    public interface GetBooleanArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JBooleanArray array, int start, int len, CCharPointer buf);
    }

    public interface GetByteArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JByteArray array, int start, int len, CCharPointer buf);
    }

    public interface GetCharArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JCharArray array, int start, int len, CShortPointer buf);
    }

    public interface GetShortArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JShortArray array, int start, int len, CShortPointer buf);
    }

    public interface GetIntArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JIntArray array, int start, int len, CIntPointer buf);
    }

    public interface GetLongArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JLongArray array, int start, int len, CLongPointer buf);
    }

    public interface GetFloatArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JFloatArray array, int start, int len, CFloatPointer buf);
    }

    public interface GetDoubleArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JDoubleArray array, int start, int len, CDoublePointer buf);
    }

    public interface SetBooleanArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JBooleanArray array, int start, int len, CCharPointer buf);
    }

    public interface SetByteArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JByteArray array, int start, int len, CCharPointer buf);
    }

    public interface SetCharArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JCharArray array, int start, int len, CShortPointer buf);
    }

    public interface SetShortArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JShortArray array, int start, int len, CShortPointer buf);
    }

    public interface SetIntArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JIntArray array, int start, int len, CIntPointer buf);
    }

    public interface SetLongArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JLongArray array, int start, int len, CLongPointer buf);
    }

    public interface SetFloatArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JFloatArray array, int start, int len, CFloatPointer buf);
    }

    public interface SetDoubleArrayRegion extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JDoubleArray array, int start, int len, CDoublePointer buf);
    }

    public interface ReleaseStringChars extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JString string, CShortPointer chars);
    }

    public interface ReleaseStringUTFChars extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JString string, CCharPointer chars);
    }

    public interface SetObjectArrayElement extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JObjectArray array, int index, JObject val);
    }

    public interface Throw extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JThrowable throwable);

        @InvokeCFunctionPointer(transition = Transition.NO_TRANSITION)
        int callNoTransition(JNIEnv env, JThrowable throwable);
    }

    public interface GetDirectBufferAddress extends CFunctionPointer {
        @InvokeCFunctionPointer
        VoidPointer call(JNIEnv env, JObject buf);
    }

    public interface IsInstanceOf extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env, JObject o, JClass c);
    }

    public interface GetStaticFieldID extends CFunctionPointer {
        @InvokeCFunctionPointer
        JFieldID call(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig);
    }

    public interface GetFieldID extends CFunctionPointer {
        @InvokeCFunctionPointer
        JFieldID call(JNIEnv env, JClass c, CCharPointer name, CCharPointer sig);
    }

    public interface GetStaticObjectField extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JClass clazz, JFieldID fieldID);
    }

    public interface GetIntField extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JObject o, JFieldID fieldId);
    }

    public interface GetStaticBooleanField extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env, JClass clazz, JFieldID fieldID);
    }

    public interface SetStaticBooleanField extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JClass clazz, JFieldID fieldID, boolean value);
    }

    public interface GetJavaVM extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JavaVMPointer javaVMOut);
    }

    public interface AttachCurrentThread extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JavaVM vm, JNIEnvPointer envOut, JavaVMAttachArgs args);
    }

    public interface AttachCurrentThreadAsDaemon extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JavaVM vm, JNIEnvPointer envOut, JavaVMAttachArgs args);
    }

    public interface DetachCurrentThread extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JavaVM vm);
    }

    public interface GetEnv extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JavaVM vm, JNIEnvPointer envOut, int version);
    }

    static class JNIHeaderDirectives implements CContext.Directives {
        private static final String[] INCLUDES = {"jni.h", "jni_md.h"};

        @Override
        public boolean isInConfiguration() {
            return ImageSingletons.contains(NativeBridgeSupport.class);
        }

        @Override
        public List<String> getOptions() {
            return Arrays.stream(findJNIHeaders()).map((p) -> "-I" + p.getParent()).collect(Collectors.toList());
        }

        @Override
        public List<String> getHeaderFiles() {
            return Arrays.stream(findJNIHeaders()).map((p) -> '<' + p.toString() + '>').collect(Collectors.toList());
        }

        private static Path[] findJNIHeaders() {
            Path javaHome = Paths.get(Services.getSavedProperties().get("java.home"));
            Path includeFolder = javaHome.resolve("include");
            if (!Files.exists(includeFolder)) {
                Path parent = javaHome.getParent();
                if (parent != null) {
                    javaHome = parent;
                }
            }
            includeFolder = javaHome.resolve("include");
            if (!Files.exists(includeFolder)) {
                throw new IllegalStateException("Cannot find 'include' folder in JDK.");
            }
            Path[] res = new Path[INCLUDES.length];
            try {
                for (int i = 0; i < INCLUDES.length; i++) {
                    String include = INCLUDES[i];
                    Optional<Path> includeFile = Files.find(includeFolder, 2, (p, attrs) -> include.equals(p.getFileName().toString())).findFirst();
                    if (!includeFile.isPresent()) {
                        throw new IllegalStateException("Include: " + res[i] + " does not exist.");
                    }
                    res[i] = includeFile.get();
                }
                return res;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }
}
