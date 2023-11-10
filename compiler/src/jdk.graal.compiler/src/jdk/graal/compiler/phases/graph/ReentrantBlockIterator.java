/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.cfg.Loop;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

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
        protected abstract StateT processBlock(HIRBlock block, StateT currentState);

        /**
         * Merge multiple states when processing {@link HIRBlock} starting with a
         * {@link AbstractMergeNode}.
         */
        protected abstract StateT merge(HIRBlock merge, List<StateT> states);

        /**
         * Clone a state for a successor invocation of
         * {@link BlockIteratorClosure#processBlock(HIRBlock, Object)}.
         */
        protected abstract StateT cloneState(StateT oldState);

        /**
         * Hook for subclasses to apply additional operations after
         * {@link BlockIteratorClosure#cloneState(Object)} for successor blocks.
         */
        protected StateT afterSplit(@SuppressWarnings("unused") HIRBlock successor, StateT oldState) {
            return oldState;
        }

        protected List<StateT> processLoop(Loop<HIRBlock> loop, StateT initialState) {
            return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
        }
    }

    private ReentrantBlockIterator() {
        // no instances allowed
    }

    public static <StateT> LoopInfo<StateT> processLoop(BlockIteratorClosure<StateT> closure, Loop<HIRBlock> loop, StateT initialState) {
        EconomicMap<FixedNode, StateT> blockEndStates = apply(closure, loop.getHeader(), initialState, block -> !(block.getLoop() == loop || block.isLoopHeader()));

        HIRBlock lh = loop.getHeader();
        final int predCount = lh.getPredecessorCount();
        LoopInfo<StateT> info = new LoopInfo<>(predCount - 1, loop.getLoopExits().size());
        for (int i = 1; i < predCount; i++) {
            StateT endState = blockEndStates.get(lh.getPredecessorAt(i).getEndNode());
            // make sure all end states are unique objects
            info.endStates.add(closure.cloneState(endState));
        }
        for (HIRBlock loopExit : loop.getLoopExits()) {
            assert loopExit.getPredecessorCount() == 1 : Assertions.errorMessage(loop, loopExit);
            assert blockEndStates.containsKey(loopExit.getBeginNode()) : loopExit.getBeginNode() + " " + blockEndStates;
            StateT exitState = blockEndStates.get(loopExit.getBeginNode());
            // make sure all exit states are unique objects
            info.exitStates.add(closure.cloneState(exitState));
        }
        return info;
    }

    public static <StateT> void apply(BlockIteratorClosure<StateT> closure, HIRBlock start) {
        apply(closure, start, closure.getInitialState(), null);
    }

    public static <StateT> EconomicMap<FixedNode, StateT> apply(BlockIteratorClosure<StateT> closure, HIRBlock start, StateT initialState, Predicate<HIRBlock> stopAtBlock) {
        Deque<HIRBlock> blockQueue = new ArrayDeque<>();
        /*
         * States are stored on EndNodes before merges, and on BeginNodes after ControlSplitNodes.
         */
        EconomicMap<FixedNode, StateT> states = EconomicMap.create(Equivalence.IDENTITY);

        StateT state = initialState;
        HIRBlock current = start;

        StructuredGraph graph = start.getBeginNode().graph();
        CompilationAlarm compilationAlarm = CompilationAlarm.current();

        while (true) { // TERMINATION ARGUMENT: processing all blocks reverse post order until end
                       // of cfg or until a bailout is triggered because of a long compile
            CompilationAlarm.checkProgress(start.getCfg().graph);
            if (compilationAlarm.hasExpired()) {
                double period = CompilationAlarm.Options.CompilationExpirationPeriod.getValue(graph.getOptions());
                throw new PermanentBailoutException("Compilation exceeded %f seconds during CFG traversal", period);
            }
            HIRBlock next = null;
            if (stopAtBlock != null && stopAtBlock.test(current)) {
                states.put(current.getBeginNode(), state);
            } else {
                state = closure.processBlock(current, state);

                if (current.getSuccessorCount() == 0) {
                    // nothing to do...
                } else if (current.getSuccessorCount() == 1) {
                    HIRBlock successor = current.getSuccessorAt(0);
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
                    next = processMultipleSuccessors(closure, blockQueue, states, state, current);
                }
            }

            // get next queued block
            if (next != null) {
                current = next;
            } else if (blockQueue.isEmpty()) {
                return states;
            } else {
                current = blockQueue.removeFirst();
                assert current.getPredecessorCount() == 1 : Assertions.errorMessage(current);
                assert states.containsKey(current.getBeginNode());
                state = states.removeKey(current.getBeginNode());
            }
        }
    }

    private static <StateT> boolean allEndsVisited(EconomicMap<FixedNode, StateT> states, HIRBlock current, AbstractMergeNode merge) {
        for (AbstractEndNode forwardEnd : merge.forwardEnds()) {
            if (forwardEnd != current.getEndNode() && !states.containsKey(forwardEnd)) {
                return false;
            }
        }
        return true;
    }

    private static <StateT> HIRBlock processMultipleSuccessors(BlockIteratorClosure<StateT> closure, Deque<HIRBlock> blockQueue, EconomicMap<FixedNode, StateT> states, StateT state,
                    HIRBlock current) {
        assert current.getSuccessorCount() > 1 : Assertions.errorMessageContext("current", current);
        for (int i = 1; i < current.getSuccessorCount(); i++) {
            HIRBlock successor = current.getSuccessorAt(i);
            blockQueue.addFirst(successor);
            states.put(successor.getBeginNode(), closure.afterSplit(successor, closure.cloneState(state)));
        }
        return current.getSuccessorAt(0);
    }

    private static <StateT> ArrayList<StateT> mergeStates(EconomicMap<FixedNode, StateT> states, StateT state, HIRBlock current, HIRBlock successor, AbstractMergeNode merge) {
        ArrayList<StateT> mergedStates = new ArrayList<>(merge.forwardEndCount());
        for (int i = 0; i < successor.getPredecessorCount(); i++) {
            HIRBlock predecessor = successor.getPredecessorAt(i);
            assert predecessor == current || states.containsKey(predecessor.getEndNode());
            StateT endState = predecessor == current ? state : states.removeKey(predecessor.getEndNode());
            mergedStates.add(endState);
        }
        return mergedStates;
    }

    private static <StateT> void recurseIntoLoop(BlockIteratorClosure<StateT> closure, Deque<HIRBlock> blockQueue, EconomicMap<FixedNode, StateT> states, StateT state, HIRBlock successor) {
        // recurse into the loop
        Loop<HIRBlock> loop = successor.getLoop();
        LoopBeginNode loopBegin = (LoopBeginNode) loop.getHeader().getBeginNode();
        assert successor.getBeginNode() == loopBegin : Assertions.errorMessage(successor, successor.getBeginNode(), loopBegin);

        List<StateT> exitStates = closure.processLoop(loop, state);

        int i = 0;
        assert loop.getLoopExits().size() == exitStates.size() : Assertions.errorMessage(loop, loop.getLoopExits(), exitStates);
        for (HIRBlock exit : loop.getLoopExits()) {
            states.put(exit.getBeginNode(), exitStates.get(i++));
            blockQueue.addFirst(exit);
        }
    }
}
