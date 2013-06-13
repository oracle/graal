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
                scope.probability = Math.max(EPSILON, nodeProbabilities.get(scope.start));
                result.put(scope.start, scope);
            }

            return result;
        }

        private Scope[] computeScopes() {
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);

            Loop[] loops = cfg.getLoops();
            HashMap<Loop, Scope> processedScopes = new HashMap<>();
            Scope[] result = new Scope[loops.length + 1];
            Scope methodScope = new Scope(graph.start(), null);
            processedScopes.put(null, methodScope);

            result[0] = methodScope;
            for (int i = 0; i < loops.length; i++) {
                result[i + 1] = createScope(loops[i], processedScopes);
            }

            return result;
        }

        private Scope createScope(Loop loop, HashMap<Loop, Scope> processedLoops) {
            Scope parent = processedLoops.get(loop.parent);
            if (parent == null) {
                parent = createScope(loop.parent, processedLoops);
            }
            Scope result = new Scope(loop.loopBegin(), parent);
            processedLoops.put(loop, result);
            return result;
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
