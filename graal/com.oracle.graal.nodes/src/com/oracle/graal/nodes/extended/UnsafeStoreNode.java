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

import static com.oracle.graal.graph.UnsafeAccess.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Store of a value at a location specified as an offset relative to an object. No null check is
 * performed before the store.
 */
public class UnsafeStoreNode extends UnsafeAccessNode implements StateSplit, Lowerable, Virtualizable, MemoryCheckpoint.Single {

    @Input private ValueNode value;
    @Input(notDataflow = true) private FrameState stateAfter;

    public UnsafeStoreNode(ValueNode object, ValueNode offset, ValueNode value, Kind accessKind) {
        this(object, offset, value, accessKind, LocationIdentity.ANY_LOCATION);
    }

    public UnsafeStoreNode(ValueNode object, ValueNode offset, ValueNode value, Kind accessKind, LocationIdentity locationIdentity) {
        super(StampFactory.forVoid(), object, offset, accessKind, locationIdentity);
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
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(object());
        if (state != null && state.getState() == EscapeState.Virtual) {
            ValueNode indexValue = tool.getReplacedValue(offset());
            if (indexValue.isConstant()) {
                long offset = indexValue.asConstant().asLong();
                int entryIndex = state.getVirtualObject().entryIndexForOffset(offset);
                if (entryIndex != -1) {
                    ValueNode entry = state.getEntry(entryIndex);
                    if (entry.kind() == this.accessKind() || state.getVirtualObject().entryKind(entryIndex) == this.accessKind()) {
                        tool.setVirtualEntry(state, entryIndex, value());
                        tool.delete();
                    }
                }
            }
        }
    }

    @Override
    protected ValueNode cloneAsFieldAccess(ResolvedJavaField field) {
        StoreFieldNode storeFieldNode = graph().add(new StoreFieldNode(object(), field, value()));
        storeFieldNode.setStateAfter(stateAfter());
        return storeFieldNode;
    }

    public FrameState getState() {
        return stateAfter;
    }

    // specialized on value type until boxing/unboxing is sorted out in intrinsification

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, Object value, @ConstantNodeParameter Kind kind) {
        unsafe.putObject(object, offset, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, boolean value, @ConstantNodeParameter Kind kind) {
        unsafe.putBoolean(object, offset, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, byte value, @ConstantNodeParameter Kind kind) {
        unsafe.putByte(object, offset, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, char value, @ConstantNodeParameter Kind kind) {
        unsafe.putChar(object, offset, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, double value, @ConstantNodeParameter Kind kind) {
        unsafe.putDouble(object, offset, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, float value, @ConstantNodeParameter Kind kind) {
        unsafe.putFloat(object, offset, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, int value, @ConstantNodeParameter Kind kind) {
        unsafe.putInt(object, offset, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, long value, @ConstantNodeParameter Kind kind) {
        unsafe.putLong(object, offset, value);
    }

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void store(Object object, long offset, short value, @ConstantNodeParameter Kind kind) {
        unsafe.putShort(object, offset, value);
    }
}
