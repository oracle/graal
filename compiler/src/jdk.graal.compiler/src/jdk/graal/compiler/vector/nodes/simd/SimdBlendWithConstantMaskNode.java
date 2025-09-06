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

import java.util.Arrays;
import java.util.Map;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;

import jdk.vm.ci.meta.Value;

/**
 * A node which creates a new SIMD value from two input SIMD values based on a constant selection
 * mask. If the selection mask is not constant, use a {@link ConditionalNode} to represent the
 * blend.
 * <p/>
 *
 * The selection mask is {@code false} to select an element from the first SIMD value or
 * {@code true} to select an element from the second. This matches the conventions in hardware where
 * the 'false' value is listed first and the 'true' value second.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class SimdBlendWithConstantMaskNode extends BinaryNode implements VectorLIRLowerable {

    public static final NodeClass<SimdBlendWithConstantMaskNode> TYPE = NodeClass.create(SimdBlendWithConstantMaskNode.class);

    private final boolean[] selector;

    /**
     * Creates a new instance.
     *
     * @param falseValues the value from which to select elements when the corresponding selector
     *            entry is false
     * @param trueValues the value from which to select elements when the corresponding sector entry
     *            is true
     * @param selector the value which determines the element sources when the blend is performed
     */
    protected SimdBlendWithConstantMaskNode(ValueNode falseValues, ValueNode trueValues, boolean[] selector) {
        super(TYPE, SimdStamp.blend(falseValues.stamp(NodeView.DEFAULT), trueValues.stamp(NodeView.DEFAULT), selector), falseValues, trueValues);
        this.selector = selector;
    }

    public static SimdBlendWithConstantMaskNode create(ValueNode falseValues, ValueNode trueValues, boolean[] selector) {
        return new SimdBlendWithConstantMaskNode(falseValues, trueValues, selector);
    }

    @Override
    public Stamp foldStamp(Stamp falseStamp, Stamp trueStamp) {
        return SimdStamp.blend(falseStamp, trueStamp, selector);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode falseValues, ValueNode trueValues) {
        if (falseValues == trueValues) {
            return falseValues;
        }

        boolean allSecond = true;
        boolean allFirst = true;
        for (int i = 0; (allSecond || allFirst) && i < selector.length; ++i) {
            allFirst &= selector[i] == false;
            allSecond &= selector[i] == true;
        }
        if (allFirst) {
            return falseValues;
        }
        if (allSecond) {
            return trueValues;
        }
        return this;
    }

    public ValueNode getFalseValues() {
        return x;
    }

    public ValueNode getTrueValues() {
        return y;
    }

    public boolean[] getSelector() {
        return selector;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        Stamp falseStamp = getFalseValues().stamp(NodeView.DEFAULT);
        Stamp trueStamp = getTrueValues().stamp(NodeView.DEFAULT);
        GraalError.guarantee(falseStamp.isCompatible(trueStamp), "incompatible blend element stamps: %s / %s", falseStamp, trueStamp);

        Value result = gen.emitVectorBlend(builder.operand(getFalseValues()), builder.operand(getTrueValues()), selector);
        builder.setResult(this, result);
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        // IGV can't print boolean arrays.
        properties.put("selector", Arrays.toString(selector));
        return properties;
    }
}
