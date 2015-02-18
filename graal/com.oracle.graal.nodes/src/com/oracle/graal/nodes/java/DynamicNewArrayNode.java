/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
//JaCoCo Exclude
package com.oracle.graal.nodes.java;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;

/**
 * The {@code DynamicNewArrayNode} is used for allocation of arrays when the type is not a
 * compile-time constant.
 */
@NodeInfo
public class DynamicNewArrayNode extends AbstractNewArrayNode {
    public static final NodeClass<DynamicNewArrayNode> TYPE = NodeClass.get(DynamicNewArrayNode.class);

    @Input ValueNode elementType;

    /**
     * A non-null value indicating the worst case element type. Mainly useful for distinguishing
     * Object arrays from primitive arrays.
     */
    protected final Kind knownElementKind;

    public DynamicNewArrayNode(ValueNode elementType, ValueNode length) {
        this(TYPE, elementType, length, true, null);
    }

    public DynamicNewArrayNode(ValueNode elementType, ValueNode length, boolean fillContents, Kind knownElementKind) {
        this(TYPE, elementType, length, fillContents, knownElementKind);
    }

    protected DynamicNewArrayNode(NodeClass<? extends DynamicNewArrayNode> c, ValueNode elementType, ValueNode length, boolean fillContents, Kind knownElementKind) {
        super(c, StampFactory.objectNonNull(), length, fillContents);
        this.elementType = elementType;
        this.knownElementKind = knownElementKind;
    }

    public ValueNode getElementType() {
        return elementType;
    }

    public Kind getKnownElementKind() {
        return knownElementKind;
    }

    protected NewArrayNode forConstantType(ResolvedJavaType type) {
        ValueNode len = length();
        NewArrayNode ret = graph().add(new NewArrayNode(type, len.isAlive() ? len : graph().addOrUniqueWithInputs(len), fillContents()));
        if (stateBefore() != null) {
            ret.setStateBefore(stateBefore());
        }
        return ret;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (isAlive() && elementType.isConstant()) {
            ResolvedJavaType javaType = tool.getConstantReflection().asJavaType(elementType.asConstant());
            if (javaType != null && !javaType.equals(tool.getMetaAccess().lookupJavaType(void.class))) {
                NewArrayNode newArray = forConstantType(javaType);
                List<Node> snapshot = inputs().snapshot();
                graph().replaceFixedWithFixed(this, newArray);
                for (Node input : snapshot) {
                    tool.removeIfUnused(input);
                }
                tool.addToWorkList(newArray);
            }
        }
    }

    @NodeIntrinsic
    public static Object newArray(Class<?> componentType, int length) {
        return Array.newInstance(componentType, length);
    }

    @NodeIntrinsic
    private static native Object newArray(Class<?> componentType, int length, @ConstantNodeParameter boolean fillContents, @ConstantNodeParameter Kind knownElementKind);

    public static Object newUninitializedArray(Class<?> componentType, int length, Kind knownElementKind) {
        return newArray(componentType, length, false, knownElementKind);
    }

}
