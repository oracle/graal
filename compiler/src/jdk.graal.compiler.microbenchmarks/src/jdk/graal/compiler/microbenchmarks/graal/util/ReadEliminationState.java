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
package jdk.graal.compiler.microbenchmarks.graal.util;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.virtual.phases.ea.LowTierReadEliminationPhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;

/**
 * Prepares graphs immediately before read elimination in the default compilation pipeline.
 * {@link GraphState} copies the prepared graph outside each timed invocation.
 */
public class ReadEliminationState {

    public static class Early extends GraphState {
        public BasePhase<? super HighTierContext> phase;
        public HighTierContext context;

        @Override
        protected StructuredGraph preprocessOriginal(StructuredGraph graph) {
            context = new HighTierContext(graal.providers, graal.backend.getSuites().getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
            Suites suites = graal.backend.getSuites().getDefaultSuites(graal.options, graal.backend.getTarget().arch);
            phase = prepareGraph(graph, suites.getHighTier(), context, ReadEliminationPhase.class);
            return graph;
        }
    }

    public static class LowTier extends GraphState {
        public BasePhase<? super LowTierContext> phase;
        public LowTierContext context;

        @Override
        protected StructuredGraph preprocessOriginal(StructuredGraph graph) {
            Suites suites = graal.backend.getSuites().getDefaultSuites(graal.options, graal.backend.getTarget().arch);
            HighTierContext highTierContext = new HighTierContext(graal.providers, graal.backend.getSuites().getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
            suites.getHighTier().apply(graph, highTierContext);
            suites.getMidTier().apply(graph, new MidTierContext(graal.providers, graal.backend, OptimisticOptimizations.ALL, graph.getProfilingInfo()));
            context = new LowTierContext(graal.providers, graal.backend);
            phase = prepareGraph(graph, suites.getLowTier(), context, LowTierReadEliminationPhase.class);
            return graph;
        }
    }

    private static <C> BasePhase<? super C> prepareGraph(StructuredGraph graph, PhaseSuite<C> suite, C context, Class<?> phaseClass) {
        for (BasePhase<? super C> phase : suite.getPhases()) {
            if (phaseClass.isInstance(phase)) {
                return phase;
            }
            phase.apply(graph, context);
        }
        throw new IllegalStateException("Compilation suite does not contain " + phaseClass.getSimpleName());
    }
}
