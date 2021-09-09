/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements;

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.nodes.BasicObjectCloneNode;
import org.graalvm.compiler.replacements.nodes.MacroInvokable;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public final class ObjectCloneNode extends BasicObjectCloneNode {

    public static final NodeClass<ObjectCloneNode> TYPE = NodeClass.create(ObjectCloneNode.class);

    public ObjectCloneNode(MacroParams p) {
        super(TYPE, p);
    }

    @Override
    public Stamp computeStamp(ValueNode object) {
        if (getConcreteType(object.stamp(NodeView.DEFAULT)) != null) {
            return AbstractPointerStamp.pointerNonNull(object.stamp(NodeView.DEFAULT));
        }
        /*
         * If this call can't be intrinsified don't report a non-null stamp, otherwise the stamp
         * would change when this is lowered back to an invoke and we might lose a null check.
         */
        return AbstractPointerStamp.pointerMaybeNull(object.stamp(NodeView.DEFAULT));
    }

    @Override
    @SuppressWarnings("try")
    public StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
        ResolvedJavaType type = StampTool.typeOrNull(getObject());

        if (type != null) {
            if (type.isArray()) {
                HotSpotLoweringProvider lowerer = (HotSpotLoweringProvider) tool.getLowerer();
                ObjectCloneSnippets.Templates objectCloneSnippets = lowerer.getObjectCloneSnippets();
                SnippetInfo info = objectCloneSnippets.arrayCloneMethods.get(type.getComponentType().getJavaKind());
                if (info != null) {
                    final ResolvedJavaMethod snippetMethod = info.getMethod();
                    final Replacements replacements = tool.getReplacements();
                    StructuredGraph snippetGraph = null;
                    DebugContext debug = getDebug();
                    try (DebugContext.Scope s = debug.scope("ArrayCloneSnippet", snippetMethod)) {
                        snippetGraph = replacements.getSnippet(snippetMethod, null, null, graph().trackNodeSourcePosition(), this.getNodeSourcePosition(), debug.getOptions());
                    } catch (Throwable e) {
                        throw debug.handle(e);
                    }

                    assert snippetGraph != null : "ObjectCloneSnippets should be installed";
                    assert getConcreteType(stamp(NodeView.DEFAULT)) != null;
                    return MacroInvokable.lowerReplacement(graph(), (StructuredGraph) snippetGraph.copy(getDebug()), tool);
                }
                assert false : "unhandled array type " + type.getComponentType().getJavaKind();
            } else {
                Assumptions assumptions = graph().getAssumptions();
                type = getConcreteType(getObject().stamp(NodeView.DEFAULT));
                if (type != null) {
                    StructuredGraph newGraph = new StructuredGraph.Builder(graph().getOptions(), graph().getDebug(), AllowAssumptions.ifNonNull(assumptions)).name("<clone>").build();
                    ParameterNode param = newGraph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(getObject().stamp(NodeView.DEFAULT))));
                    NewInstanceNode newInstance = newGraph.add(new NewInstanceNode(type, true));
                    newGraph.addAfterFixed(newGraph.start(), newInstance);
                    ReturnNode returnNode = newGraph.add(new ReturnNode(newInstance));
                    newGraph.addAfterFixed(newInstance, returnNode);

                    for (ResolvedJavaField field : type.getInstanceFields(true)) {
                        LoadFieldNode load = newGraph.add(LoadFieldNode.create(newGraph.getAssumptions(), param, field));
                        newGraph.addBeforeFixed(returnNode, load);
                        newGraph.addBeforeFixed(returnNode, newGraph.add(new StoreFieldNode(newInstance, field, load)));
                    }
                    assert getConcreteType(stamp(NodeView.DEFAULT)) != null;
                    return MacroInvokable.lowerReplacement(graph(), newGraph, tool);
                }
            }
        }
        assert getConcreteType(stamp(NodeView.DEFAULT)) == null;
        return null;
    }

}
