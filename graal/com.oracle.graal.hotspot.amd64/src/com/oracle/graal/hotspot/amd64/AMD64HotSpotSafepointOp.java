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
import static com.oracle.graal.asm.NumUtil.*;
import static com.oracle.graal.hotspot.bridge.Marks.*;
import static com.oracle.graal.phases.GraalOptions.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public class AMD64HotSpotSafepointOp extends AMD64LIRInstruction {

    @State protected LIRFrameState state;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private AllocatableValue temp;

    private final HotSpotVMConfig config;

    public AMD64HotSpotSafepointOp(LIRFrameState state, HotSpotVMConfig config, LIRGeneratorTool tool) {
        this.state = state;
        this.config = config;
        if (isPollingPageFar(config) || ImmutableCode.getValue()) {
            temp = tool.newVariable(tool.target().wordKind);
        } else {
            // Don't waste a register if it's unneeded
            temp = Value.ILLEGAL;
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        emitCode(crb, asm, config, false, state, temp instanceof RegisterValue ? ((RegisterValue) temp).getRegister() : null);
    }

    /**
     * Tests if the polling page address can be reached from the code cache with 32-bit
     * displacements.
     */
    private static boolean isPollingPageFar(HotSpotVMConfig config) {
        final long pollingPageAddress = config.safepointPollingAddress;
        return config.forceUnreachable || !isInt(pollingPageAddress - config.codeCacheLowBoundary()) || !isInt(pollingPageAddress - config.codeCacheHighBoundary());
    }

    public static void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm, HotSpotVMConfig config, boolean atReturn, LIRFrameState state, Register scratch) {
        assert !atReturn || state == null : "state is unneeded at return";
        if (ImmutableCode.getValue()) {
            Kind hostWordKind = HotSpotGraalRuntime.getHostWordKind();
            int alignment = hostWordKind.getBitCount() / Byte.SIZE;
            Constant pollingPageAddress = Constant.forIntegerKind(hostWordKind, config.safepointPollingAddress, null);
            // This move will be patched to load the safepoint page from a data segment
            // co-located with the immutable code.
            asm.movq(scratch, (AMD64Address) crb.recordDataReferenceInCode(pollingPageAddress, alignment));
            final int pos = asm.position();
            crb.recordMark(atReturn ? MARK_POLL_RETURN_FAR : MARK_POLL_FAR);
            if (state != null) {
                crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            }
            asm.testl(rax, new AMD64Address(scratch));
        } else if (isPollingPageFar(config)) {
            asm.movq(scratch, config.safepointPollingAddress);
            crb.recordMark(atReturn ? MARK_POLL_RETURN_FAR : MARK_POLL_FAR);
            final int pos = asm.position();
            if (state != null) {
                crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            }
            asm.testl(rax, new AMD64Address(scratch));
        } else {
            crb.recordMark(atReturn ? MARK_POLL_RETURN_NEAR : MARK_POLL_NEAR);
            final int pos = asm.position();
            if (state != null) {
                crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
            }
            // The C++ code transforms the polling page offset into an RIP displacement
            // to the real address at that offset in the polling page.
            asm.testl(rax, new AMD64Address(rip, 0));
        }
    }
}
