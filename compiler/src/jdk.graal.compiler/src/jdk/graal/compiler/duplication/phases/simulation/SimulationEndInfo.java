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
package jdk.graal.compiler.duplication.phases.simulation;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.duplication.phases.simulation.opportunity.CanonicalizationOpportunity;
import jdk.graal.compiler.duplication.phases.simulation.opportunity.DuplicationOpportunity;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.loop.Loop;

public final class SimulationEndInfo implements Comparable<SimulationEndInfo> {
    protected int killedBranches;
    protected int killedGuards;
    protected int cycles;
    protected int cyclesSaved;
    protected int codeSize;
    protected boolean sinks;
    protected boolean splits;
    protected int canonicalImprovements;
    protected final MergeNode originalMerge;
    protected final EndNode end;
    protected final double probabilityAfter;
    protected final HIRBlock forwardEnd;
    protected Node lastOptimizationAnchor;
    protected EconomicSet<Node> regionNodes;
    protected int visitedIndex;
    private boolean sealSize;
    private boolean insideLoop;
    private boolean loopVectorizable;

    public SimulationEndInfo(HIRBlock forwardEnd, double probabilityAfter, EconomicSet<Loop> vectorizableLoops) {
        this.originalMerge = (MergeNode) forwardEnd.getFirstSuccessor().getBeginNode();
        this.end = (EndNode) forwardEnd.getEndNode();
        this.probabilityAfter = probabilityAfter;
        this.forwardEnd = forwardEnd;
        regionNodes = EconomicSet.create(Equivalence.IDENTITY);
        VirtualState state = originalMerge.stateAfter();
        if (state != null) {
            appendVisited(state);
        }
        insideLoop = forwardEnd.getLoop() != null;
        if (insideLoop) {
            for (Loop loop : vectorizableLoops) {
                if (loop.loopBegin() == forwardEnd.getLoop().getHeader().getBeginNode()) {
                    loopVectorizable = true;
                }
            }
        }
    }

    public boolean isLoopVectorizable() {
        return loopVectorizable;
    }

    public void setInsideLoop(boolean insideLoop) {
        this.insideLoop = insideLoop;
    }

    public boolean isInsideLoop() {
        return insideLoop;
    }

    public Node lastOptimizationAnchor() {
        return lastOptimizationAnchor;
    }

    public int getKilledBranches() {
        return killedBranches;
    }

    public int getKilledGuards() {
        return killedGuards;
    }

    public int getCanonicalizationImprovements() {
        return canonicalImprovements;
    }

    public int getCycles() {
        return cycles;
    }

    public int getCyclesSaved() {
        return cyclesSaved;
    }

    public int getCodeSize() {
        return codeSize;
    }

    public boolean sinks() {
        return sinks;
    }

    public boolean splits() {
        return splits;
    }

    public boolean killsBranches() {
        return killedBranches > 0;
    }

    public MergeNode getOriginalMerge() {
        return originalMerge;
    }

    public EndNode getEnd() {
        return end;
    }

    public double getProbabilityAfter() {
        return probabilityAfter;
    }

    public void reduceCodeSize(int s) {
        codeSize = Math.max(0, codeSize - s);
    }

    public boolean alive() {
        return forwardEnd.getEndNode().isAlive() && ((EndNode) forwardEnd.getEndNode()).merge() == originalMerge;
    }

    private void appendVisited(Node n) {
        regionNodes.add(n);
    }

    public void sealSize() {
        this.sealSize = true;
    }

    public static boolean excludeNodeFromSize(Node n, EconomicSet<Node> regionNodes) {
        /*
         * Exclude memory access nodes that will float after floating reads and thus do not need to
         * be duplicated. A node only has to be duplicated if its inputs are part of the region.
         */
        if (n instanceof LoadFieldNode || n instanceof LoadIndexedNode) {
            if (n instanceof LoadFieldNode) {
                LoadFieldNode lf = (LoadFieldNode) n;
                if (lf.isStatic()) {
                    return true;
                } else {
                    if (!regionNodes.contains(lf.object())) {
                        return true;
                    }
                }
            } else if (n instanceof LoadIndexedNode) {
                LoadIndexedNode li = (LoadIndexedNode) n;
                if (!regionNodes.contains(li.array()) && !regionNodes.contains(li.index())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void processedNode(Node n) {
        boolean exclude = excludeNodeFromSize(n, regionNodes);
        if (!exclude) {
            if (!sealSize) {
                codeSize += n.estimatedNodeSize().value;
            }
            cycles += n.estimatedNodeCycles().value;
            // we actually found a sinking node here
            if (n instanceof ControlSinkNode || n instanceof LoopEndNode) {
                sinks = true;
            }
            if (n instanceof ControlSplitNode) {
                splits = true;
            }
        }
        appendVisited(n);
    }

    @SuppressWarnings("try")
    public void processCanonicalization(Node before, Node after) {
        /*
         * Computing the saved cycles follows the Canonicalizable rules. If the after node is null
         * we delete the before node after duplication and save the cycles of the before node. If
         * the after node is a pre-existing node in the same graph, we replace before with after at
         * all usages after duplication, thus we save the cycles of the before node. If the after
         * node is a newly created node that is not yet added to the graph we save the cycle diff
         * between before and after.
         */
        int c = 0;
        if (after == null) {
            c = before.estimatedNodeCycles().value;
        } else {
            if (after.isAlive()) {
                assert after.graph() == before.graph() : after.graph() + " vs " + before.graph();
                c = before.estimatedNodeCycles().value;
            } else {
                int beforeCycles = before.estimatedNodeCycles().value;
                int afterCycles = after.estimatedNodeCycles().value;
                int diff = beforeCycles - afterCycles;
                int minimalCanonicalizationCyclesSaved = AddNode.TYPE.cycles().value;
                if (diff <= minimalCanonicalizationCyclesSaved) {
                    /*
                     * Special case complex canonicalizations: If a canonicalization returns a new
                     * node this new node might be more expensive, or as expensive as the old node.
                     * An example would be the pattern "(a - b) - a" for which the canonicalizable
                     * will give negate(b) as a result. Negate takes e.g. 1 cycle and the original
                     * sub also takes 1 cycle. So cycles(before)-cycles(after)==cyclesSaved==0. This
                     * would result in a miss classification of the canonicalization if for example
                     * the usage count of a drops to 0 so we save cycles(a). In such a case we add a
                     * minimal saved cycles value to cyclesSaved.
                     */
                    c = minimalCanonicalizationCyclesSaved;
                } else {
                    c = diff;
                }
            }
        }
        assert NumUtil.assertNonNegativeInt(c);
        cyclesSaved += c;
        canonicalImprovements++;

        DebugContext debug = before.getDebug();
        try (DebugContext.Scope s = debug.scope("SimulationCanonicalizationReports")) {
            if (debug.isLogEnabled(DebugContext.VERBOSE_LEVEL)) {
                debug.log(DebugContext.VERBOSE_LEVEL, "Saved Canonicalization from node %s to %s with end %s merge %s, cycles saved %d.", before, after, forwardEnd.getEndNode(),
                                forwardEnd.getFirstSuccessor().getBeginNode(), c);
            }
        }

        registerLastOptimizableNode(before);
    }

    public void registerLastOptimizableNode(Node before) {
        lastOptimizationAnchor = before;
    }

    public void incrementKilledGuards() {
        killedGuards++;
    }

    public void incrementKilledBranches() {
        killedBranches++;
    }

    public void addSavedCycles(int c) {
        cyclesSaved += c;
    }

    public HIRBlock getForwardEnd() {
        return forwardEnd;
    }

    public DuplicationOpportunity canonicalOpportuntiy() {
        if (lastOptimizationAnchor != null && cyclesSaved > 0) {
            return new CanonicalizationOpportunity(cyclesSaved, lastOptimizationAnchor);
        }
        return DuplicationOpportunity.NO_OPPORTUNITY;
    }

    public boolean stateKillsMerge() {
        return getOriginalMerge().forwardEndCount() == 2;
    }

    public boolean stateKillsMergeSinking() {
        return stateKillsMerge() && sinks();
    }

    public int minimalPhiCreationCount() {
        return DuplicationSimulationUtil.fastEstimateUsageCount(originalMerge, regionNodes);
    }

    /**
     * DEBUG ONLY: Should be avoided for performance reasons.
     */
    public EconomicSet<Node> usagesOutside() {
        return DuplicationSimulationUtil.estimateUsagesOutSide(originalMerge, regionNodes);
    }

    public EconomicSet<Node> getRegionNodes() {
        return regionNodes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(end).append("->").append(originalMerge);
        sb.append("|size=").append(codeSize);
        sb.append("|cyclesSaved=").append(cyclesSaved);
        sb.append("|killesIf?").append(killedBranches > 0);
        sb.append("|sinks?").append(sinks);
        sb.append("|splits?").append(splits);
        return sb.toString();
    }

    @Override
    public int compareTo(SimulationEndInfo o) {
        SimulationEndInfo o1 = this;
        SimulationEndInfo o2 = o;
        double a = o1.forwardEnd.getRelativeFrequency() / o1.forwardEnd.getFirstSuccessor().getRelativeFrequency();
        double b = o2.forwardEnd.getRelativeFrequency() / o2.forwardEnd.getFirstSuccessor().getRelativeFrequency();
        int c = Double.compare(b, a);
        if (c != 0) {
            return c;
        }
        double d1 = o1.getCyclesSaved() * o1.getProbabilityAfter();
        double d2 = o2.getCyclesSaved() * o2.getProbabilityAfter();
        c = Double.compare(d2, d1);
        if (c != 0) {
            return c;
        }
        c = Boolean.compare(o1.killsBranches(), o2.killsBranches());
        if (c != 0) {
            return c;
        }
        return Integer.compare(o1.codeSize, o2.codeSize);
    }
}
