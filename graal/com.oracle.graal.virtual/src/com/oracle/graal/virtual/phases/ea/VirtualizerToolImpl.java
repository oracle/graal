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

import static com.oracle.graal.virtual.phases.ea.PartialEscapeAnalysisPhase.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.spi.Virtualizable.State;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;

class VirtualizerToolImpl implements VirtualizerTool {

    private final GraphEffectList effects;
    private final NodeBitMap usages;
    private final MetaAccessProvider metaAccess;
    private final Assumptions assumptions;

    VirtualizerToolImpl(GraphEffectList effects, NodeBitMap usages, MetaAccessProvider metaAccess, Assumptions assumptions) {
        this.effects = effects;
        this.usages = usages;
        this.metaAccess = metaAccess;
        this.assumptions = assumptions;
    }

    private boolean deleted;
    private boolean customAction;
    private BlockState state;
    private ValueNode current;
    private int newVirtualObjectCount = 0;

    @Override
    public MetaAccessProvider getMetaAccessProvider() {
        return metaAccess;
    }

    @Override
    public Assumptions getAssumptions() {
        return assumptions;
    }

    public void reset(BlockState newState, ValueNode newCurrent) {
        deleted = false;
        customAction = false;
        this.state = newState;
        this.current = newCurrent;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isCustomAction() {
        return customAction;
    }

    public int getNewVirtualObjectCount() {
        return newVirtualObjectCount;
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
            if (valueState.getState() == EscapeState.Virtual) {
                obj.setEntry(index, value);
            } else {
                obj.setEntry(index, valueState.getMaterializedValue());
            }
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
    public void customAction(Runnable action) {
        effects.customAction(action);
        customAction = true;
    }

    @Override
    public void createVirtualObject(VirtualObjectNode virtualObject, ValueNode[] entryState, int lockCount) {
        trace("{{%s}} ", current);
        if (virtualObject.isAlive()) {
            state.addAndMarkAlias(virtualObject, virtualObject, usages);
        } else {
            effects.addFloatingNode(virtualObject);
        }
        for (int i = 0; i < entryState.length; i++) {
            entryState[i] = state.getScalarAlias(entryState[i]);
        }
        state.addObject(virtualObject, new ObjectState(virtualObject, entryState, EscapeState.Virtual, lockCount));
        state.addAndMarkAlias(virtualObject, virtualObject, usages);
        PartialEscapeClosure.METRIC_ALLOCATION_REMOVED.increment();
        newVirtualObjectCount++;
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
}
