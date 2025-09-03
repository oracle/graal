/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.code.DataSection.Data;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.graal.compiler.vector.nodes.simd.SimdConstant;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;

public final class AVXBinarySimdConstantVectorOp extends AMD64VectorInstruction {

    public static final LIRInstructionClass<AVXBinarySimdConstantVectorOp> TYPE = LIRInstructionClass.create(AVXBinarySimdConstantVectorOp.class);

    @Opcode private final VexRVMOp opcode;

    @Def({REG}) protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue x;
    protected ConstantValue y;

    public AVXBinarySimdConstantVectorOp(VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, ConstantValue y) {
        super(TYPE, size);
        assert y.getConstant() instanceof SimdConstant : y.getConstant();
        this.opcode = opcode;
        this.result = result;
        this.x = x;
        this.y = y;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
        int alignment = crb.dataBuilder.ensureValidDataAlignment(kind.getSizeInBytes());
        Data data = crb.dataBuilder.createMultiDataItem(((SimdConstant) y.getConstant()).getValues());
        crb.dataBuilder.updateAlignment(data, alignment);
        opcode.emit(masm, size, asRegister(result), asRegister(x), (AMD64Address) crb.recordDataSectionReference(data));
    }
}
