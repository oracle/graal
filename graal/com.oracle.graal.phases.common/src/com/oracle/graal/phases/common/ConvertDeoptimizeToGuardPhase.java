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
            assert d.isAlive();
            visitDeoptBegin(findBeginNode(d), d, graph);
        }

        new DeadCodeEliminationPhase().apply(graph);
    }

    private void visitDeoptBegin(BeginNode deoptBegin, DeoptimizeNode deopt, StructuredGraph graph) {
        if (deoptBegin instanceof MergeNode) {
            MergeNode mergeNode = (MergeNode) deoptBegin;
            Debug.log("Visiting %s followed by %s", mergeNode, deopt);
            List<BeginNode> begins = new ArrayList<>();
            for (EndNode end : mergeNode.forwardEnds()) {
                BeginNode newBeginNode = findBeginNode(end);
                assert !begins.contains(newBeginNode);
                begins.add(newBeginNode);
            }
            for (BeginNode begin : begins) {
                assert !begin.isDeleted();
                visitDeoptBegin(begin, deopt, graph);
            }
            assert mergeNode.isDeleted();
            return;
        } else if (deoptBegin.predecessor() instanceof IfNode) {
            IfNode ifNode = (IfNode) deoptBegin.predecessor();
            BeginNode otherBegin = ifNode.trueSuccessor();
            LogicNode conditionNode = ifNode.condition();
            if (!(conditionNode instanceof InstanceOfNode) && !(conditionNode instanceof InstanceOfDynamicNode)) {
                // TODO The lowering currently does not support a FixedGuard as the usage of an
                // InstanceOfNode. Relax this restriction.
                FixedGuardNode guard = graph.add(new FixedGuardNode(conditionNode, deopt.reason(), deopt.action(), deoptBegin == ifNode.trueSuccessor()));
                FixedWithNextNode pred = (FixedWithNextNode) ifNode.predecessor();
                if (deoptBegin == ifNode.trueSuccessor()) {
                    graph.removeSplitPropagate(ifNode, ifNode.falseSuccessor());
                } else {
                    graph.removeSplitPropagate(ifNode, ifNode.trueSuccessor());
                }
                Debug.log("Converting %s on %-5s branch of %s to guard for remaining branch %s.", deopt, deoptBegin == ifNode.trueSuccessor() ? "true" : "false", ifNode, otherBegin);
                FixedNode next = pred.next();
                pred.setNext(guard);
                guard.setNext(next);
                return;
            }
        }

        // We could not convert the control split - at least cut off control flow after the split.
        FixedWithNextNode deoptPred = deoptBegin;
        FixedNode next = deoptPred.next();

        if (next != deopt) {
            DeoptimizeNode newDeoptNode = (DeoptimizeNode) deopt.clone(graph);
            deoptPred.setNext(newDeoptNode);
            assert deoptPred == newDeoptNode.predecessor();
            GraphUtil.killCFG(next);
        }
    }
}
