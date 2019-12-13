/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.lir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.nodes.CFunctionEpilogueMarker;
import com.oracle.svm.core.nodes.CFunctionPrologueMarker;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.TargetDescription;

/**
 * Verifies that reference maps for C function calls are correct. See {@link CFunctionSnippets} for
 * details on the thread state transitions.
 *
 * In the machine code, there are three instructions that have a reference map: the address loaded
 * as the "last Java instruction pointer", the C function call, and the slow-path call to block at a
 * safepoint. The first two are the same LIR instruction, therefore, in this phase we only see two
 * LIR instructions: the C function call and the slow-path call. This phase verifies that these two
 * calls have the same reference map. As long as the thread is in
 * {@link StatusSupport#STATUS_IN_NATIVE Native} state, the safepoint manager can transition it into
 * the {@link StatusSupport#STATUS_IN_SAFEPOINT Safepoint} state at any time and the safepoint code
 * can start walking the stack, using the {@link JavaFrameAnchor}. This means that the GC can scan
 * the stack for roots any time while we are in the C function, while we are in the slow-path call,
 * or while transitioning between those two calls. Having different reference maps would lead to
 * wrong roots and therefore a crash of the GC.
 */
public class VerifyCFunctionReferenceMapsLIRPhase extends PostAllocationOptimizationPhase {

    @Override
    protected CharSequence createName() {
        return "VerifyCFunctionReferenceMapsLIRPhase";
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            /*
             * We only have explicit thread state transitions with a slow path in multi-threaded
             * mode.
             */
            return;
        }

        LIR ir = lirGenRes.getLIR();
        for (AbstractBlockBase<?> block : ir.linearScanOrder()) {
            List<LIRInstruction> instructions = ir.getLIRforBlock(block);
            for (int i = 0; i < instructions.size(); i++) {
                LIRInstruction op = instructions.get(i);
                if (op instanceof VerificationMarkerOp && ((VerificationMarkerOp) op).getMarker() instanceof CFunctionPrologueMarker) {
                    CFunctionPrologueMarker prologueMarker = (CFunctionPrologueMarker) ((VerificationMarkerOp) op).getMarker();
                    new VerificationInstance(ir, prologueMarker.getNewThreadStatus(), prologueMarker.getEpilogueMarker()).run(block, i);
                }
            }
        }
    }

    static class VerificationInstance {

        private final LIR ir;
        private final int newThreadStatus;
        private final CFunctionEpilogueMarker epilogueMarker;

        private final Set<AbstractBlockBase<?>> processed = new HashSet<>();
        private final Deque<AbstractBlockBase<?>> worklist = new ArrayDeque<>();

        private final List<LIRFrameState> states = new ArrayList<>();

        VerificationInstance(LIR ir, int newThreadStatus, CFunctionEpilogueMarker epilogueMarker) {
            this.ir = ir;
            this.newThreadStatus = newThreadStatus;
            this.epilogueMarker = epilogueMarker;
        }

        void run(AbstractBlockBase<?> startBlock, int startInstruction) {

            /*
             * Traverse all instructions of the control flow graph, starting with the instruction
             * that has the CFunctionPrologueMarker, until we find an instruction with the matching
             * CFunctionEpilogueMarker.
             */
            processBlock(startBlock, startInstruction);
            while (!worklist.isEmpty()) {
                processBlock(worklist.pop(), 0);
            }

            /*
             * Depending on the transition, we expect a certain minimum number of frame states. It
             * is always possible that we have more instructions with a framestate, e.g., infopoints
             * also capture the debugger state.
             */
            if (states.size() < expectedFrameStates()) {
                throw VMError.shouldNotReachHere("Expected at least " + expectedFrameStates() + " instructions with states, but found " + states.size());
            }

            /*
             * Check the reference maps of all instructions with state that we encountered during
             * the control flow traversal.
             */
            ReferenceMap firstMap = states.get(0).debugInfo().getReferenceMap();
            for (LIRFrameState state : states) {
                ReferenceMap map = state.debugInfo().getReferenceMap();
                if (!firstMap.equals(map)) {
                    throw VMError.shouldNotReachHere("Reference maps not equal: " + firstMap + ", " + map);
                }
            }
        }

        private void processBlock(AbstractBlockBase<?> block, int startInstruction) {
            processed.add(block);

            List<LIRInstruction> instructions = ir.getLIRforBlock(block);
            for (int i = startInstruction; i < instructions.size(); i++) {
                LIRInstruction op = instructions.get(i);
                if (op instanceof VerificationMarkerOp && ((VerificationMarkerOp) op).getMarker() == epilogueMarker) {
                    /*
                     * Found matching marker, stop verifying this block and do not process
                     * successors.
                     */
                    return;
                }
                op.forEachState(state -> states.add(state));
            }

            if (block.getSuccessorCount() == 0) {
                throw VMError.shouldNotReachHere("No epilogue marker found");
            }
            for (AbstractBlockBase<?> successor : block.getSuccessors()) {
                if (!processed.contains(successor)) {
                    worklist.add(successor);
                }
            }
        }

        /**
         * When doing a C function call with a Java to native transition, we expect at least 2 call
         * instructions with a frame state (the C function call and the slowpath transition back to
         * the Java thread status).
         *
         * When doing a C function call with a Java to VM transition, we only expect 1 call
         * instruction with a frame state (only the C function call) as we only need the fastpath
         * when doing the transition back to the Java thread status.
         */
        private int expectedFrameStates() {
            switch (newThreadStatus) {
                case StatusSupport.STATUS_IN_NATIVE:
                    return 2;
                case StatusSupport.STATUS_IN_VM:
                    return 1;
                default:
                    throw VMError.shouldNotReachHere("Unexpected thread status: " + newThreadStatus);
            }
        }
    }
}
