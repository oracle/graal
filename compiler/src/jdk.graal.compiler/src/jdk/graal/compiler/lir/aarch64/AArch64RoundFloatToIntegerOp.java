/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Round float to integer. Line by line assembly translation rounding algorithm. Please refer to
 * {@link Math#round} algorithm for details.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/7bb59dc8da0c61c5da5c3aab5d56a6e4880001ce/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L6295-L6343",
          sha1 = "76d47473bf8d1408bf6e7bf6b8a3d93c19dab9c6")
// @formatter:on
@Opcode("AARCH64_ROUND_FLOAT_TO_INTEGER")
public class AArch64RoundFloatToIntegerOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64RoundFloatToIntegerOp> TYPE = LIRInstructionClass.create(AArch64RoundFloatToIntegerOp.class);

    @LIRInstruction.Def({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.HINT}) protected AllocatableValue result;
    @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected AllocatableValue input;

    @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG}) protected AllocatableValue tmp;

    public AArch64RoundFloatToIntegerOp(LIRGeneratorTool tool, AllocatableValue result, AllocatableValue input) {
        super(TYPE);

        this.result = result;
        this.input = input;

        this.tmp = tool.newVariable(input.getValueKind());
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register dst = asRegister(result);
        Register src = asRegister(input);
        Register ftmp = asRegister(tmp);

        Label labelDONE = new Label();

        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();
            Register rscratch2 = sc2.getRegister();

            if (input.getPlatformKind() == AArch64Kind.SINGLE) {
                masm.fmov(32, rscratch1, src);
                // Use RoundToNearestTiesAway unless src small and -ve.
                masm.fcvtas(32, 32, dst, src);
                // Test if src >= 0 || abs(src) >= 0x1.0p23
                masm.eor(32, rscratch1, rscratch1, 0x80000000); // flip sign bit
                masm.mov(rscratch2, Float.floatToIntBits(0x1.0p23f));
                masm.cmp(32, rscratch1, rscratch2);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.HS, labelDONE);
                // src < 0 && |src| < 0x1.0p23
                // src may have a fractional part, so add 0.5
                masm.fmov(32, ftmp, 0.5f);
                masm.fadd(32, ftmp, src, ftmp);
                // Convert float to jint, use RoundTowardsNegative
                masm.fcvtms(32, 32, dst, ftmp);
            } else {
                masm.fmov(64, rscratch1, src);
                // Use RoundToNearestTiesAway unless src small and -ve.
                masm.fcvtas(64, 64, dst, src);
                // Test if src >= 0 || abs(src) >= 0x1.0p52
                masm.eor(64, rscratch1, rscratch1, 0x80000000_00000000L); // flip sign bit
                masm.mov(rscratch2, Double.doubleToLongBits(0x1.0p52d));
                masm.cmp(64, rscratch1, rscratch2);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.HS, labelDONE);
                // src < 0 && abs(src) < 0x1.0p52
                // src may have a fractional part, so add 0.5
                masm.fmov(64, ftmp, 0.5);
                masm.fadd(64, ftmp, src, ftmp);
                // Convert double to jlong, use RoundTowardsNegative
                masm.fcvtms(64, 64, dst, ftmp);
            }
        }

        masm.bind(labelDONE);
    }
}
