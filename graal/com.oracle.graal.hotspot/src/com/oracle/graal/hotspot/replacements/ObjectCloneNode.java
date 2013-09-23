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
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
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
        Method method;
        /*
         * The first condition tests if the parameter is an array, the second condition tests if the
         * parameter can be an array. Otherwise, the parameter is known to be a non-array object.
         */
        if (type.isArray()) {
            method = ObjectCloneSnippets.arrayCloneMethod;
        } else if (type == null || type.isAssignableFrom(tool.getRuntime().lookupJavaType(Object[].class))) {
            method = ObjectCloneSnippets.genericCloneMethod;
        } else {
            method = ObjectCloneSnippets.instanceCloneMethod;
        }
        final ResolvedJavaMethod snippetMethod = tool.getRuntime().lookupJavaMethod(method);
        final Replacements replacements = tool.getReplacements();
        StructuredGraph snippetGraph = Debug.scope("ArrayCopySnippet", snippetMethod, new Callable<StructuredGraph>() {

            @Override
            public StructuredGraph call() throws Exception {
                return replacements.getSnippet(snippetMethod);
            }
        });

        assert snippetGraph != null : "ObjectCloneSnippets should be installed";
        return lowerReplacement(snippetGraph.copy(), tool);
    }

    private static boolean isCloneableType(ResolvedJavaType type, MetaAccessProvider metaAccess) {
        return type != null && metaAccess.lookupJavaType(Cloneable.class).isAssignableFrom(type);
    }

    private static ResolvedJavaType getConcreteType(Stamp stamp, Assumptions assumptions) {
        if (!(stamp instanceof ObjectStamp)) {
            return null;
        }
        ObjectStamp objectStamp = (ObjectStamp) stamp;
        if (objectStamp.isExactType() || objectStamp.type() == null) {
            return objectStamp.type();
        } else {
            ResolvedJavaType type = objectStamp.type().findUniqueConcreteSubtype();
            if (type != null) {
                assumptions.recordConcreteSubtype(objectStamp.type(), type);
            }
            return type;
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
                tool.createVirtualObject(newVirtual, newEntryState, null);
                tool.replaceWithVirtual(newVirtual);
            }
        } else {
            ValueNode obj;
            if (originalState != null) {
                obj = originalState.getMaterializedValue();
            } else {
                obj = tool.getReplacedValue(getObject());
            }
            ResolvedJavaType type = getConcreteType(obj.stamp(), tool.getAssumptions());
            if (isCloneableType(type, tool.getMetaAccessProvider())) {
                if (!type.isArray()) {
                    VirtualInstanceNode newVirtual = new VirtualInstanceNode(type, true);
                    ResolvedJavaField[] fields = newVirtual.getFields();

                    ValueNode[] state = new ValueNode[fields.length];
                    final LoadFieldNode[] loads = new LoadFieldNode[fields.length];
                    for (int i = 0; i < fields.length; i++) {
                        state[i] = loads[i] = new LoadFieldNode(obj, fields[i]);
                        tool.addNode(loads[i]);
                    }
                    tool.createVirtualObject(newVirtual, state, null);
                    tool.replaceWithVirtual(newVirtual);
                }
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
