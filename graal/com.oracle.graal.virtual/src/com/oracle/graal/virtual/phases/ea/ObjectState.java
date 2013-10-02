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

import java.util.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.Virtualizable.EscapeState;
import com.oracle.graal.nodes.virtual.*;

/**
 * This class describes the state of a virtual object while iterating over the graph. It describes
 * the fields or array elements (called "entries") and the lock count if the object is still
 * virtual. If the object was materialized, it contains the current materialized value.
 */
public class ObjectState extends Virtualizable.State {

    private static final int[] EMPTY_INT_ARRAY = new int[0];

    public static final class LockState {

        public final int depth;
        public final LockState next;

        private LockState(int depth, LockState next) {
            this.depth = depth;
            this.next = next;
        }

        @Override
        public String toString() {
            return next == null ? String.valueOf(depth) : depth + "," + next;
        }

        public static boolean equals(LockState a, LockState b) {
            if ((a == null) != (b == null)) {
                return false;
            }
            if (a != null) {
                if (a.depth != b.depth) {
                    return false;
                }
                return equals(a.next, b.next);
            }
            return true;
        }
    }

    final VirtualObjectNode virtual;

    private EscapeState state;
    private ValueNode[] entries;
    private ValueNode materializedValue;
    private LockState locks;

    public ObjectState(VirtualObjectNode virtual, ValueNode[] entries, EscapeState state, int[] locks) {
        this.virtual = virtual;
        this.entries = entries;
        this.state = state;
        if (locks == null) {
            this.locks = null;
        } else {
            for (int i = locks.length - 1; i >= 0; i--) {
                this.locks = new LockState(locks[i], this.locks);
            }
        }
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
    public void addLock(int depth) {
        locks = new LockState(depth, locks);
    }

    @Override
    public int removeLock() {
        try {
            return locks.depth;
        } finally {
            locks = locks.next;
        }
    }

    public int[] getLocks() {
        if (locks == null) {
            return EMPTY_INT_ARRAY;
        }
        int cnt = 0;
        LockState current = locks;
        while (current != null) {
            cnt++;
            current = current.next;
        }
        int[] result = new int[cnt];
        current = locks;
        cnt = 0;
        while (current != null) {
            result[cnt++] = current.depth;
            current = current.next;
        }
        return result;
    }

    public boolean hasLocks() {
        return locks != null;
    }

    public boolean locksEqual(ObjectState other) {
        return LockState.equals(locks, other.locks);
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
        result = prime * result + (locks != null ? locks.depth : 0);
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
        if (!LockState.equals(locks, other.locks)) {
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
