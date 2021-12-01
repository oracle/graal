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
package com.oracle.svm.core.graal.phases;

import java.util.ArrayDeque;
import java.util.Deque;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.DeadEndNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ProfileData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.SwitchNode;
import org.graalvm.compiler.phases.Phase;

import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;

/**
 * Optimizes the probability of branches that go to exception handlers.
 */
public class OptimizeExceptionPathsPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        /*
         * First walk back from all exceptional control flow sinks to a control flow split that also
         * goes to a "regular" return.
         */
        NodeBitMap exceptionPaths = new NodeBitMap(graph);
        for (UnwindNode unwind : graph.getNodes(UnwindNode.TYPE)) {
            walkBack(unwind, exceptionPaths);
        }
        for (DeadEndNode deadEnd : graph.getNodes(DeadEndNode.TYPE)) {
            walkBack(deadEnd, exceptionPaths);
        }
        for (LoweredDeadEndNode deadEnd : graph.getNodes(LoweredDeadEndNode.TYPE)) {
            walkBack(deadEnd, exceptionPaths);
        }

        /* Now that we know all control flow splits, we modify the branch probabilities. */
        for (Node n : exceptionPaths) {
            AbstractBeginNode exceptionBegin = (AbstractBeginNode) n;
            ControlSplitNode controlSplitNode = (ControlSplitNode) exceptionBegin.predecessor();

            /*
             * Only overwrite the branch probability if it comes from non-profiled sources. If we
             * have proper PGO information that the exception edge is used, it deserves to be
             * optimized.
             */
            if (controlSplitNode.getProfileData().getProfileSource() != ProfileData.ProfileSource.PROFILED) {
                controlSplitNode.setProbability(exceptionBegin, BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROFILE);
            }
        }
    }

    private static void walkBack(ControlSinkNode sink, NodeBitMap exceptionPaths) {
        Deque<Node> worklist = new ArrayDeque<>();
        worklist.push(sink);

        while (!worklist.isEmpty()) {
            Node node = worklist.pop();
            Node predecessor = node.predecessor();

            /* Second loop to avoid the worklist while walking back within a block. */
            while (predecessor != null) {
                if ((predecessor instanceof IfNode || predecessor instanceof SwitchNode) && node instanceof AbstractBeginNode) {
                    boolean allSuccessorsInExceptionPaths = true;
                    for (Node sux : predecessor.successors()) {
                        if (sux != node && !exceptionPaths.contains(sux)) {
                            allSuccessorsInExceptionPaths = false;
                            break;
                        }
                    }
                    if (allSuccessorsInExceptionPaths) {
                        /*
                         * All successors of the control split go to an exception path, so keep
                         * walking backward.
                         */
                        for (Node sux : predecessor.successors()) {
                            exceptionPaths.clear(sux);
                        }
                    } else {
                        exceptionPaths.mark(node);
                        break;
                    }

                } else if (predecessor instanceof MergeNode) {
                    for (ValueNode endNode : ((MergeNode) predecessor).cfgPredecessors()) {
                        worklist.push(endNode);
                    }
                    break;

                } else if (predecessor instanceof WithExceptionNode && node instanceof AbstractBeginNode) {
                    if (node == ((WithExceptionNode) predecessor).exceptionEdge()) {
                        /*
                         * We are at the exception edge of the WithExceptionNode. It has the correct
                         * probability, so nothing to do.
                         */
                        break;
                    } else {
                        /*
                         * We are at the regular successor edge of the WithExceptionNode, keep
                         * walking back.
                         */
                    }

                } else if (predecessor instanceof AbstractMergeNode || predecessor instanceof ControlSplitNode) {
                    /* Any other split or merge is suspicious: we abort. */
                    break;
                }
                node = predecessor;
                predecessor = node.predecessor();
            }
        }
    }
}
