/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import static org.graalvm.jniutils.JNIExceptionWrapper.wrapAndThrowPendingJNIException;
import static org.graalvm.jniutils.JNIUtil.GetStaticMethodID;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;

import java.util.EnumMap;
import java.util.function.Function;

import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JMethodID;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JValue;
import org.graalvm.jniutils.JNICalls;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;

import com.oracle.truffle.compiler.hotspot.libgraal.FromLibGraalId;

/**
 * Helpers for calling methods in HotSpot heap via JNI.
 */
public abstract class FromLibGraalCalls<T extends Enum<T> & FromLibGraalId> {

    private final EnumMap<T, JNIMethodImpl<T>> methods;
    private final JNICalls hotSpotCalls;
    private final JClass peer;

    protected FromLibGraalCalls(Class<T> idType, JClass peer) {
        this.methods = new EnumMap<>(idType);
        this.hotSpotCalls = JNICalls.getDefault();
        this.peer = peer;
    }

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
     * Describes a method in HotSpot peer class}.
     */
    static final class JNIMethodImpl<T extends Enum<T> & FromLibGraalId> implements JNICalls.JNIMethod {
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
        hotSpotCalls.callStaticVoid(env, peer, method, args);
    }

    public final boolean callBoolean(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, boolean.class);
        return hotSpotCalls.callStaticBoolean(env, peer, method, args);
    }

    public final long callLong(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, long.class);
        return hotSpotCalls.callStaticLong(env, peer, method, args);
    }

    public final int callInt(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, int.class);
        return hotSpotCalls.callStaticInt(env, peer, method, args);
    }

    @SuppressWarnings("unchecked")
    public final <R extends JObject> R callJObject(JNIEnv env, T id, JValue args) {
        JNIMethodImpl<T> method = getJNIMethod(env, id, Object.class);
        return hotSpotCalls.callStaticJObject(env, peer, method, args);
    }

    private JNIMethodImpl<T> getJNIMethod(JNIEnv env, T hcId, Class<?> expectedReturnType) {
        assert hcId.getReturnType() == expectedReturnType || expectedReturnType.isAssignableFrom(hcId.getReturnType());
        try {
            return methods.computeIfAbsent(hcId, new Function<T, JNIMethodImpl<T>>() {
                @Override
                public JNIMethodImpl<T> apply(T id) {
                    JClass c = peer;
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

}
