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
 * <li>{@link PropagateProbability} traverses the graph in post order (merges after their ends, ...) and keeps track of the "probability state".
 *   Whenever it encounters a {@link ControlSplitNode} it uses the split's probability information to divide the probability upon the successors.
 *   Whenever it encounters an {@link Invoke} it assumes that the exception edge is unlikely and propagates the whole probability to the normal successor.
 *   Whenever it encounters a {@link MergeNode} it sums up the probability of all predecessors.
 *   It also maintains a set of active loops (whose {@link LoopBeginNode} has been visited) and builds def/use information for step 2.</li>
 * <li></li>
 * <li>{@link PropagateLoopFrequency} propagates the loop frequencies and multiplies each {@link FixedNode}'s probability with its loop frequency.</li>
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

        if (GraalOptions.LoopFrequencyPropagationPolicy < 0) {
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);
            BitSet visitedBlocks = new BitSet(cfg.getBlocks().length);
            for (Loop loop : cfg.getLoops()) {
                if (loop.parent == null) {
                    correctLoopFrequencies(loop, 1, visitedBlocks);
                }
            }
        }

        new ComputeInliningRelevanceIterator(graph).apply();
    }

    private void correctLoopFrequencies(Loop loop, double parentFrequency, BitSet visitedBlocks) {
        LoopBeginNode loopBegin = ((LoopBeginNode) loop.header.getBeginNode());
        double frequency = parentFrequency * loopBegin.loopFrequency();
        if (frequency < 1) {
            frequency = 1;
        }
        for (Loop child : loop.children) {
            correctLoopFrequencies(child, frequency, visitedBlocks);
        }

        double factor = getCorrectionFactor(loopBegin.probability(), frequency);
        for (Block block : loop.blocks) {
            int blockId = block.getId();
            if (!visitedBlocks.get(blockId)) {
                visitedBlocks.set(blockId);

                FixedNode node = block.getBeginNode();
                while (node != block.getEndNode()) {
                    node.setProbability(node.probability() * factor);
                    node = ((FixedWithNextNode) node).next();
                }
                node.setProbability(node.probability() * factor);
            }
        }
    }

    private static double getCorrectionFactor(double probability, double frequency) {
        switch (GraalOptions.LoopFrequencyPropagationPolicy) {
            case -1:
                return 1 / frequency;
            case -2:
                return (1 / frequency) * (Math.log(Math.E + frequency) - 1);
            case -3:
                double originalProbability = probability / frequency;
                assert isRelativeProbability(originalProbability);
                return (1 / frequency) * Math.max(1, Math.pow(originalProbability, 1.5) * Math.log10(frequency));
            case -4:
                return 1 / probability;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
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

        private final FrequencyPropagationPolicy policy;

        public PropagateLoopFrequency(FixedNode start) {
            super(start, new LoopCount(1d));
            this.policy = createFrequencyPropagationPolicy();
        }

        @Override
        protected void node(FixedNode node) {
            node.setProbability(policy.compute(node.probability(), state.count));
        }

    }

    private static FrequencyPropagationPolicy createFrequencyPropagationPolicy() {
        switch (GraalOptions.LoopFrequencyPropagationPolicy) {
            case -4:
            case -3:
            case -2:
            case -1:
            case 0:
                return new FullFrequencyPropagation();
            case 1:
                return new NoFrequencyPropagation();
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private interface FrequencyPropagationPolicy {

        double compute(double probability, double frequency);
    }

    private static class FullFrequencyPropagation implements FrequencyPropagationPolicy {

        @Override
        public double compute(double probability, double frequency) {
            return probability * frequency;
        }
    }

    private static class NoFrequencyPropagation implements FrequencyPropagationPolicy {

        @Override
        public double compute(double probability, double frequency) {
            return probability;
        }
    }

    private static class ComputeInliningRelevanceIterator extends ScopedPostOrderNodeIterator {
        private final HashMap<FixedNode, Double> lowestPathProbabilities;
        private double currentProbability;

        public ComputeInliningRelevanceIterator(StructuredGraph graph) {
            super(graph);
            this.lowestPathProbabilities = computeLowestPathProbabilities(graph);
        }

        @Override
        protected void initializeScope() {
            currentProbability = lowestPathProbabilities.get(currentScope);
        }

        @Override
        protected void invoke(Invoke invoke) {
            assert !Double.isNaN(invoke.probability());
            invoke.setInliningRelevance(invoke.probability() / currentProbability);
        }

        private HashMap<FixedNode, Double> computeLowestPathProbabilities(StructuredGraph graph) {
            HashMap<FixedNode, Double> result = new HashMap<>();
            Deque<FixedNode> scopes = getScopes(graph);

            while (!scopes.isEmpty()) {
                FixedNode scopeBegin = scopes.pop();
                double probability = computeLowestPathProbability(scopeBegin);
                result.put(scopeBegin, probability);
            }

            return result;
        }

        private static double computeLowestPathProbability(FixedNode scopeStart) {
            double minPathProbability = scopeStart.probability();
            Node current = scopeStart;

            while (current != null) {
                if (current instanceof ControlSplitNode) {
                    ControlSplitNode controlSplit = (ControlSplitNode) current;
                    current = getMaxProbabilitySux(controlSplit);
                    if (((FixedNode) current).probability() < minPathProbability) {
                        minPathProbability = ((FixedNode) current).probability();
                    }
                } else {
                    assert current.successors().count() <= 1;
                    current = current.successors().first();
                }
            }

            return minPathProbability;
        }

        private static Node getMaxProbabilitySux(ControlSplitNode controlSplit) {
            Node maxSux = null;
            double maxProbability = 0.0;

            // TODO: process recursively if we have multiple successors with same probability
            for (Node sux: controlSplit.successors()) {
                double probability = controlSplit.probability((BeginNode) sux);
                if (probability > maxProbability) {
                    maxProbability = probability;
                    maxSux = sux;
                }
            }

            return maxSux;
        }
    }
}
