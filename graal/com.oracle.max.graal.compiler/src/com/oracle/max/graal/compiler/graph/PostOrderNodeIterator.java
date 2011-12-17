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
package com.oracle.max.graal.compiler.graph;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;

public abstract class PostOrderNodeIterator<T extends MergeableState<T>> {

    private final NodeBitMap visitedEnds;
    private final Deque<FixedNode> nodeQueue;
    private final IdentityHashMap<FixedNode, T> nodeStates;
    private final FixedNode start;

    protected T state;

    public PostOrderNodeIterator(FixedNode start, T initialState) {
        visitedEnds = start.graph().createNodeBitMap();
        nodeQueue = new ArrayDeque<FixedNode>();
        nodeStates = new IdentityHashMap<FixedNode, T>();
        this.start = start;
        this.state = initialState;
    }

    public void apply() {
        FixedNode current = start;

        do {
            if (current instanceof Invoke) {
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
                T loopBeginState = nodeStates.get(((LoopEndNode) current).loopBegin());
                if (loopBeginState != null) {
                    loopBeginState.loopEnd((LoopEndNode) current, state);
                }
                loopEnd((LoopEndNode) current);
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
            } else if (current instanceof DeoptimizeNode) {
                deoptimize((DeoptimizeNode) current);
                current = nextQueuedNode();
            } else if (current instanceof ReturnNode) {
                returnNode((ReturnNode) current);
                current = nextQueuedNode();
            } else if (current instanceof UnwindNode) {
                unwind((UnwindNode) current);
                current = nextQueuedNode();
            } else if (current instanceof ControlSplitNode) {
                Set<Node> successors = controlSplit((ControlSplitNode) current);
                queueSuccessors(current, successors);
                current = nextQueuedNode();
            } else {
                assert false : current;
            }
        } while(current != null);
    }

    private void queueSuccessors(FixedNode x, Set<Node> successors) {
        nodeStates.put(x, state);
        if (successors != null) {
            for (Node node : successors) {
                nodeStates.put((FixedNode) node.predecessor(), state);
                if (node != null) {
                    nodeQueue.addFirst((FixedNode) node);
                }
            }
        } else {
            for (Node node : x.successors()) {
                if (node != null) {
                    nodeQueue.addFirst((FixedNode) node);
                }
            }
        }
    }

    private FixedNode nextQueuedNode() {
        int maxIterations = nodeQueue.size();
        while (maxIterations-- > 0) {
            FixedNode node = nodeQueue.removeFirst();
            if (node instanceof MergeNode) {
                MergeNode merge = (MergeNode) node;
                state = nodeStates.get(merge.endAt(0)).clone();
                ArrayList<T> states = new ArrayList<T>(merge.endCount() - 1);
                for (int i = 1; i < merge.endCount(); i++) {
                    T other = nodeStates.get(merge.endAt(i));
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

    private void queueMerge(EndNode end) {
        assert !visitedEnds.isMarked(end);
        assert !nodeStates.containsKey(end);
        nodeStates.put(end, state);
        visitedEnds.mark(end);
        MergeNode merge = end.merge();
        boolean endsVisited = true;
        for (int i = 0; i < merge.endCount(); i++) {
            if (!visitedEnds.isMarked(merge.endAt(i))) {
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

    protected void deoptimize(DeoptimizeNode deoptimize) {
        node(deoptimize);
    }

    protected Set<Node> controlSplit(ControlSplitNode controlSplit) {
        node(controlSplit);
        return null;
    }

    protected void returnNode(ReturnNode returnNode) {
        node(returnNode);
    }

    protected void invoke(Invoke invoke) {
        node(invoke.node());
    }

    protected void unwind(UnwindNode unwind) {
        node(unwind);
    }
}
