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

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
public final class LoadIndexedNode extends AccessIndexedNode implements Canonicalizable, Node.IterableNodeType, Virtualizable {

    /**
     * Creates a new LoadIndexedNode.
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param elementKind the element type
     */
    public LoadIndexedNode(ValueNode array, ValueNode index, Kind elementKind, long leafGraphId) {
        super(createStamp(array, elementKind), array, index, elementKind, leafGraphId);
    }

    private static Stamp createStamp(ValueNode array, Kind kind) {
        if (kind == Kind.Object && array.objectStamp().type() != null) {
            return StampFactory.declared(array.objectStamp().type().getComponentType());
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        MetaAccessProvider runtime = tool.runtime();
        if (runtime != null && index().isConstant() && array().isConstant() && !array().isNullConstant()) {
            Constant arrayConst = array().asConstant();
            if (tool.isImmutable(arrayConst)) {
                int index = index().asConstant().asInt();
                Object array = arrayConst.asObject();
                int length = Array.getLength(array);
                if (index >= 0 && index < length) {
                    return ConstantNode.forConstant(elementKind().readUnsafeConstant(array,
                                    elementKind().getArrayBaseOffset() + index * elementKind().getArrayIndexScale()), runtime, graph());
                }
            }
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        VirtualObjectNode virtualArray = tool.getVirtualState(array());
        if (virtualArray != null) {
            ValueNode indexValue = tool.getReplacedValue(index());
            int index = indexValue.isConstant() ? indexValue.asConstant().asInt() : -1;
            if (index >= 0 && index < virtualArray.entryCount()) {
                ValueNode result = tool.getVirtualEntry(virtualArray, index);
                VirtualObjectNode virtualResult = tool.getVirtualState(result);
                if (virtualResult != null) {
                    tool.replaceWithVirtual(virtualResult);
                } else {
                    tool.replaceWithValue(result);
                }
            }
        }
    }
}
