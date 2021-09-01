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
 * This enum encapsulates AArch64 instructions which perform an operation over the elements within a
 * register.
 */
public enum AArch64AcrossVectorOp {
    ADDV, // signed add
    SADDLV, // signed add and expand
    UADDLV, // unsigned add and expand
    UMAX; // unsigned max

    public static class ASIMDOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<ASIMDOp> TYPE = LIRInstructionClass.create(ASIMDOp.class);

        @Opcode private final AArch64AcrossVectorOp op;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        public ASIMDOp(AArch64AcrossVectorOp op, AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.op = op;
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            ASIMDSize size = ASIMDSize.fromVectorKind(input.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(input.getPlatformKind());

            Register resultReg = asRegister(result);
            Register inputReg = asRegister(input);

            switch (op) {
                case ADDV:
                    masm.neon.addvSV(size, eSize, resultReg, inputReg);
                    break;
                case SADDLV:
                    assert ElementSize.fromKind(result.getPlatformKind()) == eSize.expand();
                    masm.neon.saddlvSV(size, eSize, resultReg, inputReg);
                    break;
                case UADDLV:
                    assert ElementSize.fromKind(result.getPlatformKind()) == eSize.expand();
                    masm.neon.uaddlvSV(size, eSize, resultReg, inputReg);
                    break;
                case UMAX:
                    masm.neon.umaxvSV(size, eSize, resultReg, inputReg);
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }
}
