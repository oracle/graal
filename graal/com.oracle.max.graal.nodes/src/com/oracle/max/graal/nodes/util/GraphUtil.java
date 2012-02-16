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
package com.oracle.max.graal.nodes.util;

import static com.oracle.max.graal.graph.iterators.NodePredicates.*;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;

public class GraphUtil {

    public static void killCFG(FixedNode node) {
        assert node.isAlive();
        if (node instanceof EndNode) {
            // We reached a control flow end.
            EndNode end = (EndNode) node;
            killEnd(end);
        } else {
            // Normal control flow node.
            for (Node successor : node.successors().snapshot()) {
                killCFG((FixedNode) successor);
            }
        }
        propagateKill(node);
    }

    private static void killEnd(EndNode end) {
        MergeNode merge = end.merge();
        merge.removeEnd(end);
        if (merge instanceof LoopBeginNode && merge.forwardEndCount() == 0) { //dead loop
            for (PhiNode phi : merge.phis().snapshot()) {
                propagateKill(phi);
            }
            LoopBeginNode begin = (LoopBeginNode) merge;
            // disconnect and delete loop ends
            for (LoopEndNode loopend : begin.loopEnds().snapshot()) {
                loopend.predecessor().replaceFirstSuccessor(loopend, null);
                loopend.safeDelete();
            }
            FixedNode next = begin.next();
            begin.safeDelete();
            killCFG(next);
        } else if (merge instanceof LoopBeginNode && ((LoopBeginNode) merge).loopEnds().isEmpty()) { // not a loop anymore
            ((StructuredGraph) end.graph()).reduceDegenerateLoopBegin((LoopBeginNode) merge);
        } else if (merge.phiPredecessorCount() == 1) { // not a merge anymore
            ((StructuredGraph) end.graph()).reduceTrivialMerge(merge);
        }
    }

    public static void propagateKill(Node node) {
        if (node != null && node.isAlive()) {
            List<Node> usagesSnapshot = node.usages().filter(isA(FloatingNode.class).or(CallTargetNode.class)).snapshot();

            // null out remaining usages
            node.replaceAtUsages(null);
            node.replaceAtPredecessors(null);
            killUnusedFloatingInputs(node);

            for (Node usage : usagesSnapshot) {
                if (!usage.isDeleted()) {
                    if (usage instanceof PhiNode) {
                        usage.replaceFirstInput(node, null);
                    } else {
                        propagateKill(usage);
                    }
                }
            }
        }
    }

    public static void killUnusedFloatingInputs(Node node) {
        List<FloatingNode> floatingInputs = node.inputs().filter(FloatingNode.class).snapshot();
        node.safeDelete();

        for (FloatingNode in : floatingInputs) {
            if (in.usages().isEmpty()) {
                killUnusedFloatingInputs(in);
            }
        }
    }
}
