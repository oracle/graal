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

import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

public final class DynamicNewInstanceTypeFlow extends TypeFlow<BytecodePosition> {

    protected final BytecodeLocation allocationSite;

    /** The new type provider. */
    protected TypeFlow<?> newTypeFlow;

    /**
     * The allocation context for the generated dynamic object. Null if this is not a clone.
     */
    protected final AnalysisContext allocationContext;

    public DynamicNewInstanceTypeFlow(TypeFlow<?> newTypeFlow, AnalysisType type, ValueNode node, BytecodeLocation allocationLabel) {
        super(node.getNodeSourcePosition(), type);
        this.allocationSite = allocationLabel;
        this.allocationContext = null;
        this.newTypeFlow = newTypeFlow;

        /*
         * The original dynamic new instance cannot be linked to the type, even using the
         * non-state-transfering method, because whenever the type is updated,which happens whenever
         * a subtype of it is discovered, would also update the dynamic new instance. We only want
         * that update in the clone.
         */
    }

    private DynamicNewInstanceTypeFlow(BigBang bb, DynamicNewInstanceTypeFlow original, MethodFlowsGraph methodFlows, AnalysisContext allocationContext) {
        super(original, methodFlows);
        this.allocationSite = original.allocationSite;
        this.allocationContext = allocationContext;
        this.newTypeFlow = methodFlows.lookupCloneOf(bb, original.newTypeFlow);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        AnalysisContext enclosingContext = methodFlows.context();
        AnalysisContext allocContext = bb.contextPolicy().allocationContext(enclosingContext, PointstoOptions.MaxHeapContextDepth.getValue(bb.getOptions()));

        return new DynamicNewInstanceTypeFlow(bb, this, methodFlows, allocContext);
    }

    @Override
    public void initClone(BigBang bb) {
        assert this.isClone();
        this.newTypeFlow.addObserver(bb, this);
    }

    @Override
    public void onObservedUpdate(BigBang bb) {
        /* Only a clone should be updated */
        assert this.isClone();

        /* The state of the new type provider has changed. */
        TypeState newTypeState = newTypeFlow.getState();
        TypeState currentTypeState = getState();

        /* Generate a heap object for every new incoming type. */
        TypeState resultState = newTypeState.typesStream()
                        .filter(t -> !currentTypeState.containsType(t))
                        .map(type -> TypeState.forAllocation(bb, allocationSite, type, allocationContext))
                        .reduce(TypeState.forEmpty(), (s1, s2) -> TypeState.forUnion(bb, s1, s2));

        assert !resultState.canBeNull();

        addState(bb, resultState);
    }

    public TypeFlow<?> newTypeFlow() {
        return newTypeFlow;
    }

    public BytecodeLocation allocationSite() {
        return allocationSite;
    }

    public AnalysisContext allocationContext() {
        return allocationContext;
    }

    @Override
    public void setObserved(TypeFlow<?> declaredTypeFlow) {
        this.newTypeFlow = declaredTypeFlow;
    }

    @Override
    public void onObservedSaturated(BigBang bb, TypeFlow<?> observed) {
        assert this.isClone();
        /* When the new-type flow saturates start observing the flow of the declared type. */
        replaceObservedWith(bb, declaredType);
    }

    @Override
    public boolean canSaturate() {
        /* The dynamic new instance tracks all of its input types. */
        return false;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("DynamicNewInstanceFlow<").append(getState()).append(">");
        return str.toString();
    }
}
