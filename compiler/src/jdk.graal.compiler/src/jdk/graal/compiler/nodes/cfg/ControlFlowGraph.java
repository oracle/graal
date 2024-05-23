/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.cfg;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.RetryableBailoutException;
import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BasicBlockSet;
import jdk.graal.compiler.core.common.cfg.CFGVerifier;
import jdk.graal.compiler.core.common.cfg.Loop;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MemUseTrackerKey;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ProfileData.LoopFrequencyData;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

public final class ControlFlowGraph implements AbstractControlFlowGraph<HIRBlock> {

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
        @Option(help = "Debug flag to dump loop frequency differences computed based on loop end or exit nodes." +
                       "If the frequencies diverge a lot, this may indicate missing profiles on control flow" +
                       "inside the loop body.", type = OptionType.Debug)
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
    private BuildConfiguration buildConfig;

    private NodeMap<HIRBlock> nodeToBlock;
    private HIRBlock[] reversePostOrder;
    private List<Loop<HIRBlock>> loops;
    private int maxDominatorDepth;
    private EconomicMap<LoopBeginNode, LoopFrequencyData> localLoopFrequencyData;

    public interface RecursiveVisitor<V> {
        V enter(HIRBlock b);

        void exit(HIRBlock b, V value);
    }

    private static final MemUseTrackerKey CFG_MEMORY = DebugContext.memUseTracker("CFGComputation");

    public static ControlFlowGraphBuilder newBuilder(StructuredGraph structuredGraph) {
        return new ControlFlowGraphBuilder(structuredGraph);
    }

    public static ControlFlowGraph computeForSchedule(StructuredGraph graph) {
        return compute(graph, true, true, true, true, true, false);
    }

    /**
     * Creates a control flow graph from the nodes in {@code graph}.
     *
     * @param modifiableBlocks specifies if the blocks can have their edges edited
     * @param connectBlocks
     * @param computeFrequency
     * @param computeLoops
     * @param computeDominators
     * @param computePostdominators
     */
    @SuppressWarnings("try")
    static ControlFlowGraph compute(StructuredGraph graph, boolean modifiableBlocks, boolean connectBlocks, boolean computeFrequency, boolean computeLoops, boolean computeDominators,
                    boolean computePostdominators) {
        try (DebugCloseable c = CFG_MEMORY.start(graph.getDebug())) {
            ControlFlowGraph cfg = lookupCached(graph, modifiableBlocks);
            if (cfg != null) {
                if (cfg.buildConfig.notWeakerThan(connectBlocks, computeFrequency, computeLoops, computeDominators, computePostdominators)) {
                    // the cached cfg contains all required data
                    return cfg;
                }
            } else {
                cfg = new ControlFlowGraph(graph);
                cfg.identifyBlocks(modifiableBlocks);
            }

            BuildConfiguration buildConfig = cfg.buildConfig;

            boolean loopInfoComputed = false;
            if (CFGOptions.DumpEndVersusExitLoopFrequencies.getValue(graph.getOptions())) {
                // additional loop info for sink frequencies inside the loop body
                if (!buildConfig.computeLoops) {
                    cfg.computeLoopInformation();
                }
                if (!buildConfig.computeDominators) {
                    cfg.computeDominators();
                }
                loopInfoComputed = true;
            }

            if (computeFrequency && !buildConfig.computeFrequency) {
                cfg.computeFrequencies();
            }

            if (computeLoops && !loopInfoComputed && !buildConfig.computeLoops) {
                cfg.computeLoopInformation();
            }
            if (computeDominators && !loopInfoComputed && !buildConfig.computeDominators) {
                cfg.computeDominators();
                assert cfg.verifyRPOInnerLoopsFirst();
            }
            if (computePostdominators && !buildConfig.computePostdominators) {
                cfg.computePostdominators();
            }

            // there's not much to verify when connectBlocks == false
            assert !(connectBlocks || computeLoops || computeDominators || computePostdominators) || CFGVerifier.verify(cfg);
            cfg.buildConfig.update(modifiableBlocks, connectBlocks, computeFrequency, computeLoops | loopInfoComputed, computeDominators | loopInfoComputed, computePostdominators);
            graph.setLastCFG(cfg);
            return cfg;
        }
    }

    /**
     * Returns the last {@link ControlFlowGraph} computed for the given {@link StructuredGraph} if
     * this CFG is still valid and uses the specified type of {@link HIRBlock}s (modifiable or
     * unmodifiable). Returns {@code null} otherwise.
     */
    private static ControlFlowGraph lookupCached(StructuredGraph graph, boolean modifiableBlocks) {
        if (graph.isLastCFGValid()) {
            ControlFlowGraph lastCFG = graph.getLastCFG();
            assert lastCFG != null : "A valid lastCFG must not be null";
            if (lastCFG.buildConfig.modifiableBlocks == modifiableBlocks) {
                return lastCFG;
            }
        }
        return null;
    }

    /**
     * Contains all flags which were used for building this CFG.
     */
    private static final class BuildConfiguration {
        private boolean modifiableBlocks = false;
        private boolean connectBlocks = false;
        private boolean computeFrequency = false;
        private boolean computeLoops = false;
        private boolean computeDominators = false;
        private boolean computePostdominators = false;

        @SuppressWarnings("hiding")
        public void update(boolean modifiableBlocks, boolean connectBlocks, boolean computeFrequency, boolean computeLoops, boolean computeDominators, boolean computePostdominators) {
            this.modifiableBlocks |= modifiableBlocks;
            this.connectBlocks |= connectBlocks;
            this.computeFrequency |= computeFrequency;
            this.computeLoops |= computeLoops;
            this.computeDominators |= computeDominators;
            this.computePostdominators |= computePostdominators;
        }

        /**
         * Checks that this build configuration is not weaker than a build configuration from the
         * provided parameters. That is, if for each parameter p the following holds:
         * {@code (p -> this.p)} which is equivalent to {@code (!p || this.p)}.
         */
        @SuppressWarnings("hiding")
        public boolean notWeakerThan(boolean connectBlocks, boolean computeFrequency, boolean computeLoops, boolean computeDominators, boolean computePostdominators) {
            return (this.connectBlocks || !connectBlocks) && (this.computeFrequency || !computeFrequency) && (this.computeLoops || !computeLoops) &&
                            (this.computeDominators || !computeDominators) && (this.computePostdominators || !computePostdominators);
        }
    }

    private void identifyBlocks(boolean makeModifiable) {
        int numBlocks = 0;
        for (AbstractBeginNode begin : graph.getNodes(AbstractBeginNode.TYPE)) {
            GraalError.guarantee(begin.predecessor() != null || (begin instanceof StartNode || begin instanceof AbstractMergeNode), "Disconnected control flow %s encountered", begin);
            HIRBlock block = makeModifiable ? new HIRBlock.ModifiableBlock(begin, this) : new HIRBlock.UnmodifiableBlock(begin, this);
            identifyBlock(block);
            numBlocks++;
            if (numBlocks > AbstractControlFlowGraph.LAST_VALID_BLOCK_INDEX) {
                throw new RetryableBailoutException("Graph too large to safely compile in reasonable time. Graph contains more than %d basic blocks",
                                AbstractControlFlowGraph.LAST_VALID_BLOCK_INDEX);
            }
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

    /**
     * Update the cached local loop frequency for the given loop. Future queries of
     * {@link #localLoopFrequency(LoopBeginNode)} on <em>this</em> {@link ControlFlowGraph} instance
     * will return the updated value. This is useful for phases to record temporary effects of
     * transformations on loop frequencies, without having to recompute a CFG.
     * </p>
     *
     * The updated frequency is a cached value local to this CFG. It is <em>not</em> persisted in
     * the IR graph. Newly computed {@link ControlFlowGraph} instances will recompute a frequency
     * from loop exit probabilities, they will not see this locally cached value. Persistent changes
     * to loop frequencies must be modeled by changing loop exit probabilities in the graph.
     */
    public void updateCachedLocalLoopFrequency(LoopBeginNode lb, Function<LoopFrequencyData, LoopFrequencyData> updater) {
        localLoopFrequencyData.put(lb, updater.apply(localLoopFrequencyData.get(lb)));
    }

    /**
     * Debug only decorator for {@link RecursiveVisitor} to log all basic blocks how they are
     * visited one by one.
     */
    public static class LoggingCFGDecorator implements ControlFlowGraph.RecursiveVisitor<HIRBlock> {
        private final ControlFlowGraph.RecursiveVisitor<HIRBlock> visitor;
        private String indent = "";

        public LoggingCFGDecorator(ControlFlowGraph.RecursiveVisitor<HIRBlock> visitor, ControlFlowGraph cfg) {
            this.visitor = visitor;
            TTY.printf("DomTree for %s%n", cfg.graph);
            printDomTree(cfg.getStartBlock(), "");
        }

        private static void printDomTree(HIRBlock cur, String indent) {
            TTY.printf("%s%s [dom %s, post dom %s]%n", indent, cur, cur.getDominator(), cur.getPostdominator());
            HIRBlock dominated = cur.getFirstDominated();
            while (dominated != null) {
                printDomTree(dominated, indent + "\t");
                dominated = dominated.getDominatedSibling();
            }
        }

        @Override
        public HIRBlock enter(HIRBlock b) {
            TTY.printf("%sEnter block %s for %s%n", indent, b, visitor);
            indent += "\t";
            return visitor.enter(b);
        }

        @Override
        public void exit(HIRBlock b, HIRBlock value) {
            indent = indent.substring(0, indent.length() - 1);
            TTY.printf("%sExit block %s with value %s for %s%n", indent, b, value, visitor);
            visitor.exit(b, value);
        }
    }

    @SuppressWarnings("unchecked")
    public <V> void visitDominatorTreeDefault(RecursiveVisitor<V> visitor) {

        HIRBlock[] stack = new HIRBlock[maxDominatorDepth + 1];
        HIRBlock current = getStartBlock();
        int tos = 0;
        Object[] values = null;
        int valuesTOS = 0;

        while (tos >= 0) {
            HIRBlock state = stack[tos];
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

                    HIRBlock dominated = skipPostDom(current.getFirstDominated());
                    if (dominated != null) {
                        // Descend into dominated.
                        stack[tos] = dominated;
                        current = dominated;
                        stack[++tos] = null;
                        continue;
                    }
                } else {
                    HIRBlock next = skipPostDom(state.getDominatedSibling());
                    if (next != null) {
                        // Descend into dominated.
                        stack[tos] = next;
                        current = next;
                        stack[++tos] = null;
                        continue;
                    }
                }

                // Finished processing all normal dominators.
                HIRBlock postDom = current.getPostdominator();
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

    private static HIRBlock skipPostDom(HIRBlock block) {
        if (block != null && block.getDominator().getPostdominator() == block) {
            // This is an always reached block.
            return block.getDominatedSibling();
        }
        return block;
    }

    public static final class DeferredExit {

        public DeferredExit(HIRBlock block, DeferredExit next) {
            this.block = block;
            this.next = next;
        }

        private final HIRBlock block;
        private final DeferredExit next;
    }

    public static void addDeferredExit(DeferredExit[] deferredExits, HIRBlock b) {
        Loop<HIRBlock> outermostExited = b.getDominator().getLoop();
        Loop<HIRBlock> exitBlockLoop = b.getLoop();
        assert outermostExited != null : "Dominator must be in a loop. Possible cause is a missing loop exit node.";
        while (outermostExited.getParent() != null && outermostExited.getParent() != exitBlockLoop) {
            outermostExited = outermostExited.getParent();
        }
        int loopIndex = outermostExited.getIndex();
        deferredExits[loopIndex] = new DeferredExit(b, deferredExits[loopIndex]);
    }

    @SuppressWarnings({"unchecked"})
    public <V> void visitDominatorTreeDeferLoopExits(RecursiveVisitor<V> visitor) {
        HIRBlock[] stack = new HIRBlock[getBlocks().length];
        int tos = 0;
        BasicBlockSet visited = this.createBasicBlockSet();
        int loopCount = getLoops().size();
        DeferredExit[] deferredExits = new DeferredExit[loopCount];
        Object[] values = null;
        int valuesTOS = 0;
        stack[0] = getStartBlock();
        List<HIRBlock> dominated = new ArrayList<>(3);

        while (tos >= 0) {
            HIRBlock cur = stack[tos];
            if (visited.get(cur)) {
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
                visited.set(cur);
                V value = visitor.enter(cur);
                if (value != null || values != null) {
                    if (values == null) {
                        values = new Object[maxDominatorDepth + 1];
                    }
                    values[valuesTOS++] = value;
                }

                HIRBlock alwaysReached = cur.getPostdominator();
                if (alwaysReached != null) {
                    if (alwaysReached.getDominator() != cur) {
                        alwaysReached = null;
                    } else if (isDominatorTreeLoopExit(alwaysReached)) {
                        addDeferredExit(deferredExits, alwaysReached);
                    } else {
                        stack[++tos] = alwaysReached;
                    }
                }

                dominated.clear();

                HIRBlock b = cur.getFirstDominated();
                while (b != null) {
                    dominated.add(b);
                    b = b.getDominatedSibling();
                }

                /*
                 * Push dominated blocks to stack in reverse order, to make sure that branches are
                 * handled before merges. This facilitates phi optimizations.
                 */
                while (!dominated.isEmpty()) {
                    b = dominated.removeLast();
                    if (b != alwaysReached) {
                        if (isDominatorTreeLoopExit(b)) {
                            addDeferredExit(deferredExits, b);
                        } else {
                            stack[++tos] = b;
                        }
                    }
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

    public static boolean isDominatorTreeLoopExit(HIRBlock b) {
        return isDominatorTreeLoopExit(b, false);
    }

    public static boolean isDominatorTreeLoopExit(HIRBlock b, boolean considerRealExits) {
        HIRBlock dominator = b.getDominator();
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
        this.buildConfig = new BuildConfiguration();
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
    private boolean rpoInnerLoopsFirst(Consumer<HIRBlock> perBasicBlockOption, Consumer<LoopBeginNode> loopClosedAction) {
        // worst case all loops in the graph are nested
        RPOLoopVerification[] openLoops = new RPOLoopVerification[graph.getNodes(LoopBeginNode.TYPE).count()];
        int tos = 0;
        for (HIRBlock b : reversePostOrder) {
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
            while (true) { // TERMINATION ARGUMENT: processing loop exit node predecessors
                CompilationAlarm.checkProgress(graph);
                if (f instanceof LoopExitNode) {
                    LoopBeginNode closedLoop = ((LoopExitNode) f).loopBegin();
                    RPOLoopVerification lv = openLoops[tos - 1];
                    assert lv.lb == closedLoop : "Must close inner loops first before closing other ones stackLoop=" + lv.lb + " exited loop=" + closedLoop + " block=" + b;
                    if (!lv.allEndsVisited()) {
                        throw GraalError.shouldNotReachHere(
                                        "Loop ends should be visited before exits. This is a major error in the reverse post order of the control " +
                                                        "flow graph of this method. This typically means wrongly specified control-split nodes have been processed in ReversePostOrder.java."); // ExcludeFromJacocoGeneratedReport
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
    private static boolean predecessorBlockSequentialLoopExit(HIRBlock b) {
        HIRBlock cur = b;
        // while cur has a single predecessor which has a single successor which is cur, i.e., a
        // sequential successor, this typically only happens in not-fully-canonicalized graphs
        while (cur.getPredecessorCount() == 1 && cur.getPredecessorAt(0).getSuccessorCount() == 1) {
            HIRBlock pred = cur.getPredecessorAt(0);
            FixedNode f = pred.getBeginNode();
            while (true) { // TERMINATION ARGUMENT: process loop exit predecessor nodes
                CompilationAlarm.checkProgress(b.getCfg().graph);
                if (f instanceof LoopExitNode) {
                    return true;
                }
                if (f == pred.getEndNode()) {
                    break;
                }
                f = ((FixedWithNextNode) f).next();
            }
            cur = cur.getPredecessorAt(0);
        }
        return false;
    }

    private void computeDominators() {
        assert reversePostOrder[0].getPredecessorCount() == 0 : "start block has no predecessor and therefore no dominator";
        HIRBlock[] blocks = reversePostOrder;
        int curMaxDominatorDepth = 0;
        for (int i = 1; i < blocks.length; i++) {
            HIRBlock block = blocks[i];
            assert NumUtil.assertPositiveInt(block.getPredecessorCount());
            HIRBlock dominator = null;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                HIRBlock pred = block.getPredecessorAt(j);
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
            HIRBlock currentDominated = dominator.getFirstDominated();
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

    private static void calcDominatorRanges(HIRBlock block, int size) {
        HIRBlock[] stack = new HIRBlock[size];
        stack[0] = block;
        int tos = 0;
        int myNumber = 0;

        do {
            HIRBlock cur = stack[tos];
            HIRBlock dominated = cur.getFirstDominated();

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
                myNumber = myNumber + 1;
            } else {
                cur.setMaxChildDomNumber(dominated.getMaxChildDominatorNumber());
                --tos;
            }
        } while (tos >= 0);
    }

    private static HIRBlock commonDominatorRaw(HIRBlock a, HIRBlock b) {
        int aDomDepth = a.getDominatorDepth();
        int bDomDepth = b.getDominatorDepth();
        if (aDomDepth > bDomDepth) {
            return commonDominatorRawSameDepth(a.getDominator(aDomDepth - bDomDepth), b);
        } else {
            return commonDominatorRawSameDepth(a, b.getDominator(bDomDepth - aDomDepth));
        }
    }

    private static HIRBlock commonDominatorRawSameDepth(HIRBlock a, HIRBlock b) {
        HIRBlock iterA = a;
        HIRBlock iterB = b;
        while (iterA != iterB) {
            iterA = iterA.getDominator();
            iterB = iterB.getDominator();
        }
        return iterA;
    }

    @Override
    public HIRBlock[] getBlocks() {
        return reversePostOrder;
    }

    @Override
    public HIRBlock getStartBlock() {
        return reversePostOrder[0];
    }

    public HIRBlock[] reversePostOrder() {
        return reversePostOrder;
    }

    public NodeMap<HIRBlock> getNodeToBlock() {
        return nodeToBlock;
    }

    public HIRBlock blockFor(Node node) {
        return nodeToBlock.get(node);
    }

    public HIRBlock commonDominatorFor(NodeIterable<? extends Node> nodes) {
        HIRBlock commonDom = null;
        for (Node n : nodes) {
            HIRBlock b = blockFor(n);
            commonDom = (HIRBlock) AbstractControlFlowGraph.commonDominator(commonDom, b);
        }
        return commonDom;
    }

    @Override
    public List<Loop<HIRBlock>> getLoops() {
        return loops;
    }

    public int getMaxDominatorDepth() {
        return maxDominatorDepth;
    }

    private void identifyBlock(HIRBlock block) {
        FixedWithNextNode cur = block.getBeginNode();
        while (true) { // TERMINATION ARGUMENT: processing fixed nodes of a basic block, bound if
                       // the graph is valid
            CompilationAlarm.checkProgress(graph);
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
            HIRBlock lexBlock = blockFor(lex);
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
        HIRBlock header = blockFor(lb);
        assert header != null;
        double loopFrequency = -1;
        ProfileSource source = ProfileSource.UNKNOWN;

        if (CFGOptions.UseLoopEndFrequencies.getValue(lb.graph().getOptions())) {
            double loopEndFrequency = 0D;
            for (LoopEndNode len : lb.loopEnds()) {
                HIRBlock endBlock = blockFor(len);
                assert endBlock != null;
                assert endBlock.relativeFrequency >= 0D : Assertions.errorMessageContext("endblock", endBlock, "endblock.rf", endBlock.relativeFrequency);
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
                HIRBlock lexBlock = blockFor(lex);
                assert lexBlock != null;
                assert lexBlock.relativeFrequency >= 0D : Assertions.errorMessageContext("lexBlock", lexBlock);
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
            for (HIRBlock loopBlock : blockFor(lb).getLoop().getBlocks()) {
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
                outer: for (HIRBlock loopBlock : blockFor(lb).getLoop().getBlocks()) {
                    if (loopBlock.isLoopHeader()) {
                        // loop exit successor frequency is weighted differently
                        continue;
                    }
                    if (isDominatorTreeLoopExit(loopBlock, true)) {
                        // loop exit successor frequency is weighted differently
                        continue outer;
                    }
                    for (int i = 0; i < loopBlock.getSuccessorCount(); i++) {
                        HIRBlock succ = loopBlock.getSuccessorAt(i);
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
                    for (int i = 0; i < loopBlock.getSuccessorCount(); i++) {
                        HIRBlock succ = loopBlock.getSuccessorAt(i);
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
                        throw GraalError.shouldNotReachHere(String.format(format, loopBlock, loopBlock.getBeginNode(), selfFrequency, succFrequency)); // ExcludeFromJacocoGeneratedReport
                    }
                }
            }
            double loopEndFrequencySum = 0D;
            for (LoopEndNode len : lb.loopEnds()) {
                HIRBlock lenBlock = blockFor(len);
                loopEndFrequencySum += lenBlock.relativeFrequency;
            }
            double endBasedFrequency = 1D / (1D - loopEndFrequencySum);
            if (loopEndFrequencySum == 1D) {
                // loop without any loop exits (and no sinks)
                endBasedFrequency = MAX_RELATIVE_FREQUENCY;
            }
            // verify inner loop frequency calculations used sane loop exit frequencies
            for (HIRBlock loopBlock : blockFor(lb).getLoop().getBlocks()) {
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
                        throw GraalError.shouldNotReachHere("Frequencies diverge too much"); // ExcludeFromJacocoGeneratedReport
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
        for (HIRBlock block : reversePostOrder) {
            block.setRelativeFrequency(0);
        }
    }

    private void computeFrequenciesFromLocal() {
        for (HIRBlock block : reversePostOrder) {
            perBasicBlockFrequencyAction(block, false);
        }
    }

    private void perBasicBlockFrequencyAction(HIRBlock b, boolean computingLocalLoopFrequencies) {
        double relativeFrequency = -1D;
        ProfileSource source = ProfileSource.UNKNOWN;
        if (b.getPredecessorCount() == 0) {
            relativeFrequency = 1D;
        } else if (b.getPredecessorCount() == 1) {
            HIRBlock pred = b.getPredecessorAt(0);
            relativeFrequency = pred.relativeFrequency;
            if (pred.getSuccessorCount() > 1) {
                assert pred.getEndNode() instanceof ControlSplitNode : Assertions.errorMessage(pred, pred.getEndNode());
                ControlSplitNode controlSplit = (ControlSplitNode) pred.getEndNode();
                relativeFrequency = multiplyRelativeFrequencies(relativeFrequency, controlSplit.probability(b.getBeginNode()));
                if (computingLocalLoopFrequencies) {
                    source = controlSplit.getProfileData().getProfileSource();
                }
            }
        } else {
            relativeFrequency = b.getPredecessorAt(0).relativeFrequency;
            for (int i = 1; i < b.getPredecessorCount(); ++i) {
                HIRBlock pred = b.getPredecessorAt(i);
                relativeFrequency += pred.relativeFrequency;
                if (computingLocalLoopFrequencies) {
                    if (pred.frequencySource != null) {
                        source = source.combine(pred.frequencySource);
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
            for (HIRBlock block : reversePostOrder) {
                assert block.getRelativeFrequency() >= 0 : "Must have a relative frequency set, block " + block;
            }
        }
    }

    private void computeLoopInformation() {
        loops = new ArrayList<>(graph.getNodes(LoopBeginNode.TYPE).count());
        if (graph.hasLoops()) {
            HIRBlock[] stack = new HIRBlock[this.reversePostOrder.length];
            for (HIRBlock block : reversePostOrder) {
                AbstractBeginNode beginNode = block.getBeginNode();
                if (beginNode instanceof LoopBeginNode) {
                    Loop<HIRBlock> parent = block.getLoop();
                    Loop<HIRBlock> loop = new HIRLoop(parent, loops.size(), block);
                    if (parent != null) {
                        parent.getChildren().add(loop);
                    }
                    loops.add(loop);
                    block.setLoop(loop);
                    loop.getBlocks().add(block);

                    LoopBeginNode loopBegin = (LoopBeginNode) beginNode;
                    for (LoopEndNode end : loopBegin.loopEnds()) {
                        HIRBlock endBlock = nodeToBlock.get(end);
                        computeLoopBlocks(endBlock, loop, stack, true);
                    }

                    // Note that at this point, due to traversal order, child loops of `loop` have
                    // not been discovered yet.
                    for (HIRBlock b : loop.getBlocks()) {
                        for (int i = 0; i < b.getSuccessorCount(); i++) {
                            HIRBlock sux = b.getSuccessorAt(i);
                            if (sux.getLoop() != loop) {
                                assert sux.getLoopDepth() < loop.getDepth() : Assertions.errorMessageContext("sux", sux, "sux.loopDepth", sux.getLoopDepth(), "loop", loop, "loop.loopDepth",
                                                loop.getDepth());
                                loop.getNaturalExits().add(sux);
                            }
                        }
                    }
                    loop.getNaturalExits().sort(BasicBlock.BLOCK_ID_COMPARATOR);

                    if (!graph.getGuardsStage().areFrameStatesAtDeopts()) {
                        for (LoopExitNode exit : loopBegin.loopExits()) {
                            HIRBlock exitBlock = nodeToBlock.get(exit);
                            assert exitBlock.getPredecessorCount() == 1 : Assertions.errorMessage(exit, exitBlock);
                            computeLoopBlocks(exitBlock.getFirstPredecessor(), loop, stack, true);
                            loop.getLoopExits().add(exitBlock);
                        }
                        loop.getLoopExits().sort(BasicBlock.BLOCK_ID_COMPARATOR);

                        // The following loop can add new blocks to the end of the loop's block
                        // list.
                        int size = loop.getBlocks().size();
                        for (int i = 0; i < size; ++i) {
                            HIRBlock b = loop.getBlocks().get(i);
                            for (int j = 0; j < b.getSuccessorCount(); j++) {
                                HIRBlock sux = b.getSuccessorAt(j);
                                if (sux.getLoop() != loop) {
                                    AbstractBeginNode begin = sux.getBeginNode();
                                    if (!loopBegin.isLoopExit(begin)) {
                                        assert !(begin instanceof LoopBeginNode) : Assertions.errorMessageContext("begin", begin);
                                        assert sux.getLoopDepth() < loop.getDepth() : Assertions.errorMessageContext("sux", sux, "sux.loopDepth", sux.getLoopDepth(), "loop", loop,
                                                        "loop.loopDepth",
                                                        loop.getDepth());
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

    private static void computeLoopBlocks(HIRBlock start, Loop<HIRBlock> loop, HIRBlock[] stack, boolean usePred) {
        if (start.getLoop() != loop) {
            start.setLoop(loop);
            stack[0] = start;
            loop.getBlocks().add(start);
            int tos = 0;
            do {
                HIRBlock block = stack[tos--];

                // Add predecessors or successors to the loop.
                for (int i = 0; i < (usePred ? block.getPredecessorCount() : block.getSuccessorCount()); i++) {
                    HIRBlock b = (usePred ? block.getPredecessorAt(i) : block.getSuccessorAt(i));
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
        HIRBlock[] reversePostOrderTmp = this.reversePostOrder;
        outer: for (int j = reversePostOrderTmp.length - 1; j >= 0; --j) {
            HIRBlock block = reversePostOrderTmp[j];
            if (block.isLoopEnd()) {
                // We do not want the loop header registered as the postdominator of the loop end.
                continue;
            }
            if (block.getSuccessorCount() == 0) {
                // No successors => no postdominator.
                continue;
            }
            HIRBlock firstSucc = block.getSuccessorAt(0);
            if (block.getSuccessorCount() == 1) {
                block.postdominator = firstSucc.getId();
                continue;
            }
            HIRBlock postdominator = firstSucc;
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                HIRBlock sux = block.getSuccessorAt(i);
                postdominator = commonPostdominator(postdominator, sux);
                if (postdominator == null) {
                    // There is a dead end => no postdominator available.
                    continue outer;
                }
            }
            assert !block.containsSucc(postdominator) : "Block " + block + " has a wrong post dominator: " + postdominator;
            block.setPostDominator(postdominator);
        }
    }

    private static HIRBlock commonPostdominator(HIRBlock a, HIRBlock b) {
        HIRBlock iterA = a;
        HIRBlock iterB = b;
        while (iterA != iterB) {
            if (iterA.getId() < iterB.getId()) {
                iterA = iterA.getPostdominator();
                if (iterA == null) {
                    return null;
                }
            } else {
                assert iterB.getId() < iterA.getId() : Assertions.errorMessageContext("a", a, "b", b, "iterB.id", iterB.getId(), "iterA.id", iterA.getId());
                iterB = iterB.getPostdominator();
                if (iterB == null) {
                    return null;
                }
            }
        }
        return iterA;
    }

    public void setNodeToBlock(NodeMap<HIRBlock> nodeMap) {
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
