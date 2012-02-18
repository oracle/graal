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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;

public class ComputeProbabilityPhase extends Phase {
    private static final double EPSILON = 1d / Integer.MAX_VALUE;

    /*
     * The computation of absolute probabilities works in three steps:
     *
     * - The first step, "PropagateProbability", traverses the graph in post order (merges after their ends, ...) and keeps track of the "probability state".
     *   Whenever it encounters a ControlSplit it uses the split's probability information to divide the probability upon the successors.
     *   Whenever it encounters an Invoke it assumes that the exception edge is unlikely and propagates the whole probability to the normal successor.
     *   Whenever it encounters a Merge it sums up the probability of all predecessors.
     *   It also maintains a set of active loops (whose LoopBegin has been visited) and builds def/use information for the second step.
     *
     * - The third step propagates the loop frequencies and multiplies each FixedNode's probability with its loop frequency.
     *
     *   TODO: add exception probability information to Invokes
     */

    @Override
    protected void run(StructuredGraph graph) {
        new PropagateProbability(graph.start()).apply();
        Debug.dump(graph, "After PropagateProbability");
        computeLoopFactors();
        Debug.dump(graph, "After computeLoopFactors");
        new PropagateLoopFrequency(graph.start()).apply();
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
                double d = backEdgeProb;
                if (d < EPSILON) {
                    d = EPSILON;
                } else if (d > loopBegin.probability() - EPSILON) {
                    d = loopBegin.probability() - EPSILON;
                }
                loopFrequency = loopBegin.probability() / (loopBegin.probability() - d);
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
        public boolean merge(MergeNode merge, Collection<Probability> withStates) {
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
        public void loopEnds(LoopBeginNode loopBegin, Collection<Probability> loopEndStates) {
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
        public void afterSplit(FixedNode node) {
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
                double sum = 0;
                for (int i = 0; i < x.blockSuccessorCount(); i++) {
                    if (x.blockSuccessor(i) == node) {
                        sum += x.probability(i);
                    }
                }
                probability *= sum;
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
        public boolean merge(MergeNode merge, Collection<LoopCount> withStates) {
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
        public void loopEnds(LoopBeginNode loopBegin, Collection<LoopCount> loopEndStates) {
            // nothing to do...
        }

        @Override
        public void afterSplit(FixedNode node) {
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

        private FrequencyPropagationPolicy createFrequencyPropagationPolicy() {
            switch (GraalOptions.LoopFrequencyPropagationPolicy) {
                case 0: return new FullFrequencyPropagation();
                case 1: return new NoFrequencyPropagation();
                case 2: return new LogarithmicFrequencyPropagation();
                default: throw GraalInternalError.shouldNotReachHere();
            }
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

    private static class LogarithmicFrequencyPropagation implements FrequencyPropagationPolicy {
        @Override
        public double compute(double probability, double frequency) {
            double result = Math.pow(probability, 1.5) * Math.log(frequency);
            return Math.max(probability, result);
        }
    }
}
