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
import com.oracle.graal.nodes.util.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.meta.*;

/**
 * Reads an {@linkplain FixedAccessNode accessed} value.
 */
@NodeInfo
public final class ReadNode extends FloatableAccessNode implements LIRLowerable, Canonicalizable, Virtualizable, GuardingNode {

    public static final NodeClass<ReadNode> TYPE = NodeClass.create(ReadNode.class);

    public ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, BarrierType barrierType) {
        super(TYPE, address, location, stamp, null, barrierType);
    }

    public ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        super(TYPE, address, location, stamp, guard, barrierType);
    }

    public ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck, FrameState stateBefore) {
        super(TYPE, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
    }

    public ReadNode(AddressNode address, LocationIdentity location, ValueNode guard, BarrierType barrierType) {
        /*
         * Used by node intrinsics. Really, you can trust me on that! Since the initial value for
         * location is a parameter, i.e., a ParameterNode, the constructor cannot use the declared
         * type LocationNode.
         */
        super(TYPE, address, location, StampFactory.forNodeIntrinsic(), (GuardingNode) guard, barrierType);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(stamp());
        gen.setResult(this, gen.getLIRGeneratorTool().emitLoad(readKind, gen.operand(address), gen.state(this)));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            if (getGuard() != null && !(getGuard() instanceof FixedNode)) {
                // The guard is necessary even if the read goes away.
                return new ValueAnchorNode((ValueNode) getGuard());
            } else {
                // Read without usages or guard can be safely removed.
                return null;
            }
        }
        if (getAddress() instanceof OffsetAddressNode) {
            OffsetAddressNode objAddress = (OffsetAddressNode) getAddress();
            if (objAddress.getBase() instanceof PiNode && ((PiNode) objAddress.getBase()).getGuard() == getGuard()) {
                OffsetAddressNode newAddress = new OffsetAddressNode(((PiNode) objAddress.getBase()).getOriginalNode(), objAddress.getOffset());
                return new ReadNode(newAddress, getLocationIdentity(), stamp(), getGuard(), getBarrierType(), getNullCheck(), stateBefore());
            }
        }
        if (!getNullCheck()) {
            return canonicalizeRead(this, getAddress(), getLocationIdentity(), tool);
        } else {
            // if this read is a null check, then replacing it with the value is incorrect for
            // guard-type usages
            return this;
        }
    }

    @Override
    public FloatingAccessNode asFloatingNode(MemoryNode lastLocationAccess) {
        return graph().unique(new FloatingReadNode(getAddress(), getLocationIdentity(), lastLocationAccess, stamp(), getGuard(), getBarrierType()));
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return (getNullCheck() && type == InputType.Guard) ? true : super.isAllowedUsageType(type);
    }

    public static ValueNode canonicalizeRead(ValueNode read, AddressNode address, LocationIdentity locationIdentity, CanonicalizerTool tool) {
        MetaAccessProvider metaAccess = tool.getMetaAccess();
        if (tool.canonicalizeReads() && address instanceof OffsetAddressNode) {
            OffsetAddressNode objAddress = (OffsetAddressNode) address;
            ValueNode object = objAddress.getBase();
            if (metaAccess != null && object.isConstant() && !object.isNullConstant() && objAddress.getOffset().isConstant()) {
                long displacement = objAddress.getOffset().asJavaConstant().asLong();
                if (locationIdentity.isImmutable()) {
                    Constant constant = read.stamp().readConstant(tool.getConstantReflection().getMemoryAccessProvider(), object.asConstant(), displacement);
                    if (constant != null) {
                        return ConstantNode.forConstant(read.stamp(), constant, metaAccess);
                    }
                }

                Constant constant = tool.getConstantReflection().readConstantArrayElementForOffset(object.asJavaConstant(), displacement);
                if (constant != null) {
                    return ConstantNode.forConstant(read.stamp(), constant, metaAccess);
                }
            }
            if (locationIdentity.equals(LocationIdentity.ARRAY_LENGTH_LOCATION)) {
                ValueNode length = GraphUtil.arrayLength(object);
                if (length != null) {
                    // TODO Does this need a PiCastNode to the positive range?
                    return length;
                }
            }
        }
        return read;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        throw JVMCIError.shouldNotReachHere("unexpected ReadNode before PEA");
    }

    public boolean canNullCheck() {
        return true;
    }
}
