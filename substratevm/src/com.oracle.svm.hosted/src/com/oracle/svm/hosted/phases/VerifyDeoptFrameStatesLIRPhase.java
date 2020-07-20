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
package com.oracle.svm.hosted.phases;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import com.oracle.svm.core.graal.lir.DeoptEntryOp;
import com.oracle.svm.core.heap.SubstrateReferenceMap;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.JavaValue;

/**
 * Verification that deoptimization target frame states do not have live values that are not in the
 * state, i.e., that do not correspond to a Java local variable or expression stack value. Such live
 * values would not be restored during deoptimization, so the value would be undefined after
 * deoptimization.
 */
public class VerifyDeoptFrameStatesLIRPhase extends PostAllocationOptimizationPhase {
    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        new Instance().run(lirGenRes);
    }

    @Override
    protected CharSequence createName() {
        return "VerifyDeoptFrameStatesLIRPhase";
    }
}

class Instance {
    private Map<Integer, Object> allowedStackSlots;

    public void run(LIRGenerationResult lirGenRes) {
        LIR ir = lirGenRes.getLIR();
        DebugContext debug = ir.getDebug();
        FrameMap frameMap = lirGenRes.getFrameMap();
        for (AbstractBlockBase<?> block : ir.linearScanOrder()) {
            for (LIRInstruction op : ir.getLIRforBlock(block)) {
                op.forEachState((instruction, state) -> doState(debug, frameMap, instruction, state));
            }
        }
    }

    private void doState(DebugContext debug, FrameMap frameMap, LIRInstruction op, LIRFrameState state) {
        SubstrateReferenceMap refMap = (SubstrateReferenceMap) state.debugInfo().getReferenceMap();

        /*
         * We want to verify explicit deoptimization entry points, and implicit deoptimization entry
         * points at call sites. Unfortunately, just checking isDeoptEntry gives us false positives
         * for some runtime calls that re-use a state (which is not marked as "during call").
         */
        boolean isDeoptEntry = ((HostedMethod) state.topFrame.getMethod()).compilationInfo.isDeoptEntry(state.topFrame.getBCI(), state.topFrame.duringCall, state.topFrame.rethrowException);
        if (op instanceof DeoptEntryOp || (state.topFrame.duringCall && isDeoptEntry)) {
            BytecodeFrame frame = state.topFrame;

            Map<Integer, Object> allUsedRegisters = refMap.getDebugAllUsedRegisters();
            Map<Integer, Object> allUsedStackSlots = refMap.getDebugAllUsedStackSlots();

            if (allUsedRegisters != null && !allUsedRegisters.isEmpty()) {
                throw shouldNotReachHere("Deoptimization target must not use any registers");
            }

            if (allUsedStackSlots != null) {
                Map<Integer, Object> cleanedStackSlots = new HashMap<>(allUsedStackSlots);
                do {
                    /*
                     * Remove stack slot information for all slots which already have a
                     * representative in the bytecode frame.
                     */
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
                            cleanedStackSlots.remove(offset);
                        } else if (ValueUtil.isConstantJavaValue(value) || ValueUtil.isIllegalJavaValue(value)) {
                            /* Nothing to do. */
                        } else {
                            throw shouldNotReachHere("unknown value in deopt target: " + value);
                        }
                    }
                    frame = frame.caller();
                } while (frame != null);

                int firstBci = state.topFrame.getMethod().isSynchronized() ? BytecodeFrame.BEFORE_BCI : 0;
                if (state.topFrame.getBCI() == firstBci && state.topFrame.caller() == null && state.topFrame.duringCall == false && state.topFrame.rethrowException == false) {
                    /*
                     * Some stack slots, e.g., the return address and manually allocated stack
                     * memory, are alive the whole method. So all stack slots that are registered
                     * for the method entry are allowed to be registered in all subsequent states.
                     */
                    assert op instanceof DeoptEntryOp;
                    assert allowedStackSlots == null;
                    allowedStackSlots = new HashMap<>(cleanedStackSlots);

                } else {
                    if (allowedStackSlots == null) {
                        allowedStackSlots = new HashMap<>();
                    }
                    for (Integer key : allowedStackSlots.keySet()) {
                        cleanedStackSlots.remove(key);
                    }

                    if (!cleanedStackSlots.isEmpty()) {
                        throw shouldNotReachHere("unknown values in stack slots: method " + state.topFrame.getMethod().toString() + ", op " + op.id() + " " + op + ": " + cleanedStackSlots);
                    }
                }
            }
        }
    }
}
