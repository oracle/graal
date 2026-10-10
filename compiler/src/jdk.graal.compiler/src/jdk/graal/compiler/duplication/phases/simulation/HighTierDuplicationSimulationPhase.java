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

import static jdk.graal.compiler.duplication.util.DuplicationUtil.blockHasOneSuccessor;
import static jdk.graal.compiler.duplication.util.DuplicationUtil.isRealMergeBlock;
import static jdk.graal.compiler.phases.common.ConditionalEliminationUtil.getOtherSafeStamp;
import static jdk.graal.compiler.phases.common.ConditionalEliminationUtil.getSafeStamp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase.VectorizationCheck;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationSimulationUtil.SimulationAdvancement;
import jdk.graal.compiler.duplication.util.DuplicationUtil;
import jdk.graal.compiler.duplication.util.DuplicationUtil.CFGFrequencyInfo;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ConditionAnchorNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph.RecursiveVisitor;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.java.TypeSwitchNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.Canonicalizable.Binary;
import jdk.graal.compiler.nodes.spi.Canonicalizable.Unary;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.StampInverter;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.SingleRunSubphase;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.GuardedCondition;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.InfoElement;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.InfoElementProvider;
import jdk.graal.compiler.phases.common.ConditionalEliminationUtil.Marks;
import jdk.graal.compiler.phases.common.SafeStampInputSearch;
import jdk.graal.compiler.phases.common.util.GlobalProfilesOptimizationUtility;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.TriState;

/**
 * @see DuplicationSimulationUtil
 */
public class HighTierDuplicationSimulationPhase extends SingleRunSubphase<CoreProviders> {

    private final SimulationConfig config;
    private final CanonicalizerTool canonicalizerTool;
    private final boolean fullSchedule;
    private final VectorizationCheck vectCheck;

    public HighTierDuplicationSimulationPhase(OptionValues optionValues, SimulationConfig config, CanonicalizerTool canonicalizerTool, VectorizationCheck vectCheck) {
        fullSchedule = DuplicationOptions.ScheduledDuplicationSimulation.getValue(optionValues);
        this.config = config;
        this.canonicalizerTool = canonicalizerTool;
        this.vectCheck = vectCheck;
    }

    public HighTierDuplicationSimulationPhase(OptionValues optionValues, SimulationConfig config, CanonicalizerTool canonicalizerTool) {
        this(optionValues, config, canonicalizerTool, (x, y, z) -> false);
    }

    private SimulationContext simContext;
    private SimulationEndInfo[] improvements;

    public SimulationEndInfo[] getImprovements() {
        return improvements;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        BlockMap<List<Node>> blockToNodes = null;
        NodeMap<HIRBlock> nodeToBlock = null;
        ControlFlowGraph cfg = null;
        if (fullSchedule) {
            cfg = ControlFlowGraph.newBuilder(graph).modifiableBlocks(true).connectBlocks(true).computeFrequency(true).computeLoops(true).computeDominators(true).computePostdominators(true).build();
            try (DebugContext.Scope scheduleScope = graph.getDebug().scope(SchedulePhase.class)) {
                SchedulePhase.run(graph, SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER, cfg, context, true);
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
        simContext = new SimulationContext(graph, cfg, blockToNodes, nodeToBlock, new CFGFrequencyInfo(cfg), context);
        SimulationCFGTraversal.visitDominatorTreeBounded(cfg, simContext.visitor, cfg.getStartBlock(), 0, Integer.MAX_VALUE);
        improvements = simContext.simulatedImprovements();
    }

    protected BlockMap<List<Node>> getBlockToNodes(ControlFlowGraph cfg) {
        BlockMap<List<Node>> nodes = new BlockMap<>(cfg);
        for (HIRBlock b : cfg.getBlocks()) {
            ArrayList<Node> curNodes = new ArrayList<>();
            for (FixedNode node : b.getNodes()) {
                curNodes.add(node);
            }
            nodes.put(b, curNodes);
        }
        return nodes;
    }

    @Override
    public float codeSizeIncrease() {
        return 1.5f;
    }

    private final class SimulationContext {
        private BlockMap<SimulationEndInfo> improvements;
        private int simulationsPerformed;
        private final ControlFlowGraph cfg;
        private final RecursiveVisitor<?> visitor;
        private SimulationFrame simulationVisitor;
        private final boolean stopAtCF;
        private final CFGFrequencyInfo cfgFrequencyInfo;
        private final LoopsData loopsData;
        private final EconomicSet<Loop> vectorizableLoops;

        private SimulationContext(StructuredGraph graph, ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodes, NodeMap<HIRBlock> nodeToBlock, CFGFrequencyInfo cfgFrequencyInfo,
                        CoreProviders context) {
            this.cfg = cfg;
            this.visitor = new SimulationEntraceFrame(graph, blockToNodes, nodeToBlock, context);
            this.stopAtCF = config.stopSimulationAtControlFlow(graph.getOptions());
            this.cfgFrequencyInfo = cfgFrequencyInfo;
            this.loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();
            vectorizableLoops = EconomicSet.create();
            for (Loop loop : loopsData.countedLoops()) {
                if (vectCheck.isVectorizable(loop, graph, context)) {
                    vectorizableLoops.add(loop);
                }
            }
        }

        private class SimulationEntraceFrame implements ControlFlowGraph.RecursiveVisitor<Marks> {
            final NodeMap<HIRBlock> nodeToBlock;
            protected final NodeMap<InfoElement> map;
            protected final BlockMap<List<Node>> blockToNodes;
            protected final NodeStack undoOperations;
            protected final StructuredGraph graph;
            protected final DebugContext debug;
            protected final ArrayDeque<GuardedCondition> conditions;
            protected final InfoElementProvider infoElementProvider;
            protected final CoreProviders providers;
            protected final SafeStampInputSearch safeStampInputSearch;
            protected EconomicMap<AbstractBeginNode, Stamp> successorStampCache;

            SimulationEntraceFrame(StructuredGraph graph, BlockMap<List<Node>> blockToNodes, NodeMap<HIRBlock> nodeToBlock, CoreProviders providers) {
                this.graph = graph;
                this.debug = graph.getDebug();
                this.blockToNodes = blockToNodes;
                this.nodeToBlock = nodeToBlock;
                this.undoOperations = new NodeStack();
                this.map = graph.createNodeMap();
                this.conditions = new ArrayDeque<>();
                this.safeStampInputSearch = new SafeStampInputSearch(graph);
                infoElementProvider = new InfoElementProvider() {

                    @Override
                    public InfoElement infoElements(ValueNode value) {
                        return getInfoElements(value);
                    }
                };
                this.providers = providers;
            }

            protected void processSimulation(HIRBlock forwardEnd) {
                HIRBlock mergeBlock = forwardEnd.getFirstSuccessor();
                SimulationEndInfo simNode = new SimulationEndInfo(forwardEnd, cfgFrequencyInfo.getFrequencyNormalizedToMaxFrequency(forwardEnd), vectorizableLoops);
                if (improvements == null) {
                    improvements = new BlockMap<>(cfg);
                }
                SimulationEndInfo simulationEndInfo = improvements.get(forwardEnd);
                assert simulationEndInfo == null : "MUst not haev an improvement recorded for " + forwardEnd + " already but found " + simulationEndInfo;
                improvements.put(forwardEnd, simNode);
                simulationsPerformed++;
                if (simulationVisitor == null) {
                    simulationVisitor = new SimulationFrame(graph, blockToNodes, nodeToBlock, providers);
                }
                simulationVisitor.beforeSimulation(simNode, this.map, this.conditions);
                simulationVisitor.pushPhiSynonyms(mergeBlock);
                simulationVisitor.simulationStartMerge = (MergeNode) mergeBlock.getBeginNode();
                int maxDepth = DuplicationUtil.blockDepthIgnoringEarlyExits(simulationVisitor.simulationStartMerge);
                SimulationCFGTraversal.visitDominatorTreeBounded(cfg, simulationVisitor, mergeBlock, mergeBlock.getDominatorDepth(), maxDepth);
                simulationVisitor.afterSimulation();
            }

            protected void simulateDuplication(HIRBlock block) {
                if (blockHasOneSuccessor(block) && isRealMergeBlock(block.getFirstSuccessor())) {
                    processSimulation(block);
                }
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

            private boolean maybeMultipleUsages(ValueNode value) {
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
                    assert value instanceof LogicNode || stamp.isCompatible(value.stamp(NodeView.DEFAULT)) : stamp + " vs. " + value.stamp(NodeView.DEFAULT) + " (" + value + ")";
                    map.setAndGrow(value, new InfoElement(stamp, guard, proxiedValue, map.getAndGrow(value)));
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

            @Override
            public void exit(HIRBlock b, Marks marks) {
                int infoElementsMark = marks.getInfoElementOperations();
                while (undoOperations.size() > infoElementsMark) {
                    Node node = undoOperations.pop();
                    if (node.isAlive()) {
                        map.set(node, map.get(node).getParent());
                    }
                }
                int conditionsMark = marks.getConditions();
                while (conditions.size() > conditionsMark) {
                    conditions.pop();
                }
            }

            protected void registerNewCondition(LogicNode condition, boolean negated, GuardingNode guard) {
                if (ConditionalEliminationUtil.conditionFoldsWithInputStamps(condition)) {
                    return;
                }

                if (condition instanceof UnaryOpLogicNode) {
                    UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) condition;
                    ValueNode value = unaryLogicNode.getValue();
                    if (maybeMultipleUsages(value)) {
                        Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(negated);
                        registerNewStamp(value, newStamp, guard, true);
                    }
                } else if (condition instanceof BinaryOpLogicNode) {
                    BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) condition;
                    ValueNode x = binaryOpLogicNode.getX();
                    ValueNode y = binaryOpLogicNode.getY();
                    if (!x.isConstant() && maybeMultipleUsages(x)) {
                        Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(negated, getSafeStamp(x),
                                        getOtherSafeStamp(y, safeStampInputSearch));
                        registerNewStamp(x, newStampX, guard);
                    }

                    if (!y.isConstant() && maybeMultipleUsages(y)) {
                        Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(negated, getOtherSafeStamp(x, safeStampInputSearch),
                                        getSafeStamp(y));
                        registerNewStamp(y, newStampY, guard);
                    }
                }
                registerCondition(condition, negated, guard);
            }

            protected void registerCondition(LogicNode condition, boolean negated, GuardingNode guard) {
                if (condition.hasMoreThanOneUsage()) {
                    registerNewStamp(condition, negated ? StampFactory.contradiction() : StampFactory.tautology(), guard);
                }
                conditions.push(new GuardedCondition(guard, condition, negated));
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

            @SuppressWarnings("try")
            private void processNode(Node node) {
                if (node instanceof SwitchNode switchNode) {
                    /*
                     * Since later in this phase we will be visiting all control split successor s
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
                }
            }

            private double simulationMinFrequency() {
                final double noPruningMinFrequency = Double.MIN_VALUE;
                // Do not ignore basic blocks if profiles are not trusted. Also process everything
                // for very hot code that is globally prioritized for optimization.
                if (!cfgFrequencyInfo.isTrusted() || GlobalProfilesOptimizationUtility.shouldPrioritizeForOptimization(graph)) {
                    return noPruningMinFrequency;
                }
                if (!DuplicationOptions.SimulationPruneUnlikelyBranches.getValue(graph.getOptions())) {
                    return noPruningMinFrequency;
                }
                return DuplicationOptions.DuplicationMinBranchFrequency.getValue(graph.getOptions());
            }

            private static boolean alwaysSimulate(HIRBlock block) {
                if (!blockHasOneSuccessor(block) || !isRealMergeBlock(block.getFirstSuccessor())) {
                    return false;
                }
                MergeNode merge = (MergeNode) block.getFirstSuccessor().getBeginNode();
                return merge.getDuplicationHint() == MergeNode.DuplicationHint.EXPLORE;
            }

            @Override
            public Marks enter(HIRBlock block) {
                int infoElementsMark = undoOperations.size();
                int conditionsMark = conditions.size();
                processNodes(block);
                double minFrequency = simulationMinFrequency();
                boolean forcedSimulation = alwaysSimulate(block);
                if (forcedSimulation || block.getRelativeFrequency() > minFrequency || DuplicationUtil.alwaysEnterBlockFilter(block)) {
                    simulateDuplication(block);
                    if (forcedSimulation) {
                        graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Positive: Entering bb %s because successor merge %s requests unconditional simulation", block,
                                        block.getFirstSuccessor().getBeginNode());
                    } else {
                        graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Positive: Entering bb %s because of relative frequency %f and min frequency %f", block,
                                        block.getRelativeFrequency(), minFrequency);
                    }
                } else {
                    graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Negative: Not entering bb %s because of relative frequency %f and min frequency %f", block, block.getRelativeFrequency(),
                                    minFrequency);
                }
                return new Marks(infoElementsMark, conditionsMark);
            }

            protected void processConditionAnchor(ConditionAnchorNode node) {
                if (!tryProveGuardCondition(node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                    // we do not want to modify the graph during simulation
                    return true;
                })) {
                    registerNewCondition(node.condition(), node.isNegated(), node);
                }
            }

            private boolean tryProveGuardCondition(LogicNode condition, ConditionalEliminationUtil.GuardRewirer rewireGuardFunction) {
                return ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, null, null, condition, rewireGuardFunction, false, safeStampInputSearch);
            }

            protected void processGuard(GuardNode node) {
                if (!ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, null, null, node.getCondition(), (guard, result, guardedValueStamp, newInput) -> {
                    // we do not want to modify the graph during simulation
                    return true;
                }, false, safeStampInputSearch)) {
                    registerNewCondition(node.getCondition(), node.isNegated(), node);
                }
            }

            protected void processFixedGuard(FixedGuardNode node) {
                if (!ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, null, null, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                    // we do not want to modify the graph during simulation
                    return true;
                }, false, safeStampInputSearch)) {
                    registerNewCondition(node.condition(), node.isNegated(), node);
                }
            }

            protected void processIf(@SuppressWarnings("unused") IfNode node) {
                // we do not want to modify the graph during simulation
            }

            protected InfoElement getInfoElements(ValueNode proxiedValue) {
                if (proxiedValue == null || map.isNew(proxiedValue)) {
                    return null;
                }
                InfoElement infoElement = map.getAndGrow(proxiedValue);
                if (infoElement == null) {
                    infoElement = map.getAndGrow(GraphUtil.skipPi(proxiedValue));
                }
                return infoElement;
            }

        }

        private class SimulationFrame extends SimulationEntraceFrame {
            protected MergeNode simulationStartMerge;
            protected NodeMap<Node> synonyms;
            protected List<Runnable> synonymUndoOperations = new ArrayList<>();
            protected int undoPointer;
            private SimulationEndInfo simulationState;
            private SimulationAdvancement status = SimulationAdvancement.CONTINUE;
            private final NodeBitMap processed;
            private final NodeBitMap inRegion;
            private final NodeBitMap visited;
            private AbstractBeginNode stopNode;
            private AbstractBeginNode restartNode;

            SimulationFrame(StructuredGraph graph, BlockMap<List<Node>> blockToNodes, NodeMap<HIRBlock> nodeToBlock, CoreProviders providers) {
                super(graph, blockToNodes, nodeToBlock, providers);
                processed = graph.createNodeBitMap();
                inRegion = graph.createNodeBitMap();
                visited = graph.createNodeBitMap();
            }

            private void beforeSimulation(SimulationEndInfo state, NodeMap<InfoElement> outerMap, ArrayDeque<GuardedCondition> outerConditions) {
                assert simulationState == null;
                this.simulationState = state;
                if (synonyms != null) {
                    undoPointer = synonymUndoOperations.size();
                }
                status = SimulationAdvancement.CONTINUE;
                MapCursor<Node, InfoElement> cursor = outerMap.getEntries();
                while (cursor.advance()) {
                    this.map.setAndGrow(cursor.getKey(), cursor.getValue());
                }
                conditions.addAll(outerConditions);
            }

            private void afterSimulation() {
                this.simulationState = null;
                int curSize = synonymUndoOperations.size();
                while (curSize > undoPointer) {
                    synonymUndoOperations.remove(--curSize).run();
                }
                stopNode = null;
                restartNode = null;
                processed.clearAll();
                inRegion.clearAll();
                map.clear();
                conditions.clear();
            }

            @Override
            protected void simulateDuplication(HIRBlock block) {
                // recursive simulation disabled
            }

            @Override
            protected void processSimulation(HIRBlock forwardEnd) {
                // recursive simulation disabled
            }

            @SuppressWarnings("try")
            private void pushSynonym(Node key, Node value) {
                assert key != null;
                assert value != null;
                assert !synonyms.isNew(key) : "Simulation must not change the graph";
                final Node synonym = synonyms.get(key);
                synonyms.set(key, value);
                synonymUndoOperations.add(() -> {
                    // there was a different synonym before
                    if (key.isAlive()) {
                        synonyms.set(key, synonym);
                    }
                });
            }

            private InfoElement querySynonymStamp(ValueNode value) {
                if (value.isAlive()) {
                    // query any synonyms for the current node, recursive simulation of synonyms
                    // is handled correctly as we push recursive synonyms at phis if needed
                    final ValueNode synoym = synonyms != null ? (ValueNode) synonyms.getAndGrow(value) : null;
                    if (synoym != null && synoym != value) {
                        if (synoym instanceof LogicConstantNode) {
                            LogicConstantNode lc = (LogicConstantNode) synoym;
                            return new InfoElement(lc.getValue() ? StampFactory.tautology() : StampFactory.contradiction(), simulationState.forwardEnd.getBeginNode(), null, null);
                        }
                        return new InfoElement(synoym.stamp(NodeView.DEFAULT), simulationState.forwardEnd.getBeginNode(), null, super.getInfoElements(value));
                    }
                }
                return null;
            }

            @Override
            protected InfoElement getInfoElements(ValueNode proxiedValue) {
                assert proxiedValue != null;
                final ValueNode value = GraphUtil.unproxify(proxiedValue);
                InfoElement info = querySynonymStamp(value);
                if (info == null) {
                    // no synonyms exist for the node
                    info = super.getInfoElements(proxiedValue);
                }
                return info;
            }

            private void pushPhiSynonyms(HIRBlock block) {
                assert block.getPredecessorCount() > 1 : "Simulation always starts at merge blocks";
                for (PhiNode phi : ((MergeNode) block.getBeginNode()).phis()) {
                    if (phi instanceof ValuePhiNode) {
                        if (synonyms == null) {
                            synonyms = cfg.graph.createNodeMap();
                        }
                        /*
                         * propagate phi synonyms: generally the only way we get synonyms is at phis
                         * if we duplicate or at canonicalizations that where triggered from already
                         * defined synonyms that create new canonicalizations
                         */
                        ValueNode inputAtEnd = phi.valueAt((EndNode) simulationState.getForwardEnd().getEndNode());
                        // TODO recursive simulation disabled
                        pushSynonym(phi, inputAtEnd);
                    }
                }
            }

            @Override
            @SuppressWarnings("try")
            protected void processNodes(HIRBlock block) {
                assert block.getBeginNode().isAlive() && block.getEndNode().isAlive() : block.getBeginNode() + " " + block.getEndNode();
                if (stopAtCF && block.getBeginNode() instanceof AbstractMergeNode && block.getBeginNode() != simulationStartMerge) {
                    status = SimulationAdvancement.STOP;
                }
                if (block.getBeginNode() == restartNode) {
                    status = SimulationAdvancement.CONTINUE;
                }
                if (block.getBeginNode() == stopNode) {
                    status = SimulationAdvancement.STOP;
                    return;
                }
                if (status != SimulationAdvancement.STOP) {
                    for (Node n : blockToNodes.get(block)) {
                        // We ignore states here as they bias the cost model if not scheduled
                        // late.
                        if (n.isAlive()) {
                            processSimulationNode(n);
                            if (status == SimulationAdvancement.STOP) {
                                return;
                            }
                        }
                    }
                }
            }

            private final Consumer<Node> inRegionConsumer = x -> processInRegionNode(x);

            private void processSimulationNode(Node node) {
                if (node instanceof FixedNode) {
                    assert !processed.isMarked(node) : "Cannot mark fixed nodes twice " + node;
                    assert !inRegion.isMarked(node) : "Cannot mark fixed nodes twice " + node;
                    visited.clearAll();
                    DuplicationSimulationUtil.inRegion(inRegion, processed, visited, simulationStartMerge, node, true, inRegionConsumer);
                    visited.clearAll();
                }
            }

            private void processInRegionNode(Node node) {
                for (Loop loop : loopsData.countedLoops()) {
                    // Do not process the node if it is the exit check of an inverted counted loop
                    // as doing so can destroy inverted counted loop detection if the inverted
                    // counted check is dominated by a merge
                    if (loop.counted().getLimitTest() == node) {
                        simulationState.cyclesSaved = 0;
                        status = SimulationAdvancement.STOP;
                        return;
                    }
                }
                if (node instanceof PhiNode) {
                    /*
                     * Either the phi is the region merge phi in which case it is not duplicated or
                     * another (potentially loop)phi that is anyway not in the region and we must
                     * break loop cycles.
                     */
                    return;
                }
                try {
                    /*
                     * Special case floating nodes with special fixed usages: If we find a special
                     * fixed node like an ifnode or a guardnode we try to perform a conditional
                     * elimination during the simulation. If it succeeds we collect the special
                     * improvement. If we do not succeed we must not immediately return here but
                     * still process a potential canonicalization of the inputs of the node as they
                     * are floating and will be duplicated as they have transitive fixed usages in
                     * the duplication region (namely the special fixed usages).
                     */
                    if (node instanceof AbstractBeginNode && config.findConditionalEliminations()) {
                        processAbstractBegin((AbstractBeginNode) node);
                    } else if (node instanceof IfNode && config.findConditionalEliminations()) {
                        Node c = ((IfNode) node).condition();
                        if (!processed.isMarked(c)) {
                            processPotentialCanonicalizations(c);
                            processed.mark(c);
                        }
                        processIf((IfNode) node);
                        if (status == SimulationAdvancement.OP_FOUND) {
                            // found an opportunity but we need to duplicate the split to
                            // get it
                            simulationState.registerLastOptimizableNode(node);
                        }
                    } else if (node instanceof IntegerSwitchNode && config.findConditionalEliminations()) {
                        tryFoldIntegerSwitch((IntegerSwitchNode) node);
                        if (status == SimulationAdvancement.OP_FOUND) {
                            // found an opportunity but we need to duplicate the split to
                            // get it
                            simulationState.registerLastOptimizableNode(node);
                        }
                    } else if (node instanceof FixedGuardNode) {
                        processFixedGuard((FixedGuardNode) node);
                        if (status != SimulationAdvancement.CE_OP_FOUND) {
                            // special case from above, we were not able to kill the node, try
                            // to find a canonicalization for the input
                            status = SimulationAdvancement.CONTINUE;
                            processPotentialCanonicalizations(((FixedGuardNode) node).condition());
                        }
                    } else if (node instanceof GuardNode) {
                        processGuard((GuardNode) node);
                        if (status != SimulationAdvancement.CE_OP_FOUND) {
                            // special case from above, we were not able to kill the node, try
                            // to find a canonicalization for the input
                            status = SimulationAdvancement.CONTINUE;
                            processPotentialCanonicalizations(((GuardNode) node).getCondition());
                        }
                    } else if (config.findCanonicalizations()) {
                        processPotentialCanonicalizations(node);
                    }
                } finally {
                    simulationState.processedNode(node);
                }
            }

            @SuppressWarnings({"try", "unchecked"})
            private void processPotentialCanonicalizations(Node node) {
                // check for canonicalization chances
                if (node instanceof Canonicalizable.Unary<?>) {
                    Unary<ValueNode> unary = (Unary<ValueNode>) node;
                    if (unary.getValue() != null && synonyms != null && (synonyms.getAndGrow(unary.getValue()) != null)) {
                        final ValueNode value = (ValueNode) synonyms.get(unary.getValue());
                        final Node improved = unary.canonical(canonicalizerTool, value);
                        if (improved != unary) {
                            simulationState.processCanonicalization((ValueNode) unary, improved);
                            if (improved instanceof ControlSinkNode) {
                                /*
                                 * We were able to cut off the rest of this branch. An example where
                                 * this can happen is when a field load is converted into a
                                 * deoptimization, because the receiver is found to be always null.
                                 */
                                status = SimulationAdvancement.STOP;
                                return;
                            }
                            if (improved != null) {
                                pushSynonym(node, improved);
                            }
                            status = SimulationAdvancement.OP_FOUND;
                            return;
                        }
                    }
                } else if (node instanceof Canonicalizable.Binary<?>) {
                    Binary<ValueNode> binary = (Binary<ValueNode>) node;
                    final ValueNode x = binary.getX();
                    final ValueNode y = binary.getY();
                    assert x != null;
                    assert y != null;
                    if (synonyms != null) {
                        final ValueNode xImproved = (ValueNode) synonyms.getAndGrow(x);
                        final ValueNode yImproved = (ValueNode) synonyms.getAndGrow(y);
                        if (xImproved != null || yImproved != null) {
                            final Node improved = binary.canonical(canonicalizerTool, xImproved == null ? x : xImproved, yImproved == null ? y : yImproved);
                            if (improved != binary) {
                                simulationState.processCanonicalization((ValueNode) binary, improved);
                                if (improved instanceof ControlSinkNode) {
                                    /*
                                     * We were able to cut off the rest of this branch. An example
                                     * where this can happen is when a field load is converted into
                                     * a deoptimization, because the receiver is found to be always
                                     * null.
                                     */
                                    status = SimulationAdvancement.STOP;
                                    return;
                                }
                                if (improved != null) {
                                    pushSynonym(node, improved);
                                    if (node instanceof LogicNode) {
                                        simulationState.addSavedCycles(4);
                                    }
                                }
                                status = SimulationAdvancement.OP_FOUND;
                                return;
                            }
                        }
                    }
                    if (node instanceof ShortCircuitOrNode) {
                        ShortCircuitOrNode sc = (ShortCircuitOrNode) node;
                        processPotentialCanonicalizations(sc.getX());
                        if (status == SimulationAdvancement.CONTINUE) {
                            processPotentialCanonicalizations(sc.getY());
                        }
                    }
                }
                if (node instanceof ConditionalNode) {
                    ConditionalNode c = (ConditionalNode) node;
                    if (synonyms != null) {
                        LogicNode synonym = (LogicNode) synonyms.getAndGrow(c.condition());
                        if (synonym != null) {
                            pushSynonym(c, ConditionalNode.create(synonym, c.trueValue(), c.falseValue(), NodeView.DEFAULT));
                            status = SimulationAdvancement.OP_FOUND;
                            return;
                        }
                    }
                }
                if (node instanceof BoxNode && ((BoxNode) node).getBoxingKind() == JavaKind.Boolean) {
                    /*
                     * Special case boolean box: Pure box is a canonicalizable node so if we enter
                     * this branch it survived regular canon. We check if we are boxing after PEA,
                     * i.e., PEA did not remove the node. If we are boxing a constant it can always
                     * be replaced with a read of the constant node directly.
                     */
                    BoxNode box = (BoxNode) node;
                    if (synonyms != null) {
                        ValueNode synonym = (ValueNode) synonyms.getAndGrow(box.getValue());
                        if (synonym instanceof ConstantNode) {
                            simulationState.processCanonicalization(box, null);
                            simulationState.codeSize -= 2 * node.estimatedNodeSize().value;
                            status = SimulationAdvancement.OP_FOUND;
                            return;
                        }
                    }
                }
                status = SimulationAdvancement.CONTINUE;
            }

            @Override
            protected void processConditionAnchor(ConditionAnchorNode node) {
                // we do not want to modify the graph during simulation
                ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, null, null, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                    if (result != node.isNegated()) {
                        // at least the condition anchor is gone
                        simulationState.addSavedCycles(1);
                        simulationState.registerLastOptimizableNode(node);
                        status = SimulationAdvancement.CE_OP_FOUND;
                    }
                    return true;
                }, false, safeStampInputSearch);
            }

            @Override
            protected void processGuard(GuardNode node) {
                if (!ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, null, null, node.getCondition(), (guard, result, guardedValueStamp, newInput) -> {
                    // we do not want to modify the graph during simulation
                    if (result != node.isNegated()) {
                        simulationState.incrementKilledGuards();
                        simulationState.registerLastOptimizableNode(node);
                        status = SimulationAdvancement.CE_OP_FOUND;
                    } else {
                        // deopt found, which is an "improvement"
                        simulationState.addSavedCycles(1);
                        simulationState.registerLastOptimizableNode(node);
                        status = SimulationAdvancement.OP_FOUND;
                    }
                    return true;
                }, false, safeStampInputSearch)) {
                    registerNewCondition(node.getCondition(), node.isNegated(), node);
                }
            }

            @Override
            protected void processFixedGuard(FixedGuardNode node) {
                if (!ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, null, null, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                    // we do not want to modify the graph during simulation
                    if (result != node.isNegated()) {
                        simulationState.incrementKilledGuards();
                        simulationState.registerLastOptimizableNode(node);
                        status = SimulationAdvancement.CE_OP_FOUND;
                    } else {
                        // deopt found, which is an "improvement"
                        simulationState.addSavedCycles(1);
                        simulationState.registerLastOptimizableNode(node);
                        status = SimulationAdvancement.OP_FOUND;
                    }
                    return true;
                }, false, safeStampInputSearch)) {
                    registerNewCondition(node.condition(), node.isNegated(), node);
                }

            }

            private void tryFoldIntegerSwitch(IntegerSwitchNode integerSwitchNode) {
                if (synonyms == null) {
                    return;
                }
                ValueNode integerSwitchVal = integerSwitchNode.switchValue();
                ValueNode switchValSynonym = (ValueNode) synonyms.get(integerSwitchVal);
                if (switchValSynonym != null) {
                    for (int i = 0; i < integerSwitchNode.keyCount(); i++) {
                        int key = integerSwitchNode.intKeyAt(i);
                        IntegerStamp valueStamp = (IntegerStamp) integerSwitchNode.value().stamp(NodeView.DEFAULT);
                        LogicNode keyCheck = IntegerEqualsNode.create(switchValSynonym, ConstantNode.forIntegerBits(valueStamp.getBits(), key), NodeView.DEFAULT);
                        if (keyCheck.isContradiction() || keyCheck.isTautology()) {
                            simulationState.sealSize();
                            switchKilled(integerSwitchNode);
                            status = SimulationAdvancement.STOP;
                        }
                    }
                }
            }

            @Override
            protected void processIf(IfNode node) {
                boolean successorsHaveGuardUsages = ConditionalEliminationUtil.ifSuccessorsHaveGuardUsages(node);
                boolean conditionHasUnsafeInputStamp = successorsHaveGuardUsages && ConditionalEliminationUtil.conditionHasUnsafeInputStamp(node.condition(), safeStampInputSearch);
                boolean proved = conditionHasUnsafeInputStamp ? tryProveIfConditionFromExistingCondition(node) : tryProveIfCondition(node, false);
                if (!proved && !successorsHaveGuardUsages) {
                    tryProveIfCondition(node, true);
                }

                if (status != SimulationAdvancement.CE_OP_FOUND) {
                    // try to canonicalize the condition to a constant and kill the if that way
                    Node synonym = synonyms == null ? null : synonyms.get(node.condition());
                    if (synonym != null && synonym instanceof LogicConstantNode) {
                        ifKilled(node);
                    }
                }

                if (status != SimulationAdvancement.CE_OP_FOUND) {
                    // we can never duplicate over a split except we entirely duplicate it
                    if (DuplicationUtil.uniqueNonSinkingSplit(node)) {
                        status = SimulationAdvancement.CONTINUE;
                    } else {
                        status = SimulationAdvancement.STOP;
                        if (nextBlockKillsBranch(simulationState.originalMerge, node.trueSuccessor()) || nextBlockKillsBranch(simulationState.originalMerge, node.falseSuccessor())) {
                            // if its cheap, lets do it
                            simulationState.addSavedCycles(2);
                        }
                        node.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Stopping at simulation frame because IF is not killed at if %s", node);
                    }
                }
            }

            private boolean tryProveIfCondition(IfNode node, boolean allowControlFlowDependentOtherStamp) {
                return ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, null, null, node.condition(),
                                (guard, result, guardedValueStamp, newInput) -> {
                                    return killIfFromProof(node, result);
                                }, allowControlFlowDependentOtherStamp, safeStampInputSearch);
            }

            private boolean tryProveIfConditionFromExistingCondition(IfNode node) {
                for (GuardedCondition guardedCondition : conditions) {
                    TriState result = guardedCondition.getCondition().implies(guardedCondition.isNegated(), node.condition());
                    if (result.isKnown()) {
                        return killIfFromProof(node, result.toBoolean());
                    }
                }
                return false;
            }

            private boolean killIfFromProof(IfNode node, boolean result) {
                if (result) {
                    stopNode = node.falseSuccessor();
                    restartNode = node.trueSuccessor();
                } else {
                    stopNode = node.trueSuccessor();
                    restartNode = node.falseSuccessor();
                }
                // we reduce the size by the if and condition
                simulationState.sealSize();
                ifKilled(node);
                return true;
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            private boolean nextBlockKillsBranch(MergeNode originalMerge, AbstractBeginNode abstractBeginNode) {
                FixedNode blockEnd = cfg.blockFor(abstractBeginNode).getEndNode();
                if (blockEnd instanceof IntegerSwitchNode) {
                    ValueNode val = ((IntegerSwitchNode) blockEnd).value();
                    if (originalMerge.isPhiAtMerge(val)) {
                        if (((PhiNode) val).valueAt(simulationState.end).isConstant()) {
                            return true;
                        }
                    }
                } else if (blockEnd instanceof IfNode) {
                    IfNode ifNode = (IfNode) blockEnd;
                    LogicNode l = ifNode.condition();
                    for (Node input : l.inputs()) {
                        if (originalMerge.isPhiAtMerge(l)) {
                            ValueNode phiVal = ((PhiNode) input).valueAt(simulationState.end);
                            if (l instanceof Canonicalizable.Unary<?>) {
                                if (((Canonicalizable.Unary) l).canonical(canonicalizerTool, phiVal) != l) {
                                    return true;
                                }
                            } else if (l instanceof Canonicalizable.Binary<?>) {
                                Canonicalizable.Binary<ValueNode> b = (Binary<ValueNode>) l;
                                if (b.getX() == phiVal) {
                                    if (b.canonical(canonicalizerTool, phiVal, b.getY()) != l) {
                                        return true;
                                    }
                                } else if (b.getY() == phiVal) {
                                    if (b.canonical(canonicalizerTool, b.getX(), phiVal) != l) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                return false;
            }

            private void ifKilled(IfNode node) {
                simulationState.incrementKilledBranches();
                simulationState.registerLastOptimizableNode(node);
                status = SimulationAdvancement.CE_OP_FOUND;
                // at least the jmp is gone
                simulationState.addSavedCycles(node.estimatedNodeCycles().value);

                simulationState.reduceCodeSize(node.estimatedNodeSize().value);
                if (node.condition().hasExactlyOneUsage()) {
                    // could go recursive here but we ignore that
                    simulationState.reduceCodeSize(node.condition().estimatedNodeSize().value);
                }

            }

            private void switchKilled(SwitchNode node) {
                simulationState.incrementKilledBranches();
                simulationState.registerLastOptimizableNode(node);
                status = SimulationAdvancement.CE_OP_FOUND;
                // at least the jmp is gone
                simulationState.addSavedCycles(node.estimatedNodeCycles().value);
                simulationState.reduceCodeSize(node.estimatedNodeSize().value);
            }

        }

        public SimulationEndInfo[] simulatedImprovements() {
            if (improvements == null) {
                return DuplicationSimulationUtil.EMPTY_IMPROVEMENTS;
            }
            final HIRBlock[] blocks = cfg.getBlocks();
            SimulationEndInfo[] res = new SimulationEndInfo[simulationsPerformed];
            int size = 0;
            for (int i = 0; i < blocks.length; i++) {
                SimulationEndInfo sim = improvements.get(blocks[i]);
                if (sim != null) {
                    res[size++] = sim;
                }
            }
            return res;
        }

    }

}
