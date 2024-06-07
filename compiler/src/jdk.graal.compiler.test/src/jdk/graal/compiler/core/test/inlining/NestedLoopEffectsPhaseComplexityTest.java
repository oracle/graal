/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.collections.EconomicSet;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class NestedLoopEffectsPhaseComplexityTest extends GraalCompilerTest {

    public static int IntSideEffect;
    public static int[] Memory = new int[]{0};

    public static void recursiveLoopMethodUnsafeLoad(int a) {
        long arrayIntBaseOffset = Unsafe.ARRAY_INT_BASE_OFFSET;
        if (UNSAFE.getInt(Memory, arrayIntBaseOffset) == 0) {
            return;
        }
        for (int i = 0; i < a; i++) {
            recursiveLoopMethodUnsafeLoad(i);
        }
    }

    public static void recursiveLoopMethodFieldLoad(int a) {
        if (IntSideEffect == 0) {
            return;
        }
        for (int i = 0; i < a; i++) {
            recursiveLoopMethodFieldLoad(i);
        }
    }

    public static void recursiveLoopMethod(int a) {
        if (a == 0) {
            return;
        }
        for (int i = 0; i < a; i++) {
            recursiveLoopMethod(i);
        }
    }

    private static final boolean LOG_PHASE_TIMINGS = false;
    private static int InliningCountLowerBound = 1;
    private static int InliningCountUpperBound = 32;

    @Rule public TestRule timeout = createTimeoutSeconds(240);

    @Test
    public void inlineDirectRecursiveLoopCallUnsafeLoad() {
        testAndTime("recursiveLoopMethodUnsafeLoad");
    }

    @Test
    public void inlineDirectRecursiveLoopCallFieldLoad() {
        testAndTime("recursiveLoopMethodFieldLoad");
    }

    @Test
    public void inlineDirectRecursiveLoopCallNoReads() {
        testAndTime("recursiveLoopMethod");
    }

    private void testAndTime(String snippet) {
        for (int i = InliningCountLowerBound; i < InliningCountUpperBound; i++) {
            StructuredGraph g1 = prepareGraph(snippet, i);
            StructuredGraph g2 = (StructuredGraph) g1.copy(g1.getDebug());
            ResolvedJavaMethod method = g1.method();
            long elapsedRE = runAndTimePhase(g1, new ReadEliminationPhase(createCanonicalizerPhase()));
            long elapsedPEA = runAndTimePhase(g2, new PartialEscapePhase(true, createCanonicalizerPhase(), g1.getOptions()));
            if (LOG_PHASE_TIMINGS) {
                TTY.printf("Needed %dms to run early partial escape analysis on a graph with %d nested loops compiling method %s\n", elapsedPEA, i, method);
            }
            if (LOG_PHASE_TIMINGS) {
                TTY.printf("Needed %dms to run early read elimination on a graph with %d nested loops compiling method %s\n", elapsedRE, i, method);
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
        ResolvedJavaMethod callerMethod = getResolvedJavaMethod(snippet);
        StructuredGraph callerGraph = parseEager(callerMethod, AllowAssumptions.YES);
        PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
        HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        Invoke next = callerGraph.getNodes(MethodCallTargetNode.TYPE).first().invoke();
        StructuredGraph calleeGraph = parseBytecodes(next.callTarget().targetMethod(), context, canonicalizer);
        ResolvedJavaMethod calleeMethod = next.callTarget().targetMethod();
        for (int i = 0; i < inliningCount; i++) {
            next = callerGraph.getNodes(MethodCallTargetNode.TYPE).first().invoke();
            EconomicSet<Node> canonicalizeNodes = InliningUtil.inlineForCanonicalization(next, calleeGraph, false, calleeMethod, null,
                            "Called explicitly from a unit test.", "Test case");
            canonicalizer.applyIncremental(callerGraph, context, canonicalizeNodes);
            callerGraph.getDebug().dump(DebugContext.DETAILED_LEVEL, callerGraph, "After inlining %s into %s iteration %d", calleeMethod, callerMethod, i);
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
