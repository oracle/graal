/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

public final class AArch64LoadMethodPointerConstantOp extends AArch64LIRInstruction implements StandardOp.LoadConstantOp {
    public static final LIRInstructionClass<AArch64LoadMethodPointerConstantOp> TYPE = LIRInstructionClass.create(AArch64LoadMethodPointerConstantOp.class);
    private final SubstrateMethodPointerConstant constant;
    @Def({REG, HINT}) private AllocatableValue result;

    AArch64LoadMethodPointerConstantOp(AllocatableValue result, SubstrateMethodPointerConstant constant) {
        super(TYPE);
        this.constant = constant;
        this.result = result;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register resultReg = asRegister(result);
        crb.recordInlineDataInCode(constant);
        masm.adrpAdd(resultReg);
    }

    @Override
    public AllocatableValue getResult() {
        return result;
    }

    @Override
    public Constant getConstant() {
        return constant;
    }
}
