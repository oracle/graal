/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.loop.phases;

import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.graph.Graph;
import jdk.compiler.graal.nodes.GraphState;
import jdk.compiler.graal.nodes.GraphState.StageFlag;
import jdk.compiler.graal.nodes.LoopBeginNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.extended.OpaqueNode;
import jdk.compiler.graal.nodes.loop.LoopEx;
import jdk.compiler.graal.nodes.loop.LoopPolicies;
import jdk.compiler.graal.nodes.loop.LoopsData;
import jdk.compiler.graal.nodes.spi.CoreProviders;
import jdk.compiler.graal.nodes.spi.LoopsDataProvider;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.util.EconomicSetNodeEventListener;

public class LoopPartialUnrollPhase extends LoopPhase<LoopPolicies> {

    public LoopPartialUnrollPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
        super(policies, canonicalizer);
    }

    @SuppressWarnings("try")
    private void unroll(StructuredGraph graph, CoreProviders context) {
        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        boolean changed = true;
        EconomicMap<LoopBeginNode, OpaqueNode> opaqueUnrolledStrides = null;
        boolean prePostInserted = false;
        while (changed) {
            changed = false;
            try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
                LoopsData dataCounted = context.getLoopsDataProvider().getLoopsData(graph);
                dataCounted.detectCountedLoops();
                graph.getDebug().log(DebugContext.INFO_LEVEL, "Detected %d counted loops", dataCounted.countedLoops().size());
                Graph.Mark mark = graph.getMark();
                for (LoopEx loop : dataCounted.countedLoops()) {
                    if (LoopTransformations.isUnrollableLoop(loop)) {
                        graph.getDebug().log(DebugContext.INFO_LEVEL, "Loop %s can be unrolled, now checking if we should", loop);
                        if (getPolicies().shouldPartiallyUnroll(loop, context)) {
                            if (loop.loopBegin().isSimpleLoop()) {
                                // First perform the pre/post transformation and do the partial
                                // unroll when we come around again.
                                LoopTransformations.insertPrePostLoops(loop);
                                prePostInserted = true;
                                changed = true;
                            } else if (prePostInserted) {
                                if (opaqueUnrolledStrides == null) {
                                    opaqueUnrolledStrides = EconomicMap.create(Equivalence.IDENTITY);
                                }
                                LoopTransformations.partialUnroll(loop, opaqueUnrolledStrides);
                                changed = true;
                            }
                        }
                    } else {
                        graph.getDebug().log(DebugContext.INFO_LEVEL, "Loop %s cannot be unrolled", loop);
                    }
                }
                dataCounted.deleteUnusedNodes();

                if (!listener.getNodes().isEmpty()) {
                    canonicalizer.applyIncremental(graph, context, listener.getNodes());
                    listener.getNodes().clear();
                }

                assert !prePostInserted || checkCounted(graph, context.getLoopsDataProvider(), mark);
            }
        }
        if (opaqueUnrolledStrides != null) {
            try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
                for (OpaqueNode opaque : opaqueUnrolledStrides.getValues()) {
                    opaque.remove();
                }
                if (!listener.getNodes().isEmpty()) {
                    canonicalizer.applyIncremental(graph, context, listener.getNodes());
                }
            }
        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.FSA, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.VALUE_PROXY_REMOVAL, graphState));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        if (graph.hasLoops()) {
            try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
                unroll(graph, context);
            }
            if (!listener.getNodes().isEmpty()) {
                // run a regular canonicalization with simplification after the entire unrolling
                canonicalizer.applyIncremental(graph, context, listener.getNodes());
            }
        }
    }

    private static boolean checkCounted(StructuredGraph graph, LoopsDataProvider loopsDataProvider, Graph.Mark mark) {
        LoopsData dataCounted;
        dataCounted = loopsDataProvider.getLoopsData(graph);
        dataCounted.detectCountedLoops();
        for (LoopEx anyLoop : dataCounted.loops()) {
            if (graph.isNew(mark, anyLoop.loopBegin())) {
                assert anyLoop.isCounted() : "pre/post transformation loses counted loop " + anyLoop.loopBegin();
            }
        }
        return true;
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
