/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;

/**
 * Allocation state holding a single, concrete {@link Value} (wrapped with {@link RAValue}), also
 * accompanied by {@link RAVInstruction instruction} and {@link BasicBlock block} where it was
 * created.
 */
public class ValueAllocationState extends AllocationState implements Cloneable {
    protected RAValue value;
    protected RAVInstruction.Base source;
    protected BasicBlock<?> block;

    public ValueAllocationState(RAValue raValue, RAVInstruction.Base source, BasicBlock<?> block) {
        var v = raValue.getValue();
        if (ValueUtil.isRegister(v) || LIRValueUtil.isVariable(v) || LIRValueUtil.isConstantValue(v) || LIRValueUtil.isStackSlotValue(v) || Value.ILLEGAL.equals(v)) {
            // Here, we make sure that no new value class is used here, without consideration.

            // StackSlot, RegisterValue is present in start block in label as predefined argument
            // VirtualStackSlot is used for RESTORE_REGISTERS and SAVE_REGISTERS
            // ConstantValue act as Variable
            this.value = raValue;
            this.source = source;
            this.block = block;
        } else {
            throw GraalError.shouldNotReachHere("Invalid type of value used " + v);
        }
    }

    public ValueAllocationState(ValueAllocationState other) {
        this.value = other.getRAValue();
        this.source = other.getSource();
        this.block = other.getBlock();
    }

    /**
     * Create an illegal value allocation state, used as a substitute for
     * {@link UnknownAllocationState unknown} when creating a {@link ConflictedAllocationState
     * conflict}.
     *
     * @return instance of {@link ValueAllocationState} holding {@link Value#ILLEGAL}.
     */
    public static ValueAllocationState createUndefined(BasicBlock<?> block) {
        return new ValueAllocationState(new RAValue(Value.ILLEGAL), null, block);
    }

    public Value getValue() {
        return value.getValue();
    }

    public RAValue getRAValue() {
        return value;
    }

    public RAVInstruction.Base getSource() {
        return source;
    }

    public BasicBlock<?> getBlock() {
        return block;
    }

    /**
     * Meet a state from predecessor block, if it's {@link ValueAllocationState} and contents are
     * equal, then same state is returned, otherwise a {@link ConflictedAllocationState conflict} is
     * created between said states.
     *
     * @param other The other state coming from a predecessor edge
     * @param otherBlock Where the other state is coming from
     * @param currBlock Where the current state is coming from
     * @return {@link ValueAllocationState} if their contents are equal, otherwise
     *         {@link ConflictedAllocationState}.
     */
    @Override
    public AllocationState meet(AllocationState other, BasicBlock<?> otherBlock, BasicBlock<?> currBlock) {
        if (other.isUnknown()) {
            // Unknown is coming from different predecessor where this location
            // is undefined, meaning this value is not always accessible in the successor
            // and thus conflict is created.
            return new ConflictedAllocationState(createUndefined(otherBlock), this);
        }

        if (other.isConflicted()) {
            var oldConfState = (ConflictedAllocationState) other;
            var newConfState = new ConflictedAllocationState(oldConfState.conflictedStates);
            newConfState.addConflictedValue(this);
            return newConfState;
        }

        var otherValueAllocState = (ValueAllocationState) other;
        if (!this.value.equals(otherValueAllocState.getRAValue())) {
            // Does not take kind into account.
            return new ConflictedAllocationState(this, otherValueAllocState);
        }

        return this;
    }

    @Override
    public boolean equals(AllocationState other) {
        return other instanceof ValueAllocationState otherVal && this.value.equals(otherVal.getRAValue());
    }

    @Override
    public ValueAllocationState clone() {
        return new ValueAllocationState(this);
    }

    @Override
    public String toString() {
        if (isUndefinedFromBlock()) {
            return "Value {undefined from " + block + "}";
        }

        return "Value {" + this.value + "}";
    }

    @Override
    public int hashCode() {
        return this.value.hashCode();
    }

    public boolean isUndefinedFromBlock() {
        return Value.ILLEGAL.equals(this.value.getValue()) && source == null;
    }
}
