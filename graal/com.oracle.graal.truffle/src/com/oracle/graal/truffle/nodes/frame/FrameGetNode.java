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

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.IterableNodeType;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.frame.*;

/**
 * Intrinsic node for read access to a Truffle frame.
 */
@NodeInfo(nameTemplate = "FrameGet{p#slotKind/s}{p#frameSlot/s}")
public class FrameGetNode extends FrameAccessNode implements IterableNodeType, Virtualizable, Lowerable {

    public FrameGetNode(Kind kind, ValueNode frame, ValueNode slot, ResolvedJavaField field) {
        super(StampFactory.forKind(kind), kind, frame, slot, field);
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
        ValueNode result = virtualArray.getEntry(getSlotIndex());
        State virtualResult = tool.getObjectState(result);
        if (virtualResult != null) {
            tool.replaceWithVirtual(virtualResult.getVirtualObject());
        } else {
            tool.replaceWithValue(result);
        }
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        assert !(getFrame() instanceof NewFrameNode);
        StructuredGraph structuredGraph = graph();

        LoadFieldNode loadFieldNode = graph().add(new LoadFieldNode(getFrame(), field));
        structuredGraph.addBeforeFixed(this, loadFieldNode);
        FixedWithNextNode loadNode;
        if (!getSlotKind().isPrimitive()) {
            ValueNode slotIndex = getSlotOffset(1, tool.getRuntime());
            loadNode = graph().add(new LoadIndexedNode(loadFieldNode, slotIndex, Kind.Object));
        } else if (getSlotKind() == Kind.Byte) {
            ValueNode slotIndex = getSlotOffset(1, tool.getRuntime());
            loadNode = graph().add(new LoadIndexedNode(loadFieldNode, slotIndex, Kind.Byte));
        } else {
            ValueNode slotOffset = getSlotOffset(Unsafe.ARRAY_LONG_INDEX_SCALE, tool.getRuntime());
            loadNode = graph().add(new UnsafeLoadNode(loadFieldNode, Unsafe.ARRAY_LONG_BASE_OFFSET, slotOffset, getSlotKind()));
        }
        structuredGraph.replaceFixedWithFixed(this, loadNode);
    }

    @NodeIntrinsic
    public static native <T> T get(@ConstantNodeParameter Kind kind, FrameWithoutBoxing frame, FrameSlot slot, @ConstantNodeParameter ResolvedJavaField field);
}
