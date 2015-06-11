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

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.jvmci.meta.*;

/**
 * The {@code DynamicNewArrayNode} is used for allocation of arrays when the type is not a
 * compile-time constant.
 */
@NodeInfo
public class DynamicNewArrayNode extends AbstractNewArrayNode implements Canonicalizable {
    public static final NodeClass<DynamicNewArrayNode> TYPE = NodeClass.create(DynamicNewArrayNode.class);

    @Input ValueNode elementType;

    /**
     * A non-null value indicating the worst case element type. Mainly useful for distinguishing
     * Object arrays from primitive arrays.
     */
    protected final Kind knownElementKind;

    public DynamicNewArrayNode(ValueNode elementType, ValueNode length, boolean fillContents, Kind knownElementKind) {
        this(TYPE, elementType, length, fillContents, knownElementKind);
    }

    protected DynamicNewArrayNode(NodeClass<? extends DynamicNewArrayNode> c, ValueNode elementType, ValueNode length, boolean fillContents, Kind knownElementKind) {
        super(c, StampFactory.objectNonNull(), length, fillContents);
        this.elementType = elementType;
        this.knownElementKind = knownElementKind;
        assert knownElementKind != Kind.Void && knownElementKind != Kind.Illegal;
    }

    public ValueNode getElementType() {
        return elementType;
    }

    public Kind getKnownElementKind() {
        return knownElementKind;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        /*
         * Do not call the super implementation: we must not eliminate unused allocations because
         * throwing a NullPointerException or IllegalArgumentException is a possible side effect of
         * an unused allocation.
         */
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (elementType.isConstant()) {
            ResolvedJavaType type = tool.getConstantReflection().asJavaType(elementType.asConstant());
            if (type != null && !throwsIllegalArgumentException(type)) {
                return new NewArrayNode(type, length(), fillContents());
            }
        }
        return this;
    }

    public static boolean throwsIllegalArgumentException(Class<?> elementType) {
        return elementType == void.class;
    }

    public static boolean throwsIllegalArgumentException(ResolvedJavaType elementType) {
        return elementType.getKind() == Kind.Void;
    }

    @NodeIntrinsic
    private static native Object newArray(Class<?> componentType, int length, @ConstantNodeParameter boolean fillContents, @ConstantNodeParameter Kind knownElementKind);

    public static Object newUninitializedArray(Class<?> componentType, int length, Kind knownElementKind) {
        return newArray(componentType, length, false, knownElementKind);
    }

}
