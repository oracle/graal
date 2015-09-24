/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.Value;
import jdk.internal.jvmci.sparc.SPARC;
import jdk.internal.jvmci.sparc.SPARCKind;

import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.asm.sparc.SPARCAssembler.Opfs;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public final class SPARCOPFOp extends SPARCLIRInstruction implements SPARCTailDelayedLIRInstruction {
    public static final LIRInstructionClass<SPARCOPFOp> TYPE = LIRInstructionClass.create(SPARCOPFOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(1);

    @Opcode protected final Opfs opf;
    @Use({REG}) protected Value rs1;
    @Use({REG}) protected Value rs2;
    @Def({REG}) protected Value rd;
    @State protected LIRFrameState state;

    public SPARCOPFOp(Opfs opf, Value rs2, Value rd) {
        this(opf, SPARC.g0.asValue(LIRKind.value(SPARCKind.SINGLE)), rs2, rd);
    }

    public SPARCOPFOp(Opfs opf, Value rs1, Value rs2, Value rd) {
        this(opf, rs1, rs2, rd, null);
    }

    public SPARCOPFOp(Opfs opf, Value rs1, Value rs2, Value rd, LIRFrameState state) {
        super(TYPE, SIZE);
        this.opf = opf;
        this.rs1 = rs1;
        this.rs2 = rs2;
        this.rd = rd;
        this.state = state;
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        getDelayedControlTransfer().emitControlTransfer(crb, masm);
        if (state != null) {
            crb.recordImplicitException(masm.position(), state);
        }
        SPARCAssembler.OpfOp.emit(masm, opf, asRegister(rs1), asRegister(rs2), asRegister(rd));
    }
}
