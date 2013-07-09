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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.*;

/**
 * Implementation of move instructions.
 */
public class HSAILMove {

    // Stack size in bytes (used to keep track of spilling).
    static int maxDatatypeSize;
    // Maximum stack offset used by a store operation.
    static long maxStackOffset = 0;

    public static long upperBoundStackSize() {
        return maxStackOffset + maxDatatypeSize;
    }

    @Opcode("MOVE")
    public static class SpillMoveOp extends HSAILLIRInstruction implements MoveOp {

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public SpillMoveOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            move(tasm, masm, getResult(), getInput());
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class MoveToRegOp extends HSAILLIRInstruction implements MoveOp {

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public MoveToRegOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            move(tasm, masm, getResult(), getInput());
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class MoveFromRegOp extends HSAILLIRInstruction implements MoveOp {

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, CONST, HINT}) protected Value input;

        public MoveFromRegOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            move(tasm, masm, getResult(), getInput());
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    public static class LoadOp extends HSAILLIRInstruction {

        @SuppressWarnings("unused") private final Kind kind;
        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected HSAILAddressValue address;
        @State protected LIRFrameState state;

        public LoadOp(Kind kind, AllocatableValue result, HSAILAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            HSAILAddress addr = address.toAddress();
            masm.emitLoad(result, addr);
        }
    }

    public static class StoreOp extends HSAILLIRInstruction {

        @SuppressWarnings("unused") private final Kind kind;
        @Use({COMPOSITE}) protected HSAILAddressValue address;
        @Use({REG}) protected AllocatableValue input;
        @State protected LIRFrameState state;

        public StoreOp(Kind kind, HSAILAddressValue address, AllocatableValue input, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            assert isRegister(input);
            HSAILAddress addr = address.toAddress();
            masm.emitStore(input, addr);
        }
    }

    public static class LeaOp extends HSAILLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected HSAILAddressValue address;

        public LeaOp(AllocatableValue result, HSAILAddressValue address) {
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            throw new InternalError("NYI");
        }
    }

    public static class StackLeaOp extends HSAILLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected StackSlot slot;

        public StackLeaOp(AllocatableValue result, StackSlot slot) {
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            throw new InternalError("NYI");
        }
    }

    @Opcode("CAS")
    public static class CompareAndSwapOp extends HSAILLIRInstruction {

        @Def protected AllocatableValue result;
        @Use({COMPOSITE}) protected HSAILAddressValue address;
        @Use protected AllocatableValue cmpValue;
        @Use protected AllocatableValue newValue;

        public CompareAndSwapOp(AllocatableValue result, HSAILAddressValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            compareAndSwap(tasm, masm, result, address, cmpValue, newValue);
        }
    }

    public static class NullCheckOp extends HSAILLIRInstruction {

        @Use protected Value input;
        @State protected LIRFrameState state;

        public NullCheckOp(Variable input, LIRFrameState state) {
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, HSAILAssembler masm) {
            Debug.log("NullCheckOp unimplemented");
        }
    }

    @SuppressWarnings("unused")
    public static void move(TargetMethodAssembler tasm, HSAILAssembler masm, Value result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                masm.emitMov(result, input);
            } else if (isStackSlot(result)) {
                masm.emitSpillStore(input, result);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                masm.emitSpillLoad(result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            if (isRegister(result)) {
                masm.emitMov(result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    @SuppressWarnings("unused")
    protected static void compareAndSwap(TargetMethodAssembler tasm, HSAILAssembler masm, AllocatableValue result, HSAILAddressValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
        throw new InternalError("NYI");
    }
}
