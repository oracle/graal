/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.hsail;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Implementation of control flow instructions.
 */
public class HSAILControlFlow {

    public static class ReturnOp extends HSAILLIRInstruction {

        @Use({REG, ILLEGAL}) protected Value x;

        public ReturnOp(Value x) {
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            if (tasm.frameContext != null) {
                tasm.frameContext.leave(tasm);
            }
            masm.exit();
        }
    }

    public static class CompareBranchOp extends HSAILLIRInstruction implements StandardOp.BranchOp {

        @Opcode protected final HSAILCompare opcode;
        @Use({REG, CONST}) protected Value x;
        @Use({REG, CONST}) protected Value y;
        @Def({REG}) protected Value z;
        protected Condition condition;
        protected LabelRef destination;
        protected boolean unordered = false;
        @Def({REG}) protected Value result;

        public CompareBranchOp(HSAILCompare opcode, Condition condition, Value x, Value y, Value z, Value result, LabelRef destination) {
            this.condition = condition;
            this.opcode = opcode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.result = result;
            this.destination = destination;
        }

        @Override
        public LabelRef destination() {
            return destination;
        }

        @Override
        public void negate(LabelRef newDestination) {
            destination = newDestination;
            condition = condition.negate();
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            HSAILCompare.emit(tasm, masm, condition, x, y, z, unordered);
            masm.cbr(masm.nameOf(destination.label()));
        }
    }

    public static class FloatCompareBranchOp extends CompareBranchOp {

        public FloatCompareBranchOp(HSAILCompare opcode, Condition condition, Value x, Value y, Value z, Value result, LabelRef destination, boolean unordered) {
            super(opcode, condition, x, y, z, result, destination);
            this.unordered = unordered;
        }

        @Override
        public void negate(LabelRef newDestination) {
            destination = newDestination;
            condition = condition.negate();
            unordered = !unordered;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            HSAILCompare.emit(tasm, masm, condition, x, y, z, unordered);
            masm.cbr(masm.nameOf(destination.label()));
        }
    }

    public static class DoubleCompareBranchOp extends CompareBranchOp {

        public DoubleCompareBranchOp(HSAILCompare opcode, Condition condition, Value x, Value y, Value z, Value result, LabelRef destination, boolean unordered) {
            super(opcode, condition, x, y, z, result, destination);
            this.unordered = unordered;
        }

        @Override
        public void negate(LabelRef newDestination) {
            destination = newDestination;
            condition = condition.negate();
            unordered = !unordered;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            HSAILCompare.emit(tasm, masm, condition, x, y, z, unordered);
            masm.cbr(masm.nameOf(destination.label()));
        }
    }

    public static class BranchOp extends HSAILLIRInstruction implements StandardOp.BranchOp {

        protected Condition condition;
        protected LabelRef destination;
        @Def({REG}) protected Value result;

        public BranchOp(Condition condition, Value result, LabelRef destination) {
            this.condition = condition;
            this.destination = destination;
            this.result = result;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            masm.cbr(masm.nameOf(destination.label()));
        }

        @Override
        public LabelRef destination() {
            return destination;
        }

        @Override
        public void negate(LabelRef newDestination) {
            destination = newDestination;
            condition = condition.negate();
        }
    }

    public static class FloatBranchOp extends BranchOp {

        public FloatBranchOp(Condition condition, Value result, LabelRef destination) {
            super(condition, result, destination);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            masm.cbr(masm.nameOf(destination.label()));
        }
    }

    public static class CondMoveOp extends HSAILLIRInstruction {

        @Opcode protected final HSAILCompare opcode;
        @Def({REG, HINT}) protected Value result;
        @Alive({REG}) protected Value trueValue;
        @Use({REG, STACK, CONST}) protected Value falseValue;
        @Use({REG, STACK, CONST}) protected Value left;
        @Use({REG, STACK, CONST}) protected Value right;
        protected final Condition condition;

        public CondMoveOp(HSAILCompare opcode, Variable left, Variable right, Variable result, Condition condition, Variable trueValue, Value falseValue) {
            this.opcode = opcode;
            this.result = result;
            this.left = left;
            this.right = right;
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            HSAILCompare.emit(tasm, masm, condition, left, right, right, false);
            cmove(tasm, masm, result, false, trueValue, falseValue);
        }
    }

    public static class FloatCondMoveOp extends CondMoveOp {

        private final boolean unorderedIsTrue;

        public FloatCondMoveOp(HSAILCompare opcode, Variable left, Variable right, Variable result, Condition condition, boolean unorderedIsTrue, Variable trueValue, Value falseValue) {
            super(opcode, left, right, result, condition, trueValue, falseValue);
            this.unorderedIsTrue = unorderedIsTrue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            HSAILCompare.emit(tasm, masm, condition, left, right, right, unorderedIsTrue);
            cmove(tasm, masm, result, false, trueValue, falseValue);
        }
    }

    @SuppressWarnings("unused")
    private static void cmove(TargetMethodAssembler tasm, HSAILAssembler masm, Value result, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        // Check that we don't overwrite an input operand before it is used.
        assert (result.getKind() == trueValue.getKind() && result.getKind() == falseValue.getKind());
        int width;
        switch (result.getKind()) {
        /**
         * We don't need to pass the cond to the assembler. We will always use $c0 as the control
         * register.
         */
            case Float:
            case Int:
                width = 32;
                break;
            case Double:
            case Long:
                width = 64;
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        masm.cmovCommon(result, trueValue, falseValue, width);
    }
}
