/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.jvmci.debug.*;

/**
 * This phase will find branches which always end with a {@link DeoptimizeNode} and replace their
 * {@link ControlSplitNode ControlSplitNodes} with {@link FixedGuardNode FixedGuardNodes}.
 *
 * This is useful because {@link FixedGuardNode FixedGuardNodes} will be lowered to
 * {@link GuardNode GuardNodes} which can later be optimized more aggressively than control-flow
 * constructs.
 *
 * This is currently only done for branches that start from a {@link IfNode}. If it encounters a
 * branch starting at an other kind of {@link ControlSplitNode}, it will only bring the
 * {@link DeoptimizeNode} as close to the {@link ControlSplitNode} as possible.
 *
 */
public class ConvertDeoptimizeToGuardPhase extends BasePhase<PhaseContext> {
    private SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(null, null, false);

    private static AbstractBeginNode findBeginNode(FixedNode startNode) {
        return GraphUtil.predecessorIterable(startNode).filter(AbstractBeginNode.class).first();
    }

    @Override
    protected void run(final StructuredGraph graph, PhaseContext context) {
        assert graph.hasValueProxies() : "ConvertDeoptimizeToGuardPhase always creates proxies";
        if (graph.getNodes(DeoptimizeNode.TYPE).isEmpty()) {
            return;
        }
        for (DeoptimizeNode d : graph.getNodes(DeoptimizeNode.TYPE)) {
            assert d.isAlive();
            visitDeoptBegin(AbstractBeginNode.prevBegin(d), d.action(), d.reason(), graph);
        }

        if (context != null) {
            for (FixedGuardNode fixedGuard : graph.getNodes(FixedGuardNode.TYPE)) {
                trySplitFixedGuard(fixedGuard, context);
            }
        }

        new DeadCodeEliminationPhase(Optional).apply(graph);
    }

    private void trySplitFixedGuard(FixedGuardNode fixedGuard, PhaseContext context) {
        LogicNode condition = fixedGuard.condition();
        if (condition instanceof CompareNode) {
            CompareNode compare = (CompareNode) condition;
            ValueNode x = compare.getX();
            ValuePhiNode xPhi = (x instanceof ValuePhiNode) ? (ValuePhiNode) x : null;
            if (x instanceof ConstantNode || xPhi != null) {
                ValueNode y = compare.getY();
                ValuePhiNode yPhi = (y instanceof ValuePhiNode) ? (ValuePhiNode) y : null;
                if (y instanceof ConstantNode || yPhi != null) {
                    processFixedGuardAndPhis(fixedGuard, context, compare, x, xPhi, y, yPhi);
                }
            }
        }
    }

    private void processFixedGuardAndPhis(FixedGuardNode fixedGuard, PhaseContext context, CompareNode compare, ValueNode x, ValuePhiNode xPhi, ValueNode y, ValuePhiNode yPhi) {
        AbstractBeginNode pred = AbstractBeginNode.prevBegin(fixedGuard);
        if (pred instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) pred;
            if (xPhi != null && xPhi.merge() != merge) {
                return;
            }
            if (yPhi != null && yPhi.merge() != merge) {
                return;
            }

            processFixedGuardAndMerge(fixedGuard, context, compare, x, xPhi, y, yPhi, merge);
        }
    }

    private void processFixedGuardAndMerge(FixedGuardNode fixedGuard, PhaseContext context, CompareNode compare, ValueNode x, ValuePhiNode xPhi, ValueNode y, ValuePhiNode yPhi, AbstractMergeNode merge) {
        List<EndNode> mergePredecessors = merge.cfgPredecessors().snapshot();
        for (int i = 0; i < mergePredecessors.size(); ++i) {
            AbstractEndNode mergePredecessor = mergePredecessors.get(i);
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
            if (xs != null && ys != null && compare.condition().foldCondition(xs, ys, context.getConstantReflection(), compare.unorderedIsTrue()) == fixedGuard.isNegated()) {
                visitDeoptBegin(AbstractBeginNode.prevBegin(mergePredecessor), fixedGuard.getAction(), fixedGuard.getReason(), fixedGuard.graph());
            }
        }
    }

    private void visitDeoptBegin(AbstractBeginNode deoptBegin, DeoptimizationAction deoptAction, DeoptimizationReason deoptReason, StructuredGraph graph) {
        if (deoptBegin instanceof AbstractMergeNode) {
            AbstractMergeNode mergeNode = (AbstractMergeNode) deoptBegin;
            Debug.log("Visiting %s", mergeNode);
            FixedNode next = mergeNode.next();
            while (mergeNode.isAlive()) {
                AbstractEndNode end = mergeNode.forwardEnds().first();
                AbstractBeginNode newBeginNode = findBeginNode(end);
                visitDeoptBegin(newBeginNode, deoptAction, deoptReason, graph);
            }
            assert next.isAlive();
            AbstractBeginNode newBeginNode = findBeginNode(next);
            visitDeoptBegin(newBeginNode, deoptAction, deoptReason, graph);
            return;
        } else if (deoptBegin.predecessor() instanceof IfNode) {
            IfNode ifNode = (IfNode) deoptBegin.predecessor();
            AbstractBeginNode otherBegin = ifNode.trueSuccessor();
            LogicNode conditionNode = ifNode.condition();
            FixedGuardNode guard = graph.add(new FixedGuardNode(conditionNode, deoptReason, deoptAction, deoptBegin == ifNode.trueSuccessor()));
            FixedWithNextNode pred = (FixedWithNextNode) ifNode.predecessor();
            AbstractBeginNode survivingSuccessor;
            if (deoptBegin == ifNode.trueSuccessor()) {
                survivingSuccessor = ifNode.falseSuccessor();
            } else {
                survivingSuccessor = ifNode.trueSuccessor();
            }
            graph.removeSplitPropagate(ifNode, survivingSuccessor);

            Node newGuard = guard;
            if (survivingSuccessor instanceof LoopExitNode) {
                newGuard = ProxyNode.forGuard(guard, survivingSuccessor, graph);
            }
            survivingSuccessor.replaceAtUsages(InputType.Guard, newGuard);

            Debug.log("Converting deopt on %-5s branch of %s to guard for remaining branch %s.", deoptBegin == ifNode.trueSuccessor() ? "true" : "false", ifNode, otherBegin);
            FixedNode next = pred.next();
            pred.setNext(guard);
            guard.setNext(next);
            survivingSuccessor.simplify(simplifierTool);
            return;
        }

        // We could not convert the control split - at least cut off control flow after the split.
        FixedWithNextNode deoptPred = deoptBegin;
        FixedNode next = deoptPred.next();

        if (!(next instanceof DeoptimizeNode)) {
            DeoptimizeNode newDeoptNode = graph.add(new DeoptimizeNode(deoptAction, deoptReason));
            deoptPred.setNext(newDeoptNode);
            assert deoptPred == newDeoptNode.predecessor();
            GraphUtil.killCFG(next);
        }
    }
}
