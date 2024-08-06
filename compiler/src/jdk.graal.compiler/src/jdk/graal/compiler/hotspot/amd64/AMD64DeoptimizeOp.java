/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.graal.compiler.hotspot.HotSpotHostBackend.DEOPT_BLOB_UNCOMMON_TRAP;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.StandardOp.BlockEndOp;
import jdk.graal.compiler.lir.amd64.AMD64BlockEndOp;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

@Opcode("DEOPT")
final class AMD64DeoptimizeOp extends AMD64BlockEndOp implements BlockEndOp {
    public static final LIRInstructionClass<AMD64DeoptimizeOp> TYPE = LIRInstructionClass.create(AMD64DeoptimizeOp.class);

    @State private LIRFrameState info;

    AMD64DeoptimizeOp(LIRFrameState info) {
        super(TYPE);
        this.info = info;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Call.directCall(crb, masm, crb.getForeignCalls().lookupForeignCall(DEOPT_BLOB_UNCOMMON_TRAP), null, false, info);
    }
}
