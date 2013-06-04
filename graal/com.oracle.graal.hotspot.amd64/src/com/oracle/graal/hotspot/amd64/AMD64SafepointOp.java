/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.phases.GraalOptions.*;
import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public class AMD64SafepointOp extends AMD64LIRInstruction {

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    @State protected LIRFrameState state;
    @Temp({OperandFlag.REG}) private AllocatableValue temp;

    private final HotSpotVMConfig config;

    public AMD64SafepointOp(LIRFrameState state, HotSpotVMConfig config, LIRGeneratorTool tool) {
        this.state = state;
        this.config = config;
        temp = tool.newVariable(tool.target().wordKind);
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler asm) {
        int pos = asm.codeBuffer.position();
        int offset = SafepointPollOffset.getValue() % unsafe.pageSize();
        RegisterValue scratch = (RegisterValue) temp;
        if (config.isPollingPageFar) {
            asm.movq(scratch.getRegister(), config.safepointPollingAddress + offset);
            tasm.recordMark(Marks.MARK_POLL_FAR);
            tasm.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            asm.movq(scratch.getRegister(), new AMD64Address(scratch.getRegister()));
        } else {
            tasm.recordMark(Marks.MARK_POLL_NEAR);
            tasm.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            // The C++ code transforms the polling page offset into an RIP displacement
            // to the real address at that offset in the polling page.
            asm.movq(scratch.getRegister(), new AMD64Address(rip, offset));
        }
    }
}
