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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMIOp;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AMD64MathCopySignOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MathCopySignOp> TYPE = LIRInstructionClass.create(AMD64MathCopySignOp.class);

    @Def({REG, HINT}) protected Value result;

    @Use({REG}) protected Value magnitude;
    @Alive({REG}) protected Value sign;

    @Temp({REG}) protected Value scratchGP;
    @Temp({REG}) protected Value scratchXMM;

    public AMD64MathCopySignOp(LIRGeneratorTool tool, Value result, AllocatableValue magnitude, AllocatableValue sign) {
        super(TYPE);
        this.result = result;
        this.magnitude = magnitude;
        this.sign = sign;
        this.scratchGP = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        this.scratchXMM = tool.newVariable(LIRKind.value(sign.getPlatformKind()));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        PlatformKind kind = result.getPlatformKind();

        Register resultReg = asRegister(result);
        Register signReg = asRegister(sign);

        Register scratchGPReg = asRegister(scratchGP);
        Register scratchXMMReg = asRegister(scratchXMM);

        AMD64Move.move(crb, masm, result, magnitude);

        if (kind == AMD64Kind.SINGLE) {
            masm.movl(scratchGPReg, 0x7FFFFFFF);
            masm.movdl(scratchXMMReg, scratchGPReg);
            VexRVMIOp.VPTERNLOGD.emit(masm, AVXSize.XMM, resultReg, signReg, scratchXMMReg, 0xE4);
        } else if (kind == AMD64Kind.DOUBLE) {
            masm.movq(scratchGPReg, 0x7FFFFFFFFFFFFFFFL);
            masm.movdq(scratchXMMReg, scratchGPReg);
            VexRVMIOp.VPTERNLOGQ.emit(masm, AVXSize.XMM, resultReg, signReg, scratchXMMReg, 0xE4);
        } else {
            throw GraalError.shouldNotReachHere("unsupported kind for Math.copySign");
        }
    }
}
