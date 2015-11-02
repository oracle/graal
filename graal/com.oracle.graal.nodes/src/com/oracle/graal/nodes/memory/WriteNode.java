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

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.LocationIdentity;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Simplifiable;
import com.oracle.graal.graph.spi.SimplifierTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.memory.address.AddressNode.Address;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode memory location}.
 */
@NodeInfo(nameTemplate = "Write#{p#location/s}")
public class WriteNode extends AbstractWriteNode implements LIRLowerable, Simplifiable, Virtualizable {

    public static final NodeClass<WriteNode> TYPE = NodeClass.create(WriteNode.class);

    private WriteNode(ValueNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        this((AddressNode) address, location, value, barrierType);
    }

    public WriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(TYPE, address, location, value, barrierType);
    }

    public WriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, boolean initialization) {
        super(TYPE, address, location, value, barrierType, initialization);
    }

    public WriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, GuardingNode guard, boolean initialization) {
        this(TYPE, address, location, value, barrierType, guard, initialization);
    }

    protected WriteNode(NodeClass<? extends WriteNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, GuardingNode guard, boolean initialization) {
        super(c, address, location, value, barrierType, guard, initialization);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(value().stamp());
        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this));
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (getAddress() instanceof OffsetAddressNode) {
            OffsetAddressNode objAddress = (OffsetAddressNode) getAddress();
            if (objAddress.getBase() instanceof PiNode && ((PiNode) objAddress.getBase()).getGuard() == getGuard()) {
                OffsetAddressNode newAddress = graph().unique(new OffsetAddressNode(((PiNode) objAddress.getBase()).getOriginalNode(), objAddress.getOffset()));
                setAddress(newAddress);
                tool.addToWorkList(newAddress);
            }
        }
    }

    @NodeIntrinsic
    public static native void writeMemory(Address address, @ConstantNodeParameter LocationIdentity location, Object value, @ConstantNodeParameter BarrierType barrierType);

    @Override
    public void virtualize(VirtualizerTool tool) {
        throw JVMCIError.shouldNotReachHere("unexpected WriteNode before PEA");
    }

    public boolean canNullCheck() {
        return true;
    }
}
