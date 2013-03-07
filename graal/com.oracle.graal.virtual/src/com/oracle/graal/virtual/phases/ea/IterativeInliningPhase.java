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

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.phases.ea.EffectList.Effect;

public class IterativeInliningPhase extends Phase {

    private final PhasePlan plan;

    private final GraalCodeCacheProvider runtime;
    private final Assumptions assumptions;
    private final GraphCache cache;
    private final OptimisticOptimizations optimisticOpts;
    private CustomCanonicalizer customCanonicalizer;

    public IterativeInliningPhase(GraalCodeCacheProvider runtime, Assumptions assumptions, GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts) {
        this.runtime = runtime;
        this.assumptions = assumptions;
        this.cache = cache;
        this.plan = plan;
        this.optimisticOpts = optimisticOpts;
    }

    public void setCustomCanonicalizer(CustomCanonicalizer customCanonicalizer) {
        this.customCanonicalizer = customCanonicalizer;
    }

    public static final void trace(String format, Object... obj) {
        if (GraalOptions.TraceEscapeAnalysis) {
            Debug.log(format, obj);
        }
    }

    public static final void error(String format, Object... obj) {
        System.out.print(String.format(format, obj));
    }

    @Override
    protected void run(final StructuredGraph graph) {
        runIterations(graph, true);
        runIterations(graph, false);
    }

    private void runIterations(final StructuredGraph graph, final boolean simple) {
        Boolean continueIteration = true;
        for (int iteration = 0; iteration < GraalOptions.EscapeAnalysisIterations && continueIteration; iteration++) {
            continueIteration = Debug.scope("iteration " + iteration, new Callable<Boolean>() {

                @Override
                public Boolean call() {
                    SchedulePhase schedule = new SchedulePhase();
                    schedule.apply(graph, false);
                    PartialEscapeClosure closure = new PartialEscapeClosure(graph.createNodeBitMap(), schedule, runtime, assumptions);
                    ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock(), new BlockState(), null);

                    if (closure.getNewVirtualObjectCount() != 0) {
                        // apply the effects collected during the escape analysis iteration
                        ArrayList<Node> obsoleteNodes = new ArrayList<>();
                        for (Effect effect : closure.getEffects()) {
                            effect.apply(graph, obsoleteNodes);
                        }
                        trace("%s\n", closure.getEffects());

                        Debug.dump(graph, "after PartialEscapeAnalysis");
                        assert PartialEscapeAnalysisPhase.noObsoleteNodes(graph, obsoleteNodes);

                        new DeadCodeEliminationPhase().apply(graph);
                        if (GraalOptions.OptCanonicalizer) {
                            new CanonicalizerPhase(runtime, assumptions, null, customCanonicalizer).apply(graph);
                        }
                    }

                    InliningPhase inlining = new InliningPhase(runtime, closure.getHints(), assumptions, cache, plan, optimisticOpts);
                    if (simple) {
                        inlining.setMaxMethodsPerInlining(1);
                    }
                    inlining.apply(graph);
                    new DeadCodeEliminationPhase().apply(graph);

                    if (GraalOptions.ConditionalElimination && GraalOptions.OptCanonicalizer) {
                        new CanonicalizerPhase(runtime, assumptions).apply(graph);
                        new IterativeConditionalEliminationPhase(runtime, assumptions).apply(graph);
                    }

                    if (!simple && closure.getNewVirtualObjectCount() == 0 && inlining.getInliningCount() == 0) {
                        return false;
                    }
                    return true;
                }
            });
        }
    }
}
