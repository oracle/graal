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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.SIMD;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Opcode("AArch64_STRING_INFLATE")
public final class AArch64StringLatin1InflateOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64StringLatin1InflateOp> TYPE = LIRInstructionClass.create(AArch64StringLatin1InflateOp.class);

    private static final int CHUNK_ELEMENT_COUNT = 16;

    @Use({REG}) protected AllocatableValue len;
    @Alive({REG}) protected AllocatableValue src;
    @Alive({REG}) protected AllocatableValue dst;
    @Temp({REG}) protected AllocatableValue temp1;
    @Temp({REG}) protected AllocatableValue temp2;
    @Temp({REG}) protected AllocatableValue temp3;
    @Temp({REG}) protected AllocatableValue vectorTemp1;
    @Temp({REG}) protected AllocatableValue vectorTemp2;

    public AArch64StringLatin1InflateOp(LIRGeneratorTool tool, AllocatableValue src, AllocatableValue dst, AllocatableValue len) {
        super(TYPE);
        assert len.getPlatformKind().equals(AArch64Kind.DWORD) : len;
        assert src.getPlatformKind().equals(AArch64Kind.QWORD) : src;
        assert dst.getPlatformKind().equals(AArch64Kind.QWORD) : dst;

        this.len = len;
        this.src = src;
        this.dst = dst;
        LIRKind archWordKind = LIRKind.value(AArch64Kind.QWORD);
        temp1 = tool.newVariable(archWordKind);
        temp2 = tool.newVariable(archWordKind);
        temp3 = tool.newVariable(archWordKind);
        LIRKind vectorKind = LIRKind.value(tool.target().arch.getLargestStorableKind(SIMD));
        vectorTemp1 = tool.newVariable(vectorKind);
        vectorTemp2 = tool.newVariable(vectorKind);

    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Label simdImpl = new Label();
        Label done = new Label();

        Register length = asRegister(temp1);
        Register srcAddress = asRegister(temp2);
        Register destAddress = asRegister(temp3);

        // return immediately if length is zero
        masm.cbz(32, asRegister(len), done);

        /*
         * Sign-extend length. Note length is guaranteed to be a non-negative value, so this is
         * equivalent to zero-extending length.
         */
        masm.sxt(64, 32, length, asRegister(len));

        masm.mov(64, srcAddress, asRegister(src));
        masm.mov(64, destAddress, asRegister(dst));

        masm.compare(64, length, CHUNK_ELEMENT_COUNT);
        masm.branchConditionally(AArch64Assembler.ConditionFlag.GE, simdImpl);

        emitScalar(masm, srcAddress, destAddress, length);
        masm.jmp(done);

        masm.bind(simdImpl);
        emitSIMD(masm, srcAddress, destAddress, length);

        masm.bind(done);
    }

    private static void emitScalar(AArch64MacroAssembler masm, Register srcAddress, Register destAddress, Register count) {
        Label loop = new Label();

        try (AArch64MacroAssembler.ScratchRegister scratchReg1 = masm.getScratchRegister()) {
            Register val = scratchReg1.getRegister();

            masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
            masm.bind(loop);
            // ldr zero-extends val to 64 bits
            masm.ldr(8, val, AArch64Address.createImmediateAddress(8, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, srcAddress, 1));
            masm.str(16, val, AArch64Address.createImmediateAddress(16, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, destAddress, 2));
            masm.subs(64, count, count, 1);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.GT, loop);
        }
    }

    private void emitSIMD(AArch64MacroAssembler masm, Register srcChunkAddress, Register destChunkAddress, Register length) {
        Register destLowV = asRegister(vectorTemp1);
        Register destHighV = asRegister(vectorTemp2);

        Label simdLoop = new Label();
        Label done = new Label();

        try (AArch64MacroAssembler.ScratchRegister scratchRegister1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister scratchRegister2 = masm.getScratchRegister()) {
            Register endOfSrcAddress = scratchRegister1.getRegister();
            Register lastChunkAddress = scratchRegister2.getRegister();

            masm.add(64, endOfSrcAddress, srcChunkAddress, length);
            masm.sub(64, lastChunkAddress, endOfSrcAddress, CHUNK_ELEMENT_COUNT);

            masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
            masm.bind(simdLoop);
            // load elements
            masm.fldr(128, destLowV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, srcChunkAddress, CHUNK_ELEMENT_COUNT));
            // split elements across 2 registers and inflate
            masm.neon.uxtl2VV(AArch64ASIMDAssembler.ElementSize.Byte, destHighV, destLowV);
            masm.neon.uxtlVV(AArch64ASIMDAssembler.ElementSize.Byte, destLowV, destLowV);
            // store inflated elements
            masm.fstp(128, destLowV, destHighV, AArch64Address.createImmediateAddress(128, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, destChunkAddress, CHUNK_ELEMENT_COUNT * 2));
            masm.cmp(64, srcChunkAddress, lastChunkAddress);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.LO, simdLoop);

            /*
             * Process the last chunk. Move the source position back to the last chunk, 16 bytes
             * before the end of the input array. Move the destination position back twice the
             * movement of source position.
             */
            masm.cmp(64, srcChunkAddress, endOfSrcAddress);
            masm.branchConditionally(AArch64Assembler.ConditionFlag.HS, done);
            masm.sub(64, srcChunkAddress, srcChunkAddress, lastChunkAddress);
            masm.sub(64, destChunkAddress, destChunkAddress, srcChunkAddress, AArch64Assembler.ShiftType.LSL, 1);
            masm.mov(64, srcChunkAddress, lastChunkAddress);
            masm.jmp(simdLoop);

            masm.bind(done);
        }
    }
}
