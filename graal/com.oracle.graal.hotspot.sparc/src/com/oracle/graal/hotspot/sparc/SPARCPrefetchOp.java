/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.asm.sparc.SPARCAssembler.Prefetch;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

public class SPARCPrefetchOp extends SPARCLIRInstruction {

    private final int instr;  // AllocatePrefetchInstr
    @Alive({COMPOSITE}) protected SPARCAddressValue address;

    public SPARCPrefetchOp(SPARCAddressValue address, int instr) {
        this.address = address;
        this.instr = instr;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        assert instr == 0 : "only supported value is 0";
        new Prefetch(address.toAddress(), Prefetch.Fcn.SeveralWritesAndPossiblyReads).emit(masm);
    }
}
