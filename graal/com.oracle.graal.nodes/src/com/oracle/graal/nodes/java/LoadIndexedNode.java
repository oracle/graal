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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * The {@code LoadIndexedNode} represents a read from an element of an array.
 */
@NodeInfo
public class LoadIndexedNode extends AccessIndexedNode implements Virtualizable, Canonicalizable {

    public static final NodeClass<LoadIndexedNode> TYPE = NodeClass.create(LoadIndexedNode.class);

    /**
     * Creates a new LoadIndexedNode.
     *
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param elementKind the element type
     */
    public LoadIndexedNode(ValueNode array, ValueNode index, Kind elementKind) {
        this(TYPE, createStamp(array, elementKind), array, index, elementKind);
    }

    public static ValueNode create(ValueNode array, ValueNode index, Kind elementKind, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        ValueNode constant = tryConstantFold(array, index, metaAccess, constantReflection);
        if (constant != null) {
            return constant;
        }
        return new LoadIndexedNode(array, index, elementKind);
    }

    protected LoadIndexedNode(NodeClass<? extends LoadIndexedNode> c, Stamp stamp, ValueNode array, ValueNode index, Kind elementKind) {
        super(c, stamp, array, index, elementKind);
    }

    private static Stamp createStamp(ValueNode array, Kind kind) {
        ResolvedJavaType type = StampTool.typeOrNull(array);
        if (kind == Kind.Object && type != null) {
            return StampFactory.declaredTrusted(type.getComponentType());
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(createStamp(array(), elementKind()));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State arrayState = tool.getObjectState(array());
        if (arrayState != null && arrayState.getState() == EscapeState.Virtual) {
            ValueNode indexValue = tool.getReplacedValue(index());
            int idx = indexValue.isConstant() ? indexValue.asJavaConstant().asInt() : -1;
            if (idx >= 0 && idx < arrayState.getVirtualObject().entryCount()) {
                tool.replaceWith(arrayState.getEntry(idx));
            }
        }
    }

    public Node canonical(CanonicalizerTool tool) {
        ValueNode constant = tryConstantFold(array(), index(), tool.getMetaAccess(), tool.getConstantReflection());
        if (constant != null) {
            return constant;
        }
        return this;
    }

    private static ValueNode tryConstantFold(ValueNode array, ValueNode index, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        if (array.isConstant() && !array.isNullConstant() && index.isConstant()) {
            JavaConstant arrayConstant = array.asJavaConstant();
            if (arrayConstant != null) {
                JavaConstant constant = constantReflection.readConstantArrayElement(arrayConstant, index.asJavaConstant().asInt());
                if (constant != null) {
                    return ConstantNode.forConstant(constant, metaAccess);
                }
            }
        }
        return null;
    }
}
