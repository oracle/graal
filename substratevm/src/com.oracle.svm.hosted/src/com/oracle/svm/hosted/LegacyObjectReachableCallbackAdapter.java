/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.util.Objects;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.meta.JVMCIObjectReachableCallback;
import com.oracle.graal.pointsto.meta.ObjectReachableCallback;
import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Adapts a legacy builder-side object callback to the constant-based analysis callback. Object
 * materialization is confined to this compatibility boundary. Equality delegates to the original
 * callback so introducing the adapter does not change callback identity comparisons in analysis.
 *
 * This compatibility class can be removed after GR-78902 migrates all hosted-internal
 * {@link ObjectReachableCallback} clients to JVMCI or guest callbacks.
 */
final class LegacyObjectReachableCallbackAdapter<T> implements JVMCIObjectReachableCallback {
    private final ObjectReachableCallback<T> callback;

    /**
     * Creates an adapter for {@code callback}.
     *
     * @param callback the object-based callback to adapt
     */
    LegacyObjectReachableCallbackAdapter(ObjectReachableCallback<T> callback) {
        this.callback = Objects.requireNonNull(callback);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doCallback(DuringAnalysisAccess access, JavaConstant object, ScanReason reason) {
        T reachableObject = (T) GuestAccess.get().getSnippetReflection().asObject(Object.class, object);
        // GR-78902: Legacy callbacks remain builder-owned because DuringAnalysisAccess and
        // ScanReason are analysis objects. Guest callbacks use the one-argument constant bridge.
        callback.doCallback(access, reachableObject, reason);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LegacyObjectReachableCallbackAdapter<?> that = (LegacyObjectReachableCallbackAdapter<?>) o;
        return Objects.equals(callback, that.callback);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(callback);
    }

    @Override
    public String toString() {
        return "Legacy[" + callback + ']';
    }
}
