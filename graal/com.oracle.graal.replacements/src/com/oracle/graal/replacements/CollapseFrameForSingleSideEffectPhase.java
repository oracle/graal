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

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.LoopInfo;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

/**
 * This phase ensures that there's a single {@linkplain FrameState#AFTER_BCI collapsed frame state}
 * per path.
 * 
 * Removes other frame states from {@linkplain StateSplit#hasSideEffect() non-side-effecting} nodes
 * in the graph, and replaces them with {@linkplain FrameState#INVALID_FRAMESTATE_BCI invalid frame
 * states}.
 * 
 * The invalid frame states ensure that no deoptimization to a snippet frame state will happen.
 */
public class CollapseFrameForSingleSideEffectPhase extends Phase {
    @Override
    protected void run(StructuredGraph graph) {
        ReentrantNodeIterator.apply(new CollapseFrameForSingleSideEffectClosure(), graph.start(), null, null);
    }

    private static class CollapseFrameForSingleSideEffectClosure extends NodeIteratorClosure<StateSplit> {

        @Override
        protected StateSplit processNode(FixedNode node, StateSplit currentState) {
            StateSplit state = currentState;
            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState frameState = stateSplit.stateAfter();
                if (frameState != null) {
                    // the stateSplit == currentState case comes from merge handling
                    if (stateSplit.hasSideEffect() || stateSplit == currentState) {
                        stateSplit.setStateAfter(createInvalidFrameState(node));
                        state = stateSplit;
                    } else if (hasInvalidState(state)) {
                        stateSplit.setStateAfter(createInvalidFrameState(node));
                    } else {
                        stateSplit.setStateAfter(null);
                    }
                    if (frameState.usages().isEmpty()) {
                        GraphUtil.killWithUnusedFloatingInputs(frameState);
                    }
                }
            }
            if (node instanceof ControlSinkNode && state != null) {
                state.setStateAfter(node.graph().add(new FrameState(FrameState.AFTER_BCI)));
            }
            return state;
        }

        @Override
        protected StateSplit merge(MergeNode merge, List<StateSplit> states) {
            boolean invalid = false;
            for (StateSplit state : states) {
                if (state != null && state.stateAfter() != null && state.stateAfter().bci == FrameState.INVALID_FRAMESTATE_BCI) {
                    invalid = true;
                    state.setStateAfter(merge.graph().add(new FrameState(FrameState.AFTER_BCI)));
                }
            }
            if (invalid) {
                // at the next processNode call, stateSplit == currentState == merge
                return merge;
            } else {
                return null;
            }
        }

        @Override
        protected StateSplit afterSplit(AbstractBeginNode node, StateSplit oldState) {
            return oldState;
        }

        @Override
        protected Map<LoopExitNode, StateSplit> processLoop(LoopBeginNode loop, StateSplit initialState) {
            LoopInfo<StateSplit> info = ReentrantNodeIterator.processLoop(this, loop, initialState);
            if (!hasInvalidState(initialState)) {
                boolean isNowInvalid = false;
                for (StateSplit endState : info.endStates.values()) {
                    isNowInvalid |= hasInvalidState(endState);
                }
                if (isNowInvalid) {
                    loop.setStateAfter(createInvalidFrameState(loop));
                    info = ReentrantNodeIterator.processLoop(this, loop, loop);
                }
            }
            return info.exitStates;
        }

        private static boolean hasInvalidState(StateSplit state) {
            assert state == null || (state.stateAfter() != null && state.stateAfter().bci == FrameState.INVALID_FRAMESTATE_BCI) : state + " " + state.stateAfter();
            return state != null;
        }

        private static FrameState createInvalidFrameState(FixedNode node) {
            return node.graph().add(new FrameState(FrameState.INVALID_FRAMESTATE_BCI));
        }
    }
}
