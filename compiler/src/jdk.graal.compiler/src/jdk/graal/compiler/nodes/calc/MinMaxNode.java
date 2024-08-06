/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited. All rights reserved.
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

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.NumUtil.Signedness;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "MinMax")
public abstract class MinMaxNode<OP> extends BinaryArithmeticNode<OP> implements NarrowableArithmeticNode, Canonicalizable.BinaryCommutative<ValueNode> {

    @SuppressWarnings("rawtypes") public static final NodeClass<MinMaxNode> TYPE = NodeClass.create(MinMaxNode.class);

    protected MinMaxNode(NodeClass<? extends BinaryArithmeticNode<OP>> c, BinaryOp<OP> opForStampComputation, ValueNode x, ValueNode y) {
        super(c, opForStampComputation, x, y);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        NodeView view = NodeView.from(tool);
        if (forX.isConstant()) {
            ValueNode result = tryCanonicalizeWithConstantInput(forX, forY);
            if (result != this) {
                return result;
            }
        } else if (forY.isConstant()) {
            ValueNode result = tryCanonicalizeWithConstantInput(forY, forX);
            if (result != this) {
                return result;
            }
        }
        return reassociateMatchedValues(this, isConstantPredicate(), forX, forY, view);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        Value op1 = nodeValueMap.operand(getX());
        assert op1 != null : getX() + ", this=" + this;
        Value op2 = nodeValueMap.operand(getY());
        if (shouldSwapInputs(nodeValueMap)) {
            Value tmp = op1;
            op1 = op2;
            op2 = tmp;
        }
        if (this instanceof MaxNode) {
            nodeValueMap.setResult(this, gen.emitMathMax(op1, op2));
        } else if (this instanceof MinNode) {
            nodeValueMap.setResult(this, gen.emitMathMin(op1, op2));
        } else if (this instanceof UnsignedMaxNode) {
            nodeValueMap.setResult(this, gen.emitMathUnsignedMax(op1, op2));
        } else if (this instanceof UnsignedMinNode) {
            nodeValueMap.setResult(this, gen.emitMathUnsignedMin(op1, op2));
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(this); // ExcludeFromJacocoGeneratedReport
        }
    }

    private ValueNode tryCanonicalizeWithConstantInput(ValueNode constantValue, ValueNode otherValue) {
        if (constantValue.isJavaConstant() && constantValue.asJavaConstant().getJavaKind().isNumericFloat()) {
            JavaConstant constant = constantValue.asJavaConstant();
            JavaKind kind = constant.getJavaKind();
            assert kind == JavaKind.Float || kind == JavaKind.Double : Assertions.errorMessage(constantValue, otherValue, constant, kind);
            if ((kind == JavaKind.Float && Float.isNaN(constant.asFloat())) || (kind == JavaKind.Double && Double.isNaN(constant.asDouble()))) {
                // If either value is NaN, then the result is NaN.
                return constantValue;
            } else if (this instanceof MaxNode) {
                if ((kind == JavaKind.Float && constant.asFloat() == Float.NEGATIVE_INFINITY) || (kind == JavaKind.Double && constant.asDouble() == Double.NEGATIVE_INFINITY)) {
                    // Math.max/max(-Infinity, other) == other.
                    return otherValue;
                }
            } else if (this instanceof MinNode) {
                if ((kind == JavaKind.Float && constant.asFloat() == Float.POSITIVE_INFINITY) || (kind == JavaKind.Double && constant.asDouble() == Double.POSITIVE_INFINITY)) {
                    // Math.min/max(Infinity, other) == other.
                    return otherValue;
                }
            }
        }
        return this;
    }

    protected boolean isNarrowable(int resultBits, Signedness signedness) {
        if (signedness == Signedness.SIGNED) {
            IntegerStamp xStamp = (IntegerStamp) getX().stamp(NodeView.DEFAULT);
            IntegerStamp yStamp = (IntegerStamp) getY().stamp(NodeView.DEFAULT);
            long lower = Math.min(xStamp.lowerBound(), yStamp.lowerBound());
            long upper = Math.max(xStamp.upperBound(), yStamp.upperBound());
            return NumUtil.minValue(resultBits) <= lower && upper <= NumUtil.maxValue(resultBits);
        } else if (signedness == Signedness.UNSIGNED) {
            IntegerStamp xStamp = (IntegerStamp) getX().stamp(NodeView.DEFAULT);
            IntegerStamp yStamp = (IntegerStamp) getY().stamp(NodeView.DEFAULT);
            long upper = NumUtil.maxUnsigned(xStamp.unsignedUpperBound(), yStamp.unsignedUpperBound());
            return Long.compareUnsigned(upper, NumUtil.maxValueUnsigned(resultBits)) <= 0;
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(signedness); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Tries to build a {@link MinMaxNode} representation of the given conditional. Returns
     * {@code null} if no simple equivalent form exists. The returned node is not necessarily a
     * {@link MinMaxNode}, it may be constant folded or an {@link IntegerConvertNode} applied to a
     * {@link MinMaxNode}. Nodes built by this method are not added to the graph.
     */
    public static ValueNode fromConditional(ConditionalNode conditional) {
        return fromConditional(conditional.condition(), conditional.trueValue(), conditional.falseValue(), NodeView.DEFAULT);
    }

    /**
     * @see #fromConditional(ConditionalNode)
     */
    public static ValueNode fromConditional(LogicNode condition, ValueNode trueValue, ValueNode falseValue, NodeView view) {
        if (!trueValue.stamp(view).isIntegerStamp()) {
            return null;
        }
        if (!(condition instanceof IntegerLessThanNode || condition instanceof IntegerBelowNode)) {
            return null;
        }
        Signedness signedness = condition instanceof IntegerBelowNode ? Signedness.UNSIGNED : Signedness.SIGNED;
        CompareNode compare = (CompareNode) condition;
        ValueNode x = compare.getX();
        ValueNode y = compare.getY();

        /*
         * Look for the pattern (x < y ? x : y) or (x < y ? y : x).
         *
         * Handle cases like Math.min(x, 42) which is represented as (x < 43 ? x : 42),
         * canonicalized from (x <= 42 ? x : 42). That is, constants in the comparison and the
         * result may have different values.
         *
         * The x value used in the comparison might be narrower than its use in the result, e.g., on
         * bytes we might have (x < (byte) y ? SignExtend(x) : (int) y), so we must look through a
         * possible extension. This can happen if y is a constant.
         *
         * We may have (ZeroExtend(x) < (int) y ? (byte) x : (byte) y) if narrow compares are not
         * supported by the target.
         */
        ValueNode minMax = null;

        /* First see if y matches the conditional's true or false value. */
        boolean flipped;
        ValueNode yValue = null;
        if (equalOrOffBy1(signedness, y, falseValue)) {
            flipped = false;
            yValue = falseValue;
        } else if (equalOrOffBy1(signedness, y, trueValue)) {
            flipped = true;
            yValue = trueValue;
        } else {
            return null;
        }

        /* Now see if x matches the other value, possibly with an extension. */
        IntegerConvertNode<?> extension = null;
        ValueNode otherValue = flipped ? falseValue : trueValue;
        if (x == otherValue) {
            // Match.
        } else if ((otherValue instanceof SignExtendNode || otherValue instanceof ZeroExtendNode) && ((IntegerConvertNode<?>) otherValue).getValue() == x) {
            // Match with a narrow compare but producing a wide result. Produce a narrow min/max and
            // extend later.
            extension = (IntegerConvertNode<?>) otherValue;
        } else if ((x instanceof SignExtendNode || x instanceof ZeroExtendNode) && ((IntegerConvertNode<?>) x).getValue() == otherValue) {
            // Match with a wide compare but producing a narrow result. Produce a narrow min/max.
            x = otherValue;
            y = yValue;
        } else {
            return null;
        }

        /*
         * If we got here, this is definitely a min/max. See if we need to adjust y: Due to the
         * special cases explained above, we might have the case where y is a constant that has the
         * correct kind but is off by 1, while the value we matched has the correct numeric value
         * but the wrong kind.
         */
        if (y.isJavaConstant() && yValue.isJavaConstant() && y.asJavaConstant().asLong() != yValue.asJavaConstant().asLong()) {
            JavaKind kind = y.asJavaConstant().getJavaKind();
            long value = yValue.asJavaConstant().asLong();
            JavaConstant newConstant = JavaConstant.forIntegerKind(kind, value);
            y = new ConstantNode(newConstant, StampFactory.forInteger(kind, value, value));
        }

        if (flipped) {
            // True/false values flipped against the condition: x < y ? y : x, this is a max.
            minMax = signedness == Signedness.SIGNED ? MaxNode.create(x, y, view) : UnsignedMaxNode.create(x, y, view);
        } else {
            minMax = signedness == Signedness.SIGNED ? MinNode.create(x, y, view) : UnsignedMinNode.create(x, y, view);
        }
        if (extension != null) {
            /*
             * If we had a narrow compare controlling an extended conditional, we have now built a
             * narrow min/max and need to extend it accordingly.
             */
            boolean zeroExtend = extension instanceof ZeroExtendNode;
            Stamp toStamp = trueValue.stamp(view).unrestricted();
            minMax = IntegerConvertNode.convert(minMax, toStamp, zeroExtend, view);
        }

        return minMax;
    }

    /**
     * Determine if {@code a == b} or both {@code a} and {@code b} are primitive integer constants
     * such that numerically {@code a == b} (even if they have different kinds) or
     * {@code a == b + 1}. The latter case only applies if {@code b + 1} does not overflow
     * {@code long} according to {@code signedness}.
     */
    private static boolean equalOrOffBy1(Signedness signedness, ValueNode a, ValueNode b) {
        if (a == b) {
            return true;
        }
        if (a.isJavaConstant() && b.isJavaConstant()) {
            long x = a.asJavaConstant().asLong();
            long y = b.asJavaConstant().asLong();
            if (x == y) {
                return true;
            } else if (x == y + 1) {
                long upperLimit = signedness == Signedness.SIGNED ? NumUtil.maxValue(Long.SIZE) : NumUtil.maxValueUnsigned(Long.SIZE);
                if (y == upperLimit) {
                    // y + 1 overflows
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to return a conditional value equivalent to this min/max node. Implementations may
     * return {@code null} if no simple equivalent conditional form exists. Implementations that
     * build a compare node must take {@link LoweringProvider#smallestCompareWidth()} into account.
     */
    public abstract ValueNode asConditional(LoweringProvider lowerer);

    /**
     * Helper for {@link #asConditional(LoweringProvider)}, extending the value if needed to match
     * the {@link LoweringProvider#smallestCompareWidth()}.
     */
    protected static ValueNode maybeExtendForCompare(ValueNode value, LoweringProvider lowerer, Signedness signedness) {
        Stamp fromStamp = value.stamp(NodeView.DEFAULT);
        if (fromStamp instanceof PrimitiveStamp && PrimitiveStamp.getBits(fromStamp) < lowerer.smallestCompareWidth()) {
            Stamp toStamp = IntegerStamp.create(lowerer.smallestCompareWidth());
            boolean zeroExtend = (signedness == Signedness.UNSIGNED);
            return IntegerConvertNode.convert(value, toStamp, zeroExtend, NodeView.DEFAULT);
        }
        return value;
    }
}
