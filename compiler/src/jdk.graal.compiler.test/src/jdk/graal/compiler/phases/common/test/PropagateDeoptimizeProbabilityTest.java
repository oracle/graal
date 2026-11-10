/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ProfileData.SwitchProbabilityData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchorNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.phases.common.PropagateDeoptimizeProbabilityPhase;

public class PropagateDeoptimizeProbabilityTest extends GraalCompilerTest {

    public static int nestedDeopt(boolean outerCondition, boolean innerCondition) {
        if (outerCondition) {
            // Use different deoptimization actions to keep the inner branch from collapsing.
            if (innerCondition) {
                GraalDirectives.deoptimize();
            } else {
                GraalDirectives.deoptimizeAndInvalidate();
            }
        }
        return 42;
    }

    /** Both arms of an inner if deoptimize, so its enclosing arm must have zero probability. */
    @Test
    public void testNestedDeoptProbability() {
        StructuredGraph graph = parseEager("nestedDeopt", AllowAssumptions.NO);
        Assert.assertEquals(2, graph.getNodes(IfNode.TYPE).count());
        IfNode outer = (IfNode) graph.start().next();
        AbstractBeginNode deoptArm = outer.trueSuccessor().next() instanceof IfNode ? outer.trueSuccessor() : outer.falseSuccessor();
        outer.setProbability(deoptArm, BranchProbabilityData.injected(0.25));
        applyPhase(graph);
        Assert.assertEquals(0.0, outer.probability(deoptArm), 0.0);
    }

    public static int noDeopt(boolean condition) {
        if (condition) {
            GraalDirectives.controlFlowAnchor();
            return 1;
        }
        return 2;
    }

    /** The phase requires fixed deoptimizations and becomes applicable at that graph stage. */
    @Test
    public void testPhaseApplicability() {
        StructuredGraph graph = parseEager("noDeopt", AllowAssumptions.NO);
        PropagateDeoptimizeProbabilityPhase phase = new PropagateDeoptimizeProbabilityPhase();
        Assert.assertTrue(phase.notApplicableTo(graph.getGraphState()).isPresent());
        graph.getGraphState().setGuardsStage(GuardsStage.FIXED_DEOPTS);
        Assert.assertFalse(phase.notApplicableTo(graph.getGraphState()).isPresent());
    }

    /** A graph without deoptimizations retains its branch probabilities. */
    @Test
    public void testNoDeoptProbability() {
        StructuredGraph graph = parseEager("noDeopt", AllowAssumptions.NO);
        IfNode branch = graph.getNodes(IfNode.TYPE).first();
        branch.setProbability(branch.trueSuccessor(), BranchProbabilityData.injected(0.25));
        applyPhase(graph);
        Assert.assertEquals(0.25, branch.probability(branch.trueSuccessor()), 0.0);
    }

    public static void unconditionalDeopt() {
        GraalDirectives.deoptimize();
    }

    /** An unconditional deoptimization reaches the start node without any split to update. */
    @Test
    public void testUnconditionalDeopt() {
        StructuredGraph graph = parseEager("unconditionalDeopt", AllowAssumptions.NO);
        Assert.assertEquals(1, graph.getNodes(AbstractDeoptimizeNode.TYPE).count());
        applyPhase(graph);
        Assert.assertTrue(graph.start().next() instanceof AbstractDeoptimizeNode);
    }

    public static int directDeopt(boolean condition) {
        if (condition) {
            GraalDirectives.deoptimize();
        }
        return 42;
    }

    /** A single deoptimizing arm becomes cold; an already-zero profile is left unchanged. */
    @Test
    public void testDirectDeoptProbability() {
        StructuredGraph graph = parseEager("directDeopt", AllowAssumptions.NO);
        IfNode branch = graph.getNodes(IfNode.TYPE).first();
        AbstractBeginNode deoptArm = AbstractBeginNode.prevBegin(graph.getNodes(AbstractDeoptimizeNode.TYPE).first());
        branch.setProbability(deoptArm, BranchProbabilityData.injected(0.25));
        applyPhase(graph);
        Assert.assertEquals(0.0, branch.probability(deoptArm), 0.0);
        branch.setProbability(deoptArm, BranchProbabilityData.injected(0.0));
        BranchProbabilityData profile = branch.getProfileData();
        applyPhase(graph);
        Assert.assertSame(profile, branch.getProfileData());
    }

    /** An intervening fixed node and begin must not stop traversal to the deoptimizing arm. */
    @Test
    public void testInterveningFixedNode() {
        StructuredGraph graph = parseEager("directDeopt", AllowAssumptions.NO);
        IfNode branch = graph.getNodes(IfNode.TYPE).first();
        AbstractDeoptimizeNode deopt = graph.getNodes(AbstractDeoptimizeNode.TYPE).first();
        AbstractBeginNode deoptArm = AbstractBeginNode.prevBegin(deopt);
        graph.addBeforeFixed(deopt, graph.add(new ControlFlowAnchorNode()));
        graph.addBeforeFixed(deopt, graph.add(new BeginNode()));
        branch.setProbability(deoptArm, BranchProbabilityData.injected(0.25));
        applyPhase(graph);
        Assert.assertEquals(0.0, branch.probability(deoptArm), 0.0);
    }

    public static int mergedDeopt(boolean outerCondition, boolean innerCondition) {
        if (outerCondition) {
            if (innerCondition) {
                GraalDirectives.controlFlowAnchor();
            }
            GraalDirectives.deoptimize();
        }
        return 42;
    }

    /** A deoptimization after a merge, with or without an extra begin, makes its incoming paths cold. */
    @Test
    public void testMergedDeoptProbability() {
        for (boolean insertBegin : new boolean[]{false, true}) {
            StructuredGraph graph = parseEager("mergedDeopt", AllowAssumptions.NO);
            Assert.assertEquals(1, graph.getNodes(MergeNode.TYPE).count());
            if (insertBegin) {
                graph.addAfterFixed(graph.getNodes(MergeNode.TYPE).first(), graph.add(new BeginNode()));
            }
            IfNode outer = (IfNode) graph.start().next();
            AbstractBeginNode deoptArm = outer.trueSuccessor().next() instanceof IfNode ? outer.trueSuccessor() : outer.falseSuccessor();
            outer.setProbability(deoptArm, BranchProbabilityData.injected(0.25));
            applyPhase(graph);
            Assert.assertEquals(0.0, outer.probability(deoptArm), 0.0);
        }
    }

    public static int partialSwitchDeopt(int value) {
        switch (value) {
            case 0:
                GraalDirectives.deoptimize();
                return 0;
            case 1:
                return 1;
            default:
                return 2;
        }
    }

    /** Only the deoptimizing switch successor becomes cold; normal successors remain reachable. */
    @Test
    public void testPartialSwitchDeoptProbability() {
        StructuredGraph graph = parseEager("partialSwitchDeopt", AllowAssumptions.NO);
        IntegerSwitchNode split = graph.getNodes().filter(IntegerSwitchNode.class).first();
        Assert.assertEquals(3, split.getSuccessorCount());
        split.setProfileData(SwitchProbabilityData.create(new double[]{0.25, 0.25, 0.5}, ProfileSource.INJECTED));
        applyPhase(graph);
        Assert.assertEquals(0.0, split.probability(split.successorAtKey(0)), 0.0);
        Assert.assertTrue(split.probability(split.successorAtKey(1)) > 0.0);
        Assert.assertTrue(split.probability(split.defaultSuccessor()) > 0.0);
    }

    public static int allSwitchDeopt(boolean outerCondition, int value) {
        if (outerCondition) {
            switch (value) {
                case 0:
                    GraalDirectives.deoptimize();
                    break;
                case 1:
                    GraalDirectives.deoptimizeAndInvalidate();
                    break;
                default:
                    GraalDirectives.deoptimizeAndRecompile();
                    break;
            }
        }
        return 42;
    }

    /** All switch successors deoptimize, so propagation continues to the enclosing if arm. */
    @Test
    public void testAllSwitchDeoptProbability() {
        StructuredGraph graph = parseEager("allSwitchDeopt", AllowAssumptions.NO);
        IntegerSwitchNode split = graph.getNodes().filter(IntegerSwitchNode.class).first();
        Assert.assertEquals(3, split.getSuccessorCount());
        IfNode outer = graph.getNodes(IfNode.TYPE).first();
        AbstractBeginNode deoptArm = AbstractBeginNode.prevBegin(split);
        outer.setProbability(deoptArm, BranchProbabilityData.injected(0.25));
        SwitchProbabilityData profile = split.getProfileData();
        applyPhase(graph);
        Assert.assertEquals(0.0, outer.probability(deoptArm), 0.0);
        Assert.assertSame(profile, split.getProfileData());
    }

    private void applyPhase(StructuredGraph graph) {
        graph.getGraphState().setGuardsStage(GuardsStage.FIXED_DEOPTS);
        new PropagateDeoptimizeProbabilityPhase().apply(graph, getProviders());
    }
}
