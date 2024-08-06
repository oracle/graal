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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Returns -1, 0, or 1 if either x &lt; y, x == y, or x &gt; y.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/aaaa86b57172d45d1126c50efc270c6e49aba7a5/src/hotspot/cpu/aarch64/aarch64.ad#L9105-L9187",
          sha1 = "84da421c1489e188366d61bb4298e0425ccac14b")
// @formatter:on
public class AArch64NormalizedUnsignedCompareOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64NormalizedUnsignedCompareOp> TYPE = LIRInstructionClass.create(AArch64NormalizedUnsignedCompareOp.class);

    @Def({REG}) protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue x;
    @Use({REG}) protected AllocatableValue y;

    public AArch64NormalizedUnsignedCompareOp(AllocatableValue result, AllocatableValue x, AllocatableValue y) {
        super(TYPE);

        this.result = result;
        this.x = x;
        this.y = y;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        if (x.getPlatformKind() == AArch64Kind.DWORD) {
            masm.cmp(32, asRegister(x), asRegister(y));
        } else {
            GraalError.guarantee(x.getPlatformKind() == AArch64Kind.QWORD, "unsupported value kind %s", x.getPlatformKind());
            masm.cmp(64, asRegister(x), asRegister(y));
        }
        masm.cset(32, asRegister(result), ConditionFlag.NE);
        masm.csneg(32, asRegister(result), asRegister(result), asRegister(result), ConditionFlag.HI);
    }
}
