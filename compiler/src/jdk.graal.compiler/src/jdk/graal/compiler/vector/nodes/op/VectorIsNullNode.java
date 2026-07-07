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

import java.util.Collections;
import java.util.List;

import jdk.graal.compiler.vector.nodes.LowerableVectorNode;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.VectorLogicNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorTransformationIterator;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.producer.InvariantVectorLogicNode;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Null check on a vector of pointer values.
 */
//@formatter:off
@NodeInfo(allowedUsageTypes = {Condition},
        cycles = CYCLES_UNKNOWN,
        cyclesRationale = "We cannot argue about vector nodes statically.",
        size = SIZE_UNKNOWN,
        sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public class VectorIsNullNode extends VectorLogicNode implements SimdifyableVectorOperation, LowerableVectorNode, VectorTransformation, VectorLIRLowerable, Canonicalizable {

    public static final NodeClass<VectorIsNullNode> TYPE = NodeClass.create(VectorIsNullNode.class);

    @Input ValueNode value;

    private final JavaConstant nullConstant;
    private final Stamp maskStamp;

    protected VectorIsNullNode(ValueNode value, JavaConstant nullConstant, Stamp maskStamp) {
        super(TYPE, buildVectorStamp(value, maskStamp));
        this.value = value;
        this.nullConstant = nullConstant;
        this.maskStamp = maskStamp;
    }

    public static VectorIsNullNode isNull(ValueNode value, JavaConstant nullConstant, Stamp maskStamp) {
        return new VectorIsNullNode(value, nullConstant, maskStamp);
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        return Collections.singletonList(value);
    }

    @Override
    public VectorNode simplify(VectorSimplifier simplifier) {
        VectorNode newValue = simplifier.simplify((VectorNode) value);
        if (newValue != value) {
            updateUsages(value, newValue.asNode());
            value = newValue.asNode();
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRGeneratorTool lirTool = builder.getLIRGeneratorTool();
        LIRKind kind = lirTool.getLIRKind(value.stamp(NodeView.DEFAULT));
        Constant vectorNullConstant = SimdConstant.broadcast(nullConstant, ((SimdStamp) stamp).getVectorLength());
        builder.setResult(this, gen.emitVectorPackedEquals(builder.operand(value), lirTool.emitConstant(kind, vectorNullConstant)));
    }

    @Override
    public VectorTransformation createCopy(FixedNode insertBefore, ValueNode... newInputs) {
        assert newInputs.length == 1 : newInputs;
        return graph().unique(new VectorIsNullNode(newInputs[0], nullConstant, maskStamp));
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
        assert inputs.length == 1 : inputs;
        return graph().unique(new VectorIsNullNode(inputs[0], nullConstant, maskStamp));
    }

    @Override
    public LogicNode asScalar() {
        return asScalar(value);
    }

    @Override
    public LogicNode cutToScalar(int offset) {
        ValueNode scalarValue = graph().addOrUnique(new SimdCutNode(value, offset, 1));
        return asScalar(scalarValue);
    }

    private LogicNode asScalar(ValueNode inputValue) {
        return graph().unique(IsNullNode.create(inputValue, nullConstant));
    }

    @Override
    public LogicNode invariantCondition() {
        if (value instanceof FillVectorNode) {
            return asScalar(((FillVectorNode) value).getElement());
        }
        return null;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch, int upperBound) {
        return CompareVectorNode.getMaxVectorLength(stamp, CanonicalCondition.EQ, arch, upperBound);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (value instanceof FillVectorNode) {
            FillVectorNode fillVector = (FillVectorNode) value;
            LogicNode scalarIsNull = IsNullNode.create(fillVector.getElement(), nullConstant);
            return new InvariantVectorLogicNode(scalarIsNull, stamp(NodeView.DEFAULT));
        }

        return this;
    }
}
