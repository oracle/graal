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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.asm.*;

/**
 * Pops the current frame off the stack including the return address and restores the return
 * registers stored on the stack.
 */
@Opcode("LEAVE_CURRENT_STACK_FRAME")
final class AMD64HotSpotLeaveCurrentStackFrameOp extends AMD64HotSpotEpilogueOp {

    private final SaveRegistersOp saveRegisterOp;

    public AMD64HotSpotLeaveCurrentStackFrameOp(SaveRegistersOp saveRegisterOp) {
        this.saveRegisterOp = saveRegisterOp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        FrameMap frameMap = crb.frameMap;
        RegisterConfig registerConfig = frameMap.registerConfig;
        RegisterSaveLayout registerSaveLayout = saveRegisterOp.getMap(frameMap);
        Register stackPointer = registerConfig.getFrameRegister();

        // Restore integer result register.
        final int stackSlotSize = frameMap.stackSlotSize();
        Register integerResultRegister = registerConfig.getReturnRegister(Kind.Long);
        masm.movptr(integerResultRegister, new AMD64Address(stackPointer, registerSaveLayout.registerToSlot(integerResultRegister) * stackSlotSize));
        masm.movptr(rdx, new AMD64Address(stackPointer, registerSaveLayout.registerToSlot(rdx) * stackSlotSize));

        // Restore float result register.
        Register floatResultRegister = registerConfig.getReturnRegister(Kind.Double);
        masm.movdbl(floatResultRegister, new AMD64Address(stackPointer, registerSaveLayout.registerToSlot(floatResultRegister) * stackSlotSize));

        /*
         * All of the register save area will be popped of the stack. Only the return address
         * remains.
         */
        leaveFrameAndRestoreRbp(crb, masm);

        // Remove return address.
        masm.addq(stackPointer, crb.target.arch.getReturnAddressSize());
    }
}
