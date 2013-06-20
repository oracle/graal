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

import java.lang.reflect.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.common.*;

public class MacroNode extends AbstractStateSplit implements Lowerable, MemoryCheckpoint.Single {

    @Input protected final NodeInputList<ValueNode> arguments;

    private final int bci;
    private final ResolvedJavaMethod targetMethod;
    private final JavaType returnType;

    protected MacroNode(Invoke invoke) {
        super(invoke.asNode().stamp(), invoke.stateAfter());
        MethodCallTargetNode methodCallTarget = (MethodCallTargetNode) invoke.callTarget();
        this.arguments = new NodeInputList<>(this, methodCallTarget.arguments());
        this.bci = invoke.bci();
        this.targetMethod = methodCallTarget.targetMethod();
        this.returnType = methodCallTarget.returnType();
    }

    public int getBci() {
        return bci;
    }

    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    public JavaType getReturnType() {
        return returnType;
    }

    @SuppressWarnings("unused")
    protected StructuredGraph getSnippetGraph(LoweringTool tool) {
        return null;
    }

    @Override
    public void lower(LoweringTool tool, LoweringType loweringType) {
        StructuredGraph snippetGraph = getSnippetGraph(tool);

        InvokeNode invoke = replaceWithInvoke();
        assert invoke.verify();

        if (snippetGraph != null) {
            InliningUtil.inline(invoke, snippetGraph, false);
        }
    }

    private InvokeNode replaceWithInvoke() {
        InvokeNode invoke = createInvoke();
        graph().replaceFixedWithFixed(this, invoke);
        return invoke;
    }

    protected InvokeNode createInvoke() {
        InvokeKind invokeKind = Modifier.isStatic(targetMethod.getModifiers()) ? InvokeKind.Static : InvokeKind.Special;
        MethodCallTargetNode callTarget = graph().add(new MethodCallTargetNode(invokeKind, targetMethod, arguments.toArray(new ValueNode[arguments.size()]), returnType));
        InvokeNode invoke = graph().add(new InvokeNode(callTarget, bci));
        invoke.setStateAfter(stateAfter());
        return invoke;
    }

    protected void replaceSnippetInvokes(StructuredGraph snippetGraph) {
        for (InvokeNode invoke : snippetGraph.getNodes(InvokeNode.class)) {
            if (((MethodCallTargetNode) invoke.callTarget()).targetMethod() != getTargetMethod()) {
                throw new GraalInternalError("unexpected invoke %s in snippet", getClass().getSimpleName());
            }
            if (invoke.stateAfter().bci == FrameState.INVALID_FRAMESTATE_BCI) {
                InvokeNode newInvoke = snippetGraph.add(new InvokeNode(invoke.callTarget(), getBci()));
                newInvoke.setStateAfter(snippetGraph.add(new FrameState(FrameState.AFTER_BCI)));
                snippetGraph.replaceFixedWithFixed(invoke, newInvoke);
            } else {
                assert invoke.stateAfter().bci == FrameState.AFTER_BCI : invoke;
            }
        }
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.ANY_LOCATION;
    }
}
