/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.amd64.vector;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPD;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPS;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public class AMD64VectorClearOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64VectorClearOp> TYPE = LIRInstructionClass.create(AMD64VectorClearOp.class);

    protected @LIRInstruction.Def({REG}) AllocatableValue result;

    public AMD64VectorClearOp(AllocatableValue result) {
        this(TYPE, result);
    }

    protected AMD64VectorClearOp(LIRInstructionClass<? extends AMD64VectorClearOp> c, AllocatableValue result) {
        super(c);
        this.result = result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
        Register register = asRegister(result);

        switch (kind.getScalar()) {
            case SINGLE:
                VXORPS.emit(masm, AVXKind.getRegisterSize(kind), register, register, register);
                break;

            case DOUBLE:
                VXORPD.emit(masm, AVXKind.getRegisterSize(kind), register, register, register);
                break;

            default:
                // on AVX1, YMM VPXOR is not supported - still it is possible to clear the whole
                // YMM/ZMM register as the upper 128/384-bit are implicitly cleared by the AVX1
                // instruction.
                VPXOR.emit(masm, XMM, register, register, register);
        }
    }
}
