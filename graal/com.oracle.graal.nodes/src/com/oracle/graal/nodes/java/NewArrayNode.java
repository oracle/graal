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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code NewArrayNode} class is the base of all instructions that allocate arrays.
 */
public abstract class NewArrayNode extends FixedWithNextNode implements Lowerable, EscapeAnalyzable, ArrayLengthProvider {

    @Input private ValueNode length;
    private final ResolvedJavaType elementType;
    private final boolean fillContents;

    public static final int MaximumEscapeAnalysisArrayLength = 32;
    private final boolean locked;

    @Override
    public ValueNode length() {
        return length;
    }

    /**
     * Constructs a new NewArrayNode.
     *
     * @param elementType the the type of the elements of the newly created array (not the type of the array itself).
     * @param length the node that produces the length for this allocation.
     * @param fillContents determines whether the array elements should be initialized to zero/null.
     * @param locked determines whether the array should be locked immediately.
     */
    protected NewArrayNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents, boolean locked) {
        super(StampFactory.exactNonNull(elementType.getArrayClass()));
        this.length = length;
        this.elementType = elementType;
        this.fillContents = fillContents;
        this.locked = locked;
    }

    /**
     * @return <code>true</code> if the elements of the array will be initialized.
     */
    public boolean fillContents() {
        return fillContents;
    }

    /**
     * @return <code>true</code> if the array will be locked immediately.
     */
    public boolean locked() {
        return locked;
    }

    /**
     * The list of node which produce input for this instruction.
     */
    public ValueNode dimension(int index) {
        assert index == 0;
        return length();
    }

    /**
     * Gets the element type of the array.
     *
     * @return the element type of the array
     */
    public ResolvedJavaType elementType() {
        return elementType;
    }

    /**
     * The rank of the array allocated by this node, i.e. how many array dimensions.
     */
    public int dimensionCount() {
        return 1;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public EscapeOp getEscapeOp() {
        if (length().asConstant() != null) {
            final int constantLength = length().asConstant().asInt();
            if (constantLength >= 0 && constantLength < MaximumEscapeAnalysisArrayLength) {
                return new EscapeOp() {

                    @Override
                    public ValueNode[] fieldState() {
                        ValueNode[] state = new ValueNode[constantLength];
                        for (int i = 0; i < constantLength; i++) {
                            state[i] = ConstantNode.defaultForKind(elementType().getKind(), graph());
                        }
                        return state;
                    }

                    @Override
                    public VirtualObjectNode virtualObject(int virtualId) {
                        return new VirtualArrayNode(virtualId, elementType, constantLength);
                    }
                };
            }
        }
        return null;
    }
}
