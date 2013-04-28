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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;

public class FrameStateAssignmentPhase extends Phase {

    private static class FrameStateAssignmentState {

        private FrameState framestate;

        public FrameStateAssignmentState(FrameState framestate) {
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

    private static class FrameStateAssignementClosure extends BlockIteratorClosure<FrameStateAssignmentState> {

        @Override
        protected void processBlock(Block block, FrameStateAssignmentState currentState) {
            FixedNode node = block.getBeginNode();
            while (node != null) {
                if (node instanceof DeoptimizingNode) {
                    DeoptimizingNode deopt = (DeoptimizingNode) node;
                    if (deopt.canDeoptimize() && deopt.getDeoptimizationState() == null) {
                        FrameState state = currentState.getFramestate();
                        deopt.setDeoptimizationState(state);
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
        protected FrameStateAssignmentState merge(Block mergeBlock, List<FrameStateAssignmentState> states) {
            MergeNode merge = (MergeNode) mergeBlock.getBeginNode();
            if (merge.stateAfter() != null) {
                return new FrameStateAssignmentState(merge.stateAfter());
            }
            return new FrameStateAssignmentState(singleFrameState(merge, states));
        }

        @Override
        protected FrameStateAssignmentState cloneState(FrameStateAssignmentState oldState) {
            return new FrameStateAssignmentState(oldState.getFramestate());
        }

        @Override
        protected List<FrameStateAssignmentState> processLoop(Loop loop, FrameStateAssignmentState initialState) {
            return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
        }

    }

    @Override
    protected void run(StructuredGraph graph) {
        assert checkFixedDeopts(graph);
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, false, false);
        ReentrantBlockIterator.apply(new FrameStateAssignementClosure(), cfg.getStartBlock(), new FrameStateAssignmentState(null), null);
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

    private static FrameState singleFrameState(MergeNode merge, List<FrameStateAssignmentState> states) {
        if (states.size() == 0) {
            return null;
        }
        FrameState firstState = states.get(0).getFramestate();
        FrameState singleState = firstState;
        if (singleState == null) {
            return null;
        }
        int singleBci = singleState.bci;
        for (int i = 1; i < states.size(); ++i) {
            FrameState cur = states.get(i).getFramestate();
            if (cur == null) {
                return null;
            }

            if (cur != singleState) {
                singleState = null;
            }

            if (cur.bci != singleBci) {
                singleBci = FrameState.INVALID_FRAMESTATE_BCI;
            }

        }
        if (singleState != null) {
            return singleState;
        }

        if (singleBci != FrameState.INVALID_FRAMESTATE_BCI) {
            FrameState outerState = firstState.outerFrameState();
            boolean duringCall = firstState.duringCall();
            boolean rethrowException = firstState.rethrowException();
            int stackSize = firstState.stackSize();
            int localsSize = firstState.localsSize();
            ResolvedJavaMethod method = firstState.method();
            FrameState newState = firstState.duplicate();
            for (int i = 1; i < states.size(); ++i) {
                FrameState curState = states.get(i).getFramestate();
                assert curState.duringCall() == duringCall;
                assert curState.rethrowException() == rethrowException;
                assert curState.stackSize() == stackSize;
                assert curState.localsSize() == localsSize;
                assert curState.method() == method;
                assert curState.outerFrameState() == outerState;

                for (int j = 0; j < localsSize + stackSize; ++j) {
                    ValueNode oldValue = newState.values().get(j);
                    ValueNode curValue = curState.values().get(j);
                    newState.values().set(j, merge(merge, oldValue, curValue, i));
                }
            }

            Debug.log("Created artificial frame state with bci %d ", singleBci);
            return newState;
        }
        return null;
    }

    private static ValueNode merge(MergeNode merge, ValueNode oldValue, ValueNode newValue, int processedPreds) {
        if (oldValue == newValue) {
            return oldValue;
        }

        if (oldValue instanceof PhiNode && ((PhiNode) oldValue).merge() == merge) {
            ((PhiNode) oldValue).addInput(newValue);
            return oldValue;
        } else {
            PhiNode newPhi = merge.graph().add(new PhiNode(oldValue.kind(), merge));
            for (int i = 0; i < processedPreds; ++i) {
                newPhi.addInput(oldValue);
            }
            newPhi.addInput(newValue);
            return newPhi;
        }
    }
}
