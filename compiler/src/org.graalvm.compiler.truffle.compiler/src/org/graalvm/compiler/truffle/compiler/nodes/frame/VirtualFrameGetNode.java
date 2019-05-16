/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class VirtualFrameGetNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameGetNode> TYPE = NodeClass.create(VirtualFrameGetNode.class);

    public VirtualFrameGetNode(Receiver frame, int frameSlotIndex, JavaKind accessKind, int accessTag) {
        super(TYPE, StampFactory.forKind(accessKind), frame, frameSlotIndex, accessTag);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.virtualFrameTagArray);
        ValueNode dataAlias = tool.getAlias(
                        TruffleCompilerRuntime.getRuntime().getJavaKindForFrameSlotKind(accessTag) == JavaKind.Object ? frame.virtualFrameObjectArray : frame.virtualFramePrimitiveArray);

        if (tagAlias instanceof VirtualObjectNode && dataAlias instanceof VirtualObjectNode) {
            VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;
            VirtualObjectNode dataVirtual = (VirtualObjectNode) dataAlias;

            if (frameSlotIndex < tagVirtual.entryCount() && frameSlotIndex < dataVirtual.entryCount()) {
                ValueNode actualTag = tool.getEntry(tagVirtual, frameSlotIndex);
                if (!actualTag.isConstant() || actualTag.asJavaConstant().asInt() != accessTag) {
                    /*
                     * We cannot constant fold the tag-check immediately, so we need to create a
                     * guard comparing the actualTag with the accessTag.
                     */
                    LogicNode comparison = new IntegerEqualsNode(actualTag, getConstant(accessTag));
                    tool.addNode(comparison);
                    tool.addNode(new FixedGuardNode(comparison, DeoptimizationReason.RuntimeConstraint, DeoptimizationAction.InvalidateRecompile));
                }

                ValueNode dataEntry = tool.getEntry(dataVirtual, frameSlotIndex);
                if (dataEntry.getStackKind() == getStackKind()) {
                    tool.replaceWith(dataEntry);
                    return;
                }
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
