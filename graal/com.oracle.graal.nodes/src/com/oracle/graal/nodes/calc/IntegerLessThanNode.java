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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo(shortName = "<")
public final class IntegerLessThanNode extends CompareNode {

    /**
     * Constructs a new integer comparison node.
     *
     * @param x the instruction producing the first input to the instruction
     * @param y the instruction that produces the second input to this instruction
     */
    public IntegerLessThanNode(ValueNode x, ValueNode y) {
        super(x, y);
        assert !x.kind().isFloatOrDouble() && x.kind() != RiKind.Object;
        assert !y.kind().isFloatOrDouble() && y.kind() != RiKind.Object;
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
    protected ValueNode optimizeNormalizeCmp(RiConstant constant, NormalizeCompareNode normalizeNode, boolean mirrored) {
        assert condition() == Condition.LT;
        if (constant.kind == RiKind.Int && constant.asInt() == 0) {
            ValueNode a = mirrored ? normalizeNode.y() : normalizeNode.x();
            ValueNode b = mirrored ? normalizeNode.x() : normalizeNode.y();

            if (normalizeNode.x().kind().isFloatOrDouble()) {
                return graph().unique(new FloatLessThanNode(a, b, mirrored ^ normalizeNode.isUnorderedLess));
            } else {
                return graph().unique(new IntegerLessThanNode(a, b));
            }
        }
        return this;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (x() == y()) {
            return ConstantNode.forBoolean(false, graph());
        } else if (x().integerStamp().upperBound() < y().integerStamp().lowerBound()) {
            return ConstantNode.forBoolean(true, graph());
        } else if (x().integerStamp().lowerBound() >= y().integerStamp().upperBound()) {
            return ConstantNode.forBoolean(false, graph());
        }
        return super.canonical(tool);
    }
}
