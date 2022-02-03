/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop.phases;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopPolicies;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.LoopsDataProvider;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;

public class LoopPartialUnrollPhase extends LoopPhase<LoopPolicies> {

    private final CanonicalizerPhase canonicalizer;

    public LoopPartialUnrollPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
        super(policies);
        this.canonicalizer = canonicalizer;
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
                dataCounted.detectedCountedLoops();
                Graph.Mark mark = graph.getMark();
                for (LoopEx loop : dataCounted.countedLoops()) {
                    if (!LoopTransformations.isUnrollableLoop(loop)) {
                        continue;
                    }
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
        dataCounted.detectedCountedLoops();
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
