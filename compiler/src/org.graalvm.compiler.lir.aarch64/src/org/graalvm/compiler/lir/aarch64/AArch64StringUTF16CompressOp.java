/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

@Opcode("AArch64_STRING_COMPRESS")
public final class AArch64StringUTF16CompressOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64StringUTF16CompressOp> TYPE = LIRInstructionClass.create(AArch64StringUTF16CompressOp.class);

    final int chunkByteSize;
    @Def({REG}) protected AllocatableValue resultValue;
    @Alive({REG}) protected AllocatableValue src;
    @Alive({REG}) protected AllocatableValue dst;
    @Use({REG}) protected AllocatableValue len;
    @Temp({REG}) protected AllocatableValue temp1;
    @Temp({REG}) protected AllocatableValue temp2;
    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;
    @Temp({REG}) protected AllocatableValue vectorTemp3;

    public AArch64StringUTF16CompressOp(LIRGeneratorTool tool, AllocatableValue src, AllocatableValue dst, AllocatableValue len, AllocatableValue result) {
        super(TYPE);
        this.src = src;
        this.dst = dst;
        this.len = len;
        resultValue = result;
        LIRKind archWordKind = LIRKind.value(tool.target().arch.getWordKind());
        temp1 = tool.newVariable(archWordKind);
        temp2 = tool.newVariable(archWordKind);
        LIRKind vectorKind = LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD));
        vectorTemp1 = tool.newVariable(vectorKind);
        vectorTemp2 = tool.newVariable(vectorKind);
        vectorTemp3 = tool.newVariable(vectorKind);
        chunkByteSize = 32;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Label simdImpl = new Label();
        Label scalarLoop = new Label();
        Label ret = new Label();

        Register srcAddress = asRegister(temp1);
        Register destAddress = asRegister(temp2);
        masm.mov(64, srcAddress, asRegister(src));
        masm.mov(64, destAddress, asRegister(dst));

        /* Initialise result for successful return */
        Register length = asRegister(resultValue);
        /* Zero-extend the length */
        masm.add(64, length, asRegister(len), zr, AArch64Assembler.ExtendType.UXTW, 0);
        masm.compare(64, length, chunkByteSize);
        masm.branchConditionally(ConditionFlag.GE, simdImpl);
        emitScalar(masm, scalarLoop, ret, srcAddress, destAddress, length);

        masm.bind(simdImpl);
        emitSIMD(masm, scalarLoop, ret, srcAddress, destAddress, length);

        masm.bind(ret);
    }

    private void emitScalar(AArch64MacroAssembler masm, Label scalarLoop, Label ret, Register srcAddress, Register destAddress, Register length) {
        Label failToCompress = new Label();

        try (ScratchRegister scratchReg1 = masm.getScratchRegister()) {
            Register currData = scratchReg1.getRegister();

            masm.align(16);
            masm.bind(scalarLoop);
            /*
             * If the current char is greater than 0xFF, stop compressing and return zero as result
             */
            masm.ldr(16, currData, AArch64Address.createImmediateAddress(16, AddressingMode.IMMEDIATE_POST_INDEXED, srcAddress, 2));
            masm.compare(64, currData, 0xFF);
            masm.branchConditionally(ConditionFlag.GT, failToCompress);

            masm.str(8, currData, AArch64Address.createImmediateAddress(8, AddressingMode.IMMEDIATE_POST_INDEXED, destAddress, 1));
            masm.subs(64, length, length, 1);
            masm.branchConditionally(ConditionFlag.GT, scalarLoop);
            masm.mov(64, asRegister(resultValue), asRegister(len));
            masm.jmp(ret);

            masm.bind(failToCompress);
            masm.mov(64, asRegister(resultValue), zr);
        }
    }

    private void emitSIMD(AArch64MacroAssembler masm, Label scalarLoop, Label ret, Register srcChunkAddress, Register destChunkAddress, Register length) {
        Register chunkPart1RegV = asRegister(vectorTemp1);
        Register chunkPart2RegV = asRegister(vectorTemp2);
        Register tmpRegV1 = asRegister(vectorTemp3);

        Label simdLoop = new Label();
        Label failToCompress = new Label();

        AArch64ASIMDAssembler.ElementSize eSize = AArch64ASIMDAssembler.ElementSize.fromSize(16);
        try (ScratchRegister scratchRegister1 = masm.getScratchRegister(); ScratchRegister scratchRegister2 = masm.getScratchRegister()) {
            Register lastChunkAddress = scratchRegister1.getRegister();
            Register endOfSrcAddress = scratchRegister2.getRegister();
            masm.add(64, endOfSrcAddress, srcChunkAddress, length, AArch64Assembler.ShiftType.LSL, 1);
            masm.sub(64, lastChunkAddress, endOfSrcAddress, chunkByteSize);

            masm.align(16);
            masm.bind(simdLoop);
            masm.fldp(128, chunkPart1RegV, chunkPart2RegV, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, srcChunkAddress, 32));
            /*
             * If a char in the chunk is greater than 0xFF, stop compressing and return zero as a
             * result
             */
            masm.neon.orrVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, tmpRegV1, chunkPart1RegV, chunkPart2RegV);
            masm.neon.uzp2VVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, eSize.narrow(), tmpRegV1, tmpRegV1, tmpRegV1);
            masm.fcmpZero(64, tmpRegV1);
            masm.branchConditionally(ConditionFlag.NE, failToCompress);

            masm.neon.xtnVV(eSize.narrow(), tmpRegV1, chunkPart1RegV);
            masm.neon.xtn2VV(eSize.narrow(), tmpRegV1, chunkPart2RegV);
            masm.fstr(128, tmpRegV1, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, destChunkAddress, 16));
            masm.cmp(64, srcChunkAddress, lastChunkAddress);
            masm.branchConditionally(ConditionFlag.LO, simdLoop);

            /*
             * Process the last chunk. Move the source position back to the last chunk, 16 bytes
             * before the end of the input array. Move the destination position back twice the
             * movement of source position.
             */
            masm.cmp(64, srcChunkAddress, endOfSrcAddress);
            masm.branchConditionally(ConditionFlag.HS, ret);
            masm.sub(64, srcChunkAddress, srcChunkAddress, lastChunkAddress);
            masm.sub(64, destChunkAddress, destChunkAddress, srcChunkAddress, AArch64Assembler.ShiftType.LSR, 1);
            masm.mov(64, srcChunkAddress, lastChunkAddress);
            masm.jmp(simdLoop);
        }

        masm.bind(failToCompress);
        /*
         * Copy compressed characters until the character that failed to compress. Compress and copy
         * the first 16 bytes if the un-compressible char(s) not present. Jump to the scalar loop to
         * process the failed chunk char-by-char.
         */
        masm.sub(64, srcChunkAddress, srcChunkAddress, 32);
        masm.mov(length, 16);
        masm.neon.uzp2VVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, eSize.narrow(), tmpRegV1, chunkPart1RegV, chunkPart1RegV);
        masm.fcmpZero(64, tmpRegV1);
        masm.branchConditionally(ConditionFlag.NE, scalarLoop);
        masm.neon.xtnVV(eSize.narrow(), tmpRegV1, chunkPart1RegV);
        masm.fstr(64, tmpRegV1, AArch64Address.createImmediateAddress(64, AddressingMode.IMMEDIATE_POST_INDEXED, destChunkAddress, 8));
        masm.neon.moveVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, chunkPart1RegV, chunkPart2RegV);
        masm.add(64, srcChunkAddress, srcChunkAddress, 16);
        masm.jmp(scalarLoop);
    }
}
