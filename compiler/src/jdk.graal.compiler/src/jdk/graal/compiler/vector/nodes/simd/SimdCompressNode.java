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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;

/**
 * This node is similar to {@link jdk.graal.compiler.nodes.calc.CompressBitsNode}, the difference is
 * that this node compresses the elements of the first vector operand based on the bitmask given by
 * the second opmask operand. This node would generate {@code vpcompress dst{mask}{z}, src} in the
 * AMD64 backend.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_1)
public class SimdCompressNode extends BinaryNode implements VectorLIRLowerable {
    public static final NodeClass<SimdCompressNode> TYPE = NodeClass.create(SimdCompressNode.class);

    protected SimdCompressNode(SimdStamp stamp, ValueNode src, ValueNode mask) {
        super(TYPE, stamp, src, mask);
        SimdStamp srcStamp = (SimdStamp) src.stamp(NodeView.DEFAULT);
        SimdStamp maskStamp = (SimdStamp) mask.stamp(NodeView.DEFAULT);
        GraalError.guarantee(stamp.isCompatible(srcStamp), "%s - %s", stamp, src);
        GraalError.guarantee(maskStamp.getComponent(0) instanceof LogicValueStamp, "%s", mask);
        GraalError.guarantee(srcStamp.getVectorLength() == maskStamp.getVectorLength(), "%s - %s", src, mask);
    }

    public static ValueNode create(ValueNode src, ValueNode mask) {
        SimdStamp stamp = (SimdStamp) src.stamp(NodeView.DEFAULT).unrestricted();
        return new SimdCompressNode(stamp, src, mask);
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind kind = builder.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        builder.setResult(this, gen.emitVectorCompress(kind, builder.operand(getX()), builder.operand(getY())));
    }

    @Override
    public Stamp foldStamp(Stamp srcStamp, Stamp maskStamp) {
        return srcStamp.unrestricted();
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return this;
    }
}
