/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_4;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil.Signedness;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.lir.amd64.AMD64VectorArithmeticLIRGenerator;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.Value;

/**
 * AMD64 vector instruction node for {@code VPMADDWD} and {@code VPMADDUBSW}. The matched graph
 * selects the even and odd lanes of two input vectors, widens those selected lanes, multiplies the
 * selected even lanes and odd lanes separately, and adds the products.
 *
 * <pre>
 * VPMADDWD:   result[i] = a[2 * i] * b[2 * i] + a[2 * i + 1] * b[2 * i + 1]
 * VPMADDUBSW: result[i] = saturateSignedShort((a[2 * i] & 0xff) * b[2 * i] + (a[2 * i + 1] & 0xff) * b[2 * i + 1])
 * </pre>
 */
@NodeInfo(cycles = CYCLES_4, size = SIZE_1)
public class AMD64SimdPairwiseMultiplyAddNode extends BinaryNode implements VectorLIRLowerable {

    public static final NodeClass<AMD64SimdPairwiseMultiplyAddNode> TYPE = NodeClass.create(AMD64SimdPairwiseMultiplyAddNode.class);

    public enum OpKind {
        SIGNED_SHORTS_TO_INTS,
        UNSIGNED_SIGNED_BYTES_TO_SHORTS_SATURATING;
    }

    private final OpKind opKind;

    public AMD64SimdPairwiseMultiplyAddNode(ValueNode x, ValueNode y, OpKind opKind) {
        super(TYPE, computeResultStamp(x.stamp(NodeView.DEFAULT), opKind), x, y);
        this.opKind = opKind;
    }

    public OpKind getOpKind() {
        return opKind;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return this;
    }

    @Override
    public Stamp foldStamp(Stamp xStamp, Stamp yStamp) {
        return computeResultStamp(xStamp, opKind);
    }

    private static Stamp computeResultStamp(Stamp xStamp, OpKind opKind) {
        SimdStamp inputStamp = (SimdStamp) xStamp;
        int inputLength = inputStamp.getVectorLength();
        return switch (opKind) {
            case SIGNED_SHORTS_TO_INTS -> SimdStamp.broadcast(IntegerStamp.create(Integer.SIZE), inputLength / 2);
            case UNSIGNED_SIGNED_BYTES_TO_SHORTS_SATURATING -> SimdStamp.broadcast(IntegerStamp.create(Short.SIZE), inputLength / 2);
        };
    }

    public static AMD64SimdPairwiseMultiplyAddNode tryMatch(ValueNode x, ValueNode y, OpKind opKind) {
        PairwiseMultiplyAddOperands operands = switch (opKind) {
            case SIGNED_SHORTS_TO_INTS -> matchingProducts(
                            matchProduct(x, Signedness.SIGNED, Signedness.SIGNED, Short.SIZE, Integer.SIZE),
                            matchProduct(y, Signedness.SIGNED, Signedness.SIGNED, Short.SIZE, Integer.SIZE),
                            true);
            case UNSIGNED_SIGNED_BYTES_TO_SHORTS_SATURATING -> matchingProducts(matchUnsignedSignedProduct(x), matchUnsignedSignedProduct(y), false);
        };
        return operands == null ? null : new AMD64SimdPairwiseMultiplyAddNode(operands.left(), operands.right(), opKind);
    }

    private record PairwiseMultiplyAddOperands(ValueNode left, ValueNode right) {
    }

    /**
     * Matches the two multiplication terms from the pairwise expression described in the class
     * Javadoc, before their products are added:
     *
     * <pre>
     * a[2 * i] * b[2 * i]
     * a[2 * i + 1] * b[2 * i + 1]
     * </pre>
     *
     * {@code x} and {@code y} may be these two terms in either order. If
     * {@code allowSwappedSources} is true, one term may use {@code b[...]} as its left operand and
     * {@code a[...]} as its right operand. This is valid for signed multiplication, but not for
     * {@code VPMADDUBSW}, where {@code a[...]} is unsigned bytes and {@code b[...]} is signed
     * bytes.
     */
    private static PairwiseMultiplyAddOperands matchingProducts(Product x, Product y, boolean allowSwappedSources) {
        if (x == null || y == null || x.even() == y.even()) {
            return null;
        }
        if (x.left() == y.left() && x.right() == y.right()) {
            return new PairwiseMultiplyAddOperands(x.left(), x.right());
        }
        if (allowSwappedSources && x.left() == y.right() && x.right() == y.left()) {
            return new PairwiseMultiplyAddOperands(x.left(), x.right());
        }
        return null;
    }

    /*
     * One multiplication in the expanded graph. The inputs have already been matched as either both
     * even lanes or both odd lanes.
     */
    private record Product(ValueNode left, ValueNode right, boolean even) {
    }

    private static Product matchUnsignedSignedProduct(ValueNode product) {
        Product productXY = matchProduct(product, Signedness.UNSIGNED, Signedness.SIGNED, Byte.SIZE, Short.SIZE);
        if (productXY != null) {
            return productXY;
        }
        Product productYX = matchProduct(product, Signedness.SIGNED, Signedness.UNSIGNED, Byte.SIZE, Short.SIZE);
        if (productYX != null) {
            return new Product(productYX.right(), productYX.left(), productYX.even());
        }
        return null;
    }

    private static Product matchProduct(ValueNode product, Signedness xSignedness, Signedness ySignedness, int fromBits, int toBits) {
        if (!(product instanceof MulNode mul)) {
            return null;
        }
        LaneSelection x = matchExtendedLaneSelection(mul.getX(), xSignedness, fromBits, toBits);
        LaneSelection y = matchExtendedLaneSelection(mul.getY(), ySignedness, fromBits, toBits);
        if (x == null || y == null || x.even() != y.even()) {
            return null;
        }
        return new Product(x.source(), y.source(), x.even());
    }

    private record LaneSelection(ValueNode source, boolean even) {
    }

    /**
     * Matches a sign or zero extension of the even lanes or odd lanes selected by a constant
     * permutation. The accepted graph represents one of these expressions:
     *
     * <pre>
     * extend(cut(permute(source, [0, 2, 4, 6, ...]), 0, resultLength)) -> even lanes of source
     * extend(cut(permute(source, [1, 3, 5, 7, ...]), 0, resultLength)) -> odd lanes of source
     * </pre>
     *
     * The source vector feeding the permutation has twice as many lanes as the extended result. For
     * a result with {@code N} lanes, the relevant mapping prefix has {@code N} entries:
     * {@code [0, 2, 4, ...]} for even lanes or {@code [1, 3, 5, ...]} for odd lanes. The
     * {@link SimdCutNode} keeps exactly that prefix, so any remaining mapping entries are ignored by
     * the extension.
     */
    private static LaneSelection matchExtendedLaneSelection(ValueNode node, Signedness signedness, int fromBits, int toBits) {
        if (signedness == Signedness.UNSIGNED ? !(node instanceof ZeroExtendNode) : !(node instanceof SignExtendNode)) {
            return null;
        }
        IntegerConvertNode<?> convert = (IntegerConvertNode<?>) node;
        if (convert.getInputBits() != fromBits || convert.getResultBits() != toBits) {
            return null;
        }

        int resultLength = ((SimdStamp) convert.stamp(NodeView.DEFAULT)).getVectorLength();
        ValueNode value = convert.getValue();
        if (!(value instanceof SimdCutNode cut && cut.getOffset() == 0 && cut.getLength() == resultLength)) {
            return null;
        }
        value = cut.getValue();
        if (value instanceof SimdPermuteNode permute) {
            int[] mapping = permute.getDestinationMapping();
            ValueNode source = permute.getValue();
            int sourceLength = ((SimdStamp) source.stamp(NodeView.DEFAULT)).getVectorLength();
            /*
             * The matched instruction consumes 2*N source lanes and produces N result lanes.
             */
            if (sourceLength != 2 * resultLength) {
                return null;
            }
            /*
             * The permute produces 2*N lanes, but only the first N mapping entries reach the
             * extension through the cut.
             */
            if (mapping.length != sourceLength) {
                return null;
            }
            if (mapping[0] != 0 && mapping[0] != 1) {
                return null;
            }
            boolean even = mapping[0] == 0;
            int firstLane = even ? 0 : 1;
            for (int i = 0; i < resultLength; i++) {
                if (mapping[i] != firstLane + 2 * i) {
                    return null;
                }
            }
            return new LaneSelection(source, even);
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind resultKind = builder.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        Value left = builder.operand(getX());
        Value right = builder.operand(getY());
        Value result = ((AMD64VectorArithmeticLIRGenerator) gen).emitVectorPairwiseMultiplyAdd(resultKind, opKind, left, right);
        builder.setResult(this, result);
    }
}
