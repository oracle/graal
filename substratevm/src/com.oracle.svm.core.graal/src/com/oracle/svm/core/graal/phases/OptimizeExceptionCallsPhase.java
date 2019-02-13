/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.phases.Phase;

import com.oracle.svm.core.graal.nodes.DeadEndNode;

/**
 * Optimizes the calls to runtime exception handler methods. Calls to runtime exception handlers are
 * {@link ForeignCallNode} nodes. The branch probability of the guarding IfNode is set to a small
 * value so that runtime exception calls are located at the end of the method code.</li>
 */
public class OptimizeExceptionCallsPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (DeadEndNode deadEnd : graph.getNodes(DeadEndNode.TYPE)) {
            setBranchProbability(deadEnd);
        }
    }

    /**
     * Sets the branch probability of the guarding IfNode to a small value. The effect is that the
     * exception call block is put at the end of the method. The other block (= the regular path)
     * gets the fall-through block of the IfNode. This should give a better performance - and it
     * looks nicer in the disassembly.
     */
    private static void setBranchProbability(Node endNode) {
        Node node = endNode;
        Node predecessor = node.predecessor();

        // Go "up" the graph until we find an IfNode
        while (predecessor != null) {
            if (predecessor instanceof IfNode && node instanceof BeginNode) {
                // We found an IfNode which branches to our runtime exception call
                IfNode ifNode = (IfNode) predecessor;
                ifNode.setTrueSuccessorProbability(node == ifNode.trueSuccessor() ? 0.00001 : 0.99999);
                return;
            }
            if (predecessor instanceof MergeNode || predecessor instanceof ControlSplitNode) {
                // Any other split or merge is suspicious: we abort
                return;
            }
            node = predecessor;
            predecessor = node.predecessor();
        }
    }
}
