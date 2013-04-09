/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;

public class FrameStateAssignementPhase extends Phase {

    private static class FrameStateAssignementState {

        private FrameState framestate;

        public FrameStateAssignementState(FrameState framestate) {
            this.framestate = framestate;
        }

        public FrameState getFramestate() {
            return framestate;
        }

        public void setFramestate(FrameState framestate) {
            assert framestate != null;
            this.framestate = framestate;
        }

        @Override
        public String toString() {
            return "FrameStateAssignementState: " + framestate;
        }
    }

    private static class FrameStateAssignementClosure extends BlockIteratorClosure<FrameStateAssignementState> {

        @Override
        protected void processBlock(Block block, FrameStateAssignementState currentState) {
            FixedNode node = block.getBeginNode();
            while (node != null) {
                if (node instanceof DeoptimizingNode) {
                    DeoptimizingNode deopt = (DeoptimizingNode) node;
                    if (deopt.canDeoptimize() && deopt.getDeoptimizationState() == null) {
                        deopt.setDeoptimizationState(currentState.getFramestate());
                    }
                }

                if (node instanceof StateSplit) {
                    StateSplit stateSplit = (StateSplit) node;
                    if (stateSplit.stateAfter() != null) {
                        currentState.setFramestate(stateSplit.stateAfter());
                        stateSplit.setStateAfter(null);
                    }
                }

                if (node instanceof FixedWithNextNode) {
                    node = ((FixedWithNextNode) node).next();
                } else {
                    node = null;
                }
            }
        }

        @Override
        protected FrameStateAssignementState merge(Block mergeBlock, List<FrameStateAssignementState> states) {
            MergeNode merge = (MergeNode) mergeBlock.getBeginNode();
            if (merge.stateAfter() != null) {
                return new FrameStateAssignementState(merge.stateAfter());
            }
            return new FrameStateAssignementState(singleFrameState(states));
        }

        @Override
        protected FrameStateAssignementState cloneState(FrameStateAssignementState oldState) {
            return new FrameStateAssignementState(oldState.getFramestate());
        }

        @Override
        protected List<FrameStateAssignementState> processLoop(Loop loop, FrameStateAssignementState initialState) {
            return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
        }

    }

    @Override
    protected void run(StructuredGraph graph) {
        assert checkFixedDeopts(graph);
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);
        ReentrantBlockIterator.apply(new FrameStateAssignementClosure(), cfg.getStartBlock(), new FrameStateAssignementState(null), null);
    }

    private static boolean checkFixedDeopts(StructuredGraph graph) {
        NodePredicate isFloatingNode = GraphUtil.isFloatingNode();
        for (Node n : graph.getNodes().filterInterface(DeoptimizingNode.class)) {
            if (((DeoptimizingNode) n).canDeoptimize() && isFloatingNode.apply(n)) {
                return false;
            }
        }
        return true;
    }

    private static FrameState singleFrameState(List<FrameStateAssignementState> states) {
        Iterator<FrameStateAssignementState> it = states.iterator();
        assert it.hasNext();
        FrameState first = it.next().getFramestate();
        while (it.hasNext()) {
            if (first != it.next().getFramestate()) {
                return null;
            }
        }
        return first;
    }
}
