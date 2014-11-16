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
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.word.*;

/**
 * Read Klass::_java_mirror and incorporate non-null type information into stamp. This is also used
 * by {@link ClassGetHubNode} to eliminate chains of klass._java_mirror._klass.
 */
@NodeInfo
public class HubGetClassNode extends FloatingGuardedNode implements Lowerable, Canonicalizable {
    @Input protected ValueNode hub;

    public static HubGetClassNode create(ValueNode hub) {
        return new HubGetClassNode(hub);
    }

    protected HubGetClassNode(ValueNode hub) {
        super(StampFactory.declaredNonNull(runtime().fromClass(Class.class)), null);
        this.hub = hub;
    }

    public ValueNode getHub() {
        return hub;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (usages().isEmpty()) {
            return null;
        } else {
            MetaAccessProvider metaAccess = tool.getMetaAccess();
            if (metaAccess != null) {
                if (hub.isConstant()) {
                    ResolvedJavaType exactType = tool.getConstantReflection().asJavaType(hub.asJavaConstant());
                    return ConstantNode.forConstant(exactType.getJavaClass(), metaAccess);
                }
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
        LocationNode location = ConstantLocationNode.create(CLASS_MIRROR_LOCATION, Kind.Object, config.classMirrorOffset, graph());
        assert !hub.isConstant();
        FloatingReadNode read = graph().unique(FloatingReadNode.create(hub, location, null, stamp(), getGuard(), BarrierType.NONE));
        graph().replaceFloating(this, read);
    }

    @NodeIntrinsic
    public static native Class<?> readClass(TypePointer hub);

}
