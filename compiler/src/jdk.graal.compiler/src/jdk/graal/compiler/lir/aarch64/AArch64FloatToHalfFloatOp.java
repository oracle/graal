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
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.DoubleWord;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.HalfWord;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/c2d76f9844aadf77a0b213a9169a7c5c8c8f1ffb/src/hotspot/cpu/aarch64/macroAssembler_aarch64.hpp#L566-L569",
          sha1 = "95116a9e350c16c09e5eed9db1328a39cb909474")
// @formatter:on
public class AArch64FloatToHalfFloatOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64FloatToHalfFloatOp> TYPE = LIRInstructionClass.create(AArch64FloatToHalfFloatOp.class);

    @LIRInstruction.Def({LIRInstruction.OperandFlag.REG}) protected Value dstValue;
    @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected Value srcValue;

    @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG}) protected Value tmpValue;

    public AArch64FloatToHalfFloatOp(LIRGeneratorTool tool, Value dstValue, Value srcValue) {
        super(TYPE);
        this.dstValue = dstValue;
        this.srcValue = srcValue;

        this.tmpValue = tool.newVariable(srcValue.getValueKind());
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Register tmp = asRegister(tmpValue);

        masm.fcvt(16, 32, tmp, src);
        masm.neon.smovGX(DoubleWord, HalfWord, dst, tmp, 0);
    }
}
