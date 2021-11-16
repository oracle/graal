/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.cfg.AbstractBlockBase.BLOCK_ID_COMPARATOR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.CFGVerifier;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ProfileData.LoopFrequencyData;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

public final class ControlFlowGraph implements AbstractControlFlowGraph<Block> {

    public static class CFGOptions {
        /**
         * Take {@link LoopEndNode} frequencies to calculate the frequency of a loop instead of
         * {@link LoopExitNode} frequency. For this a sum of the {@link LoopEndNode} frequencies is
         * taken. This sum is subtracted from 1 to get the frequency of all paths out of the loop.
         * Note that this ignores the frequency of all paths out of the loop that do not go through
         * a loop exit, i.e. control flow sinks like deopt nodes that do not require explicit loop
         * exit nodes. The formula that must hold here is sumLoopEndFrequency + sumSinkFrequency +
         * sumLoopExitFrequency = 1.
         *
         * This option only exists to debug loop frequency calculation differences when taking exits
         * or ends. A vastly different loop frequency calculated with the {@link LoopEndNode} sum
         * indicates there are many non 0 frequency paths out of a loop.
         */
        //@formatter:off
        @Option(help = "Derive loop frequencies only from backedge frequencies instead of from loop exit frequencies.", type = OptionType.Debug)
        public static final OptionKey<Boolean> UseLoopEndFrequencies = new OptionKey<>(false);
        @Option(help = "Debug flag to dump loop frequency differences computed based on loop end or exit nodes."
                        + "If the frequencies diverge a lot, this may indicate missing profiles on control flow"
                        + "inside the loop body.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DumpEndVersusExitLoopFrequencies = new OptionKey<>(false);
        @Option(help = "Scaling factor of frequency difference computed based on loop ends or exits", type = OptionType.Debug)
        public static final OptionKey<Double> LoopExitVsLoopEndFrequencyDiff = new OptionKey<>(1000D);
        //@formatter:on
    }

    /**
     * Don't allow relative frequency values to be become too small or too high as this makes
     * frequency calculations over- or underflow the range of a double. This commonly happens with
     * infinite loops within infinite loops. The value is chosen a bit lower than half the maximum
     * exponent supported by double. That way we can never overflow to infinity when multiplying two
     * relative frequency values.
     */
    public static final double MIN_RELATIVE_FREQUENCY = 0x1.0p-500;
    public static final double MAX_RELATIVE_FREQUENCY = 1 / MIN_RELATIVE_FREQUENCY;

    public final StructuredGraph graph;

    private NodeMap<Block> nodeToBlock;
    private Block[] reversePostOrder;
    private List<Loop<Block>> loops;
    private int maxDominatorDepth;
    private EconomicMap<LoopBeginNode, LoopFrequencyData> localLoopFrequencyData;

    public interface RecursiveVisitor<V> {
        V enter(Block b);

        void exit(Block b, V value);
    }

    public static ControlFlowGraph computeForSchedule(StructuredGraph graph) {
        return compute(graph, true, true, true, false);
    }

    public static ControlFlowGraph compute(StructuredGraph graph, boolean connectBlocks, boolean computeLoops, boolean computeDominators, boolean computePostdominators) {
        ControlFlowGraph cfg = new ControlFlowGraph(graph);

        cfg.identifyBlocks();

        boolean loopInfoComputed = false;
        if (CFGOptions.DumpEndVersusExitLoopFrequencies.getValue(graph.getOptions())) {
            // additional loop info for sink frequencies inside the loop body
            cfg.computeLoopInformation();
            cfg.computeDominators();
            loopInfoComputed = true;
        }

        cfg.computeFrequencies();

        if (computeLoops && !loopInfoComputed) {
            cfg.computeLoopInformation();
        }
        if (computeDominators && !loopInfoComputed) {
            cfg.computeDominators();
            assert cfg.verifyRPOInnerLoopsFirst();
        }
        if (computePostdominators) {
            cfg.computePostdominators();
        }

        // there's not much to verify when connectBlocks == false
        assert !(connectBlocks || computeLoops || computeDominators || computePostdominators) || CFGVerifier.verify(cfg);
        return cfg;
    }

    private void identifyBlocks() {
        int numBlocks = 0;
        for (AbstractBeginNode begin : graph.getNodes(AbstractBeginNode.TYPE)) {
            Block block = new Block(begin);
            identifyBlock(block);
            numBlocks++;
        }
        reversePostOrder = ReversePostOrder.identifyBlocks(this, numBlocks);
    }

    public double localLoopFrequency(LoopBeginNode lb) {
        return localLoopFrequencyData.get(lb).getLoopFrequency();
    }

    public ProfileSource localLoopFrequencySource(LoopBeginNode lb) {
        return localLoopFrequencyData.get(lb).getProfileSource();
    }

    public EconomicMap<LoopBeginNode, LoopFrequencyData> getLocalLoopFrequencyData() {
        return localLoopFrequencyData;
    }

    public String dominatorTreeString() {
        return dominatorTreeString(getStartBlock());
    }

    private static String dominatorTreeString(Block b) {
        StringBuilder sb = new StringBuilder();
        sb.append(b);
        sb.append("(");
        Block firstDominated = b.getFirstDominated();
        while (firstDominated != null) {
            if (firstDominated.getDominator().getPostdominator() == firstDominated) {
                sb.append("!");
            }
            sb.append(dominatorTreeString(firstDominated));
            firstDominated = firstDominated.getDominatedSibling();
        }
        sb.append(") ");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public <V> void visitDominatorTreeDefault(RecursiveVisitor<V> visitor) {

        Block[] stack = new Block[maxDominatorDepth + 1];
        Block current = getStartBlock();
        int tos = 0;
        Object[] values = null;
        int valuesTOS = 0;

        while (tos >= 0) {
            Block state = stack[tos];
            if (state == null || state.getDominator() == null || state.getDominator().getPostdominator() != state) {
                if (state == null) {
                    // We enter this block for the first time.
                    V value = visitor.enter(current);
                    if (value != null || values != null) {
                        if (values == null) {
                            values = new Object[maxDominatorDepth + 1];
                        }
                        values[valuesTOS++] = value;
                    }

                    Block dominated = skipPostDom(current.getFirstDominated());
                    if (dominated != null) {
                        // Descend into dominated.
                        stack[tos] = dominated;
                        current = dominated;
                        stack[++tos] = null;
                        continue;
                    }
                } else {
                    Block next = skipPostDom(state.getDominatedSibling());
                    if (next != null) {
                        // Descend into dominated.
                        stack[tos] = next;
                        current = next;
                        stack[++tos] = null;
                        continue;
                    }
                }

                // Finished processing all normal dominators.
                Block postDom = current.getPostdominator();
                if (postDom != null && postDom.getDominator() == current) {
                    // Descend into post dominator.
                    stack[tos] = postDom;
                    current = postDom;
                    stack[++tos] = null;
                    continue;
                }
            }

            // Finished processing this node, exit and pop from stack.
            V value = null;
            if (values != null && valuesTOS > 0) {
                value = (V) values[--valuesTOS];
            }
            visitor.exit(current, value);
            current = current.getDominator();
            --tos;
        }
    }

    private static Block skipPostDom(Block block) {
        if (block != null && block.getDominator().getPostdominator() == block) {
            // This is an always reached block.
            return block.getDominatedSibling();
        }
        return block;
    }

    public static final class DeferredExit {

        public DeferredExit(Block block, DeferredExit next) {
            this.block = block;
            this.next = next;
        }

        private final Block block;
        private final DeferredExit next;

        public Block getBlock() {
            return block;
        }

        public DeferredExit getNext() {
            return next;
        }

    }

    public static void addDeferredExit(DeferredExit[] deferredExits, Block b) {
        Loop<Block> outermostExited = b.getDominator().getLoop();
        Loop<Block> exitBlockLoop = b.getLoop();
        assert outermostExited != null : "Dominator must be in a loop. Possible cause is a missing loop exit node.";
        while (outermostExited.getParent() != null && outermostExited.getParent() != exitBlockLoop) {
            outermostExited = outermostExited.getParent();
        }
        int loopIndex = outermostExited.getIndex();
        deferredExits[loopIndex] = new DeferredExit(b, deferredExits[loopIndex]);
    }

    @SuppressWarnings({"unchecked"})
    public <V> void visitDominatorTreeDeferLoopExits(RecursiveVisitor<V> visitor) {
        Block[] stack = new Block[getBlocks().length];
        int tos = 0;
        BitSet visited = new BitSet(getBlocks().length);
        int loopCount = getLoops().size();
        DeferredExit[] deferredExits = new DeferredExit[loopCount];
        Object[] values = null;
        int valuesTOS = 0;
        stack[0] = getStartBlock();

        while (tos >= 0) {
            Block cur = stack[tos];
            int curId = cur.getId();
            if (visited.get(curId)) {
                V value = null;
                if (values != null && valuesTOS > 0) {
                    value = (V) values[--valuesTOS];
                }
                visitor.exit(cur, value);
                --tos;
                if (cur.isLoopHeader()) {
                    int loopIndex = cur.getLoop().getIndex();
                    DeferredExit deferredExit = deferredExits[loopIndex];
                    if (deferredExit != null) {
                        while (deferredExit != null) {
                            stack[++tos] = deferredExit.block;
                            deferredExit = deferredExit.next;
                        }
                        deferredExits[loopIndex] = null;
                    }
                }
            } else {
                visited.set(curId);
                V value = visitor.enter(cur);
                if (value != null || values != null) {
                    if (values == null) {
                        values = new Object[maxDominatorDepth + 1];
                    }
                    values[valuesTOS++] = value;
                }

                Block alwaysReached = cur.getPostdominator();
                if (alwaysReached != null) {
                    if (alwaysReached.getDominator() != cur) {
                        alwaysReached = null;
                    } else if (isDominatorTreeLoopExit(alwaysReached)) {
                        addDeferredExit(deferredExits, alwaysReached);
                    } else {
                        stack[++tos] = alwaysReached;
                    }
                }

                Block b = cur.getFirstDominated();
                while (b != null) {
                    if (b != alwaysReached) {
                        if (isDominatorTreeLoopExit(b)) {
                            addDeferredExit(deferredExits, b);
                        } else {
                            stack[++tos] = b;
                        }
                    }
                    b = b.getDominatedSibling();
                }
            }
        }
    }

    public <V> void visitDominatorTree(RecursiveVisitor<V> visitor, boolean deferLoopExits) {
        if (deferLoopExits && this.getLoops().size() > 0) {
            visitDominatorTreeDeferLoopExits(visitor);
        } else {
            visitDominatorTreeDefault(visitor);
        }
    }

    public static boolean isDominatorTreeLoopExit(Block b) {
        return isDominatorTreeLoopExit(b, false);
    }

    public static boolean isDominatorTreeLoopExit(Block b, boolean considerRealExits) {
        Block dominator = b.getDominator();
        if (dominator != null && b.getLoop() != dominator.getLoop() && (!b.isLoopHeader() || dominator.getLoopDepth() >= b.getLoopDepth())) {
            return true;
        }
        if (considerRealExits) {
            if (b.getBeginNode() instanceof LoopExitNode) {
                return true;
            }
        }
        return false;
    }

    private ControlFlowGraph(StructuredGraph graph) {
        this.graph = graph;
        this.nodeToBlock = graph.createNodeMap();
    }

    /**
     * Utility class to verify that {@link ReversePostOrder} only produces reverse post order
     * traversals of the graph that contain inner loops before outer ones.
     */
    private static class RPOLoopVerification {
        int endsVisited;
        int exitsVisited;
        LoopBeginNode lb;

        RPOLoopVerification(LoopBeginNode lb) {
            this.lb = lb;
        }

        boolean loopFullyProcessed() {
            return lb.getLoopEndCount() == endsVisited && exitsVisited == lb.loopExits().count();
        }

        boolean allEndsVisited() {
            return lb.getLoopEndCount() == endsVisited;
        }

    }

    /**
     * Verification method to ensure that inner loops are processed before outer ones: see
     * {@link ControlFlowGraph#computeFrequencies()} for details.
     */
    private boolean verifyRPOInnerLoopsFirst() {
        return rpoInnerLoopsFirst(b -> {
        }, b -> {
        });
    }

    /**
     * Special note on loop exit nodes and dominator tree loop exits: Graal has a "closed" loop
     * form, this means every {@link LoopBeginNode} needs explicit {@link LoopEndNode} nodes and (if
     * it is not an endless loop) {@link LoopExitNode}. For every path exiting a loop a
     * {@link LoopExitNode} is required. There is one exception to that rule:
     * {@link DeoptimizeNode}.
     *
     * Graal does not mandate that a {@link DeoptimizeNode} is preceded by a {@link LoopExitNode}.
     * In the following example
     *
     * <pre>
     * for (int i = 0; i < end; i++) {
     *     if (condition) {
     *         deoptimize;
     *     }
     * }
     * </pre>
     *
     * the IR does not have a preceding loop exit node before the deopt node. However, for regular
     * control flow sinks (returns, throws, etc) like in the following example
     *
     * <pre>
     * for (int i = 0; i < end; i++) {
     *     if (condition) {
     *         return;
     *     }
     * }
     * </pre>
     *
     * Graal IR creates a {@link LoopExitNode} before the {@link ReturnNode}.
     *
     * Because of the "imprecision" in the definition a regular basic block exiting a loop and a
     * "dominator tree" loop exit are not necessarily the same. If a path after a control flow split
     * unconditionally flows into a deopt it is a "dominator loop exit" while a regular loop exit
     * block contains a {@linkplain LoopExitNode}.
     */
    private boolean rpoInnerLoopsFirst(Consumer<Block> perBasicBlockOption, Consumer<LoopBeginNode> loopClosedAction) {
        // worst case all loops in the graph are nested
        RPOLoopVerification[] openLoops = new RPOLoopVerification[graph.getNodes(LoopBeginNode.TYPE).count()];
        int tos = 0;
        for (Block b : reversePostOrder) {
            // we see a loop end, open a new verification level in the loop stack
            if (b.isLoopHeader()) {
                RPOLoopVerification lv = new RPOLoopVerification((LoopBeginNode) b.getBeginNode());
                openLoops[tos++] = lv;
            }

            // process this basic block
            perBasicBlockOption.accept(b);

            /*
             * General note on the verification of loop exits: A loop exit node and a dominator tree
             * loop exit are not necessarily the same, see javadoc of this method. Ideally, we would
             * like to visit all dominator loop exits during RPO verification, however we do not
             * know how many there are (given the deopt issue above), thus we just determine by the
             * loop exit nodes seen.
             */
            boolean wasExit = predecessorBlockSequentialLoopExit(b);

            FixedNode f = b.getBeginNode();
            while (true) {
                if (f instanceof LoopExitNode) {
                    LoopBeginNode closedLoop = ((LoopExitNode) f).loopBegin();
                    RPOLoopVerification lv = openLoops[tos - 1];
                    assert lv.lb == closedLoop : "Must close inner loops first before closing other ones stackLoop=" + lv.lb + " exited loop=" + closedLoop + " block=" + b;
                    if (!lv.allEndsVisited()) {
                        throw GraalError.shouldNotReachHere(
                                        "Loop ends should be visited before exits. This is a major error in the reverse post order of the control " +
                                                        "flow graph of this method. This typically means wrongly specified control-split nodes have been processed in ReversePostOrder.java.");
                    }
                    lv.exitsVisited++;
                    if (lv.loopFullyProcessed()) {
                        loopClosedAction.accept(lv.lb);
                        tos--;
                    }
                    wasExit = true;
                }
                if (f == b.getEndNode()) {
                    break;
                }
                f = ((FixedWithNextNode) f).next();
            }

            if (b.isLoopEnd()) {
                RPOLoopVerification lv = null;
                LoopEndNode len = (LoopEndNode) b.getEndNode();
                int index = tos - 1;
                if (wasExit) {
                    // irregular case a basic block that is a loop exit to an inner loop followed by
                    // a loop end to an outer loop, while the inner loop potentially is not
                    // finished, we still need to allow such cases since the code in the fraction
                    // between loop exit inner and end outer is fine, it does not change the
                    // verification logic since its a single basic block
                    while (openLoops[index].lb != len.loopBegin()) {
                        index--;
                    }
                    lv = openLoops[index];
                } else {
                    // regular case, this block contains a loop end to the inner most loop
                    lv = openLoops[tos - 1];
                }
                LoopBeginNode closedLoop = ((LoopEndNode) b.getEndNode()).loopBegin();
                assert lv.lb == closedLoop : "Must close inner loops first before closing other ones stackLoop=" + lv.lb + " ended loop=" + closedLoop + " block=" + b + "->" + b.getBeginNode();
                lv.endsVisited++;
                if (lv.loopFullyProcessed()) {
                    loopClosedAction.accept(lv.lb);
                    // the current loop was exited but we also end an outer loop, find this one and
                    // remove it from the verification stack since its done
                    if (lv.lb != openLoops[tos - 1].lb) {
                        // we actually finished the outer loop already completely through this
                        // exit-end scenario, remove it from the stack
                        RPOLoopVerification[] tmp = new RPOLoopVerification[openLoops.length];
                        System.arraycopy(openLoops, 0, tmp, 0, index);
                        System.arraycopy(openLoops, index + 1, tmp, index, openLoops.length - (index + 1));
                        openLoops = tmp;
                    }
                    tos--;
                }
            }
        }
        assert tos == 0 : "Unfinished loops on stack " + tos;
        return true;
    }

    /**
     * Determine if sequential predecessor blocks of this block in a not-fully-canonicalized graph
     * exit a loop.
     *
     * Example: Sequential basic block: loop exit -> invoke -> killing begin -> loopend/exit
     *
     * These cases cause problems in the {@link #verifyRPOInnerLoopsFirst()} loop verification of
     * inner loop blocks because the granularity of loop ends and exits are not on block boundaries:
     * a loop exit block can also be a loop end to an outer loop, which makes verification that the
     * inner loop is fully processed before we process the rest of the outer loop tricky (since we
     * already visit a loop end to an outer loop while we should first stricly process all loop
     * ends/exits of inner loops).
     */
    private static boolean predecessorBlockSequentialLoopExit(Block b) {
        Block cur = b;
        // while cur has a single predecessor which has a single successor which is cur, i.e., a
        // sequential successor, this typically only happens in not-fully-canonicalized graphs
        while (cur.getPredecessorCount() == 1 && cur.getPredecessors()[0].getSuccessorCount() == 1) {
            Block pred = cur.getPredecessors()[0];
            FixedNode f = pred.getBeginNode();
            while (true) {
                if (f instanceof LoopExitNode) {
                    return true;
                }
                if (f == pred.getEndNode()) {
                    break;
                }
                f = ((FixedWithNextNode) f).next();
            }
            cur = cur.getPredecessors()[0];
        }
        return false;
    }

    private void computeDominators() {
        assert reversePostOrder[0].getPredecessorCount() == 0 : "start block has no predecessor and therefore no dominator";
        Block[] blocks = reversePostOrder;
        int curMaxDominatorDepth = 0;
        for (int i = 1; i < blocks.length; i++) {
            Block block = blocks[i];
            assert block.getPredecessorCount() > 0;
            Block dominator = null;
            for (Block pred : block.getPredecessors()) {
                if (!pred.isLoopEnd()) {
                    dominator = ((dominator == null) ? pred : commonDominatorRaw(dominator, pred));
                }
            }
            // Fortify: Suppress Null Dereference false positive (every block apart from the first
            // is guaranteed to have a predecessor)
            assert dominator != null;

            // Set dominator.
            block.setDominator(dominator);

            // Keep dominated linked list sorted by block ID such that predecessor blocks are always
            // before successor blocks.
            Block currentDominated = dominator.getFirstDominated();
            if (currentDominated != null && currentDominated.getId() < block.getId()) {
                while (currentDominated.getDominatedSibling() != null && currentDominated.getDominatedSibling().getId() < block.getId()) {
                    currentDominated = currentDominated.getDominatedSibling();
                }
                block.setDominatedSibling(currentDominated.getDominatedSibling());
                currentDominated.setDominatedSibling(block);
            } else {
                block.setDominatedSibling(dominator.getFirstDominated());
                dominator.setFirstDominated(block);
            }

            curMaxDominatorDepth = Math.max(curMaxDominatorDepth, block.getDominatorDepth());
        }
        this.maxDominatorDepth = curMaxDominatorDepth;
        calcDominatorRanges(getStartBlock(), reversePostOrder.length);
    }

    private static void calcDominatorRanges(Block block, int size) {
        Block[] stack = new Block[size];
        stack[0] = block;
        int tos = 0;
        int myNumber = 0;

        do {
            Block cur = stack[tos];
            Block dominated = cur.getFirstDominated();

            if (cur.getDominatorNumber() == -1) {
                cur.setDominatorNumber(myNumber);
                if (dominated != null) {
                    // Push children onto stack.
                    do {
                        stack[++tos] = dominated;
                        dominated = dominated.getDominatedSibling();
                    } while (dominated != null);
                } else {
                    cur.setMaxChildDomNumber(myNumber);
                    --tos;
                }
                ++myNumber;
            } else {
                cur.setMaxChildDomNumber(dominated.getMaxChildDominatorNumber());
                --tos;
            }
        } while (tos >= 0);
    }

    private static Block commonDominatorRaw(Block a, Block b) {
        int aDomDepth = a.getDominatorDepth();
        int bDomDepth = b.getDominatorDepth();
        if (aDomDepth > bDomDepth) {
            return commonDominatorRawSameDepth(a.getDominator(aDomDepth - bDomDepth), b);
        } else {
            return commonDominatorRawSameDepth(a, b.getDominator(bDomDepth - aDomDepth));
        }
    }

    private static Block commonDominatorRawSameDepth(Block a, Block b) {
        Block iterA = a;
        Block iterB = b;
        while (iterA != iterB) {
            iterA = iterA.getDominator();
            iterB = iterB.getDominator();
        }
        return iterA;
    }

    @Override
    public Block[] getBlocks() {
        return reversePostOrder;
    }

    @Override
    public Block getStartBlock() {
        return reversePostOrder[0];
    }

    public Block[] reversePostOrder() {
        return reversePostOrder;
    }

    public NodeMap<Block> getNodeToBlock() {
        return nodeToBlock;
    }

    public Block blockFor(Node node) {
        return nodeToBlock.get(node);
    }

    public Block commonDominatorFor(NodeIterable<? extends Node> nodes) {
        Block commonDom = null;
        for (Node n : nodes) {
            Block b = blockFor(n);
            commonDom = (Block) AbstractControlFlowGraph.commonDominator(commonDom, b);
        }
        return commonDom;
    }

    @Override
    public List<Loop<Block>> getLoops() {
        return loops;
    }

    public int getMaxDominatorDepth() {
        return maxDominatorDepth;
    }

    private void identifyBlock(Block block) {
        FixedWithNextNode cur = block.getBeginNode();
        while (true) {
            assert cur.isAlive() : cur;
            assert nodeToBlock.get(cur) == null;
            nodeToBlock.set(cur, block);
            FixedNode next = cur.next();
            assert next != null : cur;
            if (next instanceof AbstractBeginNode) {
                block.endNode = cur;
                return;
            } else if (next instanceof FixedWithNextNode) {
                cur = (FixedWithNextNode) next;
            } else {
                nodeToBlock.set(next, block);
                block.endNode = next;
                return;
            }
        }
    }

    private void finishLocalLoopFrequency(LoopBeginNode lb) {
        calculateLocalLoopFrequency(lb);
        double sumAllLexFrequency = 0;
        /*
         * Take the sum of all exit frequencies and scale each exit with the importance in relation
         * to the other exits. This factor is multiplied with the real predecessor frequency.
         *
         * Note that this code is only correct if the reverse post order includes inner loops
         * completely before containing outer loops and that dominating loops have their loop exits
         * fully processed before processing any dominated code.
         */
        for (LoopExitNode lex : lb.loopExits()) {
            sumAllLexFrequency += blockFor(lex).relativeFrequency;
        }
        for (LoopExitNode lex : lb.loopExits()) {
            Block lexBlock = blockFor(lex);
            assert lexBlock != null;
            final double lexFrequency = lexBlock.getRelativeFrequency();
            final double scaleLexFrequency = lexFrequency / sumAllLexFrequency;
            final double loopPredFrequency = blockFor(lb.forwardEnd()).relativeFrequency;
            final double exitFrequency = multiplyRelativeFrequencies(scaleLexFrequency, loopPredFrequency);
            lexBlock.setRelativeFrequency(exitFrequency);
            GraalError.guarantee(blockFor(lex).relativeFrequency <= loopPredFrequency, "Lex frequency %f must be below pred frequency %f", loopPredFrequency, exitFrequency);
        }
    }

    private void computeLocalLoopFrequencies() {
        rpoInnerLoopsFirst(b -> {
            perBasicBlockFrequencyAction(b, true);
        }, lb -> {
            finishLocalLoopFrequency(lb);
        });
    }

    private double calculateLocalLoopFrequency(LoopBeginNode lb) {
        Block header = blockFor(lb);
        assert header != null;
        double loopFrequency = -1;
        ProfileSource source = ProfileSource.UNKNOWN;

        if (CFGOptions.UseLoopEndFrequencies.getValue(lb.graph().getOptions())) {
            double loopEndFrequency = 0D;
            for (LoopEndNode len : lb.loopEnds()) {
                Block endBlock = blockFor(len);
                assert endBlock != null;
                assert endBlock.relativeFrequency >= 0D;
                loopEndFrequency += endBlock.relativeFrequency;
                source = source.combine(endBlock.frequencySource);
            }

            loopEndFrequency = Math.min(1, loopEndFrequency);
            loopEndFrequency = Math.max(ControlFlowGraph.MIN_RELATIVE_FREQUENCY, loopEndFrequency);

            if (loopEndFrequency == 1D) {
                // endless loop, loop with exit and deopt unconditionally after the exit
                loopFrequency = MAX_RELATIVE_FREQUENCY;
            } else {
                double exitFrequency = 1D - loopEndFrequency;
                loopFrequency = 1D / exitFrequency;

                assert Double.isFinite(loopFrequency) : "Loop=" + lb + " Loop Frequency=" + loopFrequency + " endFrequency=" + loopEndFrequency;
                assert !Double.isNaN(loopFrequency) : "Loop=" + lb + " Loop Frequency=" + loopFrequency + " endFrequency=" + loopEndFrequency;
            }
        } else {
            /*
             * Ideally we would like to use the loop end frequency sum here because it respects
             * control flow sinks (unwinds and deopts) inside the loop (this can be seen in the
             * branch above). However, if we ever exit a loop in compiled code it means we did not
             * do so by an unwind or deopt but a loop exit, thus we ignore the end (and sink)
             * frequencies and compute loop frequency purely based on the exit frequencies.
             */
            double loopExitFrequencySum = 0D;
            for (LoopExitNode lex : lb.loopExits()) {
                Block lexBlock = blockFor(lex);
                assert lexBlock != null;
                assert lexBlock.relativeFrequency >= 0D;
                loopExitFrequencySum += lexBlock.relativeFrequency;
                source = source.combine(lexBlock.frequencySource);
            }

            loopExitFrequencySum = Math.min(1, loopExitFrequencySum);
            loopExitFrequencySum = Math.max(ControlFlowGraph.MIN_RELATIVE_FREQUENCY, loopExitFrequencySum);

            loopFrequency = 1D / loopExitFrequencySum;
            assert Double.isFinite(loopFrequency) : "Loop=" + lb + " Loop Frequency=" + loopFrequency + " lexFrequencySum=" + loopExitFrequencySum;
            assert !Double.isNaN(loopFrequency) : "Loop=" + lb + " Loop Frequency=" + loopFrequency + " lexFrequencySum=" + loopExitFrequencySum;

            if (CFGOptions.DumpEndVersusExitLoopFrequencies.getValue(lb.getOptions())) {
                debugLocalLoopFrequencies(lb, loopFrequency, loopExitFrequencySum);
            }
        }

        localLoopFrequencyData.put(lb, LoopFrequencyData.create(loopFrequency, source));
        return loopFrequency;
    }

    @SuppressWarnings("try")
    private void debugLocalLoopFrequencies(LoopBeginNode lb, final double loopFrequency, final double loopExitFrequencySum) {
        try (DebugContext.Scope s = lb.getDebug().scope("CFGFrequencyInfo")) {
            /*
             * For loops without loop exit nodes we may only have deopt loop exit paths, they are
             * however not part of the loop data structure of a Loop<HIR>, thus it might be that the
             * reverse post order did not yet visit all sinks
             */
            boolean sinkingImplicitExitsFullyVisited = true;
            double loopSinkFrequencySum = 0D;
            for (Block loopBlock : blockFor(lb).getLoop().getBlocks()) {
                FixedNode blockEndNode = loopBlock.getEndNode();
                if (blockEndNode instanceof ControlSinkNode) {
                    double sinkBlockFrequency = blockFor(blockEndNode).relativeFrequency;
                    loopSinkFrequencySum += sinkBlockFrequency;
                }
                if (blockFor(blockEndNode).relativeFrequency == -1D) {
                    sinkingImplicitExitsFullyVisited = false;
                }
            }
            final double delta = 0.01D;
            if (sinkingImplicitExitsFullyVisited) {
                // verify integrity of the CFG so far
                outer: for (Block loopBlock : blockFor(lb).getLoop().getBlocks()) {
                    if (loopBlock.isLoopHeader()) {
                        // loop exit successor frequency is weighted differently
                        continue;
                    }
                    if (isDominatorTreeLoopExit(loopBlock, true)) {
                        // loop exit successor frequency is weighted differently
                        continue outer;
                    }
                    for (Block succ : loopBlock.getSuccessors()) {
                        if (isDominatorTreeLoopExit(succ, true)) {
                            // loop exit successor frequency is weighted differently
                            continue outer;
                        }
                        if (succ.isLoopHeader()) {
                            // header frequency is set to 1 artificially to compute local
                            // frequencies
                            continue outer;
                        }
                    }
                    final double selfFrequency = loopBlock.relativeFrequency;
                    double succFrequency = 0D;
                    for (Block succ : loopBlock.getSuccessors()) {
                        succFrequency += succ.relativeFrequency;
                    }
                    if (loopBlock.getSuccessorCount() == 0) {
                        GraalError.guarantee(loopBlock.getEndNode() instanceof ControlSinkNode, "Must sink if there is no successor");
                        // frequency "lost"
                        continue;
                    }
                    if (succFrequency < selfFrequency - delta) {
                        String format = "Successors must add up for block %s with begin %s, selfF=%f succF=%f";
                        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, format, loopBlock, loopBlock.getBeginNode(), selfFrequency, succFrequency);
                        throw GraalError.shouldNotReachHere(String.format(format, loopBlock, loopBlock.getBeginNode(), selfFrequency, succFrequency));
                    }
                }
            }
            double loopEndFrequencySum = 0D;
            for (LoopEndNode len : lb.loopEnds()) {
                Block lenBlock = blockFor(len);
                loopEndFrequencySum += lenBlock.relativeFrequency;
            }
            double endBasedFrequency = 1D / (1D - loopEndFrequencySum);
            if (loopEndFrequencySum == 1D) {
                // loop without any loop exits (and no sinks)
                endBasedFrequency = MAX_RELATIVE_FREQUENCY;
            }
            // verify inner loop frequency calculations used sane loop exit frequencies
            for (Block loopBlock : blockFor(lb).getLoop().getBlocks()) {
                if (loopBlock.isLoopHeader() && loopBlock.getBeginNode() != lb) {
                    LoopBeginNode otherLoop = (LoopBeginNode) loopBlock.getBeginNode();
                    double otherLoopExitFrequencySum = 0D;
                    for (LoopExitNode lex : otherLoop.loopExits()) {
                        otherLoopExitFrequencySum += blockFor(lex).relativeFrequency;
                    }
                    // forward end
                    final double predFrequency = loopBlock.getFirstPredecessor().relativeFrequency;
                    final double frequencyDifference = Math.abs(predFrequency - otherLoopExitFrequencySum);
                    if (frequencyDifference > delta) {
                        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Frequencies diverge too much");
                        throw GraalError.shouldNotReachHere("Frequencies diverge too much");
                    }
                }
            }
            /*
             * "Endless" looking loops, i.e., loops without exit nodes (only deopt exits) look like
             * inifinite loops if we take an exit frequency of "0", which results in max frequency
             */
            final boolean hasLoopExits = lb.loopExits().count() > 0;
            if (Math.abs(endBasedFrequency - loopFrequency) > CFGOptions.LoopExitVsLoopEndFrequencyDiff.getValue(lb.getOptions()) && hasLoopExits) {
                graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph,
                                "Frequency divergence for loop %s,exitBasedFrequency=%.4f endBasedFrequency=%.4f, exitFSum=%.2f / endFSum=%.2f/ sinkSum=%.2f [allSum=%f]", lb,
                                loopFrequency, endBasedFrequency, loopExitFrequencySum, loopEndFrequencySum,
                                loopSinkFrequencySum,
                                (loopExitFrequencySum + loopEndFrequencySum + loopSinkFrequencySum));
            }
        }
    }

    private void resetBlockFrequencies() {
        for (Block block : reversePostOrder) {
            block.setRelativeFrequency(0);
        }
    }

    private void computeFrequenciesFromLocal() {
        for (Block block : reversePostOrder) {
            perBasicBlockFrequencyAction(block, false);
        }
    }

    private void perBasicBlockFrequencyAction(Block b, boolean computingLocalLoopFrequencies) {
        double relativeFrequency = -1D;
        ProfileSource source = ProfileSource.UNKNOWN;
        Block[] predecessors = b.getPredecessors();
        if (predecessors.length == 0) {
            relativeFrequency = 1D;
        } else if (predecessors.length == 1) {
            Block pred = predecessors[0];
            relativeFrequency = pred.relativeFrequency;
            if (pred.getSuccessorCount() > 1) {
                assert pred.getEndNode() instanceof ControlSplitNode;
                ControlSplitNode controlSplit = (ControlSplitNode) pred.getEndNode();
                relativeFrequency = multiplyRelativeFrequencies(relativeFrequency, controlSplit.probability(b.getBeginNode()));
                if (computingLocalLoopFrequencies) {
                    source = controlSplit.getProfileData().getProfileSource();
                }
            }
        } else {
            relativeFrequency = predecessors[0].relativeFrequency;
            for (int i = 1; i < predecessors.length; ++i) {
                relativeFrequency += predecessors[i].relativeFrequency;
                if (computingLocalLoopFrequencies) {
                    if (predecessors[i].frequencySource != null) {
                        source = source.combine(predecessors[i].frequencySource);
                    }
                }
            }
            if (b.getBeginNode() instanceof LoopBeginNode) {
                if (computingLocalLoopFrequencies) {
                    // start with a "local" loop, i.e., assume no dominating code with different
                    // frequencies
                    relativeFrequency = 1D;
                    source = ProfileSource.UNKNOWN;
                } else {
                    // take the previously computed local frequency
                    LoopBeginNode loopBegin = (LoopBeginNode) b.getBeginNode();
                    relativeFrequency = multiplyRelativeFrequencies(relativeFrequency, localLoopFrequencyData.get(loopBegin).getLoopFrequency());
                }
            }
        }
        if (relativeFrequency < MIN_RELATIVE_FREQUENCY) {
            relativeFrequency = MIN_RELATIVE_FREQUENCY;
        } else if (relativeFrequency > MAX_RELATIVE_FREQUENCY) {
            relativeFrequency = MAX_RELATIVE_FREQUENCY;
        }

        b.setRelativeFrequency(relativeFrequency);
        if (computingLocalLoopFrequencies) {
            b.setFrequencySource(source);
        }
    }

    //@formatter:off
    /*
     * Compute the frequency data for the entire control flow graph.
     * In the following documentation the term "local loop frequency" describes the frequency of a loop
     * without any enclosing code, i.e., the loop in a form where it is not dominated by any other control flow.
     *
     * The real frequency of a loop is then the result of multiplying the local loop frequency with the frequency
     * of the basic block dominating the loop.
     *
     * Consider the following CFG:
     *
     *
     *
     *                                [B0: Loop 1 Header]
     *                                        |
     *                                     [B1:if]
     *                                      /  \
     *                    [B2: Loop 2 Header]  [B9: Loop Exit Loop1 + Return]
     *                              |
     *                          [B3: If]
     *                           /    \
     *         [B4: LoopEnd Loop2]    [B5: LoopExit Loop2 + If]
     *                                         /     \
     *                                      [B6]     [B7]
     *                                         \     /
     *                                [B8:Merge + LoopEnd Loop1]
     *
     *
     *  The frequency of the loop exit basic blocks depends on the frequency of the loop header.
     *  Why? Because the loop header is visited multiple times so the frequency of the loop exit block
     *  has to be multiplied by the frequency of the loop header (which is the frequency of all loop
     *  end blocks combined). The frequency of the loop header can be derived by summing up the frequency
     *  of all loop end blocks which gives us the frequency for exiting the loop (1- sumLoopEndFrequency).
     *  The frequency of the loop can then be calculated by 1 / frequencyToExitTheLoop.
     *
     *  In a final step we  multiply the frequency of each exit with the frequency to exit the overall
     *  loop to account for the loop frequency.
     *
     *  Ideally we would only process all basic blocks once here, however, given that the frequency of an
     *  outer loop depends on loop end frequencies which can depend on the frequency of inner loops,
     *  we either ensure to process inner loops first or perform the entire process until a fix point is reached.
     *
     *  However, we can utilize a "smart" reverse post order with inner loops first to reach that goal.
     *
     *  Graph theory states that there are multiple correct reverse post order (RPO) traversals
     *  for any given graph. Consider for example the following RPO for the CFG above:
     *      [B0,B1,B2,B3,B4,B5,B6,B7,B8,B9]
     *
     *  The inner loop is sequentially in the traversal, thus we can compute its local frequency and
     *  then propagate it outwards by processing the entire CFG once again.
     *
     *  However, there are other valid RPOs that do not allow a single pass over all loops to calculate local
     *  frequencies: e.g. [B0,B1,B9,B2,B3,B5,B6,B7,B8,B4], thus this phase ensures that we have the "right" RPO.
     *
     *  Implementation notes:
     *  The algorithm to compute the loop frequencies works in two passes: the first pass computes
     *  the local loop frequencies, i.e., for every loop predecessor block a relative frequency of 1
     *  is assumed, then the frequency inside the loop is propagated and multiplied by control split
     *  probabilities. If a loop is finished the exit frequency is set accordingly.
     *
     *  The second pass then propagates relative frequencies into the entire CFG by using the,
     *  already correct, local loop frequencies for the body of the loop.
     */
    //@formatter:on
    private void computeFrequencies() {
        /*
         * General note: While it is not verified that the reverse post order contains inner loops
         * first yet, this will be verified once we calculate dominance information, thus we should
         * be good here.
         */
        localLoopFrequencyData = EconomicMap.create();

        // pass 1 compute "local" loop frequencies, i.e., the actual frequency of each self
        // contained loop, inner loops first, then outer loops
        computeLocalLoopFrequencies();

        // reset everything again
        resetBlockFrequencies();

        // pass 2 propagate the outer frequencies into the inner ones multiplying the local loop
        // frequencies by the loop predecessor frequencies
        computeFrequenciesFromLocal();

        if (Assertions.assertionsEnabled()) {
            for (Block block : reversePostOrder) {
                assert block.getRelativeFrequency() >= 0 : "Must have a relative frequency set, block " + block;
            }
        }
    }

    private void computeLoopInformation() {
        loops = new ArrayList<>();
        if (graph.hasLoops()) {
            Block[] stack = new Block[this.reversePostOrder.length];
            for (Block block : reversePostOrder) {
                AbstractBeginNode beginNode = block.getBeginNode();
                if (beginNode instanceof LoopBeginNode) {
                    Loop<Block> parent = block.getLoop();
                    Loop<Block> loop = new HIRLoop(parent, loops.size(), block);
                    if (((LoopBeginNode) beginNode).isCompilerInverted()) {
                        loop.setInverted(true);
                    }
                    if (parent != null) {
                        parent.getChildren().add(loop);
                    }
                    loops.add(loop);
                    block.setLoop(loop);
                    loop.getBlocks().add(block);

                    LoopBeginNode loopBegin = (LoopBeginNode) beginNode;
                    for (LoopEndNode end : loopBegin.loopEnds()) {
                        Block endBlock = nodeToBlock.get(end);
                        computeLoopBlocks(endBlock, loop, stack, true);
                    }

                    // Note that at this point, due to traversal order, child loops of `loop` have
                    // not been discovered yet.
                    for (Block b : loop.getBlocks()) {
                        for (Block sux : b.getSuccessors()) {
                            if (sux.getLoop() != loop) {
                                assert sux.getLoopDepth() < loop.getDepth();
                                loop.getNaturalExits().add(sux);
                            }
                        }
                    }
                    loop.getNaturalExits().sort(BLOCK_ID_COMPARATOR);

                    if (!graph.getGuardsStage().areFrameStatesAtDeopts()) {
                        for (LoopExitNode exit : loopBegin.loopExits()) {
                            Block exitBlock = nodeToBlock.get(exit);
                            assert exitBlock.getPredecessorCount() == 1;
                            computeLoopBlocks(exitBlock.getFirstPredecessor(), loop, stack, true);
                            loop.getLoopExits().add(exitBlock);
                        }
                        loop.getLoopExits().sort(BLOCK_ID_COMPARATOR);

                        // The following loop can add new blocks to the end of the loop's block
                        // list.
                        int size = loop.getBlocks().size();
                        for (int i = 0; i < size; ++i) {
                            Block b = loop.getBlocks().get(i);
                            for (Block sux : b.getSuccessors()) {
                                if (sux.getLoop() != loop) {
                                    AbstractBeginNode begin = sux.getBeginNode();
                                    if (!loopBegin.isLoopExit(begin)) {
                                        assert !(begin instanceof LoopBeginNode);
                                        assert sux.getLoopDepth() < loop.getDepth();
                                        graph.getDebug().log(DebugContext.VERBOSE_LEVEL, "Unexpected loop exit with %s, including whole branch in the loop", sux);
                                        computeLoopBlocks(sux, loop, stack, false);
                                    }
                                }
                            }
                        }
                    } else {
                        loop.getLoopExits().addAll(loop.getNaturalExits());
                    }
                }
            }
        }
    }

    private static void computeLoopBlocks(Block start, Loop<Block> loop, Block[] stack, boolean usePred) {
        if (start.getLoop() != loop) {
            start.setLoop(loop);
            stack[0] = start;
            loop.getBlocks().add(start);
            int tos = 0;
            do {
                Block block = stack[tos--];

                // Add predecessors or successors to the loop.
                for (Block b : (usePred ? block.getPredecessors() : block.getSuccessors())) {
                    if (b.getLoop() != loop) {
                        stack[++tos] = b;
                        b.setLoop(loop);
                        loop.getBlocks().add(b);
                    }
                }
            } while (tos >= 0);
        }
    }

    public void computePostdominators() {

        Block[] reversePostOrderTmp = this.reversePostOrder;
        outer: for (int j = reversePostOrderTmp.length - 1; j >= 0; --j) {
            Block block = reversePostOrderTmp[j];
            if (block.isLoopEnd()) {
                // We do not want the loop header registered as the postdominator of the loop end.
                continue;
            }
            if (block.getSuccessorCount() == 0) {
                // No successors => no postdominator.
                continue;
            }
            Block firstSucc = block.getSuccessors()[0];
            if (block.getSuccessorCount() == 1) {
                block.postdominator = firstSucc;
                continue;
            }
            Block postdominator = firstSucc;
            for (Block sux : block.getSuccessors()) {
                postdominator = commonPostdominator(postdominator, sux);
                if (postdominator == null) {
                    // There is a dead end => no postdominator available.
                    continue outer;
                }
            }
            assert !Arrays.asList(block.getSuccessors()).contains(postdominator) : "Block " + block + " has a wrong post dominator: " + postdominator;
            block.setPostDominator(postdominator);
        }
    }

    private static Block commonPostdominator(Block a, Block b) {
        Block iterA = a;
        Block iterB = b;
        while (iterA != iterB) {
            if (iterA.getId() < iterB.getId()) {
                iterA = iterA.getPostdominator();
                if (iterA == null) {
                    return null;
                }
            } else {
                assert iterB.getId() < iterA.getId();
                iterB = iterB.getPostdominator();
                if (iterB == null) {
                    return null;
                }
            }
        }
        return iterA;
    }

    public void setNodeToBlock(NodeMap<Block> nodeMap) {
        this.nodeToBlock = nodeMap;
    }

    public static double multiplyRelativeFrequencies(double a, double b, double c) {
        return multiplyRelativeFrequencies(multiplyRelativeFrequencies(a, b), c);
    }

    /**
     * Multiplies a and b and clamps the between {@link ControlFlowGraph#MIN_RELATIVE_FREQUENCY} and
     * {@link ControlFlowGraph#MAX_RELATIVE_FREQUENCY}.
     */
    public static double multiplyRelativeFrequencies(double a, double b) {
        assert !Double.isNaN(a) && !Double.isNaN(b) && Double.isFinite(a) && Double.isFinite(b) : a + " " + b;
        double r = a * b;
        if (r > MAX_RELATIVE_FREQUENCY) {
            return MAX_RELATIVE_FREQUENCY;
        }
        if (r < MIN_RELATIVE_FREQUENCY) {
            return MIN_RELATIVE_FREQUENCY;
        }
        return r;
    }
}
