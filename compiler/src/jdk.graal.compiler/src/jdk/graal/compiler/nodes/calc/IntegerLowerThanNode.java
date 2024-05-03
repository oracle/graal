/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.CodeUtil.mask;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

/**
 * Common super-class for "a &lt; b" comparisons both {@linkplain IntegerLessThanNode signed} and
 * {@linkplain IntegerBelowNode unsigned}.
 */
@NodeInfo()
public abstract class IntegerLowerThanNode extends CompareNode {
    public static final NodeClass<IntegerLowerThanNode> TYPE = NodeClass.create(IntegerLowerThanNode.class);
    private final LowerOp op;

    protected IntegerLowerThanNode(NodeClass<? extends CompareNode> c, ValueNode x, ValueNode y, LowerOp op) {
        super(c, op.getCondition(), false, x, y);
        this.op = op;
    }

    protected LowerOp getOp() {
        return op;
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStampGeneric, Stamp yStampGeneric) {
        return getSucceedingStampForX(negated, !negated, xStampGeneric, yStampGeneric, getX(), getY());
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStampGeneric, Stamp yStampGeneric) {
        return getSucceedingStampForX(!negated, !negated, yStampGeneric, xStampGeneric, getY(), getX());
    }

    private Stamp getSucceedingStampForX(boolean mirror, boolean strict, Stamp xStampGeneric, Stamp yStampGeneric, ValueNode forX, ValueNode forY) {
        Stamp s = getSucceedingStampForX(mirror, strict, xStampGeneric, yStampGeneric);
        if (s != null && s.isUnrestricted()) {
            s = null;
        }
        if (forY instanceof AddNode && xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            AddNode addNode = (AddNode) forY;
            IntegerStamp aStamp = null;
            if (addNode.getX() == forX && addNode.getY().stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                // x < x + a
                aStamp = (IntegerStamp) addNode.getY().stamp(NodeView.DEFAULT);
            } else if (addNode.getY() == forX && addNode.getX().stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                // x < a + x
                aStamp = (IntegerStamp) addNode.getX().stamp(NodeView.DEFAULT);
            }
            if (aStamp != null) {
                IntegerStamp result = getOp().getSucceedingStampForXLowerXPlusA(mirror, strict, aStamp, xStamp);
                result = (IntegerStamp) xStamp.tryImproveWith(result);
                if (result != null) {
                    if (s != null) {
                        s = s.improveWith(result);
                    } else {
                        s = result;
                    }
                }
            }
        }
        return s;
    }

    private Stamp getSucceedingStampForX(boolean mirror, boolean strict, Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            if (yStampGeneric instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                assert yStamp.getBits() == xStamp.getBits() : Assertions.errorMessageContext("this", this, "mirror", mirror, "strict", strict, "xStamp", xStamp, "yStamp", yStamp);
                Stamp s = getOp().getSucceedingStampForX(xStamp, yStamp, mirror, strict);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        return getOp().tryFold(xStampGeneric, yStampGeneric);
    }

    public abstract static class LowerOp extends CompareOp {
        @Override
        public LogicNode canonical(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, CanonicalCondition condition,
                        boolean unorderedIsTrue, ValueNode forX, ValueNode forY, NodeView view) {
            if (forX == forY) {
                return LogicConstantNode.contradiction();
            }
            LogicNode result = super.canonical(constantReflection, metaAccess, options, smallestCompareWidth, condition, unorderedIsTrue, forX, forY, view);
            if (result != null) {
                return result;
            }
            LogicNode synonym = findSynonym(forX, forY, view);
            if (synonym != null) {
                return synonym;
            }
            return null;
        }

        protected abstract long upperBound(IntegerStamp stamp);

        protected abstract long lowerBound(IntegerStamp stamp);

        protected abstract int compare(long a, long b);

        protected abstract long min(long a, long b);

        protected abstract long max(long a, long b);

        protected long min(long a, long b, int bits) {
            return min(cast(a, bits), cast(b, bits));
        }

        protected long max(long a, long b, int bits) {
            return max(cast(a, bits), cast(b, bits));
        }

        protected abstract long cast(long a, int bits);

        protected abstract long minValue(int bits);

        protected abstract long maxValue(int bits);

        protected abstract IntegerStamp forInteger(int bits, long min, long max);

        protected abstract CanonicalCondition getCondition();

        protected abstract IntegerLowerThanNode createNode(ValueNode x, ValueNode y);

        public LogicNode create(ValueNode x, ValueNode y, NodeView view) {
            LogicNode result = tryConstantFoldPrimitive(getCondition(), x, y, false, view);
            if (result != null) {
                return result;
            } else {
                result = findSynonym(x, y, view);
                if (result != null) {
                    return result;
                }
                return createNode(x, y);
            }
        }

        /**
         * Tries to further canonicalize the condition, but only returns the result if it is
         * constant.
         *
         * @see #create
         */
        public LogicConstantNode constantOrNull(ValueNode x, ValueNode y, NodeView view) {
            LogicNode result = tryConstantFoldPrimitive(getCondition(), x, y, false, view);
            if (result instanceof LogicConstantNode) {
                return (LogicConstantNode) result;
            } else {
                result = findSynonym(x, y, view);
                if (result instanceof LogicConstantNode) {
                    return (LogicConstantNode) result;
                }
                return null;
            }
        }

        protected LogicNode findSynonym(ValueNode forX, ValueNode forY, NodeView view) {
            if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
                return LogicConstantNode.contradiction();
            }
            Stamp xStampGeneric = forX.stamp(view);
            TriState fold = tryFold(xStampGeneric, forY.stamp(view));
            if (fold.isTrue()) {
                return LogicConstantNode.tautology();
            } else if (fold.isFalse()) {
                return LogicConstantNode.contradiction();
            }
            if (forY.stamp(view) instanceof IntegerStamp) {
                IntegerStamp yStamp = (IntegerStamp) forY.stamp(view);
                IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
                int bits = yStamp.getBits();
                if (forX.isJavaConstant() && !forY.isConstant()) {
                    // bring the constant on the right
                    long xValue = forX.asJavaConstant().asLong();
                    if (xValue != maxValue(bits)) {
                        // c < x <=> !(c >= x) <=> !(x <= c) <=> !(x < c + 1)
                        return LogicNegationNode.create(create(forY, ConstantNode.forIntegerStamp(yStamp, xValue + 1), view));
                    }
                }
                if (forY.isJavaConstant()) {
                    long yValue = forY.asJavaConstant().asLong();

                    // x < MAX <=> x != MAX
                    if (yValue == maxValue(bits)) {
                        return LogicNegationNode.create(IntegerEqualsNode.create(forX, forY, view));
                    }

                    // x < MIN + 1 <=> x <= MIN <=> x == MIN
                    if (yValue == minValue(bits) + 1) {
                        return IntegerEqualsNode.create(forX, ConstantNode.forIntegerStamp(yStamp, minValue(bits)), view);
                    }

                    // (x < c && x >= c - 1) => x == c - 1
                    // If the constant is negative, only signed comparison is allowed.
                    if (yValue != minValue(bits) && xStamp.lowerBound() == yValue - 1 && (yValue > 0 || getCondition() == CanonicalCondition.LT)) {
                        return IntegerEqualsNode.create(forX, ConstantNode.forIntegerStamp(yStamp, yValue - 1), view);
                    }

                } else if (forY instanceof AddNode) {
                    AddNode addNode = (AddNode) forY;
                    LogicNode canonical = canonicalizeXLowerXPlusA(forX, addNode, false, true, view);
                    if (canonical != null) {
                        return canonical;
                    }
                }
                if (forX instanceof AddNode) {
                    AddNode addNode = (AddNode) forX;
                    LogicNode canonical = canonicalizeXLowerXPlusA(forY, addNode, true, false, view);
                    if (canonical != null) {
                        return canonical;
                    }
                }
                if (forX instanceof PiNode && forY instanceof PiNode) {
                    PiNode piX = (PiNode) forX;
                    PiNode piY = (PiNode) forY;
                    ValueNode originalX = piX.getOriginalNode();
                    ValueNode originalY = piY.getOriginalNode();
                    if (originalY instanceof AddNode && ((AddNode) originalY).getY().isConstant() && ((AddNode) originalY).getX() == originalX) {
                        // piX <- value, piY <- value + c
                        return canonicalizePiXLowerPiXPlusC(piX, piY, false, view);
                    } else if (originalX instanceof AddNode && ((AddNode) originalX).getY().isConstant() && ((AddNode) originalX).getX() == originalY) {
                        // piX <- value + c, piY <- value
                        return canonicalizePiXLowerPiXPlusC(piY, piX, true, view);
                    }
                }
                return canonicalizeCommonArithmetic(forX, forY, view);
            }
            return null;
        }

        /**
         * Converts a {@linkplain IntegerLowerThanNode} with a certain subgraph pattern with two
         * {@link PiNode} inputs and {@link AddNode} with a constant, into a pattern with a single
         * {@link PiNode}.
         *
         * <h1>Semantics</h1>
         *
         * The pattern {@code PI1(value) < PI2(value + c)} is replaced by
         * {@code PI*(value) < PI*(value) + c}, where the stamp of {@code PI*} is {@code PI1(value)}
         * improved by {@code PI2(value + c) - c}.
         *
         * <h2>Input Subgraph</h2>
         *
         * <pre>
         * +----+    +-------+   +---+    +----+
         * | G1 |    | value |   | c |    | G2 |
         * +----+    +---+---+   +-+-+    +-+--+
         *       \       |   \   /         /
         *        \      |   +-+-+        /
         *         \     |   | + |       /
         *          \    |   +-+-+      /
         *           \   |      \      /
         *            \  |       \    /
         *           +-+-+-+     +-+-+-+
         *           | PI1 |     | PI2 |
         *           +-----+     +--+--+
         *                 \       /
         *                  \     /
         *                  ++---++
         *                  |  <  |
         *                  +-----+
         * </pre>
         *
         * <h2>Result Subgraph</h2>
         *
         * <pre>
         *  +----+   +-------+      +----+
         *  | G1 |   | value |      | G2 |
         *  +----+   +---+---+      +-+--+
         *        \      |           /
         *         \     |          /
         *          \    |         /
         *           +---+-+      /
         *           | PI1 |     /
         *           +----++    /
         *                 \   /
         *                +-+-+--+
         *                | PI*  |
         *                +-+---++    +---+
         *                  |    \    | c |
         *                  |     \   +-+-+
         *                  |      \   /
         *                  |     +-+-+
         *                  |     | + |
         *                  |     ++--+
         *                  |     /
         *                  |    /
         *                +-+--+-+
         *                |  <   |
         *                +------+
         * </pre>
         *
         * The stamp of {@code PI*} is {@code stamp(PI2 - c)}.
         *
         * @param piValue a node with the pattern {@code PiNode(value)}
         * @param piWithAdd a node with the pattern {@code }PiNode(AddNode(value, c))}
         * @param mirror {@code true} if the {@link AddNode} is on {@linkplain #getY() RHS} of this
         *            {@link IntegerLowerThanNode}.
         * @return a {@link LogicConstantNode} or {@code null} if the result cannot be proven
         *         constant
         */
        private LogicConstantNode canonicalizePiXLowerPiXPlusC(PiNode piValue, PiNode piWithAdd, boolean mirror, NodeView view) {
            AddNode originalWithAdd = (AddNode) piWithAdd.getOriginalNode();
            // piValue <- value
            // piWithAdd <- value + c
            // to
            // newValue <- pi(piValue, stamp(piWithAdd - c))
            // newWithAdd <- newValue + c
            ValueNode constant = originalWithAdd.getY();
            // calculate the stamp of piWithAdd - c
            Stamp piWithAddStamp = piWithAdd.stamp(view);
            Stamp subValueStamp = ArithmeticOpTable.forStamp(piWithAddStamp).getSub() // get subOp
                            .foldStamp(piWithAddStamp, constant.stamp(view));
            // create piValue with better stamp
            ValueNode newValue = PiNode.create(piValue, subValueStamp, piWithAdd.getGuard().asNode());
            // recreate to original operation of piWithAdd but using newValue instead of value
            ValueNode newWithAdd = AddNode.create(newValue, constant, view);
            if (mirror) {
                return constantOrNull(newWithAdd, newValue, view);
            } else {
                return constantOrNull(newValue, newWithAdd, view);
            }
        }

        protected LogicNode canonicalizeCommonArithmetic(ValueNode forX, ValueNode forY, NodeView view) {
            if (isMatchingBitExtendNode(forX) && isMatchingBitExtendNode(forY)) {
                IntegerConvertNode<?> forX1 = (IntegerConvertNode<?>) forX;
                IntegerConvertNode<?> forY1 = (IntegerConvertNode<?>) forY;
                // Extending to 32 bit might be required by the architecture
                if (forX1.getInputBits() >= 32 && forX1.getResultBits() == forY1.getResultBits() && forX1.getInputBits() == forY1.getInputBits()) {
                    return create(forX1.getValue(), forY1.getValue(), view);
                }
            }

            if (forX instanceof AddNode && forY instanceof AddNode) {
                AddNode addX = (AddNode) forX;
                AddNode addY = (AddNode) forY;
                ValueNode v1 = null;
                ValueNode v2 = null;
                ValueNode common = null;
                if (addX.getX() == addY.getX()) {
                    // (x + y) < (x + z) => y < z
                    v1 = addX.getY();
                    v2 = addY.getY();
                    common = addX.getX();
                } else if (addX.getX() == addY.getY()) {
                    // (x + y) < (z + x) => y < z
                    v1 = addX.getY();
                    v2 = addY.getX();
                    common = addX.getX();
                } else if (addX.getY() == addY.getX()) {
                    // (y + x) < (x + z) => y < z
                    v1 = addX.getX();
                    v2 = addY.getY();
                    common = addX.getY();
                } else if (addX.getY() == addY.getY()) {
                    // (y + x) < (z + x) => y < z
                    v1 = addX.getX();
                    v2 = addY.getX();
                    common = addX.getY();
                }
                if (v1 != null) {
                    assert v2 != null;
                    IntegerStamp stamp1 = (IntegerStamp) v1.stamp(view);
                    IntegerStamp stamp2 = (IntegerStamp) v2.stamp(view);
                    IntegerStamp stampCommon = (IntegerStamp) common.stamp(view);
                    if (!addCanOverflow(stamp1, stampCommon) && !addCanOverflow(stamp2, stampCommon)) {
                        return create(v1, v2, view);
                    }
                }
            }

            if (forX instanceof LeftShiftNode && forY instanceof LeftShiftNode) {
                LeftShiftNode leftShiftX = (LeftShiftNode) forX;
                LeftShiftNode leftShiftY = (LeftShiftNode) forY;
                if (leftShiftX.getY() == leftShiftY.getY() &&
                                leftShiftX.getY().isConstant() && leftShiftX.getY().asConstant() instanceof PrimitiveConstant) {
                    PrimitiveConstant constant = (PrimitiveConstant) leftShiftX.getY().asConstant();
                    if (constant.getJavaKind().isNumericInteger()) {
                        ValueNode v1 = leftShiftX.getX();
                        ValueNode v2 = leftShiftY.getX();
                        IntegerStamp stamp1 = (IntegerStamp) v1.stamp(view);
                        IntegerStamp stamp2 = (IntegerStamp) v2.stamp(view);
                        if (!leftShiftCanOverflow(stamp1, constant.asLong()) && !leftShiftCanOverflow(stamp2, constant.asLong())) {
                            return create(v1, v2, view);
                        }
                    }
                }
            }
            return null;
        }

        /**
         * Return {@code true} if the node is an {@link IntegerConvertNode} that does not change the
         * comparison result.
         */
        protected abstract boolean isMatchingBitExtendNode(ValueNode node);

        /**
         * Return {@code true} if the {@code a + b} can overflow w.r.t.
         * {@linkplain IntegerLessThanNode.LessThanOp signed} or
         * {@linkplain IntegerBelowNode.BelowOp unsigned} semantics.
         */
        protected abstract boolean addCanOverflow(IntegerStamp a, IntegerStamp b);

        /**
         * Return {@code true} if the {@code a << shift} can overflow w.r.t.
         * {@linkplain IntegerLessThanNode.LessThanOp signed} or
         * {@linkplain IntegerBelowNode.BelowOp unsigned} semantics.
         */
        protected abstract boolean leftShiftCanOverflow(IntegerStamp a, long shift);

        /**
         * Exploit the fact that adding the (signed) MIN_VALUE on both side flips signed and
         * unsigned comparison.
         *
         * In particular:
         * <ul>
         * <li>{@code x + MIN_VALUE < y + MIN_VALUE <=> x |<| y}</li>
         * <li>{@code x + MIN_VALUE |<| y + MIN_VALUE <=> x < y}</li>
         * </ul>
         */
        protected static LogicNode canonicalizeRangeFlip(ValueNode forX, ValueNode forY, int bits, boolean signed, NodeView view) {
            long min = CodeUtil.minValue(bits);
            long xResidue = 0;
            ValueNode left = null;
            JavaConstant leftCst = null;
            if (forX instanceof AddNode) {
                AddNode xAdd = (AddNode) forX;
                if (xAdd.getY().isJavaConstant() && !xAdd.getY().asJavaConstant().isDefaultForKind()) {
                    long xCst = xAdd.getY().asJavaConstant().asLong();
                    xResidue = xCst - min;
                    left = xAdd.getX();
                }
            } else if (forX.isJavaConstant()) {
                leftCst = forX.asJavaConstant();
            }
            if (left == null && leftCst == null) {
                return null;
            }
            long yResidue = 0;
            ValueNode right = null;
            JavaConstant rightCst = null;
            if (forY instanceof AddNode) {
                AddNode yAdd = (AddNode) forY;
                if (yAdd.getY().isJavaConstant() && !yAdd.getY().asJavaConstant().isDefaultForKind()) {
                    long yCst = yAdd.getY().asJavaConstant().asLong();
                    yResidue = yCst - min;
                    right = yAdd.getX();
                }
            } else if (forY.isJavaConstant()) {
                rightCst = forY.asJavaConstant();
            }
            if (right == null && rightCst == null) {
                return null;
            }
            if ((xResidue == 0 && left != null) || (yResidue == 0 && right != null)) {
                if (left == null) {
                    // Fortify: Suppress Null Dereference false positive
                    assert leftCst != null;

                    left = ConstantNode.forIntegerBits(bits, leftCst.asLong() - min);
                } else if (xResidue != 0) {
                    left = AddNode.create(left, ConstantNode.forIntegerBits(bits, xResidue), view);
                }
                if (right == null) {
                    // Fortify: Suppress Null Dereference false positive
                    assert rightCst != null;

                    right = ConstantNode.forIntegerBits(bits, rightCst.asLong() - min);
                } else if (yResidue != 0) {
                    right = AddNode.create(right, ConstantNode.forIntegerBits(bits, yResidue), view);
                }
                if (signed) {
                    return new IntegerBelowNode(left, right);
                } else {
                    return new IntegerLessThanNode(left, right);
                }
            }
            return null;
        }

        private LogicNode canonicalizeXLowerXPlusA(ValueNode forX, AddNode addNode, boolean mirrored, boolean strict, NodeView view) {
            // x < x + a
            // x |<| x + a
            IntegerStamp xStamp = (IntegerStamp) forX.stamp(view);
            IntegerStamp succeedingXStamp;
            boolean exact;
            if (addNode.getX() == forX && addNode.getY().stamp(view) instanceof IntegerStamp) {
                IntegerStamp aStamp = (IntegerStamp) addNode.getY().stamp(view);
                succeedingXStamp = getSucceedingStampForXLowerXPlusA(mirrored, strict, aStamp, xStamp);
                exact = aStamp.lowerBound() == aStamp.upperBound();
            } else if (addNode.getY() == forX && addNode.getX().stamp(view) instanceof IntegerStamp) {
                IntegerStamp aStamp = (IntegerStamp) addNode.getX().stamp(view);
                succeedingXStamp = getSucceedingStampForXLowerXPlusA(mirrored, strict, aStamp, xStamp);
                exact = aStamp.lowerBound() == aStamp.upperBound();
            } else {
                return null;
            }
            if (succeedingXStamp.join(forX.stamp(view)).isEmpty()) {
                return LogicConstantNode.contradiction();
            } else if (exact && !succeedingXStamp.isEmpty()) {
                int bits = succeedingXStamp.getBits();
                if (compare(lowerBound(succeedingXStamp), minValue(bits)) > 0) {
                    // x must be in [L..MAX] <=> x >= L <=> !(x < L)
                    return LogicNegationNode.create(create(forX, ConstantNode.forIntegerStamp(succeedingXStamp, lowerBound(succeedingXStamp)), view));
                } else if (compare(upperBound(succeedingXStamp), maxValue(bits)) < 0) {
                    // x must be in [MIN..H] <=> x <= H <=> !(H < x)
                    return LogicNegationNode.create(create(ConstantNode.forIntegerStamp(succeedingXStamp, upperBound(succeedingXStamp)), forX, view));
                }
            }
            return null;
        }

        protected TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
            if (xStampGeneric instanceof IntegerStamp && yStampGeneric instanceof IntegerStamp) {
                IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
                IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
                if (compare(upperBound(xStamp), lowerBound(yStamp)) < 0) {
                    return TriState.TRUE;
                }
                if (compare(lowerBound(xStamp), upperBound(yStamp)) >= 0) {
                    return TriState.FALSE;
                }
            }
            return TriState.UNKNOWN;
        }

        protected IntegerStamp getSucceedingStampForX(IntegerStamp xStamp, IntegerStamp yStamp, boolean mirror, boolean strict) {
            int bits = xStamp.getBits();
            assert yStamp.getBits() == bits : Assertions.errorMessageContext("this", this, "xStamp", xStamp, "yStamp", yStamp, "mirror", mirror, "strict", strict);
            if (mirror) {
                long low = lowerBound(yStamp);
                if (strict) {
                    if (low == maxValue(bits)) {
                        return null;
                    }
                    low += 1;
                }
                if (compare(low, lowerBound(xStamp)) > 0 || (upperBound(xStamp) != xStamp.upperBound() && upperBound(xStamp) != (xStamp.upperBound() & mask(xStamp.getBits())))) {
                    return forInteger(bits, low, upperBound(xStamp));
                }
            } else {
                // x < y, i.e., x < y <= Y_UPPER_BOUND so x <= Y_UPPER_BOUND - 1
                long low = upperBound(yStamp);
                if (strict) {
                    if (low == minValue(bits)) {
                        return null;
                    }
                    low -= 1;
                }
                if (compare(low, upperBound(xStamp)) < 0 || (lowerBound(xStamp) != xStamp.lowerBound() && lowerBound(xStamp) != (xStamp.lowerBound() & mask(xStamp.getBits())))) {
                    return forInteger(bits, lowerBound(xStamp), low);
                }
            }
            return null;
        }

        protected IntegerStamp getSucceedingStampForXLowerXPlusA(boolean mirrored, boolean strict, IntegerStamp aStamp, IntegerStamp xStamp) {
            int bits = aStamp.getBits();
            long min = minValue(bits);
            long max = maxValue(bits);

            /*
             * if x < x + a <=> x + a didn't overflow:
             *
             * x is outside ]MAX - a, MAX], i.e., inside [MIN, MAX - a]
             *
             * if a is negative those bounds wrap around correctly.
             *
             * If a is exactly zero this gives an unbounded stamp (any integer) in the positive case
             * and an empty stamp in the negative case: if x |<| x is true, then either x has no
             * value or any value...
             *
             * This does not use upper/lowerBound from LowerOp because it's about the (signed)
             * addition not the comparison.
             */
            if (mirrored) {
                if (aStamp.contains(0)) {
                    // a may be zero
                    return aStamp.unrestricted();
                }
                return forInteger(bits, min(max - aStamp.lowerBound() + 1, max - aStamp.upperBound() + 1, bits), min(max, upperBound(xStamp)));
            } else {
                long aLower = aStamp.lowerBound();
                long aUpper = aStamp.upperBound();
                if (strict) {
                    if (aLower == 0) {
                        aLower = 1;
                    }
                    if (aUpper == 0) {
                        aUpper = -1;
                    }
                    if (aLower > aUpper) {
                        // impossible
                        return aStamp.empty();
                    }
                }
                if (aLower < 0 && aUpper > 0) {
                    // a may be zero
                    return aStamp.unrestricted();
                }
                return forInteger(bits, min, max(max - aLower, max - aUpper, bits));
            }
        }
    }

    @Override
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (other instanceof IntegerLowerThanNode) {
            IntegerLowerThanNode otherLowerThan = (IntegerLowerThanNode) other;
            if (getOp() == otherLowerThan.getOp() && getX() == otherLowerThan.getX()) {
                // x < A => x < B?
                LogicNode compareYs = getOp().create(getY(), otherLowerThan.getY(), NodeView.DEFAULT);
                if (!thisNegated && compareYs.isTautology()) {
                    // A < B, therefore x < A => x < B
                    return TriState.TRUE;
                } else if (thisNegated && compareYs.isContradiction()) {
                    // !(A < B) [== A >= B], therefore !(x < A) [== x >= A] => !(x < B) [== x >= B]
                    return TriState.FALSE;
                }
            }
        }
        return super.implies(thisNegated, other);
    }
}
