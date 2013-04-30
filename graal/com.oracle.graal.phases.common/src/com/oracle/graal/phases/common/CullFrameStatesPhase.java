/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;

/**
 * This phase culls unused FrameStates from the graph. It does a post order iteration over the
 * graph, and
 */
public class CullFrameStatesPhase extends Phase {

    private static final DebugMetric metricFrameStatesCulled = Debug.metric("FrameStatesCulled");
    private static final DebugMetric metricNodesRemoved = Debug.metric("NodesRemoved");
    private static final DebugMetric metricMergesTraversed = Debug.metric("MergesTraversed");

    @Override
    protected void run(StructuredGraph graph) {
        int initialNodes = graph.getNodeCount();
        new CullFrameStates(graph.start(), new State(null)).apply();
        metricNodesRemoved.add(initialNodes - graph.getNodeCount());
    }

    public static class State implements MergeableState<State> {

        private FrameState lastFrameState;

        public State(FrameState lastFrameState) {
            this.lastFrameState = lastFrameState;
        }

        @Override
        public boolean merge(MergeNode merge, List<State> withStates) {
            FrameState stateAfter = merge.stateAfter();
            if (merge instanceof LoopBeginNode) {
                if (stateAfter != null) {
                    lastFrameState = stateAfter;
                }
                return true;
            }
            metricMergesTraversed.increment();
            if (stateAfter != null) {
                for (State other : withStates) {
                    if (other.lastFrameState != lastFrameState) {
                        lastFrameState = stateAfter;
                        return true;
                    }
                }
                metricFrameStatesCulled.increment();
                merge.setStateAfter(null);
                if (stateAfter.usages().isEmpty()) {
                    GraphUtil.killWithUnusedFloatingInputs(stateAfter);
                }
            }
            return true;
        }

        @Override
        public void loopBegin(LoopBeginNode loopBegin) {
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<State> loopEndStates) {
        }

        @Override
        public void afterSplit(AbstractBeginNode node) {
        }

        @Override
        public State clone() {
            return new State(lastFrameState);
        }
    }

    public static class CullFrameStates extends PostOrderNodeIterator<State> {

        public CullFrameStates(FixedNode start, State initialState) {
            super(start, initialState);
        }

        @Override
        protected void node(FixedNode node) {
            if (node instanceof StateSplit) {
                FrameState stateAfter = ((StateSplit) node).stateAfter();
                if (stateAfter != null) {
                    state.lastFrameState = stateAfter;
                }
            }
        }
    }

}
