/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.guest.staging.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodRefSupport;
import com.oracle.svm.core.meta.SubstrateMethodOffsetConstant;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

public final class AArch64LoadMethodRefConstantOp extends AArch64LIRInstruction implements StandardOp.LoadConstantOp {
    public static final LIRInstructionClass<AArch64LoadMethodRefConstantOp> TYPE = LIRInstructionClass.create(AArch64LoadMethodRefConstantOp.class);
    private final Constant constant;
    @Def({REG, HINT}) private AllocatableValue result;

    AArch64LoadMethodRefConstantOp(AllocatableValue result, SubstrateMethodPointerConstant constant) {
        this(result, (Constant) constant);
    }

    AArch64LoadMethodRefConstantOp(AllocatableValue result, SubstrateMethodOffsetConstant constant) {
        this(result, (Constant) constant);
    }

    private AArch64LoadMethodRefConstantOp(AllocatableValue result, Constant constant) {
        super(TYPE);
        this.constant = constant;
        this.result = result;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        if (!SubstrateUtil.HOSTED) {
            throw VMError.shouldNotReachHere("Method reference constants must not be emitted at runtime.");
        }
        Register resultReg = asRegister(result);
        crb.recordInlineDataInCode(asMethodPointerConstant(constant));
        masm.adrpAdd(resultReg);
        if (constant instanceof SubstrateMethodOffsetConstant) {
            masm.sub(64, resultReg, resultReg, ReservedRegisters.singleton().getCodeBaseRegister());
        }
    }

    private static SubstrateMethodPointerConstant asMethodPointerConstant(Constant constant) {
        if (constant instanceof SubstrateMethodOffsetConstant offsetConstant) {
            MethodOffset offset = offsetConstant.offset();
            return new SubstrateMethodPointerConstant(MethodRefSupport.createMethodPointer(MethodRefSupport.getMethod(offset), offset.permitsRewriteToPLT()));
        }
        return (SubstrateMethodPointerConstant) constant;
    }

    @Override
    public AllocatableValue getResult() {
        return result;
    }

    @Override
    public Constant getConstant() {
        return constant;
    }

    @Override
    public boolean canRematerializeToStack() {
        return false;
    }
}
