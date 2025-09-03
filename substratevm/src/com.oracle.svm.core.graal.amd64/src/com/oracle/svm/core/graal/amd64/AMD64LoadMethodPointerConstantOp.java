/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public final class AMD64LoadMethodPointerConstantOp extends AMD64LIRInstruction implements StandardOp.LoadConstantOp {
    public static final LIRInstructionClass<AMD64LoadMethodPointerConstantOp> TYPE = LIRInstructionClass.create(AMD64LoadMethodPointerConstantOp.class);
    private final SubstrateMethodPointerConstant constant;
    @Def({REG, HINT}) private AllocatableValue result;

    AMD64LoadMethodPointerConstantOp(AllocatableValue result, SubstrateMethodPointerConstant constant) {
        super(TYPE);
        this.constant = constant;
        this.result = result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register resultReg = asRegister(result);
        crb.recordInlineDataInCode(constant);
        masm.leaq(resultReg, masm.getPlaceholder(masm.position()));
    }

    @Override
    public AllocatableValue getResult() {
        return result;
    }

    @Override
    public SubstrateMethodPointerConstant getConstant() {
        return constant;
    }

    @Override
    public boolean canRematerializeToStack() {
        return false;
    }
}
