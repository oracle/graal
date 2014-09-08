/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Options.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;

public class DeadCodeEliminationPhase extends Phase {

    public static class Options {
        // @formatter:off
        @Option(help = "Disable optional dead code eliminations")
        public static final OptionValue<Boolean> ReduceDCE = new OptionValue<>(true);
        // @formatter:on
    }

    // Metrics
    private static final DebugMetric metricNodesRemoved = Debug.metric("NodesRemoved");

    public enum Optionality {
        OPTIONAL,
        REQUIRED;
    }

    /**
     * Creates a dead code elimination phase that will be run irrespective of
     * {@link Options#ReduceDCE}.
     */
    public DeadCodeEliminationPhase() {
        this(Optionality.REQUIRED);
    }

    /**
     * Creates a dead code elimination phase that will be run only if it is
     * {@linkplain Optionality#REQUIRED non-optional} or {@link Options#ReduceDCE} is false.
     */
    public DeadCodeEliminationPhase(Optionality optionality) {
        this.optional = optionality == Optionality.OPTIONAL;
    }

    private final boolean optional;

    @Override
    public void run(StructuredGraph graph) {
        if (optional && ReduceDCE.getValue()) {
            return;
        }
        NodeFlood flood = graph.createNodeFlood();

        flood.add(graph.start());
        iterateSuccessors(flood);
        disconnectCFGNodes(flood, graph);
        iterateInputs(flood, graph);
        deleteNodes(flood, graph);

        // remove chained Merges
        for (MergeNode merge : graph.getNodes(MergeNode.class)) {
            if (merge.forwardEndCount() == 1 && !(merge instanceof LoopBeginNode)) {
                graph.reduceTrivialMerge(merge);
            }
        }
    }

    private static void iterateSuccessors(NodeFlood flood) {
        for (Node current : flood) {
            if (current instanceof AbstractEndNode) {
                AbstractEndNode end = (AbstractEndNode) current;
                flood.add(end.merge());
            } else {
                for (Node successor : current.successors()) {
                    flood.add(successor);
                }
            }
        }
    }

    private static void disconnectCFGNodes(NodeFlood flood, StructuredGraph graph) {
        for (AbstractEndNode node : graph.getNodes(AbstractEndNode.class)) {
            if (!flood.isMarked(node)) {
                MergeNode merge = node.merge();
                if (merge != null && flood.isMarked(merge)) {
                    // We are a dead end node leading to a live merge.
                    merge.removeEnd(node);
                }
            }
        }
        for (LoopBeginNode loop : graph.getNodes(LoopBeginNode.class)) {
            if (flood.isMarked(loop)) {
                boolean reachable = false;
                for (LoopEndNode end : loop.loopEnds()) {
                    if (flood.isMarked(end)) {
                        reachable = true;
                        break;
                    }
                }
                if (!reachable) {
                    Debug.log("Removing loop with unreachable end: %s", loop);
                    for (LoopEndNode end : loop.loopEnds().snapshot()) {
                        loop.removeEnd(end);
                    }
                    graph.reduceDegenerateLoopBegin(loop);
                }
            }
        }
    }

    private static void deleteNodes(NodeFlood flood, StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                node.clearInputs();
                node.clearSuccessors();
            }
        }
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                metricNodesRemoved.increment();
                node.safeDelete();
            }
        }
    }

    private static void iterateInputs(NodeFlood flood, StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (flood.isMarked(node)) {
                for (Node input : node.inputs()) {
                    flood.add(input);
                }
            }
        }
        for (Node current : flood) {
            for (Node input : current.inputs()) {
                flood.add(input);
            }
        }
    }

}
