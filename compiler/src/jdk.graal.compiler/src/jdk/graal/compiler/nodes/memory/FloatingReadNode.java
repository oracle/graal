/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.memory;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * A floating read of a value from memory specified in terms of an object base and an object
 * relative location. This node does not null check the object.
 */
@NodeInfo(nameTemplate = "Read#{p#location/s}", cycles = CYCLES_2, size = SIZE_1)
public final class FloatingReadNode extends FloatingAccessNode implements LIRLowerableAccess, Canonicalizable {
    public static final NodeClass<FloatingReadNode> TYPE = NodeClass.create(FloatingReadNode.class);

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryKill lastLocationAccess, Stamp stamp) {
        this(address, location, lastLocationAccess, stamp, null, BarrierType.NONE);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryKill lastLocationAccess, Stamp stamp, GuardingNode guard) {
        this(address, location, lastLocationAccess, stamp, guard, BarrierType.NONE);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryKill lastLocationAccess, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        super(TYPE, address, location, stamp, guard, barrierType);
        this.lastLocationAccess = lastLocationAccess;

        // The input to floating reads must be always non-null or have at least a guard.
        assert guard != null || !(address.getBase().stamp(NodeView.DEFAULT) instanceof ObjectStamp) || address.getBase() instanceof ValuePhiNode ||
                        ((ObjectStamp) address.getBase().stamp(NodeView.DEFAULT)).nonNull() : address.getBase();

        assert barrierType == BarrierType.NONE || stamp.isObjectStamp() : "incorrect barrier on non-object type: " + location;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill newlla) {
        updateUsagesInterface(lastLocationAccess, newlla);
        lastLocationAccess = newlla;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        if (getBarrierType() != BarrierType.NONE && gen.getLIRGeneratorTool().getBarrierSet() != null) {
            gen.setResult(this, gen.getLIRGeneratorTool().getBarrierSet().emitBarrieredLoad(readKind, gen.operand(address), null, MemoryOrderMode.PLAIN, getBarrierType()));
        } else {
            gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitLoad(readKind, gen.operand(address), null, MemoryOrderMode.PLAIN, MemoryExtendKind.DEFAULT));
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node result = ReadNode.canonicalizeRead(this, getAddress(), getLocationIdentity(), tool);
        if (result != this) {
            return result;
        }
        if (tool.canonicalizeReads() && getAddress().hasMoreThanOneUsage() && lastLocationAccess instanceof WriteNode) {
            WriteNode write = (WriteNode) lastLocationAccess;
            if (write.getAddress() == getAddress() && write.getAccessStamp(NodeView.DEFAULT).isCompatible(getAccessStamp(NodeView.DEFAULT))) {
                // Same memory location with no intervening write
                return write.value();
            }
        }
        return this;
    }

    @SuppressWarnings("try")
    @Override
    public FixedAccessNode asFixedNode() {
        try (DebugCloseable position = withNodeSourcePosition()) {
            ReadNode result = graph().add(new ReadNode(getAddress(), getLocationIdentity(), stamp(NodeView.DEFAULT), getBarrierType(), MemoryOrderMode.PLAIN));
            result.setGuard(getGuard());
            return result;
        }
    }

    @Override
    public boolean verifyNode() {
        MemoryKill lla = getLastLocationAccess();
        assert lla != null || getLocationIdentity().isImmutable() : "lastLocationAccess of " + this + " shouldn't be null for mutable location identity " + getLocationIdentity();
        return super.verifyNode();
    }

    @Override
    public Stamp getAccessStamp(NodeView view) {
        return stamp(view);
    }

}
