/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.Arrays;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.DeoptBciSupplier;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A node that lowers a non-side effecting snippet.
 */
@NodeInfo(nameTemplate = "SnippetSubstitutionStateSplit#{p#snippet/s}", cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public class SnippetSubstitutionStateSplitNode extends SnippetSubstitutionNode implements StateSplit, DeoptBciSupplier, DeoptimizingNode.DeoptDuring {
    public static final NodeClass<SnippetSubstitutionStateSplitNode> TYPE = NodeClass.create(SnippetSubstitutionStateSplitNode.class);

    int bci;
    @OptionalInput(InputType.State) FrameState stateAfter;
    @OptionalInput(State) FrameState stateDuring;

    public SnippetSubstitutionStateSplitNode(SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo snippet, ResolvedJavaMethod targetMethod, Stamp stamp, ValueNode... arguments) {
        super(TYPE, templates, snippet, targetMethod, stamp, arguments);
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        this.bci = bci;
    }

    @Override
    protected void fixupNodes(LoweringTool tool, SnippetTemplate template, UnmodifiableEconomicMap<Node, Node> duplicates) {
        super.fixupNodes(tool, template, duplicates);
        if (!template.hasSideEffects()) {
            throw new InternalError("snippet " + snippet + " has no side effects");
        }
        for (Node originalNode : duplicates.getKeys()) {
            if (originalNode instanceof InvokeNode) {
                InvokeNode invoke = (InvokeNode) duplicates.get(originalNode);

                // Here we need to fix the bci of the invoke
                if (invoke.bci() != bci()) {
                    invoke.setBci(bci());
                }
                invoke.setStateDuring(null);
                invoke.setStateAfter(null);
                FrameState newStateAfter = stateAfter();
                JavaKind returnKind = invoke.getStackKind();
                if (returnKind != JavaKind.Void) {
                    // Replace the return value with the invoke itself
                    newStateAfter = newStateAfter.duplicateModified(returnKind, returnKind, invoke, null);
                }
                invoke.setStateAfter(newStateAfter);

                // Replace the call target that points at the original snippet with the actual
                // target method. If any constants arguments were injected then strip those
                // arguments off as well.
                CallTargetNode targetNode = invoke.callTarget();
                ValueNode[] invokeArguments = targetNode.arguments().toArray(new ValueNode[0]);
                if (constantArguments != null) {
                    invokeArguments = Arrays.copyOf(invokeArguments, invokeArguments.length - constantArguments.length);
                }
                MethodCallTargetNode target = graph().add(new MethodCallTargetNode(targetMethod.isStatic() ? CallTargetNode.InvokeKind.Static : CallTargetNode.InvokeKind.Virtual,
                                targetMethod, invokeArguments, targetNode.returnStamp(), null));
                targetNode.replaceAndDelete(target);
                tool.getLowerer().lower(invoke, tool);
            } else if (originalNode instanceof InvokeWithExceptionNode) {
                throw new GraalError("unexpected invoke with exception %s in snippet", originalNode);
            }
        }
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public void computeStateDuring(FrameState currentStateAfter) {
        FrameState newStateDuring = currentStateAfter.duplicateModifiedDuringCall(bci, getStackKind());
        setStateDuring(newStateDuring);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
