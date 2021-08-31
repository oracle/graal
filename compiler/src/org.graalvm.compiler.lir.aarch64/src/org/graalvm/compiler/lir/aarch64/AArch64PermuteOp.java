/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * This enum encapsulates AArch64 instructions which perform permutations.
 */
public enum AArch64PermuteOp {
    TBL,
    ZIPLOW,
    ZIPHIGH,
    ZIPEVEN,
    ZIPODD,
    UNZIPEVEN,
    UNZIPODD;

    public static class ASIMDBinaryOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDBinaryOp> TYPE = LIRInstructionClass.create(ASIMDBinaryOp.class);

        @Opcode private final AArch64PermuteOp op;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue a;
        @Use({REG}) protected AllocatableValue b;

        public ASIMDBinaryOp(AArch64PermuteOp op, AllocatableValue result, AllocatableValue a, AllocatableValue b) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.a = a;
            this.b = b;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());
            Register resultReg = asRegister(result);
            Register aReg = asRegister(a);
            Register bReg = asRegister(b);

            switch (op) {
                case TBL:
                    masm.neon.tblVVV(size, resultReg, aReg, bReg);
                    break;
                case ZIPLOW:
                    masm.neon.zip1VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case ZIPHIGH:
                    masm.neon.zip2VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case ZIPEVEN:
                    masm.neon.trn1VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case ZIPODD:
                    masm.neon.trn2VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case UNZIPEVEN:
                    masm.neon.uzp1VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                case UNZIPODD:
                    masm.neon.uzp2VVV(size, eSize, resultReg, aReg, bReg);
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }

        }
    }
}
