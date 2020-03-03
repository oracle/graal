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
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

/**
 * Utility class for snippet lowering that determines which framestate can be assigned to a merge
 * node, either the before state of the replacee, or one after an effect, which must be the after
 * one, if possible.
 */
public class FramestateMergeAssignment {

    public enum MergeStateAssignment {
        BEFORE_BCI,
        AFTER_BCI,
        INVALID
    }

    public static class MergeFSAssignment extends NodeIteratorClosure<MergeStateAssignment> {

        final NodeMap<MergeStateAssignment> mergeMaps;

        public NodeMap<MergeStateAssignment> getMergeMaps() {
            return mergeMaps;
        }

        private static final boolean RUN_WITH_LOG_ON_ERROR = false;

        public boolean verify() {
            MapCursor<Node, MergeStateAssignment> stateAssignments = mergeMaps.getEntries();
            while (stateAssignments.advance()) {
                Node merge = stateAssignments.getKey();
                MergeStateAssignment fsRequirements = stateAssignments.getValue();
                switch (fsRequirements) {
                    case INVALID:
                        if (RUN_WITH_LOG_ON_ERROR) {
                            ReentrantNodeIterator.apply(new MergeFSAssignment((StructuredGraph) merge.graph(), true),
                                            ((StructuredGraph) merge.graph()).start(), MergeStateAssignment.BEFORE_BCI);
                        }
                        throw GraalError.shouldNotReachHere("Invalid snippet replacing a node before FS assignment with merge " + merge + " for graph " + merge.graph() + " other merges=" + mergeMaps);
                    default:
                        break;
                }
            }
            return true;
        }

        private final boolean logOnInvalid;

        public MergeFSAssignment(StructuredGraph graph) {
            this(graph, false);
        }

        public MergeFSAssignment(StructuredGraph graph, boolean logOnInvalid) {
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
                    /*
                     * Theoretically we must throw an error here, however if the snippet is inlined
                     * in a leaf location without ever merging again an invalid state is fine
                     */
                }
            }
            return nextStateAssignment;
        }

        @Override
        protected MergeStateAssignment merge(AbstractMergeNode merge, List<MergeStateAssignment> states) {
            /*
             * The state at a merge is either the before or after state, but if multiple effects
             * exist predeceessing a merge we must have a merged state, and this state can only
             * differ in its return values, TODO this is currently not supported
             */
            MergeStateAssignment single = states.get(0);
            for (int i = 1; i < states.size(); i++) {
                if (states.get(i) != single) {
                    single = null;
                    break;
                }
            }
            if (single == null) {
                int afterSeen = 0;
                // find out if just one was set
                for (int i = 0; i < states.size(); i++) {
                    if (states.get(i) == MergeStateAssignment.AFTER_BCI) {
                        if (afterSeen == 0) {
                            afterSeen++;
                        } else {
                            afterSeen++;
                            break;
                        }
                    }
                    if (states.get(i) == MergeStateAssignment.INVALID) {
                        break;
                    }
                }
                if (afterSeen == 1) {
                    mergeMaps.put(merge, MergeStateAssignment.AFTER_BCI);
                    return MergeStateAssignment.INVALID;
                }
                assert afterSeen == 0 || afterSeen > 1;
                mergeMaps.put(merge, MergeStateAssignment.INVALID);
                return MergeStateAssignment.INVALID;
            }
            mergeMaps.put(merge, single);
            return single;
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
