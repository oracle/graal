/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.phases;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.OSRMonitorEnterNode;
import jdk.graal.compiler.nodes.extended.OSRStartNode;
import jdk.graal.compiler.nodes.java.AccessMonitorNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.graph.MergeableState;
import jdk.graal.compiler.phases.graph.PostOrderNodeIterator;

/**
 * Ensure that the lock depths and {@link MonitorIdNode ids} agree with the enter and exits.
 */
public class VerifyLockDepthPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        /*
         * The current algorithm is only correct before PEA because it creates virtual locks, which
         * complicates checking for the correct correspondence because the FrameState can be out of
         * sync with the actual locking operations.
         */
        return NotApplicable.unlessRunBefore(this, GraphState.StageFlag.PARTIAL_ESCAPE, graphState);
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.getNodes(MonitorIdNode.TYPE).isNotEmpty();
    }

    @Override
    protected void run(StructuredGraph graph) {
        // The current algorithm is only correct before PEA because it might virtual locks, which
        // complicates the matching.
        VerifyLockDepths verify = new VerifyLockDepths(graph.start());
        verify.apply();
    }

    public static class LockStructureError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        LockStructureError(String message, Object... args) {
            super(String.format(message, args));
        }
    }

    static class VerifyLockDepths extends PostOrderNodeIterator<VerifyLockDepths.LockingState> {
        VerifyLockDepths(FixedNode start) {
            super(start, null);
            state = new LockingState();
        }

        /**
         * The current state of locks which are held.
         */
        static class LockingState extends MergeableState<LockingState> implements Cloneable {
            final ArrayList<MonitorIdNode> locks;

            LockingState() {
                locks = new ArrayList<>();
            }

            LockingState(LockingState state) {
                this.locks = new ArrayList<>(state.locks);
            }

            public void pop(MonitorExitNode exit) {
                MonitorIdNode id = exit.getMonitorId();
                if (locks.isEmpty()) {
                    throw new LockStructureError("%s: lock stack is empty at", exit);
                }
                MonitorIdNode top = locks.removeLast();
                if (top != id) {
                    throw new LockStructureError(top + " != " + id);
                }
            }

            public void push(MonitorEnterNode enter) {
                MonitorIdNode id = enter.getMonitorId();
                if (locks.size() != id.getLockDepth()) {
                    throw new LockStructureError("%s: lock depth mismatch %d != %d", enter, locks.size(), id.getLockDepth());
                }
                locks.add(id);
            }

            @Override
            public LockingState clone() {
                return new LockingState(this);
            }

            @Override
            public boolean merge(AbstractMergeNode merge, List<LockingState> withStates) {
                for (LockingState other : withStates) {
                    if (!locks.equals(other.locks)) {
                        throw new LockStructureError("%s: lock depth mismatch %d != %d", merge, locks, other.locks);
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                return "LockingState{" + locks + '}';
            }

            public void verifyState(FixedNode forNode, FrameState frameState) {
                if (frameState == null) {
                    return;
                }
                if (forNode instanceof OSRStartNode || forNode instanceof OSRMonitorEnterNode) {
                    /*
                     * Ignore the FrameState on these nodes as the FrameState on the OSRStartNode is
                     * used to construct the OSRMonitorEnterNodes which come after the start so
                     * there's always an apparent mismatch. These nodes all the have same state and
                     * it's only truly valid for the very last OSRMonitorEnterNode.
                     */
                    return;
                }
                if (locks.size() != frameState.nestedLockDepth()) {
                    throw new LockStructureError("Wrong number of locks held at %s: %d != %d", forNode, locks.size(), frameState.nestedLockDepth());
                }
                FrameState current = frameState;
                int depth = locks.size();
                while (current != null) {
                    depth -= current.locksSize();
                    for (int i = 0; i < current.locksSize(); i++) {
                        MonitorIdNode frameId = locks.get(depth + i);
                        MonitorIdNode stackId = current.monitorIdAt(i);
                        if (frameId != stackId) {
                            throw new LockStructureError("%s: mismatched lock at depth % in %s: %s != %s", forNode, i, current, frameId, stackId);
                        }
                    }
                    current = current.outerFrameState();
                }
            }
        }

        @Override
        protected void node(FixedNode node) {
            if (node instanceof DeoptimizingNode.DeoptBefore) {
                DeoptimizingNode.DeoptBefore before = (DeoptimizingNode.DeoptBefore) node;
                state.verifyState(before.asFixedNode(), before.stateBefore());
            }
            if (node instanceof DeoptimizingNode.DeoptDuring) {
                DeoptimizingNode.DeoptDuring during = (DeoptimizingNode.DeoptDuring) node;
                state.verifyState(during.asFixedNode(), during.stateDuring());
            }
            if (node instanceof MonitorEnterNode) {
                MonitorEnterNode enter = (MonitorEnterNode) node;
                state.push(enter);
                state.verifyState(node, enter.stateAfter());
            } else if (node instanceof MonitorExitNode) {
                state.pop((MonitorExitNode) node);
            } else if (node instanceof AccessMonitorNode) {
                throw GraalError.shouldNotReachHere(node.getClass().toString());
            }
            if (node instanceof DeoptimizingNode.DeoptAfter) {
                DeoptimizingNode.DeoptAfter after = (DeoptimizingNode.DeoptAfter) node;
                state.verifyState(after.asFixedNode(), after.stateAfter());
            }
        }
    }
}
