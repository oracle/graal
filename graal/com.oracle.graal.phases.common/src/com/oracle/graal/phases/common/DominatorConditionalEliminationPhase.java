/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.phases.common;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.UnreachedCode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.oracle.graal.compiler.common.cfg.AbstractControlFlowGraph;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.And;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Or;
import com.oracle.graal.compiler.common.type.IntegerStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeMap;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.BeginNode;
import com.oracle.graal.nodes.BinaryOpLogicNode;
import com.oracle.graal.nodes.ConditionAnchorNode;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.DeoptimizingGuard;
import com.oracle.graal.nodes.FixedGuardNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.GuardNode;
import com.oracle.graal.nodes.GuardProxyNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.LoopExitNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ShortCircuitOrNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.UnaryOpLogicNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AndNode;
import com.oracle.graal.nodes.calc.BinaryArithmeticNode;
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.calc.IntegerEqualsNode;
import com.oracle.graal.nodes.calc.UnaryNode;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.extended.IntegerSwitchNode;
import com.oracle.graal.nodes.extended.LoadHubNode;
import com.oracle.graal.nodes.extended.ValueAnchorNode;
import com.oracle.graal.nodes.java.CheckCastNode;
import com.oracle.graal.nodes.java.TypeSwitchNode;
import com.oracle.graal.nodes.spi.NodeWithState;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.common.LoweringPhase.Frame;
import com.oracle.graal.phases.schedule.SchedulePhase;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.TriState;

public class DominatorConditionalEliminationPhase extends Phase {

    private static final DebugMetric metricStampsRegistered = Debug.metric("StampsRegistered");
    private static final DebugMetric metricStampsFound = Debug.metric("StampsFound");
    private final boolean fullSchedule;

    public DominatorConditionalEliminationPhase(boolean fullSchedule) {
        this.fullSchedule = fullSchedule;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph) {
        try (Debug.Scope s = Debug.scope("DominatorConditionalElimination")) {
            Function<Block, Iterable<? extends Node>> blockToNodes;
            Function<Node, Block> nodeToBlock;
            Block startBlock;

            if (fullSchedule) {
                SchedulePhase schedule = new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST);
                schedule.apply(graph);
                ControlFlowGraph cfg = graph.getLastSchedule().getCFG();
                cfg.computePostdominators();
                blockToNodes = b -> graph.getLastSchedule().getBlockToNodesMap().get(b);
                nodeToBlock = n -> graph.getLastSchedule().getNodeToBlockMap().get(n);
                startBlock = cfg.getStartBlock();
            } else {
                ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, true);
                BlockMap<List<FixedNode>> nodes = new BlockMap<>(cfg);
                for (Block b : cfg.getBlocks()) {
                    ArrayList<FixedNode> curNodes = new ArrayList<>();
                    for (FixedNode node : b.getNodes()) {
                        if (node instanceof AbstractBeginNode || node instanceof FixedGuardNode || node instanceof CheckCastNode || node instanceof ConditionAnchorNode || node instanceof IfNode) {
                            curNodes.add(node);
                        }
                    }
                    nodes.put(b, curNodes);
                }
                blockToNodes = b -> nodes.get(b);
                nodeToBlock = n -> cfg.blockFor(n);
                startBlock = cfg.getStartBlock();
            }
            new Instance(graph, blockToNodes, nodeToBlock).processBlock(startBlock);
        }
    }

    public static class Instance {
        protected NodeMap<Info> map;
        protected Deque<LoopExitNode> loopExits;
        protected final Function<Block, Iterable<? extends Node>> blockToNodes;
        protected final Function<Node, Block> nodeToBlock;
        /**
         * Tests which may be eliminated because post dominating tests to prove a broader condition.
         */
        private Deque<PendingTest> pendingTests;

        public Instance(StructuredGraph graph, Function<Block, Iterable<? extends Node>> blockToNodes,
                        Function<Node, Block> nodeToBlock) {
            map = graph.createNodeMap();
            loopExits = new ArrayDeque<>();
            this.blockToNodes = blockToNodes;
            this.nodeToBlock = nodeToBlock;
            pendingTests = new ArrayDeque<>();
        }

        public void processBlock(Block startBlock) {
            LoweringPhase.processBlock(new InstanceFrame(startBlock, null));
        }

        public class InstanceFrame extends LoweringPhase.Frame<InstanceFrame> {
            protected List<Runnable> undoOperations = new ArrayList<>();

            public InstanceFrame(Block block, InstanceFrame parent) {
                super(block, parent);
            }

            @Override
            public Frame<?> enter(Block b) {
                return new InstanceFrame(b, this);
            }

            @Override
            public void postprocess() {
                Debug.log("[Post Processing block %s]", block);
                undoOperations.forEach(x -> x.run());
            }

            protected void processConditionAnchor(ConditionAnchorNode node) {
                tryProveCondition(node.condition(), (guard, result) -> {
                    if (result != node.isNegated()) {
                        node.replaceAtUsages(guard);
                        GraphUtil.unlinkFixedNode(node);
                        GraphUtil.killWithUnusedFloatingInputs(node);
                    } else {
                        ValueAnchorNode valueAnchor = node.graph().add(new ValueAnchorNode(null));
                        node.replaceAtUsages(valueAnchor);
                        node.graph().replaceFixedWithFixed(node, valueAnchor);
                    }
                    return true;
                });
            }

            protected void processGuard(GuardNode node) {
                if (!tryProveGuardCondition(node, node.getCondition(), (guard, result) -> {
                    if (result != node.isNegated()) {
                        node.replaceAndDelete(guard);
                    } else {
                        DeoptimizeNode deopt = node.graph().add(new DeoptimizeNode(node.getAction(), node.getReason(), node.getSpeculation()));
                        AbstractBeginNode beginNode = (AbstractBeginNode) node.getAnchor();
                        FixedNode next = beginNode.next();
                        beginNode.setNext(deopt);
                        GraphUtil.killCFG(next);
                    }
                    return true;
                })) {
                    registerNewCondition(node.getCondition(), node.isNegated(), node);
                }
            }

            protected void processFixedGuard(FixedGuardNode node) {
                if (!tryProveGuardCondition(node, node.condition(), (guard, result) -> {
                    if (result != node.isNegated()) {
                        node.replaceAtUsages(guard);
                        GraphUtil.unlinkFixedNode(node);
                        GraphUtil.killWithUnusedFloatingInputs(node);
                    } else {
                        DeoptimizeNode deopt = node.graph().add(new DeoptimizeNode(node.getAction(), node.getReason(), node.getSpeculation()));
                        deopt.setStateBefore(node.stateBefore());
                        node.replaceAtPredecessor(deopt);
                        GraphUtil.killCFG(node);
                    }
                    return true;
                })) {
                    registerNewCondition(node.condition(), node.isNegated(), node);
                }
            }

            protected void processIf(IfNode node) {
                tryProveCondition(node.condition(), (guard, result) -> {
                    AbstractBeginNode survivingSuccessor = node.getSuccessor(result);
                    survivingSuccessor.replaceAtUsages(InputType.Guard, guard);
                    survivingSuccessor.replaceAtPredecessor(null);
                    node.replaceAtPredecessor(survivingSuccessor);
                    GraphUtil.killCFG(node);
                    if (survivingSuccessor instanceof BeginNode) {
                        undoOperations.add(() -> {
                            if (survivingSuccessor.isAlive()) {
                                ((BeginNode) survivingSuccessor).trySimplify();
                            }
                        });
                    }
                    return true;
                });
            }

            protected void processCheckCast(CheckCastNode node) {
                for (InfoElement infoElement : getInfoElements(node.object())) {
                    TriState result = node.tryFold(infoElement.getStamp());
                    if (result.isKnown()) {
                        if (rewireGuards(infoElement.getGuard(), result.toBoolean(), (guard, checkCastResult) -> {
                            if (checkCastResult) {
                                PiNode piNode = node.graph().unique(new PiNode(node.object(), node.stamp(), guard));
                                GraphUtil.unlinkFixedNode(node);
                                node.replaceAtUsagesAndDelete(piNode);
                            } else {
                                DeoptimizeNode deopt = node.graph().add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                                node.replaceAtPredecessor(deopt);
                                GraphUtil.killCFG(node);
                            }
                            return true;
                        })) {
                            return;
                        }
                    }
                }
            }

            @Override
            public void preprocess() {
                Debug.log("[Pre Processing block %s]", block);
                AbstractBeginNode beginNode = block.getBeginNode();
                if (beginNode instanceof LoopExitNode && beginNode.isAlive()) {
                    LoopExitNode loopExitNode = (LoopExitNode) beginNode;
                    Instance.this.loopExits.push(loopExitNode);
                    undoOperations.add(() -> loopExits.pop());
                } else if (block.getDominator() != null && (block.getDominator().getLoopDepth() > block.getLoopDepth() ||
                                (block.getDominator().getLoopDepth() == block.getLoopDepth() && block.getDominator().getLoop() != block.getLoop()))) {
                    // We are exiting the loop, but there is not a single loop exit block along our
                    // dominator tree (e.g., we are a merge of two loop exits).
                    final NodeMap<Info> oldMap = map;
                    final Deque<LoopExitNode> oldLoopExits = loopExits;
                    map = map.graph().createNodeMap();
                    loopExits = new ArrayDeque<>();
                    undoOperations.add(() -> {
                        map = oldMap;
                        loopExits = oldLoopExits;
                    });
                }

                // For now conservatively collect guards only within the same block.
                pendingTests.clear();
                for (Node n : blockToNodes.apply(block)) {
                    if (n.isAlive()) {
                        processNode(n);
                    }
                }
            }

            protected void processNode(Node node) {
                if (node instanceof NodeWithState && !(node instanceof GuardingNode)) {
                    pendingTests.clear();
                }
                if (node instanceof AbstractBeginNode) {
                    processAbstractBegin((AbstractBeginNode) node);
                } else if (node instanceof FixedGuardNode) {
                    processFixedGuard((FixedGuardNode) node);
                } else if (node instanceof GuardNode) {
                    processGuard((GuardNode) node);
                } else if (node instanceof CheckCastNode) {
                    processCheckCast((CheckCastNode) node);
                } else if (node instanceof ConditionAnchorNode) {
                    processConditionAnchor((ConditionAnchorNode) node);
                } else if (node instanceof IfNode) {
                    processIf((IfNode) node);
                } else {
                    return;
                }
            }

            protected void registerNewCondition(LogicNode condition, boolean negated, ValueNode guard) {
                if (condition instanceof UnaryOpLogicNode) {
                    UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) condition;
                    Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(negated);
                    registerNewStamp(unaryLogicNode.getValue(), newStamp, guard);
                } else if (condition instanceof BinaryOpLogicNode) {
                    BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) condition;
                    ValueNode x = binaryOpLogicNode.getX();
                    if (!x.isConstant()) {
                        Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(negated);
                        registerNewStamp(x, newStampX, guard);
                    }

                    ValueNode y = binaryOpLogicNode.getY();
                    if (!y.isConstant()) {
                        Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(negated);
                        registerNewStamp(y, newStampY, guard);
                    }
                    if (condition instanceof IntegerEqualsNode && guard instanceof DeoptimizingGuard && !negated) {
                        if (y.isConstant() && x instanceof AndNode) {
                            AndNode and = (AndNode) x;
                            if (and.getY() == y) {
                                /*
                                 * This 'and' proves something about some of the bits in and.getX().
                                 * It's equivalent to or'ing in the mask value since those values
                                 * are known to be set.
                                 */
                                BinaryOp<Or> op = ArithmeticOpTable.forStamp(x.stamp()).getOr();
                                IntegerStamp newStampX = (IntegerStamp) op.foldStamp(and.getX().stamp(), y.stamp());
                                registerNewStamp(and.getX(), newStampX, guard);
                            }
                        }
                    }
                }
                if (guard instanceof DeoptimizingGuard) {
                    pendingTests.push(new PendingTest(condition, (DeoptimizingGuard) guard));
                }
                registerCondition(condition, negated, guard);
            }

            Pair<InfoElement, Stamp> recursiveFoldStamp(Node node, InfoElementProvider info) {
                if (node instanceof UnaryNode) {
                    UnaryNode unary = (UnaryNode) node;
                    ValueNode value = unary.getValue();
                    for (InfoElement infoElement : info.getInfoElements(value)) {
                        Stamp result = unary.foldStamp(infoElement.getStamp());
                        if (result != null) {
                            return new Pair<>(infoElement, result);
                        }
                    }
                    Pair<InfoElement, Stamp> foldResult = recursiveFoldStamp(value, info);
                    if (foldResult != null) {
                        Stamp result = unary.foldStamp(foldResult.second);
                        if (result != null) {
                            return new Pair<>(foldResult.first, result);
                        }
                    }
                } else if (node instanceof BinaryNode) {
                    BinaryNode binary = (BinaryNode) node;
                    ValueNode y = binary.getY();
                    ValueNode x = binary.getX();
                    if (y.isConstant()) {
                        for (InfoElement infoElement : info.getInfoElements(x)) {
                            Stamp result = binary.foldStamp(infoElement.stamp, y.stamp());
                            if (result != null) {
                                return new Pair<>(infoElement, result);
                            }
                        }
                        Pair<InfoElement, Stamp> foldResult = recursiveFoldStamp(x, info);
                        if (foldResult != null) {
                            Stamp result = binary.foldStamp(foldResult.second, y.stamp());
                            if (result != null) {
                                return new Pair<>(foldResult.first, result);
                            }
                        }
                    }
                }
                return null;
            }

            /**
             * Recursively try to fold stamps within this expression using information from
             * {@link #getInfoElements(ValueNode)}. It's only safe to use constants and one
             * {@link InfoElement} otherwise more than one guard would be required.
             *
             * @param node
             * @return the pair of the @{link InfoElement} used and the stamp produced for the whole
             *         expression
             */
            Pair<InfoElement, Stamp> recursiveFoldStampFromInfo(Node node) {
                return recursiveFoldStamp(node, (value) -> getInfoElements(value));
            }

            /**
             * Recursively try to fold stamps within this expression using {@code newStamp} if the
             * node {@code original} is encountered in the expression. It's only safe to use
             * constants and the passed in stamp otherwise more than one guard would be required.
             *
             * @param node
             * @param original
             * @param newStamp
             * @return the improved stamp or null is nothing could be done
             */
            @SuppressWarnings("unchecked")
            Stamp recursiveFoldStamp(Node node, ValueNode original, Stamp newStamp) {
                Debug.log("Recursively fold stamp for node %s original %s stamp %s", node, original, newStamp);
                InfoElement element = new InfoElement(newStamp, original);
                Pair<InfoElement, Stamp> result = recursiveFoldStamp(node, (value) -> value == original ? Collections.singleton(element) : Collections.EMPTY_LIST);
                if (result != null) {
                    return result.second;
                }
                return null;
            }

            protected boolean foldPendingTest(DeoptimizingGuard thisGuard, ValueNode original, Stamp newStamp, GuardRewirer rewireGuardFunction) {
                for (PendingTest pending : pendingTests) {
                    TriState result = TriState.UNKNOWN;
                    if (pending.condition instanceof UnaryOpLogicNode) {
                        UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) pending.condition;
                        if (unaryLogicNode.getValue() == original) {
                            result = unaryLogicNode.tryFold(newStamp);
                        }
                        if (!result.isKnown()) {
                            Stamp foldResult = recursiveFoldStamp(unaryLogicNode.getValue(), original, newStamp);
                            if (foldResult != null) {
                                result = unaryLogicNode.tryFold(foldResult);
                            }
                        }
                    } else if (pending.condition instanceof BinaryOpLogicNode) {
                        BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) pending.condition;
                        ValueNode x = binaryOpLogicNode.getX();
                        ValueNode y = binaryOpLogicNode.getY();
                        if (binaryOpLogicNode.getX() == original) {
                            result = binaryOpLogicNode.tryFold(newStamp, binaryOpLogicNode.getY().stamp());
                        } else if (binaryOpLogicNode instanceof IntegerEqualsNode && y.isConstant() && x instanceof AndNode) {
                            AndNode and = (AndNode) x;
                            if (and.getY() == y && and.getX() == original) {
                                BinaryOp<And> andOp = ArithmeticOpTable.forStamp(newStamp).getAnd();
                                result = binaryOpLogicNode.tryFold(andOp.foldStamp(newStamp, y.stamp()), y.stamp());
                            }
                        }
                        if (!result.isKnown() && y.isConstant()) {
                            Stamp foldResult = recursiveFoldStamp(x, original, newStamp);
                            if (foldResult != null) {
                                result = binaryOpLogicNode.tryFold(foldResult, y.stamp());
                            }
                        }
                    }
                    if (result.isKnown()) {
                        /*
                         * The test case be folded using the information available but the test can
                         * only be moved up if we're sure there's no schedule dependence. For now
                         * limit it to the original node and constants.
                         */
                        InputFilter v = new InputFilter(original);
                        thisGuard.getCondition().acceptInputs(v);
                        if (v.ok && foldGuard(thisGuard, pending.guard, rewireGuardFunction)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            protected boolean foldGuard(DeoptimizingGuard thisGuard, DeoptimizingGuard otherGuard, GuardRewirer rewireGuardFunction) {
                if (otherGuard.getAction() == thisGuard.getAction() && otherGuard.getReason() == thisGuard.getReason() && otherGuard.getSpeculation() == thisGuard.getSpeculation()) {
                    LogicNode condition = (LogicNode) thisGuard.getCondition().copyWithInputs();
                    GuardRewirer rewirer = (guard, result) -> {
                        if (rewireGuardFunction.rewire(guard, result)) {
                            otherGuard.setCondition(condition, thisGuard.isNegated());
                            return true;
                        }
                        condition.safeDelete();
                        return false;
                    };
                    // Move the later test up
                    return rewireGuards(otherGuard.asNode(), !thisGuard.isNegated(), rewirer);
                }
                return false;
            }

            protected void registerCondition(LogicNode condition, boolean negated, ValueNode guard) {
                registerNewStamp(condition, negated ? StampFactory.contradiction() : StampFactory.tautology(), guard);
            }

            protected Iterable<InfoElement> getInfoElements(ValueNode proxiedValue) {
                ValueNode value = GraphUtil.unproxify(proxiedValue);
                if (value == null) {
                    return Collections.emptyList();
                }
                Info info = map.get(value);
                if (info == null) {
                    return Collections.emptyList();
                } else {
                    return info.getElements();
                }
            }

            protected boolean rewireGuards(ValueNode guard, boolean result, GuardRewirer rewireGuardFunction) {
                assert guard instanceof GuardingNode;
                metricStampsFound.increment();
                ValueNode proxiedGuard = proxyGuard(guard);
                return rewireGuardFunction.rewire(proxiedGuard, result);
            }

            protected ValueNode proxyGuard(ValueNode guard) {
                ValueNode proxiedGuard = guard;
                if (!Instance.this.loopExits.isEmpty()) {
                    while (proxiedGuard instanceof GuardProxyNode) {
                        proxiedGuard = ((GuardProxyNode) proxiedGuard).value();
                    }
                    Block guardBlock = nodeToBlock.apply(proxiedGuard);
                    assert guardBlock != null;
                    for (Iterator<LoopExitNode> iter = loopExits.descendingIterator(); iter.hasNext();) {
                        LoopExitNode loopExitNode = iter.next();
                        Block loopExitBlock = nodeToBlock.apply(loopExitNode);
                        if (guardBlock != loopExitBlock && AbstractControlFlowGraph.dominates(guardBlock, loopExitBlock)) {
                            Block loopBeginBlock = nodeToBlock.apply(loopExitNode.loopBegin());
                            if (!AbstractControlFlowGraph.dominates(guardBlock, loopBeginBlock) || guardBlock == loopBeginBlock) {
                                proxiedGuard = proxiedGuard.graph().unique(new GuardProxyNode((GuardingNode) proxiedGuard, loopExitNode));
                            }
                        }
                    }
                }
                return proxiedGuard;
            }

            protected boolean tryProveCondition(LogicNode node, GuardRewirer rewireGuardFunction) {
                return tryProveGuardCondition(null, node, rewireGuardFunction);
            }

            protected boolean tryProveGuardCondition(DeoptimizingGuard thisGuard, LogicNode node, GuardRewirer rewireGuardFunction) {
                for (InfoElement infoElement : getInfoElements(node)) {
                    Stamp stamp = infoElement.getStamp();
                    JavaConstant constant = (JavaConstant) stamp.asConstant();
                    if (constant != null) {
                        return rewireGuards(infoElement.getGuard(), constant.asBoolean(), rewireGuardFunction);
                    }
                }
                if (node instanceof UnaryOpLogicNode) {
                    UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) node;
                    ValueNode value = unaryLogicNode.getValue();
                    for (InfoElement infoElement : getInfoElements(value)) {
                        Stamp stamp = infoElement.getStamp();
                        TriState result = unaryLogicNode.tryFold(stamp);
                        if (result.isKnown()) {
                            return rewireGuards(infoElement.getGuard(), result.toBoolean(), rewireGuardFunction);
                        }
                    }
                    Pair<InfoElement, Stamp> foldResult = recursiveFoldStampFromInfo(value);
                    if (foldResult != null) {
                        TriState result = unaryLogicNode.tryFold(foldResult.second);
                        if (result.isKnown()) {
                            return rewireGuards(foldResult.first.getGuard(), result.toBoolean(), rewireGuardFunction);
                        }
                    }
                    if (thisGuard != null) {
                        Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(thisGuard.isNegated());
                        if (newStamp != null && foldPendingTest(thisGuard, value, newStamp, rewireGuardFunction)) {
                            return true;
                        }

                    }
                } else if (node instanceof BinaryOpLogicNode) {
                    BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) node;
                    for (InfoElement infoElement : getInfoElements(binaryOpLogicNode)) {
                        if (infoElement.getStamp().equals(StampFactory.contradiction())) {
                            return rewireGuards(infoElement.getGuard(), false, rewireGuardFunction);
                        } else if (infoElement.getStamp().equals(StampFactory.tautology())) {
                            return rewireGuards(infoElement.getGuard(), true, rewireGuardFunction);
                        }
                    }

                    ValueNode x = binaryOpLogicNode.getX();
                    ValueNode y = binaryOpLogicNode.getY();
                    for (InfoElement infoElement : getInfoElements(x)) {
                        TriState result = binaryOpLogicNode.tryFold(infoElement.getStamp(), y.stamp());
                        if (result.isKnown()) {
                            return rewireGuards(infoElement.getGuard(), result.toBoolean(), rewireGuardFunction);
                        }
                    }

                    if (y.isConstant()) {
                        Pair<InfoElement, Stamp> foldResult = recursiveFoldStampFromInfo(x);
                        if (foldResult != null) {
                            TriState result = binaryOpLogicNode.tryFold(foldResult.second, y.stamp());
                            if (result.isKnown()) {
                                return rewireGuards(foldResult.first.getGuard(), result.toBoolean(), rewireGuardFunction);
                            }
                        }
                    } else {
                        for (InfoElement infoElement : getInfoElements(y)) {
                            TriState result = binaryOpLogicNode.tryFold(x.stamp(), infoElement.getStamp());
                            if (result.isKnown()) {
                                return rewireGuards(infoElement.getGuard(), result.toBoolean(), rewireGuardFunction);
                            }
                        }
                    }

                    /*
                     * For complex expressions involving constants, see if it's possible to fold the
                     * tests by using stamps one level up in the expression. For instance, (x + n <
                     * y) might fold if something is known about x and all other values are
                     * constants. The reason for the constant restriction is that if more than 1
                     * real value is involved the code might need to adopt multiple guards to have
                     * proper dependences.
                     */
                    if (x instanceof BinaryArithmeticNode<?> && y.isConstant()) {
                        BinaryArithmeticNode<?> binary = (BinaryArithmeticNode<?>) x;
                        if (binary.getY().isConstant()) {
                            for (InfoElement infoElement : getInfoElements(binary.getX())) {
                                Stamp newStampX = binary.foldStamp(infoElement.getStamp(), binary.getY().stamp());
                                TriState result = binaryOpLogicNode.tryFold(newStampX, y.stamp());
                                if (result.isKnown()) {
                                    return rewireGuards(infoElement.getGuard(), result.toBoolean(), rewireGuardFunction);
                                }
                            }
                        }
                    }
                    if (thisGuard != null && binaryOpLogicNode instanceof IntegerEqualsNode && !thisGuard.isNegated()) {
                        if (y.isConstant() && x instanceof AndNode) {
                            AndNode and = (AndNode) x;
                            if (and.getY() == y) {
                                /*
                                 * This 'and' proves something about some of the bits in and.getX().
                                 * It's equivalent to or'ing in the mask value since those values
                                 * are known to be set.
                                 */
                                BinaryOp<Or> op = ArithmeticOpTable.forStamp(x.stamp()).getOr();
                                IntegerStamp newStampX = (IntegerStamp) op.foldStamp(and.getX().stamp(), y.stamp());
                                if (foldPendingTest(thisGuard, and.getX(), newStampX, rewireGuardFunction)) {
                                    return true;
                                }
                            }
                        }
                    }
                    if (thisGuard != null) {
                        if (!x.isConstant()) {
                            Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(thisGuard.isNegated());
                            if (newStampX != null && foldPendingTest(thisGuard, x, newStampX, rewireGuardFunction)) {
                                return true;
                            }
                        }
                        if (!y.isConstant()) {
                            Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(thisGuard.isNegated());
                            if (newStampY != null && foldPendingTest(thisGuard, y, newStampY, rewireGuardFunction)) {
                                return true;
                            }
                        }
                    }
                } else if (node instanceof ShortCircuitOrNode) {
                    final ShortCircuitOrNode shortCircuitOrNode = (ShortCircuitOrNode) node;
                    if (Instance.this.loopExits.isEmpty()) {
                        return tryProveCondition(shortCircuitOrNode.getX(), (guard, result) -> {
                            if (result == !shortCircuitOrNode.isXNegated()) {
                                return rewireGuards(guard, true, rewireGuardFunction);
                            } else {
                                return tryProveCondition(shortCircuitOrNode.getY(), (innerGuard, innerResult) -> {
                                    if (innerGuard == guard) {
                                        return rewireGuards(guard, innerResult ^ shortCircuitOrNode.isYNegated(), rewireGuardFunction);
                                    }
                                    return false;
                                });
                            }
                        });
                    }
                }

                return false;
            }

            protected void registerNewStamp(ValueNode proxiedValue, Stamp newStamp, ValueNode guard) {
                assert proxiedValue != null;
                assert guard != null;
                if (newStamp != null) {
                    ValueNode value = GraphUtil.unproxify(proxiedValue);
                    Info info = map.get(value);
                    if (info == null) {
                        info = new Info();
                        map.set(value, info);
                    }
                    metricStampsRegistered.increment();
                    final Info finalInfo = info;
                    Debug.log("\t Saving stamp for node %s stamp %s guarded by %s", value, newStamp, guard == null ? "null" : guard);
                    finalInfo.pushElement(new InfoElement(newStamp, guard));
                    undoOperations.add(() -> finalInfo.popElement());
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
                    processTypeSwitch(beginNode, predecessor, typeSwitch);
                } else if (predecessor instanceof IntegerSwitchNode) {
                    IntegerSwitchNode integerSwitchNode = (IntegerSwitchNode) predecessor;
                    processIntegerSwitch(beginNode, predecessor, integerSwitchNode);
                }
            }

            protected void processIntegerSwitch(AbstractBeginNode beginNode, Node predecessor, IntegerSwitchNode integerSwitchNode) {
                Stamp stamp = null;
                for (int i = 0; i < integerSwitchNode.keyCount(); i++) {
                    if (integerSwitchNode.keySuccessor(i) == predecessor) {
                        if (stamp == null) {
                            stamp = StampFactory.forConstant(integerSwitchNode.keyAt(i));
                        } else {
                            stamp = stamp.meet(StampFactory.forConstant(integerSwitchNode.keyAt(i)));
                        }
                    }
                }

                if (stamp != null) {
                    registerNewStamp(integerSwitchNode.value(), stamp, beginNode);
                }
            }

            protected void processTypeSwitch(AbstractBeginNode beginNode, Node predecessor, TypeSwitchNode typeSwitch) {
                ValueNode hub = typeSwitch.value();
                if (hub instanceof LoadHubNode) {
                    LoadHubNode loadHub = (LoadHubNode) hub;
                    Stamp stamp = null;
                    for (int i = 0; i < typeSwitch.keyCount(); i++) {
                        if (typeSwitch.keySuccessor(i) == predecessor) {
                            if (stamp == null) {
                                stamp = StampFactory.exactNonNull(typeSwitch.typeAt(i));
                            } else {
                                stamp = stamp.meet(StampFactory.exactNonNull(typeSwitch.typeAt(i)));
                            }
                        }
                    }
                    if (stamp != null) {
                        registerNewStamp(loadHub.getValue(), stamp, beginNode);
                    }
                }
            }
        }
    }

    static class Pair<F, S> {
        public final F first;
        public final S second;

        Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public int hashCode() {
            return first.hashCode() * 31 ^ second.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Pair<?, ?>) {
                Pair<?, ?> other = (Pair<?, ?>) obj;
                return this.first.equals(other.first) && this.second.equals(other.second);
            }
            return false;
        }
    }

    @FunctionalInterface
    protected interface InfoElementProvider {
        Iterable<InfoElement> getInfoElements(ValueNode value);
    }

    /**
     * Checks for safe nodes when moving pending tests up.
     */
    static class InputFilter implements BiConsumer<Node, Node> {
        boolean ok;
        private ValueNode value;

        InputFilter(ValueNode value) {
            this.value = value;
            this.ok = true;
        }

        public void accept(Node node, Node curNode) {
            if (!(curNode instanceof ValueNode)) {
                ok = false;
                return;
            }
            ValueNode curValue = (ValueNode) curNode;
            if (curValue.isConstant() || curValue == value || curValue instanceof ParameterNode) {
                return;
            }
            if (curValue instanceof BinaryNode || curValue instanceof UnaryNode) {
                curValue.acceptInputs(this);
            } else {
                ok = false;
            }
        }
    }

    @FunctionalInterface
    protected interface GuardRewirer {
        /**
         * Called if the condition could be proven to have a constant value ({@code result}) under
         * {@code guard}.
         *
         * Return whether a transformation could be applied.
         */
        boolean rewire(ValueNode guard, boolean result);
    }

    protected static class PendingTest {
        private final LogicNode condition;
        private final DeoptimizingGuard guard;

        public PendingTest(LogicNode condition, DeoptimizingGuard guard) {
            this.condition = condition;
            this.guard = guard;
        }
    }

    protected static final class InfoElement {
        private final Stamp stamp;
        private final ValueNode guard;

        public InfoElement(Stamp stamp, ValueNode guard) {
            this.stamp = stamp;
            this.guard = guard;
        }

        public Stamp getStamp() {
            return stamp;
        }

        public ValueNode getGuard() {
            return guard;
        }

        @Override
        public String toString() {
            return stamp + " -> " + guard;
        }
    }

    protected static final class Info {
        private final ArrayList<InfoElement> infos;

        public Info() {
            infos = new ArrayList<>();
        }

        public Iterable<InfoElement> getElements() {
            return infos;
        }

        public void pushElement(InfoElement element) {
            Debug.log(4, "Pushing an info element:%s   size %d", element, infos);
            infos.add(element);
        }

        public void popElement() {
            infos.remove(infos.size() - 1);
        }
    }
}
