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
package jdk.graal.compiler.loop.phases;

import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.loop.phases.LoopTransformations.PreMainPostResult;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.LoopUtility;

public class LoopPartialUnrollPhase extends LoopPhase<LoopPolicies> {

    public LoopPartialUnrollPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
        super(policies, canonicalizer);
    }

    @SuppressWarnings("try")
    private void unroll(StructuredGraph graph, CoreProviders context) {
        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        boolean changed = true;
        EconomicMap<LoopBeginNode, OpaqueNode> opaqueUnrolledStrides = null;
        NodeBitMap newMainLoops = null;

        while (changed) {
            changed = false;
            try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
                LoopsData dataCounted = context.getLoopsDataProvider().getLoopsData(graph);
                dataCounted.detectCountedLoops();
                graph.getDebug().log(DebugContext.INFO_LEVEL, "Detected %d counted loops", dataCounted.countedLoops().size());
                Graph.Mark mark = graph.getMark();
                for (Loop loop : dataCounted.countedLoops()) {
                    if (LoopTransformations.isUnrollableLoop(loop)) {
                        graph.getDebug().log(DebugContext.INFO_LEVEL, "Loop %s can be unrolled, now checking if we should", loop);
                        if (getPolicies().shouldPartiallyUnroll(loop, context)) {
                            if (loop.loopBegin().isSimpleLoop()) {
                                // First perform the pre/post transformation and do the partial
                                // unroll when we come around again.
                                LoopUtility.preserveCounterStampsForDivAfterUnroll(loop);
                                PreMainPostResult res = LoopTransformations.insertPrePostLoops(loop);
                                if (newMainLoops == null) {
                                    newMainLoops = graph.createNodeBitMap();
                                }
                                newMainLoops.markAndGrow(res.getMainLoop());
                                changed = true;
                            } else if (newMainLoops != null && newMainLoops.isMarkedAndGrow(loop.loopBegin())) {
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

                assert newMainLoops == null || checkCounted(graph, context.getLoopsDataProvider(), mark);
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
        for (Loop anyLoop : dataCounted.loops()) {
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
