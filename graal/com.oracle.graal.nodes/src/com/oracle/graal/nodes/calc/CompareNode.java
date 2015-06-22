/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;

/* TODO (thomaswue/gdub) For high-level optimization purpose the compare node should be a boolean *value* (it is currently only a helper node)
 * But in the back-end the comparison should not always be materialized (for example in x86 the comparison result will not be in a register but in a flag)
 *
 * Compare should probably be made a value (so that it can be canonicalized for example) and in later stages some Compare usage should be transformed
 * into variants that do not materialize the value (CompareIf, CompareGuard...)
 */
@NodeInfo
public abstract class CompareNode extends BinaryOpLogicNode implements Canonicalizable.Binary<ValueNode> {

    public static final NodeClass<CompareNode> TYPE = NodeClass.create(CompareNode.class);
    protected final Condition condition;
    protected final boolean unorderedIsTrue;

    /**
     * Constructs a new Compare instruction.
     *
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     */
    protected CompareNode(NodeClass<? extends CompareNode> c, Condition condition, boolean unorderedIsTrue, ValueNode x, ValueNode y) {
        super(c, x, y);
        this.condition = condition;
        this.unorderedIsTrue = unorderedIsTrue;
    }

    /**
     * Gets the condition (comparison operation) for this instruction.
     *
     * @return the condition
     */
    public final Condition condition() {
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

    private ValueNode optimizeConditional(Constant constant, ConditionalNode conditionalNode, ConstantReflectionProvider constantReflection, Condition cond) {
        Constant trueConstant = conditionalNode.trueValue().asConstant();
        Constant falseConstant = conditionalNode.falseValue().asConstant();

        if (falseConstant != null && trueConstant != null && constantReflection != null) {
            boolean trueResult = cond.foldCondition(trueConstant, constant, constantReflection, unorderedIsTrue());
            boolean falseResult = cond.foldCondition(falseConstant, constant, constantReflection, unorderedIsTrue());

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
        return this;
    }

    protected ValueNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        throw new JVMCIError("NormalizeCompareNode connected to %s (%s %s %s)", this, constant, normalizeNode, mirrored);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        LogicNode constantCondition = tryConstantFold(condition(), forX, forY, constantReflection, unorderedIsTrue());
        if (constantCondition != null) {
            return constantCondition;
        }
        ValueNode result;
        if (forX.isConstant()) {
            if ((result = canonicalizeSymmetricConstant(tool, forX.asConstant(), forY, true)) != this) {
                return result;
            }
        } else if (forY.isConstant()) {
            if ((result = canonicalizeSymmetricConstant(tool, forY.asConstant(), forX, false)) != this) {
                return result;
            }
        } else if (forX instanceof ConvertNode && forY instanceof ConvertNode) {
            ConvertNode convertX = (ConvertNode) forX;
            ConvertNode convertY = (ConvertNode) forY;
            if (convertX.preservesOrder(condition()) && convertY.preservesOrder(condition()) && convertX.getValue().stamp().isCompatible(convertY.getValue().stamp())) {
                return duplicateModified(convertX.getValue(), convertY.getValue());
            }
        }
        return this;
    }

    public static LogicNode tryConstantFold(Condition condition, ValueNode forX, ValueNode forY, ConstantReflectionProvider constantReflection, boolean unorderedIsTrue) {
        if (forX.isConstant() && forY.isConstant() && constantReflection != null) {
            return LogicConstantNode.forBoolean(condition.foldCondition(forX.asConstant(), forY.asConstant(), constantReflection, unorderedIsTrue));
        }
        return null;
    }

    protected abstract LogicNode duplicateModified(ValueNode newX, ValueNode newY);

    protected ValueNode canonicalizeSymmetricConstant(CanonicalizerTool tool, Constant constant, ValueNode nonConstant, boolean mirrored) {
        if (nonConstant instanceof ConditionalNode) {
            return optimizeConditional(constant, (ConditionalNode) nonConstant, tool.getConstantReflection(), mirrored ? condition().mirror() : condition());
        } else if (nonConstant instanceof NormalizeCompareNode) {
            return optimizeNormalizeCmp(constant, (NormalizeCompareNode) nonConstant, mirrored);
        } else if (nonConstant instanceof ConvertNode) {
            ConvertNode convert = (ConvertNode) nonConstant;
            ConstantNode newConstant = canonicalConvertConstant(tool, convert, constant);
            if (newConstant != null) {
                if (mirrored) {
                    return duplicateModified(newConstant, convert.getValue());
                } else {
                    return duplicateModified(convert.getValue(), newConstant);
                }
            }
        }
        return this;
    }

    private ConstantNode canonicalConvertConstant(CanonicalizerTool tool, ConvertNode convert, Constant constant) {
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        if (convert.preservesOrder(condition(), constant, constantReflection)) {
            Constant reverseConverted = convert.reverse(constant, constantReflection);
            if (convert.convert(reverseConverted, constantReflection).equals(constant)) {
                return ConstantNode.forConstant(convert.getValue().stamp(), reverseConverted, tool.getMetaAccess());
            }
        }
        return null;
    }

    public static LogicNode createCompareNode(StructuredGraph graph, Condition condition, ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = createCompareNode(condition, x, y, constantReflection);
        return (result.graph() == null ? graph.unique(result) : result);
    }

    public static LogicNode createCompareNode(Condition condition, ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        assert x.getKind() == y.getKind();
        assert condition.isCanonical() : "condition is not canonical: " + condition;
        assert !x.getKind().isNumericFloat();

        LogicNode comparison;
        if (condition == Condition.EQ) {
            if (x.stamp() instanceof AbstractObjectStamp) {
                comparison = ObjectEqualsNode.create(x, y, constantReflection);
            } else if (x.stamp() instanceof AbstractPointerStamp) {
                comparison = PointerEqualsNode.create(x, y);
            } else {
                assert x.getKind().isNumericInteger();
                comparison = IntegerEqualsNode.create(x, y, constantReflection);
            }
        } else if (condition == Condition.LT) {
            assert x.getKind().isNumericInteger();
            comparison = IntegerLessThanNode.create(x, y, constantReflection);
        } else {
            assert condition == Condition.BT;
            assert x.getKind().isNumericInteger();
            comparison = IntegerBelowNode.create(x, y, constantReflection);
        }

        return comparison;
    }
}
