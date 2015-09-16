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
package com.oracle.graal.nodes.java;

import com.oracle.graal.compiler.common.type.IntegerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.SimplifierTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.ArrayLengthProvider;

/**
 * The {@code AbstractNewArrayNode} is used for all 1-dimensional array allocations.
 */
@NodeInfo
public abstract class AbstractNewArrayNode extends AbstractNewObjectNode implements ArrayLengthProvider {

    public static final NodeClass<AbstractNewArrayNode> TYPE = NodeClass.create(AbstractNewArrayNode.class);
    @Input protected ValueNode length;

    @Override
    public ValueNode length() {
        return length;
    }

    protected AbstractNewArrayNode(NodeClass<? extends AbstractNewArrayNode> c, Stamp stamp, ValueNode length, boolean fillContents, FrameState stateBefore) {
        super(c, stamp, fillContents, stateBefore);
        this.length = length;
    }

    /**
     * The list of node which produce input for this instruction.
     */
    public ValueNode dimension(int index) {
        assert index == 0;
        return length();
    }

    /**
     * The rank of the array allocated by this node, i.e. how many array dimensions.
     */
    public int dimensionCount() {
        return 1;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        Stamp lengthStamp = length.stamp();
        if (lengthStamp instanceof IntegerStamp && ((IntegerStamp) lengthStamp).isPositive()) {
            // otherwise, removing the allocation might swallow a NegativeArraySizeException
            super.simplify(tool);
        }
    }
}
