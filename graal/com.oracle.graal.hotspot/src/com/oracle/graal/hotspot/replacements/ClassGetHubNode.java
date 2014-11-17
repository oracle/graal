/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Read Class::_klass to get the hub for a {@link java.lang.Class}. This node mostly exists to
 * replace _klass._java_mirror._klass with _klass. The constant folding could be handled by
 * {@link ReadNode#canonicalizeRead(ValueNode, LocationNode, ValueNode, CanonicalizerTool)}.
 */
@NodeInfo
public class ClassGetHubNode extends FloatingGuardedNode implements Lowerable, Canonicalizable {
    @Input protected ValueNode clazz;

    public static ClassGetHubNode create(ValueNode clazz) {
        return new ClassGetHubNode(clazz);
    }

    public static ClassGetHubNode create(ValueNode clazz, ValueNode guard) {
        return new ClassGetHubNode(clazz, guard);
    }

    protected ClassGetHubNode(ValueNode clazz) {
        super(StampFactory.forPointer(PointerType.Type), null);
        this.clazz = clazz;
    }

    protected ClassGetHubNode(ValueNode clazz, ValueNode guard) {
        super(StampFactory.forPointer(PointerType.Type), (GuardingNode) guard);
        this.clazz = clazz;
    }

    public ValueNode getHub() {
        return clazz;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (usages().isEmpty()) {
            return null;
        } else {
            if (clazz.isConstant()) {
                MetaAccessProvider metaAccess = tool.getMetaAccess();
                if (metaAccess != null) {
                    HotSpotResolvedJavaType exactType = (HotSpotResolvedJavaType) tool.getConstantReflection().asJavaType(clazz.asJavaConstant());
                    if (exactType instanceof HotSpotResolvedObjectType) {
                        HotSpotResolvedObjectType objectType = (HotSpotResolvedObjectType) exactType;
                        ConstantNode cn = ConstantNode.forConstant(stamp(), objectType.getObjectHub(), metaAccess);
                        return cn;
                    } else if (exactType instanceof HotSpotResolvedPrimitiveType) {
                        /*
                         * The constant value is null but we don't have a JavaConstant subclass to
                         * talk about a NULL pointer yet.
                         */
                    }
                }
            }
            if (clazz instanceof HubGetClassNode) {
                // replace _klass._java_mirror._klass -> _klass
                return ((HubGetClassNode) clazz).getHub();
            }
            return this;
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        HotSpotVMConfig config = runtime().getConfig();
        LocationNode location = ConstantLocationNode.create(CLASS_KLASS_LOCATION, getWordKind(), config.klassOffset, graph());
        assert !clazz.isConstant();
        FloatingReadNode read = graph().unique(FloatingReadNode.create(clazz, location, null, stamp(), getGuard(), BarrierType.NONE));
        graph().replaceFloating(this, read);
    }

    @NodeIntrinsic
    public static native KlassPointer readClass(Class<?> clazz);

    @NodeIntrinsic
    public static native KlassPointer readClass(Class<?> clazz, GuardingNode guard);

}
