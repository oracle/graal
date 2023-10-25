/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.aarch64;

import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Writes well known garbage values to registers.
 */
@Opcode("ZAP_REGISTER")
public final class AArch64ZapRegistersOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ZapRegistersOp> TYPE = LIRInstructionClass.create(AArch64ZapRegistersOp.class);

    /**
     * The registers that are zapped.
     */
    protected final Register[] zappedRegisters;

    /**
     * The garbage values that are written to the registers.
     */
    protected final JavaConstant[] zapValues;

    public AArch64ZapRegistersOp(Register[] zappedRegisters, JavaConstant[] zapValues) {
        super(TYPE);
        this.zappedRegisters = zappedRegisters;
        this.zapValues = zapValues;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        for (int i = 0; i < zappedRegisters.length; i++) {
            Register reg = zappedRegisters[i];
            if (reg != null) {
                AArch64Kind moveKind = (AArch64Kind) crb.target.arch.getPlatformKind(zapValues[i].getJavaKind());
                AArch64Move.const2reg(moveKind, crb, masm, reg, zapValues[i]);
            }
        }
    }
}
