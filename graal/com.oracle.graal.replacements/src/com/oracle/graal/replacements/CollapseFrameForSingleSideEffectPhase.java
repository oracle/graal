/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.LoopInfo;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

/**
 * This phase ensures that there's a single {@linkplain BytecodeFrame#AFTER_BCI collapsed frame
 * state} per path.
 *
 * Removes other frame states from {@linkplain StateSplit#hasSideEffect() non-side-effecting} nodes
 * in the graph, and replaces them with {@linkplain BytecodeFrame#INVALID_FRAMESTATE_BCI invalid
 * frame states}.
 *
 * The invalid frame states ensure that no deoptimization to a snippet frame state will happen.
 */
public class CollapseFrameForSingleSideEffectPhase extends Phase {

    private static class IterationState {
        public final IterationState previous;
        public final Node node;
        public final Collection<IterationState> merge;
        public final boolean invalid;

        private IterationState(IterationState previous, Node node, Collection<IterationState> merge, boolean invalid) {
            this.previous = previous;
            this.node = node;
            this.merge = merge;
            this.invalid = invalid;
        }

        public IterationState() {
            this(null, null, null, false);
        }

        public IterationState addSideEffect(StateSplit sideEffect) {
            return new IterationState(this, sideEffect.asNode(), null, true);
        }

        public IterationState addBranch(BeginNode begin) {
            return new IterationState(this, begin, null, this.invalid);
        }

        public static IterationState merge(MergeNode merge, Collection<IterationState> before, boolean invalid) {
            return new IterationState(null, merge, before, invalid);
        }

        public void markAll(NodeBitMap set) {
            IterationState state = this;
            while (state != null && state.node != null && !set.contains(state.node)) {
                set.mark(state.node);
                if (state.merge != null) {
                    for (IterationState branch : state.merge) {
                        branch.markAll(set);
                    }
                }
                state = state.previous;
            }
        }

        public void markMasked(NodeBitMap unmasked, NodeBitMap masked) {
            IterationState state = this;
            while (state != null && state.node != null && !masked.contains(state.node)) {
                if (state.node instanceof StateSplit) {
                    unmasked.mark(state.node);
                    StateSplit split = (StateSplit) state.node;
                    if (split.hasSideEffect() && state.previous != null) {
                        state.previous.markAll(masked);
                        return;
                    }
                }

                if (state.merge != null) {
                    for (IterationState branch : state.merge) {
                        branch.markMasked(unmasked, masked);
                    }
                }
                state = state.previous;
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        CollapseFrameForSingleSideEffectClosure closure = new CollapseFrameForSingleSideEffectClosure();
        ReentrantNodeIterator.apply(closure, graph.start(), new IterationState());
        closure.finishProcessing(graph);
    }

    private static class CollapseFrameForSingleSideEffectClosure extends NodeIteratorClosure<IterationState> {

        private List<IterationState> returnStates = new ArrayList<>();
        private List<IterationState> unwindStates = new ArrayList<>();

        @Override
        protected IterationState processNode(FixedNode node, IterationState currentState) {
            IterationState state = currentState;
            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState frameState = stateSplit.stateAfter();
                if (frameState != null) {
                    if (stateSplit.hasSideEffect()) {
                        stateSplit.setStateAfter(createInvalidFrameState(node));
                        state = state.addSideEffect(stateSplit);
                    } else if (currentState.invalid) {
                        stateSplit.setStateAfter(createInvalidFrameState(node));
                    } else {
                        stateSplit.setStateAfter(null);
                    }
                    if (frameState.usages().isEmpty()) {
                        GraphUtil.killWithUnusedFloatingInputs(frameState);
                    }
                }
            }
            if (node instanceof ReturnNode) {
                returnStates.add(currentState);
            } else if (node instanceof UnwindNode) {
                unwindStates.add(currentState);
            }
            return state;
        }

        @Override
        protected IterationState merge(MergeNode merge, List<IterationState> states) {
            boolean invalid = false;
            for (IterationState state : states) {
                if (state.invalid) {
                    invalid = true;
                    break;
                }
            }
            return IterationState.merge(merge, states, invalid);
        }

        public void finishProcessing(StructuredGraph graph) {
            NodeBitMap maskedSideEffects = new NodeBitMap(graph);
            NodeBitMap returnSideEffects = new NodeBitMap(graph);
            NodeBitMap unwindSideEffects = new NodeBitMap(graph);

            for (IterationState returnState : returnStates) {
                returnState.markMasked(returnSideEffects, maskedSideEffects);
            }
            for (IterationState unwindState : unwindStates) {
                unwindState.markMasked(unwindSideEffects, maskedSideEffects);
            }

            for (Node returnSideEffect : returnSideEffects) {
                if (!unwindSideEffects.contains(returnSideEffect) && !maskedSideEffects.contains(returnSideEffect)) {
                    StateSplit split = (StateSplit) returnSideEffect;
                    if (split.stateAfter() != null) {
                        split.setStateAfter(graph.add(new FrameState(BytecodeFrame.AFTER_BCI)));
                    }
                }
            }

            for (Node unwindSideEffect : unwindSideEffects) {
                if (!returnSideEffects.contains(unwindSideEffect) && !maskedSideEffects.contains(unwindSideEffect)) {
                    StateSplit split = (StateSplit) unwindSideEffect;
                    if (split.stateAfter() != null) {
                        split.setStateAfter(graph.add(new FrameState(BytecodeFrame.AFTER_EXCEPTION_BCI)));
                    }
                }
            }
        }

        @Override
        protected IterationState afterSplit(BeginNode node, IterationState oldState) {
            return oldState.addBranch(node);
        }

        @Override
        protected Map<LoopExitNode, IterationState> processLoop(LoopBeginNode loop, IterationState initialState) {
            LoopInfo<IterationState> info = ReentrantNodeIterator.processLoop(this, loop, initialState);

            boolean isNowInvalid = initialState.invalid;
            for (IterationState endState : info.endStates.values()) {
                isNowInvalid |= endState.invalid;
            }

            if (isNowInvalid) {
                loop.setStateAfter(createInvalidFrameState(loop));
            }

            IterationState endState = IterationState.merge(loop, info.endStates.values(), isNowInvalid);
            return ReentrantNodeIterator.processLoop(this, loop, endState).exitStates;
        }

        private static FrameState createInvalidFrameState(FixedNode node) {
            return node.graph().add(new FrameState(BytecodeFrame.INVALID_FRAMESTATE_BCI));
        }
    }
}
