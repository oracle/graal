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

import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

/**
 * Node that appends the values of one vector to another to produce a single wider value.
 *
 * Not all platforms may support all concatenations see
 * {@code VectorArchitecture.supportsVectorConcat}
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class SimdConcatNode extends BinaryNode implements VectorLIRLowerable {

    public static final NodeClass<SimdConcatNode> TYPE = NodeClass.create(SimdConcatNode.class);

    /**
     * Creates a new SimdConcatNode instance.
     *
     * @param x the low part of the new vector
     * @param y the high part of the new vector
     */
    public SimdConcatNode(ValueNode x, ValueNode y) {
        super(TYPE, ((SimdStamp) x.stamp(NodeView.DEFAULT)).concat((SimdStamp) y.stamp(NodeView.DEFAULT)), x, y);
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRGeneratorTool tool = builder.getLIRGeneratorTool();

        Stamp xStamp = x.stamp(NodeView.DEFAULT);
        Stamp yStamp = y.stamp(NodeView.DEFAULT);
        assert xStamp instanceof SimdStamp && yStamp instanceof SimdStamp : "Unsupported stamps found for SIMD Concat";

        Value result = gen.emitVectorSimpleConcat(tool.getLIRKind(SimdStamp.concat(xStamp, yStamp)), builder.operand(getX()), builder.operand(getY()));
        builder.setResult(this, result);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return this;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return SimdStamp.concat(stampX, stampY);
    }
}
