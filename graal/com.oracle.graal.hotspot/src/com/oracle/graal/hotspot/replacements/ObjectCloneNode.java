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
package com.oracle.graal.hotspot.replacements;

import java.lang.reflect.Method;

import jdk.internal.jvmci.meta.Assumptions;
import jdk.internal.jvmci.meta.JavaType;
import jdk.internal.jvmci.meta.ResolvedJavaField;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;
import jdk.internal.jvmci.meta.ResolvedJavaType;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.ReturnNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.LoadFieldNode;
import com.oracle.graal.nodes.java.NewInstanceNode;
import com.oracle.graal.nodes.java.StoreFieldNode;
import com.oracle.graal.nodes.spi.ArrayLengthProvider;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.Replacements;
import com.oracle.graal.nodes.spi.VirtualizableAllocation;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.replacements.nodes.BasicObjectCloneNode;

@NodeInfo
public final class ObjectCloneNode extends BasicObjectCloneNode implements VirtualizableAllocation, ArrayLengthProvider {

    public static final NodeClass<ObjectCloneNode> TYPE = NodeClass.create(ObjectCloneNode.class);

    public ObjectCloneNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, JavaType returnType, ValueNode receiver) {
        super(TYPE, invokeKind, targetMethod, bci, returnType, receiver);
    }

    @Override
    @SuppressWarnings("try")
    protected StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
        ResolvedJavaType type = StampTool.typeOrNull(getObject());
        if (type != null) {
            if (type.isArray()) {
                Method method = ObjectCloneSnippets.arrayCloneMethods.get(type.getComponentType().getJavaKind());
                if (method != null) {
                    final ResolvedJavaMethod snippetMethod = tool.getMetaAccess().lookupJavaMethod(method);
                    final Replacements replacements = tool.getReplacements();
                    StructuredGraph snippetGraph = null;
                    try (Scope s = Debug.scope("ArrayCloneSnippet", snippetMethod)) {
                        snippetGraph = replacements.getSnippet(snippetMethod, null);
                    } catch (Throwable e) {
                        throw Debug.handle(e);
                    }

                    assert snippetGraph != null : "ObjectCloneSnippets should be installed";
                    return lowerReplacement((StructuredGraph) snippetGraph.copy(), tool);
                }
                assert false : "unhandled array type " + type.getComponentType().getJavaKind();
            } else {
                Assumptions assumptions = graph().getAssumptions();
                type = getConcreteType(getObject().stamp(), assumptions, tool.getMetaAccess());
                if (type != null) {
                    StructuredGraph newGraph = new StructuredGraph(AllowAssumptions.from(assumptions != null));
                    ParameterNode param = newGraph.unique(new ParameterNode(0, getObject().stamp()));
                    NewInstanceNode newInstance = newGraph.add(new NewInstanceNode(type, true));
                    newGraph.addAfterFixed(newGraph.start(), newInstance);
                    ReturnNode returnNode = newGraph.add(new ReturnNode(newInstance));
                    newGraph.addAfterFixed(newInstance, returnNode);

                    for (ResolvedJavaField field : type.getInstanceFields(true)) {
                        LoadFieldNode load = newGraph.add(new LoadFieldNode(param, field));
                        newGraph.addBeforeFixed(returnNode, load);
                        newGraph.addBeforeFixed(returnNode, newGraph.add(new StoreFieldNode(newInstance, field, load)));
                    }
                    return lowerReplacement(newGraph, tool);
                }
            }
        }
        return null;
    }
}
