/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

/**
 * Performs an unwind to throw an exception.
 */
@Opcode("CALL_INDIRECT")
final class AMD64HotSpotUnwindOp extends AMD64LIRInstruction {

    public static final Descriptor UNWIND_EXCEPTION = new Descriptor("unwindException", true, void.class, Object.class);

    /**
     * Vtable stubs expect the metaspace Method in RBX.
     */
    public static final Register METHOD = AMD64.rbx;

    @Use({REG}) protected AllocatableValue exception;
    @Temp private RegisterValue framePointer;

    AMD64HotSpotUnwindOp(AllocatableValue exception) {
        this.exception = exception;
        assert exception == AMD64.rax.asValue();
        framePointer = AMD64.rbp.asValue();
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        masm.movq(framePointer.getRegister(), rsp);
        masm.incrementq(framePointer.getRegister(), tasm.frameMap.frameSize() - 8);
        AMD64Call.directCall(tasm, masm, tasm.runtime.lookupRuntimeCall(UNWIND_EXCEPTION), null);
    }
}
