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
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.ptx.*;

public class PTXMove {

    @Opcode("MOVE")
    public static class SpillMoveOp extends PTXLIRInstruction implements MoveOp {

        @Def({REG, STACK}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value input;

        public SpillMoveOp(Value result, Value input) {
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
        public Value getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class MoveToRegOp extends PTXLIRInstruction implements MoveOp {

        @Def({REG, HINT}) protected Value result;
        @Use({REG, STACK, CONST}) protected Value input;

        public MoveToRegOp(Value result, Value input) {
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
        public Value getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class MoveFromRegOp extends PTXLIRInstruction implements MoveOp {

        @Def({REG, STACK}) protected Value result;
        @Use({REG, CONST, HINT}) protected Value input;

        public MoveFromRegOp(Value result, Value input) {
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
        public Value getResult() {
            return result;
        }
    }

    public static class LoadOp extends PTXLIRInstruction {

        @Def({REG}) protected Value result;
        @Use({ADDR}) protected Value address;
        @State protected LIRFrameState state;

        public LoadOp(Value result, Value address, LIRFrameState state) {
            this.result = result;
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            load(tasm, masm, result, (PTXAddress) address, state);
        }
    }

    public static class StoreOp extends PTXLIRInstruction {

        @Use({ADDR}) protected Value address;
        @Use({REG, CONST}) protected Value input;
        @State protected LIRFrameState state;

        public StoreOp(Value address, Value input, LIRFrameState state) {
            this.address = address;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            store(tasm, masm, (PTXAddress) address, input, state);
        }
    }

    public static class LeaOp extends PTXLIRInstruction {

        @Def({REG}) protected Value result;
        @Use({ADDR, STACK, UNINITIALIZED}) protected Value address;

        public LeaOp(Value result, Value address) {
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            throw new InternalError("NYI");
        }
    }

    @Opcode("CAS")
    public static class CompareAndSwapOp extends PTXLIRInstruction {

        @Def protected Value result;
        @Use({ADDR}) protected Value address;
        @Use protected Value cmpValue;
        @Use protected Value newValue;

        public CompareAndSwapOp(Value result, Address address, Value cmpValue, Value newValue) {
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            compareAndSwap(tasm, masm, result, (Address) address, cmpValue, newValue);
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
            case Object:
                masm.mov_u64(asRegister(result), asRegister(input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("kind=" + result.getKind());
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
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @SuppressWarnings("unused")
    public static void load(TargetMethodAssembler tasm, PTXAssembler masm, Value result, PTXAddress loadAddr, LIRFrameState info) {
        Register a = asRegister(loadAddr.getBase());
        long immOff = loadAddr.getDisplacement();
        switch (loadAddr.getKind()) {
            case Int:
                masm.ld_global_s32(asRegister(result), a, immOff);
                break;
            case Object:
                masm.ld_global_u32(asRegister(result), a, immOff);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @SuppressWarnings("unused")
    public static void store(TargetMethodAssembler tasm, PTXAssembler masm, PTXAddress storeAddr, Value input, LIRFrameState info) {
        Register a = asRegister(storeAddr.getBase());
        long immOff = storeAddr.getDisplacement();
        if (isRegister(input)) {
            switch (storeAddr.getKind()) {
                case Int:
                    masm.st_global_s32(a, immOff, asRegister(input));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            Constant c = (Constant) input;
            switch (storeAddr.getKind()) {
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }

        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    @SuppressWarnings("unused")
    protected static void compareAndSwap(TargetMethodAssembler tasm, PTXAssembler masm, Value result, Address address, Value cmpValue, Value newValue) {
        throw new InternalError("NYI");
    }
}
