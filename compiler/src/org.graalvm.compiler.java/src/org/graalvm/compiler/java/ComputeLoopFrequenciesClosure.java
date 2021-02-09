/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.java;

import static org.graalvm.compiler.nodes.cfg.ControlFlowGraph.multiplyRelativeFrequencies;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.ControlSplitNode.ProfileSource;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.java.ComputeLoopFrequenciesClosure.State;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;

public final class ComputeLoopFrequenciesClosure extends ReentrantNodeIterator.NodeIteratorClosure<State> {

    private static final ComputeLoopFrequenciesClosure INSTANCE = new ComputeLoopFrequenciesClosure();

    private ComputeLoopFrequenciesClosure() {
        // nothing to do
    }

    protected static class State {
        protected final double probability;
        protected final ProfileSource profileSource;

        static State ZERO = new State(0.0, ProfileSource.UNKNOWN);
        static State ONE = new State(1.0, ProfileSource.UNKNOWN);

        State(double probability, ProfileSource profileSource) {
            this.probability = probability;
            this.profileSource = profileSource;
        }

        State add(State other) {
            return new State(this.probability + other.probability, this.profileSource.combine(other.profileSource));
        }

        State scale(double otherProbability, ProfileSource otherprofileSource) {
            return new State(this.probability * otherProbability, this.profileSource.combine(otherprofileSource));
        }

        @Override
        public String toString() {
            return "[" + probability + ", " + profileSource + "]";
        }
    }

    @Override
    protected State processNode(FixedNode node, State currentState) {
        // normal nodes never change the probability of a path
        return currentState;
    }

    @Override
    protected State merge(AbstractMergeNode merge, List<State> states) {
        // a merge has the sum of all predecessor probabilities
        State result = State.ZERO;
        for (State s : states) {
            result = result.add(s);
        }
        return result;
    }

    @Override
    protected State afterSplit(AbstractBeginNode node, State oldState) {
        // a control split splits up the probability
        ControlSplitNode split = (ControlSplitNode) node.predecessor();
        return oldState.scale(split.probability(node), split.getProfileSource());
    }

    @Override
    protected EconomicMap<LoopExitNode, State> processLoop(LoopBeginNode loop, State initialState) {
        EconomicMap<LoopExitNode, State> exitStates = ReentrantNodeIterator.processLoop(this, loop, State.ONE).exitStates;

        State exitState = State.ZERO;
        for (State e : exitStates.getValues()) {
            exitState = exitState.add(e);
        }
        double exitRelativeFrequency = exitState.probability;
        exitRelativeFrequency = Math.min(1.0, exitRelativeFrequency);
        exitRelativeFrequency = Math.max(ControlFlowGraph.MIN_RELATIVE_FREQUENCY, exitRelativeFrequency);
        double loopFrequency = 1.0 / exitRelativeFrequency;
        loop.setLoopFrequency(loopFrequency, exitState.profileSource);

        double adjustmentFactor = initialState.probability * loopFrequency;
        exitStates.replaceAll((exitNode, state) -> new State(multiplyRelativeFrequencies(state.probability, adjustmentFactor), state.profileSource));

        return exitStates;
    }

    /**
     * Computes the frequencies of all loops in the given graph. This is done by performing a
     * reverse postorder iteration and computing the probability of all fixed nodes. The combined
     * probability of all exits of a loop can be used to compute the loop's expected frequency.
     */
    public static void compute(StructuredGraph graph) {
        if (graph.hasLoops()) {
            ReentrantNodeIterator.apply(INSTANCE, graph.start(), State.ONE);
        }
    }

    public static class ComputeLoopFrequencyPhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            compute(graph);
        }
    }

    public static final ComputeLoopFrequencyPhase PHASE_INSTANCE = new ComputeLoopFrequencyPhase();

}
