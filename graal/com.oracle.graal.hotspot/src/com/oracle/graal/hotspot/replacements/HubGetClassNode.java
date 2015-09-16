/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import jdk.internal.jvmci.hotspot.HotSpotResolvedObjectType;
import jdk.internal.jvmci.hotspot.HotSpotResolvedPrimitiveType;
import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.ConstantReflectionProvider;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.MetaAccessProvider;
import jdk.internal.jvmci.meta.ResolvedJavaType;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.hotspot.word.KlassPointer;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FloatingGuardedNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.ConvertNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

/**
 * Read {@code Klass::_java_mirror} and incorporate non-null type information into stamp. This is
 * also used by {@link ClassGetHubNode} to eliminate chains of {@code klass._java_mirror._klass}.
 */
@NodeInfo
public final class HubGetClassNode extends FloatingGuardedNode implements Lowerable, Canonicalizable, ConvertNode {
    public static final NodeClass<HubGetClassNode> TYPE = NodeClass.create(HubGetClassNode.class);
    @Input protected ValueNode hub;

    public HubGetClassNode(@InjectedNodeParameter MetaAccessProvider metaAccess, ValueNode hub) {
        super(TYPE, StampFactory.declaredNonNull(metaAccess.lookupJavaType(Class.class)), null);
        this.hub = hub;
    }

    public ValueNode getHub() {
        return hub;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        } else {
            MetaAccessProvider metaAccess = tool.getMetaAccess();
            if (metaAccess != null && hub.isConstant()) {
                ResolvedJavaType exactType = tool.getConstantReflection().asJavaType(hub.asConstant());
                if (exactType != null) {
                    return ConstantNode.forConstant(exactType.getJavaClass(), metaAccess);
                }
            }
            return this;
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @NodeIntrinsic
    public static native Class<?> readClass(KlassPointer hub);

    @Override
    public ValueNode getValue() {
        return hub;
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return c;
        }
        return constantReflection.asJavaType(c).getJavaClass();
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return c;
        }
        ResolvedJavaType type = constantReflection.asJavaType(c);
        if (type instanceof HotSpotResolvedObjectType) {
            return ((HotSpotResolvedObjectType) type).getObjectHub();
        } else {
            assert type instanceof HotSpotResolvedPrimitiveType;
            return JavaConstant.NULL_POINTER;
        }
    }

    @Override
    public boolean isLossless() {
        /*
         * Any concrete Klass* has a corresponding java.lang.Class
         */
        return true;
    }
}
