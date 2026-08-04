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

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.calc.FloatingNode;

/**
 * Dominance based duplication simulation (DBDS) is a special compiler graph analysis that simulates
 * code duplication at control flow merges in order to find subsequent optimization opportunities.
 * DBDS is a pure simulation, it must not modify the graph. DBDS enables the compiler to reason
 * about the impact of a specific duplication operation without actually performing it.
 *
 * Successor duplication is an optimization that duplicates code that references variables that are
 * defined in preceding branches. In the context of SSA form it duplicates code and updates
 * variables referencing phis with the respective phi input. This often leads to additional
 * optimization opportunities. In this sense duplication is an enabling optimization.
 *
 * The DBDS algorithm works by traversing the dominator tree of the compiler graph and propagating
 * type/value information for variables into dominated basic blocks. If the algorithm encounters a
 * basic block which is a merge it simulates a duplication by simulating how values would flow into
 * the dominated join block directly without phi nodes. In the join blocks it looks for optimization
 * opportunities with the updated values and collects them.
 *
 * The following example illustrates how opportunities are collected:
 *
 * <pre>
 *  phi = ...
 *  if (condition) {
 *      phi = constantValue1
 *  } else {
 *      phi = non-constant
 *  }
 *  // merge
 *  if (phi == constantValue2) {
 *      // a lot of complex code
 *  }
 * </pre>
 *
 * DBDS would traverse the dominator tree and encounter the merge block. In the merge block it
 * simulates how the value of the phi flows if there would be no phi and the following code would be
 * duplicated into the branches. It would see that the value of phi in the first branch is
 * constantValue1 so the condition evaluating phi == constantValue2 can only be false thus, if we
 * would duplicate the code, we could eliminate the condition and the entire code in it in the first
 * case. The code example below shows how the duplicated code would look like, which is also the
 * code DBDS simulates.
 *
 * <pre>
 *  phi = ...
 *  if (condition) {
 *      phi = constantValue1
 *      // the following if can now be eliminated
 *      if (phi == constantValue2) {
 *          // a lot of complex code
 *      }
 *  } else {
 *      phi = non-constant
 *      if (phi == constantValue2) {
 *          // a lot of complex code
 *      }
 *  }
 *  // merge
 * </pre>
 */
public class DuplicationSimulationUtil {
    static final SimulationEndInfo[] EMPTY_IMPROVEMENTS = new SimulationEndInfo[]{};

    enum SimulationAdvancement {
        /**
         * Continue the simulation.
         */
        CONTINUE,
        /**
         * Stop the simulation here due to e.g. a canonicalization that kills an entire branch.
         */
        STOP,
        /**
         * Continue the simulation as an opportunity was found.
         */
        OP_FOUND,
        /**
         * A special operation from the conditional elimination was found.
         */
        CE_OP_FOUND
    }

    public static void inRegion(NodeBitMap inRegion, NodeBitMap processed, NodeBitMap visited, MergeNode simulationStartMerge, Node n, boolean root, Consumer<Node> inRegionConsumer) {
        if (processed.isMarked(n)) {
            /*
             * Node is processed, look if it was deduced to be in the region
             */
            return;
        }
        NodeStack stack = new NodeStack();
        stack.push(n);
        while (!stack.isEmpty()) {
            Node cur = stack.peek();
            if (processed.isMarked(cur)) {
                stack.pop();
                continue;
            }
            if (cur.getNodeClass().isLeafNode() && cur instanceof FloatingNode) {
                // Floating nodes without inputs are never in regions.
                processed.mark(cur);
                stack.pop();
                visited.mark(cur);
                continue;
            }
            if (visited.isMarked(cur)) {
                cur = stack.pop();
                if (inRegion.isMarked(cur)) {
                    continue;
                }
                // second time we see this node, all inputs are processed already
                if (cur instanceof MergeNode) {
                    processed.mark(cur);
                    continue;
                } else if (cur instanceof PhiNode) {
                    // non simulation merge phis are never in the region
                    processed.mark(cur);
                    continue;
                }
                for (Node input : cur.inputs()) {
                    if (inRegion.isMarked(input)) {
                        inRegion.mark(cur);
                        inRegionConsumer.accept(cur);
                        assert !processed.isMarked(cur) : "Cannot process node twice";
                        break;
                    }
                }
                processed.mark(cur);
            } else {
                visited.mark(cur);
                if (root && n == cur) {
                    inRegion.mark(cur);
                    stack.pop();
                    cur.pushInputs(stack);
                    continue;
                }
                if (!inRegion.isMarked(cur)) {
                    if (cur == simulationStartMerge) {
                        stack.pop();
                        continue;
                    } else if (cur instanceof EndNode) {
                        stack.pop();
                        continue;
                    } else if (cur instanceof PhiNode) {
                        // phi nodes are in region if it is the region merge phi
                        if (simulationStartMerge.isPhiAtMerge(cur)) {
                            inRegion.mark(cur);
                        }
                        stack.pop();
                        continue;
                    } else if (cur instanceof LoopEndNode) {
                        // proxy nodes and loop ends are in region but we break loop cycles
                        // here
                        stack.pop();
                        continue;
                    } else if (cur instanceof VirtualState) {
                        stack.pop();
                        continue;
                    }
                    // cur == tos --> process inputs first
                } else {
                    stack.pop();
                }
                // inputs might still contain transitive nodes not yet in region
                cur.pushInputs(stack);
            }
        }
        if (root) {
            inRegionConsumer.accept(n);
        }
        return;
    }

    @SuppressWarnings("fallthrough")
    public static EconomicSet<Node> estimateUsagesOutSide(MergeNode merge, EconomicSet<Node> inRegionNodes) {
        EconomicSet<Node> outSideUsages = EconomicSet.create(Equivalence.IDENTITY);
        ArrayDeque<Node> worklist = new ArrayDeque<>();
        // region nodes might require outside phis
        for (Node inRegion : inRegionNodes) {
            worklist.add(inRegion);
        }
        merge.phis().snapshotTo(worklist);
        // also the phis of the original merge require phi generation if there are usages below the
        // region
        while (!worklist.isEmpty()) {
            Node duplicated = worklist.pop();
            for (Node usage : duplicated.usages()) {
                if (!inRegionNodes.contains(usage)) {
                    for (Position pos : usage.inputPositions()) {
                        if (pos.get(usage) == duplicated) {
                            InputType inputType = pos.getInputType();
                            switch (inputType) {
                                case Association:
                                    if (merge.isPhiAtMerge(usage)) {
                                        break;
                                    }
                                    // fall through
                                case Extension:
                                case Condition:
                                case State: {
                                    if (duplicated instanceof FixedNode) {
                                        // also counted to the duplicated nodes
                                        worklist.add(usage);
                                    } else {
                                        for (Node input : duplicated.inputs()) {
                                            if (inRegionNodes.contains(input) || merge.isPhiAtMerge(input)) {
                                                worklist.add(input);
                                            }
                                        }
                                    }
                                    break;
                                }
                                case Anchor:
                                    // no phi required, will be re-routed
                                    break;
                                case Guard:
                                case Memory:
                                case Value: {
                                    if (!merge.isPhiAtMerge(usage)) {
                                        outSideUsages.add(usage);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return outSideUsages;
    }

    @SuppressWarnings("fallthrough")
    private static int usageCountForNode(MergeNode merge, Node duplicated, EconomicSet<Node> inRegionNodes) {
        int outsideUsages = 0;
        EnumSet<InputType> allowedUsageTypes = duplicated.getNodeClass().getAllowedUsageTypes();
        if (allowedUsageTypes.size() > 1) {
            for (Node usage : duplicated.usages()) {
                if (!inRegionNodes.contains(usage)) {
                    for (Position pos : usage.inputPositions()) {
                        if (pos.get(usage) == duplicated) {
                            InputType inputType = pos.getInputType();
                            switch (inputType) {
                                case Association:
                                    /*
                                     * The usage is a phi at the original merge, no additional phi
                                     * is required.
                                     */
                                    if (merge.isPhiAtMerge(usage)) {
                                        break;
                                    }
                                    // fall through
                                case Extension:
                                case Condition:
                                case State: {
                                    /*
                                     * We ignore special nodes here, and do not compute the full
                                     * transitive closure for state nodes. As this may be compile
                                     * time intensive, e.g., for virtual object state cycles. We in
                                     * general cannot create phi nodes of virtual object states,
                                     * therefore in cases of the above edge types we need to include
                                     * the (floating !) usages of a node in the cycle.
                                     *
                                     * Note: this may introduce imprecision in the usage count
                                     * detection.
                                     */
                                    break;
                                }
                                case Anchor:
                                    // no phi required, will be re-routed
                                    break;
                                case Guard:
                                case Memory:
                                case Value: {
                                    if (!merge.isPhiAtMerge(usage)) {
                                        outsideUsages++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            for (Node usage : duplicated.usages()) {
                if (!inRegionNodes.contains(usage)) {
                    /*
                     * we found one usage outside, we will create one phi for it
                     */
                    outsideUsages++;
                    break;
                }
            }
        }
        return outsideUsages;
    }

    @SuppressWarnings("try")
    public static int fastEstimateUsageCount(MergeNode merge, EconomicSet<Node> inRegionNodes) {
        int outsideUsages = 0;
        try (DebugCloseable c = DuplicationDebugUtil.phiUsagesCostTimer.start(merge.getDebug())) {
            for (Node duplicated : inRegionNodes) {
                outsideUsages += usageCountForNode(merge, duplicated, inRegionNodes);
            }
            for (Node duplicated : merge.phis()) {
                outsideUsages += usageCountForNode(merge, duplicated, inRegionNodes);
            }
        }
        return outsideUsages;
    }

}
