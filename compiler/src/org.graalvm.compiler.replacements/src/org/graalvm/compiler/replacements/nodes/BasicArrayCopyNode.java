/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_64;
import static org.graalvm.word.LocationIdentity.any;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptBciSupplier;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Base class for nodes that intrinsify {@link System#arraycopy}.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = SIZE_64)
public abstract class BasicArrayCopyNode extends WithExceptionNode
                implements DeoptBciSupplier, StateSplit, Virtualizable, SingleMemoryKill, MemoryAccess, Lowerable, DeoptimizingNode.DeoptDuring {

    public static final NodeClass<BasicArrayCopyNode> TYPE = NodeClass.create(BasicArrayCopyNode.class);

    @Input ValueNode src;
    @Input ValueNode srcPos;
    @Input ValueNode dest;
    @Input ValueNode destPos;
    @Input ValueNode length;

    @OptionalInput(State) FrameState stateDuring;

    @OptionalInput(Memory) protected MemoryKill lastLocationAccess;

    @OptionalInput(InputType.State) protected FrameState stateAfter;

    protected JavaKind elementKind;

    protected int bci;

    public BasicArrayCopyNode(NodeClass<? extends BasicArrayCopyNode> type, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind, int bci) {
        super(type, StampFactory.forKind(JavaKind.Void));
        this.bci = bci;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.elementKind = elementKind != JavaKind.Illegal ? elementKind : null;
    }

    public BasicArrayCopyNode(NodeClass<? extends BasicArrayCopyNode> type, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind) {
        this(type, src, srcPos, dest, destPos, length, elementKind, BytecodeFrame.INVALID_FRAMESTATE_BCI);
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        this.bci = bci;
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return any();
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public static JavaKind selectComponentKind(BasicArrayCopyNode arraycopy) {
        if (arraycopy.getSource() == null || arraycopy.getDestination() == null) {
            return null;
        }
        ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp(NodeView.DEFAULT));
        ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp(NodeView.DEFAULT));

        if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
            return null;
        }
        if (!destType.getComponentType().isAssignableFrom(srcType.getComponentType())) {
            return null;
        }
        if (!arraycopy.isExact()) {
            return null;
        }
        return srcType.getComponentType().getJavaKind();
    }

    public ValueNode getSource() {
        return src;
    }

    public ValueNode getSourcePosition() {
        return srcPos;
    }

    public ValueNode getDestination() {
        return dest;
    }

    public ValueNode getDestinationPosition() {
        return destPos;
    }

    public ValueNode getLength() {
        return length;
    }

    public void setSource(ValueNode value) {
        updateUsages(this.src, value);
        this.src = value;
    }

    public void setSourcePosition(ValueNode value) {
        updateUsages(this.srcPos, value);
        this.srcPos = value;
    }

    public static boolean checkBounds(int position, int length, VirtualObjectNode virtualObject) {
        assert length >= 0;
        return position >= 0 && position <= virtualObject.entryCount() - length;
    }

    public static boolean checkEntryTypes(int srcPos, int length, VirtualObjectNode src, ResolvedJavaType destComponentType, VirtualizerTool tool) {
        if (destComponentType.getJavaKind() == JavaKind.Object && !destComponentType.isJavaLangObject()) {
            for (int i = 0; i < length; i++) {
                ValueNode entry = tool.getEntry(src, srcPos + i);
                ResolvedJavaType type = StampTool.typeOrNull(entry);
                if (type == null || !destComponentType.isAssignableFrom(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * Returns true if this copy doesn't require store checks. Trivially true for primitive arrays.
     */
    public boolean isExact() {
        ResolvedJavaType srcType = StampTool.typeOrNull(getSource().stamp(NodeView.DEFAULT));
        ResolvedJavaType destType = StampTool.typeOrNull(getDestination().stamp(NodeView.DEFAULT));
        if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
            return false;
        }
        if ((srcType.getComponentType().getJavaKind().isPrimitive() && destType.getComponentType().equals(srcType.getComponentType())) || getSource() == getDestination()) {
            return true;
        }

        if (StampTool.isExactType(getDestination().stamp(NodeView.DEFAULT))) {
            if (destType != null && destType.isAssignableFrom(srcType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode sourcePosition = tool.getAlias(getSourcePosition());
        ValueNode destinationPosition = tool.getAlias(getDestinationPosition());
        ValueNode replacedLength = tool.getAlias(getLength());

        if (sourcePosition.isConstant() && destinationPosition.isConstant() && replacedLength.isConstant()) {
            int srcPosInt = sourcePosition.asJavaConstant().asInt();
            int destPosInt = destinationPosition.asJavaConstant().asInt();
            int len = replacedLength.asJavaConstant().asInt();
            ValueNode destAlias = tool.getAlias(getDestination());

            if (destAlias instanceof VirtualArrayNode) {
                VirtualArrayNode destVirtual = (VirtualArrayNode) destAlias;
                if (len < 0 || !checkBounds(destPosInt, len, destVirtual)) {
                    return;
                }
                ValueNode srcAlias = tool.getAlias(getSource());

                if (srcAlias instanceof VirtualObjectNode) {
                    if (!(srcAlias instanceof VirtualArrayNode)) {
                        return;
                    }
                    VirtualArrayNode srcVirtual = (VirtualArrayNode) srcAlias;
                    if (destVirtual.componentType().getJavaKind() != srcVirtual.componentType().getJavaKind()) {
                        return;
                    }
                    if (!checkBounds(srcPosInt, len, srcVirtual)) {
                        return;
                    }
                    if (!checkEntryTypes(srcPosInt, len, srcVirtual, destVirtual.type().getComponentType(), tool)) {
                        return;
                    }
                    if (srcVirtual == destVirtual && srcPosInt < destPosInt) {
                        // must copy backwards to avoid losing elements
                        for (int i = len - 1; i >= 0; i--) {
                            tool.setVirtualEntry(destVirtual, destPosInt + i, tool.getEntry(srcVirtual, srcPosInt + i));
                        }
                    } else {
                        for (int i = 0; i < len; i++) {
                            tool.setVirtualEntry(destVirtual, destPosInt + i, tool.getEntry(srcVirtual, srcPosInt + i));
                        }
                    }
                    tool.delete();
                    DebugContext debug = this.asNode().getDebug();
                    if (debug.isLogEnabled()) {
                        debug.log("virtualized arraycopy(%s, %d, %s, %d, %d)", getSource(), srcPosInt, getDestination(), destPosInt, len);
                    }
                } else {
                    ResolvedJavaType sourceType = StampTool.typeOrNull(srcAlias);
                    if (sourceType == null || !sourceType.isArray()) {
                        return;
                    }
                    ResolvedJavaType sourceComponentType = sourceType.getComponentType();
                    ResolvedJavaType destComponentType = destVirtual.type().getComponentType();
                    if (!sourceComponentType.equals(destComponentType)) {
                        return;
                    }
                    for (int i = 0; i < len; i++) {
                        LoadIndexedNode load = new LoadIndexedNode(this.asNode().graph().getAssumptions(), srcAlias, ConstantNode.forInt(i + srcPosInt, this.asNode().graph()), null,
                                        destComponentType.getJavaKind());
                        load.setNodeSourcePosition(this.asNode().getNodeSourcePosition());
                        tool.addNode(load);
                        tool.setVirtualEntry(destVirtual, destPosInt + i, load);
                    }
                    tool.delete();
                }
            }
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void computeStateDuring(FrameState currentStateAfter) {
        FrameState newStateDuring = currentStateAfter.duplicateModifiedDuringCall(bci(), asNode().getStackKind());
        setStateDuring(newStateDuring);
    }
}
