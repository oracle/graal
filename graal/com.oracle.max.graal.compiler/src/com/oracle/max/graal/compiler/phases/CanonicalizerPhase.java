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

/* TODO (gd) Canonicalize :
 * - Compare & If
 * - InstanceOf (if it's not transformed into a Condition for Compare)
 * - Switches
 */
public class CanonicalizerPhase extends Phase {
    private static final int MAX_ITERATION_PER_NODE = 10;

    @Override
    protected void run(Graph graph) {
        NodeWorkList nodeWorkList = graph.createNodeWorkList(true, MAX_ITERATION_PER_NODE);
        for (Node node : nodeWorkList) {
            CanonicalizerOp op = node.lookup(CanonicalizerOp.class);
            if (op != null) {
                Node canonical = op.canonical(node);
                if (canonical != node) {
                    node.replaceAndDelete(canonical);
                    nodeWorkList.replaced(canonical, node, true, EdgeType.USAGES);
                    //System.out.println("-->" + n + " canonicalized to " + canonical);
                    GraalMetrics.NodesCanonicalized++;
                }
            }
        }
    }

    public interface CanonicalizerOp extends Op {
        Node canonical(Node node);
    }
}
