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

import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
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

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Opcode("AArch64_STRING_COMPRESS")
public final class AArch64StringUTF16CompressOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64StringUTF16CompressOp> TYPE = LIRInstructionClass.create(AArch64StringUTF16CompressOp.class);

    private static final int CHUNK_ELEMENT_COUNT = 16;

    @Def({REG}) protected AllocatableValue resultValue;
    @Use({REG}) protected AllocatableValue len;
    @Alive({REG}) protected AllocatableValue src;
    @Alive({REG}) protected AllocatableValue dst;
    @Temp({REG}) protected AllocatableValue temp1;
    @Temp({REG}) protected AllocatableValue temp2;
    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;
    @Temp({REG}) protected AllocatableValue vectorTemp3;

    public AArch64StringUTF16CompressOp(LIRGeneratorTool tool, AllocatableValue src, AllocatableValue dst, AllocatableValue len, AllocatableValue result) {
        super(TYPE);
        assert result.getPlatformKind().equals(AArch64Kind.DWORD) : result;
        assert len.getPlatformKind().equals(AArch64Kind.DWORD) : len;
        assert src.getPlatformKind().equals(AArch64Kind.QWORD) : src;
        assert dst.getPlatformKind().equals(AArch64Kind.QWORD) : dst;

        this.len = len;
        this.src = src;
        this.dst = dst;
        resultValue = result;
        LIRKind archWordKind = LIRKind.value(AArch64Kind.QWORD);
        temp1 = tool.newVariable(archWordKind);
        temp2 = tool.newVariable(archWordKind);
        LIRKind vectorKind = LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD));
        vectorTemp1 = tool.newVariable(vectorKind);
        vectorTemp2 = tool.newVariable(vectorKind);
        vectorTemp3 = tool.newVariable(vectorKind);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Label simdImpl = new Label();
        Label scalarImpl = new Label();
        Label done = new Label();

        /*
         * Initialise result for successful return (a sign-extended length). Note length is
         * guaranteed to be a non-negative value, so this is equivalent to zero-extending length.
         */
        Register result = asRegister(resultValue);
        masm.sxt(64, 32, result, asRegister(len));

        // return immediately if length is zero
        masm.cbz(32, result, done);

        Register srcAddress = asRegister(temp1);
        Register destAddress = asRegister(temp2);
        masm.mov(64, srcAddress, asRegister(src));
        masm.mov(64, destAddress, asRegister(dst));

        masm.compare(64, result, CHUNK_ELEMENT_COUNT);
        masm.branchConditionally(ConditionFlag.GE, simdImpl);

        masm.bind(scalarImpl);
        emitScalar(masm, done, srcAddress, destAddress, result);
        masm.jmp(done);

        masm.bind(simdImpl);
        emitSIMD(masm, scalarImpl, done, srcAddress, destAddress, result);

        masm.bind(done);
    }

    private static void emitScalar(AArch64MacroAssembler masm, Label done, Register srcAddress, Register destAddress, Register result) {
        Label failToCompress = new Label();
        Label scalarLoop = new Label();

        try (ScratchRegister scratchReg1 = masm.getScratchRegister(); ScratchRegister scratchReg2 = masm.getScratchRegister()) {
            Register val = scratchReg1.getRegister();
            Register count = scratchReg2.getRegister();

            masm.mov(64, count, result);

            masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
            masm.bind(scalarLoop);
            masm.ldr(16, val, AArch64Address.createImmediateAddress(16, AddressingMode.IMMEDIATE_POST_INDEXED, srcAddress, 2));
            /*
             * If the current char is greater than 0xFF, stop compressing and return zero as result.
             */
            masm.compare(64, val, 0xFF);
            masm.branchConditionally(ConditionFlag.GT, failToCompress);

            masm.str(8, val, AArch64Address.createImmediateAddress(8, AddressingMode.IMMEDIATE_POST_INDEXED, destAddress, 1));
            masm.subs(64, count, count, 1);
            masm.branchConditionally(ConditionFlag.GT, scalarLoop);
            masm.jmp(done);

            masm.bind(failToCompress);
            masm.mov(64, result, zr);
        }
    }

    private void emitSIMD(AArch64MacroAssembler masm, Label scalarImpl, Label done, Register srcChunkAddress, Register destChunkAddress, Register result) {
        Register chunkPart1RegV = asRegister(vectorTemp1);
        Register chunkPart2RegV = asRegister(vectorTemp2);
        Register tmpRegV1 = asRegister(vectorTemp3);

        Label simdLoop = new Label();
        Label failToCompress = new Label();
        Label redoEntireChunk = new Label();

        try (ScratchRegister scratchRegister1 = masm.getScratchRegister(); ScratchRegister scratchRegister2 = masm.getScratchRegister()) {
            Register lastChunkAddress = scratchRegister1.getRegister();
            Register endOfSrcAddress = scratchRegister2.getRegister();
            masm.add(64, endOfSrcAddress, srcChunkAddress, result, AArch64Assembler.ShiftType.LSL, 1);
            masm.sub(64, lastChunkAddress, endOfSrcAddress, CHUNK_ELEMENT_COUNT * 2);

            masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
            masm.bind(simdLoop);
            masm.fldp(128, chunkPart1RegV, chunkPart2RegV, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, srcChunkAddress, CHUNK_ELEMENT_COUNT * 2));
            /*
             * If an element in the chunk is greater than 0xFF, stop compressing and retry with
             * scalar code.
             *
             * To do so, we check for any top bytes which are not zero.
             */
            masm.neon.orrVVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, tmpRegV1, chunkPart1RegV, chunkPart2RegV);
            masm.neon.uzp2VVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, ElementSize.Byte, tmpRegV1, tmpRegV1, tmpRegV1);
            masm.fcmpZero(64, tmpRegV1);
            masm.branchConditionally(ConditionFlag.NE, failToCompress);

            // compress elements
            masm.neon.xtnVV(ElementSize.Byte, tmpRegV1, chunkPart1RegV);
            masm.neon.xtn2VV(ElementSize.Byte, tmpRegV1, chunkPart2RegV);
            masm.fstr(128, tmpRegV1, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, destChunkAddress, CHUNK_ELEMENT_COUNT));
            masm.cmp(64, srcChunkAddress, lastChunkAddress);
            masm.branchConditionally(ConditionFlag.LO, simdLoop);

            /*
             * Process the last chunk. Move the source position back to the last chunk, 16 bytes
             * before the end of the input array. Move the destination position back twice the
             * movement of source position.
             */
            masm.cmp(64, srcChunkAddress, endOfSrcAddress);
            masm.branchConditionally(ConditionFlag.HS, done);
            masm.sub(64, srcChunkAddress, srcChunkAddress, lastChunkAddress);
            // srcChunkAddress will be a value in the range (1, 31), so LSR and ASR are equivalent
            masm.sub(64, destChunkAddress, destChunkAddress, srcChunkAddress, AArch64Assembler.ShiftType.LSR, 1);
            masm.mov(64, srcChunkAddress, lastChunkAddress);
            masm.jmp(simdLoop);
        }

        masm.bind(failToCompress);
        /*
         * Determine whether the first un-compressible element was in the first chunk. Compress and
         * copy the first 16 bytes if the first un-compressible chars is in the second chunk.
         * Afterwards, jump to the scalar loop to process the failed chunk element-by-element.
         *
         * Because we start immediately at the failing chunk part, we know at most 8 elements need
         * to be checked to find the mismatch.
         */
        masm.mov(result, 8);
        masm.neon.uzp2VVV(AArch64ASIMDAssembler.ASIMDSize.FullReg, ElementSize.Byte, tmpRegV1, chunkPart1RegV, chunkPart1RegV);
        masm.fcmpZero(64, tmpRegV1);
        masm.branchConditionally(ConditionFlag.NE, redoEntireChunk);

        // fall-through: store chunkPart1 and start search at chunkPart2
        // note this is really CHUNK_ELEMENT_SIZE * 2 / 2
        masm.sub(64, srcChunkAddress, srcChunkAddress, CHUNK_ELEMENT_COUNT);
        masm.neon.xtnVV(ElementSize.Byte, tmpRegV1, chunkPart1RegV);
        masm.fstr(64, tmpRegV1, AArch64Address.createImmediateAddress(64, AddressingMode.IMMEDIATE_POST_INDEXED, destChunkAddress, CHUNK_ELEMENT_COUNT / 2));
        masm.jmp(scalarImpl);

        // start at chunkPart1
        masm.bind(redoEntireChunk);
        masm.sub(64, srcChunkAddress, srcChunkAddress, CHUNK_ELEMENT_COUNT * 2);
        masm.jmp(scalarImpl);
    }
}
