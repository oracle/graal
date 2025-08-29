/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ListIterator;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FloatingGuardedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.UnaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueNodeInterface;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph.RecursiveVisitor;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.FloatingAccessNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.graph.ScheduledNodeIterator;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.TriState;

/**
 * This phase lowers {@link FloatingReadNode FloatingReadNodes} into corresponding fixed reads.
 * After this operation, there are no longer any nodes in the graph that have to remain below a
 * control flow split to be considered "safe". Therefore, this phase subsequently removes all
 * {@link PiNode} instances from the graph. Then it runs a raw conditional elimination
 * {@link RawConditionalEliminationVisitor} that aggressively uses stamps for values based on
 * control flow. For every if node, the logic node is inspected and a stamp is derived for the true
 * and false branch. Stamps for a value are combined based on all previous knowledge about that
 * value. For merge points, a union of the stamps of a value is constructed. When a value is used,
 * the corresponding best derived stamp is provided to the canonicalizer.
 */
public class FixReadsPhase extends BasePhase<CoreProviders> {

    private static final CounterKey counterStampsRegistered = DebugContext.counter("FixReads_StampsRegistered");
    private static final CounterKey counterBetterMergedStamps = DebugContext.counter("FixReads_BetterMergedStamp");

    protected final boolean replaceInputsWithConstants;
    protected final BasePhase<? super CoreProviders> schedulePhase;

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }

    private static class FixReadsClosure extends ScheduledNodeIterator {

        /**
         * Bitmap that is used to schedule nodes for inferring a new stamp when they are visited.
         * After removing the pi nodes from the graph, the stamp information injected by the pi
         * nodes is cleared this way.
         */
        private final NodeBitMap inferStampBitmap;

        FixReadsClosure(StructuredGraph graph, ScheduleResult schedule) {
            super(schedule);
            inferStampBitmap = graph.createNodeBitMap();
        }

        @Override
        protected void processNode(Node node, HIRBlock block, ListIterator<Node> iter) {
            if (inferStampBitmap.isMarked(node) && node instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) node;
                if (valueNode.inferStamp()) {
                    for (Node n : valueNode.usages()) {
                        inferStampBitmap.mark(n);
                    }
                }
            }
            if (node instanceof AbstractMergeNode) {
                AbstractMergeNode mergeNode = (AbstractMergeNode) node;
                for (MemoryPhiNode memoryPhi : mergeNode.memoryPhis().snapshot()) {
                    // Memory phi nodes are no longer necessary at this point.
                    memoryPhi.replaceAtUsages(null);
                    memoryPhi.safeDelete();
                }
            } else if (node instanceof FloatingAccessNode) {
                FloatingAccessNode floatingAccessNode = (FloatingAccessNode) node;
                floatingAccessNode.setLastLocationAccess(null);
                GuardingNode guard = floatingAccessNode.getGuard();
                if (guard != null) {
                    floatingAccessNode.setGuard(null);
                    GraphUtil.tryKillUnused(guard.asNode());
                }
                FixedAccessNode fixedAccess = floatingAccessNode.asFixedNode();
                replaceCurrent(fixedAccess);
            } else if (node instanceof PiNode piNode) {
                // incompatible stamps can result from unreachable code with empty stamps missed by
                // control flow optimizations
                if (piNode.stamp(NodeView.DEFAULT).isCompatible(piNode.getOriginalNode().stamp(NodeView.DEFAULT))) {
                    // Pi nodes are no longer necessary at this point. Make sure to infer stamps
                    // for all usages to clear out the stamp information added by the pi node.
                    for (Node n : piNode.usages()) {
                        inferStampBitmap.mark(n);
                    }
                    piNode.replaceAndDelete(piNode.getOriginalNode());
                }
            } else if (node instanceof MemoryAccess) {
                MemoryAccess memoryAccess = (MemoryAccess) node;
                memoryAccess.setLastLocationAccess(null);
            }
        }

    }

    public static class RawConditionalEliminationVisitor implements RecursiveVisitor<Integer> {

        protected final NodeMap<StampElement> stampMap;
        protected final NodeStack undoOperations;
        private final ScheduleResult schedule;
        private final StructuredGraph graph;
        private final MetaAccessProvider metaAccess;
        private final boolean replaceConstantInputs;
        private final BlockMap<Integer> blockActionStart;
        private final EconomicMap<MergeNode, EconomicMap<ValueNode, Stamp>> endMaps;
        private final DebugContext debug;
        private final RawCanonicalizerTool rawCanonicalizerTool;
        private final EconomicMap<Node, HIRBlock> nodeToBlockMap;
        protected EconomicMap<AbstractBeginNode, Stamp> successorStampCache;

        private class RawCanonicalizerTool extends CoreProvidersDelegate implements NodeView, CanonicalizerTool {

            RawCanonicalizerTool(CoreProviders providers) {
                super(providers);
            }

            @Override
            public Assumptions getAssumptions() {
                return graph.getAssumptions();
            }

            @Override
            public boolean canonicalizeReads() {
                return false;
            }

            @Override
            public boolean allUsagesAvailable() {
                return true;
            }

            @Override
            public Integer smallestCompareWidth() {
                return null;
            }

            @Override
            public OptionValues getOptions() {
                return graph.getOptions();
            }

            @Override
            public Stamp stamp(ValueNode node) {
                return getBestStamp(node);
            }

            @Override
            public boolean divisionOverflowIsJVMSCompliant() {
                return false;
            }
        }

        public RawConditionalEliminationVisitor(StructuredGraph graph, ScheduleResult schedule, MetaAccessProvider metaAccess, boolean replaceInputsWithConstants) {
            this.graph = graph;
            this.debug = graph.getDebug();
            this.schedule = schedule;
            this.metaAccess = metaAccess;
            this.rawCanonicalizerTool = new RawCanonicalizerTool(new Providers(metaAccess, null, null, null, null, null, null, null, null, null, null, null, null, null));
            blockActionStart = new BlockMap<>(schedule.getCFG());
            endMaps = EconomicMap.create(Equivalence.IDENTITY);
            stampMap = graph.createNodeMap();
            undoOperations = new NodeStack();
            replaceConstantInputs = replaceInputsWithConstants && GraalOptions.ReplaceInputsWithConstantsBasedOnStamps.getValue(graph.getOptions());
            nodeToBlockMap = EconomicMap.create();
        }

        protected void replaceInput(Position p, Node oldInput, Node newConstantInput) {
            p.set(oldInput, newConstantInput);
        }

        protected int replaceConstantInputs(Node node) {
            int replacements = 0;
            // Check if we can replace any of the inputs with a constant.
            for (Position p : node.inputPositions()) {
                Node input = p.get(node);
                if (p.getInputType() == InputType.Value) {
                    if (input instanceof ValueNode) {
                        ValueNode valueNode = (ValueNode) input;
                        if (valueNode instanceof ConstantNode) {
                            // Input already is a constant.
                        } else {
                            Stamp bestStamp = getBestStamp(valueNode);
                            Constant constant = bestStamp.asConstant();
                            if (constant != null) {
                                if (bestStamp instanceof FloatStamp) {
                                    FloatStamp floatStamp = (FloatStamp) bestStamp;
                                    if (floatStamp.contains(0.0d)) {
                                        // Could also be -0.0d.
                                        continue;
                                    }
                                }
                                ConstantNode stampConstant = ConstantNode.forConstant(bestStamp, constant, metaAccess, graph);
                                assert stampConstant.stamp(NodeView.DEFAULT).isCompatible(valueNode.stamp(NodeView.DEFAULT));
                                replaceInput(p, node, stampConstant);
                                graph.getOptimizationLog().report(FixReadsPhase.class, "ConstantInputReplacement", node);
                                replacements++;
                            }
                        }
                    }
                }
            }
            return replacements;
        }

        private static boolean nonNullAndDominates(HIRBlock a, HIRBlock b) {
            if (a == null) {
                return false;
            } else {
                return a.dominates(b);
            }
        }

        protected void processNode(Node node, HIRBlock b, NodePredicate nodePredicate) {
            assert node.isAlive();

            if (replaceConstantInputs) {
                replaceConstantInputs(node);
            }

            if (node.getNodeClass().valueNumberable()) {
                Node dominatingDuplicate = graph.findDuplicate(node, nodePredicate);
                if (dominatingDuplicate != null) {
                    node.replaceAndDelete(dominatingDuplicate);
                    return;
                }
            }

            if (node instanceof MergeNode) {
                registerCombinedStamps((MergeNode) node);
            }

            if (node instanceof SwitchNode switchNode) {
                /*
                 * Since later in this phase we will be visiting all control split successors the
                 * operation of computing successor stamps for switch nodes can be quite costly.
                 * Thus, we already compute and cache all eagerly here.
                 */
                if (successorStampCache == null) {
                    successorStampCache = EconomicMap.create();
                }
                switchNode.getAllSuccessorValueStamps(successorStampCache);
            }

            if (node instanceof AbstractBeginNode) {
                processAbstractBegin((AbstractBeginNode) node);
            } else if (node instanceof IfNode) {
                processIf((IfNode) node);
            } else if (node instanceof IntegerSwitchNode) {
                processIntegerSwitch((IntegerSwitchNode) node);
            } else if (node instanceof BinaryNode) {
                processBinary((BinaryNode) node, b, nodePredicate);
            } else if (node instanceof ConditionalNode) {
                processConditional((ConditionalNode) node);
            } else if (node instanceof UnaryNode) {
                processUnary((UnaryNode) node, b, nodePredicate);
            } else if (node instanceof EndNode) {
                processEnd((EndNode) node);
            }

            if (node.getNodeClass().valueNumberable() && node.isAlive()) {
                nodeToBlockMap.put(node, b);
            }
        }

        protected void registerCombinedStamps(MergeNode node) {
            EconomicMap<ValueNode, Stamp> endMap = endMaps.get(node);
            MapCursor<ValueNode, Stamp> entries = endMap.getEntries();
            while (entries.advance()) {
                ValueNode value = entries.getKey();
                if (value.isDeleted()) {
                    // nodes from this map can be deleted when a loop dies
                    continue;
                }
                if (registerNewValueStamp(value, entries.getValue())) {
                    counterBetterMergedStamps.increment(debug);
                }
            }
        }

        /**
         * Maximum depth of dominators walked during the creation of better stamps at end nodes. Any
         * larger number can lead to combinatorial explosion and long compilation times.
         */
        private static final int BETTER_END_STAMPS_MAX_DOM_DEPTH = 128;

        protected void processEnd(EndNode node) {
            AbstractMergeNode abstractMerge = node.merge();
            if (abstractMerge instanceof MergeNode) {
                MergeNode merge = (MergeNode) abstractMerge;

                NodeMap<HIRBlock> blockToNodeMap = this.schedule.getNodeToBlockMap();
                HIRBlock mergeBlock = blockToNodeMap.get(merge);
                HIRBlock mergeBlockDominator = mergeBlock.getDominator();
                HIRBlock currentBlock = blockToNodeMap.get(node);

                EconomicMap<ValueNode, Stamp> currentEndMap = endMaps.get(merge);

                if (currentEndMap == null || !currentEndMap.isEmpty()) {

                    EconomicMap<ValueNode, Stamp> endMap = EconomicMap.create(Equivalence.IDENTITY);

                    // Process phis
                    for (ValuePhiNode phi : merge.valuePhis()) {
                        if (currentEndMap == null || currentEndMap.containsKey(phi)) {
                            ValueNode valueAt = phi.valueAt(node);
                            Stamp bestStamp = getBestStamp(valueAt);

                            if (currentEndMap != null) {
                                bestStamp = bestStamp.meet(currentEndMap.get(phi));
                            }

                            if (!bestStamp.equals(phi.stamp(NodeView.DEFAULT))) {
                                endMap.put(phi, bestStamp);
                            }
                        }
                    }

                    int distance = 0;
                    int lastMark = undoOperations.size();
                    while (currentBlock != mergeBlockDominator) {
                        if (distance++ > BETTER_END_STAMPS_MAX_DOM_DEPTH) {
                            break;
                        }
                        int mark = blockActionStart.get(currentBlock);
                        for (int i = lastMark - 1; i >= mark; --i) {
                            ValueNode nodeWithNewStamp = (ValueNode) undoOperations.get(i);

                            if (nodeWithNewStamp.isDeleted() || nodeWithNewStamp instanceof LogicNode || nodeWithNewStamp instanceof ConstantNode || blockToNodeMap.isNew(nodeWithNewStamp)) {
                                continue;
                            }

                            HIRBlock block = getBlock(nodeWithNewStamp, blockToNodeMap);
                            if (block == null || block.getId() <= mergeBlockDominator.getId()) {
                                // Node with new stamp in path to the merge block dominator and that
                                // at the same time was defined at least in the merge block
                                // dominator (i.e., therefore can be used after the merge.)

                                Stamp bestStamp = getBestStamp(nodeWithNewStamp);
                                assert bestStamp != null;

                                if (currentEndMap != null) {
                                    Stamp otherEndsStamp = currentEndMap.get(nodeWithNewStamp);
                                    if (otherEndsStamp == null) {
                                        // No stamp registered in one of the previously processed
                                        // ends => skip.
                                        continue;
                                    }
                                    bestStamp = bestStamp.meet(otherEndsStamp);
                                }

                                if (nodeWithNewStamp.stamp(NodeView.DEFAULT).tryImproveWith(bestStamp) == null) {
                                    // No point in registering the stamp.
                                } else {
                                    endMap.put(nodeWithNewStamp, bestStamp);
                                }
                            }
                        }
                        currentBlock = currentBlock.getDominator();
                    }

                    endMaps.put(merge, endMap);
                }
            }
        }

        private static HIRBlock getBlock(ValueNode node, NodeMap<HIRBlock> blockToNodeMap) {
            if (node instanceof PhiNode) {
                PhiNode phiNode = (PhiNode) node;
                return blockToNodeMap.get(phiNode.merge());
            }
            return blockToNodeMap.get(node);
        }

        protected void processUnary(UnaryNode node, HIRBlock block, NodePredicate gvnPredicate) {
            ValueNode value = node.getValue();
            Stamp bestStamp = getBestStamp(value);
            Stamp newStamp = node.foldStamp(bestStamp);
            if (!checkReplaceWithConstant(newStamp, node)) {
                if (!bestStamp.equals(value.stamp(NodeView.DEFAULT))) {
                    ValueNode newNode = node.canonical(rawCanonicalizerTool);
                    if (newNode != node) {
                        // Canonicalization successfully triggered.
                        if (newNode != null && !newNode.isAlive()) {
                            newNode = addHelper(newNode, block, gvnPredicate);
                        }
                        node.replaceAndDelete(newNode);
                        GraphUtil.tryKillUnused(value);
                        return;
                    }
                }
                registerNewValueStamp(node, newStamp);
            }
        }

        private ValueNode addHelper(ValueNode newNode, HIRBlock block, NodePredicate gvnPredicate) {
            Graph.Mark m = graph.getMark();
            ValueNode result = graph.addOrUniqueWithInputs(newNode, gvnPredicate);
            for (Node n : graph.getNewNodes(m)) {
                nodeToBlockMap.put(n, block);
            }
            return result;
        }

        protected boolean checkReplaceWithConstant(Stamp newStamp, ValueNode node) {
            Constant constant = newStamp.asConstant();
            if (constant != null && !(node instanceof ConstantNode)) {
                ConstantNode stampConstant = ConstantNode.forConstant(newStamp, constant, metaAccess, graph);
                node.replaceAtUsages(stampConstant, InputType.Value);
                graph.getOptimizationLog().report(FixReadsPhase.class, "ConstantReplacement", node);
                GraphUtil.tryKillUnused(node);
                return true;
            }
            return false;
        }

        protected void processBinary(BinaryNode node, HIRBlock b, NodePredicate nodePredicate) {

            ValueNode x = node.getX();
            ValueNode y = node.getY();

            Stamp xStamp = getBestStamp(x);
            Stamp yStamp = getBestStamp(y);
            Stamp newStamp = node.foldStamp(xStamp, yStamp);
            if (!checkReplaceWithConstant(newStamp, node)) {

                if (!xStamp.equals(x.stamp(NodeView.DEFAULT)) || !yStamp.equals(y.stamp(NodeView.DEFAULT))) {
                    // At least one of the inputs has an improved stamp => attempt to canonicalize
                    // based on that improvement.
                    ValueNode newNode = node.canonical(rawCanonicalizerTool);
                    if (newNode != node) {
                        // Canonicalization successfully triggered.
                        if (newNode != null && !newNode.isAlive()) {
                            newNode = addHelper(newNode, b, nodePredicate);
                        }
                        node.replaceAndDelete(newNode);
                        GraphUtil.tryKillUnused(x);
                        GraphUtil.tryKillUnused(y);
                        graph.getOptimizationLog().report(FixReadsPhase.class, "BinaryCanonicalization", node);
                        return;
                    }
                }

                registerNewValueStamp(node, newStamp);
            }
        }

        protected void processIntegerSwitch(IntegerSwitchNode node) {
            Stamp bestStamp = getBestStamp(node.value());
            if (node.tryRemoveUnreachableKeys(null, bestStamp, successorStampCache)) {
                graph.getOptimizationLog().report(FixReadsPhase.class, "SwitchCanonicalization", node);
            }
        }

        protected void processIf(IfNode node) {
            TriState result = tryProveCondition(node.condition());
            if (result != TriState.UNKNOWN) {
                boolean isTrue = (result == TriState.TRUE);
                // Don't kill the other branch immediately, see
                // `ConditionalEliminationPhase.processGuard`.
                node.setCondition(LogicConstantNode.forBoolean(isTrue, node.graph()));
                graph.getOptimizationLog().report(FixReadsPhase.class, "IfElimination", node);
            }
        }

        protected void processConditional(ConditionalNode node) {
            TriState result = tryProveCondition(node.condition());
            if (result != TriState.UNKNOWN) {
                boolean isTrue = (result == TriState.TRUE);
                node.replaceAndDelete(isTrue ? node.trueValue() : node.falseValue());
                graph.getOptimizationLog().report(FixReadsPhase.class, "ConditionalElimination", node);
            } else {
                Stamp trueStamp = getBestStamp(node.trueValue());
                Stamp falseStamp = getBestStamp(node.falseValue());
                registerNewStamp(node, trueStamp.meet(falseStamp));
            }
        }

        protected TriState tryProveCondition(LogicNode condition) {
            Stamp conditionStamp = this.getBestStamp(condition);
            if (conditionStamp == StampFactory.tautology()) {
                return TriState.TRUE;
            } else if (conditionStamp == StampFactory.contradiction()) {
                return TriState.FALSE;
            }

            if (condition instanceof UnaryOpLogicNode) {
                UnaryOpLogicNode unaryOpLogicNode = (UnaryOpLogicNode) condition;
                return unaryOpLogicNode.tryFold(this.getBestStamp(unaryOpLogicNode.getValue()));
            } else if (condition instanceof BinaryOpLogicNode) {
                BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) condition;
                return binaryOpLogicNode.tryFold(this.getBestStamp(binaryOpLogicNode.getX()), this.getBestStamp(binaryOpLogicNode.getY()));
            }

            return TriState.UNKNOWN;
        }

        protected void processAbstractBegin(AbstractBeginNode beginNode) {
            Node predecessor = beginNode.predecessor();
            if (predecessor instanceof IfNode) {
                IfNode ifNode = (IfNode) predecessor;
                boolean negated = (ifNode.falseSuccessor() == beginNode);
                LogicNode condition = ifNode.condition();
                registerNewCondition(condition, negated);
            } else if (predecessor instanceof IntegerSwitchNode) {
                IntegerSwitchNode integerSwitchNode = (IntegerSwitchNode) predecessor;
                registerIntegerSwitch(beginNode, integerSwitchNode);
            }
        }

        private void registerIntegerSwitch(AbstractBeginNode beginNode, IntegerSwitchNode integerSwitchNode) {
            if (successorStampCache == null) {
                successorStampCache = EconomicMap.create();
            }
            registerNewValueStamp(integerSwitchNode.value(), integerSwitchNode.getValueStampForSuccessor(beginNode, successorStampCache));
        }

        protected void registerNewCondition(LogicNode condition, boolean negated) {
            if (condition instanceof UnaryOpLogicNode) {
                UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) condition;
                ValueNode value = unaryLogicNode.getValue();
                Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(negated);
                registerNewValueStamp(value, newStamp);
            } else if (condition instanceof BinaryOpLogicNode) {
                BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) condition;
                ValueNode x = binaryOpLogicNode.getX();
                ValueNode y = binaryOpLogicNode.getY();
                Stamp xStamp = getBestStamp(x);
                Stamp yStamp = getBestStamp(y);
                registerNewValueStamp(x, binaryOpLogicNode.getSucceedingStampForX(negated, xStamp, yStamp));
                registerNewValueStamp(y, binaryOpLogicNode.getSucceedingStampForY(negated, xStamp, yStamp));
            }
            registerCondition(condition, negated);
        }

        protected void registerCondition(LogicNode condition, boolean negated) {
            registerNewStamp(condition, negated ? StampFactory.contradiction() : StampFactory.tautology());
        }

        protected boolean registerNewValueStamp(ValueNode value, Stamp newStamp) {
            if (newStamp != null && !value.isConstant()) {
                Stamp currentStamp = getBestStamp(value);
                Stamp betterStamp = currentStamp.tryImproveWith(newStamp);
                if (betterStamp != null) {
                    registerNewStamp(value, betterStamp);
                    return true;
                }
            }
            return false;
        }

        protected void registerNewStamp(ValueNode value, Stamp newStamp) {
            counterStampsRegistered.increment(debug);
            debug.log("\t Saving stamp for node %s stamp %s", value, newStamp);
            ValueNode originalNode = value;
            stampMap.setAndGrow(originalNode, new StampElement(newStamp, stampMap.getAndGrow(originalNode)));
            undoOperations.push(originalNode);
        }

        protected Stamp getBestStamp(ValueNode value) {
            ValueNode originalNode = value;
            if (!value.isAlive()) {
                return value.stamp(NodeView.DEFAULT);
            }

            StampElement currentStamp = stampMap.getAndGrow(originalNode);
            if (currentStamp == null) {
                return value.stamp(NodeView.DEFAULT);
            }
            return currentStamp.getStamp();
        }

        @Override
        public Integer enter(HIRBlock b) {
            int mark = undoOperations.size();
            blockActionStart.put(b, mark);
            NodePredicate nodePredicate = n -> nonNullAndDominates(nodeToBlockMap.get(n), b);
            for (Node n : schedule.getBlockToNodesMap().get(b)) {
                if (n.isAlive()) {
                    processNode(n, b, nodePredicate);
                }
            }
            return mark;
        }

        @Override
        public void exit(HIRBlock b, Integer state) {
            int mark = state;
            while (undoOperations.size() > mark) {
                Node node = undoOperations.pop();
                if (node.isAlive()) {
                    stampMap.set(node, stampMap.get(node).getParent());
                }
            }
        }

    }

    public FixReadsPhase(boolean replaceInputsWithConstants, BasePhase<? super CoreProviders> schedulePhase) {
        this.replaceInputsWithConstants = replaceInputsWithConstants;
        this.schedulePhase = schedulePhase;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.ifApplied(this, StageFlag.FIXED_READS, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.LOW_TIER_LOWERING, graphState),
                        NotApplicable.when(graphState.getGuardsStage().areFrameStatesAtSideEffects(), "This phase must run after FSA"));
    }

    @Override
    public boolean mustApply(GraphState graphState) {
        return graphState.requiresFutureStage(StageFlag.FIXED_READS);
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        schedulePhase.apply(graph, context);
        ScheduleResult schedule = graph.getLastSchedule();
        FixReadsClosure fixReadsClosure = new FixReadsClosure(graph, schedule);
        for (HIRBlock block : schedule.getCFG().getBlocks()) {
            fixReadsClosure.processNodes(block);
        }

        if (GraalOptions.RawConditionalElimination.getValue(graph.getOptions()) && GraalOptions.EnableFixReadsConditionalElimination.getValue(graph.getOptions())) {
            schedule.getCFG().visitDominatorTree(createVisitor(graph, schedule, context), false);
        }

        assert verifyPiRemovalInvariants(graph);
    }

    /**
     * Run verifications to test invariants that need to hold after removing {@link PiNode} from a
     * graph.
     */
    public static boolean verifyPiRemovalInvariants(StructuredGraph graph) {
        if (Assertions.assertionsEnabled()) {
            for (Node n : graph.getNodes()) {
                final boolean isFloatingGuardedNode = n instanceof FloatingGuardedNode;
                if (isFloatingGuardedNode) {
                    // floating guarded nodes without a guard are "universally" true meaning they
                    // can be executed everywhere
                    final GuardingNode guard = ((FloatingGuardedNode) n).getGuard();
                    assert verifyOnlyFixedGuards(guard);
                }
            }
        }
        return true;
    }

    private static boolean verifyOnlyFixedGuards(ValueNodeInterface guardingRoot) {
        final boolean isUniversallyTrue = guardingRoot == null;
        if (isUniversallyTrue) {
            return true;
        }
        final StructuredGraph graph = guardingRoot.asNode().graph();
        NodeBitMap visited = graph.createNodeBitMap();
        Deque<ValueNodeInterface> toVisit = new ArrayDeque<>();
        toVisit.add(guardingRoot);

        while (!toVisit.isEmpty()) {
            ValueNodeInterface currentGuard = toVisit.pop();
            if (visited.isMarked(currentGuard.asNode())) {
                continue;
            }
            visited.mark(currentGuard.asNode());
            if (currentGuard instanceof MultiGuardNode mg) {
                toVisit.addAll(mg.getGuards());
            } else {
                assert currentGuard instanceof FixedNode : Assertions.errorMessage(
                                "Should not have floating guarded nodes without fixed guards left after removing pis, they could float uncontrolled now", currentGuard);
            }
        }
        return true;
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.FIXED_READS);
        graphState.removeRequirementToStage(StageFlag.FIXED_READS);
    }

    protected ControlFlowGraph.RecursiveVisitor<?> createVisitor(StructuredGraph graph, ScheduleResult schedule, CoreProviders context) {
        return new RawConditionalEliminationVisitor(graph, schedule, context.getMetaAccess(), replaceInputsWithConstants);
    }

    protected static final class StampElement {
        private final Stamp stamp;
        private final StampElement parent;

        public StampElement(Stamp stamp, StampElement parent) {
            this.stamp = stamp;
            this.parent = parent;
        }

        public StampElement getParent() {
            return parent;
        }

        public Stamp getStamp() {
            return stamp;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append(stamp);
            if (this.parent != null) {
                result.append(" (");
                result.append(this.parent);
                result.append(")");
            }
            return result.toString();
        }
    }

    public BasePhase<? super CoreProviders> getSchedulePhase() {
        return schedulePhase;
    }
}
