/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import java.util.Collections;
import java.util.List;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.meta.HotSpotLoweringProvider;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.nodes.BasicObjectCloneNode;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.graal.compiler.replacements.nodes.ObjectClone;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public final class ObjectCloneNode extends BasicObjectCloneNode {

    public static final NodeClass<ObjectCloneNode> TYPE = NodeClass.create(ObjectCloneNode.class);

    public ObjectCloneNode(MacroParams p) {
        this(p, null);
    }

    private ObjectCloneNode(MacroParams p, FrameState stateAfter) {
        super(TYPE, p, stateAfter);
    }

    @Override
    protected ObjectCloneNode duplicateWithNewStamp(ObjectStamp newStamp) {
        return new ObjectCloneNode(copyParamsWithImprovedStamp(newStamp), stateAfter());
    }

    @Override
    @SuppressWarnings("try")
    public void lower(LoweringTool tool) {
        /* Check if we previously inferred a concrete type but somehow lost track of it. */
        ResolvedJavaType concreteInputType = ObjectClone.getConcreteType(getObject().stamp(NodeView.DEFAULT));
        ResolvedJavaType cachedConcreteType = ObjectClone.getConcreteType(stamp(NodeView.DEFAULT));
        if ((concreteInputType == null && cachedConcreteType != null) || (concreteInputType != null && !concreteInputType.equals(cachedConcreteType))) {
            throw GraalError.shouldNotReachHere("object %s stamp %s concrete type %s; this %s stamp %s concrete type %s".formatted(
                            getObject(), getObject().stamp(NodeView.DEFAULT), ObjectClone.getConcreteType(getObject().stamp(NodeView.DEFAULT)),
                            this, stamp(NodeView.DEFAULT), ObjectClone.getConcreteType(stamp(NodeView.DEFAULT))));
        }

        StructuredGraph replacementGraph = getLoweredSnippetGraph(tool);

        if (replacementGraph != null) {
            /*
             * Replace this node with an invoke for inlining. Verify the stamp, it must be in sync
             * with the cloned object's stamp.
             */
            InvokeNode invoke;
            try (InliningLog.UpdateScope updateScope = InliningLog.openUpdateScopeTrackingReplacement(graph().getInliningLog(), this)) {
                invoke = createInvoke(true);
                graph().replaceFixedWithFixed(this, invoke);
            }

            // Pull out the receiver null check so that a replaced
            // receiver can be lowered if necessary
            if (!getTargetMethod().isStatic()) {
                ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
                if (nonNullReceiver instanceof Lowerable) {
                    ((Lowerable) nonNullReceiver).lower(tool);
                }
            }
            InliningUtil.inline(invoke, replacementGraph, false, getTargetMethod(), "Replace with graph.", "LoweringPhase");
            replacementGraph.getDebug().dump(DebugContext.DETAILED_LEVEL, asNode().graph(), "After inlining replacement %s", replacementGraph);
        } else {
            super.lower(tool);
        }
    }

    @SuppressWarnings("try")
    private StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
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
                        snippetGraph = replacements.getSnippet(snippetMethod, null, null, null, graph().trackNodeSourcePosition(), this.getNodeSourcePosition(), debug.getOptions());
                    } catch (Throwable e) {
                        throw debug.handle(e);
                    }

                    assert snippetGraph != null : "ObjectCloneSnippets should be installed";
                    assert ObjectClone.getConcreteType(stamp(NodeView.DEFAULT)) != null;
                    return MacroInvokable.lowerReplacement(graph(), (StructuredGraph) snippetGraph.copy(getDebug()), tool);
                }
                GraalError.shouldNotReachHere("unhandled array type " + type.getComponentType().getJavaKind()); // ExcludeFromJacocoGeneratedReport
            } else {
                Assumptions assumptions = graph().getAssumptions();
                type = ObjectClone.getConcreteType(getObject().stamp(NodeView.DEFAULT));
                if (type != null) {
                    StructuredGraph newGraph = new StructuredGraph.Builder(graph().getOptions(), graph().getDebug(), AllowAssumptions.ifNonNull(assumptions)).name("<clone>").build();
                    ParameterNode param = newGraph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(getObject().stamp(NodeView.DEFAULT))));

                    // Use a CommitAllocation node so that no FrameState is required when creating
                    // the new instance.
                    CommitAllocationNode commit = newGraph.add(new CommitAllocationNode());
                    newGraph.addAfterFixed(newGraph.start(), commit);
                    VirtualObjectNode virtualObj = newGraph.add(new VirtualInstanceNode(type, true));
                    virtualObj.setObjectId(0);

                    AllocatedObjectNode newObj = newGraph.addWithoutUnique(new AllocatedObjectNode(virtualObj));
                    commit.getVirtualObjects().add(virtualObj);
                    newObj.setCommit(commit);

                    ReturnNode returnNode = newGraph.add(new ReturnNode(newObj));
                    newGraph.addAfterFixed(commit, returnNode);

                    /*
                     * The commit values follow the same ordering as the declared fields returned by
                     * JVMCI. Since the new object's fields are copies of the old one's, the values
                     * are given by a load of the corresponding field in the old object.
                     */
                    List<ValueNode> commitValues = commit.getValues();
                    for (ResolvedJavaField field : type.getInstanceFields(true)) {
                        LoadFieldNode load = newGraph.add(LoadFieldNode.create(newGraph.getAssumptions(), param, field));
                        newGraph.addBeforeFixed(commit, load);
                        commitValues.add(load);
                    }

                    commit.addLocks(Collections.emptyList());
                    commit.getEnsureVirtual().add(false);
                    assert commit.verify();
                    assert ObjectClone.getConcreteType(stamp(NodeView.DEFAULT)) != null;
                    return MacroInvokable.lowerReplacement(graph(), newGraph, tool);
                }
            }
        }
        assert ObjectClone.getConcreteType(stamp(NodeView.DEFAULT)) == null;
        return null;
    }

}
