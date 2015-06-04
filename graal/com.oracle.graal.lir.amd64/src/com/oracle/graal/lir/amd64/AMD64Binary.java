/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import com.oracle.jvmci.meta.AllocatableValue;
import com.oracle.jvmci.meta.Value;
import com.oracle.jvmci.meta.JavaConstant;

import static com.oracle.jvmci.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.jvmci.code.CompilationResult.DataSectionReference;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.ImplicitNullCheck;
import com.oracle.graal.lir.asm.*;

/**
 * AMD64 LIR instructions that have two inputs and one output.
 */
public class AMD64Binary {

    /**
     * Instruction that has two {@link AllocatableValue} operands.
     */
    public static class Op extends AMD64LIRInstruction {
        public static final LIRInstructionClass<Op> TYPE = LIRInstructionClass.create(Op.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        @Alive({REG, STACK}) protected AllocatableValue y;

        public Op(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            if (isRegister(y)) {
                opcode.emit(masm, size, asRegister(result), asRegister(y));
            } else {
                assert isStackSlot(y);
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(y));
            }
        }
    }

    /**
     * Commutative instruction that has two {@link AllocatableValue} operands.
     */
    public static class CommutativeOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CommutativeOp> TYPE = LIRInstructionClass.create(CommutativeOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;

        public CommutativeOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AllocatableValue input;
            if (sameRegister(result, y)) {
                input = x;
            } else {
                AMD64Move.move(crb, masm, result, x);
                input = y;
            }

            if (isRegister(input)) {
                opcode.emit(masm, size, asRegister(result), asRegister(input));
            } else {
                assert isStackSlot(input);
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }

    /**
     * Instruction that has one {@link AllocatableValue} operand and one 32-bit immediate operand.
     */
    public static class ConstOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ConstOp> TYPE = LIRInstructionClass.create(ConstOp.class);

        @Opcode private final AMD64MIOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        private final int y;

        public ConstOp(AMD64BinaryArithmetic opcode, OperandSize size, AllocatableValue result, AllocatableValue x, int y) {
            this(opcode.getMIOpcode(size, NumUtil.isByte(y)), size, result, x, y);
        }

        public ConstOp(AMD64MIOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, int y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            opcode.emit(masm, size, asRegister(result), y);
        }
    }

    /**
     * Instruction that has one {@link AllocatableValue} operand and one
     * {@link DataSectionReference} operand.
     */
    public static class DataOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<DataOp> TYPE = LIRInstructionClass.create(DataOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        private final JavaConstant y;

        private final int alignment;

        public DataOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, JavaConstant y) {
            this(opcode, size, result, x, y, y.getKind().getByteCount());
        }

        public DataOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, JavaConstant y, int alignment) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;

            this.alignment = alignment;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(y, alignment));
        }
    }

    /**
     * Instruction that has one {@link AllocatableValue} operand and one {@link AMD64AddressValue
     * memory} operand.
     */
    public static class MemoryOp extends AMD64LIRInstruction implements ImplicitNullCheck {
        public static final LIRInstructionClass<MemoryOp> TYPE = LIRInstructionClass.create(MemoryOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        @Alive({COMPOSITE}) protected AMD64AddressValue y;

        @State protected LIRFrameState state;

        public MemoryOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AMD64AddressValue y, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;

            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            opcode.emit(masm, size, asRegister(result), y.toAddress());
        }

        @Override
        public void verify() {
            super.verify();
            assert differentRegisters(result, y) || sameRegister(x, y);
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && y.isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    /**
     * Instruction with a separate result operand, one {@link AllocatableValue} input and one 32-bit
     * immediate input.
     */
    public static class RMIOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<RMIOp> TYPE = LIRInstructionClass.create(RMIOp.class);

        @Opcode private final AMD64RMIOp opcode;
        private final OperandSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        private final int y;

        public RMIOp(AMD64RMIOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, int y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(x)) {
                opcode.emit(masm, size, asRegister(result), asRegister(x), y);
            } else {
                assert isStackSlot(x);
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(x), y);
            }
        }
    }
}
