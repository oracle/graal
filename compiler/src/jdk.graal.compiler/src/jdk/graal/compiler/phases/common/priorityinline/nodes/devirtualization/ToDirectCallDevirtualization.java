/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization;

import jdk.graal.compiler.core.common.type.VoidStamp;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;

import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Base class for devirtualizations that always produce a direct call when a check passes.
 * Subclasses of this class implement the particular check that proves that a direct call is
 * equivalent.
 */
public abstract class ToDirectCallDevirtualization extends Devirtualization {
    /**
     * Hook that is called when the virtual invoke is duplicated into a direct invoke.
     */
    protected abstract void setDuplicatedInvoke(Invoke duplicatedInvoke);

    protected abstract ResolvedJavaMethod dispatchedMethod();

    @Override
    public AbstractBeginNode createInvocationBlock(StructuredGraph graph, Invoke originalInvoke, AbstractMergeNode returnMerge, PhiNode returnValuePhi, AbstractMergeNode exceptionMerge,
                    PhiNode exceptionObjectPhi, InliningProvider inlinlingProvider) {
        Invoke duplicatedInvoke = duplicateVirtualInvokeToDirectInvoke(graph, originalInvoke, dispatchedMethod(), exceptionMerge, exceptionObjectPhi, inlinlingProvider);
        AbstractBeginNode calleeEntryNode = graph.add(new BeginNode());
        calleeEntryNode.setNext(duplicatedInvoke.asFixedNode());

        EndNode endNode = graph.add(new EndNode());
        duplicatedInvoke.setNext(endNode);
        returnMerge.addForwardEnd(endNode);

        ResolvedJavaMethod dispatchedMethod = dispatchedMethod();
        assert duplicatedInvoke.callTarget().targetMethod().equals(dispatchedMethod) : duplicatedInvoke + " vs. " + dispatchedMethod;

        if (returnValuePhi != null) {
            returnValuePhi.addInput(duplicatedInvoke.asFixedNode());
        }
        setDuplicatedInvoke(duplicatedInvoke);
        return calleeEntryNode;
    }

    @SuppressWarnings("try")
    private Invoke duplicateVirtualInvokeToDirectInvoke(StructuredGraph graph, Invoke invoke, ResolvedJavaMethod dispatchedMethod, AbstractMergeNode exceptionMerge,
                    PhiNode exceptionObjectPhi, InliningProvider inliningProvider) {
        Invoke result;
        try (InliningLog.UpdateScope s = InliningLog.openUpdateScopeTrackingOriginalCallsites(graph.getInliningLog())) {
            result = (Invoke) invoke.asNode().copyWithInputs();
        }
        CallTargetNode callTarget = duplicateCallTargetForDirectCall(graph, result, inliningProvider);
        callTarget.setTargetMethod(dispatchedMethod);
        callTarget.setInvokeKind(CallTargetNode.InvokeKind.Special);
        result.asNode().replaceFirstInput(result.callTarget(), callTarget);

        if (!(invoke.asNode().stamp(NodeView.DEFAULT) instanceof VoidStamp)) {
            FrameState stateAfter = invoke.stateAfter();
            stateAfter = stateAfter.duplicate();
            stateAfter.replaceFirstInput(invoke.asNode(), result.asNode());
            result.setStateAfter(stateAfter);
        }

        if (invoke instanceof InvokeWithExceptionNode) {
            assert exceptionMerge != null;
            assert exceptionObjectPhi != null;

            InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
            ExceptionObjectNode exceptionEdge = (ExceptionObjectNode) invokeWithException.exceptionEdge();
            FrameState stateAfterException = exceptionEdge.stateAfter();

            ExceptionObjectNode newExceptionEdge = (ExceptionObjectNode) exceptionEdge.copyWithInputs();
            // Set new state (pop old exception object, push new one).
            newExceptionEdge.setStateAfter(stateAfterException.duplicateModified(JavaKind.Object, JavaKind.Object, newExceptionEdge, null));

            EndNode endNode = graph.add(new EndNode());
            newExceptionEdge.setNext(endNode);
            exceptionMerge.addForwardEnd(endNode);
            exceptionObjectPhi.addInput(newExceptionEdge);

            ((InvokeWithExceptionNode) result).setExceptionEdge(newExceptionEdge);
        }
        return result;
    }

    @SuppressWarnings("unused")
    protected CallTargetNode duplicateCallTargetForDirectCall(StructuredGraph graph, Invoke result, InliningProvider inliningProvider) {
        return (CallTargetNode) result.callTarget().copyWithInputs();
    }
}
