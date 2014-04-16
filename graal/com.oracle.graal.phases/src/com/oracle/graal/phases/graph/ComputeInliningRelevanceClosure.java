/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.graph;

import java.util.*;

import com.oracle.graal.cfg.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.util.*;

public class ComputeInliningRelevanceClosure {

    private static final double EPSILON = 1d / Integer.MAX_VALUE;

    private final StructuredGraph graph;
    private final NodesToDoubles nodeProbabilities;
    private final NodesToDoubles nodeRelevances;

    public ComputeInliningRelevanceClosure(StructuredGraph graph, NodesToDoubles nodeProbabilities) {
        this.graph = graph;
        this.nodeProbabilities = nodeProbabilities;
        this.nodeRelevances = new NodesToDoubles(graph.getNodeCount());
    }

    public NodesToDoubles apply() {
        new ComputeInliningRelevanceIterator(graph).apply();
        return nodeRelevances;
    }

    private class ComputeInliningRelevanceIterator extends ScopedPostOrderNodeIterator {

        private final HashMap<FixedNode, Scope> scopes;
        private double currentProbability;
        private double parentRelevance;

        public ComputeInliningRelevanceIterator(StructuredGraph graph) {
            super(graph);
            this.scopes = computeScopesAndProbabilities();
        }

        @Override
        protected void initializeScope() {
            Scope scope = scopes.get(currentScopeStart);
            parentRelevance = getParentScopeRelevance(scope);
            currentProbability = scope.probability;
        }

        private double getParentScopeRelevance(Scope scope) {
            if (scope.start instanceof LoopBeginNode) {
                assert scope.parent != null;
                double parentProbability = 0;
                for (AbstractEndNode end : ((LoopBeginNode) scope.start).forwardEnds()) {
                    parentProbability += nodeProbabilities.get(end);
                }
                return parentProbability / scope.parent.probability;
            } else {
                assert scope.parent == null;
                return 1.0;
            }
        }

        @Override
        protected void invoke(Invoke invoke) {
            double probability = nodeProbabilities.get(invoke.asNode());
            assert !Double.isNaN(probability);

            double relevance = (probability / currentProbability) * Math.min(1.0, parentRelevance);
            nodeRelevances.put(invoke.asNode(), relevance);
            assert !Double.isNaN(relevance);
        }

        private HashMap<FixedNode, Scope> computeScopesAndProbabilities() {
            HashMap<FixedNode, Scope> result = new HashMap<>();

            for (Scope scope : computeScopes()) {
                double lowestPathProbability = computeLowestPathProbability(scope);
                scope.probability = Math.max(EPSILON, lowestPathProbability);
                result.put(scope.start, scope);
            }

            return result;
        }

        private Scope[] computeScopes() {
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);

            List<Loop<Block>> loops = cfg.getLoops();
            HashMap<Loop<Block>, Scope> processedScopes = new HashMap<>();
            Scope[] result = new Scope[loops.size() + 1];
            Scope methodScope = new Scope(graph.start(), null);
            processedScopes.put(null, methodScope);

            result[0] = methodScope;
            for (int i = 0; i < loops.size(); i++) {
                result[i + 1] = createScope(loops.get(i), processedScopes);
            }

            return result;
        }

        private Scope createScope(Loop<Block> loop, HashMap<Loop<Block>, Scope> processedLoops) {
            Scope parent = processedLoops.get(loop.parent);
            if (parent == null) {
                parent = createScope(loop.parent, processedLoops);
            }
            Scope result = new Scope(loop.header.getBeginNode(), parent);
            processedLoops.put(loop, result);
            return result;
        }
    }

    private double computeLowestPathProbability(Scope scope) {
        FixedNode scopeStart = scope.start;
        ArrayList<FixedNode> pathBeginNodes = new ArrayList<>();
        pathBeginNodes.add(scopeStart);
        double minPathProbability = nodeProbabilities.get(scopeStart);
        boolean isLoopScope = scopeStart instanceof LoopBeginNode;

        do {
            Node current = pathBeginNodes.remove(pathBeginNodes.size() - 1);
            do {
                if (isLoopScope && current instanceof LoopExitNode && ((LoopBeginNode) scopeStart).loopExits().contains((LoopExitNode) current)) {
                    return minPathProbability;
                } else if (current instanceof LoopBeginNode && current != scopeStart) {
                    current = getMaxProbabilityLoopExit((LoopBeginNode) current, pathBeginNodes);
                    minPathProbability = getMinPathProbability((FixedNode) current, minPathProbability);
                } else if (current instanceof ControlSplitNode) {
                    current = getMaxProbabilitySux((ControlSplitNode) current, pathBeginNodes);
                    minPathProbability = getMinPathProbability((FixedNode) current, minPathProbability);
                } else {
                    assert current.successors().count() <= 1;
                    current = current.successors().first();
                }
            } while (current != null);
        } while (!pathBeginNodes.isEmpty());

        return minPathProbability;
    }

    private double getMinPathProbability(FixedNode current, double minPathProbability) {
        if (current != null && nodeProbabilities.get(current) < minPathProbability) {
            return nodeProbabilities.get(current);
        }
        return minPathProbability;
    }

    private static Node getMaxProbabilitySux(ControlSplitNode controlSplit, ArrayList<FixedNode> pathBeginNodes) {
        Node maxSux = null;
        double maxProbability = 0.0;
        int pathBeginCount = pathBeginNodes.size();

        for (Node sux : controlSplit.successors()) {
            double probability = controlSplit.probability((BeginNode) sux);
            if (probability > maxProbability) {
                maxProbability = probability;
                maxSux = sux;
                truncate(pathBeginNodes, pathBeginCount);
            } else if (probability == maxProbability) {
                pathBeginNodes.add((FixedNode) sux);
            }
        }

        return maxSux;
    }

    private Node getMaxProbabilityLoopExit(LoopBeginNode loopBegin, ArrayList<FixedNode> pathBeginNodes) {
        Node maxSux = null;
        double maxProbability = 0.0;
        int pathBeginCount = pathBeginNodes.size();

        for (LoopExitNode sux : loopBegin.loopExits()) {
            double probability = nodeProbabilities.get(sux);
            if (probability > maxProbability) {
                maxProbability = probability;
                maxSux = sux;
                truncate(pathBeginNodes, pathBeginCount);
            } else if (probability == maxProbability) {
                pathBeginNodes.add(sux);
            }
        }

        return maxSux;
    }

    private static void truncate(ArrayList<FixedNode> pathBeginNodes, int pathBeginCount) {
        for (int i = pathBeginNodes.size() - pathBeginCount; i > 0; i--) {
            pathBeginNodes.remove(pathBeginNodes.size() - 1);
        }
    }

    private static class Scope {

        public final FixedNode start;
        public final Scope parent;
        public double probability;

        public Scope(FixedNode start, Scope parent) {
            this.start = start;
            this.parent = parent;
        }
    }
}
