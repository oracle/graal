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
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.DIV;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.IDIV;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.IMUL;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp.MUL;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AMD64 mul/div operation. This operation has a single operand for the second input. The first
 * input must be in RAX for mul and in RDX:RAX for div. The result is in RDX:RAX.
 */
public class AMD64MulDivOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64MulDivOp> TYPE = LIRInstructionClass.create(AMD64MulDivOp.class);

    @Opcode private final AMD64MOp opcode;
    private final OperandSize size;

    @LIRInstruction.Def({LIRInstruction.OperandFlag.REG}) protected AllocatableValue highResult;
    @LIRInstruction.Def({LIRInstruction.OperandFlag.REG}) protected AllocatableValue lowResult;

    @LIRInstruction.Use({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.ILLEGAL}) protected AllocatableValue highX;
    @LIRInstruction.Use({LIRInstruction.OperandFlag.REG}) protected AllocatableValue lowX;

    // Even though an idiv instruction can use a memory address for its
    // second operand, HotSpot on Windows does not recognize this form of
    // instruction when handling the special case of MININT/-1:
    // https://github.com/openjdk/jdk/blob/4b0e656bb6a823f50507039df7855183ab98cd83/src/hotspot/os/windows/os_windows.cpp#L2397
    // As such, force the operand to be in a register.
    @LIRInstruction.Use({LIRInstruction.OperandFlag.REG}) protected AllocatableValue y;

    @LIRInstruction.State protected LIRFrameState state;

    public AMD64MulDivOp(AMD64MOp opcode, OperandSize size, LIRKind resultKind, AllocatableValue x, AllocatableValue y) {
        this(opcode, size, resultKind, Value.ILLEGAL, x, y, null);
    }

    public AMD64MulDivOp(AMD64MOp opcode, OperandSize size, LIRKind resultKind, AllocatableValue highX, AllocatableValue lowX, AllocatableValue y, LIRFrameState state) {
        super(TYPE);
        this.opcode = opcode;
        this.size = size;

        this.highResult = AMD64.rdx.asValue(resultKind);
        this.lowResult = AMD64.rax.asValue(resultKind);

        this.highX = highX;
        this.lowX = lowX;

        this.y = y;

        this.state = state;
    }

    public AllocatableValue getHighResult() {
        return highResult;
    }

    public AllocatableValue getQuotient() {
        return lowResult;
    }

    public AllocatableValue getRemainder() {
        return highResult;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (state != null) {
            crb.recordImplicitException(masm.position(), state);
        }
        if (isRegister(y)) {
            opcode.emit(masm, size, asRegister(y));
        } else {
            assert isStackSlot(y);
            opcode.emit(masm, size, (AMD64Address) crb.asAddress(y));
        }
    }

    @Override
    public void verify() {
        assert asRegister(highResult).equals(AMD64.rdx);
        assert asRegister(lowResult).equals(AMD64.rax);

        assert asRegister(lowX).equals(AMD64.rax);
        if (opcode == DIV || opcode == IDIV) {
            assert asRegister(highX).equals(AMD64.rdx);
        } else if (opcode == MUL || opcode == IMUL) {
            assert isIllegal(highX);
        }
    }
}
