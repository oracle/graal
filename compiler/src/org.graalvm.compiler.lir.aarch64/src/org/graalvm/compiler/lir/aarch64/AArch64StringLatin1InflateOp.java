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
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

@Opcode("AArch64_STRING_INFLATE")
public final class AArch64StringLatin1InflateOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64StringLatin1InflateOp> TYPE = LIRInstructionClass.create(AArch64StringLatin1InflateOp.class);

    final int chunkByteSize;
    @Alive({REG}) protected AllocatableValue src;
    @Alive({REG}) protected AllocatableValue dst;
    @Alive({REG}) protected AllocatableValue len;
    @Temp({REG}) protected AllocatableValue temp1;
    @Temp({REG}) protected AllocatableValue temp2;
    @Temp({REG}) protected AllocatableValue temp3;
    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;

    public AArch64StringLatin1InflateOp(LIRGeneratorTool tool, AllocatableValue src, AllocatableValue dst, AllocatableValue len) {
        super(TYPE);
        this.src = src;
        this.dst = dst;
        this.len = len;
        LIRKind archWordKind = LIRKind.value(tool.target().arch.getWordKind());
        temp1 = tool.newVariable(archWordKind);
        temp2 = tool.newVariable(archWordKind);
        temp3 = tool.newVariable(archWordKind);
        LIRKind vectorKind = LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD));
        vectorTemp1 = tool.newVariable(vectorKind);
        vectorTemp2 = tool.newVariable(vectorKind);
        chunkByteSize = 16;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Label simdImpl = new Label();
        Label ret = new Label();

        Register srcAddress = asRegister(temp1);
        Register destAddress = asRegister(temp2);
        Register length = asRegister(temp3);

        masm.mov(64, srcAddress, asRegister(src));
        masm.mov(64, destAddress, asRegister(dst));
        /* Zero-extend the length */
        masm.add(64, length, asRegister(len), zr, AArch64Assembler.ExtendType.UXTW, 0);

        masm.compare(64, length, chunkByteSize);
        masm.branchConditionally(AArch64Assembler.ConditionFlag.GE, simdImpl);
        emitScalar(masm, srcAddress, destAddress, length);
        masm.jmp(ret);

        masm.bind(simdImpl);
        emitSIMD(masm, srcAddress, destAddress, length);

        masm.bind(ret);
    }

    @SuppressWarnings("static-method")
    private void emitScalar(AArch64MacroAssembler masm, Register srcAddress, Register destAddress, Register count) {
        Label loop = new Label();

        try (AArch64MacroAssembler.ScratchRegister scratchReg1 = masm.getScratchRegister()) {
            Register currData = scratchReg1.getRegister();

            masm.align(16);
            masm.bind(loop);
            masm.ldr(8, currData, AArch64Address.createImmediateAddress(8, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, srcAddress, 1));
            masm.str(16, currData, AArch64Address.createImmediateAddress(16, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, destAddress, 2));
            masm.subs(64, count, count, 1);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.GT, loop);
        }
    }

    private void emitSIMD(AArch64MacroAssembler masm, Register srcChunkAddress, Register destChunkAddress, Register length) {
        Register destLowV = asRegister(vectorTemp1);
        Register destHighV = asRegister(vectorTemp2);

        Label simdLoop = new Label();
        Label ret = new Label();

        try (AArch64MacroAssembler.ScratchRegister scratchRegister1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister scratchRegister2 = masm.getScratchRegister()) {
            Register endOfSrcAddress = scratchRegister1.getRegister();
            Register lastChunkAddress = scratchRegister2.getRegister();

            masm.add(64, endOfSrcAddress, srcChunkAddress, length);
            masm.sub(64, lastChunkAddress, endOfSrcAddress, chunkByteSize);

            masm.align(16);
            masm.bind(simdLoop);
            masm.fldr(128, destLowV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, srcChunkAddress, chunkByteSize));
            masm.neon.uxtl2VV(AArch64ASIMDAssembler.ElementSize.Byte, destHighV, destLowV);
            masm.neon.uxtlVV(AArch64ASIMDAssembler.ElementSize.Byte, destLowV, destLowV);
            masm.fstp(128, destLowV, destHighV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, destChunkAddress, chunkByteSize * 2));
            masm.cmp(64, srcChunkAddress, lastChunkAddress);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.LO, simdLoop);

            /*
             * Process the last chunk. Move the source position back to the last chunk, 16 bytes
             * before the end of the input array. Move the destination position back twice the
             * movement of source position.
             */
            masm.cmp(64, srcChunkAddress, endOfSrcAddress);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.HS, ret);
            masm.sub(64, srcChunkAddress, srcChunkAddress, lastChunkAddress);
            masm.sub(64, destChunkAddress, destChunkAddress, srcChunkAddress, AArch64Assembler.ShiftType.LSL, 1);
            masm.mov(64, srcChunkAddress, lastChunkAddress);
            masm.jmp(simdLoop);

            masm.bind(ret);
        }
    }
}
