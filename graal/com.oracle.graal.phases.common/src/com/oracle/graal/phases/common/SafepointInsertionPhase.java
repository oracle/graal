/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;

/**
 * Adds safepoints to loops and return points.
 */
public class SafepointInsertionPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        final boolean addLoopSafepoints = GenLoopSafepoints.getValue();
        if (addLoopSafepoints && !GenSafepoints.getValue()) {
            // Use (faster) typed node iteration if we are not adding return safepoints
            for (LoopEndNode loopEndNode : graph.getNodes(LoopEndNode.class)) {
                addLoopSafepoint(graph, loopEndNode);
            }
        }

        if (GenSafepoints.getValue()) {
            List<ReturnNode> returnNodes = new ArrayList<>();
            boolean addReturnSafepoints = !OptEliminateSafepoints.getValue();
            for (Node n : graph.getNodes()) {
                if (addLoopSafepoints && n instanceof LoopEndNode) {
                    addLoopSafepoint(graph, (LoopEndNode) n);
                } else if (n instanceof ReturnNode) {
                    returnNodes.add((ReturnNode) n);
                } else {
                    if (!addReturnSafepoints && n instanceof LoweredCallTargetNode) {
                        addReturnSafepoints = true;
                    }
                }
            }
            if (addReturnSafepoints) {
                for (ReturnNode returnNode : returnNodes) {
                    SafepointNode safepoint = graph.add(new SafepointNode());
                    graph.addBeforeFixed(returnNode, safepoint);
                }
            }
        }
    }

    private static void addLoopSafepoint(StructuredGraph graph, LoopEndNode loopEndNode) {
        if (loopEndNode.canSafepoint()) {
            SafepointNode safepointNode = graph.add(new SafepointNode());
            graph.addBeforeFixed(loopEndNode, safepointNode);
        }
    }
}
