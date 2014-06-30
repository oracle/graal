/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.CompilationResult.RawData;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.asm.*;

public class SPARCMove {

    @Opcode("MOVE_TOREG")
    public static class MoveToRegOp extends SPARCLIRInstruction implements MoveOp {

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public MoveToRegOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            move(crb, masm, getResult(), getInput());
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

    @Opcode("MOVE_FROMREG")
    public static class MoveFromRegOp extends SPARCLIRInstruction implements MoveOp {

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, CONST, HINT}) protected Value input;

        public MoveFromRegOp(AllocatableValue result, Value input) {
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            move(crb, masm, getResult(), getInput());
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

    public abstract static class MemOp extends SPARCLIRInstruction implements ImplicitNullCheck {

        protected final Kind kind;
        @Use({COMPOSITE}) protected SPARCAddressValue address;
        @State protected LIRFrameState state;

        public MemOp(Kind kind, SPARCAddressValue address, LIRFrameState state) {
            this.kind = kind;
            this.address = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(SPARCMacroAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            emitMemAccess(masm);
        }

        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && value.equals(address.base) && address.index.equals(Value.ILLEGAL) && address.displacement >= 0 && address.displacement < implicitNullCheckLimit) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static class LoadOp extends MemOp {

        @Def({REG}) protected AllocatableValue result;

        public LoadOp(Kind kind, AllocatableValue result, SPARCAddressValue address, LIRFrameState state) {
            super(kind, address, state);
            this.result = result;
        }

        @Override
        public void emitMemAccess(SPARCMacroAssembler masm) {
            final SPARCAddress addr = address.toAddress();
            final Register dst = asRegister(result);
            switch (kind) {
                case Boolean:
                case Byte:
                    new Ldsb(addr, dst).emit(masm);
                    break;
                case Short:
                    new Ldsh(addr, dst).emit(masm);
                    break;
                case Char:
                    new Lduh(addr, dst).emit(masm);
                    break;
                case Int:
                    new Ldsw(addr, dst).emit(masm);
                    break;
                case Long:
                    new Ldx(addr, dst).emit(masm);
                    break;
                case Float:
                    new Ldf(addr, dst).emit(masm);
                    break;
                case Double:
                    new Lddf(addr, dst).emit(masm);
                    break;
                case Object:
                    new Ldx(addr, dst).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static class LoadAddressOp extends SPARCLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected SPARCAddressValue address;

        public LoadAddressOp(AllocatableValue result, SPARCAddressValue address) {
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCAddress addr = address.toAddress();
            if (addr.hasIndex()) {
                new Add(addr.getBase(), addr.getIndex(), asLongReg(result)).emit(masm);
            } else {
                new Add(addr.getBase(), addr.getDisplacement(), asLongReg(result)).emit(masm);
            }
        }
    }

    public static class LoadDataAddressOp extends SPARCLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        private final byte[] data;

        public LoadDataAddressOp(AllocatableValue result, byte[] data) {
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            RawData rawData = new RawData(data, 16);
            SPARCAddress addr = (SPARCAddress) crb.recordDataReferenceInCode(rawData);
            assert addr == masm.getPlaceholder();
            final boolean forceRelocatable = true;
            Register dstReg = asRegister(result);
            new Setx(0, dstReg, forceRelocatable).emit(masm);
        }
    }

    public static class MembarOp extends SPARCLIRInstruction {

        private final int barriers;

        public MembarOp(final int barriers) {
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            new Membar(barriers).emit(masm);
        }
    }

    public static class NullCheckOp extends SPARCLIRInstruction implements NullCheck {

        @Use({REG}) protected AllocatableValue input;
        @State protected LIRFrameState state;

        public NullCheckOp(Variable input, LIRFrameState state) {
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            crb.recordImplicitException(masm.position(), state);
            new Ldx(new SPARCAddress(asRegister(input), 0), r0).emit(masm);
        }

        public Value getCheckedValue() {
            return input;
        }

        public LIRFrameState getState() {
            return state;
        }
    }

    @Opcode("CAS")
    public static class CompareAndSwapOp extends SPARCLIRInstruction {

        // @Def protected AllocatableValue result;
        @Use protected AllocatableValue address;
        @Use protected AllocatableValue cmpValue;
        @Use protected AllocatableValue newValue;

        public CompareAndSwapOp(AllocatableValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            compareAndSwap(masm, address, cmpValue, newValue);
        }
    }

    public static class StackLoadAddressOp extends SPARCLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected StackSlot slot;

        public StackLoadAddressOp(AllocatableValue result, StackSlot slot) {
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCAddress address = (SPARCAddress) crb.asAddress(slot);
            new Add(address.getBase(), address.getDisplacement(), asLongReg(result)).emit(masm);
        }
    }

    public static class StoreOp extends MemOp {

        @Use({REG}) protected AllocatableValue input;

        public StoreOp(Kind kind, SPARCAddressValue address, AllocatableValue input, LIRFrameState state) {
            super(kind, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(SPARCMacroAssembler masm) {
            assert isRegister(input);
            SPARCAddress addr = address.toAddress();
            switch (kind) {
                case Boolean:
                case Byte:
                    new Stb(asRegister(input), addr).emit(masm);
                    break;
                case Short:
                case Char:
                    new Sth(asRegister(input), addr).emit(masm);
                    break;
                case Int:
                    new Stw(asRegister(input), addr).emit(masm);
                    break;
                case Long:
                    new Stx(asRegister(input), addr).emit(masm);
                    break;
                case Object:
                    new Stx(asRegister(input), addr).emit(masm);
                    break;
                case Float:
                    new Stf(asRegister(input), addr).emit(masm);
                    break;
                case Double:
                    new Stdf(asRegister(input), addr).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("missing: " + kind);
            }
        }
    }

    public static class StoreConstantOp extends MemOp {

        protected final Constant input;

        public StoreConstantOp(Kind kind, SPARCAddressValue address, Constant input, LIRFrameState state) {
            super(kind, address, state);
            this.input = input;
            if (!input.isDefaultForKind()) {
                throw GraalInternalError.shouldNotReachHere("Can only store null constants to memory");
            }
        }

        @Override
        public void emitMemAccess(SPARCMacroAssembler masm) {
            switch (kind) {
                case Boolean:
                case Byte:
                    new Stb(g0, address.toAddress()).emit(masm);
                    break;
                case Short:
                case Char:
                    new Sth(g0, address.toAddress()).emit(masm);
                    break;
                case Int:
                    new Stw(g0, address.toAddress()).emit(masm);
                    break;
                case Long:
                case Object:
                    new Stx(g0, address.toAddress()).emit(masm);
                    break;
                case Float:
                case Double:
                    throw GraalInternalError.shouldNotReachHere("Cannot store float constants to memory");
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    public static void move(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(masm, result, input);
            } else if (isStackSlot(result)) {
                reg2stack(crb, masm, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                stack2reg(crb, masm, result, input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            if (isRegister(result)) {
                const2reg(crb, masm, result, (Constant) input);
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    @SuppressWarnings("unused")
    private static void reg2reg(SPARCAssembler masm, Value result, Value input) {
        final Register src = asRegister(input);
        final Register dst = asRegister(result);
        // implicit conversions between double and float registers can happen in the "same Register"
        // f0->d0
        boolean isFloatToDoubleConversion = result.getKind() == Kind.Double && input.getKind() == Kind.Float;
        if (src.equals(dst) && !isFloatToDoubleConversion) {
            return;
        }
        switch (input.getKind()) {
            case Int:
            case Long:
            case Object:
                new Mov(src, dst).emit(masm);
                break;
            case Float:
                switch (result.getKind()) {
                    case Long:
                        new Movstosw(src, dst).emit(masm);
                        break;
                    case Int:
                        new Movstouw(src, dst).emit(masm);
                        break;
                    case Float:
                        new Fmovs(src, dst).emit(masm);
                        break;
                    case Double:
                        new Fstod(masm, src, dst);
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere();
                }
                break;
            case Double:
                if (result.getPlatformKind() == Kind.Long) {
                    new Movdtox(src, dst).emit(masm);
                } else if (result.getPlatformKind() == Kind.Int) {
                    new Movstouw(src, dst).emit(masm);
                } else {
                    new Fmovd(src, dst).emit(masm);
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void reg2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input) {
        SPARCAddress dst = (SPARCAddress) crb.asAddress(result);
        Register src = asRegister(input);
        switch (input.getKind()) {
            case Int:
                new Stw(src, dst).emit(masm);
                break;
            case Long:
            case Object:
                new Stx(src, dst).emit(masm);
                break;
            case Float:
                new Stf(src, dst).emit(masm);
                break;
            case Double:
                new Stdf(src, dst).emit(masm);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void stack2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input) {
        SPARCAddress src = (SPARCAddress) crb.asAddress(input);
        Register dst = asRegister(result);
        switch (input.getKind()) {
            case Int:
                new Ldsw(src, dst).emit(masm);
                break;
            case Long:
            case Object:
                new Ldx(src, dst).emit(masm);
                break;
            case Float:
            case Double:
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void const2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Constant input) {
        Register scratch = g5;
        switch (input.getKind().getStackKind()) {
            case Int:
                if (crb.codeCache.needsDataPatch(input)) {
                    crb.recordInlineDataInCode(input);
                    new Setuw(input.asInt(), asRegister(result)).emit(masm);
                } else {
                    if (input.isDefaultForKind()) {
                        new Clr(asRegister(result)).emit(masm);
                    } else {
                        new Setuw(input.asInt(), asRegister(result)).emit(masm);
                    }
                }
                break;
            case Long:
                if (crb.codeCache.needsDataPatch(input)) {
                    crb.recordInlineDataInCode(input);
                    new Setx(input.asLong(), asRegister(result), true).emit(masm);
                } else {
                    if (input.isDefaultForKind()) {
                        new Clr(asRegister(result)).emit(masm);
                    } else {
                        new Setx(input.asLong(), asRegister(result)).emit(masm);
                    }
                }
                break;
            case Float:
                crb.asFloatConstRef(input);
                // First load the address into the scratch register
                new Setx(0, scratch, true).emit(masm);
                // Now load the float value
                new Ldf(scratch, asFloatReg(result)).emit(masm);
                break;
            case Double:
                crb.asDoubleConstRef(input);
                scratch = g5;
                // First load the address into the scratch register
                new Setx(0, scratch, true).emit(masm);
                // Now load the float value
                new Lddf(scratch, asDoubleReg(result)).emit(masm);
                break;
            case Object:
                if (input.isNull()) {
                    new Clr(asRegister(result)).emit(masm);
                } else if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(input);
                    new Setx(0xDEADDEADDEADDEADL, asRegister(result), true).emit(masm);
                } else {
                    Register dst = asRegister(result);
                    new Rdpc(dst).emit(masm);
                    crb.asObjectConstRef(input);
                    new Ldx(new SPARCAddress(dst, 0), dst).emit(masm);
                    throw GraalInternalError.shouldNotReachHere("the patched offset might be too big for the load");
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + input.getKind());
        }
    }

    protected static void compareAndSwap(SPARCMacroAssembler masm, AllocatableValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
        switch (cmpValue.getKind()) {
            case Int:
                new Cas(asRegister(address), asRegister(cmpValue), asRegister(newValue)).emit(masm);
                break;
            case Long:
            case Object:
                new Casx(asRegister(address), asRegister(cmpValue), asRegister(newValue)).emit(masm);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }
}
