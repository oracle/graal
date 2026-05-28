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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.GuardedValueNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerLowerThanNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.debug.SideEffectNode;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil;
import jdk.graal.compiler.phases.common.SafeStampInputSearch;
import jdk.graal.compiler.phases.util.GraphOrder;

/**
 * Tests that a condition proven from one guard does not also consume a second {@link PiNode} stamp
 * whose guard is not represented in the rewritten dependency. Current Java graph producers do not
 * naturally create the problematic integer-Pi shapes, so the white-box tests inject those shapes
 * directly and check the local ConditionalElimination invariant that would be required if such a
 * producer is added later.
 */
public class ConditionalEliminationPiStampDependencyTest extends ConditionalEliminationTestBase {

    @NodeInfo
    private static final class DefaultFoldStampUnaryNode extends UnaryNode {
        public static final NodeClass<DefaultFoldStampUnaryNode> TYPE = NodeClass.create(DefaultFoldStampUnaryNode.class);

        protected DefaultFoldStampUnaryNode(ValueNode value) {
            super(TYPE, value.stamp(NodeView.DEFAULT), value);
        }

        @Override
        public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
            return this;
        }
    }

    /**
     * Produces a bounded {@code index} value and a later {@code index < limit} guard. The white-box
     * test replaces {@code limit} in that last comparison with a synthetic Pi carrying a tight lower
     * bound, which would make the comparison look constant true if ConditionalElimination consumed
     * the Pi stamp without also depending on the Pi guard.
     */
    public static int snippet(int index, int limit) {
        if (index < 0) {
            GraalDirectives.deoptimize();
        }
        if (index > 50) {
            GraalDirectives.deoptimize();
        }
        if (index < limit) {
            return index;
        }
        GraalDirectives.deoptimize();
        return -1;
    }

    /**
     * Mirrors {@link #snippet(int, int)} so the white-box test can exercise the binary proof path
     * where the {@link PiNode} is the left operand and the known value is the right operand.
     */
    public static int mirroredSnippet(int index, int limit) {
        if (index < 0) {
            GraalDirectives.deoptimize();
        }
        if (index > 50) {
            GraalDirectives.deoptimize();
        }
        if (limit < index) {
            return index;
        }
        GraalDirectives.deoptimize();
        return -1;
    }

    /**
     * Keeps the {@code index < limit} test as an {@link IfNode} so a white-box test can attach a
     * guarded value to its surviving successor.
     */
    public static int branchGuardedUsageSnippet(int index, int limit, boolean flag) {
        if (index < 0) {
            GraalDirectives.deoptimize();
        }
        if (index > 50) {
            GraalDirectives.deoptimize();
        }
        if (index < limit) {
            GraalDirectives.sideEffect(index);
            return flag ? index + 1 : index + 2;
        }
        return index - 1;
    }

    /**
     * Produces a value whose range comes from a char array load and zero extension rather than a
     * guard-created {@link PiNode}. ConditionalElimination may use that conversion-implied range
     * for the non-info operand because the dependency is on the conversion itself, not on a hidden
     * guard.
     */
    public static Object charArrayLoadSnippet(char[] indices, int i, Object[] y) {
        if (i < 0) {
            GraalDirectives.deoptimize();
        }
        if (i >= indices.length) {
            GraalDirectives.deoptimize();
        }
        if (y.length <= Character.MAX_VALUE) {
            GraalDirectives.deoptimize();
        }
        return y[indices[i]];
    }

    @Test
    public void testFunctionalSnippet() {
        test("snippet", 0, 1);
        test("snippet", 7, 100);
        test("snippet", 50, 51);
        test("mirroredSnippet", 7, 0);
        test("mirroredSnippet", 50, 49);
        test("charArrayLoadSnippet", new char[]{1}, 0, new Object[Character.MAX_VALUE + 1]);
    }

    @Test
    public void testOtherOperandPiStampDoesNotProveGuard() {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        prepareGraph(graph, canonicalizer, getProviders(), false);

        ValueNode index = graph.getParameter(0);
        ValueNode limit = graph.getParameter(1);
        FixedGuardNode targetGuard = null;
        FixedGuardNode piGuard = null;

        for (FixedGuardNode guard : graph.getNodes().filter(FixedGuardNode.class)) {
            if (guard.condition() instanceof IntegerLessThanNode lessThan && lessThan.getX() == index && lessThan.getY() == limit && !guard.isNegated()) {
                targetGuard = guard;
            } else if (piGuard == null) {
                piGuard = guard;
            }
        }

        Assert.assertNotNull("Expected a fixed guard for index < limit.", targetGuard);
        Assert.assertNotNull("Expected a preceding guard to anchor the synthetic PiNode.", piGuard);

        IntegerLessThanNode targetCondition = (IntegerLessThanNode) targetGuard.condition();
        ValueNode limitPi = graph.addOrUnique(PiNode.create(limit, IntegerStamp.create(32, 100, Integer.MAX_VALUE), piGuard.asNode()));
        targetCondition.replaceFirstInput(limit, limitPi);
        Assert.assertSame(limitPi, targetCondition.getY());

        new ConditionalEliminationPhase(canonicalizer, true).apply(graph, getDefaultHighTierContext());

        Assert.assertTrue("Conditional elimination must not prove index < limit from the other operand's Pi-derived stamp.", targetGuard.isAlive());
        GraphOrder.assertSchedulableGraph(graph);
    }

    @Test
    public void testOtherOperandPiStampDoesNotProveMirroredGuard() {
        StructuredGraph graph = parseEager("mirroredSnippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        prepareGraph(graph, canonicalizer, getProviders(), false);

        ValueNode index = graph.getParameter(0);
        ValueNode limit = graph.getParameter(1);
        FixedGuardNode targetGuard = null;
        FixedGuardNode piGuard = null;

        for (FixedGuardNode guard : graph.getNodes().filter(FixedGuardNode.class)) {
            if (guard.condition() instanceof IntegerLessThanNode lessThan && lessThan.getX() == limit && lessThan.getY() == index && !guard.isNegated()) {
                targetGuard = guard;
            } else if (piGuard == null) {
                piGuard = guard;
            }
        }

        Assert.assertNotNull("Expected a fixed guard for limit < index.", targetGuard);
        Assert.assertNotNull("Expected a preceding guard to anchor the synthetic PiNode.", piGuard);

        IntegerLessThanNode targetCondition = (IntegerLessThanNode) targetGuard.condition();
        ValueNode limitPi = graph.addOrUnique(PiNode.create(limit, IntegerStamp.create(32, 100, Integer.MAX_VALUE), piGuard.asNode()));
        targetCondition.replaceFirstInput(limit, limitPi);
        Assert.assertSame(limitPi, targetCondition.getX());

        new ConditionalEliminationPhase(canonicalizer, true).apply(graph, getDefaultHighTierContext());

        Assert.assertTrue("Conditional elimination must not prove limit < index from the other operand's Pi-derived stamp.", targetGuard.isAlive());
        GraphOrder.assertSchedulableGraph(graph);
    }

    @Test
    public void testIfNodeOtherOperandPiStampDoesNotRewriteGuardedUsages() {
        StructuredGraph graph = parseEager("branchGuardedUsageSnippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        prepareGraph(graph, canonicalizer, getProviders(), false);

        ValueNode index = graph.getParameter(0);
        ValueNode limit = graph.getParameter(1);
        IfNode targetIf = null;
        FixedGuardNode piGuard = null;
        SideEffectNode sideEffect = null;

        for (IfNode ifNode : graph.getNodes().filter(IfNode.class)) {
            if (ifNode.condition() instanceof IntegerLessThanNode lessThan && lessThan.getX() == index && lessThan.getY() == limit) {
                targetIf = ifNode;
                break;
            }
        }
        for (FixedGuardNode guard : graph.getNodes().filter(FixedGuardNode.class)) {
            piGuard = guard;
            break;
        }
        for (SideEffectNode candidate : graph.getNodes().filter(SideEffectNode.class)) {
            if (candidate.getValue() == index) {
                sideEffect = candidate;
                break;
            }
        }

        Assert.assertNotNull("Expected an IfNode for index < limit.", targetIf);
        Assert.assertNotNull("Expected a preceding guard to anchor the synthetic PiNode.", piGuard);
        Assert.assertNotNull("Expected a true-branch side effect to keep the guarded value live.", sideEffect);

        IntegerLessThanNode targetCondition = (IntegerLessThanNode) targetIf.condition();
        ValueNode limitPi = graph.addOrUnique(PiNode.create(limit, IntegerStamp.create(32, 100, Integer.MAX_VALUE), piGuard.asNode()));
        targetCondition.replaceFirstInput(limit, limitPi);
        ValueNode guardedIndex = graph.addOrUniqueWithInputs(GuardedValueNode.create(index, targetIf.trueSuccessor()));
        sideEffect.replaceFirstInput(index, guardedIndex);
        Assert.assertSame(guardedIndex, sideEffect.getValue());
        Assert.assertSame(limitPi, targetCondition.getY());
        Assert.assertTrue("The surviving successor must have a guarded usage for this regression.", targetIf.trueSuccessor().hasUsagesOfType(InputType.Guard));

        new ConditionalEliminationPhase(canonicalizer, true).apply(graph, getDefaultHighTierContext());

        Assert.assertTrue("Conditional elimination must not fold an IfNode with a hidden other-operand guard dependency when surviving guarded usages would be rewired.", targetIf.isAlive());
        Assert.assertSame("The IfNode condition should remain unchanged when the permissive proof is rejected.", targetCondition, targetIf.condition());
        Assert.assertTrue("The branch-local guarded value should keep its original branch guard.", guardedIndex.isAlive());
        Assert.assertSame(targetIf.trueSuccessor(), ((GuardedValueNode) guardedIndex).getGuard());
        GraphOrder.assertSchedulableGraph(graph);
    }

    @Test
    public void testOtherOperandZeroExtendStampKeepsOperationRange() {
        StructuredGraph graph = parseEager("charArrayLoadSnippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        prepareGraph(graph, canonicalizer, getProviders(), true);

        GuardNode targetGuard = null;
        ZeroExtendNode zeroExtend = null;

        for (GuardNode guard : graph.getNodes().filter(GuardNode.class)) {
            if (guard.getCondition() instanceof IntegerLowerThanNode lessThan && lessThan.getX() instanceof ZeroExtendNode candidate && !guard.isNegated() &&
                            candidate.getInputBits() == Character.SIZE && candidate.stamp(NodeView.DEFAULT) instanceof IntegerStamp candidateStamp &&
                            candidateStamp.upperBound() == Character.MAX_VALUE) {
                targetGuard = guard;
                zeroExtend = candidate;
            }
        }

        Assert.assertNotNull("Expected a guard for the zero-extended char array value < array length.", targetGuard);
        SafeStampInputSearch search = new SafeStampInputSearch(graph);
        IntegerStamp safeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(zeroExtend, search);

        Assert.assertEquals("Zero extension should preserve its operation-implied lower bound for other-operand folds.", 0, safeStamp.lowerBound());
        Assert.assertEquals("Zero extension should preserve its operation-implied upper bound for other-operand folds.", Character.MAX_VALUE, safeStamp.upperBound());
        GraphOrder.assertSchedulableGraph(graph);
    }

    @Test
    public void testOtherOperandDefaultUnaryStampIsNotSafe() {
        StructuredGraph graph = parseEager("snippet", AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        prepareGraph(graph, canonicalizer, getProviders(), false);

        ValueNode limit = graph.getParameter(1);
        FixedGuardNode piGuard = graph.getNodes().filter(FixedGuardNode.class).first();
        Assert.assertNotNull("Expected a guard to anchor the synthetic unary input.", piGuard);

        ValueNode limitPi = graph.addOrUnique(PiNode.create(limit, IntegerStamp.create(32, 100, Integer.MAX_VALUE), piGuard.asNode()));
        ValueNode unary = graph.addOrUniqueWithInputs(new DefaultFoldStampUnaryNode(limitPi));
        SafeStampInputSearch search = new SafeStampInputSearch(graph);
        IntegerStamp safeStamp = (IntegerStamp) ConditionalEliminationUtil.getOtherSafeStamp(unary, search);

        Assert.assertEquals("UnaryNode's default foldStamp keeps the current node stamp, so it must not be treated as operation-derived evidence.", Integer.MIN_VALUE, safeStamp.lowerBound());
        Assert.assertEquals("UnaryNode's default foldStamp keeps the current node stamp, so it must not be treated as operation-derived evidence.", Integer.MAX_VALUE, safeStamp.upperBound());
        GraphOrder.assertSchedulableGraph(graph);
    }

}
