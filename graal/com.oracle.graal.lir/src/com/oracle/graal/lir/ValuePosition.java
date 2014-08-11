/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.LIRInstruction.*;

/**
 * Describes an operand slot for a {@link LIRInstructionClass}.
 */
public final class ValuePosition {

    private final OperandMode mode;
    private final int index;
    private final int subIndex;
    private final ValuePosition outerPosition;

    public static final int NO_SUBINDEX = -1;
    public static final ValuePosition ROOT_VALUE_POSITION = null;

    public ValuePosition(OperandMode mode, int index, int subIndex, ValuePosition outerPosition) {
        this.mode = mode;
        this.index = index;
        this.subIndex = subIndex;
        this.outerPosition = outerPosition;
    }

    public boolean isCompositePosition() {
        return outerPosition != ROOT_VALUE_POSITION;
    }

    public Value get(LIRInstruction inst) {
        if (isCompositePosition()) {
            CompositeValue compValue = (CompositeValue) outerPosition.get(inst);
            return compValue.getValueClass().getValue(compValue, this);
        }
        return inst.getLIRInstructionClass().getValue(inst, this);
    }

    public EnumSet<OperandFlag> getFlags(LIRInstruction inst) {
        if (isCompositePosition()) {
            CompositeValue compValue = (CompositeValue) outerPosition.get(inst);
            return compValue.getValueClass().getFlags(this);
        }
        return inst.getLIRInstructionClass().getFlags(this);
    }

    public void set(LIRInstruction inst, Value value) {
        if (isCompositePosition()) {
            CompositeValue compValue = (CompositeValue) outerPosition.get(inst);
            CompositeValue newCompValue = compValue.getValueClass().createUpdatedValue(compValue, this, value);
            outerPosition.set(inst, newCompValue);
        } else {
            inst.getLIRInstructionClass().setValue(inst, this, value);
        }
    }

    public int getSubIndex() {
        return subIndex;
    }

    public int getIndex() {
        return index;
    }

    public OperandMode getMode() {
        return mode;
    }

    public ValuePosition getSuperPosition() {
        return outerPosition;
    }

    @Override
    public String toString() {
        if (outerPosition == ROOT_VALUE_POSITION) {
            return mode.toString() + "(" + index + (subIndex < 0 ? "" : "/" + subIndex) + ")";
        }
        return outerPosition.toString() + "[" + mode.toString() + "(" + index + (subIndex < 0 ? "" : "/" + subIndex) + ")]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + index;
        result = prime * result + ((mode == null) ? 0 : mode.hashCode());
        result = prime * result + subIndex;
        result = prime * result + ((outerPosition == null) ? 0 : outerPosition.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ValuePosition other = (ValuePosition) obj;
        if (index != other.index) {
            return false;
        }
        if (mode != other.mode) {
            return false;
        }
        if (subIndex != other.subIndex) {
            return false;
        }
        if (outerPosition == null) {
            if (other.outerPosition != null) {
                return false;
            }
        } else if (!outerPosition.equals(other.outerPosition)) {
            return false;
        }
        return true;
    }

}
