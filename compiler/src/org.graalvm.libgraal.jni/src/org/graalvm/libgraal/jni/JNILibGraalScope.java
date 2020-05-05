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
package org.graalvm.libgraal.jni;

import static org.graalvm.libgraal.jni.JNIUtil.PopLocalFrame;
import static org.graalvm.libgraal.jni.JNIUtil.PushLocalFrame;

import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;

/**
 * Scope of a call from HotSpot to libgraal. This also provides access to the {@link JNIEnv} value
 * for the current thread within the libgraal call.
 *
 * If the libgraal call returns a non-primitive value, the return value must be
 * {@linkplain #setObjectResult(JObject) set} within the try-with-resources statement and then
 * {@linkplain #getObjectResult() retrieved} and returned outside the try-with-resources statement.
 * This is necessary to support use of JNI local frames.
 */
public class JNILibGraalScope<T extends Enum<T>> implements AutoCloseable {

    private static final ThreadLocal<JNILibGraalScope<?>> topScope = new ThreadLocal<>();

    private final JNIEnv env;
    private final JNILibGraalScope<?> parent;
    private JNILibGraalScope<?> leaf;

    /**
     * List of scope local {@link HSObject}s that created within this scope. These are
     * {@linkplain HSObject#invalidate(HSObject) invalidated} when the scope closes.
     */
    HSObject locals;

    /**
     * The libgraal call for this scope.
     */
    private final Enum<T> id;

    /**
     * Gets the {@link JNIEnv} value for the current thread.
     */
    public static JNIEnv env() {
        return scope().env;
    }

    public JNIEnv getEnv() {
        return env;
    }

    /**
     * Gets the inner most {@link JNILibGraalScope} value for the current thread.
     */
    public static JNILibGraalScope<?> scopeOrNull() {
        JNILibGraalScope<?> scope = topScope.get();
        if (scope == null) {
            return null;
        }
        return scope.leaf;
    }

    /**
     * Gets the inner most {@link JNILibGraalScope} value for the current thread.
     */
    public static JNILibGraalScope<?> scope() {
        JNILibGraalScope<?> scope = topScope.get();
        if (scope == null) {
            throw new IllegalStateException("Not in the scope of an libgraal call");
        }
        return scope.leaf;
    }

    /**
     * Casts this {@link JNILibGraalScope} to scope of given scope id type.
     *
     * @param scopeIdType the requested scope id type
     * @throws ClassCastException if this {@link JNILibGraalScope}'s id is not an instance of given
     *             {@code scopeIdType}
     */
    @SuppressWarnings("unchecked")
    public <P extends Enum<P>> JNILibGraalScope<P> narrow(Class<P> scopeIdType) {
        if (id.getClass() != scopeIdType) {
            throw new ClassCastException("Expected ToLibGraalScope type is " + scopeIdType + " but actual type is " + id.getClass());
        }
        return (JNILibGraalScope<P>) this;
    }

    /**
     * Enters the scope of an libgraal call.
     */
    @SuppressWarnings("unchecked")
    public JNILibGraalScope(Enum<T> id, JNIEnv env) {
        JNIUtil.trace(1, "HS->LIBGRAAL[enter]: %s", id);
        this.id = id;
        JNILibGraalScope<?> top = topScope.get();
        this.env = env;
        if (top == null) {
            // Only push a JNI frame for the top level libgraal call.
            // HotSpot's JNI implementation currently ignores the `capacity` argument
            PushLocalFrame(env, 64);
            top = this;
            parent = null;
            topScope.set(this);
        } else {
            if (top.env != this.env) {
                throw new IllegalStateException("Cannot mix JNI scopes: " + this + " and " + top);
            }
            parent = top.leaf;
        }
        top.leaf = this;
    }

    /**
     * Used to copy the handle to an object return value out of the JNI local frame.
     */
    private JObject objResult;

    public void setObjectResult(JObject obj) {
        objResult = obj;
    }

    @SuppressWarnings("unchecked")
    public <R extends JObject> R getObjectResult() {
        return (R) objResult;
    }

    @Override
    public void close() {
        HSObject.invalidate(locals);
        if (parent == null) {
            if (topScope.get() != this) {
                throw new IllegalStateException("Unexpected JNI scope: " + topScope.get());
            }
            topScope.set(null);
            objResult = PopLocalFrame(env, objResult);
        } else {
            JNILibGraalScope<?> top = parent;
            while (top.parent != null) {
                top = top.parent;
            }
            top.leaf = parent;
        }
        JNIUtil.trace(1, "HS->LIBGRAAL[ exit]: %s", id);
    }

    int depth() {
        int depth = 0;
        JNILibGraalScope<?> ancestor = parent;
        while (ancestor != null) {
            depth++;
            ancestor = ancestor.parent;
        }
        return depth;
    }

    @Override
    public String toString() {
        return "JNILibGraalScope[" + depth() + "]@" + Long.toHexString(env.rawValue());
    }
}
