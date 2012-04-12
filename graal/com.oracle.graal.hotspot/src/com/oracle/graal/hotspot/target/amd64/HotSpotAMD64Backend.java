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
package com.oracle.graal.hotspot.target.amd64;

import static com.oracle.graal.hotspot.ri.HotSpotXirGenerator.*;
import static com.oracle.max.asm.target.amd64.AMD64.*;
import static com.oracle.max.cri.ci.CiCallingConvention.Type.*;
import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.lang.reflect.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.compiler.target.amd64.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.ri.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.*;

public class HotSpotAMD64Backend extends Backend {

    public HotSpotAMD64Backend(RiRuntime runtime, CiTarget target) {
        super(runtime, target);
    }

    @Override
    public LIRGenerator newLIRGenerator(Graph graph, FrameMap frameMap, RiResolvedMethod method, LIR lir, RiXirGenerator xir) {
        return new AMD64LIRGenerator(graph, runtime, target, frameMap, method, lir, xir);
    }

    @Override
    public AMD64XirAssembler newXirAssembler() {
        return new AMD64XirAssembler(target);
    }

    class HotSpotFrameContext implements FrameContext {

        private final LIR lir;
        private boolean needsLeave;

        public HotSpotFrameContext(LIR lir) {
            this.lir = lir;
        }

        @Override
        public void enter(TargetMethodAssembler tasm) {
            FrameMap frameMap = tasm.frameMap;
            int frameSize = frameMap.frameSize();
            if (frameSize != 0) {
                AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
                emitStackOverflowCheck(tasm, lir, false);
                asm.push(rbp);
                asm.movq(rbp, rsp);
                asm.decrementq(rsp, frameSize - 8); // account for the push of RBP above
                if (GraalOptions.ZapStackOnMethodEntry) {
                    final int intSize = 4;
                    for (int i = 0; i < frameSize / intSize; ++i) {
                        asm.movl(new CiAddress(CiKind.Int, rsp.asValue(), i * intSize), 0xC1C1C1C1);
                    }
                }
                CiCalleeSaveLayout csl = frameMap.registerConfig.getCalleeSaveLayout();
                if (csl != null && csl.size != 0) {
                    int frameToCSA = frameMap.offsetToCalleeSaveArea();
                    assert frameToCSA >= 0;
                    asm.save(csl, frameToCSA);
                }
                needsLeave = true;
            }
        }

        @Override
        public void leave(TargetMethodAssembler tasm) {
            if (!needsLeave) {
                return;
            }

            int frameSize = tasm.frameMap.frameSize();
            AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
            CiCalleeSaveLayout csl = tasm.frameMap.registerConfig.getCalleeSaveLayout();
            RiRegisterConfig regConfig = tasm.frameMap.registerConfig;

            if (csl != null && csl.size != 0) {
                tasm.targetMethod.setRegisterRestoreEpilogueOffset(asm.codeBuffer.position());
                // saved all registers, restore all registers
                int frameToCSA = tasm.frameMap.offsetToCalleeSaveArea();
                asm.restore(csl, frameToCSA);
            }

            asm.incrementq(rsp, frameSize - 8); // account for the pop of RBP below
            asm.pop(rbp);

            if (GraalOptions.GenSafepoints) {
                HotSpotVMConfig config = ((HotSpotRuntime) runtime).config;

                // If at the return point, then the frame has already been popped
                // so deoptimization cannot be performed here. The HotSpot runtime
                // detects this case - see the definition of frame::should_be_deoptimized()

                CiRegister scratch = regConfig.getScratchRegister();
                if (config.isPollingPageFar) {
                    asm.movq(scratch, config.safepointPollingAddress);
                    tasm.recordMark(MARK_POLL_RETURN_FAR);
                    asm.movq(scratch, new CiAddress(tasm.target.wordKind, scratch.asValue()));
                } else {
                    tasm.recordMark(MARK_POLL_RETURN_NEAR);
                    asm.movq(scratch, new CiAddress(tasm.target.wordKind, rip.asValue()));
                }
            }
        }
    }

    @Override
    public TargetMethodAssembler newAssembler(FrameMap frameMap, LIR lir) {
        AbstractAssembler masm = new AMD64MacroAssembler(target, frameMap.registerConfig);
        HotSpotFrameContext frameContext = new HotSpotFrameContext(lir);
        TargetMethodAssembler tasm = new TargetMethodAssembler(target, runtime, frameMap, masm, frameContext);
        tasm.setFrameSize(frameMap.frameSize());
        tasm.targetMethod.setCustomStackAreaOffset(frameMap.offsetToCustomArea());
        return tasm;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, RiResolvedMethod method, LIR lir) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) tasm.asm;
        FrameMap frameMap = tasm.frameMap;
        RiRegisterConfig regConfig = frameMap.registerConfig;
        HotSpotVMConfig config = ((HotSpotRuntime) runtime).config;
        Label unverifiedStub = new Label();
        CiRegister scratch = regConfig.getScratchRegister();

        // Emit the prefix
        tasm.recordMark(MARK_OSR_ENTRY);
        tasm.recordMark(MARK_UNVERIFIED_ENTRY);

        boolean isStatic = Modifier.isStatic(method.accessFlags());
        if (!isStatic) {
            CiCallingConvention cc = regConfig.getCallingConvention(JavaCallee, new CiKind[] {CiKind.Object}, target, false);
            CiRegister inlineCacheKlass = rax; // see definition of IC_Klass in c1_LIRAssembler_x86.cpp
            CiRegister receiver = asRegister(cc.locations[0]);
            CiAddress src = new CiAddress(target.wordKind, receiver.asValue(), config.hubOffset);

            asm.movq(scratch, src);
            asm.cmpq(inlineCacheKlass, scratch);
            asm.jcc(ConditionFlag.notEqual, unverifiedStub);
        }

        asm.align(config.codeEntryAlignment);
        tasm.recordMark(MARK_VERIFIED_ENTRY);

        // Emit code for the LIR
        lir.emitCode(tasm);

        // Emit the suffix (i.e. out-of-line stubs)
        CiRegister thread = r15;
        CiRegister exceptionOop = regConfig.getScratchRegister();
        Label unwind = new Label();
        asm.bind(unwind);
        tasm.recordMark(MARK_UNWIND_ENTRY);
        CiAddress exceptionOopField = new CiAddress(CiKind.Object, thread.asValue(), config.threadExceptionOopOffset);
        CiAddress exceptionPcField = new CiAddress(CiKind.Object, thread.asValue(), config.threadExceptionPcOffset);
        asm.movq(exceptionOop, exceptionOopField);
        asm.xorq(scratch, scratch);
        asm.movq(exceptionOopField, scratch);
        asm.movq(exceptionPcField, scratch);

        AMD64Call.directCall(tasm, asm, config.unwindExceptionStub, null);
        AMD64Call.shouldNotReachHere(tasm, asm);

        tasm.recordMark(MARK_EXCEPTION_HANDLER_ENTRY);
        AMD64Call.directCall(tasm, asm, config.handleExceptionStub, null);
        AMD64Call.shouldNotReachHere(tasm, asm);

        tasm.recordMark(MARK_DEOPT_HANDLER_ENTRY);
        AMD64Call.directCall(tasm, asm, config.handleDeoptStub, null);
        AMD64Call.shouldNotReachHere(tasm, asm);

        if (!isStatic) {
            asm.bind(unverifiedStub);
            AMD64Call.directJmp(tasm, asm, config.inlineCacheMissStub);
        }

        for (int i = 0; i < GraalOptions.MethodEndBreakpointGuards; ++i) {
            asm.int3();
        }
    }
}
