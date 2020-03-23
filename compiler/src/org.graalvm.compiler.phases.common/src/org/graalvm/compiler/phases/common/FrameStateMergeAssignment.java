/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

/**
 * Utility class for snippet lowering. Certain nodes in Graal IR can be lowered not to another node
 * but to a (sub) graph of nodes, this is called snippet lowering for details see
 * {@linkplain Snippet}.
 *
 * If a node is lowered to a snippet the snippet can have control flow, i.e., merge nodes. Merge
 * nodes are problematic for deoptimization support in the compiler as they can cause missing
 * interpreter state (i.e. framestate) information if improperly optimized. For example, a snippet
 * lowering may create a merge node without a state, which can cause missing frame state information
 * on deoptimization points later in the compiler since we cannot deterministically decide which
 * frame state to take (which predecessor branch) if a deopt is inserted after a merge without a
 * state. (see {@linkplain GraphUtil#mayRemoveSplit(org.graalvm.compiler.nodes.IfNode) for details}.
 *
 * Therefore, this phase, based on the effects of a snippet graph, determines which frame state can
 * be assigned to a merge node.
 *
 * During lowering a node is replaced with the snippet which means there are only 2 possible states
 * that can be used inside the snippet nodes: the before frame state of the snippet lowered node and
 * the after state. Generally, if a side-effect is happening inside a snippet, only the after state
 * is a valid state to deoptimize to.
 */
public class FrameStateMergeAssignment {

    /**
     * Possible states to be used inside a snippet.
     */
    public enum MergeStateAssignment {
        /**
         * The frame state before the snippet replacee.
         */
        BEFORE_BCI,
        /**
         * The frame state after the snippet replacee.
         */
        AFTER_BCI,
        /**
         * An invalid state setup (e.g. multiple subsequent effects inside a snippet)for a
         * side-effecting node inside a snippet.
         */
        INVALID
    }

    /**
     * The iterator below visits a compiler graph in reverse post order and, based on the
     * side-effecting nodes, decides which state can be used after snippet lowering for a merge
     * node.
     */
    public static class FrameStateMergeAssignmentClosure extends NodeIteratorClosure<MergeStateAssignment> {

        /**
         * Final assignment of states to merges.
         */
        private final NodeMap<MergeStateAssignment> mergeMaps;

        public NodeMap<MergeStateAssignment> getMergeMaps() {
            return mergeMaps;
        }

        /**
         * Debugging flag to run the phase again on error to find specific invalid merges.
         */
        private static final boolean RUN_WITH_LOG_ON_ERROR = false;

        public boolean verify() {
            MapCursor<Node, MergeStateAssignment> stateAssignments = mergeMaps.getEntries();
            while (stateAssignments.advance()) {
                Node merge = stateAssignments.getKey();
                MergeStateAssignment fsRequirements = stateAssignments.getValue();
                switch (fsRequirements) {
                    case INVALID:
                        if (RUN_WITH_LOG_ON_ERROR) {
                            ReentrantNodeIterator.apply(new FrameStateMergeAssignmentClosure((StructuredGraph) merge.graph(), true),
                                            ((StructuredGraph) merge.graph()).start(), MergeStateAssignment.BEFORE_BCI);
                        }
                        throw GraalError.shouldNotReachHere("Invalid snippet replacing a node before FS assignment with merge " + merge + " for graph " + merge.graph() + " other merges=" + mergeMaps);
                    default:
                        break;
                }
            }
            return true;
        }

        /**
         * Flag to enable logging of invalid state assignments during processing.
         */
        private final boolean logOnInvalid;

        public FrameStateMergeAssignmentClosure(StructuredGraph graph) {
            this(graph, false);
        }

        public FrameStateMergeAssignmentClosure(StructuredGraph graph, boolean logOnInvalid) {
            mergeMaps = new NodeMap<>(graph);
            this.logOnInvalid = logOnInvalid;
        }

        @Override
        protected MergeStateAssignment processNode(FixedNode node, MergeStateAssignment stateAssignment) {
            MergeStateAssignment nextStateAssignment = stateAssignment;
            if (node instanceof StateSplit && ((StateSplit) node).hasSideEffect() && !(node instanceof StartNode || node instanceof AbstractMergeNode)) {
                if (stateAssignment == MergeStateAssignment.BEFORE_BCI) {
                    nextStateAssignment = MergeStateAssignment.AFTER_BCI;
                } else {
                    if (logOnInvalid) {
                        node.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Node %s creating invalid assignment", node);
                    }
                    nextStateAssignment = MergeStateAssignment.INVALID;
                }
            }
            return nextStateAssignment;
        }

        @Override
        protected MergeStateAssignment merge(AbstractMergeNode merge, List<MergeStateAssignment> states) {
            /*
             * The state at a merge is either the before or after state, but if multiple effects
             * exist preceding a merge we must have a merged state, and this state can only differ
             * in its return values. This is currently not supported.
             */
            int beforeCount = 0;
            int afterCount = 0;
            int invalidCount = 0;

            for (int i = 0; i < states.size(); i++) {
                if (states.get(i) == MergeStateAssignment.BEFORE_BCI) {
                    beforeCount++;
                } else if (states.get(i) == MergeStateAssignment.AFTER_BCI) {
                    afterCount++;
                } else {
                    invalidCount++;
                }
            }
            if (invalidCount > 0) {
                mergeMaps.put(merge, MergeStateAssignment.INVALID);
                return MergeStateAssignment.INVALID;
            } else {
                if (afterCount == 0) {
                    // only before states
                    assert beforeCount == states.size();
                    mergeMaps.put(merge, MergeStateAssignment.BEFORE_BCI);
                    return MergeStateAssignment.BEFORE_BCI;
                } else {
                    // some before, and at least one after
                    assert afterCount > 0;
                    mergeMaps.put(merge, MergeStateAssignment.AFTER_BCI);
                    return MergeStateAssignment.AFTER_BCI;
                }
            }

        }

        @Override
        protected MergeStateAssignment afterSplit(AbstractBeginNode node, MergeStateAssignment oldState) {
            return oldState;
        }

        @Override
        protected EconomicMap<LoopExitNode, MergeStateAssignment> processLoop(LoopBeginNode loop, MergeStateAssignment initialState) {
            return ReentrantNodeIterator.processLoop(this, loop, initialState).exitStates;
        }

    }
}
