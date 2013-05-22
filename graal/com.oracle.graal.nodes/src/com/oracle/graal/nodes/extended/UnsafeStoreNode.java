/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.graph.UnsafeAccess.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Store of a value at a location specified as an offset relative to an object. No null check is
 * performed before the store.
 */
public class UnsafeStoreNode extends UnsafeAccessNode implements StateSplit, Lowerable, Virtualizable, Canonicalizable, MemoryCheckpoint {

    @Input private ValueNode value;
    @Input(notDataflow = true) private FrameState stateAfter;

    public UnsafeStoreNode(ValueNode object, int displacement, ValueNode offset, ValueNode value, Kind accessKind) {
        this(StampFactory.forVoid(), object, displacement, offset, value, accessKind);
    }

    public UnsafeStoreNode(Stamp stamp, ValueNode object, int displacement, ValueNode offset, ValueNode value, Kind accessKind) {
        super(stamp, object, displacement, offset, accessKind);
        assert accessKind != Kind.Void && accessKind != Kind.Illegal;
        this.value = value;
    }

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public LocationIdentity[] getLocationIdentities() {
        return new LocationIdentity[]{ANY_LOCATION};
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object());
        if (state != null && state.getState() == EscapeState.Virtual) {
            ValueNode indexValue = tool.getReplacedValue(offset());
            if (indexValue.isConstant()) {
                long offset = indexValue.asConstant().asLong() + displacement();
                int entryIndex = state.getVirtualObject().entryIndexForOffset(offset);
                if (entryIndex != -1 && state.getVirtualObject().entryKind(entryIndex) == accessKind()) {
                    tool.setVirtualEntry(state, entryIndex, value());
                    tool.delete();
                }
            }
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        if (offset().isConstant()) {
            long constantOffset = offset().asConstant().asLong();
            if (constantOffset != 0) {
                int intDisplacement = (int) (constantOffset + displacement());
                if (constantOffset == intDisplacement) {
                    Graph graph = this.graph();
                    return graph.add(new UnsafeStoreNode(this.stamp(), object(), intDisplacement, graph.unique(ConstantNode.forInt(0, graph)), value(), accessKind()));
                }
            } else if (object().stamp() instanceof ObjectStamp) { // TODO (gd) remove that once
                                                                  // UnsafeAccess only have an
                                                                  // object base
                ObjectStamp receiverStamp = object().objectStamp();
                if (receiverStamp.nonNull()) {
                    ResolvedJavaType receiverType = receiverStamp.type();
                    ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(displacement());
                    if (field != null) {
                        return this.graph().add(new StoreFieldNode(object(), field, value()));
                    }
                }
            }
        }
        return this;
    }

    // specialized on value type until boxing/unboxing is sorted out in intrinsification

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, Object value, @ConstantNodeParameter Kind kind) {
        unsafe.putObject(object, offset + displacement, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, boolean value, @ConstantNodeParameter Kind kind) {
        unsafe.putBoolean(object, offset + displacement, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, byte value, @ConstantNodeParameter Kind kind) {
        unsafe.putByte(object, offset + displacement, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, char value, @ConstantNodeParameter Kind kind) {
        unsafe.putChar(object, offset + displacement, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, double value, @ConstantNodeParameter Kind kind) {
        unsafe.putDouble(object, offset + displacement, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, float value, @ConstantNodeParameter Kind kind) {
        unsafe.putFloat(object, offset + displacement, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, int value, @ConstantNodeParameter Kind kind) {
        unsafe.putInt(object, offset + displacement, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, long value, @ConstantNodeParameter Kind kind) {
        unsafe.putLong(object, offset + displacement, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, @ConstantNodeParameter int displacement, long offset, short value, @ConstantNodeParameter Kind kind) {
        unsafe.putShort(object, offset + displacement, value);
    }
}
