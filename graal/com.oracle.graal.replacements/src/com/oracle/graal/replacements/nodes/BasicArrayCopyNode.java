/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;

@NodeInfo
public class BasicArrayCopyNode extends MacroStateSplitNode implements Virtualizable {

    public static BasicArrayCopyNode create(Invoke invoke) {
        return USE_GENERATED_NODES ? new BasicArrayCopyNodeGen(invoke) : new BasicArrayCopyNode(invoke);
    }

    protected BasicArrayCopyNode(Invoke invoke) {
        super(invoke);
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
        if (getSourcePosition().isConstant() && getDestinationPosition().isConstant() && getLength().isConstant()) {
            int srcPos = getSourcePosition().asConstant().asInt();
            int destPos = getDestinationPosition().asConstant().asInt();
            int length = getLength().asConstant().asInt();
            State srcState = tool.getObjectState(getSource());
            State destState = tool.getObjectState(getDestination());

            if (srcState != null && srcState.getState() == EscapeState.Virtual && destState != null && destState.getState() == EscapeState.Virtual) {
                VirtualObjectNode srcVirtual = srcState.getVirtualObject();
                VirtualObjectNode destVirtual = destState.getVirtualObject();
                if (!(srcVirtual instanceof VirtualArrayNode) || !(destVirtual instanceof VirtualArrayNode)) {
                    return;
                }
                if (((VirtualArrayNode) srcVirtual).componentType().getKind() != Kind.Object || ((VirtualArrayNode) destVirtual).componentType().getKind() != Kind.Object) {
                    return;
                }
                if (length < 0 || !checkBounds(srcPos, length, srcVirtual) || !checkBounds(destPos, length, destVirtual)) {
                    return;
                }
                if (!checkEntryTypes(srcPos, length, srcState, destVirtual.type().getComponentType(), tool)) {
                    return;
                }
                for (int i = 0; i < length; i++) {
                    tool.setVirtualEntry(destState, destPos + i, srcState.getEntry(srcPos + i), false);
                }
                tool.delete();
                if (Debug.isLogEnabled()) {
                    Debug.log("virtualized arraycopyf(%s, %d, %s, %d, %d)", getSource(), srcPos, getDestination(), destPos, length);
                }
            }
        }
    }

}
