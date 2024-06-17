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
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class VirtualFrameSetNode extends VirtualFrameAccessorNode implements Virtualizable {
    public static final NodeClass<VirtualFrameSetNode> TYPE = NodeClass.create(VirtualFrameSetNode.class);

    @Input private ValueNode value;

    public VirtualFrameSetNode(Receiver frame, int frameSlotIndex, int accessTag, ValueNode value, VirtualFrameAccessType type, VirtualFrameAccessFlags accessFlags) {
        super(TYPE, StampFactory.forVoid(), frame, frameSlotIndex, accessTag, type, accessFlags);
        this.value = value;
        assert accessFlags.updatesFrame();
    }

    public VirtualFrameSetNode(NewFrameNode frame, int frameSlotIndex, int accessTag, ValueNode value, VirtualFrameAccessType type, VirtualFrameAccessFlags accessFlags) {
        super(TYPE, StampFactory.forVoid(), frame, frameSlotIndex, accessTag, type, accessFlags);
        this.value = value;
        assert accessFlags.updatesFrame();
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        JavaKind valueKind = value.getStackKind();
        if (type == VirtualFrameAccessType.Auxiliary) {
            ValueNode dataAlias = tool.getAlias(frame.getObjectArray(type));

            assert valueKind == JavaKind.Object : Assertions.errorMessage(valueKind);
            // no tags array
            if (dataAlias instanceof VirtualObjectNode) {
                VirtualObjectNode dataVirtual = (VirtualObjectNode) dataAlias;

                if (frameSlotIndex < dataVirtual.entryCount()) {
                    if (tool.setVirtualEntry(dataVirtual, frameSlotIndex, value, valueKind, -1)) {
                        tool.delete();
                        return;
                    }
                }
            }
        } else {
            ValueNode tagAlias = tool.getAlias(frame.getTagArray(type));
            ValueNode dataAlias = tool.getAlias(valueKind == JavaKind.Object ? frame.getObjectArray(type) : frame.getPrimitiveArray(type));
            if (tagAlias instanceof VirtualObjectNode && dataAlias instanceof VirtualObjectNode) {

                VirtualObjectNode tagVirtual = (VirtualObjectNode) tagAlias;
                VirtualObjectNode dataVirtual = (VirtualObjectNode) dataAlias;

                if (frameSlotIndex < tagVirtual.entryCount() && frameSlotIndex < dataVirtual.entryCount()) {
                    ensureStaticSlotAccessConsistency();
                    if (accessFlags.setsTag()) {
                        if (accessFlags.isStatic()) {
                            tool.setVirtualEntry(tagVirtual, frameSlotIndex, getConstantWithStaticModifier(accessTag));
                        } else {
                            tool.setVirtualEntry(tagVirtual, frameSlotIndex, getConstant(accessTag));
                        }
                    }
                    ValueNode actualValue = maybeExtendForOSRStaticAccess(tool);
                    if (tool.setVirtualEntry(dataVirtual, frameSlotIndex, actualValue, valueKind == JavaKind.Object ? JavaKind.Object : JavaKind.Long, -1)) {
                        if (valueKind == JavaKind.Object) {
                            // clear out native entry
                            ValueNode primitiveAlias = tool.getAlias(frame.getPrimitiveArray(type));
                            if (primitiveAlias instanceof VirtualObjectNode) {
                                tool.setVirtualEntry((VirtualObjectNode) primitiveAlias, frameSlotIndex, ConstantNode.defaultForKind(JavaKind.Long, graph()), JavaKind.Long, -1);
                            }
                        }
                        tool.delete();
                        return;
                    }
                }
            }
        }

        /*
         * Deoptimization is our only option here. We cannot go back to a UnsafeStoreNode because we
         * do not have a FrameState to use for the memory store.
         */
        insertDeoptimization(tool);
    }

    private ValueNode maybeExtendForOSRStaticAccess(VirtualizerTool tool) {
        if (!isOSRRawStaticAccess()) {
            return value;
        }
        Stamp valueStamp = value.stamp(NodeView.DEFAULT);
        if (!(valueStamp instanceof PrimitiveStamp)) {
            return value;
        }
        // Force all primitive to be long for bytecode OSR frame.
        return extendForOSRStaticAccess(tool, tool.getAlias(value));
    }

    private static ValueNode extendForOSRStaticAccess(VirtualizerTool tool, ValueNode entry) {
        JavaKind entryKind = entry.stamp(NodeView.DEFAULT).getStackKind();
        if (entryKind == JavaKind.Long) {
            return entry;
        }
        assert entryKind.isPrimitive();
        ValueNode tmpValue = entry;
        if (entryKind.isNumericFloat()) {
            // Convert from numeric float to numeric integer
            entryKind = entryKind == JavaKind.Float ? JavaKind.Int : JavaKind.Long;
            tmpValue = new ReinterpretNode(entryKind, tmpValue);
            tool.addNode(tmpValue);
        }
        if (entryKind != JavaKind.Long) {
            tmpValue = new ZeroExtendNode(tmpValue, JavaKind.Long.getBitCount());
            tool.addNode(tmpValue);
        }
        assert tmpValue.stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Long : Assertions.errorMessage(tmpValue, entry, tool);
        return tmpValue;
    }

    private boolean isOSRRawStaticAccess() {
        return accessFlags.isStatic() && frame.isBytecodeOSRTransferTarget();
    }

    @Override
    public <State> void updateVerificationState(VirtualFrameVerificationStateUpdater<State> updater, State state) {
        if (isOSRRawStaticAccess()) {
            // Static accesses to bytecode OSR frames are advertised as long.
            updater.set(state, getFrameSlotIndex(), NewFrameNode.FrameSlotKindLongTag);
        } else {
            updater.set(state, getFrameSlotIndex(), (byte) getAccessTag());
        }
    }
}
