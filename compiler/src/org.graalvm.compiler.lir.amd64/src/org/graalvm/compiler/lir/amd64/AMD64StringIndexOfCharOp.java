/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
 */
@Opcode("AMD64_STRING_INDEX_OF_CHAR")
public final class AMD64StringIndexOfCharOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64StringIndexOfCharOp> TYPE = LIRInstructionClass.create(AMD64StringIndexOfCharOp.class);

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value charPtr1Value;
    @Use({REG}) protected RegisterValue cnt1Value;
    @Temp({REG}) protected RegisterValue cnt1ValueT;
    @Use({REG}) protected RegisterValue cnt2Value;
    @Temp({REG}) protected RegisterValue cnt2ValueT;
    @Temp({REG}) protected Value temp1;
    @Temp({REG, ILLEGAL}) protected Value vectorTemp1;

    private final int vmPageSize;

    public AMD64StringIndexOfCharOp(LIRGeneratorTool tool, Value result, Value charPtr1, RegisterValue cnt1, RegisterValue cnt2, RegisterValue temp1, RegisterValue vectorTemp1,
                    int vmPageSize) {
        super(TYPE);
        assert ((AMD64) tool.target().arch).getFeatures().contains(CPUFeature.SSE4_2);
        resultValue = result;
        charPtr1Value = charPtr1;
        /*
         * The count values are inputs but are also killed like temporaries so need both Use and
         * Temp annotations, which will only work with fixed registers.
         */
        cnt1Value = cnt1;
        cnt1ValueT = cnt1;
        cnt2Value = cnt2;
        cnt2ValueT = cnt2;
        assert asRegister(cnt1).equals(rdx) && asRegister(cnt2).equals(rax) && asRegister(temp1).equals(rcx) : "fixed register usage required";

        this.temp1 = temp1;
        this.vectorTemp1 = vectorTemp1;
        this.vmPageSize = vmPageSize;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register charPtr1 = asRegister(charPtr1Value);
        Register cnt1 = asRegister(cnt1Value);
        Register cnt2 = asRegister(cnt2Value);
        Register result = asRegister(resultValue);
        Register vec = asRegister(vectorTemp1);
        Register tmp = asRegister(temp1);
        masm.stringIndexOfChar(charPtr1, cnt1, cnt2, result, vec, tmp, vmPageSize);
    }
}
