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
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Read Klass::_layout_helper and incorporate any useful stamp information based on any type
 * information in {@code klass}.
 */
@NodeInfo
public class KlassLayoutHelperNode extends FloatingGuardedNode implements Canonicalizable, Lowerable {

    @Input protected ValueNode klass;

    public static KlassLayoutHelperNode create(ValueNode klass) {
        return new KlassLayoutHelperNode(klass);
    }

    public static KlassLayoutHelperNode create(ValueNode klass, ValueNode guard) {
        return new KlassLayoutHelperNode(klass, guard);
    }

    protected KlassLayoutHelperNode(ValueNode klass) {
        super(StampFactory.forKind(Kind.Int));
        this.klass = klass;
    }

    protected KlassLayoutHelperNode(ValueNode klass, ValueNode guard) {
        super(StampFactory.forKind(Kind.Int), (GuardingNode) guard);
        this.klass = klass;
    }

    @Override
    public boolean inferStamp() {
        if (klass instanceof LoadHubNode) {
            LoadHubNode hub = (LoadHubNode) klass;
            Stamp hubStamp = hub.getValue().stamp();
            if (hubStamp instanceof ObjectStamp) {
                ObjectStamp objectStamp = (ObjectStamp) hubStamp;
                ResolvedJavaType type = objectStamp.type();
                if (type != null && !type.isJavaLangObject()) {
                    if (!type.isArray() && !type.isInterface()) {
                        /*
                         * Definitely some form of instance type.
                         */
                        return updateStamp(StampFactory.forInteger(Kind.Int, runtime().getConfig().klassLayoutHelperNeutralValue, Integer.MAX_VALUE));
                    }
                    if (type.isArray()) {
                        return updateStamp(StampFactory.forInteger(Kind.Int, Integer.MIN_VALUE, runtime().getConfig().klassLayoutHelperNeutralValue - 1));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (usages().isEmpty()) {
            return null;
        } else {
            if (klass.isConstant()) {
                long base = klass.asJavaConstant().asLong();
                if (base != 0L) {
                    Constant constant = stamp().readConstant(tool.getConstantReflection().getMemoryAccessProvider(), klass.asJavaConstant(), runtime().getConfig().klassLayoutHelperOffset);
                    return ConstantNode.forConstant(stamp(), constant, tool.getMetaAccess());
                }
            }
            if (klass instanceof LoadHubNode) {
                LoadHubNode hub = (LoadHubNode) klass;
                Stamp hubStamp = hub.getValue().stamp();
                if (hubStamp instanceof ObjectStamp) {
                    ObjectStamp ostamp = (ObjectStamp) hubStamp;
                    HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) ostamp.type();
                    if (type != null && type.isArray() && !type.getComponentType().isPrimitive()) {
                        // The layout for all object arrays is the same.
                        Constant constant = stamp().readConstant(tool.getConstantReflection().getMemoryAccessProvider(), type.klass(), runtime().getConfig().klassLayoutHelperOffset);
                        return ConstantNode.forConstant(stamp(), constant, tool.getMetaAccess());
                    }
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
        LocationNode location = ConstantLocationNode.create(KLASS_LAYOUT_HELPER_LOCATION, Kind.Int, runtime().getConfig().klassLayoutHelperOffset, graph());
        assert !klass.isConstant();
        graph().replaceFloating(this, graph().unique(FloatingReadNode.create(klass, location, null, stamp(), getGuard(), BarrierType.NONE)));
    }
}
