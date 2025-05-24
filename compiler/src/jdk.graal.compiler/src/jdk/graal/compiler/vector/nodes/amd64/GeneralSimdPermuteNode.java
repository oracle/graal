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

package jdk.graal.compiler.vector.nodes.amd64;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_16;

import java.util.Arrays;
import java.util.Map;

import jdk.graal.compiler.core.amd64.AMD64LIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.lir.amd64.AMD64VectorArithmeticLIRGenerator;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.Value;

/**
 * Node that reorders the elements of a vector - potentially duplicating or dropping elements.
 *
 * This AMD64 specific node maps to PSHUFB and can handle any permutation operation at the cost of
 * requiring a wide constant to be stored in the generated program and loaded into a vector
 * register. This handles all byte permutes and anything that is not lane symmetric.
 */
@NodeInfo(cycles = CYCLES_16, size = NodeSize.SIZE_16)
public class GeneralSimdPermuteNode extends UnaryNode implements VectorLIRLowerable {

    public static final NodeClass<GeneralSimdPermuteNode> TYPE = NodeClass.create(GeneralSimdPermuteNode.class);
    protected byte[] selector;

    /**
     * Returns a {@code byte[]} that describes the byte movements in a register to achieve a given
     * permutation.
     *
     * @param arch VectorArchitecture description object
     * @param value The vector which will be permuted
     * @param destinationMapping The mapping of destination indices back to source indices
     * @return A {@code byte[]} encoding the destinationMapping in a little endian format
     */
    protected static byte[] buildByteMappingArray(VectorArchitecture arch, ValueNode value, int[] destinationMapping) {
        Stamp stamp = value.stamp(NodeView.DEFAULT);
        assert stamp instanceof SimdStamp : "Unsupported stamp kind " + stamp;
        Stamp scalar = ((SimdStamp) stamp).getComponent(0);
        int elementSizeInBytes = arch.getVectorStride(scalar);
        int vectorSizeInElements = arch.getSupportedVectorPermuteLength(scalar, Math.max(((SimdStamp) stamp).getVectorLength(), destinationMapping.length));

        assert vectorSizeInElements > 1 : "Vector store not supported for given scalar type " + scalar;
        assert vectorSizeInElements <= arch.getMaxVectorLength(scalar) : "Unsupported vector length for permutation";

        byte[] toReturn = new byte[Math.max(vectorSizeInElements * elementSizeInBytes, 16)];

        // we may not be selecting an element for every original element so set all entries to -1
        // (eg to 0 on select)
        // before populating the indices
        Arrays.fill(toReturn, (byte) -1);

        for (int idx = destinationMapping.length - 1; idx >= 0; --idx) {
            for (int b = 0; b < elementSizeInBytes; ++b) {
                if (destinationMapping[idx] > -1) {
                    toReturn[(elementSizeInBytes * (idx + 1)) - (b + 1)] = (byte) ((destinationMapping[idx] * elementSizeInBytes) + (elementSizeInBytes - 1 - b));
                }
            }
        }
        return toReturn;
    }

    /**
     * Creates a new {@code GeneralSimdPermuteNode}.
     *
     * @param stamp the result type of this instruction
     * @param value the vector to permute
     * @param selector a byte array encoding the permutation operation
     */
    public GeneralSimdPermuteNode(Stamp stamp, ValueNode value, byte[] selector) {
        super(TYPE, stamp, value);
        this.selector = selector;
    }

    public static GeneralSimdPermuteNode create(VectorArchitecture arch, ValueNode value, int[] destinationMapping) {
        return new GeneralSimdPermuteNode(
                        ((SimdStamp) value.stamp(NodeView.DEFAULT)).permute(destinationMapping),
                        value,
                        buildByteMappingArray(arch, value, destinationMapping));
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        AMD64LIRGenerator tool = (AMD64LIRGenerator) builder.getLIRGeneratorTool();
        LIRKind resultKind = tool.getLIRKind(stamp(NodeView.DEFAULT));
        Value result = ((AMD64VectorArithmeticLIRGenerator) gen).emitConstShuffleBytes(resultKind, builder.operand(getValue()), selector);
        builder.setResult(this, result);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        return this;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        // IGV can't print byte arrays.
        properties.put("selector", Arrays.toString(selector));
        return properties;
    }
}
