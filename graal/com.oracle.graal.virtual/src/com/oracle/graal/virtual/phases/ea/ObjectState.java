/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.virtual.*;

/**
 * This class describes the state of a virtual object while iterating over the graph. It describes
 * the fields or array elements (called "entries") and the lock count if the object is still
 * virtual. If the object was materialized, it contains the current materialized value.
 */
public class ObjectState extends Virtualizable.State {

    final VirtualObjectNode virtual;

    private EscapeState state;
    private ValueNode[] entries;
    private ValueNode materializedValue;
    private LockState locks;

    public ObjectState(VirtualObjectNode virtual, ValueNode[] entries, EscapeState state, List<MonitorIdNode> locks) {
        this(virtual, entries, state, (LockState) null);
        for (int i = locks.size() - 1; i >= 0; i--) {
            this.locks = new LockState(locks.get(i), this.locks);
        }
    }

    public ObjectState(VirtualObjectNode virtual, ValueNode[] entries, EscapeState state, LockState locks) {
        this.virtual = virtual;
        this.entries = entries;
        this.state = state;
        this.locks = locks;
    }

    public ObjectState(VirtualObjectNode virtual, ValueNode materializedValue, EscapeState state, LockState locks) {
        this.virtual = virtual;
        this.materializedValue = materializedValue;
        this.state = state;
        this.locks = locks;
    }

    private ObjectState(ObjectState other) {
        virtual = other.virtual;
        entries = other.entries == null ? null : other.entries.clone();
        materializedValue = other.materializedValue;
        locks = other.locks;
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

    public void setEntry(int index, ValueNode value) {
        assert isVirtual();
        entries[index] = value;
    }

    public void escape(ValueNode materialized, EscapeState newState) {
        assert state == EscapeState.Virtual && newState == EscapeState.Materialized;
        state = newState;
        materializedValue = materialized;
        entries = null;
        assert !isVirtual();
    }

    @Override
    public ValueNode getMaterializedValue() {
        assert state == EscapeState.Materialized;
        return materializedValue;
    }

    public void updateMaterializedValue(ValueNode value) {
        assert !isVirtual();
        materializedValue = value;
    }

    @Override
    public void addLock(MonitorIdNode monitorId) {
        locks = new LockState(monitorId, locks);
    }

    @Override
    public MonitorIdNode removeLock() {
        try {
            return locks.monitorId;
        } finally {
            locks = locks.next;
        }
    }

    public LockState getLocks() {
        return locks;
    }

    public boolean hasLocks() {
        return locks != null;
    }

    public boolean locksEqual(ObjectState other) {
        LockState a = locks;
        LockState b = other.locks;
        while (a != null && b != null && a.monitorId == b.monitorId) {
            a = a.next;
            b = b.next;
        }
        return a == null && b == null;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder().append('{');
        if (locks != null) {
            str.append('l').append(locks).append(' ');
        }
        if (entries != null) {
            for (int i = 0; i < entries.length; i++) {
                str.append(virtual.entryName(i)).append('=').append(entries[i]).append(' ');
            }
        }
        if (materializedValue != null) {
            str.append("mat=").append(materializedValue);
        }

        return str.append('}').toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(entries);
        result = prime * result + (locks != null ? locks.monitorId.getLockDepth() : 0);
        result = prime * result + ((materializedValue == null) ? 0 : materializedValue.hashCode());
        result = prime * result + ((state == null) ? 0 : state.hashCode());
        result = prime * result + ((virtual == null) ? 0 : virtual.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ObjectState other = (ObjectState) obj;
        if (!Arrays.equals(entries, other.entries)) {
            return false;
        }
        if (!locksEqual(other)) {
            return false;
        }
        if (materializedValue == null) {
            if (other.materializedValue != null) {
                return false;
            }
        } else if (!materializedValue.equals(other.materializedValue)) {
            return false;
        }
        if (state != other.state) {
            return false;
        }
        assert virtual != null && other.virtual != null;
        if (!virtual.equals(other.virtual)) {
            return false;
        }
        return true;
    }
}
