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
import com.oracle.graal.nodes.HeapAccess.WriteBarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;

/**
 * Verification phase that checks if, for every write, at least one write barrier is present at all
 * paths leading to the previous safepoint. For every write, necessitating a write barrier, a
 * bottom-up traversal of the graph is performed up to the previous safepoints via all possible
 * paths. If, for a certain path, no write barrier satisfying the processed write is found, an
 * assertion is generated.
 */
public class WriteBarrierVerificationPhase extends Phase {

    private final boolean useG1GC;

    public WriteBarrierVerificationPhase(boolean useG1GC) {
        this.useG1GC = useG1GC;
    }

    @Override
    protected void run(StructuredGraph graph) {
        processWrites(graph);
    }

    private void processWrites(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (isObjectWrite(node)) {
                validateWrite(node);
            }
        }
    }

    private void validateWrite(Node write) {
        /*
         * The currently validated write is checked in order to discover if it has an appropriate
         * attached write barrier.
         */
        if (hasAttachedBarrier(write)) {
            return;
        }
        NodeFlood frontier = write.graph().createNodeFlood();
        expandFrontier(frontier, write);
        Iterator<Node> iterator = frontier.iterator();
        while (iterator.hasNext()) {
            Node currentNode = iterator.next();
            assert !isSafepoint(currentNode) : "Write barrier must be present";
            if (useG1GC) {
                if (!(currentNode instanceof G1PostWriteBarrier) || ((currentNode instanceof G1PostWriteBarrier) && !validateBarrier(write, (WriteBarrier) currentNode))) {
                    expandFrontier(frontier, currentNode);
                }
            } else {
                if (!(currentNode instanceof SerialWriteBarrier) || ((currentNode instanceof SerialWriteBarrier) && !validateBarrier(write, (WriteBarrier) currentNode))) {
                    expandFrontier(frontier, currentNode);
                }
            }
        }
    }

    private boolean hasAttachedBarrier(Node node) {
        if (useG1GC) {
            return ((FixedWithNextNode) node).next() instanceof G1PostWriteBarrier && ((FixedWithNextNode) node).predecessor() instanceof G1PreWriteBarrier &&
                            validateBarrier(node, (G1PostWriteBarrier) ((FixedWithNextNode) node).next()) && validateBarrier(node, (G1PreWriteBarrier) ((FixedWithNextNode) node).predecessor());
        } else {
            return (((FixedWithNextNode) node).next() instanceof SerialWriteBarrier) && validateBarrier(node, (SerialWriteBarrier) ((FixedWithNextNode) node).next());
        }
    }

    private static boolean isObjectWrite(Node node) {
        if ((node instanceof WriteNode && (((WriteNode) node).getWriteBarrierType() != WriteBarrierType.NONE)) ||
                        (node instanceof CompareAndSwapNode && (((CompareAndSwapNode) node).getWriteBarrierType() != WriteBarrierType.NONE))) {
            return true;
        }
        return false;
    }

    private static void expandFrontier(NodeFlood frontier, Node node) {
        for (Node previousNode : node.cfgPredecessors()) {
            if (previousNode != null) {
                frontier.add(previousNode);
            }
        }
    }

    private static boolean isSafepoint(Node node) {
        /*
         * LoopBegin nodes are also treated as safepoints since a bottom-up analysis is performed
         * and loop safepoints are placed before LoopEnd nodes. Possible elimination of write
         * barriers inside loops, derived from writes outside loops, can not be permitted.
         */
        return ((node instanceof DeoptimizingNode) && ((DeoptimizingNode) node).canDeoptimize()) || (node instanceof LoopBeginNode);
    }

    private static boolean validateBarrier(Node write, WriteBarrier barrier) {
        ValueNode writtenObject = null;
        LocationNode writtenLocation = null;
        if (write instanceof WriteNode) {
            writtenObject = ((WriteNode) write).object();
            writtenLocation = ((WriteNode) write).location();
        } else if (write instanceof CompareAndSwapNode) {
            writtenObject = ((CompareAndSwapNode) write).object();
            writtenLocation = ((CompareAndSwapNode) write).getLocation();
        } else {
            assert false : "Node must be of type requiring a write barrier";
        }

        if ((barrier.getObject() == writtenObject) && (!barrier.usePrecise() || (barrier.usePrecise() && barrier.getLocation() == writtenLocation))) {
            return true;
        }
        return false;
    }
}
