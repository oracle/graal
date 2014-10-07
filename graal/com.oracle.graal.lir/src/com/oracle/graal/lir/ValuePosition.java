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
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRIntrospection.Values;

/**
 * Describes an operand slot for a {@link LIRInstruction}.
 */
public final class ValuePosition {

    /**
     * The {@linkplain Values offsets} to the fields of the containing element (either
     * {@link LIRInstruction} or {@link CompositeValue}).
     */
    private final Values values;
    /**
     * The index into {@link #values}.
     *
     * @see Values#getValue(Object, int)
     */
    private final int index;
    /**
     * The sub-index if {@link #index} points to a value array, otherwise {@link #NO_SUBINDEX}.
     *
     * @see Values#getDirectCount()
     * @see Values#getValueArray(Object, int)
     */
    private final int subIndex;
    /**
     * @see #getOuterPosition()
     */
    private final ValuePosition outerPosition;

    public static final int NO_SUBINDEX = -1;
    public static final ValuePosition ROOT_VALUE_POSITION = null;

    ValuePosition(Values values, int index, int subIndex, ValuePosition outerPosition) {
        this.values = values;
        this.index = index;
        this.subIndex = subIndex;
        this.outerPosition = outerPosition;
    }

    /**
     * @return True if the value denoted by this {@linkplain ValuePosition position} is part of a
     *         {@link CompositeValue}.
     */
    public boolean isCompositePosition() {
        return outerPosition != ROOT_VALUE_POSITION;
    }

    /**
     * @param inst The instruction this {@linkplain ValuePosition position} belongs to.
     * @return The value denoted by this {@linkplain ValuePosition position}.
     */
    public Value get(LIRInstruction inst) {
        if (isCompositePosition()) {
            CompositeValue compValue = (CompositeValue) outerPosition.get(inst);
            return compValue.getValueClass().getValue(compValue, this);
        }
        if (index < values.getDirectCount()) {
            return values.getValue(inst, index);
        }
        return values.getValueArray(inst, index)[subIndex];
    }

    /**
     * Sets the value denoted by this {@linkplain ValuePosition position}.
     *
     * @param inst The instruction this {@linkplain ValuePosition position} belongs to.
     */
    public void set(LIRInstruction inst, Value value) {
        if (isCompositePosition()) {
            CompositeValue compValue = (CompositeValue) outerPosition.get(inst);
            CompositeValue newCompValue = compValue.getValueClass().createUpdatedValue(compValue, this, value);
            outerPosition.set(inst, newCompValue);
        } else {
            if (index < values.getDirectCount()) {
                values.setValue(inst, index, value);
            } else {
                values.getValueArray(inst, index)[subIndex] = value;
            }
        }
    }

    int getSubIndex() {
        return subIndex;
    }

    int getIndex() {
        return index;
    }

    /**
     * @return The flags associated with the value denoted by this {@linkplain ValuePosition
     *         position}.
     */
    public EnumSet<OperandFlag> getFlags() {
        return values.getFlags(index);
    }

    /**
     * @return The {@link ValuePosition} of the containing {@link CompositeValue} if this value is
     *         part of a {@link CompositeValue}, otherwise {@link #ROOT_VALUE_POSITION}.
     *
     * @see #isCompositePosition()
     */
    public ValuePosition getOuterPosition() {
        return outerPosition;
    }

    @Override
    public String toString() {
        String str = "(" + index + (subIndex < 0 ? "" : "/" + subIndex) + ")";
        if (isCompositePosition()) {
            return outerPosition.toString() + "[" + str + "]";
        }
        return str;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + index;
        result = prime * result + subIndex;
        result = prime * result + ((outerPosition == null) ? 0 : outerPosition.hashCode());
        result = prime * result + ((values == null) ? 0 : values.hashCode());
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
        if (values == null) {
            if (other.values != null) {
                return false;
            }
        } else if (!values.equals(other.values)) {
            return false;
        }
        return true;
    }

}
