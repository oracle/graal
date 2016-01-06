/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.aarch64;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.aarch64.AArch64LIRInstruction;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

@Opcode
public class AArch64HotSpotCRuntimeCallPrologueOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotCRuntimeCallPrologueOp> TYPE = LIRInstructionClass.create(AArch64HotSpotCRuntimeCallPrologueOp.class);

    private final int threadLastJavaSpOffset;
    private final Register thread;
    @Temp({REG}) protected AllocatableValue spScratch;

    public AArch64HotSpotCRuntimeCallPrologueOp(int threadLastJavaSpOffset, Register thread, AllocatableValue spScratch) {
        super(TYPE);
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.thread = thread;
        this.spScratch = spScratch;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        // save last Java frame
        // We cannot save the SP directly so use a temporary register.
        Register scratchRegister = asRegister(spScratch);
        masm.movx(scratchRegister, sp);
        masm.str(64, scratchRegister, masm.makeAddress(thread, threadLastJavaSpOffset, 8));
    }
}
