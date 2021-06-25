/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.ProfileData.LoopFrequencyData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;

public final class ComputeLoopFrequenciesClosure extends ReentrantNodeIterator.NodeIteratorClosure<BranchProbabilityData> {

    private static final ComputeLoopFrequenciesClosure INSTANCE = new ComputeLoopFrequenciesClosure();

    private static final BranchProbabilityData ZERO = BranchProbabilityData.create(0.0, ProfileSource.UNKNOWN);
    private static final BranchProbabilityData ONE = BranchProbabilityData.create(1.0, ProfileSource.UNKNOWN);

    private ComputeLoopFrequenciesClosure() {
        // nothing to do
    }

    private static BranchProbabilityData add(BranchProbabilityData x, BranchProbabilityData y) {
        double p = x.getDesignatedSuccessorProbability() + y.getDesignatedSuccessorProbability();
        return BranchProbabilityData.create(p, x.getProfileSource().combine(y.getProfileSource()));
    }

    private static BranchProbabilityData scale(BranchProbabilityData x, double scaleFactor) {
        return scaleAndCombine(x, scaleFactor, x.getProfileSource());
    }

    private static BranchProbabilityData scaleAndCombine(BranchProbabilityData x, double scaleFactor, ProfileSource otherSource) {
        double p = multiplyRelativeFrequencies(x.getDesignatedSuccessorProbability(), scaleFactor);
        return BranchProbabilityData.create(p, x.getProfileSource().combine(otherSource));
    }

    @Override
    protected BranchProbabilityData processNode(FixedNode node, BranchProbabilityData currentState) {
        // normal nodes never change the probability of a path
        return currentState;
    }

    @Override
    protected BranchProbabilityData merge(AbstractMergeNode merge, List<BranchProbabilityData> states) {
        // a merge has the sum of all predecessor probabilities
        BranchProbabilityData result = ZERO;
        for (BranchProbabilityData s : states) {
            result = add(result, s);
        }
        return result;
    }

    @Override
    protected BranchProbabilityData afterSplit(AbstractBeginNode node, BranchProbabilityData oldState) {
        // a control split splits up the probability
        ControlSplitNode split = (ControlSplitNode) node.predecessor();
        return scaleAndCombine(oldState, split.probability(node), split.getProfileData().getProfileSource());
    }

    @Override
    protected EconomicMap<LoopExitNode, BranchProbabilityData> processLoop(LoopBeginNode loop, BranchProbabilityData initialState) {
        EconomicMap<LoopExitNode, BranchProbabilityData> exitStates = ReentrantNodeIterator.processLoop(this, loop, ONE).exitStates;

        BranchProbabilityData exitState = ZERO;
        for (BranchProbabilityData e : exitStates.getValues()) {
            exitState = add(exitState, e);
        }
        double exitRelativeFrequency = exitState.getDesignatedSuccessorProbability();
        exitRelativeFrequency = Math.min(1.0, exitRelativeFrequency);
        exitRelativeFrequency = Math.max(ControlFlowGraph.MIN_RELATIVE_FREQUENCY, exitRelativeFrequency);
        double loopFrequency = 1.0 / exitRelativeFrequency;
        loop.setLoopFrequency(LoopFrequencyData.create(loopFrequency, exitState.getProfileSource()));

        double adjustmentFactor = initialState.getDesignatedSuccessorProbability() * loopFrequency;
        exitStates.replaceAll((exitNode, state) -> scale(state, adjustmentFactor));

        return exitStates;
    }

    /**
     * Computes the frequencies of all loops in the given graph. This is done by performing a
     * reverse postorder iteration and computing the probability of all fixed nodes. The combined
     * probability of all exits of a loop can be used to compute the loop's expected frequency.
     */
    public static void compute(StructuredGraph graph) {
        if (graph.hasLoops()) {
            ReentrantNodeIterator.apply(INSTANCE, graph.start(), ONE);
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
