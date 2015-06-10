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

import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.TriState;
import static com.oracle.jvmci.meta.DeoptimizationAction.*;
import static com.oracle.jvmci.meta.DeoptimizationReason.*;

import java.util.*;
import java.util.function.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.LoweringPhase.Frame;
import com.oracle.graal.phases.schedule.*;
import com.oracle.jvmci.debug.*;

public class DominatorConditionalEliminationPhase extends Phase {

    private static final DebugMetric metricStampsRegistered = Debug.metric("StampsRegistered");
    private static final DebugMetric metricStampsFound = Debug.metric("StampsFound");
    private final boolean fullSchedule;

    public DominatorConditionalEliminationPhase(boolean fullSchedule) {
        this.fullSchedule = fullSchedule;
    }

    private static final class InfoElement {
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

    private static final class Info {
        private final ArrayList<InfoElement> infos;

        public Info() {
            infos = new ArrayList<>();
        }

        public Iterable<InfoElement> getElements() {
            return infos;
        }

        public void pushElement(InfoElement element) {
            infos.add(element);
        }

        public void popElement() {
            infos.remove(infos.size() - 1);
        }
    }

    @Override
    protected void run(StructuredGraph graph) {

        Function<Block, Iterable<? extends Node>> blockToNodes;
        Function<Node, Block> nodeToBlock;
        Block startBlock;

        if (fullSchedule) {
            SchedulePhase schedule = new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST);
            schedule.apply(graph);
            ControlFlowGraph cfg = schedule.getCFG();
            cfg.computePostdominators();
            blockToNodes = b -> schedule.getBlockToNodesMap().get(b);
            nodeToBlock = n -> schedule.getNodeToBlockMap().get(n);
            startBlock = cfg.getStartBlock();
        } else {
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, true);
            cfg.computePostdominators();
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

        Instance instance = new Instance(graph, blockToNodes, nodeToBlock);
        instance.processBlock(startBlock);
    }

    private static class Instance {

        private NodeMap<Info> map;
        private Deque<LoopExitNode> loopExits;
        private final Function<Block, Iterable<? extends Node>> blockToNodes;
        private final Function<Node, Block> nodeToBlock;

        public Instance(StructuredGraph graph, Function<Block, Iterable<? extends Node>> blockToNodes, Function<Node, Block> nodeToBlock) {
            map = graph.createNodeMap();
            loopExits = new ArrayDeque<>();
            this.blockToNodes = blockToNodes;
            this.nodeToBlock = nodeToBlock;
        }

        public void processBlock(Block startBlock) {
            LoweringPhase.processBlock(new InstanceFrame(startBlock, null));
        }

        public class InstanceFrame extends LoweringPhase.Frame<InstanceFrame> {
            List<Runnable> undoOperations = new ArrayList<>();

            public InstanceFrame(Block block, InstanceFrame parent) {
                super(block, parent);
            }

            @Override
            public Frame<?> enter(Block b) {
                return new InstanceFrame(b, this);
            }

            @Override
            public void preprocess() {
                Instance.this.preprocess(block, undoOperations);
            }

            @Override
            public void postprocess() {
                Instance.postprocess(undoOperations);
            }
        }

        private static void postprocess(List<Runnable> undoOperations) {
            for (Runnable r : undoOperations) {
                r.run();
            }
        }

        private void preprocess(Block block, List<Runnable> undoOperations) {
            AbstractBeginNode beginNode = block.getBeginNode();
            if (beginNode instanceof LoopExitNode && beginNode.isAlive()) {
                LoopExitNode loopExitNode = (LoopExitNode) beginNode;
                this.loopExits.push(loopExitNode);
                undoOperations.add(() -> loopExits.pop());
            } else if (block.getDominator() != null &&
                            (block.getDominator().getLoopDepth() > block.getLoopDepth() || (block.getDominator().getLoopDepth() == block.getLoopDepth() && block.getDominator().getLoop() != block.getLoop()))) {
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
            for (Node n : blockToNodes.apply(block)) {
                if (n.isAlive()) {
                    processNode(n, undoOperations);
                }
            }
        }

        private void processNode(Node node, List<Runnable> undoOperations) {
            if (node instanceof AbstractBeginNode) {
                processAbstractBegin((AbstractBeginNode) node, undoOperations);
            } else if (node instanceof FixedGuardNode) {
                processFixedGuard((FixedGuardNode) node, undoOperations);
            } else if (node instanceof GuardNode) {
                processGuard((GuardNode) node, undoOperations);
            } else if (node instanceof CheckCastNode) {
                processCheckCast((CheckCastNode) node);
            } else if (node instanceof ConditionAnchorNode) {
                processConditionAnchor((ConditionAnchorNode) node);
            } else if (node instanceof IfNode) {
                processIf((IfNode) node, undoOperations);
            } else {
                return;
            }
        }

        private void processCheckCast(CheckCastNode node) {
            for (InfoElement infoElement : getInfoElements(node.object())) {
                TriState result = node.tryFold(infoElement.getStamp());
                if (result.isKnown()) {
                    if (rewireGuards(infoElement.getGuard(), result.toBoolean(), (guard, checkCastResult) -> {
                        if (checkCastResult) {
                            PiNode piNode = node.graph().unique(new PiNode(node.object(), node.stamp(), guard));
                            node.replaceAtUsages(piNode);
                            GraphUtil.unlinkFixedNode(node);
                            node.safeDelete();
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

        private void processIf(IfNode node, List<Runnable> undoOperations) {
            tryProofCondition(node.condition(), (guard, result) -> {
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

        private void registerNewCondition(LogicNode condition, boolean negated, ValueNode guard, List<Runnable> undoOperations) {
            if (condition instanceof UnaryOpLogicNode) {
                UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) condition;
                Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(negated);
                registerNewStamp(unaryLogicNode.getValue(), newStamp, guard, undoOperations);
            } else if (condition instanceof BinaryOpLogicNode) {
                BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) condition;
                ValueNode x = binaryOpLogicNode.getX();
                if (!x.isConstant()) {
                    Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(negated);
                    registerNewStamp(x, newStampX, guard, undoOperations);
                }

                ValueNode y = binaryOpLogicNode.getY();
                if (!y.isConstant()) {
                    Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(negated);
                    registerNewStamp(y, newStampY, guard, undoOperations);
                }
            }
            registerCondition(condition, negated, guard, undoOperations);
        }

        private void registerCondition(LogicNode condition, boolean negated, ValueNode guard, List<Runnable> undoOperations) {
            registerNewStamp(condition, negated ? StampFactory.contradiction() : StampFactory.tautology(), guard, undoOperations);
        }

        private Iterable<InfoElement> getInfoElements(ValueNode proxiedValue) {
            ValueNode value = GraphUtil.unproxify(proxiedValue);
            Info info = map.get(value);
            if (info == null) {
                return Collections.emptyList();
            } else {
                return info.getElements();
            }
        }

        private boolean rewireGuards(ValueNode guard, boolean result, GuardRewirer rewireGuardFunction) {
            assert guard instanceof GuardingNode;
            metricStampsFound.increment();
            ValueNode proxiedGuard = proxyGuard(guard);
            return rewireGuardFunction.rewire(proxiedGuard, result);
        }

        private ValueNode proxyGuard(ValueNode guard) {
            ValueNode proxiedGuard = guard;
            if (!this.loopExits.isEmpty()) {
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

        @FunctionalInterface
        private interface GuardRewirer {
            /**
             * Called if the condition could be proven to have a constant value ({@code result})
             * under {@code guard}.
             *
             * Return whether a transformation could be applied.
             */
            boolean rewire(ValueNode guard, boolean result);
        }

        private boolean tryProofCondition(LogicNode node, GuardRewirer rewireGuardFunction) {
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

                for (InfoElement infoElement : getInfoElements(y)) {
                    TriState result = binaryOpLogicNode.tryFold(x.stamp(), infoElement.getStamp());
                    if (result.isKnown()) {
                        return rewireGuards(infoElement.getGuard(), result.toBoolean(), rewireGuardFunction);
                    }
                }
            } else if (node instanceof ShortCircuitOrNode) {
                final ShortCircuitOrNode shortCircuitOrNode = (ShortCircuitOrNode) node;
                if (this.loopExits.isEmpty()) {
                    return tryProofCondition(shortCircuitOrNode.getX(), (guard, result) -> {
                        if (result == !shortCircuitOrNode.isXNegated()) {
                            return rewireGuards(guard, true, rewireGuardFunction);
                        } else {
                            return tryProofCondition(shortCircuitOrNode.getY(), (innerGuard, innerResult) -> {
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

        private void registerNewStamp(ValueNode proxiedValue, Stamp newStamp, ValueNode guard, List<Runnable> undoOperations) {
            if (newStamp != null) {
                ValueNode value = GraphUtil.unproxify(proxiedValue);
                Info info = map.get(value);
                if (info == null) {
                    info = new Info();
                    map.set(value, info);
                }
                metricStampsRegistered.increment();
                final Info finalInfo = info;
                finalInfo.pushElement(new InfoElement(newStamp, guard));
                undoOperations.add(() -> finalInfo.popElement());
            }
        }

        private void processConditionAnchor(ConditionAnchorNode node) {
            tryProofCondition(node.condition(), (guard, result) -> {
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

        private void processGuard(GuardNode node, List<Runnable> undoOperations) {
            if (!tryProofCondition(node.condition(), (guard, result) -> {
                if (result != node.isNegated()) {
                    node.replaceAndDelete(guard);
                } else {
                    DeoptimizeNode deopt = node.graph().add(new DeoptimizeNode(node.action(), node.reason()));
                    AbstractBeginNode beginNode = (AbstractBeginNode) node.getAnchor();
                    FixedNode next = beginNode.next();
                    beginNode.setNext(deopt);
                    GraphUtil.killCFG(next);
                }
                return true;
            })) {
                registerNewCondition(node.condition(), node.isNegated(), node, undoOperations);
            }
        }

        private void processFixedGuard(FixedGuardNode node, List<Runnable> undoOperations) {
            if (!tryProofCondition(node.condition(), (guard, result) -> {
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
                registerNewCondition(node.condition(), node.isNegated(), node, undoOperations);
            }
        }

        private void processAbstractBegin(AbstractBeginNode beginNode, List<Runnable> undoOperations) {
            Node predecessor = beginNode.predecessor();
            if (predecessor instanceof IfNode) {
                IfNode ifNode = (IfNode) predecessor;
                boolean negated = (ifNode.falseSuccessor() == beginNode);
                LogicNode condition = ifNode.condition();
                registerNewCondition(condition, negated, beginNode, undoOperations);
            } else if (predecessor instanceof TypeSwitchNode) {
                TypeSwitchNode typeSwitch = (TypeSwitchNode) predecessor;
                processTypeSwitch(beginNode, undoOperations, predecessor, typeSwitch);
            } else if (predecessor instanceof IntegerSwitchNode) {
                IntegerSwitchNode integerSwitchNode = (IntegerSwitchNode) predecessor;
                processIntegerSwitch(beginNode, undoOperations, predecessor, integerSwitchNode);
            }
        }

        private void processIntegerSwitch(AbstractBeginNode beginNode, List<Runnable> undoOperations, Node predecessor, IntegerSwitchNode integerSwitchNode) {
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
                registerNewStamp(integerSwitchNode.value(), stamp, beginNode, undoOperations);
            }
        }

        private void processTypeSwitch(AbstractBeginNode beginNode, List<Runnable> undoOperations, Node predecessor, TypeSwitchNode typeSwitch) {
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
                    registerNewStamp(loadHub.getValue(), stamp, beginNode, undoOperations);
                }
            }
        }
    }
}
