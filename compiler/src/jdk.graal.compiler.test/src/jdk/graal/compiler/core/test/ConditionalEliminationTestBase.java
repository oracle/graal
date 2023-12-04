/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

/**
 * Collection of tests for {@link ConditionalEliminationPhase} including those that triggered bugs
 * in this phase.
 */
public class ConditionalEliminationTestBase extends GraalCompilerTest {
    protected static int sink0;
    protected static int sink1;
    protected static int sink2;

    /**
     * These tests assume all code paths in called routines are reachable so disable removal of dead
     * code based on method profiles.
     */
    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.RemoveNeverExecutedCode);
    }

    protected void testConditionalElimination(String snippet, String referenceSnippet) {
        testConditionalElimination(snippet, referenceSnippet, false, false);
    }

    @SuppressWarnings("try")
    protected void testConditionalElimination(String snippet, String referenceSnippet, boolean applyConditionalEliminationOnReference, boolean applyLowering) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        CoreProviders context = getProviders();
        CanonicalizerPhase canonicalizer1 = createCanonicalizerPhase();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        try (DebugContext.Scope scope = debug.scope("ConditionalEliminationTest", graph)) {
            prepareGraph(graph, canonicalizer1, context, applyLowering);
            new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);
            canonicalizer.apply(graph, context);
            canonicalizer.apply(graph, context);
        } catch (Throwable t) {
            debug.handle(t);
        }
        StructuredGraph referenceGraph = parseEager(referenceSnippet, AllowAssumptions.YES);
        try (DebugContext.Scope scope = debug.scope("ConditionalEliminationTest.ReferenceGraph", referenceGraph)) {
            prepareGraph(referenceGraph, canonicalizer, context, applyLowering);
            if (applyConditionalEliminationOnReference) {
                new ConditionalEliminationPhase(canonicalizer, true).apply(referenceGraph, context);
            }
            canonicalizer.apply(referenceGraph, context);
        } catch (Throwable t) {
            debug.handle(t);
        }
        assertEquals(referenceGraph, graph);
    }

    protected void prepareGraph(StructuredGraph graph, CanonicalizerPhase canonicalizer, CoreProviders context, boolean applyLowering) {
        if (applyLowering) {
            new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, context);
            new HighTierLoweringPhase(canonicalizer).apply(graph, context);
            canonicalizer.apply(graph, context);
        }
        canonicalizer.apply(graph, context);
        new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, context);
    }

    public void testProxies(String snippet, int expectedProxiesCreated) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        CoreProviders context = getProviders();
        CanonicalizerPhase canonicalizer1 = CanonicalizerPhase.createWithoutCFGSimplification();
        canonicalizer1.apply(graph, context);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        new HighTierLoweringPhase(canonicalizer).apply(graph, context);
        canonicalizer.apply(graph, context);

        int baseProxyCount = graph.getNodes().filter(ProxyNode.class).count();
        new ConditionalEliminationPhase(canonicalizer, true).apply(graph, context);
        new SchedulePhase(graph.getOptions()).apply(graph, context);
        int actualProxiesCreated = graph.getNodes().filter(ProxyNode.class).count() - baseProxyCount;
        Assert.assertEquals(expectedProxiesCreated, actualProxiesCreated);
    }
}
