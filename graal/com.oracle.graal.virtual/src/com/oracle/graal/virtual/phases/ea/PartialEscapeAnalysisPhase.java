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
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

public class PartialEscapeAnalysisPhase extends BasePhase<PhaseContext> {

    public abstract static class Closure<T> extends ReentrantBlockIterator.BlockIteratorClosure<T> {

        public abstract boolean hasChanged();

        public abstract void applyEffects();
    }

    private final boolean iterative;
    private final boolean readElimination;
    private final CanonicalizerPhase canonicalizer;

    public PartialEscapeAnalysisPhase(boolean iterative, boolean readElimination, CanonicalizerPhase canonicalizer) {
        this.iterative = iterative;
        this.readElimination = readElimination;
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        runAnalysis(graph, context);
    }

    public boolean runAnalysis(final StructuredGraph graph, final PhaseContext context) {
        if (!VirtualUtil.matches(graph, EscapeAnalyzeOnly.getValue())) {
            return false;
        }

        if (!readElimination) {
            boolean analyzableNodes = false;
            for (Node node : graph.getNodes()) {
                if (node instanceof VirtualizableAllocation) {
                    analyzableNodes = true;
                    break;
                }
            }
            if (!analyzableNodes) {
                return false;
            }
        }

        boolean continueIteration = true;
        boolean changed = false;
        for (int iteration = 0; iteration < EscapeAnalysisIterations.getValue() && continueIteration; iteration++) {
            boolean currentChanged = Debug.scope("iteration " + iteration, new Callable<Boolean>() {

                @Override
                public Boolean call() {

                    SchedulePhase schedule = new SchedulePhase();
                    schedule.apply(graph, false);
                    Closure<?> closure = createAnalysisClosure(context, schedule);
                    ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock());

                    if (!closure.hasChanged()) {
                        return false;
                    }

                    // apply the effects collected during the escape analysis iteration
                    closure.applyEffects();

                    Debug.dump(graph, "after PartialEscapeAnalysis iteration");

                    new DeadCodeEliminationPhase().apply(graph);

                    if (OptCanonicalizer.getValue()) {
                        canonicalizer.apply(graph, context);
                    }

                    return true;
                }
            });
            continueIteration = currentChanged && iterative;
            changed |= currentChanged;
        }

        return changed;
    }

    protected Closure<?> createAnalysisClosure(PhaseContext context, SchedulePhase schedule) {
        return new PartialEscapeClosure<>(schedule, context.getRuntime(), context.getAssumptions());
    }

    public static Map<Invoke, Double> getHints(StructuredGraph graph) {
        NodesToDoubles probabilities = new ComputeProbabilityClosure(graph).apply();
        Map<Invoke, Double> hints = null;
        for (CommitAllocationNode commit : graph.getNodes(CommitAllocationNode.class)) {
            double sum = 0;
            double invokeSum = 0;
            for (Node commitUsage : commit.usages()) {
                for (Node usage : commitUsage.usages()) {
                    if (usage instanceof FixedNode) {
                        sum += probabilities.get((FixedNode) usage);
                    } else {
                        if (usage instanceof MethodCallTargetNode) {
                            invokeSum += probabilities.get(((MethodCallTargetNode) usage).invoke().asNode());
                        }
                        for (Node secondLevelUage : usage.usages()) {
                            if (secondLevelUage instanceof FixedNode) {
                                sum += probabilities.get(((FixedNode) secondLevelUage));
                            }
                        }
                    }
                }
            }
            // TODO(lstadler) get rid of this magic number
            if (sum > 100 && invokeSum > 0) {
                for (Node commitUsage : commit.usages()) {
                    for (Node usage : commitUsage.usages()) {
                        if (usage instanceof MethodCallTargetNode) {
                            if (hints == null) {
                                hints = new HashMap<>();
                            }
                            Invoke invoke = ((MethodCallTargetNode) usage).invoke();
                            hints.put(invoke, sum / invokeSum);
                        }
                    }
                }
            }
        }
        return hints;
    }
}
