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
package jdk.graal.compiler.lir.amd64.vector;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KXORB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KXORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KXORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KXORW;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public class AMD64VectorClearOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64VectorClearOp> TYPE = LIRInstructionClass.create(AMD64VectorClearOp.class);

    protected @Def({OperandFlag.REG}) AllocatableValue result;

    private final AMD64SIMDInstructionEncoding encoding;

    public AMD64VectorClearOp(AllocatableValue result, AMD64SIMDInstructionEncoding encoding) {
        this(TYPE, result, encoding);
    }

    protected AMD64VectorClearOp(LIRInstructionClass<? extends AMD64VectorClearOp> c, AllocatableValue result, AMD64SIMDInstructionEncoding encoding) {
        super(c);
        this.result = result;
        this.encoding = encoding;
    }

    public static AMD64VectorClearOp clearMask(AllocatableValue result) {
        assert ((AMD64Kind) result.getPlatformKind()).isMask() : "This method is only intended to be used to clear op mask values";
        // AVX512 op mask instructions are encoded using the VEX prefix
        return new AMD64VectorClearOp(result, AMD64SIMDInstructionEncoding.VEX);
    }

    private static final VexRVROp[] KXOR_OPS = new VexRVROp[]{KXORB, KXORW, KXORD, KXORQ};

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Kind kind = (AMD64Kind) result.getPlatformKind();
        Register register = asRegister(result);

        if (kind.isMask()) {
            KXOR_OPS[CodeUtil.log2(kind.getSizeInBytes())].emit(masm, register, register, register);
            return;
        }

        switch (kind.getScalar()) {
            case SINGLE:
                VXORPS.encoding(encoding).emit(masm, AVXKind.getRegisterSize(kind), register, register, register);
                break;

            case DOUBLE:
                VXORPD.encoding(encoding).emit(masm, AVXKind.getRegisterSize(kind), register, register, register);
                break;

            default:
                // on AVX1, YMM VPXOR is not supported - still it is possible to clear the whole
                // YMM/ZMM register as the upper 128/384-bit are implicitly cleared by the AVX1
                // instruction.
                VPXOR.encoding(encoding).emit(masm, XMM, register, register, register);
        }
    }
}
