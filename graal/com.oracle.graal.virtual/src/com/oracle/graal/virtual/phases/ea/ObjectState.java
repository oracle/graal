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
import com.oracle.graal.nodes.virtual.*;

class ObjectState {

    public final VirtualObjectNode virtual;
    public ValueNode[] fieldState;
    public ValueNode materializedValue;
    public int lockCount;

    public ObjectState(VirtualObjectNode virtual, ValueNode[] fieldState, int lockCount) {
        this.virtual = virtual;
        this.fieldState = fieldState;
        this.lockCount = lockCount;
    }

    public ObjectState(VirtualObjectNode virtual, ValueNode materializedValue, int lockCount) {
        this.virtual = virtual;
        this.materializedValue = materializedValue;
        this.lockCount = lockCount;
    }

    private ObjectState(ObjectState other) {
        virtual = other.virtual;
        fieldState = other.fieldState == null ? null : other.fieldState.clone();
        materializedValue = other.materializedValue;
        lockCount = other.lockCount;
    }

    @Override
    public ObjectState clone() {
        return new ObjectState(this);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder().append('{');
        if (lockCount > 0) {
            str.append('l').append(lockCount).append(' ');
        }
        if (fieldState != null) {
            for (int i = 0; i < fieldState.length; i++) {
                str.append(virtual.fieldName(i)).append('=').append(fieldState[i]).append(' ');
            }
        }
        if (materializedValue != null) {
            str.append("mat=").append(materializedValue);
        }

        return str.append('}').toString();
    }
}
