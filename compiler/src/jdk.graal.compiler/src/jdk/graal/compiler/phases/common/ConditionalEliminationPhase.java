/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodes.StaticDeoptimizingNode.mergeActions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.And;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Or;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConditionAnchorNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.DeoptimizingGuard;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.GuardProxyNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.OpaqueLogicNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.TypeSwitchNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.NodeWithState;
import jdk.graal.compiler.nodes.spi.StampInverter;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.InfoElement;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import jdk.vm.ci.meta.TriState;

/**
 * Performs conditional branch elimination on a {@link StructuredGraph}. This is done by optimizing
 * {@link LogicNode}s to be conditionally (flow-sensitive) {@code true} or {@code false}. If such a
 * condition is input to a control split node, i.e., an {@link IfNode} a subsequent application of
 * the {@link CanonicalizerPhase} can remove the unconditional branch from the graph.
 *
 * In order to prove conditions this phase build the
 * <a href="https://en.wikipedia.org/wiki/Dominator_(graph_theory)">Dominator Tree</a> of a method
 * and traverses it depth first. Every time the traversal encounters a basic block whose predecessor
 * has multiple successors (i.e., the predecessor block ends with a control flow split node) it
 * inspects the control split's condition in detail: The condition leading to the current block
 * carries value &amp; type information for the operands of the condition.
 *
 * Consider the following example where a variable {@code a} is used in a condition 3 times.
 * Traversing the dominator tree depth first and recording the value ranges for {@code a} after
 * every condition effectively makes the last condition {@code a == 0} trivially true.
 *
 * <pre>
 * if (a >= 0) {
 *     // a in [0:Integer.MAX_VAL]
 *     if (a &lt; 1) {
 *         // a in [Integer.MIN_VAL,0] &amp;&amp; a in [0:Integer.MAX_VAL]
 *         // --> a in [0]
 *         if (a == 0) { // true
 *         }
 *     }
 * }
 * </pre>
 *
 * @implNote This phase considers the following nodes (all of which have a
 *           {@link InputType#Condition} input edge):
 *           <ul>
 *           <li>{@link IfNode}
 *           <li>{@link SwitchNode}
 *           <li>{@link GuardNode}
 *           <li>{@link FixedGuardNode}
 *           <li>{@link ConditionAnchorNode}
 *           </ul>
 */
public class ConditionalEliminationPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public static class Options {
        // @formatter:off
        @Option(help = "Moves guard nodes to earlier places in the dominator tree if " +
                       "all successors of a basic block share a common guard condition.", type = OptionType.Expert)
        public static final OptionKey<Boolean> MoveGuardsUpwards = new OptionKey<>(true);
        @Option(help = "", type = OptionType.Debug)
         public static final OptionKey<Boolean> FieldAccessSkipPreciseTypes = new OptionKey<>(true);
        // @formatter:on
    }

    private static final CounterKey counterStampsRegistered = DebugContext.counter("StampsRegistered");
    private final boolean fullSchedule;
    private final boolean moveGuards;

    public ConditionalEliminationPhase(CanonicalizerPhase canonicalizer, boolean fullSchedule) {
        this(canonicalizer, fullSchedule, true);
    }

    public ConditionalEliminationPhase(CanonicalizerPhase canonicalizer, boolean fullSchedule, boolean moveGuards) {
        super(canonicalizer);
        this.fullSchedule = fullSchedule;
        this.moveGuards = moveGuards;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        try (DebugContext.Scope s = graph.getDebug().scope("DominatorConditionalElimination")) {
            BlockMap<List<Node>> blockToNodes = null;
            NodeMap<HIRBlock> nodeToBlock = null;
            ControlFlowGraph cfg = null;
            if (fullSchedule) {
                trySkippingGuardPis(graph);
                cfg = ControlFlowGraph.newBuilder(graph).modifiableBlocks(true).connectBlocks(true).computeFrequency(true).computeLoops(true).computeDominators(true).computePostdominators(
                                true).build();
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Conditional elimination after computing CFG");
                if (moveGuards && Options.MoveGuardsUpwards.getValue(graph.getOptions())) {
                    /**
                     * See comment in {@link MoveGuardsUpwards#enter}.
                     */
                    final boolean deferLoopExits = false;
                    cfg.visitDominatorTree(new MoveGuardsUpwards(), deferLoopExits);
                }
                try (DebugContext.Scope scheduleScope = graph.getDebug().scope(SchedulePhase.class)) {
                    if (!graph.isLastCFGValid()) {
                        cfg = null;
                    }
                    SchedulePhase.run(graph, SchedulePhase.SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER, cfg, context, false);
                    cfg = graph.getLastCFG();
                    cfg.computePostdominators();
                } catch (Throwable t) {
                    throw graph.getDebug().handle(t);
                }
                ScheduleResult r = graph.getLastSchedule();
                blockToNodes = r.getBlockToNodesMap();
                nodeToBlock = r.getNodeToBlockMap();
            } else {
                cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeDominators(true).computePostdominators(true).computeFrequency(true).build();
                nodeToBlock = cfg.getNodeToBlock();
                blockToNodes = getBlockToNodes(cfg);
            }
            ControlFlowGraph.RecursiveVisitor<?> visitor = createVisitor(graph, cfg, blockToNodes, nodeToBlock, context);
            cfg.visitDominatorTree(visitor, graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL));
        }
    }

    private static void trySkippingGuardPis(StructuredGraph graph) {
        for (GuardNode floatingGuard : graph.getNodes(GuardNode.TYPE).snapshot()) {
            PiNode.guardTrySkipPi(floatingGuard, floatingGuard.getCondition(), floatingGuard.isNegated(), NodeView.DEFAULT);
        }
        graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After trySkipGuardPis");
    }

    protected BlockMap<List<Node>> getBlockToNodes(@SuppressWarnings("unused") ControlFlowGraph cfg) {
        return null;
    }

    protected ControlFlowGraph.RecursiveVisitor<?> createVisitor(StructuredGraph graph, @SuppressWarnings("unused") ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodes,
                    NodeMap<HIRBlock> nodeToBlock, CoreProviders context) {
        return new Instance(graph, blockToNodes, nodeToBlock, context);
    }

    public static class MoveGuardsUpwards implements ControlFlowGraph.RecursiveVisitor<HIRBlock> {

        HIRBlock anchorBlock;

        @Override
        public String toString() {
            return "MoveGuardsUpwards - anchorBlock=" + anchorBlock;
        }

        @Override
        @SuppressWarnings("try")
        public HIRBlock enter(HIRBlock b) {
            HIRBlock oldAnchorBlock = anchorBlock;
            /*
             * REASONING:
             *
             * The goal of this pass is to move guards upward while not introducing the guards on
             * new paths. At all points the anchorBlock must set so the following two invariants
             * hold:
             *
             * (1) The anchorBlock dominates the current block.
             *
             * (2) The current block post-dominates the anchorBlock.
             *
             * Note blocks are traversed in dominator tree order.
             *
             * anchorBlock must be set to the current block if:
             *
             * (1) The current block does not have a dominator (i.e., this is the start of a new
             * dominator tree walk).
             *
             * (2) The immediate dominator of current block is not post-dominated by this block. Due
             * to using a dominator tree traversal, this is equivalent to ensuring the current block
             * post-dominates the anchorBlock.
             *
             * (3) Guards are not allowed to move above this block. The can happen when dominator
             * blocks can have invalid FrameStates, such as when the block start is a
             * CaptureStateBeginNode.
             */
            boolean updateAnchorBlock = b.getDominator() == null ||
                            b.getDominator().getPostdominator() != b ||
                            b.getBeginNode().mustNotMoveAttachedGuards();
            if (updateAnchorBlock) {
                // New anchor.
                anchorBlock = b;
            }

            final AbstractBeginNode beginNode = b.getBeginNode();

            /**
             * A note on loop exits and deferred loop exits in dominator tree traversal for
             * MoveGuardsUpwards: We must not defer loop exits when we run the move guards upwards
             * because a loop exit block is itself part of the dominator tree (normally as a
             * dedicated block) and we cannot leave it out. All the logic explained above under
             * "REASONING" is only correct if we visit all blocks in dominance order. Thus, when we
             * move guards we must ensure we are safe proxy wise.
             *
             * In order to do so we verify that the location of the anchor block can be used by the
             * current block without the need of any proxies.
             */
            final boolean canMoveGuardsToAnchorBlock = b.getCfg().graph.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) ||
                            LoopUtility.canUseWithoutProxy(b.getCfg(), anchorBlock.getBeginNode(), b.getBeginNode());

            if (canMoveGuardsToAnchorBlock) {
                if (anchorBlock != b) {
                    AbstractBeginNode abstractBegin = beginNode;
                    abstractBegin.replaceAtUsages(anchorBlock.getBeginNode(), InputType.Anchor, InputType.Guard);
                    abstractBegin.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, abstractBegin.graph(), "After moving guard and anchored usages from %s to %s", abstractBegin,
                                    anchorBlock.getBeginNode());
                    assert abstractBegin.anchored().isEmpty();
                }
                FixedNode endNode = b.getEndNode();
                if (endNode instanceof IfNode) {
                    IfNode node = (IfNode) endNode;

                    // Check if we can move guards upwards.
                    AbstractBeginNode trueSuccessor = node.trueSuccessor();
                    AbstractBeginNode falseSuccessor = node.falseSuccessor();

                    EconomicMap<LogicNode, GuardNode> trueGuards = EconomicMap.create(Equivalence.IDENTITY);
                    for (GuardNode guard : trueSuccessor.guards()) {
                        LogicNode condition = guard.getCondition();
                        if (condition.hasMoreThanOneUsage()) {
                            trueGuards.put(condition, guard);
                        }
                    }
                    if (!trueGuards.isEmpty()) {
                        /*
                         * Special case loop exits: We must only ever move guards over loop exits if
                         * we move them over all loop exits (i.e. if a successor is a loop exit it
                         * must be the only loop exit or a loop has two exits and both are
                         * successors of the current if). Else we would risk moving a guard from
                         * after a particular exit into the loop (might be loop invariant) which can
                         * be too early resulting in the generated code deopting without the need
                         * to.
                         *
                         * Note: The code below is written with the possibility in mind that both
                         * successors are loop exits, even of potentially different loops. Thus, we
                         * need to ensure we see all possible loop exits involved for all loops.
                         */
                        EconomicSet<LoopExitNode> allLoopsAllExits = null;
                        if (trueSuccessor instanceof LoopExitNode successor) {
                            if (allLoopsAllExits == null) {
                                allLoopsAllExits = EconomicSet.create();
                            }
                            allLoopsAllExits.addAll(successor.loopBegin().loopExits());
                            allLoopsAllExits.remove(successor);
                        }
                        if (falseSuccessor instanceof LoopExitNode successor) {
                            if (allLoopsAllExits == null) {
                                allLoopsAllExits = EconomicSet.create();
                            }
                            allLoopsAllExits.addAll(successor.loopBegin().loopExits());
                            allLoopsAllExits.remove(successor);
                        }
                        if (allLoopsAllExits == null || allLoopsAllExits.isEmpty()) {
                            for (GuardNode falseGuard : falseSuccessor.guards().snapshot()) {
                                GuardNode trueGuard = trueGuards.get(falseGuard.getCondition());
                                if (trueGuard != null && falseGuard.isNegated() == trueGuard.isNegated()) {
                                    Speculation speculation = trueGuard.getSpeculation();
                                    if (speculation == null) {
                                        speculation = falseGuard.getSpeculation();
                                    } else if (falseGuard.getSpeculation() != null && !falseGuard.getSpeculation().equals(speculation)) {
                                        // Cannot optimize due to different speculations.
                                        continue;
                                    }
                                    try (DebugCloseable closeable = falseGuard.withNodeSourcePosition()) {
                                        StructuredGraph graph = falseGuard.graph();
                                        GuardNode newlyCreatedGuard = new GuardNode(falseGuard.getCondition(), anchorBlock.getBeginNode(), falseGuard.getReason(), falseGuard.getAction(),
                                                        falseGuard.isNegated(), speculation,
                                                        falseGuard.getNoDeoptSuccessorPosition());
                                        GuardNode newGuard = node.graph().unique(newlyCreatedGuard);
                                        if (trueGuard.isAlive()) {
                                            if (trueSuccessor instanceof LoopExitNode && beginNode.graph().isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
                                                trueGuard.replaceAndDelete(ProxyNode.forGuard(newGuard, (LoopExitNode) trueSuccessor));
                                            } else {
                                                trueGuard.replaceAndDelete(newGuard);
                                            }
                                        }
                                        if (falseSuccessor instanceof LoopExitNode && beginNode.graph().isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
                                            falseGuard.replaceAndDelete(ProxyNode.forGuard(newGuard, (LoopExitNode) falseSuccessor));
                                        } else {
                                            falseGuard.replaceAndDelete(newGuard);
                                        }
                                        graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "GuardCombination", falseGuard);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return oldAnchorBlock;
        }

        @Override
        public void exit(HIRBlock b, HIRBlock value) {
            anchorBlock = value;
        }

    }

    private static final class PhiInfoElement {

        private EconomicMap<EndNode, ConditionalEliminationUtil.InfoElement> infoElements;

        public void set(EndNode end, ConditionalEliminationUtil.InfoElement infoElement) {
            if (infoElements == null) {
                infoElements = EconomicMap.create(Equivalence.IDENTITY);
            }
            infoElements.put(end, infoElement);
        }

        public ConditionalEliminationUtil.InfoElement get(EndNode end) {
            if (infoElements == null) {
                return null;
            }
            return infoElements.get(end);
        }
    }

    public static class Instance implements ControlFlowGraph.RecursiveVisitor<ConditionalEliminationUtil.Marks> {
        protected final NodeMap<ConditionalEliminationUtil.InfoElement> map;
        protected final BlockMap<List<Node>> blockToNodes;
        protected final NodeMap<HIRBlock> nodeToBlock;
        protected final CanonicalizerTool tool;
        protected final NodeStack undoOperations;
        protected final StructuredGraph graph;
        protected final DebugContext debug;
        protected final EconomicMap<MergeNode, EconomicMap<ValuePhiNode, PhiInfoElement>> mergeMaps;
        private final ConditionalEliminationUtil.InfoElementProvider infoElementProvider;
        private final ConditionalEliminationUtil.GuardFolding guardFolding;
        protected final ArrayDeque<ConditionalEliminationUtil.GuardedCondition> conditions;
        private final boolean processFieldAccess;
        private final List<RebuildPiData> piCache = new ArrayList<>(8);
        private final EconomicSet<Pair<Stamp, Stamp>> joinedStamps = EconomicSet.create();
        protected EconomicMap<AbstractBeginNode, Stamp> successorStampCache;

        /**
         * Tests which may be eliminated because post dominating tests to prove a broader condition.
         */
        private Deque<DeoptimizingGuard> pendingTests;

        public Instance(StructuredGraph graph, BlockMap<List<Node>> blockToNodes, NodeMap<HIRBlock> nodeToBlock, CoreProviders context) {
            this.graph = graph;
            this.debug = graph.getDebug();
            this.blockToNodes = blockToNodes;
            this.nodeToBlock = nodeToBlock;
            this.undoOperations = new NodeStack();
            this.map = graph.createNodeMap();
            this.pendingTests = new ArrayDeque<>();
            this.conditions = new ArrayDeque<>();
            tool = GraphUtil.getDefaultSimplifier(context, false, graph.getAssumptions(), graph.getOptions());
            mergeMaps = EconomicMap.create(Equivalence.IDENTITY);
            infoElementProvider = new ConditionalEliminationUtil.InfoElementProvider() {

                @Override
                public ConditionalEliminationUtil.InfoElement infoElements(ValueNode value) {
                    return getInfoElements(value);
                }
            };
            guardFolding = new ConditionalEliminationUtil.GuardFolding() {

                @Override
                public boolean foldGuard(DeoptimizingGuard thisGuard, ValueNode original, Stamp newStamp, ConditionalEliminationUtil.GuardRewirer rewireGuardFunction) {
                    return foldPendingTest(thisGuard, original, newStamp, rewireGuardFunction);
                }
            };
            this.processFieldAccess = Options.FieldAccessSkipPreciseTypes.getValue(graph.getOptions());
        }

        protected void processConditionAnchor(ConditionAnchorNode node) {
            tryProveGuardCondition(null, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                if (result != node.isNegated()) {
                    node.replaceAtUsages(guard.asNode());
                    GraphUtil.unlinkFixedNode(node);
                    GraphUtil.killWithUnusedFloatingInputs(node);
                } else {
                    ValueAnchorNode valueAnchor = node.graph().add(new ValueAnchorNode());
                    node.replaceAtUsages(valueAnchor);
                    node.graph().replaceFixedWithFixed(node, valueAnchor);
                }
                graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "ConditionAnchorElimination", node);
                return true;
            });
        }

        protected void processGuard(GuardNode node) {
            if (!tryProveGuardCondition(node, node.getCondition(), (guard, result, guardedValueStamp, newInput) -> {
                if (result != node.isNegated()) {
                    ValueNode condition = node.getCondition();
                    node.replaceAndDelete(guard.asNode());
                    if (condition.hasNoUsages()) {
                        GraphUtil.killWithUnusedFloatingInputs(condition);
                    }
                    if (guard instanceof BeginNode b && b.predecessor() instanceof IfNode ifNode) {
                        rebuildPiNodes(b, ifNode.condition());
                    } else if (guard instanceof DeoptimizingGuard dg && !((DeoptimizingGuard) guard).isNegated()) {
                        rebuildPiNodes(dg, dg.getCondition());
                    }
                } else {
                    AbstractBeginNode beginNode = (AbstractBeginNode) node.getAnchor();

                    if (beginNode.next() instanceof DeoptimizeNode) {
                        // This branch is already dead.
                    } else {
                        /*
                         * Don't kill this branch immediately because `killCFG` can have complex
                         * implications in the presence of loops: it might replace or delete nodes
                         * in other branches or even above the kill point. Instead of killing
                         * immediately, just leave the graph in a state that is easy to simplify by
                         * a subsequent canonicalizer phase.
                         */
                        FixedGuardNode deopt = new FixedGuardNode(LogicConstantNode.forBoolean(result, node.graph()), node.getReason(), node.getAction(), node.getSpeculation(), node.isNegated(),
                                        node.getNodeSourcePosition());
                        graph.addAfterFixed(beginNode, node.graph().add(deopt));
                    }
                }
                graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "GuardElimination", node);
                return true;
            })) {
                registerNewCondition(node.getCondition(), node.isNegated(), node);
            }
        }

        protected void processFixedGuard(FixedGuardNode node) {
            if (!tryProveGuardCondition(node, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                if (result != node.isNegated()) {
                    node.replaceAtUsages(guard.asNode());
                    GraphUtil.unlinkFixedNode(node);
                    GraphUtil.killWithUnusedFloatingInputs(node);

                    if (guard instanceof BeginNode b && b.predecessor() instanceof IfNode ifNode) {
                        rebuildPiNodes(b, ifNode.condition());
                    } else if (guard instanceof DeoptimizingGuard dg && !((DeoptimizingGuard) guard).isNegated()) {
                        rebuildPiNodes(dg, dg.getCondition());
                    }
                } else {
                    node.setCondition(LogicConstantNode.forBoolean(result, node.graph()), node.isNegated());
                    // Don't kill this branch immediately, see `processGuard`.
                }
                graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "FixedGuardElimination", node);
                return true;
            })) {
                registerNewCondition(node.condition(), node.isNegated(), node);
            }
        }

        /**
         * Optimize load field / store field pi inputs if possible.
         *
         * This is a common Java source code pattern like:
         *
         * <pre>
         * if (someObject instanceof ConcreteClass concrete) {
         *     use(concrete.field);
         * }
         * </pre>
         *
         * Bytecode parsing builds two instanceof checks:
         *
         * <pre>
         *     if (someObject instanceof ConcreteClass[non-null]) {
         *         guard = FixedGuard(ClassCastException, someObject instanceof ConcreteClass[may be null]);
         *         concrete = Pi(guard, someObject, piStamp = ConcreteClass[may be null]);
         *         use(concrete.field);
         *     }
         * </pre>
         *
         * Conditional elimination removes the guard. We are left with the Pi anchored on the if:
         *
         * <pre>
         *     if (someObject instanceof ConcreteClass[non-null]) {
         *         anchor = BeginNode();
         *         concrete = Pi(anchor, someObject, piStamp = ConcreteClass[may be null]);
         *         use(concrete.field);
         *     }
         * </pre>
         *
         * The Pi now proves a weaker stamp than the branch on which it is anchored. When we lower
         * the field access, we would need to insert a null check. That null check would eventually
         * fold away, but in the meantime it could prevent other high tier canonicalizations.
         * Strengthen the Pi to include the information that the object is non-null.
         */
        private void processAccessField(AccessFieldNode af) {
            ValueNode object = af.object();
            ResolvedJavaField field = af.field();
            if (object instanceof PiNode objectPi) {
                // see if there are earlier pi's we can use for the same data
                final boolean nonNull = ((AbstractObjectStamp) object.stamp(NodeView.DEFAULT)).nonNull();
                GuardingNode fieldPiGuard = objectPi.getGuard();
                LogicNode condition = null;
                if (fieldPiGuard instanceof BeginNode b) {
                    if (b.predecessor() instanceof IfNode ifNode && b == ifNode.trueSuccessor()) {
                        condition = ifNode.condition();
                    }
                } else if (fieldPiGuard instanceof GuardNode floatingGuard && !floatingGuard.isNegated()) {
                    condition = floatingGuard.getCondition();
                } else if (fieldPiGuard instanceof FixedGuardNode fixedGuard && !fixedGuard.isNegated()) {
                    condition = fixedGuard.getCondition();
                }

                if (condition instanceof UnaryOpLogicNode unaryLogicNode) {
                    final ValueNode value = unaryLogicNode.getValue();
                    InfoElement infoElement = infoElementProvider.infoElements(value);
                    while (infoElement != null) {
                        /*
                         * Once we optimized and skipped a pi we do not immediately remove it from
                         * the graph. Other nodes may still use it, we let the canonicalizer take
                         * care of it. We have to ensure we are not using a later pi again that we
                         * just recently skipped. In the optimization (if conditional elimination is
                         * called multiple times).
                         */
                        if (infoElement.getGuard() != fieldPiGuard && nodeToBlock.get(infoElement.getGuard().asNode()).strictlyDominates(nodeToBlock.get(fieldPiGuard.asNode()))) {
                            final Stamp stamp = infoElement.getStamp();
                            /*
                             * Determine if this pi can be skipped by using a pi based on the stamp
                             * of this guard.
                             */
                            if (stamp instanceof AbstractObjectStamp objectStamp && objectStamp.nonNull() == nonNull && objectStamp.type() != null &&
                                            field.getDeclaringClass().isAssignableFrom(objectStamp.type())) {
                                ValueNode newPi = graph.addOrUnique(PiNode.create(value, stamp, infoElement.getGuard().asNode()));
                                af.setObject(newPi);
                                graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "AccessFieldSkipPi", af);
                                break;
                            }
                        }
                        infoElement = infoElementProvider.nextElement(infoElement);
                    }
                }

            }
        }

        record RebuildPiData(PiNode piNode, boolean differentCheckedStamp,
                        boolean differentObject) {
        }

        private void rebuildPiNodes(GuardingNode guard, LogicNode condition) {
            piCache.clear();
            LogicNode newCondition = condition;
            if (newCondition instanceof InstanceOfNode) {
                InstanceOfNode inst = (InstanceOfNode) newCondition;
                ValueNode originalValue = GraphUtil.skipPi(inst.getValue());
                PiNode pi = null;

                for (PiNode existing : guard.asNode().usages().filter(PiNode.class)) {
                    if (!existing.isAlive()) {
                        continue;
                    }
                    boolean differentCheckedStamp = !existing.piStamp().equals(inst.getCheckedStamp());
                    boolean differentObject = existing.object() != inst.getValue();
                    if (differentObject || differentCheckedStamp) {
                        // only call out to skipPi which can be expensive if we would try to
                        // optimize this pi
                        if (originalValue != GraphUtil.skipPi(existing.object())) {
                            // Somehow these are unrelated values so leave it alone
                            continue;
                        }
                        piCache.add(new RebuildPiData(existing, differentCheckedStamp, differentObject));
                    }
                }
                if (piCache.isEmpty()) {
                    return;
                }
                // Ensure that any Pi that's weaker than what the instanceof proves is
                // replaced by one derived from the instanceof itself.
                for (RebuildPiData piData : piCache) {
                    PiNode existing = piData.piNode;

                    Pair<Stamp, Stamp> strongerStampPairKey = Pair.create(existing.piStamp(), inst.getCheckedStamp());

                    // If the pi has a weaker stamp or the same stamp but a different input
                    // then replace it.
                    final boolean previouslyJoined = joinedStamps.contains(strongerStampPairKey);
                    boolean weakerOrSame = previouslyJoined;
                    if (!previouslyJoined) {
                        weakerOrSame = existing.piStamp().join(inst.getCheckedStamp()).equals(inst.getCheckedStamp());
                    }
                    if (weakerOrSame) {
                        assert piData.differentCheckedStamp || piData.differentObject : Assertions.errorMessage("Cache should only be filled if we have a reason ", piData);
                        joinedStamps.add(strongerStampPairKey);
                        if (pi == null) {
                            pi = graph.unique(new PiNode(inst.getValue(), inst.getCheckedStamp(), (ValueNode) guard));
                        }
                        if (!pi.stamp(NodeView.DEFAULT).join(existing.stamp(NodeView.DEFAULT)).equals(pi.stamp(NodeView.DEFAULT))) {
                            /*
                             * With a code sequence like null check, type check, null check of type
                             * checked value, CE will use the first null check to prove the second
                             * null check so the graph ends up a Pi guarded by the first null check
                             * but consuming the output Pi from the type check check. In this case
                             * we should still canonicalize the checked stamp for consistency.
                             */
                            if (piData.differentCheckedStamp) {
                                PiNode alternatePi = graph.unique(new PiNode(existing.object(), inst.getCheckedStamp(), (ValueNode) guard));
                                /*
                                 * If the resulting stamp is as good or better then do the
                                 * replacement. However when interface types are involved it's
                                 * possible that improving the checked stamp merges types which
                                 * appear unrelated so there's we must skip the replacement.
                                 */
                                if (alternatePi.stamp(NodeView.DEFAULT).join(existing.stamp(NodeView.DEFAULT)).equals(alternatePi.stamp(NodeView.DEFAULT))) {
                                    existing.replaceAndDelete(alternatePi);
                                    graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "PiImprovement", existing);
                                }
                            }
                            continue;
                        }
                        existing.replaceAndDelete(pi);
                        graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "PiImprovement", existing);
                    }
                }
            }
        }

        protected void processIf(IfNode node) {
            tryProveGuardCondition(null, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                node.setCondition(LogicConstantNode.forBoolean(result, node.graph()));
                AbstractBeginNode survivingSuccessor = node.getSuccessor(result);
                if (survivingSuccessor instanceof LoopExitNode loopExitNode) {
                    Node replacementForGuardedNodes = graph.unique(new GuardProxyNode(guard, loopExitNode));
                    survivingSuccessor.replaceAtUsages(replacementForGuardedNodes, InputType.Guard);
                    if (replacementForGuardedNodes.hasNoUsages()) {
                        replacementForGuardedNodes.safeDelete();
                    }
                } else {
                    survivingSuccessor.replaceAtUsages(guard.asNode(), InputType.Guard);
                }

                // Don't kill the other branch immediately, see `processGuard`.
                graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "IfElimination", node);
                return true;
            });
        }

        /**
         * {@link PiNode} anchored at a {@link ValueAnchorNode} are used to incorporate
         * inter-procedural information such as static analysis results. When such a {@link PiNode}
         * is proven by a previous condition, it can be optimized the same way as a condition.
         */
        private void processValueAnchor(ValueAnchorNode node) {
            for (Node usage : node.usages().snapshot()) {
                if (usage instanceof PiNode && ((PiNode) usage).getGuard() == node) {
                    tryImproveAnchoredPi((PiNode) usage);
                }
            }
        }

        private void tryImproveAnchoredPi(PiNode piNode) {
            ConditionalEliminationUtil.InfoElement infoElement = infoElementProvider.infoElements(piNode.object());
            while (infoElement != null) {
                Stamp joinedStamp = infoElement.getStamp().join(piNode.piStamp());
                if (joinedStamp.equals(infoElement.getStamp())) {
                    /*
                     * The PiNode is already proven by a dominating condition. We just re-anchor the
                     * PiNode at the dominating point. If that point already has an equivalent
                     * PiNode, the Canonicalizer will combine them.
                     */
                    piNode.setGuard(infoElement.getGuard());
                    return;
                }
                infoElement = infoElementProvider.nextElement(infoElement);
            }

            /*
             * The PiNode cannot be proven by a dominating condition. But the information can be
             * used to eliminate dominating conditions or anchored PiNode.
             */
            registerNewStamp(piNode.object(), piNode.piStamp(), piNode.getGuard());
        }

        private void processCompressionNode(CompressionNode compression) {
            if (!(compression.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp)) {
                return;
            }

            AbstractObjectStamp stamp = (AbstractObjectStamp) compression.stamp(NodeView.DEFAULT);
            ConditionalEliminationUtil.InfoElement infoElement = infoElementProvider.infoElements(compression.getValue());
            while (infoElement != null) {
                if (infoElement.getStamp() instanceof AbstractObjectStamp) {
                    Stamp improvedStamp = compression.foldStamp(infoElement.getStamp());
                    if (!stamp.equals(improvedStamp)) {
                        registerNewStamp(compression, improvedStamp, infoElement.getGuard());
                    }
                }
                infoElement = infoElementProvider.nextElement(infoElement);
            }
        }

        @Override
        public ConditionalEliminationUtil.Marks enter(HIRBlock block) {
            int infoElementsMark = undoOperations.size();
            int conditionsMark = conditions.size();
            debug.log("[Pre Processing block %s]", block);
            // For now conservatively collect guards only within the same block.
            pendingTests.clear();
            processNodes(block);
            return new ConditionalEliminationUtil.Marks(infoElementsMark, conditionsMark);
        }

        protected void processNodes(HIRBlock block) {
            if (blockToNodes != null) {
                for (Node n : blockToNodes.get(block)) {
                    if (n.isAlive()) {
                        processNode(n);
                    }
                }
            } else {
                processBlock(block);
            }
        }

        private void processBlock(HIRBlock block) {
            FixedNode n = block.getBeginNode();
            FixedNode endNode = block.getEndNode();
            debug.log("[Processing block %s]", block);
            while (n != endNode) {
                if (n.isDeleted() || endNode.isDeleted()) {
                    // This branch was deleted!
                    return;
                }
                FixedNode next = ((FixedWithNextNode) n).next();
                processNode(n);
                n = next;
            }
            if (endNode.isAlive()) {
                processNode(endNode);
            }
        }

        @SuppressWarnings("try")
        protected void processNode(Node node) {
            try (DebugCloseable closeable = node.withNodeSourcePosition()) {
                if (node instanceof NodeWithState && !(node instanceof GuardingNode)) {
                    pendingTests.clear();
                }

                if (node instanceof MergeNode) {
                    introducePisForPhis((MergeNode) node);
                }

                if (node instanceof SwitchNode switchNode) {
                    /*
                     * Since later in this phase we will be visiting all control split successors
                     * the operation of computing successor stamps for switch nodes can be quite
                     * costly. Thus, we already compute and cache all eagerly here.
                     */
                    if (successorStampCache == null) {
                        successorStampCache = EconomicMap.create();
                    }
                    switchNode.getAllSuccessorValueStamps(successorStampCache);
                }

                if (node instanceof AbstractBeginNode) {
                    if (node instanceof LoopExitNode && graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
                        // Condition must not be used down this path.
                        return;
                    }
                    processAbstractBegin((AbstractBeginNode) node);
                } else if (node instanceof FixedGuardNode) {
                    processFixedGuard((FixedGuardNode) node);
                } else if (node instanceof GuardNode) {
                    processGuard((GuardNode) node);
                } else if (node instanceof ConditionAnchorNode) {
                    processConditionAnchor((ConditionAnchorNode) node);
                } else if (node instanceof IfNode) {
                    processIf((IfNode) node);
                } else if (node instanceof EndNode) {
                    processEnd((EndNode) node);
                } else if (node instanceof ValueAnchorNode) {
                    processValueAnchor((ValueAnchorNode) node);
                } else if (node instanceof CompressionNode c) {
                    processCompressionNode(c);
                } else if (processFieldAccess && node instanceof AccessFieldNode af) {
                    processAccessField(af);
                }
            }
        }

        protected void introducePisForPhis(MergeNode merge) {
            EconomicMap<ValuePhiNode, PhiInfoElement> mergeMap = this.mergeMaps.get(merge);
            if (mergeMap != null) {
                MapCursor<ValuePhiNode, PhiInfoElement> entries = mergeMap.getEntries();
                while (entries.advance()) {
                    ValuePhiNode phi = entries.getKey();
                    assert phi.isAlive() || phi.isDeleted();
                    /*
                     * Phi might have been killed already via a conditional elimination in another
                     * branch.
                     */
                    if (phi.isDeleted()) {
                        continue;
                    }
                    PhiInfoElement phiInfoElements = entries.getValue();
                    Stamp bestPossibleStamp = null;
                    for (int i = 0; i < phi.valueCount(); ++i) {
                        ValueNode valueAt = phi.valueAt(i);
                        Stamp curBestStamp = valueAt.stamp(NodeView.DEFAULT);
                        ConditionalEliminationUtil.InfoElement infoElement = phiInfoElements.get(merge.forwardEndAt(i));
                        if (infoElement != null) {
                            curBestStamp = curBestStamp.join(infoElement.getStamp());
                        }

                        if (bestPossibleStamp == null) {
                            bestPossibleStamp = curBestStamp;
                        } else {
                            bestPossibleStamp = bestPossibleStamp.meet(curBestStamp);
                        }
                    }

                    Stamp oldStamp = phi.stamp(NodeView.DEFAULT);
                    if (oldStamp.tryImproveWith(bestPossibleStamp) != null) {

                        // Need to be careful to not run into stamp update cycles with the iterative
                        // canonicalization.
                        boolean allow = false;
                        if (bestPossibleStamp instanceof ObjectStamp) {
                            // Always allow object stamps.
                            allow = true;
                        } else if (bestPossibleStamp instanceof IntegerStamp) {
                            IntegerStamp integerStamp = (IntegerStamp) bestPossibleStamp;
                            IntegerStamp oldIntegerStamp = (IntegerStamp) oldStamp;
                            if (integerStamp.isPositive() != oldIntegerStamp.isPositive()) {
                                allow = true;
                            } else if (integerStamp.isNegative() != oldIntegerStamp.isNegative()) {
                                allow = true;
                            } else if (integerStamp.isStrictlyPositive() != oldIntegerStamp.isStrictlyPositive()) {
                                allow = true;
                            } else if (integerStamp.isStrictlyNegative() != oldIntegerStamp.isStrictlyNegative()) {
                                allow = true;
                            } else if (integerStamp.asConstant() != null) {
                                allow = true;
                            } else if (oldStamp.isUnrestricted()) {
                                allow = true;
                            }
                        } else {
                            // Fortify: Suppress Null Dereference false positive
                            assert bestPossibleStamp != null;
                            allow = (bestPossibleStamp.asConstant() != null);
                        }

                        if (allow) {
                            ValuePhiNode newPhi = graph.addWithoutUnique(new ValuePhiNode(bestPossibleStamp, merge));
                            for (int i = 0; i < phi.valueCount(); ++i) {
                                ValueNode valueAt = phi.valueAt(i);
                                if (bestPossibleStamp.meet(valueAt.stamp(NodeView.DEFAULT)).equals(bestPossibleStamp)) {
                                    // Pi not required here.
                                } else {
                                    ConditionalEliminationUtil.InfoElement infoElement = phiInfoElements.get(merge.forwardEndAt(i));
                                    assert infoElement != null;
                                    Stamp curBestStamp = infoElement.getStamp();
                                    ValueNode input = infoElement.getProxifiedInput();
                                    if (input == null) {
                                        input = valueAt;
                                    }
                                    valueAt = graph.addOrUnique(PiNode.create(input, curBestStamp, (ValueNode) infoElement.getGuard()));
                                }
                                newPhi.addInput(valueAt);
                            }
                            phi.replaceAtUsagesAndDelete(newPhi);
                            graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "PhiImprovement", phi);
                        }
                    }
                }
            }
        }

        protected void processEnd(EndNode end) {
            AbstractMergeNode abstractMerge = end.merge();
            if (abstractMerge instanceof MergeNode) {
                MergeNode merge = (MergeNode) abstractMerge;

                EconomicMap<ValuePhiNode, PhiInfoElement> mergeMap = this.mergeMaps.get(merge);
                for (ValuePhiNode phi : merge.valuePhis()) {
                    ValueNode valueAt = phi.valueAt(end);
                    ConditionalEliminationUtil.InfoElement infoElement = this.getInfoElements(valueAt);
                    while (infoElement != null) {
                        Stamp newStamp = infoElement.getStamp();
                        if (phi.stamp(NodeView.DEFAULT).tryImproveWith(newStamp) != null) {
                            if (mergeMap == null) {
                                mergeMap = EconomicMap.create(Equivalence.IDENTITY);
                                mergeMaps.put(merge, mergeMap);
                            }

                            PhiInfoElement phiInfoElement = mergeMap.get(phi);
                            if (phiInfoElement == null) {
                                phiInfoElement = new PhiInfoElement();
                                mergeMap.put(phi, phiInfoElement);
                            }

                            phiInfoElement.set(end, infoElement);
                            break;
                        }
                        infoElement = nextElement(infoElement);
                    }
                }
            }
        }

        protected void registerNewCondition(LogicNode condition, boolean negated, GuardingNode guard) {
            /*
             * <PI proven always true> Special case PI nodes and already-to-be-proven logic nodes:
             * If a previous operation leads to a guard that will already unconditionally fold away
             * because of its (new) input: Since we did not yet ran canonicalization on it we are
             * not allowed to use this guard to try to prove any other knowledge. This is the case
             * since this node is not a valid source of truth if we look through pi nodes. If a
             * rebuilt pi in a dominator lets it to be correct already, using this guard without
             * considering the proving guard of its pi is not allowed. A follow up canonicalization
             * can plainly remove this guard then and any proven guards lose the relation to this
             * nodes input pi.
             */

            if (condition instanceof UnaryOpLogicNode) {
                UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) condition;
                ValueNode value = unaryLogicNode.getValue();

                TriState unconditionallyFold = unaryLogicNode.tryFold(value.stamp(NodeView.DEFAULT));
                if (unconditionallyFold.isKnown()) {
                    // <PI proven always true>
                    return;
                }

                if (maybeMultipleUsages(value)) {
                    // getSucceedingStampForValue doesn't take the (potentially a Pi Node) input
                    // stamp into account, so it can be safely propagated.
                    Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(negated);
                    registerNewStamp(value, newStamp, guard, true);
                }
            } else if (condition instanceof BinaryOpLogicNode) {

                BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) condition;
                ValueNode x = binaryOpLogicNode.getX();
                ValueNode y = binaryOpLogicNode.getY();

                TriState unconditionallyFold = binaryOpLogicNode.tryFold(x.stamp(NodeView.DEFAULT),
                                y.stamp(NodeView.DEFAULT));
                if (unconditionallyFold.isKnown()) {
                    // <PI proven always true>
                    return;
                }

                if (!x.isConstant() && maybeMultipleUsages(x)) {
                    Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(negated, ConditionalEliminationUtil.getSafeStamp(x), ConditionalEliminationUtil.getOtherSafeStamp(y));
                    registerNewStamp(x, newStampX, guard);
                }

                if (!y.isConstant() && maybeMultipleUsages(y)) {
                    Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(negated, ConditionalEliminationUtil.getOtherSafeStamp(x), ConditionalEliminationUtil.getSafeStamp(y));
                    registerNewStamp(y, newStampY, guard);
                }

                if (condition instanceof IntegerEqualsNode && guard instanceof DeoptimizingGuard && !negated) {
                    if (y.isConstant() && x instanceof AndNode) {
                        AndNode and = (AndNode) x;
                        ValueNode andX = and.getX();
                        if (and.getY() == y && maybeMultipleUsages(andX)) {
                            /*
                             * This 'and' proves something about some of the bits in and.getX().
                             * It's equivalent to or'ing in the mask value since those values are
                             * known to be set.
                             */
                            BinaryOp<Or> op = ArithmeticOpTable.forStamp(x.stamp(NodeView.DEFAULT)).getOr();
                            IntegerStamp newStampX = (IntegerStamp) op.foldStamp(ConditionalEliminationUtil.getSafeStamp(andX), ConditionalEliminationUtil.getOtherSafeStamp(y));
                            registerNewStamp(andX, newStampX, guard);
                        }
                    }
                }
            }
            if (guard instanceof DeoptimizingGuard) {
                // For <PI proven always true> no need since both optimizable classes of logic nodes
                // are handled under the unary and binary cases above
                assert ((DeoptimizingGuard) guard).getCondition() == condition : Assertions.errorMessageContext("guard", guard, "condition", condition);
                pendingTests.push((DeoptimizingGuard) guard);
            }
            registerCondition(condition, negated, guard);
        }

        /**
         * Look for a preceding guard whose condition is implied by {@code thisGuard}. If we find
         * one, try to move this guard just above that preceding guard so that we can fold it:
         *
         * <pre>
         *     guard(C1); // preceding guard
         *     ...
         *     guard(C2); // thisGuard
         * </pre>
         *
         * If C2 => C1, transform to:
         *
         * <pre>
         *     guard(C2);
         *     ...
         * </pre>
         */
        protected boolean foldPendingTest(DeoptimizingGuard thisGuard, ValueNode original, Stamp newStamp, ConditionalEliminationUtil.GuardRewirer rewireGuardFunction) {
            for (DeoptimizingGuard pendingGuard : pendingTests) {
                LogicNode pendingCondition = pendingGuard.getCondition();
                TriState result = TriState.UNKNOWN;
                if (pendingCondition instanceof UnaryOpLogicNode) {
                    UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) pendingCondition;
                    if (unaryLogicNode.getValue() == original) {
                        result = unaryLogicNode.tryFold(newStamp);
                    }
                } else if (pendingCondition instanceof BinaryOpLogicNode) {
                    BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) pendingCondition;
                    ValueNode x = binaryOpLogicNode.getX();
                    ValueNode y = binaryOpLogicNode.getY();
                    if (x == original) {
                        result = binaryOpLogicNode.tryFold(newStamp, ConditionalEliminationUtil.getOtherSafeStamp(y));
                    } else if (y == original) {
                        result = binaryOpLogicNode.tryFold(ConditionalEliminationUtil.getOtherSafeStamp(x), newStamp);
                    } else if (binaryOpLogicNode instanceof IntegerEqualsNode && y.isConstant() && x instanceof AndNode) {
                        AndNode and = (AndNode) x;
                        if (and.getY() == y && and.getX() == original) {
                            BinaryOp<And> andOp = ArithmeticOpTable.forStamp(newStamp).getAnd();
                            result = binaryOpLogicNode.tryFold(andOp.foldStamp(newStamp, ConditionalEliminationUtil.getOtherSafeStamp(y)), ConditionalEliminationUtil.getOtherSafeStamp(y));
                        }
                    }
                }
                if (result.isKnown()) {
                    /*
                     * The test case be folded using the information available but the test can only
                     * be moved up if we're sure there's no schedule dependence.
                     */
                    if (canScheduleAbove(thisGuard.getCondition(), pendingGuard.asNode(), original) && foldGuard(thisGuard, pendingGuard, result.toBoolean(), newStamp, rewireGuardFunction)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean canScheduleAbove(Node n, Node target, ValueNode knownToBeAbove) {
            HIRBlock targetBlock = nodeToBlock.get(target);
            HIRBlock testBlock = nodeToBlock.get(n);
            if (targetBlock != null && testBlock != null) {
                if (targetBlock == testBlock) {
                    for (Node fixed : blockToNodes.get(targetBlock)) {
                        if (fixed == n) {
                            return true;
                        } else if (fixed == target) {
                            break;
                        }
                    }
                } else if (testBlock.dominates(targetBlock)) {
                    return true;
                }
            }
            ConditionalEliminationUtil.InputFilter v = new ConditionalEliminationUtil.InputFilter(knownToBeAbove);
            n.applyInputs(v);
            return v.ok;
        }

        protected boolean foldGuard(DeoptimizingGuard thisGuard, DeoptimizingGuard otherGuard, boolean outcome, Stamp guardedValueStamp, ConditionalEliminationUtil.GuardRewirer rewireGuardFunction) {
            DeoptimizationAction action = mergeActions(otherGuard.getAction(), thisGuard.getAction());
            if (action != null && Objects.equals(otherGuard.getSpeculation(), thisGuard.getSpeculation())) {
                LogicNode condition = (LogicNode) thisGuard.getCondition().copyWithInputs();
                /*
                 * We have ...; guard(C1); guard(C2);...
                 *
                 * Where the first guard is `otherGuard` and the second one `thisGuard`.
                 *
                 * Depending on `outcome`, we have C2 => C1 or C2 => !C1.
                 *
                 * - If C2 => C1, `mustDeopt` below is false and we transform to ...; guard(C2); ...
                 *
                 * - If C2 => !C1, `mustDeopt` is true and we transform to ..; guard(C1); deopt;
                 */
                // for the second case, the action of the deopt is copied from there:
                thisGuard.setAction(action);
                ConditionalEliminationUtil.GuardRewirer rewirer = (guard, result, innerGuardedValueStamp, newInput) -> {
                    // `result` is `outcome`, `guard` is `otherGuard`
                    boolean mustDeopt = result == otherGuard.isNegated();
                    if (rewireGuardFunction.rewire(guard, mustDeopt == thisGuard.isNegated(), innerGuardedValueStamp, newInput)) {
                        if (!mustDeopt) {
                            otherGuard.setCondition(condition, thisGuard.isNegated());
                            otherGuard.setAction(action);
                            otherGuard.setReason(thisGuard.getReason());
                            graph.getOptimizationLog().report(ConditionalEliminationPhase.class, "GuardFolding", thisGuard.asNode());
                        }
                        return true;
                    }
                    condition.safeDelete();
                    return false;
                };
                // Move the later test up
                return ConditionalEliminationUtil.rewireGuards(otherGuard, outcome, null, guardedValueStamp, rewirer);
            }
            return false;
        }

        protected boolean tryProveGuardCondition(DeoptimizingGuard thisGuard, LogicNode node, ConditionalEliminationUtil.GuardRewirer rewireGuardFunction) {
            return ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, guardFolding, thisGuard, node, rewireGuardFunction);
        }

        protected void registerCondition(LogicNode condition, boolean negated, GuardingNode guard) {
            if (condition instanceof OpaqueLogicNode) {
                return;
            }
            if (condition.hasMoreThanOneUsage()) {
                registerNewStamp(condition, negated ? StampFactory.contradiction() : StampFactory.tautology(), guard);
            }
            conditions.push(new ConditionalEliminationUtil.GuardedCondition(guard, condition, negated));
        }

        protected ConditionalEliminationUtil.InfoElement getInfoElements(ValueNode proxiedValue) {
            if (proxiedValue == null) {
                return null;
            }
            ConditionalEliminationUtil.InfoElement infoElement = map.getAndGrow(proxiedValue);
            if (infoElement == null) {
                infoElement = map.getAndGrow(GraphUtil.skipPi(proxiedValue));
            }
            return infoElement;
        }

        private ConditionalEliminationUtil.InfoElement nextElement(ConditionalEliminationUtil.InfoElement current) {
            ConditionalEliminationUtil.InfoElement parent = current.getParent();
            if (parent != null) {
                return parent;
            } else {
                ValueNode proxifiedInput = current.getProxifiedInput();
                if (proxifiedInput instanceof PiNode) {
                    PiNode piNode = (PiNode) proxifiedInput;
                    return getInfoElements(piNode.getOriginalNode());
                }
            }
            return null;
        }

        protected void registerNewStamp(ValueNode maybeProxiedValue, Stamp newStamp, GuardingNode guard) {
            registerNewStamp(maybeProxiedValue, newStamp, guard, false);
        }

        protected void registerNewStamp(ValueNode maybeProxiedValue, Stamp newStamp, GuardingNode guard, boolean propagateThroughPis) {
            assert maybeProxiedValue != null;
            assert guard != null;

            if (newStamp == null || newStamp.isUnrestricted()) {
                return;
            }

            ValueNode value = maybeProxiedValue;
            Stamp stamp = newStamp;

            while (stamp != null && value != null) {
                ValueNode proxiedValue = null;
                if (value instanceof PiNode) {
                    proxiedValue = value;
                }
                counterStampsRegistered.increment(debug);
                debug.log("\t Saving stamp for node %s stamp %s guarded by %s", value, stamp, guard);
                assert value instanceof LogicNode || stamp.isCompatible(value.stamp(NodeView.DEFAULT)) : stamp + " vs. " + value.stamp(NodeView.DEFAULT) + " (" + value + ")";
                map.setAndGrow(value, new ConditionalEliminationUtil.InfoElement(stamp, guard, proxiedValue, map.getAndGrow(value)));
                undoOperations.push(value);
                if (propagateThroughPis && value instanceof PiNode) {
                    PiNode piNode = (PiNode) value;
                    value = piNode.getOriginalNode();
                } else if (value instanceof StampInverter) {
                    StampInverter stampInverter = (StampInverter) value;
                    value = stampInverter.getValue();
                    stamp = stampInverter.invertStamp(stamp);
                } else {
                    break;
                }
            }
        }

        protected void processAbstractBegin(AbstractBeginNode beginNode) {
            Node predecessor = beginNode.predecessor();
            if (predecessor instanceof IfNode) {
                IfNode ifNode = (IfNode) predecessor;
                boolean negated = (ifNode.falseSuccessor() == beginNode);
                LogicNode condition = ifNode.condition();
                registerNewCondition(condition, negated, beginNode);
            } else if (predecessor instanceof TypeSwitchNode) {
                TypeSwitchNode typeSwitch = (TypeSwitchNode) predecessor;
                processTypeSwitch(beginNode, typeSwitch);
            } else if (predecessor instanceof IntegerSwitchNode) {
                IntegerSwitchNode integerSwitchNode = (IntegerSwitchNode) predecessor;
                processIntegerSwitch(beginNode, integerSwitchNode);
            }
        }

        private static boolean maybeMultipleUsages(ValueNode value) {
            if (value.hasMoreThanOneUsage()) {
                return true;
            } else {
                return value instanceof ProxyNode ||
                                value instanceof PiNode ||
                                value instanceof StampInverter;
            }
        }

        protected void processIntegerSwitch(AbstractBeginNode beginNode, IntegerSwitchNode integerSwitchNode) {
            ValueNode value = integerSwitchNode.value();
            if (maybeMultipleUsages(value)) {
                if (successorStampCache == null) {
                    successorStampCache = EconomicMap.create();
                }
                Stamp stamp = integerSwitchNode.getValueStampForSuccessor(beginNode, successorStampCache);
                if (stamp != null) {
                    registerNewStamp(value, stamp, beginNode);
                }
            }
        }

        protected void processTypeSwitch(AbstractBeginNode beginNode, TypeSwitchNode typeSwitch) {
            ValueNode hub = typeSwitch.value();
            if (hub instanceof LoadHubNode) {
                LoadHubNode loadHub = (LoadHubNode) hub;
                ValueNode value = loadHub.getValue();
                if (maybeMultipleUsages(value)) {
                    if (successorStampCache == null) {
                        successorStampCache = EconomicMap.create();
                    }
                    Stamp stamp = typeSwitch.getValueStampForSuccessor(beginNode, successorStampCache);
                    if (stamp != null) {
                        registerNewStamp(value, stamp, beginNode);
                    }
                }
            }
        }

        @Override
        public void exit(HIRBlock b, ConditionalEliminationUtil.Marks marks) {
            int infoElementsMark = marks.infoElementOperations;
            while (undoOperations.size() > infoElementsMark) {
                Node node = undoOperations.pop();
                if (node.isAlive()) {
                    map.set(node, map.get(node).getParent());
                }
            }

            int conditionsMark = marks.conditions;
            while (conditions.size() > conditionsMark) {
                conditions.pop();
            }
        }

    }

    @Override
    public float codeSizeIncrease() {
        return 1.5f;
    }
}
