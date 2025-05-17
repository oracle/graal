/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.inlining;

import static jdk.graal.compiler.debug.DebugOptions.DumpOnError;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.collections.EconomicSet;
import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.RetryableBailoutException;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class NestedLoopEffectsPhaseComplexityTest2 extends GraalCompilerTest {

    public static int IntSideEffect;

    public static class A {
        int x;

        A(int x) {
            this.x = x;
        }
    }

    public static class B {
        int x;

        B(int x) {
            this.x = x;
        }
    }

    public static class C {
        int x;

        C(int x) {
            this.x = x;
        }
    }

    public static int method20LevelNoNewAllocationsEnsureVirtualizedWronglyMaterialized(int a) {
        if (a == 0) {
            return 0;
        }
        int res = 0;
        for (int i = 0; i < a; i++) {
            C c = new C(i);
            GraalDirectives.ensureVirtualized(c);
            res += new A(method20LevelNoNewAllocations1EnsureVirtualizedWronglyMaterialized(20, c)).x;
        }
        return res;
    }

    public static int method20LevelNoNewAllocations1EnsureVirtualizedWronglyMaterialized(int a, Object o) {
        if (GraalDirectives.injectBranchProbability(0.01D, a == 0)) {
            // materialize the escaped object
            OSideEffect = o;
            return 0;
        }
        int res = 0;
        for (int i = 0; i < IntSideEffect; i++) {
            res += new A(method20LevelNoNewAllocations1EnsureVirtualizedWronglyMaterialized(a - 1, o)).x;
        }
        return res;
    }

    /**
     * Test that the depth cutoff of partial escape analysis triggers after the correct loop depth
     * and that no new virtualizations are performed once we reach a certain depth.
     */
    @Test
    public void testNoNewAllocationsEnsureVirtualizedWronglyMaterialized() {
        method20LevelNoNewAllocationsEnsureVirtualizedWronglyMaterialized(0);
        method20LevelNoNewAllocationsEnsureVirtualizedWronglyMaterialized(1);
        method20LevelNoNewAllocationsEnsureVirtualizedWronglyMaterialized(0);
        method20LevelNoNewAllocationsEnsureVirtualizedWronglyMaterialized(1);
        method20LevelNoNewAllocationsEnsureVirtualizedWronglyMaterialized(3);
        try {
            testAndTimeFixedDepth("method20LevelNoNewAllocationsEnsureVirtualizedWronglyMaterialized", 1);
            Assert.fail("PEA should run into a bailout");
        } catch (RetryableBailoutException e) {
            Assert.assertTrue(e.getMessage().contains("ensureVirtualized"));
        }
    }

    /**
     * Very deep loop nests, once {@linkplain GraalOptions#EscapeAnalysisLoopCutoff} is reached, no
     * new virtualizations are performed except the one that has ensure virtualized set/used.
     */
    public static int method20LevelNoNewAllocationsEnsureVirtualized(int a) {
        if (a == 0) {
            return 0;
        }
        int res = 0;
        for (int i = 0; i < a; i++) {
            res += new A(method20LevelNoNewAllocations1EnsureVirtualized(20)).x;
        }
        return res;
    }

    public static int method20LevelNoNewAllocations1EnsureVirtualized(int a) {
        if (GraalDirectives.injectBranchProbability(0.01D, a == 0)) {
            B b = new B(a);
            GraalDirectives.ensureVirtualized(b);
            return b.x;
        }
        int res = 0;
        for (int i = 0; i < IntSideEffect; i++) {
            res += new A(method20LevelNoNewAllocations1EnsureVirtualized(a - 1)).x;
        }
        return res;
    }

    /**
     * Test that the depth cutoff of partial escape analysis triggers after the correct loop depth
     * and that no new virtualizations are performed once we reach a certain depth.
     */
    @Test
    public void testNoNewAllocationsEnsureVirtualized() {
        method20LevelNoNewAllocations(0);
        method20LevelNoNewAllocations(1);
        method20LevelNoNewAllocations1(0);
        method20LevelNoNewAllocations1(1);
        /*
         * 2 remaining allocations = 1 times the >= depth level allocation of a and one allocations
         * of b inside
         */
        testAndTimeFixedDepth("method20LevelNoNewAllocationsEnsureVirtualized", 2);
    }

    /**
     * Very deep loop nests, once {@linkplain GraalOptions#EscapeAnalysisLoopCutoff} is reached, no
     * new virtualizations are performed. We check this by ensuring that the allocations of B >
     * level remain
     */
    public static int method20LevelNoNewAllocations(int a) {
        if (a == 0) {
            return 0;
        }
        int res = 0;
        for (int i = 0; i < a; i++) {
            res += new A(method20LevelNoNewAllocations1(20)).x;
        }
        return res;
    }

    public static int method20LevelNoNewAllocations1(int a) {
        if (GraalDirectives.injectBranchProbability(0.01D, a == 0)) {
            return new B(a).x;
        }
        int res = 0;
        for (int i = 0; i < IntSideEffect; i++) {
            res += new A(method20LevelNoNewAllocations1(a - 1)).x;
        }
        return res;
    }

    /**
     * Test that the depth cutoff of partial escape analysis triggers after the correct loop depth
     * and that no new virtualizations are performed once we reach a certain depth.
     */
    @Test
    public void testNoNewAllocations() {
        method20LevelNoNewAllocations(0);
        method20LevelNoNewAllocations(1);
        method20LevelNoNewAllocations1(0);
        method20LevelNoNewAllocations1(1);
        /*
         * 2 remaining allocations = 1 times the >= depth level allocation of a and one allocations
         * of b inside
         */
        testAndTimeFixedDepth("method20LevelNoNewAllocations", 2);
    }

    public static int method20LevelEscapeInInnerMostAllocations(int a) {
        if (a == 0) {
            return 0;
        }
        int res = 0;
        for (int i = 0; i < a; i++) {
            res += new A(method20LevelEscapeInInnerMostAllocations1(20, new C(i))).x;
        }
        // will EA again
        res += new C(a).x;
        res += new C(a).x;
        res += new C(a).x;
        res += new C(a).x;
        res += new C(a).x;
        res += new C(a).x;
        res += new C(a).x;
        res += new C(a).x;
        return res;
    }

    static Object OSideEffect;

    public static int method20LevelEscapeInInnerMostAllocations1(int a, Object otherObject) {
        if (GraalDirectives.injectBranchProbability(0.01D, a == 0)) {
            /*
             * this will escape our object in the inner most section, thus we will throw out of all
             * loops and continue PEA of the entire loop nest without EA
             */
            OSideEffect = otherObject;
            return new B(a).x;
        }
        int res = 0;
        for (int i = 0; i < IntSideEffect; i++) {
            res += new A(method20LevelEscapeInInnerMostAllocations1(a - 1, otherObject)).x;
        }
        return res;
    }

    @Test
    public void testEscapeInInnerMost() {
        method20LevelEscapeInInnerMostAllocations(0);
        method20LevelEscapeInInnerMostAllocations(1);
        method20LevelEscapeInInnerMostAllocations1(0, null);
        method20LevelEscapeInInnerMostAllocations1(1, null);
        /*
         * 23 = all inside the loop, the caller one inside the loop and the final B one
         */
        testAndTimeFixedDepth("method20LevelEscapeInInnerMostAllocations", 23);
    }

    /**
     * Very deep loop nests, once {@linkplain GraalOptions#EscapeAnalysisLoopCutoff} is reached, no
     * new virtualizations are performed.
     */
    public static int recursiveLoopMethodFieldLoad(int a) {
        if (IntSideEffect == 0) {
            return 1;
        }
        int res = 0;
        for (int i = 0; i < a; i++) {
            res += new A(recursiveLoopMethodFieldLoad(i)).x;
        }
        return res;
    }

    public static int recursiveLoopMethodFieldLoadWithPrevAlloc(int a) {
        if (IntSideEffect == 0) {
            return 1;
        }
        C c = new C(0);
        C d = new C(0);
        for (int i = 0; i < a; i++) {
            IntSideEffect = c.x;
            c = new C(recursiveLoopMethodFieldLoadWithPrevAlloc(i));
            OSideEffect = d;
        }
        OSideEffect = c;
        return c.x;
    }

    @Test
    public void testIterative() {
        OptionValues op = new OptionValues(getInitialOptions(), GraalOptions.EscapeAnalysisLoopCutoff, 1);
        prepareGraph("recursiveLoopMethodFieldLoadWithPrevAlloc", 30, true, op);
    }

    public static class AB {
        A x;

        AB(A x) {
            this.x = x;
        }
    }

    static AB BSideEffect;

    public static int recursiveMethod1(int a, @SuppressWarnings("unused") A oe) {
        if (GraalDirectives.injectBranchProbability(0.01D, a <= 0)) {
            return 0;
        }
        int res = 0;
        A aO = new A(12);
        for (int i = 0; GraalDirectives.injectIterationCount(100000000, i < a); i++) {
            res += new A(recursiveMethod2(a - 1, new A(i))).x;
            AB o;
            if (IntSideEffect > 0) {
                o = BSideEffect;
            } else {
                o = new AB(aO);
            }
            res += o.x.x;
        }
        return res;
    }

    public static int recursiveMethod2(int a, A o) {
        if (GraalDirectives.injectBranchProbability(0.01D, a <= 0)) {
            return 0;
        }
        int res = o.x;
        for (int i = 0; GraalDirectives.injectIterationCount(100000000, i < a); i++) {
            res += new A(recursiveMethod1(a - 1, o)).x + o.x;
        }
        OSideEffect = o;
        return res;
    }

    @Test
    public void testIterative2() {
        try (AutoCloseable _ = new TTY.Filter()) {
            OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false, GraalOptions.EscapeAnalysisLoopCutoff, 2);
            prepareGraph("recursiveMethod1", 30, true, options);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    private static final boolean LOG_PHASE_TIMINGS = false;
    private static int InliningCountLowerBound = 1;
    private static int InliningCountUpperBound = 128;

    @Rule public TestRule timeout = createTimeoutSeconds(240);

    private void testAndTimeFixedDepth(String snippet, int remainingNewInstanceOffs) {
        StructuredGraph g1 = prepareGraph(snippet, Integer.MAX_VALUE);
        long elapsedPEA = runAndTimePhase(g1, new PartialEscapePhase(false, createCanonicalizerPhase(), g1.getOptions()));
        if (LOG_PHASE_TIMINGS) {
            TTY.printf("Needed %dms to run early partial escape analysis on a graph with fixed level of loops", elapsedPEA);
        }
        int allocations = 0;
        for (Node n : g1.getNodes()) {
            if (n instanceof NewInstanceNode) {
                allocations++;
            } else if (n instanceof CommitAllocationNode) {
                CommitAllocationNode com = (CommitAllocationNode) n;
                allocations += com.getVirtualObjects().size();
            }
        }
        Assert.assertEquals(remainingNewInstanceOffs, allocations);
    }

    @Test
    public void inlineDirectRecursiveLoopCallAllocation() {
        testAndTime("recursiveLoopMethodFieldLoad");
    }

    private void testAndTime(String snippet) {
        for (int i = InliningCountLowerBound; i < InliningCountUpperBound; i++) {
            StructuredGraph g1 = prepareGraph(snippet, i);
            StructuredGraph g2 = (StructuredGraph) g1.copy(g1.getDebug());
            ResolvedJavaMethod method = g1.method();
            long elapsedPEA = runAndTimePhase(g2, new PartialEscapePhase(false, createCanonicalizerPhase(), g1.getOptions()));
            if (LOG_PHASE_TIMINGS) {
                TTY.printf("Needed %dms to run early partial escape analysis on a graph with %d nested loops compiling method %s\n", elapsedPEA, i, method);
            }
        }
    }

    private long runAndTimePhase(StructuredGraph g, BasePhase<? super CoreProviders> phase) {
        HighTierContext context = getDefaultHighTierContext();
        long start = System.currentTimeMillis();
        phase.apply(g, context);
        long end = System.currentTimeMillis();
        DebugContext debug = g.getDebug();
        debug.dump(DebugContext.DETAILED_LEVEL, g, "After %s", phase.contractorName());
        return end - start;
    }

    private StructuredGraph prepareGraph(String snippet, int inliningCount) {
        return prepareGraph(snippet, inliningCount, false, getInitialOptions());
    }

    private StructuredGraph prepareGraph(String snippet, int inliningCount, boolean peaDuring, OptionValues options) {
        ResolvedJavaMethod callerMethod = getResolvedJavaMethod(snippet);
        StructuredGraph callerGraph = parseEager(callerMethod, AllowAssumptions.NO, options);
        PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
        HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.NONE);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        Invoke next = callerGraph.getNodes(MethodCallTargetNode.TYPE).first().invoke();
        StructuredGraph calleeGraph = parseBytecodes(next.callTarget().targetMethod(), context, canonicalizer);
        ResolvedJavaMethod calleeMethod = next.callTarget().targetMethod();
        if (peaDuring) {
            new PartialEscapePhase(false, createCanonicalizerPhase(),
                            callerGraph.getOptions()).apply(callerGraph, getDefaultHighTierContext());
        }
        for (int i = 0; i < inliningCount; i++) {
            if (callerGraph.getNodes(MethodCallTargetNode.TYPE).isEmpty()) {
                break;
            }
            next = callerGraph.getNodes(MethodCallTargetNode.TYPE).first().invoke();
            EconomicSet<Node> canonicalizeNodes = InliningUtil.inlineForCanonicalization(next, calleeGraph, false, calleeMethod, null,
                            "Called explicitly from a unit test.", "Test case");
            canonicalizer.applyIncremental(callerGraph, context, canonicalizeNodes);
            callerGraph.getDebug().dump(DebugContext.DETAILED_LEVEL, callerGraph, "After inlining %s into %s iteration %d", calleeMethod, callerMethod, i);
            assert calleeGraph.verify();
            if (peaDuring) {
                new PartialEscapePhase(false, createCanonicalizerPhase(),
                                callerGraph.getOptions()).apply(callerGraph, getDefaultHighTierContext());
            }
            assert calleeGraph.verify();
        }

        return callerGraph;
    }

    private StructuredGraph parseBytecodes(ResolvedJavaMethod method, HighTierContext context, CanonicalizerPhase canonicalizer) {
        OptionValues options = getInitialOptions();
        StructuredGraph newGraph = new StructuredGraph.Builder(options, getDebugContext(options, null, method), AllowAssumptions.NO).method(method).speculationLog(getSpeculationLog()).build();
        context.getGraphBuilderSuite().apply(newGraph, context);
        new DeadCodeEliminationPhase(Optional).apply(newGraph);
        canonicalizer.apply(newGraph, context);
        return newGraph;
    }

}
