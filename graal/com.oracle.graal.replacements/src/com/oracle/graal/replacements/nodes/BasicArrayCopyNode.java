/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import static com.oracle.jvmci.meta.LocationIdentity.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.meta.*;

@NodeInfo
public class BasicArrayCopyNode extends AbstractMemoryCheckpoint implements Virtualizable, MemoryCheckpoint.Single, MemoryAccess, Lowerable, DeoptimizingNode.DeoptDuring {

    public static final NodeClass<BasicArrayCopyNode> TYPE = NodeClass.create(BasicArrayCopyNode.class);

    @Input protected ValueNode src;
    @Input protected ValueNode srcPos;
    @Input protected ValueNode dest;
    @Input protected ValueNode destPos;
    @Input protected ValueNode length;

    @OptionalInput(InputType.State) FrameState stateDuring;

    @OptionalInput(InputType.Memory) protected MemoryNode lastLocationAccess;

    protected Kind elementKind;

    protected int bci;

    public BasicArrayCopyNode(NodeClass<? extends AbstractMemoryCheckpoint> type, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind, int bci) {
        super(type, StampFactory.forKind(Kind.Void));
        this.bci = bci;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.elementKind = elementKind != Kind.Illegal ? elementKind : null;
    }

    public BasicArrayCopyNode(NodeClass<? extends AbstractMemoryCheckpoint> type, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind) {
        super(type, StampFactory.forKind(Kind.Void));
        this.bci = -6;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.elementKind = elementKind != Kind.Illegal ? elementKind : null;
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

    public int getBci() {
        return bci;
    }

    public Kind getElementKind() {
        return elementKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return any();
    }

    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    public void setLastLocationAccess(MemoryNode lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    private static boolean checkBounds(int position, int length, VirtualObjectNode virtualObject) {
        return position >= 0 && position + length <= virtualObject.entryCount();
    }

    private static boolean checkEntryTypes(int srcPos, int length, State srcState, ResolvedJavaType destComponentType, VirtualizerTool tool) {
        if (destComponentType.getKind() == Kind.Object) {
            for (int i = 0; i < length; i++) {
                ValueNode entry = srcState.getEntry(srcPos + i);
                State state = tool.getObjectState(entry);
                ResolvedJavaType type;
                if (state != null) {
                    if (state.getState() == EscapeState.Virtual) {
                        type = state.getVirtualObject().type();
                    } else {
                        type = StampTool.typeOrNull(state.getMaterializedValue());
                    }
                } else {
                    type = StampTool.typeOrNull(entry);
                }
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
        ResolvedJavaType srcType = StampTool.typeOrNull(getSource().stamp());
        ResolvedJavaType destType = StampTool.typeOrNull(getDestination().stamp());
        if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
            return false;
        }
        if ((srcType.getComponentType().getKind().isPrimitive() && destType.getComponentType().equals(srcType.getComponentType())) || getSource() == getDestination()) {
            return true;
        }

        if (StampTool.isExactType(getDestination().stamp())) {
            if (destType != null && destType.isAssignableFrom(srcType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode sourcePosition = tool.getReplacedValue(getSourcePosition());
        ValueNode destinationPosition = tool.getReplacedValue(getDestinationPosition());
        ValueNode replacedLength = tool.getReplacedValue(getLength());

        if (sourcePosition.isConstant() && destinationPosition.isConstant() && replacedLength.isConstant()) {
            int srcPosInt = sourcePosition.asJavaConstant().asInt();
            int destPosInt = destinationPosition.asJavaConstant().asInt();
            int len = replacedLength.asJavaConstant().asInt();
            State destState = tool.getObjectState(getDestination());

            if (destState != null && destState.getState() == EscapeState.Virtual) {
                VirtualObjectNode destVirtual = destState.getVirtualObject();
                if (!(destVirtual instanceof VirtualArrayNode)) {
                    return;
                }
                if (len < 0 || !checkBounds(destPosInt, len, destVirtual)) {
                    return;
                }
                State srcState = tool.getObjectState(getSource());
                if (srcState != null && srcState.getState() == EscapeState.Virtual) {
                    VirtualObjectNode srcVirtual = srcState.getVirtualObject();
                    if (((VirtualArrayNode) destVirtual).componentType().getKind() != Kind.Object) {
                        return;
                    }
                    if (!(srcVirtual instanceof VirtualArrayNode)) {
                        return;
                    }
                    if (((VirtualArrayNode) srcVirtual).componentType().getKind() != Kind.Object) {
                        return;
                    }
                    if (!checkBounds(srcPosInt, len, srcVirtual)) {
                        return;
                    }
                    if (!checkEntryTypes(srcPosInt, len, srcState, destVirtual.type().getComponentType(), tool)) {
                        return;
                    }
                    for (int i = 0; i < len; i++) {
                        tool.setVirtualEntry(destState, destPosInt + i, srcState.getEntry(srcPosInt + i), false);
                    }
                    tool.delete();
                    if (Debug.isLogEnabled()) {
                        Debug.log("virtualized arraycopyf(%s, %d, %s, %d, %d)", getSource(), srcPosInt, getDestination(), destPosInt, len);
                    }
                } else {
                    ValueNode source = srcState == null ? tool.getReplacedValue(getSource()) : srcState.getMaterializedValue();
                    ResolvedJavaType sourceType = StampTool.typeOrNull(source);
                    if (sourceType == null || !sourceType.isArray()) {
                        return;
                    }
                    ResolvedJavaType sourceComponentType = sourceType.getComponentType();
                    ResolvedJavaType destComponentType = destVirtual.type().getComponentType();
                    if (!sourceComponentType.equals(destComponentType)) {
                        return;
                    }
                    for (int i = 0; i < len; i++) {
                        LoadIndexedNode load = new LoadIndexedNode(source, ConstantNode.forInt(i + srcPosInt, graph()), destComponentType.getKind());
                        tool.addNode(load);
                        tool.setVirtualEntry(destState, destPosInt + i, load, false);
                    }
                    tool.delete();
                }
            }
        }
    }

    public boolean canDeoptimize() {
        return true;
    }

    public FrameState stateDuring() {
        return stateDuring;
    }

    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    public void computeStateDuring(FrameState currentStateAfter) {
        FrameState newStateDuring = currentStateAfter.duplicateModifiedDuringCall(getBci(), asNode().getKind());
        setStateDuring(newStateDuring);
    }
}
