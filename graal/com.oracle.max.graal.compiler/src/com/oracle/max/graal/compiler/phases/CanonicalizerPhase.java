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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;

public class CanonicalizerPhase extends Phase {
    private static final int MAX_ITERATION_PER_NODE = 10;

    private boolean newNodes;

    public CanonicalizerPhase() {
        this(false);
    }

    public CanonicalizerPhase(boolean newNodes) {
        this.newNodes = newNodes;
    }

    @Override
    protected void run(Graph graph) {
        final NodeWorkList nodeWorkList = graph.createNodeWorkList(!newNodes, MAX_ITERATION_PER_NODE);
        if (newNodes) {
            nodeWorkList.addAll(graph.getNewNodes());
        }
        NotifyReProcess reProcess = new NotifyReProcess() {
            @Override
            public void reProccess(Node n) {
                nodeWorkList.addAgain(n);
            }
        };
        for (Node node : nodeWorkList) {
            CanonicalizerOp op = node.lookup(CanonicalizerOp.class);
            if (op != null) {
                graph.mark();
                Node canonical = op.canonical(node, reProcess);
                if (canonical != node) {
                    node.replaceAndDelete(canonical);
                    nodeWorkList.replaced(canonical, node, false, EdgeType.USAGES);
                    for (Node newNode : graph.getNewNodes()) {
                        nodeWorkList.add(newNode);
                    }
                    GraalMetrics.NodesCanonicalized++;
                }
            }
        }
    }

    public interface NotifyReProcess {
        void reProccess(Node n);
    }

    public interface CanonicalizerOp extends Op {
        Node canonical(Node node, NotifyReProcess reProcess);
    }
}
