/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.phases;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.phases.Phase;

import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.snippets.SnippetRuntime;

/**
 * The {@link SnippetRuntime#UNWIND_EXCEPTION exception handling mechanism} of Substrate VM is
 * capable of jumping over methods that have no exception handler registered. That saves us from
 * emitting boilerplate code in every call site in every method that just forwards the exception
 * object from the {@link InvokeWithExceptionNode} to the {@link UnwindNode}.
 */
public class RemoveUnwindPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        SharedMethod method = (SharedMethod) graph.method();
        if (method.isDeoptTarget()) {
            /*
             * Deoptimization targets need need to have an exception entry point for every invoke.
             * This decouples deoptimization from exception handling: the exception handling
             * mechanism can just deliver an exception to a deoptimized method without any checks.
             */
            return;
        }

        List<InvokeWithExceptionNode> invocations = new ArrayList<>();
        for (UnwindNode node : graph.getNodes().filter(UnwindNode.class)) {
            walkBack(node.predecessor(), node, invocations);
        }

        /*
         * Modify graph only after all suitable invocations are found, to avoid problems with
         * deleted nodes during graph traversal.
         */
        for (InvokeWithExceptionNode node : invocations) {
            InvokeNode replacement = node.graph().add(new InvokeNode(node.callTarget(), node.bci(), node.stamp(NodeView.DEFAULT)));
            replacement.setStateAfter(node.stateAfter());

            node.killExceptionEdge();
            node.graph().replaceSplit(node, replacement, node.next());
        }
    }

    /**
     * We walk back from the {@link UnwindNode} to an {@link InvokeWithExceptionNode}. If the
     * control flow path only contains nodes white-listed in this method, then we know that we have
     * an invoke that just forwards the exception to the {@link UnwindNode}. Such invokes are
     * rewritten to a plain {@link InvokeNode}, i.e., no exception handler entry is created for such
     * invokes.
     */
    protected void walkBack(Node n, Node successor, List<InvokeWithExceptionNode> invocations) {
        if (n instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode node = (InvokeWithExceptionNode) n;
            if (node.exceptionEdge() == successor) {
                invocations.add(node);
            }

        } else if (n instanceof MergeNode) {
            MergeNode node = (MergeNode) n;
            for (ValueNode predecessor : node.cfgPredecessors()) {
                walkBack(predecessor, node, invocations);
            }

        } else if (n instanceof EndNode || n instanceof LoopExitNode || n instanceof ExceptionObjectNode) {
            walkBack(n.predecessor(), n, invocations);
        }
    }
}
