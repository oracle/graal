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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

public class PTXMemOp {

    // Load operation from .global state space
    @Opcode("LOAD")
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

    // Store operation from .global state space
    @Opcode("STORE")
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
                    masm.st_global_u64(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + address.getKind());
            }
        }
    }

    // Load operation from .param state space
    @Opcode("LOAD")
    public static class LoadParamOp extends PTXLIRInstruction {

        private final Kind kind;
        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @State protected LIRFrameState state;

        public LoadParamOp(Kind kind, AllocatableValue result, PTXAddressValue address, LIRFrameState state) {
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
                    masm.ld_from_state_space(".param.s8", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Short:
                    masm.ld_from_state_space(".param.s16", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Char:
                    masm.ld_from_state_space(".param.u16", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Int:
                    masm.ld_from_state_space(".param.s32", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Long:
                    masm.ld_from_state_space(".param.s64", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Float:
                    masm.ld_from_state_space(".param.f32", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Double:
                    masm.ld_from_state_space(".param.f64", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Object:
                    masm.ld_from_state_space(".param.u64", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    // Load contents of return value pointer from return argument in
    // .param state space
    @Opcode("LOAD_RET_ADDR")
    public static class LoadReturnAddrOp extends PTXLIRInstruction {

        private final Kind kind;
        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @State protected LIRFrameState state;

        public LoadReturnAddrOp(Kind kind, AllocatableValue result, PTXAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            PTXAddress addr = address.toAddress();
            switch (kind) {
                case Int:
                    masm.ld_return_address("u32", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Long:
                    masm.ld_return_address("u64", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Float:
                    masm.ld_return_address("f32", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                case Double:
                    masm.ld_return_address("f64", asRegister(result), addr.getBase(), addr.getDisplacement());
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    // Store operation from .global state space
    @Opcode("STORE_RETURN_VALUE")
    public static class StoreReturnValOp extends PTXLIRInstruction {

        private final Kind kind;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @Use({REG}) protected AllocatableValue input;
        @State protected LIRFrameState state;

        public StoreReturnValOp(Kind kind, PTXAddressValue address, AllocatableValue input, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
            assert isRegister(input);
            PTXAddress addr = address.toAddress();
            // masm.st_global_return_value_s64(addr.getBase(), addr.getDisplacement(), asRegister(input));

            switch (kind) {
                case Byte:
                case Short:
                    masm.st_global_return_value_s8(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Int:
                    masm.st_global_return_value_s32(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Long:
                    masm.st_global_return_value_s64(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Float:
                    masm.st_global_return_value_f32(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Double:
                    masm.st_global_return_value_f64(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                case Object:
                    masm.st_global_return_value_u64(addr.getBase(), addr.getDisplacement(), asRegister(input));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + address.getKind());
            }
        }
    }
}
