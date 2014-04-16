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

import static com.oracle.graal.asm.ptx.PTXStateSpace.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.Ld;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.LoadAddr;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.LoadParam;
import com.oracle.graal.asm.ptx.PTXMacroAssembler.St;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

public class PTXMemOp {

    // Load operation from .global state space
    @Opcode("LOAD_REGBASE_DISP")
    public static class LoadOp extends PTXLIRInstruction {

        private final Kind kind;
        @Def({REG}) protected Variable result;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @State protected LIRFrameState state;

        public LoadOp(Kind kind, Variable result, PTXAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            PTXAddress addr = address.toAddress();
            switch (kind) {
                case Boolean:
                case Byte:
                case Short:
                case Char:
                case Int:
                case Long:
                case Float:
                case Double:
                case Object:
                    new Ld(Global, result, addr.getBase(), Constant.forLong(addr.getDisplacement())).emit(masm);
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
        @Use({REG}) protected Variable input;
        @State protected LIRFrameState state;

        public StoreOp(Kind kind, PTXAddressValue address, Variable input, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            PTXAddress addr = address.toAddress();
            switch (kind) {
                case Byte:
                case Short:
                case Int:
                case Long:
                case Float:
                case Double:
                case Object:
                    new St(Global, input, addr.getBase(), Constant.forLong(addr.getDisplacement())).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + address.getKind());
            }
        }
    }

    // Load operation from .param state space
    @Opcode("LOAD_PARAM")
    public static class LoadParamOp extends PTXLIRInstruction {

        private final Kind kind;
        @Def({REG}) protected Variable result;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @State protected LIRFrameState state;

        public LoadParamOp(Kind kind, Variable result, PTXAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            PTXAddress addr = address.toAddress();
            switch (kind) {
                case Byte:
                case Short:
                case Char:
                case Int:
                case Long:
                case Float:
                case Double:
                case Object:
                    new LoadParam(Parameter, result, addr.getBase(), Constant.forLong(addr.getDisplacement())).emit(masm);
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
        @Def({REG}) protected Variable result;
        @Use({COMPOSITE}) protected PTXAddressValue address;
        @State protected LIRFrameState state;

        public LoadReturnAddrOp(Kind kind, Variable result, PTXAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            PTXAddress addr = address.toAddress();
            switch (kind) {
                case Int:
                case Long:
                case Float:
                case Double:
                    new LoadAddr(Parameter, result, addr.getBase(), Constant.forLong(addr.getDisplacement())).emit(masm);
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
        @Use({REG}) protected Variable input;
        @State protected LIRFrameState state;

        public StoreReturnValOp(Kind kind, PTXAddressValue address, Variable input, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
            PTXAddress addr = address.toAddress();

            switch (kind) {
                case Byte:
                case Short:
                case Int:
                case Long:
                case Float:
                case Double:
                case Object:
                    new St(Global, input, addr.getBase(), Constant.forLong(addr.getDisplacement())).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + address.getKind());
            }
        }
    }
}
