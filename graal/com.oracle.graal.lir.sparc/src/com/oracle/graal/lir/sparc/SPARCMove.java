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
import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;
import jdk.internal.jvmci.sparc.*;
import jdk.internal.jvmci.sparc.SPARC.CPUFeature;

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setx;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.ImplicitNullCheck;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.StandardOp.NullCheck;
import com.oracle.graal.lir.asm.*;

public class SPARCMove {

    @Opcode("MOVE_TOREG")
    public static class MoveToRegOp extends SPARCLIRInstruction implements MoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MoveToRegOp> TYPE = LIRInstructionClass.create(MoveToRegOp.class);

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK, CONST}) protected Value input;

        public MoveToRegOp(AllocatableValue result, Value input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            move(crb, masm, getResult(), getInput(), delayedControlTransfer);
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
    public static final class MoveFromRegOp extends SPARCLIRInstruction implements MoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MoveFromRegOp> TYPE = LIRInstructionClass.create(MoveFromRegOp.class);

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG, CONST, HINT}) protected Value input;

        public MoveFromRegOp(AllocatableValue result, Value input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            move(crb, masm, getResult(), getInput(), delayedControlTransfer);
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
     * Move between floating-point and general purpose register domain (WITHOUT VIS3).
     */
    @Opcode("MOVE")
    public static final class MoveFpGp extends SPARCLIRInstruction implements MoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MoveFpGp> TYPE = LIRInstructionClass.create(MoveFpGp.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;
        @Use({STACK}) protected StackSlotValue temp;

        public MoveFpGp(AllocatableValue result, AllocatableValue input, StackSlotValue temp) {
            super(TYPE);
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

    /**
     * Move between floating-point and general purpose register domain (WITH VIS3).
     */
    @Opcode("MOVE")
    public static final class MoveFpGpVIS3 extends SPARCLIRInstruction implements MoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MoveFpGpVIS3> TYPE = LIRInstructionClass.create(MoveFpGpVIS3.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        public MoveFpGpVIS3(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
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
    }

    @Opcode("STACKMOVE")
    public static final class SPARCStackMove extends SPARCLIRInstruction implements MoveOp, SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<SPARCStackMove> TYPE = LIRInstructionClass.create(SPARCStackMove.class);

        @Def({STACK}) protected AllocatableValue result;
        @Use({STACK, HINT}) protected Value input;

        public SPARCStackMove(AllocatableValue result, Value input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            try (ScratchRegister scratchReg = masm.getScratchRegister()) {
                Register scratch = scratchReg.getRegister();
                StackSlot intInput = reInterpret(asStackSlot(getInput()));
                StackSlot intResult = reInterpret(asStackSlot(getResult()));
                // move stack slot
                move(crb, masm, scratch.asValue(intInput.getLIRKind()), intInput, SPARCDelayedControlTransfer.DUMMY);
                move(crb, masm, intResult, scratch.asValue(intResult.getLIRKind()), delayedControlTransfer);
            }
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

    public abstract static class MemOp extends SPARCLIRInstruction implements ImplicitNullCheck {
        public static final LIRInstructionClass<MemOp> TYPE = LIRInstructionClass.create(MemOp.class);

        protected final PlatformKind kind;
        @Use({COMPOSITE}) protected SPARCAddressValue address;
        @State protected LIRFrameState state;

        public MemOp(LIRInstructionClass<? extends MemOp> c, PlatformKind kind, SPARCAddressValue address, LIRFrameState state) {
            super(c);
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

        @Def({REG}) protected AllocatableValue result;
        protected boolean signExtend;

        public LoadOp(Kind kind, AllocatableValue result, SPARCAddressValue address, LIRFrameState state) {
            this(kind, result, address, state, false);
        }

        public LoadOp(Kind kind, AllocatableValue result, SPARCAddressValue address, LIRFrameState state, boolean signExtend) {
            super(TYPE, kind, address, state);
            this.result = result;
            this.signExtend = signExtend;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitLoad(address.toAddress(), result, signExtend, kind, delayedControlTransfer, state, crb, masm);
        }
    }

    public static final class LoadAddressOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<LoadAddressOp> TYPE = LIRInstructionClass.create(LoadAddressOp.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE, UNINITIALIZED}) protected SPARCAddressValue addressValue;

        public LoadAddressOp(AllocatableValue result, SPARCAddressValue address) {
            super(TYPE);
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
            SPARCAddress addr = (SPARCAddress) crb.recordDataReferenceInCode(data, 16);
            assert addr == masm.getPlaceholder();
            final boolean forceRelocatable = true;
            Register dstReg = asRegister(result);
            new Setx(0, dstReg, forceRelocatable).emit(masm);
        }
    }

    public static final class MembarOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<MembarOp> TYPE = LIRInstructionClass.create(MembarOp.class);

        private final int barriers;

        public MembarOp(final int barriers) {
            super(TYPE);
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

        @Use({COMPOSITE}) protected SPARCAddressValue input;
        @State protected LIRFrameState state;

        public NullCheckOp(SPARCAddressValue input, LIRFrameState state) {
            super(TYPE);
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

        @Def({REG, HINT}) protected AllocatableValue result;
        @Alive({REG}) protected AllocatableValue address;
        @Alive({REG}) protected AllocatableValue cmpValue;
        @Use({REG}) protected AllocatableValue newValue;

        public CompareAndSwapOp(AllocatableValue result, AllocatableValue address, AllocatableValue cmpValue, AllocatableValue newValue) {
            super(TYPE);
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

        @Def({REG}) protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected StackSlotValue slot;

        public StackLoadAddressOp(AllocatableValue result, StackSlotValue address) {
            super(TYPE);
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

        @Use({REG}) protected AllocatableValue input;

        public StoreOp(Kind kind, SPARCAddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.input = input;
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            emitStore(input, address.toAddress(), kind, delayedControlTransfer, state, crb, masm);
        }
    }

    public static final class StoreConstantOp extends MemOp implements SPARCTailDelayedLIRInstruction {
        public static final LIRInstructionClass<StoreConstantOp> TYPE = LIRInstructionClass.create(StoreConstantOp.class);

        protected final JavaConstant input;

        public StoreConstantOp(Kind kind, SPARCAddressValue address, JavaConstant input, LIRFrameState state) {
            super(TYPE, kind, address, state);
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
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(crb, masm, result, input, delaySlotLir);
            } else if (isStackSlot(result)) {
                SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
                emitStore(input, resultAddress, input.getPlatformKind(), delaySlotLir, null, crb, masm);
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                SPARCAddress inputAddress = (SPARCAddress) crb.asAddress(input);
                emitLoad(inputAddress, result, false, input.getPlatformKind(), delaySlotLir, null, crb, masm);
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            JavaConstant constant = asConstant(input);
            if (isRegister(result)) {
                const2reg(crb, masm, result, constant, delaySlotLir);
            } else if (isStackSlot(result)) {
                if (constant.isDefaultForKind() || constant.isNull()) {
                    SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
                    emitStore(g0.asValue(LIRKind.derive(input)), resultAddress, input.getPlatformKind(), delaySlotLir, null, crb, masm);
                } else {
                    try (ScratchRegister sc = masm.getScratchRegister()) {
                        Register scratch = sc.getRegister();
                        long value = constant.asLong();
                        if (isSimm13(value)) {
                            masm.or(g0, (int) value, scratch);
                        } else {
                            new Setx(value, scratch).emit(masm);
                        }
                        SPARCAddress resultAddress = (SPARCAddress) crb.asAddress(result);
                        emitStore(scratch.asValue(LIRKind.derive(input)), resultAddress, input.getPlatformKind(), delaySlotLir, null, crb, masm);
                    }
                }
            } else {
                throw JVMCIError.shouldNotReachHere("Result is a: " + result);
            }
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
    }

    private static void reg2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, Value input, SPARCDelayedControlTransfer delaySlotLir) {
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

    private static void const2reg(CompilationResultBuilder crb, SPARCMacroAssembler masm, Value result, JavaConstant input, SPARCDelayedControlTransfer delaySlotLir) {
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            boolean hasVIS3 = ((SPARC) masm.target.arch).getFeatures().contains(CPUFeature.VIS3);
            Register resultRegister = asRegister(result);
            switch (input.getKind().getStackKind()) {
                case Int:
                    if (input.isDefaultForKind()) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.clr(resultRegister);
                    } else if (isSimm13(input.asLong())) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.or(g0, input.asInt(), resultRegister);
                    } else {
                        Setx set = new Setx(input.asLong(), resultRegister, false, true);
                        set.emitFirstPartOfDelayed(masm);
                        delaySlotLir.emitControlTransfer(crb, masm);
                        set.emitSecondPartOfDelayed(masm);
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
                        Setx setx = new Setx(input.asLong(), resultRegister, false, true);
                        setx.emitFirstPartOfDelayed(masm);
                        delaySlotLir.emitControlTransfer(crb, masm);
                        setx.emitSecondPartOfDelayed(masm);
                    }
                    break;
                case Float: {
                    float constant = input.asFloat();
                    int constantBits = java.lang.Float.floatToIntBits(constant);
                    if (constantBits == 0) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.fzeros(resultRegister);
                    } else {
                        if (hasVIS3) {
                            if (isSimm13(constantBits)) {
                                masm.or(g0, constantBits, scratch);
                            } else {
                                new Setx(constantBits, scratch, false).emit(masm);
                            }
                            delaySlotLir.emitControlTransfer(crb, masm);
                            // Now load the float value
                            masm.movwtos(scratch, resultRegister);
                        } else {
                            crb.asFloatConstRef(input);
                            // First load the address into the scratch register
                            new Setx(0, scratch, true).emit(masm);
                            // Now load the float value
                            delaySlotLir.emitControlTransfer(crb, masm);
                            masm.ldf(new SPARCAddress(scratch, 0), resultRegister);
                        }
                    }
                    break;
                }
                case Double: {
                    double constant = input.asDouble();
                    long constantBits = java.lang.Double.doubleToRawLongBits(constant);
                    if (constantBits == 0) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.fzerod(resultRegister);
                    } else {
                        if (hasVIS3) {
                            if (isSimm13(constantBits)) {
                                masm.or(g0, (int) constantBits, scratch);
                            } else {
                                new Setx(constantBits, scratch, false).emit(masm);
                            }
                            delaySlotLir.emitControlTransfer(crb, masm);
                            // Now load the float value
                            masm.movxtod(scratch, resultRegister);
                        } else {
                            crb.asDoubleConstRef(input);
                            // First load the address into the scratch register
                            new Setx(0, scratch, true).emit(masm);
                            delaySlotLir.emitControlTransfer(crb, masm);
                            // Now load the float value
                            masm.lddf(new SPARCAddress(scratch, 0), resultRegister);
                        }
                    }
                    break;
                }
                case Object:
                    if (input.isNull()) {
                        delaySlotLir.emitControlTransfer(crb, masm);
                        masm.clr(resultRegister);
                    } else if (crb.target.inlineObjects) {
                        crb.recordInlineDataInCode(input); // relocatable cannot be delayed
                        new Setx(0xDEADDEADDEADDEADL, resultRegister, true).emit(masm);
                    } else {
                        throw JVMCIError.unimplemented();
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

    private static void emitLoad(SPARCAddress address, Value result, boolean signExtend, PlatformKind kind, SPARCDelayedControlTransfer delayedControlTransfer, LIRFrameState state,
                    CompilationResultBuilder crb, SPARCMacroAssembler masm) {
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

    private static void emitStore(Value input, SPARCAddress address, PlatformKind kind, SPARCDelayedControlTransfer delayedControlTransfer, LIRFrameState state, CompilationResultBuilder crb,
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
}
