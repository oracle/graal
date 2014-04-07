/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * A floating read of a value from memory specified in terms of an object base and an object
 * relative location. This node does not null check the object.
 */
public final class FloatingReadNode extends FloatingAccessNode implements IterableNodeType, LIRLowerable, Canonicalizable {

    @Input(InputType.Memory) private MemoryNode lastLocationAccess;

    public FloatingReadNode(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp) {
        this(object, location, lastLocationAccess, stamp, null, BarrierType.NONE, false);
    }

    public FloatingReadNode(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard) {
        this(object, location, lastLocationAccess, stamp, guard, BarrierType.NONE, false);
    }

    public FloatingReadNode(ValueNode object, LocationNode location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean compressible) {
        super(object, location, stamp, guard, barrierType, compressible);
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
        Value address = location().generateAddress(gen, gen.operand(object()));
        PlatformKind readKind = gen.getLIRGeneratorTool().getPlatformKind(stamp());
        gen.setResult(this, gen.getLIRGeneratorTool().emitLoad(readKind, address, this));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object() instanceof PiNode && ((PiNode) object()).getGuard() == getGuard()) {
            return graph().unique(new FloatingReadNode(((PiNode) object()).getOriginalNode(), location(), getLastLocationAccess(), stamp(), getGuard(), getBarrierType(), isCompressible()));
        }
        return ReadNode.canonicalizeRead(this, location(), object(), tool, isCompressible());
    }

    @Override
    public FixedAccessNode asFixedNode() {
        return graph().add(new ReadNode(object(), accessLocation(), stamp(), getGuard(), getBarrierType(), isCompressible()));
    }

    @Override
    public boolean verify() {
        MemoryNode lla = getLastLocationAccess();
        assert lla == null || lla.asMemoryCheckpoint() != null || lla.asMemoryPhi() != null : "lastLocationAccess of " + this + " should be a MemoryCheckpoint, but is " + lla;
        return super.verify();
    }
}
