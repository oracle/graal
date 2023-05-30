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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Returns {@code true} if the given byte array contains any negative bytes, otherwise
 * {@code false}.
 */
@Opcode("HAS_NEGATIVES")
public final class AArch64HasNegativesOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64HasNegativesOp> TYPE = LIRInstructionClass.create(AArch64HasNegativesOp.class);

    @Def({REG}) private Value result;
    @Alive({REG}) private Value array;
    @Alive({REG}) private Value length;

    @Temp({REG}) private Value[] temp;
    @Temp({REG}) private Value[] vectorTemp;

    public AArch64HasNegativesOp(LIRGeneratorTool tool, Value result, Value array, Value length) {
        super(TYPE);
        this.result = result;
        this.array = array;
        this.length = length;
        temp = allocateTempRegisters(tool, 1);
        vectorTemp = allocateVectorRegisters(tool, 3);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler asm) {
        try (AArch64MacroAssembler.ScratchRegister sc1 = asm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister sc2 = asm.getScratchRegister()) {
            Register arr = sc1.getRegister();
            Register len = sc2.getRegister();
            Register tmp = asRegister(temp[0]);
            Register ret = asRegister(result);
            Label end = new Label();
            asm.mov(64, arr, asRegister(array));
            asm.mov(32, len, asRegister(length));
            AArch64CalcStringAttributesOp.emitLatin1(asm, arr, len, tmp, ret, end, asRegister(vectorTemp[0]), asRegister(vectorTemp[1]), asRegister(vectorTemp[2]));
            asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            asm.bind(end);
        }
    }
}
