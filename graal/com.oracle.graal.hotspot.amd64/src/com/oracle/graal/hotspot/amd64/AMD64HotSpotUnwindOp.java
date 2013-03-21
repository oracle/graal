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
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

/**
 * Performs an unwind to throw an exception.
 */
@Opcode("UNWIND")
final class AMD64HotSpotUnwindOp extends AMD64HotSpotEpilogueOp {

    public static final Descriptor UNWIND_EXCEPTION = new Descriptor("unwindException", true, void.class, Object.class);

    /**
     * Unwind stub expects the exception in RAX.
     */
    public static final Register EXCEPTION = AMD64.rax;

    @Use({REG}) protected AllocatableValue exception;

    AMD64HotSpotUnwindOp(AllocatableValue exception) {
        this.exception = exception;
        assert asRegister(exception) == EXCEPTION;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        // Copy the saved RBP value into the slot just below the return address
        // so that the stub can pick it up from there.
        AMD64Address rbpSlot;
        int rbpSlotOffset = tasm.frameMap.frameSize() - 8;
        if (isStackSlot(savedRbp)) {
            rbpSlot = (AMD64Address) tasm.asAddress(savedRbp);
            assert rbpSlot.getDisplacement() == rbpSlotOffset;
        } else {
            rbpSlot = new AMD64Address(rsp, rbpSlotOffset);
            masm.movq(rbpSlot, asRegister(savedRbp));
        }

        // Pass the address of the RBP slot in RBP itself
        masm.leaq(rbp, rbpSlot);
        AMD64Call.directCall(tasm, masm, tasm.runtime.lookupRuntimeCall(UNWIND_EXCEPTION), AMD64.r10, false, null);
    }
}
