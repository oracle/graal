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

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;

public class GraphUtil {

    public static void killCFG(FixedNode node) {
        assert node.isAlive();
        if (node instanceof EndNode) {
            // We reached a control flow end.
            EndNode end = (EndNode) node;
            killEnd(end);
        } else if (node instanceof LoopEndNode) {
            // We reached a loop end.
            killLoopEnd(node);
        } else {
            // Normal control flow node.
            for (Node successor : node.successors().snapshot()) {
                killCFG((FixedNode) successor);
            }
        }
        propagateKill(node);
    }

    private static void killLoopEnd(FixedNode node) {
        LoopEndNode loopEndNode = (LoopEndNode) node;
        LoopBeginNode loop = loopEndNode.loopBegin();
        loopEndNode.setLoopBegin(null);
        EndNode endNode = loop.endAt(0);
        assert endNode.predecessor() != null;
        replaceLoopPhis(loop);
        loop.removeEnd(endNode);

        FixedNode next = loop.next();
        loop.setNext(null);
        endNode.replaceAndDelete(next);
        loop.safeDelete();
    }

    private static void replaceLoopPhis(MergeNode merge) {
        for (Node usage : merge.usages().snapshot()) {
            assert usage instanceof PhiNode;
            usage.replaceAndDelete(((PhiNode) usage).valueAt(0));
        }
    }

    private static void killEnd(EndNode end) {
        MergeNode merge = end.merge();
        if (merge instanceof LoopBeginNode) {
            for (PhiNode phi : merge.phis().snapshot()) {
                ValueNode value = phi.valueAt(0);
                phi.replaceAndDelete(value);
            }
            killCFG(merge);
        } else {
            merge.removeEnd(end);
            if (merge.phiPredecessorCount() == 1) {
                for (PhiNode phi : merge.phis().snapshot()) {
                    ValueNode value = phi.valueAt(0);
                    phi.replaceAndDelete(value);
                }
                Node replacedSux = merge.phiPredecessorAt(0);
                Node pred = replacedSux.predecessor();
                assert replacedSux instanceof EndNode;
                FixedNode next = merge.next();
                merge.setNext(null);
                pred.replaceFirstSuccessor(replacedSux, next);
                merge.safeDelete();
                replacedSux.safeDelete();
            }
        }
    }

    // TODO(tw): Factor this code with other branch deletion code.
    public static void propagateKill(Node node) {
        if (node != null && node.isAlive()) {
            List<Node> usagesSnapshot = node.usages().snapshot();

            // null out remaining usages
            node.replaceAtUsages(null);
            node.replaceAtPredecessors(null);
            node.safeDelete();

            for (Node usage : usagesSnapshot) {
                if (!usage.isDeleted()) {
                    propagateKill(usage);
                }
            }
        }
    }
}
