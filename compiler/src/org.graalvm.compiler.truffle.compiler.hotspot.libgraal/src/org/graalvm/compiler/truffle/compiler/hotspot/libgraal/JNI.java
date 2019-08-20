/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;

import jdk.vm.ci.services.Services;

final class JNI {

    private JNI() {
        throw new IllegalStateException("No instance allowed");
    }

    interface JMethodID extends PointerBase {
    }

    interface JObject extends PointerBase {
    }

    interface JArray extends JObject {
        int MODE_WRITE_RELEASE = 0;
        int MODE_WRITE = 1;
        int MODE_RELEASE = 2;
    }

    interface JByteArray extends JArray {
    }

    interface JLongArray extends JArray {
    }

    interface JObjectArray extends JArray {
    }

    interface JClass extends JObject {
    }

    interface JString extends JObject {
    }

    interface JThrowable extends JObject {
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

    @CContext(JNIHeaderDirectives.class)
    @CStruct(value = "JNINativeInterface_", addStructKeyword = true)
    interface JNINativeInterface extends PointerBase {

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

        @CField("NewByteArray")
        NewByteArray getNewByteArray();

        @CField("GetObjectArrayElement")
        GetObjectArrayElement getGetObjectArrayElement();

        @CField("SetObjectArrayElement")
        SetObjectArrayElement getSetObjectArrayElement();

        @CField("GetByteArrayElements")
        GetByteArrayElements getGetByteArrayElements();

        @CField("GetLongArrayElements")
        GetLongArrayElements getGetLongArrayElements();

        @CField("ReleaseByteArrayElements")
        ReleaseByteArrayElements getReleaseByteArrayElements();

        @CField("ReleaseLongArrayElements")
        ReleaseLongArrayElements getReleaseLongArrayElements();

        @CField("FindClass")
        FindClass getFindClass();

        @CField("IsSameObject")
        IsSameObject getIsSameObject();

        @CField("GetObjectClass")
        GetObjectClass getGetObjectClass();

        @CField("NewGlobalRef")
        NewGlobalRef getNewGlobalRef();

        @CField("DeleteGlobalRef")
        DeleteGlobalRef getDeleteGlobalRef();

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
    }

    interface CallStaticIntMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    interface CallStaticBooleanMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    interface CallStaticVoidMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    interface CallStaticObjectMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    interface CallStaticLongMethodA extends CFunctionPointer {
        @InvokeCFunctionPointer
        long call(JNIEnv env, JClass clazz, JMethodID methodID, JValue args);
    }

    interface DeleteGlobalRef extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JObject gref);
    }

    interface DeleteLocalRef extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JObject lref);
    }

    interface PushLocalFrame extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, int capacity);
    }

    interface PopLocalFrame extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JObject result);
    }

    interface ExceptionCheck extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env);
    }

    interface ExceptionClear extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env);
    }

    interface ExceptionDescribe extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env);
    }

    interface ExceptionOccurred extends CFunctionPointer {
        @InvokeCFunctionPointer
        JThrowable call(JNIEnv env);
    }

    interface FindClass extends CFunctionPointer {
        @InvokeCFunctionPointer
        JClass call(JNIEnv env, CCharPointer name);
    }

    interface GetArrayLength extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JArray array);
    }

    interface GetByteArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CCharPointer call(JNIEnv env, JByteArray array, JValue isCopy);
    }

    interface GetLongArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        CLongPointer call(JNIEnv env, JLongArray array, JValue isCopy);
    }

    interface GetMethodID extends CFunctionPointer {
        @InvokeCFunctionPointer
        JMethodID call(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig);
    }

    interface GetObjectArrayElement extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JObjectArray array, int index);
    }

    interface GetObjectClass extends CFunctionPointer {
        @InvokeCFunctionPointer
        JClass call(JNIEnv env, JObject object);
    }

    interface GetObjectRefType extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JObject obj);
    }

    interface GetStaticMethodID extends CFunctionPointer {
        @InvokeCFunctionPointer
        JMethodID call(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig);
    }

    interface GetStringChars extends CFunctionPointer {
        @InvokeCFunctionPointer
        CShortPointer call(JNIEnv env, JString string, JValue isCopy);
    }

    interface GetStringLength extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JString string);
    }

    interface GetStringUTFChars extends CFunctionPointer {
        @InvokeCFunctionPointer
        CCharPointer call(JNIEnv env, JString string, JValue isCopy);
    }

    interface GetStringUTFLength extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JString str);
    }

    interface IsSameObject extends CFunctionPointer {
        @InvokeCFunctionPointer
        boolean call(JNIEnv env, JObject ref1, JObject ref2);
    }

    interface NewByteArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JByteArray call(JNIEnv env, int len);
    }

    interface NewGlobalRef extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JNIEnv env, JObject lobj);
    }

    interface NewObjectA extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObject call(JClass clazz, JMethodID methodID, JValue args);
    }

    interface NewObjectArray extends CFunctionPointer {
        @InvokeCFunctionPointer
        JObjectArray call(JNIEnv env, int len, JClass clazz, JObject init);
    }

    interface NewString extends CFunctionPointer {
        @InvokeCFunctionPointer
        JString call(JNIEnv env, CShortPointer unicode, int len);
    }

    interface NewStringUTF8 extends CFunctionPointer {
        @InvokeCFunctionPointer
        JString call(JNIEnv env, CCharPointer bytes);
    }

    interface ReleaseByteArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JByteArray array, CCharPointer elems, int mode);
    }

    interface ReleaseLongArrayElements extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JLongArray array, CLongPointer elems, int mode);
    }

    interface ReleaseStringChars extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JString string, CShortPointer chars);
    }

    interface ReleaseStringUTFChars extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JString string, CCharPointer chars);
    }

    interface SetObjectArrayElement extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnv env, JObjectArray array, int index, JObject val);
    }

    interface Throw extends CFunctionPointer {
        @InvokeCFunctionPointer
        int call(JNIEnv env, JThrowable throwable);
    }

    interface GetDirectBufferAddress extends CFunctionPointer {
        @InvokeCFunctionPointer
        VoidPointer call(JNIEnv env, JObject buf);
    }

    static class JNIHeaderDirectives implements CContext.Directives {
        private static final String[] INCLUDES = {"jni.h", "jni_md.h"};

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
