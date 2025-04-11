/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen.phase;

import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.Phase;

/**
 * This phase removes {@link LoopExitNode} that are directly followed by a {@link ReturnNode} or
 * {@link UnwindNode}. This has the effect that the {@link HIRBlock} that contains the
 * {@link LoopExitNode} and Return/Unwind becomes part of the loop and therefore allows control flow
 * reconstruction to pull the return/throw statement into the loop. Additionally,
 * {@link LoopExitNode} that are in a basic block without successors are replaced with
 * {@link BeginNode}. This allows the removal of LoopExitNodes that are not directly followed by a
 * {@link ReturnNode} or {@link UnwindNode} but have e.g. an invocation before the control sink
 * node.
 *
 * Example:
 *
 * <pre>
 * while (condition) {
 *     if (condition2) {
 *         return 5;
 *     }
 *     // do some work
 * }
 * </pre>
 *
 * This gives the Graal IR that looks like:
 *
 * <pre>
 * while (condition) {
 *     if (condition2) {
 *         // loopExitNode
 *         break;
 *     }
 *     // do some work
 * }
 * return 5;
 * </pre>
 *
 * This phase removes the {@link LoopExitNode}, yielding the following:
 *
 * <pre>
 * while (condition) {
 *     if (condition2) {
 *         return 5;
 *     }
 *     // do some work
 * }
 * </pre>
 *
 * Eliminating {@link LoopExitNode} that are in a basic block without successor allows us to also
 * target the patterns like in the following example:
 *
 * <pre>
 * while (condition) {
 *     if (condition2) {
 *         return foo(x);
 *     }
 *     // do some work
 * }
 * </pre>
 *
 * In this example the {@link LoopExitNode} is not directly followed by a control sink node, but it
 * is in a basic block with no successors.
 */
public class RemoveUnusedExitsPhase extends Phase {

    private static <T extends ControlSinkNode> void removeExit(ControlSinkNode ctrlSinkNode) {
        if (ctrlSinkNode.predecessor() instanceof LoopExitNode) {
            LoopExitNode lex = (LoopExitNode) ctrlSinkNode.predecessor();
            assert lex.proxies().isEmpty() : lex.proxies().snapshot();
            if (ctrlSinkNode.predecessor().predecessor() instanceof FixedWithNextNode) {
                FixedWithNextNode uww = (FixedWithNextNode) ctrlSinkNode.predecessor().predecessor();
                ControlSinkNode newControlSink = ctrlSinkNode.graph().addWithoutUnique(createNewControlSinkNode(ctrlSinkNode, getInput(ctrlSinkNode)));
                lex.setNext(null);
                uww.setNext(newControlSink);
                ctrlSinkNode.safeDelete();
                lex.safeDelete();
            } else if (ctrlSinkNode.predecessor().predecessor() instanceof IfNode) {
                lex.graph().replaceFixedWithFixed(lex, lex.graph().add(new BeginNode()));
            }
        }
    }

    private static <T extends ControlSinkNode> ValueNode getInput(T controlSinkNode) {
        assert controlSinkNode instanceof ReturnNode || controlSinkNode instanceof UnwindNode : controlSinkNode;
        if (controlSinkNode instanceof ReturnNode) {
            return ((ReturnNode) controlSinkNode).result();
        } else {
            return ((UnwindNode) controlSinkNode).exception();
        }
    }

    private static <T extends ControlSinkNode> ControlSinkNode createNewControlSinkNode(T oldControlSinkNode, ValueNode input) {
        assert oldControlSinkNode instanceof ReturnNode || oldControlSinkNode instanceof UnwindNode : oldControlSinkNode;
        if (oldControlSinkNode instanceof ReturnNode) {
            return new ReturnNode(input);
        } else {
            return new UnwindNode(input);
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (UnwindNode uwind : graph.getNodes(UnwindNode.TYPE)) {
            removeExit(uwind);
        }

        for (ReturnNode ret : graph.getNodes(ReturnNode.TYPE)) {
            removeExit(ret);
        }

        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeFrequency(true).build();
        for (LoopExitNode loopExitNode : graph.getNodes(LoopExitNode.TYPE)) {
            HIRBlock loopExitBlock = cfg.blockFor(loopExitNode);
            if (loopExitBlock.getSuccessorCount() == 0) {
                BeginNode beginNode = new BeginNode();
                graph.add(beginNode);
                graph.replaceFixedWithFixed(loopExitNode, beginNode);
            }
        }
    }
}
