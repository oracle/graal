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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.api.code.ValueUtil.asRegister;
import static com.oracle.graal.api.code.ValueUtil.isConstant;
import static com.oracle.graal.api.code.ValueUtil.isRegister;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.SPARCAddress;
import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.asm.sparc.SPARCAssembler.Lddf;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldf;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldsb;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldsh;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldsw;
import com.oracle.graal.asm.sparc.SPARCAssembler.Lduw;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldx;
import com.oracle.graal.asm.sparc.SPARCAssembler.Or;
import com.oracle.graal.asm.sparc.SPARCAssembler.Stb;
import com.oracle.graal.asm.sparc.SPARCAssembler.Sth;
import com.oracle.graal.asm.sparc.SPARCAssembler.Stw;
import com.oracle.graal.asm.sparc.SPARCAssembler.Stx;
import com.oracle.graal.asm.sparc.SPARCAssembler.NullCheck;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setuw;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setx;
import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.TargetMethodAssembler;
import com.oracle.graal.sparc.SPARC;

public class SPARCMove {

    public static class NullCheckOp extends SPARCLIRInstruction {

        @Use({REG}) protected AllocatableValue input;
        @State protected LIRFrameState state;

        public NullCheckOp(Variable input, LIRFrameState state) {
            this.input = input;
            this.state = state;
        }

        @Override
        @SuppressWarnings("unused")
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            tasm.recordImplicitException(masm.codeBuffer.position(), state);
            new NullCheck(masm, new SPARCAddress(asRegister(input), 0));
        }
    }

    public static class LoadOp extends SPARCLIRInstruction {

        private final Kind kind;
        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected SPARCAddressValue address;
        @State protected LIRFrameState state;

        public LoadOp(Kind kind, AllocatableValue result, SPARCAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
        }

        @Override
        @SuppressWarnings("unused")
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            SPARCAddress addr = address.toAddress();
            switch (kind) {
                case Byte:
                    new Ldsb(masm, addr, asRegister(result));
                    break;
                case Short:
                    new Ldsh(masm, addr, asRegister(result));
                    break;
                case Char:
                    new Lduw(masm, addr, asRegister(result));
                    break;
                case Int:
                    new Ldsw(masm, addr, asRegister(result));
                    break;
                case Long:
                    new Ldx(masm, addr, asRegister(result));
                    break;
                case Float:
                    new Ldf(masm, addr, asRegister(result));
                    break;
                case Double:
                    new Lddf(masm, addr, asRegister(result));
                    break;
                case Object:
                    new Ldx(masm, addr, asRegister(result));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class StoreOp extends SPARCLIRInstruction {

        private final Kind kind;
        @Use({COMPOSITE}) protected SPARCAddressValue address;
        @Use({REG}) protected AllocatableValue input;
        @State protected LIRFrameState state;

        public StoreOp(Kind kind, SPARCAddressValue address, AllocatableValue input, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.input = input;
            this.state = state;
        }

        @Override
        @SuppressWarnings("unused")
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            assert isRegister(input);
            SPARCAddress addr = address.toAddress();
            switch (kind) {
                case Byte:
                    new Stb(masm, addr, asRegister(input));
                    break;
                case Short:
                    new Sth(masm, addr, asRegister(input));
                    break;
                case Int:
                    new Stw(masm, addr, asRegister(input));
                    break;
                case Long:
                    new Stx(masm, addr, asRegister(input));
                    break;
                case Float:
                    new Stx(masm, addr, asRegister(input));
                    break;
                case Double:
                    new Stx(masm, addr, asRegister(input));
                    break;
                case Object:
                    new Stx(masm, addr, asRegister(input));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + address.getKind());
            }
        }
    }

    @Opcode("MOVE")
    public static class MoveToRegOp extends SPARCLIRInstruction implements MoveOp {

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public MoveToRegOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
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
    public static class MoveFromRegOp extends SPARCLIRInstruction implements MoveOp {

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, CONST, HINT}) protected Value input;

        public MoveFromRegOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
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

    public static void move(TargetMethodAssembler tasm, SPARCAssembler masm, Value result, Value input) {
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

    @SuppressWarnings("unused")
    private static void reg2reg(SPARCAssembler masm, Value result, Value input) {
        if (asRegister(input).equals(asRegister(result))) {
            return;
        }
        switch (input.getKind()) {
            case Int:
                new Or(masm, SPARC.r0, asRegister(input), asRegister(result));
                break;
            case Long:
                new Or(masm, SPARC.r0, asRegister(input), asRegister(result));
                break;
            case Float:
                new Or(masm, SPARC.r0, asRegister(input), asRegister(result));
                break;
            case Double:
                new Or(masm, SPARC.r0, asRegister(input), asRegister(result));
                break;
            case Object:
                new Or(masm, SPARC.r0, asRegister(input), asRegister(result));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + input.getKind());
        }
    }

    @SuppressWarnings("unused")
    private static void const2reg(TargetMethodAssembler tasm, SPARCAssembler masm, Value result, Constant input) {
        switch (input.getKind().getStackKind()) {
            case Int:
                if (tasm.runtime.needsDataPatch(input)) {
                    tasm.recordDataReferenceInCode(input, 0, true);
                }
                new Setuw(masm, input.asInt(), asRegister(result));
                break;
            case Long:
                if (tasm.runtime.needsDataPatch(input)) {
                    tasm.recordDataReferenceInCode(input, 0, true);
                }
                new Setx(masm, input.asInt(), null, asRegister(result));
                break;
            case Object:
                if (input.isNull()) {
                    new Setx(masm, 0x0L, null, asRegister(result));
                } else if (tasm.target.inlineObjects) {
                    tasm.recordDataReferenceInCode(input, 0, true);
                    new Setx(masm, 0xDEADDEADDEADDEADL, null, asRegister(result));
                } else {
                    throw new InternalError("NYI");
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + input.getKind());
        }
    }
}
