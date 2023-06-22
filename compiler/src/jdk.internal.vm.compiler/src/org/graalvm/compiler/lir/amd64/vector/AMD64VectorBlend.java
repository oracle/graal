/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;

public class AMD64VectorBlend {
    private abstract static class AbstractBlendOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AbstractBlendOp> TYPE = LIRInstructionClass.create(AbstractBlendOp.class);

        protected final AVXKind.AVXSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;
        @Use({REG}) protected AllocatableValue mask;

        AbstractBlendOp(LIRInstructionClass<? extends AbstractBlendOp> c, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y, AllocatableValue mask) {
            super(c);
            this.size = size;
            this.result = result;
            this.x = x;
            this.y = y;
            this.mask = mask;
        }
    }

    public static class VexBlendOp extends AbstractBlendOp {
        public static final LIRInstructionClass<VexBlendOp> TYPE = LIRInstructionClass.create(VexBlendOp.class);

        @Opcode private final AMD64Assembler.VexRVMROp opcode;

        public VexBlendOp(AMD64Assembler.VexRVMROp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y, AllocatableValue mask) {
            super(TYPE, size, result, x, y, mask);
            this.opcode = opcode;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                opcode.emit(masm, size, asRegister(result), asRegister(mask), asRegister(x), asRegister(y));
            } else {
                opcode.emit(masm, size, asRegister(result), asRegister(mask), asRegister(x), (AMD64Address) crb.asAddress(y));
            }
        }
    }

    public static class EvexBlendOp extends AbstractBlendOp implements AVX512Support {
        public static final LIRInstructionClass<EvexBlendOp> TYPE = LIRInstructionClass.create(EvexBlendOp.class);

        @Opcode private final AMD64Assembler.VexRVMOp opcode;

        public EvexBlendOp(AMD64Assembler.VexRVMOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y, AllocatableValue mask) {
            super(TYPE, size, result, x, y, mask);
            this.opcode = opcode;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                opcode.emit(masm, size, asRegister(result), asRegister(x), asRegister(y), asRegister(mask));
            } else {
                opcode.emit(masm, size, asRegister(result), asRegister(x), (AMD64Address) crb.asAddress(y), asRegister(mask));
            }
        }

        @Override
        public AllocatableValue getOpmask() {
            return mask;
        }
    }
}
