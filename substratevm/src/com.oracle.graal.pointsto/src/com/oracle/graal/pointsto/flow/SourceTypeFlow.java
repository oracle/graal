/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Models a flow that *conditionally* introduces a type in the type flow graph only if it is marked
 * as instantiated, e.g., by scanning a constant of that type or parsing an allocation bytecode.
 * This flow is used for nodes that produce an object but don't register its type as instantiated
 * themselves, e.g., like {@link JavaReadNode} or {@link BytecodeExceptionNode}. Also LoadHubNode,
 * GetClassNode, LoadVMThreadLocalNode.
 * 
 * The type state of this source is "empty" or "null" until the declared type is marked as
 * instantiated, depending on the null state of the node stamp. When this flow is initialized it
 * registers a callback with its declared type such that when the type is marked as instantiated it
 * propagates the source state. If the declared type is already instantiated when the source flow is
 * initialized then the callback is immediately triggered.
 * 
 * If the type is really never instantiated, i.e., {@link AnalysisType#isInstantiated()} is still
 * false at the end of the static analysis, then the callback is never triggered. That is correct,
 * because in this case that type can never be produced by this flow (and the only possible value is
 * {@code null}, if the stamp can be null, or empty).
 */
public final class SourceTypeFlow extends TypeFlow<BytecodePosition> {

    public SourceTypeFlow(BytecodePosition position, AnalysisType type, boolean canBeNull) {
        super(position, type, canBeNull);
    }

    public SourceTypeFlow(SourceTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows, original.getState().canBeNull() ? TypeState.forNull() : TypeState.forEmpty());
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new SourceTypeFlow(this, methodFlows);
    }

    @Override
    public void initFlow(PointsToAnalysis bb) {
        /* Propagate the source state when the type is marked as instantiated. */
        declaredType.registerInstantiatedCallback(a -> addState(bb, TypeState.forExactType(bb, declaredType, false)));
    }

    @Override
    public boolean needsInitialization() {
        return true;
    }

    @Override
    public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
        AnalysisError.shouldNotReachHere("NewInstanceTypeFlow cannot saturate.");
    }

    @Override
    protected void onInputSaturated(PointsToAnalysis bb, TypeFlow<?> input) {
        AnalysisError.shouldNotReachHere("NewInstanceTypeFlow cannot saturate.");
    }

    @Override
    protected void onSaturated(PointsToAnalysis bb) {
        AnalysisError.shouldNotReachHere("NewInstanceTypeFlow cannot saturate.");
    }

    @Override
    public boolean canSaturate() {
        return false;
    }

    @Override
    public String toString() {
        return "SourceFlow<" + getState() + ">";
    }
}
