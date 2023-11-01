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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

@Opcode("MATH_SIGNUM")
public final class AArch64MathCopySignOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64MathCopySignOp> TYPE = LIRInstructionClass.create(AArch64MathCopySignOp.class);

    @Def({REG}) protected Value result;
    /**
     * These arguments must be Alive to ensure that they are not assigned to the same register as
     * result, which would break the code generation by destroying these arguments too early.
     */
    @Alive({REG}) protected Value magnitude;
    @Alive({REG}) protected Value sign;

    public AArch64MathCopySignOp(Value result, AllocatableValue magnitude, AllocatableValue sign) {
        super(TYPE);
        this.result = result;
        this.magnitude = magnitude;
        this.sign = sign;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register resultReg = asRegister(result);
        Register magnitudeReg = asRegister(magnitude);
        Register signReg = asRegister(sign);

        int size = result.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        masm.fmov(size, resultReg, 0);
        masm.fneg(size, resultReg, resultReg);
        masm.neon.bslVVV(ASIMDSize.HalfReg, resultReg, signReg, magnitudeReg);
    }

}
