/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.amd64.AMD64.xmm0;

import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizationSlotPacking;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;

public class AMD64InterpreterStubs {

    private static SubstrateAMD64RegisterConfig getRegisterConfig() {
        return new SubstrateAMD64RegisterConfig(SubstrateRegisterConfig.ConfigKind.NORMAL, null, ConfigurationValues.getTarget(),
                        SubstrateOptions.PreserveFramePointer.getValue());
    }

    public static final Register TRAMPOLINE_ARGUMENT = AMD64.rax;

    public static class InterpreterEnterStubContext extends SubstrateAMD64Backend.SubstrateAMD64FrameContext {

        public InterpreterEnterStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
        }

        private static AMD64Address createAddress(int offset) {
            int deoptSlotSize = 8 + 8 /* padding */;
            return new AMD64Address(rsp, deoptSlotSize + offset);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;

            Register trampArg = TRAMPOLINE_ARGUMENT;
            Register spCopy = AMD64.r11;

            masm.movq(spCopy, rsp);

            super.enter(crb);

            /* sp points to InterpreterData struct */
            masm.movq(createAddress(offsetAbiSpReg()), spCopy);

            List<Register> gps = getRegisterConfig().getJavaGeneralParameterRegs();
            VMError.guarantee(gps.size() == 6);

            masm.movq(createAddress(offsetAbiGp0()), gps.get(0));
            masm.movq(createAddress(offsetAbiGp1()), gps.get(1));
            masm.movq(createAddress(offsetAbiGp2()), gps.get(2));
            masm.movq(createAddress(offsetAbiGp3()), gps.get(3));
            masm.movq(createAddress(offsetAbiGp4()), gps.get(4));
            masm.movq(createAddress(offsetAbiGp5()), gps.get(5));

            List<Register> fps = getRegisterConfig().getFloatingPointParameterRegs();

            masm.movq(createAddress(offsetAbiFpArg0()), fps.get(0));
            masm.movq(createAddress(offsetAbiFpArg1()), fps.get(1));
            masm.movq(createAddress(offsetAbiFpArg2()), fps.get(2));
            masm.movq(createAddress(offsetAbiFpArg3()), fps.get(3));

            if (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) {
                VMError.guarantee(fps.size() == 8);
                masm.movq(createAddress(offsetAbiFpArg4()), fps.get(4));
                masm.movq(createAddress(offsetAbiFpArg5()), fps.get(5));
                masm.movq(createAddress(offsetAbiFpArg6()), fps.get(6));
                masm.movq(createAddress(offsetAbiFpArg7()), fps.get(7));
            } else {
                assert Platform.includedIn(InternalPlatform.WINDOWS_BASE.class);
                VMError.guarantee(fps.size() == 4);
            }

            /* sp points to InterpreterData struct, move it as 2nd arg */
            masm.movq(gps.get(1), rsp);
            masm.addq(gps.get(1), 16 /* deoptSlotSize */);

            /* Pass the interpreter method index as first arg */
            masm.movq(gps.get(0), trampArg);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;

            /* rax is a pointer to InterpreterEnterData */

            /* Move fp return value into ABI register */
            masm.movq(xmm0, new AMD64Address(rax, offsetAbiFpRet()));

            /* Move gp return value into ABI register */
            masm.movq(rax, new AMD64Address(rax, offsetAbiGpRet()));

            super.leave(crb);
        }
    }

    public static class InterpreterLeaveStubContext extends SubstrateAMD64Backend.SubstrateAMD64FrameContext {

        public InterpreterLeaveStubContext(SharedMethod method, CallingConvention callingConvention) {
            super(method, callingConvention);
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            super.enter(crb);
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            List<Register> gps = getRegisterConfig().getJavaGeneralParameterRegs();

            /* sp points to four reserved stack slots for this stub */

            /* arg0 is untouched by this extra prolog */

            /* arg1: Pointer to InterpreterData struct */
            masm.movq(new AMD64Address(rsp, 0), gps.get(1));
            /* arg2: Variable stack size */
            masm.movq(new AMD64Address(rsp, 8), gps.get(2));
            /* arg3: gcReferenceMap next */
            masm.movq(new AMD64Address(rsp, 16), gps.get(3));

            /* 4th slot is for stack alignment to 0x10 */

            masm.subq(rsp, gps.get(2) /* variable stack size */);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            List<Register> gps = getRegisterConfig().getJavaGeneralParameterRegs();
            List<Register> fps = getRegisterConfig().getFloatingPointParameterRegs();

            /* Save call target */
            Register callTarget = AMD64.r10;
            masm.movq(callTarget, rax);

            /* Get pointer to InterpreterData struct */
            masm.movq(rax, gps.get(1));

            Register stackSize = AMD64.r11;
            masm.movq(stackSize, gps.get(2));

            Label regsHandling = new Label();
            /* if stackSize == 0 */
            masm.testq(stackSize, stackSize);
            masm.jccb(AMD64Assembler.ConditionFlag.Zero, regsHandling);

            /* Copy prepared outgoing args to the stack where the ABI expects it */
            Register calleeSpArgs = AMD64.r12;
            Register interpDataSp = AMD64.r9;
            masm.movq(interpDataSp, new AMD64Address(rax, offsetAbiSpReg()));
            masm.movq(calleeSpArgs, rsp);

            int wordSize = 8;
            Label spCopyBegin = new Label();
            masm.bind(spCopyBegin);
            /* 5th arg is not used, use it as a temp register */
            masm.movq(gps.get(4), new AMD64Address(interpDataSp, 0));
            masm.movq(new AMD64Address(calleeSpArgs, 0), gps.get(4));
            masm.addq(interpDataSp, wordSize);
            masm.addq(calleeSpArgs, wordSize);
            masm.subl(stackSize, wordSize);

            masm.testq(stackSize, stackSize);
            masm.jccb(AMD64Assembler.ConditionFlag.NotZero, spCopyBegin);

            masm.bind(regsHandling);

            /* Set fp argument registers */
            masm.movq(fps.get(0), new AMD64Address(rax, offsetAbiFpArg0()));
            masm.movq(fps.get(1), new AMD64Address(rax, offsetAbiFpArg1()));
            masm.movq(fps.get(2), new AMD64Address(rax, offsetAbiFpArg2()));
            masm.movq(fps.get(3), new AMD64Address(rax, offsetAbiFpArg3()));

            if (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) {
                masm.movq(fps.get(4), new AMD64Address(rax, offsetAbiFpArg4()));
                masm.movq(fps.get(5), new AMD64Address(rax, offsetAbiFpArg5()));
                masm.movq(fps.get(6), new AMD64Address(rax, offsetAbiFpArg6()));
                masm.movq(fps.get(7), new AMD64Address(rax, offsetAbiFpArg7()));
            }

            /* Set gp argument registers */
            masm.movq(gps.get(0), new AMD64Address(rax, offsetAbiGp0()));
            masm.movq(gps.get(1), new AMD64Address(rax, offsetAbiGp1()));
            masm.movq(gps.get(2), new AMD64Address(rax, offsetAbiGp2()));
            masm.movq(gps.get(3), new AMD64Address(rax, offsetAbiGp3()));
            masm.movq(gps.get(4), new AMD64Address(rax, offsetAbiGp4()));
            masm.movq(gps.get(5), new AMD64Address(rax, offsetAbiGp5()));

            /* Call into target method */
            masm.call(callTarget);

            Register resultCopy = AMD64.r10;
            masm.movq(resultCopy, rax);

            /* Obtain stack size from deopt slot */
            masm.movq(AMD64.r12, new AMD64Address(rsp, 0));

            /* Assumption of deopt slot encoding */
            assert crb.target.stackAlignment == 0x10;
            masm.shrq(AMD64.r12, DeoptimizationSlotPacking.POS_VARIABLE_FRAMESIZE - DeoptimizationSlotPacking.STACK_ALIGNMENT);

            /* Restore stack pointer */
            masm.addq(rsp, AMD64.r12);

            /* Pointer InterpreterData struct */
            masm.movq(rax, new AMD64Address(rsp, 0));

            /* Save gp ABI register into InterpreterData struct */
            masm.movq(new AMD64Address(rax, offsetAbiGpRet()), resultCopy);

            /* Save fp ABI register into InterpreterData struct */
            masm.movq(new AMD64Address(rax, offsetAbiFpRet()), xmm0);

            super.leave(crb);
        }
    }

    public static int sizeOfInterpreterData() {
        return NumUtil.roundUp(SizeOf.get(InterpreterDataAMD64.class), 0x10);
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
    public interface InterpreterDataAMD64 extends PointerBase {
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
        void setAbiGpRet(long val);

        @RawField
        long getAbiGpRet();

        @RawField
        void setAbiFpRet(long val);

        @RawField
        long getAbiFpRet();

        @RawField
        long getAbiGp0();

        @RawField
        void setAbiGp0(long val);

        @RawField
        long getAbiGp1();

        @RawField
        void setAbiGp1(long val);

        @RawField
        long getAbiGp2();

        @RawField
        void setAbiGp2(long val);

        @RawField
        long getAbiGp3();

        @RawField
        void setAbiGp3(long val);

        @RawField
        long getAbiGp4();

        @RawField
        void setAbiGp4(long val);

        @RawField
        long getAbiGp5();

        @RawField
        void setAbiGp5(long val);

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

        // GR-55154: We could save 32 bytes on Windows by exluding storage for xmm4-xmm7
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
    }

    @Fold
    public static int offsetAbiSpReg() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiSpReg");
    }

    @Fold
    public static int offsetAbiGp0() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGp0");
    }

    @Fold
    public static int offsetAbiGp1() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGp1");
    }

    @Fold
    public static int offsetAbiGp2() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGp2");
    }

    @Fold
    public static int offsetAbiGp3() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGp3");
    }

    @Fold
    public static int offsetAbiGp4() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGp4");
    }

    @Fold
    public static int offsetAbiGp5() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGp5");
    }

    @Fold
    public static int offsetAbiFpArg0() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg0");
    }

    @Fold
    public static int offsetAbiFpArg1() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg1");
    }

    @Fold
    public static int offsetAbiFpArg2() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg2");
    }

    @Fold
    public static int offsetAbiFpArg3() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg3");
    }

    @Fold
    public static int offsetAbiFpArg4() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg4");
    }

    @Fold
    public static int offsetAbiFpArg5() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg5");
    }

    @Fold
    public static int offsetAbiFpArg6() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg6");
    }

    @Fold
    public static int offsetAbiFpArg7() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpArg7");
    }

    @Fold
    public static int offsetAbiGpRet() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiGpRet");
    }

    @Fold
    public static int offsetAbiFpRet() {
        return OffsetOf.get(InterpreterDataAMD64.class, "AbiFpRet");
    }

    public static class AMD64InterpreterAccessStubData implements InterpreterAccessStubData {

        @Override
        public void setSp(Pointer data, int stackSize, Pointer stackBuffer) {
            VMError.guarantee(stackBuffer.isNonNull());

            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            p.setAbiSpReg(stackBuffer.rawValue());
            p.setStackSize(stackSize);

            /*
             * We re-use the deopt slot to leave the stack size at a known place for the stack
             * walker. See
             *
             * com.oracle.svm.core.graal.amd64.SubstrateAMD64RegisterConfig#getCallingConvention
             *
             * comment above `currentStackOffset` declaration.
             */
            assert stackSize % 0x10 == 0;
            stackBuffer.writeLong(0, DeoptimizationSlotPacking.encodeVariableFrameSizeIntoDeoptSlot(stackSize));
        }

        @Override
        public long getGpArgumentAt(AllocatableValue ccArg, Pointer data, int pos) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            return switch (pos) {
                case 0 -> p.getAbiGp0();
                case 1 -> p.getAbiGp1();
                case 2 -> p.getAbiGp2();
                case 3 -> p.getAbiGp3();
                case 4 -> p.getAbiGp4();
                case 5 -> p.getAbiGp5();
                default -> {
                    StackSlot stackSlot = (StackSlot) ccArg;
                    Pointer sp = Word.pointer(p.getAbiSpReg());
                    int spAdjustmentOnCall = ConfigurationValues.getTarget().wordSize;
                    int offset = stackSlot.getOffset(0) + spAdjustmentOnCall;
                    yield sp.readLong(offset);
                }
            };
        }

        @Override
        public long setGpArgumentAt(AllocatableValue ccArg, Pointer data, int pos, long val) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            if (pos >= 0 && pos <= 5) {
                VMError.guarantee(ccArg instanceof RegisterValue);
                switch (pos) {
                    case 0 -> p.setAbiGp0(val);
                    case 1 -> p.setAbiGp1(val);
                    case 2 -> p.setAbiGp2(val);
                    case 3 -> p.setAbiGp3(val);
                    case 4 -> p.setAbiGp4(val);
                    case 5 -> p.setAbiGp5(val);
                }
                /* no GC mask required */
                return 0;
            }
            StackSlot stackSlot = (StackSlot) ccArg;

            Pointer sp = Word.pointer(p.getAbiSpReg());
            int offset = stackSlot.getOffset(0);
            VMError.guarantee(sp.isNonNull());
            VMError.guarantee(offset < p.getStackSize());

            sp.writeLong(offset, val);

            VMError.guarantee((pos - 6) < Long.SIZE, "more than 64 stack args are not supported");
            return 1L << (pos - 6);
        }

        private static int upperFpEnd() {
            /* only 4 floating point regs on Windows, 8 otherwise */
            return Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) ? 3 : 7;
        }

        @Override
        public long getFpArgumentAt(AllocatableValue ccArg, Pointer data, int pos) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            if (pos >= 0 && pos <= upperFpEnd()) {
                VMError.guarantee(ccArg instanceof RegisterValue);
                switch (pos) {
                    case 0:
                        return p.getAbiFpArg0();
                    case 1:
                        return p.getAbiFpArg1();
                    case 2:
                        return p.getAbiFpArg2();
                    case 3:
                        return p.getAbiFpArg3();
                    case 4:
                        return p.getAbiFpArg4();
                    case 5:
                        return p.getAbiFpArg5();
                    case 6:
                        return p.getAbiFpArg6();
                    case 7:
                        return p.getAbiFpArg7();
                }
            }
            StackSlot stackSlot = (StackSlot) ccArg;
            Pointer sp = Word.pointer(p.getAbiSpReg());

            int spAdjustmentOnCall = ConfigurationValues.getTarget().wordSize;
            int offset = stackSlot.getOffset(0) + spAdjustmentOnCall;

            return sp.readLong(offset);
        }

        @Override
        public void setFpArgumentAt(AllocatableValue ccArg, Pointer data, int pos, long val) {
            InterpreterDataAMD64 p = (InterpreterDataAMD64) data;
            if (pos >= 0 && pos <= upperFpEnd()) {
                VMError.guarantee(ccArg instanceof RegisterValue);
                switch (pos) {
                    case 0 -> p.setAbiFpArg0(val);
                    case 1 -> p.setAbiFpArg1(val);
                    case 2 -> p.setAbiFpArg2(val);
                    case 3 -> p.setAbiFpArg3(val);
                    case 4 -> p.setAbiFpArg4(val);
                    case 5 -> p.setAbiFpArg5(val);
                    case 6 -> p.setAbiFpArg6(val);
                    case 7 -> p.setAbiFpArg7(val);
                }
            } else {
                StackSlot stackSlot = (StackSlot) ccArg;

                Pointer sp = Word.pointer(p.getAbiSpReg());
                int offset = stackSlot.getOffset(0);

                VMError.guarantee(sp.isNonNull());
                VMError.guarantee(offset < p.getStackSize());

                sp.writeLong(offset, val);
            }
        }

        @Override
        public long getGpReturn(Pointer data) {
            return ((InterpreterDataAMD64) data).getAbiGpRet();
        }

        @Override
        public void setGpReturn(Pointer data, long gpReturn) {
            ((InterpreterDataAMD64) data).setAbiGpRet(gpReturn);
        }

        @Override
        public long getFpReturn(Pointer data) {
            return ((InterpreterDataAMD64) data).getAbiFpRet();
        }

        @Override
        public void setFpReturn(Pointer data, long fpReturn) {
            ((InterpreterDataAMD64) data).setAbiFpRet(fpReturn);
        }

        @Override
        @Fold
        public int allocateStubDataSize() {
            return sizeOfInterpreterData();
        }
    }
}
