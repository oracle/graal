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
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.UnaryArithmeticNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.lir.amd64.AMD64AVX512ArithmeticLIRGenerator;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.MaskedOpMetaData;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteWithVectorIndicesNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.Value;

/**
 * A masked operation on AVX512 machine such as {@code vpaddd dst{mask}, src1, src2}.
 * <p>
 * Possible operations:
 * <li>
 * <ul>
 * Unary arithmetic operations such as {@code vpabsd dst{mask}, src1}
 * </ul>
 * <ul>
 * Binary arithmetic operations such as {@code vpaddd dst{mask}, src1, src2}
 * </ul>
 * <ul>
 * Permute operations such as {@code vpermb dst{mask}, src2, src1}
 * </ul>
 * <ul>
 * Binary comparison operations such as {@code vpcmpeqd dst{mask}, src1, src2}
 * </ul>
 * </li>
 * <p>
 * There are 2 types of masked operations, merge-masking and zero-masking. For the merge-masking
 * cases, the elements that are unset in {@code mask} are taken from {@code dst} while for
 * zero-masking, the elements that are unset in {@code mask} are cleared.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public class AVX512MaskedOpNode extends FloatingNode implements VectorLIRLowerable {

    public static final NodeClass<AVX512MaskedOpNode> TYPE = NodeClass.create(AVX512MaskedOpNode.class);

    // The destination, null for zero-masking and non-null for merge-masking
    @OptionalInput protected ValueNode background;
    @Input protected ValueNode mask;
    @Input protected ValueNode src1;
    @OptionalInput protected ValueNode src2;

    private final MaskedOpMetaData meta;

    protected AVX512MaskedOpNode(SimdStamp stamp, ValueNode op, ValueNode background, ValueNode mask, ValueNode src1, ValueNode src2) {
        super(TYPE, stamp);
        GraalError.guarantee(background == null || background.stamp(NodeView.DEFAULT).isCompatible(stamp), "must be compatible %s - %s", stamp, background);
        SimdStamp maskStamp = (SimdStamp) mask.stamp(NodeView.DEFAULT);
        GraalError.guarantee(maskStamp.getComponent(0) instanceof LogicValueStamp, "must be an opmask %s", mask);
        GraalError.guarantee(stamp.getVectorLength() == maskStamp.getVectorLength(), "must have the same length %s - %s", stamp, mask);
        this.background = background;
        this.mask = mask;
        this.src1 = src1;
        this.src2 = src2;
        this.meta = new MaskedOpMetaData(op);
    }

    public static AVX512MaskedOpNode createUnaryArithmetic(UnaryArithmeticNode<?> op, ValueNode dst, ValueNode mask, ValueNode src) {
        SimdStamp srcStamp = (SimdStamp) src.stamp(NodeView.DEFAULT).unrestricted();
        return new AVX512MaskedOpNode(srcStamp, op, dst, mask, src, null);
    }

    public static AVX512MaskedOpNode createBinaryArithmetic(BinaryArithmeticNode<?> op, ValueNode dst, ValueNode mask, ValueNode src1, ValueNode src2) {
        SimdStamp src1Stamp = (SimdStamp) src1.stamp(NodeView.DEFAULT).unrestricted();
        GraalError.guarantee(src1Stamp.isCompatible(src2.stamp(NodeView.DEFAULT)), "must be compatible %s - %s", src1, src2);
        return new AVX512MaskedOpNode(src1Stamp, op, dst, mask, src1, src2);
    }

    public static AVX512MaskedOpNode createPermute(SimdPermuteWithVectorIndicesNode op, ValueNode dst, ValueNode mask, ValueNode src, ValueNode indices) {
        SimdStamp srcStamp = (SimdStamp) src.stamp(NodeView.DEFAULT).unrestricted();
        SimdStamp indicesStamp = (SimdStamp) indices.stamp(NodeView.DEFAULT).unrestricted();
        GraalError.guarantee(PrimitiveStamp.getBits(srcStamp.getComponent(0)) == PrimitiveStamp.getBits(indicesStamp.getComponent(0)), "must have the same width %s - %s", src, indices);
        return new AVX512MaskedOpNode(srcStamp, op, dst, mask, src, indices);
    }

    public static AVX512MaskedOpNode createBinaryComparison(SimdPrimitiveCompareNode op, ValueNode mask, ValueNode src1, ValueNode src2) {
        GraalError.guarantee(src1.stamp(NodeView.DEFAULT).isCompatible(src2.stamp(NodeView.DEFAULT)), "must be compatible %s - %s", src1, src2);
        SimdStamp stamp = (SimdStamp) mask.stamp(NodeView.DEFAULT).unrestricted();
        return new AVX512MaskedOpNode(stamp, op, null, mask, src1, src2);
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind resultKind = builder.getLIRGeneratorTool().getLIRKind(stamp);
        AMD64AVX512ArithmeticLIRGenerator concreteGen = (AMD64AVX512ArithmeticLIRGenerator) gen;
        Value src2Operand = src2 == null ? null : builder.operand(src2);
        Value lirOp;
        if (background == null) {
            lirOp = concreteGen.emitMaskedZeroOp(resultKind, meta, builder.operand(mask), builder.operand(src1), src2Operand);
        } else {
            lirOp = concreteGen.emitMaskedMergeOp(resultKind, meta, builder.operand(background), builder.operand(mask), builder.operand(src1), src2Operand);
        }
        builder.setResult(this, lirOp);
    }
}
