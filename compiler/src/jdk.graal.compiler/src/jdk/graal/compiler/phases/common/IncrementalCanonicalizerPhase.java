/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.EnumSet;
import java.util.Optional;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeWorkList;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;

/**
 * The phase that does the work of {@link CanonicalizerPhase#applyIncremental}. This phase contains
 * mutable state so it should only be created and used transiently.
 */
public class IncrementalCanonicalizerPhase extends CanonicalizerPhase {

    private final StructuredGraph initialGraph;
    private final Tool theTool;

    IncrementalCanonicalizerPhase(EnumSet<CanonicalizerFeature> features, CustomSimplification customSimplification, StructuredGraph graph, CoreProviders context, Graph.Mark newNodesMark) {
        this(features, customSimplification, graph, context, graph.getNewNodes(newNodesMark));
    }

    IncrementalCanonicalizerPhase(EnumSet<CanonicalizerFeature> features, CustomSimplification customSimplification, StructuredGraph graph, CoreProviders context,
                    Iterable<? extends Node> workingSet) {
        super(customSimplification, features);
        this.initialGraph = graph;
        NodeWorkList workList = graph.createIterativeNodeWorkList(false, MAX_ITERATION_PER_NODE);
        workList.addAll(workingSet);
        theTool = new Tool(graph, context, workList);
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    public Optional<BasePhase.NotApplicable> notApplicableTo(GraphState graphState) {
        GraalError.guarantee(!theTool.finalCanonicalization(), "Final canonicalization must not be incremental");
        return super.notApplicableTo(graphState);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        GraalError.guarantee(graph == this.initialGraph, "Canonicalizer instances contain graph-specific state, they must be applied to the graph used during construction.");
        processWorkSet(graph, theTool);
    }

    /**
     * Helper class to apply incremental canonicalization using scopes.
     */
    public static class Apply implements BasePhase.ApplyScope {
        private final EconomicSetNodeEventListener listener;
        private final StructuredGraph graph;
        private final CoreProviders context;
        private final CanonicalizerPhase canonicalizer;
        private final Graph.NodeEventScope scope;

        public Apply(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer) {
            assert canonicalizer != null;
            this.graph = graph;
            this.context = context;
            this.canonicalizer = canonicalizer;
            this.listener = new EconomicSetNodeEventListener();
            scope = graph.trackNodeEvents(listener);

            // The BasePhase dumping will emit an after dump at VERBOSE_LEVEL and a before dump at
            // VERBOSE_LEVEL + 1. Emitting a before dump here at VERBOSE_LEVEL makes it easier to
            // see the effects of the primary phase before the incremental canonicalization cleans
            // up the graph.
            if (graph.getDebug().isDumpEnabled(DebugContext.VERBOSE_LEVEL) && !graph.getDebug().isDumpEnabled(DebugContext.VERBOSE_LEVEL + 1)) {
                graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Before subphase %s", canonicalizer.getName());
            }
        }

        @Override
        public void close(Throwable throwable) {
            scope.close();
            if (throwable == null) {
                // Perform the canonicalization if the main work completed without an exception.
                if (!listener.getNodes().isEmpty()) {
                    canonicalizer.applyIncremental(graph, context, listener.getNodes());
                }
            }
        }
    }
}
