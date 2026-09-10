/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import static jdk.graal.compiler.phases.common.LockEliminationPhase.removeMonitorAccess;

import java.util.ArrayList;
import java.util.Optional;

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.debug.DynamicCounterNode;
import jdk.graal.compiler.nodes.extended.OSRMonitorEnterNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.AccessIndexedNode;
import jdk.graal.compiler.nodes.java.AccessMonitorNode;
import jdk.graal.compiler.nodes.java.CheckFastPathMonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.EscapeObjectState;
import jdk.graal.compiler.nodes.virtual.MaterializedObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.phases.Phase;

public class LateLockEliminationPhase extends Phase {

    static class Options {
        @Option(help = "")//
        static final OptionKey<Boolean> ProfileLockElimination = new OptionKey<>(false);
    }

    private static void insertProfiling(String name, FixedNode position) {
        if (Options.ProfileLockElimination.getValue(position.getOptions())) {
            DynamicCounterNode.addCounterBefore("Lock Elimination", "eliminated lock - " + name, 1, false, position);
        }
    }

    private static void eliminateNested(StructuredGraph graph) {
        for (MonitorIdNode monitor : graph.getNodes(MonitorIdNode.TYPE)) {
            if (monitor.getLockDepth() > 0 && !monitor.isMultipleEntry()) {
                tryEliminateNested(monitor);
            }
        }
    }

    private static void tryEliminateNested(MonitorIdNode monitor) {
        MonitorEnterNode enter = null;
        for (MonitorEnterNode operation : monitor.usages().filter(MonitorEnterNode.class)) {
            if (!isNestedLock(operation)) {
                return;
            }
            enter = operation;
        }
        if (enter != null) {
            ValueNode object = enter.object();
            for (Node usage : monitor.usages().snapshot()) {
                if (usage.isAlive()) {
                    if (usage instanceof AccessMonitorNode) {
                        removeMonitorAccess((AccessMonitorNode) usage);
                    } else if (usage instanceof FrameState || usage instanceof CheckFastPathMonitorEnterNode) {
                        // Lock state usage. Both nodes are simply tracking the current lock stack
                        // so they don't need any adjustment when nested locks are removed.
                    } else {
                        throw GraalError.shouldNotReachHere("unexpected usage of MonitorIdNode: " + usage); // ExcludeFromJacocoGeneratedReport
                    }
                }
            }
            monitor.setEliminated();
            monitor.graph().getOptimizationLog().withProperty("depth", monitor.getLockDepth()).withLazyProperty("type", () -> StampTool.typeOrNull(object)).report(LateLockEliminationPhase.class,
                            "NestedLockElimination", enter);
        } else {
            /*
             * MonitorIdNode without a MonitorEnterNode: the graph is already lowered, or the
             * monitor is coming from an on stack replacement. Either way, we can't eliminate the
             * lock.
             */
        }
    }

    private static boolean isNestedLock(MonitorEnterNode access) {
        if (access instanceof OSRMonitorEnterNode) {
            // The state on an OSRMonitorEnterNode shows all locks held at the OSR entry point and
            // is not a proper stateAfter so it must be explicitly filtered out here. It should
            // never be treated as nested anyway, since it's not removable.
            return false;
        }
        int accessLockDepth = access.getMonitorId().getLockDepth();
        assert accessLockDepth > 0 : "Lock depth must be positive " + accessLockDepth;
        ValueNode lockedObject = getLockedObject(access, access.object());
        FrameState state = access.stateAfter();
        while (state != null) {
            for (int i = 0; i < state.locksSize(); i++) {
                // If an earlier lock has already locked this object then the current lock is
                // nested.
                if (state.monitorIdAt(i).getLockDepth() < accessLockDepth && getLockedObject(access, state.lockAt(i)) == lockedObject) {
                    return true;
                }
            }
            state = state.outerFrameState();
        }
        /*
         * CommitAllocation produces a sequence of RawMonitorEnterNodes with incorrect FrameState so
         * look for sequential locking instead.
         */
        Node previous = access.predecessor();
        if (previous instanceof MonitorEnterNode) {
            MonitorEnterNode previousEnter = (MonitorEnterNode) previous;
            if (getLockedObject(previousEnter, previousEnter.object()) == lockedObject) {
                int prevLockDepth = previousEnter.getMonitorId().getLockDepth();
                assert prevLockDepth < accessLockDepth : prevLockDepth + " vs " + accessLockDepth;
                return true;
            }
        }
        return false;
    }

    protected static ValueNode getLockedObject(MonitorEnterNode access, ValueNode lockedObject) {
        ValueNode object = GraphUtil.unproxify(lockedObject);
        if (object instanceof VirtualObjectNode) {
            /*
             * FrameStates may still refer to the VirtualInstance even after the object has been
             * materialized, so find the materialized object.
             */
            VirtualObjectNode virtual = (VirtualObjectNode) object;
            for (int v = 0; v < access.stateAfter().virtualObjectMappingCount(); v++) {
                EscapeObjectState mapping = access.stateAfter().virtualObjectMappingAt(v);
                if (mapping.object() == virtual && mapping instanceof MaterializedObjectState) {
                    MaterializedObjectState materialized = (MaterializedObjectState) mapping;
                    object = materialized.materializedValue();
                }
            }
            object = GraphUtil.unproxify(object);
        }
        return object;
    }

    private static boolean isOptimizableLock(MonitorIdNode lock) {
        /*
         * Multiple Entry lock regions shouldn't be touched and OSRMonitorEnterNodes are generally
         * not optimizable as they represent a lock which has already been acquired by the
         * interpreter.
         */
        return !lock.isMultipleEntry() && lock.usages().filter(OSRMonitorEnterNode.class).isEmpty();
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        eliminateNested(graph);
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeDominators(true).computeFrequency(true).build();
        for (MonitorExitNode exit : graph.getNodes(MonitorExitNode.TYPE)) {
            MonitorIdNode exitId = exit.getMonitorId();
            if (!isOptimizableLock(exitId)) {
                continue;
            }

            FixedNode next = getNextNonReorderable(exit.next());
            if (next instanceof MonitorEnterNode) {
                // Apply slightly more aggressive lock coarsening
                AccessMonitorNode enter = (AccessMonitorNode) next;
                MonitorIdNode enterId = enter.getMonitorId();
                if (LockEliminationPhase.isCompatibleLock(exit, enter, true, cfg) && getEnterCount(enterId) == 1) {
                    /*
                     * A MonitorExitNode is followed by a RawMonitorEnterNode on the same object:
                     * exit and enter can safely be removed if the enter is the only enter for the
                     * corresponding MonitorIdNode. If it is not the only enter, then the two
                     * MonitorIdNodes cannot be merged.
                     */
                    insertProfiling("Exit-Enter", exit);
                    if (enterId != exitId) {
                        enterId.replaceAndDelete(exitId);
                    }
                    removeMonitorAccess(enter);
                    removeMonitorAccess(exit);
                    graph.getOptimizationLog().report(getClass(), "ExitEnterElimination", enter);
                }
            }

            if (!graph.getGraphState().getGuardsStage().areFrameStatesAtDeopts()) {
                // The following optimizations should be done after FSA
                return;
            }

            if (next instanceof ControlSplitNode && !(MemoryKill.isMemoryKill(next))) {
                ControlSplitNode split = (ControlSplitNode) next;

                // Extract all RawMonitorEnterNodes that re-lock the object and insert null for
                // paths which don't contain a matching RawMonitorEnterNode.
                ArrayList<MonitorEnterNode> enters = new ArrayList<>();
                for (Node successor : split.successors()) {
                    FixedNode successorNext = getNextNonReorderable((FixedNode) successor);
                    MonitorEnterNode enter = null;
                    if (successorNext instanceof MonitorEnterNode) {
                        GraalError.guarantee(successor instanceof AbstractBeginNode, "BeginNode expected: %s", successor);
                        enter = (MonitorEnterNode) successorNext;
                        // check that the same object is locked again
                        if (!LockEliminationPhase.isCompatibleLock(exit, enter, true, cfg)) {
                            // Lock on a different object
                            enter = null;
                        }
                    }
                    enters.add(enter);
                }

                /*
                 * count optimization opportunities: only MonitorIdNodes for which all
                 * RawMonitorEnterNodes are processed qualify for this optimization
                 */
                int optimizedCount = 0;
                for (int i = 0; i < enters.size(); i++) {
                    MonitorEnterNode enter = enters.get(i);
                    if (enter != null) {
                        if (!enters.containsAll(enter.getMonitorId().usages().filter(MonitorEnterNode.class).snapshot())) {
                            enters.set(i, null);
                        } else {
                            optimizedCount++;
                        }
                    }
                }

                if (optimizedCount > 0) {
                    int i = 0;
                    for (Node successor : split.successors()) {
                        MonitorEnterNode enter = enters.get(i);
                        if (enter != null) {
                            insertProfiling("Exit-ControlSplit-Enter", exit);
                            if (enter.getMonitorId() != exitId) {
                                enter.getMonitorId().replaceAndDelete(exitId);
                            }
                            removeMonitorAccess(enter);
                        } else {
                            graph.addAfterFixed((FixedWithNextNode) successor, (FixedNode) exit.copyWithInputs());
                        }
                        i++;
                    }
                    removeMonitorAccess(exit);
                    graph.getOptimizationLog().report(getClass(), "ExitControlSplitEnterElimination", exit);
                }
            }
        }

        for (MonitorEnterNode enter : graph.getNodes(MonitorEnterNode.TYPE)) {
            MonitorIdNode enterId = enter.getMonitorId();
            if (!isOptimizableLock(enterId)) {
                continue;
            }

            FixedNode previous = getPreviousNonReorderable((FixedNode) enter.predecessor(), enter.object(), enter.getObjectData());
            if (previous instanceof AbstractMergeNode && previous.usages().isEmpty()) {
                AbstractMergeNode merge = (AbstractMergeNode) previous;

                // extract all MonitorExitNodes that unlock the object
                ArrayList<MonitorExitNode> exits = new ArrayList<>();
                for (int i = 0; i < merge.phiPredecessorCount(); i++) {
                    AbstractEndNode predecessor = merge.phiPredecessorAt(i);
                    FixedNode predecessorPrevious = getPreviousNonReorderable((FixedNode) predecessor.predecessor(), enter.object(), enter.getObjectData());
                    MonitorExitNode exit = null;
                    if (predecessorPrevious instanceof MonitorExitNode) {
                        exit = (MonitorExitNode) predecessorPrevious;
                        // check that the same object is locked again
                        if (!LockEliminationPhase.isCompatibleLock(enter, exit, false, cfg)) {
                            // Lock on a different object
                            exit = null;
                        }
                        // The block to which the monitorenter will be duplicated must dominate the
                        // monitorenter's inputs.
                        HIRBlock lowestBlockForEnter = LockEliminationPhase.lowestGuardedInputBlock(enter, cfg);
                        if (lowestBlockForEnter != null) {
                            if (!lowestBlockForEnter.dominates(cfg.blockFor(predecessor))) {
                                exit = null;
                            }
                        }
                    }
                    exits.add(exit);
                }

                /*
                 * count optimization opportunities: only MonitorIdNodes for which all
                 * MonitorExitNodes are processed qualify for this optimization
                 */
                int optimizedCount = 0;
                for (int i = 0; i < exits.size(); i++) {
                    MonitorExitNode exit = exits.get(i);
                    if (exit != null) {
                        if (!exits.containsAll(exit.getMonitorId().usages().filter(MonitorExitNode.class).snapshot())) {
                            exits.set(i, null);
                        } else {
                            optimizedCount++;
                        }
                    }
                }

                if (optimizedCount > 0) {
                    for (int i = 0; i < merge.phiPredecessorCount(); i++) {
                        AbstractEndNode predecessor = merge.phiPredecessorAt(i);
                        MonitorExitNode exit = exits.get(i);
                        if (exit != null) {
                            insertProfiling("Exit-Merge-Enter", exit);
                            if (exit.getMonitorId() != enterId) {
                                exit.getMonitorId().replaceAndDelete(enterId);
                            }
                            removeMonitorAccess(exit);
                        } else {
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before duplicating exit");
                            graph.addBeforeFixed(predecessor, (FixedWithNextNode) enter.copyWithInputs());
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After duplicating exit");
                        }
                    }
                    removeMonitorAccess(enter);
                    graph.getOptimizationLog().report(getClass(), "ExitMergeEnterElimination", enter);
                }
            }
        }
    }

    private static FixedNode getNextNonReorderable(FixedNode node) {
        FixedNode next = node;
        while (next instanceof FixedWithNextNode && isReorderable(next)) {
            next = ((FixedWithNextNode) next).next();
        }
        return next;
    }

    private static FixedNode getPreviousNonReorderable(FixedNode node, ValueNode object, ValueNode objectData) {
        FixedNode previous = node;
        while (isReorderable(previous)) {
            if (previous instanceof ValueAnchorNode) {
                if (LockEliminationPhase.unproxifyHighestGuard(object) == previous ||
                                LockEliminationPhase.unproxifyHighestGuard(objectData) == previous) {
                    return previous;
                }
            }
            previous = (FixedNode) previous.predecessor();
        }
        return previous;
    }

    private static int getEnterCount(MonitorIdNode enterId) {
        return enterId.usages().filter(MonitorEnterNode.class).count();
    }

    /**
     * Determines if this node can be reordered with a {@code AccessMonitorNode} node. That means
     * being pulled inside or outside a lock region without conflicting with Java semantic.
     */
    public static boolean isReorderable(FixedNode next) {
        if (next instanceof DeoptimizingNode && ((DeoptimizingNode) next).canDeoptimize()) {
            return false;
        }
        if (next != null && next.graph() != null && next.graph().getGraphState().isBeforeStage(StageFlag.FINAL_PARTIAL_ESCAPE)) {
            /*
             * If we are running before PEA also reason about the simplest high tier nodes. PEA may
             * be able to help us.
             */
            if ((next instanceof AccessFieldNode af && !MemoryOrderMode.ordersMemoryAccesses(af.getMemoryOrder())) || next instanceof AccessIndexedNode) {
                return true;
            }
        }
        return next instanceof ReadNode || next instanceof WriteNode || next instanceof ValueAnchorNode || (next instanceof AbstractBeginNode && !(next instanceof AbstractMergeNode));
    }
}
