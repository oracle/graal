/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.spi.NodeWithIdentity;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.FixedBinaryNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.cfg.HIRLoop;
import org.graalvm.compiler.nodes.cfg.LocationSet;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchored;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.util.GraphOrder;
import org.graalvm.word.LocationIdentity;

/**
 * Optimization phase that performs global value numbering and loop invariant code motion on a
 * {@link StructuredGraph}. It only considers {@link FixedNode} nodes, floating nodes are handled
 * automatically via their representation in the IR as {@link FloatingNode}.
 *
 * Note on loop invariant code motion: This phase only performs loop invariant code motion for
 * instructions unconditionally executed inside a loop. A standard example is a loop iterating until
 * an array length
 *
 * <pre>
 * for (int i = 0; i < arr.length; i++) {
 * // body
 * }
 * </pre>
 *
 * In a more IR focused notation this code looks like
 *
 * *
 *
 * <pre>
 * int i = 0;
 * while (true) {
 *     int len = arr.length;
 *     if (i < len) {
 *         // body
 *         i++;
 *     }
 *     break;
 * }
 * </pre>
 *
 * The length of the array is read in every iteration of the loop. However, it is loop invariant and
 * the array length {@link LocationIdentity} is immutable. Thus, the read can be moved out of the
 * loop as it is loop-invariant.
 *
 * The algorithm is based on a dominator tree traversal of the {@link ControlFlowGraph} where
 * dominated blocks are visited before post dominated blocks. This means if a {@link Block} starts
 * with a {@link MergeNode} all its predecessor blocks have already been visited. This is important
 * to properly track {@link MemoryKill} nodes.
 *
 * The algorithm uses {@link MemoryKill} and {@link MemoryAccess} to reason about memory effects.
 *
 * The algorithm does not create {@link ProxyNode}. Thus, if a node is defined inside a loop and
 * another value equal node is outside of this loop, value numbering will not happen (for the sake
 * of simplicity of the algorithm).
 *
 * @see <a href="https://en.wikipedia.org/wiki/Value_numbering">Global Value Numbering</a>
 * @see <a href="https://en.wikipedia.org/wiki/Loop-invariant_code_motion">Loop-invariant code
 *      motion</a>
 */
public class DominatorBasedGlobalValueNumberingPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public DominatorBasedGlobalValueNumberingPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    public static final CounterKey earlyGVN = DebugContext.counter("EarlyGVN");
    public static final CounterKey earlyGVNLICM = DebugContext.counter("EarlyGVN_LICM");
    public static final CounterKey earlyGVNAbort = DebugContext.counter("EarlyGVN_AbortProxy");

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        runFixedNodeGVN(graph, context);
        assert verifyGVN(graph);
    }

    private static void runFixedNodeGVN(StructuredGraph graph, CoreProviders context) {
        LoopsData ld = context.getLoopsDataProvider().getLoopsData(graph);
        ld.getCFG().visitDominatorTreeDefault(new GVNVisitor(ld.getCFG(), ld));
    }

    private static boolean verifyGVN(StructuredGraph graph) {
        assert GraphOrder.assertNonCyclicGraph(graph);
        return true;
    }

    /*
     * Either we process a loop twice until a fix point or we compute the killed locations inside a
     * loop and process them further, we are mostly interested in memory reads here
     */
    protected static class GVNVisitor implements ControlFlowGraph.RecursiveVisitor<ValueMap> {
        final StructuredGraph graph;
        final ControlFlowGraph cfg;
        final LoopsData ld;
        final NodeBitMap licmNodes;
        final BlockMap<ValueMap> blockMaps;
        final boolean considerLICM;

        public GVNVisitor(ControlFlowGraph cfg, LoopsData ld) {
            this.cfg = cfg;
            this.ld = ld;
            this.graph = cfg.graph;
            this.licmNodes = graph.createNodeBitMap();
            this.blockMaps = new BlockMap<>(cfg);
            this.considerLICM = GraalOptions.EarlyLICM.getValue(graph.getOptions()) && graph.hasLoops();
        }

        /**
         * Traversal order and kill effects: The visit order of the dominator tree guarantees
         * that predecessors (except loop edges which are handled explicitly) are visited before
         * a block itself (i.e. the post dominator of a split). This ensures we will have seen
         * all necessary predecessor kills before we process a merge block.
         *
         * Example: Block b0,b1,b2,b3
         *
         * @formatter:off
         * if()  b0
         *  b1
         * else
         *  b2
         * merge b3
         *
         * The order of traversal would be b0,b1,b2,b3(post dom) where the effects of b1 and b2 are
         * collected and applied to b3 as well
         * @formatter:on
         */
        @Override
        public ValueMap enter(Block b) {
            ValueMap blockMap = blockMaps.get(b);

            assert blockMap == null;
            Block dominator = b.getDominator();
            if (dominator == null) {
                blockMap = new ValueMap();
            } else {
                ValueMap dominatorMap = blockMaps.get(dominator);
                blockMap = dominatorMap.copy();
            }

            // preserve for dominated and successors
            blockMaps.put(b, blockMap);

            Loop<Block> hirLoop = b.getLoop();
            LocationSet thisLoopKilledLocations = hirLoop == null ? null : ((HIRLoop) hirLoop).getKillLocations();

            if (!b.isLoopHeader()) {
                // apply kill effects of dominator tree siblings (not the dominator itself)
                for (Block predecessor : b.getPredecessors()) {
                    if (b.getDominator() == predecessor) {
                        // dominator already handled when creating the map, don't re-kill
                        // everything already killed there.
                        continue;
                    }
                    ValueMap predMap = blockMaps.get(predecessor);
                    GraalError.guarantee(predMap != null, "This block %s pred block %s", b.getBeginNode(), predecessor.getEndNode());
                    blockMap.killAllValuesByOtherMap(predMap);
                }
            } else {
                // kill everything by the backedge kills
                killLoopLocations(thisLoopKilledLocations, blockMap);
            }

            LoopEx loopCandidate = null;
            boolean tryLICM = false;
            if (hirLoop != null && considerLICM) {
                checkLICM: {
                    /*
                     * Check if LICM can be applied because we are in a tail counted loop or have
                     * code dominating the exit conditions.
                     */
                    tryLICM = true;
                    for (LoopExitNode lex : ((LoopBeginNode) hirLoop.getHeader().getBeginNode()).loopExits()) {
                        Block lexBLock = cfg.blockFor(lex);
                        tryLICM &= AbstractControlFlowGraph.strictlyDominates(b, lexBLock);
                        if (!tryLICM) {
                            break checkLICM;
                        }
                    }
                    for (Block loopBlock : hirLoop.getBlocks()) {
                        // if(sth) deopt patterns are also exits
                        if (loopBlock.getEndNode() instanceof ControlSinkNode) {
                            tryLICM &= AbstractControlFlowGraph.strictlyDominates(b, loopBlock);
                            if (!tryLICM) {
                                break checkLICM;
                            }
                        }
                    }
                }
                if (tryLICM) {
                    loopCandidate = ld.loop(hirLoop);
                }
            }

            // begin nodes can be memory kills
            blockMap.killValuesByPotentialMemoryKill(b.getBeginNode());
            ArrayList<FixedWithNextNode> nodes = new ArrayList<>();
            // ignore single node basic blocks (loop exits)
            if (b.getBeginNode() != b.getEndNode()) {
                FixedNode cur = (FixedNode) b.getEndNode().predecessor();
                while (cur != b.getBeginNode()) {
                    nodes.add((FixedWithNextNode) cur);
                    cur = (FixedNode) cur.predecessor();
                }
                for (int i = nodes.size() - 1; i >= 0; i--) {
                    FixedWithNextNode fwn = nodes.get(i);
                    // a previous GVN can remove this node
                    if (fwn != null) {
                        procesNode(fwn, thisLoopKilledLocations, loopCandidate, blockMap, licmNodes, ld.getCFG());
                    }
                }
            }
            // end nodes can be memory kills
            blockMap.killValuesByPotentialMemoryKill(b.getEndNode());
            return blockMap;
        }

        public static void killLoopLocations(LocationSet thisLoopKilledLocations, ValueMap blockMap) {
            if (thisLoopKilledLocations != null) {
                /*
                 * Each location identity killed inside the loop prohibits a folding to the outer
                 * accesses, this is true for all paths inside a loop. The traversal of the
                 * dominator tree can have paths where a location is not killed while it is killed
                 * as a result of a conditional loop branch.
                 */
                for (LocationIdentity loopKilled : thisLoopKilledLocations.getCopyAsList()) {
                    blockMap.killValuesByIdentity(loopKilled);
                }
            }
        }

        private static void procesNode(FixedWithNextNode cur, LocationSet thisLoopKilledLocations,
                        LoopEx loopCandidate, ValueMap blockMap, NodeBitMap licmNodes, ControlFlowGraph cfg) {
            if (cur instanceof LoopExitNode) {
                /*
                 * We exit a loop down this path, we have to account for the effects of the loop
                 * since it is not necessarily true that all effects of the loop body dominate the
                 * loop exit. However, if an iteration of a loop is taken it will kill all locations
                 * it touches.
                 */
                final LoopBeginNode exitedLoop = ((LoopExitNode) cur).loopBegin();
                killLoopLocations(((HIRLoop) cfg.blockFor(exitedLoop).getLoop()).getKillLocations(), blockMap);
            }

            if (MemoryKill.isMemoryKill(cur)) {
                blockMap.killValuesByPotentialMemoryKill(cur);
                return;
            }

            if (!canGVN(cur)) {
                return;
            }

            boolean canSubsitute = blockMap.hasSubstitute(cur);
            boolean canLICM = loopCandidate != null;
            if (cur instanceof MemoryAccess) {
                MemoryAccess access = (MemoryAccess) cur;
                if (loopKillsLocation(thisLoopKilledLocations, access.getLocationIdentity())) {
                    if (canSubsitute) {
                        // loop kills this location, do not bother trying to figure out if parts
                        // can be GVNed, let floating reads later handle that
                        return;
                    }
                    canLICM = false;
                }
            }

            if (canLICM) {
                canLICM = nodeCanBeLifted(cur, loopCandidate, licmNodes);
            }

            /*
             * Perform the actual global value numbering of fixed nodes. May also perform LICM for
             * nodes that either already have a dominating value number equal operation or performs
             * a limited form of LICM for nodes that are dominated by the loop header and dominate
             * all exits. Such operations that are unconditionally executed in the particular loop.
             */
            if (canSubsitute) {
                // GVN node
                blockMap.substitute(cur, cfg, licmNodes, canLICM ? loopCandidate : null);
            } else {
                if (canLICM) {
                    tryPerformLICM(loopCandidate, cur, licmNodes);
                }
                blockMap.rememberNodeForGVN(cur);
            }
        }

        @Override
        public void exit(Block b, ValueMap oldMap) {

        }

    }

    /**
     * Method to decide if a node can be GVNed or LICM-moved.
     *
     * We exclude
     *
     * {@link MemoryKill} memory kills must not be touched, their effects are observable.
     *
     * {@link VirtualizableAllocation} allocations must not be GVNed, though they are no side effect
     * they have identity
     *
     * {@link ControlFlowAnchored} special nodes used to model control flow paths that must not be
     * changed
     *
     * {@link AnchoringNode} nodes used to mark an anchor, i.e., a place for floating usages above
     * which they must not float
     */
    public static boolean canGVN(Node n) {
        return !MemoryKill.isMemoryKill(n) && (n instanceof MemoryAccess || n instanceof FixedGuardNode || n instanceof FixedBinaryNode) && !(n instanceof ControlFlowAnchored) &&
                        !(n instanceof AnchoringNode) &&
                        !(n instanceof NodeWithIdentity);
    }

    /**
     * Perform loop invariant code motion of the given node. Determine if the node is actually loop
     * invariant based on its inputs (data fields of the node including indirect loop variant
     * dependencies like the memory graph are checked by the caller).
     */
    public static boolean tryPerformLICM(LoopEx loop, FixedNode n, NodeBitMap liftedNodes) {
        if (nodeCanBeLifted(n, loop, liftedNodes)) {
            loop.loopBegin().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, loop.loopBegin().graph(), "Before LICM of node %s", n);
            loop.loopBegin().getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Early GVN: LICM on node %s", n);
            FixedWithNextNode toLift = (FixedWithNextNode) n;
            FixedNode next = ((FixedWithNextNode) n).next();
            FixedWithNextNode fwn = (FixedWithNextNode) toLift.predecessor();
            fwn.setNext(null);
            toLift.setNext(null);
            fwn.setNext(next);
            n.graph().addBeforeFixed(loop.loopBegin().forwardEnd(), toLift);
            liftedNodes.checkAndMarkInc(toLift);
            loop.loopBegin().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, loop.loopBegin().graph(), "After LICM of node %s for loop %s", n, loop);
            earlyGVNLICM.increment(loop.loopBegin().getDebug());
            if (loop.loopBegin().getDebug().isCountEnabled()) {
                DebugContext.counter(earlyGVNLICM.getName() + "_" + n.getClass().getSimpleName()).increment(n.graph().getDebug());
            }
            return true;
        }
        return false;

    }

    public static boolean nodeCanBeLifted(Node n, LoopEx loop, NodeBitMap liftedNodes) {
        /*
         * Note that the caller has to ensure n is not a memory kill at that point
         */
        boolean canLICM = true;
        for (Node input : n.inputs()) {
            if (input instanceof LogicNode || input instanceof PiNode) {
                /*
                 * Only process logic nodes here, do not bother trying different floating nodes as
                 * they are most likely a proxy for complex patterns
                 */
                if (!inputIsLoopInvariant(input, loop, liftedNodes)) {
                    /*
                     * Check if this floating node became loop invariant because all its inputs
                     * already are, itself is not though because we do not recompute loops data
                     * between LICM runs
                     */
                    boolean isActuallyLI = true;
                    for (Node floatingInput : input.inputs()) {
                        isActuallyLI &= inputIsLoopInvariant(floatingInput, loop, liftedNodes);
                    }
                    if (isActuallyLI) {
                        liftedNodes.mark(input);
                    }
                }
            }
            canLICM &= inputIsLoopInvariant(input, loop, liftedNodes);
        }
        return canLICM;
    }

    public static boolean inputIsLoopInvariant(Node input, LoopEx loop, NodeBitMap liftedNodes) {
        if (input == null) {
            return true;
        }
        return !loop.whole().contains(input) || liftedNodes.contains(input);
    }

    public static boolean loopKillsLocation(LocationSet thisLoopKilledLocations, LocationIdentity loc) {
        if (thisLoopKilledLocations == null) {
            return false;
        }
        return thisLoopKilledLocations.containsOrOverlaps(loc);
    }

    /**
     * A data structure used to implement global value numbering on fixed nodes in the Graal IR. It
     * stores all the values seen so far and exposes utility functionality to deal with memory.
     *
     * Loop handling: We can only perform global value numbering on the fixed node graph if we know
     * the memory effect of a loop. For a loop we can statically not tell if a kill inside may be
     * performed or not, thus, we can only perform GVN if we know that a loop does not kill a
     * certain memory location.
     *
     * Loop invariant code motion: This phase will not perform speculative loop invariant code
     * motion (this is done in mid tier on the real memory graph. Thus, we can only perform a
     * limited amount of LICM for code unconditionally executed inside a loop that is invariant).
     */
    public static final class ValueMap {
        private static final int INITIAL_SIZE = 4;

        /**
         * The array storing the {@link Node} remembered for global value numbering. Can contain
         * holes if nodes are deleted. Will be {@link #compress() compressed} or can become larger
         * if necessary with {@link #grow()}.
         */
        private Node[] entries;
        /**
         * The number of nodes stored already. Note that {@code entries[length]==null}and
         * {@code entries[length-1]=lastAddedNode}
         */
        private int length;
        private int deleted;
        private final LocationSet kills;

        public ValueMap() {
            this.entries = new Node[INITIAL_SIZE];
            this.kills = new LocationSet();
        }

        private void grow() {
            compress();
            if (length == entries.length) {
                entries = Arrays.copyOf(entries, entries.length * 2);
            }
        }

        private void compress() {
            if (deleted > length / 2) {
                Node[] entriesNew = new Node[entries.length];
                int newLength = 0;
                for (int i = 0; i < length; i++) {
                    Node entry = entries[i];
                    if (entry != null) {
                        entriesNew[newLength++] = entry;
                    }
                }
                entries = entriesNew;
                length = newLength;
                deleted = 0;
            }
        }

        private Node find(Node n) {
            for (int i = 0; i < length; i++) {
                Node entry = entries[i];
                if (entry != null) {
                    if (valueEquals(entry, n)) {
                        // return the dominating node
                        return entry;
                    }
                } else {
                    // holes allowed
                }
            }
            return null;
        }

        private void add(Node n) {
            grow();
            entries[length++] = n;
        }

        /**
         * Equivalence strategy for global value numbering.
         *
         * Determine if two nodes are equal with respect to global value numbering. Tihs means their
         * inputs are equal, their node classes are equal and their data fields.
         */

        private static boolean valueEquals(Node a, Node b) {
            if (a.getNodeClass().equals(b.getNodeClass())) {
                if (a.getNodeClass().dataEquals(a, b)) {
                    if (a.getNodeClass().equalInputs(a, b)) {
                        return true;
                    }
                }
            }
            return false;
        }

        ValueMap copy() {
            ValueMap other = new ValueMap();
            other.entries = Arrays.copyOf(entries, entries.length);
            other.kills.addAll(kills);
            other.length = length;
            other.deleted = deleted;
            return other;
        }

        public void killAllValuesByOtherMap(ValueMap predMap) {
            for (LocationIdentity otherKills : predMap.kills.getCopyAsList()) {
                killValuesByIdentity(otherKills);
            }
        }

        /**
         * Determine if there is a node that is equal to the given one.
         */
        public boolean hasSubstitute(Node n) {
            Node edgeDataEqual = find(n);
            assert edgeDataEqual == null || edgeDataEqual != n : "Must find different value equal node with different identity for " + n;
            return edgeDataEqual != null;
        }

        /**
         * Perform actual global value numbering. Replace node {@code n} with an equal node (inputs
         * and data fields) up in the dominance chain.
         */
        public void substitute(Node n, ControlFlowGraph cfg, NodeBitMap licmNodes, LoopEx invariantInLoop) {
            Node edgeDataEqual = find(n);
            if (edgeDataEqual != null) {
                assert edgeDataEqual.graph() != null;
                assert edgeDataEqual instanceof FixedNode : "Only process fixed nodes";
                StructuredGraph graph = (StructuredGraph) edgeDataEqual.graph();

                Block defBlock = cfg.blockFor(edgeDataEqual);

                if (licmNodes.contains(edgeDataEqual)) {
                    /*
                     * Note that due to performing LICM on the original CFG the block for this node
                     * is still inside the loop though we already moved the node to before the loop
                     */
                    assert defBlock.getLoop() != null;
                    defBlock = defBlock.getLoop().getHeader().getDominator();
                }

                if (invariantInLoop != null) {
                    Block loopDefBlock = cfg.blockFor(invariantInLoop.loopBegin()).getLoop().getHeader().getDominator();
                    if (AbstractControlFlowGraph.strictlyDominates(loopDefBlock, defBlock)) {
                        /*
                         * The LICM location strictly dominates the GVN location so it must be the
                         * final location. Move the GVN node to the LICM location and then perform
                         * the substitution normally.
                         */
                        if (!tryPerformLICM(invariantInLoop, (FixedNode) edgeDataEqual, licmNodes)) {
                            GraalError.shouldNotReachHere("tryPerformLICM must succeed for " + edgeDataEqual);
                        }
                    } else {
                        GraalError.guarantee(AbstractControlFlowGraph.dominates(defBlock, loopDefBlock), "No dominance relation between GVN and LICM locations: %s and %s", defBlock, loopDefBlock);
                    }
                }

                Block useBlock = cfg.blockFor(n);
                Loop<Block> defLoop = defBlock.getLoop();
                Loop<Block> useLoop = useBlock.getLoop();

                if (defLoop != null) {
                    // the def is inside a loop, either a parent or a disjunct loop
                    if (useLoop != null) {
                        // we are only safe without proxies if we are included in the def loop,
                        // i.e., the def loop is a parent loop
                        if (!useLoop.isAncestorOrSelf(defLoop)) {
                            earlyGVNAbort.increment(graph.getDebug());
                            return;
                        }
                    } else {
                        // the use is not in a loop but the def is, needs proxies, fail
                        earlyGVNAbort.increment(graph.getDebug());
                        return;
                    }
                }

                graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Early GVN: replacing %s with %s", n, edgeDataEqual);
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before replacing %s with %s", n, edgeDataEqual);
                n.replaceAtUsages(edgeDataEqual);
                GraphUtil.unlinkFixedNode((FixedWithNextNode) n);
                n.safeDelete();
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing %s with %s", n, edgeDataEqual);
                earlyGVN.increment(graph.getDebug());
                if (graph.getDebug().isCountEnabled()) {
                    DebugContext.counter(earlyGVN.getName() + "_" + edgeDataEqual.getClass().getSimpleName()).increment(graph.getDebug());
                }
            }
        }

        /**
         * Preserve a node for global value numbering in dominated code.
         */
        public void rememberNodeForGVN(Node n) {
            GraalError.guarantee(find(n) == null, "Must GVN before adding a new node");
            add(n);
        }

        public void killValuesByPotentialMemoryKill(Node n) {
            if (((StructuredGraph) n.graph()).start() == n) {
                return;
            }
            if (MemoryKill.isMemoryKill(n)) {
                if (MemoryKill.isSingleMemoryKill(n)) {
                    SingleMemoryKill singleMemoryKill = MemoryKill.asSingleMemoryKill(n);
                    killValuesByIdentity(singleMemoryKill.getKilledLocationIdentity());
                } else if (MemoryKill.isMultiMemoryKill(n)) {
                    MultiMemoryKill multiMemoryKill = MemoryKill.asMultiMemoryKill(n);
                    for (LocationIdentity loc : multiMemoryKill.getKilledLocationIdentities()) {
                        killValuesByIdentity(loc);
                    }
                }
            }
        }

        /**
         * Kill all remembered nodes based on the given memory location.
         */
        private void killValuesByIdentity(LocationIdentity loc) {
            kills.add(loc);
            for (int i = 0; i < length; i++) {
                Node entry = entries[i];
                if (entry != null) {
                    if (entry instanceof MemoryAccess) {
                        MemoryAccess mem = (MemoryAccess) entry;
                        if (mem.getLocationIdentity().overlaps(loc)) {
                            entries[i] = null;
                            deleted++;
                        }
                    }
                }
            }
        }

    }

}
