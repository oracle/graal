/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.memory;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.address.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.meta.*;

/**
 * A floating read of a value from memory specified in terms of an object base and an object
 * relative location. This node does not null check the object.
 */
@NodeInfo(nameTemplate = "Read#{p#location/s}")
public final class FloatingReadNode extends FloatingAccessNode implements LIRLowerable, Canonicalizable {
    public static final NodeClass<FloatingReadNode> TYPE = NodeClass.create(FloatingReadNode.class);

    @OptionalInput(InputType.Memory) MemoryNode lastLocationAccess;

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryNode lastLocationAccess, Stamp stamp) {
        this(address, location, lastLocationAccess, stamp, null, BarrierType.NONE);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard) {
        this(address, location, lastLocationAccess, stamp, guard, BarrierType.NONE);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        super(TYPE, address, location, stamp, guard, barrierType);
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
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(stamp());
        gen.setResult(this, gen.getLIRGeneratorTool().emitLoad(readKind, gen.operand(address), null));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getAddress() instanceof OffsetAddressNode) {
            OffsetAddressNode objAddress = (OffsetAddressNode) getAddress();
            if (objAddress.getBase() instanceof PiNode && ((PiNode) objAddress.getBase()).getGuard() == getGuard()) {
                OffsetAddressNode newAddress = new OffsetAddressNode(((PiNode) objAddress.getBase()).getOriginalNode(), objAddress.getOffset());
                return new FloatingReadNode(newAddress, getLocationIdentity(), getLastLocationAccess(), stamp(), getGuard(), getBarrierType());
            }
        }
        return ReadNode.canonicalizeRead(this, getAddress(), getLocationIdentity(), tool);
    }

    @Override
    public FixedAccessNode asFixedNode() {
        return graph().add(new ReadNode(getAddress(), getLocationIdentity(), stamp(), getGuard(), getBarrierType()));
    }

    @Override
    public boolean verify() {
        MemoryNode lla = getLastLocationAccess();
        assert lla != null || getLocationIdentity().isImmutable() : "lastLocationAccess of " + this + " shouldn't be null for mutable location identity " + getLocationIdentity();
        return super.verify();
    }
}
