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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

public class PartialEscapeAnalysisPhase extends BasePhase<HighTierContext> {

    private final CustomCanonicalizer customCanonicalizer;
    private final boolean iterative;
    private final boolean readElimination;

    public PartialEscapeAnalysisPhase(boolean iterative, boolean readElimination) {
        this(null, iterative, readElimination);
    }

    public PartialEscapeAnalysisPhase(CustomCanonicalizer customCanonicalizer, boolean iterative, boolean readElimination) {
        this.customCanonicalizer = customCanonicalizer;
        this.iterative = iterative;
        this.readElimination = readElimination;
    }

    public static final void trace(String format, Object... obj) {
        if (GraalOptions.TraceEscapeAnalysis) {
            Debug.log(format, obj);
        }
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        runAnalysis(graph, context);
    }

    public boolean runAnalysis(final StructuredGraph graph, final HighTierContext context) {
        if (!matches(graph, GraalOptions.EscapeAnalyzeOnly)) {
            return false;
        }

        if (!readElimination) {
            boolean analyzableNodes = false;
            for (Node node : graph.getNodes()) {
                if (node instanceof VirtualizableRoot) {
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
        for (int iteration = 0; iteration < GraalOptions.EscapeAnalysisIterations && continueIteration; iteration++) {
            boolean currentChanged = Debug.scope("iteration " + iteration, new Callable<Boolean>() {

                @Override
                public Boolean call() {

                    SchedulePhase schedule = new SchedulePhase();
                    schedule.apply(graph, false);
                    PartialEscapeClosure closure = new PartialEscapeClosure(graph.createNodeBitMap(), schedule, context.getRuntime(), context.getAssumptions());
                    ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock(), new BlockState(), null);

                    if (!closure.hasChanged()) {
                        return false;
                    }

                    // apply the effects collected during the escape analysis iteration
                    List<Node> obsoleteNodes = closure.applyEffects(graph);

                    Debug.dump(graph, "after PartialEscapeAnalysis iteration");
                    assert noObsoleteNodes(graph, obsoleteNodes);

                    new DeadCodeEliminationPhase().apply(graph);

                    if (GraalOptions.OptCanonicalizer) {
                        new CanonicalizerPhase.Instance(context.getRuntime(), context.getAssumptions(), null, customCanonicalizer).apply(graph);
                    }

                    return true;
                }
            });
            continueIteration = currentChanged && iterative;
            changed |= currentChanged;
        }

        return changed;
    }

    private static boolean matches(StructuredGraph graph, String filter) {
        if (filter != null) {
            if (filter.startsWith("~")) {
                ResolvedJavaMethod method = graph.method();
                return method == null || !MetaUtil.format("%H.%n", method).contains(filter.substring(1));
            } else {
                ResolvedJavaMethod method = graph.method();
                return method != null && MetaUtil.format("%H.%n", method).contains(filter);
            }
        }
        return true;
    }

    static boolean noObsoleteNodes(StructuredGraph graph, List<Node> obsoleteNodes) {
        // helper code that determines the paths that keep obsolete nodes alive:

        NodeFlood flood = graph.createNodeFlood();
        IdentityHashMap<Node, Node> path = new IdentityHashMap<>();
        flood.add(graph.start());
        for (Node current : flood) {
            if (current instanceof AbstractEndNode) {
                AbstractEndNode end = (AbstractEndNode) current;
                flood.add(end.merge());
                if (!path.containsKey(end.merge())) {
                    path.put(end.merge(), end);
                }
            } else {
                for (Node successor : current.successors()) {
                    flood.add(successor);
                    if (!path.containsKey(successor)) {
                        path.put(successor, current);
                    }
                }
            }
        }

        for (Node node : obsoleteNodes) {
            if (node instanceof FixedNode) {
                assert !flood.isMarked(node) : node;
            }
        }

        for (Node node : graph.getNodes()) {
            if (node instanceof LocalNode) {
                flood.add(node);
            }
            if (flood.isMarked(node)) {
                for (Node input : node.inputs()) {
                    flood.add(input);
                    if (!path.containsKey(input)) {
                        path.put(input, node);
                    }
                }
            }
        }
        for (Node current : flood) {
            for (Node input : current.inputs()) {
                flood.add(input);
                if (!path.containsKey(input)) {
                    path.put(input, current);
                }
            }
        }
        boolean success = true;
        for (Node node : obsoleteNodes) {
            if (flood.isMarked(node)) {
                TTY.print("offending node path:");
                Node current = node;
                while (current != null) {
                    TTY.println(current.toString());
                    current = path.get(current);
                    if (current != null && current instanceof FixedNode && !obsoleteNodes.contains(current)) {
                        break;
                    }
                }
                success = false;
            }
        }
        return success;
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
