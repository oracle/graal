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
package com.oracle.graal.truffle.nodes.frame;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeCycles;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodeinfo.NodeSize;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;
import com.oracle.graal.truffle.FrameWithoutBoxing;
import com.oracle.truffle.api.frame.FrameSlot;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class VirtualFrameGetNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameGetNode> TYPE = NodeClass.create(VirtualFrameGetNode.class);

    public VirtualFrameGetNode(NewFrameNode frame, FrameSlot frameSlot, JavaKind accessKind, int accessTag) {
        super(TYPE, StampFactory.forKind(accessKind), frame, frameSlot, accessTag);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.virtualFrameTagArray);
        ValueNode dataAlias = tool.getAlias(accessTag == FrameWithoutBoxing.OBJECT_TAG ? frame.virtualFrameObjectArray : frame.virtualFramePrimitiveArray);

        if (tagAlias instanceof VirtualObjectNode && dataAlias instanceof VirtualObjectNode) {
            VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;
            VirtualObjectNode dataVirtual = (VirtualObjectNode) dataAlias;

            ValueNode actualTag = tool.getEntry(tagVirtual, getFrameSlotIndex());
            if (!actualTag.isConstant() || actualTag.asJavaConstant().asInt() != accessTag) {
                /*
                 * We cannot constant fold the tag-check immediately, so we need to create a guard
                 * comparing the actualTag with the accessTag.
                 */
                LogicNode comparison = new IntegerEqualsNode(actualTag, getConstant(accessTag));
                tool.addNode(comparison);
                tool.addNode(new FixedGuardNode(comparison, DeoptimizationReason.TransferToInterpreter, DeoptimizationAction.InvalidateRecompile));
            }

            ValueNode dataEntry = tool.getEntry(dataVirtual, getFrameSlotIndex());
            if (dataEntry.getStackKind() == getStackKind()) {
                tool.replaceWith(dataEntry);
                return;
            }
        }

        /*
         * We could "virtualize" to a UnsafeLoadNode here that remains a memory access. However,
         * that could prevent further escape analysis for parts of the method that actually matter.
         * So we just deoptimize.
         */
        insertDeoptimization(tool);
    }
}
