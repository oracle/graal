/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.truffle.compiler.nodes.frame;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaConstant;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public class VirtualFrameFreeNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameFreeNode> TYPE = NodeClass.create(VirtualFrameFreeNode.class);

    public VirtualFrameFreeNode(Receiver frame, int frameSlotIndex, int accessTag) {
        super(TYPE, StampFactory.forVoid(), frame, frameSlotIndex, accessTag);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.virtualFrameTagArray);
        ValueNode objectStorageAlias = tool.getAlias(frame.virtualFrameObjectArray);
        ValueNode primitiveStorageAlias = tool.getAlias(frame.virtualFramePrimitiveArray);

        if (tagAlias instanceof VirtualObjectNode && objectStorageAlias instanceof VirtualObjectNode && primitiveStorageAlias instanceof VirtualObjectNode) {
            VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;
            VirtualObjectNode objectStorageVirtual = (VirtualObjectNode) objectStorageAlias;
            VirtualObjectNode primitiveStorageVirtual = (VirtualObjectNode) primitiveStorageAlias;

            if (frameSlotIndex < tagVirtual.entryCount() && frameSlotIndex < objectStorageVirtual.entryCount() && frameSlotIndex < primitiveStorageVirtual.entryCount()) {
                tool.setVirtualEntry(tagVirtual, frameSlotIndex, getConstant(accessTag));

                ValueNode objectEntry = tool.getEntry(objectStorageVirtual, frameSlotIndex);
                ValueNode primitiveEntry = tool.getEntry(primitiveStorageVirtual, frameSlotIndex);
                ValueNode illegal = ConstantNode.forConstant(JavaConstant.forIllegal(), tool.getMetaAccess(), graph());
                boolean success = tool.setVirtualEntry(objectStorageVirtual, frameSlotIndex, illegal, objectEntry.getStackKind(), -1) &&
                                tool.setVirtualEntry(primitiveStorageVirtual, frameSlotIndex, illegal, primitiveEntry.getStackKind(), -1);
                if (success) {
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
