/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexGeneralPurposeRMVOp.SHLX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/f3b34d32d6ea409f8c8f0382e8f01e746366f842/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L3940-L4183",
          sha1 = "1aa453c52215c1fc8d467460a93fa590f9edab15")
// @formatter:on
@Opcode("AMD64_COUNT_POSITIVES")
public final class AMD64CountPositivesOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64CountPositivesOp> TYPE = LIRInstructionClass.create(AMD64CountPositivesOp.class);

    @Def({OperandFlag.REG}) private Value resultValue;
    @Alive({OperandFlag.REG}) private Value originArrayValue;
    @Alive({OperandFlag.REG, OperandFlag.CONST}) private Value originLengthValue;

    @Temp({OperandFlag.REG}) private Value arrayValue;
    @Temp({OperandFlag.REG}) private Value lenValue;

    @Temp({OperandFlag.REG}) private Value tmpValue1;
    @Temp({OperandFlag.REG}) private Value tmpValue3;

    @Temp({OperandFlag.REG}) private Value vecValue1;
    @Temp({OperandFlag.REG}) private Value vecValue2;

    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected Value maskValue1;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) protected Value maskValue2;

    private final int useAVX3Threshold;

    public AMD64CountPositivesOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, int useAVX3Threshold, AllocatableValue result, AllocatableValue array, Value length) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, supportsAVX512VLBW(tool.target(), runtimeCheckedCPUFeatures) && supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.BMI2) ? ZMM : YMM);

        assert useAVX3Threshold == 0 || CodeUtil.isPowerOf2(useAVX3Threshold) : "AVX3Threshold must be 0 or a power of 2: " + useAVX3Threshold;
        this.useAVX3Threshold = useAVX3Threshold;

        this.resultValue = result;
        this.originArrayValue = array;
        this.originLengthValue = length;

        this.arrayValue = tool.newVariable(array.getValueKind());
        this.lenValue = tool.newVariable(length.getValueKind());

        this.tmpValue1 = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        this.tmpValue3 = tool.newVariable(length.getValueKind());

        LIRKind lirKind = LIRKind.value(getVectorKind(JavaKind.Byte));
        this.vecValue1 = tool.newVariable(lirKind);
        this.vecValue2 = tool.newVariable(lirKind);

        if (ZMM.fitsWithin(vectorSize)) {
            this.maskValue1 = tool.newVariable(LIRKind.value(AMD64Kind.MASK64));
            this.maskValue2 = tool.newVariable(LIRKind.value(AMD64Kind.MASK64));
        } else {
            this.maskValue1 = Value.ILLEGAL;
            this.maskValue2 = Value.ILLEGAL;
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label labelTailAdjust = new Label();
        Label labelDone = new Label();
        Label labelTailStart = new Label();
        Label labelCharAdjust = new Label();
        Label labelCompareChar = new Label();
        Label labelCompareVectors = new Label();
        Label labelCompareByte = new Label();

        AMD64Move.move(crb, masm, arrayValue, originArrayValue);
        AMD64Move.move(crb, masm, lenValue, originLengthValue);

        Register ary1 = asRegister(arrayValue);
        Register len = asRegister(lenValue);
        Register result = asRegister(resultValue);

        Register tmp1 = asRegister(tmpValue1);
        Register vec1 = asRegister(vecValue1);
        Register vec2 = asRegister(vecValue2);

        masm.movl(result, len);
        // len == 0
        masm.testlAndJcc(len, len, ConditionFlag.Zero, labelDone, false);

        if (useAVX3Threshold == 0 && supportsAVX512VLBWAndZMM() && supports(CPUFeature.BMI2)) {
            Label labelTest64Loop = new Label();
            Label labelTestTail = new Label();
            Label labelBreakLoop = new Label();

            Register tmp3Aliased = asRegister(tmpValue3);
            Register mask1 = asRegister(maskValue1);
            Register mask2 = asRegister(maskValue2);

            masm.movl(tmp1, len);
            masm.emit(EVPXORD, vec2, vec2, vec2, ZMM);
            // tail count (in chars) 0x3
            masm.andl(tmp1, 0x0000003f);
            // vector count (in chars)
            masm.andlAndJcc(len, 0xffffffc0, ConditionFlag.Zero, labelTestTail, true);

            masm.leaq(ary1, new AMD64Address(ary1, len, Stride.S1));
            masm.negq(len);

            masm.bind(labelTest64Loop);
            // Check whether our 64 elements of size byte contain negatives
            masm.evpcmpgtb(mask1, vec2, new AMD64Address(ary1, len, Stride.S1));
            masm.kortestq(mask1, mask1);
            masm.jcc(ConditionFlag.NotZero, labelBreakLoop);

            masm.addqAndJcc(len, 64, ConditionFlag.NotZero, labelTest64Loop, true);

            masm.bind(labelTestTail);
            // bail out when there is nothing to be done
            masm.testlAndJcc(tmp1, -1, ConditionFlag.Zero, labelDone, false);

            // check the tail for absence of negatives
            // ~(~0 << len) applied up to two times (for 32-bit scenario)
            masm.movq(tmp3Aliased, 0xFFFFFFFF_FFFFFFFFL);
            masm.emit(SHLX, tmp3Aliased, tmp3Aliased, tmp1, QWORD);
            masm.notq(tmp3Aliased);

            masm.kmovq(mask2, tmp3Aliased);

            masm.evpcmpgtb(mask1, mask2, vec2, new AMD64Address(ary1, 0));
            masm.ktestq(mask1, mask2);
            masm.jcc(ConditionFlag.Zero, labelDone);

            // do a full check for negative registers in the tail
            // tmp1 holds low 6-bit from original len
            masm.movl(len, tmp1);
            // ary1 already pointing to the right place
            masm.jmpb(labelTailStart);

            masm.bind(labelBreakLoop);
            // At least one byte in the last 64 byte block was negative.
            // Set up to look at the last 64 bytes as if they were a tail
            masm.leaq(ary1, new AMD64Address(ary1, len, Stride.S1));
            masm.addq(result, len);
            // Ignore the very last byte: if all others are positive,
            // it must be negative, so we can skip right to the 2+1 byte
            // end comparison at this point
            masm.orl(result, 63);
            masm.movl(len, 63);
            // Fallthru to tail compare
        } else {
            if (supportsAVX2AndYMM()) {
                // With AVX2, use 32-byte vector compare
                Label labelCompareWideVectors = new Label();
                Label labelBreakLoop = new Label();

                // Compare 32-byte vectors
                // len contains vector count (in bytes)
                masm.testlAndJcc(len, 0xffffffe0, ConditionFlag.Zero, labelTailStart, true);

                masm.andl(len, 0xffffffe0);
                masm.leaq(ary1, new AMD64Address(ary1, len, Stride.S1));
                masm.negq(len);

                masm.movl(tmp1, 0x80808080); // create mask to test for Unicode chars in vector
                masm.movdl(vec2, tmp1);
                masm.emit(VPBROADCASTD, vec2, vec2, YMM);

                masm.bind(labelCompareWideVectors);
                masm.vmovdqu(vec1, new AMD64Address(ary1, len, Stride.S1));
                masm.vptest(vec1, vec2, AVXKind.AVXSize.YMM);
                masm.jccb(ConditionFlag.NotZero, labelBreakLoop);
                masm.addqAndJcc(len, 32, ConditionFlag.NotZero, labelCompareWideVectors, true);

                masm.testlAndJcc(result, 0x0000001f, ConditionFlag.Zero, labelDone, false);

                // Quick test using the already prepared vector mask
                masm.movl(len, result);
                masm.andl(len, 0x0000001f);
                masm.vmovdqu(vec1, new AMD64Address(ary1, len, Stride.S1, -32));
                masm.vptest(vec1, vec2, AVXKind.AVXSize.YMM);
                masm.jcc(ConditionFlag.Zero, labelDone);
                masm.jmp(labelTailStart);

                masm.bind(labelBreakLoop); // len is zero
                // At least one byte in the last 32-byte vector is negative.
                // Set up to look at the last 32 bytes as if they were a tail
                masm.leaq(ary1, new AMD64Address(ary1, len, Stride.S1));
                masm.addq(result, len);
                // Ignore the very last byte: if all others are positive,
                // it must be negative, so we can skip right to the 2+1 byte
                // end comparison at this point
                masm.orl(result, 31);
                masm.movl(len, 31);
                // Fallthru to tail compare
            } else if (masm.supports(CPUFeature.SSE4_2)) {
                // With SSE4.2, use double quad vector compare
                Label labelCompareWideVectors = new Label();
                Label labelBreakLoop = new Label();

                // Compare 16-byte vectors
                // len contains vector count (in bytes)
                masm.testlAndJcc(len, 0xfffffff0, ConditionFlag.Zero, labelTailStart, false);

                masm.andl(len, 0xfffffff0);
                masm.leaq(ary1, new AMD64Address(ary1, len, Stride.S1));
                masm.negq(len);

                masm.movl(tmp1, 0x80808080);
                masm.movdl(vec2, tmp1);
                masm.pshufd(vec2, vec2, 0);

                masm.bind(labelCompareWideVectors);
                masm.movdqu(vec1, new AMD64Address(ary1, len, Stride.S1));
                masm.ptest(vec1, vec2);
                masm.jccb(ConditionFlag.NotZero, labelBreakLoop);
                masm.addqAndJcc(len, 16, ConditionFlag.NotZero, labelCompareWideVectors, true);

                masm.testlAndJcc(result, 0x0000000f, ConditionFlag.Zero, labelDone, false);

                // Quick test using the already prepared vector mask
                masm.movl(len, result);
                masm.andl(len, 0x0000000f); // tail count (in bytes)
                masm.movdqu(vec1, new AMD64Address(ary1, len, Stride.S1, -16));
                masm.ptest(vec1, vec2);
                masm.jcc(ConditionFlag.Zero, labelDone);
                masm.jmpb(labelTailStart);

                masm.bind(labelBreakLoop); // len is zero
                // At least one byte in the last 16-byte vector is negative.
                // Set up and look at the last 16 bytes as if they were a tail
                masm.leaq(ary1, new AMD64Address(ary1, len, Stride.S1));
                masm.addq(result, len);
                // Ignore the very last byte: if all others are positive,
                // it must be negative, so we can skip right to the 2+1 byte
                // end comparison at this point
                masm.orl(result, 15);
                masm.movl(len, 15);
                // Fallthru to tail compare
            }
        }

        masm.bind(labelTailStart);
        // Compare 4-byte vectors
        // vector count (in bytes)
        masm.andlAndJcc(len, 0xfffffffc, ConditionFlag.Zero, labelCompareChar, true);

        masm.leaq(ary1, new AMD64Address(ary1, len, Stride.S1));
        masm.negq(len);

        masm.bind(labelCompareVectors);
        masm.movl(tmp1, new AMD64Address(ary1, len, Stride.S1));
        masm.andlAndJcc(tmp1, 0x80808080, ConditionFlag.NotZero, labelTailAdjust, true);
        masm.addqAndJcc(len, 4, ConditionFlag.NotZero, labelCompareVectors, true);

        // Compare trailing char (final 2-3 bytes), if any
        masm.bind(labelCompareChar);

        masm.testlAndJcc(result, 0x2, ConditionFlag.Zero, labelCompareByte, true); // tail char
        masm.movzwl(tmp1, new AMD64Address(ary1, 0));
        masm.andlAndJcc(tmp1, 0x00008080, ConditionFlag.NotZero, labelCharAdjust, true);
        masm.leaq(ary1, new AMD64Address(ary1, 2));

        masm.bind(labelCompareByte);
        masm.testlAndJcc(result, 0x1, ConditionFlag.Zero, labelDone, true); // tail byte
        masm.movzbl(tmp1, new AMD64Address(ary1, 0));
        masm.testlAndJcc(tmp1, 0x00000080, ConditionFlag.Zero, labelDone, true);
        masm.subq(result, 1);
        masm.jmpb(labelDone);

        masm.bind(labelTailAdjust);

        // there are negative bits in the last 4 byte block.
        // Adjust result and check the next three bytes
        masm.addq(result, len);
        masm.orl(result, 3);
        masm.leaq(ary1, new AMD64Address(ary1, len, Stride.S1));
        masm.jmpb(labelCompareChar);

        masm.bind(labelCharAdjust);
        // We are looking at a char + optional byte tail, and found that one
        // of the bytes in the char is negative. Adjust the result, check the
        // first byte and readjust if needed.
        masm.andl(result, 0xfffffffc);
        // little-endian, so lowest byte comes first
        masm.testlAndJcc(tmp1, 0x00000080, ConditionFlag.NotZero, labelDone, true);
        masm.addq(result, 1);

        // That's it
        masm.bind(labelDone);
    }
}
