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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * Reads an {@linkplain AccessNode accessed} value.
 */
public final class ReadNode extends FloatableAccessNode implements Node.IterableNodeType, LIRLowerable, Canonicalizable, PiPushable, Virtualizable {

    public ReadNode(ValueNode object, ValueNode location, Stamp stamp, BarrierType barrierType, boolean compressible) {
        super(object, location, stamp, barrierType, compressible);
    }

    public ReadNode(ValueNode object, ValueNode location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean compressible) {
        super(object, location, stamp, guard, barrierType, compressible);
    }

    public ReadNode(ValueNode object, int displacement, LocationIdentity locationIdentity, Kind kind) {
        super(object, ConstantLocationNode.create(locationIdentity, kind, displacement, object.graph()), StampFactory.forKind(kind));
    }

    private ReadNode(ValueNode object, ValueNode location, ValueNode guard) {
        /*
         * Used by node intrinsics. Since the initial value for location is a parameter, i.e., a
         * LocalNode, the constructor cannot use the declared type LocationNode.
         */
        super(object, location, StampFactory.forNodeIntrinsic(), (GuardingNode) guard, BarrierType.NONE, false);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        Value address = location().generateAddress(gen, gen.operand(object()));
        gen.setResult(this, gen.emitLoad(location().getValueKind(), address, this));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        return canonicalizeRead(this, location(), object(), tool, isCompressible());
    }

    @Override
    public FloatingAccessNode asFloatingNode(ValueNode lastLocationAccess) {
        return graph().unique(new FloatingReadNode(object(), location(), lastLocationAccess, stamp(), getGuard(), getBarrierType(), isCompressible()));
    }

    public static ValueNode canonicalizeRead(ValueNode read, LocationNode location, ValueNode object, CanonicalizerTool tool, boolean compressible) {
        MetaAccessProvider runtime = tool.runtime();
        if (read.usages().count() == 0) {
            // Read without usages can be savely removed.
            return null;
        }
        if (tool.canonicalizeReads() && runtime != null && object != null && object.isConstant()) {
            if (location.getLocationIdentity() == LocationIdentity.FINAL_LOCATION && location instanceof ConstantLocationNode) {
                long displacement = ((ConstantLocationNode) location).getDisplacement();
                Kind kind = location.getValueKind();
                if (object.kind() == Kind.Object) {
                    Object base = object.asConstant().asObject();
                    if (base != null) {
                        Constant constant = tool.runtime().readUnsafeConstant(kind, base, displacement, compressible);
                        if (constant != null) {
                            return ConstantNode.forConstant(constant, runtime, read.graph());
                        }
                    }
                } else if (object.kind() == Kind.Long || object.kind().getStackKind() == Kind.Int) {
                    long base = object.asConstant().asLong();
                    if (base != 0L) {
                        Constant constant = tool.runtime().readUnsafeConstant(kind, null, base + displacement, compressible);
                        if (constant != null) {
                            return ConstantNode.forConstant(constant, runtime, read.graph());
                        }
                    }
                }
            }
        }
        return read;
    }

    @Override
    public boolean push(PiNode parent) {
        if (location() instanceof ConstantLocationNode) {
            long displacement = ((ConstantLocationNode) location()).getDisplacement();
            if (parent.stamp() instanceof ObjectStamp) {
                ObjectStamp piStamp = (ObjectStamp) parent.stamp();
                ResolvedJavaType receiverType = piStamp.type();
                if (receiverType != null) {
                    ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(displacement);

                    if (field != null) {
                        ResolvedJavaType declaringClass = field.getDeclaringClass();
                        if (declaringClass.isAssignableFrom(receiverType) && declaringClass != receiverType && parent.object().stamp() instanceof ObjectStamp) {
                            ObjectStamp piValueStamp = (ObjectStamp) parent.object().stamp();
                            if (piStamp.nonNull() == piValueStamp.nonNull() && piStamp.alwaysNull() == piValueStamp.alwaysNull()) {
                                replaceFirstInput(parent, parent.object());
                                return true;
                            }
                        }
                    }
                }
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
}
