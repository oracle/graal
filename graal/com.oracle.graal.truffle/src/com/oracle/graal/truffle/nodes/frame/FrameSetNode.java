/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.frame;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.frame.*;

/**
 * Intrinsic node for write access to a Truffle frame.
 */
@NodeInfo(nameTemplate = "FrameSet{p#slotKind/s}{p#frameSlot/s}")
public class FrameSetNode extends FrameAccessNode implements IterableNodeType, Virtualizable, Lowerable {

    @Input private ValueNode value;

    public FrameSetNode(Kind kind, ValueNode frame, ValueNode frameSlot, ValueNode value, ResolvedJavaField field) {
        super(StampFactory.forVoid(), kind, frame, frameSlot, field);
        this.value = value;
    }

    public ValueNode getValue() {
        return value;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (!isConstantFrameSlot()) {
            return;
        }
        assert isValidAccessKind();
        State virtualFrame = tool.getObjectState(getFrame());
        if (virtualFrame == null || virtualFrame.getState() != EscapeState.Virtual) {
            return;
        }
        assert virtualFrame.getVirtualObject().type() == NewFrameNode.FRAME_TYPE : virtualFrame;
        VirtualInstanceNode virtualFrameObject = (VirtualInstanceNode) virtualFrame.getVirtualObject();
        int arrayFieldIndex = virtualFrameObject.fieldIndex(field);
        State virtualArray = tool.getObjectState(virtualFrame.getEntry(arrayFieldIndex));
        assert virtualArray != null;
        ValueNode storedValue = value;
        tool.setVirtualEntry(virtualArray, getSlotIndex(), storedValue);
        tool.delete();
    }

    @Override
    public void lower(LoweringTool tool) {
        assert !(getFrame() instanceof NewFrameNode);
        StructuredGraph structuredGraph = graph();

        LoadFieldNode loadFieldNode = graph().add(new LoadFieldNode(getFrame(), field));
        structuredGraph.addBeforeFixed(this, loadFieldNode);
        FixedWithNextNode storeNode;
        ValueNode slotIndex = getSlotOffset(1, tool.getRuntime());
        if (isTagAccess()) {
            storeNode = graph().add(new StoreIndexedNode(loadFieldNode, slotIndex, getSlotKind(), value));
        } else if (!getSlotKind().isPrimitive()) {
            storeNode = graph().add(new StoreIndexedNode(loadFieldNode, slotIndex, Kind.Object, value));
        } else {
            storeNode = graph().add(new StoreIndexedNode(loadFieldNode, slotIndex, Kind.Long, value));
        }
        structuredGraph.replaceFixedWithFixed(this, storeNode);
        loadFieldNode.lower(tool);
        ((Lowerable) storeNode).lower(tool);
    }

    @NodeIntrinsic
    public static native void set(@ConstantNodeParameter Kind kind, FrameWithoutBoxing frame, FrameSlot slot, Object value, @ConstantNodeParameter ResolvedJavaField field);

    @NodeIntrinsic
    public static native void set(@ConstantNodeParameter Kind kind, FrameWithoutBoxing frame, FrameSlot slot, byte value, @ConstantNodeParameter ResolvedJavaField field);

    @NodeIntrinsic
    public static native void set(@ConstantNodeParameter Kind kind, FrameWithoutBoxing frame, FrameSlot slot, boolean value, @ConstantNodeParameter ResolvedJavaField field);

    @NodeIntrinsic
    public static native void set(@ConstantNodeParameter Kind kind, FrameWithoutBoxing frame, FrameSlot slot, int value, @ConstantNodeParameter ResolvedJavaField field);

    @NodeIntrinsic
    public static native void set(@ConstantNodeParameter Kind kind, FrameWithoutBoxing frame, FrameSlot slot, long value, @ConstantNodeParameter ResolvedJavaField field);

    @NodeIntrinsic
    public static native void set(@ConstantNodeParameter Kind kind, FrameWithoutBoxing frame, FrameSlot slot, double value, @ConstantNodeParameter ResolvedJavaField field);

    @NodeIntrinsic
    public static native void set(@ConstantNodeParameter Kind kind, FrameWithoutBoxing frame, FrameSlot slot, float value, @ConstantNodeParameter ResolvedJavaField field);
}
