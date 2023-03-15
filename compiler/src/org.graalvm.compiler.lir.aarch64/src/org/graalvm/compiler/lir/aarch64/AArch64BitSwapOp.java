/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/aarch64/aarch64.ad",
          lineStart = 14061,
          lineEnd   = 14085,
          commit    = "afda8fbf0bcea18cbe741e9c693789ebe0c6c4c5",
          sha1      = "dbb9206a315ed8fe2eae0287681c776a668c1833")
// @formatter:on
public class AArch64BitSwapOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64BitSwapOp> TYPE = LIRInstructionClass.create(AArch64BitSwapOp.class);

    @Def({REG}) protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue input;

    public AArch64BitSwapOp(AllocatableValue result, AllocatableValue input) {
        super(TYPE);
        AArch64Kind kind = (AArch64Kind) input.getPlatformKind();
        assert kind == AArch64Kind.DWORD || kind == AArch64Kind.QWORD;

        this.result = result;
        this.input = input;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        int size = input.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        masm.rbit(size, asRegister(result), asRegister(input));
    }
}
