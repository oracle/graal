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
package com.oracle.graal.lir.ptx;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.*;

public class PTXMove {

    @Opcode("MOVE")
    public static class SpillMoveOp extends PTXLIRInstruction implements MoveOp {

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public SpillMoveOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
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
    public static class MoveToRegOp extends PTXLIRInstruction implements MoveOp {

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public MoveToRegOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
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
    public static class MoveFromRegOp extends PTXLIRInstruction implements MoveOp {

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, CONST, HINT}) protected Value input;

        public MoveFromRegOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
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

    public static class LoadOp extends PTXLIRInstruction {

        private final Kind kind;
        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @State protected LIRFrameState state;

        public LoadOp(Kind kind, AllocatableValue result, PTXAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            PTXAddress addr = address.toAddress();
            switch (kind) {
                case Byte:
                    masm.ld_global_s8(asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Short:
                    masm.ld_global_s16(asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Char:
                    masm.ld_global_u16(asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Int:
                    masm.ld_global_s32(asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Long:
                    masm.ld_global_s64(asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Float:
                    masm.ld_global_f32(asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Double:
                    masm.ld_global_f64(asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Object:
                    masm.ld_global_u32(asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class StoreOp extends PTXLIRInstruction {

        private final Kind kind;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @Use({REG}) protected AllocatableValue input;
        @State protected LIRFrameState state;

        public StoreOp(Kind kind, PTXAddressValue address, AllocatableValue input, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            assert isRegister(input);
            PTXAddress addr = address.toAddress();
            switch (kind) {
                case Byte:
                    masm.st_global_s8(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Short:
                    masm.st_global_s8(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Int:
                    masm.st_global_s32(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Long:
                    masm.st_global_s64(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Float:
                    masm.st_global_f32(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Double:
                    masm.st_global_f64(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Object:
                    masm.st_global_s32(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + address.getKind());
            }
        }
    }

    public static class LeaOp extends PTXLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected PTXAddressValue address;

        public LeaOp(AllocatableValue result, PTXAddressValue address) {
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            throw new InternalError("NYI");
        }
    }

    public static class StackLeaOp extends PTXLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected StackSlot slot;

        public StackLeaOp(AllocatableValue result, StackSlot slot) {
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            throw new InternalError("NYI");
        }
    }

    @Opcode("CAS")
    public static class CompareAndSwapOp extends PTXLIRInstruction {

        @Def protected AllocatableValue result;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @Use protected AllocatableValue cmpValue;
        @Use protected AllocatableValue newValue;

        public CompareAndSwapOp(AllocatableValue result, PTXAddressValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            compareAndSwap(tasm, masm, result, address, cmpValue, newValue);
        }
    }

    public static void move(TargetMethodAssembler tasm, PTXAssembler masm, Value result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(masm, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            if (isRegister(result)) {
                const2reg(tasm, masm, result, (Constant) input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void reg2reg(PTXAssembler masm, Value result, Value input) {
        if (asRegister(input).equals(asRegister(result))) {
            return;
        }
        switch (input.getKind()) {
            case Int:
                masm.mov_s32(asRegister(result), asRegister(input));
                break;
            case Long:
                masm.mov_s64(asRegister(result), asRegister(input));
                break;
            case Float:
                masm.mov_f32(asRegister(result), asRegister(input));
                break;
            case Double:
                masm.mov_f64(asRegister(result), asRegister(input));
                break;
            case Object:
                masm.mov_u64(asRegister(result), asRegister(input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + input.getKind());
        }
    }

    private static void const2reg(TargetMethodAssembler tasm, PTXAssembler masm, Value result, Constant input) {
        switch (input.getKind().getStackKind()) {
            case Int:
                if (tasm.runtime.needsDataPatch(input)) {
                    tasm.recordDataReferenceInCode(input, 0, true);
                }
                masm.mov_s32(asRegister(result), input.asInt());
                break;
            case Long:
                if (tasm.runtime.needsDataPatch(input)) {
                    tasm.recordDataReferenceInCode(input, 0, true);
                }
                masm.mov_s64(asRegister(result), input.asLong());
                break;
            case Object:
                if (input.isNull()) {
                    masm.mov_u64(asRegister(result), 0x0L);
                } else if (tasm.target.inlineObjects) {
                    tasm.recordDataReferenceInCode(input, 0, true);
                    masm.mov_u64(asRegister(result), 0xDEADDEADDEADDEADL);
                } else {
                    masm.mov_u64(asRegister(result), tasm.recordDataReferenceInCode(input, 0, false));
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + input.getKind());
        }
    }

    @SuppressWarnings("unused")
    protected static void compareAndSwap(TargetMethodAssembler tasm, PTXAssembler masm, AllocatableValue result, PTXAddressValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
        throw new InternalError("NYI");
    }
}
