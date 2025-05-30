/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.phases.aarch64;

import java.util.Optional;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatEqualsNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.architecture.aarch64.VectorAArch64;
import jdk.graal.compiler.vector.nodes.aarch64.AArch64AcrossVectorNode;
import jdk.graal.compiler.vector.nodes.aarch64.AArch64PermuteNode;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdMaskLogicNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.nodes.simd.TargetVectorLoweringUtils;

/**
 * This class implements custom vector lowerings for AArch64 as some operations cannot be handled by
 * the backend directly.
 */
public class AArch64VectorLoweringPhase extends BasePhase<LowTierContext> {

    @Override
    public boolean mustApply(GraphState graphState) {
        return graphState.requiresFutureStage(StageFlag.TARGET_VECTOR_LOWERING) || super.mustApply(graphState);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        VectorAArch64 vectorArch = (VectorAArch64) ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();
        for (Node node : graph.getNodes()) {
            if (node instanceof SimdCutNode) {
                lowerSimdCut((SimdCutNode) node, context);
            } else if (node instanceof SimdPermuteNode) {
                lowerSimdPermute((SimdPermuteNode) node, context);
            } else if (node instanceof SimdMaskLogicNode) {
                lowerSimdMaskLogic((SimdMaskLogicNode) node, vectorArch);
            } else if (node instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) node;
                if (valueNode.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
                    lowerSimdNode(valueNode, (SimdStamp) valueNode.stamp(NodeView.DEFAULT), context, vectorArch);
                }
            }
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.removeRequirementToStage(StageFlag.TARGET_VECTOR_LOWERING);
    }

    private static void lowerSimdCut(SimdCutNode cut, LowTierContext context) {
        ValueNode legalCut = TargetVectorLoweringUtils.legalizeSimdCutLength(cut, context);
        if (legalCut != cut) {
            cut.replaceAtUsagesAndDelete(legalCut);
        }
    }

    /**
     * Lowering a {@link SimdMaskLogicNode} into an AArch64-specific comparison.
     *
     * This is done by performing a floating point comparison against "0".
     *
     * Before this can happen, the following transformations may be necessary.
     *
     * <ul>
     * <li>If the condition is ALL_ONES, then the value must be flipped.</li>
     * <li>If the vector size is 128 bits, then the value must be shrunk to fit into 64 bits. When
     * the element size is greater than 8 bits, then this is performed via a narrowing. Otherwise,
     * unsigned max across vector operation is used to ensure all bits are 0.</li>
     * </ul>
     */
    private static void lowerSimdMaskLogic(SimdMaskLogicNode simdMaskLogic, VectorArchitecture vectorArch) {
        ValueNode vectorLogic = simdMaskLogic.getValue();
        SimdStamp simdStamp = (SimdStamp) vectorLogic.stamp(NodeView.DEFAULT);
        Stamp logicStamp = simdStamp.getComponent(0);

        IntegerStamp elementStamp = IntegerStamp.create(vectorArch.getVectorStride(logicStamp) * Byte.SIZE);
        int conditionBits = simdStamp.getVectorLength() * elementStamp.getBits();
        assert conditionBits == 32 || conditionBits == 64 || conditionBits == 128 : conditionBits;

        ValueNode condition = vectorLogic;
        if (simdMaskLogic.getCondition() == SimdMaskLogicNode.Condition.ALL_ONES) {
            /* Perform negate so a comparison against 0 can be performed. */
            condition = NotNode.create(condition);
        }

        if (conditionBits == 128) {
            /* Need to shrink value to be within 64 bits. */
            if (elementStamp.getBits() == 8) {
                /*
                 * Use unsigned max across vector to extract the value.
                 *
                 * Reinterpret value as a vector of 32-bit elements - this makes the computation
                 * faster.
                 */
                conditionBits = 32;
                SimdStamp acrossVectorStamp = SimdStamp.broadcast(IntegerStamp.create(conditionBits), 4);
                condition = ReinterpretNode.create(acrossVectorStamp, condition, NodeView.DEFAULT);
                condition = new AArch64AcrossVectorNode(AArch64AcrossVectorNode.Operation.UNSIGNED_MAX, conditionBits, condition);
            } else {
                /*
                 * Use narrow to extract the value. Note this operation should be faster then the
                 * unsigned max across vector, so this the preferred operation.
                 */
                condition = new NarrowNode(condition, elementStamp.getBits(), elementStamp.getBits() / 2);
                conditionBits = 64;
            }
        }

        /* Reinterpreting result as a scalar floating point value. */
        ConstantNode zeroVal = conditionBits == 64 ? ConstantNode.forDouble(0) : ConstantNode.forFloat(0);
        Stamp comparisonStamp = zeroVal.stamp(NodeView.DEFAULT).unrestricted();
        condition = ReinterpretNode.create(comparisonStamp, condition, NodeView.DEFAULT);

        LogicNode replacementCondition = FloatEqualsNode.create(condition, zeroVal, NodeView.DEFAULT);
        simdMaskLogic.replaceAndDelete(simdMaskLogic.graph().addOrUniqueWithInputs(replacementCondition));
    }

    private static void lowerSimdPermute(SimdPermuteNode permute, LowTierContext context) {
        VectorArchitecture arch = ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();

        StructuredGraph graph = permute.graph();
        Stamp pStamp = permute.stamp(NodeView.DEFAULT);
        assert pStamp instanceof SimdStamp : pStamp;

        permute.replaceAtUsagesAndDelete(graph.unique(AArch64PermuteNode.create(arch, pStamp, permute)));
    }

    private static void lowerSimdNode(ValueNode node, SimdStamp stamp, LowTierContext context, VectorAArch64 vectorArch) {
        if (node instanceof ConstantNode) {
            TargetVectorLoweringUtils.uniqueSimdConstant((ConstantNode) node, stamp, context, vectorArch);
        }
    }
}
