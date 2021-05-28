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
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
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
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.Simplifiable;

/**
 * Utility class computing a reverse post order of the {@link ControlFlowGraph}.
 *
 * The block order is special in that {@linkplain LoopEndNode blocks} are processed before
 * {@linkplain LoopExitNode blocks}. This has the advantage that for
 * {@linkplain Block#getRelativeFrequency() basic block frequency calculations} nested loops are
 * processed before the outer loop exit blocks allowing a linear computation of loop frequencies.
 */
public class ReversePostOrder {

    /**
     * Utility class for eager basic block creation. The CFG will create the shortest possible basic
     * blocks within the given control flow graph: this class has to ensure that predecessor and
     * successor blocks are set accordingly.
     *
     * "Shortest" possible is a special case in that, if a {@link StructuredGraph} is not fully
     * {@linkplain Canonicalizable canonicalized}, i.e., it contains redundant
     * {@link AbstractBeginNode}, each {@link AbstractBeginNode} will form a new basic block.
     *
     * This is necessary given that we do not want to run a full canonicalization and CFG
     * {@linkplain Simplifiable simplification}} every time to compute the {@link ControlFlowGraph}.
     */
    private static class TraversalQueueHandler {
        private final ControlFlowGraph cfg;
        /**
         * The result reverse post order block list.
         */
        private final Block[] reversePostOrder;
        /**
         * Index of the next block to be added to {@link #reversePostOrder}.
         */
        private int nextBlockIndex;
        private final NodeMap<Block> nodeMap;

        TraversalQueueHandler(ControlFlowGraph cfg, int numBlocks) {
            this.cfg = cfg;
            this.nodeMap = cfg.getNodeToBlock();
            reversePostOrder = new Block[numBlocks];
            nextBlockIndex = 0;
        }

        /**
         * Graal IR has certain requirements with respect to what can be a
         * {@link Block#getBeginNode()} and {@link Block#getEndNode()}: Every
         * {@link AbstractBeginNode} forms a new {@link Block}. This method must ensure
         * {@link Block#getPredecessors()} and {@link Block#getSuccessors()} are set accordingly.
         */
        protected Block potentiallySplitBlock(FixedNode currentNode, Block currentBlock) {
            assert currentBlock != null : "Block must not be null for node " + currentNode;
            if (currentBlock.getBeginNode() == currentNode) {
                /*
                 * We process a block begin node add the block to the next index in the traversal
                 * and set the predecessors accordingly.
                 */
                Block b = cfg.blockFor(currentNode);
                reversePostOrder[nextBlockIndex] = b;
                b.setId(nextBlockIndex);
                nextBlockIndex++;

                if (currentNode instanceof LoopBeginNode) {
                    computeLoopPredecessors(nodeMap, currentBlock, (LoopBeginNode) currentNode);
                } else if (currentNode instanceof AbstractMergeNode) {
                    AbstractMergeNode mergeNode = (AbstractMergeNode) currentNode;
                    int forwardEndCount = mergeNode.forwardEndCount();
                    Block[] predecessors = new Block[forwardEndCount];
                    for (int i = 0; i < forwardEndCount; ++i) {
                        predecessors[i] = nodeMap.get(mergeNode.forwardEndAt(i));
                    }
                    currentBlock.setPredecessors(predecessors);
                }
            }
            if (currentBlock.getEndNode() == currentNode) {
                /**
                 * We process a block end node: create the necessary successor and set them
                 * accordingly.
                 */
                if (currentNode instanceof EndNode) {
                    // next node is a merge, update successors
                    EndNode endNode = (EndNode) currentNode;
                    Block suxBlock = nodeMap.get(endNode.merge());
                    currentBlock.setSuccessors(new Block[]{suxBlock});
                } else if (currentNode instanceof ControlSplitNode) {
                    // next is a split, set successors and predecessors accordingly for the split
                    ArrayList<Block> succ = new ArrayList<>();
                    Block[] ifPred = new Block[]{currentBlock};
                    for (Node sux : currentNode.successors()) {
                        Block sucBlock = nodeMap.get(sux);
                        succ.add(sucBlock);
                        sucBlock.setPredecessors(ifPred);
                    }
                    currentBlock.setSuccessors(succ.toArray(new Block[succ.size()]));
                } else if (currentNode instanceof LoopEndNode) {
                    LoopEndNode loopEndNode = (LoopEndNode) currentNode;
                    currentBlock.setSuccessors(new Block[]{nodeMap.get(loopEndNode.loopBegin())});
                } else if (currentNode instanceof ControlSinkNode) {
                    currentBlock.setSuccessors(Block.EMPTY_ARRAY);
                } else {
                    assert !(currentNode instanceof AbstractEndNode) : "Algorithm only supports EndNode and LoopEndNode.";
                    Block[] ifPred = new Block[]{currentBlock};
                    for (Node suxNode : currentNode.successors()) {
                        Block sux = nodeMap.get(suxNode);
                        sux.setPredecessors(ifPred);
                    }
                    assert currentNode.successors().count() == 1 : "Node " + currentNode;
                    Block sequentialSuc = nodeMap.get(currentNode.successors().first());
                    currentBlock.setSuccessors(new Block[]{sequentialSuc});
                    return sequentialSuc;
                }
            }
            return currentBlock;
        }

        private LoopInfo processLoop(LoopBeginNode loop) {
            EconomicMap<FixedNode, Block> blockEndStates = apply(this, loop, cfg.blockFor(loop), loop);
            LoopInfo info = new LoopInfo(loop.loopEnds().count(), loop.loopExits().count());
            for (LoopEndNode end : loop.loopEnds()) {
                if (blockEndStates.containsKey(end)) {
                    info.endStates.put(end, blockEndStates.get(end));
                    blockEndStates.removeKey(end);
                }
            }
            for (LoopExitNode exit : loop.loopExits()) {
                if (blockEndStates.containsKey(exit)) {
                    info.exitStates.put(exit, blockEndStates.get(exit));
                    blockEndStates.removeKey(exit);
                }
            }
            /*
             * Deopt grouping can create loop exits without loop exit nodes that do not sink but
             * merge
             */
            for (FixedNode f : blockEndStates.getKeys()) {
                info.naturalExits.put(f, blockEndStates.get(f));
            }
            return info;
        }

        private Block enqueBlockUnconditionally(FixedNode newBlockBegin) {
            return cfg.blockFor(newBlockBegin);
        }

    }

    private static class LoopInfo {
        public final EconomicMap<LoopEndNode, Block> endStates;
        public final EconomicMap<LoopExitNode, Block> exitStates;
        public final EconomicMap<FixedNode, Block> naturalExits;

        LoopInfo(int endCount, int exitCount) {
            endStates = EconomicMap.create(Equivalence.IDENTITY, endCount);
            exitStates = EconomicMap.create(Equivalence.IDENTITY, exitCount);
            naturalExits = EconomicMap.create(Equivalence.IDENTITY, exitCount);
        }
    }

    private static EconomicMap<FixedNode, Block> apply(TraversalQueueHandler traversalHandler, FixedNode start, Block initialBlock, LoopBeginNode boundary) {
        assert start != null;
        Deque<AbstractBeginNode> nodeQueue = new ArrayDeque<>();
        EconomicMap<FixedNode, Block> blockEndStates = EconomicMap.create(Equivalence.IDENTITY);

        Block currentBlock = initialBlock;

        FixedNode currentFixedNode = start;

        do {
            while (currentFixedNode instanceof FixedWithNextNode) {
                if (boundary != null && currentFixedNode instanceof LoopExitNode && ((LoopExitNode) currentFixedNode).loopBegin() == boundary) {
                    blockEndStates.put(currentFixedNode, currentBlock);
                    currentFixedNode = null;
                } else {
                    FixedNode next = ((FixedWithNextNode) currentFixedNode).next();
                    currentBlock = traversalHandler.potentiallySplitBlock(currentFixedNode, currentBlock);
                    currentFixedNode = next;
                }
            }

            if (currentFixedNode != null) {
                currentBlock = traversalHandler.potentiallySplitBlock(currentFixedNode, currentBlock);

                Iterator<Node> successors = currentFixedNode.successors().iterator();
                if (!successors.hasNext()) {
                    if (currentFixedNode instanceof LoopEndNode) {
                        blockEndStates.put(currentFixedNode, currentBlock);
                    } else if (currentFixedNode instanceof EndNode) {
                        // add the end node and see if the merge is ready for processing
                        AbstractMergeNode merge = ((EndNode) currentFixedNode).merge();
                        if (merge instanceof LoopBeginNode) {
                            Block toProcess = processLoop(merge, currentFixedNode, currentBlock, blockEndStates, nodeQueue, traversalHandler);
                            if (toProcess != null) {
                                currentBlock = toProcess;
                            }
                        } else {
                            if (endsVisited(merge, currentFixedNode, blockEndStates)) {
                                currentBlock = merge(merge, currentFixedNode, currentBlock, blockEndStates, nodeQueue, traversalHandler);
                            } else {
                                assert !blockEndStates.containsKey(currentFixedNode);
                                blockEndStates.put(currentFixedNode, currentBlock);
                            }
                        }
                    }
                } else {
                    FixedNode firstSuccessor = (FixedNode) successors.next();
                    if (!successors.hasNext()) {
                        if (currentFixedNode instanceof ControlSplitNode) {
                            // strange split with a single successor, do not record the block end
                            // state since we will immediately process the next block
                            currentBlock = traversalHandler.enqueBlockUnconditionally(firstSuccessor);
                        }
                        currentFixedNode = firstSuccessor;
                        continue;
                    } else {
                        // first direct successor
                        currentBlock = traversalHandler.enqueBlockUnconditionally(firstSuccessor);
                        nodeQueue.addLast((AbstractBeginNode) firstSuccessor);
                        blockEndStates.put(firstSuccessor, currentBlock);
                        do {
                            AbstractBeginNode successor = (AbstractBeginNode) successors.next();
                            Block successorState = traversalHandler.enqueBlockUnconditionally(successor);
                            blockEndStates.put(successor, successorState);
                            nodeQueue.addLast(successor);

                        } while (successors.hasNext());
                    }

                }
            }
            if (nodeQueue.isEmpty()) {
                return blockEndStates;
            } else {
                currentFixedNode = nodeQueue.removeFirst();
                assert blockEndStates.containsKey(currentFixedNode);
                currentBlock = blockEndStates.removeKey(currentFixedNode);
            }
        } while (true);
    }

    private static Block processLoop(AbstractMergeNode merge, FixedNode currentFixedNode, Block currentBlock, EconomicMap<FixedNode, Block> blockEndStates, Deque<AbstractBeginNode> nodeQueue,
                    TraversalQueueHandler blockSplitter) {

        Block nextCurrentBlock = null;
        LoopInfo loopInfo = blockSplitter.processLoop((LoopBeginNode) merge);
        EconomicMap<LoopExitNode, Block> loopExitState = loopInfo.exitStates;
        MapCursor<LoopExitNode, Block> entry = loopExitState.getEntries();

        // process loop exit blocks in their increasing node id next
        ArrayList<LoopExitNode> lexs = new ArrayList<>();
        while (entry.advance()) {
            blockEndStates.put(entry.getKey(), entry.getValue());
            lexs.add(entry.getKey());
        }
        for (int i = lexs.size() - 1; i >= 0; i--) {
            // process loop exits next
            nodeQueue.addFirst(lexs.get(i));
        }
        MapCursor<FixedNode, Block> naturalExits = loopInfo.naturalExits.getEntries();
        while (naturalExits.advance()) {
            FixedNode naturalExit = naturalExits.getKey();
            if (naturalExit instanceof EndNode) {
                blockEndStates.put(naturalExit, naturalExits.getValue());
                MergeNode mergeAfter = (MergeNode) ((EndNode) naturalExit).merge();
                if (endsVisited(mergeAfter, currentFixedNode, blockEndStates)) {
                    nextCurrentBlock = merge(mergeAfter, currentFixedNode, currentBlock, blockEndStates, nodeQueue, blockSplitter);
                }
            } else {
                GraalError.shouldNotReachHere("Can only exit a loop " + merge + " naturally with a merge without a loop exit " + naturalExit);
            }
        }

        return nextCurrentBlock;
    }

    private static boolean endsVisited(AbstractMergeNode merge, FixedNode currentFixedNode, EconomicMap<FixedNode, Block> blockEndStates) {
        for (AbstractEndNode forwardEnd : merge.forwardEnds()) {
            if (forwardEnd != currentFixedNode && !blockEndStates.containsKey(forwardEnd)) {
                return false;
            }
        }
        return true;
    }

    private static Block merge(AbstractMergeNode merge, FixedNode currentFixedNode, Block currentBlock, EconomicMap<FixedNode, Block> blockEndStates, Deque<AbstractBeginNode> nodeQueue,
                    TraversalQueueHandler blockSplitter) {
        ArrayList<Block> states = new ArrayList<>(merge.forwardEndCount());
        for (int i = 0; i < merge.forwardEndCount(); i++) {
            AbstractEndNode forwardEnd = merge.forwardEndAt(i);
            assert forwardEnd == currentFixedNode || blockEndStates.containsKey(forwardEnd);
            Block other = forwardEnd == currentFixedNode ? currentBlock : blockEndStates.removeKey(forwardEnd);
            states.add(other);
        }
        Block nextCurrentBlock = blockSplitter.enqueBlockUnconditionally(merge);
        nodeQueue.addLast(merge);
        blockEndStates.put(merge, nextCurrentBlock);
        return nextCurrentBlock;
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

    public static Block[] identifyBlocks(ControlFlowGraph cfg, int numBlocks) {
        NodeMap<Block> nodeMap = cfg.getNodeToBlock();
        TraversalQueueHandler c = new TraversalQueueHandler(cfg, numBlocks);
        Block startBlock = cfg.blockFor(cfg.graph.start());
        startBlock.setPredecessors(Block.EMPTY_ARRAY);
        apply(c, cfg.graph.start(), startBlock, null);
        if (Assertions.detailedAssertionsEnabled(cfg.graph.getOptions())) {
            outer: for (Block b : nodeMap.getValues()) {
                for (int i = 0; i < c.reversePostOrder.length; i++) {
                    if (c.reversePostOrder[i] == b) {
                        continue outer;
                    }
                }
                GraalError.shouldNotReachHere("No mapping in reverse post oder for block " + b);
            }
            for (int i = 0; i < c.reversePostOrder.length; i++) {
                assert c.reversePostOrder[i] != null : "Null entry for block " + i + " Blocks " + Arrays.toString(c.reversePostOrder);
                assert c.reversePostOrder[i].getPredecessors() != null : "Pred null for block " + c.reversePostOrder[i];
                assert c.reversePostOrder[i].getSuccessors() != null : "Succ null for block " + c.reversePostOrder[i];
            }
        }
        return c.reversePostOrder;
    }

}
