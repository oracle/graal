/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.op;

import static jdk.graal.compiler.nodeinfo.InputType.Condition;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.vector.nodes.LowerableVectorNode;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.VectorLogicNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorTransformationIterator;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.producer.InvariantVectorLogicNode;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Vectorized comparison of two vector values. The inputs and the matching result can be either
 * high-level {@linkplain VectorStamp vector} or low-level {@linkplain SimdStamp SIMD} values.
 * <p/>
 *
 * @implNote This node is built by the loop vectorizer. It can handle both primitive and pointer
 *           compare operations. When transforming from high-level vectors to SIMD values, primitive
 *           compares are lowered to {@link SimdPrimitiveCompareNode}. Non-primitive compares are
 *           still represented by this node even on the SIMD level.
 */
//@formatter:off
@NodeInfo(allowedUsageTypes = {Condition},
        cycles = CYCLES_UNKNOWN,
        cyclesRationale = "We cannot argue about vector nodes statically.",
        size = SIZE_UNKNOWN,
        sizeRationale = "We cannot argue about vector nodes statically.",
        nameTemplate = "CompareVector {p#condition/s}")
//@formatter:on
public class CompareVectorNode extends VectorLogicNode implements SimdifyableVectorOperation, LowerableVectorNode, VectorTransformation, VectorLIRLowerable, Canonicalizable {

    public static final NodeClass<CompareVectorNode> TYPE = NodeClass.create(CompareVectorNode.class);

    @Input ValueNode x;
    @Input ValueNode y;

    private final CanonicalCondition condition;
    private final boolean unorderedIsTrue;
    private final Stamp maskStamp;

    protected CompareVectorNode(CanonicalCondition condition, ValueNode x, ValueNode y, boolean unorderedIsTrue, Stamp maskStamp) {
        super(TYPE, buildVectorStamp(x, y, maskStamp));
        this.condition = condition;
        this.x = x;
        this.y = y;
        this.unorderedIsTrue = unorderedIsTrue;
        this.maskStamp = maskStamp;
    }

    public static CompareVectorNode compare(CanonicalCondition condition, ValueNode x, ValueNode y, boolean unorderedIsTrue, Stamp maskStamp) {
        GraalError.guarantee(maskStamp instanceof IntegerStamp || maskStamp instanceof LogicValueStamp, "need bitmask or logic mask stamp: %s", maskStamp);
        return new CompareVectorNode(condition, x, y, unorderedIsTrue, maskStamp);
    }

    public ValueNode getX() {
        return x;
    }

    public ValueNode getY() {
        return y;
    }

    public CanonicalCondition getCondition() {
        return condition;
    }

    public boolean unorderedIsTrue() {
        return unorderedIsTrue;
    }

    private static Stamp buildVectorStamp(ValueNode x, ValueNode y, Stamp maskStamp) {
        Stamp xStamp = x.stamp(NodeView.DEFAULT);
        Stamp yStamp = y.stamp(NodeView.DEFAULT);
        if (xStamp instanceof VectorStamp) {
            assert yStamp instanceof VectorStamp : yStamp;
        } else if (xStamp instanceof SimdStamp) {
            assert yStamp instanceof SimdStamp : yStamp;
        }
        return buildVectorStamp(x, maskStamp);
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        ArrayList<ValueNode> inputs = new ArrayList<>();
        inputs.add(x);
        inputs.add(y);
        return inputs;
    }

    @Override
    public VectorIterator createInitialIterator(AnchoringNode anchor, TargetDescription target) {
        return VectorTransformationIterator.createInitialIterator(this, anchor, target);
    }

    @Override
    public VectorIterator createPhiIterator(AbstractMergeNode merge, AnchoringNode anchor, TargetDescription target) {
        return VectorTransformationIterator.createPhiIterator(this, merge, anchor, target);
    }

    @Override
    public ValueNode simdify(VectorArchitecture arch, ValueNode... inputs) {
        assert inputs.length == 2 : inputs;
        if (inputs[0].stamp(NodeView.DEFAULT) instanceof SimdStamp simdStamp && simdStamp.getComponent(0) instanceof PrimitiveStamp) {
            return graph().addWithoutUnique(SimdPrimitiveCompareNode.simdCompare(condition, inputs[0], inputs[1], unorderedIsTrue, arch));
        } else {
            return graph().addWithoutUnique(new CompareVectorNode(condition, inputs[0], inputs[1], unorderedIsTrue, maskStamp));
        }
    }

    @Override
    public VectorNode simplify(VectorSimplifier simplifier) {
        VectorNode newX = simplifier.simplify((VectorNode) x);
        if (newX != x) {
            setX(newX);
        }
        VectorNode newY = simplifier.simplify((VectorNode) y);
        if (newY != y) {
            setY(newY);
        }
        return this;
    }

    public void setX(ValueNode x) {
        updateUsages(this.x, x);
        this.x = x;
    }

    private void setX(VectorNode newX) {
        updateUsages(x, newX.asNode());
        x = newX.asNode();
    }

    public void setY(ValueNode y) {
        updateUsages(this.y, y);
        this.y = y;
    }

    private void setY(VectorNode newY) {
        updateUsages(y, newY.asNode());
        y = newY.asNode();
    }

    @Override
    public VectorTransformation createCopy(FixedNode insertBefore, ValueNode... newInputs) {
        assert newInputs.length == 2 : newInputs;
        return graph().unique(new CompareVectorNode(condition, newInputs[0], newInputs[1], unorderedIsTrue, maskStamp));
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        Value xValue = builder.operand(x);
        Value yValue = builder.operand(y);
        builder.setResult(this, gen.emitVectorPackedComparison(condition, xValue, yValue, unorderedIsTrue));
    }

    @Override
    public LogicNode asScalar() {
        return asScalar(getX(), getY());
    }

    @Override
    public LogicNode cutToScalar(int offset) {
        ValueNode scalarX = graph().addOrUnique(new SimdCutNode(getX(), offset, 1));
        ValueNode scalarY = graph().addOrUnique(new SimdCutNode(getY(), offset, 1));
        return asScalar(scalarX, scalarY);
    }

    private LogicNode asScalar(ValueNode xValue, ValueNode yValue) {
        if (xValue.getStackKind().isNumericFloat()) {
            return CompareNode.createFloatCompareNode(graph(), getCondition(), xValue, yValue, unorderedIsTrue(), NodeView.DEFAULT);
        } else {
            return CompareNode.createCompareNode(graph(), getCondition(), xValue, yValue, null, NodeView.DEFAULT);
        }
    }

    @Override
    public LogicNode invariantCondition() {
        if (getX() instanceof FillVectorNode && getY() instanceof FillVectorNode) {
            ValueNode xValue = ((FillVectorNode) getX()).getElement();
            ValueNode yValue = ((FillVectorNode) getY()).getElement();
            return asScalar(xValue, yValue);
        }
        return null;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch, int upperBound) {
        return getMaxVectorLength(stamp, condition, arch, upperBound);
    }

    protected static int getMaxVectorLength(Stamp vectorizedStamp, CanonicalCondition vectorCondition, VectorArchitecture arch, int upperBound) {
        Stamp elementStamp;
        if (vectorizedStamp instanceof VectorStamp) {
            elementStamp = ((VectorStamp) vectorizedStamp).getElementStamp();
        } else if (vectorizedStamp instanceof SimdStamp) {
            elementStamp = ((SimdStamp) vectorizedStamp).getComponent(0);
        } else {
            return 1;
        }
        if (elementStamp instanceof LogicValueStamp) {
            return arch.getMaxLogicVectorLength(elementStamp);
        } else {
            return arch.getSupportedVectorComparisonLength(elementStamp, vectorCondition, upperBound);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x instanceof FillVectorNode && y instanceof FillVectorNode) {
            ValueNode xElement = ((FillVectorNode) x).getElement();
            ValueNode yElement = ((FillVectorNode) y).getElement();
            LogicNode elementComparison;
            if (xElement.getStackKind().isNumericFloat()) {
                elementComparison = CompareNode.createFloatCompareNode(getCondition(), xElement, yElement, unorderedIsTrue(), NodeView.DEFAULT);
            } else {
                elementComparison = CompareNode.createCompareNode(getCondition(), xElement, yElement, null, NodeView.DEFAULT);
            }
            return new InvariantVectorLogicNode(elementComparison, stamp(NodeView.DEFAULT));
        }
        return this;
    }
}
