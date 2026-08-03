/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.vector.nodes.consumer;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AbstractNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;

import jdk.graal.compiler.vector.nodes.consumer.AbstractMaterializeVectorNode.Allocator;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Materialize a vector with an unknown element type.
 */
@NodeInfo(shortName = "Object[]")
public final class DynamicObjectVectorAllocator extends MaterializeVectorNode.Allocator {
    public static final NodeClass<DynamicObjectVectorAllocator> TYPE = NodeClass.create(DynamicObjectVectorAllocator.class);

    @Input ValueNode elementType;

    /**
     * @param stamp pass {@code null} to let {@link Allocator#Allocator(NodeClass)} set the stamp
     */
    public DynamicObjectVectorAllocator(@InjectedNodeParameter Stamp stamp, ValueNode elementType) {
        super(TYPE);
        if (stamp != null) {
            setStamp(stamp);
        }
        this.elementType = elementType;
    }

    @Override
    public AbstractNewArrayNode createAllocationNode(MetaAccessProvider metaAccess, ValueNode length) {
        return new DynamicNewArrayNode(metaAccess, elementType, length, false, JavaKind.Object);
    }

    @Override
    public JavaKind getArrayKind() {
        return JavaKind.Object;
    }
}
