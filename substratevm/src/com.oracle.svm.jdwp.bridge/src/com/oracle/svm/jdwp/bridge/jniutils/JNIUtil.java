/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge.jniutils;

import static com.oracle.svm.jdwp.bridge.jniutils.JNI.JNI_OK;
import static com.oracle.svm.jdwp.bridge.jniutils.JNI.JNI_VERSION_10;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.VoidPointer;

import com.oracle.svm.jdwp.bridge.jniutils.JNI.JArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JBooleanArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JByteArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JCharArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JClass;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JDoubleArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JFieldID;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JFloatArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JIntArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JLongArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JMethodID;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JNIEnv;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JNIEnvPointer;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JObject;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JObjectArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JShortArray;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JString;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JThrowable;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JValue;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JWeak;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JavaVM;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JavaVMAttachArgs;
import com.oracle.svm.jdwp.bridge.jniutils.JNI.JavaVMPointer;

import jdk.graal.compiler.word.Word;

/**
 * Helpers for calling JNI functions.
 */

public final class JNIUtil {

    private static final String[] METHOD_GET_PLATFORM_CLASS_LOADER = {
                    "getPlatformClassLoader",
                    "()Ljava/lang/ClassLoader;"
    };
    private static final String[] METHOD_GET_SYSTEM_CLASS_LOADER = {
                    "getSystemClassLoader",
                    "()Ljava/lang/ClassLoader;"
    };
    private static final String[] METHOD_LOAD_CLASS = {
                    "loadClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;"
    };

    private static final int ARRAY_COPY_STATIC_BUFFER_SIZE = 8192;

    // Checkstyle: stop
    public static boolean IsSameObject(JNIEnv env, JObject ref1, JObject ref2) {
        traceJNI("IsSameObject");
        return env.getFunctions().getIsSameObject().call(env, ref1, ref2);
    }

    public static void DeleteLocalRef(JNIEnv env, JObject ref) {
        traceJNI("DeleteLocalRef");
        env.getFunctions().getDeleteLocalRef().call(env, ref);
    }

    public static int PushLocalFrame(JNIEnv env, int capacity) {
        traceJNI("PushLocalFrame");
        return env.getFunctions().getPushLocalFrame().call(env, capacity);
    }

    public static JObject PopLocalFrame(JNIEnv env, JObject result) {
        traceJNI("PopLocalFrame");
        return env.getFunctions().getPopLocalFrame().call(env, result);
    }

    public static JClass DefineClass(JNIEnv env, CCharPointer name, JObject loader, CCharPointer buf, int bufLen) {
        return env.getFunctions().getDefineClass().call(env, name, loader, buf, bufLen);
    }

    public static JClass FindClass(JNIEnv env, CCharPointer name) {
        traceJNI("FindClass");
        return env.getFunctions().getFindClass().call(env, name);
    }

    public static JClass GetObjectClass(JNIEnv env, JObject object) {
        traceJNI("GetObjectClass");
        return env.getFunctions().getGetObjectClass().call(env, object);
    }

    public static JMethodID GetStaticMethodID(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig) {
        traceJNI("GetStaticMethodID");
        return env.getFunctions().getGetStaticMethodID().call(env, clazz, name, sig);
    }

    public static JMethodID GetMethodID(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig) {
        traceJNI("GetMethodID");
        return env.getFunctions().getGetMethodID().call(env, clazz, name, sig);
    }

    public static JFieldID GetStaticFieldID(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig) {
        traceJNI("GetStaticFieldID");
        return env.getFunctions().getGetStaticFieldID().call(env, clazz, name, sig);
    }

    public static JFieldID GetFieldID(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer signature) {
        traceJNI("GetFieldID");
        return env.getFunctions().getGetFieldID().call(env, clazz, name, signature);
    }

    public static JObject GetStaticObjectField(JNIEnv env, JClass clazz, JFieldID fieldID) {
        traceJNI("GetFieldID");
        return env.getFunctions().getGetStaticObjectField().call(env, clazz, fieldID);
    }

    public static int GetIntField(JNIEnv env, JObject object, JFieldID fieldID) {
        traceJNI("GetIntField");
        return env.getFunctions().getGetIntField().call(env, object, fieldID);
    }

    public static JObjectArray NewObjectArray(JNIEnv env, int len, JClass componentClass, JObject initialElement) {
        traceJNI("NewObjectArray");
        return env.getFunctions().getNewObjectArray().call(env, len, componentClass, initialElement);
    }

    public static JBooleanArray NewBooleanArray(JNIEnv env, int len) {
        traceJNI("NewBooleanArray");
        return env.getFunctions().getNewBooleanArray().call(env, len);
    }

    public static JByteArray NewByteArray(JNIEnv env, int len) {
        traceJNI("NewByteArray");
        return env.getFunctions().getNewByteArray().call(env, len);
    }

    public static JCharArray NewCharArray(JNIEnv env, int len) {
        traceJNI("NewCharArray");
        return env.getFunctions().getNewCharArray().call(env, len);
    }

    public static JShortArray NewShortArray(JNIEnv env, int len) {
        traceJNI("NewShortArray");
        return env.getFunctions().getNewShortArray().call(env, len);
    }

    public static JIntArray NewIntArray(JNIEnv env, int len) {
        traceJNI("NewIntArray");
        return env.getFunctions().getNewIntArray().call(env, len);
    }

    public static JLongArray NewLongArray(JNIEnv env, int len) {
        traceJNI("NewLongArray");
        return env.getFunctions().getNewLongArray().call(env, len);
    }

    public static JFloatArray NewFloatArray(JNIEnv env, int len) {
        traceJNI("NewFloatArray");
        return env.getFunctions().getNewFloatArray().call(env, len);
    }

    public static JDoubleArray NewDoubleArray(JNIEnv env, int len) {
        traceJNI("NewDoubleArray");
        return env.getFunctions().getNewDoubleArray().call(env, len);
    }

    public static int GetArrayLength(JNIEnv env, JArray array) {
        traceJNI("GetArrayLength");
        return env.getFunctions().getGetArrayLength().call(env, array);
    }

    public static void SetObjectArrayElement(JNIEnv env, JObjectArray array, int index, JObject value) {
        traceJNI("SetObjectArrayElement");
        env.getFunctions().getSetObjectArrayElement().call(env, array, index, value);
    }

    public static JObject GetObjectArrayElement(JNIEnv env, JObjectArray array, int index) {
        traceJNI("GetObjectArrayElement");
        return env.getFunctions().getGetObjectArrayElement().call(env, array, index);
    }

    public static CCharPointer GetBooleanArrayElements(JNIEnv env, JBooleanArray array, JValue isCopy) {
        traceJNI("GetBooleanArrayElements");
        return env.getFunctions().getGetBooleanArrayElements().call(env, array, isCopy);
    }

    public static CCharPointer GetByteArrayElements(JNIEnv env, JByteArray array, JValue isCopy) {
        traceJNI("GetByteArrayElements");
        return env.getFunctions().getGetByteArrayElements().call(env, array, isCopy);
    }

    public static CShortPointer GetCharArrayElements(JNIEnv env, JCharArray array, JValue isCopy) {
        traceJNI("GetCharArrayElements");
        return env.getFunctions().getGetCharArrayElements().call(env, array, isCopy);
    }

    public static CShortPointer GetShortArrayElements(JNIEnv env, JShortArray array, JValue isCopy) {
        traceJNI("GetShortArrayElements");
        return env.getFunctions().getGetShortArrayElements().call(env, array, isCopy);
    }

    public static CIntPointer GetIntArrayElements(JNIEnv env, JIntArray array, JValue isCopy) {
        traceJNI("GetIntArrayElements");
        return env.getFunctions().getGetIntArrayElements().call(env, array, isCopy);
    }

    public static CLongPointer GetLongArrayElements(JNIEnv env, JLongArray array, JValue isCopy) {
        traceJNI("GetLongArrayElements");
        return env.getFunctions().getGetLongArrayElements().call(env, array, isCopy);
    }

    public static CFloatPointer GetFloatArrayElements(JNIEnv env, JFloatArray array, JValue isCopy) {
        traceJNI("GetFloatArrayElements");
        return env.getFunctions().getGetFloatArrayElements().call(env, array, isCopy);
    }

    public static CDoublePointer GetDoubleArrayElements(JNIEnv env, JDoubleArray array, JValue isCopy) {
        traceJNI("GetFloatArrayElements");
        return env.getFunctions().getGetDoubleArrayElements().call(env, array, isCopy);
    }

    public static void ReleaseBooleanArrayElements(JNIEnv env, JBooleanArray array, CCharPointer elems, int mode) {
        traceJNI("ReleaseBooleanArrayElements");
        env.getFunctions().getReleaseBooleanArrayElements().call(env, array, elems, mode);
    }

    public static void ReleaseByteArrayElements(JNIEnv env, JByteArray array, CCharPointer elems, int mode) {
        traceJNI("ReleaseByteArrayElements");
        env.getFunctions().getReleaseByteArrayElements().call(env, array, elems, mode);
    }

    public static void ReleaseCharArrayElements(JNIEnv env, JCharArray array, CShortPointer elems, int mode) {
        traceJNI("ReleaseCharArrayElements");
        env.getFunctions().getReleaseCharArrayElements().call(env, array, elems, mode);
    }

    public static void ReleaseShortArrayElements(JNIEnv env, JShortArray array, CShortPointer elems, int mode) {
        traceJNI("ReleaseShortArrayElements");
        env.getFunctions().getReleaseShortArrayElements().call(env, array, elems, mode);
    }

    public static void ReleaseIntArrayElements(JNIEnv env, JIntArray array, CIntPointer elems, int mode) {
        traceJNI("ReleaseIntArrayElements");
        env.getFunctions().getReleaseIntArrayElements().call(env, array, elems, mode);
    }

    public static void ReleaseLongArrayElements(JNIEnv env, JLongArray array, CLongPointer elems, int mode) {
        traceJNI("ReleaseLongArrayElements");
        env.getFunctions().getReleaseLongArrayElements().call(env, array, elems, mode);
    }

    public static void ReleaseFloatArrayElements(JNIEnv env, JFloatArray array, CFloatPointer elems, int mode) {
        traceJNI("ReleaseFloatArrayElements");
        env.getFunctions().getReleaseFloatArrayElements().call(env, array, elems, mode);
    }

    public static void ReleaseDoubleArrayElements(JNIEnv env, JDoubleArray array, CDoublePointer elems, int mode) {
        traceJNI("ReleaseDoubleArrayElements");
        env.getFunctions().getReleaseDoubleArrayElements().call(env, array, elems, mode);
    }

    public static void GetBooleanArrayRegion(JNIEnv env, JBooleanArray array, int offset, int len, CCharPointer buff) {
        traceJNI("GetBooleanArrayRegion");
        env.getFunctions().getGetBooleanArrayRegion().call(env, array, offset, len, buff);
    }

    public static void GetByteArrayRegion(JNIEnv env, JByteArray array, int offset, int len, CCharPointer buff) {
        traceJNI("GetByteArrayRegion");
        env.getFunctions().getGetByteArrayRegion().call(env, array, offset, len, buff);
    }

    public static void GetCharArrayRegion(JNIEnv env, JCharArray array, int offset, int len, CShortPointer buff) {
        traceJNI("GetCharArrayRegion");
        env.getFunctions().getGetCharArrayRegion().call(env, array, offset, len, buff);
    }

    public static void GetShortArrayRegion(JNIEnv env, JShortArray array, int offset, int len, CShortPointer buff) {
        traceJNI("GetShortArrayRegion");
        env.getFunctions().getGetShortArrayRegion().call(env, array, offset, len, buff);
    }

    public static void GetIntArrayRegion(JNIEnv env, JIntArray array, int offset, int len, CIntPointer buff) {
        traceJNI("GetIntArrayRegion");
        env.getFunctions().getGetIntArrayRegion().call(env, array, offset, len, buff);
    }

    public static void GetLongArrayRegion(JNIEnv env, JLongArray array, int offset, int len, CLongPointer buff) {
        traceJNI("GetLongArrayRegion");
        env.getFunctions().getGetLongArrayRegion().call(env, array, offset, len, buff);
    }

    public static void GetFloatArrayRegion(JNIEnv env, JFloatArray array, int offset, int len, CFloatPointer buff) {
        traceJNI("GetFloatArrayRegion");
        env.getFunctions().getGetFloatArrayRegion().call(env, array, offset, len, buff);
    }

    public static void GetDoubleArrayRegion(JNIEnv env, JDoubleArray array, int offset, int len, CDoublePointer buff) {
        traceJNI("GetDoubleArrayRegion");
        env.getFunctions().getGetDoubleArrayRegion().call(env, array, offset, len, buff);
    }

    public static void SetBooleanArrayRegion(JNIEnv env, JBooleanArray array, int offset, int len, CCharPointer buff) {
        traceJNI("SetBooleanArrayRegion");
        env.getFunctions().getSetBooleanArrayRegion().call(env, array, offset, len, buff);
    }

    public static void SetByteArrayRegion(JNIEnv env, JByteArray array, int offset, int len, CCharPointer buff) {
        traceJNI("SetByteArrayRegion");
        env.getFunctions().getSetByteArrayRegion().call(env, array, offset, len, buff);
    }

    public static void SetCharArrayRegion(JNIEnv env, JCharArray array, int offset, int len, CShortPointer buff) {
        traceJNI("SetCharArrayRegion");
        env.getFunctions().getSetCharArrayRegion().call(env, array, offset, len, buff);
    }

    public static void SetShortArrayRegion(JNIEnv env, JShortArray array, int offset, int len, CShortPointer buff) {
        traceJNI("SetShortArrayRegion");
        env.getFunctions().getSetShortArrayRegion().call(env, array, offset, len, buff);
    }

    public static void SetIntArrayRegion(JNIEnv env, JIntArray array, int offset, int len, CIntPointer buff) {
        traceJNI("SetIntArrayRegion");
        env.getFunctions().getSetIntArrayRegion().call(env, array, offset, len, buff);
    }

    public static void SetLongArrayRegion(JNIEnv env, JLongArray array, int offset, int len, CLongPointer buff) {
        traceJNI("SetLongArrayRegion");
        env.getFunctions().getSetLongArrayRegion().call(env, array, offset, len, buff);
    }

    public static void SetFloatArrayRegion(JNIEnv env, JFloatArray array, int offset, int len, CFloatPointer buff) {
        traceJNI("SetFloatArrayRegion");
        env.getFunctions().getSetFloatArrayRegion().call(env, array, offset, len, buff);
    }

    public static void SetDoubleArrayRegion(JNIEnv env, JDoubleArray array, int offset, int len, CDoublePointer buff) {
        traceJNI("SetDoubleArrayRegion");
        env.getFunctions().getSetDoubleArrayRegion().call(env, array, offset, len, buff);
    }

    public static JavaVM GetJavaVM(JNIEnv env) {
        traceJNI("GetJavaVM");
        JavaVMPointer javaVMPointer = StackValue.get(JavaVMPointer.class);
        if (env.getFunctions().getGetJavaVM().call(env, javaVMPointer) == JNI_OK) {
            return javaVMPointer.readJavaVM();
        } else {
            return Word.nullPointer();
        }
    }

    public static JNIEnv GetEnv(JavaVM vm) {
        traceJNI("GetEnv");
        JNIEnvPointer envPointer = StackValue.get(JNIEnvPointer.class);
        if (vm.getFunctions().getGetEnv().call(vm, envPointer, JNI_VERSION_10) == JNI_OK) {
            return envPointer.readJNIEnv();
        } else {
            return Word.nullPointer();
        }
    }

    public static JNIEnv AttachCurrentThread(JavaVM vm, JavaVMAttachArgs args) {
        traceJNI("AttachCurrentThread");
        JNIEnvPointer envPointer = StackValue.get(JNIEnvPointer.class);
        if (vm.getFunctions().getAttachCurrentThread().call(vm, envPointer, args) == JNI_OK) {
            return envPointer.readJNIEnv();
        } else {
            return Word.nullPointer();
        }
    }

    public static JNIEnv AttachCurrentThreadAsDaemon(JavaVM vm, JavaVMAttachArgs args) {
        traceJNI("AttachCurrentThreadAsDaemon");
        JNIEnvPointer envPointer = StackValue.get(JNIEnvPointer.class);
        if (vm.getFunctions().getAttachCurrentThreadAsDaemon().call(vm, envPointer, args) == JNI_OK) {
            return envPointer.readJNIEnv();
        } else {
            return Word.nullPointer();
        }
    }

    public static boolean DetachCurrentThread(JavaVM vm) {
        traceJNI("DetachCurrentThread");
        return vm.getFunctions().getDetachCurrentThread().call(vm) == JNI_OK;
    }

    public static void Throw(JNIEnv env, JThrowable throwable) {
        traceJNI("Throw");
        env.getFunctions().getThrow().call(env, throwable);
    }

    public static boolean ExceptionCheck(JNIEnv env) {
        traceJNI("ExceptionCheck");
        return env.getFunctions().getExceptionCheck().call(env);
    }

    public static void ExceptionClear(JNIEnv env) {
        traceJNI("ExceptionClear");
        env.getFunctions().getExceptionClear().call(env);
    }

    public static void ExceptionDescribe(JNIEnv env) {
        traceJNI("ExceptionDescribe");
        env.getFunctions().getExceptionDescribe().call(env);
    }

    public static JThrowable ExceptionOccurred(JNIEnv env) {
        traceJNI("ExceptionOccurred");
        return env.getFunctions().getExceptionOccurred().call(env);
    }

    /**
     * Creates a new global reference.
     *
     * @param env the JNIEnv
     * @param ref JObject to create JNI global reference for
     * @param type type of the object, used only for tracing to distinguish global references
     * @return JNI global reference for given {@link JObject}
     */
    @SuppressWarnings("unchecked")
    public static <T extends JObject> T NewGlobalRef(JNIEnv env, T ref, String type) {
        traceJNI("NewGlobalRef");
        T res = (T) env.getFunctions().getNewGlobalRef().call(env, ref);
        if (tracingAt(3)) {
            trace(3, "New global reference for 0x%x of type %s -> 0x%x", ref.rawValue(), type, res.rawValue());
        }
        return res;
    }

    public static void DeleteGlobalRef(JNIEnv env, JObject ref) {
        traceJNI("DeleteGlobalRef");
        if (tracingAt(3)) {
            trace(3, "Delete global reference 0x%x", ref.rawValue());
        }
        env.getFunctions().getDeleteGlobalRef().call(env, ref);
    }

    /**
     * Creates a new weak global reference.
     *
     * @param env the JNIEnv
     * @param ref JObject to create JNI weak global reference for
     * @param type type of the object, used only for tracing to distinguish global references
     * @return JNI weak global reference for given {@link JObject}
     */
    public static JWeak NewWeakGlobalRef(JNIEnv env, JObject ref, String type) {
        traceJNI("NewWeakGlobalRef");
        JWeak res = env.getFunctions().getNewWeakGlobalRef().call(env, ref);
        if (tracingAt(3)) {
            trace(3, "New weak global reference for 0x%x of type %s -> 0x%x", ref.rawValue(), type, res.rawValue());
        }
        return res;
    }

    public static JObject NewLocalRef(JNIEnv env, JObject ref) {
        traceJNI("NewLocalRef");
        return env.getFunctions().getNewLocalRef().call(env, ref);
    }

    public static void DeleteWeakGlobalRef(JNIEnv env, JWeak ref) {
        traceJNI("DeleteWeakGlobalRef");
        if (tracingAt(3)) {
            trace(3, "Delete weak global reference 0x%x", ref.rawValue());
        }
        env.getFunctions().getDeleteWeakGlobalRef().call(env, ref);
    }

    public static VoidPointer GetDirectBufferAddress(JNIEnv env, JObject buf) {
        traceJNI("GetDirectBufferAddress");
        return env.getFunctions().getGetDirectBufferAddress().call(env, buf);
    }

    public static boolean IsInstanceOf(JNIEnv env, JObject obj, JClass clazz) {
        traceJNI("IsInstanceOf");
        return env.getFunctions().getIsInstanceOf().call(env, obj, clazz);
    }

    // Checkstyle: resume

    private static void traceJNI(String function) {
        trace(2, "%s->JNI: %s", getFeatureName(), function);
    }

    private JNIUtil() {
    }

    /**
     * Decodes a string in the HotSpot heap to a local {@link String}.
     */
    public static String createString(JNIEnv env, JString hsString) {
        if (hsString.isNull()) {
            return null;
        }
        int len = env.getFunctions().getGetStringLength().call(env, hsString);
        CShortPointer unicode = env.getFunctions().getGetStringChars().call(env, hsString, Word.nullPointer());
        try {
            char[] data = new char[len];
            for (int i = 0; i < len; i++) {
                data[i] = (char) unicode.read(i);
            }
            return new String(data);
        } finally {
            env.getFunctions().getReleaseStringChars().call(env, hsString, unicode);
        }
    }

    /**
     * Creates a String in the HotSpot heap from {@code string}.
     */
    public static JString createHSString(JNIEnv env, String string) {
        if (string == null) {
            return Word.nullPointer();
        }
        int len = string.length();
        CShortPointer buffer = UnmanagedMemory.malloc(len << 1);
        try {
            for (int i = 0; i < len; i++) {
                buffer.write(i, (short) string.charAt(i));
            }
            return env.getFunctions().getNewString().call(env, buffer, len);
        } finally {
            UnmanagedMemory.free(buffer);
        }
    }

    public static boolean[] createArray(JNIEnv env, JBooleanArray booleanArray) {
        if (booleanArray.isNull()) {
            return null;
        }
        int len = GetArrayLength(env, booleanArray);
        boolean[] booleans = new boolean[len];
        arrayCopy(env, booleanArray, 0, booleans, 0, len);
        return booleans;
    }

    public static JBooleanArray createHSArray(JNIEnv jniEnv, boolean[] a) {
        if (a == null) {
            return Word.nullPointer();
        }
        JBooleanArray array = NewBooleanArray(jniEnv, a.length);
        arrayCopy(jniEnv, a, 0, array, 0, a.length);
        return array;
    }

    public static byte[] createArray(JNIEnv env, JByteArray byteArray) {
        if (byteArray.isNull()) {
            return null;
        }
        int len = GetArrayLength(env, byteArray);
        byte[] bytes = new byte[len];
        arrayCopy(env, byteArray, 0, bytes, 0, len);
        return bytes;
    }

    public static JByteArray createHSArray(JNIEnv jniEnv, byte[] a) {
        if (a == null) {
            return Word.nullPointer();
        }
        JByteArray array = NewByteArray(jniEnv, a.length);
        arrayCopy(jniEnv, a, 0, array, 0, a.length);
        return array;
    }

    public static char[] createArray(JNIEnv env, JCharArray charArray) {
        if (charArray.isNull()) {
            return null;
        }
        int len = GetArrayLength(env, charArray);
        char[] chars = new char[len];
        arrayCopy(env, charArray, 0, chars, 0, len);
        return chars;
    }

    public static JCharArray createHSArray(JNIEnv jniEnv, char[] a) {
        if (a == null) {
            return Word.nullPointer();
        }
        JCharArray array = NewCharArray(jniEnv, a.length);
        arrayCopy(jniEnv, a, 0, array, 0, a.length);
        return array;
    }

    public static short[] createArray(JNIEnv env, JShortArray shortArray) {
        if (shortArray.isNull()) {
            return null;
        }
        int len = GetArrayLength(env, shortArray);
        short[] shorts = new short[len];
        arrayCopy(env, shortArray, 0, shorts, 0, len);
        return shorts;
    }

    public static JShortArray createHSArray(JNIEnv jniEnv, short[] a) {
        if (a == null) {
            return Word.nullPointer();
        }
        JShortArray array = NewShortArray(jniEnv, a.length);
        arrayCopy(jniEnv, a, 0, array, 0, a.length);
        return array;
    }

    public static int[] createArray(JNIEnv env, JIntArray intArray) {
        if (intArray.isNull()) {
            return null;
        }
        int len = GetArrayLength(env, intArray);
        int[] ints = new int[len];
        arrayCopy(env, intArray, 0, ints, 0, len);
        return ints;
    }

    public static JIntArray createHSArray(JNIEnv jniEnv, int[] a) {
        if (a == null) {
            return Word.nullPointer();
        }
        JIntArray array = NewIntArray(jniEnv, a.length);
        arrayCopy(jniEnv, a, 0, array, 0, a.length);
        return array;
    }

    public static long[] createArray(JNIEnv env, JLongArray longArray) {
        if (longArray.isNull()) {
            return null;
        }
        int len = GetArrayLength(env, longArray);
        long[] longs = new long[len];
        arrayCopy(env, longArray, 0, longs, 0, len);
        return longs;
    }

    public static JLongArray createHSArray(JNIEnv jniEnv, long[] a) {
        if (a == null) {
            return Word.nullPointer();
        }
        JLongArray array = NewLongArray(jniEnv, a.length);
        arrayCopy(jniEnv, a, 0, array, 0, a.length);
        return array;
    }

    public static float[] createArray(JNIEnv env, JFloatArray floatArray) {
        if (floatArray.isNull()) {
            return null;
        }
        int len = GetArrayLength(env, floatArray);
        float[] floats = new float[len];
        arrayCopy(env, floatArray, 0, floats, 0, len);
        return floats;
    }

    public static JFloatArray createHSArray(JNIEnv jniEnv, float[] a) {
        if (a == null) {
            return Word.nullPointer();
        }
        JFloatArray array = NewFloatArray(jniEnv, a.length);
        arrayCopy(jniEnv, a, 0, array, 0, a.length);
        return array;
    }

    public static double[] createArray(JNIEnv env, JDoubleArray doubleArray) {
        if (doubleArray.isNull()) {
            return null;
        }
        int len = GetArrayLength(env, doubleArray);
        double[] doubles = new double[len];
        arrayCopy(env, doubleArray, 0, doubles, 0, len);
        return doubles;
    }

    public static JDoubleArray createHSArray(JNIEnv jniEnv, double[] a) {
        if (a == null) {
            return Word.nullPointer();
        }
        JDoubleArray array = NewDoubleArray(jniEnv, a.length);
        arrayCopy(jniEnv, a, 0, array, 0, a.length);
        return array;
    }

    public static JObjectArray createHSArray(JNIEnv jniEnv, Object[] array, int sourcePosition, int length, String componentTypeBinaryName) {
        JObjectArray hsArray;
        if (array != null) {
            hsArray = JNIUtil.NewObjectArray(jniEnv, length, JNIUtil.findClass(jniEnv, Word.nullPointer(), componentTypeBinaryName, true), Word.nullPointer());
            for (int i = 0; i < length; i++) {
                HSObject element = (HSObject) array[sourcePosition + i];
                JObject hsElement = element != null ? element.getHandle() : Word.nullPointer();
                JNIUtil.SetObjectArrayElement(jniEnv, hsArray, i, hsElement);
            }
        } else {
            hsArray = Word.nullPointer();
        }
        return hsArray;
    }

    public static void arrayCopy(JNIEnv jniEnv, JBooleanArray src, int srcPos, boolean[] dest, int destPos, int length) {
        CCharPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        CCharPointer booleanPointer = length <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(length);
        try {
            GetBooleanArrayRegion(jniEnv, src, srcPos, length, booleanPointer);
            for (int i = 0; i < length; i++) {
                dest[destPos + i] = booleanPointer.addressOf(i).read() != 0;
            }
        } finally {
            if (booleanPointer != staticBuffer) {
                UnmanagedMemory.free(booleanPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, boolean[] src, int srcPos, JBooleanArray dest, int destPos, int length) {
        CCharPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        CCharPointer booleanPointer = length <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(length);
        try {
            for (int i = 0; i < length; i++) {
                booleanPointer.write(i, src[srcPos + i] ? (byte) 1 : 0);
            }
            SetBooleanArrayRegion(jniEnv, dest, destPos, length, booleanPointer);
        } finally {
            if (booleanPointer != staticBuffer) {
                UnmanagedMemory.free(booleanPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, JByteArray src, int srcPos, byte[] dest, int destPos, int length) {
        CCharPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        CCharPointer bytePointer = length <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(length);
        try {
            GetByteArrayRegion(jniEnv, src, srcPos, length, bytePointer);
            CTypeConversion.asByteBuffer(bytePointer, length).get(dest, destPos, length);
        } finally {
            if (bytePointer != staticBuffer) {
                UnmanagedMemory.free(bytePointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, byte[] src, int srcPos, JByteArray dest, int destPos, int length) {
        CCharPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        CCharPointer bytePointer = length <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(length);
        try {
            CTypeConversion.asByteBuffer(bytePointer, length).put(src, srcPos, length);
            SetByteArrayRegion(jniEnv, dest, destPos, length, bytePointer);
        } finally {
            if (bytePointer != staticBuffer) {
                UnmanagedMemory.free(bytePointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, JCharArray src, int srcPos, char[] dest, int destPos, int length) {
        CShortPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Character.BYTES;
        CShortPointer shortPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            GetCharArrayRegion(jniEnv, src, srcPos, length, shortPointer);
            CTypeConversion.asByteBuffer(shortPointer, bytesLength).asCharBuffer().get(dest, destPos, length);
        } finally {
            if (shortPointer != staticBuffer) {
                UnmanagedMemory.free(shortPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, char[] src, int srcPos, JCharArray dest, int destPos, int length) {
        CShortPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Character.BYTES;
        CShortPointer shortPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            CTypeConversion.asByteBuffer(shortPointer, bytesLength).asCharBuffer().put(src, srcPos, length);
            SetCharArrayRegion(jniEnv, dest, destPos, length, shortPointer);
        } finally {
            if (shortPointer != staticBuffer) {
                UnmanagedMemory.free(shortPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, JShortArray src, int srcPos, short[] dest, int destPos, int length) {
        CShortPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Short.BYTES;
        CShortPointer shortPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            GetShortArrayRegion(jniEnv, src, srcPos, length, shortPointer);
            CTypeConversion.asByteBuffer(shortPointer, bytesLength).asShortBuffer().get(dest, destPos, length);
        } finally {
            if (shortPointer != staticBuffer) {
                UnmanagedMemory.free(shortPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, short[] src, int srcPos, JShortArray dest, int destPos, int length) {
        CShortPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Short.BYTES;
        CShortPointer shortPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            CTypeConversion.asByteBuffer(shortPointer, bytesLength).asShortBuffer().put(src, srcPos, length);
            SetShortArrayRegion(jniEnv, dest, destPos, length, shortPointer);
        } finally {
            if (shortPointer != staticBuffer) {
                UnmanagedMemory.free(shortPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, JIntArray src, int srcPos, int[] dest, int destPos, int length) {
        CIntPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Integer.BYTES;
        CIntPointer intPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            GetIntArrayRegion(jniEnv, src, srcPos, length, intPointer);
            CTypeConversion.asByteBuffer(intPointer, bytesLength).asIntBuffer().get(dest, destPos, length);
        } finally {
            if (intPointer != staticBuffer) {
                UnmanagedMemory.free(intPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, int[] src, int srcPos, JIntArray dest, int destPos, int length) {
        CIntPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Integer.BYTES;
        CIntPointer intPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            CTypeConversion.asByteBuffer(intPointer, bytesLength).asIntBuffer().put(src, srcPos, length);
            SetIntArrayRegion(jniEnv, dest, destPos, length, intPointer);
        } finally {
            if (intPointer != staticBuffer) {
                UnmanagedMemory.free(intPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, JLongArray src, int srcPos, long[] dest, int destPos, int length) {
        CLongPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Long.BYTES;
        CLongPointer longPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            GetLongArrayRegion(jniEnv, src, srcPos, length, longPointer);
            CTypeConversion.asByteBuffer(longPointer, bytesLength).asLongBuffer().get(dest, destPos, length);
        } finally {
            if (longPointer != staticBuffer) {
                UnmanagedMemory.free(longPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, long[] src, int srcPos, JLongArray dest, int destPos, int length) {
        CLongPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Long.BYTES;
        CLongPointer longPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            CTypeConversion.asByteBuffer(longPointer, bytesLength).asLongBuffer().put(src, srcPos, length);
            SetLongArrayRegion(jniEnv, dest, destPos, length, longPointer);
        } finally {
            if (longPointer != staticBuffer) {
                UnmanagedMemory.free(longPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, JFloatArray src, int srcPos, float[] dest, int destPos, int length) {
        CFloatPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Float.BYTES;
        CFloatPointer floatPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            GetFloatArrayRegion(jniEnv, src, srcPos, length, floatPointer);
            CTypeConversion.asByteBuffer(floatPointer, bytesLength).asFloatBuffer().get(dest, destPos, length);
        } finally {
            if (floatPointer != staticBuffer) {
                UnmanagedMemory.free(floatPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, float[] src, int srcPos, JFloatArray dest, int destPos, int length) {
        CFloatPointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Float.BYTES;
        CFloatPointer floatPointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            CTypeConversion.asByteBuffer(floatPointer, bytesLength).asFloatBuffer().put(src, srcPos, length);
            SetFloatArrayRegion(jniEnv, dest, destPos, length, floatPointer);
        } finally {
            if (floatPointer != staticBuffer) {
                UnmanagedMemory.free(floatPointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, JDoubleArray src, int srcPos, double[] dest, int destPos, int length) {
        CDoublePointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Double.BYTES;
        CDoublePointer doublePointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            GetDoubleArrayRegion(jniEnv, src, srcPos, length, doublePointer);
            CTypeConversion.asByteBuffer(doublePointer, bytesLength).asDoubleBuffer().get(dest, destPos, length);
        } finally {
            if (doublePointer != staticBuffer) {
                UnmanagedMemory.free(doublePointer);
            }
        }
    }

    public static void arrayCopy(JNIEnv jniEnv, double[] src, int srcPos, JDoubleArray dest, int destPos, int length) {
        CDoublePointer staticBuffer = StackValue.get(ARRAY_COPY_STATIC_BUFFER_SIZE);
        int bytesLength = length * Double.BYTES;
        CDoublePointer doublePointer = bytesLength <= ARRAY_COPY_STATIC_BUFFER_SIZE ? staticBuffer : UnmanagedMemory.malloc(bytesLength);
        try {
            CTypeConversion.asByteBuffer(doublePointer, bytesLength).asDoubleBuffer().put(src, srcPos, length);
            SetDoubleArrayRegion(jniEnv, dest, destPos, length, doublePointer);
        } finally {
            if (doublePointer != staticBuffer) {
                UnmanagedMemory.free(doublePointer);
            }
        }
    }

    /**
     * Converts a fully qualified Java class name from Java source format (e.g.
     * {@code "java.lang.getString"}) to internal format (e.g. {@code "Ljava/lang/getString;"}.
     */
    public static String getInternalName(String fqn) {
        return "L" + getBinaryName(fqn) + ";";
    }

    /**
     * Converts a fully qualified Java class name from Java source format (e.g.
     * {@code "java.lang.getString"}) to binary format (e.g. {@code "java/lang/getString"}.
     */
    public static String getBinaryName(String fqn) {
        return fqn.replace('.', '/');
    }

    /**
     * Creates a JVM method signature as specified in the Sections 4.3.3 of the JVM Specification.
     */
    public static String encodeMethodSignature(Class<?> returnType, Class<?>... parameterTypes) {
        StringBuilder builder = new StringBuilder("(");
        for (Class<?> type : parameterTypes) {
            encodeType(type, builder);
        }
        builder.append(")");
        encodeType(returnType, builder);
        return builder.toString();
    }

    /**
     * Creates a JVM field signature as specified in the Sections 4.3.2 of the JVM Specification.
     */
    public static String encodeFieldSignature(Class<?> type) {
        StringBuilder res = new StringBuilder();
        encodeType(type, res);
        return res.toString();
    }

    private static void encodeType(Class<?> type, StringBuilder buf) {
        String desc;
        if (type == boolean.class) {
            desc = "Z";
        } else if (type == byte.class) {
            desc = "B";
        } else if (type == char.class) {
            desc = "C";
        } else if (type == short.class) {
            desc = "S";
        } else if (type == int.class) {
            desc = "I";
        } else if (type == long.class) {
            desc = "J";
        } else if (type == float.class) {
            desc = "F";
        } else if (type == double.class) {
            desc = "D";
        } else if (type == void.class) {
            desc = "V";
        } else if (type.isArray()) {
            buf.append('[');
            encodeType(type.getComponentType(), buf);
            return;
        } else {
            desc = "L" + type.getName().replace('.', '/') + ";";
        }
        buf.append(desc);
    }

    /**
     * Returns a {@link JClass} for given binary name.
     */
    public static JClass findClass(JNIEnv env, String binaryName) {
        trace(1, "%s->HS: findClass %s", getFeatureName(), binaryName);
        try (CCharPointerHolder name = CTypeConversion.toCString(binaryName)) {
            return JNIUtil.FindClass(env, name.get());
        }
    }

    /**
     * Finds a class in HotSpot heap using a given {@code ClassLoader}.
     *
     * @param env the {@code JNIEnv}
     * @param binaryName the class binary name
     */
    public static JClass findClass(JNIEnv env, JObject classLoader, String binaryName) {
        if (classLoader.isNull()) {
            throw new IllegalArgumentException("ClassLoader must be non null.");
        }
        trace(1, "%s->HS: findClass %s", getFeatureName(), binaryName);
        JMethodID findClassId = findMethod(env, JNIUtil.GetObjectClass(env, classLoader), false, false, METHOD_LOAD_CLASS[0], METHOD_LOAD_CLASS[1]);
        JValue params = StackValue.get(1, JValue.class);
        params.addressOf(0).setJObject(JNIUtil.createHSString(env, binaryName.replace('/', '.')));
        return (JClass) env.getFunctions().getCallObjectMethodA().call(env, classLoader, findClassId, params);
    }

    /**
     * Finds a class in HotSpot heap using JNI.
     *
     * @param env the {@code JNIEnv}
     * @param classLoader the class loader to find class in or {@link Word#nullPointer() NULL
     *            pointer}.
     * @param binaryName the class binary name
     * @param required if {@code true} the {@link JNIExceptionWrapper} is thrown when the class is
     *            not found. If {@code false} the {@code NULL pointer} is returned when the class is
     *            not found.
     */
    public static JClass findClass(JNIEnv env, JObject classLoader, String binaryName, boolean required) {
        Class<? extends Throwable> allowedException = null;
        try {
            if (classLoader.isNonNull()) {
                allowedException = required ? null : ClassNotFoundException.class;
                return findClass(env, classLoader, binaryName);
            } else {
                allowedException = required ? null : NoClassDefFoundError.class;
                return findClass(env, binaryName);
            }
        } finally {
            JNIExceptionWrapper.wrapAndThrowPendingJNIException(env,
                            allowedException == null ? JNIExceptionWrapper.ExceptionHandler.DEFAULT : JNIExceptionWrapper.ExceptionHandler.allowExceptions(allowedException));
        }
    }

    /**
     * Returns a ClassLoader used to load the compiler classes.
     */
    public static JObject getJVMCIClassLoader(JNIEnv env) {
        JClass clazz;
        try (CCharPointerHolder className = CTypeConversion.toCString(JNIUtil.getBinaryName(ClassLoader.class.getName()))) {
            clazz = JNIUtil.FindClass(env, className.get());
        }
        if (clazz.isNull()) {
            throw new InternalError("No such class " + ClassLoader.class.getName());
        }
        JMethodID getClassLoaderId = findMethod(env, clazz, true, true, METHOD_GET_PLATFORM_CLASS_LOADER[0], METHOD_GET_PLATFORM_CLASS_LOADER[1]);
        if (getClassLoaderId.isNull()) {
            throw new InternalError(String.format("Cannot find method %s in class %s.", METHOD_GET_PLATFORM_CLASS_LOADER[0], ClassLoader.class.getName()));
        }
        return env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, getClassLoaderId, Word.nullPointer());
    }

    public static JObject getClassLoader(JNIEnv env, JClass clazz) {
        if (clazz.isNull()) {
            throw new NullPointerException();
        }
        // Class<Runtime>
        JClass classClass = GetObjectClass(env, clazz); // Class<Class>
        JMethodID getClassLoader = JNIUtil.findMethod(env, classClass, false, "getClassLoader", "()Ljava/lang/ClassLoader;");
        if (getClassLoader.isNull()) {
            throw new NullPointerException("Not found: getClassLoader()");
        }
        return env.getFunctions().getCallObjectMethodA().call(env, clazz, getClassLoader, Word.nullPointer());
    }

    /**
     * Returns the {@link ClassLoader#getSystemClassLoader()}.
     */
    public static JObject getSystemClassLoader(JNIEnv env) {
        JClass clazz;
        try (CCharPointerHolder className = CTypeConversion.toCString(JNIUtil.getBinaryName(ClassLoader.class.getName()))) {
            clazz = JNIUtil.FindClass(env, className.get());
        }
        if (clazz.isNull()) {
            throw new InternalError("No such class " + ClassLoader.class.getName());
        }
        JMethodID getClassLoaderId = findMethod(env, clazz, true, true, METHOD_GET_SYSTEM_CLASS_LOADER[0], METHOD_GET_SYSTEM_CLASS_LOADER[1]);
        if (getClassLoaderId.isNull()) {
            throw new InternalError(String.format("Cannot find method %s in class %s.", METHOD_GET_SYSTEM_CLASS_LOADER[0], ClassLoader.class.getName()));
        }
        return env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, getClassLoaderId, Word.nullPointer());
    }

    public static JMethodID findMethod(JNIEnv env, JClass clazz, boolean staticMethod, String methodName, String methodSignature) {
        return findMethod(env, clazz, staticMethod, false, methodName, methodSignature);
    }

    static JMethodID findMethod(JNIEnv env, JClass clazz, boolean staticMethod, boolean required,
                    String methodName, String methodSignature) {
        JMethodID result;
        try (CCharPointerHolder name = toCString(methodName); CCharPointerHolder sig = toCString(methodSignature)) {
            result = staticMethod ? GetStaticMethodID(env, clazz, name.get(), sig.get()) : GetMethodID(env, clazz, name.get(), sig.get());
            JNIExceptionWrapper.wrapAndThrowPendingJNIException(env,
                            required ? JNIExceptionWrapper.ExceptionHandler.DEFAULT : JNIExceptionWrapper.ExceptionHandler.allowExceptions(NoSuchMethodError.class));
            return result;
        }
    }

    public static JFieldID findField(JNIEnv env, JClass clazz, boolean staticField, String fieldName, String fieldSignature) {
        JFieldID result;
        try (CCharPointerHolder name = toCString(fieldName); CCharPointerHolder sig = toCString(fieldSignature)) {
            result = staticField ? GetStaticFieldID(env, clazz, name.get(), sig.get()) : GetFieldID(env, clazz, name.get(), sig.get());
            JNIExceptionWrapper.wrapAndThrowPendingJNIException(env);
            return result;
        }
    }

    /**
     * Attaches the current C thread to a Java Thread.
     *
     * @param vm the {@link JavaVM} pointer.
     * @param daemon if true attaches the thread as a daemon thread.
     * @param name the name of the Java tread or {@code null}.
     * @param threadGroup the thread group to add the thread into or C {@code NULL} pointer.
     * @return the current thread {@link JNIEnv} or C {@code NULL} pointer in case of error.
     */
    public static JNIEnv attachCurrentThread(JavaVM vm, boolean daemon, String name, JObject threadGroup) {
        try (CCharPointerHolder cname = CTypeConversion.toCString(name)) {
            JavaVMAttachArgs args = StackValue.get(JavaVMAttachArgs.class);
            args.setVersion(JNI_VERSION_10);
            args.setGroup(threadGroup);
            args.setName(cname.get());
            return daemon ? AttachCurrentThreadAsDaemon(vm, args) : AttachCurrentThread(vm, args);
        }
    }

    /*----------------- TRACING ------------------*/

    public static boolean tracingAt(int level) {
        return NativeBridgeSupport.getInstance().isTracingEnabled(level);
    }

    /**
     * Emits a trace line composed of {@code format} and {@code args} if the tracing level equal to
     * or greater than {@code level}.
     */
    public static void trace(int level, String format, Object... args) {
        if (tracingAt(level)) {
            NativeBridgeSupport.getInstance().trace(String.format(format, args));
        }
    }

    public static void trace(int level, Throwable throwable) {
        if (tracingAt(level)) {
            StringWriter stringWriter = new StringWriter();
            try (PrintWriter out = new PrintWriter(stringWriter)) {
                throwable.printStackTrace(out);
            }
            trace(level, stringWriter.toString());
        }
    }

    static String getFeatureName() {
        return NativeBridgeSupport.getInstance().getFeatureName();
    }
}
