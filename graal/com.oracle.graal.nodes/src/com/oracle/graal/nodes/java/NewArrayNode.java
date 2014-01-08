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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code NewArrayNode} is used for all array allocations where the element type is know at
 * compile time.
 */
public class NewArrayNode extends AbstractNewArrayNode implements VirtualizableAllocation {

    /**
     * Constructs a new NewArrayNode.
     * 
     * @param elementType the the type of the elements of the newly created array (not the type of
     *            the array itself).
     * @param length the node that produces the length for this allocation.
     * @param fillContents determines whether the array elements should be initialized to zero/null.
     */
    public NewArrayNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
        super(StampFactory.exactNonNull(elementType.getArrayClass()), length, fillContents);
    }

    /**
     * Gets the element type of the array.
     * 
     * @return the element type of the array
     */
    public ResolvedJavaType elementType() {
        return ObjectStamp.typeOrNull(this).getComponentType();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (length().asConstant() != null) {
            final int constantLength = length().asConstant().asInt();
            if (constantLength >= 0 && constantLength < tool.getMaximumEntryCount()) {
                ValueNode[] state = new ValueNode[constantLength];
                ConstantNode defaultForKind = constantLength == 0 ? null : defaultElementValue();
                for (int i = 0; i < constantLength; i++) {
                    state[i] = defaultForKind;
                }
                VirtualObjectNode virtualObject = new VirtualArrayNode(elementType(), constantLength);
                tool.createVirtualObject(virtualObject, state, Collections.<MonitorIdNode> emptyList());
                tool.replaceWithVirtual(virtualObject);
            }
        }
    }

    /* Factored out in a separate method so that subclasses can override it. */
    protected ConstantNode defaultElementValue() {
        return ConstantNode.defaultForKind(elementType().getKind(), graph());
    }
}
