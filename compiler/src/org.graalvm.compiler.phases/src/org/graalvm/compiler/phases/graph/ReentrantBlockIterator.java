/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.phases.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;

public final class ReentrantBlockIterator {

    public static class LoopInfo<StateT> {

        public final List<StateT> endStates;
        public final List<StateT> exitStates;

        public LoopInfo(int endCount, int exitCount) {
            endStates = new ArrayList<>(endCount);
            exitStates = new ArrayList<>(exitCount);
        }
    }

    /**
     * Abstract base class for reverse post order iteration over the {@link ControlFlowGraph}.
     */
    public abstract static class BlockIteratorClosure<StateT> {

        /**
         * Create the initial state for the reverse post order iteration over the
         * {@link ControlFlowGraph}.
         */
        protected abstract StateT getInitialState();

        /**
         * Process the current block with the current state during reverse post order iteration.
         */
        protected abstract StateT processBlock(Block block, StateT currentState);

        /**
         * Merge multiple states when processing {@link Block} starting with a
         * {@link AbstractMergeNode}.
         */
        protected abstract StateT merge(Block merge, List<StateT> states);

        /**
         * Clone a state for a successor invocation of
         * {@link BlockIteratorClosure#processBlock(Block, Object)}.
         */
        protected abstract StateT cloneState(StateT oldState);

        /**
         * Hook for subclasses to apply additional operations after
         * {@link BlockIteratorClosure#cloneState(Object)} for successor blocks.
         */
        protected StateT afterSplit(@SuppressWarnings("unused") Block successor, StateT oldState) {
            return oldState;
        }

        protected abstract List<StateT> processLoop(Loop<Block> loop, StateT initialState);
    }

    private ReentrantBlockIterator() {
        // no instances allowed
    }

    public static <StateT> LoopInfo<StateT> processLoop(BlockIteratorClosure<StateT> closure, Loop<Block> loop, StateT initialState) {
        EconomicMap<FixedNode, StateT> blockEndStates = apply(closure, loop.getHeader(), initialState, block -> !(block.getLoop() == loop || block.isLoopHeader()));

        Block[] predecessors = loop.getHeader().getPredecessors();
        LoopInfo<StateT> info = new LoopInfo<>(predecessors.length - 1, loop.getLoopExits().size());
        for (int i = 1; i < predecessors.length; i++) {
            StateT endState = blockEndStates.get(predecessors[i].getEndNode());
            // make sure all end states are unique objects
            info.endStates.add(closure.cloneState(endState));
        }
        for (Block loopExit : loop.getLoopExits()) {
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

    public static <StateT> EconomicMap<FixedNode, StateT> apply(BlockIteratorClosure<StateT> closure, Block start, StateT initialState, Predicate<Block> stopAtBlock) {
        Deque<Block> blockQueue = new ArrayDeque<>();
        /*
         * States are stored on EndNodes before merges, and on BeginNodes after ControlSplitNodes.
         */
        EconomicMap<FixedNode, StateT> states = EconomicMap.create(Equivalence.IDENTITY);

        StateT state = initialState;
        Block current = start;

        StructuredGraph graph = start.getBeginNode().graph();
        CompilationAlarm compilationAlarm = CompilationAlarm.current();
        while (true) {
            if (compilationAlarm.hasExpired()) {
                int period = CompilationAlarm.Options.CompilationExpirationPeriod.getValue(graph.getOptions());
                if (period > 120) {
                    throw new PermanentBailoutException("Compilation exceeded %d seconds during CFG traversal", period);
                } else {
                    throw new RetryableBailoutException("Compilation exceeded %d seconds during CFG traversal", period);
                }
            }
            Block next = null;
            if (stopAtBlock != null && stopAtBlock.test(current)) {
                states.put(current.getBeginNode(), state);
            } else {
                state = closure.processBlock(current, state);

                Block[] successors = current.getSuccessors();
                if (successors.length == 0) {
                    // nothing to do...
                } else if (successors.length == 1) {
                    Block successor = successors[0];
                    if (successor.isLoopHeader()) {
                        if (current.isLoopEnd()) {
                            // nothing to do... loop ends only lead to loop begins we've already
                            // visited
                            states.put(current.getEndNode(), state);
                        } else {
                            recurseIntoLoop(closure, blockQueue, states, state, successor);
                        }
                    } else if (current.getEndNode() instanceof AbstractEndNode) {
                        AbstractEndNode end = (AbstractEndNode) current.getEndNode();

                        // add the end node and see if the merge is ready for processing
                        AbstractMergeNode merge = end.merge();
                        if (allEndsVisited(states, current, merge)) {
                            ArrayList<StateT> mergedStates = mergeStates(states, state, current, successor, merge);
                            state = closure.merge(successor, mergedStates);
                            next = successor;
                        } else {
                            assert !states.containsKey(end);
                            states.put(end, state);
                        }
                    } else {
                        next = successor;
                    }
                } else {
                    next = processMultipleSuccessors(closure, blockQueue, states, state, successors);
                }
            }

            // get next queued block
            if (next != null) {
                current = next;
            } else if (blockQueue.isEmpty()) {
                return states;
            } else {
                current = blockQueue.removeFirst();
                assert current.getPredecessorCount() == 1;
                assert states.containsKey(current.getBeginNode());
                state = states.removeKey(current.getBeginNode());
            }
        }
    }

    private static <StateT> boolean allEndsVisited(EconomicMap<FixedNode, StateT> states, Block current, AbstractMergeNode merge) {
        for (AbstractEndNode forwardEnd : merge.forwardEnds()) {
            if (forwardEnd != current.getEndNode() && !states.containsKey(forwardEnd)) {
                return false;
            }
        }
        return true;
    }

    private static <StateT> Block processMultipleSuccessors(BlockIteratorClosure<StateT> closure, Deque<Block> blockQueue, EconomicMap<FixedNode, StateT> states, StateT state, Block[] successors) {
        assert successors.length > 1;
        for (int i = 1; i < successors.length; i++) {
            Block successor = successors[i];
            blockQueue.addFirst(successor);
            states.put(successor.getBeginNode(), closure.afterSplit(successor, closure.cloneState(state)));
        }
        return successors[0];
    }

    private static <StateT> ArrayList<StateT> mergeStates(EconomicMap<FixedNode, StateT> states, StateT state, Block current, Block successor, AbstractMergeNode merge) {
        ArrayList<StateT> mergedStates = new ArrayList<>(merge.forwardEndCount());
        for (Block predecessor : successor.getPredecessors()) {
            assert predecessor == current || states.containsKey(predecessor.getEndNode());
            StateT endState = predecessor == current ? state : states.removeKey(predecessor.getEndNode());
            mergedStates.add(endState);
        }
        return mergedStates;
    }

    private static <StateT> void recurseIntoLoop(BlockIteratorClosure<StateT> closure, Deque<Block> blockQueue, EconomicMap<FixedNode, StateT> states, StateT state, Block successor) {
        // recurse into the loop
        Loop<Block> loop = successor.getLoop();
        LoopBeginNode loopBegin = (LoopBeginNode) loop.getHeader().getBeginNode();
        assert successor.getBeginNode() == loopBegin;

        List<StateT> exitStates = closure.processLoop(loop, state);

        int i = 0;
        assert loop.getLoopExits().size() == exitStates.size();
        for (Block exit : loop.getLoopExits()) {
            states.put(exit.getBeginNode(), exitStates.get(i++));
            blockQueue.addFirst(exit);
        }
    }
}
