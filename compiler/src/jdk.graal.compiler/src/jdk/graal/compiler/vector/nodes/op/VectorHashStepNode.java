/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.op;

import static jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.onlyNonConditionalUsage;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.vector.nodes.VectorAccess;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode.AccumulatorNode;
import jdk.graal.compiler.vector.nodes.producer.VectorHashInitNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphUtil;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.iterators.FilteredNodeIterable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;

/**
 * This is an operation implementing one step of a hash computation of the following form:
 *
 * <pre>
 * step = accumulator * multiplier + vectorValue;
 * </pre>
 *
 * It is meant to be used inside {@link FoldVectorNode} subgraphs, where it is treated as
 * associative and commutative. During simdification we can generate SIMD code for this pattern even
 * though the operation doesn't match the normal constraints for associative-commutative fold
 * operations.
 *
 * The multiplier must be a constant, it is stored in this node as an unboxed {@code long}.
 *
 * Overall, this node and {@link VectorHashInitNode} are used to vectorize a loop like:
 *
 * <pre>
 * int result = init;
 * for (int i = 0; i < array.length; i++) {
 *     result = result * 31 + array[i];
 * }
 * </pre>
 *
 * to the following form (assuming SIMD length 4):
 *
 * <pre>
 * hashes = <0, 0, 0, init>;
 * // vector loop
 * for (int i = 0; i < length - length % 4; i += 4) {
 *     hashes *= <31^4, 31^4, 31^4, 31^4>;
 *     hashes += array[i:i+3];
 * }
 * hashes *= <31^3, 31^2, 31^1, 31^0>;
 * hash = hashes[0] + hashes[1] + hashes[2] + hashes[3];
 * // scalar tail consumer
 * for ( ; i < length; i++) {
 *     hash = hash * 31 + array[i];
 * }
 * </pre>
 *
 * When looking at an example array a with length 16, an accumulator vector with length 4 and a
 * scalar multiplication constant x, the scalar calculation can be written like:
 *
 * <pre>
 * (init * x^16) + (a[0] * x^15) + (a[4] * x^11) + (a[8]  * x^7) + (a[12] * x^3)
 *               + (a[1] * x^14) + (a[5] * x^10) + (a[9]  * x^6) + (a[13] * x^2)
 *               + (a[2] * x^13) + (a[6] * x^9)  + (a[10] * x^5) + (a[14] * x^1)
 *               + (a[3] * x^12) + (a[7] * x^8)  + (a[11] * x^4) + (a[15] * x^0) = result
 * </pre>
 *
 * This calculation can be reshaped to look like:
 *
 * <pre>
 *   (0 * x^(4 * 4)    + a[0] * x^(4 * 3) + a[4] * x^(4 * 2) + a[8]  * x^(4 * 1) + a[12] * x^(4 * 0))  * x^3
 * + (0 * x^(4 * 4)    + a[1] * x^(4 * 3) + a[5] * x^(4 * 2) + a[9]  * x^(4 * 1) + a[13] * x^(4 * 0))  * x^2
 * + (0 * x^(4 * 4)    + a[2] * x^(4 * 3) + a[6] * x^(4 * 2) + a[10] * x^(4 * 1) + a[14] * x^(4 * 0))  * x^1
 * + (init * x^(4 * 4) + a[3] * x^(4 * 3) + a[7] * x^(4 * 2) + a[11] * x^(4 * 1) + a[15] * x^(4 * 0))  * x^0 = result
 * </pre>
 *
 * Here we can interpret each line as an element of the accumulator vector and each column
 * (separated by the addition) as a loop iteration which leads to the transformation of the loop in
 * the way demonstrated in the example above. The last multiplication lies outside the loop.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
//@formatter:on
public class VectorHashStepNode extends FoldVectorNode.BinaryMacroNode {

    public static final NodeClass<VectorHashStepNode> TYPE = NodeClass.create(VectorHashStepNode.class);

    private final long multiplier;
    private final Direction producerStride;
    private final ArithmeticOpTable.BinaryOp<?> secondaryOp;

    public VectorHashStepNode(ValueNode accumulatorValue, ValueNode vectorValue, long multiplier, Direction producerStride, ArithmeticOpTable.BinaryOp<?> secondaryOp) {
        super(TYPE, accumulatorValue.stamp(NodeView.DEFAULT), accumulatorValue, vectorValue);
        this.multiplier = multiplier;
        this.producerStride = producerStride;
        this.secondaryOp = secondaryOp;
        GraalError.guarantee(validSecondaryOp(secondaryOp, multiplier), "%s is not a valid secondary operation in hash step for multiplier %s!", secondaryOp, multiplier);
    }

    public long getMultiplier() {
        return multiplier;
    }

    @Override
    public ArithmeticOpTable.BinaryOp<?> getInnerBinaryOp() {
        return secondaryOp;
    }

    @Override
    public ValueNode expand(int operationLength, int simdLength) {
        if (simdLength == 1) {
            ValueNode result;
            if (operationLength == 1) {
                // This is inside the scalar (main or tail) consumer loop. Reconstruct the original
                // scalar accumulator * multiplier + vectorValue computation.
                ValueNode mul = BinaryArithmeticNode.mul(graph(), getX(), ConstantNode.forIntegerStamp(getX().stamp(NodeView.DEFAULT), multiplier, graph()), NodeView.DEFAULT);
                result = BinaryArithmeticNode.binaryIntegerOp(graph(), mul, getY(), NodeView.DEFAULT, secondaryOp);
            } else {
                // This is a horizontal combination of results from a hash of length > 1. Don't
                // include the multiplier, just combine the results.
                if (IntegerStamp.OPS.getSub().equals(secondaryOp)) {
                    // Combination of the accumulator elements of vectorized subtractions is done by
                    // addition (see VectorSubtractStepNode)
                    result = BinaryArithmeticNode.add(graph(), getX(), getY(), NodeView.DEFAULT);
                } else {
                    result = BinaryArithmeticNode.binaryIntegerOp(graph(), getX(), getY(), NodeView.DEFAULT, secondaryOp);
                }
            }
            return result;
        } else {
            /*-
             * In the case with SIMD length N, we expand a hash step to:
             *   step = acc * simdMultiplier + vectorValue
             *
             * where:
             *   simdMultiplier = <multiplier**N, ..., multiplier**N>
             */
            Stamp elementStamp = ((SimdStamp) getX().stamp(NodeView.DEFAULT)).getComponent(0);
            int multiplierBits = PrimitiveStamp.getBits(elementStamp);
            long currentPower = 1;
            for (int i = 0; i < simdLength; i++) {
                currentPower *= multiplier;
            }
            ValueNode mul;
            if (CodeUtil.isPowerOf2(currentPower) && CodeUtil.log2(currentPower) < multiplierBits) {
                mul = BinaryArithmeticNode.shl(graph(), getX(), ConstantNode.forInt(CodeUtil.log2(currentPower), graph()), NodeView.DEFAULT);
            } else {
                JavaConstant finalPowerConstant = JavaConstant.forPrimitiveInt(multiplierBits, currentPower);
                ValueNode simdMultiplier = graph().unique(SimdConstant.constantNodeForBroadcast(finalPowerConstant, simdLength));
                mul = BinaryArithmeticNode.mul(graph(), getX(), simdMultiplier, NodeView.DEFAULT);
            }
            ValueNode result = BinaryArithmeticNode.binaryIntegerOp(graph(), mul, getY(), NodeView.DEFAULT, secondaryOp);
            return result;
        }
    }

    /**
     * This method inserts a multiplication with powers extracted from the loop where
     * {@code powers = <multiplier**(N-1), ..., multiplier**1, multiplier**0>} into the given graph.
     *
     * @param graph graph this multiplication should be inserted into
     * @param currentVector input of the multiplication
     * @param simdLength current vector length in the given context
     * @return reference to the newly created node
     */
    @Override
    public ValueNode preExpand(StructuredGraph graph, ValueNode currentVector, int simdLength) {
        Stamp elementStamp = ((SimdStamp) currentVector.stamp(NodeView.DEFAULT)).getComponent(0);
        int multiplierBits = PrimitiveStamp.getBits(elementStamp);
        JavaConstant[] powers = new JavaConstant[simdLength];
        long currentPower = 1;
        /*
         * The multiplications in the loops below may overflow. This is OK: We are modeling a hash
         * computation with repeated multiplications that would overflow in the user program too.
         */
        if (producerStride == Direction.Up) {
            for (int i = simdLength - 1; i >= 0; i--) {
                powers[i] = JavaConstant.forPrimitiveInt(multiplierBits, currentPower);
                currentPower *= multiplier;
            }
        } else {
            for (int i = 0; i < simdLength; i++) {
                powers[i] = JavaConstant.forPrimitiveInt(multiplierBits, currentPower);
                currentPower *= multiplier;
            }
        }

        // the order of the values in powers is inverted if producerStride == Down.
        ValueNode powersConstantNode = graph.unique(SimdConstant.constantNodeForConstants(powers));
        return BinaryArithmeticNode.mul(graph, currentVector, powersConstantNode, NodeView.DEFAULT);
    }

    @Override
    public VectorNode initialVector(FoldVectorNode fold) {
        ValueNode initial = fold.getOriginalInitial();
        assert initial != null;
        return fold.graph().addOrUniqueWithInputs(new VectorHashInitNode(initial, producerStride));
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return this;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return stampY.unrestricted();
    }

    /**
     * Find a hash-shaped computation inside the fold's subgraph. The fold is expected to be fully
     * simplified, with maps extracted as far as possible, etc. If a hash computation is found, this
     * method replaces it with a corresponding {@link VectorHashStepNode} inside the fold's subgraph
     * and returns {@code true}. Otherwise, it does not modify the subgraph and returns
     * {@code false}.
     */
    public static boolean maybeTransformToHash(FoldVectorNode fold) {
        // Try to find an integer computation of the form
        // result = accumulator * multiplier + vectorParameter.
        AccumulatorNode accumulator = SubGraphUtil.getAccumulator(fold);

        // find multiplication constant
        MultiplicationDescriptor descriptor = MultiplicationDescriptor.find(accumulator);
        if (descriptor == MultiplicationDescriptor.NO_HASH) {
            return false;
        }
        Node lastPartOfMul = descriptor.lastPartOfMul;
        Node secondaryOp = descriptor.secondaryOp;
        long mulConst = descriptor.multiplier;

        // check if the secondary operation is compatible with the multiplier
        ValueNode vectorParameter;
        ArithmeticOpTable.BinaryOp<?> op;
        if (secondaryOp instanceof SubNode && ((SubNode) secondaryOp).stamp(NodeView.DEFAULT) instanceof IntegerStamp && ((SubNode) secondaryOp).getY().equals(lastPartOfMul)) {
            // special case if array[i] - (hash << const)
            mulConst *= -1;
            SubNode sub = (SubNode) secondaryOp;
            vectorParameter = vectorParameter(sub.getX()) != null ? sub.getX() : null;
            op = IntegerStamp.OPS.getAdd();
        } else if (secondaryOp == null || !validSecondaryOp(secondaryOp, mulConst)) {
            // we either did not find a secondary op or an invalid one
            return false;
        } else {
            // standard case
            // cast checked by validSecondaryOp
            BinaryArithmeticNode<?> secondaryBinaryOp = (BinaryArithmeticNode<?>) secondaryOp;
            vectorParameter = (vectorParameter(secondaryBinaryOp.getX()) != null ? secondaryBinaryOp.getX()
                            : vectorParameter(secondaryBinaryOp.getY()) != null ? secondaryBinaryOp.getY() : null);
            op = secondaryBinaryOp.getArithmeticOp();
        }

        if (vectorParameter == null) {
            return false;
        }

        Direction producerStride = findUniformProducerStrides(fold);
        if (producerStride == null) {
            return false;
        }

        // create hash step and kill all nodes involved bottom up
        VectorHashStepNode hashStep = fold.getOp().unique(new VectorHashStepNode(accumulator, vectorParameter, mulConst, producerStride, op));
        secondaryOp.replaceAtUsagesAndDelete(hashStep);
        GraphUtil.tryKillUnused(lastPartOfMul);

        return true;
    }

    private static final class MultiplicationDescriptor {
        public static final MultiplicationDescriptor NO_HASH = new MultiplicationDescriptor();

        public final Node lastPartOfMul;
        public final Node secondaryOp;
        public final long multiplier;

        private MultiplicationDescriptor() {
            // default values, only used for NO_HASH
            lastPartOfMul = null;
            secondaryOp = null;
            multiplier = 0;
        }

        private MultiplicationDescriptor(Node lastPartOfMul, Node secondaryOp, long multiplier) {
            this.lastPartOfMul = lastPartOfMul;
            this.secondaryOp = secondaryOp;
            this.multiplier = multiplier;
        }

        public static MultiplicationDescriptor find(AccumulatorNode accumulator) {
            // find candidate for multiplication in hash code
            FilteredNodeIterable<Node> nonConditionalUsages = accumulator.usages().filter(usage -> !(usage instanceof ConditionalNode));
            int nonConditionalUsageCount = nonConditionalUsages.count();
            BinaryNode mulCandidate = null;
            int candidateCnt = 0;
            for (Node n : nonConditionalUsages) {
                if (n instanceof LeftShiftNode || n instanceof MulNode) {
                    if (mulCandidate == null) {
                        mulCandidate = (BinaryNode) n;
                        candidateCnt++;
                    } else if (candidateCnt < 2) {
                        // this could still be a canonicalized multiplication
                        candidateCnt++;
                    } else {
                        // accumulator has more multiplicative usages than possible for a single
                        // canonicalized multiplication
                        return NO_HASH;
                    }
                }
            }
            if (mulCandidate == null) {
                // no candidate found
                return NO_HASH;
            }

            // find preliminary multiplication constant
            // in case mulCandidate is a left shift, mulConst will be adapted accordingly later on
            long mulConst;
            if (mulCandidate instanceof LeftShiftNode || mulCandidate.getX() == accumulator) {
                if (mulCandidate.getY().isJavaConstant() && mulCandidate.getY().stamp(NodeView.DEFAULT).isIntegerStamp()) {
                    mulConst = mulCandidate.getY().asJavaConstant().asLong();
                } else {
                    // other operand of multiplication or shift needs to be a constant
                    return NO_HASH;
                }
            } else {
                if (mulCandidate.getX().isJavaConstant() && mulCandidate.getX().stamp(NodeView.DEFAULT).isIntegerStamp()) {
                    mulConst = mulCandidate.getX().asJavaConstant().asLong();
                } else {
                    // other operand of multiplication needs to be a constant
                    return NO_HASH;
                }
            }

            Node secondaryOp = onlyNonConditionalUsage(mulCandidate);
            Node lastPartOfMul = mulCandidate;
            /**
             * left shift in combination with addition or subtraction can be a canonicalized
             * multiplication with a constant, see:
             * {@link MulNode#canonical(Stamp, ValueNode, long, NodeView)}
             */
            do {
                if (mulCandidate instanceof LeftShiftNode) {
                    mulConst = 1L << mulConst;
                    if (secondaryOp instanceof AddNode) {
                        AddNode add = (AddNode) secondaryOp;
                        ValueNode other = add.getX().equals(mulCandidate) ? add.getY() : add.getX();
                        if (other instanceof AccumulatorNode) {
                            /*-
                             *      Accumulator
                             *        /   \
                             *  LeftShift  |
                             *        \   /
                             *         Add
                             */
                            mulConst++;
                        } else if (other instanceof LeftShiftNode && ((LeftShiftNode) other).getX() instanceof AccumulatorNode && ((LeftShiftNode) other).getY().isConstant()) {
                            /*-
                             *      Accumulator
                             *         /    \
                             *  LeftShift  LeftShift
                             *         \    /
                             *          Add
                             */
                            mulConst += 1L << ((LeftShiftNode) other).getY().asJavaConstant().asLong();
                        } else {
                            break;
                        }
                    } else if (secondaryOp instanceof SubNode) {
                        SubNode sub = (SubNode) secondaryOp;
                        ValueNode other = sub.getX().equals(mulCandidate) ? sub.getY() : sub.getX();
                        if (other instanceof AccumulatorNode && sub.getY().equals(other)) {
                            /*-
                             *       Accumulator
                             *         /   \
                             *  LeftShift   |
                             *         \   /
                             *          Sub
                             */
                            mulConst--;
                        } else if (other instanceof LeftShiftNode && ((LeftShiftNode) other).getX() instanceof AccumulatorNode && ((LeftShiftNode) other).getY().isConstant()) {
                            LeftShiftNode shift = (LeftShiftNode) other;
                            /*-
                             *      Accumulator
                             *         /    \
                             *  LeftShift  LeftShift
                             *         \    /
                             *          Sub
                             */
                            if (sub.getX().equals(mulCandidate)) {
                                mulConst = mulConst - (1L << shift.getY().asJavaConstant().asLong());
                            } else {
                                mulConst = (1L << shift.getY().asJavaConstant().asLong()) - mulConst;
                            }
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                    nonConditionalUsageCount--;
                    lastPartOfMul = secondaryOp;
                    secondaryOp = onlyNonConditionalUsage(secondaryOp);
                }
            } while (false);

            if (secondaryOp instanceof NegateNode) {
                mulConst *= -1;
                lastPartOfMul = secondaryOp;
                secondaryOp = onlyNonConditionalUsage(secondaryOp);
            }

            if (nonConditionalUsageCount > 1) {
                /*
                 * even after creating a hash step, the accumulator will have more than 1
                 * non-conditional usage which makes this fold unvectorizable. Therefore, we do not
                 * elaborate the possibility of converting this operation to a hash step further.
                 */
                return NO_HASH;
            }

            return new MultiplicationDescriptor(lastPartOfMul, secondaryOp, mulConst);
        }
    }

    private static ValueNode vectorParameter(ValueNode value) {
        ValueNode node = value;
        while (node instanceof IntegerConvertNode) {
            node = ((IntegerConvertNode<?>) node).getValue();
        }
        if (node instanceof ParameterNode && ((ParameterNode) node).index() < SubGraphUtil.SCALAR_OFFSET) {
            return value;
        } else {
            return null;
        }
    }

    private static boolean validSecondaryOp(Node n, long multiplier) {
        if (n instanceof BinaryArithmeticNode<?> && ((ValueNode) n).stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
            return validSecondaryOp(((BinaryArithmeticNode<?>) n).getArithmeticOp(), multiplier);
        }
        return false;
    }

    private static boolean validSecondaryOp(ArithmeticOpTable.BinaryOp<?> op, long multiplier) {
        return IntegerStamp.OPS.getAdd().equals(op) || IntegerStamp.OPS.getSub().equals(op) ||
                        (CodeUtil.isPowerOf2(multiplier) && (IntegerStamp.OPS.getOr().equals(op) || IntegerStamp.OPS.getXor().equals(op)));
    }

    private static Direction findUniformProducerStrides(FoldVectorNode fold) {
        Direction direction = null;
        NodeFlood flood = new NodeFlood(fold.graph());

        flood.addAll(fold.getVectorInputs());
        for (Node node : flood) {
            if (node instanceof VectorNode) {
                if (node instanceof VectorAccess) {
                    int stride = ((VectorAccess) node).getElementStride();
                    assert stride != 0 : stride + " for fold " + node;
                    Direction strideDirection = (stride > 0 ? Direction.Up : Direction.Down);
                    if (direction == null) {
                        direction = strideDirection;
                    } else if (direction != strideDirection) {
                        // mixed strides, reject this computation
                        return null;
                    }
                }
                flood.addAll(node.inputs());
            }
        }

        return direction;
    }
}
