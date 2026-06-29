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

import static jdk.graal.compiler.core.common.SpectrePHTMitigations.GuardTargets;
import static jdk.graal.compiler.core.common.SpectrePHTMitigations.Options.SpectrePHTBarriers;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.extended.SpeculationFenceNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ExpandLogicPhase;
import jdk.graal.compiler.phases.common.InsertGuardFencesPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that block-entry speculation fences inserted for GuardTargets survive the low-tier
 * expansion of a {@link ShortCircuitOrNode}. The regression shape is:
 *
 * <pre>
 * if (x || y) {
 *     protected memory access
 * }
 * </pre>
 *
 * {@link InsertGuardFencesPhase} inserts a fence after the original guarded branch begin. Later,
 * {@link ExpandLogicPhase} rewrites the single short-circuit condition into nested {@link IfNode
 * IfNodes}; every new branch that reaches the protected access must keep a fence.
 *
 * The tests also cover other CFG rewrites from the same root cause: redundant begin deletion,
 * loop-begin and loop-exit removal, and if/phi splitting. In each case, a block-entry fence must
 * remain on every surviving path to the protected access.
 */
public class SpectreGuardTargetsBypassTest extends GraalCompilerTest {

    private static final long ARRAY_LONG_BASE_OFFSET = Unsafe.ARRAY_LONG_BASE_OFFSET;

    public static long[] memory = new long[]{1, 2};

    private int postInsertFenceCount = -1;
    private int postInsertShortCircuitOrCount = -1;
    private int postInsertLoopBeginFixedAccessCount = -1;
    private int postInsertLoopBeginFixedAccessFenceCount = -1;
    private int postInsertLoopExitFixedAccessCount = -1;
    private int postInsertLoopExitFixedAccessFenceCount = -1;
    private String postInsertLoopBeginFixedAccessDetails;
    private String postInsertLoopExitFixedAccessDetails;

    public static boolean orPositive(int a, int b) {
        return a > 0 || b > 0;
    }

    public static boolean orPositiveNatural(int a, int b) {
        return a > 0 || b > 0;
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, SpectreGuardTargetsBypassTest.class);
        r.register(new InvocationPlugin("orPositive", int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode bValue) {
                ValueNode zero = b.add(ConstantNode.forInt(0));
                LogicNode x = b.add(IntegerLessThanNode.create(zero, a, NodeView.DEFAULT));
                LogicNode y = b.add(IntegerLessThanNode.create(zero, bValue, NodeView.DEFAULT));
                LogicNode or = b.add(ShortCircuitOrNode.create(x, false, y, false, BranchProbabilityData.unknown()));
                b.addPush(JavaKind.Boolean, new ConditionalNode(or, b.add(ConstantNode.forBoolean(true)), b.add(ConstantNode.forBoolean(false))));
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        ListIterator<BasePhase<? super MidTierContext>> phases = suites.getMidTier().findPhase(InsertGuardFencesPhase.class);
        Assert.assertNotNull("InsertGuardFencesPhase must run when GuardTargets is enabled", phases);
        phases.add(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                int fences = 0;
                fences += graph.getNodes().filter(SpeculationFenceNode.class).count();
                postInsertFenceCount = fences;
                postInsertShortCircuitOrCount = graph.getNodes().filter(ShortCircuitOrNode.class).count();
                postInsertLoopBeginFixedAccessCount = 0;
                postInsertLoopBeginFixedAccessFenceCount = 0;
                StringBuilder loopBeginFixedAccessDetails = new StringBuilder();
                for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.TYPE)) {
                    FixedAccessNode access = firstFixedAccess(loopBegin);
                    if (access != null && access.getGuard() == null) {
                        postInsertLoopBeginFixedAccessCount++;
                        if (hasFenceBeforeFirstFixedAccess(loopBegin)) {
                            postInsertLoopBeginFixedAccessFenceCount++;
                        }
                        loopBeginFixedAccessDetails.append(loopBegin).append(" fence=").append(hasFenceBeforeFirstFixedAccess(loopBegin)).append(" access=").append(access).append(' ');
                    }
                }
                postInsertLoopBeginFixedAccessDetails = loopBeginFixedAccessDetails.toString();
                postInsertLoopExitFixedAccessCount = 0;
                postInsertLoopExitFixedAccessFenceCount = 0;
                StringBuilder loopExitFixedAccessDetails = new StringBuilder();
                for (LoopExitNode loopExit : graph.getNodes(LoopExitNode.TYPE)) {
                    FixedAccessNode access = firstFixedAccess(loopExit);
                    if (access != null && access.getGuard() == null) {
                        postInsertLoopExitFixedAccessCount++;
                        if (hasFenceBeforeFirstFixedAccess(loopExit)) {
                            postInsertLoopExitFixedAccessFenceCount++;
                        }
                        loopExitFixedAccessDetails.append(loopExit).append(" fence=").append(hasFenceBeforeFirstFixedAccess(loopExit)).append(" access=").append(access).append(' ');
                    }
                }
                postInsertLoopExitFixedAccessDetails = loopExitFixedAccessDetails.toString();
            }

            @Override
            public CharSequence getName() {
                return "RecordSpeculationFenceCount";
            }
        });
        return suites;
    }

    private static OptionValues guardTargets() {
        return new OptionValues(getInitialOptions(), SpectrePHTBarriers, GuardTargets);
    }

    private static int countFences(StructuredGraph graph) {
        return graph.getNodes().filter(SpeculationFenceNode.class).count();
    }

    /**
     * Walk one linear successor path until it either observes a speculation fence or reaches a
     * fixed memory access.
     *
     * <pre>
     * fenced = false
     * while current is a straight-line node:
     *     if current is a speculation fence:
     *         fenced = true
     *     if current is a fixed memory access:
     *         return current when fenced is false
     *     current = next control-flow node
     * return no violation
     * </pre>
     */
    private static FixedAccessNode firstUnfencedAccess(AbstractBeginNode start) {
        boolean fenced = false;
        FixedNode current = start.next();
        while (current != null) {
            if (current instanceof SpeculationFenceNode speculationFence) {
                fenced = true;
                current = speculationFence.next();
            } else if (current instanceof AbstractBeginNode begin) {
                current = begin.next();
            } else if (current instanceof FixedAccessNode access) {
                return fenced ? null : access;
            } else if (current instanceof AbstractEndNode end) {
                AbstractMergeNode merge = end.merge();
                current = merge.next();
            } else if (current instanceof FixedWithNextNode fixedWithNext) {
                current = fixedWithNext.next();
            } else {
                return null;
            }
        }
        return null;
    }

    private static boolean hasFenceBeforeFirstFixedAccess(AbstractBeginNode start) {
        FixedNode current = start.next();
        while (current != null) {
            if (current instanceof SpeculationFenceNode) {
                return true;
            } else if (current instanceof AbstractBeginNode begin) {
                current = begin.next();
            } else if (current instanceof FixedAccessNode) {
                return false;
            } else if (current instanceof AbstractEndNode end) {
                current = end.merge().next();
            } else if (current instanceof FixedWithNextNode fixedWithNext) {
                current = fixedWithNext.next();
            } else {
                return false;
            }
        }
        return false;
    }

    private static FixedAccessNode firstFixedAccess(AbstractBeginNode start) {
        FixedNode current = start.next();
        while (current != null) {
            if (current instanceof AbstractBeginNode begin) {
                current = begin.next();
            } else if (current instanceof FixedAccessNode access) {
                return access;
            } else if (current instanceof AbstractEndNode end) {
                current = end.merge().next();
            } else if (current instanceof FixedWithNextNode fixedWithNext) {
                current = fixedWithNext.next();
            } else {
                return null;
            }
        }
        return null;
    }

    private static WriteNode findLongArrayWrite(StructuredGraph graph) {
        for (WriteNode write : graph.getNodes(WriteNode.TYPE)) {
            if (write.getLocationIdentity().equals(NamedLocationIdentity.LONG_ARRAY_LOCATION)) {
                return write;
            }
        }
        return null;
    }

    private static boolean hasPrecedingFence(FixedNode node) {
        for (Node current = node.predecessor(); current instanceof FixedNode; current = current.predecessor()) {
            if (current instanceof SpeculationFenceNode) {
                return true;
            }
        }
        return false;
    }

    private static String precedingFenceDetails(FixedNode node) {
        AbstractBeginNode previousBegin = null;
        for (Node current = node.predecessor(); current instanceof FixedNode; current = current.predecessor()) {
            if (current instanceof SpeculationFenceNode) {
                return "write=" + node + ", previousFence=" + current;
            }
            if (current instanceof AbstractBeginNode begin) {
                previousBegin = begin;
                break;
            }
        }
        return "write=" + node + ", previousFence=<none>, previousBegin=" + (previousBegin == null ? "<none>" : previousBegin);
    }

    /**
     * Finds every {@link IfNode} successor that can reach a fixed memory access on a straight-line
     * path without first passing a speculation fence.
     */
    private static List<String> unfencedIfSuccessorAccesses(StructuredGraph graph) {
        List<String> violations = new ArrayList<>();
        for (IfNode ifNode : graph.getNodes(IfNode.TYPE)) {
            for (AbstractBeginNode successor : new AbstractBeginNode[]{ifNode.trueSuccessor(), ifNode.falseSuccessor()}) {
                FixedAccessNode access = firstUnfencedAccess(successor);
                if (access != null) {
                    violations.add(ifNode + " -> " + successor + " reaches " + access + " (" + access.getLocationIdentity() + ") with no speculation fence");
                }
            }
        }
        return violations;
    }

    /**
     * White-box regression shape that uses the invocation plugin above to force a
     * {@link ShortCircuitOrNode} condition whose true target contains the protected access.
     */
    public static void shortCircuitOrGuardTarget(int a, int b, long value) {
        if (orPositive(a, b)) {
            UNSAFE.putLong(memory, ARRAY_LONG_BASE_OFFSET, value);
        }
        GraalDirectives.controlFlowAnchor();
    }

    public static long shortCircuitOrGuardTargetResult(int a, int b, long value) {
        if (orPositive(a, b)) {
            UNSAFE.putLong(memory, ARRAY_LONG_BASE_OFFSET, value);
        }
        GraalDirectives.controlFlowAnchor();
        return memory[0];
    }

    public static void shortCircuitOrFalseGuardTarget(int a, int b, long value) {
        if (orPositive(a, b)) {
            GraalDirectives.controlFlowAnchor();
        } else {
            UNSAFE.putLong(memory, ARRAY_LONG_BASE_OFFSET, value);
        }
        GraalDirectives.controlFlowAnchor();
    }

    public static long shortCircuitOrFalseGuardTargetResult(int a, int b, long value) {
        if (orPositive(a, b)) {
            GraalDirectives.controlFlowAnchor();
        } else {
            UNSAFE.putLong(memory, ARRAY_LONG_BASE_OFFSET, value);
        }
        GraalDirectives.controlFlowAnchor();
        return memory[0];
    }

    /**
     * Natural Java short-circuit helper shape, without the invocation plugin used by
     * {@link #orPositive(int, int)}. Keeping the short-circuit expression in a small helper lets
     * the optimizer create the same {@link ShortCircuitOrNode} guarded branch from ordinary Java
     * code.
     */
    public static void naturalMethodShortCircuitOrGuardTarget(int a, int b, long value) {
        if (orPositiveNatural(a, b)) {
            UNSAFE.putLong(memory, ARRAY_LONG_BASE_OFFSET, value);
        }
        GraalDirectives.controlFlowAnchor();
    }

    public static long naturalMethodShortCircuitOrGuardTargetResult(int a, int b, long value) {
        if (orPositiveNatural(a, b)) {
            UNSAFE.putLong(memory, ARRAY_LONG_BASE_OFFSET, value);
        }
        GraalDirectives.controlFlowAnchor();
        return memory[0];
    }

    /**
     * The unsafe access is in the first loop body block, so {@link InsertGuardFencesPhase} should
     * fence the {@link LoopBeginNode}. The opaque loop condition folds to false after guard
     * lowering, allowing later phases to remove the one-iteration loop structure.
     */
    public static long opaqueSingleIterationLoopBeginSnippet(long value) {
        final Object m = memory;
        do {
            GraalDirectives.controlFlowAnchor();
            UNSAFE.putLong(m, ARRAY_LONG_BASE_OFFSET, value);
        } while (GraalDirectives.opaqueUntilAfter(false, StageFlag.GUARD_LOWERING));
        GraalDirectives.controlFlowAnchor();
        return memory[0];
    }

    /**
     * The unsafe access is on a loop side exit. The control-flow anchor keeps loop optimizations
     * from moving the access away before {@link InsertGuardFencesPhase}. The opaque condition folds
     * after guard lowering, so later phases can call {@link LoopExitNode#removeExit(boolean)},
     * replacing the {@link LoopExitNode} that carried the fence with a plain {@link BeginNode}.
     */
    public static long opaqueLoopSideExitSnippet(int iterations, long value) {
        final Object m = memory;
        long result = 0;
        for (int i = 0; i < iterations; i++) {
            GraalDirectives.controlFlowAnchor();
            result += GraalDirectives.sideEffect(i);
            if (GraalDirectives.opaqueUntilAfter(true, StageFlag.GUARD_LOWERING)) {
                UNSAFE.putLong(m, ARRAY_LONG_BASE_OFFSET, value + i);
                break;
            }
        }
        GraalDirectives.controlFlowAnchor();
        return result + memory[0];
    }

    public static int returnSnippet(int value) {
        GraalDirectives.controlFlowAnchor();
        return value;
    }

    /**
     * Builds a {@code Merge -> Phi -> If} shape where canonicalization can split the second
     * {@link IfNode} through the phi-producing merge.
     */
    public static int ifPhiSplitSnippet(int value) {
        int selector;
        if (value < 0) {
            selector = 0;
        } else if (value == 0) {
            selector = 0;
        } else {
            selector = 1;
        }
        int result;
        if (selector == 0) {
            result = 1;
        } else {
            result = 2;
        }
        return result + value;
    }

    /**
     * Finds the second {@link IfNode} in {@link #ifPhiSplitSnippet(int)}, whose condition compares
     * a phi from the immediately preceding merge.
     */
    private static IfNode findIfAfterPhiMerge(StructuredGraph graph) {
        for (IfNode ifNode : graph.getNodes(IfNode.TYPE)) {
            if (ifNode.predecessor() instanceof AbstractMergeNode) {
                return ifNode;
            }
        }
        Assert.fail("expected an IfNode whose condition depends on a phi from the preceding merge");
        return null;
    }

    private static IfNode findIfBeforePhiMerge(StructuredGraph graph) {
        for (IfNode ifNode : graph.getNodes(IfNode.TYPE)) {
            if (!(ifNode.predecessor() instanceof AbstractMergeNode)) {
                return ifNode;
            }
        }
        Assert.fail("expected an IfNode before the phi-producing merge");
        return null;
    }

    private void assertGuardTargetFenceSurvives(String methodName) {
        postInsertFenceCount = -1;
        postInsertShortCircuitOrCount = -1;

        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(methodName), guardTargets());

        Assert.assertTrue("precondition: ShortCircuitOrNode must reach InsertGuardFencesPhase, got " + postInsertShortCircuitOrCount, postInsertShortCircuitOrCount > 0);
        Assert.assertTrue("precondition: InsertGuardFencesPhase must have inserted at least one fence, got " + postInsertFenceCount, postInsertFenceCount > 0);

        int finalFences = countFences(graph);
        List<String> violations = unfencedIfSuccessorAccesses(graph);
        StringBuilder message = new StringBuilder();
        message.append("InsertGuardFencesPhase placed ").append(postInsertFenceCount).append(" fence(s); final graph has ").append(finalFences).append(".");
        for (String violation : violations) {
            message.append(System.lineSeparator()).append("  VIOLATION: ").append(violation);
        }
        Assert.assertTrue("[GuardTargets bypass]\n" + message, violations.isEmpty());
    }

    @Test
    public void testShortCircuitOrGuardTargetFenceSurvives() {
        assertGuardTargetFenceSurvives("shortCircuitOrGuardTarget");
    }

    @Test
    public void testShortCircuitOrGuardTargetExecutes() {
        memory = new long[]{1, 2};
        test(guardTargets(), "shortCircuitOrGuardTargetResult", 1, -1, 42L);
        memory = new long[]{1, 2};
        test(guardTargets(), "shortCircuitOrGuardTargetResult", -1, 1, 43L);
        memory = new long[]{1, 2};
        test(guardTargets(), "shortCircuitOrGuardTargetResult", -1, -1, 44L);
    }

    @Test
    public void testShortCircuitOrFalseGuardTargetExecutes() {
        memory = new long[]{1, 2};
        test(guardTargets(), "shortCircuitOrFalseGuardTargetResult", 1, -1, 45L);
        memory = new long[]{1, 2};
        test(guardTargets(), "shortCircuitOrFalseGuardTargetResult", -1, 1, 46L);
        memory = new long[]{1, 2};
        test(guardTargets(), "shortCircuitOrFalseGuardTargetResult", -1, -1, 47L);
    }

    @Test
    public void testShortCircuitOrFalseGuardTargetFenceSurvives() {
        assertGuardTargetFenceSurvives("shortCircuitOrFalseGuardTarget");
    }

    @Test
    public void testNaturalMethodShortCircuitOrGuardTargetFenceSurvives() {
        assertGuardTargetFenceSurvives("naturalMethodShortCircuitOrGuardTarget");
    }

    @Test
    public void testNaturalMethodShortCircuitOrGuardTargetExecutes() {
        memory = new long[]{1, 2};
        test(guardTargets(), "naturalMethodShortCircuitOrGuardTargetResult", 1, -1, 48L);
        memory = new long[]{1, 2};
        test(guardTargets(), "naturalMethodShortCircuitOrGuardTargetResult", -1, 1, 49L);
        memory = new long[]{1, 2};
        test(guardTargets(), "naturalMethodShortCircuitOrGuardTargetResult", -1, -1, 50L);
    }

    @Test
    public void testOpaqueSingleIterationLoopBeginFenceSurvivesAfterLoopRemoved() {
        postInsertLoopBeginFixedAccessCount = -1;
        postInsertLoopBeginFixedAccessFenceCount = -1;

        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("opaqueSingleIterationLoopBeginSnippet"), guardTargets());

        Assert.assertEquals("precondition: InsertGuardFencesPhase must see exactly one LoopBeginNode reaching the unsafe access; details=" +
                        postInsertLoopBeginFixedAccessDetails, 1, postInsertLoopBeginFixedAccessCount);
        Assert.assertEquals("precondition: InsertGuardFencesPhase must insert a fence after the LoopBeginNode before the unsafe access; details=" +
                        postInsertLoopBeginFixedAccessDetails, 1, postInsertLoopBeginFixedAccessFenceCount);
        Assert.assertEquals("precondition: the opaque loop condition should fold after InsertGuardFencesPhase", 0, graph.getNodes(LoopBeginNode.TYPE).count());

        WriteNode write = findLongArrayWrite(graph);
        Assert.assertNotNull("precondition: expected the unsafe long array write to remain in the final graph", write);
        Assert.assertTrue("loop begin fence must survive after the opaque loop condition folds; " + precedingFenceDetails(write), hasPrecedingFence(write));
    }

    @Test
    public void testOpaqueLoopSideExitFenceSurvivesAfterLoopExitRemoved() {
        postInsertLoopExitFixedAccessCount = -1;
        postInsertLoopExitFixedAccessFenceCount = -1;

        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("opaqueLoopSideExitSnippet"), guardTargets());

        Assert.assertEquals("precondition: InsertGuardFencesPhase must see exactly one LoopExitNode reaching the unsafe access; details=" +
                        postInsertLoopExitFixedAccessDetails, 1, postInsertLoopExitFixedAccessCount);
        Assert.assertEquals("precondition: InsertGuardFencesPhase must insert a fence after the LoopExitNode before the unsafe access; details=" +
                        postInsertLoopExitFixedAccessDetails, 1, postInsertLoopExitFixedAccessFenceCount);

        WriteNode write = findLongArrayWrite(graph);
        Assert.assertNotNull("precondition: expected the unsafe long array write to remain in the final graph", write);
        Assert.assertTrue("loop exit fence must survive after the opaque loop-exit condition folds; " + precedingFenceDetails(write), hasPrecedingFence(write));
    }

    private static BeginNode insertRedundantBeginBeforeReturn(StructuredGraph graph) {
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        FixedWithNextNode predecessor = (FixedWithNextNode) returnNode.predecessor();
        BeginNode begin = graph.add(new BeginNode());
        predecessor.setNext(null);
        begin.setNext(returnNode);
        predecessor.setNext(begin);
        return begin;
    }

    @Test
    public void testSimplifyPreservesSpeculationFenceNode() {
        StructuredGraph graph = parseEager("returnSnippet", StructuredGraph.AllowAssumptions.YES);
        BeginNode begin = insertRedundantBeginBeforeReturn(graph);
        SpeculationFenceNode fence = graph.add(new SpeculationFenceNode());
        graph.addAfterFixed(begin, fence);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();

        CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());

        Assert.assertTrue("canonicalization must preserve explicit speculation fences", fence.isAlive());
        Assert.assertSame(fence, returnNode.predecessor());
    }

    @Test
    public void testSimplifyMovesBlockEntrySpeculationFenceToBegin() {
        StructuredGraph graph = parseEager("returnSnippet", StructuredGraph.AllowAssumptions.YES);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        SpeculationFenceNode fence = graph.add(SpeculationFenceNode.forBlockEntry());
        graph.addBeforeFixed(returnNode, fence);

        CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());

        Assert.assertTrue("canonicalization must preserve block-entry speculation fences", fence.isAlive());
        Assert.assertSame("block-entry speculation fences must be placed immediately after the nearest begin", fence, graph.start().next());
    }

    @Test
    public void testSimplifyRemovesDuplicateBlockEntrySpeculationFence() {
        StructuredGraph graph = parseEager("returnSnippet", StructuredGraph.AllowAssumptions.YES);
        SpeculationFenceNode existingFence = graph.add(new SpeculationFenceNode());
        graph.addAfterFixed(graph.start(), existingFence);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        SpeculationFenceNode duplicateFence = graph.add(SpeculationFenceNode.forBlockEntry());
        graph.addBeforeFixed(returnNode, duplicateFence);

        CanonicalizerPhase.create().apply(graph, getDefaultHighTierContext());

        Assert.assertTrue("canonicalization must keep the existing speculation fence", existingFence.isAlive());
        Assert.assertFalse("duplicate block-entry speculation fence must be removed", duplicateFence.isAlive());
        Assert.assertSame(existingFence, graph.start().next());
    }

    @Test
    public void testSplitSuccessorFencesAreNotCommoned() {
        StructuredGraph graph = parseEager("ifPhiSplitSnippet", StructuredGraph.AllowAssumptions.YES);
        IfNode ifNode = findIfBeforePhiMerge(graph);
        AbstractBeginNode trueSuccessor = ifNode.trueSuccessor();
        AbstractBeginNode falseSuccessor = ifNode.falseSuccessor();
        SpeculationFenceNode trueFence = graph.add(SpeculationFenceNode.forBlockEntry());
        SpeculationFenceNode falseFence = graph.add(SpeculationFenceNode.forBlockEntry());
        graph.addAfterFixed(trueSuccessor, trueFence);
        graph.addAfterFixed(falseSuccessor, falseFence);

        CanonicalizerPhase.create().applyIncremental(graph, getDefaultHighTierContext(), List.of(ifNode));

        Assert.assertTrue("precondition: canonicalization must keep the if node", ifNode.isAlive());
        Assert.assertTrue("canonicalization must keep both successor speculation fences", trueFence.isAlive());
        Assert.assertTrue("canonicalization must keep both successor speculation fences", falseFence.isAlive());
        Assert.assertSame(trueFence, trueSuccessor.next());
        Assert.assertSame(falseFence, falseSuccessor.next());
    }

    @Test
    public void testIfPhiSplitPreservesFencedSuccessor() {
        StructuredGraph graph = parseEager("ifPhiSplitSnippet", StructuredGraph.AllowAssumptions.YES);
        IfNode ifNode = findIfAfterPhiMerge(graph);
        AbstractBeginNode fencedSuccessor = ifNode.trueSuccessor();
        SpeculationFenceNode fence = graph.add(SpeculationFenceNode.forBlockEntry());
        graph.addAfterFixed(fencedSuccessor, fence);

        CanonicalizerPhase.create().applyIncremental(graph, getDefaultHighTierContext(), List.of(ifNode));

        Assert.assertFalse("precondition: canonicalization must exercise the if/phi split", ifNode.isAlive());
        Assert.assertTrue("if/phi splitting must preserve an explicit speculation fence", fence.isAlive());
    }

    @Test
    public void testIfPhiSplitExecutes() {
        test(guardTargets(), "ifPhiSplitSnippet", -1);
        test(guardTargets(), "ifPhiSplitSnippet", 0);
        test(guardTargets(), "ifPhiSplitSnippet", 1);
    }
}
