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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.compiler.GraalCompiler.*;

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.replacements.nodes.*;

@NodeInfo
public class ObjectCloneNode extends BasicObjectCloneNode implements VirtualizableAllocation, ArrayLengthProvider {

    public static ObjectCloneNode create(Invoke invoke) {
        return USE_GENERATED_NODES ? new ObjectCloneNodeGen(invoke) : new ObjectCloneNode(invoke);
    }

    protected ObjectCloneNode(Invoke invoke) {
        super(invoke);
    }

    @Override
    protected StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
        if (!shouldIntrinsify(getTargetMethod())) {
            return null;
        }

        ResolvedJavaType type = StampTool.typeOrNull(getObject());
        if (type != null) {
            if (type.isArray()) {
                Method method = ObjectCloneSnippets.arrayCloneMethods.get(type.getComponentType().getKind());
                if (method != null) {
                    final ResolvedJavaMethod snippetMethod = tool.getMetaAccess().lookupJavaMethod(method);
                    final Replacements replacements = tool.getReplacements();
                    StructuredGraph snippetGraph = null;
                    try (Scope s = Debug.scope("ArrayCopySnippet", snippetMethod)) {
                        snippetGraph = replacements.getSnippet(snippetMethod);
                    } catch (Throwable e) {
                        throw Debug.handle(e);
                    }

                    assert snippetGraph != null : "ObjectCloneSnippets should be installed";
                    return lowerReplacement(snippetGraph.copy(), tool);
                }
                assert false : "unhandled array type " + type.getComponentType().getKind();
            } else {
                type = getConcreteType(getObject().stamp(), tool.assumptions(), tool.getMetaAccess());
                if (type != null) {
                    StructuredGraph newGraph = new StructuredGraph();
                    ParameterNode param = newGraph.unique(ParameterNode.create(0, getObject().stamp()));
                    NewInstanceNode newInstance = newGraph.add(NewInstanceNode.create(type, true));
                    newGraph.addAfterFixed(newGraph.start(), newInstance);
                    ReturnNode returnNode = newGraph.add(ReturnNode.create(newInstance));
                    newGraph.addAfterFixed(newInstance, returnNode);

                    for (ResolvedJavaField field : type.getInstanceFields(true)) {
                        LoadFieldNode load = newGraph.add(LoadFieldNode.create(param, field));
                        newGraph.addBeforeFixed(returnNode, load);
                        newGraph.addBeforeFixed(returnNode, newGraph.add(StoreFieldNode.create(newInstance, field, load)));
                    }
                    return lowerReplacement(newGraph, tool);
                }
            }
        }
        return null;
    }
}
