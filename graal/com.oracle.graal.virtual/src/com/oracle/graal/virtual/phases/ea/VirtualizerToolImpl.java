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

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

class VirtualizerToolImpl implements VirtualizerTool, CanonicalizerTool {

    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider constantReflection;
    private final PartialEscapeClosure<?> closure;

    VirtualizerToolImpl(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, PartialEscapeClosure<?> closure) {
        this.metaAccess = metaAccess;
        this.constantReflection = constantReflection;
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
    public ValueNode getAlias(ValueNode value) {
        return closure.getAliasAndResolve(state, value);
    }

    public ValueNode getEntry(VirtualObjectNode virtualObject, int index) {
        return state.getObjectState(virtualObject).getEntry(index);
    }

    @Override
    public void setVirtualEntry(VirtualObjectNode virtual, int index, ValueNode value, boolean unsafe) {
        ObjectState obj = state.getObjectState(virtual);
        assert obj.isVirtual() : "not virtual: " + obj;
        ValueNode newValue;
        if (value == null) {
            newValue = null;
        } else {
            newValue = closure.getAliasAndResolve(state, value);
            assert unsafe || obj.getEntry(index) == null || obj.getEntry(index).getKind() == newValue.getKind() || (isObjectEntry(obj.getEntry(index)) && isObjectEntry(newValue));
        }
        state.setEntry(virtual.getObjectId(), index, newValue);
    }

    public void setEnsureVirtualized(VirtualObjectNode virtualObject, boolean ensureVirtualized) {
        int id = virtualObject.getObjectId();
        state.setEnsureVirtualized(id, ensureVirtualized);
    }

    public boolean getEnsureVirtualized(VirtualObjectNode virtualObject) {
        return state.getObjectState(virtualObject).getEnsureVirtualized();
    }

    private static boolean isObjectEntry(ValueNode value) {
        return value.getKind() == Kind.Object || value instanceof VirtualObjectNode;
    }

    @Override
    public void replaceWithVirtual(VirtualObjectNode virtual) {
        closure.addAndMarkAlias(virtual, current);
        effects.deleteNode(current);
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
        effects.deleteNode(current);
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
    public void createVirtualObject(VirtualObjectNode virtualObject, ValueNode[] entryState, List<MonitorIdNode> locks, boolean ensureVirtualized) {
        VirtualUtil.trace("{{%s}} ", current);
        if (!virtualObject.isAlive()) {
            effects.addFloatingNode(virtualObject, "newVirtualObject");
        }
        for (int i = 0; i < entryState.length; i++) {
            ValueNode entry = entryState[i];
            entryState[i] = entry instanceof VirtualObjectNode ? entry : closure.getAliasAndResolve(state, entry);
        }
        int id = virtualObject.getObjectId();
        if (id == -1) {
            id = closure.virtualObjects.size();
            closure.virtualObjects.add(virtualObject);
            virtualObject.setObjectId(id);
        }
        state.addObject(id, new ObjectState(entryState, locks, ensureVirtualized));
        closure.addAndMarkAlias(virtualObject, virtualObject);
        PartialEscapeClosure.METRIC_ALLOCATION_REMOVED.increment();
    }

    @Override
    public int getMaximumEntryCount() {
        return MaximumEscapeAnalysisArrayLength.getValue();
    }

    @Override
    public void replaceWith(ValueNode node) {
        if (node instanceof VirtualObjectNode) {
            replaceWithVirtual((VirtualObjectNode) node);
        } else {
            replaceWithValue(node);
        }
    }

    public void addLock(VirtualObjectNode virtualObject, MonitorIdNode monitorId) {
        int id = virtualObject.getObjectId();
        state.addLock(id, monitorId);
    }

    public MonitorIdNode removeLock(VirtualObjectNode virtualObject) {
        int id = virtualObject.getObjectId();
        return state.removeLock(id);
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public ConstantReflectionProvider getConstantReflection() {
        return constantReflection;
    }

    public boolean canonicalizeReads() {
        return false;
    }

    @Override
    public boolean allUsagesAvailable() {
        return true;
    }
}
