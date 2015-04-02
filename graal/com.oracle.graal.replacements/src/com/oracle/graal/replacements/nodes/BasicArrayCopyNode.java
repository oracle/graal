/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

@NodeInfo
public abstract class BasicArrayCopyNode extends MacroStateSplitNode implements Virtualizable {

    public static final NodeClass<BasicArrayCopyNode> TYPE = NodeClass.create(BasicArrayCopyNode.class);

    public BasicArrayCopyNode(NodeClass<? extends MacroNode> c, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, JavaType returnType, ValueNode... arguments) {
        super(c, invokeKind, targetMethod, bci, returnType, arguments);
    }

    protected ValueNode getSource() {
        return arguments.get(0);
    }

    protected ValueNode getSourcePosition() {
        return arguments.get(1);
    }

    protected ValueNode getDestination() {
        return arguments.get(2);
    }

    protected ValueNode getDestinationPosition() {
        return arguments.get(3);
    }

    protected ValueNode getLength() {
        return arguments.get(4);
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

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode sourcePosition = tool.getReplacedValue(getSourcePosition());
        ValueNode destinationPosition = tool.getReplacedValue(getDestinationPosition());
        ValueNode length = tool.getReplacedValue(getLength());

        if (sourcePosition.isConstant() && destinationPosition.isConstant() && length.isConstant()) {
            int srcPos = sourcePosition.asJavaConstant().asInt();
            int destPos = destinationPosition.asJavaConstant().asInt();
            int len = length.asJavaConstant().asInt();
            State destState = tool.getObjectState(getDestination());

            if (destState != null && destState.getState() == EscapeState.Virtual) {
                VirtualObjectNode destVirtual = destState.getVirtualObject();
                if (!(destVirtual instanceof VirtualArrayNode)) {
                    return;
                }
                if (len < 0 || !checkBounds(destPos, len, destVirtual)) {
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
                    if (!checkBounds(srcPos, len, srcVirtual)) {
                        return;
                    }
                    if (!checkEntryTypes(srcPos, len, srcState, destVirtual.type().getComponentType(), tool)) {
                        return;
                    }
                    for (int i = 0; i < len; i++) {
                        tool.setVirtualEntry(destState, destPos + i, srcState.getEntry(srcPos + i), false);
                    }
                    tool.delete();
                    if (Debug.isLogEnabled()) {
                        Debug.log("virtualized arraycopyf(%s, %d, %s, %d, %d)", getSource(), srcPos, getDestination(), destPos, len);
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
                        LoadIndexedNode load = new LoadIndexedNode(source, ConstantNode.forInt(i + srcPos, graph()), destComponentType.getKind());
                        tool.addNode(load);
                        tool.setVirtualEntry(destState, destPos + i, load, false);
                    }
                    tool.delete();
                }
            }
        }
    }
}
