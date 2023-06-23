/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.jniutils;

import static org.graalvm.jniutils.JNIUtil.getFeatureName;

import java.util.Objects;

import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;

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
    @SuppressWarnings({"unchecked", "this-escape"})
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

    public final int depth() {
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
