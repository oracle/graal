/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Emits code to find the index of the first non-equal elements in two arrays, using SIMD
 * instructions where possible.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/7bb59dc8da0c61c5da5c3aab5d56a6e4880001ce/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L7147-L7365",
          sha1 = "72f9b7a60b75ecabf09fc10cb01a9504be97957a")
// @formatter:on
@Opcode("VECTORIZED_MISMATCH")
public final class AMD64VectorizedMismatchOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64VectorizedMismatchOp> TYPE = LIRInstructionClass.create(AMD64VectorizedMismatchOp.class);

    private static final Register REG_ARRAY_A = rsi;
    private static final Register REG_ARRAY_B = rdi;
    private static final Register REG_LENGTH = rdx;
    private static final Register REG_STRIDE = rcx;

    private static final int ONES_16 = 0xffff;
    private static final int ONES_32 = 0xffffffff;

    @Def({OperandFlag.REG}) private Value resultValue;
    @Use({OperandFlag.REG}) private Value arrayAValue;
    @Use({OperandFlag.REG}) private Value arrayBValue;
    @Use({OperandFlag.REG}) private Value lengthValue;
    @Alive({OperandFlag.REG}) private Value strideValue;

    @Temp({OperandFlag.REG}) private Value arrayAValueTemp;
    @Temp({OperandFlag.REG}) private Value arrayBValueTemp;
    @Temp({OperandFlag.REG}) private Value lengthValueTemp;

    @Temp({OperandFlag.REG}) Value[] temp;
    @Temp({OperandFlag.REG}) Value[] vectorTemp;

    private AMD64VectorizedMismatchOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value result, Value arrayA, Value arrayB, Value length, Value stride) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, YMM);
        this.resultValue = result;
        this.arrayAValue = this.arrayAValueTemp = arrayA;
        this.arrayBValue = this.arrayBValueTemp = arrayB;
        this.lengthValue = this.lengthValueTemp = length;
        this.strideValue = stride;
        this.temp = allocateTempRegisters(tool, AMD64Kind.QWORD, 2);
        this.vectorTemp = allocateVectorRegisters(tool, JavaKind.Byte, 3);
    }

    /**
     * Compares array regions of length {@code length} in {@code arrayA} and {@code arrayB}.
     *
     * @param length length (number of array slots respective to stride) of the region to compare.
     * @param stride element size in log2 format (0: byte, 1: char, 2: int, 3: long).
     */
    public static AMD64VectorizedMismatchOp movParamsAndCreate(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value result, Value arrayA, Value arrayB, Value length, Value stride) {
        RegisterValue regArrayA = REG_ARRAY_A.asValue(arrayA.getValueKind());
        RegisterValue regArrayB = REG_ARRAY_B.asValue(arrayB.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());
        RegisterValue regStride = REG_STRIDE.asValue(length.getValueKind());
        tool.emitMove(regArrayA, arrayA);
        tool.emitMove(regArrayB, arrayB);
        tool.emitMove(regLength, length);
        tool.emitMove(regStride, stride);
        return new AMD64VectorizedMismatchOp(tool, runtimeCheckedCPUFeatures, result, regArrayA, regArrayB, regLength, regStride);
    }

    /**
     * Emits code that uses SSE2/AVX1 128-bit (16-byte) or AVX2 256-bit (32-byte) vector compares.
     * Underlying algorithm: check vectors for equality with PCMPEQ, identify the first differing
     * elements with PMOVMSK and BSF.
     */
    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Register result = asRegister(resultValue);
        Register arrayA = asRegister(arrayAValue);
        Register arrayB = asRegister(arrayBValue);
        Register length = asRegister(lengthValue);
        Register tailLength = asRegister(temp[0]);
        Register tmp = asRegister(temp[1]);

        Label returnLabel = new Label();
        Label returnEqualLabel = new Label();

        Register vector1 = asRegister(vectorTemp[0]);
        Register vector2 = asRegister(vectorTemp[1]);
        Register vector3 = asRegister(vectorTemp[2]);
        int bytesPerVector = vectorSize.getBytes();
        Stride stride = Stride.S1;

        Label vectorLoop = new Label();
        Label diffFound = new Label();
        Label tail = new Label();
        Label qwordTail = new Label();
        Label qwordTail2 = new Label();
        Label dwordTail = new Label();
        Label dwordTail2 = new Label();
        Label scalarTail = new Label();
        Label scalarLoop = new Label();

        GraalError.guarantee(asRegister(strideValue).equals(rcx), "stride must be in rcx for shift op");
        // convert length to byte-length (uses stride in the RCX register as implicit argument)
        asm.shlq(length);
        // result = 0
        asm.xorq(result, result);
        asm.movq(tailLength, length);

        asm.andq(tailLength, bytesPerVector - 1);
        asm.andqAndJcc(length, -bytesPerVector, ConditionFlag.Zero, tail, false);

        if (supports(CPUFeature.AVX)) {
            // main loop
            asm.align(preferredLoopAlignment(crb));
            asm.bind(vectorLoop);
            // check vector regions for equality
            asm.movdqu(vectorSize, vector1, new AMD64Address(arrayA, result, stride));
            asm.movdqu(vectorSize, vector2, new AMD64Address(arrayB, result, stride));
            asm.pxor(vectorSize, vector3, vector1, vector2);
            asm.ptest(vectorSize, vector3, vector3);
            asm.jccb(ConditionFlag.NotZero, diffFound);
            // regions are equal, continue the loop
            asm.addq(result, bytesPerVector);
            asm.subqAndJcc(length, bytesPerVector, ConditionFlag.NotZero, vectorLoop, true);

            // tail: compare the remaining bytes with a vector load aligned to the end of the array.
            // adjust index:
            // result = length + tailLength - elementsPerVector
            asm.leaq(result, new AMD64Address(result, tailLength, stride, -bytesPerVector));
            asm.movdqu(vectorSize, vector1, new AMD64Address(arrayA, result, stride));
            asm.movdqu(vectorSize, vector2, new AMD64Address(arrayB, result, stride));
            asm.pxor(vectorSize, vector3, vector1, vector2);
            asm.ptest(vectorSize, vector3, vector3);
            asm.jcc(ConditionFlag.Zero, returnEqualLabel);

            asm.align(preferredBranchTargetAlignment(crb));
            asm.bind(diffFound);
            AMD64Assembler.VexRVMOp.VPCMPEQB.emit(asm, vectorSize, vector3, vector1, vector2);
            asm.pmovmsk(vectorSize, tmp, vector3);
            asm.notq(tmp);
            // different elements found in the current region, find the byte index of the first
            // non-equal elements
            bsfq(asm, tmp, tmp);
            // add to current vector loop index
            asm.addq(result, tmp);
            asm.jmp(returnLabel);
        } else {
            // PTEST requires SSE4.1, avoid using it here for maximum compatibility

            // main loop
            asm.align(preferredLoopAlignment(crb));
            asm.bind(vectorLoop);
            asm.movdqu(vectorSize, vector1, new AMD64Address(arrayA, result, stride));
            asm.movdqu(vectorSize, vector2, new AMD64Address(arrayB, result, stride));
            asm.pcmpeq(vectorSize, stride, vector1, vector2);
            asm.pmovmsk(vectorSize, tmp, vector1);
            asm.xorlAndJcc(tmp, vectorSize == XMM ? ONES_16 : ONES_32, ConditionFlag.NotZero, diffFound, true);
            // regions are equal, continue the loop
            asm.addq(result, bytesPerVector);
            asm.subqAndJcc(length, bytesPerVector, ConditionFlag.NotZero, vectorLoop, true);

            // tail: compare the remaining bytes with a vector load aligned to the end of the array.
            // adjust index:
            // result = length + tailLength - elementsPerVector
            asm.leaq(result, new AMD64Address(result, tailLength, stride, -bytesPerVector));
            asm.movdqu(vectorSize, vector1, new AMD64Address(arrayA, result, stride));
            asm.movdqu(vectorSize, vector2, new AMD64Address(arrayB, result, stride));
            asm.pcmpeq(vectorSize, stride, vector1, vector2);
            asm.pmovmsk(vectorSize, tmp, vector1);
            asm.xorlAndJcc(tmp, vectorSize == XMM ? ONES_16 : ONES_32, ConditionFlag.Zero, returnEqualLabel, false);

            asm.align(preferredBranchTargetAlignment(crb));
            asm.bind(diffFound);
            // different elements found in the current region, find the byte index of the first
            // non-equal elements
            bsfq(asm, tmp, tmp);
            // add to current vector loop index
            asm.addq(result, tmp);
            asm.jmp(returnLabel);
        }

        asm.align(preferredBranchTargetAlignment(crb));
        asm.bind(tail);

        if (supportsAVX2AndYMM()) {
            // region is too small for YMM vectors, try XMM
            asm.cmpqAndJcc(tailLength, XMM.getBytes(), ConditionFlag.Less, qwordTail, false);
            // no loop needed, region is guaranteed to be between 16 and 31 bytes at this point
            // load and compare from start of region
            asm.movdqu(XMM, vector1, new AMD64Address(arrayA));
            asm.pcmpeq(XMM, stride, vector1, new AMD64Address(arrayB));
            // load and compare from end of region
            asm.movdqu(XMM, vector2, new AMD64Address(arrayA, tailLength, stride, -XMM.getBytes()));
            asm.pcmpeq(XMM, stride, vector2, new AMD64Address(arrayB, tailLength, stride, -XMM.getBytes()));
            // combine results into one YMM vector, by copying vector2 to the upper half of vector1
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, YMM, vector1, vector2, vector1, 0x02);
            asm.pmovmsk(YMM, result, vector1);
            asm.xorlAndJcc(result, ONES_32, ConditionFlag.Zero, returnEqualLabel, false);
            bsfq(asm, result, result);
            // if the resulting index is greater than XMM bytes, we have to adjust it to be based on
            // the end of the array
            asm.leaq(tmp, new AMD64Address(result, tailLength, Stride.S1, -YMM.getBytes()));
            asm.cmpq(result, XMM.getBytes());
            asm.cmovq(ConditionFlag.Greater, result, tmp);
            asm.jmp(returnLabel);
        }

        // region is too small for XMM vectors, try QWORD
        asm.bind(qwordTail);
        asm.cmpqAndJcc(tailLength, QWORD.getBytes(), ConditionFlag.Less, dwordTail, true);

        // region is guaranteed to be between 8 and 15 bytes at this point
        // check first 8 bytes
        asm.movq(result, new AMD64Address(arrayA));
        asm.xorqAndJcc(result, new AMD64Address(arrayB), ConditionFlag.Zero, qwordTail2, true);
        bsfq(asm, result, result);
        asm.shrq(result, 3);
        asm.jmp(returnLabel);

        // check last 8 bytes
        asm.bind(qwordTail2);
        asm.movq(result, new AMD64Address(arrayA, tailLength, stride, -QWORD.getBytes()));
        asm.xorqAndJcc(result, new AMD64Address(arrayB, tailLength, stride, -QWORD.getBytes()), ConditionFlag.Zero, returnEqualLabel, true);
        bsfq(asm, result, result);
        asm.shrl(result, 3);
        asm.leaq(result, new AMD64Address(result, tailLength, Stride.S1, -QWORD.getBytes()));
        asm.jmpb(returnLabel);

        // region is too small for QWORD loads, try DWORD
        asm.bind(dwordTail);
        asm.cmpqAndJcc(tailLength, DWORD.getBytes(), ConditionFlag.Less, scalarTail, true);

        // region is guaranteed to be between 4 and 7 bytes at this point
        // check first 4 bytes
        asm.movl(result, new AMD64Address(arrayA));
        asm.xorlAndJcc(result, new AMD64Address(arrayB), ConditionFlag.Zero, dwordTail2, true);
        bsfq(asm, result, result);
        asm.shrl(result, 3);
        asm.jmpb(returnLabel);

        // check last 4 bytes
        asm.bind(dwordTail2);
        asm.movl(result, new AMD64Address(arrayA, tailLength, stride, -DWORD.getBytes()));
        asm.xorlAndJcc(result, new AMD64Address(arrayB, tailLength, stride, -DWORD.getBytes()), ConditionFlag.Zero, returnEqualLabel, true);
        bsfq(asm, result, result);
        asm.shrl(result, 3);
        asm.leaq(result, new AMD64Address(result, tailLength, Stride.S1, -DWORD.getBytes()));
        asm.jmpb(returnLabel);

        asm.bind(scalarTail);
        asm.testqAndJcc(tailLength, tailLength, ConditionFlag.Zero, returnEqualLabel, true);
        // scalar tail loop for regions smaller than DWORD
        asm.bind(scalarLoop);
        asm.movzbl(tmp, new AMD64Address(arrayA, result, stride));
        asm.movzbl(length, new AMD64Address(arrayB, result, stride));
        asm.cmplAndJcc(tmp, length, ConditionFlag.NotEqual, returnLabel, true);
        asm.incl(result);
        asm.decqAndJcc(tailLength, ConditionFlag.NotZero, scalarLoop, true);

        asm.align(preferredBranchTargetAlignment(crb));
        asm.bind(returnEqualLabel);
        // all elements are equal, return -1
        asm.movq(result, -1);
        asm.align(preferredBranchTargetAlignment(crb));
        asm.bind(returnLabel);
        // scale byte-based result back to stride
        asm.sarq(result);
    }
}
