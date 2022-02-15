/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AtomicUtils;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PointsToAnalysisMethod extends AnalysisMethod {
    private MethodTypeFlow typeFlow;

    private ConcurrentMap<InvokeTypeFlow, Object> invokedBy;
    private ConcurrentMap<InvokeTypeFlow, Object> implementationInvokedBy;
    /**
     * Unique, per method, context insensitive invoke. The context insensitive invoke uses the
     * receiver type of the method, i.e., its declaring-class. Therefore, this invoke will link with
     * all possible callees.
     */
    private final AtomicReference<InvokeTypeFlow> contextInsensitiveInvoke = new AtomicReference<>();

    PointsToAnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped) {
        super(universe, wrapped);
        typeFlow = new MethodTypeFlow(universe.hostVM().options(), this);
    }

    @Override
    public void startTrackInvocations() {
        if (invokedBy == null) {
            invokedBy = new ConcurrentHashMap<>();
        }
        if (implementationInvokedBy == null) {
            implementationInvokedBy = new ConcurrentHashMap<>();
        }
    }

    public MethodTypeFlow getTypeFlow() {
        return typeFlow;
    }

    public boolean registerAsInvoked(InvokeTypeFlow invoke) {
        if (invokedBy != null && invoke != null) {
            invokedBy.put(invoke, Boolean.TRUE);
        }
        return super.registerAsInvoked();
    }

    public boolean registerAsImplementationInvoked(InvokeTypeFlow invoke) {
        if (implementationInvokedBy != null && invoke != null) {
            implementationInvokedBy.put(invoke, Boolean.TRUE);
        }
        return super.registerAsImplementationInvoked();
    }

    @Override
    public List<BytecodePosition> getInvokeLocations() {
        List<BytecodePosition> locations = new ArrayList<>();
        for (InvokeTypeFlow invoke : implementationInvokedBy.keySet()) {
            if (InvokeTypeFlow.isContextInsensitiveVirtualInvoke(invoke)) {
                locations.addAll(((AbstractVirtualInvokeTypeFlow) invoke).getInvokeLocations());
            } else {
                locations.add(invoke.getSource());
            }
        }
        return locations;
    }

    @Override
    public Collection<InvokeInfo> getInvokes() {
        return Collections.unmodifiableCollection(getTypeFlow().getInvokes());
    }

    @Override
    public StackTraceElement[] getParsingContext() {
        return getTypeFlow().getParsingContext();
    }

    public InvokeTypeFlow initAndGetContextInsensitiveInvoke(PointsToAnalysis bb, BytecodePosition originalLocation) {
        return AtomicUtils.produceAndSetValue(contextInsensitiveInvoke,
                        () -> InvokeTypeFlow.createContextInsensitiveInvoke(bb, this, originalLocation),
                        (t) -> InvokeTypeFlow.initContextInsensitiveInvoke(bb, this, t));
    }

    public InvokeTypeFlow getContextInsensitiveInvoke() {
        InvokeTypeFlow invoke = contextInsensitiveInvoke.get();
        AnalysisError.guarantee(invoke != null);
        return invoke;
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        contextInsensitiveInvoke.set(null);
        typeFlow = null;
        invokedBy = null;
        implementationInvokedBy = null;
    }

}
