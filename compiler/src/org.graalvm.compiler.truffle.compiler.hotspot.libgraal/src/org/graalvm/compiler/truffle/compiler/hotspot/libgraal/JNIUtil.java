/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JArray;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JByteArray;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JClass;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JLongArray;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JMethodID;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObjectArray;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JString;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JThrowable;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;

/**
 * Helpers for calling JNI functions.
 */
final class JNIUtil {

    // Checkstyle: stop
    static boolean IsSameObject(JNIEnv env, JObject ref1, JObject ref2) {
        traceJNI("IsSameObject");
        return env.getFunctions().getIsSameObject().call(env, ref1, ref2);
    }

    static void DeleteLocalRef(JNIEnv env, JObject ref) {
        traceJNI("DeleteLocalRef");
        env.getFunctions().getDeleteLocalRef().call(env, ref);
    }

    static int PushLocalFrame(JNIEnv env, int capacity) {
        traceJNI("PushLocalFrame");
        return env.getFunctions().getPushLocalFrame().call(env, capacity);
    }

    static JObject PopLocalFrame(JNIEnv env, JObject result) {
        traceJNI("PopLocalFrame");
        return env.getFunctions().getPopLocalFrame().call(env, result);
    }

    static JClass FindClass(JNIEnv env, CCharPointer name) {
        traceJNI("FindClass");
        return env.getFunctions().getFindClass().call(env, name);
    }

    static JMethodID GetStaticMethodID(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig) {
        traceJNI("GetStaticMethodID");
        return env.getFunctions().getGetStaticMethodID().call(env, clazz, name, sig);
    }

    static JObjectArray NewObjectArray(JNIEnv env, int len, JClass componentClass, JObject initialElement) {
        traceJNI("NewObjectArray");
        return env.getFunctions().getNewObjectArray().call(env, len, componentClass, initialElement);
    }

    static JByteArray NewByteArray(JNIEnv env, int len) {
        traceJNI("NewByteArray");
        return env.getFunctions().getNewByteArray().call(env, len);
    }

    static int GetArrayLength(JNIEnv env, JArray array) {
        traceJNI("GetArrayLength");
        return env.getFunctions().getGetArrayLength().call(env, array);
    }

    static void SetObjectArrayElement(JNIEnv env, JObjectArray array, int index, JObject value) {
        traceJNI("SetObjectArrayElement");
        env.getFunctions().getSetObjectArrayElement().call(env, array, index, value);
    }

    static JObject GetObjectArrayElement(JNIEnv env, JObjectArray array, int index) {
        traceJNI("GetObjectArrayElement");
        return env.getFunctions().getGetObjectArrayElement().call(env, array, index);
    }

    static CLongPointer GetLongArrayElements(JNIEnv env, JLongArray array, JValue isCopy) {
        traceJNI("GetLongArrayElements");
        return env.getFunctions().getGetLongArrayElements().call(env, array, isCopy);
    }

    static void ReleaseLongArrayElements(JNIEnv env, JLongArray array, CLongPointer elems, int mode) {
        traceJNI("ReleaseLongArrayElements");
        env.getFunctions().getReleaseLongArrayElements().call(env, array, elems, mode);
    }

    static CCharPointer GetByteArrayElements(JNIEnv env, JByteArray array, JValue isCopy) {
        traceJNI("GetByteArrayElements");
        return env.getFunctions().getGetByteArrayElements().call(env, array, isCopy);
    }

    static void ReleaseByteArrayElements(JNIEnv env, JByteArray array, CCharPointer elems, int mode) {
        traceJNI("ReleaseByteArrayElements");
        env.getFunctions().getReleaseByteArrayElements().call(env, array, elems, mode);
    }

    static void Throw(JNIEnv env, JThrowable throwable) {
        traceJNI("Throw");
        env.getFunctions().getThrow().call(env, throwable);
    }

    static boolean ExceptionCheck(JNIEnv env) {
        traceJNI("ExceptionCheck");
        return env.getFunctions().getExceptionCheck().call(env);
    }

    static void ExceptionClear(JNIEnv env) {
        traceJNI("ExceptionClear");
        env.getFunctions().getExceptionClear().call(env);
    }

    static void ExceptionDescribe(JNIEnv env) {
        traceJNI("ExceptionDescribe");
        env.getFunctions().getExceptionDescribe().call(env);
    }

    static JThrowable ExceptionOccurred(JNIEnv env) {
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
    static <T extends JObject> T NewGlobalRef(JNIEnv env, T ref, String type) {
        traceJNI("NewGlobalRef");
        T res = (T) env.getFunctions().getNewGlobalRef().call(env, ref);
        if (HotSpotToSVMEntryPoints.tracingAt(3)) {
            HotSpotToSVMEntryPoints.trace(3, "New global reference for 0x%x of type %s -> 0x%x", ref.rawValue(), type, res.rawValue());
        }
        return res;
    }

    static void DeleteGlobalRef(JNIEnv env, JObject ref) {
        traceJNI("DeleteGlobalRef");
        if (HotSpotToSVMEntryPoints.tracingAt(3)) {
            HotSpotToSVMEntryPoints.trace(3, "Delete global reference 0x%x", ref.rawValue());
        }
        env.getFunctions().getDeleteGlobalRef().call(env, ref);
    }

    static VoidPointer GetDirectBufferAddress(JNIEnv env, JObject buf) {
        traceJNI("GetDirectBufferAddress");
        return env.getFunctions().getGetDirectBufferAddress().call(env, buf);
    }

    // Checkstyle: resume

    private static void traceJNI(String function) {
        HotSpotToSVMEntryPoints.trace(2, "SVM->JNI: %s", function);
    }

    private JNIUtil() {
    }

    /**
     * Decodes a string in the HotSpot heap to a local {@link String}.
     */
    static String createString(JNIEnv env, JString hsString) {
        if (hsString.isNull()) {
            return null;
        }
        int len = env.getFunctions().getGetStringLength().call(env, hsString);
        CShortPointer unicode = env.getFunctions().getGetStringChars().call(env, hsString, WordFactory.nullPointer());
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
    static JString createHSString(JNIEnv env, String string) {
        if (string == null) {
            return WordFactory.nullPointer();
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

    /**
     * Converts a fully qualified Java class name from Java source format (e.g.
     * {@code "java.lang.getString"}) to internal format (e.g. {@code "Ljava/lang/getString;"}.
     */
    static String getInternalName(String fqn) {
        return "L" + getBinaryName(fqn) + ";";
    }

    /**
     * Converts a fully qualified Java class name from Java source format (e.g.
     * {@code "java.lang.getString"}) to binary format (e.g. {@code "java/lang/getString"}.
     */
    static String getBinaryName(String fqn) {
        return fqn.replace('.', '/');
    }
}
