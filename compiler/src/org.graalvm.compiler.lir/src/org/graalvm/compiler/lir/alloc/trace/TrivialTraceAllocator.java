/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.alloc.trace;

import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.alloc.trace.TraceUtil.isTrivialTrace;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.ValueProcedure;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.ssa.SSAUtil;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Allocates a trivial trace i.e. a trace consisting of a single block with no instructions other
 * than the {@link LabelOp} and the {@link JumpOp}.
 */
final class TrivialTraceAllocator extends TraceAllocationPhase<TraceAllocationPhase.TraceAllocationContext> {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, TraceAllocationContext context) {
        LIR lir = lirGenRes.getLIR();
        assert isTrivialTrace(lir, trace) : "Not a trivial trace! " + trace;
        AbstractBlockBase<?> block = trace.getBlocks()[0];
        assert TraceAssertions.singleHeadPredecessor(trace) : "Trace head with more than one predecessor?!" + trace;
        AbstractBlockBase<?> pred = block.getPredecessors()[0];

        GlobalLivenessInfo livenessInfo = context.livenessInfo;
        allocate(lir, block, pred, livenessInfo);
    }

    private static void allocate(LIR lir, AbstractBlockBase<?> block, AbstractBlockBase<?> pred, GlobalLivenessInfo livenessInfo) {
        assert TraceAssertions.liveSetsAreSorted(livenessInfo, block);
        assert TraceAssertions.liveSetsAreSorted(livenessInfo, pred);

        // If we have Phis, we need to create a map from variables to locations.
        boolean hasPhis = SSAUtil.numPhiOut(lir, block) > 0;
        Value[] variableMap = hasPhis ? new Value[lir.numVariables()] : null;

        // setup incoming variables/locations
        final int[] blockIn = livenessInfo.getBlockIn(block);
        final Value[] predLocOut = livenessInfo.getOutLocation(pred);
        int inLenght = blockIn.length;

        // setup outgoing variables/locations
        final int[] blockOut = livenessInfo.getBlockOut(block);
        int outLength = blockOut.length;
        final Value[] locationOut = new Value[outLength];

        assert outLength <= inLenght : "Trivial Trace! There cannot be more outgoing values than incoming.";
        int outIdx = 0;
        for (int inIdx = 0; inIdx < inLenght; inIdx++) {
            Value value = predLocOut[inIdx];
            if (hasPhis) {
                // collect mapping for Phi resolution
                variableMap[blockIn[inIdx]] = value;
            }
            if (outIdx < outLength && blockOut[outIdx] == blockIn[inIdx]) {
                // set the outgoing location to the incoming value
                locationOut[outIdx++] = value;
            }
        }
        assert outIdx == outLength;

        /*
         * Since we do not change any of the location we can just use the outgoing of the
         * predecessor.
         */
        livenessInfo.setInLocations(block, predLocOut);
        livenessInfo.setOutLocations(block, locationOut);
        if (hasPhis) {
            handlePhiOut(lir, block, variableMap);
        }
    }

    private static void handlePhiOut(LIR lir, AbstractBlockBase<?> block, Value[] variableMap) {
        // handle outgoing phi values
        ValueProcedure outputConsumer = new ValueProcedure() {
            @Override
            public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVariable(value)) {
                    return variableMap[asVariable(value).index];
                }
                return value;
            }
        };

        JumpOp jump = SSAUtil.phiOut(lir, block);
        // Jumps have only alive values (outgoing phi values)
        jump.forEachAlive(outputConsumer);
    }

}
