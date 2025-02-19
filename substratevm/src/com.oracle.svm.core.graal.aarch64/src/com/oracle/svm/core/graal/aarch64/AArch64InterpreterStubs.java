/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.aarch64;

import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.deopt.DeoptimizationSlotPacking;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.util.VMError;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;

public class AArch64InterpreterStubs {

    public static final Register TRAMPOLINE_ARGUMENT = AArch64.r12;

    public static class InterpreterEnterStubContext extends SubstrateAArch64Backend.SubstrateAArch64FrameContext {

        public InterpreterEnterStubContext(SharedMethod method) {
            super(method);
        }

        private static AArch64Address createImmediate(int offset) {
            int deoptSlotSize = 8 + 8 /* padding */;
            return createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, deoptSlotSize + offset);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            Register trampArg = TRAMPOLINE_ARGUMENT;
            Register spCopy = AArch64.r11;

            masm.mov(64, spCopy, sp);

            super.enter(crb);

            /* sp points to InterpreterData struct */
            masm.str(64, spCopy, createImmediate(offsetAbiSpReg()));

            masm.str(64, r0, createImmediate(offsetAbiGpArg0()));
            masm.str(64, r1, createImmediate(offsetAbiGpArg1()));
            masm.str(64, r2, createImmediate(offsetAbiGpArg2()));
            masm.str(64, r3, createImmediate(offsetAbiGpArg3()));
            masm.str(64, r4, createImmediate(offsetAbiGpArg4()));
            masm.str(64, r5, createImmediate(offsetAbiGpArg5()));
            masm.str(64, r6, createImmediate(offsetAbiGpArg6()));
            masm.str(64, r7, createImmediate(offsetAbiGpArg7()));

            masm.fstr(64, v0, createImmediate(offsetAbiFpArg0()));
            masm.fstr(64, v1, createImmediate(offsetAbiFpArg1()));
            masm.fstr(64, v2, createImmediate(offsetAbiFpArg2()));
            masm.fstr(64, v3, createImmediate(offsetAbiFpArg3()));
            masm.fstr(64, v4, createImmediate(offsetAbiFpArg4()));
            masm.fstr(64, v5, createImmediate(offsetAbiFpArg5()));
            masm.fstr(64, v6, createImmediate(offsetAbiFpArg6()));
            masm.fstr(64, v7, createImmediate(offsetAbiFpArg7()));

            /* sp+deoptSlotSize points to InterpreterData struct */
            masm.add(64, r1, sp, 16 /* deoptSlotSize */);

            /* Pass the interpreter method index as first arg */
            masm.mov(64, r0, trampArg);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            /* r0 is a pointer to InterpreterEnterData */

            /* Move fp return value into ABI register */
            masm.fldr(64, v0, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, r0, offsetAbiFpRet()));

            /* Move gp return value into ABI register */
            masm.ldr(64, r0, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, r0, offsetAbiGpRet()));

            super.leave(crb);
        }
    }

    public static class InterpreterLeaveStubContext extends SubstrateAArch64Backend.SubstrateAArch64FrameContext {

        public InterpreterLeaveStubContext(SharedMethod method) {
            super(method);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            super.enter(crb);
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;

            /* sp points to four reserved stack slots for this stub */

            /* Pointer to InterpreterData struct */
            masm.str(64, r1, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 0));
            /* Variable stack size */
            masm.str(64, r2, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 8));
            /* gcReferenceMap next */
            masm.str(64, r3, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 16));

            /* 4th slot is for stack alignment to 0x10 */

            masm.sub(64, sp, sp, r2 /* variable stack size */);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            Register callTarget = AArch64.r11;

            /* Save call target */
            masm.mov(64, callTarget, r0);

            /* Get pointer to InterpreterData struct */
            masm.mov(64, r0, r1);

            Register stackSize = AArch64.r2;
            Label regsHandling = new Label();
            /* if stackSize == 0 */
            masm.cbz(64, stackSize, regsHandling);

            /* Copy prepared outgoing args to the stack where the ABI expects it */
            Register calleeSpArgs = AArch64.r4;
            Register interpDataSp = AArch64.r1;
            masm.ldr(64, interpDataSp, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, r0, offsetAbiSpReg()));
            masm.mov(64, calleeSpArgs, sp);

            int wordSize = 8;
            Label spCopyBegin = new Label();
            masm.bind(spCopyBegin);
            masm.ldr(64, r3, createImmediateAddress(64, IMMEDIATE_POST_INDEXED, interpDataSp, wordSize));
            masm.str(64, r3, createImmediateAddress(64, IMMEDIATE_POST_INDEXED, calleeSpArgs, wordSize));
            masm.sub(64, stackSize, stackSize, wordSize);

            masm.cbnz(64, stackSize, spCopyBegin);

            masm.bind(regsHandling);

            /* Set fp argument registers */
            masm.fldr(64, v7, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg7()));
            masm.fldr(64, v6, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg6()));
            masm.fldr(64, v5, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg5()));
            masm.fldr(64, v4, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg4()));
            masm.fldr(64, v3, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg3()));
            masm.fldr(64, v2, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg2()));
            masm.fldr(64, v1, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg1()));
            masm.fldr(64, v0, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiFpArg0()));

            /* Set gp argument registers */
            masm.ldr(64, r7, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg7()));
            masm.ldr(64, r6, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg6()));
            masm.ldr(64, r5, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg5()));
            masm.ldr(64, r4, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg4()));
            masm.ldr(64, r3, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg3()));
            masm.ldr(64, r2, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg2()));
            masm.ldr(64, r1, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg1()));
            masm.ldr(64, r0, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, AArch64.r0, offsetAbiGpArg0()));

            /* Call into target method */
            masm.blr(callTarget);

            try (AArch64MacroAssembler.ScratchRegister scratchRegister = masm.getScratchRegister()) {
                Register resultCopy = scratchRegister.getRegister();
                masm.mov(64, resultCopy, r0);

                /* Obtain stack size from deopt slot */
                masm.ldr(64, r2, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 0));

                /* Assumption of deopt slot encoding */
                assert crb.target.stackAlignment == 0x10;
                masm.lsr(64, r2, r2, DeoptimizationSlotPacking.POS_VARIABLE_FRAMESIZE - DeoptimizationSlotPacking.STACK_ALIGNMENT);

                /* Restore stack pointer */
                masm.add(64, sp, sp, r2);

                /* Pointer InterpreterData struct */
                masm.ldr(64, r0, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, sp, 0));

                /* Save gp ABI register into InterpreterData struct */
                masm.str(64, resultCopy, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, r0, offsetAbiGpRet()));
            }

            /* Save fp ABI register into InterpreterData struct */
            masm.fstr(64, v0, createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, r0, offsetAbiFpRet()));

            super.leave(crb);
        }
    }

    public static int sizeOfInterpreterData() {
        return NumUtil.roundUp(SizeOf.get(InterpreterDataAArch64.class), 0x10);
    }

    public static int additionalFrameSizeEnterStub() {
        int wordSize = 8;
        int deoptSlotSize = wordSize + wordSize /* for padding */;
        return sizeOfInterpreterData() + deoptSlotSize;
    }

    public static int additionalFrameSizeLeaveStub() {
        int wordSize = 8;
        /*
         * reserve four slots for: base address of outgoing stack args, variable stack size,
         * gcReferenceMap, padding
         */
        return 4 * wordSize;
    }

    @RawStructure
    public interface InterpreterDataAArch64 extends PointerBase {
        @RawField
        long getStackSize();

        @RawField
        void setStackSize(long val);

        @RawField
        long getAbiSpReg();

        @RawField
        void setAbiSpReg(long val);

        /* arch specific */
        @RawField
        long getAbiGpArg0();

        @RawField
        void setAbiGpArg0(long val);

        @RawField
        long getAbiGpArg1();

        @RawField
        void setAbiGpArg1(long val);

        @RawField
        long getAbiGpArg2();

        @RawField
        void setAbiGpArg2(long val);

        @RawField
        long getAbiGpArg3();

        @RawField
        void setAbiGpArg3(long val);

        @RawField
        long getAbiGpArg4();

        @RawField
        void setAbiGpArg4(long val);

        @RawField
        long getAbiGpArg5();

        @RawField
        void setAbiGpArg5(long val);

        @RawField
        long getAbiGpArg6();

        @RawField
        void setAbiGpArg6(long val);

        @RawField
        long getAbiGpArg7();

        @RawField
        void setAbiGpArg7(long val);

        @RawField
        long getAbiFpArg0();

        @RawField
        void setAbiFpArg0(long val);

        @RawField
        long getAbiFpArg1();

        @RawField
        void setAbiFpArg1(long val);

        @RawField
        long getAbiFpArg2();

        @RawField
        void setAbiFpArg2(long val);

        @RawField
        long getAbiFpArg3();

        @RawField
        void setAbiFpArg3(long val);

        @RawField
        long getAbiFpArg4();

        @RawField
        void setAbiFpArg4(long val);

        @RawField
        long getAbiFpArg5();

        @RawField
        void setAbiFpArg5(long val);

        @RawField
        long getAbiFpArg6();

        @RawField
        void setAbiFpArg6(long val);

        @RawField
        long getAbiFpArg7();

        @RawField
        void setAbiFpArg7(long val);

        @RawField
        void setAbiGpRet(long val);

        @RawField
        long getAbiGpRet();

        @RawField
        void setAbiFpRet(long val);

        @RawField
        long getAbiFpRet();
    }

    @Fold
    public static int offsetAbiSpReg() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiSpReg");
    }

    @Fold
    public static int offsetAbiGpArg0() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg0");
    }

    @Fold
    public static int offsetAbiGpArg1() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg1");
    }

    @Fold
    public static int offsetAbiGpArg2() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg2");
    }

    @Fold
    public static int offsetAbiGpArg3() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg3");
    }

    @Fold
    public static int offsetAbiGpArg4() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg4");
    }

    @Fold
    public static int offsetAbiGpArg5() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg5");
    }

    @Fold
    public static int offsetAbiGpArg6() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg6");
    }

    @Fold
    public static int offsetAbiGpArg7() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpArg7");
    }

    @Fold
    public static int offsetAbiFpArg0() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg0");
    }

    @Fold
    public static int offsetAbiFpArg1() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg1");
    }

    @Fold
    public static int offsetAbiFpArg2() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg2");
    }

    @Fold
    public static int offsetAbiFpArg3() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg3");
    }

    @Fold
    public static int offsetAbiFpArg4() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg4");
    }

    @Fold
    public static int offsetAbiFpArg5() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg5");
    }

    @Fold
    public static int offsetAbiFpArg6() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg6");
    }

    @Fold
    public static int offsetAbiFpArg7() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpArg7");
    }

    @Fold
    public static int offsetAbiFpRet() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiFpRet");
    }

    @Fold
    public static int offsetAbiGpRet() {
        return OffsetOf.get(InterpreterDataAArch64.class, "AbiGpRet");
    }

    public static class AArch64InterpreterAccessStubData implements InterpreterAccessStubData {

        @Override
        public void setSp(Pointer data, int stackSize, Pointer stackBuffer) {
            VMError.guarantee(stackBuffer.isNonNull());

            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            p.setAbiSpReg(stackBuffer.rawValue());
            p.setStackSize(stackSize);

            /*
             * We re-use the deopt slot to leave the stack size at a known place for the stack
             * walker. See
             *
             * com.oracle.svm.core.graal.aarch64.SubstrateAArch64RegisterConfig#getCallingConvention
             *
             * comment above `currentStackOffset` declaration.
             */
            assert stackSize % 0x10 == 0;
            stackBuffer.writeLong(0, DeoptimizationSlotPacking.encodeVariableFrameSizeIntoDeoptSlot(stackSize));
        }

        @Override
        public long getGpArgumentAt(AllocatableValue ccArg, Pointer data, int pos) {
            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            return switch (pos) {
                case 0 -> p.getAbiGpArg0();
                case 1 -> p.getAbiGpArg1();
                case 2 -> p.getAbiGpArg2();
                case 3 -> p.getAbiGpArg3();
                case 4 -> p.getAbiGpArg4();
                case 5 -> p.getAbiGpArg5();
                case 6 -> p.getAbiGpArg6();
                case 7 -> p.getAbiGpArg7();
                default -> {
                    StackSlot stackSlot = (StackSlot) ccArg;
                    Pointer spVal = Word.pointer(p.getAbiSpReg());
                    yield spVal.readLong(stackSlot.getOffset(0));
                }
            };
        }

        @Override
        public long setGpArgumentAt(AllocatableValue ccArg, Pointer data, int pos, long val) {
            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            if (pos >= 0 && pos <= 7) {
                VMError.guarantee(ccArg instanceof RegisterValue);
                switch (pos) {
                    case 0 -> p.setAbiGpArg0(val);
                    case 1 -> p.setAbiGpArg1(val);
                    case 2 -> p.setAbiGpArg2(val);
                    case 3 -> p.setAbiGpArg3(val);
                    case 4 -> p.setAbiGpArg4(val);
                    case 5 -> p.setAbiGpArg5(val);
                    case 6 -> p.setAbiGpArg6(val);
                    case 7 -> p.setAbiGpArg7(val);
                }
                /* no GC mask required */
                return 0;
            }
            StackSlot stackSlot = (StackSlot) ccArg;

            Pointer spVal = Word.pointer(p.getAbiSpReg());
            int offset = stackSlot.getOffset(0);
            VMError.guarantee(spVal.isNonNull());
            VMError.guarantee(offset < p.getStackSize());

            spVal.writeLong(offset, val);

            VMError.guarantee((pos - 8) < Long.SIZE, "more than 64 stack args are not supported");
            return 1L << (pos - 8);
        }

        @Override
        public long getFpArgumentAt(AllocatableValue ccArg, Pointer data, int pos) {
            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            return switch (pos) {
                case 0 -> p.getAbiFpArg0();
                case 1 -> p.getAbiFpArg1();
                case 2 -> p.getAbiFpArg2();
                case 3 -> p.getAbiFpArg3();
                case 4 -> p.getAbiFpArg4();
                case 5 -> p.getAbiFpArg5();
                case 6 -> p.getAbiFpArg6();
                case 7 -> p.getAbiFpArg7();
                default -> {
                    StackSlot stackSlot = (StackSlot) ccArg;
                    Pointer spVal = Word.pointer(p.getAbiSpReg());
                    yield spVal.readLong(stackSlot.getOffset(0));
                }
            };
        }

        @Override
        public void setFpArgumentAt(AllocatableValue ccArg, Pointer data, int pos, long val) {
            InterpreterDataAArch64 p = (InterpreterDataAArch64) data;
            switch (pos) {
                case 0 -> p.setAbiFpArg0(val);
                case 1 -> p.setAbiFpArg1(val);
                case 2 -> p.setAbiFpArg2(val);
                case 3 -> p.setAbiFpArg3(val);
                case 4 -> p.setAbiFpArg4(val);
                case 5 -> p.setAbiFpArg5(val);
                case 6 -> p.setAbiFpArg6(val);
                case 7 -> p.setAbiFpArg7(val);
                default -> {
                    StackSlot stackSlot = (StackSlot) ccArg;

                    Pointer spVal = Word.pointer(p.getAbiSpReg());
                    int offset = stackSlot.getOffset(0);

                    VMError.guarantee(spVal.isNonNull());
                    VMError.guarantee(offset < p.getStackSize());

                    spVal.writeLong(offset, val);
                }
            }
        }

        @Override
        public long getGpReturn(Pointer data) {
            return ((InterpreterDataAArch64) data).getAbiGpRet();
        }

        @Override
        public void setGpReturn(Pointer data, long gpReturn) {
            ((InterpreterDataAArch64) data).setAbiGpRet(gpReturn);
        }

        @Override
        public long getFpReturn(Pointer data) {
            return ((InterpreterDataAArch64) data).getAbiFpRet();
        }

        @Override
        public void setFpReturn(Pointer data, long fpReturn) {
            ((InterpreterDataAArch64) data).setAbiFpRet(fpReturn);
        }

        @Override
        @Fold
        public int allocateStubDataSize() {
            return sizeOfInterpreterData();
        }
    }
}
