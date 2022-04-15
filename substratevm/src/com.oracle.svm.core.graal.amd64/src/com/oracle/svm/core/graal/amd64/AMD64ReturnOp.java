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
package com.oracle.svm.core.graal.amd64;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.amd64.AMD64BlockEndOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.Value;

@Opcode("RETURN")
public final class AMD64ReturnOp extends AMD64BlockEndOp implements StandardOp.BlockEndOp {
    public static final LIRInstructionClass<AMD64ReturnOp> TYPE = LIRInstructionClass.create(AMD64ReturnOp.class);
    @Use({REG, ILLEGAL}) protected Value x;

    public AMD64ReturnOp(Value x) {
        super(TYPE);
        this.x = x;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        crb.frameContext.leave(crb);
        if (SubstrateAMD64Backend.runtimeToAOTIsAvxSseTransition(crb.target)) {
            /*
             * We potentially return to hosted code, and that's an AVX-SSE transition. The only live
             * value at this point should be the return value in either rax, or in xmm0 with the
             * upper half of the register unused, so we don't destroy any value here.
             */
            masm.vzeroupper();
        }
        masm.ret(0);
        crb.frameContext.returned(crb);
    }
}
