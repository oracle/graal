/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.cri.ci.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Returns -1, 0, or 1 if either x < y, x == y, or x > y.
 */
public final class NormalizeCompareNode extends BinaryNode implements Lowerable {
    @Data public final boolean isUnorderedLess;

    /**
     * Creates a new compare operation.
     * @param x the first input
     * @param y the second input
     * @param isUnorderedLess true when an unordered floating point comparison is interpreted as less, false when greater.
     */
    public NormalizeCompareNode(ValueNode x, ValueNode y, boolean isUnorderedLess) {
        super(CiKind.Int, x, y);
        this.isUnorderedLess = isUnorderedLess;
    }

    @Override
    public void lower(CiLoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) graph();

        CompareNode equalComp = graph.unique(new CompareNode(x(), Condition.EQ, false, y()));
        MaterializeNode equalValue = MaterializeNode.create(equalComp, graph, ConstantNode.forInt(0, graph), ConstantNode.forInt(1, graph));

        CompareNode lessComp = graph.unique(new CompareNode(x(), Condition.LT, isUnorderedLess, y()));
        MaterializeNode value =  MaterializeNode.create(lessComp, graph, ConstantNode.forInt(-1, graph), equalValue);

        graph.replaceFloating(this, value);
    }
}
