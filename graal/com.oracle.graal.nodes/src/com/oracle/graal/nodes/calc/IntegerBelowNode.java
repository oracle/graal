/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "|<|")
public final class IntegerBelowNode extends CompareNode {
    public static final NodeClass<IntegerBelowNode> TYPE = NodeClass.create(IntegerBelowNode.class);

    public IntegerBelowNode(ValueNode x, ValueNode y) {
        super(TYPE, Condition.BT, false, x, y);
        assert x.stamp() instanceof IntegerStamp;
        assert y.stamp() instanceof IntegerStamp;
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.BT, x, y, constantReflection, false);
        if (result != null) {
            return result;
        } else {
            result = findSynonym(x, y);
            if (result != null) {
                return result;
            }
            return new IntegerBelowNode(x, y);
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = super.canonical(tool, forX, forY);
        if (result != this) {
            return result;
        }
        LogicNode synonym = findSynonym(forX, forY);
        if (synonym != null) {
            return synonym;
        }
        if (forX.isConstant() && forX.asJavaConstant().asLong() == 0) {
            // 0 |<| y is the same as 0 != y
            return LogicNegationNode.create(CompareNode.createCompareNode(Condition.EQ, forX, forY, tool.getConstantReflection()));
        }
        return this;
    }

    private static LogicNode findSynonym(ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.contradiction();
        } else if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) forX.stamp();
            IntegerStamp yStamp = (IntegerStamp) forY.stamp();
            if (yStamp.isPositive()) {
                if (xStamp.isPositive() && xStamp.upperBound() < yStamp.lowerBound()) {
                    return LogicConstantNode.tautology();
                } else if (xStamp.isStrictlyNegative() || xStamp.lowerBound() >= yStamp.upperBound()) {
                    return LogicConstantNode.contradiction();
                }
            }
        }
        return null;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        return new IntegerBelowNode(newX, newY);
    }
}
