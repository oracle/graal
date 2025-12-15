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
package jdk.graal.compiler.vector.nodes.amd64;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.lir.amd64.AMD64VectorArithmeticLIRGenerator;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

/**
 * A slice operation concatenates its inputs into a sequence of {@code VLENGTH * 2} elements, then
 * {@code VLENGTH} elements are collected starting at index {@link #origin} to form the result. If
 * the 2 inputs are the same, then this operation is the same as rotating the input left by
 * {@link #origin} elements.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public class AMD64SimdSliceNode extends FloatingNode implements VectorLIRLowerable {

    public static final NodeClass<AMD64SimdSliceNode> TYPE = NodeClass.create(AMD64SimdSliceNode.class);

    @Input protected ValueNode src1;
    @Input protected ValueNode src2;
    private final int origin;

    protected AMD64SimdSliceNode(SimdStamp stamp, ValueNode src1, ValueNode src2, int origin) {
        super(TYPE, stamp);
        this.src1 = src1;
        this.src2 = src2;
        this.origin = origin;
    }

    public static AMD64SimdSliceNode create(ValueNode src1, ValueNode src2, int origin) {
        GraalError.guarantee(src1.stamp(NodeView.DEFAULT) instanceof SimdStamp, "unexpected input stamp %s", src1);
        SimdStamp stamp = (SimdStamp) src1.stamp(NodeView.DEFAULT).unrestricted();
        GraalError.guarantee(stamp.isCompatible(src2.stamp(NodeView.DEFAULT)), "unexpected input stamps: %s, %s", src1, src2);
        GraalError.guarantee(origin > 0 && origin < stamp.getVectorLength(), "unexpected origin %d of vector input %s", origin, src1);
        return new AMD64SimdSliceNode(stamp, src1, src2, origin);
    }

    public ValueNode getSrc1() {
        return src1;
    }

    public ValueNode getSrc2() {
        return src2;
    }

    public int getOrigin() {
        return origin;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind resultKind = builder.getLIRGeneratorTool().getLIRKind(stamp);
        builder.setResult(this, ((AMD64VectorArithmeticLIRGenerator) gen).emitVectorSlice(resultKind, builder.operand(src1), builder.operand(src2), origin));
    }
}
