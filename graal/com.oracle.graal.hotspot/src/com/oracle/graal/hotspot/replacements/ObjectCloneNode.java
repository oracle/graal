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
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.replacements.nodes.*;

public class ObjectCloneNode extends MacroNode implements VirtualizableAllocation, ArrayLengthProvider {

    public ObjectCloneNode(Invoke invoke) {
        super(invoke);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(getObject().stamp());
    }

    private ValueNode getObject() {
        return arguments.get(0);
    }

    @Override
    protected StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
        if (!shouldIntrinsify(getTargetMethod())) {
            return null;
        }

        ResolvedJavaType type = ObjectStamp.typeOrNull(getObject());
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
            } else {
                type = getConcreteType(getObject().stamp(), tool.assumptions(), tool.getMetaAccess());
                if (type != null) {
                    StructuredGraph newGraph = new StructuredGraph();
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

    private static boolean isCloneableType(ResolvedJavaType type, MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaType(Cloneable.class).isAssignableFrom(type);
    }

    /*
     * Looks at the given stamp and determines if it is an exact type (or can be assumed to be an
     * exact type) and if it is a cloneable type.
     * 
     * If yes, then the exact type is returned, otherwise it returns null.
     */
    private static ResolvedJavaType getConcreteType(Stamp stamp, Assumptions assumptions, MetaAccessProvider metaAccess) {
        if (!(stamp instanceof ObjectStamp)) {
            return null;
        }
        ObjectStamp objectStamp = (ObjectStamp) stamp;
        if (objectStamp.type() == null) {
            return null;
        } else if (objectStamp.isExactType()) {
            return isCloneableType(objectStamp.type(), metaAccess) ? objectStamp.type() : null;
        } else {
            ResolvedJavaType type = objectStamp.type().findUniqueConcreteSubtype();
            if (type != null && isCloneableType(type, metaAccess)) {
                assumptions.recordConcreteSubtype(objectStamp.type(), type);
                return type;
            } else {
                return null;
            }
        }
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State originalState = tool.getObjectState(getObject());
        if (originalState != null && originalState.getState() == EscapeState.Virtual) {
            VirtualObjectNode originalVirtual = originalState.getVirtualObject();
            if (isCloneableType(originalVirtual.type(), tool.getMetaAccessProvider())) {
                ValueNode[] newEntryState = new ValueNode[originalVirtual.entryCount()];
                for (int i = 0; i < newEntryState.length; i++) {
                    newEntryState[i] = originalState.getEntry(i);
                }
                VirtualObjectNode newVirtual = originalVirtual.duplicate();
                tool.createVirtualObject(newVirtual, newEntryState, Collections.<MonitorIdNode> emptyList());
                tool.replaceWithVirtual(newVirtual);
            }
        } else {
            ValueNode obj;
            if (originalState != null) {
                obj = originalState.getMaterializedValue();
            } else {
                obj = tool.getReplacedValue(getObject());
            }
            ResolvedJavaType type = getConcreteType(obj.stamp(), tool.getAssumptions(), tool.getMetaAccessProvider());
            if (type != null && !type.isArray()) {
                VirtualInstanceNode newVirtual = new VirtualInstanceNode(type, true);
                ResolvedJavaField[] fields = newVirtual.getFields();

                ValueNode[] state = new ValueNode[fields.length];
                final LoadFieldNode[] loads = new LoadFieldNode[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    state[i] = loads[i] = new LoadFieldNode(obj, fields[i]);
                    tool.addNode(loads[i]);
                }
                tool.createVirtualObject(newVirtual, state, Collections.<MonitorIdNode> emptyList());
                tool.replaceWithVirtual(newVirtual);
            }
        }
    }

    @Override
    public ValueNode length() {
        if (getObject() instanceof ArrayLengthProvider) {
            return ((ArrayLengthProvider) getObject()).length();
        } else {
            return null;
        }
    }
}
