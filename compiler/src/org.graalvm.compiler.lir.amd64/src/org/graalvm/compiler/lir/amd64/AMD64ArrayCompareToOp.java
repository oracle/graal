/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.k7;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.EnumSet;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Emits code which compares two arrays lexicographically. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_COMPARE_TO")
public final class AMD64ArrayCompareToOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64ArrayCompareToOp> TYPE = LIRInstructionClass.create(AMD64ArrayCompareToOp.class);

    private final Stride strideA;
    private final Stride strideB;
    private final int useAVX3Threshold;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value arrayAValue;
    @Alive({REG}) protected Value arrayBValue;
    @Use({REG}) protected Value lengthAValue;
    @Use({REG}) protected Value lengthBValue;
    @Temp({REG}) protected Value lengthAValueTemp;
    @Temp({REG}) protected Value lengthBValueTemp;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;

    @Temp({REG, ILLEGAL}) protected Value vectorTemp1;

    public AMD64ArrayCompareToOp(LIRGeneratorTool tool, int useAVX3Threshold, Stride strideA, Stride strideB,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value result,
                    Value arrayA, Value lengthA,
                    Value arrayB, Value lengthB) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, AVXSize.ZMM);

        assert CodeUtil.isPowerOf2(useAVX3Threshold) : "AVX3Threshold must be power of 2";
        this.useAVX3Threshold = useAVX3Threshold;
        this.strideA = strideA;
        this.strideB = strideB;

        this.resultValue = result;
        this.arrayAValue = arrayA;
        this.arrayBValue = arrayB;
        /*
         * The length values are inputs but are also killed like temporaries so need both Use and
         * Temp annotations, which will only work with fixed registers.
         */
        this.lengthAValue = lengthAValueTemp = lengthA;
        this.lengthBValue = lengthBValueTemp = lengthB;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp2 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));

        // We only need the vector temporaries if we generate SSE code.
        if (supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.SSE4_2)) {
            this.vectorTemp1 = tool.newVariable(LIRKind.value(AMD64Kind.DOUBLE));
        } else {
            this.vectorTemp1 = Value.ILLEGAL;
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register str1 = asRegister(temp1);
        Register str2 = asRegister(temp2);

        // Load array base addresses.
        masm.movq(str1, asRegister(arrayAValue));
        masm.movq(str2, asRegister(arrayBValue));
        Register cnt1 = asRegister(lengthAValue);
        Register cnt2 = asRegister(lengthBValue);

        Label labelLengthDiff = new Label();
        Label labelPop = new Label();
        Label labelDone = new Label();
        Label labelWhileHead = new Label();
        Label labelCompareWideVectorsLoopFailed = new Label(); // used only _LP64 && AVX3

        int stride2x2 = 0x40;
        Stride maxStride = Stride.max(strideA, strideB);
        Stride scale1 = Stride.S1;
        Stride scale2 = Stride.S2;
        int elementsPerXMMVector = 16 >> maxStride.log2;
        int elementsPerYMMVector = 32 >> maxStride.log2;

        if (!(strideA == Stride.S1 && strideB == Stride.S1)) {
            stride2x2 = 0x20;
        }

        if (strideA != strideB) {
            masm.shrl(cnt2, 1);
        }
        // Compute the minimum of the string lengths and the
        // difference of the string lengths (stack).
        // Do the conditional move stuff
        masm.movl(result, cnt1);
        masm.subl(cnt1, cnt2);
        masm.push(cnt1);
        masm.cmovl(ConditionFlag.LessEqual, cnt2, result);    // cnt2 = min(cnt1, cnt2)

        // Is the minimum length zero?
        masm.testlAndJcc(cnt2, cnt2, ConditionFlag.Zero, labelLengthDiff, false);

        if (strideA == Stride.S1 && strideB == Stride.S1) {
            // Load first bytes
            masm.movzbl(result, new AMD64Address(str1, 0));  // result = str1[0]
            masm.movzbl(cnt1, new AMD64Address(str2, 0));    // cnt1 = str2[0]
        } else if (strideA == Stride.S2 && strideB == Stride.S2) {
            // Load first characters
            masm.movzwl(result, new AMD64Address(str1, 0));
            masm.movzwl(cnt1, new AMD64Address(str2, 0));
        } else {
            masm.movzbl(result, new AMD64Address(str1, 0));
            masm.movzwl(cnt1, new AMD64Address(str2, 0));
        }
        masm.sublAndJcc(result, cnt1, ConditionFlag.NotZero, labelPop, false);

        if (strideA == Stride.S2 && strideB == Stride.S2) {
            // Divide length by 2 to get number of chars
            masm.shrl(cnt2, 1);
        }
        masm.cmplAndJcc(cnt2, 1, ConditionFlag.Equal, labelLengthDiff, false);

        // Check if the strings start at the same location and setup scale and stride
        if (strideA == strideB) {
            masm.cmpqAndJcc(str1, str2, ConditionFlag.Equal, labelLengthDiff, false);
        }

        if (supportsAVX2AndYMM() && masm.supports(CPUFeature.SSE4_2)) {
            Register vec1 = asRegister(vectorTemp1, AMD64Kind.DOUBLE);

            Label labelCompareWideVectors = new Label();
            Label labelVectorNotEqual = new Label();
            Label labelCompareWideTail = new Label();
            Label labelCompareSmallStr = new Label();
            Label labelCompareWideVectorsLoop = new Label();
            Label labelCompare16Chars = new Label();
            Label labelCompareIndexChar = new Label();
            Label labelCompareWideVectorsLoopAVX2 = new Label();
            Label labelCompareTailLong = new Label();
            Label labelCompareWideVectorsLoopAVX3 = new Label();  // used only _LP64 && AVX3

            int pcmpmask = 0x19;
            if (strideA == Stride.S1 && strideB == Stride.S1) {
                pcmpmask &= ~0x01;
            }

            // Setup to compare 16-chars (32-bytes) vectors,
            // start from first character again because it has aligned address.

            assert result.equals(rax) && cnt2.equals(rdx) && cnt1.equals(rcx) : "pcmpestri";
            // rax and rdx are used by pcmpestri as elements counters
            masm.movl(result, cnt2);
            masm.andlAndJcc(cnt2, ~(elementsPerYMMVector - 1), ConditionFlag.Zero, labelCompareTailLong, false);

            // fast path : compare first 2 8-char vectors.
            masm.bind(labelCompare16Chars);
            if (strideA == strideB) {
                masm.movdqu(vec1, new AMD64Address(str1, 0));
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, 0));
            }
            masm.pcmpestri(vec1, new AMD64Address(str2, 0), pcmpmask);
            masm.jccb(ConditionFlag.Below, labelCompareIndexChar);

            if (strideA == strideB) {
                masm.movdqu(vec1, new AMD64Address(str1, 16));
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, 8));
            }
            masm.pcmpestri(vec1, new AMD64Address(str2, 16), pcmpmask);
            masm.jccb(ConditionFlag.AboveEqual, labelCompareWideVectors);
            masm.addl(cnt1, elementsPerXMMVector);

            // Compare the characters at index in cnt1
            masm.bind(labelCompareIndexChar); // cnt1 has the offset of the mismatching character
            loadNextElements(masm, result, cnt2, str1, str2, maxStride, scale1, scale2, cnt1);
            masm.subl(result, cnt2);
            masm.jmp(labelPop);

            // Setup the registers to start vector comparison loop
            masm.bind(labelCompareWideVectors);
            if (strideA == strideB) {
                masm.leaq(str1, new AMD64Address(str1, result, maxStride));
                masm.leaq(str2, new AMD64Address(str2, result, maxStride));
            } else {
                masm.leaq(str1, new AMD64Address(str1, result, scale1));
                masm.leaq(str2, new AMD64Address(str2, result, scale2));
            }
            masm.subl(result, elementsPerYMMVector);
            masm.sublAndJcc(cnt2, elementsPerYMMVector, ConditionFlag.Zero, labelCompareWideTail, false);
            masm.negq(result);

            // In a loop, compare 16-chars (32-bytes) at once using (vpxor+vptest)
            masm.bind(labelCompareWideVectorsLoop);

            // trying 64 bytes fast loop
            if (useAVX3Threshold == 0 && supportsAVX512VLBWAndZMM()) {
                masm.cmplAndJcc(cnt2, stride2x2, ConditionFlag.Below, labelCompareWideVectorsLoopAVX2, true);
                // cnt2 holds the vector, not-zero means we cannot subtract by 0x40
                masm.testlAndJcc(cnt2, stride2x2 - 1, ConditionFlag.NotZero, labelCompareWideVectorsLoopAVX2, true);

                masm.bind(labelCompareWideVectorsLoopAVX3); // the hottest loop
                if (strideA == strideB) {
                    masm.evmovdqu64(vec1, new AMD64Address(str1, result, maxStride));
                    // k7 == 11..11, if operands equal, otherwise k7 has some 0
                    masm.evpcmpeqb(k7, vec1, new AMD64Address(str2, result, maxStride));
                } else {
                    masm.evpmovzxbw(vec1, new AMD64Address(str1, result, scale1));
                    // k7 == 11..11, if operands equal, otherwise k7 has some 0
                    masm.evpcmpeqb(k7, vec1, new AMD64Address(str2, result, scale2));
                }
                masm.kortestq(k7, k7);
                masm.jcc(ConditionFlag.AboveEqual, labelCompareWideVectorsLoopFailed); // miscompare
                masm.addq(result, stride2x2); // update since we already compared at this addr
                // and sub the size too
                masm.sublAndJcc(cnt2, stride2x2, ConditionFlag.NotZero, labelCompareWideVectorsLoopAVX3, true);

                masm.vpxor(vec1, vec1, vec1, AVXSize.YMM);
                masm.jmpb(labelCompareWideTail);
            }

            masm.bind(labelCompareWideVectorsLoopAVX2);
            if (strideA == strideB) {
                masm.vmovdqu(vec1, new AMD64Address(str1, result, maxStride));
                masm.vpxor(vec1, vec1, new AMD64Address(str2, result, maxStride), AVXSize.YMM);
            } else {
                masm.vpmovzxbw(vec1, new AMD64Address(str1, result, scale1));
                masm.vpxor(vec1, vec1, new AMD64Address(str2, result, scale2), AVXSize.YMM);
            }
            masm.vptest(vec1, vec1, AVXSize.YMM);
            masm.jcc(ConditionFlag.NotZero, labelVectorNotEqual);
            masm.addq(result, elementsPerYMMVector);
            masm.sublAndJcc(cnt2, elementsPerYMMVector, ConditionFlag.NotZero, labelCompareWideVectorsLoop, false);
            // clean upper bits of YMM registers
            masm.vpxor(vec1, vec1, vec1, AVXSize.YMM);

            // compare wide vectors tail
            masm.bind(labelCompareWideTail);
            masm.testqAndJcc(result, result, ConditionFlag.Zero, labelLengthDiff, false);

            masm.movl(result, elementsPerYMMVector);
            masm.movl(cnt2, result);
            masm.negq(result);
            masm.jmp(labelCompareWideVectorsLoopAVX2);

            // Identifies the mismatching (higher or lower)16-bytes in the 32-byte vectors.
            masm.bind(labelVectorNotEqual);
            // clean upper bits of YMM registers
            masm.vpxor(vec1, vec1, vec1, AVXSize.YMM);
            if (strideA == strideB) {
                masm.leaq(str1, new AMD64Address(str1, result, maxStride));
                masm.leaq(str2, new AMD64Address(str2, result, maxStride));
            } else {
                masm.leaq(str1, new AMD64Address(str1, result, scale1));
                masm.leaq(str2, new AMD64Address(str2, result, scale2));
            }
            masm.jmp(labelCompare16Chars);

            // Compare tail chars, length between 1 to 15 chars
            masm.bind(labelCompareTailLong);
            masm.movl(cnt2, result);
            masm.cmplAndJcc(cnt2, elementsPerXMMVector, ConditionFlag.Less, labelCompareSmallStr, false);

            if (strideA == strideB) {
                masm.movdqu(vec1, new AMD64Address(str1, 0));
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, 0));
            }
            masm.pcmpestri(vec1, new AMD64Address(str2, 0), pcmpmask);
            masm.jcc(ConditionFlag.Below, labelCompareIndexChar);
            masm.subqAndJcc(cnt2, elementsPerXMMVector, ConditionFlag.Zero, labelLengthDiff, false);
            if (strideA == strideB) {
                masm.leaq(str1, new AMD64Address(str1, result, maxStride));
                masm.leaq(str2, new AMD64Address(str2, result, maxStride));
            } else {
                masm.leaq(str1, new AMD64Address(str1, result, scale1));
                masm.leaq(str2, new AMD64Address(str2, result, scale2));
            }
            masm.negq(cnt2);
            masm.jmpb(labelWhileHead);

            masm.bind(labelCompareSmallStr);
        } else if (masm.supports(CPUFeature.SSE4_2)) {
            Register vec1 = asRegister(vectorTemp1, AMD64Kind.DOUBLE);

            Label labelCompareWideVectors = new Label();
            Label labelVectorNotEqual = new Label();
            Label labelCompareTail = new Label();
            int pcmpmask = 0x19;
            // Setup to compare 8-char (16-byte) vectors,
            // start from first character again because it has aligned address.
            masm.movl(result, cnt2);
            if (strideA == Stride.S1 && strideB == Stride.S1) {
                pcmpmask &= ~0x01;
            }
            masm.andlAndJcc(cnt2, ~(elementsPerXMMVector - 1), ConditionFlag.Zero, labelCompareTail, false);
            if (strideA == strideB) {
                masm.leaq(str1, new AMD64Address(str1, result, maxStride));
                masm.leaq(str2, new AMD64Address(str2, result, maxStride));
            } else {
                masm.leaq(str1, new AMD64Address(str1, result, scale1));
                masm.leaq(str2, new AMD64Address(str2, result, scale2));
            }
            masm.negq(result);

            // pcmpestri
            // inputs:
            // vec1- substring
            // rax - negative string length (elements count)
            // mem - scanned string
            // rdx - string length (elements count)
            // pcmpmask - cmp mode: 11000 (string compare with negated result)
            // + 00 (unsigned bytes) or + 01 (unsigned shorts)
            // outputs:
            // rcx - first mismatched element index
            assert result.equals(rax) && cnt2.equals(rdx) && cnt1.equals(rcx) : "pcmpestri";

            masm.bind(labelCompareWideVectors);
            if (strideA == strideB) {
                masm.movdqu(vec1, new AMD64Address(str1, result, maxStride));
                masm.pcmpestri(vec1, new AMD64Address(str2, result, maxStride), pcmpmask);
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, result, scale1));
                masm.pcmpestri(vec1, new AMD64Address(str2, result, scale2), pcmpmask);
            }
            // After pcmpestri cnt1(rcx) contains mismatched element index

            masm.jccb(ConditionFlag.Below, labelVectorNotEqual);  // CF==1
            masm.addq(result, elementsPerXMMVector);
            masm.subqAndJcc(cnt2, elementsPerXMMVector, ConditionFlag.NotZero, labelCompareWideVectors, true);

            // compare wide vectors tail
            masm.testqAndJcc(result, result, ConditionFlag.Zero, labelLengthDiff, false);

            masm.movl(cnt2, elementsPerXMMVector);
            masm.movl(result, elementsPerXMMVector);
            masm.negq(result);
            if (strideA == strideB) {
                masm.movdqu(vec1, new AMD64Address(str1, result, maxStride));
                masm.pcmpestri(vec1, new AMD64Address(str2, result, maxStride), pcmpmask);
            } else {
                masm.pmovzxbw(vec1, new AMD64Address(str1, result, scale1));
                masm.pcmpestri(vec1, new AMD64Address(str2, result, scale2), pcmpmask);
            }
            masm.jccb(ConditionFlag.AboveEqual, labelLengthDiff);

            // Mismatched characters in the vectors
            masm.bind(labelVectorNotEqual);
            masm.addq(cnt1, result);
            loadNextElements(masm, result, cnt2, str1, str2, maxStride, scale1, scale2, cnt1);
            masm.subl(result, cnt2);
            masm.jmpb(labelPop);

            masm.bind(labelCompareTail); // limit is zero
            masm.movl(cnt2, result);
            // Fallthru to tail compare
        }

        // Shift str2 and str1 to the end of the arrays, negate min
        if (strideA == strideB) {
            masm.leaq(str1, new AMD64Address(str1, cnt2, maxStride));
            masm.leaq(str2, new AMD64Address(str2, cnt2, maxStride));
        } else {
            masm.leaq(str1, new AMD64Address(str1, cnt2, scale1));
            masm.leaq(str2, new AMD64Address(str2, cnt2, scale2));
        }
        masm.decrementl(cnt2);  // first character was compared already
        masm.negq(cnt2);

        // Compare the rest of the elements
        masm.bind(labelWhileHead);
        loadNextElements(masm, result, cnt1, str1, str2, maxStride, scale1, scale2, cnt2);
        masm.sublAndJcc(result, cnt1, ConditionFlag.NotZero, labelPop, true);
        masm.incqAndJcc(cnt2, ConditionFlag.NotZero, labelWhileHead, true);

        // Strings are equal up to min length. Return the length difference.
        masm.bind(labelLengthDiff);
        masm.pop(result);
        if (strideA == Stride.S2 && strideB == Stride.S2) {
            // Divide diff by 2 to get number of chars
            masm.sarl(result, 1);
        }
        masm.jmpb(labelDone);

        if (supportsAVX512VLBWAndZMM()) {
            masm.bind(labelCompareWideVectorsLoopFailed);

            masm.kmovq(cnt1, k7);
            masm.notq(cnt1);
            masm.bsfq(cnt2, cnt1);
            // if (ae != StrIntrinsicNode::LL) {
            if (!(strideA == Stride.S1 && strideB == Stride.S1)) {
                // Divide diff by 2 to get number of chars
                masm.sarl(cnt2, 1);
            }
            masm.addq(result, cnt2);
            if (strideA == Stride.S1 && strideB == Stride.S1) {
                masm.movzbl(cnt1, new AMD64Address(str2, result, Stride.S1));
                masm.movzbl(result, new AMD64Address(str1, result, Stride.S1));
            } else if (strideA == Stride.S2 && strideB == Stride.S2) {
                masm.movzwl(cnt1, new AMD64Address(str2, result, maxStride));
                masm.movzwl(result, new AMD64Address(str1, result, maxStride));
            } else {
                masm.movzwl(cnt1, new AMD64Address(str2, result, scale2));
                masm.movzbl(result, new AMD64Address(str1, result, scale1));
            }
            masm.subl(result, cnt1);
            masm.jmpb(labelPop);
        }

        // Discard the stored length difference
        masm.bind(labelPop);
        masm.pop(cnt1);

        // That's it
        masm.bind(labelDone);
        if (strideA == Stride.S2 && strideB == Stride.S1) {
            masm.negl(result);
        }
    }

    private void loadNextElements(AMD64MacroAssembler masm, Register elem1, Register elem2, Register str1, Register str2,
                    Stride stride, Stride stride1,
                    Stride stride2, Register index) {
        if (strideA == Stride.S1 && strideB == Stride.S1) {
            masm.movzbl(elem1, new AMD64Address(str1, index, stride, 0));
            masm.movzbl(elem2, new AMD64Address(str2, index, stride, 0));
        } else if (strideA == Stride.S2 && strideB == Stride.S2) {
            masm.movzwl(elem1, new AMD64Address(str1, index, stride, 0));
            masm.movzwl(elem2, new AMD64Address(str2, index, stride, 0));
        } else {
            masm.movzbl(elem1, new AMD64Address(str1, index, stride1, 0));
            masm.movzwl(elem2, new AMD64Address(str2, index, stride2, 0));
        }
    }
}
