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
package jdk.compiler.graal.loop.phases;

import static jdk.compiler.graal.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.util.List;
import java.util.Optional;

import jdk.compiler.graal.core.common.GraalOptions;
import jdk.compiler.graal.core.common.cfg.Loop;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.debug.DebugCloseable;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeSourcePosition;
import jdk.compiler.graal.nodeinfo.InputType;
import jdk.compiler.graal.nodes.AbstractBeginNode;
import jdk.compiler.graal.nodes.AbstractEndNode;
import jdk.compiler.graal.nodes.AbstractMergeNode;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.ControlSplitNode;
import jdk.compiler.graal.nodes.DeoptimizeNode;
import jdk.compiler.graal.nodes.EndNode;
import jdk.compiler.graal.nodes.FixedGuardNode;
import jdk.compiler.graal.nodes.FixedNode;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.GraphState;
import jdk.compiler.graal.nodes.GraphState.StageFlag;
import jdk.compiler.graal.nodes.GuardNode;
import jdk.compiler.graal.nodes.IfNode;
import jdk.compiler.graal.nodes.LogicNode;
import jdk.compiler.graal.nodes.LoopExitNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ProxyNode;
import jdk.compiler.graal.nodes.StartNode;
import jdk.compiler.graal.nodes.StaticDeoptimizingNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.ValuePhiNode;
import jdk.compiler.graal.nodes.calc.CompareNode;
import jdk.compiler.graal.nodes.calc.IntegerEqualsNode;
import jdk.compiler.graal.nodes.cfg.HIRBlock;
import jdk.compiler.graal.nodes.extended.BranchProbabilityNode;
import jdk.compiler.graal.nodes.loop.LoopEx;
import jdk.compiler.graal.nodes.loop.LoopsData;
import jdk.compiler.graal.nodes.spi.CoreProviders;
import jdk.compiler.graal.nodes.spi.Simplifiable;
import jdk.compiler.graal.nodes.spi.SimplifierTool;
import jdk.compiler.graal.nodes.util.GraphUtil;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.DeadCodeEliminationPhase;
import jdk.compiler.graal.phases.common.LazyValue;
import jdk.compiler.graal.phases.common.PostRunCanonicalizationPhase;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.TriState;

/**
 * This phase will find branches which always end with a {@link DeoptimizeNode} and replace their
 * {@link ControlSplitNode ControlSplitNodes} with {@link FixedGuardNode FixedGuardNodes}.
 * <p>
 * This is useful because {@link FixedGuardNode FixedGuardNodes} will be lowered to {@link GuardNode
 * GuardNodes} which can later be optimized more aggressively than control-flow constructs.
 * <p>
 * This is currently only done for branches that start from a {@link IfNode}. If it encounters a
 * branch starting at an other kind of {@link ControlSplitNode}, it will only bring the
 * {@link DeoptimizeNode} as close to the {@link ControlSplitNode} as possible.
 */
public class ConvertDeoptimizeToGuardPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public ConvertDeoptimizeToGuardPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState),
                        NotApplicable.when(graphState.getGuardsStage().areFrameStatesAtDeopts(),
                                        "This phase creates guard nodes, i.e., the graph must allow guard insertion"));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(final StructuredGraph graph, final CoreProviders context) {
        LazyValue<LoopsData> lazyLoops = new LazyValue<>(() -> context.getLoopsDataProvider().getLoopsData(graph));

        for (DeoptimizeNode d : graph.getNodes(DeoptimizeNode.TYPE)) {
            assert d.isAlive();
            if (!d.canFloat()) {
                continue;
            }
            try (DebugCloseable closable = d.withNodeSourcePosition()) {
                propagateFixed(d, d, context, lazyLoops);
            }
        }
        if (context != null) {
            for (FixedGuardNode fixedGuard : graph.getNodes(FixedGuardNode.TYPE)) {
                try (DebugCloseable closable = fixedGuard.withNodeSourcePosition()) {
                    trySplitFixedGuard(fixedGuard, context, lazyLoops);
                }
            }
        }
        new DeadCodeEliminationPhase(Optional).apply(graph);
    }

    private static void trySplitFixedGuard(FixedGuardNode fixedGuard, CoreProviders context, LazyValue<LoopsData> lazyLoops) {
        LogicNode condition = fixedGuard.condition();
        if (condition instanceof CompareNode) {
            CompareNode compare = (CompareNode) condition;
            ValueNode x = compare.getX();
            ValuePhiNode xPhi = (x instanceof ValuePhiNode) ? (ValuePhiNode) x : null;
            if (x instanceof ConstantNode || xPhi != null) {
                ValueNode y = compare.getY();
                ValuePhiNode yPhi = (y instanceof ValuePhiNode) ? (ValuePhiNode) y : null;
                if (y instanceof ConstantNode || yPhi != null) {
                    processFixedGuardAndPhis(fixedGuard, context, compare, x, xPhi, y, yPhi, lazyLoops);
                }
            }
        }
    }

    private static void processFixedGuardAndPhis(FixedGuardNode fixedGuard, CoreProviders context, CompareNode compare, ValueNode x, ValuePhiNode xPhi, ValueNode y, ValuePhiNode yPhi,
                    LazyValue<LoopsData> lazyLoops) {
        AbstractBeginNode pred = AbstractBeginNode.prevBegin(fixedGuard);
        if (pred instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) pred;
            if (xPhi != null && xPhi.merge() != merge) {
                return;
            }
            if (yPhi != null && yPhi.merge() != merge) {
                return;
            }

            processFixedGuardAndMerge(fixedGuard, context, compare, x, xPhi, y, yPhi, merge, lazyLoops);
        }
    }

    @SuppressWarnings("try")
    private static void processFixedGuardAndMerge(FixedGuardNode fixedGuard, CoreProviders context, CompareNode compare, ValueNode x, ValuePhiNode xPhi, ValueNode y, ValuePhiNode yPhi,
                    AbstractMergeNode merge, LazyValue<LoopsData> lazyLoops) {
        List<EndNode> mergePredecessors = merge.cfgPredecessors().snapshot();
        for (AbstractEndNode mergePredecessor : mergePredecessors) {
            if (!mergePredecessor.isAlive()) {
                break;
            }
            Constant xs;
            if (xPhi == null) {
                xs = x.asConstant();
            } else {
                xs = xPhi.valueAt(mergePredecessor).asConstant();
            }
            Constant ys;
            if (yPhi == null) {
                ys = y.asConstant();
            } else {
                ys = yPhi.valueAt(mergePredecessor).asConstant();
            }
            if (xs != null && ys != null) {
                Stamp compareStamp = x.stamp(NodeView.DEFAULT);
                TriState compareResult = compare.condition().foldCondition(compareStamp, xs, ys, context.getConstantReflection(), compare.unorderedIsTrue());
                if (compareResult.isKnown() && compareResult.toBoolean() == fixedGuard.isNegated()) {
                    try (DebugCloseable position = fixedGuard.withNodeSourcePosition()) {
                        propagateFixed(mergePredecessor, fixedGuard, context, lazyLoops);
                    }
                }
            }
        }
    }

    @SuppressWarnings("try")
    public static void propagateFixed(FixedNode from, StaticDeoptimizingNode deopt, CoreProviders providers, LazyValue<LoopsData> lazyLoops) {
        Node current = from;
        while (current != null) {
            if (GraalOptions.GuardPriorities.getValue(from.getOptions()) && current instanceof FixedGuardNode) {
                FixedGuardNode otherGuard = (FixedGuardNode) current;
                if (otherGuard.computePriority().isHigherPriorityThan(deopt.computePriority())) {
                    moveAsDeoptAfter(otherGuard, deopt);
                    return;
                }
            } else if (current instanceof AbstractBeginNode) {
                if (current instanceof AbstractMergeNode) {
                    AbstractMergeNode mergeNode = (AbstractMergeNode) current;
                    FixedNode next = mergeNode.next();
                    while (mergeNode.isAlive()) {
                        AbstractEndNode end = mergeNode.forwardEnds().first();
                        propagateFixed(end, deopt, providers, lazyLoops);
                    }
                    if (next.isAlive()) {
                        propagateFixed(next, deopt, providers, lazyLoops);
                    }
                    return;
                } else if (current.predecessor() instanceof IfNode) {
                    AbstractBeginNode begin = (AbstractBeginNode) current;
                    IfNode ifNode = (IfNode) current.predecessor();
                    if (isOsrLoopExit(begin) || isCountedLoopExit(ifNode, lazyLoops)) {
                        moveAsDeoptAfter(begin, deopt);
                    } else {
                        if (begin instanceof LoopExitNode && ifNode.condition() instanceof IntegerEqualsNode) {
                            StructuredGraph graph = ifNode.graph();
                            IntegerEqualsNode integerEqualsNode = (IntegerEqualsNode) ifNode.condition();
                            // If this loop exit is associated with an injected profile, propagate
                            // before converting to guard.
                            if (integerEqualsNode.getX() instanceof BranchProbabilityNode) {
                                SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(providers, false, graph.getAssumptions(), graph.getOptions());
                                ((BranchProbabilityNode) integerEqualsNode.getX()).simplify(simplifierTool);
                            } else if (integerEqualsNode.getY() instanceof BranchProbabilityNode) {
                                SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(providers, false, graph.getAssumptions(), graph.getOptions());
                                ((BranchProbabilityNode) integerEqualsNode.getY()).simplify(simplifierTool);
                            }
                        }
                        // Prioritize the source position of the IfNode
                        try (DebugCloseable closable = ifNode.withNodeSourcePosition()) {
                            StructuredGraph graph = ifNode.graph();
                            LogicNode conditionNode = ifNode.condition();
                            boolean negateGuardCondition = current == ifNode.trueSuccessor();
                            NodeSourcePosition survivingSuccessorPosition = negateGuardCondition ? ifNode.falseSuccessor().getNodeSourcePosition() : ifNode.trueSuccessor().getNodeSourcePosition();
                            FixedGuardNode guard = graph.add(
                                            new FixedGuardNode(conditionNode, deopt.getReason(), deopt.getAction(), deopt.getSpeculation(), negateGuardCondition, survivingSuccessorPosition));
                            FixedWithNextNode pred = (FixedWithNextNode) ifNode.predecessor();
                            AbstractBeginNode survivingSuccessor;
                            if (negateGuardCondition) {
                                survivingSuccessor = ifNode.falseSuccessor();
                            } else {
                                survivingSuccessor = ifNode.trueSuccessor();
                            }
                            graph.removeSplitPropagate(ifNode, survivingSuccessor);

                            Node newGuard = guard;
                            if (survivingSuccessor instanceof LoopExitNode) {
                                newGuard = ProxyNode.forGuard(guard, (LoopExitNode) survivingSuccessor);
                            }
                            survivingSuccessor.replaceAtUsages(newGuard, InputType.Guard);

                            graph.getOptimizationLog().report(ConvertDeoptimizeToGuardPhase.class, "DeoptimizeToGuardConversion", deopt.asNode());
                            FixedNode next = pred.next();
                            pred.setNext(guard);
                            guard.setNext(next);
                            assert providers != null;
                            SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(providers, false, graph.getAssumptions(), graph.getOptions());
                            ((Simplifiable) survivingSuccessor).simplify(simplifierTool);
                        }
                    }
                    return;
                } else if (current.predecessor() == null || current.predecessor() instanceof ControlSplitNode) {
                    assert current.predecessor() != null || (current instanceof StartNode && current == ((AbstractBeginNode) current).graph().start());
                    moveAsDeoptAfter((AbstractBeginNode) current, deopt);
                    return;
                }
            }
            current = current.predecessor();
        }
    }

    @SuppressWarnings("try")
    private static void moveAsDeoptAfter(FixedWithNextNode node, StaticDeoptimizingNode deopt) {
        try (DebugCloseable position = deopt.asNode().withNodeSourcePosition()) {
            FixedNode next = node.next();
            if (next != deopt.asNode()) {
                node.setNext(node.graph().add(new DeoptimizeNode(deopt.getAction(), deopt.getReason(), deopt.getSpeculation())));
                GraphUtil.killCFG(next);
                node.graph().getOptimizationLog().report(ConvertDeoptimizeToGuardPhase.class, "DeoptimizeMovement", deopt.asNode());
            }
        }
    }

    private static boolean isOsrLoopExit(AbstractBeginNode node) {
        if (!(node instanceof LoopExitNode)) {
            return false;
        }
        return ((LoopExitNode) node).loopBegin().isOsrLoop();
    }

    private static boolean isCountedLoopExit(IfNode ifNode, LazyValue<LoopsData> lazyLoops) {
        LoopsData loopsData = lazyLoops.get();
        Loop<HIRBlock> loop = loopsData.getCFG().getNodeToBlock().get(ifNode).getLoop();
        if (loop != null) {
            LoopEx loopEx = loopsData.loop(loop);
            if (loopEx.detectCounted()) {
                return ifNode == loopEx.counted().getLimitTest();
            }
            if (loopEx.canBecomeLimitTestAfterFloatingReads(ifNode)) {
                return true;
            }
        }
        return false;
    }
}
