/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.nodes.frame;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.truffle.FrameWithoutBoxing;

import com.oracle.truffle.api.frame.FrameSlot;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class VirtualFrameSetNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameSetNode> TYPE = NodeClass.create(VirtualFrameSetNode.class);

    @Input private ValueNode value;

    public VirtualFrameSetNode(NewFrameNode frame, FrameSlot frameSlot, int accessTag, ValueNode value) {
        super(TYPE, StampFactory.forVoid(), frame, frameSlot, accessTag);
        this.value = value;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.virtualFrameTagArray);
        ValueNode dataAlias = tool.getAlias(accessTag == FrameWithoutBoxing.OBJECT_TAG ? frame.virtualFrameObjectArray : frame.virtualFramePrimitiveArray);

        if (tagAlias instanceof VirtualObjectNode && dataAlias instanceof VirtualObjectNode) {
            VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;
            VirtualObjectNode dataVirtual = (VirtualObjectNode) dataAlias;

            int frameSlotIndex = getFrameSlotIndex();
            if (frameSlotIndex < tagVirtual.entryCount() && frameSlotIndex < dataVirtual.entryCount()) {
                tool.setVirtualEntry(tagVirtual, frameSlotIndex, getConstant(accessTag), false);

                ValueNode dataEntry = tool.getEntry(dataVirtual, frameSlotIndex);
                if (dataEntry.getStackKind() == value.getStackKind()) {
                    tool.setVirtualEntry(dataVirtual, frameSlotIndex, value, true);
                    tool.delete();
                    return;
                }
            }
        }

        /*
         * Deoptimization is our only option here. We cannot go back to a UnsafeStoreNode because we
         * do not have a FrameState to use for the memory store.
         */
        insertDeoptimization(tool);
    }
}
