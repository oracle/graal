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
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Constant;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

/**
 * This is the opposite of an SimdCutNode. Insert an SIMD/scalar value into a larger one and return
 * the result. E.g: insert([1, 2, 3, 4], [5, 6], 2) = [1, 2, 5, 6]
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public final class SimdInsertNode extends BinaryNode implements VectorLIRLowerable {

    public static final NodeClass<SimdInsertNode> TYPE = NodeClass.create(SimdInsertNode.class);
    protected int offset;

    protected SimdInsertNode(SimdStamp stamp, ValueNode vec, ValueNode val, int offset) {
        super(TYPE, stamp, vec, val);
        this.offset = offset;
    }

    public static ValueNode create(ValueNode vec, ValueNode val, int offset) {
        ValueNode n = tryCanonicalize(null, vec, val, offset);
        if (n != null) {
            return n;
        }

        SimdStamp stamp = computeStamp((SimdStamp) vec.stamp(NodeView.DEFAULT), val.stamp(NodeView.DEFAULT), offset);
        return new SimdInsertNode(stamp, vec, val, offset);
    }

    public int offset() {
        return offset;
    }

    @Override
    public Stamp foldStamp(Stamp vecStamp, Stamp valStamp) {
        return computeStamp((SimdStamp) vecStamp, valStamp, offset);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode vec, ValueNode val) {
        ValueNode n = tryCanonicalize(tool, vec, val, offset);
        return n != null ? n : this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitVectorInsert(offset, builder.operand(getX()), builder.operand(getY())));
    }

    private static ValueNode tryCanonicalize(CanonicalizerTool tool, ValueNode vec, ValueNode val, int offset) {
        Stamp vecStamp = vec.stamp(NodeView.from(tool));
        Stamp valStamp = val.stamp(NodeView.from(tool));
        if (vecStamp.isCompatible(valStamp)) {
            GraalError.guarantee(offset == 0, "illegal insert of %s into %s at %d", val, vec, offset);
        }

        SimdStamp vecSimdStamp = (SimdStamp) vecStamp;
        SimdStamp resStamp = computeStamp(vecSimdStamp, valStamp, offset);
        Constant resCon = resStamp.asConstant();
        if (resCon != null) {
            return new ConstantNode(resCon, resStamp);
        }

        return null;
    }

    private static SimdStamp computeStamp(SimdStamp vecStamp, Stamp valStamp, int offset) {
        Stamp[] res = new Stamp[vecStamp.getVectorLength()];
        for (int i = 0; i < res.length; i++) {
            res[i] = vecStamp.getComponent(i);
        }
        if (valStamp instanceof SimdStamp s) {
            GraalError.guarantee(s.getComponent(0).isCompatible(vecStamp.getComponent(0)), "trying to insert incompatible %s into %s", s, vecStamp);
            GraalError.guarantee(offset >= 0 && s.getVectorLength() + offset <= vecStamp.getVectorLength(), "out of bounds trying to insert %s into %s at %d", s, vecStamp, offset);
            for (int i = 0; i < s.getVectorLength(); i++) {
                res[i + offset] = s.getComponent(i);
            }
        } else {
            GraalError.guarantee(valStamp.isCompatible(vecStamp.getComponent(0)), "trying to insert incompatible %s into %s", valStamp, vecStamp);
            GraalError.guarantee(offset >= 0 && offset < vecStamp.getVectorLength(), "out of bounds trying to insert %s into %s at %d", valStamp, vecStamp, offset);
            res[offset] = valStamp;
        }
        return (SimdStamp) SimdStamp.create(res);
    }
}
