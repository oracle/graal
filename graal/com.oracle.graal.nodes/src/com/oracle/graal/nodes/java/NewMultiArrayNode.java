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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code NewMultiArrayNode} represents an allocation of a multi-dimensional object array.
 */
@NodeInfo
public class NewMultiArrayNode extends DeoptimizingFixedWithNextNode implements Lowerable, ArrayLengthProvider {

    @Input private final NodeInputList<ValueNode> dimensions;
    private final ResolvedJavaType type;

    public ValueNode dimension(int index) {
        return dimensions.get(index);
    }

    public int dimensionCount() {
        return dimensions.size();
    }

    public NodeList<ValueNode> dimensions() {
        return dimensions;
    }

    /**
     * Constructs a new NewMultiArrayNode.
     *
     * @param type the element type of the array
     * @param dimensions the node which produce the dimensions for this array
     */
    public static NewMultiArrayNode create(ResolvedJavaType type, ValueNode[] dimensions) {
        return new NewMultiArrayNodeGen(type, dimensions);
    }

    NewMultiArrayNode(ResolvedJavaType type, ValueNode[] dimensions) {
        super(StampFactory.exactNonNull(type));
        this.type = type;
        this.dimensions = new NodeInputList<>(this, dimensions);
        assert dimensions.length > 0 && type.isArray();
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public ResolvedJavaType type() {
        return type;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    public ValueNode length() {
        return dimension(0);
    }
}
