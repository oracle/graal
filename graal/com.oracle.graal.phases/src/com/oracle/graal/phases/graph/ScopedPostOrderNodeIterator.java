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
package com.oracle.graal.phases.graph;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;

public abstract class ScopedPostOrderNodeIterator {
    private final NodeBitMap processedNodes;
    private final Deque<FixedNode> nodeQueue;
    private final NodeBitMap queuedNodes;
    private final Deque<FixedNode> scopes;

    protected FixedNode currentScope;

    public ScopedPostOrderNodeIterator(StructuredGraph graph) {
        this.processedNodes = graph.createNodeBitMap();
        this.queuedNodes = graph.createNodeBitMap();
        this.nodeQueue = new ArrayDeque<>();
        this.scopes = getScopes(graph);
    }

    public void apply() {
        while (!scopes.isEmpty()) {
            processedNodes.clearAll();
            queuedNodes.clearAll();
            this.currentScope = scopes.pop();
            initializeScope();
            processScope();
        }
    }

    public void processScope() {
        FixedNode current = currentScope;
        do {
            assert current.isAlive();
            processedNodes.mark(current);

            if (current instanceof Invoke) {
                invoke((Invoke) current);
                queueSuccessors(current);
                current = nextQueuedNode();
            } else if (current instanceof LoopBeginNode) {
                queueLoopBeginSuccessors((LoopBeginNode) current);
                current = nextQueuedNode();
            } else if (current instanceof LoopExitNode) {
                queueLoopExitSuccessors((LoopExitNode) current);
                current = nextQueuedNode();
            } else if (current instanceof LoopEndNode) {
                current = nextQueuedNode();
            } else if (current instanceof MergeNode) {
                current = ((MergeNode) current).next();
                assert current != null;
            } else if (current instanceof FixedWithNextNode) {
                queueSuccessors(current);
                current = nextQueuedNode();
            } else if (current instanceof EndNode) {
                queueMerge((EndNode) current);
                current = nextQueuedNode();
            } else if (current instanceof DeoptimizeNode) {
                current = nextQueuedNode();
            } else if (current instanceof ReturnNode) {
                current = nextQueuedNode();
            } else if (current instanceof UnwindNode) {
                current = nextQueuedNode();
            } else if (current instanceof ControlSplitNode) {
                queueSuccessors(current);
                current = nextQueuedNode();
            } else {
                assert false : current;
            }
        } while(current != null);
    }

    protected void queueLoopBeginSuccessors(LoopBeginNode node) {
        if (currentScope == node) {
            queue(node.next());
        } else if (currentScope instanceof LoopBeginNode) {
            // so we are currently processing loop A and found another loop B
            // -> queue all loop exits of B except those that also exit loop A
            for (LoopExitNode loopExit: node.loopExits()) {
                if (!((LoopBeginNode) currentScope).loopExits().contains(loopExit)) {
                    queue(loopExit);
                }
            }
        } else {
            queue(node.loopExits());
        }
    }

    protected void queueLoopExitSuccessors(LoopExitNode node) {
        if (!(currentScope instanceof LoopBeginNode) || !((LoopBeginNode) currentScope).loopExits().contains(node)) {
            queueSuccessors(node);
        }
    }

    protected Deque<FixedNode> getScopes(StructuredGraph graph) {
        Deque<FixedNode> result = new ArrayDeque<>();
        result.push(graph.start());
        for (LoopBeginNode loopBegin: graph.getNodes(LoopBeginNode.class)) {
            result.push(loopBegin);
        }
        return result;
    }

    private void queueSuccessors(FixedNode x) {
        queue(x.successors());
    }

    private void queue(NodeIterable<? extends Node> iter) {
        for (Node node : iter) {
            queue(node);
        }
    }

    private void queue(Node node) {
        if (node != null && !queuedNodes.isMarked(node)) {
            queuedNodes.mark(node);
            nodeQueue.addFirst((FixedNode) node);
        }
    }

    private FixedNode nextQueuedNode() {
        if (nodeQueue.isEmpty()) {
            return null;
        }

        FixedNode result = nodeQueue.removeFirst();
        assert queuedNodes.isMarked(result);
        return result;
    }

    private void queueMerge(EndNode end) {
        MergeNode merge = end.merge();
        if (!queuedNodes.isMarked(merge) && visitedAllEnds(merge)) {
            queuedNodes.mark(merge);
            nodeQueue.add(merge);
        }
    }

    private boolean visitedAllEnds(MergeNode merge) {
        for (int i = 0; i < merge.forwardEndCount(); i++) {
            if (!processedNodes.isMarked(merge.forwardEndAt(i))) {
                return false;
            }
        }
        return true;
    }

    protected abstract void initializeScope();
    protected abstract void invoke(Invoke invoke);
}
