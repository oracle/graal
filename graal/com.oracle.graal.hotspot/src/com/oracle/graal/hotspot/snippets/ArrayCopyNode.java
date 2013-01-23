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
package com.oracle.graal.hotspot.snippets;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.IterableNodeType;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.snippets.nodes.*;

public class ArrayCopyNode extends MacroNode implements Virtualizable, IterableNodeType, Lowerable {

    public ArrayCopyNode(InvokeNode invoke) {
        super(invoke);
    }

    public ValueNode src() {
        return arguments.get(0);
    }

    public ValueNode srcPos() {
        return arguments.get(1);
    }

    public ValueNode dest() {
        return arguments.get(2);
    }

    public ValueNode destPos() {
        return arguments.get(3);
    }

    public ValueNode length() {
        return arguments.get(4);
    }

    @Override
    public void lower(LoweringTool tool) {
        ResolvedJavaMethod snippetMethod = null;
        ResolvedJavaType srcType = src().objectStamp().type();
        ResolvedJavaType destType = dest().objectStamp().type();
        if (srcType != null && srcType.isArray() && destType != null && destType.isArray()) {
            Kind componentKind = srcType.getComponentType().getKind();
            if (componentKind != Kind.Object) {
                if (srcType.getComponentType() == destType.getComponentType()) {
                    snippetMethod = tool.getRuntime().lookupJavaMethod(ArrayCopySnippets.getSnippetForKind(componentKind));
                }
            } else if (destType.getComponentType().isAssignableFrom(srcType.getComponentType()) && dest().objectStamp().isExactType()) {
                snippetMethod = tool.getRuntime().lookupJavaMethod(ArrayCopySnippets.getSnippetForKind(Kind.Object));
            }
        }
        if (snippetMethod == null) {
            snippetMethod = tool.getRuntime().lookupJavaMethod(ArrayCopySnippets.increaseGenericCallCounterMethod);
            // we will call the generic method. the generic snippet will only increase the counter,
            // not call the actual
            // method. therefore we create a second invoke here.
            ((StructuredGraph) graph()).addAfterFixed(this, createInvoke());
        } else {
            Debug.log("%s > Intrinsify (%s)", Debug.currentScope(), snippetMethod.getSignature().getParameterType(0, snippetMethod.getDeclaringClass()).getComponentType());
        }

        if (snippetMethod != null) {
            StructuredGraph snippetGraph = (StructuredGraph) snippetMethod.getCompilerStorage().get(Graph.class);
            assert snippetGraph != null : "ArrayCopySnippets should be installed";
            InvokeNode invoke = replaceWithInvoke();
            InliningUtil.inline(invoke, snippetGraph, false);
        } else {
            super.lower(tool);
        }
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        if (srcPos().isConstant() && destPos().isConstant() && length().isConstant()) {
            int srcPos = srcPos().asConstant().asInt();
            int destPos = destPos().asConstant().asInt();
            int length = length().asConstant().asInt();
            State srcState = tool.getObjectState(src());
            State destState = tool.getObjectState(dest());

            if (srcState != null && srcState.getState() == EscapeState.Virtual && destState != null && destState.getState() == EscapeState.Virtual) {
                VirtualObjectNode srcVirtual = srcState.getVirtualObject();
                VirtualObjectNode destVirtual = destState.getVirtualObject();
                if (length < 0) {
                    return;
                }
                if (srcPos < 0 || srcPos + length > srcVirtual.entryCount()) {
                    return;
                }
                if (destPos < 0 || destPos + length > destVirtual.entryCount()) {
                    return;
                }
                ResolvedJavaType destComponentType = destVirtual.type().getComponentType();
                if (destComponentType.getKind() == Kind.Object) {
                    for (int i = 0; i < length; i++) {
                        if (!destComponentType.isAssignableFrom(srcState.getEntry(srcPos + i).objectStamp().javaType(tool.getMetaAccessProvider()))) {
                            return;
                        }
                    }
                }
                for (int i = 0; i < length; i++) {
                    tool.setVirtualEntry(destState, destPos + i, srcState.getEntry(srcPos + i));
                }
                tool.delete();
                Debug.log("virtualized arraycopyf(%s, %d, %s, %d, %d)", src(), srcPos, dest(), destPos, length);
            }
        }
    }
}
