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
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

/**
 * The all-instantiated type state is defined as the maximum state that is allowed. If our type was
 * just discovered as instantiated, it is not yet part of the "all instantiated" flow (so that
 * updates to the all-instantiated flow can be batched together).
 *
 * Therefore, the type state of this source is "empty" until the "all instantiated" type state gets
 * updated. We discover that by registering ourself as an observer of the all-instantiated type
 * flow, and de-registering ourselves as soon as we change our type from "empty" to the actual exact
 * type.
 *
 * To temporarily suspend updates containing the type that is not in all-instantiated yet we save
 * the state in a temporary, sourceState, which is copied in the type state of the source flow when
 * all-instantiated is updated.
 *
 * After the source state type is added to the all-instantiated state this.state and
 * this.sourceState point to the same state object (due to TypeState.addState() union operation
 * special case optimization).
 *
 * If the type is really never instantiated, i.e., AnalysisType.isInstantiated() is still false at
 * the end of the static analysis, then the rewrite never happens. That is correct, because in this
 * case that type can never be returned by this flow (and the only possible value is null, which is
 * set regardless of the type update).
 */
public abstract class SourceTypeFlowBase extends TypeFlow<BytecodePosition> {

    /**
     * The source state is a temporary buffer for this flow's type state. The source state is added
     * to the flow, and propagated to its uses, only when its exact type is added to the
     * all-instantiated type state.
     */
    protected final TypeState sourceState;

    public SourceTypeFlowBase(ValueNode node, TypeState state) {
        this(node, state.exactType(), state);
    }

    public SourceTypeFlowBase(ValueNode node, AnalysisType declaredType, TypeState state) {
        super(node.getNodeSourcePosition(), declaredType);
        this.sourceState = state;
    }

    public SourceTypeFlowBase(BigBang bb, SourceTypeFlowBase original, MethodFlowsGraph methodFlows) {
        this(bb, original, methodFlows, original.sourceState);
    }

    public SourceTypeFlowBase(@SuppressWarnings("unused") BigBang bb, SourceTypeFlowBase original, MethodFlowsGraph methodFlows, TypeState state) {
        super(original, methodFlows);
        this.sourceState = state;
    }

    @Override
    public void initClone(BigBang bb) {
        /* When the clone is linked check if the all-instantiated contains the source state type. */
        if (sourceState.isNull() || sourceState.isEmpty() || bb.getAllInstantiatedTypeFlow().getState().containsType(sourceState.exactType())) {
            /* If yes, set the state and propagate it to uses. */
            addState(bb, sourceState);
        } else {
            /*
             * If no, update the can-be-null state of the source flow and register it as an observer
             * for all-instantiated.
             */
            addState(bb, sourceState.canBeNull() ? TypeState.forNull() : TypeState.forEmpty());
            bb.getAllInstantiatedTypeFlow().addObserver(bb, this);
        }
    }

    @Override
    public void onObservedUpdate(BigBang bb) {
        /* When the all-instantiated changes it will notify the source flow. */
        if (bb.getAllInstantiatedTypeFlow().getState().containsType(sourceState.exactType())) {
            /* The source state type was instantiated. */
            /* Now the source flow can be removed from the all-instantiated observers. */
            bb.getAllInstantiatedTypeFlow().removeObserver(this);
            /* Update the state and propagate it to uses. */
            addState(bb, sourceState);
        }
    }

    @Override
    public void onObservedSaturated(BigBang bb, TypeFlow<?> observed) {
        AnalysisError.shouldNotReachHere("NewInstanceTypeFlow cannot saturate.");
    }

    @Override
    protected void onInputSaturated(BigBang bb, TypeFlow<?> input) {
        AnalysisError.shouldNotReachHere("NewInstanceTypeFlow cannot saturate.");
    }

    @Override
    protected void onSaturated(BigBang bb) {
        AnalysisError.shouldNotReachHere("NewInstanceTypeFlow cannot saturate.");
    }

    @Override
    public boolean canSaturate() {
        return false;
    }

    @Override
    public boolean addState(BigBang bb, TypeState add) {
        /* Only a clone should be updated */
        assert this.isClone();
        return super.addState(bb, add);
    }

    @Override
    public void update(BigBang bb) {
        assert !getState().isEmpty() : "why update when state is still empty?";
        super.update(bb);
    }
}
