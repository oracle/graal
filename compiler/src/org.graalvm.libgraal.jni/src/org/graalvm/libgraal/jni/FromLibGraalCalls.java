/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal.jni;

import static org.graalvm.jniutils.JNIExceptionWrapper.wrapAndThrowPendingJNIException;

import org.graalvm.libgraal.jni.annotation.FromLibGraalId;
import static org.graalvm.jniutils.JNIUtil.GetStaticMethodID;
import static org.graalvm.jniutils.JNIUtil.NewGlobalRef;
import static org.graalvm.jniutils.JNIUtil.getBinaryName;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.jniutils.HotSpotCalls;
import org.graalvm.jniutils.JNI;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JMethodID;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JValue;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;

/**
 * Helpers for calling methods in HotSpot heap via JNI.
 */
public abstract class FromLibGraalCalls<T extends Enum<T> & FromLibGraalId> {

    private static final Map<String, JNIClass> classes = new ConcurrentHashMap<>();

    private final EnumMap<T, JNIMethodImpl<T>> methods;
    private final HotSpotCalls hotSpotCalls;
    private volatile JClass peer;

    protected FromLibGraalCalls(Class<T> idType) {
        methods = new EnumMap<>(idType);
        hotSpotCalls = HotSpotCalls.getDefault();
    }

    /**
     * Returns a {@link JClass} for the from LibGraal entry points.
     */
    protected abstract JClass resolvePeer(JNIEnv env);

    /**
     * Describes a class and holds a reference to its {@linkplain #jclass JNI value}.
     */
    static final class JNIClass {
        final String className;
        final JClass jclass;

        JNIClass(String className, JClass clazz) {
            this.className = className;
            this.jclass = clazz;
        }
    }

    /**
     * Describes a method in {@link #peer(JNI.JNIEnv) HotSpot peer class}.
     */
    static final class JNIMethodImpl<T extends Enum<T> & FromLibGraalId> implements HotSpotCalls.JNIMethod {
        final T hcId;
        final JMethodID jniId;

        JNIMethodImpl(T hcId, JMethodID jniId) {
            this.hcId = hcId;
            this.jniId = jniId;
        }

        @Override
        public JMethodID getJMethodID() {
            return jniId;
        }

        @Override
        public String getDisplayName() {
            return hcId.getName();
        }

        @Override
        public String toString() {
            return hcId + "[0x" + Long.toHexString(jniId.rawValue()) + ']';
        }
    }

    public final void callVoid(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, void.class);
        hotSpotCalls.callStaticVoid(env, peer(env), method, args);
    }

    public final boolean callBoolean(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, boolean.class);
        return hotSpotCalls.callStaticBoolean(env, peer(env), method, args);
    }

    public final long callLong(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, long.class);
        return hotSpotCalls.callStaticLong(env, peer(env), method, args);
    }

    public final int callInt(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, int.class);
        return hotSpotCalls.callStaticInt(env, peer(env), method, args);
    }

    @SuppressWarnings("unchecked")
    public final <R extends JObject> R callJObject(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, Object.class);
        return hotSpotCalls.callStaticJObject(env, peer(env), method, args);
    }

    public static JClass getJNIClass(JNIEnv env, Class<?> clazz) {
        if (clazz.isArray()) {
            throw new UnsupportedOperationException("Array classes are not supported");
        }
        return getJNIClassImpl(env, clazz.getName()).jclass;
    }

    public static JClass getJNIClass(JNIEnv env, String className) {
        return getJNIClassImpl(env, className).jclass;
    }

    private static JNIClass getJNIClassImpl(JNIEnv env, String className) {
        try {
            return classes.computeIfAbsent(className, new Function<String, JNIClass>() {
                @Override
                public JNIClass apply(String name) {
                    JNI.JObject classLoader = JNIUtil.getJVMCIClassLoader(env);
                    // If the JVMCI classloader does not exist, then JVMCI must have been loaded on
                    // the boot classpath. This is the case, for example, in unit tests, when the
                    // -XX:-UseJVMCIClassLoader flag is specified.
                    JClass clazz = classLoader.isNull() ? JNIUtil.findClass(env, getBinaryName(name)) : JNIUtil.findClass(env, classLoader, getBinaryName(name));
                    if (clazz.isNull()) {
                        JNIUtil.ExceptionClear(env);
                        throw new InternalError("Cannot load class: " + name);
                    }
                    return new JNIClass(name, NewGlobalRef(env, clazz, "Class<" + name + ">"));
                }
            });
        } catch (InternalError ie) {
            wrapAndThrowPendingJNIException(env);
            throw ie;
        }
    }

    private JNIMethodImpl<T> getJNIMethod(JNIEnv env, T hcId, Class<?> expectedReturnType) {
        assert hcId.getReturnType() == expectedReturnType || expectedReturnType.isAssignableFrom(hcId.getReturnType());
        try {
            return methods.computeIfAbsent(hcId, new Function<T, JNIMethodImpl<T>>() {
                @Override
                public JNIMethodImpl<T> apply(T id) {
                    JClass c = peer(env);
                    String methodName = id.getMethodName();
                    try (CCharPointerHolder name = toCString(methodName); CCharPointerHolder sig = toCString(id.getSignature())) {
                        JMethodID jniId = GetStaticMethodID(env, c, name.get(), sig.get());
                        if (jniId.isNull()) {
                            throw new InternalError("No such method: " + methodName);
                        }
                        return new JNIMethodImpl<>(id, jniId);
                    }
                }
            });
        } catch (InternalError ie) {
            wrapAndThrowPendingJNIException(env);
            throw ie;
        }
    }

    private JClass peer(JNIEnv env) {
        if (peer.isNull()) {
            peer = resolvePeer(env);
        }
        return peer;
    }
}
