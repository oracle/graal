/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;
import static jdk.graal.compiler.nodes.calc.CompareNode.createCompareNode;

import java.util.function.Function;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool.RoundingMode;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * The {@code ConditionalNode} class represents a comparison that yields one of two (eagerly
 * evaluated) values.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_2)
public final class ConditionalNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    public static final NodeClass<ConditionalNode> TYPE = NodeClass.create(ConditionalNode.class);
    @Input(InputType.Condition) LogicNode condition;
    @Input(InputType.Value) ValueNode trueValue;
    @Input(InputType.Value) ValueNode falseValue;

    public LogicNode condition() {
        return condition;
    }

    public ConditionalNode(LogicNode condition) {
        this(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()));
    }

    public ConditionalNode(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        super(TYPE, combineStamps(condition, trueValue, falseValue, NodeView.DEFAULT));
        assert trueValue.stamp(NodeView.DEFAULT).isCompatible(falseValue.stamp(NodeView.DEFAULT));
        this.condition = condition;
        this.trueValue = trueValue;
        this.falseValue = falseValue;
    }

    public static ValueNode create(LogicNode condition, NodeView view) {
        return create(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()), view);
    }

    public static ValueNode create(LogicNode condition, ValueNode trueValue, ValueNode falseValue, NodeView view) {
        ValueNode synonym = findSynonym(condition, trueValue, falseValue, view);
        if (synonym != null) {
            return synonym;
        }
        ValueNode result = tryCanonicalizeConditional(condition, trueValue, falseValue, combineStamps(condition, trueValue, falseValue, view), view, null);
        if (result != null) {
            return result;
        }
        return new ConditionalNode(condition, trueValue, falseValue);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(combineStamps(condition, trueValue, falseValue, NodeView.DEFAULT));
    }

    private static Stamp combineStamps(LogicNode condition, ValueNode trueValue, ValueNode falseValue, NodeView view) {
        ValueNode asMinMax = MinMaxNode.fromConditional(condition, trueValue, falseValue, view);
        if (asMinMax != null) {
            return asMinMax.stamp(view);
        }
        return trueValue.stamp(view).meet(falseValue.stamp(view));
    }

    public ValueNode trueValue() {
        return trueValue;
    }

    public ValueNode falseValue() {
        return falseValue;
    }

    /**
     * Determines whether an integer operation can fold at least one of this conditional's values.
     *
     * <pre>{@code
     * -(condition ? 4 : value)  ->  condition ? -4 : -value
     * (condition ? 4 : value) + 1  ->  condition ? 5 : value + 1
     * }</pre>
     *
     * The operation using this conditional is responsible for ensuring that its other inputs are
     * constants and that it is safe to distribute over the conditional.
     */
    boolean hasConstantIntegerValue(NodeView view) {
        return stamp(view) instanceof IntegerStamp && (trueValue.isConstant() || falseValue.isConstant());
    }

    /**
     * Determines whether distributing an operation is profitable. A one-constant conditional is
     * restricted to a single use because distributing several uses duplicates its comparison and
     * conditional select in generated code. When both values are constant, distribution removes
     * each operation completely and is allowed for multiple uses.
     */
    boolean isFoldableOperation(NodeView view) {
        return hasConstantIntegerValue(view) && (!hasMoreThanOneUsage() || (trueValue.isConstant() && falseValue.isConstant()));
    }

    /**
     * Distributes an operation over a conditional, folding constant values and recreating the
     * operation for nonconstant values.
     *
     * <pre>{@code
     * operation(condition ? constant : value)
     *     -> condition ? foldConstant(constant) : recreateOperation(value)
     * }</pre>
     *
     * @return the distributed conditional, or {@code null} if either value cannot be transformed
     */
    static ValueNode foldOperation(ConditionalNode conditional, NodeView view, Function<ValueNode, ValueNode> foldConstant,
                    Function<ValueNode, ValueNode> recreateOperation) {
        ValueNode newTrueValue = transformValue(conditional.trueValue(), foldConstant, recreateOperation);
        ValueNode newFalseValue = transformValue(conditional.falseValue(), foldConstant, recreateOperation);
        if (newTrueValue == null || newFalseValue == null) {
            return null;
        }
        return create(conditional.condition(), newTrueValue, newFalseValue, view);
    }

    private static ValueNode transformValue(ValueNode value, Function<ValueNode, ValueNode> foldConstant, Function<ValueNode, ValueNode> recreateOperation) {
        return value.isConstant() ? foldConstant.apply(value) : recreateOperation.apply(value);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        NodeView view = NodeView.from(tool);
        ValueNode synonym = findSynonym(condition, trueValue(), falseValue(), view);
        if (synonym != null) {
            return synonym;
        }

        ValueNode result = tryCanonicalizeConditional(condition, trueValue(), falseValue(), stamp, view, tool);
        if (result != null) {
            return result;
        }

        if (tool != null && stamp instanceof IntegerStamp integerStamp) {
            Integer smallestCompareWidth = tool.smallestCompareWidth();
            if (smallestCompareWidth != null && integerStamp.getBits() >= smallestCompareWidth) {
                ValueNode minMaxSynonym = MinMaxNode.fromConditional(condition, trueValue, falseValue, view);
                if (minMaxSynonym != null) {
                    return minMaxSynonym;
                }
            }
        }

        return this;
    }

    /**
     * Attempts to replace a conditional selection with a simpler value. Unlike
     * {@link #create(LogicNode, ValueNode, ValueNode, NodeView)}, this method returns
     * {@code null} instead of creating a new {@link ConditionalNode} when no canonicalization
     * applies. This allows control-flow canonicalization to distinguish a simplification that
     * eliminates the selection from one that merely represents it as a conditional value.
     * <p>
     * A returned replacement may not yet belong to a graph. The caller is responsible for adding
     * it when necessary.
     *
     * @param tool the canonicalizer context, or {@code null} when context-dependent
     *            canonicalizations are unavailable
     * @return a replacement value, possibly detached from a graph, or {@code null}
     */
    public static ValueNode tryCanonicalizeConditional(LogicNode condition, ValueNode trueValue, ValueNode falseValue, Stamp stamp, NodeView view, CanonicalizerTool tool) {
        if (trueValue == falseValue) {
            return trueValue;
        }

        ValueNode result = canonicalizeIdentityComparison(condition, trueValue, falseValue);
        if (result != null) {
            return result;
        }

        if (trueValue.stamp(view) instanceof IntegerStamp) {
            result = canonicalizeRedundantIntegerConditional(condition, trueValue, falseValue, view);
            if (result != null) {
                return result;
            }
            result = canonicalizeBooleanMaterialization(condition, trueValue, falseValue, stamp, view);
            if (result != null) {
                return result;
            }
            result = canonicalizeConditionalAdd(condition, trueValue, falseValue);
            if (result != null) {
                return result;
            }
        }

        result = canonicalizeRoundToTruncate(condition, trueValue, falseValue, tool);
        if (result != null) {
            return result;
        }

        return canonicalizeNullSelection(condition, trueValue, falseValue);
    }

    /**
     * Removes a selection between the values compared by an identity comparison.
     *
     * <pre>{@code
     * x == y ? x : y  ->  y
     * x == y ? y : x  ->  x
     * }</pre>
     */
    private static ValueNode canonicalizeIdentityComparison(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        if (condition instanceof CompareNode compare && compare.isIdentityComparison() &&
                        ((compare.getX() == trueValue && compare.getY() == falseValue) || (compare.getX() == falseValue && compare.getY() == trueValue))) {
            // Optimize the pattern (x == y) ? x : y.
            return falseValue;
        }
        return null;
    }

    /**
     * Removes an integer conditional when stamps prove which selected value is smaller.
     *
     * <pre>{@code
     * x < y ? x : y  ->  x  when x <= y
     * x < y ? y : x  ->  y  when x <= y
     * }</pre>
     */
    private static ValueNode canonicalizeRedundantIntegerConditional(LogicNode condition, ValueNode trueValue, ValueNode falseValue, NodeView view) {
        if (condition instanceof IntegerLessThanNode lessThan) {
            IntegerStamp falseValueStamp = (IntegerStamp) falseValue.stamp(view);
            IntegerStamp trueValueStamp = (IntegerStamp) trueValue.stamp(view);
            if (lessThan.getX() == trueValue && lessThan.getY() == falseValue && trueValueStamp.upperBound() <= falseValueStamp.lowerBound()) {
                // Return x for x < y ? x : y when x <= y.
                return trueValue;
            } else if (lessThan.getX() == falseValue && lessThan.getY() == trueValue && falseValueStamp.upperBound() <= trueValueStamp.lowerBound()) {
                // Return y for x < y ? y : x when x <= y.
                return trueValue;
            }
        }
        return null;
    }

    /**
     * Replaces materialization of an integer value known to be zero or one with integer logic.
     *
     * <pre>{@code
     * x == 0 ? 0 : 1  ->  x
     * x == 0 ? 1 : 0  ->  x ^ 1
     * x == 1 ? 1 : 0  ->  x
     * x == 1 ? 0 : 1  ->  x ^ 1
     * (x & 1) == 0 ? 0 : 1  ->  x & 1
     * }</pre>
     */
    private static ValueNode canonicalizeBooleanMaterialization(LogicNode condition, ValueNode trueValue, ValueNode falseValue, Stamp stamp, NodeView view) {
        if (!trueValue.isConstant() || !falseValue.isConstant()) {
            return null;
        }
        long constTrueValue = trueValue.asJavaConstant().asLong();
        long constFalseValue = falseValue.asJavaConstant().asLong();
        if (condition instanceof IntegerEqualsNode equals && equals.getY().isConstant() && equals.getX().stamp(view) instanceof IntegerStamp equalsXStamp &&
                        equalsXStamp.mayBeSet() == 1) {
            long equalsY = equals.getY().asJavaConstant().asLong();
            if (equalsY == 0) {
                if (constTrueValue == 0 && constFalseValue == 1) {
                    // Return x for x == 0 ? 0 : 1.
                    return IntegerConvertNode.convertUnsigned(equals.getX(), stamp, view);
                } else if (constTrueValue == 1 && constFalseValue == 0) {
                    // Negate a boolean value via xor.
                    return IntegerConvertNode.convertUnsigned(XorNode.create(equals.getX(), ConstantNode.forIntegerStamp(equals.getX().stamp(view), 1), view), stamp, view);
                }
            } else if (equalsY == 1) {
                if (constTrueValue == 1 && constFalseValue == 0) {
                    // Return x for x == 1 ? 1 : 0.
                    return IntegerConvertNode.convertUnsigned(equals.getX(), stamp, view);
                } else if (constTrueValue == 0 && constFalseValue == 1) {
                    // Negate a boolean value via xor.
                    return IntegerConvertNode.convertUnsigned(XorNode.create(equals.getX(), ConstantNode.forIntegerStamp(equals.getX().stamp(view), 1), view), stamp, view);
                }
            }
        } else if (condition instanceof IntegerTestNode integerTest && integerTest.getY().isConstant() && integerTest.getX().stamp(view) instanceof IntegerStamp) {
            // Replace (value & 1) == 0 ? 0 : 1 with an AndNode.
            long testY = integerTest.getY().asJavaConstant().asLong();
            if (testY == 1 && constTrueValue == 0 && constFalseValue == 1) {
                return IntegerConvertNode.convertUnsigned(AndNode.create(integerTest.getX(), integerTest.getY(), view), stamp, view);
            }
        }
        return null;
    }

    /**
     * Replaces a conditional addition with bitwise arithmetic that avoids the comparison.
     *
     * <pre>{@code
     * x < 0 ? x + y : x  ->  x + (y & (x >> (bits - 1)))
     * }</pre>
     */
    private static ValueNode canonicalizeConditionalAdd(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        if (condition instanceof IntegerLessThanNode lessThan && lessThan.getY().isDefaultConstant() &&
                        falseValue == lessThan.getX() && trueValue instanceof AddNode add && add.getX() == falseValue) {
            int bits = ((IntegerStamp) trueValue.stamp(NodeView.DEFAULT)).getBits();
            ValueNode shift = new RightShiftNode(lessThan.getX(), ConstantNode.forIntegerBits(32, bits - 1));
            ValueNode and = new AndNode(shift, add.getY());
            return new AddNode(add.getX(), and);
        }
        return null;
    }

    /**
     * Replaces sign-dependent floor or ceiling with truncation toward zero.
     *
     * <pre>{@code
     * x < 0.0 ? ceil(x) : floor(x)  ->  truncate(x)
     * 0.0 < x ? floor(x) : ceil(x)  ->  truncate(x)
     * }</pre>
     */
    private static ValueNode canonicalizeRoundToTruncate(LogicNode condition, ValueNode trueValue, ValueNode falseValue, CanonicalizerTool tool) {
        if (tool == null || !RoundNode.isSupported(tool.getLowerer().getTarget().arch) ||
                        !(condition instanceof FloatLessThanNode lessThan) ||
                        !(trueValue instanceof RoundNode trueRound) ||
                        !(falseValue instanceof RoundNode falseRound) ||
                        trueRound.getValue() != falseRound.getValue()) {
            return null;
        }

        ValueNode roundInput = trueRound.getValue();
        // Account for the fact that x might be compared as a float but converted to double
        // for rounding: `x < 0.0f ? Math.ceil((double) x) : Math.floor((double) x)`.
        ValueNode originalRoundInput = roundInput;
        if (roundInput instanceof FloatConvertNode convert && convert.op == FloatConvert.F2D) {
            originalRoundInput = convert.getValue();
        }

        boolean isTruncate = lessThan.getX() == originalRoundInput && lessThan.getY().isDefaultConstant() &&
                        trueRound.mode() == RoundingMode.UP && falseRound.mode() == RoundingMode.DOWN;
        if (!isTruncate) {
            // Also recognize 0.0 < x ? floor(x) : ceil(x).
            isTruncate = lessThan.getX().isDefaultConstant() && lessThan.getY() == originalRoundInput &&
                            trueRound.mode() == RoundingMode.DOWN && falseRound.mode() == RoundingMode.UP;
        }
        return isTruncate ? new RoundNode(roundInput, RoundingMode.TRUNCATE) : null;
    }

    private static ValueNode canonicalizeNullSelection(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        if (condition instanceof IsNullNode isNull && trueValue.isJavaConstant() && trueValue.asJavaConstant().isDefaultForKind() &&
                        falseValue == isNull.getValue()) {
            return falseValue;
        }
        return null;
    }

    private static ValueNode findSynonym(ValueNode condition, ValueNode trueValue, ValueNode falseValue, NodeView view) {
        if (condition instanceof LogicNegationNode) {
            LogicNegationNode negated = (LogicNegationNode) condition;
            return ConditionalNode.create(negated.getValue(), falseValue, trueValue, view);
        }
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            if (c.getValue()) {
                return trueValue;
            } else {
                return falseValue;
            }
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.emitConditional(this);
    }

    public ConditionalNode(StructuredGraph graph, CanonicalCondition condition, ValueNode x, ValueNode y) {
        this(createCompareNode(graph, condition, x, y, null, NodeView.DEFAULT));
    }
}
