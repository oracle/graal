/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.test;

import java.util.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.java.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.virtual.phases.ea.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

public class PartialEvaluationTest extends GraalCompilerTest {

    private static final long UNROLL_LIMIT = 100;
    private final TruffleCompilerImpl truffleCompiler;

    public PartialEvaluationTest() {
        // Make sure Truffle runtime is initialized.
        Assert.assertTrue(Truffle.getRuntime() != null);
        this.truffleCompiler = new TruffleCompilerImpl();

        DebugEnvironment.initialize(System.out);
    }

    protected InstalledCode assertPartialEvalEquals(String methodName, RootNode root) {
        return assertPartialEvalEquals(methodName, root, new Object[0]);
    }

    protected InstalledCode assertPartialEvalEquals(String methodName, RootNode root, Object[] arguments) {
        Assumptions assumptions = new Assumptions(true);
        StructuredGraph actual = partialEval(root, arguments, assumptions, true);
        InstalledCode result = new InstalledCode();
        truffleCompiler.compileMethodHelper(actual, assumptions, root.toString(), getSpeculationLog(), result);
        StructuredGraph expected = parseForComparison(methodName);
        removeFrameStates(actual);
        Assert.assertEquals(getCanonicalGraphString(expected, true, true), getCanonicalGraphString(actual, true, true));
        return result;
    }

    protected void assertPartialEvalNoInvokes(RootNode root) {
        assertPartialEvalNoInvokes(root, new Object[0]);
    }

    protected void assertPartialEvalNoInvokes(RootNode root, Object[] arguments) {
        Assumptions assumptions = new Assumptions(true);
        StructuredGraph actual = partialEval(root, arguments, assumptions, true);
        removeFrameStates(actual);
        for (MethodCallTargetNode node : actual.getNodes(MethodCallTargetNode.class)) {
            Assert.fail("Found invalid method call target node: " + node);
        }
    }

    protected StructuredGraph partialEval(RootNode root, Object[] arguments, final Assumptions assumptions, final boolean canonicalizeReads) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root);

        // Executed AST so that all classes are loaded and initialized.
        compilable.call(arguments);
        compilable.call(arguments);
        compilable.call(arguments);
        compilable.performInlining();

        try (Scope s = Debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {

            StructuredGraph resultGraph = truffleCompiler.getPartialEvaluator().createGraph(compilable, assumptions);
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase(canonicalizeReads);
            PhaseContext context = new PhaseContext(getProviders(), assumptions);

            if (resultGraph.hasLoops()) {
                boolean unrolled;
                do {
                    unrolled = false;
                    LoopsData loopsData = new LoopsData(resultGraph);
                    loopsData.detectedCountedLoops();
                    for (LoopEx ex : innerLoopsFirst(loopsData.countedLoops())) {
                        if (ex.counted().isConstantMaxTripCount()) {
                            long constant = ex.counted().constantMaxTripCount();
                            if (constant <= UNROLL_LIMIT) {
                                LoopTransformations.fullUnroll(ex, context, canonicalizer);
                                Debug.dump(resultGraph, "After loop unrolling %d times", constant);

                                canonicalizer.apply(resultGraph, context);
                                unrolled = true;
                                break;
                            }
                        }
                    }
                } while (unrolled);
            }

            new DeadCodeEliminationPhase().apply(resultGraph);
            new PartialEscapePhase(true, canonicalizer).apply(resultGraph, context);

            return resultGraph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private static List<LoopEx> innerLoopsFirst(Collection<LoopEx> loops) {
        ArrayList<LoopEx> sortedLoops = new ArrayList<>(loops);
        Collections.sort(sortedLoops, new Comparator<LoopEx>() {

            @Override
            public int compare(LoopEx o1, LoopEx o2) {
                return o2.lirLoop().getDepth() - o1.lirLoop().getDepth();
            }
        });
        return sortedLoops;
    }

    protected void removeFrameStates(StructuredGraph graph) {
        for (FrameState frameState : graph.getNodes(FrameState.class)) {
            frameState.replaceAtUsages(null);
            frameState.safeDelete();
        }
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), new Assumptions(false)));
        new DeadCodeEliminationPhase().apply(graph);
    }

    protected StructuredGraph parseForComparison(final String methodName) {

        try (Scope s = Debug.scope("Truffle", new DebugDumpScope("Comparison: " + methodName))) {
            Assumptions assumptions = new Assumptions(false);
            StructuredGraph graph = parse(methodName);
            PhaseContext context = new PhaseContext(getProviders(), assumptions);
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase(true);
            canonicalizer.apply(graph, context);

            // Additional inlining.
            PhaseSuite<HighTierContext> graphBuilderSuite = getCustomGraphBuilderSuite(GraphBuilderConfiguration.getEagerInfopointDefault());
            graphBuilderSuite.appendPhase(canonicalizer);
            graphBuilderSuite.appendPhase(new DeadCodeEliminationPhase());

            new ConvertDeoptimizeToGuardPhase().apply(graph);
            canonicalizer.apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);

            HighTierContext highTierContext = new HighTierContext(getProviders(), assumptions, null, graphBuilderSuite, TruffleCompilerImpl.Optimizations);
            InliningPhase inliningPhase = new InliningPhase(canonicalizer);
            inliningPhase.apply(graph, highTierContext);
            removeFrameStates(graph);

            new ConvertDeoptimizeToGuardPhase().apply(graph);
            canonicalizer.apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);

            new LoweringPhase(new CanonicalizerPhase(true), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            canonicalizer.apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
