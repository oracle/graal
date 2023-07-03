/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.amd64;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

@Opcode("AMD64_MASK_ADDRESS")
public final class AMD64MaskAddressOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MaskAddressOp> TYPE = LIRInstructionClass.create(AMD64MaskAddressOp.class);

    @Alive({REG}) protected Value base; // todo illegal if missing, check if possible
    @Alive({REG}) protected Value index; // todo check if it should be alive or @Use
    @Def({REG}) protected Value result;
    @Temp({REG}) protected Value indexTmp;
    private int displacement;
    private long mask;
    private int shift;



    public AMD64MaskAddressOp(Value base, Value index, Value indexTmp, int displacement, int shift, long mask, AllocatableValue result) {
        super(TYPE);
        this.base = base;
        this.displacement = displacement;
        this.index = index;
        this.mask = mask;
        this.shift = shift;
        this.result = result;
        this.indexTmp = indexTmp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {

        masm.leaq(asRegister(indexTmp), new AMD64Address(Register.None, asRegister(index), Stride.fromLog2(shift), displacement));
        masm.movq(asRegister(result), mask);
        masm.andq(asRegister(result), asRegister(indexTmp));
    }


}
