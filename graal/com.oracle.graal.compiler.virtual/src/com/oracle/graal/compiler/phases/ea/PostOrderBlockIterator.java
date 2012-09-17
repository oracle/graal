/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.phases.ea;

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;

public abstract class PostOrderBlockIterator<T extends MergeableBlockState<T>> {

    private final NodeBitMap visitedEnds;
    private final Deque<Block> blockQueue;
    private final IdentityHashMap<FixedNode, T> blockEndStates;
    private final IdentityHashMap<LoopBeginNode, T> loopBeginStates;
    private final Block start;

    private T state;

    public PostOrderBlockIterator(StructuredGraph graph, Block start, T initialState) {
        visitedEnds = graph.createNodeBitMap();
        blockQueue = new ArrayDeque<>();
        blockEndStates = new IdentityHashMap<>();
        loopBeginStates = new IdentityHashMap<>();
        this.start = start;
        this.state = initialState;
    }

    public void apply() {
        Block current = start;

        do {
            processBlock(current, state);

            if (current.getSuccessors().isEmpty()) {
                // nothing to do...
            } else if (current.getSuccessors().size() == 1) {
                Block successor = current.getSuccessors().get(0);
                if (successor.isLoopHeader()) {
                    if (current.getEndNode() instanceof LoopEndNode) {
                        finishLoopEnds((LoopEndNode) current.getEndNode());
                    } else {
                        LoopBeginNode loopBegin = (LoopBeginNode) successor.getBeginNode();
                        state = loopBegin(loopBegin, state);
                        loopBeginStates.put(loopBegin, state);
                        state = state.clone();
                        current = successor;
                        continue;
                    }
                } else {
                    if (successor.getBeginNode() instanceof LoopExitNode) {
                        assert successor.getPredecessors().size() == 1;
                        current = successor;
                        continue;
                    } else {
                        assert successor.getPredecessors().size() > 1 : "invalid block schedule at " + successor.getBeginNode();
                        queueMerge((EndNode) current.getEndNode(), successor);
                    }
                }
            } else {
                assert current.getSuccessors().size() > 1;
                queueSuccessors(current);
            }
            current = nextQueuedBlock();
        } while (current != null);
    }

    protected abstract void processBlock(Block block, T currentState);

    protected abstract T merge(MergeNode merge, List<T> states);

    protected abstract T loopBegin(LoopBeginNode loopBegin, T beforeLoopState);

    protected abstract T loopEnds(LoopBeginNode loopBegin, T loopBeginState, List<T> loopEndStates);

    protected abstract T afterSplit(FixedNode node, T oldState);


    private void queueSuccessors(Block current) {
        blockEndStates.put(current.getEndNode(), state);
        for (Block block : current.getSuccessors()) {
            blockQueue.addFirst(block);
        }
    }

    private Block nextQueuedBlock() {
        int maxIterations = blockQueue.size();
        while (maxIterations-- > 0) {
            Block block = blockQueue.removeFirst();
            if (block.getPredecessors().size() > 1) {
                MergeNode merge = (MergeNode) block.getBeginNode();
                ArrayList<T> states = new ArrayList<>(merge.forwardEndCount());
                for (int i = 0; i < merge.forwardEndCount(); i++) {
                    T other = blockEndStates.get(merge.forwardEndAt(i));
                    assert other != null;
                    states.add(other);
                }
                state = merge(merge, states);
                if (state != null) {
                    return block;
                } else {
                    blockQueue.addLast(block);
                }
            } else {
                assert block.getPredecessors().size() == 1;
                assert block.getBeginNode().predecessor() != null;
                state = afterSplit(block.getBeginNode(), blockEndStates.get(block.getBeginNode().predecessor()));
                return block;
            }
        }
        return null;
    }

    private void queueMerge(EndNode end, Block mergeBlock) {
        assert !visitedEnds.isMarked(end);
        assert !blockEndStates.containsKey(end);
        blockEndStates.put(end, state);
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
            blockQueue.addFirst(mergeBlock);
        }
    }

    private void finishLoopEnds(LoopEndNode end) {
        assert !visitedEnds.isMarked(end);
        assert !blockEndStates.containsKey(end);
        blockEndStates.put(end, state);
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
                states.add(blockEndStates.get(le));
            }
            T loopBeginState = loopBeginStates.get(begin);
            if (loopBeginState != null) {
                loopEnds(begin, loopBeginState, states);
            }
        }
    }
}
