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
package com.oracle.graal.phases.graph;

import static com.oracle.graal.graph.util.CollectionsAccess.*;

import java.util.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

public final class ReentrantBlockIterator {

    public static class LoopInfo<StateT> {

        public final List<StateT> endStates;
        public final List<StateT> exitStates;

        public LoopInfo(int endCount, int exitCount) {
            endStates = new ArrayList<>(endCount);
            exitStates = new ArrayList<>(exitCount);
        }
    }

    public abstract static class BlockIteratorClosure<StateT> {

        protected abstract StateT getInitialState();

        protected abstract StateT processBlock(Block block, StateT currentState);

        protected abstract StateT merge(Block merge, List<StateT> states);

        protected abstract StateT cloneState(StateT oldState);

        protected abstract List<StateT> processLoop(Loop<Block> loop, StateT initialState);
    }

    private ReentrantBlockIterator() {
        // no instances allowed
    }

    public static <StateT> LoopInfo<StateT> processLoop(BlockIteratorClosure<StateT> closure, Loop<Block> loop, StateT initialState) {
        Map<FixedNode, StateT> blockEndStates = apply(closure, loop.getHeader(), initialState, new HashSet<>(loop.getBlocks()));

        List<Block> predecessors = loop.getHeader().getPredecessors();
        LoopInfo<StateT> info = new LoopInfo<>(predecessors.size() - 1, loop.getExits().size());
        for (int i = 1; i < predecessors.size(); i++) {
            StateT endState = blockEndStates.get(predecessors.get(i).getEndNode());
            // make sure all end states are unique objects
            info.endStates.add(closure.cloneState(endState));
        }
        for (Block loopExit : loop.getExits()) {
            assert loopExit.getPredecessorCount() == 1;
            assert blockEndStates.containsKey(loopExit.getBeginNode()) : loopExit.getBeginNode() + " " + blockEndStates;
            StateT exitState = blockEndStates.get(loopExit.getBeginNode());
            // make sure all exit states are unique objects
            info.exitStates.add(closure.cloneState(exitState));
        }
        return info;
    }

    public static <StateT> void apply(BlockIteratorClosure<StateT> closure, Block start) {
        apply(closure, start, closure.getInitialState(), null);
    }

    public static <StateT> Map<FixedNode, StateT> apply(BlockIteratorClosure<StateT> closure, Block start, StateT initialState, Set<Block> boundary) {
        Deque<Block> blockQueue = new ArrayDeque<>();
        /*
         * States are stored on EndNodes before merges, and on BeginNodes after ControlSplitNodes.
         */
        Map<FixedNode, StateT> states = newNodeIdentityMap();

        StateT state = initialState;
        Block current = start;

        while (true) {
            if (boundary != null && !boundary.contains(current)) {
                states.put(current.getBeginNode(), state);
            } else {
                state = closure.processBlock(current, state);

                if (current.getSuccessors().isEmpty()) {
                    // nothing to do...
                } else if (current.getSuccessors().size() == 1) {
                    Block successor = current.getSuccessors().get(0);
                    if (successor.isLoopHeader()) {
                        if (current.isLoopEnd()) {
                            // nothing to do... loop ends only lead to loop begins we've already
                            // visited
                            states.put(current.getEndNode(), state);
                        } else {
                            // recurse into the loop
                            Loop<Block> loop = successor.getLoop();
                            LoopBeginNode loopBegin = (LoopBeginNode) loop.getHeader().getBeginNode();
                            assert successor.getBeginNode() == loopBegin;

                            List<StateT> exitStates = closure.processLoop(loop, state);

                            int i = 0;
                            assert loop.getExits().size() == exitStates.size();
                            for (Block exit : loop.getExits()) {
                                states.put(exit.getBeginNode(), exitStates.get(i++));
                                blockQueue.addFirst(exit);
                            }
                        }
                    } else if (current.getEndNode() instanceof AbstractEndNode) {
                        assert successor.getPredecessors().size() > 1 : "invalid block schedule at " + successor.getBeginNode();
                        AbstractEndNode end = (AbstractEndNode) current.getEndNode();

                        // add the end node and see if the merge is ready for processing
                        MergeNode merge = end.merge();
                        boolean endsVisited = true;
                        for (AbstractEndNode forwardEnd : merge.forwardEnds()) {
                            if (forwardEnd != current.getEndNode() && !states.containsKey(forwardEnd)) {
                                endsVisited = false;
                                break;
                            }
                        }
                        if (endsVisited) {
                            ArrayList<StateT> mergedStates = new ArrayList<>(merge.forwardEndCount());
                            for (Block predecessor : successor.getPredecessors()) {
                                assert predecessor == current || states.containsKey(predecessor.getEndNode());
                                StateT endState = predecessor == current ? state : states.remove(predecessor.getEndNode());
                                mergedStates.add(endState);
                            }
                            state = closure.merge(successor, mergedStates);
                            current = successor;
                            continue;
                        } else {
                            assert !states.containsKey(end);
                            states.put(end, state);
                        }
                    } else {
                        assert successor.getPredecessors().size() == 1 : "invalid block schedule at " + successor.getBeginNode();
                        current = successor;
                        continue;
                    }
                } else {
                    assert current.getSuccessors().size() > 1;
                    for (int i = 1; i < current.getSuccessors().size(); i++) {
                        Block successor = current.getSuccessors().get(i);
                        blockQueue.addFirst(successor);
                        states.put(successor.getBeginNode(), closure.cloneState(state));
                    }
                    current = current.getSuccessors().get(0);
                    continue;
                }
            }

            // get next queued block
            if (blockQueue.isEmpty()) {
                return states;
            } else {
                current = blockQueue.removeFirst();
                assert current.getPredecessorCount() == 1;
                assert states.containsKey(current.getBeginNode());
                state = states.remove(current.getBeginNode());
            }
        }
    }
}
