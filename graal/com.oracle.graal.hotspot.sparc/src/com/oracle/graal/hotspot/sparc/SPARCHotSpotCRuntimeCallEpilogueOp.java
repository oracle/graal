/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Stw;
import com.oracle.graal.asm.sparc.SPARCAssembler.Stx;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

@Opcode("CRUNTIME_CALL_EPILOGUE")
final class SPARCHotSpotCRuntimeCallEpilogueOp extends SPARCLIRInstruction {

    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    private final int threadJavaFrameAnchorFlagsOffset;
    private final Register thread;
    @Use({REG, STACK}) protected Value threadTemp;

    public SPARCHotSpotCRuntimeCallEpilogueOp(int threadLastJavaSpOffset, int threadLastJavaPcOffset, int threadJavaFrameAnchorFlagsOffset, Register thread, Value threadTemp) {
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.threadJavaFrameAnchorFlagsOffset = threadJavaFrameAnchorFlagsOffset;
        this.thread = thread;
        this.threadTemp = threadTemp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {

        // Restore the thread register when coming back from the runtime.
        SPARCMove.move(crb, masm, thread.asValue(LIRKind.value(Kind.Long)), threadTemp);

        // Reset last Java frame, last Java PC and flags.
        new Stx(g0, new SPARCAddress(thread, threadLastJavaSpOffset)).emit(masm);
        new Stx(g0, new SPARCAddress(thread, threadLastJavaPcOffset)).emit(masm);
        new Stw(g0, new SPARCAddress(thread, threadJavaFrameAnchorFlagsOffset)).emit(masm);
    }
}
