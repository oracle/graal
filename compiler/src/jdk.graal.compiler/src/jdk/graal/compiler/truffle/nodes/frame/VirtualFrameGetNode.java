/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class VirtualFrameGetNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameGetNode> TYPE = NodeClass.create(VirtualFrameGetNode.class);

    private final JavaKind accessKind;

    public VirtualFrameGetNode(Receiver frame, int frameSlotIndex, JavaKind accessKind, int accessTag, VirtualFrameAccessType type, VirtualFrameAccessFlags accessFlags) {
        super(TYPE, StampFactory.forKind(accessKind), frame, frameSlotIndex, accessTag, type, accessFlags);
        this.accessKind = accessKind;
        assert !accessFlags.updatesFrame();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode tagAlias = tool.getAlias(frame.getTagArray(type));

        ValueNode dataAlias = tool.getAlias(accessKind == JavaKind.Object ? frame.getObjectArray(type) : frame.getPrimitiveArray(type));

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
                ensureStaticSlotAccessConsistency();
                ValueNode actualTag = tool.getEntry(tagVirtual, frameSlotIndex);
                final boolean staticAccess = accessFlags.isStatic();
                if (staticAccess && accessKind.isPrimitive() &&
                                (actualTag.isConstant() && actualTag.asJavaConstant().asInt() == 0)) {
                    /*
                     * Reading a primitive from an uninitialized static slot: return the default
                     * value for the access kind. Reading an object can go through the regular
                     * route.
                     *
                     * If it were allowed to go through the normal route, the value stored in the
                     * virtual array will always be an i64, and it would have to be converted to the
                     * access kind before returns. This shortcut ensures both correctness, and
                     * removes the need for conversion.
                     */
                    ValueNode dataEntry = ConstantNode.defaultForKind(getStackKind(), graph());
                    tool.replaceWith(dataEntry);
                    return;
                } else if (!staticAccess && (!actualTag.isConstant() || actualTag.asJavaConstant().asInt() != accessTag)) {
                    /*
                     * We cannot constant fold the tag-check immediately, so we need to create a
                     * guard comparing the actualTag with the accessTag.
                     */
                    LogicNode comparison = new IntegerEqualsNode(actualTag, getConstant(accessTag));
                    tool.addNode(comparison);
                    tool.addNode(new FixedGuardNode(comparison, DeoptimizationReason.RuntimeConstraint, DeoptimizationAction.InvalidateRecompile));
                }

                ValueNode dataEntry = tool.getEntry(dataVirtual, frameSlotIndex);
                dataEntry = maybeNarrowForOSRStaticAccess(tool, dataEntry);

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
        if (!accessKind.isPrimitive() || !isOSRRawStaticAccess()) {
            return value;
        }
        // bytecode OSR frame transfer puts raw longs in the virtual array. Trust usages
        // of static access to do the right thing.
        Stamp valueStamp = value.stamp(NodeView.DEFAULT);
        if (!(valueStamp instanceof PrimitiveStamp)) {
            return value;
        }
        assert valueStamp.getStackKind() == JavaKind.Long : Assertions.errorMessage(value, tool);
        return narrowForOSRStaticAccess(tool, value);
    }

    private ValueNode narrowForOSRStaticAccess(VirtualizerTool tool, ValueNode value) {
        assert value.getStackKind() == JavaKind.Long : value;
        assert accessKind.isPrimitive();
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
        int targetBits = accessKind.getBitCount();
        ValueNode tmpValue = value;
        int longBits = JavaKind.Long.getBitCount();
        if (targetBits < longBits) {
            tmpValue = new NarrowNode(tmpValue, targetBits);
            tool.addNode(tmpValue);
        }
        int intBits = JavaKind.Int.getBitCount();
        if (targetBits < intBits) {
            assert accessKind == JavaKind.Byte : Assertions.errorMessage(accessKind, value, tool);
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

    private boolean isOSRRawStaticAccess() {
        return accessFlags.isStatic() && frame.isBytecodeOSRTransferTarget();
    }
}
