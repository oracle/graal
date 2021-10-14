/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
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

        protected abstract void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm);

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int prePosition = masm.position();
            emitMemAccess(crb, masm);
            if (state != null) {
                int implicitExceptionPosition = prePosition;
                // Adjust implicit exception position if this ldr/str has been merged to ldp/stp.
                if (prePosition == masm.position() && masm.isImmLoadStoreMerged()) {
                    implicitExceptionPosition = prePosition - 4;
                    if (crb.isImplicitExceptionExist(implicitExceptionPosition)) {
                        return;
                    }
                }
                crb.recordImplicitException(implicitExceptionPosition, state);
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

        protected int dstBitSize;
        protected ExtendKind extend;

        ExtendableLoadOp(LIRInstructionClass<? extends ExtendableLoadOp> c, AArch64Kind kind, int dstBitSize, ExtendKind extend, AllocatableValue result, AArch64AddressValue address,
                        LIRFrameState state) {
            super(c, kind, address, state);
            this.dstBitSize = dstBitSize;
            this.extend = extend;
            this.result = result;
        }
    }

    public static final class LoadOp extends ExtendableLoadOp {
        public static final LIRInstructionClass<LoadOp> TYPE = LIRInstructionClass.create(LoadOp.class);

        public LoadOp(AArch64Kind accessKind, AllocatableValue result, AArch64AddressValue address, LIRFrameState state) {
            this(accessKind, accessKind.getSizeInBytes() * Byte.SIZE, ExtendKind.NONE, result, address, state);
        }

        public LoadOp(AArch64Kind accessKind, int dstBitSize, ExtendKind extend, AllocatableValue result, AArch64AddressValue address, LIRFrameState state) {
            super(TYPE, accessKind, dstBitSize, extend, result, address, state);
        }

        @Override
        protected void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Address address = addressValue.toAddress();
            Register dst = asRegister(result);

            int srcBitSize = accessKind.getSizeInBytes() * Byte.SIZE;
            if (accessKind.isInteger()) {
                switch (extend) {
                    case NONE:
                        assert dstBitSize == srcBitSize;
                        masm.ldr(srcBitSize, dst, address);
                        break;
                    case ZERO_EXTEND:
                        assert dstBitSize >= srcBitSize;
                        // ldr zeros out remaining bits
                        masm.ldr(srcBitSize, dst, address);
                        break;
                    case SIGN_EXTEND:
                        assert dstBitSize >= srcBitSize;
                        // ldrs will sign extend value to required length
                        masm.ldrs(dstBitSize, srcBitSize, dst, address);
                        break;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
            } else {
                assert extend == ExtendKind.NONE;
                assert srcBitSize == dstBitSize && dstBitSize == result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
                masm.fldr(srcBitSize, dst, address);
            }
        }
    }

    public static final class VolatileLoadOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VolatileLoadOp> TYPE = LIRInstructionClass.create(VolatileLoadOp.class);
        protected final AArch64Kind kind;
        @State protected LIRFrameState state;
        @Def protected AllocatableValue result;
        @Use protected AllocatableValue address;

        public VolatileLoadOp(AArch64Kind kind, AllocatableValue result, AllocatableValue address, LIRFrameState state) {
            super(TYPE);
            this.kind = kind;
            this.result = result;
            this.address = address;
            this.state = state;
            if (state != null) {
                throw GraalError.shouldNotReachHere("Can't handle implicit null check");
            }
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int srcSize = kind.getSizeInBytes() * Byte.SIZE;
            int destSize = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            if (kind.isInteger()) {
                masm.ldar(srcSize, asRegister(result), asRegister(address));
            } else {
                assert srcSize == destSize;
                try (ScratchRegister r1 = masm.getScratchRegister()) {
                    Register rscratch1 = r1.getRegister();
                    masm.ldar(srcSize, rscratch1, asRegister(address));
                    masm.fmov(destSize, asRegister(result), rscratch1);
                }
            }
        }

        public AArch64Kind getKind() {
            return kind;
        }
    }

    public static class StoreOp extends MemOp {
        public static final LIRInstructionClass<StoreOp> TYPE = LIRInstructionClass.create(StoreOp.class);
        @Use protected AllocatableValue input;

        public StoreOp(AArch64Kind kind, AArch64AddressValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.input = input;
        }

        @Override
        protected void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            emitStore(crb, masm, accessKind, addressValue.toAddress(), input);
        }
    }

    public static final class StoreConstantOp extends MemOp {
        public static final LIRInstructionClass<StoreConstantOp> TYPE = LIRInstructionClass.create(StoreConstantOp.class);

        protected final JavaConstant input;

        public StoreConstantOp(AArch64Kind kind, AArch64AddressValue address, JavaConstant input, LIRFrameState state) {
            super(TYPE, kind, address, state);
            this.input = input;
            if (!input.isDefaultForKind()) {
                throw GraalError.shouldNotReachHere("Can only store null constants to memory");
            }
        }

        @Override
        public void emitMemAccess(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            emitStore(crb, masm, accessKind, addressValue.toAddress(), zr.asValue(LIRKind.combine(addressValue)));
        }
    }

    public static class VolatileStoreOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VolatileStoreOp> TYPE = LIRInstructionClass.create(VolatileStoreOp.class);
        protected final AArch64Kind kind;
        @State protected LIRFrameState state;
        @Use protected AllocatableValue input;
        @Use protected AllocatableValue address;

        public VolatileStoreOp(AArch64Kind kind, AllocatableValue address, AllocatableValue input, LIRFrameState state) {
            super(TYPE);
            this.kind = kind;
            this.address = address;
            this.input = input;
            this.state = state;
            if (state != null) {
                throw GraalError.shouldNotReachHere("Can't handle implicit null check");
            }
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int destSize = kind.getSizeInBytes() * Byte.SIZE;
            if (kind.isInteger()) {
                masm.stlr(destSize, asRegister(input), asRegister(address));
            } else {
                try (ScratchRegister r1 = masm.getScratchRegister()) {
                    Register rscratch1 = r1.getRegister();
                    masm.fmov(destSize, rscratch1, asRegister(input));
                    masm.stlr(destSize, rscratch1, asRegister(address));
                }
            }
        }

        public AArch64Kind getKind() {
            return kind;
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
            int prePosition = masm.position();
            masm.ldr(64, zr, address.toAddress());
            int implicitExceptionPosition = prePosition;
            // Adjust implicit exception position if this ldr has been merged to ldp.
            if (prePosition == masm.position() && masm.isImmLoadStoreMerged()) {
                implicitExceptionPosition = prePosition - 4;
                if (crb.isImplicitExceptionExist(implicitExceptionPosition)) {
                    return;
                }
            }
            crb.recordImplicitException(implicitExceptionPosition, state);
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

    private static void emitStore(@SuppressWarnings("unused") CompilationResultBuilder crb, AArch64MacroAssembler masm, AArch64Kind kind, AArch64Address dst, Value src) {
        int destSize = kind.getSizeInBytes() * Byte.SIZE;
        if (kind.isInteger()) {
            masm.str(destSize, asRegister(src), dst);
        } else {
            masm.fstr(destSize, asRegister(src), dst);
        }
    }

    private static void move(AArch64Kind moveKind, CompilationResultBuilder crb, AArch64MacroAssembler masm, AllocatableValue result, Value input) {
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

                final int size = moveKind.getSizeInBytes() * Byte.SIZE;
                // Always perform stack -> stack copies through integer registers
                crb.blockComment("[stack -> stack copy]");
                AArch64Address src = loadStackSlotAddress(size, crb, masm, input, rscratch2);
                masm.ldr(size, rscratch1, src);
                AArch64Address dst = loadStackSlotAddress(size, crb, masm, result, rscratch2);
                masm.str(size, rscratch1, dst);
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
            AArch64Address dest = loadStackSlotAddress(size, crb, masm, result, scratch.getRegister());
            if (input.getRegisterCategory().equals(CPU)) {
                masm.str(size, input, dest);
            } else {
                assert input.getRegisterCategory().equals(SIMD);
                masm.fstr(size, input, dest);
            }
        }
    }

    static void stack2reg(AArch64Kind moveKind, CompilationResultBuilder crb, AArch64MacroAssembler masm, Register result, StackSlot input) {
        /*
         * Since AArch64ArithmeticLIRGenerator.emitNarrow creates a move from a QWORD to DWORD, it
         * is possible that the stack slot is an aligned QWORD while the moveKind is a DWORD. When
         * this happens, it is better treat the move as a QWORD, as this allows an immediate
         * addressing mode to be used more often.
         */
        final int size = input.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        assert moveKind.getSizeInBytes() * Byte.SIZE <= size;
        if (result.getRegisterCategory().equals(CPU)) {
            AArch64Address src = loadStackSlotAddress(size, crb, masm, input, result);
            masm.ldr(size, result, src);
        } else {
            assert result.getRegisterCategory().equals(SIMD);
            try (ScratchRegister sc = masm.getScratchRegister()) {
                AArch64Address src = loadStackSlotAddress(size, crb, masm, input, sc.getRegister());
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
     * Returns AArch64Address of given StackSlot. We cannot use CompilationResultBuilder.asAddress
     * since this calls AArch64MacroAssembler.makeAddress with displacements that may be larger than
     * 9-bit signed, which cannot be handled by that method.
     *
     * Instead we create an address ourselves. We use scaled unsigned addressing since we know the
     * transfersize, which gives us a 15-bit address range (for longs/doubles) respectively a 14-bit
     * range (for everything else).
     *
     * @param scratchReg Scratch register that can be used to load address.
     * @return AArch64Address of given StackSlot. Uses scratch register if necessary to do so.
     */
    private static AArch64Address loadStackSlotAddress(int size, CompilationResultBuilder crb, AArch64MacroAssembler masm, StackSlot slot, Register scratchReg) {
        int displacement = crb.frameMap.offsetForStackSlot(slot);
        assert size == slot.getPlatformKind().getSizeInBytes() * Byte.SIZE;
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
