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
package com.oracle.graal.virtual.phases.ea;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.LoopInfo;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.virtual.phases.ea.EffectList.Effect;

public abstract class EffectsClosure<BlockT extends EffectsBlockState<BlockT>> extends EffectsPhase.Closure<BlockT> {

    private final SchedulePhase schedule;

    protected final BlockMap<GraphEffectList> blockEffects;
    private final IdentityHashMap<Loop, GraphEffectList> loopMergeEffects = new IdentityHashMap<>();

    private boolean changed;

    public EffectsClosure(SchedulePhase schedule) {
        this.schedule = schedule;
        this.blockEffects = new BlockMap<>(schedule.getCFG());
        for (Block block : schedule.getCFG().getBlocks()) {
            blockEffects.put(block, new GraphEffectList());
        }
    }

    @Override
    public boolean hasChanged() {
        return changed;
    }

    @Override
    public void applyEffects() {
        final StructuredGraph graph = schedule.getCFG().graph;
        final ArrayList<Node> obsoleteNodes = new ArrayList<>(0);
        BlockIteratorClosure<Void> closure = new BlockIteratorClosure<Void>() {

            @Override
            protected Void getInitialState() {
                return null;
            }

            private void apply(GraphEffectList effects, Object context) {
                if (!effects.isEmpty()) {
                    Debug.log(" ==== effects for %s", context);
                    for (Effect effect : effects) {
                        effect.apply(graph, obsoleteNodes);
                        if (effect.isVisible()) {
                            Debug.log("    %s", effect);
                        }
                    }
                }
            }

            @Override
            protected Void processBlock(Block block, Void currentState) {
                apply(blockEffects.get(block), block);
                Debug.dump(graph, "after processing block %s", block);
                return currentState;
            }

            @Override
            protected Void merge(Block merge, List<Void> states) {
                return null;
            }

            @Override
            protected Void cloneState(Void oldState) {
                return oldState;
            }

            @Override
            protected List<Void> processLoop(Loop loop, Void initialState) {
                LoopInfo<Void> info = ReentrantBlockIterator.processLoop(this, loop, initialState);
                apply(loopMergeEffects.get(loop), loop);
                return info.exitStates;
            }
        };
        ReentrantBlockIterator.apply(closure, schedule.getCFG().getStartBlock());
        assert VirtualUtil.assertNonReachable(graph, obsoleteNodes);
    }

    @Override
    protected BlockT processBlock(Block block, BlockT state) {
        VirtualUtil.trace("\nBlock: %s (", block);

        GraphEffectList effects = blockEffects.get(block);
        FixedWithNextNode lastFixedNode = null;
        for (Node node : schedule.getBlockToNodesMap().get(block)) {
            changed |= processNode(node, state, effects, lastFixedNode);
            if (node instanceof FixedWithNextNode) {
                lastFixedNode = (FixedWithNextNode) node;
            }
        }
        VirtualUtil.trace(")\n    end state: %s\n", state);
        return state;
    }

    protected abstract boolean processNode(Node node, BlockT state, GraphEffectList effects, FixedWithNextNode lastFixedNode);

    @Override
    protected BlockT merge(Block merge, List<BlockT> states) {
        assert blockEffects.get(merge).isEmpty();
        MergeProcessor processor = createMergeProcessor(merge);
        processor.merge(states);
        blockEffects.get(merge).addAll(processor.mergeEffects);
        blockEffects.get(merge).addAll(processor.afterMergeEffects);
        return processor.newState;
    }

    @Override
    protected final List<BlockT> processLoop(Loop loop, BlockT initialState) {
        BlockT loopEntryState = initialState;
        BlockT lastMergedState = initialState;
        MergeProcessor mergeProcessor = createMergeProcessor(loop.header);
        for (int iteration = 0; iteration < 10; iteration++) {
            LoopInfo<BlockT> info = ReentrantBlockIterator.processLoop(this, loop, cloneState(lastMergedState));

            List<BlockT> states = new ArrayList<>();
            states.add(initialState);
            states.addAll(info.endStates);
            mergeProcessor.merge(states);

            Debug.log("================== %s", loop.header);
            Debug.log("%s", mergeProcessor.newState);
            Debug.log("===== vs.");
            Debug.log("%s", lastMergedState);

            if (mergeProcessor.newState.equivalentTo(lastMergedState)) {
                blockEffects.get(loop.header).insertAll(mergeProcessor.mergeEffects, 0);
                loopMergeEffects.put(loop, mergeProcessor.afterMergeEffects);

                assert info.exitStates.size() == loop.exits.size();
                for (int i = 0; i < loop.exits.size(); i++) {
                    BlockT exitState = info.exitStates.get(i);
                    assert exitState != null : "no loop exit state at " + loop.exits.get(i) + " / " + loop.header;
                    processLoopExit((LoopExitNode) loop.exits.get(i).getBeginNode(), loopEntryState, exitState, blockEffects.get(loop.exits.get(i)));
                }

                return info.exitStates;
            } else {
                lastMergedState = mergeProcessor.newState;
                for (Block block : loop.blocks) {
                    blockEffects.get(block).clear();
                }
            }
        }
        throw new GraalInternalError("too many iterations at %s", loop);
    }

    protected abstract void processLoopExit(LoopExitNode exitNode, BlockT initialState, BlockT exitState, GraphEffectList effects);

    protected abstract MergeProcessor createMergeProcessor(Block merge);

    protected class MergeProcessor {

        protected final Block mergeBlock;
        protected final MergeNode merge;

        protected final GraphEffectList mergeEffects;
        protected final GraphEffectList afterMergeEffects;
        protected BlockT newState;

        public MergeProcessor(Block mergeBlock) {
            this.mergeBlock = mergeBlock;
            this.merge = (MergeNode) mergeBlock.getBeginNode();
            this.mergeEffects = new GraphEffectList();
            this.afterMergeEffects = new GraphEffectList();
        }

        protected void merge(List<BlockT> states) {
            newState = getInitialState();
            newState.meetAliases(states);
        }
    }
}
