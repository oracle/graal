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
 * Removes frame states from {@linkplain StateSplit#hasSideEffect() non-side-effecting} nodes in a
 * snippet.
 * 
 * The frame states of side-effecting nodes are replaced with
 * {@linkplain FrameState#INVALID_FRAMESTATE_BCI invalid} frame states. Loops that contain invalid
 * frame states are also assigned an invalid frame state.
 * 
 * The invalid frame states ensure that no deoptimization to a snippet frame state will happen.
 */
public class SnippetFrameStateCleanupPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        ReentrantNodeIterator.apply(new SnippetFrameStateCleanupClosure(), graph.start(), false, null);
    }

    /**
     * A proper (loop-aware) iteration over the graph is used to detect loops that contain invalid
     * frame states, so that they can be marked with an invalid frame state.
     */
    private static class SnippetFrameStateCleanupClosure extends NodeIteratorClosure<Boolean> {

        @Override
        protected Boolean processNode(FixedNode node, Boolean currentState) {
            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState frameState = stateSplit.stateAfter();
                if (frameState != null) {
                    if (stateSplit.hasSideEffect()) {
                        stateSplit.setStateAfter(node.graph().add(new FrameState(FrameState.INVALID_FRAMESTATE_BCI)));
                        return true;
                    } else {
                        stateSplit.setStateAfter(null);
                    }
                    if (frameState.usages().isEmpty()) {
                        GraphUtil.killWithUnusedFloatingInputs(frameState);
                    }
                }
            }
            return currentState;
        }

        @Override
        protected Boolean merge(MergeNode merge, List<Boolean> states) {
            for (boolean state : states) {
                if (state) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected Boolean afterSplit(AbstractBeginNode node, Boolean oldState) {
            return oldState;
        }

        @Override
        protected Map<LoopExitNode, Boolean> processLoop(LoopBeginNode loop, Boolean initialState) {
            LoopInfo<Boolean> info = ReentrantNodeIterator.processLoop(this, loop, false);
            boolean containsFrameState = false;
            for (Boolean state : info.endStates.values()) {
                containsFrameState |= state;
            }
            if (containsFrameState) {
                loop.setStateAfter(loop.graph().add(new FrameState(FrameState.INVALID_FRAMESTATE_BCI)));
            }
            if (containsFrameState || initialState) {
                for (Map.Entry<?, Boolean> entry : info.exitStates.entrySet()) {
                    entry.setValue(true);
                }
            }
            return info.exitStates;
        }

    }
}
