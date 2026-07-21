/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.duplication.phases.simulation.opportunity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;

import jdk.graal.compiler.duplication.util.DuplicationUtil;

public final class PEAOpportunity extends DuplicationOpportunity implements Predicate<FixedNode> {

    public static final PEAOpportunity DEFAULT_ESCAPING_OPPORTUNITY = new PEAOpportunity(null, null);

    private final NodeBitMap visitedVirtualizableUsages;
    private final ValuePhiNode escapingPhi;

    private PEAOpportunity(StructuredGraph graph, ValuePhiNode escapingPhi) {
        this.visitedVirtualizableUsages = graph != null ? graph.createNodeBitMap() : null;
        this.escapingPhi = escapingPhi;
    }

    private PEAOpportunity(Node lastOptimizableNode, int cyclesSaved) {
        this.lastOptimizableNode = lastOptimizableNode;
        this.cyclesSaved = cyclesSaved;
        visitedVirtualizableUsages = null;
        escapingPhi = null;
    }

    void computeFinalBenefit() {
        boolean fullyVirtualizable = true;
        for (Node usage : escapingPhi.usages()) {
            if (usage instanceof FixedNode) {
                if (!visitedVirtualizableUsages.isMarked(usage)) {
                    fullyVirtualizable = false;
                }
            }
        }
        if (fullyVirtualizable) {
            // assume we save an allocation of cycles
            cyclesSaved += AbstractNewObjectNode.TYPE.cycles().value;
        }
    }

    @Override
    public boolean test(FixedNode node) {
        if (node instanceof Invoke) {
            return !((Invoke) node).callTarget().arguments().contains(escapingPhi);
        } else {
            if (DuplicationUtil.isInputOrPiInput(node, escapingPhi)) {
                // we found one usage of the original escaping phis and this usage can also be
                // virtualized if the original allocation is
                if (node instanceof Virtualizable) {
                    visitedVirtualizableUsages.mark(node);
                    // for simplicity we assume that the virtualization makes the node cost less
                    cyclesSaved += node.estimatedNodeCycles().value;
                    lastOptimizableNode = node;
                }
            }
            return node instanceof Virtualizable || !node.inputs().contains(escapingPhi);
        }
    }

    public static PEAOpportunity getPEAOpportunity(EndNode simulatedEnd, MergeNode merge, FixedNode regionEnd) {
        assert merge.forwardEnds().contains(simulatedEnd) : "Merge ends " + merge.forwardEnds() + " must contain " + simulatedEnd;

        List<PEAOpportunity> opportunities = null;

        for (ValuePhiNode phi : merge.valuePhis()) {
            int newCount = 0;
            int nonNewCount = 0;
            EndNode newEnd = null;
            EndNode nonNewEnd = null;
            for (int i = 0; i < merge.forwardEndCount(); i++) {
                EndNode end = merge.forwardEndAt(i);
                ValueNode phiValue = phi.valueAt(i);
                if (phiValue instanceof NewArrayNode && !((NewArrayNode) phiValue).length().isConstant()) {
                    nonNewCount++;
                    nonNewEnd = end;
                }
                if (phiValue instanceof VirtualizableAllocation) {
                    newCount++;
                    newEnd = end;
                } else if (phiValue instanceof AllocatedObjectNode && ((AllocatedObjectNode) phiValue).getCommit() == end.predecessor()) {
                    newCount++;
                    newEnd = end;
                } else {
                    nonNewCount++;
                    nonNewEnd = end;
                }
            }
            if (newCount > 0 && nonNewCount > 0) {
                /*
                 * we have a phi node where there are allocations that escape. we now have 3 cases
                 * that matter for duplication
                 *
                 * @formatter:off
                 * 1) all usages are within the region [merge,regionEnd] and all usages are virtualizable
                 *      -> duplicate
                 * 2) all usages are within the region [merge,nodeDominatingRegionEnd] and all usages are virtualizable
                 *      -> duplicate and shrink region
                 * 3) not all usages are within the region [merge,regionEnd] and they may or may not be virtualizable
                 *      -> do not duplicate if the allocation will eventually anyway escape (maybe incorporate profiling info here TODO)
                 * @formatter:on
                 */
                if (regionEnd instanceof AbstractEndNode && regionEnd.predecessor() instanceof CommitAllocationNode && regionEnd.predecessor().inputs().contains(phi)) {
                    // this will make it likely that the same situation will appear again
                    continue;
                }

                PEAOpportunity p = new PEAOpportunity(simulatedEnd.graph(), phi);
                boolean staysVirtualWithinRegion = DuplicationUtil.traverseLinear(merge, regionEnd, p, null);
                if (staysVirtualWithinRegion) {
                    if (simulatedEnd == newEnd && newCount <= 2) {
                        p.computeFinalBenefit();
                        if (opportunities == null) {
                            opportunities = new ArrayList<>(8);
                        }
                        opportunities.add(p);
                        continue;
                    } else if (simulatedEnd == nonNewEnd && nonNewCount <= 2) {
                        p.computeFinalBenefit();
                        if (opportunities == null) {
                            opportunities = new ArrayList<>(8);
                        }
                        opportunities.add(p);
                        continue;
                    }
                }
            }
        }
        if (opportunities != null) {
            Node lastOptimizable = null;
            // need to find the latest optimizable node
            outer: for (FixedNode fixed : GraphUtil.predecessorIterable(regionEnd)) {
                for (PEAOpportunity p : opportunities) {
                    if (fixed == p.lastOptimizableNode) {
                        lastOptimizable = fixed;
                        break outer;
                    }
                }
            }
            if (lastOptimizable == null) {
                lastOptimizable = regionEnd;
            }
            int benefit = 0;
            for (PEAOpportunity p : opportunities) {
                benefit += p.cyclesSaved;
            }
            return new PEAOpportunity(lastOptimizable, benefit);
        }
        return PEAOpportunity.DEFAULT_ESCAPING_OPPORTUNITY;
    }

}
