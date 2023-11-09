/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;

public class AArch64ByteSwap {

    @Opcode("BSWAP")
    public static class ByteSwapOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ByteSwapOp> TYPE = LIRInstructionClass.create(ByteSwapOp.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        public ByteSwapOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            AArch64Kind kind = (AArch64Kind) input.getPlatformKind();
            assert kind == AArch64Kind.DWORD || kind == AArch64Kind.QWORD : kind;

            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            int size = input.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            masm.rev(size, asRegister(result), asRegister(input));
        }
    }

    @Opcode("BSWAP")
    public static class ASIMDByteSwapOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ASIMDByteSwapOp> TYPE = LIRInstructionClass.create(ASIMDByteSwapOp.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        public ASIMDByteSwapOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            assert ((AArch64Kind) result.getPlatformKind()).getScalar().isInteger();
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());
            masm.neon.revVV(size, eSize, asRegister(result), asRegister(input));
        }
    }
}
