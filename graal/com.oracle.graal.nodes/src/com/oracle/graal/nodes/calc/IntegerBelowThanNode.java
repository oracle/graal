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
import com.oracle.graal.api.meta.ProfilingInfo.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "|<|")
public final class IntegerBelowThanNode extends CompareNode {

    /**
     * Constructs a new unsigned integer comparison node.
     *
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     */
    public IntegerBelowThanNode(ValueNode x, ValueNode y) {
        super(x, y);
        assert !x.getKind().isNumericFloat() && x.getKind() != Kind.Object;
        assert !y.getKind().isNumericFloat() && y.getKind() != Kind.Object;
    }

    @Override
    public Condition condition() {
        return Condition.BT;
    }

    @Override
    public boolean unorderedIsTrue() {
        return false;
    }

    @Override
    public TriState evaluate(ConstantReflectionProvider constantReflection, ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return TriState.FALSE;
        } else if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) forX.stamp();
            IntegerStamp yStamp = (IntegerStamp) forY.stamp();
            if (yStamp.isPositive()) {
                if (xStamp.isPositive() && xStamp.upperBound() < yStamp.lowerBound()) {
                    return TriState.TRUE;
                } else if (xStamp.isStrictlyNegative() || xStamp.lowerBound() >= yStamp.upperBound()) {
                    return TriState.FALSE;
                }
            }
        }
        return super.evaluate(constantReflection, forX, forY);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node result = super.canonical(tool);
        if (result != this) {
            return result;
        }
        if (x().isConstant() && x().asConstant().asLong() == 0) {
            // 0 |<| y is the same as 0 != y
            return graph().unique(new LogicNegationNode(CompareNode.createCompareNode(graph(), Condition.EQ, x(), y())));
        }
        return this;
    }
}
