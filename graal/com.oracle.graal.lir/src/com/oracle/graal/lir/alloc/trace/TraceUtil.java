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

import java.util.List;

import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;

import jdk.vm.ci.meta.Value;

public class TraceUtil {

    public static AbstractBlockBase<?> getBestTraceInterPredecessor(TraceBuilderResult traceResult, AbstractBlockBase<?> block) {
        AbstractBlockBase<?> bestPred = null;
        int bestTraceId = traceResult.getTraceForBlock(block).getId();
        for (AbstractBlockBase<?> pred : block.getPredecessors()) {
            int predTraceId = traceResult.getTraceForBlock(pred).getId();
            if (predTraceId < bestTraceId) {
                bestPred = pred;
                bestTraceId = predTraceId;
            }
        }
        return bestPred;
    }

    public static boolean isShadowedRegisterValue(Value value) {
        assert value != null;
        return value instanceof ShadowedRegisterValue;
    }

    public static ShadowedRegisterValue asShadowedRegisterValue(Value value) {
        assert isShadowedRegisterValue(value);
        return (ShadowedRegisterValue) value;
    }

    public static boolean isTrivialTrace(LIR lir, Trace trace) {
        if (trace.size() != 1) {
            return false;
        }
        List<LIRInstruction> instructions = lir.getLIRforBlock(trace.getBlocks().iterator().next());
        if (instructions.size() != 2) {
            return false;
        }
        assert instructions.get(0) instanceof LabelOp : "First instruction not a LabelOp: " + instructions.get(0);
        /*
         * Now we need to check if the BlockEndOp has no special operand requirements (i.e.
         * stack-slot, register). For now we just check for JumpOp because we know that it doesn't.
         */
        return instructions.get(1) instanceof JumpOp;
    }
}
