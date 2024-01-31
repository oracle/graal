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

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.Phase;

import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.snippets.ExceptionUnwind;

/**
 * The {@link ExceptionUnwind exception handling mechanism} of Substrate VM is capable of jumping
 * over methods that have no exception handler registered. That saves us from emitting boilerplate
 * code in every call site in every method that just forwards the exception object from the
 * {@link WithExceptionNode} to the {@link UnwindNode}.
 */
public class RemoveUnwindPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        SharedMethod method = (SharedMethod) graph.method();
        if (method.isDeoptTarget()) {
            /*
             * Deoptimization targets need to have an exception entry point for every invoke. This
             * decouples deoptimization from exception handling: the exception handling mechanism
             * can just deliver an exception to a deoptimized method without any checks.
             */
            return;
        }

        List<WithExceptionNode> withExceptionNodes = new ArrayList<>();
        List<BytecodeExceptionNode> bytecodeExceptionNodes = new ArrayList<>();
        for (UnwindNode node : graph.getNodes(UnwindNode.TYPE)) {
            walkBack(node.predecessor(), node, GraphUtil.unproxify(node.exception()), withExceptionNodes, bytecodeExceptionNodes);
        }

        /*
         * Modify graph only after all suitable nodes with exceptions are found, to avoid problems
         * with deleted nodes during graph traversal.
         */
        for (WithExceptionNode node : withExceptionNodes) {
            if (node.isAlive()) {
                graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Removing exception edge for: %s", node);
                node.replaceWithNonThrowing();
            }
        }
        for (BytecodeExceptionNode bytecodeExceptionNode : bytecodeExceptionNodes) {
            if (bytecodeExceptionNode.isAlive()) {
                graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Converting a BytecodeException node to a ThrowBytecodeException node for: %s", bytecodeExceptionNode);
                convertToThrow(bytecodeExceptionNode);
            }
        }
    }

    private record WorklistEntry(Node current, Node successor, ValueNode expectedException) {
    }

    /**
     * We walk back from the {@link UnwindNode} to a {@link WithExceptionNode}. If the control flow
     * path only contains nodes explicitly allowed in this method, then we know that we have a node
     * that just forwards the exception to the {@link UnwindNode}. Such nodes are rewritten to a
     * variant without an exception edge, i.e., no exception handler entry is created for such
     * invokes.
     */
    protected static void walkBack(Node initialNode, Node initialSuccessor, ValueNode initialExpectedException,
                    List<WithExceptionNode> withExceptionNodes, List<BytecodeExceptionNode> bytecodeExceptionNodes) {
        Node current = initialNode;
        Node successor = initialSuccessor;
        ValueNode expectedException = initialExpectedException;

        /*
         * The worklist is used when a node has multiple predecessors. The inner loop walks back
         * single-predecessor nodes to avoid allocating unnecessary worklist entries.
         */
        List<WorklistEntry> worklist = new ArrayList<>();
        while (true) {
            nodeWalk: while (true) {
                if (current instanceof WithExceptionNode node) {
                    if (node.exceptionEdge() == successor) {
                        withExceptionNodes.add(node);
                    }
                    break;

                } else if (current instanceof BytecodeExceptionNode || current instanceof ExceptionObjectNode) {
                    if (current == expectedException) {
                        if (current instanceof BytecodeExceptionNode) {
                            BytecodeExceptionNode node = (BytecodeExceptionNode) current;
                            bytecodeExceptionNodes.add(node);
                        } else {
                            successor = current;
                            current = current.predecessor();
                            continue nodeWalk;
                        }
                    }
                    // bailout here, the node does not flow into the corresponding unwind, its a
                    // unrelated exception, but a different one than the unwind one
                    break;
                } else if (current instanceof MergeNode merge) {
                    if (merge.isPhiAtMerge(expectedException)) {
                        /* Propagate expected exception on each control path leading to the merge */
                        PhiNode expectedExceptionForInput = (PhiNode) expectedException;
                        for (int input = 0; input < merge.forwardEndCount(); input++) {
                            Node predecessor = merge.forwardEndAt(input);
                            worklist.add(new WorklistEntry(predecessor, merge, GraphUtil.unproxify(expectedExceptionForInput.valueAt(input))));
                        }
                    } else {
                        for (ValueNode predecessor : merge.cfgPredecessors()) {
                            worklist.add(new WorklistEntry(predecessor, merge, expectedException));
                        }
                    }
                    break;

                } else if (current instanceof AbstractBeginNode || current instanceof AbstractEndNode) {
                    successor = current;
                    current = current.predecessor();

                } else {
                    break;
                }
            }

            if (worklist.isEmpty()) {
                break;
            }
            var entry = worklist.removeLast();
            current = entry.current;
            successor = entry.successor;
            expectedException = entry.expectedException;
        }
    }

    private static void convertToThrow(BytecodeExceptionNode bytecodeExceptionNode) {
        StructuredGraph graph = bytecodeExceptionNode.graph();

        ThrowBytecodeExceptionNode throwNode = graph.add(new ThrowBytecodeExceptionNode(bytecodeExceptionNode.getExceptionKind(), bytecodeExceptionNode.getArguments()));
        throwNode.setStateBefore(bytecodeExceptionNode.createStateDuring());

        /*
         * BytecodeExceptionNode instance is replaced with ThrowBytecodeExceptionNode instance, so
         * we copy its node source position to not lose information.
         */
        throwNode.setNodeSourcePosition(bytecodeExceptionNode.getNodeSourcePosition());

        FixedWithNextNode predecessor = (FixedWithNextNode) bytecodeExceptionNode.predecessor();
        GraphUtil.killCFG(bytecodeExceptionNode);
        assert predecessor.next() == null : "must be killed now";
        predecessor.setNext(throwNode);
    }
}
