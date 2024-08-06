/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * AMD64 shift/rotate operation. This operation has a single operand for the first input and output.
 * The second input must be in the RCX register.
 */
public class AMD64ShiftOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ShiftOp> TYPE = LIRInstructionClass.create(AMD64ShiftOp.class);

    @Opcode private final AMD64MOp opcode;
    private final OperandSize size;

    @Def({OperandFlag.REG, OperandFlag.HINT}) protected AllocatableValue result;
    @Use({OperandFlag.REG, OperandFlag.STACK}) protected AllocatableValue x;
    @Alive({OperandFlag.REG}) protected AllocatableValue y;

    public AMD64ShiftOp(AMD64MOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
        super(TYPE);
        this.opcode = opcode;
        this.size = size;

        this.result = result;
        this.x = x;
        this.y = y;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Move.move(crb, masm, result, x);
        opcode.emit(masm, size, asRegister(result));
    }

    @Override
    public void verify() {
        assert asRegister(y).equals(AMD64.rcx);
    }
}
