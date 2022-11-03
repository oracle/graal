/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.OptEliminateGuards;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static org.graalvm.compiler.nodes.memory.MemoryKill.NO_LOCATION;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_ENTER;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_ENTER_ALWAYS_REACHED;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_LEAVE;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_PROCESS;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_PROCESS_ALWAYS_REACHED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.UnreachableBeginNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.ForeignCall;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.MemoryMapNode;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.SideEffectFreeWriteNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.CoreProvidersDelegate;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public abstract class LoweringPhase extends BasePhase<CoreProviders> {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class DummyGuardHandle extends ValueNode implements GuardedNode {
        public static final NodeClass<DummyGuardHandle> TYPE = NodeClass.create(DummyGuardHandle.class);
        @Input(InputType.Guard) GuardingNode guard;

        protected DummyGuardHandle(GuardingNode guard) {
            super(TYPE, StampFactory.forVoid());
            this.guard = guard;
        }

        @Override
        public GuardingNode getGuard() {
            return guard;
        }

        @Override
        public void setGuard(GuardingNode guard) {
            updateUsagesInterface(this.guard, guard);
            this.guard = guard;
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    final class LoweringToolImpl extends CoreProvidersDelegate implements LoweringTool {

        private final NodeBitMap activeGuards;
        private AnchoringNode guardAnchor;
        private FixedWithNextNode lastFixedNode;
        private NodeMap<Block> nodeMap;

        LoweringToolImpl(CoreProviders context, AnchoringNode guardAnchor, NodeBitMap activeGuards, FixedWithNextNode lastFixedNode, NodeMap<Block> nodeMap) {
            super(context);
            this.guardAnchor = guardAnchor;
            this.activeGuards = activeGuards;
            this.lastFixedNode = lastFixedNode;
            this.nodeMap = nodeMap;
        }

        @Override
        public LoweringStage getLoweringStage() {
            return loweringStage;
        }

        @Override
        public AnchoringNode getCurrentGuardAnchor() {
            return guardAnchor;
        }

        @Override
        public boolean lowerOptimizableMacroNodes() {
            return lowerOptimizableMacroNodes;
        }

        @Override
        public GuardingNode createGuard(FixedNode before, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action) {
            return createGuard(before, condition, deoptReason, action, SpeculationLog.NO_SPECULATION, false, null);
        }

        @Override
        public GuardingNode createGuard(FixedNode before, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, Speculation speculation, boolean negated,
                        NodeSourcePosition noDeoptSucccessorPosition) {
            StructuredGraph graph = before.graph();
            if (OptEliminateGuards.getValue(graph.getOptions())) {
                for (Node usage : condition.usages()) {
                    if (!activeGuards.isNew(usage) && activeGuards.isMarked(usage) && ((GuardNode) usage).isNegated() == negated &&
                                    (before.graph().isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) ||
                                                    nodeMap.get(((GuardNode) usage).getAnchor().asNode()).isInSameOrOuterLoopOf(nodeMap.get(before)))) {
                        return (GuardNode) usage;
                    }
                }
            }
            if (!condition.graph().getGuardsStage().allowsFloatingGuards()) {
                FixedGuardNode fixedGuard = graph.add(new FixedGuardNode(condition, deoptReason, action, speculation, negated, noDeoptSucccessorPosition));
                graph.addBeforeFixed(before, fixedGuard);
                DummyGuardHandle handle = graph.add(new DummyGuardHandle(fixedGuard));
                fixedGuard.lower(this);
                GuardingNode result = handle.getGuard();
                handle.safeDelete();
                return result;
            } else {
                GuardNode newGuard = graph.unique(new GuardNode(condition, guardAnchor, deoptReason, action, negated, speculation, noDeoptSucccessorPosition));
                if (OptEliminateGuards.getValue(graph.getOptions())) {
                    activeGuards.markAndGrow(newGuard);
                }
                return newGuard;
            }
        }

        @Override
        public FixedWithNextNode lastFixedNode() {
            GraalError.guarantee(lastFixedNode.isAlive(), "The last fixed node %s was deleted by a previous lowering", lastFixedNode);
            return lastFixedNode;
        }

        private void setLastFixedNode(FixedWithNextNode n) {
            GraalError.guarantee(n.isAlive(), "Cannot add last fixed node %s because it is not alive", n);
            lastFixedNode = n;
        }
    }

    private final CanonicalizerPhase canonicalizer;
    private final LoweringTool.StandardLoweringStage loweringStage;
    private final boolean lowerOptimizableMacroNodes;
    private final GraphState.StageFlag postRunStage;

    LoweringPhase(CanonicalizerPhase canonicalizer, LoweringTool.StandardLoweringStage loweringStage, boolean lowerOptimizableMacroNodes, GraphState.StageFlag postRunStage) {
        this.canonicalizer = canonicalizer;
        this.loweringStage = loweringStage;
        this.lowerOptimizableMacroNodes = lowerOptimizableMacroNodes;
        this.postRunStage = postRunStage;
    }

    LoweringPhase(CanonicalizerPhase canonicalizer, LoweringTool.StandardLoweringStage loweringStage, GraphState.StageFlag postRunStage) {
        this(canonicalizer, loweringStage, false, postRunStage);
    }

    @Override
    protected boolean shouldDumpBeforeAtBasicLevel() {
        return loweringStage == LoweringTool.StandardLoweringStage.HIGH_TIER;
    }

    /**
     * Checks that second lowering of a given graph did not introduce any new nodes.
     *
     * @param graph a graph that was just {@linkplain #lower lowered}
     * @throws AssertionError if the check fails
     */
    private boolean checkPostLowering(StructuredGraph graph, CoreProviders context) {
        Mark expectedMark = graph.getMark();
        lower(graph, context, LoweringMode.VERIFY_LOWERING);
        Mark mark = graph.getMark();
        assert mark.equals(expectedMark) : graph + ": a second round in the current lowering phase introduced these new nodes: " + graph.getNewNodes(expectedMark).snapshot();
        return true;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return this.canonicalizer.notApplicableTo(graphState);
    }

    @Override
    protected void run(final StructuredGraph graph, CoreProviders context) {
        lower(graph, context, LoweringMode.LOWERING);
        assert checkPostLowering(graph, context);
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(postRunStage);
    }

    @SuppressWarnings("try")
    private void lower(StructuredGraph graph, CoreProviders context, LoweringMode mode) {
        boolean immutableSchedule = mode == LoweringMode.VERIFY_LOWERING;
        OptionValues options = graph.getOptions();
        new SchedulePhase(immutableSchedule, options).apply(graph, context);

        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
            ScheduleResult schedule = graph.getLastSchedule();
            schedule.getCFG().computePostdominators();
            Block startBlock = schedule.getCFG().getStartBlock();
            ProcessFrame rootFrame = new ProcessFrame(context, startBlock, graph.createNodeBitMap(), startBlock.getBeginNode(), null, schedule);
            LoweringPhase.processBlock(rootFrame);
        }

        if (!listener.getNodes().isEmpty()) {
            canonicalizer.applyIncremental(graph, context, listener.getNodes());
        }
        assert graph.verify();
    }

    /**
     * Checks that lowering of a given node did not introduce any new {@link Lowerable} nodes that
     * could be lowered in the current {@link LoweringPhase}. Such nodes must be recursively lowered
     * as part of lowering {@code node}.
     *
     * @param node a node that was just lowered
     * @param preLoweringMark the graph mark before {@code node} was lowered
     * @param unscheduledUsages set of {@code node}'s usages that were unscheduled before it was
     *            lowered
     * @throws AssertionError if the check fails
     */
    private static boolean checkPostNodeLowering(Node node, LoweringToolImpl loweringTool, Mark preLoweringMark, Collection<Node> unscheduledUsages) {
        StructuredGraph graph = (StructuredGraph) node.graph();
        Mark postLoweringMark = graph.getMark();
        NodeIterable<Node> newNodesAfterLowering = graph.getNewNodes(preLoweringMark);
        if (node instanceof FloatingNode) {
            if (!unscheduledUsages.isEmpty()) {
                for (Node n : newNodesAfterLowering) {
                    assert !(n instanceof FixedNode) : node.graph() + ": cannot lower floatable node " + node + " as it introduces fixed node(s) but has the following unscheduled usages: " +
                                    unscheduledUsages;
                }
            }
        }

        final boolean wasMemoryAccessBefore = node instanceof MemoryAccess;
        final boolean wasMemoryKillBefore = MemoryKill.isMemoryKill(node);

        for (Node n : newNodesAfterLowering) {
            if (n instanceof Lowerable) {
                ((Lowerable) n).lower(loweringTool);
                Mark mark = graph.getMark();
                assert postLoweringMark.equals(mark) : graph + ": lowering of " + node + " produced lowerable " + n + " that should have been recursively lowered as it introduces these new nodes: " +
                                graph.getNewNodes(postLoweringMark).snapshot();
            }
            if (!graph.isSubstitution()) {
                if (loweringTool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
                    /*
                     * We have to deal with a few special cases when lowering nodes high to mid
                     * tier:
                     *
                     * 1) Lowering to foreign call node: They have complex multi kills with private
                     * locations to the high tier graph, ignore them
                     *
                     * 2) Unreachable begin nodes per definition
                     *
                     * 3) Invokable implementations (e.g. macro nodes): Such nodes typically
                     * represent intrinics that undergo different lowering strategies which can mean
                     * a node lowers back to an original invoke. Lowering back to an invoke can
                     * result in problems given an invoke typically kills any. However, we are
                     * reasoning about well known intrinsics here, thus we allow it.
                     *
                     * 4) Created memory map nodes, they are pure meta-nodes we do not care about
                     *
                     * 5) Commit allocation nodes consume the their lock list to derive if it was a
                     * kill or not, after lowering the deleted node no longer has inputs TODO solve
                     * more generically?
                     */
                    if (!(n instanceof ForeignCall || n instanceof UnreachableBeginNode || node instanceof WithExceptionNode || n instanceof MemoryMapNode || node instanceof CommitAllocationNode ||
                                    n instanceof SideEffectFreeWriteNode) &&
                                    MemoryKill.isMemoryKill(n)) {

                        // lowered to a kill verify the original node was a kill
                        if (MemoryKill.isSingleMemoryKill(n)) {
                            SingleMemoryKill singleKill = (SingleMemoryKill) n;
                            if (!singleKill.getKilledLocationIdentity().equals(NO_LOCATION)) {
                                if (!wasMemoryKillBefore) {
                                    // Kills to ININT_LOCATION are excluded above. We would like to
                                    // perform this check before and only once for both
                                    // single and multi kills however we have special nodes like a
                                    // side effect free write which use init location writes which
                                    // we ignore for verification purposes
                                    throw GraalError.shouldNotReachHere(String.format("Original node %s was not a kill but %s is", node, n));
                                }
                                if (!(MemoryKill.isSingleMemoryKill(node))) {
                                    throw GraalError.shouldNotReachHere(String.format("Original node %s was not a single kill but %s is", node, n));
                                }
                                SingleMemoryKill oldKill = (SingleMemoryKill) node;
                                if (!oldKill.getKilledLocationIdentity().isSingle() && singleKill.getKilledLocationIdentity().isSingle()) {
                                    // fine, high level node killed any, new nodes have more precise
                                    // kills
                                } else if (!oldKill.getKilledLocationIdentity().equals(singleKill.getKilledLocationIdentity())) {
                                    throw GraalError.shouldNotReachHere(String.format("Original node %s kills %s while new node %s kills %s", node, oldKill.getKilledLocationIdentity(), singleKill,
                                                    singleKill.getKilledLocationIdentity()));
                                }
                            }
                        } else if (MemoryKill.isMultiMemoryKill(n)) {
                            if (!wasMemoryKillBefore) {
                                // INIT_LOCATION special case: context above
                                throw GraalError.shouldNotReachHere(String.format("Original node %s was not a kill but %s is", node, n));
                            }
                            if (!(MemoryKill.isMultiMemoryKill(node))) {
                                throw GraalError.shouldNotReachHere(String.format("Original node %s was not a multi kill but %s is", node, n));
                            }
                            MultiMemoryKill newKill = (MultiMemoryKill) n;
                            MultiMemoryKill oldKill = (MultiMemoryKill) node;
                            EconomicSet<LocationIdentity> killed = EconomicSet.create();
                            for (LocationIdentity loc : newKill.getKilledLocationIdentities()) {
                                killed.add(loc);
                            }
                            for (LocationIdentity oldLoc : oldKill.getKilledLocationIdentities()) {
                                if (killed.contains(oldLoc)) {
                                    killed.remove(oldLoc);
                                } else {
                                    throw GraalError.shouldNotReachHere(String.format("Original node %s kills %s while new node %s does not kill that location", oldKill, oldLoc, newKill));
                                }
                            }
                            for (LocationIdentity newLoc : killed) {
                                throw GraalError.shouldNotReachHere(String.format("New kill %s kills location %s while old kill %s does not", newKill, newLoc, oldKill));
                            }
                        } else {
                            throw GraalError.shouldNotReachHere("Unknown memory kill " + n);
                        }
                    } else if (n instanceof MemoryAccess) {
                        // lowered to a memory access, verify high level node accesses same
                        // locations
                        MemoryAccess access = (MemoryAccess) n;
                        if (access.getLocationIdentity().isMutable()) {
                            if (wasMemoryKillBefore && !wasMemoryAccessBefore) {
                                if (MemoryKill.isSingleMemoryKill(node)) {
                                    if (!((SingleMemoryKill) node).getKilledLocationIdentity().overlaps(access.getLocationIdentity())) {
                                        GraalError.shouldNotReachHere(String.format("Node %s was a memory kill killing %s but lowered to a memory access %s which accesses %s", node,
                                                        ((SingleMemoryKill) node).getKilledLocationIdentity(), n, access.getLocationIdentity()));
                                    }
                                } else if (MemoryKill.isMultiMemoryKill(node)) {
                                    boolean found = false;
                                    for (LocationIdentity ident : ((MultiMemoryKill) node).getKilledLocationIdentities()) {
                                        if (ident.overlaps(access.getLocationIdentity())) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        GraalError.shouldNotReachHere(String.format("Node %s was a memory kill not killing the location accessed by the lowered node: %s which accesses %s", node, n,
                                                        access.getLocationIdentity()));
                                    }
                                } else {
                                    throw GraalError.shouldNotReachHere("Unknown type of memory kill " + node);
                                }

                            } else if (wasMemoryAccessBefore) {
                                if (!access.getLocationIdentity().overlaps(((MemoryAccess) node).getLocationIdentity())) {
                                    GraalError.shouldNotReachHere(
                                                    String.format("Node %s was a memory access (%s) but lowered to a memory access %s %s", node, ((MemoryAccess) node).getLocationIdentity(),
                                                                    n, access.getLocationIdentity()));
                                }
                            } else {
                                GraalError.shouldNotReachHere(String.format("Node %s was not a memory access but lowered to a memory access %s", node, n));
                            }
                        }
                    }
                }
            }

            if (MemoryKill.isMemoryKill(n) && !(n instanceof MemoryMapNode) && !(wasMemoryKillBefore) && !(node instanceof ControlSinkNode)) {
                /*
                 * The lowering introduced a MemoryCheckpoint but the current node isn't a
                 * checkpoint. This is only OK if the locations involved don't affect the memory
                 * graph or if the new kill location doesn't connect into the existing graph.
                 */
                boolean isAny = false;
                if (MemoryKill.isSingleMemoryKill(n)) {
                    isAny = ((SingleMemoryKill) n).getKilledLocationIdentity().isAny();
                } else if (MemoryKill.isMultiMemoryKill(n)) {
                    for (LocationIdentity ident : ((MultiMemoryKill) n).getKilledLocationIdentities()) {
                        if (ident.isAny()) {
                            isAny = true;
                        }
                    }
                } else {
                    throw GraalError.shouldNotReachHere("Unknown type of memory kill " + n);
                }
                if (isAny && n instanceof FixedWithNextNode) {
                    /*
                     * Check if the next kill location leads directly to a ControlSinkNode in the
                     * new part of the graph. This is a fairly conservative test that could be made
                     * more general if required.
                     */
                    FixedWithNextNode cur = (FixedWithNextNode) n;
                    while (cur != null && graph.isNew(preLoweringMark, cur)) {
                        if (cur.next() instanceof ControlSinkNode) {
                            isAny = false;
                            break;
                        }
                        if (cur.next() instanceof FixedWithNextNode) {
                            cur = (FixedWithNextNode) cur.next();
                        } else {
                            break;
                        }
                    }
                }
                assert !isAny : node + " " + n;
            }
        }
        return true;
    }

    private enum LoweringMode {
        LOWERING,
        VERIFY_LOWERING
    }

    public static class LoweringStatistics {
        static final EnumSet<LoweringTool.StandardLoweringStage> stages = EnumSet.allOf(LoweringTool.StandardLoweringStage.class);

        /**
         * Records time spent in {@link BasePhase#apply(StructuredGraph, Object, boolean)}.
         */
        private final TimerKey[] timers;

        /**
         * Counts calls to {@link BasePhase#apply(StructuredGraph, Object, boolean)}.
         */
        private final CounterKey[] counters;

        public LoweringStatistics(Class<?> clazz) {
            timers = new TimerKey[stages.size()];
            counters = new CounterKey[stages.size()];
            for (LoweringTool.StandardLoweringStage loweringStage : stages) {
                timers[loweringStage.ordinal()] = DebugContext.timer("LoweringTime_%s_%s", loweringStage, clazz);
                counters[loweringStage.ordinal()] = DebugContext.counter("LoweringCount_%s_%s", loweringStage, clazz);
            }
        }
    }

    private static final ClassValue<LoweringStatistics> statisticsClassValue = new ClassValue<>() {
        @Override
        protected LoweringStatistics computeValue(Class<?> c) {
            return new LoweringStatistics(c);
        }
    };

    private class ProcessFrame extends Frame<ProcessFrame> {
        private final NodeBitMap activeGuards;
        private AnchoringNode anchor;
        private final ScheduleResult schedule;
        private final CoreProviders context;

        ProcessFrame(CoreProviders context, Block block, NodeBitMap activeGuards, AnchoringNode anchor, ProcessFrame parent, ScheduleResult schedule) {
            super(block, parent);
            this.context = context;
            this.activeGuards = activeGuards;
            this.anchor = anchor;
            this.schedule = schedule;
        }

        @Override
        public void preprocess() {
            this.anchor = process(context, block, activeGuards, anchor, schedule);
        }

        @Override
        public ProcessFrame enter(Block b) {
            return new ProcessFrame(context, b, activeGuards, b.getBeginNode(), this, schedule);
        }

        @Override
        public Frame<?> enterAlwaysReached(Block b) {
            AnchoringNode newAnchor = anchor;
            if (parent != null && b.getLoop() != parent.block.getLoop() && !b.isLoopHeader()) {
                // We are exiting a loop => cannot reuse the anchor without inserting loop
                // proxies.
                newAnchor = b.getBeginNode();
            }
            return new ProcessFrame(context, b, activeGuards, newAnchor, this, schedule);
        }

        @Override
        public void postprocess() {
            if (anchor == block.getBeginNode() && OptEliminateGuards.getValue(activeGuards.graph().getOptions())) {
                for (GuardNode guard : anchor.asNode().usages().filter(GuardNode.class)) {
                    if (activeGuards.isMarkedAndGrow(guard)) {
                        activeGuards.clear(guard);
                    }
                }
            }
        }
    }

    @SuppressWarnings("try")
    private AnchoringNode process(CoreProviders context, final Block b, final NodeBitMap activeGuards, final AnchoringNode startAnchor, ScheduleResult schedule) {

        final LoweringToolImpl loweringTool = new LoweringToolImpl(context, startAnchor, activeGuards, b.getBeginNode(), schedule.getNodeToBlockMap());

        DebugContext debug = startAnchor.asNode().getDebug();

        // Lower the instructions of this block.
        List<Node> nodes = schedule.nodesFor(b);
        for (Node node : nodes) {

            if (node.isDeleted()) {
                // This case can happen when previous lowerings deleted nodes.
                continue;
            }

            // Cache the next node to be able to reconstruct the previous of the next node
            // after lowering.
            FixedNode nextNode = null;
            if (node instanceof FixedWithNextNode) {
                nextNode = ((FixedWithNextNode) node).next();
            } else {
                nextNode = loweringTool.lastFixedNode().next();
            }

            if (node instanceof Lowerable) {
                Collection<Node> unscheduledUsages = null;
                assert (unscheduledUsages = getUnscheduledUsages(node, schedule)) != null;
                Mark preLoweringMark = node.graph().getMark();
                try (DebugCloseable s = node.graph().withNodeSourcePosition(node)) {
                    TimerKey timer = null;
                    if (debug.areMetricsEnabled()) {
                        LoweringStatistics statistics = statisticsClassValue.get(node.getClass());
                        statistics.counters[loweringStage.ordinal()].increment(debug);
                        timer = statistics.timers[loweringStage.ordinal()];
                    }
                    try (DebugCloseable a = timer != null ? timer.start(debug) : null;
                                    DebugCloseable a2 = loweringStage.timer.start(debug)) {
                        ((Lowerable) node).lower(loweringTool);
                    }
                }
                if (loweringTool.guardAnchor.asNode().isDeleted()) {
                    // TODO nextNode could be deleted but this is not currently supported
                    assert nextNode.isAlive();
                    loweringTool.guardAnchor = AbstractBeginNode.prevBegin(nextNode);
                }
                assert checkPostNodeLowering(node, loweringTool, preLoweringMark, unscheduledUsages);
            }

            if (!nextNode.isAlive()) {
                // can happen when the rest of the block is killed by lowering
                // (e.g. by an unconditional deopt)
                break;
            } else {
                Node nextLastFixed = nextNode.predecessor();
                if (!(nextLastFixed instanceof FixedWithNextNode)) {
                    // insert begin node, to have a valid last fixed for next lowerable node.
                    // This is about lowering a FixedWithNextNode to a control split while this
                    // FixedWithNextNode is followed by some kind of BeginNode.
                    // For example the when a FixedGuard followed by a loop exit is lowered to a
                    // control-split + deopt.
                    AbstractBeginNode begin = node.graph().add(new BeginNode());
                    nextLastFixed.replaceFirstSuccessor(nextNode, begin);
                    begin.setNext(nextNode);
                    nextLastFixed = begin;
                }
                loweringTool.setLastFixedNode((FixedWithNextNode) nextLastFixed);
            }
        }
        return loweringTool.getCurrentGuardAnchor();
    }

    /**
     * Gets all usages of a floating, lowerable node that are unscheduled.
     * <p>
     * Given that the lowering of such nodes may introduce fixed nodes, they must be lowered in the
     * context of a usage that dominates all other usages. The fixed nodes resulting from lowering
     * are attached to the fixed node context of the dominating usage. This ensures the
     * post-lowering graph still has a valid schedule.
     *
     * @param node a {@link Lowerable} node
     */
    private static Collection<Node> getUnscheduledUsages(Node node, ScheduleResult schedule) {
        List<Node> unscheduledUsages = new ArrayList<>();
        if (node instanceof FloatingNode) {
            for (Node usage : node.usages()) {
                if (usage instanceof ValueNode && !(usage instanceof PhiNode) && !(usage instanceof ProxyNode)) {
                    if (schedule.getCFG().getNodeToBlock().isNew(usage) || schedule.getCFG().blockFor(usage) == null) {
                        unscheduledUsages.add(usage);
                    }
                }
            }
        }
        return unscheduledUsages;
    }

    enum ProcessBlockState {
        ST_ENTER,
        ST_PROCESS,
        ST_ENTER_ALWAYS_REACHED,
        ST_LEAVE,
        ST_PROCESS_ALWAYS_REACHED;
    }

    /**
     * This state-machine resembles the following recursion:
     *
     * <pre>
     * void processBlock(Block block) {
     *     preprocess();
     *     // Process always reached block first.
     *     Block alwaysReachedBlock = block.getPostdominator();
     *     if (alwaysReachedBlock != null &amp;&amp; alwaysReachedBlock.getDominator() == block) {
     *         processBlock(alwaysReachedBlock);
     *     }
     *
     *     // Now go for the other dominators.
     *     for (Block dominated : block.getDominated()) {
     *         if (dominated != alwaysReachedBlock) {
     *             assert dominated.getDominator() == block;
     *             processBlock(dominated);
     *         }
     *     }
     *     postprocess();
     * }
     * </pre>
     *
     * This is necessary, as the recursive implementation can quickly exceed the stack depth.
     *
     * @param rootFrame contains the starting block.
     */
    public static void processBlock(final Frame<?> rootFrame) {
        ProcessBlockState state = ST_PROCESS;
        Frame<?> f = rootFrame;
        while (f != null) {
            ProcessBlockState nextState;
            if (state == ST_PROCESS || state == ST_PROCESS_ALWAYS_REACHED) {
                f.preprocess();
                nextState = state == ST_PROCESS_ALWAYS_REACHED ? ST_ENTER : ST_ENTER_ALWAYS_REACHED;
            } else if (state == ST_ENTER_ALWAYS_REACHED) {
                if (f.alwaysReachedBlock != null && f.alwaysReachedBlock.getDominator() == f.block) {
                    f = f.enterAlwaysReached(f.alwaysReachedBlock);
                    nextState = ST_PROCESS;
                } else {
                    nextState = ST_ENTER;
                }
            } else if (state == ST_ENTER) {
                if (f.dominated != null) {
                    Block n = f.dominated;
                    f.dominated = n.getDominatedSibling();
                    if (n == f.alwaysReachedBlock) {
                        if (f.dominated != null) {
                            n = f.dominated;
                            f.dominated = n.getDominatedSibling();
                        } else {
                            n = null;
                        }
                    }
                    if (n == null) {
                        nextState = ST_LEAVE;
                    } else {
                        f = f.enter(n);
                        assert f.block.getDominator() == f.parent.block;
                        nextState = ST_PROCESS;
                    }
                } else {
                    nextState = ST_LEAVE;
                }
            } else if (state == ST_LEAVE) {
                f.postprocess();
                f = f.parent;
                nextState = ST_ENTER;
            } else {
                throw GraalError.shouldNotReachHere();
            }
            state = nextState;
        }
    }

    public abstract static class Frame<T extends Frame<?>> {
        protected final Block block;
        final T parent;
        Block dominated;
        final Block alwaysReachedBlock;

        public Frame(Block block, T parent) {
            this.block = block;
            this.alwaysReachedBlock = block.getPostdominator();
            this.dominated = block.getFirstDominated();
            this.parent = parent;
        }

        public Frame<?> enterAlwaysReached(Block b) {
            return enter(b);
        }

        public abstract Frame<?> enter(Block b);

        public abstract void preprocess();

        public abstract void postprocess();
    }

}
