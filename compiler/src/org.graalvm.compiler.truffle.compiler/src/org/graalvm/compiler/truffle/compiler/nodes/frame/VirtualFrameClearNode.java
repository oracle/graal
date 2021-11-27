/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public class VirtualFrameClearNode extends VirtualFrameAccessorNode implements Virtualizable, IterableNodeType {
    public static final NodeClass<VirtualFrameClearNode> TYPE = NodeClass.create(VirtualFrameClearNode.class);

    public VirtualFrameClearNode(Receiver frame, int frameSlotIndex, int illegalTag, VirtualFrameAccessType type) {
        super(TYPE, StampFactory.forVoid(), frame, frameSlotIndex, illegalTag, type);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.getTagArray(type));
        ValueNode localsAlias = tool.getAlias(frame.getObjectArray(type));
        ValueNode primitiveAlias = tool.getAlias(frame.getPrimitiveArray(type));
        if (tagAlias instanceof VirtualObjectNode && localsAlias instanceof VirtualObjectNode && primitiveAlias instanceof VirtualObjectNode) {
            VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;
            VirtualObjectNode localsVirtual = (VirtualObjectNode) localsAlias;
            VirtualObjectNode primitiveVirtual = (VirtualObjectNode) primitiveAlias;
            if (frameSlotIndex < tagVirtual.entryCount()) {
                // Simply set kind to illegal. A later phase will clear the slots.
                JavaKind tagKind = tagVirtual.entryKind(tool.getMetaAccessExtensionProvider(), frameSlotIndex);
                if (tool.setVirtualEntry(tagVirtual, frameSlotIndex, getConstant(accessTag), tagKind, -1) &&
                                tool.setVirtualEntry(localsVirtual, frameSlotIndex, ConstantNode.defaultForKind(JavaKind.Object, graph()), JavaKind.Object, -1) &&
                                tool.setVirtualEntry(primitiveVirtual, frameSlotIndex, ConstantNode.defaultForKind(JavaKind.Long, graph()), JavaKind.Long, -1)) {
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
