/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.UNINITIALIZED;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import java.util.function.Function;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.memory.MemoryExtendKind;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.core.common.type.DataPointerConstant;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.StandardOp.NullCheck;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.MemoryBarriers;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

public class AArch64Move {

    public static class LoadInlineConstant extends AArch64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<LoadInlineConstant> TYPE = LIRInstructionClass.create(LoadInlineConstant.class);

        private JavaConstant constant;
        @Def({REG, STACK}) AllocatableValue result;

        public LoadInlineConstant(JavaConstant constant, AllocatableValue result) {
            super(TYPE);
            this.constant = constant;
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Kind moveKind = (AArch64Kind) result.getPlatformKind();
            if (isRegister(result)) {
                const2reg(moveKind, crb, masm, asRegister(result), constant);
            } else if (isStackSlot(result)) {
                const2stack(moveKind, crb, masm, asStackSlot(result), constant);
            }
        }

        @Override
        public Constant getConstant() {
            return constant;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    @Opcode("MOVE")
    public static class Move extends AArch64LIRInstruction implements ValueMoveOp {
        public static final LIRInstructionClass<Move> TYPE = LIRInstructionClass.create(Move.class);

        @Def({REG, STACK, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue input;

        private AArch64Kind moveKind;

        public Move(AArch64Kind moveKind, AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.moveKind = moveKind;
            int resultSize = result.getPlatformKind().getSizeInBytes();
            int inputSize = input.getPlatformKind().getSizeInBytes();
            assert resultSize == moveKind.getSizeInBytes() && resultSize <= inputSize;
            assert !(isStackSlot(result) && isStackSlot(input));
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            move(moveKind, crb, masm, getResult(), getInput());
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

    public static class LoadAddressOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<LoadAddressOp> TYPE = LIRInstructionClass.create(LoadAddressOp.class);

        @Def protected AllocatableValue result;
        @Use(COMPOSITE) protected AArch64AddressValue address;

        public LoadAddressOp(AllocatableValue result, AArch64AddressValue address) {
            super(TYPE);
            this.result = result;
            this.address = address;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            masm.loadAddress(dst, address.toAddress());
        }
    }

    public static class LoadDataOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<LoadDataOp> TYPE = LIRInstructionClass.create(LoadDataOp.class);

        @Def protected AllocatableValue result;
        private final DataPointerConstant data;

        public LoadDataOp(AllocatableValue result, DataPointerConstant data) {
            super(TYPE);
            this.result = result;
            this.data = data;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            crb.recordDataReferenceInCode(data);
            masm.adrpAdd(dst);
        }
    }

    public static class StackLoadAddressOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<StackLoadAddressOp> TYPE = LIRInstructionClass.create(StackLoadAddressOp.class);

        @Def protected AllocatableValue result;
        @Use({STACK, UNINITIALIZED}) protected AllocatableValue slot;

        public StackLoadAddressOp(AllocatableValue result, AllocatableValue slot) {
            super(TYPE);
            assert slot instanceof VirtualStackSlot || slot instanceof StackSlot;
            this.result = result;
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            /* Address of slot in stack will be sp + displacement. */
            int displacement = crb.frameMap.offsetForStackSlot((StackSlot) slot);
            masm.add(64, asRegister(result), sp, displacement);
        }
    }

    public static class MembarOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<MembarOp> TYPE = LIRInstructionClass.create(MembarOp.class);

        private final int barriers;

        public MembarOp(int barriers) {
            super(TYPE);
            this.barriers = barriers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert barriers >= MemoryBarriers.LOAD_LOAD && barriers <= (MemoryBarriers.STORE_STORE | MemoryBarriers.STORE_LOAD | MemoryBarriers.LOAD_STORE | MemoryBarriers.LOAD_LOAD);
            switch (barriers) {
                case MemoryBarriers.STORE_STORE:
                    masm.dmb(AArch64MacroAssembler.BarrierKind.STORE_STORE);
                    break;
                case MemoryBarriers.LOAD_LOAD:
                case MemoryBarriers.LOAD_STORE:
                case MemoryBarriers.LOAD_LOAD | MemoryBarriers.LOAD_STORE:
                    masm.dmb(AArch64MacroAssembler.BarrierKind.LOAD_ANY);
                    break;
                default:
                    masm.dmb(AArch64MacroAssembler.BarrierKind.ANY_ANY);
                    break;
            }
        }
    }

    abstract static class MemOp extends AArch64LIRInstruction implements StandardOp.ImplicitNullCheck {

        protected final AArch64Kind accessKind;
        @Use({COMPOSITE}) protected AArch64AddressValue addressValue;
        @State protected LIRFrameState state;

        MemOp(LIRInstructionClass<? extends MemOp> c, AArch64Kind accessKind, AArch64AddressValue address, LIRFrameState state) {
            super(c);

            int size = address.getBitMemoryTransferSize();
            assert size == AArch64Address.ANY_SIZE || size == accessKind.getSizeInBytes() * Byte.SIZE;

            this.accessKind = accessKind;
            this.addressValue = address;
            this.state = state;
        }

        /**
         * Emit the memory access.
         *
         * @return The buffer position before the memory access was issued. Normally this will be
         *         the same as the position of the memory access; however, if the memory access was
         *         merged with a previous access, then it will be the position after the access.
         */
        protected abstract int emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm);

        /**
         * Checks whether the current memory access could be merged with the prior memory access.
         */
        protected boolean mergingAllowed(CompilationResultBuilder crb, int memPosition) {
            return state == null || crb.getLastImplicitExceptionOffset() == memPosition - 4;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int memPosition = emitMemAccess(crb, masm);
            if (state != null) {
                // Adjust implicit exception position if a ldr/str has been merged into a ldp/stp.
                if (memPosition == masm.position()) {
                    /*
                     * If two memory accesses are merged, it is not valid for only the second memory
                     * access to be an implicit exception; in this case then the first memory access
                     * would never execute if the merged instruction caused an exception.
                     */
                    GraalError.guarantee(masm.isImmLoadStoreMerged() && crb.getLastImplicitExceptionOffset() == memPosition - 4, "Missing state for implicit exception.");
                    /* Only first memory access's state should be tied to the implicit exception. */
                    return;
                }
                crb.recordImplicitException(memPosition, state);
            }
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            int displacement = addressValue.getDisplacement();
            if (state == null && value.equals(addressValue.getBase()) && addressValue.getOffset().equals(Value.ILLEGAL) && displacement >= 0 && displacement < implicitNullCheckLimit) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    public enum ExtendKind {
        NONE,
        ZERO_EXTEND,
        SIGN_EXTEND;
    }

    abstract static class ExtendableLoadOp extends MemOp {

        @Def({REG}) protected AllocatableValue result;

        protected final MemoryExtendKind extend;

        ExtendableLoadOp(LIRInstructionClass<? extends ExtendableLoadOp> c, AArch64Kind kind, MemoryExtendKind extend, AllocatableValue result, AArch64AddressValue address,
                        LIRFrameState state) {
            super(c, kind, address, state);
            this.extend = extend;
            this.result = result;
        }
    }

    public static final class LoadOp extends ExtendableLoadOp {
        public static final LIRInstructionClass<LoadOp> TYPE = LIRInstructionClass.create(LoadOp.class);

        public LoadOp(AArch64Kind accessKind, MemoryExtendKind extendKind, AllocatableValue result, AArch64AddressValue address, LIRFrameState state) {
            super(TYPE, accessKind, extendKind, result, address, state);
        }

        @Override
        protected int emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Address address = addressValue.toAddress();
            Register dst = asRegister(result);

            int srcBitSize = accessKind.getSizeInBytes() * Byte.SIZE;
            int dstBitSize;
            if (extend.isNotExtended()) {
                dstBitSize = srcBitSize;
            } else {
                assert accessKind.isInteger();
                dstBitSize = extend.getExtendedBitSize();
                assert dstBitSize >= srcBitSize;
            }
            int memPosition = masm.position();
            boolean tryMerge = mergingAllowed(crb, memPosition);
            if (accessKind.isInteger()) {
                switch (extend) {
                    case DEFAULT:
                        masm.ldr(srcBitSize, dst, address, tryMerge);
                        break;
                    case ZERO_16:
                    case ZERO_32:
                    case ZERO_64:
                        // ldr zeros out remaining bits
                        masm.ldr(srcBitSize, dst, address, tryMerge);
                        break;
                    case SIGN_16:
                    case SIGN_32:
                    case SIGN_64:
                        /*
                         * ldrs will sign extend value to required length. Note ldrs must be extend
                         * to at least 32 bits
                         */
                        masm.ldrs(Integer.max(dstBitSize, 32), srcBitSize, dst, address);
                        break;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            } else {
                // floating point or vector access
                assert srcBitSize == result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
                masm.fldr(srcBitSize, dst, address, tryMerge);
            }
            return memPosition;
        }
    }

    public static final class LoadAcquireOp extends ExtendableLoadOp {
        public static final LIRInstructionClass<LoadAcquireOp> TYPE = LIRInstructionClass.create(LoadAcquireOp.class);

        public LoadAcquireOp(AArch64Kind accessKind, MemoryExtendKind extendKind, AllocatableValue result, AArch64AddressValue address, LIRFrameState state) {
            super(TYPE, accessKind, extendKind, result, address, state);
        }

        @Override
        protected int emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int srcBitSize = accessKind.getSizeInBytes() * Byte.SIZE;
            if (extend.isExtended()) {
                assert accessKind.isInteger();
                assert extend.isZeroExtend();
                assert extend.getExtendedBitSize() >= srcBitSize;
            }

            Register dst = asRegister(result);

            try (ScratchRegister scratch1 = masm.getScratchRegister()) {
                AArch64Address address = addressValue.toAddress();
                final Register addrReg;
                if (address.getAddressingMode() == AArch64Address.AddressingMode.BASE_REGISTER_ONLY) {
                    // Can directly use the base register as the address
                    addrReg = address.getBase();
                } else {
                    addrReg = scratch1.getRegister();
                    masm.loadAddress(addrReg, address);
                }
                int memPosition = masm.position();
                if (accessKind.isInteger()) {
                    masm.ldar(srcBitSize, dst, addrReg);
                } else {
                    // floating point access
                    assert srcBitSize == result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
                    try (ScratchRegister scratch2 = masm.getScratchRegister()) {
                        Register temp = scratch2.getRegister();
                        masm.ldar(srcBitSize, temp, addrReg);
                        masm.fmov(srcBitSize, dst, temp);
                    }
                }
                return memPosition;
            }
        }

        public AArch64Kind getAccessKind() {
            return accessKind;
        }
    }

    public static final class StoreZeroOp extends MemOp {
        public static final LIRInstructionClass<StoreZeroOp> TYPE = LIRInstructionClass.create(StoreZeroOp.class);

        public StoreZeroOp(AArch64Kind accessKind, AArch64AddressValue address, LIRFrameState state) {
            super(TYPE, accessKind, address, state);
            assert accessKind.getSizeInBytes() <= Long.BYTES;
        }

        @Override
        public int emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int destSize = accessKind.getSizeInBytes() * Byte.SIZE;
            AArch64Address address = addressValue.toAddress();
            int memPosition = masm.position();
            masm.str(destSize, zr, address, mergingAllowed(crb, memPosition));
            return memPosition;
        }
    }

    /**
     * This helper method moves sp to a scratch gp register before generating the store code. This
     * is needed because store instructions cannot directly refer to the sp register. This is
     * because both ZR and SP are encoded as '31'.
     */
    public static int moveSPAndEmitStore(AArch64MacroAssembler masm, Register input, Function<Register, Integer> storeGen) {
        if (input.equals(sp)) {
            try (ScratchRegister scratch = masm.getScratchRegister()) {
                Register newInput = scratch.getRegister();
                masm.mov(64, newInput, sp);
                return storeGen.apply(newInput);
            }
        } else {
            return storeGen.apply(input);
        }
    }

    public static final class StoreOp extends MemOp {
        public static final LIRInstructionClass<StoreOp> TYPE = LIRInstructionClass.create(StoreOp.class);
        @Use protected AllocatableValue input;

        public StoreOp(AArch64Kind accessKind, AArch64AddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, accessKind, address, state);
            this.input = input;
        }

        @Override
        protected int emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int destSize = accessKind.getSizeInBytes() * Byte.SIZE;
            AArch64Address address = addressValue.toAddress();
            return moveSPAndEmitStore(masm, asRegister(input), src -> {
                int memPosition = masm.position();
                boolean tryMerge = mergingAllowed(crb, memPosition);
                if (accessKind.isInteger()) {
                    masm.str(destSize, src, address, tryMerge);
                } else {
                    masm.fstr(destSize, src, address, tryMerge);
                }
                return memPosition;
            });
        }
    }

    public static final class StoreReleaseOp extends MemOp {
        public static final LIRInstructionClass<StoreReleaseOp> TYPE = LIRInstructionClass.create(StoreReleaseOp.class);
        @Use protected AllocatableValue input;

        public StoreReleaseOp(AArch64Kind accessKind, AArch64AddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, accessKind, address, state);
            this.input = input;
        }

        @Override
        protected int emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int destSize = accessKind.getSizeInBytes() * Byte.SIZE;

            return moveSPAndEmitStore(masm, asRegister(input), src -> {
                try (ScratchRegister scratch1 = masm.getScratchRegister()) {
                    AArch64Address address = addressValue.toAddress();
                    final Register addrReg;
                    if (address.getAddressingMode() == AArch64Address.AddressingMode.BASE_REGISTER_ONLY) {
                        // Can directly use the base register as the address
                        addrReg = address.getBase();
                    } else {
                        addrReg = scratch1.getRegister();
                        masm.loadAddress(addrReg, address);
                    }
                    int memPosition;
                    if (accessKind.isInteger()) {
                        memPosition = masm.position();
                        masm.stlr(destSize, src, addrReg);
                    } else {
                        try (ScratchRegister scratch2 = masm.getScratchRegister()) {
                            Register temp = scratch2.getRegister();
                            masm.fmov(destSize, temp, src);
                            memPosition = masm.position();
                            masm.stlr(destSize, temp, addrReg);
                        }
                    }
                    return memPosition;
                }
            });
        }

        public AArch64Kind getAccessKind() {
            return accessKind;
        }
    }

    public static final class NullCheckOp extends AArch64LIRInstruction implements NullCheck {
        public static final LIRInstructionClass<NullCheckOp> TYPE = LIRInstructionClass.create(NullCheckOp.class);

        @Use(COMPOSITE) protected AArch64AddressValue address;
        @State protected LIRFrameState state;

        public NullCheckOp(AArch64AddressValue address, LIRFrameState state) {
            super(TYPE);
            this.address = address;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int loadPosition = masm.position();
            masm.deadLoad(64, address.toAddress(), crb.getLastImplicitExceptionOffset() == loadPosition - 4);
            // Adjust implicit exception position if this ldr has been merged to ldp.
            if (loadPosition == masm.position()) {
                /*
                 * If two memory accesses are merged, it is not valid for only the second memory
                 * access to be an implicit exception; in this case then the first memory access
                 * would never execute if the merged instruction caused an exception.
                 */
                GraalError.guarantee(masm.isImmLoadStoreMerged() && crb.getLastImplicitExceptionOffset() == loadPosition - 4, "Missing state for implicit exception.");
                /* Only first memory access's state should be tied to the implicit exception. */
                return;
            }
            crb.recordImplicitException(loadPosition, state);
        }

        @Override
        public Value getCheckedValue() {
            return address.base;
        }

        @Override
        public LIRFrameState getState() {
            return state;
        }
    }

    public static void move(AArch64Kind moveKind, CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, Value input) {
        if (isRegister(input)) {
            Register src = asRegister(input);
            if (isRegister(result)) {
                reg2reg(moveKind, masm, asRegister(result), src);
            } else if (isStackSlot(result)) {
                reg2stack(moveKind, crb, masm, asStackSlot(result), src);
            } else {
                throw GraalError.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            StackSlot src = asStackSlot(input);
            if (isRegister(result)) {
                stack2reg(moveKind, crb, masm, asRegister(result), src);
            } else if (isStackSlot(result)) {
                stack2stack(moveKind, crb, masm, asStackSlot(result), src);
            } else {
                throw GraalError.shouldNotReachHere();
            }
        } else if (isJavaConstant(input)) {
            if (isRegister(result)) {
                const2reg(moveKind, crb, masm, asRegister(result), asJavaConstant(input));
            } else {
                throw GraalError.shouldNotReachHere();
            }
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    private static void stack2stack(AArch64Kind moveKind, CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot result, StackSlot input) {
        try (ScratchRegister r1 = masm.getScratchRegister()) {
            try (ScratchRegister r2 = masm.getScratchRegister()) {
                Register rscratch1 = r1.getRegister();
                Register rscratch2 = r2.getRegister();

                // Always perform stack -> stack copies through integer registers
                crb.blockComment("[stack -> stack copy]");
                int loadSize = determineStackSlotLoadSize(moveKind, input, rscratch1);
                AArch64Address src = createStackSlotLoadAddress(loadSize, crb, masm, input, rscratch2);
                masm.ldr(loadSize, rscratch1, src);
                int storeSize = moveKind.getSizeInBytes() * Byte.SIZE;
                AArch64Address dst = createStackSlotStoreAddress(storeSize, crb, masm, result, rscratch2);
                masm.str(storeSize, rscratch1, dst);
            }
        }
    }

    private static void reg2reg(AArch64Kind moveKind, AArch64MacroAssembler masm, Register result, Register input) {
        if (input.equals(result)) {
            return;
        }
        final int size = moveKind.getSizeInBytes() * Byte.SIZE;
        assert size == 32 || size == 64 || size == 128;
        if (result.getRegisterCategory().equals(CPU) && input.getRegisterCategory().equals(CPU)) {
            masm.mov(size, result, input);
        } else if (size == 128) {
            assert result.getRegisterCategory().equals(SIMD) && input.getRegisterCategory().equals(SIMD);
            masm.neon.moveVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, result, input);
        } else {
            masm.fmov(size, result, input);
        }
    }

    static void reg2stack(AArch64Kind moveKind, CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot result, Register input) {
        try (ScratchRegister scratch = masm.getScratchRegister()) {
            final int size = moveKind.getSizeInBytes() * Byte.SIZE;
            AArch64Address dest = createStackSlotStoreAddress(size, crb, masm, result, scratch.getRegister());
            if (input.getRegisterCategory().equals(CPU)) {
                masm.str(size, input, dest);
            } else {
                assert input.getRegisterCategory().equals(SIMD);
                masm.fstr(size, input, dest);
            }
        }
    }

    static void stack2reg(AArch64Kind moveKind, CompilationResultBuilder crb, AArch64MacroAssembler masm, Register result, StackSlot input) {
        int size = determineStackSlotLoadSize(moveKind, input, result);
        if (result.getRegisterCategory().equals(CPU)) {
            AArch64Address src = createStackSlotLoadAddress(size, crb, masm, input, result);
            masm.ldr(size, result, src);
        } else {
            assert result.getRegisterCategory().equals(SIMD);
            try (ScratchRegister sc = masm.getScratchRegister()) {
                AArch64Address src = createStackSlotLoadAddress(size, crb, masm, input, sc.getRegister());
                masm.fldr(size, result, src);
            }
        }
    }

    static void const2reg(AArch64Kind moveKind, CompilationResultBuilder crb, AArch64MacroAssembler masm, Register result, JavaConstant input) {
        JavaKind stackKind = input.getJavaKind().getStackKind();
        assert stackKind.isObject() || moveKind.getSizeInBytes() <= stackKind.getByteCount();
        switch (stackKind) {
            case Int:
                masm.mov(result, input.asInt());
                break;
            case Long:
                masm.mov(result, input.asLong());
                break;
            case Float:
                if (AArch64MacroAssembler.isFloatImmediate(input.asFloat()) && result.getRegisterCategory().equals(SIMD)) {
                    masm.fmov(32, result, input.asFloat());
                } else if (result.getRegisterCategory().equals(CPU)) {
                    masm.mov(result, Float.floatToRawIntBits(input.asFloat()));
                } else {
                    try (ScratchRegister scr = masm.getScratchRegister()) {
                        Register scratch = scr.getRegister();
                        masm.mov(scratch, Float.floatToRawIntBits(input.asFloat()));
                        masm.fmov(32, result, scratch);
                    }
                }
                break;
            case Double:
                if (AArch64MacroAssembler.isDoubleImmediate(input.asDouble()) && result.getRegisterCategory().equals(SIMD)) {
                    masm.fmov(64, result, input.asDouble());
                } else if (result.getRegisterCategory().equals(CPU)) {
                    masm.mov(result, Double.doubleToRawLongBits(input.asDouble()));
                } else {
                    try (ScratchRegister scr = masm.getScratchRegister()) {
                        Register scratch = scr.getRegister();
                        masm.mov(scratch, Double.doubleToRawLongBits(input.asDouble()));
                        masm.fmov(64, result, scratch);
                    }
                }
                break;
            case Object:
                if (input.isNull()) {
                    if (crb.mustReplaceWithUncompressedNullRegister(input)) {
                        masm.mov(64, result, crb.uncompressedNullRegister);
                    } else {
                        masm.mov(result, 0);
                    }
                } else if (crb.target.inlineObjects) {
                    crb.recordInlineDataInCode(input);
                    masm.mov(result, 0xDEADDEADDEADDEADL, true);
                } else {
                    crb.recordDataReferenceInCode(input, 8);
                    masm.adrpLdr(64, result, result);
                }
                break;
            default:
                throw GraalError.shouldNotReachHere("kind=" + input.getJavaKind().getStackKind());
        }
    }

    static void const2stack(AArch64Kind moveKind, CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot result, JavaConstant constant) {
        if (constant.isNull() && !crb.mustReplaceWithUncompressedNullRegister(constant)) {
            reg2stack(moveKind, crb, masm, result, zr);
        } else {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                const2reg(moveKind, crb, masm, scratch, constant);
                reg2stack(moveKind, crb, masm, result, scratch);
            }
        }
    }

    /**
     * Determines the optimal load size to use with given stack slot.
     *
     * Due to AArch64ArithmeticLIRGenerator.emitNarrow, which creates a move from a QWORD to DWORD,
     * and CastValue, which reads a subset of a value's bits, it is possible for a load to be
     * smaller than the underlying stack slot size. When this happens, it is better load the entire
     * stack slot, as this allows an immediate addressing mode to be used more often.
     */
    private static int determineStackSlotLoadSize(AArch64Kind originalLoadKind, StackSlot slot, Register targetReg) {
        int slotBitSize = slot.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        int accessBitSize = originalLoadKind.getSizeInBytes() * Byte.SIZE;
        assert accessBitSize <= slotBitSize;
        // maximum load size of GP regs is 64 bits
        assert targetReg.getRegisterCategory().equals(SIMD) || accessBitSize <= Long.SIZE;
        if (accessBitSize == slotBitSize || targetReg.getRegisterCategory().equals(SIMD)) {
            return slotBitSize;
        } else {
            assert targetReg.getRegisterCategory().equals(CPU);
            // maximum load size of GP regs is 64 bits
            return Integer.min(slotBitSize, Long.SIZE);
        }
    }

    private static AArch64Address createStackSlotLoadAddress(int size, CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot slot, Register scratchReg) {
        return createStackSlotAddress(size, crb, masm, slot, scratchReg, true);
    }

    private static AArch64Address createStackSlotStoreAddress(int size, CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot slot, Register scratchReg) {
        return createStackSlotAddress(size, crb, masm, slot, scratchReg, false);
    }

    /**
     * Returns AArch64Address of given StackSlot.
     *
     * @param scratchReg Scratch register that can be used to load address.
     * @param isLoad whether the address will be used as part of a load or store
     * @return AArch64Address of given StackSlot. Uses scratch register if necessary to do so.
     */
    private static AArch64Address createStackSlotAddress(int size, CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot slot, Register scratchReg, boolean isLoad) {
        int displacement = crb.frameMap.offsetForStackSlot(slot);
        // Ensure memory access does not exceed the stack slot size.
        if (isLoad) {
            assert size <= slot.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        } else {
            assert size == slot.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        }
        return masm.makeAddress(size, sp, displacement, scratchReg);
    }

    public abstract static class PointerCompressionOp extends AArch64LIRInstruction {

        @Def({REG, HINT}) private AllocatableValue result;
        @Use({REG, CONST}) private Value input;
        @Alive({REG, ILLEGAL, UNINITIALIZED}) private AllocatableValue baseRegister;

        protected final CompressEncoding encoding;
        protected final boolean nonNull;
        protected final LIRKindTool lirKindTool;

        protected PointerCompressionOp(LIRInstructionClass<? extends PointerCompressionOp> type, AllocatableValue result, Value input,
                        AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {

            super(type);
            this.result = result;
            this.input = input;
            this.baseRegister = baseRegister;
            this.encoding = encoding;
            this.nonNull = nonNull;
            this.lirKindTool = lirKindTool;
        }

        public static boolean hasBase(CompressEncoding encoding) {
            return encoding.hasBase();
        }

        public final Value getInput() {
            return input;
        }

        public final AllocatableValue getResult() {
            return result;
        }

        protected final Register getResultRegister() {
            return asRegister(result);
        }

        protected final Register getBaseRegister() {
            return hasBase(encoding) ? asRegister(baseRegister) : Register.None;
        }

        protected final int getShift() {
            return encoding.getShift();
        }
    }

    public static class CompressPointerOp extends PointerCompressionOp {
        public static final LIRInstructionClass<CompressPointerOp> TYPE = LIRInstructionClass.create(CompressPointerOp.class);

        public CompressPointerOp(AllocatableValue result, Value input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            this(TYPE, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        private CompressPointerOp(LIRInstructionClass<? extends PointerCompressionOp> type, AllocatableValue result, Value input,
                        AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {

            super(type, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register resultRegister = getResultRegister();
            Register ptr = asRegister(getInput());
            Register base = getBaseRegister();
            // result = (ptr - base) >> shift
            if (!encoding.hasBase()) {
                if (encoding.hasShift()) {
                    masm.lsr(64, resultRegister, ptr, encoding.getShift());
                } else {
                    masm.mov(64, resultRegister, ptr);
                }
            } else if (nonNull) {
                masm.sub(64, resultRegister, ptr, base);
                if (encoding.hasShift()) {
                    masm.lsr(64, resultRegister, resultRegister, encoding.getShift());
                }
            } else {
                // if ptr is null it still has to be null after compression
                masm.compare(64, ptr, 0);
                masm.csel(64, resultRegister, ptr, base, AArch64Assembler.ConditionFlag.NE);
                masm.sub(64, resultRegister, resultRegister, base);
                if (encoding.hasShift()) {
                    masm.lsr(64, resultRegister, resultRegister, encoding.getShift());
                }
            }
        }
    }

    public static class UncompressPointerOp extends PointerCompressionOp {
        public static final LIRInstructionClass<UncompressPointerOp> TYPE = LIRInstructionClass.create(UncompressPointerOp.class);

        public UncompressPointerOp(AllocatableValue result, Value input, AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            this(TYPE, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        private UncompressPointerOp(LIRInstructionClass<? extends PointerCompressionOp> type, AllocatableValue result, Value input,
                        AllocatableValue baseRegister, CompressEncoding encoding, boolean nonNull, LIRKindTool lirKindTool) {
            super(type, result, input, baseRegister, encoding, nonNull, lirKindTool);
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register inputRegister = asRegister(getInput());
            Register resultRegister = getResultRegister();
            Register base = encoding.hasBase() ? getBaseRegister() : null;

            // result = base + (ptr << shift)
            if (nonNull || base == null) {
                masm.add(64, resultRegister, base == null ? zr : base, inputRegister, AArch64Assembler.ShiftType.LSL, encoding.getShift());
            } else {
                // if ptr is null it has to be null after decompression
                Label done = new Label();
                if (!resultRegister.equals(inputRegister)) {
                    masm.mov(32, resultRegister, inputRegister);
                }
                masm.cbz(32, resultRegister, done);
                masm.add(64, resultRegister, base, resultRegister, AArch64Assembler.ShiftType.LSL, encoding.getShift());
                masm.bind(done);
            }
        }
    }

    private abstract static class ZeroNullConversionOp extends AArch64LIRInstruction {
        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        protected ZeroNullConversionOp(LIRInstructionClass<? extends ZeroNullConversionOp> type, AllocatableValue result, AllocatableValue input) {
            super(type);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register nullRegister = crb.uncompressedNullRegister;
            if (!nullRegister.equals(Register.None)) {
                emitConversion(asRegister(result), asRegister(input), nullRegister, masm);
            }
        }

        protected abstract void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AArch64MacroAssembler masm);
    }

    public static class ConvertNullToZeroOp extends ZeroNullConversionOp {
        public static final LIRInstructionClass<ConvertNullToZeroOp> TYPE = LIRInstructionClass.create(ConvertNullToZeroOp.class);

        public ConvertNullToZeroOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE, result, input);
        }

        @Override
        protected final void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AArch64MacroAssembler masm) {
            masm.cmp(64, inputRegister, nullRegister);
            masm.csel(64, resultRegister, zr, inputRegister, AArch64Assembler.ConditionFlag.EQ);
        }
    }

    public static class ConvertZeroToNullOp extends ZeroNullConversionOp {
        public static final LIRInstructionClass<ConvertZeroToNullOp> TYPE = LIRInstructionClass.create(ConvertZeroToNullOp.class);

        public ConvertZeroToNullOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE, result, input);
        }

        @Override
        protected final void emitConversion(Register resultRegister, Register inputRegister, Register nullRegister, AArch64MacroAssembler masm) {
            masm.cmp(64, inputRegister, zr);
            masm.csel(64, resultRegister, nullRegister, inputRegister, AArch64Assembler.ConditionFlag.EQ);
        }
    }

}
