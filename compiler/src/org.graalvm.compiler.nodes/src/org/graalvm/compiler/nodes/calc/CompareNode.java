/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;

import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;

@NodeInfo(cycles = CYCLES_1)
public abstract class CompareNode extends BinaryOpLogicNode implements Canonicalizable.Binary<ValueNode> {

    public static final NodeClass<CompareNode> TYPE = NodeClass.create(CompareNode.class);
    protected final CanonicalCondition condition;
    protected final boolean unorderedIsTrue;

    /**
     * Constructs a new Compare instruction.
     *
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     */
    protected CompareNode(NodeClass<? extends CompareNode> c, CanonicalCondition condition, boolean unorderedIsTrue, ValueNode x, ValueNode y) {
        super(c, x, y);
        this.condition = condition;
        this.unorderedIsTrue = unorderedIsTrue;
    }

    /**
     * Gets the condition (comparison operation) for this instruction.
     *
     * @return the condition
     */
    public final CanonicalCondition condition() {
        return condition;
    }

    /**
     * Checks whether unordered inputs mean true or false (only applies to float operations).
     *
     * @return {@code true} if unordered inputs produce true
     */
    public final boolean unorderedIsTrue() {
        return this.unorderedIsTrue;
    }

    public static LogicNode tryConstantFold(CanonicalCondition condition, ValueNode forX, ValueNode forY, ConstantReflectionProvider constantReflection, boolean unorderedIsTrue) {
        if (forX.isConstant() && forY.isConstant() && (constantReflection != null || forX.asConstant() instanceof PrimitiveConstant)) {
            return LogicConstantNode.forBoolean(condition.foldCondition(forX.asConstant(), forY.asConstant(), constantReflection, unorderedIsTrue));
        }
        return null;
    }

    @SuppressWarnings("unused")
    public static LogicNode tryConstantFoldPrimitive(CanonicalCondition condition, ValueNode forX, ValueNode forY, boolean unorderedIsTrue, NodeView view) {
        if (forX.asConstant() instanceof PrimitiveConstant && forY.asConstant() instanceof PrimitiveConstant) {
            return LogicConstantNode.forBoolean(condition.foldCondition((PrimitiveConstant) forX.asConstant(), (PrimitiveConstant) forY.asConstant(), unorderedIsTrue));
        }
        return null;
    }

    /**
     * Does this operation represent an identity check such that for x == y, x is exactly the same
     * thing as y. This is generally true except for some floating point comparisons.
     *
     * @return true for identity comparisons
     */
    public boolean isIdentityComparison() {
        return condition == CanonicalCondition.EQ;
    }

    public abstract static class CompareOp {
        public LogicNode canonical(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, CanonicalCondition condition,
                        boolean unorderedIsTrue, ValueNode forX, ValueNode forY, NodeView view) {
            LogicNode constantCondition = tryConstantFold(condition, forX, forY, constantReflection, unorderedIsTrue);
            if (constantCondition != null) {
                return constantCondition;
            }
            LogicNode result;
            if (forX.isConstant()) {
                if ((result = canonicalizeSymmetricConstant(constantReflection, metaAccess, options, smallestCompareWidth, condition, forX.asConstant(), forY, true, unorderedIsTrue, view)) != null) {
                    return result;
                }
            } else if (forY.isConstant()) {
                if ((result = canonicalizeSymmetricConstant(constantReflection, metaAccess, options, smallestCompareWidth, condition, forY.asConstant(), forX, false, unorderedIsTrue, view)) != null) {
                    return result;
                }
            } else if (forX instanceof ConvertNode && forY instanceof ConvertNode) {
                ConvertNode convertX = (ConvertNode) forX;
                ConvertNode convertY = (ConvertNode) forY;
                if (convertX.preservesOrder(condition) && convertY.preservesOrder(condition) && convertX.getValue().stamp(view).isCompatible(convertY.getValue().stamp(view))) {
                    boolean supported = isConstantConversionSupported(convertX, view, smallestCompareWidth);
                    if (supported && convertX.getValue().stamp(view) instanceof IntegerStamp) {
                        supported = convertX.getClass() == convertY.getClass();
                    }

                    if (supported) {

                        ValueNode xValue = convertX.getValue();
                        ValueNode yValue = convertY.getValue();

                        if (forX instanceof ZeroExtendNode || forX instanceof SignExtendNode) {

                            int introducedUsages = 0;
                            int eliminatedNodes = 0;

                            if (convertX.asNode().hasExactlyOneUsage()) {
                                eliminatedNodes++;
                            } else if (xValue.hasExactlyOneUsage()) {
                                introducedUsages++;
                            }

                            if (convertY.asNode().hasExactlyOneUsage()) {
                                eliminatedNodes++;
                            } else if (yValue.hasExactlyOneUsage()) {
                                introducedUsages++;
                            }

                            if (introducedUsages > eliminatedNodes) {
                                // Only perform the optimization if there is
                                // a good trade-off between introduced new usages and
                                // eliminated nodes.
                                return null;
                            }
                        }
                        return duplicateModified(convertX.getValue(), convertY.getValue(), unorderedIsTrue, view);
                    }
                }
            }
            return null;
        }

        protected LogicNode canonicalizeSymmetricConstant(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        CanonicalCondition condition, Constant constant, ValueNode nonConstant, boolean mirrored, boolean unorderedIsTrue, NodeView view) {
            if (nonConstant instanceof ConditionalNode) {
                Condition realCondition = condition.asCondition();
                if (mirrored) {
                    realCondition = realCondition.mirror();
                }
                return optimizeConditional(constant, (ConditionalNode) nonConstant, constantReflection, realCondition, unorderedIsTrue);
            } else if (nonConstant instanceof AbstractNormalizeCompareNode) {
                return optimizeNormalizeCompare(constantReflection, metaAccess, options, smallestCompareWidth, constant, (AbstractNormalizeCompareNode) nonConstant, mirrored, view);
            } else if (nonConstant instanceof ConvertNode) {
                ConvertNode convert = (ConvertNode) nonConstant;
                boolean multiUsage = convert.asNode().hasMoreThanOneUsage() && convert.getValue().hasExactlyOneUsageOfType(InputType.Value);
                if (convert instanceof IntegerConvertNode && multiUsage) {
                    // Do not perform for integer converts if it could introduce
                    // new live values.
                    return null;
                }

                if (convert instanceof NarrowNode) {
                    NarrowNode narrowNode = (NarrowNode) convert;
                    if (narrowNode.getInputBits() > 32 && !constant.isDefaultForKind()) {
                        // Avoid large integer constants.
                        return null;
                    }
                    Stamp convertInputStamp = narrowNode.getValue().stamp(NodeView.DEFAULT);

                    // if we don't know the range the narrowing cannot be safely folded away
                    if (convertInputStamp.isUnrestricted()) {
                        return null;
                    }

                    // don't proceed if we will be changing the range of values
                    if (convertInputStamp instanceof IntegerStamp) {
                        IntegerStamp intConvertInputStamp = (IntegerStamp) convertInputStamp;
                        IntegerStamp intConvertStamp = (IntegerStamp) narrowNode.stamp(NodeView.DEFAULT);
                        if (condition.isUnsigned()) {
                            if (intConvertInputStamp.unsignedLowerBound() < intConvertStamp.unsignedLowerBound() || intConvertInputStamp.unsignedUpperBound() > intConvertStamp.unsignedUpperBound()) {
                                return null;
                            }
                        } else {
                            if (intConvertInputStamp.lowerBound() < intConvertStamp.lowerBound() || intConvertInputStamp.upperBound() > intConvertStamp.upperBound()) {
                                return null;
                            }
                        }
                    } else if (convertInputStamp instanceof FloatStamp) {
                        FloatStamp floatConvertInputStamp = (FloatStamp) convertInputStamp;
                        FloatStamp floatConvertStamp = (FloatStamp) narrowNode.stamp(NodeView.DEFAULT);
                        GraalError.guarantee(!condition.isUnsigned(), "An unsigned floating point comparison makes no sense");
                        if (floatConvertInputStamp.lowerBound() < floatConvertStamp.lowerBound() || floatConvertInputStamp.upperBound() > floatConvertStamp.upperBound()) {
                            return null;
                        }
                    } else {
                        return null;
                    }
                }

                if (isConstantConversionSupported(convert, view, smallestCompareWidth)) {
                    ConstantNode newConstant = canonicalConvertConstant(constantReflection, metaAccess, condition, convert, constant, view);
                    if (newConstant != null) {
                        if (mirrored) {
                            return duplicateModified(newConstant, convert.getValue(), unorderedIsTrue, view);
                        } else {
                            return duplicateModified(convert.getValue(), newConstant, unorderedIsTrue, view);
                        }
                    }
                }
            }

            return null;
        }

        private static boolean isConstantConversionSupported(ConvertNode convert, NodeView view, Integer smallestCompareWidth) {
            Stamp stamp = convert.getValue().stamp(view);
            boolean supported = stamp instanceof PrimitiveStamp || stamp.isPointerStamp();

            /*
             * Must ensure comparison width is not less than the minimum compare width supported on
             * the target.
             */
            if (supported && stamp instanceof IntegerStamp) {
                IntegerStamp intStamp = (IntegerStamp) convert.getValue().stamp(view);
                supported = smallestCompareWidth != null && intStamp.getBits() >= smallestCompareWidth;
            }

            return supported;
        }

        private static ConstantNode canonicalConvertConstant(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, CanonicalCondition condition, ConvertNode convert,
                        Constant constant, NodeView view) {
            if (convert.preservesOrder(condition, constant, constantReflection)) {
                Constant reverseConverted = convert.reverse(constant, constantReflection);
                if (reverseConverted != null && convert.convert(reverseConverted, constantReflection).equals(constant)) {
                    return ConstantNode.forConstant(convert.getValue().stamp(view), reverseConverted, metaAccess);
                }
            }
            return null;
        }

        @SuppressWarnings("unused")
        protected LogicNode optimizeNormalizeCompare(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        Constant constant, AbstractNormalizeCompareNode normalizeNode, boolean mirrored, NodeView view) {
            throw new PermanentBailoutException("NormalizeCompareNode connected to %s (%s %s %s)", this, constant, normalizeNode, mirrored);
        }

        private static LogicNode optimizeConditional(Constant constant, ConditionalNode conditionalNode, ConstantReflectionProvider constantReflection, Condition cond, boolean unorderedIsTrue) {
            Constant trueConstant = conditionalNode.trueValue().asConstant();
            Constant falseConstant = conditionalNode.falseValue().asConstant();

            if (falseConstant != null && trueConstant != null && constantReflection != null) {
                boolean trueResult = cond.foldCondition(trueConstant, constant, constantReflection, unorderedIsTrue);
                boolean falseResult = cond.foldCondition(falseConstant, constant, constantReflection, unorderedIsTrue);

                if (trueResult == falseResult) {
                    return LogicConstantNode.forBoolean(trueResult);
                } else {
                    if (trueResult) {
                        assert falseResult == false;
                        return conditionalNode.condition();
                    } else {
                        assert falseResult == true;
                        return LogicNegationNode.create(conditionalNode.condition());

                    }
                }
            }

            return null;
        }

        protected abstract LogicNode duplicateModified(ValueNode newW, ValueNode newY, boolean unorderedIsTrue, NodeView view);
    }

    public static LogicNode createCompareNode(StructuredGraph graph, CanonicalCondition condition, ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection, NodeView view) {
        LogicNode result = createCompareNode(condition, x, y, constantReflection, view);
        return (result.graph() == null ? graph.addOrUniqueWithInputs(result) : result);
    }

    public static LogicNode createCompareNode(CanonicalCondition condition, ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection, NodeView view) {
        assert x.getStackKind() == y.getStackKind();
        assert !x.getStackKind().isNumericFloat();

        LogicNode comparison;
        if (condition == CanonicalCondition.EQ) {
            if (x.stamp(view) instanceof AbstractObjectStamp) {
                comparison = ObjectEqualsNode.create(x, y, constantReflection, view);
            } else if (x.stamp(view) instanceof AbstractPointerStamp) {
                comparison = PointerEqualsNode.create(x, y, view);
            } else {
                assert x.getStackKind().isNumericInteger();
                comparison = IntegerEqualsNode.create(x, y, view);
            }
        } else if (condition == CanonicalCondition.LT) {
            assert x.getStackKind().isNumericInteger();
            comparison = IntegerLessThanNode.create(x, y, view);
        } else {
            assert condition == CanonicalCondition.BT;
            assert x.getStackKind().isNumericInteger();
            comparison = IntegerBelowNode.create(x, y, view);
        }

        return comparison;
    }

    public static LogicNode createCompareNode(StructuredGraph graph, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                    CanonicalCondition condition, ValueNode x, ValueNode y, NodeView view) {
        LogicNode result = createCompareNode(constantReflection, metaAccess, options, smallestCompareWidth, condition, x, y, view);
        return (result.graph() == null ? graph.addOrUniqueWithInputs(result) : result);
    }

    public static LogicNode createAnyCompareNode(Condition condition, ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        ValueNode xx = x;
        ValueNode yy = y;
        Condition canonicalCondition = condition;
        if (canonicalCondition.canonicalMirror()) {
            xx = y;
            yy = x;
            canonicalCondition = condition.mirror();
        }
        boolean negate = false;
        if (canonicalCondition.canonicalNegate()) {
            negate = true;
            canonicalCondition = canonicalCondition.negate();
        }
        CanonicalCondition canon = null;
        switch (canonicalCondition) {
            case EQ:
                canon = CanonicalCondition.EQ;
                break;
            case LT:
                canon = CanonicalCondition.LT;
                break;
            case BT:
                canon = CanonicalCondition.BT;
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        LogicNode logic = createCompareNode(canon, xx, yy, constantReflection, NodeView.DEFAULT);
        if (negate) {
            return LogicNegationNode.create(logic);
        } else {
            return logic;
        }
    }

    public boolean implies(LogicNode otherLogicNode, boolean thisNegated) {
        boolean otherNegated = false;
        LogicNode otherLogic = otherLogicNode;
        while (otherLogic instanceof LogicNegationNode) {
            otherLogic = ((LogicNegationNode) otherLogic).getValue();
            otherNegated = !otherNegated;
        }
        if (otherLogic instanceof CompareNode) {
            CompareNode otherCompare = (CompareNode) otherLogic;
            return implies(otherCompare, otherNegated, thisNegated);
        } else {
            return false;
        }
    }

    public boolean implies(CompareNode otherCompare, boolean otherNegated, boolean thisNegated) {
        CanonicalCondition otherCondition = otherCompare.condition();
        ValueNode otherX = otherCompare.getX();
        ValueNode otherY = otherCompare.getY();
        if (condition() == otherCondition && sameValue(getX(), otherX) && sameValue(getY(), otherY) && thisNegated == otherNegated) {
            return true;
        }
        if ((sameValue(getX(), otherX) && sameValue(getY(), otherY)) || (sameValue(getX(), otherY) && sameValue(getY(), otherX))) {
            if (condition() == CanonicalCondition.EQ && (otherCondition == CanonicalCondition.LT || otherCondition == CanonicalCondition.BT)) {
                if (!thisNegated && otherNegated) {
                    // a == b => !(a < b)
                    return true;
                }
            }
            if (otherCondition == CanonicalCondition.EQ && (condition() == CanonicalCondition.LT || condition() == CanonicalCondition.BT)) {
                if (thisNegated && !otherNegated) {
                    // a < b => a != b
                    return true;
                }
            }
        }
        if (condition() == otherCondition && sameValue(getX(), otherY) && sameValue(getY(), otherX)) {
            if ((condition() == CanonicalCondition.LT || condition() == CanonicalCondition.BT) && getY().stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                if (!thisNegated && otherNegated) {
                    // a < b => !(b < a)
                    return true;
                }
            }
        }
        if (sameValue(getX(), otherX) && getY().isJavaConstant() && otherY.isJavaConstant() && getY().stamp(NodeView.DEFAULT) instanceof IntegerStamp &&
                        otherY.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
            long thisYLong = getY().asJavaConstant().asLong();
            long otherYLong = otherY.asJavaConstant().asLong();
            if (condition() == CanonicalCondition.EQ && !thisNegated) {
                if (otherCondition == CanonicalCondition.EQ) {
                    if (thisYLong != otherYLong && otherNegated) {
                        // a == c1 & c1 != c2 => a != c2
                        return true;
                    }
                } else if (otherCondition == CanonicalCondition.LT) {
                    if (!otherNegated && thisYLong < otherYLong) {
                        // a == c1 & c1 < c2 => a < c2
                        return true;
                    } else if (otherNegated && otherYLong < thisYLong) {
                        // a == c1 & c2 < c1 => !(a < c2)
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean sameValue(ValueNode v1, ValueNode v2) {
        if (v1 == v2) {
            return true;
        }
        if (v1.isConstant() && v2.isConstant()) {
            return v1.asConstant().equals(v2.asConstant());
        }
        if (GraphUtil.skipPi(v1) == GraphUtil.skipPi(v2)) {
            return true;
        }
        return false;
    }

    public static LogicNode createCompareNode(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                    CanonicalCondition condition, ValueNode x, ValueNode y, NodeView view) {
        assert x.getStackKind() == y.getStackKind();
        assert !x.getStackKind().isNumericFloat();

        LogicNode comparison;
        if (condition == CanonicalCondition.EQ) {
            if (x.stamp(view) instanceof AbstractObjectStamp) {
                assert smallestCompareWidth == null;
                comparison = ObjectEqualsNode.create(constantReflection, metaAccess, options, x, y, view);
            } else if (x.stamp(view) instanceof AbstractPointerStamp) {
                comparison = PointerEqualsNode.create(x, y, view);
            } else {
                assert x.getStackKind().isNumericInteger();
                comparison = IntegerEqualsNode.create(constantReflection, metaAccess, options, smallestCompareWidth, x, y, view);
            }
        } else if (condition == CanonicalCondition.LT) {
            assert x.getStackKind().isNumericInteger();
            comparison = IntegerLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, x, y, view);
        } else {
            assert condition == CanonicalCondition.BT;
            assert x.getStackKind().isNumericInteger();
            comparison = IntegerBelowNode.create(constantReflection, metaAccess, options, smallestCompareWidth, x, y, view);
        }

        return comparison;
    }

    public static LogicNode createFloatCompareNode(StructuredGraph graph, CanonicalCondition condition, ValueNode x, ValueNode y, boolean unorderedIsTrue, NodeView view) {
        LogicNode result = createFloatCompareNode(condition, x, y, unorderedIsTrue, view);
        return (result.graph() == null ? graph.addOrUniqueWithInputs(result) : result);
    }

    public static LogicNode createFloatCompareNode(CanonicalCondition condition, ValueNode x, ValueNode y, boolean unorderedIsTrue, NodeView view) {
        assert x.getStackKind() == y.getStackKind();
        assert x.getStackKind().isNumericFloat();

        LogicNode comparison;
        if (condition == CanonicalCondition.EQ) {
            comparison = FloatEqualsNode.create(x, y, view);
        } else {
            assert condition == CanonicalCondition.LT;
            comparison = FloatLessThanNode.create(x, y, unorderedIsTrue, view);
        }

        return comparison;
    }
}
