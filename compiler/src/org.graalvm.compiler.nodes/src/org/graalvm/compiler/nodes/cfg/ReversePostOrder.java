/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.cfg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;

/**
 * Utility class computing a reverse post order of the {@link ControlFlowGraph}.
 *
 * The block order is special in that {@linkplain LoopEndNode blocks} are processed before
 * {@linkplain LoopExitNode blocks}. This has the advantage that for
 * {@linkplain Block#getRelativeFrequency() basic block frequency calculations} nested loops are
 * processed before the outer loop exit blocks allowing a linear computation of loop frequencies.
 *
 * Additionally, loops are guaranteed to be fully included in the RPO before any dominated sibling
 * loops are processed. This is necessary in order to attribute correct frequencies to loop exit
 * nodes of dominating sibling loops before processing any other code.
 */
public class ReversePostOrder {

    /**
     * Enqueue the block in the reverse post order with the next index and assign the index to the
     * block itself.
     */
    private static void enqueueBlockInRPO(Block b, Block[] reversePostOrder, int nextIndex) {
        reversePostOrder[nextIndex] = b;
        b.setId(nextIndex);
    }

    /**
     * Compute the reverse post order for the given {@link ControlFlowGraph}. The creation of
     * {@link Block} and the assignment of {@link FixedNode} to {@link Block} is already done by the
     * {@link ControlFlowGraph}.
     *
     * The algorithm has special handling for {@link LoopBeginNode} and {@link LoopExitNode} nodes
     * to ensure a loop is fully processed before any dominated code is visited.
     */
    private static void compute(ControlFlowGraph cfg, FixedNode start, Block[] rpoBlocks, int startIndex) {
        assert startIndex < rpoBlocks.length;
        LinkedList<FixedNode> toProcess = new LinkedList<>();
        toProcess.push(start);
        NodeBitMap visitedNodes = cfg.graph.createNodeBitMap();
        int currentIndex = startIndex;

        // utility data structure to handle the processing of loops
        class OpenLoopsData {
            int endsVisited;
            final LoopBeginNode lb;
            final boolean loopHasNoExits;

            OpenLoopsData(LoopBeginNode lb) {
                this.lb = lb;
                loopHasNoExits = lb.loopExits().count() == 0;
            }

            /**
             * A loop is fully processed, i.e., all body {@link Block} are part of the reverse post
             * order array, if all loop end blocks and all loop exit predecessor blocks are visited.
             */
            boolean loopFullyProcessed() {
                return allEndsVisited() && allLexPredecessorsVisited();
            }

            boolean allEndsVisited() {
                return lb.getLoopEndCount() == endsVisited;
            }

            boolean allLexPredecessorsVisited() {
                for (LoopExitNode lex : lb.loopExits()) {
                    FixedNode pred = (FixedNode) lex.predecessor();
                    if (!visitedNodes.isMarked(pred)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                return lb + "-> ends visited=" + endsVisited;
            }
        }

        // stack of open (nested) loops processed at the moment, i.e., not fully included in the
        // reverse post order yet
        ArrayDeque<OpenLoopsData> openLoops = new ArrayDeque<>();

        /**
         * Traverse the FixedNodes of the graph in a reverse post order manner by following next
         * nodes.
         */
        while (true) {

            /*
             * Select the next node to process, either a regular node from the toProcess nodes
             * (branches, merges, etc.) or the next nodes after loop exits if an (inner) loop was
             * fully processed in between
             */
            FixedNode cur = null;
            if (!openLoops.isEmpty()) {
                OpenLoopsData olPeek = openLoops.peek();
                // (one of the) last processing fully visited a loop
                if (olPeek.loopFullyProcessed()) {
                    cur = olPeek.lb.loopExits().first();
                }
            }
            if (cur == null) {
                if (!toProcess.isEmpty()) {
                    cur = toProcess.pop();
                }
            }
            // we are done
            if (cur == null) {
                break;
            }

            // remember we enter a loop, a loop needs to be fully closed before we advance to all
            // the code on the exit paths
            if (cur instanceof LoopBeginNode) {
                openLoops.push(new OpenLoopsData((LoopBeginNode) cur));
            }

            /*
             * Special handling for loop exit nodes, for motivation see:
             * ControlFlowGraph#computeFrequencies.
             *
             * Handling of loops and loop exits: In order to guarantee that a loop is fully
             * processed before advancing to any dominated (including sibling loops) code we stall
             * the processing of loop exit nodes until the entire body of a loop is processed. For
             * this we need to guarantee that all loop ends have been seen and all predecessor nodes
             * of loop exits have been seen. Anything after a loop will always have to go through
             * loop exit blocks (or deopt paths).
             */
            if (cur instanceof LoopExitNode) {
                final LoopExitNode lex = (LoopExitNode) cur;
                final OpenLoopsData ol = openLoops.peek();
                GraalError.guarantee(ol.lb == lex.loopBegin(), "Different loop on stack, should be %s is %s", lex.loopBegin(), ol.lb);
                GraalError.guarantee(ol != null, "No open loop for loop exit %s with loop begin %s", lex, lex.loopBegin());

                /*
                 * A loop is only fully visited if every block until a loop end is visited already
                 * and every predecessor node of a loop exit is visited
                 */
                if (!ol.loopFullyProcessed()) {
                    GraalError.guarantee(toProcess.size() > 0, "If a loop is not fully processed there need to be further blocks, %s", lex);
                    /*
                     * Since the current loop is not fully processed we need to stall the processing
                     * of the exit paths still. Mixing the stalled exits with the loop blocks would
                     * create an arbitrary order, and we avoid this by processing loop exit blocks
                     * at the very end of a loop. We fetch the next loop body block by finding a
                     * block that is not a loop exit as the next to process. Note that at this point
                     * there cannot be any nodes in toProcess that are after loop exits since we
                     * stall all exits until the loop is fully processed.
                     */
                    continue;
                }
                /*
                 * If we reach this point the entire body of a loop (including any non-loop exit
                 * node deopt paths) is processed. Now process all exits at once, enqueue their
                 * successors and continue normal processing.
                 */
                openLoops.pop();
                final List<LoopExitNode> loopExits = ol.lb.loopExits().snapshot();
                for (int i = loopExits.size() - 1; i >= 0; i--) {
                    final LoopExitNode singleExit = loopExits.get(i);
                    enqueueBlockInRPO(cfg.blockFor(singleExit), rpoBlocks, currentIndex++);
                    visitedNodes.mark(singleExit);
                    pushOrStall(singleExit.next(), toProcess);
                }
                continue;
            }

            final Block curBlock = cfg.blockFor(cur);
            if (cur == curBlock.getBeginNode()) {
                // we are at a block start, enqueue the actual block in the RPO
                enqueueBlockInRPO(curBlock, rpoBlocks, currentIndex++);
            }

            while (true) {
                visitedNodes.mark(cur);
                /*
                 * Depending on the block end nodes we have different actions for the different
                 * graph shapes.
                 */
                if (cur == curBlock.getEndNode()) {
                    if (cur instanceof EndNode) {
                        final EndNode endNode = (EndNode) cur;
                        // NOTE: allEndsVisited implicitly returns true for a loop begin's forward
                        // end
                        if (allEndsVisited(visitedNodes, endNode)) {
                            pushOrStall(endNode.merge(), toProcess);
                        }
                    } else if (cur instanceof LoopEndNode) {
                        final LoopEndNode len = (LoopEndNode) cur;
                        final OpenLoopsData ol = openLoops.peek();
                        GraalError.guarantee(ol.lb == len.loopBegin(), "Loop begin does not match, loop end begin %s stack loop begin %s", len.loopBegin(), ol.lb);
                        ol.endsVisited++;
                        if (ol.loopHasNoExits && ol.loopFullyProcessed()) {
                            openLoops.pop();
                        }
                    } else if (cur instanceof ControlSplitNode) {
                        final ControlSplitNode split = (ControlSplitNode) cur;
                        List<Node> successors = split.successors().snapshot();
                        for (int i = successors.size() - 1; i >= 0; i--) {
                            final FixedNode successor = (FixedNode) successors.get(i);
                            pushOrStall(successor, toProcess);
                        }
                    } else if (cur instanceof FixedWithNextNode) {
                        pushOrStall(((FixedWithNextNode) cur).next(), toProcess);
                    } else {
                        GraalError.guarantee(cur instanceof ControlSinkNode, "Node %s must be a control flow sink", cur);
                    }
                    break;
                } else {
                    cur = ((FixedWithNextNode) cur).next();
                }
            }

        }

    }

    private static void pushOrStall(FixedNode n, LinkedList<FixedNode> toProcess) {
        if (!(n instanceof LoopExitNode)) {
            toProcess.push(n);
        }
    }

    private static boolean allEndsVisited(NodeBitMap stalledEnds, AbstractEndNode current) {
        for (AbstractEndNode end : current.merge().forwardEnds()) {
            if (end != current) {
                if (!stalledEnds.isMarked(end)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Block[] identifyBlocks(ControlFlowGraph cfg, int numBlocks) {
        Block startBlock = cfg.blockFor(cfg.graph.start());
        startBlock.setPredecessors(Block.EMPTY_ARRAY);
        Block[] reversePostOrder = new Block[numBlocks];
        compute(cfg, cfg.graph.start(), reversePostOrder, 0);
        assignPredecessorsAndSuccessors(reversePostOrder, cfg);
        return reversePostOrder;

    }

    private static void computeLoopPredecessors(NodeMap<Block> nodeMap, Block block, LoopBeginNode loopBeginNode) {
        int forwardEndCount = loopBeginNode.forwardEndCount();
        LoopEndNode[] loopEnds = loopBeginNode.orderedLoopEnds();
        Block[] predecessors = new Block[forwardEndCount + loopEnds.length];
        for (int i = 0; i < forwardEndCount; ++i) {
            predecessors[i] = nodeMap.get(loopBeginNode.forwardEndAt(i));
        }
        for (int i = 0; i < loopEnds.length; ++i) {
            predecessors[i + forwardEndCount] = nodeMap.get(loopEnds[i]);
        }
        block.setPredecessors(predecessors);
    }

    private static void assignPredecessorsAndSuccessors(Block[] blocks, ControlFlowGraph cfg) {
        for (int bI = 0; bI < blocks.length; bI++) {
            Block b = blocks[bI];
            FixedNode blockEndNode = b.getEndNode();
            if (blockEndNode instanceof EndNode) {
                EndNode endNode = (EndNode) blockEndNode;
                Block suxBlock = cfg.getNodeToBlock().get(endNode.merge());
                b.setSuccessors(new Block[]{suxBlock});
            } else if (blockEndNode instanceof ControlSplitNode) {
                ArrayList<Block> succ = new ArrayList<>();
                Block[] ifPred = new Block[]{b};
                for (Node sux : blockEndNode.successors()) {
                    Block sucBlock = cfg.getNodeToBlock().get(sux);
                    succ.add(sucBlock);
                    sucBlock.setPredecessors(ifPred);
                }
                b.setSuccessors(succ.toArray(new Block[succ.size()]), ((ControlSplitNode) blockEndNode).successorProbabilities());
            } else if (blockEndNode instanceof LoopEndNode) {
                LoopEndNode loopEndNode = (LoopEndNode) blockEndNode;
                b.setSuccessors(new Block[]{cfg.getNodeToBlock().get(loopEndNode.loopBegin())});
            } else if (blockEndNode instanceof ControlSinkNode) {
                b.setSuccessors(Block.EMPTY_ARRAY);
            } else {
                assert !(blockEndNode instanceof AbstractEndNode) : "Algorithm only supports EndNode and LoopEndNode.";
                Block[] ifPred = new Block[]{b};
                for (Node suxNode : blockEndNode.successors()) {
                    Block sux = cfg.getNodeToBlock().get(suxNode);
                    sux.setPredecessors(ifPred);
                }
                assert blockEndNode.successors().count() == 1 : "Node " + blockEndNode;
                Block sequentialSuc = cfg.getNodeToBlock().get(blockEndNode.successors().first());
                b.setSuccessors(new Block[]{sequentialSuc});
            }
            FixedNode blockBeginNode = b.getBeginNode();
            if (blockBeginNode instanceof LoopBeginNode) {
                computeLoopPredecessors(cfg.getNodeToBlock(), b, (LoopBeginNode) blockBeginNode);
            } else if (blockBeginNode instanceof AbstractMergeNode) {
                AbstractMergeNode mergeNode = (AbstractMergeNode) blockBeginNode;
                int forwardEndCount = mergeNode.forwardEndCount();
                Block[] predecessors = new Block[forwardEndCount];
                for (int i = 0; i < forwardEndCount; ++i) {
                    predecessors[i] = cfg.getNodeToBlock().get(mergeNode.forwardEndAt(i));
                }
                b.setPredecessors(predecessors);
            }
        }
    }

}
