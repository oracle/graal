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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.CMP;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Returns -1, 0, or 1 if either x &lt; y, x == y, or x &gt; y.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/cfd9209e03176bd8e02acd74b51a16f3113fbd21/src/hotspot/cpu/x86/x86_64.ad#L11748-L11772",
          sha1 = "90dcf08952d34fa4381e43cbe988ce01a0fd2f26")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/cfd9209e03176bd8e02acd74b51a16f3113fbd21/src/hotspot/cpu/x86/x86_64.ad#L11800-L11824",
          sha1 = "541cc1716b2aa630e52634a3f1595159f274aa8f")
// @formatter:on
public class AMD64NormalizedUnsignedCompareOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64NormalizedUnsignedCompareOp> TYPE = LIRInstructionClass.create(AMD64NormalizedUnsignedCompareOp.class);

    @Def({OperandFlag.REG}) protected AllocatableValue result;
    @Use({OperandFlag.REG}) protected AllocatableValue x;
    @Use({OperandFlag.REG}) protected AllocatableValue y;

    private final LIRKind compareKind;

    public AMD64NormalizedUnsignedCompareOp(AllocatableValue result, LIRKind compareKind, AllocatableValue x, AllocatableValue y) {
        super(TYPE);

        this.result = result;
        this.x = x;
        this.y = y;
        this.compareKind = compareKind;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label done = new Label();
        AMD64BaseAssembler.OperandSize size = AMD64BaseAssembler.OperandSize.get(compareKind.getPlatformKind());
        CMP.getRMOpcode(size).emit(masm, size, asRegister(x), asRegister(y));
        masm.movl(asRegister(result), -1);
        masm.jccb(AMD64Assembler.ConditionFlag.Below, done);
        masm.setl(AMD64Assembler.ConditionFlag.NotEqual, asRegister(result));
        masm.bind(done);
    }
}
