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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.createBaseRegisterOnlyAddress;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Emits code to find the index of the first non-equal elements in two arrays, using SIMD
 * instructions where possible.
 */
@Opcode("VECTORIZED_MISMATCH")
public final class AArch64VectorizedMismatchOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64VectorizedMismatchOp> TYPE = LIRInstructionClass.create(AArch64VectorizedMismatchOp.class);

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value arrayAValue;
    @Alive({REG}) protected Value arrayBValue;
    @Alive({REG}) protected Value lengthValue;
    @Alive({REG}) protected Value strideValue;
    @Temp({REG}) protected AllocatableValue[] temp;
    @Temp({REG}) protected AllocatableValue[] vectorTemp;

    public AArch64VectorizedMismatchOp(LIRGeneratorTool tool, Value result, Value arrayA, Value arrayB, Value length, Value stride) {
        super(TYPE);

        GraalError.guarantee(result.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(arrayA.getPlatformKind() == AArch64Kind.QWORD && arrayA.getPlatformKind() == arrayB.getPlatformKind(), "pointer value expected");
        GraalError.guarantee(length.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(stride.getPlatformKind() == AArch64Kind.DWORD, "int value expected");

        this.resultValue = result;
        this.arrayAValue = arrayA;
        this.arrayBValue = arrayB;
        this.lengthValue = length;
        this.strideValue = stride;

        temp = allocateTempRegisters(tool, 3);
        vectorTemp = allocateVectorRegisters(tool, 5);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            Register arrayA = sc1.getRegister();
            Register arrayB = sc2.getRegister();
            Register length = asRegister(temp[0]);
            Register tmp = asRegister(temp[1]);
            Register ret = asRegister(resultValue);
            Label end = new Label();

            // Load array base addresses.
            masm.mov(64, arrayA, asRegister(arrayAValue));
            masm.mov(64, arrayB, asRegister(arrayBValue));

            emitVectorizedMismatch(masm, arrayA, arrayB, length, tmp, ret, end);

            masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            masm.bind(end);
            masm.asr(64, ret, ret, asRegister(strideValue));
        }
    }

    private void emitVectorizedMismatch(AArch64MacroAssembler asm, Register arrayA, Register arrayB, Register len, Register tmp, Register ret, Label end) {
        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();
        Label tailLessThan2 = new Label();

        Label vectorLoop = new Label();
        Label diffFound = new Label();
        Label retEqual = new Label();

        Register vecArrayA1 = asRegister(vectorTemp[0]);
        Register vecArrayA2 = asRegister(vectorTemp[1]);
        Register vecArrayB1 = asRegister(vectorTemp[2]);
        Register vecArrayB2 = asRegister(vectorTemp[3]);
        Register vecMask = asRegister(vectorTemp[4]);

        Register refAddress = asRegister(temp[2]);

        asm.lsl(32, len, asRegister(lengthValue), asRegister(strideValue));

        initCalcIndexOfFirstMatchMask(asm, vecMask, tmp);

        // subtract 32 from len. if the result is negative, jump to branch for less than 32 bytes
        asm.subs(64, len, len, 32);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan32);

        // fast path: check first 8 bytes
        Label fastPathContinue = new Label();
        asm.ldr(64, ret, createBaseRegisterOnlyAddress(64, arrayA));
        asm.ldr(64, tmp, createBaseRegisterOnlyAddress(64, arrayB));
        asm.add(64, refAddress, arrayA, len);
        asm.fldp(128, vecArrayA1, vecArrayA2, createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayA, 32));
        asm.fldp(128, vecArrayB1, vecArrayB2, createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayB, 32));
        asm.eor(64, ret, ret, tmp);
        asm.cbz(64, ret, fastPathContinue);
        asm.rbit(64, ret, ret);
        asm.clz(64, ret, ret);
        asm.lsr(64, ret, ret, 3);
        asm.jmp(end);

        // 32 byte loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(vectorLoop);
        asm.fldp(128, vecArrayA1, vecArrayA2, createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayA, 32));
        asm.fldp(128, vecArrayB1, vecArrayB2, createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayB, 32));
        asm.bind(fastPathContinue);
        asm.neon.eorVVV(FullReg, vecArrayA1, vecArrayA1, vecArrayB1);
        asm.neon.eorVVV(FullReg, vecArrayA2, vecArrayA2, vecArrayB2);
        asm.neon.orrVVV(FullReg, vecArrayB1, vecArrayA1, vecArrayA2);
        asm.neon.umaxvSV(FullReg, ElementSize.Word, vecArrayB1, vecArrayB1);
        asm.fcmpZero(64, vecArrayB1);
        asm.branchConditionally(ConditionFlag.NE, diffFound);
        asm.cmp(64, arrayA, refAddress);
        asm.branchConditionally(ConditionFlag.LO, vectorLoop);

        // 32 byte loop tail
        asm.sub(64, tmp, arrayA, refAddress);
        asm.mov(64, arrayA, refAddress);
        asm.sub(64, arrayB, arrayB, tmp);

        asm.fldp(128, vecArrayA1, vecArrayA2, createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayA, 32));
        asm.fldp(128, vecArrayB1, vecArrayB2, createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayB, 32));
        asm.neon.eorVVV(FullReg, vecArrayA1, vecArrayA1, vecArrayB1);
        asm.neon.eorVVV(FullReg, vecArrayA2, vecArrayA2, vecArrayB2);
        asm.neon.orrVVV(FullReg, vecArrayB1, vecArrayA1, vecArrayA2);
        asm.neon.umaxvSV(FullReg, ElementSize.Word, vecArrayB1, vecArrayB1);
        asm.fcmpZero(64, vecArrayB1);
        asm.branchConditionally(ConditionFlag.EQ, retEqual);

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(diffFound);
        // differing bytes found, calculate index
        asm.neon.cmtstVVV(FullReg, ElementSize.Byte, vecArrayA1, vecArrayA1, vecArrayA1);
        asm.neon.cmtstVVV(FullReg, ElementSize.Byte, vecArrayA2, vecArrayA2, vecArrayA2);
        calcIndexOfFirstMatch(asm, ret, vecArrayA1, vecArrayA2, vecMask, false, () -> {
            asm.sub(64, tmp, arrayA, asRegister(arrayAValue));
            asm.sub(64, tmp, tmp, 32);
        });
        asm.add(64, ret, tmp, ret, ShiftType.LSR, 1);
        asm.jmp(end);

        // tail for 16 - 31 bytes
        tailLessThan32(asm, arrayA, arrayB, len, tmp, ret, vecArrayA1, vecArrayA2, vecArrayB1, vecArrayB2, vecMask, tailLessThan32, tailLessThan16, retEqual, end);
        // tail for 8 - 15 bytes
        tail(asm, arrayA, arrayB, len, tmp, ret, end, tailLessThan16, tailLessThan8, retEqual, 8);
        // tail for 4 - 7 bytes
        tail(asm, arrayA, arrayB, len, tmp, ret, end, tailLessThan8, tailLessThan4, retEqual, 4);
        // tail for 2 - 3 bytes
        tail(asm, arrayA, arrayB, len, tmp, ret, end, tailLessThan4, tailLessThan2, retEqual, 2);
        // tail for 0 - 1 bytes
        asm.bind(tailLessThan2);
        // check for len == 0
        asm.adds(64, ret, len, 1);
        asm.branchConditionally(ConditionFlag.MI, end);
        // len == 1
        asm.ldr(8, ret, AArch64Address.createBaseRegisterOnlyAddress(8, arrayA));
        asm.ldr(8, tmp, AArch64Address.createBaseRegisterOnlyAddress(8, arrayB));
        asm.eor(64, ret, ret, tmp);
        asm.cbz(64, ret, retEqual);
        asm.mov(64, ret, zr);
        asm.jmp(end);

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(retEqual);
        asm.mov(ret, -1);
        asm.lsl(64, ret, ret, asRegister(strideValue));
    }

    private static void tail(AArch64MacroAssembler asm, Register arrayA, Register arrayB, Register len, Register tmp, Register ret, Label end, Label entry, Label nextTail, Label retEqual,
                    int nBytes) {
        Label endOfArray = new Label();
        int bits = nBytes << 3;
        asm.bind(entry);
        // check if length is big enough for current load size
        asm.adds(64, len, len, nBytes);
        asm.branchConditionally(ConditionFlag.MI, nextTail);
        // load from start of arrays
        asm.ldr(bits, ret, AArch64Address.createBaseRegisterOnlyAddress(bits, arrayA));
        asm.ldr(bits, tmp, AArch64Address.createBaseRegisterOnlyAddress(bits, arrayB));
        asm.eor(64, ret, ret, tmp);
        asm.cbz(64, ret, endOfArray);
        asm.rbit(Math.max(bits, 32), ret, ret);
        asm.clz(Math.max(bits, 32), ret, ret);
        asm.lsr(64, ret, ret, 3);
        asm.jmp(end);
        asm.bind(endOfArray);
        // load from end of arrays
        asm.ldr(bits, ret, AArch64Address.createRegisterOffsetAddress(bits, arrayA, len, false));
        asm.ldr(bits, tmp, AArch64Address.createRegisterOffsetAddress(bits, arrayB, len, false));
        asm.eor(64, ret, ret, tmp);
        asm.cbz(64, ret, retEqual);
        asm.rbit(Math.max(bits, 32), ret, ret);
        asm.clz(Math.max(bits, 32), ret, ret);
        asm.add(64, ret, len, ret, ShiftType.LSR, 3);
        asm.jmp(end);
    }

    private static void tailLessThan32(AArch64MacroAssembler asm,
                    Register arrayA,
                    Register arrayB,
                    Register len,
                    Register tmp,
                    Register ret,
                    Register vecArrayA1,
                    Register vecArrayA2,
                    Register vecArrayB1,
                    Register vecArrayB2,
                    Register vecMask,
                    Label entry,
                    Label nextTail,
                    Label retEqual, Label end) {
        asm.bind(entry);
        // check if length is big enough for current load size
        asm.adds(64, len, len, 16);
        asm.branchConditionally(ConditionFlag.MI, nextTail);
        // load from start of arrays
        asm.fldr(128, vecArrayA1, AArch64Address.createBaseRegisterOnlyAddress(128, arrayA));
        asm.fldr(128, vecArrayB1, AArch64Address.createBaseRegisterOnlyAddress(128, arrayB));
        // load from end of arrays
        asm.fldr(128, vecArrayA2, AArch64Address.createRegisterOffsetAddress(128, arrayA, len, false));
        asm.fldr(128, vecArrayB2, AArch64Address.createRegisterOffsetAddress(128, arrayB, len, false));
        asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecArrayA1, vecArrayA1, vecArrayB1);
        asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecArrayA2, vecArrayA2, vecArrayB2);
        calcIndexOfFirstMatch(asm, ret, vecArrayA1, vecArrayA2, vecMask, true, () -> {
            asm.cbz(64, ret, retEqual);
        });
        asm.sub(64, tmp, len, 16);
        asm.lsr(64, ret, ret, 1);
        asm.add(64, tmp, ret, tmp);
        asm.compare(64, ret, 16);
        asm.csel(64, ret, ret, tmp, ConditionFlag.LO);
        asm.jmp(end);
    }
}
