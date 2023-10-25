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
package jdk.graal.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/0a3a925ad88921d387aa851157f54ac0054d347b/src/hotspot/cpu/x86/macroAssembler_x86.hpp#L200-L206",
          sha1 = "f1f7051b93fb7037a3f7baf2cfc25681979ac6dc")
// @formatter:on
public final class AMD64FloatToHalfFloatOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64FloatToHalfFloatOp> TYPE = LIRInstructionClass.create(AMD64FloatToHalfFloatOp.class);

    @LIRInstruction.Def({LIRInstruction.OperandFlag.REG}) protected Value dstValue;
    @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) protected Value srcValue;

    @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG}) protected Value tmpValue;

    public AMD64FloatToHalfFloatOp(LIRGeneratorTool tool, Value dstValue, Value srcValue) {
        super(TYPE);
        this.dstValue = dstValue;
        this.srcValue = srcValue;

        this.tmpValue = tool.newVariable(srcValue.getValueKind());
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Register tmp = asRegister(tmpValue);

        masm.vcvtps2ph(tmp, src, 0b100);
        masm.movdl(dst, tmp);
        masm.movswl(dst, dst);
    }
}
