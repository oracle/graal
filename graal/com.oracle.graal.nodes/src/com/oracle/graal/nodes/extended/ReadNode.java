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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * Reads an {@linkplain FixedAccessNode accessed} value.
 */
public final class ReadNode extends FloatableAccessNode implements LIRLowerable, Canonicalizable, PiPushable, Virtualizable, GuardingNode {

    public ReadNode(ValueNode object, ValueNode location, Stamp stamp, BarrierType barrierType, boolean compressible) {
        super(object, location, stamp, barrierType, compressible);
    }

    public ReadNode(ValueNode object, ValueNode location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean compressible) {
        super(object, location, stamp, guard, barrierType, compressible);
    }

    private ReadNode(ValueNode object, ValueNode location, ValueNode guard, BarrierType barrierType, boolean compressible) {
        /*
         * Used by node intrinsics. Really, you can trust me on that! Since the initial value for
         * location is a parameter, i.e., a ParameterNode, the constructor cannot use the declared
         * type LocationNode.
         */
        super(object, location, StampFactory.forNodeIntrinsic(), (GuardingNode) guard, barrierType, compressible);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value address = location().generateAddress(gen, gen.getLIRGeneratorTool(), gen.operand(object()));
        PlatformKind readKind = gen.getLIRGeneratorTool().getPlatformKind(stamp());
        gen.setResult(this, gen.getLIRGeneratorTool().emitLoad(readKind, address, gen.state(this)));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object() instanceof PiNode && ((PiNode) object()).getGuard() == getGuard()) {
            ReadNode readNode = graph().add(new ReadNode(((PiNode) object()).getOriginalNode(), location(), stamp(), getGuard(), getBarrierType(), isCompressible()));
            readNode.setNullCheck(getNullCheck());
            readNode.setStateBefore(stateBefore());
            return readNode;
        }
        return canonicalizeRead(this, location(), object(), tool, isCompressible());
    }

    @Override
    public FloatingAccessNode asFloatingNode(MemoryNode lastLocationAccess) {
        return graph().unique(new FloatingReadNode(object(), location(), lastLocationAccess, stamp(), getGuard(), getBarrierType(), isCompressible()));
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return (getNullCheck() && type == InputType.Guard) ? true : super.isAllowedUsageType(type);
    }

    public static ValueNode canonicalizeRead(ValueNode read, LocationNode location, ValueNode object, CanonicalizerTool tool, boolean compressible) {
        MetaAccessProvider metaAccess = tool.getMetaAccess();
        if (read.usages().isEmpty()) {
            GuardingNode guard = ((Access) read).getGuard();
            if (guard != null && !(guard instanceof FixedNode)) {
                // The guard is necessary even if the read goes away.
                return read.graph().add(new ValueAnchorNode((ValueNode) guard));
            } else {
                // Read without usages or guard can be safely removed.
                return null;
            }
        }
        if (tool.canonicalizeReads()) {
            if (metaAccess != null && object != null && object.isConstant() && !compressible) {
                if ((location.getLocationIdentity() == LocationIdentity.FINAL_LOCATION || location.getLocationIdentity() == LocationIdentity.ARRAY_LENGTH_LOCATION) &&
                                location instanceof ConstantLocationNode) {
                    long displacement = ((ConstantLocationNode) location).getDisplacement();
                    Constant base = object.asConstant();
                    if (base != null) {
                        Constant constant;
                        if (read.stamp() instanceof PrimitiveStamp) {
                            PrimitiveStamp stamp = (PrimitiveStamp) read.stamp();
                            constant = tool.getConstantReflection().readRawConstant(stamp.getStackKind(), base, displacement, stamp.getBits());
                        } else {
                            assert read.stamp() instanceof ObjectStamp;
                            constant = tool.getConstantReflection().readUnsafeConstant(Kind.Object, base, displacement);
                        }
                        if (constant != null) {
                            return ConstantNode.forConstant(read.stamp(), constant, metaAccess, read.graph());
                        }
                    }
                }
            }
            if (location.getLocationIdentity() == LocationIdentity.ARRAY_LENGTH_LOCATION && object instanceof ArrayLengthProvider) {
                ValueNode length = ((ArrayLengthProvider) object).length();
                if (length != null) {
                    // TODO Does this need a PiCastNode to the positive range?
                    return length;
                }
            }
        }
        return read;
    }

    @Override
    public boolean push(PiNode parent) {
        if (!(location() instanceof ConstantLocationNode && parent.stamp() instanceof ObjectStamp && parent.object().stamp() instanceof ObjectStamp)) {
            return false;
        }

        ObjectStamp piStamp = (ObjectStamp) parent.stamp();
        ResolvedJavaType receiverType = piStamp.type();
        if (receiverType == null) {
            return false;
        }

        ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(((ConstantLocationNode) location()).getDisplacement());
        if (field == null) {
            // field was not declared by receiverType
            return false;
        }

        ObjectStamp valueStamp = (ObjectStamp) parent.object().stamp();
        ResolvedJavaType valueType = StampTool.typeOrNull(valueStamp);
        if (valueType != null && field.getDeclaringClass().isAssignableFrom(valueType)) {
            if (piStamp.nonNull() == valueStamp.nonNull() && piStamp.alwaysNull() == valueStamp.alwaysNull()) {
                replaceFirstInput(parent, parent.object());
                return true;
            }
        }

        return false;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (location() instanceof ConstantLocationNode) {
            ConstantLocationNode constantLocation = (ConstantLocationNode) location();
            State state = tool.getObjectState(object());
            if (state != null && state.getState() == EscapeState.Virtual) {
                VirtualObjectNode virtual = state.getVirtualObject();
                int entryIndex = virtual.entryIndexForOffset(constantLocation.getDisplacement());
                if (entryIndex != -1 && virtual.entryKind(entryIndex) == constantLocation.getValueKind()) {
                    tool.replaceWith(state.getEntry(entryIndex));
                }
            }
        }
    }

    public boolean canNullCheck() {
        return true;
    }
}
