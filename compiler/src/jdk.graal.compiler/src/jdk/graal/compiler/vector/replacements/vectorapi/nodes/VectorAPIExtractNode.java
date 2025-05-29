/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi.nodes;

import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import java.util.List;

import jdk.graal.compiler.vector.nodes.amd64.OpMaskToIntegerNode;
import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIType;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdToBitMaskNode;

/**
 * Intrinsics node for the {@code VectorSupport::extract} method. This returns the bit
 * representation of the element and the canonicalizer will elide the bit-casts.
 * <p>
 * {@code
 * result = reinterpretAsLong(v[idx]);
 * }
 *
 * {@snippet :
 * public static <VM extends VectorPayload, E> long extract(Class<? extends VM> vClass, Class<E> eClass,
 *                 int length,
 *                 VM vm, int i,
 *                 VecExtractOp<VM> defaultImpl);
 * }
 */
@NodeInfo
public class VectorAPIExtractNode extends VectorAPISinkNode implements Canonicalizable {

    public static final NodeClass<VectorAPIExtractNode> TYPE = NodeClass.create(VectorAPIExtractNode.class);

    /* Indices into the macro argument list for relevant input values. */
    private static final int VM_ARG_INDEX = 3;
    private static final int IDX_ARG_INDEX = 4;

    private final VectorAPIType inputType;

    protected VectorAPIExtractNode(MacroParams params, VectorAPIType inputType, FrameState stateAfter) {
        super(TYPE, params);
        this.inputType = inputType;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIExtractNode create(MacroParams params, VectorAPIType inputType) {
        return new VectorAPIExtractNode(params, inputType, null);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vector());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (inputType != null) {
            return this;
        }

        VectorAPIType newInputType = VectorAPIType.ofConstant(getArgument(VM_ARG_INDEX), tool);
        if (newInputType != null) {
            return new VectorAPIExtractNode(copyParams(), newInputType, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        return inputType != null && (inputType.isMask || idx().isJavaConstant());
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        ValueNode vector = expanded.get(vector());
        if (inputType.isMask) {
            ValueNode bitmask;

            if (((SimdStamp) vector.stamp(NodeView.DEFAULT)).getComponent(0) instanceof LogicValueStamp) {
                bitmask = OpMaskToIntegerNode.create(vector);
            } else {
                bitmask = new SimdToBitMaskNode(vector);
            }
            int width = ((IntegerStamp) bitmask.stamp(NodeView.DEFAULT)).getBits();
            ValueNode mask = LeftShiftNode.create(ConstantNode.forIntegerBits(width, 1), idx(), NodeView.DEFAULT);
            LogicNode cond = IntegerTestNode.create(bitmask, mask, NodeView.DEFAULT);
            return ConditionalNode.create(cond, ConstantNode.forLong(0), ConstantNode.forLong(1), NodeView.DEFAULT);
        }

        ValueNode element = new SimdCutNode(vector, idx().asJavaConstant().asInt(), 1);
        return reinterpretAsLong(element);
    }

    private ValueNode vector() {
        return getArgument(VM_ARG_INDEX);
    }

    private ValueNode idx() {
        return getArgument(IDX_ARG_INDEX);
    }
}
