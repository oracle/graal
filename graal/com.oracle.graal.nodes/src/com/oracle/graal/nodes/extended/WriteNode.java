/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.LocationNode.Location;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode memory location}.
 */
@NodeInfo
public class WriteNode extends AbstractWriteNode implements LIRLowerable, Simplifiable, Virtualizable {

    public static WriteNode create(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType) {
        return USE_GENERATED_NODES ? new WriteNodeGen(object, value, location, barrierType) : new WriteNode(object, value, location, barrierType);
    }

    WriteNode(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType) {
        super(object, value, location, barrierType);
    }

    public static WriteNode create(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean initialization) {
        return USE_GENERATED_NODES ? new WriteNodeGen(object, value, location, barrierType, initialization) : new WriteNode(object, value, location, barrierType, initialization);
    }

    WriteNode(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, boolean initialization) {
        super(object, value, location, barrierType, initialization);
    }

    public static WriteNode create(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, GuardingNode guard, boolean initialization) {
        return USE_GENERATED_NODES ? new WriteNodeGen(object, value, location, barrierType, guard, initialization) : new WriteNode(object, value, location, barrierType, guard, initialization);
    }

    WriteNode(ValueNode object, ValueNode value, ValueNode location, BarrierType barrierType, GuardingNode guard, boolean initialization) {
        super(object, value, location, barrierType, guard, initialization);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value address = location().generateAddress(gen, gen.getLIRGeneratorTool(), gen.operand(object()));
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(value().stamp());
        gen.getLIRGeneratorTool().emitStore(writeKind, address, gen.operand(value()), gen.state(this));
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (object() instanceof PiNode && ((PiNode) object()).getGuard() == getGuard()) {
            setObject(((PiNode) object()).getOriginalNode());
        }
    }

    @NodeIntrinsic
    public static native void writeMemory(Object object, Object value, Location location, @ConstantNodeParameter BarrierType barrierType);

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
