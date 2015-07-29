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

import static com.oracle.graal.lir.LIRValueUtil.*;
import static com.oracle.graal.lir.alloc.trace.TraceRegisterAllocationPhase.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.alloc.TraceBuilder.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.lir.ssi.*;
import com.oracle.graal.lir.util.*;

/**
 * Allocates a trivial trace i.e. a trace consisting of a single block with no instructions other
 * than the {@link LabelOp} and the {@link BlockEndOp}.
 */
public class TraceTrivialAllocator extends AllocationPhase {

    private final TraceBuilderResult<?> resultTraces;

    public TraceTrivialAllocator(TraceBuilderResult<?> resultTraces) {
        this.resultTraces = resultTraces;
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> trace, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        LIR lir = lirGenRes.getLIR();
        assert isTrivialTrace(lir, trace) : "Not a trivial trace! " + trace;
        B block = trace.iterator().next();

        AbstractBlockBase<?> pred = TraceUtil.getBestTraceInterPredecessor(resultTraces, block);

        VariableVirtualStackValueMap<Variable, Value> variableMap = new VariableVirtualStackValueMap<>(lir.nextVariable(), 0);
        SSIUtil.forEachValuePair(lir, block, pred, (to, from) -> {
            if (isVariable(to)) {
                variableMap.put(asVariable(to), from);
            }
        });

        ValueProcedure outputConsumer = (value, mode, flags) -> {
            if (isVariable(value)) {
                return variableMap.get(asVariable(value));
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
