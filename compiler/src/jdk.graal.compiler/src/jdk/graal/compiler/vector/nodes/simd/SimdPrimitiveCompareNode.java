/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.vector.nodes.simd;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;

/**
 * Comparison of SIMD values with primitive (integer or floating point) elements.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN)
public class SimdPrimitiveCompareNode extends BinaryNode implements Canonicalizable, VectorLIRLowerable {
    public static final NodeClass<SimdPrimitiveCompareNode> TYPE = NodeClass.create(SimdPrimitiveCompareNode.class);

    private final CanonicalCondition condition;
    private final boolean unorderedIsTrue;

    public SimdPrimitiveCompareNode(CanonicalCondition condition, ValueNode x, ValueNode y, boolean unorderedIsTrue, Stamp logicElementStamp) {
        super(TYPE, buildSimdStamp(logicElementStamp, x, y), x, y);
        this.condition = condition;
        this.unorderedIsTrue = unorderedIsTrue;
    }

    /**
     * Build a comparison of SIMD values {@code x} and {@code y}. These must be SIMD vectors of
     * primitive values.
     */
    public static SimdPrimitiveCompareNode simdCompare(CanonicalCondition condition, ValueNode x, ValueNode y, boolean unorderedIsTrue, VectorArchitecture vectorArch) {
        Stamp elementStamp = ((SimdStamp) x.stamp(NodeView.DEFAULT)).getComponent(0);
        GraalError.guarantee(elementStamp instanceof PrimitiveStamp, "can't compute bits for %s", elementStamp);
        return new SimdPrimitiveCompareNode(condition, x, y, unorderedIsTrue, vectorArch.maskStamp(elementStamp));
    }

    public CanonicalCondition getCondition() {
        return condition;
    }

    public boolean unorderedIsTrue() {
        return unorderedIsTrue;
    }

    private static Stamp buildSimdStamp(Stamp logicElementStamp, ValueNode x, ValueNode y) {
        SimdStamp xStamp = (SimdStamp) x.stamp(NodeView.DEFAULT);
        SimdStamp yStamp = (SimdStamp) y.stamp(NodeView.DEFAULT);
        GraalError.guarantee(xStamp.isCompatible(yStamp), "incompatible stamps: %s, %s", xStamp, yStamp);
        return SimdStamp.broadcast(logicElementStamp, xStamp.getVectorLength());
    }

    public LogicNode asScalar() {
        return asScalar(getX(), getY());
    }

    private LogicNode asScalar(ValueNode xValue, ValueNode yValue) {
        if (xValue.getStackKind().isNumericFloat()) {
            return CompareNode.createFloatCompareNode(graph(), getCondition(), xValue, yValue, unorderedIsTrue(), NodeView.DEFAULT);
        } else {
            return CompareNode.createCompareNode(graph(), getCondition(), xValue, yValue, null, NodeView.DEFAULT);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        Value xValue = builder.operand(x);
        Value yValue = builder.operand(y);
        builder.setResult(this, gen.emitVectorPackedComparison(condition, xValue, yValue, unorderedIsTrue));
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return stamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (x.isConstant() && y.isConstant()) {
            SimdConstant simdX = (SimdConstant) x.asConstant();
            SimdConstant simdY = (SimdConstant) y.asConstant();
            GraalError.guarantee(simdX.getVectorLength() == simdY.getVectorLength(), "incompatible constants: %s, %s", simdX, simdY);
            boolean[] results = new boolean[simdX.getVectorLength()];
            for (int i = 0; i < simdX.getVectorLength(); i++) {
                PrimitiveConstant xElement = (PrimitiveConstant) simdX.getValue(i);
                PrimitiveConstant yElement = (PrimitiveConstant) simdY.getValue(i);
                results[i] = getCondition().foldCondition(xElement, yElement, unorderedIsTrue());
            }
            SimdConstant mask;
            Stamp elementStamp = ((SimdStamp) stamp(NodeView.DEFAULT)).getComponent(0);
            if (elementStamp instanceof LogicValueStamp) {
                mask = SimdConstant.forOpmaskBlendSelector(results);
            } else {
                int kindIndex = CodeUtil.log2(((IntegerStamp) elementStamp).getBits() / 8);
                JavaKind maskKind = VectorLIRGeneratorTool.MASK_JAVA_KINDS[kindIndex];
                mask = SimdConstant.forBitmaskBlendSelector(results, maskKind);
            }
            return new ConstantNode(mask, stamp(NodeView.DEFAULT).constant(mask, null));
        }

        return this;
    }
}
