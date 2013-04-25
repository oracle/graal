/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.phases;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.extended.WriteNode.WriteBarrierType;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;

/**
 * Verification phase that checks if, for every write, at least one write barrier is present between
 * safepoint intervals. Initially, the algorithm performs a bottom-up traversal of the graph in
 * order to discover safepoints. For every safepoint discovered, a brute-force bottom-up traversal
 * is performed again that validates every write until all possible reachable safepoints from the
 * currently processed write.
 */
public class WriteBarrierVerificationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        processSafepoints(graph);
    }

    private static void processSafepoints(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (isSafepoint(node)) {
                verifyWrites(node);
            }
        }
    }

    private static void verifyWrites(Node safepoint) {
        /*
         * The list below holds all already processed writes in order to avoid repetitive
         * validations of writes reachable from different paths.
         */
        List<Node> processedWrites = new LinkedList<>();
        Deque<Node> frontier = new ArrayDeque<>();
        expandFrontier(frontier, safepoint);
        while (!frontier.isEmpty()) {
            Node currentNode = frontier.removeFirst();
            /*
             * Any safepoints reached at this point are skipped since they will be processed later.
             */
            if (isSafepoint(currentNode)) {
                continue;
            }
            if (isObjectWrite(currentNode) && !processedWrites.contains(currentNode)) {
                validateWrite(currentNode);
                processedWrites.add(currentNode);
            }
            expandFrontier(frontier, currentNode);
        }
    }

    private static void validateWrite(Node write) {
        /*
         * The currently validated write is checked in order to discover if it has an appropriate
         * attached write barrier.
         */
        if (hasAttachedBarrier(write)) {
            return;
        }
        Deque<Node> frontier = new ArrayDeque<>();
        expandFrontier(frontier, write);
        while (!frontier.isEmpty()) {
            Node currentNode = frontier.removeFirst();
            assert !isSafepoint(currentNode) : "Write barrier must be present";
            if (!(currentNode instanceof SerialWriteBarrier) || ((currentNode instanceof SerialWriteBarrier) && !validateBarrier(write, currentNode))) {
                expandFrontier(frontier, currentNode);
            }
        }
    }

    private static boolean hasAttachedBarrier(Node node) {
        return (((FixedWithNextNode) node).next() instanceof SerialWriteBarrier) && validateBarrier(node, ((FixedWithNextNode) node).next());
    }

    private static boolean isObjectWrite(Node node) {
        if ((node instanceof WriteNode && (((WriteNode) node).getWriteBarrierType() != WriteBarrierType.NONE)) ||
                        (node instanceof CompareAndSwapNode && (((CompareAndSwapNode) node).getWriteBarrierType() != WriteBarrierType.NONE))) {
            return true;
        }
        return false;
    }

    private static void expandFrontier(Deque<Node> frontier, Node node) {
        for (Node previousNode : node.cfgPredecessors()) {
            if (previousNode != null) {
                // Control split nodes are processed only once.
                if ((previousNode instanceof ControlSplitNode) && frontier.contains(previousNode)) {
                    continue;
                }
                frontier.addFirst(previousNode);
            }
        }
    }

    private static boolean isSafepoint(Node node) {
        return ((node instanceof DeoptimizingNode) && ((DeoptimizingNode) node).canDeoptimize()) || (node instanceof LoopBeginNode);
    }

    private static boolean validateBarrier(Node write, Node barrier) {
        SerialWriteBarrier barrierNode = (SerialWriteBarrier) barrier;
        if (write instanceof WriteNode) {
            WriteNode writeNode = (WriteNode) write;
            if ((barrierNode.getObject() == writeNode.object()) && (!barrierNode.usePrecise() || (barrierNode.usePrecise() && barrierNode.getLocation() == writeNode.location()))) {
                return true;
            }
            return false;
        } else if (write instanceof CompareAndSwapNode) {
            CompareAndSwapNode casNode = (CompareAndSwapNode) write;
            if ((barrierNode.getObject() == casNode.object()) && (!barrierNode.usePrecise() || (barrierNode.usePrecise() && barrierNode.getLocation() == casNode.getLocation()))) {
                return true;
            }
            return false;
        }
        assert false : "Node must be of type requiring a write barrier";
        return false;
    }
}
