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
package jdk.graal.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AMD64MathSignumOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MathSignumOp> TYPE = LIRInstructionClass.create(AMD64MathSignumOp.class);

    @Def({OperandFlag.REG, OperandFlag.HINT}) protected Value result;
    @Use({OperandFlag.REG}) protected Value input;
    @Temp({OperandFlag.REG}) protected Value scratch;

    public AMD64MathSignumOp(LIRGeneratorTool tool, Value result, AllocatableValue input) {
        super(TYPE);
        this.result = result;
        this.input = input;
        this.scratch = tool.newVariable(LIRKind.value(input.getPlatformKind()));
    }

    private static ArrayDataPointerConstant floatOne = pointerConstant(8, new int[]{
            // @formatter:off
            0x3F800000, 0x00000000
            // @formatter:on
    });

    private static ArrayDataPointerConstant floatSignMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x80000000, 0x80000000
            // @formatter:on
    });

    private static ArrayDataPointerConstant doubleOne = pointerConstant(8, new int[]{
            // @formatter:off
            0x00000000, 0x3FF00000
            // @formatter:on
    });

    private static ArrayDataPointerConstant doubleSignMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000000, 0x80000000
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        PlatformKind kind = input.getPlatformKind();
        Register scratchReg = asRegister(scratch);
        Register resultReg = asRegister(result);
        Label done = new Label();

        AMD64Move.move(crb, masm, result, input);

        if (kind == AMD64Kind.SINGLE) {
            masm.xorps(scratchReg, scratchReg);
            masm.ucomiss(resultReg, scratchReg);
            masm.jcc(AMD64Assembler.ConditionFlag.Equal, done);
            masm.jcc(AMD64Assembler.ConditionFlag.Parity, done);
            masm.movflt(resultReg, recordExternalAddress(crb, floatOne));
            masm.jcc(AMD64Assembler.ConditionFlag.Above, done);
            masm.xorps(resultReg, recordExternalAddress(crb, floatSignMask));
            masm.bind(done);
        } else if (kind == AMD64Kind.DOUBLE) {
            masm.xorpd(scratchReg, scratchReg);
            masm.ucomisd(resultReg, scratchReg);
            masm.jcc(AMD64Assembler.ConditionFlag.Equal, done);
            masm.jcc(AMD64Assembler.ConditionFlag.Parity, done);
            masm.movdbl(resultReg, recordExternalAddress(crb, doubleOne));
            masm.jcc(AMD64Assembler.ConditionFlag.Above, done);
            masm.xorpd(resultReg, recordExternalAddress(crb, doubleSignMask));
            masm.bind(done);
        } else {
            throw GraalError.shouldNotReachHere("unsupported kind for Math.signum"); // ExcludeFromJacocoGeneratedReport
        }
    }
}
