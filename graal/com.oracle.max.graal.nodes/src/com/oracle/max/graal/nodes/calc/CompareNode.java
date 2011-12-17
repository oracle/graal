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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/* TODO(tw/gd) For high-level optimization purpose the compare node should be a boolean *value* (it is currently only a helper node)
 * But in the back-end the comparison should not always be materialized (for example in x86 the comparison result will not be in a register but in a flag)
 *
 * Compare should probably be made a value (so that it can be canonicalized for example) and in later stages some Compare usage should be transformed
 * into variants that do not materialize the value (CompareIf, CompareGuard...)
 */
public final class CompareNode extends BooleanNode implements Canonicalizable, LIRLowerable {

    @Input private ValueNode x;
    @Input private ValueNode y;

    @Data private final Condition condition;
    @Data private final boolean unorderedIsTrue;

    public ValueNode x() {
        return x;
    }

    public ValueNode y() {
        return y;
    }

    /**
     * Constructs a new Compare instruction.
     *
     * @param x the instruction producing the first input to the instruction
     * @param condition the condition (comparison operation)
     * @param y the instruction that produces the second input to this instruction
     * @param graph
     */
    public CompareNode(ValueNode x, Condition condition, ValueNode y) {
        this(x, condition, false, y);
    }

    /**
     * Constructs a new Compare instruction.
     *
     * @param x the instruction producing the first input to the instruction
     * @param condition the condition (comparison operation)
     * @param y the instruction that produces the second input to this instruction
     * @param graph
     */
    public CompareNode(ValueNode x, Condition condition, boolean unorderedIsTrue, ValueNode y) {
        super(StampFactory.illegal());
        assert (x == null && y == null) || x.kind() == y.kind();
        this.condition = condition;
        this.unorderedIsTrue = unorderedIsTrue;
        this.x = x;
        this.y = y;
    }

    /**
     * Gets the condition (comparison operation) for this instruction.
     *
     * @return the condition
     */
    public Condition condition() {
        return condition;
    }

    /**
     * Checks whether unordered inputs mean true or false.
     *
     * @return {@code true} if unordered inputs produce true
     */
    public boolean unorderedIsTrue() {
        return unorderedIsTrue;
    }

    @Override
    public BooleanNode negate() {
        return graph().unique(new CompareNode(x(), condition.negate(), !unorderedIsTrue, y()));
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(Verbosity.Name) + " " + condition.operator;
        } else {
            return super.toString(verbosity);
        }
    }

    private Node optimizeMaterialize(CiConstant constant, MaterializeNode materializeNode, RiRuntime runtime) {
        CiConstant trueConstant = materializeNode.trueValue().asConstant();
        CiConstant falseConstant = materializeNode.falseValue().asConstant();

        if (falseConstant != null && trueConstant != null) {
            Boolean trueResult = condition().foldCondition(trueConstant, constant, runtime, unorderedIsTrue());
            Boolean falseResult = condition().foldCondition(falseConstant, constant, runtime, unorderedIsTrue());

            if (trueResult != null && falseResult != null) {
                boolean trueUnboxedResult = trueResult;
                boolean falseUnboxedResult = falseResult;
                if (trueUnboxedResult == falseUnboxedResult) {
                    return ConstantNode.forBoolean(trueUnboxedResult, graph());
                } else {
                    if (trueUnboxedResult) {
                        assert falseUnboxedResult == false;
                        return materializeNode.condition();
                    } else {
                        assert falseUnboxedResult == true;
                        return materializeNode.condition().negate();

                    }
                }
            }
        }
        return this;
    }

    private Node optimizeNormalizeCmp(CiConstant constant, NormalizeCompareNode normalizeNode) {
        if (constant.kind == CiKind.Int && constant.asInt() == 0) {
            Condition condition = condition();
            if (normalizeNode.x().kind().isFloatOrDouble()) {
                switch (condition) {
                    case LT: condition = Condition.BT; break;
                    case LE: condition = Condition.BE; break;
                    case GE: condition = Condition.AE; break;
                    case GT: condition = Condition.AT; break;
                }
            }
            if (normalizeNode == y()) {
                condition = condition.mirror();
            }
            boolean isLess = condition == Condition.LE || condition == Condition.LT || condition == Condition.BE || condition == Condition.BT;
            boolean canonUnorderedIsTrue = condition != Condition.EQ && (condition == Condition.NE || !(isLess ^ normalizeNode.isUnorderedLess));
            CompareNode result = graph().unique(new CompareNode(normalizeNode.x(), condition, canonUnorderedIsTrue, normalizeNode.y()));
            return result;
        }
        return this;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (x().isConstant() && !y().isConstant()) { // move constants to the left (y)
            return graph().unique(new CompareNode(y(), condition.mirror(), unorderedIsTrue(), x()));
        } else if (x().isConstant() && y().isConstant()) {
            CiConstant constX = x().asConstant();
            CiConstant constY = y().asConstant();
            Boolean result = condition().foldCondition(constX, constY, tool.runtime(), unorderedIsTrue());
            if (result != null) {
                return ConstantNode.forBoolean(result, graph());
            }
        }

        if (y().isConstant()) {
            if (x() instanceof MaterializeNode) {
                return optimizeMaterialize(y().asConstant(), (MaterializeNode) x(), tool.runtime());
            } else if (x() instanceof NormalizeCompareNode) {
                return optimizeNormalizeCmp(y().asConstant(), (NormalizeCompareNode) x());
            }
        }

        if (x() == y() && x().kind() != CiKind.Float && x().kind() != CiKind.Double) {
            return ConstantNode.forBoolean(condition().check(1, 1), graph());
        }
        if ((condition == Condition.NE || condition == Condition.EQ) && x().kind() == CiKind.Object) {
            ValueNode object = null;
            if (x().isNullConstant()) {
                object = y();
            } else if (y().isNullConstant()) {
                object = x();
            }
            if (object != null) {
                return graph().unique(new NullCheckNode(object, condition == Condition.EQ));
            } else {
                Stamp xStamp = x.stamp();
                Stamp yStamp = y.stamp();
                if (xStamp.alwaysDistinct(yStamp)) {
                    return ConstantNode.forBoolean(condition == Condition.NE, graph());
                }
            }
        }
        return this;
    }
}
