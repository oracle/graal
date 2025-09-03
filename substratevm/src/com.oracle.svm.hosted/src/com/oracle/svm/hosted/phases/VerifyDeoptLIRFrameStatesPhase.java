/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.hosted.phases;

import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.graal.lir.DeoptEntryOp;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.FinalCodeAnalysisPhase;
import jdk.graal.compiler.nodes.FrameState;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.JavaValue;

/**
 * Verifies that at deoptimization entry points all live values are contained in the entry point's
 * LIRFrameState. Live values missing from the LIRFrameState would not be restored during
 * deoptimization, so the value would be undefined after deoptimization.
 *
 * The verification is performed by looking at the reference map for each deoptimization entry point
 * and comparing it with what is captured within the LIRFrameState. All stack slots present within
 * the reference map should also be in the LIRFrameState.
 */
public class VerifyDeoptLIRFrameStatesPhase extends FinalCodeAnalysisPhase {
    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, FinalCodeAnalysisContext context) {
        new Instance().run(lirGenRes);
    }

    @Override
    protected CharSequence createName() {
        return "VerifyDeoptLIRFrameStatesPhase";
    }
}

class Instance {
    private StringBuilder errors;

    public void run(LIRGenerationResult lirGenRes) {
        LIR ir = lirGenRes.getLIR();
        DebugContext debug = ir.getDebug();
        FrameMap frameMap = lirGenRes.getFrameMap();
        for (int bockId : ir.linearScanOrder()) {
            if (LIR.isBlockDeleted(bockId)) {
                continue;
            }
            BasicBlock<?> block = ir.getControlFlowGraph().getBlocks()[bockId];
            for (LIRInstruction op : ir.getLIRforBlock(block)) {
                op.forEachState((instruction, state) -> doState(debug, frameMap, instruction, state));
            }
        }
        if (errors != null) {
            throw VMError.shouldNotReachHere(errors.toString());
        }
    }

    private void reportError(LIRFrameState state, LIRInstruction op, String message) {
        if (errors == null) {
            errors = new StringBuilder();
            errors.append(System.lineSeparator()).append("Problems found within VerifyDeoptLIRFrameStatesPhase").append(System.lineSeparator());
        }
        errors.append(System.lineSeparator()).append("Problem: ").append(message);
        BytecodeFrame frame = state.topFrame;
        while (frame.caller() != null) {
            frame = frame.caller();
        }
        errors.append(System.lineSeparator()).append("Method: ").append(frame.getMethod());
        errors.append(System.lineSeparator()).append("op id: ").append(op.id()).append(", ").append(op);
        frame = state.topFrame;
        do {
            errors.append(System.lineSeparator()).append("at: bci ").append(frame.getBCI()).append(", duringCall: ").append(frame.duringCall).append(", rethrowException: ")
                            .append(frame.rethrowException).append(", method: ").append(frame.getMethod());
            frame = frame.caller();
        } while (frame != null);
        errors.append(System.lineSeparator()).append("End Problem").append(System.lineSeparator());
    }

    private static boolean isImplicitDeoptEntry(LIRFrameState state) {
        BytecodeFrame frame = state.topFrame;
        if (frame.duringCall) {
            /*
             * A state is an implicit deoptimization entrypoint if it corresponds to a call which is
             * valid for deoptimization and is registered as a deopt entry.
             */
            return state.validForDeoptimization && ((HostedMethod) frame.getMethod()).compilationInfo.isDeoptEntry(frame.getBCI(), FrameState.StackState.of(frame));
        }

        return false;
    }

    private void doState(DebugContext debug, FrameMap frameMap, LIRInstruction op, LIRFrameState state) {
        /*
         * We want to verify explicit deoptimization entry points and implicit deoptimization entry
         * points at call sites.
         *
         * Explicit deoptimization entrypoints are represented with a DeoptEntryOp whereas implicit
         * entry points must query the compilation information.
         */
        if (op instanceof DeoptEntryOp || isImplicitDeoptEntry(state)) {
            SubstrateReferenceMap refMap = (SubstrateReferenceMap) state.debugInfo().getReferenceMap();
            Map<Integer, Object> refMapRegisters = refMap.getDebugAllUsedRegisters();
            Map<Integer, Object> refMapStackSlots = refMap.getDebugAllUsedStackSlots();

            if (refMapRegisters != null && !refMapRegisters.isEmpty()) {
                reportError(state, op, "Deoptimization target must not use any registers");
            }

            if (refMapStackSlots != null) {
                Map<Integer, Object> missingStackSlots = new HashMap<>(refMapStackSlots);
                /*
                 * Remove stack slot information for all slots which have a representative in the
                 * bytecode frame (and callers when there is inlining).
                 */
                BytecodeFrame frame = state.topFrame;
                do {
                    for (JavaValue v : frame.values) {
                        JavaValue value = v;
                        if (value instanceof StackLockValue) {
                            StackLockValue lock = (StackLockValue) value;
                            assert ValueUtil.isIllegal(lock.getSlot());
                            value = lock.getOwner();
                        }
                        if (value instanceof StackSlot) {
                            StackSlot stackSlot = (StackSlot) value;
                            int offset = stackSlot.getOffset(frameMap.totalFrameSize());
                            debug.log("remove slot %d: %s", offset, stackSlot);
                            missingStackSlots.remove(offset);
                        } else if (ValueUtil.isConstantJavaValue(value) || ValueUtil.isIllegalJavaValue(value)) {
                            /* Nothing to do. */
                        } else if (ReservedRegisters.singleton().isAllowedInFrameState(value)) {
                            /* Nothing to do. */
                        } else {
                            reportError(state, op, "unknown value in deopt target: " + value);
                        }
                    }
                    frame = frame.caller();
                } while (frame != null);

                if (!missingStackSlots.isEmpty()) {
                    reportError(state, op, "LIRFrameState is missing live stack slot values: " + missingStackSlots);
                }
            }
        }
    }
}
