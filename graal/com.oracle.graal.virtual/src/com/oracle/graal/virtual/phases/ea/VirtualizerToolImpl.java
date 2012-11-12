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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

class VirtualizerToolImpl implements VirtualizerTool {

    private final GraphEffectList effects;
    private final NodeBitMap usages;
    private final MetaAccessProvider metaAccess;

    VirtualizerToolImpl(GraphEffectList effects, NodeBitMap usages, MetaAccessProvider metaAccess) {
        this.effects = effects;
        this.usages = usages;
        this.metaAccess = metaAccess;
    }

    private boolean deleted;
    private boolean customAction;
    private BlockState state;
    private ValueNode current;

    @Override
    public MetaAccessProvider getMetaAccessProvider() {
        return metaAccess;
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

    @Override
    public VirtualObjectNode getVirtualState(ValueNode value) {
        ObjectState obj = state.getObjectState(value);
        return obj != null && obj.isVirtual() ? obj.virtual : null;
    }

    @Override
    public ValueNode getVirtualEntry(VirtualObjectNode virtual, int index) {
        ObjectState obj = state.getObjectState(virtual);
        assert obj != null && obj.isVirtual() : "not virtual: " + obj;
        ValueNode result = obj.getEntry(index);
        ValueNode materialized = getMaterializedValue(result);
        return materialized != null ? materialized : result;
    }

    @Override
    public void setVirtualEntry(VirtualObjectNode virtual, int index, ValueNode value) {
        ObjectState obj = state.getObjectState(virtual);
        assert obj != null && obj.isVirtual() : "not virtual: " + obj;
        if (getVirtualState(value) == null) {
            ValueNode materialized = getMaterializedValue(value);
            if (materialized != null) {
                obj.setEntry(index, materialized);
            } else {
                obj.setEntry(index, getReplacedValue(value));
            }
        } else {
            obj.setEntry(index, value);
        }
    }

    @Override
    public int getVirtualLockCount(VirtualObjectNode virtual) {
        ObjectState obj = state.getObjectState(virtual);
        assert obj != null && obj.isVirtual() : "not virtual: " + obj;
        return obj.getLockCount();
    }

    @Override
    public void setVirtualLockCount(VirtualObjectNode virtual, int lockCount) {
        ObjectState obj = state.getObjectState(virtual);
        assert obj != null && obj.isVirtual() : "not virtual: " + obj;
        obj.setLockCount(lockCount);
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
}
