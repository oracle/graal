/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * A floating read of a value from memory specified in terms of an object base and an object
 * relative location. This node does not null check the object.
 */
@NodeInfo
public class FloatingReadNode extends FloatingAccessNode implements IterableNodeType, LIRLowerable, Canonicalizable {

    @OptionalInput(InputType.Memory) MemoryNode lastLocationAccess;

    public static FloatingReadNode create(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp) {
        return USE_GENERATED_NODES ? new FloatingReadNodeGen(object, location, lastLocationAccess, stamp) : new FloatingReadNode(object, location, lastLocationAccess, stamp);
    }

    FloatingReadNode(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp) {
        this(object, location, lastLocationAccess, stamp, null, BarrierType.NONE);
    }

    public static FloatingReadNode create(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard) {
        return USE_GENERATED_NODES ? new FloatingReadNodeGen(object, location, lastLocationAccess, stamp, guard) : new FloatingReadNode(object, location, lastLocationAccess, stamp, guard);
    }

    FloatingReadNode(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard) {
        this(object, location, lastLocationAccess, stamp, guard, BarrierType.NONE);
    }

    public static FloatingReadNode create(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        return USE_GENERATED_NODES ? new FloatingReadNodeGen(object, location, lastLocationAccess, stamp, guard, barrierType) : new FloatingReadNode(object, location, lastLocationAccess, stamp, guard, barrierType);
    }

    FloatingReadNode(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        super(object, location, stamp, guard, barrierType);
        this.lastLocationAccess = lastLocationAccess;
    }

    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    public void setLastLocationAccess(MemoryNode newlla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(newlla));
        lastLocationAccess = newlla;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value address = location().generateAddress(gen, gen.getLIRGeneratorTool(), gen.operand(object()));
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(stamp());
        gen.setResult(this, gen.getLIRGeneratorTool().emitLoad(readKind, address, null));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object() instanceof PiNode && ((PiNode) object()).getGuard() == getGuard()) {
            return FloatingReadNode.create(((PiNode) object()).getOriginalNode(), location(), getLastLocationAccess(), stamp(), getGuard(), getBarrierType());
        }
        return ReadNode.canonicalizeRead(this, location(), object(), tool);
    }

    @Override
    public FixedAccessNode asFixedNode() {
        return graph().add(ReadNode.create(object(), accessLocation(), stamp(), getGuard(), getBarrierType()));
    }

    @Override
    public boolean verify() {
        MemoryNode lla = getLastLocationAccess();
        assert lla == null || lla instanceof MemoryCheckpoint || lla instanceof MemoryProxy || lla instanceof MemoryPhiNode : "lastLocationAccess of " + this +
                        " should be a MemoryCheckpoint, but is " + lla;
        return super.verify();
    }
}
