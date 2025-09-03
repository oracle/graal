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
package jdk.graal.compiler.vector.nodes.simd;

import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.PrimitiveConstant;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

/**
 * Node that reorders the elements of a vector based on the indices given by another vector. The
 * result will have the stamp compatible with that of the first operand. The operands must have
 * their stamps of type {@link SimdStamp} with the same length and element size. The second operand
 * must have its elements being integral values in the inclusive interval [0, VLENGTH - 1].
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public class SimdPermuteWithVectorIndicesNode extends BinaryNode implements VectorLIRLowerable {

    public static final NodeClass<SimdPermuteWithVectorIndicesNode> TYPE = NodeClass.create(SimdPermuteWithVectorIndicesNode.class);

    protected SimdPermuteWithVectorIndicesNode(SimdStamp stamp, ValueNode x, ValueNode y) {
        super(TYPE, stamp, x, y);
        SimdStamp xStamp = (SimdStamp) x.stamp(NodeView.DEFAULT);
        SimdStamp yStamp = (SimdStamp) y.stamp(NodeView.DEFAULT);
        GraalError.guarantee(xStamp.getVectorLength() == yStamp.getVectorLength(), "%s - %s", x, y);
        GraalError.guarantee(PrimitiveStamp.getBits(xStamp.getComponent(0)) == PrimitiveStamp.getBits(yStamp.getComponent(0)), "%s - %s", x, y);
        GraalError.guarantee(yStamp.getComponent(0) instanceof IntegerStamp, "%s", y);
    }

    public static ValueNode create(ValueNode source, ValueNode indices) {
        ValueNode canonical = tryCanonical(source, indices);
        if (canonical != null) {
            return canonical;
        }

        SimdStamp stamp = foldStamp((SimdStamp) source.stamp(NodeView.DEFAULT), (SimdStamp) indices.stamp(NodeView.DEFAULT));
        return new SimdPermuteWithVectorIndicesNode(stamp, source, indices);
    }

    @Override
    public Stamp foldStamp(Stamp xStamp, Stamp yStamp) {
        SimdStamp source = (SimdStamp) xStamp;
        SimdStamp indices = (SimdStamp) yStamp;
        return foldStamp(source, indices);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode res = tryCanonical(forX, forY);
        return res != null ? res : this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind kind = builder.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        builder.setResult(this, gen.emitVectorPermute(kind, builder.operand(getX()), builder.operand(getY())));
    }

    private static SimdStamp foldStamp(SimdStamp source, SimdStamp indices) {
        Stamp[] res = new Stamp[source.getVectorLength()];
        Stamp base = source.getComponent(0).empty();

        // For each element of the result vector, find all the possible values of the corresponding
        // index value, then meet all the stamps of the elements of the source vector at those
        // values
        for (int i = 0; i < source.getVectorLength(); i++) {
            IntegerStamp index = (IntegerStamp) indices.getComponent(i);
            Stamp element = base;
            for (int j = 0; j < source.getVectorLength(); j++) {
                if (index.contains(j)) {
                    element = element.meet(source.getComponent(j));
                }
            }
            if (element.isEmpty()) {
                return (SimdStamp) source.empty();
            }
            res[i] = element;
        }
        return new SimdStamp(res);
    }

    private static ValueNode tryCanonical(ValueNode x, ValueNode y) {
        if (x instanceof SimdBroadcastNode) {
            return x;
        }

        if (y.isConstant()) {
            SimdConstant yCon = (SimdConstant) y.asConstant();
            int[] indices = new int[yCon.getVectorLength()];
            for (int i = 0; i < yCon.getVectorLength(); i++) {
                indices[i] = (int) ((PrimitiveConstant) yCon.getValue(i)).asLong();
            }
            return new SimdPermuteNode(x, indices);
        }

        return null;
    }
}
