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

@NodeInfo
public final class LoweredDynamicNewUnknownArrayNode extends LoweredDynamicNewArrayNode {
    public static final NodeClass<LoweredDynamicNewUnknownArrayNode> TYPE = NodeClass.create(LoweredDynamicNewUnknownArrayNode.class);

    public interface ArrayLoweringInfo {

        int getArrayBaseOffset(JavaKind kind);

        int getArrayIndexScale(JavaKind kind);

        ValueNode getDefaultValue(JavaKind kind);
    }

    private final ArrayLoweringInfo arrayLoweringInfo;

    public LoweredDynamicNewUnknownArrayNode(MetaAccessProvider metaAccess, ValueNode elementType, ValueNode length, boolean fillContents, ArrayLoweringInfo arrayLoweringInfo,
                    FrameState stateBefore) {
        super(TYPE, elementType, length, fillContents, null, stateBefore, metaAccess);
        this.arrayLoweringInfo = arrayLoweringInfo;
    }

    @Override
    protected NewArrayNode createNewArrayNode(ResolvedJavaType type) {
        JavaKind elementKind = type.getJavaKind();
        ValueNode defaultValue = arrayLoweringInfo.getDefaultValue(elementKind);
        int arrayBaseOffset = arrayLoweringInfo.getArrayBaseOffset(elementKind);
        int arrayIndexScale = arrayLoweringInfo.getArrayIndexScale(elementKind);
        return new LoweredNewArrayNode(type, length(), fillContents(), defaultValue, arrayBaseOffset, arrayIndexScale, stateBefore);
    }

}
