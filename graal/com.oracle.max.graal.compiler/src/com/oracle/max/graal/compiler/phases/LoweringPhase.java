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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;

public class LoweringPhase extends Phase {
    @Override
    protected void run(Graph graph) {
        NodeMap<Node> javaBlockNodes = graph.createNodeMap();
        for (Node n : graph.getNodes()) {
            if (n instanceof FixedNode) {
                LoweringOp op = n.lookup(LoweringOp.class);
                if (op != null) {
                    Node javaBlockNode = getJavaBlockNode(javaBlockNodes, n);
                }
            }

        }
    }

    private Node getJavaBlockNode(NodeMap<Node> javaBlockNodes, Node n) {
        assert n instanceof FixedNode;
        if (javaBlockNodes.get(n) == null) {

            Node truePred = null;
            int count = 0;
            for (Node pred : n.predecessors()) {
                if (pred instanceof FixedNode) {
                    truePred = pred;
                    count++;
                }
            }

            assert count > 0;
            if (count == 1) {
                if (Schedule.trueSuccessorCount(truePred) == 1) {
                    javaBlockNodes.set(n, getJavaBlockNode(javaBlockNodes, truePred));
                } else {
                    // Single predecessor is a split => we are our own Java block node.
                    javaBlockNodes.set(n, n);
                }
            } else {
                Node dominator = null;
                for (Node pred : n.predecessors()) {
                    if (pred instanceof FixedNode) {
                       dominator = getCommonDominator(javaBlockNodes, dominator, pred);
                    }
                }
            }
        }

        assert Schedule.truePredecessorCount(javaBlockNodes.get(n)) == 1;
        return javaBlockNodes.get(n);
    }

    private Node getCommonDominator(NodeMap<Node> javaBlockNodes, Node a, Node b) {
        if (a == null) {
            return b;
        }

        if (b == null) {
            return a;
        }

        Node cur = getJavaBlockNode(javaBlockNodes, a);
    }

    public interface LoweringOp extends Op {
        Node lower(Node node);
    }
}
