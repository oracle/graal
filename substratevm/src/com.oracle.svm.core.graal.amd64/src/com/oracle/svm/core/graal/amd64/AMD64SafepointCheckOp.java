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

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.nodes.SafepointCheckNode;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.thread.SafepointCheckCounter;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

/**
 * Compact instruction for {@link SafepointCheckNode}.
 */
@Opcode
public class AMD64SafepointCheckOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64SafepointCheckOp> TYPE = LIRInstructionClass.create(AMD64SafepointCheckOp.class);

    public AMD64SafepointCheckOp() {
        super(TYPE);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        int counterOffset = SafepointCheckCounter.getThreadLocalOffset();
        AMD64Address counter = new AMD64Address(ReservedRegisters.singleton().getThreadRegister(), counterOffset);
        if (RecurringCallbackSupport.isEnabled()) {
            masm.subl(counter, 1);
        } else {
            masm.cmpl(counter, 0);
        }
    }

    public AMD64Assembler.ConditionFlag getConditionFlag() {
        return AMD64Assembler.ConditionFlag.LessEqual;
    }
}
