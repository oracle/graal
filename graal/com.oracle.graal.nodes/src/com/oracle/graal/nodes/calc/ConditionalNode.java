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
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code ConditionalNode} class represents a comparison that yields one of two values. Note
 * that these nodes are not built directly from the bytecode but are introduced by canonicalization.
 */
public final class ConditionalNode extends FloatingNode implements Canonicalizable, LIRLowerable {

    @Input(InputType.Condition) private LogicNode condition;
    @Input private ValueNode trueValue;
    @Input private ValueNode falseValue;

    public LogicNode condition() {
        return condition;
    }

    public ConditionalNode(LogicNode condition) {
        this(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()));
    }

    public ConditionalNode(LogicNode condition, ValueNode trueValue, ValueNode falseValue) {
        super(trueValue.stamp().meet(falseValue.stamp()));
        assert trueValue.stamp().isCompatible(falseValue.stamp());
        this.condition = condition;
        this.trueValue = trueValue;
        this.falseValue = falseValue;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(trueValue.stamp().meet(falseValue.stamp()));
    }

    public ValueNode trueValue() {
        return trueValue;
    }

    public ValueNode falseValue() {
        return falseValue;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (condition instanceof LogicNegationNode) {
            LogicNegationNode negated = (LogicNegationNode) condition;
            return new ConditionalNode(negated.getValue(), falseValue(), trueValue());
        }

        // this optimizes the case where a value that can only be 0 or 1 is materialized to 0 or 1
        if (trueValue().isConstant() && falseValue().isConstant() && condition instanceof IntegerEqualsNode) {
            IntegerEqualsNode equals = (IntegerEqualsNode) condition;
            if (equals.getY().isConstant() && equals.getY().asConstant().equals(Constant.INT_0) && equals.getX().stamp() instanceof IntegerStamp) {
                IntegerStamp equalsXStamp = (IntegerStamp) equals.getX().stamp();
                if (equalsXStamp.upMask() == 1) {
                    if (trueValue().asConstant().equals(Constant.INT_0) && falseValue().asConstant().equals(Constant.INT_1)) {
                        return IntegerConvertNode.convertUnsigned(equals.getX(), stamp());
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
            if ((compare.getX() == trueValue() && compare.getY() == falseValue()) || (compare.getX() == falseValue() && compare.getY() == trueValue())) {
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
    public static boolean materializeIsInstance(Class<?> mirror, Object object) {
        return mirror.isInstance(object);
    }
}
