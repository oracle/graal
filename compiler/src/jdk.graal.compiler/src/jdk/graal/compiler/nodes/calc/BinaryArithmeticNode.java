/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.Arrays;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeValueMap;
import jdk.vm.ci.meta.Constant;

@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public abstract class BinaryArithmeticNode<OP> extends BinaryNode implements ArithmeticOperation, ArithmeticLIRLowerable, Canonicalizable.Binary<ValueNode> {

    @SuppressWarnings("rawtypes") public static final NodeClass<BinaryArithmeticNode> TYPE = NodeClass.create(BinaryArithmeticNode.class);

    protected BinaryArithmeticNode(NodeClass<? extends BinaryArithmeticNode<OP>> c, BinaryOp<OP> opForStampComputation, ValueNode x, ValueNode y) {
        super(c, opForStampComputation.foldStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT)), x, y);
    }

    protected BinaryArithmeticNode(NodeClass<? extends BinaryArithmeticNode<OP>> c, Stamp stamp, ValueNode x, ValueNode y) {
        super(c, stamp, x, y);
    }

    public static ArithmeticOpTable getArithmeticOpTable(ValueNode forValue) {
        return ArithmeticOpTable.forStamp(forValue.stamp(NodeView.DEFAULT));
    }

    protected abstract BinaryOp<OP> getOp(ArithmeticOpTable table);

    protected final BinaryOp<OP> getOp(ValueNode forX, ValueNode forY) {
        ArithmeticOpTable table = getArithmeticOpTable(forX);
        assert table.equals(getArithmeticOpTable(forY)) : Assertions.errorMessage("Invalid table ops", forX, table, forY, getArithmeticOpTable(forY));
        return getOp(table);
    }

    @Override
    public final BinaryOp<OP> getArithmeticOp() {
        return getOp(getX(), getY());
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode result = tryConstantFold(getOp(forX, forY), forX, forY, stamp(view), view);
        if (result != null) {
            return result;
        }
        if (forX instanceof ConditionalNode && forY.isConstant() && forX.hasExactlyOneUsage()) {
            ConditionalNode conditionalNode = (ConditionalNode) forX;
            BinaryOp<OP> arithmeticOp = getArithmeticOp();
            ConstantNode trueConstant = tryConstantFold(arithmeticOp, conditionalNode.trueValue(), forY, this.stamp(view), view);
            if (trueConstant != null) {
                ConstantNode falseConstant = tryConstantFold(arithmeticOp, conditionalNode.falseValue(), forY, this.stamp(view), view);
                if (falseConstant != null) {
                    // @formatter:off
                    /* The arithmetic is folded into a constant on both sides of the conditional.
                     * Example:
                     *            (cond ? -5 : 5) + 100
                     * canonicalizes to:
                     *            (cond ? 95 : 105)
                     */
                    // @formatter:on
                    return ConditionalNode.create(conditionalNode.condition, trueConstant,
                                    falseConstant, view);
                }
            }
        }
        return this;
    }

    @SuppressWarnings("unused")
    public static <OP> ConstantNode tryConstantFold(BinaryOp<OP> op, ValueNode forX, ValueNode forY, Stamp stamp, NodeView view) {
        if (forX.isConstant() && forY.isConstant()) {
            Constant ret = op.foldConstant(forX.asConstant(), forY.asConstant());
            if (ret != null) {
                return ConstantNode.forPrimitive(stamp, ret);
            }
        }
        return null;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        assert stampX.isCompatible(x.stamp(NodeView.DEFAULT)) : Assertions.errorMessageContext("this", this, "xStamp", x.stamp(NodeView.DEFAULT), "stampX", stampX);
        assert stampY.isCompatible(y.stamp(NodeView.DEFAULT)) : Assertions.errorMessageContext("this", this, "xStamp", x.stamp(NodeView.DEFAULT), "stampX", stampX);
        return getArithmeticOp().foldStamp(stampX, stampY);
    }

    public static ValueNode binaryIntegerOp(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view, BinaryOp<?> op) {
        return graph.addOrUniqueWithInputs(binaryIntegerOp(v1, v2, view, op));
    }

    public static ValueNode binaryIntegerOp(ValueNode v1, ValueNode v2, NodeView view, BinaryOp<?> op) {
        if (IntegerStamp.OPS.getAdd().equals(op)) {
            return add(v1, v2, view);
        } else if (IntegerStamp.OPS.getSub().equals(op)) {
            return sub(v1, v2, view);
        } else if (IntegerStamp.OPS.getMul().equals(op)) {
            return mul(v1, v2, view);
        } else if (IntegerStamp.OPS.getRem().equals(op)) {
            return rem(v1, v2, view);
        } else if (IntegerStamp.OPS.getAnd().equals(op)) {
            return and(v1, v2, view);
        } else if (IntegerStamp.OPS.getOr().equals(op)) {
            return or(v1, v2, view);
        } else if (IntegerStamp.OPS.getXor().equals(op)) {
            return xor(v1, v2, view);
        } else if (IntegerStamp.OPS.getShl().equals(op)) {
            return shl(v1, v2, view);
        } else if (IntegerStamp.OPS.getUShr().equals(op)) {
            return ushr(v1, v2, view);
        } else if (IntegerStamp.OPS.getShr().equals(op)) {
            return shr(v1, v2, view);
        } else if (IntegerStamp.OPS.getMax().equals(op)) {
            return max(v1, v2, view);
        } else if (IntegerStamp.OPS.getMin().equals(op)) {
            return min(v1, v2, view);
        } else if (IntegerStamp.OPS.getUMax().equals(op)) {
            return umax(v1, v2, view);
        } else if (IntegerStamp.OPS.getUMin().equals(op)) {
            return umin(v1, v2, view);
        } else if (Arrays.asList(IntegerStamp.OPS.getBinaryOps()).contains(op)) {
            GraalError.unimplemented(String.format("creating %s via BinaryArithmeticNode#binaryIntegerOp is not implemented yet", op));
        } else {
            GraalError.shouldNotReachHere(String.format("%s is not a binary operation!", op));
        }
        return null;
    }

    public static ValueNode binaryFloatOp(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view, BinaryOp<?> op) {
        return graph.addOrUniqueWithInputs(binaryFloatOp(v1, v2, view, op));
    }

    public static ValueNode binaryFloatOp(ValueNode v1, ValueNode v2, NodeView view, BinaryOp<?> op) {
        if (FloatStamp.OPS.getAdd().equals(op)) {
            return add(v1, v2, view);
        } else if (FloatStamp.OPS.getSub().equals(op)) {
            return sub(v1, v2, view);
        } else if (FloatStamp.OPS.getMul().equals(op)) {
            return mul(v1, v2, view);
        } else if (FloatStamp.OPS.getDiv().equals(op)) {
            return FloatDivNode.create(v1, v2, view);
        } else if (FloatStamp.OPS.getAnd().equals(op)) {
            return and(v1, v2, view);
        } else if (FloatStamp.OPS.getOr().equals(op)) {
            return or(v1, v2, view);
        } else if (FloatStamp.OPS.getXor().equals(op)) {
            return xor(v1, v2, view);
        } else if (FloatStamp.OPS.getMax().equals(op)) {
            return max(v1, v2, view);
        } else if (FloatStamp.OPS.getMin().equals(op)) {
            return min(v1, v2, view);
        } else if (Arrays.asList(FloatStamp.OPS.getBinaryOps()).contains(op)) {
            GraalError.unimplemented(String.format("creating %s via BinaryArithmeticNode#binaryFloatOp is not implemented yet", op));
        } else {
            GraalError.shouldNotReachHere(String.format("%s is not a binary operation!", op));
        }
        return null;
    }

    public static ValueNode add(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(AddNode.create(v1, v2, view));
    }

    public static ValueNode add(ValueNode v1, ValueNode v2, NodeView view) {
        return AddNode.create(v1, v2, view);
    }

    public static ValueNode add(ValueNode v1, ValueNode v2) {
        return add(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode sub(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(SubNode.create(v1, v2, view));
    }

    public static ValueNode sub(ValueNode v1, ValueNode v2, NodeView view) {
        return SubNode.create(v1, v2, view);
    }

    public static ValueNode sub(ValueNode v1, ValueNode v2) {
        return sub(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode mul(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(MulNode.create(v1, v2, view));
    }

    public static ValueNode mul(ValueNode v1, ValueNode v2, NodeView view) {
        return MulNode.create(v1, v2, view);
    }

    public static ValueNode mul(ValueNode v1, ValueNode v2) {
        return mul(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode rem(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(RemNode.create(v1, v2, view));
    }

    public static ValueNode rem(ValueNode v1, ValueNode v2, NodeView view) {
        return RemNode.create(v1, v2, view);
    }

    public static ValueNode rem(ValueNode v1, ValueNode v2) {
        return rem(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode and(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(AndNode.create(v1, v2, view));
    }

    public static ValueNode and(ValueNode v1, ValueNode v2, NodeView view) {
        return AndNode.create(v1, v2, view);
    }

    public static ValueNode and(ValueNode v1, ValueNode v2) {
        return and(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode or(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(OrNode.create(v1, v2, view));
    }

    public static ValueNode or(ValueNode v1, ValueNode v2, NodeView view) {
        return OrNode.create(v1, v2, view);
    }

    public static ValueNode or(ValueNode v1, ValueNode v2) {
        return or(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode xor(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(XorNode.create(v1, v2, view));
    }

    public static ValueNode xor(ValueNode v1, ValueNode v2, NodeView view) {
        return XorNode.create(v1, v2, view);
    }

    public static ValueNode xor(ValueNode v1, ValueNode v2) {
        return xor(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode max(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(MaxNode.create(v1, v2, view));
    }

    public static ValueNode max(ValueNode v1, ValueNode v2, NodeView view) {
        return MaxNode.create(v1, v2, view);
    }

    public static ValueNode max(ValueNode v1, ValueNode v2) {
        return max(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode min(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(MinNode.create(v1, v2, view));
    }

    public static ValueNode min(ValueNode v1, ValueNode v2, NodeView view) {
        return MinNode.create(v1, v2, view);
    }

    public static ValueNode min(ValueNode v1, ValueNode v2) {
        return min(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode umax(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(UnsignedMaxNode.create(v1, v2, view));
    }

    public static ValueNode umax(ValueNode v1, ValueNode v2, NodeView view) {
        return UnsignedMaxNode.create(v1, v2, view);
    }

    public static ValueNode umax(ValueNode v1, ValueNode v2) {
        return umax(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode umin(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(UnsignedMinNode.create(v1, v2, view));
    }

    public static ValueNode umin(ValueNode v1, ValueNode v2, NodeView view) {
        return UnsignedMinNode.create(v1, v2, view);
    }

    public static ValueNode umin(ValueNode v1, ValueNode v2) {
        return umin(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode shl(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(LeftShiftNode.create(v1, v2, view));
    }

    public static ValueNode shl(ValueNode v1, ValueNode v2, NodeView view) {
        return LeftShiftNode.create(v1, v2, view);
    }

    public static ValueNode shl(ValueNode v1, ValueNode v2) {
        return shl(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode shr(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(RightShiftNode.create(v1, v2, view));
    }

    public static ValueNode shr(ValueNode v1, ValueNode v2, NodeView view) {
        return RightShiftNode.create(v1, v2, view);
    }

    public static ValueNode shr(ValueNode v1, ValueNode v2) {
        return shr(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode ushr(StructuredGraph graph, ValueNode v1, ValueNode v2, NodeView view) {
        return graph.addOrUniqueWithInputs(UnsignedRightShiftNode.create(v1, v2, view));
    }

    public static ValueNode ushr(ValueNode v1, ValueNode v2, NodeView view) {
        return UnsignedRightShiftNode.create(v1, v2, view);
    }

    public static ValueNode ushr(ValueNode v1, ValueNode v2) {
        return ushr(v1, v2, NodeView.DEFAULT);
    }

    public static ValueNode branchlessMin(ValueNode v1, ValueNode v2, NodeView view) {
        if (v1.isDefaultConstant() && !v2.isDefaultConstant()) {
            return branchlessMin(v2, v1, view);
        }
        int bits = ((IntegerStamp) v1.stamp(view)).getBits();
        assert ((IntegerStamp) v2.stamp(view)).getBits() == bits : bits + " and v2 " + v2;
        ValueNode t1 = sub(v1, v2, view);
        ValueNode t2 = RightShiftNode.create(t1, bits - 1, view);
        ValueNode t3 = AndNode.create(t1, t2, view);
        return add(v2, t3, view);
    }

    public static ValueNode branchlessMax(ValueNode v1, ValueNode v2, NodeView view) {
        if (v1.isDefaultConstant() && !v2.isDefaultConstant()) {
            return branchlessMax(v2, v1, view);
        }
        int bits = ((IntegerStamp) v1.stamp(view)).getBits();
        assert ((IntegerStamp) v2.stamp(view)).getBits() == bits : bits + " and v2 " + v2;
        if (v2.isDefaultConstant()) {
            // prefer a & ~(a>>31) to a - (a & (a>>31))
            return AndNode.create(v1, NotNode.create(RightShiftNode.create(v1, bits - 1, view)), view);
        } else {
            ValueNode t1 = sub(v1, v2, view);
            ValueNode t2 = RightShiftNode.create(t1, bits - 1, view);
            ValueNode t3 = AndNode.create(t1, t2, view);
            return sub(v1, t3, view);
        }
    }

    private enum ReassociateMatch {
        x,
        y;

        public ValueNode getValue(BinaryNode binary) {
            switch (this) {
                case x:
                    return binary.getX();
                case y:
                    return binary.getY();
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(this); // ExcludeFromJacocoGeneratedReport
            }
        }

        public ValueNode getOtherValue(BinaryNode binary) {
            switch (this) {
                case x:
                    return binary.getY();
                case y:
                    return binary.getX();
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(this); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    private static ReassociateMatch findReassociate(BinaryNode binary, NodePredicate criterion) {
        boolean resultX = criterion.apply(binary.getX());
        boolean resultY = criterion.apply(binary.getY());
        if (resultX && !resultY) {
            return ReassociateMatch.x;
        }
        if (!resultX && resultY) {
            return ReassociateMatch.y;
        }
        return null;
    }

    private static ReassociateMatch findReassociate(BinaryArithmeticNode<?> parent, ValueNode child, NodePredicate criterion) {
        if (!isReassociative(parent, child)) {
            return null;
        }
        // "child" should be single used to "parent", or it might be not worth for the
        // re-association.
        if (child.hasExactlyOneUsage() && child.usages().first().equals(parent)) {
            return findReassociate((BinaryNode) child, criterion);
        }
        return null;
    }

    private static boolean isReassociative(BinaryArithmeticNode<?> parent, ValueNode child) {
        if (!parent.mayReassociate()) {
            return false;
        }
        if (isNonExactAddOrSub(parent)) {
            return isNonExactAddOrSub(child);
        }
        return child.getClass() == parent.getClass();
    }

    /**
     * Determines whether this operation may be reassociated in the sense of
     * {@link #reassociateUnmatchedValues} and {@link #reassociateMatchedValues}. These methods can
     * perform transformations like {@code (a * 2) * b => (a * b) * 2}. In general, these
     * transformations require the binary operation to be both {@linkplain BinaryOp#isAssociative()
     * associative} to allow shifting of parentheses and {@linkplain BinaryOp#isCommutative()
     * commutative} to allow changing the order of the operands.
     * <p/>
     * As a special case, subtraction on integers allows certain similar transformations, especially
     * in expressions where it is mixed with addition. For example,
     * {@link #reassociateUnmatchedValues} can transform {@code x + (C - y)  ->  (x - y) + C}, and
     * {@link SubNode#canonical(CanonicalizerTool, ValueNode, ValueNode)} can transform
     * {@code a - (a + b) -> -b}. Therefore this method returns {@code true} for integer
     * subtraction. Users of this method must still check if the operation in question is
     * subtraction and ensure that they only reassociate subtractions in sound ways. Floating-point
     * subtraction does not permit such mathematically sound transformations due to rounding errors.
     */
    public boolean mayReassociate() {
        return mayReassociate(getArithmeticOp(), stamp(NodeView.DEFAULT));
    }

    /**
     * Determines whether the {@code op} may be reassociated in the sense of
     * {@link #reassociateUnmatchedValues} and {@link #reassociateMatchedValues}.
     *
     * @see #mayReassociate()
     */
    public static boolean mayReassociate(BinaryOp<?> op, Stamp stamp) {
        return (op.isAssociative() && op.isCommutative()) || (stamp.isIntegerStamp() && op.equals(((ArithmeticStamp) stamp).getOps().getSub()));
    }

    /**
     * Tries to push down values which satisfy the criterion. This is an assistant function for
     * {@linkplain BinaryArithmeticNode#reassociateMatchedValues}. For example with a constantness
     * criterion: {@code (a * 2) * b => (a * b) * 2}
     *
     * <p>
     * This method accepts only {@linkplain #mayReassociate() operations that allow reassociation}
     * such as +, -, *, &amp;, |, ^, min, and max.
     */
    public static ValueNode reassociateUnmatchedValues(BinaryArithmeticNode<?> node, NodePredicate criterion, NodeView view) {
        ValueNode forX = node.getX();
        ValueNode forY = node.getY();
        BinaryOp<?> op = node.getOp(forX, forY);
        GraalError.guarantee(node.mayReassociate(), "%s: binary op %s does not satisfy precondition of reassociateUnmatchedValues", node, op);

        // No need to re-associate if one of the operands has matched the criterion.
        if (criterion.apply(forX) || criterion.apply(forY)) {
            return node;
        }

        // Find the operand that could be re-associated with its parent node.
        ReassociateMatch match = findReassociate(node, forX, criterion);
        BinaryNode matchBinary = null;
        ValueNode otherValue1 = null;
        if (match != null) {
            matchBinary = (BinaryNode) forX;
            otherValue1 = forY;
        } else {
            match = findReassociate(node, forY, criterion);
            if (match != null) {
                matchBinary = (BinaryNode) forY;
                otherValue1 = forX;
            }
        }
        if (match == null) {
            return node;
        }

        assert matchBinary != null;
        assert otherValue1 != null;
        ValueNode matchValue = match.getValue(matchBinary);
        ValueNode otherValue2 = match.getOtherValue(matchBinary);

        if (isNonExactAddOrSub(node)) {
            //@formatter:off
            /**
             * Re-association for the following patterns:
             *
             * x + (y + C)  ->  (x + y) + C
             * x + (y - C)  ->  (x + y) - C
             * x + (C - y)  ->  (x - y) + C
             *
             * x - (C - y)  ->  (x + y) - C
             * x - (y - C)  ->  (x - y) + C
             * x - (C + y)  ->  (x - y) - C
             *
             * (C - x) - y  ->  C - (x + y)
             * (x - C) - y  ->  (x - y) - C
             * (C + x) - y  ->  (x - y) + C
             */
            //@formatter:on
            boolean addSub = isNonExactAdd(node) && isNonExactSub(matchBinary);
            boolean subAdd = isNonExactSub(node) && isNonExactAdd(matchBinary);
            boolean subSub = isNonExactSub(node) && isNonExactSub(matchBinary);
            boolean sub = false;
            boolean invertSub = false;
            if (addSub) {
                sub = match == ReassociateMatch.y;
            } else if (subAdd) {
                sub = matchBinary == forY;
            } else if (subSub) {
                sub = (matchBinary == forX && match == ReassociateMatch.y) || (matchBinary == forY && match == ReassociateMatch.x);
                invertSub = matchBinary == forX && match == ReassociateMatch.x;
            }

            // For patterns like "(x - C) - y" and "(C + x) - y", swap the operands of association.
            if (node instanceof SubNode && matchBinary == forX) {
                ValueNode temp = otherValue1;
                otherValue1 = otherValue2;
                otherValue2 = temp;
            }

            ValueNode associated;
            if (subAdd || (addSub && match == ReassociateMatch.x) || (subSub && match == ReassociateMatch.y)) {
                associated = BinaryArithmeticNode.sub(otherValue1, otherValue2, view);
            } else {
                associated = BinaryArithmeticNode.add(otherValue1, otherValue2, view);
            }

            if (invertSub) {
                return BinaryArithmeticNode.sub(matchValue, associated, view);
            } else if (sub) {
                return BinaryArithmeticNode.sub(associated, matchValue, view);
            } else {
                return BinaryArithmeticNode.add(associated, matchValue, view);
            }
        } else if (isNonExactMul(node)) {
            // Re-association from "x * (y * C)" to "(x * y) * C"
            return BinaryArithmeticNode.mul(matchValue, BinaryArithmeticNode.mul(otherValue1, otherValue2, view), view);
        } else if (node instanceof AndNode) {
            // Re-association from "x & (y & C)" to "(x & y) & C"
            return AndNode.create(matchValue, AndNode.create(otherValue1, otherValue2, view), view);
        } else if (node instanceof OrNode) {
            // Re-association from "x | (y | C)" to "(x | y) | C"
            return OrNode.create(matchValue, OrNode.create(otherValue1, otherValue2, view), view);
        } else if (node instanceof XorNode) {
            // Re-association from "x ^ (y ^ C)" to "(x ^ y) ^ C"
            return XorNode.create(matchValue, XorNode.create(otherValue1, otherValue2, view), view);
        } else if (node instanceof MinNode) {
            // Re-association from "Math.min(x, Math.min(y, C))" to "Math.min(Math.min(x, y), C)"
            return MinNode.create(matchValue, MinNode.create(otherValue1, otherValue2, view), view);
        } else if (node instanceof MaxNode) {
            // Re-association from "Math.max(x, Math.max(y, C))" to "Math.max(Math.max(x, y), C)"
            return MaxNode.create(matchValue, MaxNode.create(otherValue1, otherValue2, view), view);
        } else {
            throw GraalError.shouldNotReachHere("unhandled node in reassociation with constants: " + node); // ExcludeFromJacocoGeneratedReport
        }
    }

    //@formatter:off
    /*
     * In reassociate, complexity comes from the handling of IntegerSub (non commutative) which can
     * be mixed with IntegerAdd. It first tries to find m1, m2 which match the criterion :
     * (a o m2) o m1
     * (m2 o a) o m1
     * m1 o (a o m2)
     * m1 o (m2 o a)
     * It then produces 4 boolean for the -/+ cases:
     * invertA : should the final expression be like *-a (rather than a+*)
     * aSub : should the final expression be like a-* (rather than a+*)
     * invertM1 : should the final expression contain -m1
     * invertM2 : should the final expression contain -m2
     *
     */
    //@formatter:on
    /**
     * Tries to re-associate values which satisfy the criterion. For example with a constantness
     * criterion: {@code (a + 2) + 1 => a + (1 + 2)}
     * <p>
     * This method accepts only {@linkplain #mayReassociate() operations that allow reassociation}
     * such as +, -, *, &amp;, |, ^, min, and max.
     *
     * @param forY
     * @param forX
     */
    public static ValueNode reassociateMatchedValues(BinaryArithmeticNode<?> node, NodePredicate criterion, ValueNode forX, ValueNode forY, NodeView view) {
        BinaryOp<?> op = node.getOp(forX, forY);
        GraalError.guarantee(node.mayReassociate(), "%s: binary op %s does not satisfy precondition of reassociateMatchedValues", node, op);
        ReassociateMatch match1 = findReassociate(node, criterion);
        if (match1 == null) {
            return node;
        }
        if (isExactMathOperation(node)) {
            return node;
        }
        if (node instanceof GuardedNode && ((GuardedNode) node).getGuard() != null) {
            // cannot re-associate guarded nodes
            return node;
        }
        ValueNode otherValue = match1.getOtherValue(node);
        boolean addSub = false;
        boolean subAdd = false;
        if (otherValue.getClass() != node.getClass()) {
            if (isNonExactAdd(node) && isNonExactSub(otherValue)) {
                addSub = true;
            } else if (isNonExactSub(node) && isNonExactAdd(otherValue)) {
                subAdd = true;
            } else {
                return node;
            }
        }
        BinaryNode other = (BinaryNode) otherValue;
        ReassociateMatch match2 = findReassociate(other, criterion);
        if (match2 == null) {
            return node;
        }
        if (isExactMathOperation(other)) {
            return node;
        }
        boolean invertA = false;
        boolean aSub = false;
        boolean invertM1 = false;
        boolean invertM2 = false;
        if (addSub) {
            invertM2 = match2 == ReassociateMatch.y;
            invertA = !invertM2;
        } else if (subAdd) {
            invertA = invertM2 = match1 == ReassociateMatch.x;
            invertM1 = !invertM2;
        } else if (isNonExactSub(node) && isNonExactSub(other)) {
            invertA = match1 == ReassociateMatch.x ^ match2 == ReassociateMatch.x;
            aSub = match1 == ReassociateMatch.y && match2 == ReassociateMatch.y;
            invertM1 = match1 == ReassociateMatch.y && match2 == ReassociateMatch.x;
            invertM2 = match1 == ReassociateMatch.x && match2 == ReassociateMatch.x;
        }
        assert !(invertM1 && invertM2) && !(invertA && aSub) : Assertions.errorMessageContext("node", node, "invertM1", invertM1, "invertM2", invertM2, "invertA", invertA, "aSub", aSub);
        ValueNode m1 = match1.getValue(node);
        ValueNode m2 = match2.getValue(other);
        ValueNode a = match2.getOtherValue(other);
        if (isNonExactAddOrSub(node)) {
            ValueNode associated;
            if (invertM1) {
                associated = BinaryArithmeticNode.sub(m2, m1, view);
            } else if (invertM2) {
                associated = BinaryArithmeticNode.sub(m1, m2, view);
            } else {
                associated = BinaryArithmeticNode.add(m1, m2, view);
            }
            if (invertA) {
                return BinaryArithmeticNode.sub(associated, a, view);
            }
            if (aSub) {
                return BinaryArithmeticNode.sub(a, associated, view);
            }
            return BinaryArithmeticNode.add(a, associated, view);
        } else if (isNonExactMul(node)) {
            return BinaryArithmeticNode.mul(a, mul(m1, m2, view), view);
        } else if (node instanceof AndNode) {
            return AndNode.create(a, AndNode.create(m1, m2, view), view);
        } else if (node instanceof OrNode) {
            return OrNode.create(a, OrNode.create(m1, m2, view), view);
        } else if (node instanceof XorNode) {
            return XorNode.create(a, XorNode.create(m1, m2, view), view);
        } else if (node instanceof MaxNode) {
            return MaxNode.create(a, MaxNode.create(m1, m2, view), view);
        } else if (node instanceof MinNode) {
            return MinNode.create(a, MinNode.create(m1, m2, view), view);
        } else {
            throw GraalError.shouldNotReachHere("unhandled node in reassociation with matched values: " + node); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static boolean isNonExactMul(Node n) {
        if (n instanceof MulNode) {
            return !((MulNode) n).isExact();
        }
        return false;
    }

    private static boolean isNonExactAdd(Node n) {
        if (n instanceof AddNode) {
            return !((AddNode) n).isExact();
        }
        return false;
    }

    private static boolean isNonExactSub(Node n) {
        if (n instanceof SubNode) {
            return !((SubNode) n).isExact();
        }
        return false;
    }

    private static boolean isNonExactAddOrSub(Node n) {
        return isNonExactAdd(n) || isNonExactSub(n);
    }

    private static boolean isExactMathOperation(Node n) {
        if (n instanceof AddNode) {
            return ((AddNode) n).isExact();
        }
        if (n instanceof SubNode) {
            return ((SubNode) n).isExact();
        }
        if (n instanceof MulNode) {
            return ((MulNode) n).isExact();
        }
        return false;
    }

    /**
     * Ensure a canonical ordering of inputs for commutative nodes to improve GVN results. Order the
     * inputs by increasing {@link Node#id} and call {@link Graph#findDuplicate(Node)} on the node
     * if it's currently in a graph. It's assumed that if there was a constant on the left it's been
     * moved to the right by other code and that ordering is left alone.
     *
     * @return the original node or another node with the same input ordering
     */
    @SuppressWarnings("deprecation")
    public BinaryNode maybeCommuteInputs() {
        assert this instanceof BinaryCommutative : Assertions.errorMessageContext("this", this);
        if (!y.isConstant() && (x.isConstant() || x.getId() > y.getId())) {
            ValueNode tmp = x;
            x = y;
            y = tmp;
            if (graph() != null) {
                // See if this node already exists
                BinaryNode duplicate = graph().findDuplicate(this);
                if (duplicate != null) {
                    return duplicate;
                }
            }
        }
        return this;
    }

    /**
     * Determines if it would be better to swap the inputs in order to produce better assembly code.
     * First we try to pick a value which is dead after this use. If both values are dead at this
     * use then we try pick an induction variable phi to encourage the phi to live in a single
     * register.
     *
     * @param nodeValueMap
     * @return true if inputs should be swapped, false otherwise
     */
    protected boolean shouldSwapInputs(NodeValueMap nodeValueMap) {
        final boolean xHasOtherUsages = getX().hasUsagesOtherThan(this, nodeValueMap);
        final boolean yHasOtherUsages = getY().hasUsagesOtherThan(this, nodeValueMap);

        if (!getY().isConstant() && !yHasOtherUsages) {
            if (xHasOtherUsages == yHasOtherUsages) {
                return getY() instanceof ValuePhiNode && getY().inputs().contains(this);
            } else {
                return true;
            }
        }
        return false;
    }

}
