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
package jdk.graal.compiler.loop.test;

import java.util.List;
import java.util.ListIterator;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import jdk.graal.compiler.loop.phases.SimulationBasedLoopPeeling;
import jdk.graal.compiler.loop.phases.SimulationBasedLoopPolicies;
import jdk.graal.compiler.loop.phases.InjectLoopCounterStampsPhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestBasePhase;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.loop.phases.LoopPeelingPhase;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.VirtualState.NodePositionClosure;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SimulationBasedLoopPeelingTest extends GraalCompilerTest {

    @SuppressWarnings("all")
    public static int[] snippetManyLocals(int n, int[] a, int[] b) {
        int bb = 0;
        int c = 2;
        int d = 3;
        int e = 4;
        int f = 5;
        int g = 6;
        int h = 7;
        int ii = 8;
        int j = 9;
        /*
         * Optimize read in loop rest
         */
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            a[i] = a[i] + a[0];
            bb = a[i];
            // c = bb * c;

            if (SideEffect == 1) {
                d = c * d * c;
                e = d * e * c;
                f = e * f * c;
                g = f * g * c;
                h = f * h * c;
                ii = h * ii * c;
                j = ii * j * c;
                c++;
                continue;
            }
            SideEffect = GraalDirectives.sideEffect(n);
            if (SideEffect == 2) {
                d = c * d * 3;
                e = d * e * 3;
                f = e * f * 3;
                g = f * g * 3;
                h = f * h * 3;
                ii = h * ii * 3;
                j = ii * j * 3;
                c++;
                continue;
            }
            SideEffect = GraalDirectives.sideEffect(n);
            if (SideEffect == 3) {
                d = c * d * 4;
                e = d * e * 4;
                f = e * f * 4;
                g = f * g * 4;
                h = f * h * 4;
                ii = h * ii * 4;
                j = ii * j * 4;
                c++;
                continue;
            }
            SideEffect = GraalDirectives.sideEffect(n);
            if (SideEffect == 4) {
                d = c * d * 5;
                e = d * e * 5;
                f = e * f * 5;
                g = f * g * 5;
                h = f * h * 5;
                ii = h * ii * 5;
                j = ii * j * 5;
                c++;
                continue;
            }
            SideEffect = GraalDirectives.sideEffect(n);
            if (SideEffect == 5) {
                d = c * d * 6;
                e = d * e * 6;
                f = e * f * 6;
                g = f * g * 6;
                h = f * h * 6;
                ii = h * ii * 6;
                j = ii * j * 6;
                c++;
                continue;
            }
            SideEffect = GraalDirectives.sideEffect(n);
            if (SideEffect == 6) {
                d = c * d * 7;
                e = d * e * 7;
                f = e * f * 7;
                g = f * g * 7;
                h = f * h * 7;
                ii = h * ii * 7;
                j = ii * j * 7;
                c++;
                continue;
            }
            SideEffect = GraalDirectives.sideEffect(n);
            if (SideEffect == 7) {
                d = c * d * 8;
                e = d * e * 8;
                f = e * f * 8;
                g = f * g * 8;
                h = f * h * 8;
                ii = h * ii * 8;
                j = ii * j * 8;
                c++;
                continue;
            }
            SideEffect = GraalDirectives.sideEffect(n);
            if (SideEffect == 8) {
                d = c * d * 9;
                e = d * e * 9;
                f = e * f * 9;
                g = f * g * 9;
                h = f * h * 9;
                ii = h * ii * 9;
                j = ii * j * 9;
                c++;
                continue;
            }
            SideEffect = GraalDirectives.sideEffect(n);
        }
        SideEffect = bb + c + d + e + f + g + h + ii + j;
        return a;
    }

    @Test
    public void testManyLocals() throws Throwable {
        test("snippetManyLocals", 2, new int[10], new int[10]);
    }

    static class Acc {
        int i;
    }

    public static int nestedLoops(int iterations, int[] a) {
        Acc acc = new Acc();
        for (int i1 = 0; GraalDirectives.injectIterationCount(1000_00000, i1 < iterations); i1++) {
            a[i1] = a[i1] + a[0];
            for (int i2 = 0; GraalDirectives.injectIterationCount(1000_00000, i2 < iterations); i2++) {
                for (int i3 = 0; GraalDirectives.injectIterationCount(1000_00000, i3 < iterations); i3++) {
                    for (int i4 = 0; GraalDirectives.injectIterationCount(1000_00000, i4 < iterations); i4++) {
                        for (int i5 = 0; GraalDirectives.injectIterationCount(1000_00000, i5 < iterations); i5++) {
                            for (int i6 = 0; GraalDirectives.injectIterationCount(1000_00000, i6 < iterations); i6++) {
                                for (int i7 = 0; GraalDirectives.injectIterationCount(1000_00000, i7 < iterations); i7++) {
                                    for (int i8 = 0; GraalDirectives.injectIterationCount(1000_00000, i8 < iterations); i8++) {
                                        for (int i9 = 0; GraalDirectives.injectIterationCount(1000_00000, i9 < iterations); i9++) {
                                            for (int i10 = 0; GraalDirectives.injectIterationCount(1000_00000, i10 < iterations); i10++) {
                                                for (int i11 = 0; GraalDirectives.injectIterationCount(1000_00000, i11 < iterations); i11++) {
                                                    for (int i12 = 0; GraalDirectives.injectIterationCount(1000_00000, i12 < iterations); i12++) {
                                                        for (int i13 = 0; GraalDirectives.injectIterationCount(1000_00000, i13 < iterations); i13++) {
                                                            for (int i14 = 0; GraalDirectives.injectIterationCount(1000_00000, i14 < iterations); i14++) {
                                                                for (int i15 = 0; GraalDirectives.injectIterationCount(1000_00000, i15 < iterations); i15++) {
                                                                    acc.i += nestedLoops(iterations - 1, a);
                                                                    acc.i += nestedLoops(iterations - 1, a);
                                                                    acc.i += nestedLoops(iterations - 1, a);
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return acc.i;
    }

    @Test
    @Ignore("GR-62005")
    public void testNested() throws Throwable {
        OptionValues opt = new OptionValues(getInitialOptions(), SimulationBasedLoopPeeling.Options.PeelingConsideredMinRelativeFrequency, 16D);
        test(opt, "nestedLoops", 2, new int[10]);
    }

    @SuppressWarnings("all")
    public static int[] snippet01(int n, int[] a, int[] b) {
        /*
         * Optimize read in loop rest
         */
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            a[i] = a[i] + a[0];
        }
        return a;
    }

    @Test
    public void test01() throws Throwable {
        StructuredGraph g = getHighTierGraph("snippet01");
        Assert.assertEquals(1, g.getNodes(LoopBeginNode.TYPE).first().peelings());
        Assert.assertEquals(2, g.getNodes().filter(LoadIndexedNode.class).count());
        test("snippet01", 10, new int[10], new int[10]);
    }

    public static int SideEffect;

    public static final Object OSideEffect = new Object();

    public static Object snippet02(int n) {
        /*
         * Fold if in peeled and loop rest
         */
        Object phi = null;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            if (phi == null) {
                SideEffect = 12;
            }
            phi = OSideEffect;
        }
        return phi;
    }

    @Test
    public void test02() throws Throwable {
        StructuredGraph g = getHighTierGraph("snippet02");
        Assert.assertEquals(1, g.getNodes(LoopBeginNode.TYPE).first().peelings());
        Assert.assertEquals(2, g.getNodes().filter(IfNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(StoreFieldNode.class).count());
        test("snippet02", 10);
    }

    /*
     * Fold in peeled and loop rest
     */
    public static int snippet03(int n) {
        int phi = 12;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            if (phi % 2 == 0) {
                SideEffect = 12;
            }
            phi = 1;
        }
        return phi;
    }

    @Test
    public void test03() throws Throwable {
        StructuredGraph g = getHighTierGraph("snippet03");
        Assert.assertEquals(1, g.getNodes(LoopBeginNode.TYPE).first().peelings());
        Assert.assertEquals(2, g.getNodes().filter(IfNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(StoreFieldNode.class).count());
        test("snippet03", 10);
    }

    public static int snippet04(int n) {
        int phi = 12;
        for (int i = 0; GraalDirectives.injectIterationCount(10, i < n); i++) {
            if (SideEffect < 444) {
                phi = SideEffect;
                continue;
            }
            if (phi % 2 == 0) {
                SideEffect = 12;
            }
            phi = 1;
        }
        return phi;
    }

    @Test
    public void test04() throws Throwable {
        StructuredGraph g = getHighTierGraph("snippet04");
        Assert.assertEquals(1, g.getNodes(LoopBeginNode.TYPE).first().peelings());
        Assert.assertEquals(3, g.getNodes().filter(IfNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(StoreFieldNode.class).count());
        test("snippet04", 10);
    }

    /*
     * Multi ends still fold in loop rest
     */
    public static int snippet05(int n) {
        int phi = 12;
        /*
         * after peeling the stamp of phi is 3 union 1
         */
        for (int i = 0; GraalDirectives.injectIterationCount(10000, i < n); i++) {
            if (SideEffect < 444) {
                phi = 3;
                continue;
            }
            if (phi % 2 == 0) {
                SideEffect = 12;
            }
            phi = 1;
        }
        return phi;
    }

    @Test
    public void test05() throws Throwable {
        StructuredGraph g = getHighTierGraph("snippet05");
        Assert.assertEquals(1, g.getNodes(LoopBeginNode.TYPE).first().peelings());
        Assert.assertEquals(3, g.getNodes().filter(IfNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(StoreFieldNode.class).count());
        test("snippet05", 10);
    }

    static class A {
        int x;

        A(int x) {
            this.x = x;
        }
    }

    static final A AConst = new A(1);

    public static void snippet06(int n) {
        A a = AConst;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            SideEffect = a.x;
            // escape here
            a = new A(n);
        }
        GraalDirectives.blackhole(a);
    }

    @Test
    public void test06() throws Throwable {
        StructuredGraph g = getHighTierGraph("snippet06");
        Assert.assertEquals(1, g.getNodes(LoopBeginNode.TYPE).first().peelings());
        Assert.assertEquals(2, g.getNodes().filter(IfNode.class).count());
        Assert.assertEquals(2, g.getNodes().filter(StoreFieldNode.class).count());
        // after the loop on the exit branch
        Assert.assertEquals(1, g.getNodes().filter(CommitAllocationNode.class).count());
        test("snippet06", 10);
    }

    public static void snippet07(int n) {
        A a = AConst;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            if (i < 3) {
                GraalDirectives.blackhole(null);
            }
            GraalDirectives.blackhole(a);
        }
        GraalDirectives.blackhole(a);
    }

    @Ignore("Requires peeling of multipl iterations at once, currently not supported")
    @Test
    public void test07() throws Throwable {
        test("snippet07", 10);
    }

    public static void snippet08(int n) {
        A a = AConst;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            if (n < 2) {
                GraalDirectives.blackhole(null);
            }
            GraalDirectives.blackhole(a);
        }
        GraalDirectives.blackhole(a);
    }

    @Ignore("Requires peeling of multipl iterations at once, currently not supported")
    @Test
    public void test08() throws Throwable {
        test("snippet08", 10);
    }

    private Integer expectedInnerPeelings;

    private static class CheckMidTierPeelingInnerLoopPhase extends TestBasePhase<MidTierContext> {
        int peelings;

        CheckMidTierPeelingInnerLoopPhase(int peelings) {
            this.peelings = peelings;
        }

        @Override
        protected void run(StructuredGraph graph, MidTierContext providers) {
            List<Loop> loops = providers.getLoopsDataProvider().getLoopsData(graph).loops();
            boolean innerLoopFound = false;
            for (Loop loop : loops) {
                if (loop.parent() != null) {
                    innerLoopFound = true;
                    if (peelings >= 0) {
                        Assert.assertEquals("expected peelings of inner loop", peelings, loop.loopBegin().peelings());
                    } else {
                        Assert.assertTrue("inner loop must not be peeled iteratively", loop.loopBegin().peelings() <= 1);
                    }
                }
            }
            Assert.assertTrue("graph must contain nested loop", innerLoopFound);
        }

        @Override
        public CharSequence getName() {
            return "CheckMidTierPeelingInnerLoopPhase";
        }
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites suites = super.createSuites(opts);
        if (expectedInnerPeelings != null) {
            ListIterator<BasePhase<? super MidTierContext>> pos = suites.getMidTier().findPhase(LoopPeelingPhase.class, true);
            pos.add(new CheckMidTierPeelingInnerLoopPhase(expectedInnerPeelings));
        }
        return suites;
    }

    public static void snippet09(int n, long m) {
        int s = 0;
        // The innermost loop has a very low frequency in high-frequency region, it should be peeled
        // iteratively.
        for (int i = 0; GraalDirectives.injectIterationCount(1_000_001, i < n); i++) {
            for (long j = 0; GraalDirectives.injectIterationCount(3, j < m); j++) {
                SideEffect = s;
                s++;
            }
        }
        GraalDirectives.blackhole(s);
    }

    @Test
    public void test09() {
        expectedInnerPeelings = 2;
        test("snippet09", 1_000_000, 2L);
        expectedInnerPeelings = null;
    }

    public static void snippet10(int n, long m) {
        int s = 0;
        // Like snippet09 but with frequencies that do not permit iterative peeling of the inner
        // loop. Profile availability can determine whether the single non-iterative peel happens.
        for (int i = 0; GraalDirectives.injectIterationCount(2_001, i < n); i++) {
            for (long j = 0; GraalDirectives.injectIterationCount(1_001, j < m); j++) {
                SideEffect = s;
                s++;
            }
        }
        GraalDirectives.blackhole(s);
    }

    @Test
    public void test10() {
        expectedInnerPeelings = -1;
        test("snippet10", 2_000, 1_000L);
        expectedInnerPeelings = null;
    }

    protected StructuredGraph getHighTierGraph(String snippet) throws Throwable {
        StructuredGraph graph = null;
        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope _ = debug.scope(getClass(), method, getCodeCache())) {
            graph = parseEager(method, AllowAssumptions.YES, debug);
            new DisableOverflownCountedLoopsPhase().apply(graph);

            HighTierContext c = getDefaultHighTierContext();
            CanonicalizerPhase canon = CanonicalizerPhase.create();
            canon.apply(graph, c);
            // for read elimination detection during simulation
            new InjectLoopCounterStampsPhase().apply(graph, c);
            new LoopPeelingPhase(new SimulationBasedLoopPolicies(SimulationBasedLoopPeeling.getHighTierPeelingFactors(getInitialOptions())), canon).apply(graph, c);
            canon.apply(graph, c);
            // for read elimination detection during read elimination
            new InjectLoopCounterStampsPhase().apply(graph, c);
            new ReadEliminationPhase(canon).apply(graph, c);
            canon.apply(graph, c);
            // for conditional elimination opportunities
            new ConditionalEliminationPhase(canon, false).apply(graph, c);
            // for partial escape analysis opportunities
            new PartialEscapePhase(true, canon, getInitialOptions()).apply(graph, c);
            canon.apply(graph, c);
        }
        return graph;
    }

    static int S;

    public void snippet11() {
        for (int i = 0; i < 1000; i++) {
            for (int j = 0; j < 1000; j++) {
                // we need to loop ends to force header creation
                GraalDirectives.sideEffect(1);
                if (j == i + S) {
                    GraalDirectives.sideEffect(2);
                } else {
                    GraalDirectives.sideEffect(3);
                    continue;
                }
                GraalDirectives.sideEffect(4);
                GraalDirectives.sideEffect(5);
            }
        }
    }

    /**
     * Test case to test if {@link LoopPeelingPhase} correctly creates the {@link FrameState} at the
     * {@link MergeNode} created when merging all {@link LoopEndNode} paths in the peeled iteration.
     */
    @Test
    public void test11() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("snippet11"), AllowAssumptions.NO);
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());

        LoopsDataProvider ld = getDefaultHighTierContext().getLoopsDataProvider();
        for (Loop loop : ld.getLoopsData(g).loops()) {
            if (loop.getCFGLoop().getChildren().size() == 0) {
                LoopBeginNode lb = loop.loopBegin();
                List<PhiNode> phis = lb.phis().snapshot();
                assert phis.size() == 1 : "Must only have a single phi for this test snippet";
                FrameState headerState = lb.stateAfter();
                FrameState duplicate = headerState.duplicateWithVirtualState();
                ValuePhiNode vp = (ValuePhiNode) phis.getFirst();
                duplicate.applyToNonVirtual(new NodePositionClosure<>() {
                    @Override
                    public void apply(Node from, Position p) {
                        Node usage = p.get(from);
                        if (usage == vp) {
                            AddNode add = g.addWithoutUnique(new AddNode(vp, ConstantNode.forInt(1, g)));
                            p.set(from, add);
                            g.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, g, "After setting offset phi %s in state %s", add, duplicate);
                        }
                    }
                });
                lb.setStateAfter(duplicate);
                headerState.safeDelete();
                break;
            }
        }
        g.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, g, "After preparing loop");

        for (Loop loop : ld.getLoopsData(g).loops()) {
            if (loop.getCFGLoop().getChildren().size() == 0) {
                LoopTransformations.peel(loop);
                break;
            }
        }
        SchedulePhase.runWithoutContextOptimizations(g);
    }

}
