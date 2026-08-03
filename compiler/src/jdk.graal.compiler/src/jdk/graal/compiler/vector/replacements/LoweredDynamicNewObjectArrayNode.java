/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.vector.nodes.consumer.AbstractMaterializeVectorNode.Allocator;
import jdk.graal.compiler.vector.nodes.consumer.DynamicObjectVectorAllocator;

@NodeInfo
public final class LoweredDynamicNewObjectArrayNode extends LoweredDynamicNewArrayNode implements VectorizableNewArrayNode {
    public static final NodeClass<LoweredDynamicNewObjectArrayNode> TYPE = NodeClass.create(LoweredDynamicNewObjectArrayNode.class);

    @Input ValueNode defaultValue;

    protected final int arrayBaseOffset;
    protected final int arrayIndexScale;

    public LoweredDynamicNewObjectArrayNode(MetaAccessProvider metaAccess, ValueNode elementType, ValueNode length, boolean fillContents, ValueNode defaultValue, int arrayBaseOffset,
                    int arrayIndexScale, FrameState stateBefore) {
        super(TYPE, elementType, length, fillContents, JavaKind.Object, stateBefore, metaAccess);
        this.defaultValue = defaultValue;
        this.arrayBaseOffset = arrayBaseOffset;
        this.arrayIndexScale = arrayIndexScale;
    }

    @Override
    protected NewArrayNode createNewArrayNode(ResolvedJavaType type) {
        return new LoweredNewArrayNode(type, length(), fillContents(), defaultValue, arrayBaseOffset, arrayIndexScale, stateBefore);
    }

    @Override
    public ValueNode getDefaultValue() {
        return defaultValue;
    }

    @Override
    public int getArrayBaseOffset() {
        return arrayBaseOffset;
    }

    @Override
    public int getArrayIndexScale() {
        return arrayIndexScale;
    }

    @Override
    public Allocator getAllocator() {
        return graph().unique(new DynamicObjectVectorAllocator(null, getElementType()));
    }
}
