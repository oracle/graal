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
package com.oracle.graal.nodes;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.calc.*;

public final class MaterializeNode extends ConditionalNode {

    public static CompareNode createCompareNode(Condition condition, ValueNode x, ValueNode y) {
        assert x.kind() == y.kind();
        assert condition.isCanonical() : "condition is not canonical: " + condition;

        assert !x.kind().isFloatOrDouble();
        CompareNode comparison;
        if (condition == Condition.EQ) {
            if (x.kind().isObject()) {
                comparison = new ObjectEqualsNode(x, y);
            } else {
                assert x.kind().getStackKind().isStackInt() || x.kind().isLong();
                comparison = new IntegerEqualsNode(x, y);
            }
        } else if (condition == Condition.LT) {
            assert x.kind().getStackKind().isStackInt() || x.kind().isLong();
            comparison = new IntegerLessThanNode(x, y);
        } else {
            assert condition == Condition.BT;
            assert x.kind().getStackKind().isStackInt() || x.kind().isLong();
            comparison = new IntegerBelowThanNode(x, y);
        }

        return x.graph().unique(comparison);
    }

    private MaterializeNode(Condition condition, ValueNode x, ValueNode y) {
        this(createCompareNode(condition, x, y), ConstantNode.forInt(1, x.graph()), ConstantNode.forInt(0, x.graph()));
    }

    private MaterializeNode(BooleanNode condition, ValueNode trueValue, ValueNode falseValue) {
        super(condition, trueValue, falseValue);
    }

    public static MaterializeNode create(BooleanNode condition, ValueNode trueValue, ValueNode falseValue) {
        Graph graph = condition.graph();
        MaterializeNode result = new MaterializeNode(condition, trueValue, falseValue);
        return graph.unique(result);

    }

    public static MaterializeNode create(BooleanNode condition) {
        return create(condition, ConstantNode.forInt(1, condition.graph()), ConstantNode.forInt(0, condition.graph()));
    }

    @NodeIntrinsic
    public static native boolean materialize(@ConstantNodeParameter Condition condition, int x, int y);

    @NodeIntrinsic
    public static native boolean materialize(@ConstantNodeParameter Condition condition, long x, long y);
}
