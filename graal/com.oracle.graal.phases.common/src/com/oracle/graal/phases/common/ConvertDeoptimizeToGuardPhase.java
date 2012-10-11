/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;

public class ConvertDeoptimizeToGuardPhase extends Phase {

    private static BeginNode findBeginNode(Node startNode) {
        Node n = startNode;
        while (true) {
            if (n instanceof BeginNode) {
                return (BeginNode) n;
            } else {
                n = n.predecessor();
            }
        }
    }

    @Override
    protected void run(final StructuredGraph graph) {
        if (graph.getNodes(DeoptimizeNode.class).isEmpty()) {
            return;
        }

        for (DeoptimizeNode d : graph.getNodes(DeoptimizeNode.class)) {
            visitDeoptBegin(findBeginNode(d), d, graph);
        }

        new DeadCodeEliminationPhase().apply(graph);
    }

    private void visitDeoptBegin(BeginNode deoptBegin, DeoptimizeNode deopt, StructuredGraph graph) {
        if (deoptBegin instanceof MergeNode) {
            MergeNode mergeNode = (MergeNode) deoptBegin;
            Debug.log("Visiting %s followed by %s", mergeNode, deopt);
            List<EndNode> ends = mergeNode.forwardEnds().snapshot();
            for (EndNode end : ends) {
                if (!end.isDeleted()) {
                    BeginNode beginNode = findBeginNode(end);
                    if (!(beginNode instanceof MergeNode)) {
                        visitDeoptBegin(beginNode, deopt, graph);
                    }
                }
            }
            if (mergeNode.isDeleted()) {
                if (!deopt.isDeleted()) {
                    Debug.log("Merge deleted, deopt moved to %s", findBeginNode(deopt));
                    visitDeoptBegin(findBeginNode(deopt), deopt, graph);
                }
            }
        } else if (deoptBegin.predecessor() instanceof IfNode) {
            IfNode ifNode = (IfNode) deoptBegin.predecessor();
            BeginNode otherBegin = ifNode.trueSuccessor();
            BooleanNode conditionNode = ifNode.compare();
            if (conditionNode instanceof InstanceOfNode) {
                // TODO The lowering currently does not support a FixedGuard as the usage of an InstanceOfNode. Relax this restriction.
                return;
            }
            boolean negated = false;
            if (deoptBegin == ifNode.trueSuccessor()) {
                negated = true;
                otherBegin = ifNode.falseSuccessor();
            }
            BeginNode ifBlockBegin = findBeginNode(ifNode);
            Debug.log("Converting %s on %-5s branch of %s to guard for remaining branch %s. IfBegin=%s", deopt, deoptBegin == ifNode.trueSuccessor() ? "true" : "false", ifNode, otherBegin, ifBlockBegin);
            FixedGuardNode guard = graph.add(new FixedGuardNode(conditionNode, deopt.reason(), deopt.action(), negated, deopt.leafGraphId()));
            otherBegin.replaceAtUsages(ifBlockBegin);
            FixedNode next = otherBegin.next();
            otherBegin.setNext(null);
            guard.setNext(next);
            ifNode.replaceAtPredecessor(guard);
            GraphUtil.killCFG(ifNode);
        }
    }
}
