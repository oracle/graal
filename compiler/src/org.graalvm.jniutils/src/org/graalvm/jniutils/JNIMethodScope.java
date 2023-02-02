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

import static org.graalvm.jniutils.JNIUtil.getFeatureName;

import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;

import java.util.Objects;

/**
 * Scope of a call from HotSpot to native method. This also provides access to the {@link JNIEnv}
 * value for the current thread within the native method call.
 *
 * If the native method call returns a non-primitive value, the return value must be
 * {@linkplain #setObjectResult(JObject) set} within the try-with-resources statement and then
 * {@linkplain #getObjectResult() retrieved} and returned outside the try-with-resources statement.
 * This is necessary to support use of JNI local frames.
 */
public class JNIMethodScope implements AutoCloseable {

    private static final ThreadLocal<JNIMethodScope> topScope = new ThreadLocal<>();

    private final JNIEnv env;
    private final JNIMethodScope parent;
    private JNIMethodScope leaf;

    /**
     * List of scope local {@link HSObject}s that created within this scope. These are
     * {@linkplain HSObject#invalidate(HSObject) invalidated} when the scope closes.
     */
    HSObject locals;

    /**
     * The name for this scope.
     */
    private final String scopeName;

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
     * Gets the inner most {@link JNIMethodScope} value for the current thread.
     */
    public static JNIMethodScope scopeOrNull() {
        JNIMethodScope scope = topScope.get();
        if (scope == null) {
            return null;
        }
        return scope.leaf;
    }

    /**
     * Gets the inner most {@link JNIMethodScope} value for the current thread.
     */
    public static JNIMethodScope scope() {
        JNIMethodScope scope = topScope.get();
        if (scope == null) {
            throw new IllegalStateException("Not in the scope of an JNI method call");
        }
        return scope.leaf;
    }

    /**
     * Enters the scope of an native method call.
     */
    @SuppressWarnings("unchecked")
    public JNIMethodScope(String scopeName, JNIEnv env) {
        Objects.requireNonNull(scopeName, "ScopeName must be non null.");
        this.scopeName = scopeName;
        JNIMethodScope top = topScope.get();
        this.env = env;
        if (top == null) {
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
        JNIUtil.trace(1, "HS->%s[enter]: %s", JNIUtil.getFeatureName(), scopeName);
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
        JNIUtil.trace(1, "HS->%s[ exit]: %s", getFeatureName(), scopeName);
        HSObject.invalidate(locals);
        if (parent == null) {
            if (topScope.get() != this) {
                throw new IllegalStateException("Unexpected JNI scope: " + topScope.get());
            }
            topScope.set(null);
        } else {
            JNIMethodScope top = parent;
            while (top.parent != null) {
                top = top.parent;
            }
            top.leaf = parent;
        }
    }

    int depth() {
        int depth = 0;
        JNIMethodScope ancestor = parent;
        while (ancestor != null) {
            depth++;
            ancestor = ancestor.parent;
        }
        return depth;
    }

    @Override
    public String toString() {
        return "JNIMethodScope[" + depth() + "]@" + Long.toHexString(env.rawValue());
    }
}
