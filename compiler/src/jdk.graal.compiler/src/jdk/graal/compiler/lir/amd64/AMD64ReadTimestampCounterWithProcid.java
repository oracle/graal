/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * AMD64 rdtscp operation. The timestamp result is in EDX:EAX, the processor ID is left in ECX. Note
 * that the processor ID is the contents of IA32_TSC_AUX, so it's up to the OS to set it. Linux
 * apparently puts 12-bits for the chip ID, followed by 12-bits for the CPU ID.
 */
@Opcode("RDTSCP")
public class AMD64ReadTimestampCounterWithProcid extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ReadTimestampCounterWithProcid> TYPE = LIRInstructionClass.create(AMD64ReadTimestampCounterWithProcid.class);

    @Def({OperandFlag.REG}) protected AllocatableValue highResult;
    @Def({OperandFlag.REG}) protected AllocatableValue lowResult;
    @Def({OperandFlag.REG}) protected AllocatableValue procidResult;

    public AMD64ReadTimestampCounterWithProcid() {
        super(TYPE);

        this.highResult = AMD64.rdx.asValue(LIRKind.value(AMD64Kind.DWORD));
        this.lowResult = AMD64.rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        this.procidResult = AMD64.rcx.asValue(LIRKind.value(AMD64Kind.DWORD));
    }

    public AllocatableValue getHighResult() {
        return highResult;
    }

    public AllocatableValue getLowResult() {
        return lowResult;
    }

    public AllocatableValue getProcidResult() {
        return procidResult;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        masm.rdtscp();
    }
}
