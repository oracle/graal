/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;

public class OptimizeGuardAnchorsPhase extends Phase {
    private static final DebugMetric metricGuardsAnchorOptimized = Debug.metric("GuardsAnchorOptimized");
    private static final DebugMetric metricGuardsOptimizedAtSplit = Debug.metric("GuardsOptimizedAtSplit");

    public static class LazyCFG {
        private ControlFlowGraph cfg;
        private StructuredGraph graph;

        public LazyCFG(StructuredGraph graph) {
            this.graph = graph;
        }

        public ControlFlowGraph get() {
            if (cfg == null) {
                cfg = ControlFlowGraph.compute(graph, true, false, true, true);
            }
            return cfg;
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        LazyCFG cfg = new LazyCFG(graph);
        for (AbstractBeginNode begin : graph.getNodes(AbstractBeginNode.class)) {
            if (!(begin instanceof StartNode || begin.predecessor() instanceof ControlSplitNode)) {
                NodeIterable<GuardNode> guards = begin.guards();
                if (guards.isNotEmpty()) {
                    AbstractBeginNode newAnchor = computeOptimalAnchor(cfg.get(), begin);
                    // newAnchor == begin is possible because postdominator computation assumes that
                    // loops never end
                    if (newAnchor != begin) {
                        for (GuardNode guard : guards.snapshot()) {
                            guard.setGuard(newAnchor);
                        }
                        metricGuardsAnchorOptimized.increment();
                    }
                }
            }
        }
        for (ControlSplitNode controlSplit : graph.getNodes(ControlSplitNode.class)) {
            optimizeAtControlSplit(controlSplit, cfg);
        }
    }

    public static AbstractBeginNode getOptimalAnchor(LazyCFG cfg, AbstractBeginNode begin) {
        if (begin instanceof StartNode || begin.predecessor() instanceof ControlSplitNode) {
            return begin;
        }
        return computeOptimalAnchor(cfg.get(), begin);
    }

    private static AbstractBeginNode computeOptimalAnchor(ControlFlowGraph cfg, AbstractBeginNode begin) {
        Block anchor = cfg.blockFor(begin);
        while (anchor.getDominator() != null && anchor.getDominator().getPostdominator() == anchor) {
            anchor = anchor.getDominator();
        }
        return anchor.getBeginNode();
    }

    private static void optimizeAtControlSplit(ControlSplitNode controlSplit, LazyCFG cfg) {
        AbstractBeginNode successor = findMinimumUsagesSuccessor(controlSplit);
        int successorCount = controlSplit.successors().count();
        List<GuardNode> otherGuards = new ArrayList<>(successorCount - 1);
        for (GuardNode guard : successor.guards().snapshot()) {
            if (guard.isDeleted() || guard.condition().usages().count() < successorCount) {
                continue;
            }
            for (GuardNode conditonGuard : guard.condition().usages().filter(GuardNode.class)) {
                if (conditonGuard != guard) {
                    GuardingNode conditonGuardAnchor = conditonGuard.getGuard();
                    if (conditonGuardAnchor.asNode().predecessor() == controlSplit && compatibleGuards(guard, conditonGuard)) {
                        otherGuards.add(conditonGuard);
                    }
                }
            }

            if (otherGuards.size() == successorCount - 1) {
                AbstractBeginNode anchor = computeOptimalAnchor(cfg.get(), AbstractBeginNode.prevBegin(controlSplit));
                GuardNode newGuard = controlSplit.graph().unique(new GuardNode(guard.condition(), anchor, guard.reason(), guard.action(), guard.negated(), guard.getSpeculation()));
                for (GuardNode otherGuard : otherGuards) {
                    otherGuard.replaceAndDelete(newGuard);
                }
                guard.replaceAndDelete(newGuard);
                metricGuardsOptimizedAtSplit.increment();
            }
            otherGuards.clear();
        }
    }

    private static boolean compatibleGuards(GuardNode guard, GuardNode conditonGuard) {
        return conditonGuard.negated() == guard.negated() && conditonGuard.action() == guard.action() && conditonGuard.reason() == guard.reason() &&
                        conditonGuard.getSpeculation().equals(guard.getSpeculation());
    }

    private static AbstractBeginNode findMinimumUsagesSuccessor(ControlSplitNode controlSplit) {
        NodeClassIterator successors = controlSplit.successors().iterator();
        AbstractBeginNode min = (AbstractBeginNode) successors.next();
        int minUsages = min.usages().count();
        while (successors.hasNext()) {
            AbstractBeginNode successor = (AbstractBeginNode) successors.next();
            int count = successor.usages().count();
            if (count < minUsages) {
                minUsages = count;
                min = successor;
            }
        }
        return min;
    }
}
