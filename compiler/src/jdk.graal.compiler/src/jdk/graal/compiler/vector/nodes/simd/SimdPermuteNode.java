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

import jdk.vm.ci.meta.Constant;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.replacements.nodes.ReverseBytesNode;

import java.util.Arrays;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

/**
 * Node that reorders the elements of a vector - potentially duplicating or dropping elements.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class SimdPermuteNode extends UnaryNode {

    public static final NodeClass<SimdPermuteNode> TYPE = NodeClass.create(SimdPermuteNode.class);

    /**
     * Specification of destination vector elements in terms of source vector element indices
     * (starting from 0). As a special case, setting an element index to -1 indicates that the
     * element should be set to zero.
     */
    protected final int[] destinationMapping;

    public SimdPermuteNode(ValueNode value, int[] destinationMapping) {
        super(TYPE, ((SimdStamp) value.stamp(NodeView.DEFAULT)).permute(destinationMapping), value);
        this.destinationMapping = Arrays.copyOf(destinationMapping, destinationMapping.length);
    }

    public int[] getDestinationMapping() {
        return Arrays.copyOf(destinationMapping, destinationMapping.length);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(((SimdStamp) getValue().stamp(NodeView.DEFAULT)).permute(destinationMapping));
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        boolean isReverse = ((SimdStamp) getValue().stamp(NodeView.DEFAULT)).getVectorLength() == destinationMapping.length;
        boolean isIdentity = isReverse;
        for (int i = 0; (isReverse || isIdentity) && i < destinationMapping.length; ++i) {
            if (destinationMapping[i] != destinationMapping.length - 1 - i) {
                isReverse = false;
            }
            if (destinationMapping[i] != i) {
                isIdentity = false;
            }
        }

        if (isIdentity) {
            return forValue;
        } else if (isReverse && forValue instanceof ReinterpretNode) {
            // SimdPermute[reverse](Reinterpret[Integer->SIMD i8](x)) => Reinterpret[Integer->SIMD
            // i8](ReverseBytes(x))
            Stamp forValueStamp = forValue.stamp(NodeView.from(tool));
            Stamp componentStamp = ((SimdStamp) forValueStamp).getComponent(0);
            if (componentStamp instanceof IntegerStamp && ((IntegerStamp) componentStamp).getBits() == 8) {
                ValueNode reinterpretedValue = ((ReinterpretNode) forValue).getValue();
                Stamp reinterpretedStamp = reinterpretedValue.stamp(NodeView.from(tool));
                if (reinterpretedStamp instanceof IntegerStamp && ((IntegerStamp) reinterpretedStamp).getBits() == ((SimdStamp) forValueStamp).getVectorLength() * 8) {
                    return ReinterpretNode.create(forValueStamp, new ReverseBytesNode(reinterpretedValue), NodeView.from(tool));
                }
            }
        } else if (forValue instanceof ConstantNode) {
            SimdConstant input = (SimdConstant) (forValue).asConstant();
            Constant[] newValues = new Constant[destinationMapping.length];
            for (int i = 0; i < destinationMapping.length; ++i) {
                if (destinationMapping[i] == -1) {
                    return this;
                }
                newValues[i] = input.getValue(destinationMapping[i]);
            }
            return ConstantNode.forConstant(stamp(NodeView.from(tool)), new SimdConstant(newValues), tool.getMetaAccess());
        } else if (forValue instanceof ReinterpretNode) {
            // A ReinterpretNode can appear in a SIMD context to convert a floating point value to
            // a SIMD floating point vector of length 1
            ReinterpretNode reinterpret = (ReinterpretNode) forValue;
            SimdStamp reinterpretStamp = ((SimdStamp) reinterpret.stamp(NodeView.from(tool)));
            if (reinterpretStamp.getVectorLength() == 1 && !(reinterpret.getValue().stamp(NodeView.from(tool)) instanceof SimdStamp)) {
                return new SimdBroadcastNode(reinterpret.getValue(), ((SimdStamp) stamp(NodeView.from(tool))).getVectorLength());
            }
        } else if (forValue instanceof SimdPermuteNode) {
            SimdPermuteNode forValuePerm = (SimdPermuteNode) forValue;
            int[] newDestinationMapping = new int[destinationMapping.length];
            for (int i = 0; i < destinationMapping.length; ++i) {
                newDestinationMapping[i] = destinationMapping[i] > -1 ? forValuePerm.destinationMapping[destinationMapping[i]] : -1;
            }
            return new SimdPermuteNode(forValuePerm.getValue(), newDestinationMapping);
        } else if (forValue instanceof SimdBroadcastNode) {
            SimdBroadcastNode broadcastNode = (SimdBroadcastNode) forValue;
            return new SimdBroadcastNode(broadcastNode.getValue(), destinationMapping.length);
        }
        return this;
    }
}
