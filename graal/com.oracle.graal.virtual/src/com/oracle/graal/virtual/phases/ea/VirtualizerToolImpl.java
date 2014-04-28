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

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.spi.Virtualizable.State;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

class VirtualizerToolImpl implements VirtualizerTool {

    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final Assumptions assumptions;
    private final PartialEscapeClosure<?> closure;

    VirtualizerToolImpl(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, Assumptions assumptions, PartialEscapeClosure<?> closure) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
        this.assumptions = assumptions;
        this.closure = closure;
    }

    private boolean deleted;
    private PartialEscapeBlockState<?> state;
    private ValueNode current;
    private FixedNode position;
    private GraphEffectList effects;

    @Override
    public MetaAccessProvider getMetaAccessProvider() {
        return metaAccess;
    }

    public ConstantReflectionProvider getConstantReflectionProvider() {
        return constantReflection;
    }

    @Override
    public Assumptions getAssumptions() {
        return assumptions;
    }

    public void reset(PartialEscapeBlockState<?> newState, ValueNode newCurrent, FixedNode newPosition, GraphEffectList newEffects) {
        deleted = false;
        state = newState;
        current = newCurrent;
        position = newPosition;
        effects = newEffects;
    }

    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public State getObjectState(ValueNode value) {
        return closure.getObjectState(state, value);
    }

    @Override
    public void setVirtualEntry(State objectState, int index, ValueNode value, boolean unsafe) {
        ObjectState obj = (ObjectState) objectState;
        assert obj != null && obj.isVirtual() : "not virtual: " + obj;
        ValueNode newValue;
        if (value == null) {
            newValue = null;
        } else {
            ObjectState valueState = closure.getObjectState(state, value);
            if (valueState == null) {
                newValue = getReplacedValue(value);
                assert unsafe || obj.getEntry(index) == null || obj.getEntry(index).getKind() == newValue.getKind() || (isObjectEntry(obj.getEntry(index)) && isObjectEntry(newValue));
            } else {
                if (valueState.getState() != EscapeState.Virtual) {
                    newValue = valueState.getMaterializedValue();
                    assert newValue.getKind() == Kind.Object;
                } else {
                    newValue = valueState.getVirtualObject();
                }
                assert obj.getEntry(index) == null || isObjectEntry(obj.getEntry(index));
            }
        }
        obj.setEntry(index, newValue);
    }

    private static boolean isObjectEntry(ValueNode value) {
        return value.getKind() == Kind.Object || value instanceof VirtualObjectNode;
    }

    @Override
    public ValueNode getReplacedValue(ValueNode original) {
        return closure.getScalarAlias(original);
    }

    @Override
    public void replaceWithVirtual(VirtualObjectNode virtual) {
        closure.addAndMarkAlias(virtual, current);
        if (current instanceof FixedWithNextNode) {
            effects.deleteFixedNode((FixedWithNextNode) current);
        }
        deleted = true;
    }

    @Override
    public void replaceWithValue(ValueNode replacement) {
        effects.replaceAtUsages(current, closure.getScalarAlias(replacement));
        closure.addScalarAlias(current, replacement);
        deleted = true;
    }

    @Override
    public void delete() {
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
    public void createVirtualObject(VirtualObjectNode virtualObject, ValueNode[] entryState, List<MonitorIdNode> locks) {
        VirtualUtil.trace("{{%s}} ", current);
        if (!virtualObject.isAlive()) {
            effects.addFloatingNode(virtualObject, "newVirtualObject");
        }
        for (int i = 0; i < entryState.length; i++) {
            if (!(entryState[i] instanceof VirtualObjectNode)) {
                ObjectState v = closure.getObjectState(state, entryState[i]);
                if (v != null) {
                    entryState[i] = v.isVirtual() ? v.getVirtualObject() : v.getMaterializedValue();
                } else {
                    entryState[i] = closure.getScalarAlias(entryState[i]);
                }
            }
        }
        state.addObject(virtualObject, new ObjectState(virtualObject, entryState, EscapeState.Virtual, locks));
        closure.addAndMarkAlias(virtualObject, virtualObject);
        PartialEscapeClosure.METRIC_ALLOCATION_REMOVED.increment();
    }

    @Override
    public int getMaximumEntryCount() {
        return MaximumEscapeAnalysisArrayLength.getValue();
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
