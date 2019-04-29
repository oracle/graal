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

import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.PopLocalFrame;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNIUtil.PushLocalFrame;

import org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.HotSpotToSVM.Id;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;
import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;

/**
 * Scope of a call from HotSpot to SVM. This also provides access to the {@link JNIEnv} value for
 * the current thread within the SVM call.
 *
 * If the SVM call returns a non-primitive value, the return value must be
 * {@linkplain #setObjectResult(JObject) set} within the try-with-resources statement and then
 * {@linkplain #getObjectResult() retrieved} and returned outside the try-with-resources statement.
 * This is necessary to support use of JNI local frames.
 */
public class HotSpotToSVMScope implements AutoCloseable {

    private static final ThreadLocal<HotSpotToSVMScope> topScope = new ThreadLocal<>();

    private final JNIEnv env;
    private final HotSpotToSVMScope parent;
    private HotSpotToSVMScope leaf;

    /**
     * List of scope local {@link HSObject}s that created within this scope. These are
     * {@linkplain HSObject#invalidate(HSObject) invalidated} when the scope closes.
     */
    HSObject locals;

    /**
     * The SVM call for this scope.
     */
    private final Id id;

    /**
     * Gets the {@link JNIEnv} value for the current thread.
     */
    static JNIEnv env() {
        return scope().env;
    }

    JNIEnv getEnv() {
        return env;
    }

    /**
     * Gets the inner most {@link HotSpotToSVMScope} value for the current thread.
     */
    static HotSpotToSVMScope scopeOrNull() {
        HotSpotToSVMScope scope = topScope.get();
        if (scope == null) {
            return null;
        }
        return scope.leaf;
    }

    /**
     * Gets the inner most {@link HotSpotToSVMScope} value for the current thread.
     */
    static HotSpotToSVMScope scope() {
        HotSpotToSVMScope scope = topScope.get();
        if (scope == null) {
            throw new IllegalStateException("Not in the scope of an SVM call");
        }
        return scope.leaf;
    }

    /**
     * Enters the scope of an SVM call.
     */
    public HotSpotToSVMScope(HotSpotToSVM.Id id, JNIEnv env) {
        HotSpotToSVMEntryPoints.trace(1, "HS->SVM[enter]: %s", id);
        this.id = id;
        HotSpotToSVMScope top = topScope.get();
        this.env = env;
        if (top == null) {
            // Only push a JNI frame for the top level SVM call.
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

    void setObjectResult(JObject obj) {
        objResult = obj;
    }

    @SuppressWarnings("unchecked")
    <T extends JObject> T getObjectResult() {
        return (T) objResult;
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
            HotSpotToSVMScope top = parent;
            while (top.parent != null) {
                top = top.parent;
            }
            top.leaf = parent;
        }
        HotSpotToSVMEntryPoints.trace(1, "HS->SVM[ exit]: %s", id);
    }

    int depth() {
        int depth = 0;
        HotSpotToSVMScope ancestor = parent;
        while (ancestor != null) {
            depth++;
            ancestor = ancestor.parent;
        }
        return depth;
    }

    @Override
    public String toString() {
        return "SVMCall[" + depth() + "]@" + Long.toHexString(env.rawValue());
    }
}
