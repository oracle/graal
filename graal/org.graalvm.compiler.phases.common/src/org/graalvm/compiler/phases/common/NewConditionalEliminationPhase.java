/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.And;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Or;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeStack;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConditionAnchorNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.DeoptimizingGuard;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnaryOpLogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.PointerEqualsNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.TypeSwitchNode;
import org.graalvm.compiler.nodes.spi.NodeWithState;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.util.Pair;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.TriState;

public class NewConditionalEliminationPhase extends BasePhase<PhaseContext> {

    private static final DebugCounter counterStampsRegistered = Debug.counter("StampsRegistered");
    private static final DebugCounter counterStampsFound = Debug.counter("StampsFound");
    private static final DebugCounter counterIfsKilled = Debug.counter("CE_KilledIfs");
    private static final DebugCounter counterLFFolded = Debug.counter("ConstantLFFolded");
    private final boolean fullSchedule;

    public NewConditionalEliminationPhase(boolean fullSchedule) {
        this.fullSchedule = fullSchedule;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, PhaseContext context) {
        try (Debug.Scope s = Debug.scope("DominatorConditionalElimination")) {
            BlockMap<List<Node>> blockToNodes;
            NodeMap<Block> nodeToBlock;
            ControlFlowGraph cfg;
            if (fullSchedule) {
                SchedulePhase schedule = new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST);
                schedule.apply(graph);
                cfg = graph.getLastSchedule().getCFG();
                cfg.computePostdominators();
                blockToNodes = graph.getLastSchedule().getBlockToNodesMap();
                nodeToBlock = graph.getLastSchedule().getNodeToBlockMap();
            } else {
                cfg = ControlFlowGraph.compute(graph, true, true, true, true);
                blockToNodes = null;
                nodeToBlock = cfg.getNodeToBlock();
            }

            Instance visitor = new Instance(graph, blockToNodes, nodeToBlock, context);
            cfg.visitDominatorTree(visitor, graph.hasValueProxies());
        }
    }

    public static class Instance implements ControlFlowGraph.RecursiveVisitor<Integer> {
        protected final NodeMap<InfoElement> map;
        protected final BlockMap<List<Node>> blockToNodes;
        protected final NodeMap<Block> nodeToBlock;
        protected final CanonicalizerTool tool;
        protected final NodeStack undoOperations;
        protected final StructuredGraph graph;

        /**
         * Tests which may be eliminated because post dominating tests to prove a broader condition.
         */
        private Deque<PendingTest> pendingTests;

        public Instance(StructuredGraph graph, BlockMap<List<Node>> blockToNodes,
                        NodeMap<Block> nodeToBlock, PhaseContext context) {
            this.graph = graph;
            this.blockToNodes = blockToNodes;
            this.nodeToBlock = nodeToBlock;
            this.undoOperations = new NodeStack();
            this.map = graph.createNodeMap();
            pendingTests = new ArrayDeque<>();
            tool = GraphUtil.getDefaultSimplifier(context.getMetaAccess(), context.getConstantReflection(), context.getConstantFieldProvider(), false, graph.getAssumptions(), context.getLowerer());
        }

        protected void processConditionAnchor(ConditionAnchorNode node) {
            tryProveCondition(node.condition(), (guard, result, newInput) -> {
                if (result != node.isNegated()) {
                    rewirePiNodes(node, newInput);
                    node.replaceAtUsages(guard.asNode());
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

        private static void rewirePiNodes(GuardingNode node, ValueProxy newInput) {
            ValueNode unproxified = GraphUtil.unproxify(newInput);
            for (Node usage : node.asNode().usages()) {
                if (usage instanceof PiNode) {
                    PiNode piNode = (PiNode) usage;
                    if (piNode.getOriginalNode() != newInput && GraphUtil.unproxify(piNode.getOriginalNode()) == unproxified) {
                        piNode.setOriginalNode((ValueNode) newInput.asNode());
                    }
                }
            }
        }

        protected void processGuard(GuardNode node) {
            if (!tryProveGuardCondition(node, node.getCondition(), (guard, result, newInput) -> {
                if (result != node.isNegated()) {
                    rewirePiNodes(node, newInput);
                    node.replaceAndDelete(guard.asNode());
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
            if (!tryProveGuardCondition(node, node.condition(), (guard, result, newInput) -> {
                if (result != node.isNegated()) {
                    rewirePiNodes(node, newInput);
                    node.replaceAtUsages(guard.asNode());
                    GraphUtil.unlinkFixedNode(node);
                    GraphUtil.killWithUnusedFloatingInputs(node);
                } else {
                    DeoptimizeNode deopt = node.graph().add(new DeoptimizeNode(node.getAction(), node.getReason(), node.getSpeculation()));
                    deopt.setStateBefore(node.stateBefore());
                    node.replaceAtPredecessor(deopt);
                    GraphUtil.killCFG(node);
                }
                Debug.log("Kill fixed guard guard");
                return true;
            })) {
                registerNewCondition(node.condition(), node.isNegated(), node);
            }
        }

        protected void processIf(IfNode node) {
            tryProveCondition(node.condition(), (guard, result, newInput) -> {
                AbstractBeginNode survivingSuccessor = node.getSuccessor(result);
                rewirePiNodes(survivingSuccessor, newInput);
                survivingSuccessor.replaceAtUsages(InputType.Guard, guard.asNode());
                survivingSuccessor.replaceAtPredecessor(null);
                node.replaceAtPredecessor(survivingSuccessor);
                GraphUtil.killCFG(node);
                Debug.log("Kill if");
                counterIfsKilled.increment();
                return true;
            });
        }

        @Override
        public Integer enter(Block block) {
            int mark = undoOperations.size();
            Debug.log("[Pre Processing block %s]", block);
            // For now conservatively collect guards only within the same block.
            pendingTests.clear();
            if (blockToNodes != null) {
                for (Node n : blockToNodes.get(block)) {
                    if (n.isAlive()) {
                        processNode(n);
                    }
                }
            } else {
                processBlock(block);
            }
            return mark;
        }

        private void processBlock(Block block) {
            FixedNode n = block.getBeginNode();
            FixedNode endNode = block.getEndNode();
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

        protected void processNode(Node node) {
            if (node instanceof NodeWithState && !(node instanceof GuardingNode)) {
                pendingTests.clear();
            }
            if (node instanceof AbstractBeginNode) {
                if (node instanceof LoopExitNode && graph.hasValueProxies()) {
                    // Condition must not be used down this path.
                } else {
                    processAbstractBegin((AbstractBeginNode) node);
                }
            } else if (node instanceof FixedGuardNode) {
                processFixedGuard((FixedGuardNode) node);
            } else if (node instanceof GuardNode) {
                processGuard((GuardNode) node);
            } else if (node instanceof ConditionAnchorNode) {
                processConditionAnchor((ConditionAnchorNode) node);
            } else if (node instanceof IfNode) {
                processIf((IfNode) node);
            } else if (node instanceof LoadFieldNode) {
                processLoadField((LoadFieldNode) node);
            } else {
                return;
            }
        }

        private void processLoadField(LoadFieldNode node) {
            GuardedConstantStamp stamp = this.getConstantObjectStamp(node.object());
            if (stamp != null) {
                counterLFFolded.increment();
                node.setObject(ConstantNode.forConstant(stamp.objectConstant, tool.getMetaAccess(), graph));
            }
        }

        protected void registerNewCondition(LogicNode condition, boolean negated, GuardingNode guard) {
            if (!negated && condition instanceof PointerEqualsNode) {
                PointerEqualsNode pe = (PointerEqualsNode) condition;
                ValueNode x = pe.getX();
                if (maybeMultipleUsages(x)) {
                    ValueNode y = pe.getY();
                    if (y.isConstant()) {
                        JavaConstant constant = y.asJavaConstant();
                        Stamp succeeding = pe.getSucceedingStampForX(negated);
                        if (succeeding == null && pe instanceof ObjectEqualsNode && guard instanceof FixedGuardNode) {
                            succeeding = y.stamp();
                        }
                        if (succeeding != null) {
                            if (y.stamp() instanceof ObjectStamp) {
                                GuardedConstantStamp cos = new GuardedConstantStamp(constant, (ObjectStamp) succeeding);
                                registerNewStamp(x, cos, guard);
                                return;
                            }
                        }
                    }
                }
            }
            if (condition instanceof UnaryOpLogicNode) {
                UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) condition;
                ValueNode value = unaryLogicNode.getValue();
                if (maybeMultipleUsages(value)) {
                    Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(negated);
                    registerNewStamp(value, newStamp, guard);
                }
            } else if (condition instanceof BinaryOpLogicNode) {
                BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) condition;
                ValueNode x = binaryOpLogicNode.getX();
                if (!x.isConstant() && maybeMultipleUsages(x)) {
                    Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(negated);
                    registerNewStamp(x, newStampX, guard);
                }

                ValueNode y = binaryOpLogicNode.getY();
                if (!y.isConstant() && maybeMultipleUsages(y)) {
                    Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(negated);
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
                            BinaryOp<Or> op = ArithmeticOpTable.forStamp(x.stamp()).getOr();
                            IntegerStamp newStampX = (IntegerStamp) op.foldStamp(andX.stamp(), y.stamp());
                            registerNewStamp(andX, newStampX, guard);
                        }
                    }
                }
            }
            if (guard instanceof DeoptimizingGuard) {
                pendingTests.push(new PendingTest(condition, (DeoptimizingGuard) guard));
            }
            registerCondition(condition, negated, guard);
        }

        private GuardedConstantStamp getConstantObjectStamp(ValueNode n) {
            InfoElement infoElement = getInfoElements(n);
            while (infoElement != null) {
                Stamp s = infoElement.getStamp();
                if (s instanceof GuardedConstantStamp) {
                    return (GuardedConstantStamp) s;
                }
                infoElement = infoElement.getParent();
            }
            return null;
        }

        Pair<InfoElement, Stamp> recursiveFoldStamp(Node node) {
            if (node instanceof UnaryNode) {
                UnaryNode unary = (UnaryNode) node;
                ValueNode value = unary.getValue();
                InfoElement infoElement = getInfoElements(value);
                while (infoElement != null) {
                    Stamp result = unary.foldStamp(infoElement.getStamp());
                    if (result != null) {
                        return Pair.create(infoElement, result);
                    }
                    infoElement = infoElement.getParent();
                }
            } else if (node instanceof BinaryNode) {
                BinaryNode binary = (BinaryNode) node;
                ValueNode y = binary.getY();
                ValueNode x = binary.getX();
                if (y.isConstant()) {
                    InfoElement infoElement = getInfoElements(x);
                    while (infoElement != null) {
                        Stamp result = binary.foldStamp(infoElement.stamp, y.stamp());
                        if (result != null) {
                            return Pair.create(infoElement, result);
                        }
                        infoElement = infoElement.getParent();
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
            return recursiveFoldStamp(node);
        }

        protected boolean foldPendingTest(DeoptimizingGuard thisGuard, ValueNode original, Stamp newStamp, GuardRewirer rewireGuardFunction) {
            for (PendingTest pending : pendingTests) {
                TriState result = TriState.UNKNOWN;
                if (pending.condition instanceof UnaryOpLogicNode) {
                    UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) pending.condition;
                    if (unaryLogicNode.getValue() == original) {
                        result = unaryLogicNode.tryFold(newStamp);
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
                }
                if (result.isKnown()) {
                    /*
                     * The test case be folded using the information available but the test can only
                     * be moved up if we're sure there's no schedule dependence. For now limit it to
                     * the original node and constants.
                     */
                    InputFilter v = new InputFilter(original);
                    thisGuard.getCondition().applyInputs(v);
                    if (v.ok && foldGuard(thisGuard, pending.guard, rewireGuardFunction)) {
                        return true;
                    }
                }
            }
            return false;
        }

        protected boolean foldGuard(DeoptimizingGuard thisGuard, DeoptimizingGuard otherGuard, GuardRewirer rewireGuardFunction) {
            if (otherGuard.getAction() == thisGuard.getAction() && otherGuard.getSpeculation() == thisGuard.getSpeculation()) {
                LogicNode condition = (LogicNode) thisGuard.getCondition().copyWithInputs();
                GuardRewirer rewirer = (guard, result, newInput) -> {
                    if (rewireGuardFunction.rewire(guard, result, newInput)) {
                        otherGuard.setCondition(condition, thisGuard.isNegated());
                        return true;
                    }
                    condition.safeDelete();
                    return false;
                };
                // Move the later test up
                return rewireGuards(otherGuard, !thisGuard.isNegated(), null, rewirer);
            }
            return false;
        }

        protected void registerCondition(LogicNode condition, boolean negated, GuardingNode guard) {
            if (condition.getUsageCount() > 1) {
                registerNewStamp(condition, negated ? StampFactory.contradiction() : StampFactory.tautology(), guard);
            }
        }

        protected InfoElement getInfoElements(ValueNode proxiedValue) {
            ValueNode value = GraphUtil.unproxify(proxiedValue);
            if (value == null) {
                return null;
            }
            return map.get(value);
        }

        protected boolean rewireGuards(GuardingNode guard, boolean result, ValueProxy proxifiedInput, GuardRewirer rewireGuardFunction) {
            counterStampsFound.increment();
            return rewireGuardFunction.rewire(guard, result, proxifiedInput);
        }

        protected boolean tryProveCondition(LogicNode node, GuardRewirer rewireGuardFunction) {
            return tryProveGuardCondition(null, node, rewireGuardFunction);
        }

        protected boolean tryProveGuardCondition(DeoptimizingGuard thisGuard, LogicNode node, GuardRewirer rewireGuardFunction) {
            InfoElement infoElement = getInfoElements(node);
            if (infoElement != null) {
                assert infoElement.getStamp() == StampFactory.tautology() || infoElement.getStamp() == StampFactory.contradiction();
                return rewireGuards(infoElement.getGuard(), infoElement.getStamp() == StampFactory.tautology(), infoElement.getProxifiedInput(), rewireGuardFunction);
            }
            if (node instanceof UnaryOpLogicNode) {
                UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) node;
                ValueNode value = unaryLogicNode.getValue();
                infoElement = getInfoElements(value);
                while (infoElement != null) {
                    Stamp stamp = infoElement.getStamp();
                    TriState result = unaryLogicNode.tryFold(stamp);
                    if (result.isKnown()) {
                        return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), rewireGuardFunction);
                    }
                    infoElement = infoElement.getParent();
                }
                Pair<InfoElement, Stamp> foldResult = recursiveFoldStampFromInfo(value);
                if (foldResult != null) {
                    TriState result = unaryLogicNode.tryFold(foldResult.getRight());
                    if (result.isKnown()) {
                        return rewireGuards(foldResult.getLeft().getGuard(), result.toBoolean(), foldResult.getLeft().getProxifiedInput(), rewireGuardFunction);
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
                infoElement = getInfoElements(binaryOpLogicNode);
                while (infoElement != null) {
                    if (infoElement.getStamp().equals(StampFactory.contradiction())) {
                        return rewireGuards(infoElement.getGuard(), false, infoElement.getProxifiedInput(), rewireGuardFunction);
                    } else if (infoElement.getStamp().equals(StampFactory.tautology())) {
                        return rewireGuards(infoElement.getGuard(), true, infoElement.getProxifiedInput(), rewireGuardFunction);
                    }
                    infoElement = infoElement.getParent();
                }

                ValueNode x = binaryOpLogicNode.getX();
                ValueNode y = binaryOpLogicNode.getY();
                infoElement = getInfoElements(x);
                while (infoElement != null) {
                    TriState result = binaryOpLogicNode.tryFold(infoElement.getStamp(), y.stamp());
                    if (result.isKnown()) {
                        return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), rewireGuardFunction);
                    }
                    infoElement = infoElement.getParent();
                }

                if (y.isConstant()) {
                    Pair<InfoElement, Stamp> foldResult = recursiveFoldStampFromInfo(x);
                    if (foldResult != null) {
                        TriState result = binaryOpLogicNode.tryFold(foldResult.getRight(), y.stamp());
                        if (result.isKnown()) {
                            return rewireGuards(foldResult.getLeft().getGuard(), result.toBoolean(), foldResult.getLeft().getProxifiedInput(), rewireGuardFunction);
                        }
                    }
                } else {
                    infoElement = getInfoElements(y);
                    while (infoElement != null) {
                        TriState result = binaryOpLogicNode.tryFold(x.stamp(), infoElement.getStamp());
                        if (result.isKnown()) {
                            return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), rewireGuardFunction);
                        }
                        infoElement = infoElement.getParent();
                    }
                }

                /*
                 * For complex expressions involving constants, see if it's possible to fold the
                 * tests by using stamps one level up in the expression. For instance, (x + n < y)
                 * might fold if something is known about x and all other values are constants. The
                 * reason for the constant restriction is that if more than 1 real value is involved
                 * the code might need to adopt multiple guards to have proper dependences.
                 */
                if (x instanceof BinaryArithmeticNode<?> && y.isConstant()) {
                    BinaryArithmeticNode<?> binary = (BinaryArithmeticNode<?>) x;
                    if (binary.getY().isConstant()) {
                        infoElement = getInfoElements(binary.getX());
                        while (infoElement != null) {
                            Stamp newStampX = binary.foldStamp(infoElement.getStamp(), binary.getY().stamp());
                            TriState result = binaryOpLogicNode.tryFold(newStampX, y.stamp());
                            if (result.isKnown()) {
                                return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), rewireGuardFunction);
                            }
                            infoElement = infoElement.getParent();
                        }
                    }
                }
                if (thisGuard != null && binaryOpLogicNode instanceof IntegerEqualsNode && !thisGuard.isNegated()) {
                    if (y.isConstant() && x instanceof AndNode) {
                        AndNode and = (AndNode) x;
                        if (and.getY() == y) {
                            /*
                             * This 'and' proves something about some of the bits in and.getX().
                             * It's equivalent to or'ing in the mask value since those values are
                             * known to be set.
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
                return tryProveCondition(shortCircuitOrNode.getX(), (guard, result, newInput) -> {
                    if (result == !shortCircuitOrNode.isXNegated()) {
                        return rewireGuards(guard, true, newInput, rewireGuardFunction);
                    } else {
                        return tryProveCondition(shortCircuitOrNode.getY(), (innerGuard, innerResult, innerNewInput) -> {
                            if (innerGuard == guard && newInput == innerNewInput) {
                                return rewireGuards(guard, innerResult ^ shortCircuitOrNode.isYNegated(), newInput, rewireGuardFunction);
                            }
                            return false;
                        });
                    }
                });
            }

            return false;
        }

        protected void registerNewStamp(ValueNode maybeProxiedValue, Stamp newStamp, GuardingNode guard) {
            assert maybeProxiedValue != null;
            assert guard != null;
            if (newStamp != null) {
                ValueNode value = maybeProxiedValue;
                ValueProxy proxiedValue = null;
                if (value instanceof ValueProxy) {
                    proxiedValue = (ValueProxy) value;
                    value = GraphUtil.unproxify(value);
                }
                counterStampsRegistered.increment();
                Debug.log("\t Saving stamp for node %s stamp %s guarded by %s", value, newStamp, guard == null ? "null" : guard);
                map.set(value, new InfoElement(newStamp, guard, proxiedValue, map.get(value)));
                undoOperations.push(value);
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

        private static boolean maybeMultipleUsages(ValueNode value) {
            if (value.getUsageCount() > 1) {
                return true;
            } else {
                return value instanceof ProxyNode;
            }
        }

        protected void processIntegerSwitch(AbstractBeginNode beginNode, Node predecessor, IntegerSwitchNode integerSwitchNode) {
            ValueNode value = integerSwitchNode.value();
            if (maybeMultipleUsages(value)) {
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
                    registerNewStamp(value, stamp, beginNode);
                }
            }
        }

        protected void processTypeSwitch(AbstractBeginNode beginNode, Node predecessor, TypeSwitchNode typeSwitch) {
            ValueNode hub = typeSwitch.value();
            if (hub instanceof LoadHubNode) {
                LoadHubNode loadHub = (LoadHubNode) hub;
                ValueNode value = loadHub.getValue();
                if (maybeMultipleUsages(value)) {
                    Stamp stamp = null;
                    for (int i = 0; i < typeSwitch.keyCount(); i++) {
                        if (typeSwitch.keySuccessor(i) == predecessor) {
                            if (stamp == null) {
                                stamp = StampFactory.objectNonNull(TypeReference.createExactTrusted(typeSwitch.typeAt(i)));
                            } else {
                                stamp = stamp.meet(StampFactory.objectNonNull(TypeReference.createExactTrusted(typeSwitch.typeAt(i))));
                            }
                        }
                    }
                    if (stamp != null) {
                        registerNewStamp(value, stamp, beginNode);
                    }
                }
            }
        }

        @Override
        public void exit(Block b, Integer state) {
            int mark = state;
            while (undoOperations.size() > mark) {
                Node node = undoOperations.pop();
                if (node.isAlive()) {
                    map.set(node, map.get(node).getParent());
                }
            }
        }
    }

    @FunctionalInterface
    protected interface InfoElementProvider {
        Iterable<InfoElement> getInfoElements(ValueNode value);
    }

    /**
     * Checks for safe nodes when moving pending tests up.
     */
    static class InputFilter extends Node.EdgeVisitor {
        boolean ok;
        private ValueNode value;

        InputFilter(ValueNode value) {
            this.value = value;
            this.ok = true;
        }

        @Override
        public Node apply(Node node, Node curNode) {
            if (!(curNode instanceof ValueNode)) {
                ok = false;
                return curNode;
            }
            ValueNode curValue = (ValueNode) curNode;
            if (curValue.isConstant() || curValue == value || curValue instanceof ParameterNode) {
                return curNode;
            }
            if (curValue instanceof BinaryNode || curValue instanceof UnaryNode) {
                curValue.applyInputs(this);
            } else {
                ok = false;
            }
            return curNode;
        }
    }

    @FunctionalInterface
    protected interface GuardRewirer {
        /**
         * Called if the condition could be proven to have a constant value ({@code result}) under
         * {@code guard}.
         *
         * @param guard the guard whose result is proven
         * @param result the known result of the guard
         * @param newInput new input to pi nodes depending on the new guard
         * @return whether the transformation could be applied
         */
        boolean rewire(GuardingNode guard, boolean result, ValueProxy newInput);
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
        private final GuardingNode guard;
        private final ValueProxy proxifiedInput;
        private final InfoElement parent;

        public InfoElement(Stamp stamp, GuardingNode guard, ValueProxy proxifiedInput, InfoElement parent) {
            this.stamp = stamp;
            this.guard = guard;
            this.proxifiedInput = proxifiedInput;
            this.parent = parent;
        }

        public InfoElement getParent() {
            return parent;
        }

        public Stamp getStamp() {
            return stamp;
        }

        public GuardingNode getGuard() {
            return guard;
        }

        public ValueProxy getProxifiedInput() {
            return proxifiedInput;
        }

        @Override
        public String toString() {
            return stamp + " -> " + guard;
        }
    }

    private static class GuardedConstantStamp extends ObjectStamp {
        private final JavaConstant objectConstant;

        GuardedConstantStamp(JavaConstant objectConstant, ObjectStamp succeedingStamp) {
            super(succeedingStamp.type(), succeedingStamp.isExactType(), succeedingStamp.nonNull(), succeedingStamp.alwaysNull());
            this.objectConstant = objectConstant;
        }

    }

    @Override
    public float codeSizeIncrease() {
        return 1.5f;
    }
}
