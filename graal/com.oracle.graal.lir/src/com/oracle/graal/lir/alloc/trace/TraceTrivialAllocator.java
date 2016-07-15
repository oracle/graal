/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.lir.LIRValueUtil.asVariable;
import static com.oracle.graal.lir.LIRValueUtil.isVariable;
import static com.oracle.graal.lir.alloc.trace.TraceUtil.isTrivialTrace;

import java.util.List;

import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.ValueProcedure;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.ssi.SSIUtil;
import com.oracle.graal.lir.util.VariableVirtualStackValueMap;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Allocates a trivial trace i.e. a trace consisting of a single block with no instructions other
 * than the {@link LabelOp} and the {@link JumpOp}.
 */
final class TraceTrivialAllocator extends TraceAllocationPhase<TraceAllocationPhase.TraceAllocationContext> {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, TraceAllocationContext context) {
        LIR lir = lirGenRes.getLIR();
        TraceBuilderResult resultTraces = context.resultTraces;
        assert isTrivialTrace(lir, trace) : "Not a trivial trace! " + trace;
        AbstractBlockBase<?> block = trace.getBlocks()[0];

        AbstractBlockBase<?> pred = TraceUtil.getBestTraceInterPredecessor(resultTraces, block);

        VariableVirtualStackValueMap<Variable, Value> variableMap = new VariableVirtualStackValueMap<>(lir.numVariables(), 0);
        SSIUtil.forEachValuePair(lir, block, pred, (to, from) -> {
            if (isVariable(to)) {
                variableMap.put(asVariable(to), from);
            }
        });

        ValueProcedure outputConsumer = (value, mode, flags) -> {
            if (isVariable(value)) {
                Value incomingValue = variableMap.get(asVariable(value));
                assert !flags.contains(OperandFlag.COMPOSITE);
                return incomingValue;
            }
            return value;
        };

        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
        for (LIRInstruction op : instructions) {

            op.forEachOutput(outputConsumer);
            op.forEachTemp(outputConsumer);
            op.forEachAlive(outputConsumer);
            op.forEachInput(outputConsumer);
            op.forEachState(outputConsumer);
        }
    }
}
