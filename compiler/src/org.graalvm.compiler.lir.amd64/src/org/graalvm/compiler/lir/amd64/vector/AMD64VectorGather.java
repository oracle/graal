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
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;

public class AMD64VectorGather {
    private abstract static class AbstractGatherOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<AbstractGatherOp> TYPE = LIRInstructionClass.create(AbstractGatherOp.class);

        protected final AVXKind.AVXSize size;
        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue base;
        // must be alive to avoid conflict with result
        @Alive({REG}) protected AllocatableValue index;

        AbstractGatherOp(LIRInstructionClass<? extends AbstractGatherOp> c, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue base, AllocatableValue index) {
            super(c);
            this.size = size;
            this.result = result;
            this.base = base;
            this.index = index;
        }

        public abstract AMD64Assembler.VexOp getOpcode();
    }

    public static class VexVectorGatherOp extends AbstractGatherOp {
        public static final LIRInstructionClass<VexVectorGatherOp> TYPE = LIRInstructionClass.create(VexVectorGatherOp.class);

        @Opcode private final AMD64Assembler.VexGatherOp opcode;
        // both used and killed, must be a fixed register
        @Use({REG}) protected AllocatableValue mask;
        @Temp({REG}) protected AllocatableValue maskTemp;

        public VexVectorGatherOp(AMD64Assembler.VexGatherOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue base, AllocatableValue index, AllocatableValue mask) {
            super(TYPE, size, result, base, index);
            this.opcode = opcode;
            this.mask = mask;
            this.maskTemp = mask;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Address address = new AMD64Address(asRegister(base), asRegister(index), Stride.S1);
            opcode.emit(masm, size, asRegister(result), address, asRegister(mask));
        }

        @Override
        public AMD64Assembler.VexGatherOp getOpcode() {
            return opcode;
        }
    }

    public static class EvexVectorGatherOp extends AbstractGatherOp implements AVX512Support {
        public static final LIRInstructionClass<EvexVectorGatherOp> TYPE = LIRInstructionClass.create(EvexVectorGatherOp.class);

        @Opcode private final AMD64Assembler.EvexGatherOp opcode;
        // both used and killed, must be a fixed register
        @Use({REG}) protected AllocatableValue mask;
        @Temp({REG}) protected AllocatableValue maskTemp;

        public EvexVectorGatherOp(AMD64Assembler.EvexGatherOp opcode, AVXKind.AVXSize size, AllocatableValue result, AllocatableValue base, AllocatableValue index, AllocatableValue mask) {
            super(TYPE, size, result, base, index);
            this.opcode = opcode;
            this.mask = mask;
            this.maskTemp = mask;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Address address = new AMD64Address(asRegister(base), asRegister(index), Stride.S1);
            opcode.emit(masm, size, asRegister(result), address, asRegister(mask), EVEXPrefixConfig.Z0, EVEXPrefixConfig.B0);
        }

        @Override
        public AMD64Assembler.EvexGatherOp getOpcode() {
            return opcode;
        }

        @Override
        public AllocatableValue getOpmask() {
            return mask;
        }
    }
}
