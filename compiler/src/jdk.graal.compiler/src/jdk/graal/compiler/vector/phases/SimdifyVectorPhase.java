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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorProducer;
import jdk.graal.compiler.vector.nodes.VectorAccess;
import jdk.graal.compiler.vector.nodes.VectorLogicNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorWriteNode;
import jdk.graal.compiler.vector.nodes.op.MapVectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorOperation;
import jdk.graal.compiler.vector.nodes.op.VectorPhi;
import jdk.graal.compiler.vector.nodes.op.VectorTransformation;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.producer.InvariantVectorLogicNode;
import jdk.graal.compiler.vector.nodes.producer.SequenceVectorNode;
import jdk.graal.compiler.vector.nodes.producer.VectorReadNode;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.VoidStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.MinMaxNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.nodes.simd.SimdBlendWithConstantMaskNode;
import jdk.graal.compiler.vector.nodes.simd.SimdBroadcastNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.TriState;

/**
 * Transforms high-level vector operations into SIMD form. The remaining vector values with
 * conceptually infinite-length vector stamps are replaced by SIMD operations with
 * {@link SimdStamp}s with the fixed lengths required by their users.
 *
 * @implNote The SIMD form is computed by each node's
 *           {@link SimdifyableVectorOperation#simdify(VectorArchitecture, ValueNode...)} or
 *           {@link SimdifyableVectorProducer#simdify(int, Direction)} method. The main results of
 *           this phase are:
 *
 *           <ul>
 *           <li>{@link VectorReadNode} and {@link VectorWriteNode} are transformed to normal reads
 *           and writes</li>
 *           <li>arithmetic operations inside a {@link MapVectorNode} are expanded with SIMD
 *           stamps</li>
 *           <li>{@link FoldVectorNode} is expanded to a computation using a
 *           {@link ValuePhiNode}</li>
 *           <li>{@link FillVectorNode} is expanded to {@link SimdBroadcastNode}</li>
 *           <li>induction variables represented by {@link SequenceVectorNode} are expanded to SIMD
 *           computations that we then try to turn back into scalar form</li>
 *           </ul>
 *
 *           Internally and in some method signatures you will see a consumerDirection of type
 *           {@link Direction}. The stride for a {@link VectorAccess} is determined by the stride
 *           and direction of the underlying operation (forward is positive and reverse is
 *           negative). Every vector operation needs a direction to ensure that memory accesses and
 *           other operations are transformed to correct SIMD code.
 */
public class SimdifyVectorPhase extends PostRunCanonicalizationPhase<LowTierContext> {

    public SimdifyVectorPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer.copyWithCustomSimplification(new SimdSimplification()));
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FIXED_READS, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.ADDRESS_LOWERING, graphState));
    }

    @Override
    public void run(StructuredGraph graph, LowTierContext context) {
        ArrayList<VectorConsumer> consumers = new ArrayList<>();
        for (Node n : graph.getNodes()) {
            if (n instanceof VectorConsumer) {
                consumers.add((VectorConsumer) n);
            }
        }
        if (consumers.isEmpty()) {
            return;
        }

        VectorArchitecture arch = ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();
        Mark mark = graph.getMark();
        SimdifyVectorClosure closure = new SimdifyVectorClosure(graph, arch, context.getLowerer());

        // determine length of each vector node
        for (VectorConsumer node : consumers) {
            closure.propagateLength(node);
        }

        // SIMDify all vector consumers
        for (VectorConsumer node : consumers) {
            closure.simdify(node);
        }

        closure.finish();

        optimizeSimdCuts(graph, mark, context.getMetaAccess());

        GraalError.guarantee(graph.getNewNodes(mark).filter(FloatingReadNode.class).isEmpty(), "SimdifyVector must not create floating reads");
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.addFutureStageRequirement(StageFlag.FINAL_CANONICALIZATION);
        graphState.addFutureStageRequirement(StageFlag.TARGET_VECTOR_LOWERING);
    }

    private static void optimizeSimdCuts(StructuredGraph graph, Mark mark, MetaAccessProvider metaAccess) {
        // We might have generated SIMD code that is only used by a SimdCut with length 1.
        // This happens for vectorized induction variables for VectorGuard frame states. See if we
        // can turn this back into scalar code.
        for (Node newNode : graph.getNewNodes(mark)) {
            if (newNode instanceof SimdCutNode && usedByNewFrameState(graph, mark, newNode)) {
                SimdCutNode cut = (SimdCutNode) newNode;
                cut.tryToScalarize(metaAccess);
            }
        }
    }

    /**
     * Determine whether {@code newNode} is used, possibly transitively, by any {@link FrameState}
     * that is newer than the mark. This search only considers new nodes.
     */
    private static boolean usedByNewFrameState(StructuredGraph graph, Mark mark, Node newNode) {
        assert graph.isNew(mark, newNode) : "Must be a new node " + newNode + " " + graph.getNewNodes(mark).snapshot();
        NodeFlood flood = new NodeFlood(graph);
        flood.add(newNode);
        for (Node node : flood) {
            if (node instanceof FrameState) {
                return true;
            }
            for (Node usage : node.usages()) {
                if (graph.isNew(mark, usage)) {
                    flood.add(usage);
                }
            }
        }
        return false;
    }

    private static class SimdifyVectorClosure {

        private final NodeMap<Integer> vectorLength;
        private final EconomicMap<ValueNode, ValueNode> simdifiedValues;

        private final Queue<ValuePhiNode> phiQueue;
        private final NodeMap<VectorPhi> phiMap;
        private final NodeMap<Direction> phiDirectionMap;

        private final VectorArchitecture arch;
        private final LoweringProvider loweringProvider;

        SimdifyVectorClosure(StructuredGraph graph, VectorArchitecture arch, LoweringProvider loweringProvider) {
            vectorLength = new NodeMap<>(graph);
            simdifiedValues = EconomicMap.create(Equivalence.IDENTITY);
            phiQueue = new LinkedList<>();
            phiMap = new NodeMap<>(graph);
            phiDirectionMap = new NodeMap<>(graph);
            this.arch = arch;
            this.loweringProvider = loweringProvider;
        }

        public void propagateLength(VectorConsumer consumer) {
            assert consumer.getLength().isConstant() : "vector consumer " + consumer + " not lowered";
            assert NumUtil.isUnsignedNbit(31, consumer.getLength().asJavaConstant().asLong()) : consumer.getLength().asJavaConstant();
            int length = consumer.getLength().asJavaConstant().asInt();

            for (ValueNode input : consumer.getVectorInputs()) {
                bumpVectorLength((VectorNode) input, length);
            }
        }

        public void simdify(VectorConsumer consumer) {
            Direction consumerDirection = ((LowerableVectorConsumer) consumer).direction();
            simdify(consumer.asNode(), consumerDirection);
        }

        private static int getVectorLength(ValueNode simd) {
            if (simd.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
                SimdStamp stamp = (SimdStamp) simd.stamp(NodeView.DEFAULT);
                return stamp.getVectorLength();
            } else {
                return 1;
            }
        }

        public void finish() {
            while (!phiQueue.isEmpty()) {
                ValuePhiNode simd = phiQueue.remove();
                VectorPhi vector = phiMap.get(simd);
                int length = getVectorLength(simd);
                Direction consumerDirection = phiDirectionMap.get(simd);

                for (ValueNode input : simdifyInputs(length, vector, consumerDirection)) {
                    simd.addInput(input);
                }

            }
            deleteOriginalVectorNodes();
        }

        private void bumpVectorLength(VectorNode vector, int length) {
            Integer oldLength = vectorLength.get(vector.asNode());
            if (oldLength != null && length <= oldLength) {
                // break cycles (e.g. phi nodes)
                return;
            }

            vectorLength.set(vector.asNode(), length);
            if (vector instanceof VectorTransformation) {
                for (ValueNode input : ((VectorTransformation) vector).getVectorInputs()) {
                    bumpVectorLength((VectorNode) input, length);
                }
            }
        }

        private void rememberSimdifiedValue(ValueNode node, ValueNode replacement) {
            GraalError.guarantee(simdifiedValues.get(node) == null, "already simdified %s", node);
            simdifiedValues.put(node, replacement);
        }

        private boolean isSimdifiedOriginal(Node node) {
            return node instanceof ValueNode && simdifiedValues.get((ValueNode) node) != null;
        }

        private void deleteOriginalVectorNodes() {
            for (ValueNode node : simdifiedValues.getKeys()) {
                node.replaceAtUsages(null, this::isSimdifiedOriginal);
            }
            for (ValueNode node : simdifiedValues.getKeys()) {
                GraalError.guarantee(node.hasNoUsages(), "unexpected remaining usage %s of simdified node %s", node.usages().first(), node);
                if (node instanceof FixedWithNextNode fixedNode) {
                    fixedNode.graph().removeFixed(fixedNode);
                } else {
                    node.safeDelete();
                }
            }
        }

        private ValueNode simdify(ValueNode node, Direction consumerDirection) {
            ValueNode ret;
            ValueNode cached = simdifiedValues.get(node);
            if (cached != null) {
                return cached;
            }
            Stamp stamp = node.stamp(NodeView.DEFAULT);
            if (stamp instanceof SimdStamp || node instanceof InvariantVectorLogicNode || (stamp instanceof VoidStamp && node instanceof VectorLogicNode)) {
                // This node has already been simdified, this can happen if it has multiple users.
                // Simdified logic nodes have void stamps like scalar logic nodes (before
                // simdification they have vector stamps).
                return node;
            } else if (node instanceof SimdifyableVectorOperation) {
                ret = simdifyOperation((SimdifyableVectorOperation) node, consumerDirection);
            } else if (node instanceof SimdifyableVectorProducer) {
                ret = simdifyProducer((SimdifyableVectorProducer) node, consumerDirection);
            } else if (node instanceof VectorPhi) {
                assert node instanceof VectorPhi : "unexpected high-level vector node " + node;
                ret = simdifyPhi((VectorPhi) node, consumerDirection);
            } else {
                return node;
            }

            if (node.isAlive()) {
                rememberSimdifiedValue(node, ret);
            }
            return ret;
        }

        private ValueNode[] simdifyInputs(int length, VectorOperation operation, Direction consumerDirection) {
            List<? extends ValueNode> inputs = operation.getVectorInputs();

            ValueNode[] simdInputs = new ValueNode[inputs.size()];
            for (int i = 0; i < inputs.size(); i++) {
                ValueNode simd = simdify(inputs.get(i), consumerDirection);

                int inputLength;
                if (simd instanceof InvariantVectorLogicNode) {
                    // This node can match any required length.
                    inputLength = length;
                } else {
                    Stamp simdStamp = simd.stamp(NodeView.DEFAULT);
                    GraalError.guarantee(!(simdStamp instanceof VectorStamp), "unexpected high-level vector node %s", simd);
                    inputLength = getLength(simdStamp);
                }
                assert inputLength >= length : String.format("input %s (length %d) is shorter than required length %d", simd, inputLength, length);
                if (inputLength > length) {
                    if (consumerDirection == Direction.Up) {
                        simd = simd.graph().unique(new SimdCutNode(simd, length));
                    } else {
                        simd = simd.graph().unique(new SimdCutNode(simd, inputLength - length, length));
                    }
                }

                simdInputs[i] = simd;
            }

            return simdInputs;
        }

        private ValueNode simdifyOperation(SimdifyableVectorOperation operation, Direction consumerDirection) {
            int length = getLength(operation.asNode());
            ValueNode[] simdInputs = simdifyInputs(length, operation, consumerDirection);
            Mark mark = operation.asNode().graph().getMark();
            ValueNode simdifiedResult = operation.simdify(arch, simdInputs);
            return expandMacroNodes(operation, mark, length, simdifiedResult);
        }

        private ValueNode expandMacroNodes(SimdifyableVectorOperation operation, Mark mark, int length, ValueNode initialResult) {
            ValueNode result = initialResult;
            for (Node simdifiedNode : operation.asNode().graph().getNewNodes(mark)) {
                ValueNode expansion = null;
                if (simdifiedNode instanceof FoldVectorNode.BinaryMacroNode) {
                    FoldVectorNode.BinaryMacroNode macro = (FoldVectorNode.BinaryMacroNode) simdifiedNode;
                    expansion = macro.expand(length, getLength(macro.asNode().stamp(NodeView.DEFAULT)));
                }
                /* Expand scalar min/max back to conditionals if needed. */
                if (simdifiedNode instanceof MinMaxNode) {
                    Stamp stamp = ((MinMaxNode<?>) simdifiedNode).stamp(NodeView.DEFAULT);
                    Stamp elementStamp = stamp instanceof SimdStamp ? ((SimdStamp) stamp).getComponent(0) : stamp;
                    int simdLength = getLength(stamp);
                    boolean supportedAsSimd = simdLength > 1 &&
                                    arch.getSupportedVectorArithmeticLength(elementStamp, simdLength, ((MinMaxNode<?>) simdifiedNode).getArithmeticOp().unwrap()) == simdLength;
                    if (simdLength == 1 || !supportedAsSimd) {
                        /*
                         * Besides unsupported vector min/max, we always transform scalar min/max
                         * back to a conditional form. Canonicalization will turn into a min/max
                         * again if the target supports it. This way we don't need to worry about
                         * this aspect of the target here.
                         */
                        expansion = ((MinMaxNode<?>) simdifiedNode).asConditional(loweringProvider);
                        if (elementStamp instanceof IntegerStamp) {
                            GraalError.guarantee(expansion != null, "min/max expansion to conditional must always be possible");
                        }
                    }
                }
                if (expansion != null) {
                    expansion = operation.asNode().graph().addOrUniqueWithInputs(expansion);
                    simdifiedNode.replaceAtUsages(expansion);
                    if (simdifiedNode == initialResult) {
                        result = expansion;
                    }
                }
            }
            return result;
        }

        private ValueNode simdifyProducer(SimdifyableVectorProducer node, Direction consumerDirection) {
            return node.simdify(getLength(node.asNode()), consumerDirection);
        }

        private ValueNode simdifyPhi(VectorPhi node, Direction consumerDirection) {
            int length = getLength(node);
            ValuePhiNode simd = node.graph().addWithoutUnique(new ValuePhiNode(node.getVectorStamp().toSimd(length), node.merge()));

            phiMap.setAndGrow(simd, node);
            phiQueue.add(simd);
            phiDirectionMap.setAndGrow(simd, consumerDirection);
            return simd;
        }

        private int getLength(Node node) {
            if (node instanceof VectorConsumer) {
                ValueNode length = ((VectorConsumer) node).getLength();
                assert NumUtil.isUnsignedNbit(31, length.asJavaConstant().asLong()) : length.asJavaConstant();
                return length.asJavaConstant().asInt();
            } else {
                Integer length = vectorLength.get(node);
                return length == null ? 0 : length;
            }
        }

        private static int getLength(Stamp stamp) {
            if (stamp instanceof SimdStamp) {
                return ((SimdStamp) stamp).getVectorLength();
            } else {
                return 1;
            }
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    private static final class SimdSimplification implements CanonicalizerPhase.CustomSimplification {
        @Override
        public void simplify(Node node, SimplifierTool tool) {
            /*
             * After instantiating SIMD nodes we can have conditional nodes whose conditions are
             * comparisons on SIMD constants, but where not all results go the same way. Normal
             * constant folding can't do anything with cases like <8,9,10,11> |<| <10,10,10,10>
             * because this condition is neither all-true nor all-false. However, if this is the
             * condition of a conditional node, we can constant fold it component-wise and build a
             * blend node from it, which may then fold further.
             */
            if (node instanceof ConditionalNode conditional && conditional.condition() instanceof CompareNode compare) {
                Constant x = compare.getX().asConstant();
                Constant y = compare.getY().asConstant();
                if (x instanceof SimdConstant simdX && y instanceof SimdConstant simdY) {
                    Stamp compareStamp = compare.getX().stamp(NodeView.DEFAULT);
                    Condition condition = compare.condition().asCondition();
                    TriState foldedCompare = compareStamp.tryConstantFold(condition, x, y, compare.unorderedIsTrue(), tool.getConstantReflection());
                    if (foldedCompare.isKnown()) {
                        // Condition folds completely, normal canonicalization will handle this.
                        return;
                    }
                    Stamp scalarStamp = ((SimdStamp) compareStamp).getComponent(0);
                    boolean[] conditions = new boolean[simdX.getVectorLength()];
                    for (int i = 0; i < conditions.length; i++) {
                        TriState componentCompare = scalarStamp.tryConstantFold(condition, simdX.getValue(i), simdY.getValue(i), compare.unorderedIsTrue(), tool.getConstantReflection());
                        if (componentCompare.isUnknown()) {
                            // give up
                            return;
                        }
                        conditions[i] = componentCompare.toBoolean();
                    }
                    SimdBlendWithConstantMaskNode blend = conditional.graph().addOrUniqueWithInputs(SimdBlendWithConstantMaskNode.create(conditional.falseValue(), conditional.trueValue(),
                                    conditions));
                    conditional.replaceAndDelete(blend);
                    tool.addToWorkList(blend);
                }
            }
        }
    }
}
