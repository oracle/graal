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

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.phases.LIRPhase;

import jdk.vm.ci.code.TargetDescription;

public abstract class TraceAllocationPhase extends LIRPhase<TraceAllocationPhase.TraceAllocationContext> {

    public static final class TraceAllocationContext {
        public final MoveFactory spillMoveFactory;
        public final RegisterAllocationConfig registerAllocationConfig;
        public final TraceBuilderResult<?> resultTraces;

        public TraceAllocationContext(MoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig, TraceBuilderResult<?> resultTraces) {
            this.spillMoveFactory = spillMoveFactory;
            this.registerAllocationConfig = registerAllocationConfig;
            this.resultTraces = resultTraces;
        }
    }

    public final <B extends AbstractBlockBase<B>> void apply(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, Trace<B> trace, TraceAllocationContext context,
                    boolean dumpLIR) {
        apply(target, lirGenRes, codeEmittingOrder, trace.getBlocks(), context, dumpLIR);
    }

    public final <B extends AbstractBlockBase<B>> void apply(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, Trace<B> trace, TraceAllocationContext context) {
        apply(target, lirGenRes, codeEmittingOrder, trace.getBlocks(), context);
    }

    @Override
    protected final <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> sortedBlocks,
                    TraceAllocationContext context) {
        TraceBuilderResult<B> resultTraces = getTraceBuilderResult(context);
        Trace<B> trace = resultTraces.getTraces().get(resultTraces.getTraceForBlock(sortedBlocks.get(0)));
        run(target, lirGenRes, codeEmittingOrder, trace, context);
    }

    @SuppressWarnings("unchecked")
    private static <B extends AbstractBlockBase<B>> TraceBuilderResult<B> getTraceBuilderResult(TraceAllocationContext context) {
        return (TraceBuilderResult<B>) context.resultTraces;
    }

    protected abstract <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, Trace<B> trace, TraceAllocationContext context);
}
