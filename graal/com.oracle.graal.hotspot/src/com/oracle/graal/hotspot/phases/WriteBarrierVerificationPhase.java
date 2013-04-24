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

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.extended.WriteNode.WriteBarrierType;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;

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
        Deque<Node> frontier = new ArrayDeque<>();
        List<Node> processedWrites = new LinkedList<>();
        expandFrontier(frontier, safepoint);
        while (!frontier.isEmpty()) {
            Node currentNode = frontier.removeFirst();
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
        if (hasCorrectAttachedBarrier(write)) {
            return;
        }
        Deque<Node> frontier = new ArrayDeque<>();
        expandFrontier(frontier, write);
        while (!frontier.isEmpty()) {
            Node currentNode = frontier.removeFirst();
            assert !isSafepoint(currentNode) : "Write barrier must be present";
            if (!(currentNode instanceof SerialWriteBarrier) || ((currentNode instanceof SerialWriteBarrier) && !foundCorrectBarrier(write, currentNode))) {
                expandFrontier(frontier, currentNode);
            }
        }
    }

    private static boolean hasCorrectAttachedBarrier(Node node) {
        return (((FixedWithNextNode) node).next() instanceof SerialWriteBarrier) && foundCorrectBarrier(node, ((FixedWithNextNode) node).next());
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
                if (previousNode instanceof ControlSplitNode && frontier.contains(previousNode)) {
                    continue;
                }
                frontier.addFirst(previousNode);
            }
        }
    }

    private static boolean isSafepoint(Node node) {
        return (node instanceof DeoptimizingNode) && ((DeoptimizingNode) node).canDeoptimize();
    }

    private static boolean foundCorrectBarrier(Node write, Node barrier) {
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
