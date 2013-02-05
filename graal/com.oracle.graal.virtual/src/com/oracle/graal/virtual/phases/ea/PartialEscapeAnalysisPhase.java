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
import com.oracle.graal.api.meta.*;
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

public class PartialEscapeAnalysisPhase extends Phase {

    private final TargetDescription target;
    private final MetaAccessProvider runtime;
    private final Assumptions assumptions;
    private CustomCanonicalizer customCanonicalizer;
    private final boolean iterative;

    public PartialEscapeAnalysisPhase(TargetDescription target, MetaAccessProvider runtime, Assumptions assumptions, boolean iterative) {
        this.target = target;
        this.runtime = runtime;
        this.assumptions = assumptions;
        this.iterative = iterative;
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
        if (!matches(graph, GraalOptions.EscapeAnalyzeOnly)) {
            return;
        }

        boolean analyzableNodes = false;
        for (Node node : graph.getNodes()) {
            if (node instanceof VirtualizableAllocation) {
                analyzableNodes = true;
                break;
            }
        }
        if (!analyzableNodes) {
            return;
        }

        Boolean continueIteration = true;
        for (int iteration = 0; iteration < GraalOptions.EscapeAnalysisIterations && continueIteration; iteration++) {
            continueIteration = Debug.scope("iteration " + iteration, new Callable<Boolean>() {

                @Override
                public Boolean call() {
                    SchedulePhase schedule = new SchedulePhase();
                    schedule.apply(graph, false);
                    PartialEscapeClosure closure = new PartialEscapeClosure(graph.createNodeBitMap(), schedule, runtime);
                    ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock(), new BlockState(), null);

                    if (closure.getNewVirtualObjectCount() == 0) {
                        return false;
                    }

                    // apply the effects collected during the escape analysis iteration
                    ArrayList<Node> obsoleteNodes = new ArrayList<>();
                    for (Effect effect : closure.getEffects()) {
                        effect.apply(graph, obsoleteNodes);
                    }
                    trace("%s\n", closure.getEffects());

                    Debug.dump(graph, "after PartialEscapeAnalysis");
                    assert noObsoleteNodes(graph, obsoleteNodes);

                    new DeadCodeEliminationPhase().apply(graph);
                    if (!iterative) {
                        return false;
                    }
                    if (GraalOptions.OptCanonicalizer) {
                        new CanonicalizerPhase(target, runtime, assumptions, null, customCanonicalizer).apply(graph);
                    }
                    return true;
                }
            });
        }
    }

    private static boolean matches(StructuredGraph graph, String filter) {
        if (filter != null) {
            ResolvedJavaMethod method = graph.method();
            return method != null && MetaUtil.format("%H.%n", method).contains(filter);
        }
        return true;
    }

    private static boolean noObsoleteNodes(StructuredGraph graph, ArrayList<Node> obsoleteNodes) {
        // helper code that determines the paths that keep obsolete nodes alive:

        NodeFlood flood = graph.createNodeFlood();
        IdentityHashMap<Node, Node> path = new IdentityHashMap<>();
        flood.add(graph.start());
        for (Node current : flood) {
            if (current instanceof EndNode) {
                EndNode end = (EndNode) current;
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
                assert !flood.isMarked(node);
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
                error("offending node path:");
                Node current = node;
                while (current != null) {
                    error(current.toString());
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
}
