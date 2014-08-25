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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "<")
public class IntegerLessThanNode extends CompareNode {

    /**
     * Constructs a new integer comparison node.
     *
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     */
    public static IntegerLessThanNode create(ValueNode x, ValueNode y) {
        return USE_GENERATED_NODES ? new IntegerLessThanNodeGen(x, y) : new IntegerLessThanNode(x, y);
    }

    protected IntegerLessThanNode(ValueNode x, ValueNode y) {
        super(x, y);
        assert !x.getKind().isNumericFloat() && x.getKind() != Kind.Object;
        assert !y.getKind().isNumericFloat() && y.getKind() != Kind.Object;
    }

    @Override
    public Condition condition() {
        return Condition.LT;
    }

    @Override
    public boolean unorderedIsTrue() {
        return false;
    }

    @Override
    protected ValueNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        assert condition() == Condition.LT;
        if (constant.getKind() == Kind.Int && constant.asInt() == 0) {
            ValueNode a = mirrored ? normalizeNode.getY() : normalizeNode.getX();
            ValueNode b = mirrored ? normalizeNode.getX() : normalizeNode.getY();

            if (normalizeNode.getX().getKind() == Kind.Double || normalizeNode.getX().getKind() == Kind.Float) {
                return FloatLessThanNode.create(a, b, mirrored ^ normalizeNode.isUnorderedLess);
            } else {
                return IntegerLessThanNode.create(a, b);
            }
        }
        return this;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = super.canonical(tool, forX, forY);
        if (result != this) {
            return result;
        }
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.contradiction();
        } else if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) forX.stamp();
            IntegerStamp yStamp = (IntegerStamp) forY.stamp();
            if (xStamp.upperBound() < yStamp.lowerBound()) {
                return LogicConstantNode.tautology();
            } else if (xStamp.lowerBound() >= yStamp.upperBound()) {
                return LogicConstantNode.contradiction();
            }
        }
        if (forX.stamp() instanceof IntegerStamp && forY.stamp() instanceof IntegerStamp) {
            if (IntegerStamp.sameSign((IntegerStamp) forX.stamp(), (IntegerStamp) forY.stamp())) {
                return IntegerBelowNode.create(forX, forY);
            }
        }
        return this;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        if (newX.stamp() instanceof FloatStamp && newY.stamp() instanceof FloatStamp) {
            return FloatLessThanNode.create(newX, newY, true);
        } else if (newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp) {
            return IntegerLessThanNode.create(newX, newY);
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
