/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.virtual.phases.ea;

import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class IterativeInliningPhase extends BasePhase<HighTierContext> {

    private final PhasePlan plan;

    private final GraphCache cache;
    private final OptimisticOptimizations optimisticOpts;
    private final CanonicalizerPhase canonicalizer;

    public IterativeInliningPhase(GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts, CanonicalizerPhase canonicalizer) {
        this.cache = cache;
        this.plan = plan;
        this.optimisticOpts = optimisticOpts;
        this.canonicalizer = canonicalizer;
    }

    public static final void trace(String format, Object... obj) {
        if (TraceEscapeAnalysis.getValue()) {
            Debug.log(format, obj);
        }
    }

    @Override
    protected void run(final StructuredGraph graph, final HighTierContext context) {
        runIterations(graph, true, context);
        runIterations(graph, false, context);
    }

    private void runIterations(final StructuredGraph graph, final boolean simple, final HighTierContext context) {
        Boolean continueIteration = true;
        for (int iteration = 0; iteration < EscapeAnalysisIterations.getValue() && continueIteration; iteration++) {
            continueIteration = Debug.scope("iteration " + iteration, new Callable<Boolean>() {

                @Override
                public Boolean call() {
                    boolean progress = false;
                    PartialEscapePhase ea = new PartialEscapePhase(false, canonicalizer);
                    boolean eaResult = ea.runAnalysis(graph, context);
                    progress |= eaResult;

                    Map<Invoke, Double> hints = PEAInliningHints.getValue() ? PartialEscapePhase.getHints(graph) : null;

                    InliningPhase inlining = new InliningPhase(context.getRuntime(), hints, context.getReplacements(), context.getAssumptions(), cache, plan, optimisticOpts);
                    inlining.setMaxMethodsPerInlining(simple ? 1 : Integer.MAX_VALUE);
                    inlining.apply(graph);
                    progress |= inlining.getInliningCount() > 0;

                    new DeadCodeEliminationPhase().apply(graph);

                    if (ConditionalElimination.getValue() && OptCanonicalizer.getValue()) {
                        canonicalizer.apply(graph, context);
                        new IterativeConditionalEliminationPhase().apply(graph, context);
                    }

                    return progress;
                }
            });
        }
    }
}
