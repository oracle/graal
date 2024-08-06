/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.nodes.frame;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class VirtualFrameGetTagNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameGetTagNode> TYPE = NodeClass.create(VirtualFrameGetTagNode.class);
    private static final int STATIC_TAG = NewFrameNode.FrameSlotKindStaticTag;

    public VirtualFrameGetTagNode(Receiver frame, int frameSlotIndex) {
        super(TYPE, StampFactory.forKind(JavaKind.Byte), frame, frameSlotIndex, 0, VirtualFrameAccessType.Indexed, VirtualFrameAccessFlags.BENIGN);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.getTagArray(type));

        if (tagAlias instanceof VirtualObjectNode) {
            VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;

            if (frameSlotIndex < tagVirtual.entryCount()) {
                ValueNode actualTag = tool.getEntry(tagVirtual, frameSlotIndex);
                if (actualTag.isConstant()) {
                    final int constantTag = actualTag.asJavaConstant().asInt();
                    tool.replaceWith(getConstant(constantTag < STATIC_TAG ? constantTag : STATIC_TAG));
                } else {
                    ValueNode staticTag = getConstant(STATIC_TAG);
                    LogicNode comparison = new IntegerLessThanNode(actualTag, staticTag);
                    tool.addNode(comparison);
                    ConditionalNode result = new ConditionalNode(comparison, actualTag, staticTag);
                    tool.addNode(result);
                    tool.replaceWith(result);
                }
                return;
            }
        }

        /*
         * We could "virtualize" to a UnsafeLoadNode here that remains a memory access. But it is
         * simpler, and consistent with the get and set intrinsification, to deoptimize.
         */
        insertDeoptimization(tool);
    }
}
