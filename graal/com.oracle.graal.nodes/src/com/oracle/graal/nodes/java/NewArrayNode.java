/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code NewArrayNode} is used for all array allocations where the element type is know at
 * compile time.
 */
// JaCoCo Exclude
@NodeInfo
public class NewArrayNode extends AbstractNewArrayNode implements VirtualizableAllocation {

    public static final NodeClass<NewArrayNode> TYPE = NodeClass.create(NewArrayNode.class);

    public NewArrayNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
        this(elementType, length, fillContents, null);
    }

    public NewArrayNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents, FrameState stateBefore) {
        this(TYPE, elementType, length, fillContents, stateBefore);
    }

    protected NewArrayNode(NodeClass<? extends NewArrayNode> c, ResolvedJavaType elementType, ValueNode length, boolean fillContents, FrameState stateBefore) {
        super(c, StampFactory.exactNonNull(elementType.getArrayClass()), length, fillContents, stateBefore);
    }

    @NodeIntrinsic
    private static native Object newArray(@ConstantNodeParameter Class<?> elementType, int length, @ConstantNodeParameter boolean fillContents);

    public static Object newUninitializedArray(Class<?> elementType, int length) {
        return newArray(elementType, length, false);
    }

    /**
     * Gets the element type of the array.
     *
     * @return the element type of the array
     */
    public ResolvedJavaType elementType() {
        return StampTool.typeOrNull(this).getComponentType();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode replacedLength = tool.getReplacedValue(length());
        if (replacedLength.asConstant() != null) {
            final int constantLength = replacedLength.asJavaConstant().asInt();
            if (constantLength >= 0 && constantLength < tool.getMaximumEntryCount()) {
                ValueNode[] state = new ValueNode[constantLength];
                ConstantNode defaultForKind = constantLength == 0 ? null : defaultElementValue();
                for (int i = 0; i < constantLength; i++) {
                    state[i] = defaultForKind;
                }
                VirtualObjectNode virtualObject = createVirtualArrayNode(constantLength);
                tool.createVirtualObject(virtualObject, state, Collections.<MonitorIdNode> emptyList());
                tool.replaceWithVirtual(virtualObject);
            }
        }
    }

    protected VirtualArrayNode createVirtualArrayNode(int constantLength) {
        return new VirtualArrayNode(elementType(), constantLength);
    }

    /* Factored out in a separate method so that subclasses can override it. */
    protected ConstantNode defaultElementValue() {
        return ConstantNode.defaultForKind(elementType().getKind(), graph());
    }
}
