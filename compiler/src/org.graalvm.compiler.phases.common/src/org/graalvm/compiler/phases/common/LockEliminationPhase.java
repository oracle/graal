/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.OSRMonitorEnterNode;
import org.graalvm.compiler.nodes.java.AccessMonitorNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;

public class LockEliminationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, false);
        for (MonitorExitNode monitorExitNode : graph.getNodes(MonitorExitNode.TYPE)) {
            FixedNode next = monitorExitNode.next();
            if ((next instanceof MonitorEnterNode)) {
                // should never happen, osr monitor enters are always direct successors of the graph
                // start
                assert !(next instanceof OSRMonitorEnterNode);
                AccessMonitorNode monitorEnterNode = (AccessMonitorNode) next;
                if (isCompatibleLock(monitorEnterNode, monitorExitNode, true, cfg)) {
                    /*
                     * We've coarsened the lock so use the same monitor id for the whole region,
                     * otherwise the monitor operations appear to be unrelated.
                     */
                    MonitorIdNode enterId = monitorEnterNode.getMonitorId();
                    MonitorIdNode exitId = monitorExitNode.getMonitorId();
                    if (enterId != exitId) {
                        enterId.replaceAndDelete(exitId);
                    }
                    GraphUtil.removeFixedWithUnusedInputs(monitorEnterNode);
                    GraphUtil.removeFixedWithUnusedInputs(monitorExitNode);
                }
            }
        }
    }

    /**
     * Check that the paired monitor operations operate on the same object at the same lock depth.
     * Additionally, ensure that any {@link PiNode} in between respect a dominance relation between
     * a and b. This is necessary to ensure any monitor rewiring respects a proper schedule.
     *
     * @param a The first {@link AccessMonitorNode}
     * @param b The first {@link AccessMonitorNode}
     * @param aDominatesB if {@code true} determine if a must dominate b (including any guarded
     *            {@link PiNode} in between to determine if a and b are compatible, else if
     *            {@code false} determine if b must dominate a
     *
     */
    public static boolean isCompatibleLock(AccessMonitorNode a, AccessMonitorNode b, boolean aDominatesB, ControlFlowGraph cfg) {
        /*
         * It is not always the case that sequential monitor operations on the same object have the
         * same lock depth: Escape analysis can have removed a lock operation that was in between,
         * leading to a mismatch in lock depth.
         */
        ValueNode objectA = GraphUtil.unproxify(a.object());
        ValueNode objectB = GraphUtil.unproxify(b.object());
        if (objectA == objectB && a.getMonitorId().getLockDepth() == b.getMonitorId().getLockDepth() &&
                        a.getMonitorId().isMultipleEntry() == b.getMonitorId().isMultipleEntry()) {
            /*
             * If the monitor operations operate on the same unproxified object, ensure any pi nodes
             * in the proxy chain are safe to re-order when moving monitor operations.
             */
            Block lowestBlockA = lowestGuardedInputBlock(b, cfg);
            Block lowestBlockB = null;
            /*
             * If the object nodes are the same and there is no object or data guard for one of the
             * monitor operations it can only mean that one of them did not have to skip any pi
             * nodes while the other did. We are safe then because the object node is the same (by
             * identity) and it has to dominate both monitor operations.
             */
            if (lowestBlockA != null) {
                lowestBlockB = lowestGuardedInputBlock(b, cfg);
            }
            if (lowestBlockA == null || lowestBlockB == null) {
                return true;
            }
            if (aDominatesB) {
                return AbstractControlFlowGraph.dominates(lowestBlockA, lowestBlockB);
            } else {
                return AbstractControlFlowGraph.dominates(lowestBlockB, lowestBlockA);
            }
        }
        return false;
    }

    /**
     * Get the lowest (by dominance relation) {@link Block} for the (potentially hidden behind
     * {@link ProxyNode}s) inputs of the {@link AccessMonitorNode}.
     */
    public static Block lowestGuardedInputBlock(AccessMonitorNode monitorNode, ControlFlowGraph cfg) {
        return lowestGuardedInputBlock(unproxifyHighestGuard(monitorNode.object()), unproxifyHighestGuard(monitorNode.getObjectData()), cfg);
    }

    public static Block lowestGuardedInputBlock(GuardingNode g1, GuardingNode g2, ControlFlowGraph cfg) {
        Block b1 = getGuardingBlock(g1, cfg);
        Block b2 = getGuardingBlock(g2, cfg);
        if (b1 == null) {
            return b2;
        }
        if (b2 == null) {
            return b1;
        }
        if (AbstractControlFlowGraph.dominates(b1, b2)) {
            return b2;
        }
        return b1;
    }

    /**
     * Get the basic block of the {@link GuardingNode}. Handles fixed and floating guarded nodes.
     */
    public static Block getGuardingBlock(GuardingNode g1, ControlFlowGraph cfg) {
        Block b1 = null;
        if (g1 != null) {
            if (g1 instanceof FixedNode) {
                b1 = cfg.blockFor((Node) g1);
            } else if (g1 instanceof GuardNode) {
                AnchoringNode a = ((GuardNode) g1).getAnchor();
                if (a instanceof FixedNode) {
                    b1 = cfg.blockFor((FixedNode) a);
                }
            }
        }
        return b1;
    }

    /**
     * Get the highest (by input traversal) {@link GuardingNode} attached to any {@link ProxyNode}
     * visited in {@link GraphUtil#unproxify(ValueNode)}.
     */
    public static GuardingNode unproxifyHighestGuard(ValueNode value) {
        if (value != null) {
            ValueNode result = value;
            GuardingNode highestGuard = null;
            while (result instanceof ValueProxy) {
                GuardingNode curGuard = ((ValueProxy) result).getGuard();
                if (curGuard != null) {
                    highestGuard = curGuard;
                }
                result = ((ValueProxy) result).getOriginalNode();
            }
            return highestGuard;
        } else {
            return null;
        }
    }

}
