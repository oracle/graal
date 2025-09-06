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
package jdk.graal.compiler.vector.nodes.simd;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.TernaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.vm.ci.meta.Value;

/**
 * A node which creates a new SIMD value from two input SIMD values based on a non-constant
 * selection mask given as a SIMD vector of {@link LogicValueStamp} elements.
 * <p/>
 *
 * The selection mask is {@code false} to select an element from the first SIMD value or
 * {@code true} to select an element from the second. This matches the conventions in hardware where
 * the 'false' value is listed first and the 'true' value second.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class SimdBlendWithLogicMaskNode extends TernaryNode implements VectorLIRLowerable {

    public static final NodeClass<SimdBlendWithLogicMaskNode> TYPE = NodeClass.create(SimdBlendWithLogicMaskNode.class);

    /**
     * Creates a new instance.
     *
     * @param falseValues the value from which to select elements when the corresponding selector
     *            entry is false
     * @param trueValues the value from which to select elements when the corresponding sector entry
     *            is true
     * @param selector the value which determines the element sources when the blend is performed
     */
    protected SimdBlendWithLogicMaskNode(ValueNode falseValues, ValueNode trueValues, ValueNode selector) {
        super(TYPE, computeStamp(falseValues, trueValues), falseValues, trueValues, selector);
    }

    public static SimdBlendWithLogicMaskNode create(ValueNode falseValues, ValueNode trueValues, ValueNode selector) {
        return new SimdBlendWithLogicMaskNode(falseValues, trueValues, selector);
    }

    private static Stamp computeStamp(ValueNode falseValues, ValueNode trueValues) {
        return falseValues.stamp(NodeView.DEFAULT).meet(trueValues.stamp(NodeView.DEFAULT));
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        return stampX.meet(stampY);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY, ValueNode forZ) {
        if (forX == forY) {
            return forX;
        }

        ValueNode mask = forZ;
        Stamp toStamp = mask.stamp(NodeView.DEFAULT);
        while (mask instanceof ReinterpretNode simdMask && SimdStamp.isOpmask(simdMask.stamp(NodeView.DEFAULT))) {
            mask = simdMask.getValue();
        }
        Stamp fromStamp = mask.stamp(NodeView.DEFAULT);
        if (mask.isConstant()) {
            SimdConstant constantSelector = (SimdConstant) mask.asConstant();
            boolean[] selectors = new boolean[constantSelector.getVectorLength()];
            for (int i = 0; i < constantSelector.getVectorLength(); i++) {
                selectors[i] = constantSelector.getPrimitiveValue(i) != 0;
            }
            return SimdBlendWithConstantMaskNode.create(forX, forY, selectors);
        }

        NarrowNode narrow = null;
        if (mask instanceof NarrowNode n) {
            narrow = n;
            mask = narrow.getValue();
        }
        boolean negate = false;
        while (mask instanceof NotNode n) {
            negate = !negate;
            mask = n.getValue();
        }
        if (negate) {
            /* We can permute this blend's inputs and skip the negation. */
            ValueNode newSelector = mask;
            if (narrow != null) {
                newSelector = NarrowNode.create(newSelector, narrow.getInputBits(), narrow.getResultBits(), NodeView.from(tool));
            }
            if (!fromStamp.isCompatible(toStamp)) {
                // If we reach here, we skipped SimdLogicReinterpretNodes, therefore the following
                // casts should be fine.
                newSelector = SimdStamp.reinterpretMask((SimdStamp) toStamp, newSelector, NodeView.DEFAULT);
            }
            return SimdBlendWithLogicMaskNode.create(forY, forX, newSelector);
        }

        return this;
    }

    public ValueNode getFalseValues() {
        return x;
    }

    public ValueNode getTrueValues() {
        return y;
    }

    public ValueNode getSelector() {
        return z;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        Stamp falseStamp = getFalseValues().stamp(NodeView.DEFAULT);
        Stamp trueStamp = getTrueValues().stamp(NodeView.DEFAULT);
        GraalError.guarantee(falseStamp.isCompatible(trueStamp), "incompatible blend element stamps: %s / %s", falseStamp, trueStamp);

        Value result = gen.emitVectorBlend(builder.operand(getFalseValues()), builder.operand(getTrueValues()), builder.operand(getSelector()));
        builder.setResult(this, result);
    }
}
