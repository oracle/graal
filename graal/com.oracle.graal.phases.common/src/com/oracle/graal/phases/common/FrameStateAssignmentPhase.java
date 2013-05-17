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
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

public class FrameStateAssignmentPhase extends Phase {

    private static class FrameStateAssignmentClosure extends NodeIteratorClosure<FrameState> {

        @Override
        protected FrameState processNode(FixedNode node, FrameState currentState) {
            if (node instanceof DeoptimizingNode) {
                DeoptimizingNode deopt = (DeoptimizingNode) node;
                if (deopt.canDeoptimize() && deopt.getDeoptimizationState() == null) {
                    deopt.setDeoptimizationState(currentState);
                }
            }

            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                if (stateSplit.stateAfter() != null) {
                    FrameState newState = stateSplit.stateAfter();
                    stateSplit.setStateAfter(null);
                    return newState;
                }
            }
            return currentState;
        }

        @Override
        protected FrameState merge(MergeNode merge, List<FrameState> states) {
            if (merge.stateAfter() != null) {
                return merge.stateAfter();
            }
            return singleFrameState(merge, states);
        }

        @Override
        protected FrameState afterSplit(AbstractBeginNode node, FrameState oldState) {
            return oldState;
        }

        @Override
        protected Map<LoopExitNode, FrameState> processLoop(LoopBeginNode loop, FrameState initialState) {
            return ReentrantNodeIterator.processLoop(this, loop, initialState).exitStates;
        }

    }

    @Override
    protected void run(StructuredGraph graph) {
        assert checkFixedDeopts(graph);
        ReentrantNodeIterator.apply(new FrameStateAssignmentClosure(), graph.start(), null, null);
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

    private static FrameState singleFrameState(@SuppressWarnings("unused") MergeNode merge, List<FrameState> states) {
        if (states.size() == 0) {
            return null;
        }
        FrameState firstState = states.get(0);
        FrameState singleState = firstState;
        if (singleState == null) {
            return null;
        }
        int singleBci = singleState.bci;
        for (int i = 1; i < states.size(); ++i) {
            FrameState cur = states.get(i);
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
        return null;
    }
}
