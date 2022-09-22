/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class VirtualFrameGetNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameGetNode> TYPE = NodeClass.create(VirtualFrameGetNode.class);

    private final byte accessFlags;
    private final JavaKind accessKind;

    public VirtualFrameGetNode(Receiver frame, int frameSlotIndex, JavaKind accessKind, int accessTag, VirtualFrameAccessType type, byte accessFlags) {
        super(TYPE, StampFactory.forKind(accessKind), frame, frameSlotIndex, accessTag, type);
        this.accessFlags = accessFlags;
        this.accessKind = accessKind;
    }

    public VirtualFrameGetNode(Receiver frame, int frameSlotIndex, JavaKind accessKind, int accessTag, VirtualFrameAccessType type) {
        this(frame, frameSlotIndex, accessKind, accessTag, type, VirtualFrameAccessFlags.NON_STATIC);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.getTagArray(type));
        ValueNode dataAlias = tool.getAlias(
                        TruffleCompilerRuntime.getRuntime().getJavaKindForFrameSlotKind(accessTag) == JavaKind.Object ? frame.getObjectArray(type) : frame.getPrimitiveArray(type));

        if (type == VirtualFrameAccessType.Auxiliary) {
            // no tags array
            if (dataAlias instanceof VirtualObjectNode) {
                VirtualObjectNode dataVirtual = (VirtualObjectNode) dataAlias;

                if (frameSlotIndex < dataVirtual.entryCount()) {
                    ValueNode dataEntry = tool.getEntry(dataVirtual, frameSlotIndex);
                    tool.replaceWith(dataEntry);
                    return;
                }
            }
        } else if (tagAlias instanceof VirtualObjectNode && dataAlias instanceof VirtualObjectNode) {
            VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;
            VirtualObjectNode dataVirtual = (VirtualObjectNode) dataAlias;

            if (frameSlotIndex < tagVirtual.entryCount() && frameSlotIndex < dataVirtual.entryCount()) {
                ValueNode actualTag = tool.getEntry(tagVirtual, frameSlotIndex);
                final boolean staticAccess = (accessFlags & VirtualFrameAccessFlags.STATIC_FLAG) != 0;
                if (!staticAccess && (!actualTag.isConstant() || actualTag.asJavaConstant().asInt() != accessTag)) {
                    /*
                     * We cannot constant fold the tag-check immediately, so we need to create a
                     * guard comparing the actualTag with the accessTag.
                     */
                    LogicNode comparison = new IntegerEqualsNode(actualTag, getConstant(accessTag));
                    tool.addNode(comparison);
                    tool.addNode(new FixedGuardNode(comparison, DeoptimizationReason.RuntimeConstraint, DeoptimizationAction.InvalidateRecompile));
                }

                ValueNode dataEntry = tool.getEntry(dataVirtual, frameSlotIndex);
                if (staticAccess) {
                    // bytecode OSR frame transfer puts raw longs in the virtual array. Trust usages
                    // of static access to do the right thing.
                    ValueNode narrowedEntry = maybeNarrowForOSRStaticAccess(tool, dataEntry);
                    if (dataEntry != narrowedEntry) {
                        tool.setVirtualEntry(dataVirtual, frameSlotIndex, narrowedEntry, JavaKind.Long, -1);
                        dataEntry = narrowedEntry;
                    }
                }

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

    /**
     * Converts raw longs read from the parent frame to the required primitive type, so they can be
     * virtualized and fed into later {@link VirtualFrameGetNode}.
     */
    private ValueNode maybeNarrowForOSRStaticAccess(VirtualizerTool tool, ValueNode value) {
        if (!accessKind.isPrimitive() || !isOSRRawStaticAccess(value)) {
            return value;
        }
        if (accessKind == JavaKind.Boolean) {
            // Special handling for boolean slots.
            // Canonically equivalent to:
            // (int) value != 0;
            LogicNode logicNode = new IntegerEqualsNode(value, ConstantNode.forLong(0, graph()));
            tool.addNode(logicNode);
            ValueNode conditional = new ConditionalNode(logicNode, ConstantNode.forInt(0, graph()), ConstantNode.forInt(1, graph()));
            tool.addNode(conditional);
            return conditional;
        }
        Stamp valueStamp = value.stamp(NodeView.DEFAULT);
        if (!(valueStamp instanceof PrimitiveStamp)) {
            return value;
        }
        assert value.getStackKind() == JavaKind.Long && accessKind.isPrimitive();
        int targetBits = accessKind.getBitCount();
        ValueNode tmpValue = value;
        int longBits = JavaKind.Long.getBitCount();
        if (targetBits < longBits) {
            tmpValue = new NarrowNode(tmpValue, targetBits);
            tool.addNode(tmpValue);
        }
        int intBits = JavaKind.Int.getBitCount();
        if (targetBits < intBits) {
            assert accessKind == JavaKind.Byte;
            /*
             * Narrowed too much, need to make a stack value. Note that the narrow + sign-extends
             * provides the correct stamp for the value (i32[-128, 127]). A single narrow to int
             * would give the full i32 stamp.
             */
            tmpValue = new SignExtendNode(tmpValue, JavaKind.Int.getBitCount());
            tool.addNode(tmpValue);
        }
        if (accessKind.isNumericFloat()) {
            tmpValue = new ReinterpretNode(accessKind, tmpValue);
            tool.addNode(tmpValue);
        }
        return tmpValue;
    }

    /*
     * Best effort to guess if a given frame slot is filled by bytecode OSR frame transfer.
     */
    private boolean isOSRRawStaticAccess(ValueNode dataEntry) {
        if ((accessFlags & VirtualFrameAccessFlags.STATIC_FLAG) == 0) {
            return false;
        }
        if (dataEntry.stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Long) {
            return true;
        }
        return false;
    }
}
