/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorInstruction;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * AMD64 LIR instructions that have three inputs and one output.
 */
public class AMD64Ternary {

    /**
     * Instruction that has two {@link AllocatableValue} operands.
     */
    public static class ThreeOp extends AMD64VectorInstruction {
        public static final LIRInstructionClass<ThreeOp> TYPE = LIRInstructionClass.create(ThreeOp.class);

        @Opcode private final VexRVMOp opcode;

        @Def({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.HINT}) protected AllocatableValue result;
        @Use({LIRInstruction.OperandFlag.REG}) protected AllocatableValue x;
        /**
         * This argument must be Alive to ensure that result and y are not assigned to the same
         * register, which would break the code generation by destroying y too early.
         */
        @Alive({LIRInstruction.OperandFlag.REG}) protected AllocatableValue y;
        @Alive({LIRInstruction.OperandFlag.REG, LIRInstruction.OperandFlag.STACK}) protected AllocatableValue z;

        public ThreeOp(VexRVMOp opcode, AVXSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y, AllocatableValue z) {
            super(TYPE, size);
            this.opcode = opcode;
            this.result = result;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64SIMDInstructionEncoding simdEncoding = AMD64SIMDInstructionEncoding.forFeatures(masm.getFeatures());
            new AMD64VectorMove.MoveToRegOp(result, x, simdEncoding).emitCode(crb, masm);
            if (isRegister(z)) {
                opcode.emit(masm, size, asRegister(result), asRegister(y), asRegister(z));
            } else {
                assert isStackSlot(z);
                opcode.emit(masm, size, asRegister(result), asRegister(y), (AMD64Address) crb.asAddress(z));
            }
        }
    }
}
