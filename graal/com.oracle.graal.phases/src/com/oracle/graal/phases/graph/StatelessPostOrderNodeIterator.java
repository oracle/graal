/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.graph;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

/**
 * This iterator implements a reverse post order iteration over the fixed nodes in the graph,
 * starting at the given fixed node.
 *
 * No changes to the fixed nodes are expected during the iteration, they cause undefined behavior.
 */
public abstract class StatelessPostOrderNodeIterator {

    private final NodeBitMap visitedEnds;
    private final Deque<AbstractBeginNode> nodeQueue;
    private final FixedNode start;

    public StatelessPostOrderNodeIterator(FixedNode start) {
        visitedEnds = start.graph().createNodeBitMap();
        nodeQueue = new ArrayDeque<>();
        this.start = start;
    }

    public void apply() {
        FixedNode current = start;

        do {
            if (current instanceof LoopBeginNode) {
                loopBegin((LoopBeginNode) current);
                current = ((LoopBeginNode) current).next();
                assert current != null;
            } else if (current instanceof LoopEndNode) {
                loopEnd((LoopEndNode) current);
                assert !visitedEnds.isMarked(current);
                visitedEnds.mark(current);
                current = nodeQueue.pollFirst();
            } else if (current instanceof MergeNode) {
                merge((MergeNode) current);
                current = ((MergeNode) current).next();
                assert current != null;
            } else if (current instanceof FixedWithNextNode) {
                node(current);
                current = ((FixedWithNextNode) current).next();
            } else if (current instanceof EndNode) {
                end((EndNode) current);
                queueMerge((EndNode) current);
                current = nodeQueue.pollFirst();
            } else if (current instanceof ControlSinkNode) {
                node(current);
                current = nodeQueue.pollFirst();
            } else if (current instanceof ControlSplitNode) {
                controlSplit((ControlSplitNode) current);
                for (Node node : current.successors()) {
                    if (node != null) {
                        nodeQueue.addFirst((AbstractBeginNode) node);
                    }
                }
                current = nodeQueue.pollFirst();
            } else {
                assert false : current;
            }
        } while (current != null);
        finished();
    }

    private void queueMerge(EndNode end) {
        assert !visitedEnds.isMarked(end);
        visitedEnds.mark(end);
        MergeNode merge = end.merge();
        boolean endsVisited = true;
        for (int i = 0; i < merge.forwardEndCount(); i++) {
            if (!visitedEnds.isMarked(merge.forwardEndAt(i))) {
                endsVisited = false;
                break;
            }
        }
        if (endsVisited) {
            nodeQueue.add(merge);
        }
    }

    protected void node(@SuppressWarnings("unused") FixedNode node) {
        // empty default implementation
    }

    protected void end(EndNode endNode) {
        node(endNode);
    }

    protected void merge(MergeNode merge) {
        node(merge);
    }

    protected void loopBegin(LoopBeginNode loopBegin) {
        node(loopBegin);
    }

    protected void loopEnd(LoopEndNode loopEnd) {
        node(loopEnd);
    }

    protected void controlSplit(ControlSplitNode controlSplit) {
        node(controlSplit);
    }

    protected void finished() {
        // nothing to do
    }
}
