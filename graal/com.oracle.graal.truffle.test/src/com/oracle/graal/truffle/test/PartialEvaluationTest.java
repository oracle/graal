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
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.java.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.runtime.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.virtual.phases.ea.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class PartialEvaluationTest extends GraalCompilerTest {

    private static final long UNROLL_LIMIT = 100;
    private final PartialEvaluator partialEvaluator;

    public PartialEvaluationTest() {
        // Make sure Truffle runtime is initialized.
        Assert.assertTrue(Truffle.getRuntime() instanceof GraalTruffleRuntime);
        Replacements truffleReplacements = ((GraalTruffleRuntime) Truffle.getRuntime()).getReplacements();
        Providers providers = getProviders().copyWith(truffleReplacements);
        TruffleCache truffleCache = new TruffleCache(providers, GraphBuilderConfiguration.getDefault(), TruffleCompilerImpl.Optimizations);
        this.partialEvaluator = new PartialEvaluator(Graal.getRequiredCapability(RuntimeProvider.class), providers, truffleCache);

        DebugEnvironment.initialize(System.out);
    }

    protected InstalledCode assertPartialEvalEquals(String methodName, RootNode root, FrameDescriptor descriptor) {
        return assertPartialEvalEquals(methodName, root, descriptor, Arguments.EMPTY_ARGUMENTS);
    }

    protected InstalledCode assertPartialEvalEquals(String methodName, RootNode root, FrameDescriptor descriptor, Arguments arguments) {
        Assumptions assumptions = new Assumptions(true);
        StructuredGraph actual = partialEval(root, descriptor, arguments, assumptions, true);
        InstalledCode result = new TruffleCompilerImpl().compileMethodHelper(actual, GraphBuilderConfiguration.getDefault(), assumptions);
        StructuredGraph expected = parseForComparison(methodName);
        removeFrameStates(actual);
        Assert.assertEquals(getCanonicalGraphString(expected, true), getCanonicalGraphString(actual, true));
        return result;
    }

    protected void assertPartialEvalNoInvokes(RootNode root, FrameDescriptor descriptor) {
        assertPartialEvalNoInvokes(root, descriptor, Arguments.EMPTY_ARGUMENTS);
    }

    protected void assertPartialEvalNoInvokes(RootNode root, FrameDescriptor descriptor, Arguments arguments) {
        Assumptions assumptions = new Assumptions(true);
        StructuredGraph actual = partialEval(root, descriptor, arguments, assumptions, true);
        removeFrameStates(actual);
        for (MethodCallTargetNode node : actual.getNodes(MethodCallTargetNode.class)) {
            Assert.fail("Found invalid method call target node: " + node);
        }
    }

    protected StructuredGraph partialEval(RootNode root, FrameDescriptor descriptor, Arguments arguments, final Assumptions assumptions, final boolean canonicalizeReads) {
        final OptimizedCallTarget compilable = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(root, descriptor);

        // Executed AST so that all classes are loaded and initialized.
        do {
            compilable.call(null, arguments);
            compilable.call(null, arguments);
            compilable.call(null, arguments);
        } while (compilable.inline());

        try (Scope s = Debug.scope("TruffleCompilation", new TruffleDebugJavaMethod(compilable))) {

            StructuredGraph resultGraph = partialEvaluator.createGraph(compilable, assumptions);
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
                return o2.lirLoop().depth - o1.lirLoop().depth;
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

    @SuppressWarnings("deprecation")
    protected StructuredGraph parseForComparison(final String methodName) {

        try (Scope s = Debug.scope("Truffle", new DebugDumpScope("Comparison: " + methodName))) {
            Assumptions assumptions = new Assumptions(false);
            StructuredGraph graph = parse(methodName);
            PhaseContext context = new PhaseContext(getProviders(), assumptions);
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase(true);
            canonicalizer.apply(graph, context);

            // Additional inlining.
            final PhasePlan plan = new PhasePlan();
            GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(getMetaAccess(), getForeignCalls(), GraphBuilderConfiguration.getEagerDefault(), TruffleCompilerImpl.Optimizations);
            plan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
            canonicalizer.addToPhasePlan(plan, context);
            plan.addPhase(PhasePosition.AFTER_PARSING, new DeadCodeEliminationPhase());

            new ConvertDeoptimizeToGuardPhase().apply(graph);
            canonicalizer.apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);

            HighTierContext highTierContext = new HighTierContext(getProviders(), assumptions, null, plan, OptimisticOptimizations.NONE);
            InliningPhase inliningPhase = new InliningPhase(canonicalizer);
            inliningPhase.apply(graph, highTierContext);
            removeFrameStates(graph);

            new ConvertDeoptimizeToGuardPhase().apply(graph);
            canonicalizer.apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);

            new LoweringPhase(new CanonicalizerPhase(true)).apply(graph, context);
            canonicalizer.apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
