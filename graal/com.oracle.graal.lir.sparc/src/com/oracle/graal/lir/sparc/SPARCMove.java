/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.asm.sparc.SPARCAssembler.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static jdk.internal.jvmci.code.ValueUtil.*;
import static jdk.internal.jvmci.meta.Kind.*;
import static jdk.internal.jvmci.sparc.SPARC.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;
import jdk.internal.jvmci.sparc.*;
import jdk.internal.jvmci.sparc.SPARC.CPUFeature;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Sethix;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setx;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.ImplicitNullCheck;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.StandardOp.NullCheck;
import com.oracle.graal.lir.asm.*;

public class SPARCMove {

    public static class LoadInlineConstant extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction, MoveOp {
        public static final LIRInstructionClass<LoadInlineConstant> TYPE = LIRInstructionClass.create(LoadInlineConstant.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);
        private JavaConstant constant;
        @Def({REG, STACK}) AllocatableValue result;

        public LoadInlineConstant(JavaConstant constant, AllocatableValue result) {
            super(TYPE, SIZE);
            this.constant = constant;
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (isRegister(result)) {
                const2reg(crb, masm, result, g0, constant, delayedControlTransfer);
            } else if (isStackSlot(result)) {
                StackSlot slot = asStackSlot(result);
                const2stack(crb, masm, slot, g0, constant, delayedControlTransfer, constant);
            }
        }

        public Value getInput() {
            return constant;
        }

        public AllocatableValue getResult() {
            return result;
        }
    }

    public static class LoadConstantFromTable extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<LoadConstantFromTable> TYPE = LIRInstructionClass.create(LoadConstantFromTable.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1, 8);

        private JavaConstant constant;
        @Def({REG, STACK}) AllocatableValue result;
        @Use({REG}) private AllocatableValue constantTableBase;

        public LoadConstantFromTable(JavaConstant constant, AllocatableValue constantTableBase, AllocatableValue result) {
            super(TYPE, SIZE);
            this.constant = constant;
            this.result = result;
            this.constantTableBase = constantTableBase;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            final Runnable recordReference;
            final Kind constantKind = constant.getKind().equals(Object) ? Kind.Long : constant.getKind();
            switch (constantKind) {
                case Object:
                case Float:
                case Double:
                case Char:
                case Short:
                case Int:
                case Long:
                    recordReference = () -> crb.recordDataReferenceInCode(constant, constantKind.getByteCount());
                    break;
                case Byte:
                case Boolean: // Byte and Boolean always fits into simm13
                    throw JVMCIError.shouldNotReachHere("Byte/Boolean must not be loaded via constant table");
                default:
                    throw JVMCIError.shouldNotReachHere("Unimplemented constant type: " + constant);
            }
            Register baseRegister = asRegister(constantTableBase);
            if (isRegister(result)) {
                Register resultRegister = asRegister(result);
                loadFromConstantTable(crb, masm, constantKind, baseRegister, resultRegister, delayedControlTransfer, recordReference);
            } else if (isStackSlot(result)) {
                try (ScratchRegister scratch = masm.getScratchRegister()) {
                    Register scratchRegister = scratch.getRegister();
                    loadFromConstantTable(crb, masm, constantKind, baseRegister, scratchRegister, delayedControlTransfer, recordReference);
                    StackSlot slot = asStackSlot(result);
                    delayedControlTransfer.emitControlTransfer(crb, masm);
                    masm.stx(scratchRegister, (SPARCAddress) crb.asAddress(slot));
                }
            }
        }
    }

    @Opcode("MOVE")
    public static class Move extends SPARCLIRInstruction implements MoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<Move> TYPE = LIRInstructionClass.create(Move.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(8);

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, STACK}) protected Value input;

        public Move(AllocatableValue result, Value input) {
            super(TYPE, SIZE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert !isConstant(input);
            if (isStackSlot(input) && isStackSlot(result)) {
                stack2stack(crb, masm, reInterpret(asStackSlot(result)), reInterpret(asStackSlot(input)), delayedControlTransfer);
            } else {
                move(crb, masm, getResult(), getInput(), delayedControlTransfer);
            }
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        private static StackSlot reInterpret(StackSlot slot) {
            switch ((Kind) slot.getPlatformKind()) {
                case Boolean:
                case Byte:
                case Short:
                case Char:
                case Int:
                case Long:
                case Object:
                    return slot;
                case Float:
                    return StackSlot.get(LIRKind.value(Kind.Int), slot.getRawOffset(), slot.getRawAddFrameSize());
                case Double:
                    return StackSlot.get(LIRKind.value(Kind.Long), slot.getRawOffset(), slot.getRawAddFrameSize());
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }

    /**
     * Move between floating-point and general purpose register domain.
     */
    @Opcode("MOVE_FPGP")
    public static final class MoveFpGp extends SPARCLIRInstruction implements MoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MoveFpGp> TYPE = LIRInstructionClass.create(MoveFpGp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Use({STACK, ILLEGAL}) protected AllocatableValue temp;

        public MoveFpGp(AllocatableValue result, AllocatableValue input, AllocatableValue temp) {
            super(TYPE, SIZE);
            this.result = result;
            this.input = input;
            this.temp = temp;
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
            if (AllocatableValue.ILLEGAL.equals(temp)) {
                moveDirect(crb, masm, inputKind, resultKind);
            } else {
                moveViaStack(crb, masm, inputKind, resultKind);
            }
        }

        private void moveDirect(CompilationResultBuilder crb, SPARCMacroAssembler masm, Kind inputKind, Kind resultKind) {
            delayedControlTransfer.emitControlTransfer(crb, masm);
            if (resultKind == Float) {
                if (inputKind == Int || inputKind == Short || inputKind == Char || inputKind == Byte) {
                    masm.movwtos(asIntReg(input), asFloatReg(result));
                } else {
                    throw JVMCIError.shouldNotReachHere();
                }
            } else if (resultKind == Double) {
                if (inputKind == Int || inputKind == Short || inputKind == Char || inputKind == Byte) {
                    masm.movxtod(asIntReg(input), asDoubleReg(result));
                } else {
                    masm.movxtod(asLongReg(input), asDoubleReg(result));
                }
            } else if (inputKind == Float) {
                if (resultKind == Int || resultKind == Short || resultKind == Byte) {
                    masm.movstosw(asFloatReg(input), asIntReg(result));
                } else {
                    masm.movstouw(asFloatReg(input), asIntReg(result));
                }
            } else if (inputKind == Double) {
                if (resultKind == Long) {
                    masm.movdtox(asDoubleReg(input), asLongReg(result));
                } else {
                    throw JVMCIError.shouldNotReachHere();
                }
            }
        }

        private void moveViaStack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Kind inputKind, Kind resultKind) {
            int resultKindSize = crb.target.getSizeInBytes(resultKind);
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                SPARCAddress tempAddress = generateSimm13OffsetLoad((SPARCAddress) crb.asAddress(temp), masm, scratch);
                switch (inputKind) {
                    case Float:
                        assert resultKindSize == 4;
                        masm.stf(asFloatReg(input), tempAddress);
                        break;
                    case Double:
                        assert resultKindSize == 8;
                        masm.stdf(asDoubleReg(input), tempAddress);
                        break;
                    case Long:
                    case Int:
                    case Short:
                    case Char:
                    case Byte:
                        if (resultKindSize == 8) {
                            masm.stx(asLongReg(input), tempAddress);
                        } else if (resultKindSize == 4) {
                            masm.stw(asIntReg(input), tempAddress);
                        } else if (resultKindSize == 2) {
                            masm.sth(asIntReg(input), tempAddress);
                        } else if (resultKindSize == 1) {
                            masm.stb(asIntReg(input), tempAddress);
                        } else {
                            throw JVMCIError.shouldNotReachHere();
                        }
                        break;
                    default:
                        JVMCIError.shouldNotReachHere();
                }
                delayedControlTransfer.emitControlTransfer(crb, masm);
                switch (resultKind) {
                    case Long:
                        masm.ldx(tempAddress, asLongReg(result));
                        break;
                    case Int:
                        masm.ldsw(tempAddress, asIntReg(result));
                        break;
                    case Short:
                        masm.ldsh(tempAddress, asIntReg(input));
                        break;
                    case Char:
                        masm.lduh(tempAddress, asIntReg(input));
                        break;
                    case Byte:
                        masm.ldsb(tempAddress, asIntReg(input));
                        break;
                    case Float:
                        masm.ldf(tempAddress, asFloatReg(result));
                        break;
                    case Double:
                        masm.lddf(tempAddress, asDoubleReg(result));
                        break;
                    default:
                        JVMCIError.shouldNotReachHere();
                        break;
                }
            }
        }
    }

    public abstract static class MemOp extends SPARCLIRInstruction implements ImplicitNullCheck {
        public static final LIRInstructionClass<MemOp> TYPE = LIRInstructionClass.create(MemOp.class);

        protected final PlatformKind kind;
        @Use({COMPOSITE}) protected SPARCAddressValue address;
        @State protected LIRFrameState state;

        public MemOp(LIRInstructionClass<? extends MemOp> c, SizeEstimate size, PlatformKind kind, SPARCAddressValue address, LIRFrameState state) {
            super(c, size);
            this.kind = kind;
            this.address = address;
            this.state = state;
        }

        protected abstract void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitMemAccess(crb, masm);
        }

        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && address.isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public static final class LoadOp extends MemOp implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<LoadOp> TYPE = LIRInstructionClass.create(LoadOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        @Def({REG}) protected AllocatableValue result;
        protected boolean signExtend;

        public LoadOp(Kind kind, AllocatableValue result, SPARCAddressValue address, LIRFrameState state) {
            this(kind, result, address, state, false);
        }

        public LoadOp(Kind kind, AllocatableValue result, SPARCAddressValue address, LIRFrameState state, boolean signExtend) {
            super(TYPE, SIZE, kind, address, state);
            this.result = result;
            this.signExtend = signExtend;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitLoad(crb, masm, address.toAddress(), result, signExtend, kind, delayedControlTransfer, state);
        }
    }

    public static final class LoadAddressOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<LoadAddressOp> TYPE = LIRInstructionClass.create(LoadAddressOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(8);

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected SPARCAddressValue addressValue;

        public LoadAddressOp(AllocatableValue result, SPARCAddressValue address) {
            super(TYPE, SIZE);
            this.result = result;
            this.addressValue = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCAddress address = addressValue.toAddress();
            loadEffectiveAddress(crb, masm, address, asLongReg(result), delayedControlTransfer);
        }
    }

    public static final class LoadDataAddressOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<LoadDataAddressOp> TYPE = LIRInstructionClass.create(LoadDataAddressOp.class);

        @Def({REG}) protected AllocatableValue result;
        private final byte[] data;

        public LoadDataAddressOp(AllocatableValue result, byte[] data) {
            super(TYPE);
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCAddress addr = (SPARCAddress) crb.recordDataReferenceInCode(data, 8);
            assert addr == masm.getPlaceholder();
            final boolean forceRelocatable = true;
            Register dstReg = asRegister(result);
            new Setx(0, dstReg, forceRelocatable).emit(masm);
        }

        @Override
        public SizeEstimate estimateSize() {
            return SizeEstimate.create(8, data.length);
        }
    }

    public static final class MembarOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MembarOp> TYPE = LIRInstructionClass.create(MembarOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        private final int barriers;

        public MembarOp(final int barriers) {
            super(TYPE, SIZE);
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            delayedControlTransfer.emitControlTransfer(crb, masm);
            masm.membar(barriers);
        }
    }

    public static final class NullCheckOp extends SPARCLIRInstruction implements NullCheck, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<NullCheckOp> TYPE = LIRInstructionClass.create(NullCheckOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        @Use({COMPOSITE}) protected SPARCAddressValue input;
        @State protected LIRFrameState state;

        public NullCheckOp(SPARCAddressValue input, LIRFrameState state) {
            super(TYPE, SIZE);
            this.input = input;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            delayedControlTransfer.emitControlTransfer(crb, masm);
            SPARCAddress addr = input.toAddress();
            crb.recordImplicitException(masm.position(), state);
            // Just need to check whether this is a valid address or not; alignment is not
            // checked
            masm.ldub(addr, g0);
        }

        public Value getCheckedValue() {
            return input;
        }

        public LIRFrameState getState() {
            return state;
        }
    }

    @Opcode("CAS")
    public static final class CompareAndSwapOp extends SPARCLIRInstruction {
        public static final LIRInstructionClass<CompareAndSwapOp> TYPE = LIRInstructionClass.create(CompareAndSwapOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Def({REG, HINT}) protected AllocatableValue result;
        @Alive({REG}) protected AllocatableValue address;
        @Alive({REG}) protected AllocatableValue cmpValue;
        @Use({REG}) protected AllocatableValue newValue;

        public CompareAndSwapOp(AllocatableValue result, AllocatableValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            super(TYPE, SIZE);
            this.result = result;
            this.address = address;
            this.cmpValue = cmpValue;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            move(crb, masm, result, newValue, SPARCDelayedControlTransfer.DUMMY);
            compareAndSwap(crb, masm, address, cmpValue, result, delayedControlTransfer);
        }
    }

    public static final class StackLoadAddressOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<StackLoadAddressOp> TYPE = LIRInstructionClass.create(StackLoadAddressOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected StackSlotValue slot;

        public StackLoadAddressOp(AllocatableValue result, StackSlotValue address) {
            super(TYPE, SIZE);
            this.result = result;
            this.slot = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            SPARCAddress address = (SPARCAddress) crb.asAddress(slot);
            loadEffectiveAddress(crb, masm, address, asLongReg(result), delayedControlTransfer);
        }
    }

    private static void loadEffectiveAddress(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCAddress address, Register result, SPARCDelayedControlTransfer delaySlotHolder) {
        if (address.getIndex().equals(Register.None)) {
            if (isSimm13(address.getDisplacement())) {
                delaySlotHolder.emitControlTransfer(crb, masm);
                masm.add(address.getBase(), address.getDisplacement(), result);
            } else {
                assert result.encoding() != address.getBase().encoding();
                new Setx(address.getDisplacement(), result).emit(masm);
                // No relocation, therefore, the add can be delayed as well
                delaySlotHolder.emitControlTransfer(crb, masm);
                masm.add(address.getBase(), result, result);
            }
        } else {
            delaySlotHolder.emitControlTransfer(crb, masm);
            masm.add(address.getBase(), address.getIndex(), result);
        }
    }

    public static class StoreOp extends MemOp implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<StoreOp> TYPE = LIRInstructionClass.create(StoreOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        @Use({REG}) protected AllocatableValue input;

        public StoreOp(Kind kind, SPARCAddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, SIZE, kind, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitStore(input, address.toAddress(), kind, delayedControlTransfer, state, crb, masm);
        }
    }

    public static final class StoreConstantOp extends MemOp implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<StoreConstantOp> TYPE = LIRInstructionClass.create(StoreConstantOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(2);

        protected final JavaConstant input;

        public StoreConstantOp(Kind kind, SPARCAddressValue address, JavaConstant input, LIRFrameState state) {
            super(TYPE, SIZE, kind, address, state);
            this.input = input;
            if (!input.isDefaultForKind()) {
                throw JVMCIError.shouldNotReachHere("Can only store null constants to memory");
            }
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                SPARCAddress addr = generateSimm13OffsetLoad(address.toAddress(), masm, scratch);
                delayedControlTransfer.emitControlTransfer(crb, masm);
                if (state != null) {
                    crb.recordImplicitException(masm.position(), state);
                }
                switch ((Kind) kind) {
                    case Boolean:
                    case Byte:
                        masm.stb(g0, addr);
                        break;
                    case Short:
                    case Char:
                        masm.sth(g0, addr);
                        break;
                    case Int:
                        masm.stw(g0, addr);
                        break;
                    case Long:
                    case Object:
                        masm.stx(g0, addr);
                        break;
                    case Float:
                    case Double:
                        throw JVMCIError.shouldNotReachHere("Cannot store float constants to memory");
                    default:
                        throw JVMCIError.shouldNotReachHere();
                }
            }
        }
    }

    public static void move(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        move(crb, masm, result, g0, input, delaySlotLir);
    }

    public static void move(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Register constantTableBase, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(crb, masm, result, input, delaySlotLir);
            } else if (isStackSlot(result)) {
                reg2stack(crb, masm, result, input, delaySlotLir);
            } else {
                throw JVMCIError.shouldNotReachHere("Result is a: " + result);
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                SPARCAddress inputAddress = (SPARCAddress) crb.asAddress(input);
                emitLoad(crb, masm, inputAddress, result, false, input.getPlatformKind(), delaySlotLir, null);
            } else if (isStackSlot(result)) {
                stack2stack(crb, masm, result, input, delaySlotLir);
            } else {
                throw JVMCIError.shouldNotReachHere("Result is a: " + result);
            }
        } else if (isConstant(input)) {
            JavaConstant constant = asConstant(input);
            if (isRegister(result)) {
                const2reg(crb, masm, result, constantTableBase, constant, delaySlotLir);
            } else if (isStackSlot(result)) {
                const2stack(crb, masm, result, constantTableBase, input, delaySlotLir, constant);
            } else {
                throw JVMCIError.shouldNotReachHere("Result is a: " + result);
            }
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
    }

    public static void const2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Register constantTableBase, Value input, SPARCDelayedControlTransfer delaySlotLir,
                    JavaConstant constant) {
        if (constant.isDefaultForKind() || constant.isNull()) {
            SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
            emitStore(g0.asValue(LIRKind.derive(input)), resultAddress, input.getPlatformKind(), delaySlotLir, null, crb, masm);
        } else {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Value scratchRegisterValue = sc.getRegister().asValue(LIRKind.derive(constant));
                const2reg(crb, masm, scratchRegisterValue, constantTableBase, constant, SPARCDelayedControlTransfer.DUMMY);
                SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
                emitStore(scratchRegisterValue, resultAddress, input.getPlatformKind(), delaySlotLir, null, crb, masm);
            }
        }
    }

    public static void stack2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            SPARCAddress inputAddress = (SPARCAddress) crb.asAddress(input);
            Value scratchRegisterValue = sc.getRegister().asValue(LIRKind.derive(input));
            emitLoad(crb, masm, inputAddress, scratchRegisterValue, false, input.getPlatformKind(), SPARCDelayedControlTransfer.DUMMY, null);
            SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
            emitStore(scratchRegisterValue, resultAddress, input.getPlatformKind(), delaySlotLir, null, crb, masm);
        }
    }

    public static void reg2stack(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
        SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
        emitStore(input, resultAddress, input.getPlatformKind(), delaySlotLir, null, crb, masm);
    }

    public static void reg2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
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
                delaySlotLir.emitControlTransfer(crb, masm);
                masm.mov(src, dst);
                break;
            case Float:
                if (result.getPlatformKind() == Kind.Float) {
                    masm.fmovs(src, dst);
                } else {
                    throw JVMCIError.shouldNotReachHere();
                }
                break;
            case Double:
                if (result.getPlatformKind() == Kind.Double) {
                    masm.fmovd(src, dst);
                } else {
                    throw JVMCIError.shouldNotReachHere();
                }
                break;
            default:
                throw JVMCIError.shouldNotReachHere("Input is a: " + input.getKind());
        }
    }

    /**
     * Guarantees that the given SPARCAddress given before is loadable by subsequent load/store
     * instruction. If the displacement exceeds the simm13 value range, the value is put into a
     * scratch register.
     *
     * @param addr Address to modify
     * @param masm assembler to output the potential code to store the value in the scratch register
     * @param scratch The register as scratch to use
     * @return a loadable SPARCAddress
     */
    public static SPARCAddress generateSimm13OffsetLoad(SPARCAddress addr, SPARCMacroAssembler masm, Register scratch) {
        boolean displacementOutOfBound = addr.getIndex().equals(Register.None) && !SPARCAssembler.isSimm13(addr.getDisplacement());
        if (displacementOutOfBound) {
            new Setx(addr.getDisplacement(), scratch, false).emit(masm);
            return new SPARCAddress(addr.getBase(), scratch);
        } else {
            return addr;
        }
    }

    public static void const2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Register constantTableBase, JavaConstant input, SPARCDelayedControlTransfer delaySlotLir) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            Set<CPUFeature> cpuFeatures = ((SPARC) masm.target.arch).getFeatures();
            boolean hasVIS1 = cpuFeatures.contains(CPUFeature.VIS1);
            boolean hasVIS3 = cpuFeatures.contains(CPUFeature.VIS3);
            Register resultRegister = asRegister(result);
            switch (input.getKind().getStackKind()) {
                case Int:
                    if (input.isDefaultForKind()) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.clr(resultRegister);
                    } else if (isSimm13(input.asInt())) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.or(g0, input.asInt(), resultRegister);
                    } else {
                        if (constantTableBase.equals(g0)) {
                            throw JVMCIError.shouldNotReachHere();
                        } else {
                            Runnable recordReference = () -> crb.recordDataReferenceInCode(input, input.getKind().getByteCount());
                            loadFromConstantTable(crb, masm, input.getKind(), constantTableBase, resultRegister, delaySlotLir, recordReference);
                        }
                    }
                    break;
                case Long:
                    if (input.isDefaultForKind()) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.clr(resultRegister);
                    } else if (isSimm13(input.asLong())) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.or(g0, (int) input.asLong(), resultRegister);
                    } else {
                        Runnable recordReference = () -> crb.asLongConstRef(input);
                        loadFromConstantTable(crb, masm, Long, constantTableBase, resultRegister, delaySlotLir, recordReference);
                    }
                    break;
                case Float: {
                    float constant = input.asFloat();
                    int constantBits = java.lang.Float.floatToIntBits(constant);
                    if (hasVIS1 && constantBits == 0) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.fzeros(resultRegister);
                    } else {
                        if (hasVIS3 && isSimm13(constantBits)) {
                            masm.or(g0, constantBits, scratch);
                            delaySlotLir.emitControlTransfer(crb, masm);
                            masm.movwtos(scratch, resultRegister);
                        } else {
                            Runnable recordReference = () -> crb.asFloatConstRef(input);
                            // First load the address into the scratch register
                            loadFromConstantTable(crb, masm, Float, constantTableBase, resultRegister, delaySlotLir, recordReference);
                        }
                    }
                    break;
                }
                case Double: {
                    double constant = input.asDouble();
                    long constantBits = java.lang.Double.doubleToRawLongBits(constant);
                    if (hasVIS1 && constantBits == 0) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.fzerod(resultRegister);
                    } else {
                        if (hasVIS3 && isSimm13(constantBits)) {
                            masm.or(g0, (int) constantBits, scratch);
                            delaySlotLir.emitControlTransfer(crb, masm);
                            masm.movxtod(scratch, resultRegister);
                        } else {
                            Runnable recordReference = () -> crb.asDoubleConstRef(input);
                            loadFromConstantTable(crb, masm, Double, constantTableBase, resultRegister, delaySlotLir, recordReference);
                        }
                    }
                    break;
                }
                case Object:
                    if (input.isNull()) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.clr(resultRegister);
                    } else {
                        Runnable recordReference = () -> crb.recordDataReferenceInCode(input, 8);
                        loadFromConstantTable(crb, masm, Long, constantTableBase, resultRegister, delaySlotLir, recordReference);
                    }
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere("missing: " + input.getKind());
            }
        }
    }

    protected static void compareAndSwap(CompilationResultBuilder crb, SPARCMacroAssembler masm, AllocatableValue address, AllocatableValue cmpValue, AllocatableValue newValue,
                    SPARCDelayedControlTransfer delay) {
        delay.emitControlTransfer(crb, masm);
        switch (cmpValue.getKind()) {
            case Int:
                masm.cas(asRegister(address), asRegister(cmpValue), asRegister(newValue));
                break;
            case Long:
            case Object:
                masm.casx(asRegister(address), asRegister(cmpValue), asRegister(newValue));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private static void emitLoad(CompilationResultBuilder crb, SPARCMacroAssembler masm, SPARCAddress address, Value result, boolean signExtend, PlatformKind kind,
                    SPARCDelayedControlTransfer delayedControlTransfer, LIRFrameState state) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            final SPARCAddress addr = generateSimm13OffsetLoad(address, masm, scratch);
            final Register dst = asRegister(result);
            delayedControlTransfer.emitControlTransfer(crb, masm);
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            switch ((Kind) kind) {
                case Boolean:
                case Byte:
                    if (signExtend) {
                        masm.ldsb(addr, dst);
                    } else {
                        masm.ldub(addr, dst);
                    }
                    break;
                case Short:
                    if (signExtend) {
                        masm.ldsh(addr, dst);
                    } else {
                        masm.lduh(addr, dst);
                    }
                    break;
                case Char:
                    if (signExtend) {
                        masm.ldsh(addr, dst);
                    } else {
                        masm.lduh(addr, dst);
                    }
                    break;
                case Int:
                    if (signExtend) {
                        masm.ldsw(addr, dst);
                    } else {
                        masm.lduw(addr, dst);
                    }
                    break;
                case Long:
                    masm.ldx(addr, dst);
                    break;
                case Float:
                    masm.ldf(addr, dst);
                    break;
                case Double:
                    masm.lddf(addr, dst);
                    break;
                case Object:
                    masm.ldx(addr, dst);
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }
        }
    }

    public static void emitStore(Value input, SPARCAddress address, PlatformKind kind, SPARCDelayedControlTransfer delayedControlTransfer, LIRFrameState state, CompilationResultBuilder crb,
                    SPARCMacroAssembler masm) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            SPARCAddress addr = generateSimm13OffsetLoad(address, masm, scratch);
            delayedControlTransfer.emitControlTransfer(crb, masm);
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            switch ((Kind) kind) {
                case Boolean:
                case Byte:
                    masm.stb(asRegister(input), addr);
                    break;
                case Short:
                case Char:
                    masm.sth(asRegister(input), addr);
                    break;
                case Int:
                    masm.stw(asRegister(input), addr);
                    break;
                case Long:
                    masm.stx(asRegister(input), addr);
                    break;
                case Object:
                    masm.stx(asRegister(input), addr);
                    break;
                case Float:
                    masm.stf(asRegister(input), addr);
                    break;
                case Double:
                    masm.stdf(asRegister(input), addr);
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere("missing: " + kind);
            }
        }
    }

    /**
     * This method creates a load from the constant section. It automatically respects the different
     * patterns used for small constant sections (<8k) and large constant sections (>=8k). The
     * generated patterns by this method must be understood by
     * CodeInstaller::pd_patch_DataSectionReference (jvmciCodeInstaller_sparc.cpp).
     */
    public static void loadFromConstantTable(CompilationResultBuilder crb, SPARCMacroAssembler masm, Kind kind, Register constantTableBase, Register dest,
                    SPARCDelayedControlTransfer delaySlotInstruction, Runnable recordReference) {
        SPARCAddress address;
        ScratchRegister scratch = null;
        try {
            if (masm.isImmediateConstantLoad()) {
                address = new SPARCAddress(constantTableBase, 0);
                // Make delayed only, when using immediate constant load.
                delaySlotInstruction.emitControlTransfer(crb, masm);
                recordReference.run();
            } else {
                scratch = masm.getScratchRegister();
                Register sr = scratch.getRegister();
                recordReference.run();
                new Sethix(0, sr, true).emit(masm);
                address = new SPARCAddress(sr, 0);
            }
            switch (kind) {
                case Boolean:
                case Byte:
                    masm.ldub(address, dest);
                    break;
                case Short:
                case Char:
                    masm.lduh(address, dest);
                    break;
                case Int:
                    masm.lduw(address, dest);
                    break;
                case Long:
                    masm.ldx(address, dest);
                    break;
                case Float:
                    masm.ldf(address, dest);
                    break;
                case Double:
                    masm.lddf(address, dest);
                    break;
                default:
                    throw new InternalError("Unknown constant load kind: " + kind);
            }
        } finally {
            if (scratch != null) {
                scratch.close();
            }
        }
    }
}
