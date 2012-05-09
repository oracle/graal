/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.types;

import java.io.*;
import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.schedule.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.types.*;
import com.oracle.graal.nodes.spi.types.TypeCanonicalizable.Result;

public class PropagateTypeCachePhase extends Phase {

    private static final boolean DUMP = false;

    private final CiTarget target;
    private final RiRuntime runtime;
    private final CiAssumptions assumptions;

    private NodeWorkList changedNodes;
    private StructuredGraph currentGraph;
    private SchedulePhase schedule;

    private TypeFeedbackChanged changed = new TypeFeedbackChanged();
    private static PrintStream out = System.out;

    private int changes = 0;

//    private static int totalChanges = 0;
//
//    static {
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            @Override
//            public void run() {
//                System.out.println("Total changes: " + totalChanges);
//            }
//        });
//    }

    public PropagateTypeCachePhase(CiTarget target, RiRuntime runtime, CiAssumptions assumptions) {
        this.target = target;
        this.runtime = runtime;
        this.assumptions = assumptions;
    }

    @Override
    protected void run(StructuredGraph graph) {

// if (!graph.method().holder().name().contains("IntegerAddNode") || !graph.method().name().equals("canonical")) {
// return;
// }

// if (!graph.method().name().equals("notifySourceElementRequestor")) {
// return;
// }

//        if (graph.method().holder().name().contains("jj_3R_75")) {
//            return;
//        }

        this.currentGraph = graph;
        new DeadCodeEliminationPhase().apply(graph);

        for (GuardNode guard : graph.getNodes(GuardNode.class)) {
            if (guard.condition() != null && guard.condition().usages().size() > 1) {
                BooleanNode clone = (BooleanNode) guard.condition().copyWithInputs();
                if (DUMP) {
                    out.println("replaced!! " + clone);
                }
                guard.setCondition(clone);
            }
        }
        for (FixedGuardNode guard : graph.getNodes(FixedGuardNode.class)) {
            for (int i = 0; i < guard.conditions().size(); i++) {
                BooleanNode condition = guard.conditions().get(i);
                if (condition != null && condition.usages().size() > 1) {
                    BooleanNode clone = (BooleanNode) condition.copyWithInputs();
                    if (DUMP) {
                        out.println("replaced!! " + clone);
                    }
                    guard.conditions().set(i, clone);
                }
            }
        }

        changedNodes = graph.createNodeWorkList(false, 10);

        schedule = new SchedulePhase();
        schedule.apply(graph);

        new Iterator().apply(schedule.getCFG().getStartBlock());

        Debug.dump(graph, "After PropagateType iteration");
        if (changes > 0) {
//            totalChanges += changes;
//            out.println(graph.method() + ": " + changes + " changes");
        }

        CanonicalizerPhase.canonicalize(graph, changedNodes, runtime, target, assumptions, null);
// outputGraph(graph);
    }

    public void outputGraph(StructuredGraph graph) {
        SchedulePhase printSchedule = new SchedulePhase();
        printSchedule.apply(graph);
        for (Block block : printSchedule.getCFG().getBlocks()) {
            System.out.print("Block " + block + " ");
            if (block == printSchedule.getCFG().getStartBlock()) {
                out.print("* ");
            }
            System.out.print("-> ");
            for (Block succ : block.getSuccessors()) {
                out.print(succ + " ");
            }
            System.out.println();
            for (Node node : printSchedule.getBlockToNodesMap().get(block)) {
                out.println("  " + node + "    (" + node.usages().size() + ")");
            }
        }
    }

    private class Iterator extends PostOrderBlockIterator {

        private final HashMap<Block, TypeFeedbackCache> caches = new HashMap<>();

        @Override
        protected void block(Block block) {
            if (DUMP) {
                out.println("======= block B" + block.getId());
            }
            final TypeFeedbackCache cache;
            if (block.getPredecessors().isEmpty()) {
                cache = new TypeFeedbackCache(runtime, currentGraph, changed);
            } else {
                if (block.getPredecessors().size() == 1) {
                    cache = caches.get(block.getPredecessors().get(0)).clone();
                    Node lastInstruction = block.getPredecessors().get(0).getEndNode();
                    if (lastInstruction instanceof ControlSplitNode && lastInstruction instanceof SplitTypeFeedbackProvider) {
                        ControlSplitNode split = (ControlSplitNode) lastInstruction;
                        int successorIndex = -1;
                        for (int i = 0; i < split.blockSuccessorCount(); i++) {
                            if (split.blockSuccessor(i) == block.getBeginNode()) {
                                successorIndex = i;
                                break;
                            }
                        }
                        assert successorIndex != -1;
                        changed.node = block.getBeginNode();
                        ((SplitTypeFeedbackProvider) split).typeFeedback(successorIndex, cache);
                        if (DUMP) {
                            out.println("split (edge " + successorIndex + ") " + split + ": " + cache);
                        }
                        changed.node = null;
                    }
                } else {
                    TypeFeedbackCache[] cacheList = new TypeFeedbackCache[block.getPredecessors().size()];
                    MergeNode merge = (MergeNode) block.getBeginNode();
                    for (int i = 0; i < block.getPredecessors().size(); i++) {
                        Block predecessor = block.getPredecessors().get(i);
                        TypeFeedbackCache other = caches.get(predecessor);
                        int endIndex = merge.forwardEndIndex((EndNode) predecessor.getEndNode());
                        cacheList[endIndex] = other;
                    }
                    changed.node = merge;
                    cache = TypeFeedbackCache.meet(cacheList, merge.phis());
                    changed.node = null;
                    if (DUMP) {
                        out.println("merge " + merge + ": " + cache);
                    }
                }
            }
            processNodes(block, cache);
        }

        private void processNodes(Block block, TypeFeedbackCache cache) {
            for (Node node : schedule.nodesFor(block)) {
                if (node.isAlive()) {
                    if (DUMP) {
                        out.println(node);
                    }
                    if (node instanceof TypeCanonicalizable) {
                        Result canonical = ((TypeCanonicalizable) node).canonical(cache);

                        if (canonical != null) {
                            changes++;
// System.out.print("!");
                            if (DUMP) {
                                out.println("TypeCanonicalizable: replacing " + node + " with " + canonical);
                            }
                            if (canonical.dependencies.length > 0) {
                                for (Node usage : node.usages()) {
                                    if (usage instanceof ValueNode) {
                                        for (Node dependency : canonical.dependencies) {
                                            // TODO(lstadler) dead dependencies should be handled differently
                                            if (dependency.isAlive()) {
                                                ((ValueNode) usage).dependencies().add(dependency);
                                            }
                                        }
                                    }
                                }
                            }
                            ValueNode replacement = canonical.replacement;
                            if (node instanceof FloatingNode) {
                                currentGraph.replaceFloating((FloatingNode) node, replacement);
                            } else {
                                assert node instanceof FixedWithNextNode;
                                currentGraph.replaceFixed((FixedWithNextNode) node, replacement);
                            }
                            changedNodes.addAll(replacement.usages());
                        }
                    }
                    if (node.isAlive() && node instanceof TypeFeedbackProvider) {
                        changed.node = node;
                        ((TypeFeedbackProvider) node).typeFeedback(cache);
                        if (DUMP) {
                            out.println("  " + cache);
                        }
                        changed.node = null;
                    }
                }
            }
            caches.put(block, cache);
        }

        @Override
        protected void loopHeaderInitial(Block block) {
            if (DUMP) {
                out.println("======= loop block B" + block.getId());
            }
            assert block.getPredecessors().get(0) == block.getDominator();
            TypeFeedbackCache cache = caches.get(block.getPredecessors().get(0)).clone();
            processNodes(block, cache);
        }

        @Override
        protected boolean loopHeader(Block block, int loopVisitedCount) {
            if (DUMP) {
                out.println("======= loop again block B" + block.getId());
            }
            if (loopVisitedCount == 1) {
                TypeFeedbackCache[] cacheList = new TypeFeedbackCache[block.getPredecessors().size()];
                LoopBeginNode loop = (LoopBeginNode) block.getBeginNode();
                for (int i = 0; i < block.getPredecessors().size(); i++) {
                    Block predecessor = block.getPredecessors().get(i);
                    TypeFeedbackCache other = caches.get(predecessor);
                    int endIndex;
                    if (loop.forwardEnd() == predecessor.getEndNode()) {
                        endIndex = 0;
                    } else {
                        endIndex = loop.orderedLoopEnds().indexOf(predecessor.getEndNode()) + 1;
                        assert endIndex != 0;
                    }
                    cacheList[endIndex] = other;
                }
                TypeFeedbackCache cache = TypeFeedbackCache.meet(cacheList, loop.phis());
                if (DUMP) {
                    out.println("loop merge " + loop + ": " + cache);
                }
                processNodes(block, cache);
                return true;
            } else {
                return false;
            }
        }
    }

}
