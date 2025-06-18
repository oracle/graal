/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64.vector;

import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.B0;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig.Z1;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMROp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * This class implements the LIR nodes for AVX512 instructions {@code vpcompress} and
 * {@code vpexpand}.
 */
public class AVX512CompressExpand {
    /**
     * The LIR node for the instruction {@code vpcompress}.
     */
    public static final class CompressOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CompressOp> TYPE = LIRInstructionClass.create(CompressOp.class);

        @Def protected AllocatableValue result;
        @Use protected AllocatableValue source;
        @Use protected AllocatableValue mask;

        public CompressOp(AllocatableValue result, AllocatableValue source, AllocatableValue mask) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.mask = mask;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind eKind = ((AMD64Kind) result.getPlatformKind()).getScalar();
            AVXKind.AVXSize avxSize = AVXKind.getRegisterSize(result);
            VexMROp op = switch (eKind) {
                case BYTE -> VexMROp.EVPCOMPRESSB;
                case WORD -> VexMROp.EVPCOMPRESSW;
                case DWORD, SINGLE -> VexMROp.EVPCOMPRESSD;
                case QWORD, DOUBLE -> VexMROp.EVPCOMPRESSQ;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(eKind);
            };
            op.emit(masm, avxSize, asRegister(result), asRegister(source), asRegister(mask), Z1, B0);
        }
    }

    /**
     * The LIR node for the instruction {@code vpexpand}.
     */
    public static final class ExpandOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ExpandOp> TYPE = LIRInstructionClass.create(ExpandOp.class);

        @Def protected AllocatableValue result;
        @Use protected AllocatableValue source;
        @Use protected AllocatableValue mask;

        public ExpandOp(AllocatableValue result, AllocatableValue source, AllocatableValue mask) {
            super(TYPE);
            this.result = result;
            this.source = source;
            this.mask = mask;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Kind eKind = ((AMD64Kind) result.getPlatformKind()).getScalar();
            AVXKind.AVXSize avxSize = AVXKind.getRegisterSize(result);
            VexRMOp op = switch (eKind) {
                case BYTE -> VexRMOp.EVPEXPANDB;
                case WORD -> VexRMOp.EVPEXPANDW;
                case DWORD, SINGLE -> VexRMOp.EVPEXPANDD;
                case QWORD, DOUBLE -> VexRMOp.EVPEXPANDQ;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(eKind);
            };
            op.emit(masm, avxSize, asRegister(result), asRegister(source), asRegister(mask), Z1, B0);
        }
    }
}
