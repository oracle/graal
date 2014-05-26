/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.calc;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;

/* TODO (thomaswue/gdub) For high-level optimization purpose the compare node should be a boolean *value* (it is currently only a helper node)
 * But in the back-end the comparison should not always be materialized (for example in x86 the comparison result will not be in a register but in a flag)
 *
 * Compare should probably be made a value (so that it can be canonicalized for example) and in later stages some Compare usage should be transformed
 * into variants that do not materialize the value (CompareIf, CompareGuard...)
 */
public abstract class CompareNode extends BinaryOpLogicNode {

    /**
     * Constructs a new Compare instruction.
     *
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     */
    public CompareNode(ValueNode x, ValueNode y) {
        super(x, y);
    }

    /**
     * Gets the condition (comparison operation) for this instruction.
     *
     * @return the condition
     */
    public abstract Condition condition();

    /**
     * Checks whether unordered inputs mean true or false (only applies to float operations).
     *
     * @return {@code true} if unordered inputs produce true
     */
    public abstract boolean unorderedIsTrue();

    private LogicNode optimizeConditional(Constant constant, ConditionalNode conditionalNode, ConstantReflectionProvider constantReflection, Condition cond) {
        Constant trueConstant = conditionalNode.trueValue().asConstant();
        Constant falseConstant = conditionalNode.falseValue().asConstant();

        if (falseConstant != null && trueConstant != null && constantReflection != null) {
            boolean trueResult = cond.foldCondition(trueConstant, constant, constantReflection, unorderedIsTrue());
            boolean falseResult = cond.foldCondition(falseConstant, constant, constantReflection, unorderedIsTrue());

            if (trueResult == falseResult) {
                return LogicConstantNode.forBoolean(trueResult, graph());
            } else {
                if (trueResult) {
                    assert falseResult == false;
                    return conditionalNode.condition();
                } else {
                    assert falseResult == true;
                    return graph().unique(new LogicNegationNode(conditionalNode.condition()));

                }
            }
        }
        return this;
    }

    protected LogicNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        throw new GraalInternalError("NormalizeCompareNode connected to %s (%s %s %s)", this, constant, normalizeNode, mirrored);
    }

    @Override
    public TriState evaluate(ConstantReflectionProvider constantReflection, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && forY.isConstant()) {
            return TriState.get(condition().foldCondition(forX.asConstant(), forY.asConstant(), constantReflection, unorderedIsTrue()));
        }
        return TriState.UNKNOWN;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node result = super.canonical(tool);
        if (result != this) {
            return result;
        }
        if (x().isConstant()) {
            if (y() instanceof ConditionalNode) {
                return optimizeConditional(x().asConstant(), (ConditionalNode) y(), tool.getConstantReflection(), condition().mirror());
            } else if (y() instanceof NormalizeCompareNode) {
                return optimizeNormalizeCmp(x().asConstant(), (NormalizeCompareNode) y(), true);
            }
        } else if (y().isConstant()) {
            if (x() instanceof ConditionalNode) {
                return optimizeConditional(y().asConstant(), (ConditionalNode) x(), tool.getConstantReflection(), condition());
            } else if (x() instanceof NormalizeCompareNode) {
                return optimizeNormalizeCmp(y().asConstant(), (NormalizeCompareNode) x(), false);
            }
        }
        if (x() instanceof ConvertNode && y() instanceof ConvertNode) {
            ConvertNode convertX = (ConvertNode) x();
            ConvertNode convertY = (ConvertNode) y();
            if (convertX.preservesOrder(condition()) && convertY.preservesOrder(condition()) && convertX.getInput().stamp().isCompatible(convertY.getInput().stamp())) {
                setX(convertX.getInput());
                setY(convertY.getInput());
            }
        } else if (x() instanceof ConvertNode && y().isConstant()) {
            ConvertNode convertX = (ConvertNode) x();
            ConstantNode newY = canonicalConvertConstant(tool, convertX, y().asConstant());
            if (newY != null) {
                setX(convertX.getInput());
                setY(newY);
            }
        } else if (y() instanceof ConvertNode && x().isConstant()) {
            ConvertNode convertY = (ConvertNode) y();
            ConstantNode newX = canonicalConvertConstant(tool, convertY, x().asConstant());
            if (newX != null) {
                setX(newX);
                setY(convertY.getInput());
            }
        }
        return this;
    }

    private ConstantNode canonicalConvertConstant(CanonicalizerTool tool, ConvertNode convert, Constant constant) {
        if (convert.preservesOrder(condition())) {
            Constant reverseConverted = convert.reverse(constant);
            if (convert.convert(reverseConverted).equals(constant)) {
                return ConstantNode.forConstant(convert.getInput().stamp(), reverseConverted, tool.getMetaAccess(), convert.graph());
            }
        }
        return null;
    }

    public static CompareNode createCompareNode(StructuredGraph graph, Condition condition, ValueNode x, ValueNode y) {
        assert x.getKind() == y.getKind();
        assert condition.isCanonical() : "condition is not canonical: " + condition;
        assert !x.getKind().isNumericFloat();

        CompareNode comparison;
        if (condition == Condition.EQ) {
            if (x.getKind() == Kind.Object) {
                comparison = new ObjectEqualsNode(x, y);
            } else {
                assert x.getKind().isNumericInteger();
                comparison = new IntegerEqualsNode(x, y);
            }
        } else if (condition == Condition.LT) {
            assert x.getKind().isNumericInteger();
            comparison = new IntegerLessThanNode(x, y);
        } else {
            assert condition == Condition.BT;
            assert x.getKind().isNumericInteger();
            comparison = new IntegerBelowThanNode(x, y);
        }

        return graph.unique(comparison);
    }
}
