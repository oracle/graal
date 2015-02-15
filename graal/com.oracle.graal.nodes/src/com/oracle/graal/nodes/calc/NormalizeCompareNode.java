/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Returns -1, 0, or 1 if either x &lt; y, x == y, or x &gt; y. If the comparison is undecided (one
 * of the inputs is NaN), the result is 1 if isUnorderedLess is false and -1 if isUnorderedLess is
 * true.
 */
@NodeInfo
public class NormalizeCompareNode extends BinaryNode implements Lowerable {

    protected final boolean isUnorderedLess;

    public NormalizeCompareNode(ValueNode x, ValueNode y, boolean isUnorderedLess) {
        super(StampFactory.forKind(Kind.Int), x, y);
        this.isUnorderedLess = isUnorderedLess;
    }

    public static ValueNode create(ValueNode x, ValueNode y, boolean isUnorderedLess, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.EQ, x, y, constantReflection, false);
        if (result instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) result;
            LogicNode resultLT = CompareNode.tryConstantFold(Condition.LT, x, y, constantReflection, isUnorderedLess);
            if (resultLT instanceof LogicConstantNode) {
                LogicConstantNode logicConstantNodeLT = (LogicConstantNode) resultLT;
                if (logicConstantNodeLT.getValue()) {
                    return ConstantNode.forInt(-1);
                } else if (logicConstantNode.getValue()) {
                    return ConstantNode.forInt(0);
                } else {
                    return ConstantNode.forInt(1);
                }
            }
        }

        return new NormalizeCompareNode(x, y, isUnorderedLess);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        // nothing to do
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        LogicNode equalComp;
        LogicNode lessComp;
        if (getX().stamp() instanceof FloatStamp) {
            equalComp = graph().unique(FloatEqualsNode.create(getX(), getY(), tool.getConstantReflection()));
            lessComp = graph().unique(FloatLessThanNode.create(getX(), getY(), isUnorderedLess, tool.getConstantReflection()));
        } else {
            equalComp = graph().unique(IntegerEqualsNode.create(getX(), getY(), tool.getConstantReflection()));
            lessComp = graph().unique(IntegerLessThanNode.create(getX(), getY(), tool.getConstantReflection()));
        }

        ConditionalNode equalValue = graph().unique(new ConditionalNode(equalComp, ConstantNode.forInt(0, graph()), ConstantNode.forInt(1, graph())));
        ConditionalNode value = graph().unique(new ConditionalNode(lessComp, ConstantNode.forInt(-1, graph()), equalValue));

        graph().replaceFloating(this, value);
    }
}
