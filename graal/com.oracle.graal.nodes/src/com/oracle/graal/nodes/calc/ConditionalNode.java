/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.nodes.calc.CompareNode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code ConditionalNode} class represents a comparison that yields one of two values. Note
 * that these nodes are not built directly from the bytecode but are introduced by canonicalization.
 */
public final class ConditionalNode extends BinaryNode implements Canonicalizable, LIRLowerable {

    @Input private LogicNode condition;

    public LogicNode condition() {
        return condition;
    }

    public ConditionalNode(LogicNode condition) {
        this(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()));
    }

    public ConditionalNode(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        super(trueValue.stamp().meet(falseValue.stamp()), trueValue, falseValue);
        assert trueValue.stamp().isCompatible(falseValue.stamp());
        this.condition = condition;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(x().stamp().meet(y().stamp()));
    }

    public ValueNode trueValue() {
        return x();
    }

    public ValueNode falseValue() {
        return y();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (condition instanceof LogicNegationNode) {
            LogicNegationNode negated = (LogicNegationNode) condition;
            return graph().unique(new ConditionalNode(negated.getInput(), falseValue(), trueValue()));
        }

        // this optimizes the case where a value that can only be 0 or 1 is materialized to 0 or 1
        if (x().isConstant() && y().isConstant() && condition instanceof IntegerEqualsNode) {
            IntegerEqualsNode equals = (IntegerEqualsNode) condition;
            if (equals.y().isConstant() && equals.y().asConstant().equals(Constant.INT_0) && equals.x().stamp() instanceof IntegerStamp) {
                IntegerStamp equalsXStamp = (IntegerStamp) equals.x().stamp();
                if (equalsXStamp.upMask() == 1) {
                    if (x().asConstant().equals(Constant.INT_0) && y().asConstant().equals(Constant.INT_1)) {
                        return equals.x();
                    }
                }
            }
        }
        if (condition instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) condition;
            if (c.getValue()) {
                return trueValue();
            } else {
                return falseValue();
            }
        }
        if (condition instanceof CompareNode && ((CompareNode) condition).condition() == Condition.EQ) {
            // optimize the pattern (x == y) ? x : y
            CompareNode compare = (CompareNode) condition;
            if ((compare.x() == trueValue() && compare.y() == falseValue()) || (compare.x() == falseValue() && compare.y() == trueValue())) {
                return falseValue();
            }
        }
        if (trueValue() == falseValue()) {
            return trueValue();
        }

        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.emitConditional(this);
    }

    private ConditionalNode(@InjectedNodeParameter StructuredGraph graph, Condition condition, ValueNode x, ValueNode y) {
        this(createCompareNode(graph, condition, x, y));
    }

    private ConditionalNode(ValueNode type, ValueNode object) {
        this(type.graph().unique(new InstanceOfDynamicNode(type, object)));
    }

    @NodeIntrinsic
    public static native boolean materializeCondition(@ConstantNodeParameter Condition condition, int x, int y);

    @NodeIntrinsic
    public static native boolean materializeCondition(@ConstantNodeParameter Condition condition, long x, long y);

    @NodeIntrinsic
    public static boolean materializeIsInstance(Class mirror, Object object) {
        return mirror.isInstance(object);
    }
}
