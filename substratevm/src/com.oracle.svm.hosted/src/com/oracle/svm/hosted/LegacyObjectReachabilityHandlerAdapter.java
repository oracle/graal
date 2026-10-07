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
import java.util.function.Consumer;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.meta.JVMCIObjectReachableCallback;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Adapts a legacy builder-side object reachability handler to the constant-based analysis pipeline.
 * Object materialization is confined to this compatibility boundary.
 *
 * This adapter can be removed after GR-78928 migrates all builder-side clients to constant-based or
 * guest handlers.
 */
final class LegacyObjectReachabilityHandlerAdapter<T> implements JVMCIObjectReachableCallback {
    private final Consumer<T> handler;
    private final HostedValuesProvider hostedValuesProvider;

    /** Creates an adapter for {@code handler} using {@code hostedValuesProvider}. */
    LegacyObjectReachabilityHandlerAdapter(Consumer<T> handler, HostedValuesProvider hostedValuesProvider) {
        this.handler = Objects.requireNonNull(handler);
        this.hostedValuesProvider = Objects.requireNonNull(hostedValuesProvider);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doCallback(DuringAnalysisAccess access, JavaConstant constant, ScanReason reason) {
        T object = (T) hostedValuesProvider.asObject(Object.class, constant);
        handler.accept(object);
    }
}
