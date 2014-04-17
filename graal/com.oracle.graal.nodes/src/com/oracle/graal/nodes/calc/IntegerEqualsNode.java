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
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo(shortName = "==")
public final class IntegerEqualsNode extends CompareNode {

    /**
     * Constructs a new integer equality comparison node.
     * 
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     */
    public IntegerEqualsNode(ValueNode x, ValueNode y) {
        super(x, y);
        assert !x.getKind().isNumericFloat() && x.getKind() != Kind.Object;
        assert !y.getKind().isNumericFloat() && y.getKind() != Kind.Object;
    }

    @Override
    public Condition condition() {
        return Condition.EQ;
    }

    @Override
    public boolean unorderedIsTrue() {
        return false;
    }

    @Override
    protected LogicNode optimizeNormalizeCmp(Constant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        if (constant.getKind() == Kind.Int && constant.asInt() == 0) {
            ValueNode a = mirrored ? normalizeNode.y() : normalizeNode.x();
            ValueNode b = mirrored ? normalizeNode.x() : normalizeNode.y();

            if (normalizeNode.x().getKind() == Kind.Double || normalizeNode.x().getKind() == Kind.Float) {
                return graph().unique(new FloatEqualsNode(a, b));
            } else {
                return graph().unique(new IntegerEqualsNode(a, b));
            }
        }
        return this;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (GraphUtil.unproxify(x()) == GraphUtil.unproxify(y())) {
            return LogicConstantNode.tautology(graph());
        } else if (x().stamp().alwaysDistinct(y().stamp())) {
            return LogicConstantNode.contradiction(graph());
        }

        ValueNode result = canonicalizeSymmetric(x(), y());
        if (result != null) {
            return result;
        }

        result = canonicalizeSymmetric(y(), x());
        if (result != null) {
            return result;
        }

        return super.canonical(tool);
    }

    private ValueNode canonicalizeSymmetric(ValueNode x, ValueNode y) {
        if (y.isConstant() && y.asConstant().asLong() == 0) {
            if (x instanceof AndNode) {
                return graph().unique(new IntegerTestNode(((AndNode) x).x(), ((AndNode) x).y()));
            } else if (x instanceof LeftShiftNode) {
                LeftShiftNode shift = (LeftShiftNode) x;
                if (shift.y().isConstant()) {
                    int mask = shift.getShiftAmountMask();
                    int amount = shift.y().asConstant().asInt() & mask;
                    if (shift.x().getKind() == Kind.Int) {
                        return graph().unique(new IntegerTestNode(shift.x(), ConstantNode.forInt(-1 >>> amount, graph())));
                    } else {
                        assert shift.x().getKind() == Kind.Long;
                        return graph().unique(new IntegerTestNode(shift.x(), ConstantNode.forLong(-1L >>> amount, graph())));
                    }
                }
            } else if (x instanceof RightShiftNode) {
                RightShiftNode shift = (RightShiftNode) x;
                if (shift.y().isConstant() && ((IntegerStamp) shift.x().stamp()).isPositive()) {
                    int mask = shift.getShiftAmountMask();
                    int amount = shift.y().asConstant().asInt() & mask;
                    if (shift.x().getKind() == Kind.Int) {
                        return graph().unique(new IntegerTestNode(shift.x(), ConstantNode.forInt(-1 << amount, graph())));
                    } else {
                        assert shift.x().getKind() == Kind.Long;
                        return graph().unique(new IntegerTestNode(shift.x(), ConstantNode.forLong(-1L << amount, graph())));
                    }
                }
            } else if (x instanceof UnsignedRightShiftNode) {
                UnsignedRightShiftNode shift = (UnsignedRightShiftNode) x;
                if (shift.y().isConstant()) {
                    int mask = shift.getShiftAmountMask();
                    int amount = shift.y().asConstant().asInt() & mask;
                    if (shift.x().getKind() == Kind.Int) {
                        return graph().unique(new IntegerTestNode(shift.x(), ConstantNode.forInt(-1 << amount, graph())));
                    } else {
                        assert shift.x().getKind() == Kind.Long;
                        return graph().unique(new IntegerTestNode(shift.x(), ConstantNode.forLong(-1L << amount, graph())));
                    }
                }
            }
        }
        return null;
    }
}
