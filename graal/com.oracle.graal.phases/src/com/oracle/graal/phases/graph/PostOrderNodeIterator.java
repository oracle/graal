/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * A PostOrderNodeIterator iterates the fixed nodes of the graph in post order starting from a
 * specified fixed node.
 * <p>
 * For this iterator the CFG is defined by the classical CFG nodes ({@link ControlSplitNode},
 * {@link MergeNode}...) and the {@link FixedWithNextNode#next() next} pointers of
 * {@link FixedWithNextNode}.
 * <p>
 * While iterating it maintains a user-defined state by calling the methods available in
 * {@link MergeableState}.
 * 
 * @param <T> the type of {@link MergeableState} handled by this PostOrderNodeIterator
 */
public abstract class PostOrderNodeIterator<T extends MergeableState<T>> {

    private final NodeBitMap visitedEnds;
    private final Deque<BeginNode> nodeQueue;
    private final IdentityHashMap<FixedNode, T> nodeStates;
    private final FixedNode start;

    protected T state;

    public PostOrderNodeIterator(FixedNode start, T initialState) {
        visitedEnds = start.graph().createNodeBitMap();
        nodeQueue = new ArrayDeque<>();
        nodeStates = new IdentityHashMap<>();
        this.start = start;
        this.state = initialState;
    }

    public void apply() {
        FixedNode current = start;

        do {
            if (current instanceof InvokeWithExceptionNode) {
                invoke((Invoke) current);
                queueSuccessors(current, null);
                current = nextQueuedNode();
            } else if (current instanceof LoopBeginNode) {
                state.loopBegin((LoopBeginNode) current);
                nodeStates.put(current, state);
                state = state.clone();
                loopBegin((LoopBeginNode) current);
                current = ((LoopBeginNode) current).next();
                assert current != null;
            } else if (current instanceof LoopEndNode) {
                loopEnd((LoopEndNode) current);
                finishLoopEnds((LoopEndNode) current);
                current = nextQueuedNode();
            } else if (current instanceof MergeNode) {
                merge((MergeNode) current);
                current = ((MergeNode) current).next();
                assert current != null;
            } else if (current instanceof FixedWithNextNode) {
                FixedNode next = ((FixedWithNextNode) current).next();
                assert next != null : current;
                node(current);
                current = next;
            } else if (current instanceof EndNode) {
                end((EndNode) current);
                queueMerge((EndNode) current);
                current = nextQueuedNode();
            } else if (current instanceof ControlSinkNode) {
                node(current);
                current = nextQueuedNode();
            } else if (current instanceof ControlSplitNode) {
                Set<Node> successors = controlSplit((ControlSplitNode) current);
                queueSuccessors(current, successors);
                current = nextQueuedNode();
            } else {
                assert false : current;
            }
        } while (current != null);
        finished();
    }

    private void queueSuccessors(FixedNode x, Set<Node> successors) {
        nodeStates.put(x, state);
        if (successors != null) {
            for (Node node : successors) {
                if (node != null) {
                    nodeStates.put((FixedNode) node.predecessor(), state);
                    nodeQueue.addFirst((BeginNode) node);
                }
            }
        } else {
            for (Node node : x.successors()) {
                if (node != null) {
                    nodeQueue.addFirst((BeginNode) node);
                }
            }
        }
    }

    private FixedNode nextQueuedNode() {
        int maxIterations = nodeQueue.size();
        while (maxIterations-- > 0) {
            BeginNode node = nodeQueue.removeFirst();
            if (node instanceof MergeNode) {
                MergeNode merge = (MergeNode) node;
                state = nodeStates.get(merge.forwardEndAt(0)).clone();
                ArrayList<T> states = new ArrayList<>(merge.forwardEndCount() - 1);
                for (int i = 1; i < merge.forwardEndCount(); i++) {
                    T other = nodeStates.get(merge.forwardEndAt(i));
                    assert other != null;
                    states.add(other);
                }
                boolean ready = state.merge(merge, states);
                if (ready) {
                    return merge;
                } else {
                    nodeQueue.addLast(merge);
                }
            } else {
                assert node.predecessor() != null;
                state = nodeStates.get(node.predecessor()).clone();
                state.afterSplit(node);
                return node;
            }
        }
        return null;
    }

    private void finishLoopEnds(LoopEndNode end) {
        assert !visitedEnds.isMarked(end);
        assert !nodeStates.containsKey(end);
        nodeStates.put(end, state);
        visitedEnds.mark(end);
        LoopBeginNode begin = end.loopBegin();
        boolean endsVisited = true;
        for (LoopEndNode le : begin.loopEnds()) {
            if (!visitedEnds.isMarked(le)) {
                endsVisited = false;
                break;
            }
        }
        if (endsVisited) {
            ArrayList<T> states = new ArrayList<>(begin.loopEnds().count());
            for (LoopEndNode le : begin.orderedLoopEnds()) {
                states.add(nodeStates.get(le));
            }
            T loopBeginState = nodeStates.get(begin);
            if (loopBeginState != null) {
                loopBeginState.loopEnds(begin, states);
            }
        }
    }

    private void queueMerge(EndNode end) {
        assert !visitedEnds.isMarked(end);
        assert !nodeStates.containsKey(end);
        nodeStates.put(end, state);
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

    protected abstract void node(FixedNode node);

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

    protected Set<Node> controlSplit(ControlSplitNode controlSplit) {
        node(controlSplit);
        return null;
    }

    protected void invoke(Invoke invoke) {
        node(invoke.asNode());
    }

    protected void finished() {
        // nothing to do
    }
}
