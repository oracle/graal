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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopRotationPhase;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.phases.LowTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.memory.GuardedMemoryAccess;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AggressivePartialUnrollInvertedTest extends GraalCompilerTest {

    private boolean mustNotUnroll = false;

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        if (mustNotUnroll) {
            Assert.assertEquals("Must not unroll in this method", 0, graph.getNodes(LoopBeginNode.TYPE).filter(x -> {
                LoopBeginNode lb = (LoopBeginNode) x;
                return lb.isPreLoop() || lb.isMainLoop() || lb.isPostLoop();
            }).count());
        } else {
            NodeIterable<LoopBeginNode> loops = graph.getNodes().filter(LoopBeginNode.class);
            boolean mainLoopFound = false;
            for (LoopBeginNode loop : loops) {
                if (loop.isMainLoop()) {
                    mainLoopFound = true;
                    int unrollFactor = loop.getUnrollFactor();
                    if (AggressivePartialUnrollPhase.Options.InsertPreMainPostOnly.getValue(graph.getOptions()) || unrollFactor > 1) {
                        return;
                    }
                    fail("Loop %s should be unrolled after pre/main/post creation", loop);
                }
            }
            Assert.assertTrue("Must have partially unrolled loops, no mainloop found", mainLoopFound);
        }
    }

    public static int testPreInvertedSnippet(int a) {
        int i = 0;
        if (i < a) {
            do {
                GraalDirectives.sideEffect(i);
                i++;
            } while (GraalDirectives.injectIterationCount(10000, i < a));
        }
        return i;
    }

    public static int testPreInvertedSnippet2(int a) {
        int i = 0;
        if (i < a) {
            do {
                GraalDirectives.sideEffect(i);
            } while (GraalDirectives.injectIterationCount(10000, i++ < a));
        }
        return i;
    }

    OptionValues getOptions() {
        return new OptionValues(getInitialOptions(), AggressivePartialUnrollPhase.Options.AggressivePartialUnroll, true, GraalOptions.OptDuplication, false,
                        GraalOptions.SpeculativeGuardMovement, true, AggressivePartialUnrollPhase.Options.MultiExitCostFactorSink, 0,
                        AggressivePartialUnrollPhase.Options.MultiExitCostFactor, 0, DefaultLoopPolicies.Options.InvertVectorizableLoops, true);
    }

    OptionValues getOptionsPreMainPostOnly() {
        return new OptionValues(getInitialOptions(), AggressivePartialUnrollPhase.Options.AggressivePartialUnroll, true, AggressivePartialUnrollPhase.Options.InsertPreMainPostOnly, true,
                        GraalOptions.OptDuplication, false, GraalOptions.SpeculativeGuardMovement, true, AggressivePartialUnrollPhase.Options.MultiExitCostFactorSink, 0,
                        AggressivePartialUnrollPhase.Options.MultiExitCostFactor, 0, DefaultLoopPolicies.Options.InvertVectorizableLoops, true);
    }

    @Test
    public void testUnrollPreInverted1() {
        test(getOptionsPreMainPostOnly(), "testPreInvertedSnippet", 100);
        for (int i = 0; i < 100; i++) {
            test(getOptions(), "testPreInvertedSnippet", i);
        }
    }

    @Test
    public void testUnrollPreInverted2() {
        test(getOptionsPreMainPostOnly(), "testPreInvertedSnippet2", 100);
        for (int i = 0; i < 100; i++) {
            test(getOptions(), "testPreInvertedSnippet2", i);
        }
    }

    static class A {
        int x;
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    public static class GuardAgainstPredecessor extends FixedWithNextNode implements Simplifiable {

        public static final NodeClass<GuardAgainstPredecessor> TYPE = NodeClass.create(GuardAgainstPredecessor.class);

        public GuardAgainstPredecessor() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void simplify(SimplifierTool tool) {
            FixedNode nextNode = next();
            if (nextNode != null) {
                if (nextNode instanceof GuardedMemoryAccess) {
                    AbstractBeginNode guard = AbstractBeginNode.prevBegin(this);
                    GuardingNode oldGuard = ((GuardedMemoryAccess) nextNode).getGuard();
                    oldGuard.asNode().replaceAtUsagesAndDelete(guard);
                    GraphUtil.removeFixedWithUnusedInputs(this);
                }
            }
        }

    }

    static void guardBefore() {

    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins p = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(p.getInvocationPlugins(), AggressivePartialUnrollInvertedTest.class);
        r.register(new InvocationPlugin("guardBefore") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.append(new GuardAgainstPredecessor());
                return true;
            }
        });
        return p;
    }

    public static int testGuardedPatternCannotUnroll() {
        int i = 0;
        Object[] o = new Object[200000];
        Object phi2 = o;
        int len = o.length;
        while (true) {
            GraalDirectives.sideEffect(i);
            if (GraalDirectives.injectIterationCount(10000, ++i < len)) {
                guardBefore();
                phi2 = o[i];
                continue;
            } else {
                break;
            }
        }
        return i + (phi2 == null ? 0 : phi2.hashCode());
    }

    /**
     * Test that we are actually not unrolling a loop that looks inverted but is not.
     */
    @Test
    public void testGuardPattern() {
        mustNotUnroll = true;
        test(new OptionValues(getOptionsPreMainPostOnly(), GraalOptions.SpeculativeGuardMovement, false, AggressivePartialUnrollPhase.Options.ForceUnroll, true, GraalOptions.PreferUnsignedComparison,
                        false, LoopRotationPhase.Options.LoopRotation, false), "testGuardedPatternCannotUnroll");
        mustNotUnroll = false;
    }

    public static int testGuardedPatternCannotUnroll1(int limit) {
        int i = 0;
        int inductionVariable2 = 0;
        if (i < limit) {
            while (true) {
                GraalDirectives.sideEffect(i);
                int oldI = i;
                if (GraalDirectives.injectIterationCount(10000, ++i < limit)) {
                    inductionVariable2 = Math.addExact(oldI, 1);
                    continue;
                } else {
                    break;
                }
            }
        }
        return i + inductionVariable2;
    }

    /**
     * Test that we are actually not unrolling a loop that looks inverted but is not.
     */
    @Test
    public void testGuardPattern1() {
        mustNotUnroll = true;
        test(new OptionValues(getOptionsPreMainPostOnly(), GraalOptions.SpeculativeGuardMovement, false, AggressivePartialUnrollPhase.Options.ForceUnroll, true, GraalOptions.PreferUnsignedComparison,
                        false, LoopRotationPhase.Options.LoopRotation, false, jdk.graal.compiler.core.phases.MidTier.Options.StripMineNonCountedLoops, false),
                        "testGuardedPatternCannotUnroll1", 10000);
        mustNotUnroll = false;
    }

    static Object o1 = new Object();

    public static int naturalExit(int limit) {
        int i = 0;
        if (limit > 100_00) {
            return -1;
        }
        if (i < limit) {
            while (true) {
                k: {
                    GraalDirectives.sideEffect(i);
                    if (o1.hashCode() == 123) {
                        break k;
                    }
                    if (GraalDirectives.injectIterationCount(10000, ++i == limit)) {
                        break k;
                    } else {
                        continue;
                    }
                }
                if (limit > 100_001) {
                    continue;
                }
                break;
            }
        }
        return i;
    }

    @Test
    public void testNaturalExits() {
        mustNotUnroll = true;
        test(new OptionValues(getOptionsPreMainPostOnly(), GraalOptions.SpeculativeGuardMovement, false, AggressivePartialUnrollPhase.Options.ForceUnroll, true, GraalOptions.PreferUnsignedComparison,
                        false, LoopRotationPhase.Options.LoopRotation, false, jdk.graal.compiler.core.phases.MidTier.Options.StripMineNonCountedLoops, false,
                        GraalOptions.LoopPeeling, false),
                        "naturalExit", 10000);
        mustNotUnroll = false;
    }

    @Test
    public void testReRoll() throws InvalidInstalledCodeException {
        StructuredGraph g = parseEager(getResolvedJavaMethod("testPreInvertedSnippet"), AllowAssumptions.YES);
        boolean[] shouldUnrollSequence = new boolean[]{true, true, false, true};
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());
        new DisableOverflownCountedLoopsPhase().apply(g);

        DefaultLoopPolicies ep = new DefaultLoopPolicies() {
            int calls;

            @Override
            public UnswitchingDecision shouldUnswitch(Loop loop, EconomicMap<ValueNode, List<ControlSplitNode>> controlSplits) {
                return UnswitchingDecision.NO;
            }

            @Override
            public boolean shouldTryUnswitch(Loop loop) {
                return false;
            }

            @Override
            public boolean shouldPeel(Loop loop, ControlFlowGraph cfg, CoreProviders providers, int peelingIteration) {
                return false;
            }

            @Override
            public boolean shouldPartiallyUnroll(Loop loop, CoreProviders providers) {
                return calls >= shouldUnrollSequence.length ? false : shouldUnrollSequence[calls++];
            }

            @Override
            public boolean shouldFullUnroll(Loop loop) {
                return false;
            }

            @Override
            public boolean shouldInvert(Loop loop, IfNode controlSplit, CoreProviders providers) {
                return false;
            }
        };
        new AggressivePartialUnrollPhase(ep, CanonicalizerPhase.create()).apply(g, getDefaultHighTierContext());
        Assert.assertEquals(testPreInvertedSnippet(100), getCode(getResolvedJavaMethod("testPreInvertedSnippet"), g).executeVarargs(100));
    }

    static int snippetLastIterationValOriginal() {
        int var19 = 17;
        int var25 = Integer.MAX_VALUE;
        for (int i1 = 0; i1 < 900; i1++) {
            var19 = var25++;
        }
        return var19;
    }

    public static final boolean LOG = false;

    static int snippetLastIterationVal() {
        int var19 = 17;
        int var25 = Integer.MAX_VALUE;
        for (int i1 = 0; i1 < 4; i1++) {
            int tmp = var25;
            var19 = tmp;
            var25 = tmp + 1;

            if (LOG) {
                /*
                 * Originally, this test caused different output in the compiled version, if it
                 * starts to fail again with wrong values enable the logging and check per iteration
                 * values.
                 */
                GraalDirectives.log("tmp=%d\n", tmp);
                GraalDirectives.log("var19=%d\n", var19);
                GraalDirectives.log("var25=%d\n", var25);
                GraalDirectives.log("i1=%d\n", i1);
            }
        }
        return var19;
    }

    @Test
    public void testSnippetLastIteration1() {
        test(new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false), "snippetLastIterationValOriginal");
    }

    @Test
    public void testSnippetLastIteration2() {
        OptionValues opt = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false, LowTier.Options.BreakChainedPhis, false,
                        GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false, AggressivePartialUnrollPhase.Options.InsertPreMainPostOnly, true, LoopRotationPhase.Options.LoopRotation,
                        false);
        test(opt, "snippetLastIterationVal");
    }
}
