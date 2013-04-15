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

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;

/**
 * Computes probabilities for nodes in a graph.
 * <p>
 * The computation of absolute probabilities works in three steps:
 * <ol>
 * <li>{@link PropagateProbability} traverses the graph in post order (merges after their ends, ...)
 * and keeps track of the "probability state". Whenever it encounters a {@link ControlSplitNode} it
 * uses the split's probability information to divide the probability upon the successors. Whenever
 * it encounters an {@link Invoke} it assumes that the exception edge is unlikely and propagates the
 * whole probability to the normal successor. Whenever it encounters a {@link MergeNode} it sums up
 * the probability of all predecessors. It also maintains a set of active loops (whose
 * {@link LoopBeginNode} has been visited) and builds def/use information for step 2.</li>
 * <li></li>
 * <li>{@link PropagateLoopFrequency} propagates the loop frequencies and multiplies each
 * {@link FixedNode}'s probability with its loop frequency.</li>
 * </ol>
 * TODO: add exception probability information to Invokes
 */
public class ComputeProbabilityPhase extends Phase {

    private static final double EPSILON = 1d / Integer.MAX_VALUE;

    @Override
    protected void run(StructuredGraph graph) {
        new PropagateProbability(graph.start()).apply();
        Debug.dump(graph, "After PropagateProbability");
        computeLoopFactors();
        Debug.dump(graph, "After computeLoopFactors");
        new PropagateLoopFrequency(graph.start()).apply();
        new ComputeInliningRelevanceIterator(graph).apply();
    }

    private void computeLoopFactors() {
        for (LoopInfo info : loopInfos) {
            double frequency = info.loopFrequency();
            assert frequency != -1;
        }
    }

    private static boolean isRelativeProbability(double prob) {
        // 1.01 to allow for some rounding errors
        return prob >= 0 && prob <= 1.01;
    }

    public static class LoopInfo {

        public final LoopBeginNode loopBegin;

        public final NodeMap<Set<LoopInfo>> requires;

        private double loopFrequency = -1;
        public boolean ended = false;

        public LoopInfo(LoopBeginNode loopBegin) {
            this.loopBegin = loopBegin;
            this.requires = loopBegin.graph().createNodeMap();
        }

        public double loopFrequency() {
            if (loopFrequency == -1 && ended) {
                double backEdgeProb = 0.0;
                for (LoopEndNode le : loopBegin.loopEnds()) {
                    double factor = 1;
                    Set<LoopInfo> requireds = requires.get(le);
                    for (LoopInfo required : requireds) {
                        double t = required.loopFrequency();
                        if (t == -1) {
                            return -1;
                        }
                        factor *= t;
                    }
                    backEdgeProb += le.probability() * factor;
                }
                double d = loopBegin.probability() - backEdgeProb;
                if (d < EPSILON) {
                    d = EPSILON;
                }
                loopFrequency = loopBegin.probability() / d;
                loopBegin.setLoopFrequency(loopFrequency);
            }
            return loopFrequency;
        }
    }

    public Set<LoopInfo> loopInfos = new HashSet<>();
    public Map<MergeNode, Set<LoopInfo>> mergeLoops = new IdentityHashMap<>();

    private class Probability implements MergeableState<Probability> {

        public double probability;
        public HashSet<LoopInfo> loops;
        public LoopInfo loopInfo;

        public Probability(double probability, HashSet<LoopInfo> loops) {
            this.probability = probability;
            this.loops = new HashSet<>(4);
            if (loops != null) {
                this.loops.addAll(loops);
            }
        }

        @Override
        public Probability clone() {
            return new Probability(probability, loops);
        }

        @Override
        public boolean merge(MergeNode merge, List<Probability> withStates) {
            if (merge.forwardEndCount() > 1) {
                HashSet<LoopInfo> intersection = new HashSet<>(loops);
                for (Probability other : withStates) {
                    intersection.retainAll(other.loops);
                }
                for (LoopInfo info : loops) {
                    if (!intersection.contains(info)) {
                        double loopFrequency = info.loopFrequency();
                        if (loopFrequency == -1) {
                            return false;
                        }
                        probability *= loopFrequency;
                    }
                }
                for (Probability other : withStates) {
                    double prob = other.probability;
                    for (LoopInfo info : other.loops) {
                        if (!intersection.contains(info)) {
                            double loopFrequency = info.loopFrequency();
                            if (loopFrequency == -1) {
                                return false;
                            }
                            prob *= loopFrequency;
                        }
                    }
                    probability += prob;
                }
                loops = intersection;
                mergeLoops.put(merge, new HashSet<>(intersection));
                assert isRelativeProbability(probability) : probability;
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
            loopInfo = new LoopInfo(loopBegin);
            loopInfos.add(loopInfo);
            loops.add(loopInfo);
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<Probability> loopEndStates) {
            assert loopInfo != null;
            List<LoopEndNode> loopEnds = loopBegin.orderedLoopEnds();
            int i = 0;
            for (Probability proba : loopEndStates) {
                LoopEndNode loopEnd = loopEnds.get(i++);
                Set<LoopInfo> requires = loopInfo.requires.get(loopEnd);
                if (requires == null) {
                    requires = new HashSet<>();
                    loopInfo.requires.set(loopEnd, requires);
                }
                for (LoopInfo innerLoop : proba.loops) {
                    if (innerLoop != loopInfo && !this.loops.contains(innerLoop)) {
                        requires.add(innerLoop);
                    }
                }
            }
            loopInfo.ended = true;
        }

        @Override
        public void afterSplit(BeginNode node) {
            assert node.predecessor() != null;
            Node pred = node.predecessor();
            if (pred instanceof Invoke) {
                Invoke x = (Invoke) pred;
                if (x.next() != node) {
                    probability = 0;
                }
            } else {
                assert pred instanceof ControlSplitNode;
                ControlSplitNode x = (ControlSplitNode) pred;
                probability *= x.probability(node);
            }
        }
    }

    private class PropagateProbability extends PostOrderNodeIterator<Probability> {

        public PropagateProbability(FixedNode start) {
            super(start, new Probability(1d, null));
        }

        @Override
        protected void node(FixedNode node) {
            node.setProbability(state.probability);
        }
    }

    private class LoopCount implements MergeableState<LoopCount> {

        public double count;

        public LoopCount(double count) {
            this.count = count;
        }

        @Override
        public LoopCount clone() {
            return new LoopCount(count);
        }

        @Override
        public boolean merge(MergeNode merge, List<LoopCount> withStates) {
            assert merge.forwardEndCount() == withStates.size() + 1;
            if (merge.forwardEndCount() > 1) {
                Set<LoopInfo> loops = mergeLoops.get(merge);
                assert loops != null;
                double countProd = 1;
                for (LoopInfo loop : loops) {
                    countProd *= loop.loopFrequency();
                }
                count = countProd;
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
            count *= loopBegin.loopFrequency();
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<LoopCount> loopEndStates) {
            // nothing to do...
        }

        @Override
        public void afterSplit(BeginNode node) {
            // nothing to do...
        }
    }

    private class PropagateLoopFrequency extends PostOrderNodeIterator<LoopCount> {

        public PropagateLoopFrequency(FixedNode start) {
            super(start, new LoopCount(1d));
        }

        @Override
        protected void node(FixedNode node) {
            node.setProbability(node.probability() * state.count);
        }

    }

    private static class ComputeInliningRelevanceIterator extends ScopedPostOrderNodeIterator {

        private final HashMap<FixedNode, Scope> scopes;
        private double currentProbability;
        private double parentRelevance;

        public ComputeInliningRelevanceIterator(StructuredGraph graph) {
            super(graph);
            this.scopes = computeLowestPathProbabilities(computeScopeInformation(graph));
        }

        @Override
        protected void initializeScope() {
            Scope scope = scopes.get(currentScopeStart);
            parentRelevance = getParentScopeRelevance(scope);
            currentProbability = scope.minPathProbability;
        }

        private static double getParentScopeRelevance(Scope scope) {
            if (scope.start instanceof LoopBeginNode) {
                assert scope.parent != null;
                double parentProbability = 0;
                for (EndNode end : ((LoopBeginNode) scope.start).forwardEnds()) {
                    parentProbability += end.probability();
                }
                return parentProbability / scope.parent.minPathProbability;
            } else {
                assert scope.parent == null;
                return 1.0;
            }
        }

        @Override
        protected void invoke(Invoke invoke) {
            assert !Double.isNaN(invoke.probability());
            invoke.setInliningRelevance((invoke.probability() / currentProbability) * Math.min(1.0, parentRelevance));
            assert !Double.isNaN(invoke.inliningRelevance());
        }

        private static Scope[] computeScopeInformation(StructuredGraph graph) {
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);

            Loop[] loops = cfg.getLoops();
            HashMap<Loop, Scope> processedScopes = new HashMap<>();
            Scope[] scopes = new Scope[loops.length + 1];
            Scope methodScope = new Scope(graph.start(), null);
            processedScopes.put(null, methodScope);

            scopes[0] = methodScope;
            for (int i = 0; i < loops.length; i++) {
                scopes[i + 1] = createScope(loops[i], processedScopes);
            }

            return scopes;
        }

        private static Scope createScope(Loop loop, HashMap<Loop, Scope> processedLoops) {
            Scope parent = processedLoops.get(loop.parent);
            if (parent == null) {
                parent = createScope(loop.parent, processedLoops);
            }
            Scope result = new Scope(loop.loopBegin(), parent);
            processedLoops.put(loop, result);
            return result;
        }

        private static HashMap<FixedNode, Scope> computeLowestPathProbabilities(Scope[] scopes) {
            HashMap<FixedNode, Scope> result = new HashMap<>();

            for (Scope scope : scopes) {
                double lowestPathProbability = computeLowestPathProbability(scope);
                scope.minPathProbability = Math.max(EPSILON, lowestPathProbability);
                result.put(scope.start, scope);
            }

            return result;
        }

        private static double computeLowestPathProbability(Scope scope) {
            FixedNode scopeStart = scope.start;
            ArrayList<FixedNode> pathBeginNodes = new ArrayList<>();
            pathBeginNodes.add(scopeStart);
            double minPathProbability = scopeStart.probability();
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

        private static double getMinPathProbability(FixedNode current, double minPathProbability) {
            if (current != null && current.probability() < minPathProbability) {
                return current.probability();
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

        private static Node getMaxProbabilityLoopExit(LoopBeginNode loopBegin, ArrayList<FixedNode> pathBeginNodes) {
            Node maxSux = null;
            double maxProbability = 0.0;
            int pathBeginCount = pathBeginNodes.size();

            for (LoopExitNode sux : loopBegin.loopExits()) {
                double probability = sux.probability();
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

        public static void truncate(ArrayList<FixedNode> pathBeginNodes, int pathBeginCount) {
            for (int i = pathBeginNodes.size() - pathBeginCount; i > 0; i--) {
                pathBeginNodes.remove(pathBeginNodes.size() - 1);
            }
        }
    }

    private static class Scope {

        public final FixedNode start;
        public final Scope parent;
        public double minPathProbability;

        public Scope(FixedNode start, Scope parent) {
            this.start = start;
            this.parent = parent;
        }
    }
}
