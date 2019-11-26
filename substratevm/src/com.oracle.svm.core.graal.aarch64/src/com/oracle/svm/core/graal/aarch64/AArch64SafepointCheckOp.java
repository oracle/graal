/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.aarch64;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.ThreadingSupportImpl;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;

/**
 * Compact instruction for {@link SafepointCheckNode}.
 */
public class AArch64SafepointCheckOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64SafepointCheckOp> TYPE = LIRInstructionClass.create(AArch64SafepointCheckOp.class);

    protected AArch64SafepointCheckOp() {
        super(TYPE);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        assert SubstrateOptions.MultiThreaded.getValue();
        SubstrateRegisterConfig threadRegister = (SubstrateRegisterConfig) crb.codeCache.getRegisterConfig();
        AArch64Address safepointAddress = AArch64Address.createUnscaledImmediateAddress(threadRegister.getThreadRegister(), Math.toIntExact(Safepoint.getThreadLocalSafepointRequestedOffset()));
        int safepointSize = AArch64Kind.QWORD.getSizeInBytes() * 8;
        try (ScratchRegister scratchRegister = masm.getScratchRegister()) {
            Register scratch = scratchRegister.getRegister();
            masm.ldr(safepointSize, scratch, safepointAddress);
            masm.subs(safepointSize, scratch, scratch, 1);
            if (ThreadingSupportImpl.isRecurringCallbackSupported()) {
                masm.str(safepointSize, scratch, safepointAddress);
            }
        }
    }

    public AArch64Assembler.ConditionFlag getConditionFlag() {
        return AArch64Assembler.ConditionFlag.LE;
    }
}
