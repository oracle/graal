/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.hotspot.HotSpotHostBackend;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.StandardOp.BlockEndOp;
import jdk.graal.compiler.lir.aarch64.AArch64BlockEndOp;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

@Opcode("DEOPT")
public class AArch64HotSpotDeoptimizeOp extends AArch64BlockEndOp implements BlockEndOp {
    public static final LIRInstructionClass<AArch64HotSpotDeoptimizeOp> TYPE = LIRInstructionClass.create(AArch64HotSpotDeoptimizeOp.class);

    @State private LIRFrameState info;

    public AArch64HotSpotDeoptimizeOp(LIRFrameState info) {
        super(TYPE);
        this.info = info;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (AArch64MacroAssembler.ScratchRegister scratch = masm.getScratchRegister()) {
            AArch64Call.directCall(crb, masm, crb.getForeignCalls().lookupForeignCall(HotSpotHostBackend.DEOPT_BLOB_UNCOMMON_TRAP), scratch.getRegister(), info, null);
        }
    }

}
