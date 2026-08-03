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

import static jdk.graal.compiler.vector.phases.NodeVectorizationPhase.Options.VectorizeAllocation;

import java.util.Optional;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.LoweredMaterializeVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.MaterializeVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorWriteNode;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.replacements.CopyOfSnippets;
import jdk.graal.compiler.vector.replacements.UncheckedCopyOfNode;
import jdk.graal.compiler.vector.replacements.VectorizableNewArrayNode;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.java.AbstractNewArrayNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.replacements.nodes.ArrayFillNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * This phase transforms operations with an equivalent vectorized representation, like array
 * allocation and initialization operations, into a high-level vectorized form. These forms can be
 * combined and optimized with the vectorized loop representations computed by
 * {@link LoopVectorizationPhase}. They are lowered to efficient SIMD code as appropriate for the
 * target.
 */
public class NodeVectorizationPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public NodeVectorizationPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    public static class Options {

        // @formatter:off
        @Option(help = "Enable vectorized array initialization")
        public static final OptionKey<Boolean> VectorizeAllocation = new OptionKey<>(true);
        // @formatter:on
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.ifApplied(this, StageFlag.NODE_VECTORIZATION, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.FSA, graphState));
    }

    @Override
    public void run(StructuredGraph graph, CoreProviders context) {
        CopyOfSnippets.Templates templates = context.getReplacements().getSnippetTemplateCache(CopyOfSnippets.Templates.class);
        for (Node node : graph.getNodes()) {
            if (node instanceof UncheckedCopyOfNode) {
                templates.lower(context, (UncheckedCopyOfNode) node);
            } else if (VectorizeAllocation.getValue(graph.getOptions()) && node instanceof VectorizableNewArrayNode) {
                VectorizableNewArrayNode newArray = (VectorizableNewArrayNode) node;
                if (newArray.asNewArrayNode().fillContents()) {
                    vectorizeArrayInitialization(graph, newArray);
                }
            } else if (node instanceof ArrayFillNode arrayFill) {
                vectorizeArrayFill(graph, arrayFill);
            }
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.NODE_VECTORIZATION);
        graphState.addFutureStageRequirement(StageFlag.VECTOR_MATERIALIZATION);
        graphState.addFutureStageRequirement(StageFlag.VECTOR_LOWERING);
    }

    @SuppressWarnings("try")
    private static void vectorizeArrayInitialization(StructuredGraph graph, VectorizableNewArrayNode newArray) {
        try (DebugCloseable nsp = newArray.asNode().withNodeSourcePosition()) {
            VectorNode vector = graph.unique(new FillVectorNode(newArray.getDefaultValue()));
            MaterializeVectorNode.Allocator allocator = newArray.getAllocator();

            AbstractNewArrayNode newArrayNode = newArray.asNewArrayNode();
            ValueNode length = newArrayNode.length();
            Stamp newArrayStamp = newArrayNode.stamp(NodeView.DEFAULT);
            // We're initializing with default values, which should never need a barrier.
            BarrierType barrierType = BarrierType.NONE;
            LoweredMaterializeVectorNode ret = new LoweredMaterializeVectorNode(allocator, newArrayStamp, vector, length, newArray.getArrayBaseOffset(), newArray.getArrayIndexScale(), barrierType);
            ret = graph.add(ret);
            ret.setStateBefore(newArrayNode.stateBefore());
            ret.setEmitMemoryBarrier(newArrayNode.emitMemoryBarrier());

            graph.replaceFixedWithFixed(newArrayNode, ret);
            graph.getOptimizationLog().report(NodeVectorizationPhase.class, "ArrayInitializationVectorization", newArrayNode);
        }
    }

    @SuppressWarnings("try")
    private static void vectorizeArrayFill(StructuredGraph graph, ArrayFillNode arrayFill) {
        try (DebugCloseable nsp = arrayFill.withNodeSourcePosition()) {
            ValueNode fillValue = arrayFill.getValueToFillWith();
            /*
             * ArrayFillNode represents floating-point values reinterpreted as integer bits, and
             * subword integers as full ints. Adjust for the real element kind.
             */
            JavaKind elementKind = arrayFill.getElementKind();
            if (elementKind.isNumericInteger() && elementKind.getByteCount() < JavaKind.Int.getByteCount()) {
                fillValue = NarrowNode.create(fillValue, elementKind.getByteCount() * Byte.SIZE, NodeView.DEFAULT);
            } else if (elementKind.isNumericFloat()) {
                fillValue = ReinterpretNode.create(elementKind, fillValue, NodeView.DEFAULT);
            }
            ValueNode vector = graph.addOrUniqueWithInputs(new FillVectorNode(fillValue));
            OffsetAddressNode address = graph.unique(new OffsetAddressNode(arrayFill.getArrayBase(), arrayFill.getOffsetToFirstElement()));
            ValueNode length = arrayFill.getArrayLength();

            VectorWriteNode vectorFill = graph.add(new VectorWriteNode(address, arrayFill.getLocationIdentity(), vector, length, elementKind.getByteCount(), false, BarrierType.NONE));
            vectorFill.setLastLocationAccess(arrayFill.getLastLocationAccess());
            graph.replaceFixedWithFixed(arrayFill, vectorFill);
        }
    }
}
