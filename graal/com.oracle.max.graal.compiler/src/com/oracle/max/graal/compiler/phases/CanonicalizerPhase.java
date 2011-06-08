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
import com.oracle.max.graal.graph.*;

public class CanonicalizerPhase extends Phase {


    @Override
    protected void run(Graph graph) {
        NodeBitMap visited = graph.createNodeBitMap();
        List<Node> nodes = new ArrayList<Node>(graph.getNodes());
        for (Node n : nodes) {
            if (n == null) {
                continue;
            }
            if (!visited.isMarked(n)) {
                this.canonicalize(n, visited);
            }
        }
    }

    private void canonicalize(Node n, NodeBitMap visited) {
        visited.mark(n);
        for (Node input : n.inputs()) {
            if (input == null) {
                continue;
            }
            if (!visited.isNew(input) && !visited.isMarked(input)) {
                canonicalize(input, visited);
            }
        }

        CanonicalizerOp op = n.lookup(CanonicalizerOp.class);
        if (op != null) {
            Node canonical = op.canonical(n);
            if (canonical != n) {
                n.replace(canonical);
                C1XMetrics.NodesCanonicalized++;
            }
        }
    }

    public interface CanonicalizerOp extends Op {
        Node canonical(Node node);
    }
}
