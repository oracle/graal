/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop.test;

import static org.graalvm.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static org.graalvm.compiler.api.directives.GraalDirectives.injectIterationCount;

import java.util.ListIterator;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.loop.phases.LoopPartialUnrollPhase;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.loop.DefaultLoopPolicies;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopFragmentInside;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.DeoptimizationGroupingPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.junit.Ignore;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class LoopPartialUnrollTest extends GraalCompilerTest {

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        NodeIterable<LoopBeginNode> loops = graph.getNodes().filter(LoopBeginNode.class);
        // Loops might be optimizable after partial unrolling
        if (!loops.isEmpty()) {
            for (LoopBeginNode loop : loops) {
                if (loop.isMainLoop()) {
                    return;
                }
            }
            fail("expected a main loop");
        }
    }

    public static long sumWithEqualityLimit(int[] text) {
        long sum = 0;
        for (int i = 0; injectBranchProbability(0.99, i != text.length); ++i) {
            sum += volatileInt;
        }
        return sum;
    }

    @Ignore("equality limits aren't working properly")
    @Test
    public void testSumWithEqualityLimit() {
        for (int i = -1; i < 128; i++) {
            int[] data = new int[i];
            test("sumWithEqualityLimit", data);
        }
    }

    @Test
    public void testLoopCarried() {
        for (int i = -1; i < 64; i++) {
            test("testLoopCarriedSnippet", i);
        }
    }

    @Test
    public void testLoopCarriedDuplication() {
        testDuplicateBody("testLoopCarriedReference", "testLoopCarriedSnippet");
    }

    static volatile int volatileInt = 3;

    public static int testLoopCarriedSnippet(int iterations) {
        int a = 0;
        int b = 0;
        int c = 0;

        for (int i = 0; injectBranchProbability(0.99, i < iterations); i++) {
            int t1 = volatileInt;
            int t2 = a + b;
            c = b;
            b = a;
            a = t1 + t2;
        }

        return c;
    }

    public static int testLoopCarriedReference(int iterations) {
        int a = 0;
        int b = 0;
        int c = 0;

        for (int i = 0; injectBranchProbability(0.99, i < iterations); i += 2) {
            int t1 = volatileInt;
            int t2 = a + b;
            c = b;
            b = a;
            a = t1 + t2;
            t1 = volatileInt;
            t2 = a + b;
            c = b;
            b = a;
            a = t1 + t2;
        }

        return c;
    }

    @Test
    @Ignore
    public void testUnsignedLoopCarried() {
        for (int i = -1; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                test("testUnsignedLoopCarriedSnippet", i, j);
            }
        }
        test("testUnsignedLoopCarriedSnippet", -1 - 32, -1);
        test("testUnsignedLoopCarriedSnippet", -1 - 4, -1);
        test("testUnsignedLoopCarriedSnippet", -1 - 32, 0);
    }

    public static int testUnsignedLoopCarriedSnippet(int start, int end) {
        int a = 0;
        int b = 0;
        int c = 0;

        for (int i = start; injectBranchProbability(0.99, Integer.compareUnsigned(i, end) < 0); i++) {
            int t1 = volatileInt;
            int t2 = a + b;
            c = b;
            b = a;
            a = t1 + t2;
        }

        return c;
    }

    @Test
    public void testLoopCarried2() {
        for (int i = -1; i < 64; i++) {
            for (int j = -1; j < 64; j++) {
                test("testLoopCarried2Snippet", i, j);
            }
        }
        test("testLoopCarried2Snippet", Integer.MAX_VALUE - 32, Integer.MAX_VALUE);
        test("testLoopCarried2Snippet", Integer.MAX_VALUE - 4, Integer.MAX_VALUE);
        test("testLoopCarried2Snippet", Integer.MAX_VALUE, 0);
        test("testLoopCarried2Snippet", Integer.MIN_VALUE, Integer.MIN_VALUE + 32);
        test("testLoopCarried2Snippet", Integer.MIN_VALUE, Integer.MIN_VALUE + 4);
        test("testLoopCarried2Snippet", 0, Integer.MIN_VALUE);
    }

    public static int testLoopCarried2Snippet(int start, int end) {
        int a = 0;
        int b = 0;
        int c = 0;

        for (int i = start; injectBranchProbability(0.99, i < end); i++) {
            int t1 = volatileInt;
            int t2 = a + b;
            c = b;
            b = a;
            a = t1 + t2;
        }

        return c;
    }

    public static long init = Runtime.getRuntime().totalMemory();
    private int x;
    private int z;

    public int[] testComplexSnippet(int d) {
        x = 3;
        int y = 5;
        z = 7;
        for (int i = 0; i < d; i++) {
            for (int j = 0; injectBranchProbability(0.99, j < i); j++) {
                z += x;
            }
            y = x ^ z;
            if ((i & 4) == 0) {
                z--;
            } else if ((i & 8) == 0) {
                Runtime.getRuntime().totalMemory();
            }
        }
        return new int[]{x, y, z};
    }

    @Test
    public void testComplex() {
        for (int i = -1; i < 10; i++) {
            test("testComplexSnippet", i);
        }
        test("testComplexSnippet", 10);
        test("testComplexSnippet", 100);
        test("testComplexSnippet", 1000);
    }

    public static long testSignExtensionSnippet(long arg) {
        long r = 1;
        for (int i = 0; injectBranchProbability(0.99, i < arg); i++) {
            r *= i;
        }
        return r;
    }

    @Test
    public void testSignExtension() {
        test("testSignExtensionSnippet", 9L);
    }

    public static long deoptExitSnippet(long arg) {
        long r = 1;
        int i = 0;
        while (true) {
            if (injectBranchProbability(0.99, i >= arg)) {
                GraalDirectives.deoptimizeAndInvalidate();
                GraalDirectives.sideEffect(i);
                if (i == 123) {
                    continue;
                }
                break;
            }
            r *= i;
            i++;
        }
        return r;
    }

    @Test
    public void deoptExitTest() {
        test("deoptExitSnippet", 9L);
    }

    public static Object objectPhi(int n) {
        Integer v = Integer.valueOf(200);
        GraalDirectives.blackhole(v); // Prevents PEA
        Integer r = 1;

        for (int i = 0; injectIterationCount(100, i < n); i++) {
            GraalDirectives.blackhole(r); // Create a phi of two loop invariants
            r = v;
        }

        return r;
    }

    @Test
    public void testObjectPhi() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        test(options, "objectPhi", 1);
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites suites = super.createSuites(opts).copy();
        PhaseSuite<MidTierContext> mid = suites.getMidTier();
        ListIterator<BasePhase<? super MidTierContext>> iter = mid.findPhase(LoopPartialUnrollPhase.class);
        BasePhase<? super MidTierContext> partialUnoll = iter.previous();
        if (iter.previous().getClass() != FrameStateAssignmentPhase.class) {
            // Ensure LoopPartialUnrollPhase runs immediately after FrameStateAssignment, so it gets
            // priority over other optimizations in these tests.
            mid.findPhase(LoopPartialUnrollPhase.class).remove();
            ListIterator<BasePhase<? super MidTierContext>> fsa = mid.findPhase(FrameStateAssignmentPhase.class);
            fsa.add(partialUnoll);
        }
        return suites;
    }

    public void testGraph(String reference, String test) {
        StructuredGraph referenceGraph = buildGraph(reference, false);
        StructuredGraph testGraph = buildGraph(test, true);
        assertEquals(referenceGraph, testGraph, false, false);
    }

    @SuppressWarnings("try")
    public StructuredGraph buildGraph(String name, boolean partialUnroll) {
        CompilationIdentifier id = new CompilationIdentifier() {
            @Override
            public String toString(Verbosity verbosity) {
                return name;
            }
        };
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        OptionValues options = new OptionValues(getInitialOptions(), DefaultLoopPolicies.Options.UnrollMaxIterations, 2);
        StructuredGraph graph = parse(builder(method, StructuredGraph.AllowAssumptions.YES, id, options), getEagerGraphBuilderSuite());
        try (DebugContext.Scope buildScope = graph.getDebug().scope(name, method, graph)) {
            MidTierContext context = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, null);

            CanonicalizerPhase canonicalizer = this.createCanonicalizerPhase();
            canonicalizer.apply(graph, context);
            new RemoveValueProxyPhase().apply(graph);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            new FloatingReadPhase().apply(graph);
            new DeadCodeEliminationPhase().apply(graph);
            new ConditionalEliminationPhase(true).apply(graph, context);
            new GuardLoweringPhase().apply(graph, context);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, context);
            new FrameStateAssignmentPhase().apply(graph);
            new DeoptimizationGroupingPhase().apply(graph, context);
            canonicalizer.apply(graph, context);
            new ConditionalEliminationPhase(true).apply(graph, context);
            if (partialUnroll) {
                LoopsData dataCounted = getDefaultMidTierContext().getLoopsDataProvider().getLoopsData(graph);
                dataCounted.detectedCountedLoops();
                assertTrue(!dataCounted.countedLoops().isEmpty(), "must have counted loops");
                for (LoopEx loop : dataCounted.countedLoops()) {
                    LoopFragmentInside newSegment = loop.inside().duplicate();
                    newSegment.insertWithinAfter(loop, null);
                }
                canonicalizer.apply(graph, getDefaultMidTierContext());
            }
            new DeadCodeEliminationPhase().apply(graph);
            canonicalizer.apply(graph, context);
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "before compare");
            return graph;
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    public void testDuplicateBody(String reference, String test) {

        StructuredGraph referenceGraph = buildGraph(reference, false);
        StructuredGraph testGraph = buildGraph(test, true);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        canonicalizer.apply(testGraph, getDefaultMidTierContext());
        canonicalizer.apply(referenceGraph, getDefaultMidTierContext());
        assertEquals(referenceGraph, testGraph);
    }
}
