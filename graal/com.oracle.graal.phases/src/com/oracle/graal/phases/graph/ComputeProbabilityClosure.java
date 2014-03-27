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
package com.oracle.graal.phases.graph;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.util.*;

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
 */
public class ComputeProbabilityClosure {

    private static final double EPSILON = Double.MIN_NORMAL;

    private final StructuredGraph graph;
    private final NodesToDoubles nodeProbabilities;
    private final Set<LoopInfo> loopInfos;
    private final Map<MergeNode, Set<LoopInfo>> mergeLoops;

    public ComputeProbabilityClosure(StructuredGraph graph) {
        this.graph = graph;
        this.nodeProbabilities = new NodesToDoubles(graph.getNodeCount());
        this.loopInfos = new ArraySet<>();
        this.mergeLoops = new IdentityHashMap<>();
    }

    public NodesToDoubles apply() {
        // adjustControlSplitProbabilities();
        new PropagateProbability(graph.start()).apply();
        computeLoopFactors();
        new PropagateLoopFrequency(graph.start()).apply();
        // assert verifyProbabilities();
        return nodeProbabilities;
    }

    private void computeLoopFactors() {
        for (LoopInfo info : loopInfos) {
            double frequency = info.loopFrequency(nodeProbabilities);
            assert frequency != -1;
        }
    }

    public static class LoopInfo {

        public final LoopBeginNode loopBegin;

        public final NodeMap<Set<LoopInfo>> requires;

        private double loopFrequency = -1.0;
        public boolean ended = false;

        public LoopInfo(LoopBeginNode loopBegin) {
            this.loopBegin = loopBegin;
            this.requires = loopBegin.graph().createNodeMap();
        }

        public double loopFrequency(NodesToDoubles nodeProbabilities) {
            // loopFrequency is initialized with -1.0
            if (loopFrequency < 0.0 && ended) {
                double backEdgeProb = 0.0;
                for (LoopEndNode le : loopBegin.loopEnds()) {
                    double factor = 1;
                    Set<LoopInfo> requireds = requires.get(le);
                    for (LoopInfo required : requireds) {
                        double t = required.loopFrequency(nodeProbabilities);
                        if (t == -1) {
                            return -1;
                        }
                        factor = multiplySaturate(factor, t);
                    }
                    backEdgeProb += nodeProbabilities.get(le) * factor;
                }
                double entryProb = nodeProbabilities.get(loopBegin);
                double d = entryProb - backEdgeProb;
                if (d <= EPSILON) {
                    d = EPSILON;
                }
                loopFrequency = entryProb / d;
                loopBegin.setLoopFrequency(loopFrequency);
            }
            return loopFrequency;
        }
    }

    /**
     * Multiplies a and b and saturates the result to 1/{@link Double#MIN_NORMAL}.
     * 
     * @param a
     * @param b
     * @return a times b saturated to 1/{@link Double#MIN_NORMAL}
     */
    public static double multiplySaturate(double a, double b) {
        double r = a * b;
        if (r > 1 / Double.MIN_NORMAL) {
            return 1 / Double.MIN_NORMAL;
        }
        return r;
    }

    private class Probability extends MergeableState<Probability> implements Cloneable {

        public double probability;
        public Set<LoopInfo> loops;
        public LoopInfo loopInfo;

        public Probability(double probability, Set<LoopInfo> loops) {
            assert probability >= 0.0;
            this.probability = probability;
            this.loops = new ArraySet<>(4);
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
                Set<LoopInfo> intersection = new ArraySet<>(loops);
                for (Probability other : withStates) {
                    intersection.retainAll(other.loops);
                }
                for (LoopInfo info : loops) {
                    if (!intersection.contains(info)) {
                        double loopFrequency = info.loopFrequency(nodeProbabilities);
                        if (loopFrequency == -1) {
                            return false;
                        }
                        probability = multiplySaturate(probability, loopFrequency);
                        assert probability >= 0;
                    }
                }
                for (Probability other : withStates) {
                    double prob = other.probability;
                    for (LoopInfo info : other.loops) {
                        if (!intersection.contains(info)) {
                            double loopFrequency = info.loopFrequency(nodeProbabilities);
                            if (loopFrequency == -1) {
                                return false;
                            }
                            prob = multiplySaturate(prob, loopFrequency);
                            assert prob >= 0;
                        }
                    }
                    probability += prob;
                    assert probability >= 0;
                }
                loops = intersection;
                mergeLoops.put(merge, new ArraySet<>(intersection));
                probability = Math.max(0.0, probability);
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
        public void afterSplit(AbstractBeginNode node) {
            assert node.predecessor() != null;
            Node pred = node.predecessor();
            ControlSplitNode x = (ControlSplitNode) pred;
            double nodeProbability = x.probability(node);
            assert nodeProbability >= 0.0 : "Node " + x + " provided negative probability for begin " + node + ": " + nodeProbability;
            probability *= nodeProbability;
            assert probability >= 0.0;
        }
    }

    private class PropagateProbability extends PostOrderNodeIterator<Probability> {

        public PropagateProbability(FixedNode start) {
            super(start, new Probability(1d, null));
        }

        @Override
        protected void node(FixedNode node) {
            nodeProbabilities.put(node, state.probability);
        }
    }

    private class LoopCount extends MergeableState<LoopCount> implements Cloneable {

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
                    countProd = multiplySaturate(countProd, loop.loopFrequency(nodeProbabilities));
                }
                count = countProd;
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
            count = multiplySaturate(count, loopBegin.loopFrequency());
        }
    }

    private class PropagateLoopFrequency extends PostOrderNodeIterator<LoopCount> {

        public PropagateLoopFrequency(FixedNode start) {
            super(start, new LoopCount(1d));
        }

        @Override
        protected void node(FixedNode node) {
            nodeProbabilities.put(node, nodeProbabilities.get(node) * state.count);
        }

    }
}
