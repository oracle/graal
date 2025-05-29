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

package jdk.graal.compiler.vector.nodes.aarch64;

import jdk.graal.compiler.asm.aarch64.ASIMDKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64PermuteOp;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_2)
public class AArch64PermuteNode extends UnaryNode implements LIRLowerable {
    public static final NodeClass<AArch64PermuteNode> TYPE = NodeClass.create(AArch64PermuteNode.class);

    protected int[] destinationMapping;
    private final int elementByteSize;

    protected AArch64PermuteNode(Stamp stamp, ValueNode value, int[] destinationMapping, int elementByteSize) {
        super(TYPE, stamp, value);

        this.elementByteSize = elementByteSize;
        this.destinationMapping = destinationMapping;
    }

    public static AArch64PermuteNode create(VectorArchitecture arch, Stamp pStamp, SimdPermuteNode permute) {
        assert pStamp instanceof SimdStamp : pStamp;
        ValueNode input = permute.getValue();
        int[] mapping = permute.getDestinationMapping();

        Stamp elementStamp = ((SimdStamp) input.stamp(NodeView.DEFAULT)).getComponent(0);
        assert (elementStamp instanceof PrimitiveStamp) || elementStamp instanceof AbstractObjectStamp : "Unsupported element stamp " + elementStamp;
        int elementByteSize = arch.getVectorStride(elementStamp);

        return new AArch64PermuteNode(pStamp, input, mapping, elementByteSize);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        /*
         * Currently, this node is created after the last canonicalization round, so this shouldn't
         * be called.
         */
        return this;
    }

    private SimdConstant generatePermuteMapping() {
        int mapLength = destinationMapping.length;
        byte[] byteMap = new byte[mapLength * elementByteSize];
        int mapIdx = 0;
        for (int idx : destinationMapping) {
            for (int byteOffset = 0; byteOffset < elementByteSize; byteOffset++) {
                int byteIndex = idx * elementByteSize + byteOffset;
                assert byteIndex >= 0 && byteIndex < 16 : byteIndex;
                /*
                 * Note NumUtil.safeToByte caps byteIndex at 127; However, this is fine since the
                 * index should be < 16 on AArch64 NEON.
                 */
                byteMap[mapIdx++] = NumUtil.safeToByte(byteIndex);
            }
        }

        Constant[] constants = new Constant[byteMap.length];
        for (int i = 0; i < byteMap.length; i++) {
            constants[i] = JavaConstant.forByte(byteMap[i]);
        }

        return new SimdConstant(constants);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value input = gen.operand(value);
        /*
         * TODO things to maybe check for in the future.
         *
         * right rotate (ext)
         *
         * even copy (trn1)
         *
         * odd copy (trn2)
         *
         * low copy (zip1)
         *
         * high copy (zip2)
         *
         * even repeat (uzp1)
         *
         * odd repeat (uzp2)
         */

        SimdConstant simdConstant = generatePermuteMapping();
        LIRKind constantKind = LIRKind.value(ASIMDKind.getASIMDKind(AArch64Kind.BYTE, simdConstant.getVectorLength()));
        Value permuteMapping = tool.emitConstant(constantKind, simdConstant);
        Variable result = tool.newVariable(tool.getLIRKind(stamp(NodeView.DEFAULT)));
        tool.append(new AArch64PermuteOp.ASIMDBinaryOp(AArch64PermuteOp.TBL, result, tool.asAllocatable(input), tool.asAllocatable(permuteMapping)));
        gen.setResult(this, result);
    }
}
