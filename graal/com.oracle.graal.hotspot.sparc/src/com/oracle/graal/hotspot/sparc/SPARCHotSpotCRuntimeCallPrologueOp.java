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
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Add;
import com.oracle.graal.asm.sparc.SPARCAssembler.Stx;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

@Opcode("CRUNTIME_CALL_PROLOGUE")
final class SPARCHotSpotCRuntimeCallPrologueOp extends SPARCLIRInstruction {

    private final int threadLastJavaSpOffset;
    private final Register thread;
    private final Register stackPointer;
    @Def({REG, STACK}) protected Value threadTemp;

    public SPARCHotSpotCRuntimeCallPrologueOp(int threadLastJavaSpOffset, Register thread, Register stackPointer, Value threadTemp) {
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.thread = thread;
        this.stackPointer = stackPointer;
        this.threadTemp = threadTemp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // Save last Java frame.
        new Add(stackPointer, STACK_BIAS, g4).emit(masm);
        new Stx(g4, new SPARCAddress(thread, threadLastJavaSpOffset)).emit(masm);

        // Save the thread register when calling out to the runtime.
        SPARCMove.move(crb, masm, threadTemp, thread.asValue(LIRKind.value(Kind.Long)));
    }
}
