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
package jdk.graal.compiler.phases.common;

import static jdk.graal.compiler.core.common.GraalOptions.OptEliminateGuards;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static jdk.graal.compiler.nodes.memory.MemoryKill.NO_LOCATION;
import static jdk.graal.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_ENTER;
import static jdk.graal.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_ENTER_ALWAYS_REACHED;
import static jdk.graal.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_LEAVE;
import static jdk.graal.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_PROCESS;
import static jdk.graal.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_PROCESS_ALWAYS_REACHED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryMapNode;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SideEffectFreeWriteNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public abstract class LoweringPhase extends BasePhase<CoreProviders> {

    public static class Options {
        //@formatter:off
        @Option(help = "Print schedule result pre lowering to TTY.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintLoweringScheduleToTTY = new OptionKey<>(false);
        @Option(help = "Dump lowering after every node to igv.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DumpAfterEveryLowering = new OptionKey<>(false);
        //@formatter:on
    }

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
        private FixedNode nextFixedNode;
        private NodeMap<HIRBlock> nodeMap;

        LoweringToolImpl(CoreProviders context, AnchoringNode guardAnchor, NodeBitMap activeGuards, FixedWithNextNode lastFixedNode, FixedNode nextFixedNode, NodeMap<HIRBlock> nodeMap) {
            super(context);
            this.guardAnchor = guardAnchor;
            this.activeGuards = activeGuards;
            this.lastFixedNode = lastFixedNode;
            this.nextFixedNode = nextFixedNode;
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
                        NodeSourcePosition noDeoptSuccessorPosition) {
            StructuredGraph graph = before.graph();
            if (OptEliminateGuards.getValue(graph.getOptions())) {
                for (GuardNode usage : condition.usages().filter(GuardNode.class)) {
                    if (!activeGuards.isNew(usage) && activeGuards.isMarked(usage) && usage.isNegated() == negated) {
                        ValueNode anchor = usage.getAnchor().asNode();
                        if (before.graph().isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) ||
                                        !nodeMap.isNew(anchor) && nodeMap.get(anchor).isInSameOrOuterLoopOf(nodeMap.get(before))) {
                            return usage;
                        }
                    }
                }
            }
            if (!condition.graph().getGuardsStage().allowsFloatingGuards()) {
                FixedGuardNode fixedGuard = graph.add(new FixedGuardNode(condition, deoptReason, action, speculation, negated, noDeoptSuccessorPosition));
                graph.addBeforeFixed(before, fixedGuard);
                DummyGuardHandle handle = graph.add(new DummyGuardHandle(fixedGuard));
                fixedGuard.lower(this);
                GuardingNode result = handle.getGuard();
                handle.safeDelete();
                return result;
            } else {
                GuardNode newGuard = graph.unique(new GuardNode(condition, guardAnchor, deoptReason, action, negated, speculation, noDeoptSuccessorPosition));
                if (OptEliminateGuards.getValue(graph.getOptions())) {
                    activeGuards.markAndGrow(newGuard);
                }
                return newGuard;
            }
        }

        public FixedNode nextFixedNode() {
            return nextFixedNode;
        }

        public void setNextFixedNode(FixedNode n) {
            GraalError.guarantee(n.isAlive(), "Cannot add next fixed node %s because it is not alive", n);
            nextFixedNode = n;
        }

        @Override
        public FixedWithNextNode lastFixedNode() {
            if (lastFixedNode == null) {
                Node pred = nextFixedNode.predecessor();
                if (!(pred instanceof FixedWithNextNode)) {
                    // insert begin node to have a valid FixedWithNextNode to insert after
                    AbstractBeginNode begin = nextFixedNode.graph().add(new BeginNode());
                    pred.replaceFirstSuccessor(lastFixedNode, begin);
                    begin.setNext(lastFixedNode);
                    lastFixedNode = begin;
                } else {
                    lastFixedNode = (FixedWithNextNode) pred;
                }
            } else {
                GraalError.guarantee(lastFixedNode.isAlive(), "The last fixed node %s was deleted by a previous lowering", lastFixedNode);
            }
            return lastFixedNode;
        }

        private void setLastFixedNode(FixedWithNextNode n) {
            GraalError.guarantee(n == null || n.isAlive(), "Cannot add last fixed node %s because it is not alive", n);
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
        assert mark.equals(expectedMark) || graph.getNewNodes(mark).count() == 0 : graph + ": a second round in the current lowering phase introduced these new nodes: " +
                        graph.getNewNodes(expectedMark).snapshot();
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

        if (Options.PrintLoweringScheduleToTTY.getValue(graph.getOptions())) {
            TTY.printf("%s%n", graph.getLastSchedule().print());
        }

        EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
        try (Graph.NodeEventScope nes = graph.trackNodeEvents(listener)) {
            ScheduleResult schedule = graph.getLastSchedule();
            schedule.getCFG().computePostdominators();
            HIRBlock startBlock = schedule.getCFG().getStartBlock();
            ProcessFrame rootFrame = new ProcessFrame(context, startBlock, graph.createNodeBitMap(), startBlock.getBeginNode(), null, schedule);
            LoweringPhase.processBlock(rootFrame);
        }

        if (!listener.getNodes().isEmpty()) {
            canonicalizer.applyIncremental(graph, context, listener.getNodes());
        }
    }

    /**
     * Checks that lowering of a given node did not introduce any new {@link Lowerable} nodes that
     * could be lowered in the current {@link LoweringPhase}. Such nodes must be recursively lowered
     * as part of lowering {@code node}.
     *
     * @param justLoweredNode a node that was just lowered
     * @param preLoweringMark the graph mark before {@code node} was lowered
     * @param unscheduledUsages set of {@code node}'s usages that were unscheduled before it was
     *            lowered
     * @throws AssertionError if the check fails
     */
    private static boolean checkPostNodeLowering(Node justLoweredNode, LoweringToolImpl loweringTool, Mark preLoweringMark, Collection<Node> unscheduledUsages) {
        StructuredGraph graph = (StructuredGraph) justLoweredNode.graph();
        Mark postLoweringMark = graph.getMark();
        NodeIterable<Node> newNodesAfterLowering = graph.getNewNodes(preLoweringMark);
        if (justLoweredNode instanceof FloatingNode) {
            if (!unscheduledUsages.isEmpty()) {
                for (Node n : newNodesAfterLowering) {
                    assert !(n instanceof FixedNode) : justLoweredNode.graph() + ": cannot lower floatable node " + justLoweredNode +
                                    " as it introduces fixed node(s) but has the following unscheduled usages: " +
                                    unscheduledUsages;
                }
            }
        }

        final boolean wasMemoryAccessBefore = justLoweredNode instanceof MemoryAccess;
        final boolean wasMemoryKillBefore = MemoryKill.isMemoryKill(justLoweredNode);

        for (Node newNodeAfterLowering : newNodesAfterLowering) {
            if (newNodeAfterLowering instanceof Lowerable) {
                ((Lowerable) newNodeAfterLowering).lower(loweringTool);
                Mark mark = graph.getMark();
                assert postLoweringMark.equals(mark) : graph + ": lowering of " + justLoweredNode + " produced lowerable " + newNodeAfterLowering +
                                " that should have been recursively lowered as it introduces these new nodes: " +
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
                    if (!(newNodeAfterLowering instanceof ForeignCall || newNodeAfterLowering instanceof UnreachableBeginNode || justLoweredNode instanceof WithExceptionNode ||
                                    newNodeAfterLowering instanceof MemoryMapNode || justLoweredNode instanceof CommitAllocationNode ||
                                    newNodeAfterLowering instanceof SideEffectFreeWriteNode) &&
                                    MemoryKill.isMemoryKill(newNodeAfterLowering)) {

                        // lowered to a kill verify the original node was a kill
                        if (MemoryKill.isSingleMemoryKill(newNodeAfterLowering)) {
                            SingleMemoryKill singleKill = (SingleMemoryKill) newNodeAfterLowering;
                            if (!singleKill.getKilledLocationIdentity().equals(NO_LOCATION)) {
                                if (!wasMemoryKillBefore) {
                                    // Kills to ININT_LOCATION are excluded above. We would like to
                                    // perform this check before and only once for both
                                    // single and multi kills however we have special nodes like a
                                    // side effect free write which use init location writes which
                                    // we ignore for verification purposes
                                    throw GraalError.shouldNotReachHere(String.format("Original node %s was not a kill but %s is", justLoweredNode, newNodeAfterLowering)); // ExcludeFromJacocoGeneratedReport
                                }
                                if (!(MemoryKill.isSingleMemoryKill(justLoweredNode))) {
                                    throw GraalError.shouldNotReachHere(String.format("Original node %s was not a single kill but %s is", justLoweredNode, newNodeAfterLowering)); // ExcludeFromJacocoGeneratedReport
                                }
                                SingleMemoryKill oldKill = (SingleMemoryKill) justLoweredNode;
                                if (!oldKill.getKilledLocationIdentity().isSingle() && singleKill.getKilledLocationIdentity().isSingle()) {
                                    // fine, high level node killed any, new nodes have more precise
                                    // kills
                                } else if (!oldKill.getKilledLocationIdentity().equals(singleKill.getKilledLocationIdentity())) {
                                    throw GraalError.shouldNotReachHere(
                                                    String.format("Original node %s kills %s while new node %s kills %s", justLoweredNode, oldKill.getKilledLocationIdentity(), singleKill,
                                                                    singleKill.getKilledLocationIdentity())); // ExcludeFromJacocoGeneratedReport
                                }
                            }
                        } else if (MemoryKill.isMultiMemoryKill(newNodeAfterLowering)) {
                            if (!wasMemoryKillBefore) {
                                // INIT_LOCATION special case: context above
                                throw GraalError.shouldNotReachHere(String.format("Original node %s was not a kill but %s is", justLoweredNode, newNodeAfterLowering)); // ExcludeFromJacocoGeneratedReport
                            }
                            if (!(MemoryKill.isMultiMemoryKill(justLoweredNode))) {
                                throw GraalError.shouldNotReachHere(String.format("Original node %s was not a multi kill but %s is", justLoweredNode, newNodeAfterLowering)); // ExcludeFromJacocoGeneratedReport
                            }
                            MultiMemoryKill newKill = (MultiMemoryKill) newNodeAfterLowering;
                            MultiMemoryKill oldKill = (MultiMemoryKill) justLoweredNode;
                            EconomicSet<LocationIdentity> killed = EconomicSet.create();
                            for (LocationIdentity loc : newKill.getKilledLocationIdentities()) {
                                killed.add(loc);
                            }
                            for (LocationIdentity oldLoc : oldKill.getKilledLocationIdentities()) {
                                if (killed.contains(oldLoc)) {
                                    killed.remove(oldLoc);
                                } else {
                                    throw GraalError.shouldNotReachHere(String.format("Original node %s kills %s while new node %s does not kill that location", oldKill, oldLoc, newKill)); // ExcludeFromJacocoGeneratedReport
                                }
                            }
                            for (LocationIdentity newLoc : killed) {
                                throw GraalError.shouldNotReachHere(String.format("New kill %s kills location %s while old kill %s does not", newKill, newLoc, oldKill)); // ExcludeFromJacocoGeneratedReport
                            }
                        } else {
                            throw GraalError.shouldNotReachHere("Unknown memory kill " + newNodeAfterLowering); // ExcludeFromJacocoGeneratedReport
                        }
                    } else if (newNodeAfterLowering instanceof MemoryAccess) {
                        // lowered to a memory access, verify high level node accesses same
                        // locations
                        MemoryAccess access = (MemoryAccess) newNodeAfterLowering;
                        if (access.getLocationIdentity().isMutable()) {
                            if (wasMemoryKillBefore && !wasMemoryAccessBefore) {
                                if (MemoryKill.isSingleMemoryKill(justLoweredNode)) {
                                    if (!((SingleMemoryKill) justLoweredNode).getKilledLocationIdentity().overlaps(access.getLocationIdentity())) {
                                        GraalError.shouldNotReachHere(String.format("Node %s was a memory kill killing %s but lowered to a memory access %s which accesses %s", justLoweredNode,
                                                        ((SingleMemoryKill) justLoweredNode).getKilledLocationIdentity(), newNodeAfterLowering, access.getLocationIdentity())); // ExcludeFromJacocoGeneratedReport
                                    }
                                } else if (MemoryKill.isMultiMemoryKill(justLoweredNode)) {
                                    boolean found = false;
                                    for (LocationIdentity ident : ((MultiMemoryKill) justLoweredNode).getKilledLocationIdentities()) {
                                        if (ident.overlaps(access.getLocationIdentity())) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        GraalError.shouldNotReachHere(String.format("Node %s was a memory kill not killing the location accessed by the lowered node: %s which accesses %s",
                                                        justLoweredNode, newNodeAfterLowering,
                                                        access.getLocationIdentity())); // ExcludeFromJacocoGeneratedReport
                                    }
                                } else {
                                    throw GraalError.shouldNotReachHere("Unknown type of memory kill " + justLoweredNode); // ExcludeFromJacocoGeneratedReport
                                }

                            } else if (wasMemoryAccessBefore) {
                                if (!access.getLocationIdentity().overlaps(((MemoryAccess) justLoweredNode).getLocationIdentity())) {
                                    GraalError.shouldNotReachHere(
                                                    String.format("Node %s was a memory access (%s) but lowered to a memory access %s %s", justLoweredNode,
                                                                    ((MemoryAccess) justLoweredNode).getLocationIdentity(),
                                                                    newNodeAfterLowering, access.getLocationIdentity())); // ExcludeFromJacocoGeneratedReport
                                }
                            } else {
                                GraalError.shouldNotReachHere(String.format("Node %s was not a memory access but lowered to a memory access %s", justLoweredNode, newNodeAfterLowering)); // ExcludeFromJacocoGeneratedReport
                            }
                        }
                    }
                }
            }

            if (MemoryKill.isMemoryKill(newNodeAfterLowering) && !(newNodeAfterLowering instanceof MemoryMapNode) && !(wasMemoryKillBefore) && !(justLoweredNode instanceof ControlSinkNode)) {
                if (!replaceeWithExceptionHandler(justLoweredNode, newNodeAfterLowering)) {
                    /*
                     * The lowering introduced a MemoryCheckpoint but the current node isn't a
                     * checkpoint. This is only OK if the locations involved don't affect the memory
                     * graph or if the new kill location doesn't connect into the existing graph.
                     */
                    boolean isAny = false;
                    if (MemoryKill.isSingleMemoryKill(newNodeAfterLowering)) {
                        isAny = ((SingleMemoryKill) newNodeAfterLowering).getKilledLocationIdentity().isAny();
                    } else if (MemoryKill.isMultiMemoryKill(newNodeAfterLowering)) {
                        for (LocationIdentity ident : ((MultiMemoryKill) newNodeAfterLowering).getKilledLocationIdentities()) {
                            if (ident.isAny()) {
                                isAny = true;
                            }
                        }
                    } else {
                        throw GraalError.shouldNotReachHere("Unknown type of memory kill " + newNodeAfterLowering); // ExcludeFromJacocoGeneratedReport
                    }
                    if (isAny && newNodeAfterLowering instanceof FixedWithNextNode) {
                        /*
                         * Check if the next kill location leads directly to a ControlSinkNode in
                         * the new part of the graph. This is a fairly conservative test that could
                         * be made more general if required.
                         */
                        FixedWithNextNode cur = (FixedWithNextNode) newNodeAfterLowering;
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
                    assert !isAny : justLoweredNode + " " + newNodeAfterLowering;
                }
            }
        }
        return true;
    }

    /**
     * Determine if the replacee was a {@link WithExceptionNode} that is lowered to another
     * {@code WithExceptionNode}, in this case there will be memory map nodes for the killing
     * of{@link LocationIdentity#ANY_LOCATION}.
     */
    private static boolean replaceeWithExceptionHandler(Node n, Node newNodeAfterLowering) {
        if (n instanceof WithExceptionNode && !n.isAlive() && newNodeAfterLowering instanceof FixedNode f) {
            return SnippetTemplate.walkBackToExceptionEdgeStart(f) instanceof ExceptionObjectNode.LoweredExceptionObjectBegin;
        }
        return false;
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

        ProcessFrame(CoreProviders context, HIRBlock block, NodeBitMap activeGuards, AnchoringNode anchor, ProcessFrame parent, ScheduleResult schedule) {
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
        public ProcessFrame enter(HIRBlock b) {
            return new ProcessFrame(context, b, activeGuards, b.getBeginNode(), this, schedule);
        }

        @Override
        public Frame<?> enterAlwaysReached(HIRBlock b) {
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
    private AnchoringNode process(CoreProviders context, final HIRBlock b, final NodeBitMap activeGuards, final AnchoringNode startAnchor, ScheduleResult schedule) {
        FixedWithNextNode lastFixedNode = b.getBeginNode();
        FixedNode nextFixedNode = lastFixedNode.next();
        if (b.getBeginNode() instanceof LoopExitNode) {
            /**
             * If we are processing a loop exit block and there are floating nodes only used flowing
             * out of the loop via proxies we must lower any control flow before the loop exit,
             * i.e., the fixed node that is used to introduce new control flow (after it) must be
             * before the loop exit node.
             *
             * Consider the following piece of code
             *
             * <pre>
             *             ifNode
             *       /              \
             * trueSuccessor         fN1                //  will be lowered to control flow
             *                     loopExitNode - proxy1(fN1)
             *                        use(proxy1)
             * </pre>
             *
             *
             * The floating node fN1 will be lowered to control flow - so for insertion we need a
             * fixed node before the loop exit. However, the loop exit is the begin node of the
             * basic block but there are floating nodes that need to be schedule before. This is
             * special because all other begin nodes will not have inputs but the loop exit via its
             * proxies will. Thus, in this case we need to create a begin node to act as the last
             * fixed node for insertion. Then control flow can be inserted between ifNode and
             * loopExitNode.
             *
             * Note that the control flow graph automatically creates a new basic block for every
             * loop exit because its a valid implementation of AbstractBeginNode.
             */
            FixedNode pred = (FixedNode) lastFixedNode.predecessor();
            if (pred instanceof FixedWithNextNode predWithNext) {
                lastFixedNode = predWithNext;
                nextFixedNode = predWithNext.next();
            } else {
                /**
                 * The loop exit is not preceded by a FixedWithNextNode. If a node needs to be
                 * lowered before the LoopExit, a BeginNode is introduced lazily (see:
                 * {@link LoweringToolImpl#lastFixedNode()}).
                 */
                nextFixedNode = b.getBeginNode();
                lastFixedNode = null;
            }
        }

        final LoweringToolImpl loweringTool = new LoweringToolImpl(context, startAnchor, activeGuards, lastFixedNode, nextFixedNode, schedule.getNodeToBlockMap());

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
            if (node instanceof FixedWithNextNode fixedWithNext) {
                loweringTool.setNextFixedNode(fixedWithNext.next());
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
                    assert loweringTool.nextFixedNode().isAlive();
                    loweringTool.guardAnchor = AbstractBeginNode.prevBegin(loweringTool.nextFixedNode());
                }
                assert checkPostNodeLowering(node, loweringTool, preLoweringMark, unscheduledUsages);
            }

            if (!loweringTool.nextFixedNode().isAlive()) {
                // can happen when the rest of the block is killed by lowering
                // (e.g. by an unconditional deopt)
                break;
            } else {
                Node nextLastFixed = loweringTool.nextFixedNode().predecessor();
                if (!(nextLastFixed instanceof FixedWithNextNode)) {
                    /**
                     * There is no FixedWithNextNode where subsequently lowered nodes can be
                     * attached to. This can happen when lowering a FixedWithNextNode to a control
                     * split while the FixedWithNextNode is followed by some kind of BeginNode. For
                     * example when a FixedGuard followed by a loop exit is lowered to a
                     * control-split + deopt. If there are further nodes to be lowered between the
                     * split and the next begin, {@link LoweringToolImpl#lastFixedNode()}) will
                     * lazily introduce a BeginNode.
                     */
                    nextLastFixed = null;
                }
                loweringTool.setLastFixedNode((FixedWithNextNode) nextLastFixed);
            }
            if (Options.DumpAfterEveryLowering.getValue(debug.getOptions())) {
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, b.getBeginNode().graph(), "After lowering %s", node);
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
                    if (schedule.blockFor(usage, true) == null) {
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
                    HIRBlock n = f.dominated;
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
                        assert f.block.getDominator() == f.parent.block : Assertions.errorMessage(f.block, f.block.getDominator(), f.parent.block);
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
                throw GraalError.shouldNotReachHereUnexpectedValue(state); // ExcludeFromJacocoGeneratedReport
            }
            state = nextState;
        }
    }

    public abstract static class Frame<T extends Frame<?>> {
        protected final HIRBlock block;
        final T parent;
        HIRBlock dominated;
        final HIRBlock alwaysReachedBlock;

        public Frame(HIRBlock block, T parent) {
            this.block = block;
            this.alwaysReachedBlock = block.getPostdominator();
            this.dominated = block.getFirstDominated();
            this.parent = parent;
        }

        public Frame<?> enterAlwaysReached(HIRBlock b) {
            return enter(b);
        }

        public abstract Frame<?> enter(HIRBlock b);

        public abstract void preprocess();

        public abstract void postprocess();
    }

}
