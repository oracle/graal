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

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.virtual.*;

/**
 * This class describes the state of a virtual object while iterating over the graph.
 * It describes the fields or array elements (called "entries") and the lock count if the object is still virtual.
 * If the object was materialized, it contains the current materialized value.
 */
class ObjectState extends Virtualizable.State {

    public final VirtualObjectNode virtual;

    private EscapeState state;
    private ValueNode[] entries;
    private ValueNode materializedValue;
    private int lockCount;

    public ObjectState(VirtualObjectNode virtual, ValueNode[] entries, EscapeState state, int lockCount) {
        this.virtual = virtual;
        this.entries = entries;
        this.state = state;
        this.lockCount = lockCount;
    }

    public ObjectState(VirtualObjectNode virtual, ValueNode materializedValue, EscapeState state, int lockCount) {
        this.virtual = virtual;
        this.materializedValue = materializedValue;
        this.state = state;
        this.lockCount = lockCount;
    }

    private ObjectState(ObjectState other) {
        virtual = other.virtual;
        entries = other.entries == null ? null : other.entries.clone();
        materializedValue = other.materializedValue;
        lockCount = other.lockCount;
        state = other.state;
    }

    public ObjectState cloneState() {
        return new ObjectState(this);
    }

    @Override
    public EscapeState getState() {
        return state;
    }

    @Override
    public VirtualObjectNode getVirtualObject() {
        return virtual;
    }

    public boolean isVirtual() {
        return state == EscapeState.Virtual;
    }

    public ValueNode[] getEntries() {
        assert isVirtual() && entries != null;
        return entries;
    }

    @Override
    public ValueNode getEntry(int index) {
        assert isVirtual();
        return entries[index];
    }

    @Override
    public void setEntry(int index, ValueNode value) {
        assert isVirtual();
        entries[index] = value;
    }

    public void escape(ValueNode materialized, EscapeState newState) {
        assert state == EscapeState.Virtual || (state == EscapeState.ThreadLocal && newState == EscapeState.Global);
        state = newState;
        materializedValue = materialized;
        entries = null;
        assert !isVirtual();
    }

    @Override
    public ValueNode getMaterializedValue() {
        assert state == EscapeState.ThreadLocal || state == EscapeState.Global;
        return materializedValue;
    }

    public void updateMaterializedValue(ValueNode value) {
        assert !isVirtual();
        materializedValue = value;
    }

    @Override
    public int getLockCount() {
        return lockCount;
    }

    @Override
    public void setLockCount(int lockCount) {
        this.lockCount = lockCount;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder().append('{');
        if (lockCount > 0) {
            str.append('l').append(lockCount).append(' ');
        }
        if (entries != null) {
            for (int i = 0; i < entries.length; i++) {
                str.append(virtual.fieldName(i)).append('=').append(entries[i]).append(' ');
            }
        }
        if (materializedValue != null) {
            str.append("mat=").append(materializedValue);
        }

        return str.append('}').toString();
    }
}
