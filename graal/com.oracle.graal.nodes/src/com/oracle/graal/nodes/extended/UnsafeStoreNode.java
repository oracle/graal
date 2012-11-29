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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

/**
 * Store of a value at a location specified as an offset relative to an object.
 * No null check is performed before the store.
 */
public class UnsafeStoreNode extends UnsafeAccessNode implements StateSplit, Lowerable, Virtualizable {

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
    public void lower(LoweringTool tool) {
        tool.getRuntime().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        VirtualObjectNode virtual = tool.getVirtualState(object());
        if (virtual != null) {
            ValueNode indexValue = tool.getReplacedValue(offset());
            if (indexValue.isConstant()) {
                int fieldIndex = virtual.fieldIndexForOffset(indexValue.asConstant().asLong());
                if (fieldIndex != -1) {
                    tool.setVirtualEntry(virtual, fieldIndex, value());
                    tool.delete();
                }
            }
        }
    }

    // specialized on value type until boxing/unboxing is sorted out in intrinsification
    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, Object value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, boolean value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, byte value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, char value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, double value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, float value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, int value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, long value, @ConstantNodeParameter Kind kind);

    @NodeIntrinsic
    public static native void store(Object object, @ConstantNodeParameter int displacement, long offset, short value, @ConstantNodeParameter Kind kind);

}
