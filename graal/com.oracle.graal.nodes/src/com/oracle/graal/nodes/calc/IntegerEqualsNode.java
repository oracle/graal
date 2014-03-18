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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;

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
        if (x() == y()) {
            return LogicConstantNode.tautology(graph());
        } else if (x().stamp().alwaysDistinct(y().stamp())) {
            return LogicConstantNode.contradiction(graph());
        }

        if (x() instanceof AndNode && y().isConstant() && y().asConstant().asLong() == 0) {
            return graph().unique(new IntegerTestNode(((AndNode) x()).x(), ((AndNode) x()).y()));
        } else if (y() instanceof AndNode && x().isConstant() && x().asConstant().asLong() == 0) {
            return graph().unique(new IntegerTestNode(((AndNode) y()).x(), ((AndNode) y()).y()));
        }
        return super.canonical(tool);
    }
}
