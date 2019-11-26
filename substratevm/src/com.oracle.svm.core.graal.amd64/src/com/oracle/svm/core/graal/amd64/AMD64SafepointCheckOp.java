/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.ThreadingSupportImpl;

/**
 * Compact instruction for {@link SafepointCheckNode}.
 */
@Opcode
public class AMD64SafepointCheckOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64SafepointCheckOp> TYPE = LIRInstructionClass.create(AMD64SafepointCheckOp.class);

    protected AMD64SafepointCheckOp() {
        super(TYPE);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        assert SubstrateOptions.MultiThreaded.getValue();
        SubstrateRegisterConfig threadRegister = (SubstrateRegisterConfig) crb.codeCache.getRegisterConfig();
        int safepointRequestedOffset = Math.toIntExact(Safepoint.getThreadLocalSafepointRequestedOffset());
        AMD64Address safepointRequested = new AMD64Address(threadRegister.getThreadRegister(), safepointRequestedOffset);
        if (ThreadingSupportImpl.isRecurringCallbackSupported()) {
            masm.subl(safepointRequested, 1);
        } else {
            // Ensuring safepointRequested offset fits a byte would make for a smaller instruction
            masm.cmpl(safepointRequested, 0);
        }
    }

    public AMD64Assembler.ConditionFlag getConditionFlag() {
        return AMD64Assembler.ConditionFlag.LessEqual;
    }
}
