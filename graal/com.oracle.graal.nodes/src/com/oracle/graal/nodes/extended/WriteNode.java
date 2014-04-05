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
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.LocationNode.Location;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode memory location}.
 */
public final class WriteNode extends AbstractWriteNode implements LIRLowerable, Simplifiable, Virtualizable {

    public WriteNode(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean compressible) {
        super(object, value, location, barrierType, compressible);
    }

    public WriteNode(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean compressible, boolean initialization) {
        super(object, value, location, barrierType, compressible, initialization);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value address = location().generateAddress(gen, gen.operand(object()));
        // It's possible a constant was forced for other usages so inspect the value directly and
        // use a constant if it can be directly stored.
        Value v;
        if (value().isConstant() && gen.getLIRGeneratorTool().canStoreConstant(value().asConstant(), isCompressible())) {
            v = value().asConstant();
        } else {
            v = gen.operand(value());
        }
        PlatformKind writeKind = gen.getLIRGeneratorTool().getPlatformKind(value().stamp());
        gen.getLIRGeneratorTool().emitStore(writeKind, address, v, this);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (object() instanceof PiNode && ((PiNode) object()).getGuard() == getGuard()) {
            setObject(((PiNode) object()).getOriginalValue());
        }
    }

    @NodeIntrinsic
    public static native void writeMemory(Object object, Object value, Location location, @ConstantNodeParameter BarrierType barrierType, @ConstantNodeParameter boolean compressible);

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (location() instanceof ConstantLocationNode) {
            ConstantLocationNode constantLocation = (ConstantLocationNode) location();
            State state = tool.getObjectState(object());
            if (state != null && state.getState() == EscapeState.Virtual) {
                VirtualObjectNode virtual = state.getVirtualObject();
                int entryIndex = virtual.entryIndexForOffset(constantLocation.getDisplacement());
                if (entryIndex != -1 && virtual.entryKind(entryIndex) == constantLocation.getValueKind()) {
                    tool.setVirtualEntry(state, entryIndex, value(), false);
                    tool.delete();
                }
            }
        }
    }

    public boolean canNullCheck() {
        return true;
    }
}
