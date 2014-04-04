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
import com.oracle.graal.nodes.virtual.*;

/**
 * Reads an {@linkplain FixedAccessNode accessed} value.
 */
public final class ReadNode extends FloatableAccessNode implements LIRLowerable, Canonicalizable, PiPushable, Virtualizable {

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
        Value address = location().generateAddress(gen, gen.operand(object()));
        PlatformKind readKind = gen.getLIRGeneratorTool().getPlatformKind(stamp());
        gen.setResult(this, gen.getLIRGeneratorTool().emitLoad(readKind, address, this));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object() instanceof PiNode && ((PiNode) object()).getGuard() == getGuard()) {
            return graph().add(new ReadNode(((PiNode) object()).getOriginalValue(), location(), stamp(), getGuard(), getBarrierType(), isCompressible()));
        }
        return canonicalizeRead(this, location(), object(), tool, isCompressible());
    }

    @Override
    public FloatingAccessNode asFloatingNode(MemoryNode lastLocationAccess) {
        return graph().unique(new FloatingReadNode(object(), location(), lastLocationAccess, stamp(), getGuard(), getBarrierType(), isCompressible()));
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
            if (metaAccess != null && object != null && object.isConstant()) {
                if ((location.getLocationIdentity() == LocationIdentity.FINAL_LOCATION || location.getLocationIdentity() == LocationIdentity.ARRAY_LENGTH_LOCATION) &&
                                location instanceof ConstantLocationNode) {
                    long displacement = ((ConstantLocationNode) location).getDisplacement();
                    Kind kind = location.getValueKind();
                    if (object.getKind() == Kind.Object) {
                        Object base = object.asConstant().asObject();
                        if (base != null) {
                            Constant constant = tool.getConstantReflection().readUnsafeConstant(kind, base, displacement, compressible);
                            if (constant != null) {
                                return ConstantNode.forConstant(constant, metaAccess, read.graph());
                            }
                        }
                    } else if (object.getKind().isNumericInteger()) {
                        long base = object.asConstant().asLong();
                        if (base != 0L) {
                            Constant constant = tool.getConstantReflection().readUnsafeConstant(kind, null, base + displacement, compressible);
                            if (constant != null) {
                                return ConstantNode.forConstant(constant, metaAccess, read.graph());
                            }
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
        ResolvedJavaType valueType = ObjectStamp.typeOrNull(valueStamp);
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
