/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "|<|")
public final class IntegerBelowNode extends IntegerLowerThanNode {
    public static final NodeClass<IntegerBelowNode> TYPE = NodeClass.create(IntegerBelowNode.class);
    private static final BelowOp OP = new BelowOp();

    public IntegerBelowNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, OP);
        Stamp xStamp = x.stamp(NodeView.DEFAULT);
        Stamp yStamp = y.stamp(NodeView.DEFAULT);
        assert xStamp.isIntegerStamp() : "expected integer x value: " + x;
        assert yStamp.isIntegerStamp() : "expected integer y value: " + y;
        assert xStamp.isCompatible(yStamp) : "expected compatible stamps: " + xStamp + " / " + yStamp;
    }

    public static LogicNode create(ValueNode x, ValueNode y, NodeView view) {
        return OP.create(x, y, view);
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, ValueNode x, ValueNode y,
                    NodeView view) {
        LogicNode value = OP.canonical(constantReflection, metaAccess, options, smallestCompareWidth, OP.getCondition(), false, x, y, view);
        if (value != null) {
            return value;
        }
        return create(x, y, view);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), OP.getCondition(), false, forX, forY, view);
        if (value != null) {
            return value;
        }
        return this;
    }

    public static class BelowOp extends LowerOp {
        @Override
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue, NodeView view) {
            assert newX.stamp(NodeView.DEFAULT) instanceof IntegerStamp : Assertions.errorMessageContext("newX", newX);
            assert newY.stamp(NodeView.DEFAULT) instanceof IntegerStamp : Assertions.errorMessageContext("newY", newY);
            return new IntegerBelowNode(newX, newY);
        }

        @Override
        protected LogicNode optimizeNormalizeCompare(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                        Constant constant, AbstractNormalizeCompareNode normalizeNode, boolean mirrored, NodeView view) {
            PrimitiveConstant primitive = (PrimitiveConstant) constant;
            long c = primitive.asLong();
            /*
             * Optimize comparisons like:
             *
             * @formatter:off
             * (a NC b) |<| c  (not mirrored)
             * c |<| (a NC b)  (mirrored)
             * @formatter:on
             *
             * (a NC b) is always (-1, 0, 1) corresponding to a (<, ==, >) b according to some signed or unsigned ordering.
             */
            if (mirrored) {
                /* @formatter:off
                 * c |<| (a NC b)  (mirrored)
                 * cases for c:
                 *  UMAX (=-1)  -> false
                 *  0           -> a != b
                 *  [1, UMAX-1] -> a < b
                 * @formatter:on
                 */
                if (c == -1) {
                    return LogicConstantNode.contradiction();
                } else if (c == 0) {
                    LogicNode equal = normalizeNode.createEqualComparison(constantReflection, metaAccess, options, smallestCompareWidth, view);
                    return LogicNegationNode.create(equal);
                } else {
                    return normalizeNode.createLowerComparison(constantReflection, metaAccess, options, smallestCompareWidth, view);
                }
            } else {
                /* @formatter:off
                 * (a NC b) |<| c  (not mirrored)
                 * cases for c:
                 *  0         -> false
                 *  1         -> a == b
                 *  [2, UMAX] -> a >= b
                 * @formatter:on
                 */
                if (c == 0) {
                    return LogicConstantNode.contradiction();
                } else if (c == 1) {
                    return normalizeNode.createEqualComparison(constantReflection, metaAccess, options, smallestCompareWidth, view);
                } else {
                    // a >= b -> !(a < b)
                    LogicNode compare = normalizeNode.createLowerComparison(constantReflection, metaAccess, options, smallestCompareWidth, view);
                    return LogicNegationNode.create(compare);
                }
            }
        }

        @Override
        protected LogicNode findSynonym(ValueNode forX, ValueNode forY, NodeView view) {
            LogicNode result = super.findSynonym(forX, forY, view);
            if (result != null) {
                return result;
            }
            if (forX.stamp(view) instanceof IntegerStamp) {
                assert forY.stamp(view) instanceof IntegerStamp : Assertions.errorMessageContext("this", this, "forX", forX, "forY", forY);
                IntegerStamp xStamp = (IntegerStamp) forX.stamp(view);
                IntegerStamp yStamp = (IntegerStamp) forY.stamp(view);
                int bits = xStamp.getBits();
                assert yStamp.getBits() == bits : Assertions.errorMessageContext("this", this, "yStamp", yStamp);
                LogicNode logic = canonicalizeRangeFlip(forX, forY, bits, false, view);
                if (logic != null) {
                    return logic;
                }
                if (xStamp.isPositive() && forY instanceof ConditionalNode && ((ConditionalNode) forY).condition() instanceof IntegerBelowNode) {
                    logic = canonicalizeBelowCanonicalOfBelow(forX, (ConditionalNode) forY, view);
                    if (logic != null) {
                        return logic;
                    }
                }
            }
            return null;
        }

        private static LogicNode canonicalizeBelowCanonicalOfBelow(ValueNode forX, ConditionalNode forY, NodeView view) {
            IntegerBelowNode below = (IntegerBelowNode) forY.condition();
            ValueNode n = below.getX();
            if (((IntegerStamp) n.stamp(view)).isPositive() && forY.trueValue().isDefaultConstant() && below.getY().isJavaConstant() && forY.falseValue() instanceof AddNode) {
                AddNode add = (AddNode) forY.falseValue();
                if (add.getX() == below.getX() && add.getY().isJavaConstant() && add.getY().asJavaConstant().asLong() < 0 &&
                                add.getY().asJavaConstant().asLong() == -below.getY().asJavaConstant().asLong()) {
                    ValueNode c = below.getY();
                    /*
                     * We have:
                     *
                     * x |<| (n |<| C ? 0 : n - C)
                     *
                     * where we know that both x (checked by the caller) and n are non-negative.
                     * This is the same as:
                     *
                     * x < n - C
                     */
                    return IntegerLessThanNode.create(forX, SubNode.create(n, c, view), view);
                }
            }

            return null;
        }

        @Override
        protected boolean isMatchingBitExtendNode(ValueNode node) {
            return node instanceof ZeroExtendNode;
        }

        @Override
        protected boolean addCanOverflow(IntegerStamp a, IntegerStamp b) {
            assert a.getBits() == b.getBits() : Assertions.errorMessageContext("a", a, "b", b);
            // a + b |<| a
            if (a.getBits() == Long.SIZE) {
                return Long.compareUnsigned(upperBound(a) + upperBound(b), upperBound(a)) < 0;
            }
            if (a.getBits() == Integer.SIZE) {
                return Integer.compareUnsigned((int) upperBound(a) + (int) upperBound(b), (int) upperBound(a)) < 0;
            }
            return true;
        }

        @Override
        protected boolean leftShiftCanOverflow(IntegerStamp a, long shift) {
            // leading zeros, adjusted to stamp bits
            int leadingZeroForBits = Long.numberOfLeadingZeros(a.mayBeSet()) - (Long.SIZE - a.getBits());
            return leadingZeroForBits < shift;
        }

        @Override
        protected long upperBound(IntegerStamp stamp) {
            return stamp.unsignedUpperBound();
        }

        @Override
        protected long lowerBound(IntegerStamp stamp) {
            return stamp.unsignedLowerBound();
        }

        @Override
        protected int compare(long a, long b) {
            return Long.compareUnsigned(a, b);
        }

        @Override
        protected long min(long a, long b) {
            return NumUtil.minUnsigned(a, b);
        }

        @Override
        protected long max(long a, long b) {
            return NumUtil.maxUnsigned(a, b);
        }

        @Override
        protected long cast(long a, int bits) {
            return CodeUtil.zeroExtend(a, bits);
        }

        @Override
        protected long minValue(int bits) {
            return 0;
        }

        @Override
        protected long maxValue(int bits) {
            return NumUtil.maxValueUnsigned(bits);
        }

        @Override
        protected IntegerStamp forInteger(int bits, long min, long max) {
            return StampFactory.forUnsignedInteger(bits, min, max);
        }

        @Override
        protected CanonicalCondition getCondition() {
            return CanonicalCondition.BT;
        }

        @Override
        protected IntegerLowerThanNode createNode(ValueNode x, ValueNode y) {
            return new IntegerBelowNode(x, y);
        }
    }

    @Override
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (other instanceof LogicNegationNode) {
            // Unwrap negations.
            TriState result = implies(thisNegated, ((LogicNegationNode) other).getValue());
            if (result.isKnown()) {
                return TriState.get(!result.toBoolean());
            }
        }
        if (!thisNegated) {
            if (other instanceof IntegerLessThanNode) {
                IntegerLessThanNode integerLessThanNode = (IntegerLessThanNode) other;
                IntegerStamp stampL = (IntegerStamp) this.getY().stamp(NodeView.DEFAULT);
                // if L >= 0:
                if (stampL.isPositive()) { // L >= 0
                    if (this.getX() == integerLessThanNode.getX()) {
                        // x |<| L implies x < L
                        if (this.getY() == integerLessThanNode.getY()) {
                            return TriState.TRUE;
                        }
                        // x |<| L implies !(x < 0)
                        if (integerLessThanNode.getY().isConstant() &&
                                        IntegerStamp.OPS.getAdd().isNeutral(integerLessThanNode.getY().asConstant())) {
                            return TriState.FALSE;
                        }
                    }
                }
            }
        }
        return super.implies(thisNegated, other);
    }
}
