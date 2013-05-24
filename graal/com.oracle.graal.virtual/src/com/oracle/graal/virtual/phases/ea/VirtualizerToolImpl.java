/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.virtual.phases.ea;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.spi.Virtualizable.State;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;

class VirtualizerToolImpl implements VirtualizerTool {

    private final NodeBitMap usages;
    private final MetaAccessProvider metaAccess;
    private final Assumptions assumptions;
    private GraphEffectList effects;

    VirtualizerToolImpl(NodeBitMap usages, MetaAccessProvider metaAccess, Assumptions assumptions) {
        this.usages = usages;
        this.metaAccess = metaAccess;
        this.assumptions = assumptions;
    }

    private boolean deleted;
    private BlockState state;
    private ValueNode current;
    private FixedNode position;

    @Override
    public MetaAccessProvider getMetaAccessProvider() {
        return metaAccess;
    }

    @Override
    public Assumptions getAssumptions() {
        return assumptions;
    }

    public void setEffects(GraphEffectList effects) {
        this.effects = effects;
    }

    public void reset(BlockState newState, ValueNode newCurrent, FixedNode newPosition) {
        deleted = false;
        state = newState;
        current = newCurrent;
        position = newPosition;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public State getObjectState(ValueNode value) {
        return state.getObjectState(value);
    }

    @Override
    public void setVirtualEntry(State objectState, int index, ValueNode value) {
        ObjectState obj = (ObjectState) objectState;
        assert obj != null && obj.isVirtual() : "not virtual: " + obj;
        ObjectState valueState = state.getObjectState(value);
        if (valueState == null) {
            obj.setEntry(index, getReplacedValue(value));
        } else {
            ValueNode newValue = value;
            if (valueState.getState() != EscapeState.Virtual) {
                newValue = valueState.getMaterializedValue();
            }
            assert obj.getEntry(index) == null || obj.getEntry(index).kind() == newValue.kind();
            obj.setEntry(index, newValue);
        }
    }

    @Override
    public ValueNode getMaterializedValue(ValueNode value) {
        ObjectState obj = state.getObjectState(value);
        return obj != null && !obj.isVirtual() ? obj.getMaterializedValue() : null;
    }

    @Override
    public ValueNode getReplacedValue(ValueNode original) {
        return state.getScalarAlias(original);
    }

    @Override
    public void replaceWithVirtual(VirtualObjectNode virtual) {
        state.addAndMarkAlias(virtual, current, usages);
        if (current instanceof FixedWithNextNode) {
            effects.deleteFixedNode((FixedWithNextNode) current);
        }
        deleted = true;
    }

    @Override
    public void replaceWithValue(ValueNode replacement) {
        effects.replaceAtUsages(current, state.getScalarAlias(replacement));
        state.addScalarAlias(current, replacement);
        deleted = true;
    }

    @Override
    public void delete() {
        assert current instanceof FixedWithNextNode;
        effects.deleteFixedNode((FixedWithNextNode) current);
        deleted = true;
    }

    @Override
    public void replaceFirstInput(Node oldInput, Node replacement) {
        effects.replaceFirstInput(current, oldInput, replacement);
    }

    @Override
    public void addNode(ValueNode node) {
        if (node instanceof FloatingNode) {
            effects.addFloatingNode(node, "VirtualizerTool");
        } else {
            effects.addFixedNodeBefore((FixedWithNextNode) node, position);
        }
    }

    @Override
    public void createVirtualObject(VirtualObjectNode virtualObject, ValueNode[] entryState, int[] locks) {
        VirtualUtil.trace("{{%s}} ", current);
        if (virtualObject.isAlive()) {
            state.addAndMarkAlias(virtualObject, virtualObject, usages);
        } else {
            effects.addFloatingNode(virtualObject, "newVirtualObject");
        }
        for (int i = 0; i < entryState.length; i++) {
            entryState[i] = state.getScalarAlias(entryState[i]);
        }
        state.addObject(virtualObject, new ObjectState(virtualObject, entryState, EscapeState.Virtual, locks));
        state.addAndMarkAlias(virtualObject, virtualObject, usages);
        PartialEscapeClosure.METRIC_ALLOCATION_REMOVED.increment();
    }

    @Override
    public int getMaximumEntryCount() {
        return GraalOptions.MaximumEscapeAnalysisArrayLength;
    }

    @Override
    public void replaceWith(ValueNode node) {
        State resultState = getObjectState(node);
        if (resultState == null) {
            replaceWithValue(node);
        } else {
            if (resultState.getState() == EscapeState.Virtual) {
                replaceWithVirtual(resultState.getVirtualObject());
            } else {
                replaceWithValue(resultState.getMaterializedValue());
            }
        }
    }

    @Override
    public void addReadCache(ValueNode object, ResolvedJavaField identity, ValueNode value) {
        if (GraalOptions.OptEarlyReadElimination) {
            state.addReadCache(object, identity, value);
        }
    }

    @Override
    public ValueNode getReadCache(ValueNode object, ResolvedJavaField identity) {
        if (GraalOptions.OptEarlyReadElimination) {
            return state.getReadCache(object, identity);
        }
        return null;
    }

    @Override
    public void killReadCache(ResolvedJavaField identity) {
        if (GraalOptions.OptEarlyReadElimination) {
            state.killReadCache(identity);
        }
    }
}
