/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.hsail;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Emits a safepoint deoptimization from HSA back to CPU.
 */
@Opcode("SAFEPOINT")
public class HSAILHotSpotSafepointOp extends HSAILLIRInstruction implements HSAILControlFlow.DeoptimizingOp {
    private Constant actionAndReason;
    @State protected LIRFrameState frameState;
    protected int codeBufferPos = -1;
    final int offsetToNoticeSafepoints;
    final HotSpotVMConfig config;

    public HSAILHotSpotSafepointOp(LIRFrameState state, HotSpotVMConfig config, NodeLIRBuilderTool tool) {
        actionAndReason = tool.getLIRGeneratorTool().getMetaAccess().encodeDeoptActionAndReason(DeoptimizationAction.None, DeoptimizationReason.None, 0);
        frameState = state;
        offsetToNoticeSafepoints = config.hsailNoticeSafepointsOffset;
        this.config = config;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
        if (config.useHSAILDeoptimization) {
            // get a unique codeBuffer position
            // when we save our state, we will save this as well (it can be used as a key to get the
            // debugInfo)
            codeBufferPos = masm.position();

            masm.emitComment(" /* HSAIL safepoint bci=" + frameState.debugInfo().getBytecodePosition().getBCI() + ", frameState=" + frameState + " */");
            String afterSafepointLabel = "@LAfterSafepoint_at_pos_" + codeBufferPos;

            AllocatableValue scratch64 = HSAIL.d16.asValue(Kind.Object);
            AllocatableValue spAddrReg = HSAIL.d17.asValue(Kind.Object);
            AllocatableValue scratch32 = HSAIL.s34.asValue(Kind.Int);
            masm.emitLoadKernelArg(scratch64, masm.getDeoptInfoName(), "u64");

            // Build address of noticeSafepoints field
            HSAILAddress noticeSafepointsAddr = new HSAILAddressValue(Kind.Object, scratch64, offsetToNoticeSafepoints).toAddress();
            masm.emitLoad(Kind.Object, spAddrReg, noticeSafepointsAddr);

            // Load int value from that field
            HSAILAddress noticeSafepointsIntAddr = new HSAILAddressValue(Kind.Int, spAddrReg, 0).toAddress();
            masm.emitLoadAcquire(scratch32, noticeSafepointsIntAddr);
            masm.emitCompare(Kind.Int, scratch32, Constant.forInt(0), "eq", false, false);
            masm.cbr(afterSafepointLabel);

            AllocatableValue actionAndReasonReg = HSAIL.actionAndReasonReg.asValue(Kind.Int);
            AllocatableValue codeBufferOffsetReg = HSAIL.codeBufferOffsetReg.asValue(Kind.Int);
            masm.emitMov(Kind.Int, actionAndReasonReg, actionAndReason);
            masm.emitMov(Kind.Int, codeBufferOffsetReg, Constant.forInt(codeBufferPos));
            masm.emitJumpToLabelName(masm.getDeoptLabelName());

            masm.emitString0(afterSafepointLabel + ":\n");

            // now record the debuginfo
            crb.recordInfopoint(codeBufferPos, frameState, InfopointReason.SAFEPOINT);
        } else {
            masm.emitComment("/* HSAIL safepoint would have been here. */");
        }
    }

    public LIRFrameState getFrameState() {
        return frameState;
    }

    public int getCodeBufferPos() {
        return codeBufferPos;
    }
}
