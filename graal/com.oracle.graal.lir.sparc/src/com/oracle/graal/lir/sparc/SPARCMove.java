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
import static com.oracle.graal.asm.sparc.SPARCAssembler.*;

import com.oracle.graal.api.code.CompilationResult.RawData;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.ImplicitNullCheck;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.StandardOp.NullCheck;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.sparc.*;

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

    /**
     * Move between floating-point and general purpose register domain (WITHOUT VIS3)
     */
    @Opcode("MOVE")
    public static class MoveFpGp extends SPARCLIRInstruction implements MoveOp {

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Use({STACK}) protected StackSlot temp;

        public MoveFpGp(AllocatableValue result, AllocatableValue input, StackSlot temp) {
            super();
            this.result = result;
            this.input = input;
            this.temp = temp;
            assert this.temp.getPlatformKind() == Kind.Long;
        }

        public Value getInput() {
            return input;
        }

        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            Kind inputKind = (Kind) input.getPlatformKind();
            Kind resultKind = (Kind) result.getPlatformKind();
            int resultKindSize = crb.target.getSizeInBytes(resultKind);
            try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                Register scratch = sc.getRegister();
                SPARCAddress tempAddress = guaranueeLoadable((SPARCAddress) crb.asAddress(temp), masm, scratch);
                switch (inputKind) {
                    case Float:
                        assert resultKindSize == 4;
                        new Stf(asFloatReg(input), tempAddress).emit(masm);
                        break;
                    case Double:
                        assert resultKindSize == 8;
                        new Stdf(asDoubleReg(input), tempAddress).emit(masm);
                        break;
                    case Long:
                    case Int:
                    case Short:
                    case Char:
                    case Byte:
                        if (resultKindSize == 8) {
                            new Stx(asLongReg(input), tempAddress).emit(masm);
                        } else if (resultKindSize == 4) {
                            new Stw(asIntReg(input), tempAddress).emit(masm);
                        } else if (resultKindSize == 2) {
                            new Sth(asIntReg(input), tempAddress).emit(masm);
                        } else if (resultKindSize == 1) {
                            new Stb(asIntReg(input), tempAddress).emit(masm);
                        } else {
                            throw GraalInternalError.shouldNotReachHere();
                        }
                        break;
                    default:
                        GraalInternalError.shouldNotReachHere();
                }
                switch (resultKind) {
                    case Long:
                        new Ldx(tempAddress, asLongReg(result)).emit(masm);
                        break;
                    case Int:
                        new Ldsw(tempAddress, asIntReg(result)).emit(masm);
                        break;
                    case Short:
                        new Ldsh(tempAddress, asIntReg(input)).emit(masm);
                        break;
                    case Char:
                        new Lduh(tempAddress, asIntReg(input)).emit(masm);
                        break;
                    case Byte:
                        new Ldsb(tempAddress, asIntReg(input)).emit(masm);
                        break;
                    case Float:
                        new Ldf(tempAddress, asFloatReg(result)).emit(masm);
                        break;
                    case Double:
                        new Lddf(tempAddress, asDoubleReg(result)).emit(masm);
                        break;
                    default:
                        GraalInternalError.shouldNotReachHere();
                        break;
                }
            }
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
            try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                Register scratch = sc.getRegister();
                final SPARCAddress addr = guaranueeLoadable(address.toAddress(), masm, scratch);
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
    }

    public static class LoadAddressOp extends SPARCLIRInstruction {

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected SPARCAddressValue addressValue;

        public LoadAddressOp(AllocatableValue result, SPARCAddressValue address) {
            this.result = result;
            this.addressValue = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCAddress address = addressValue.toAddress();
            loadEffectiveAddress(address, asLongReg(result), masm);
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
            loadEffectiveAddress(address, asLongReg(result), masm);
        }
    }

    private static void loadEffectiveAddress(SPARCAddress address, Register result, SPARCMacroAssembler masm) {
        if (address.getIndex().equals(Register.None)) {
            if (isSimm13(address.getDisplacement())) {
                new Add(address.getBase(), address.getDisplacement(), result).emit(masm);
            } else {
                assert result.encoding() != address.getBase().encoding();
                new Setx(address.getDisplacement(), result).emit(masm);
                new Add(address.getBase(), result, result).emit(masm);
            }
        } else {
            new Add(address.getBase(), address.getIndex(), result).emit(masm);
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
            try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                Register scratch = sc.getRegister();
                SPARCAddress addr = guaranueeLoadable(address.toAddress(), masm, scratch);
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
            try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                Register scratch = sc.getRegister();
                SPARCAddress addr = guaranueeLoadable(address.toAddress(), masm, scratch);
                switch (kind) {
                    case Boolean:
                    case Byte:
                        new Stb(g0, addr).emit(masm);
                        break;
                    case Short:
                    case Char:
                        new Sth(g0, addr).emit(masm);
                        break;
                    case Int:
                        new Stw(g0, addr).emit(masm);
                        break;
                    case Long:
                    case Object:
                        new Stx(g0, addr).emit(masm);
                        break;
                    case Float:
                    case Double:
                        throw GraalInternalError.shouldNotReachHere("Cannot store float constants to memory");
                    default:
                        throw GraalInternalError.shouldNotReachHere();
                }
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
            Constant constant = asConstant(input);
            if (isRegister(result)) {
                const2reg(crb, masm, result, constant);
            } else if (isStackSlot(result)) {
                if (constant.isDefaultForKind() || constant.isNull()) {
                    reg2stack(crb, masm, result, g0.asValue(LIRKind.derive(input)));
                } else {
                    try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                        Register scratch = sc.getRegister();
                        long value = constant.asLong();
                        if (isSimm13(value)) {
                            new Or(g0, (int) value, scratch).emit(masm);
                        } else {
                            new Setx(value, scratch).emit(masm);
                        }
                        reg2stack(crb, masm, result, scratch.asValue(LIRKind.derive(input)));
                    }
                }
            } else {
                throw GraalInternalError.shouldNotReachHere("Result is a: " + result);
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static void reg2reg(SPARCAssembler masm, Value result, Value input) {
        final Register src = asRegister(input);
        final Register dst = asRegister(result);
        if (src.equals(dst)) {
            return;
        }
        switch (input.getKind()) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Long:
            case Object:
                new Mov(src, dst).emit(masm);
                break;
            case Float:
                if (result.getPlatformKind() == Kind.Float) {
                    new Fmovs(src, dst).emit(masm);
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
                break;
            case Double:
                if (result.getPlatformKind() == Kind.Double) {
                    new Fmovd(src, dst).emit(masm);
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("Input is a: " + input.getKind());
        }
    }

    /**
     * Guarantees that the given SPARCAddress given before is loadable by subsequent call. If the
     * displacement exceeds the imm13 value, the value is put into a scratch register o7, which must
     * be used as soon as possible.
     *
     * @param addr Address to modify
     * @param masm assembler to output the prior stx command
     * @return a loadable SPARCAddress
     */
    public static SPARCAddress guaranueeLoadable(SPARCAddress addr, SPARCMacroAssembler masm, Register scratch) {
        boolean displacementOutOfBound = addr.getIndex().equals(Register.None) && !SPARCAssembler.isSimm13(addr.getDisplacement());
        if (displacementOutOfBound) {
            new Setx(addr.getDisplacement(), scratch, false).emit(masm);
            return new SPARCAddress(addr.getBase(), scratch);
        } else {
            return addr;
        }
    }

    private static void reg2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input) {
        SPARCAddress dst = (SPARCAddress) crb.asAddress(result);
        try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
            Register scratch = sc.getRegister();
            dst = guaranueeLoadable(dst, masm, scratch);
            Register src = asRegister(input);
            switch (input.getKind()) {
                case Byte:
                case Boolean:
                    new Stb(src, dst).emit(masm);
                    break;
                case Char:
                case Short:
                    new Sth(src, dst).emit(masm);
                    break;
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
                    throw GraalInternalError.shouldNotReachHere("Input is a: " + input.getKind() + "(" + input + ")");
            }
        }
    }

    private static void stack2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input) {
        SPARCAddress src = (SPARCAddress) crb.asAddress(input);
        try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
            Register scratch = sc.getRegister();
            src = guaranueeLoadable(src, masm, scratch);
            Register dst = asRegister(result);
            switch (input.getKind()) {
                case Boolean:
                case Byte:
                    new Ldsb(src, dst).emit(masm);
                    break;
                case Short:
                    new Ldsh(src, dst).emit(masm);
                    break;
                case Char:
                    new Lduh(src, dst).emit(masm);
                    break;
                case Int:
                    new Ldsw(src, dst).emit(masm);
                    break;
                case Long:
                case Object:
                    new Ldx(src, dst).emit(masm);
                    break;
                case Float:
                    new Ldf(src, dst).emit(masm);
                    break;
                case Double:
                    new Lddf(src, dst).emit(masm);
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("Input is a: " + input.getKind());
            }
        }
    }

    private static void const2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Constant input) {
        try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
            Register scratch = sc.getRegister();
            switch (input.getKind().getStackKind()) {
                case Int:
                    if (input.isDefaultForKind()) {
                        new Clr(asIntReg(result)).emit(masm);
                    } else if (isSimm13(input.asLong())) {
                        new Or(g0, input.asInt(), asIntReg(result)).emit(masm);
                    } else {
                        new Setx(input.asLong(), asIntReg(result)).emit(masm);
                    }
                    break;
                case Long:
                    if (input.isDefaultForKind()) {
                        new Clr(asLongReg(result)).emit(masm);
                    } else if (isSimm13(input.asLong())) {
                        new Or(g0, (int) input.asLong(), asLongReg(result)).emit(masm);
                    } else {
                        new Setx(input.asLong(), asLongReg(result)).emit(masm);
                    }
                    break;
                case Float:
                    // TODO: Handle it the same way, as in the double case with Movwtos
                    crb.asFloatConstRef(input);
                    // First load the address into the scratch register
                    new Setx(0, scratch, true).emit(masm);
                    // Now load the float value
                    new Ldf(scratch, asFloatReg(result)).emit(masm);
                    break;
                case Double:
                    // instead loading this from memory and do the complicated lookup,
                    // just load it directly into a scratch register
                    // First load the address into the scratch register
                    // new Setx(Double.doubleToLongBits(input.asDouble()), scratch,
                    // true).emit(masm);
                    // Now load the float value
                    // new Movxtod(scratch, asDoubleReg(result)).emit(masm);
                    crb.asDoubleConstRef(input);
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
