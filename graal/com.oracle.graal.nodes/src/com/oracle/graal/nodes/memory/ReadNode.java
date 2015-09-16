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

import static com.oracle.graal.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.LocationIdentity;
import jdk.internal.jvmci.meta.MetaAccessProvider;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.CanonicalizableLocation;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.extended.ValueAnchorNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.util.GraphUtil;

/**
 * Reads an {@linkplain FixedAccessNode accessed} value.
 */
@NodeInfo(nameTemplate = "Read#{p#location/s}")
public class ReadNode extends FloatableAccessNode implements LIRLowerable, Canonicalizable, Virtualizable, GuardingNode {

    public static final NodeClass<ReadNode> TYPE = NodeClass.create(ReadNode.class);

    public ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, BarrierType barrierType) {
        super(TYPE, address, location, stamp, null, barrierType);
    }

    public ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        super(TYPE, address, location, stamp, guard, barrierType);
    }

    public ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck, FrameState stateBefore) {
        this(TYPE, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
    }

    protected ReadNode(NodeClass<? extends ReadNode> c, AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck,
                    FrameState stateBefore) {
        super(c, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
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
            if (locationIdentity.equals(ARRAY_LENGTH_LOCATION)) {
                ValueNode length = GraphUtil.arrayLength(object);
                if (length != null) {
                    // TODO Does this need a PiCastNode to the positive range?
                    return length;
                }
            }
            if (locationIdentity instanceof CanonicalizableLocation) {
                CanonicalizableLocation canonicalize = (CanonicalizableLocation) locationIdentity;
                ValueNode result = canonicalize.canonicalizeRead(read, address, object, tool);
                assert result != null && result.stamp().isCompatible(read.stamp());
                return result;
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
