/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases;

import java.util.Optional;

import jdk.graal.compiler.vector.nodes.VectorPolicies;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopNode;
import jdk.graal.compiler.vector.nodes.lowered.CommitVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorConsumerProxyNode;
import jdk.graal.compiler.vector.replacements.VectorSnippets;

import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;

/**
 * This phase computes the target-specific vector lengths for high-level vector operations. In
 * general, a high-level vector operation will be expanded to optional alignment code, the main
 * vectorized loop, and "tail consumer" code for performing the last few computations below the
 * chosen vector length. The vector length for the vector loop is chosen here depending on how the
 * computations in the high-level vector operation are supported by the SIMD instructions of the
 * target CPU. The tail consumer may be a loop or a linear sequence of branches.
 * </p>
 *
 * After this phase, the high-level vector operations are not expanded yet. They are represented
 * inside the vector loop and the tail consumer by generic {@link PartialVectorConsumerNode}s which
 * are then expanded in {@link VectorConsumerPhase}.
 * </p>
 *
 * See {@link VectorPolicies} for the computation of vector lengths and {@link VectorSnippets} for
 * the creation of the general structure consisting of alignment code, the vector loop, and the tail
 * consumer.
 */
public class VectorLoweringPhase extends PostRunCanonicalizationPhase<LowTierContext> {

    public static final CounterKey ConsumersLowered = DebugContext.counter("VectorLowering_ConsumersLowered");

    public VectorLoweringPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.ADDRESS_LOWERING, graphState));
    }

    @SuppressWarnings("try")
    @Override
    public void run(StructuredGraph graph, LowTierContext context) {
        VectorSnippets.Templates templates = context.getReplacements().getSnippetTemplateCache(VectorSnippets.Templates.class);

        for (VectorLoopNode group : graph.getNodes().filter(VectorLoopNode.class)) {
            group.prepareLoopForLowering();
        }

        DebugContext debug = graph.getDebug();
        Graph.Mark before = graph.getMark();
        for (Node node : graph.getNodes()) {
            if (node instanceof LowerableVectorConsumer) {
                LowerableVectorConsumer consumer = (LowerableVectorConsumer) node;

                // If this consumer is part of a group, do not lower it separately. Its entire group
                // will be lowered as a unit.
                if (consumer.isPartOfALoop()) {
                    continue;
                }

                try (Indent indent = debug.logAndIndent(DebugContext.VERBOSE_LEVEL, "lower vector consumer %s", consumer)) {
                    consumer.lower(context);
                    ConsumersLowered.increment(debug);
                    if (consumer.asNode().isDeleted()) {
                        debug.log(DebugContext.VERBOSE_LEVEL, "consumer is deleted, nothing to do");
                        continue;
                    }

                    Graph.Mark mark = graph.getMark();
                    int primaryVectorLength = templates.lower(context, consumer);
                    inferStamps(graph, mark);
                    consumer.finishLowering(context);
                    if (primaryVectorLength > 1) {
                        graph.getOptimizationLog().withProperty("primaryVectorLength", primaryVectorLength).report(getClass(), "ConsumerSimdification", node);
                    } else {
                        graph.getOptimizationLog().report(getClass(), "ConsumerScalarization", node);
                    }
                }
            }
        }
        // assume for now vector loops can never overflow GR-6684
        for (Node n : graph.getNewNodes(before)) {
            if (n instanceof LoopBeginNode) {
                ((LoopBeginNode) n).setCanNeverOverflow();
            }
        }
    }

    private static void inferStamps(StructuredGraph graph, Graph.Mark mark) {
        for (Node newNode : graph.getNewNodes(mark)) {
            if (newNode instanceof VectorConsumerProxyNode) {
                ((VectorConsumerProxyNode) newNode).inferStamp();
            }
        }

        for (Node newNode : graph.getNewNodes(mark)) {
            if (newNode instanceof CommitVectorConsumerNode) {
                ((CommitVectorConsumerNode) newNode).inferStamp();
            }
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
