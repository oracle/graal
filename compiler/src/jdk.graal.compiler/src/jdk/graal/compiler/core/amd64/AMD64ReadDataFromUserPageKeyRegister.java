/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.amd64;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

/**
 * Reads the value of PKRU into EAX and clears EDX. ECX must be 0 when RDPKRU is executed;
 * otherwise, a general-protection exception (#GP) occurs. RDPKRU can be executed only if CR4.PKE =
 * 1; otherwise, an invalid-opcode exception (#UD) occurs. Software can discover the value of
 * CR4.PKE by examining CPUID.(EAX=07H,ECX=0H):ECX.OSPKE [bit 4]. On processors that support the
 * Intel 64 Architecture, the high-order 32-bits of RCX are ignored and the high-order 32-bits of
 * RDX and RAX are cleared.
 */
@Opcode("RDPKRU")
public class AMD64ReadDataFromUserPageKeyRegister extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ReadDataFromUserPageKeyRegister> TYPE = LIRInstructionClass.create(AMD64ReadDataFromUserPageKeyRegister.class);

    // the result of the rdpkru is in eax
    @Def protected Value retVal;

    // edx will be cleared
    @Temp({REG}) protected AllocatableValue edx;

    // ecx must be zero
    @Temp({REG}) protected AllocatableValue zeroArg1;

    public AMD64ReadDataFromUserPageKeyRegister() {
        super(TYPE);
        this.retVal = AMD64.rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        this.edx = AMD64.rdx.asValue(LIRKind.value(AMD64Kind.DWORD));
        this.zeroArg1 = AMD64.rcx.asValue(LIRKind.value(AMD64Kind.DWORD));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        masm.xorl(ValueUtil.asRegister(zeroArg1), ValueUtil.asRegister(zeroArg1));
        masm.rdpkru();
    }
}
