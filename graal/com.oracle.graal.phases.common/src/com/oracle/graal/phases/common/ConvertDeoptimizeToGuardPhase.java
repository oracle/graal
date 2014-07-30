/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;

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
public class ConvertDeoptimizeToGuardPhase extends Phase {
    private SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(null, null, null, false);

    private static BeginNode findBeginNode(FixedNode startNode) {
        return GraphUtil.predecessorIterable(startNode).filter(BeginNode.class).first();
    }

    @Override
    protected void run(final StructuredGraph graph) {
        assert graph.hasValueProxies() : "ConvertDeoptimizeToGuardPhase always creates proxies";
        if (graph.getNodes(DeoptimizeNode.class).isEmpty()) {
            return;
        }
        for (DeoptimizeNode d : graph.getNodes(DeoptimizeNode.class)) {
            assert d.isAlive();
            visitDeoptBegin(BeginNode.prevBegin(d), d.action(), d.reason(), graph);
        }

        for (FixedGuardNode fixedGuard : graph.getNodes(FixedGuardNode.class)) {

            BeginNode pred = BeginNode.prevBegin(fixedGuard);
            if (pred instanceof MergeNode) {
                MergeNode merge = (MergeNode) pred;
                if (fixedGuard.condition() instanceof CompareNode) {
                    CompareNode compare = (CompareNode) fixedGuard.condition();
                    List<AbstractEndNode> mergePredecessors = merge.cfgPredecessors().snapshot();

                    Constant[] xs = IfNode.constantValues(compare.getX(), merge, true);
                    if (xs == null) {
                        continue;
                    }
                    Constant[] ys = IfNode.constantValues(compare.getY(), merge, true);
                    if (ys == null) {
                        continue;
                    }
                    for (int i = 0; i < mergePredecessors.size(); ++i) {
                        AbstractEndNode mergePredecessor = mergePredecessors.get(i);
                        if (!mergePredecessor.isAlive()) {
                            break;
                        }
                        if (xs[i] == null) {
                            continue;
                        }
                        if (ys[i] == null) {
                            continue;
                        }
                        if (xs[i].getKind() != Kind.Object && ys[i].getKind() != Kind.Object &&
                                        compare.condition().foldCondition(xs[i], ys[i], null, compare.unorderedIsTrue()) == fixedGuard.isNegated()) {
                            visitDeoptBegin(BeginNode.prevBegin(mergePredecessor), fixedGuard.getAction(), fixedGuard.getReason(), graph);
                        }
                    }
                }
            }
        }

        new DeadCodeEliminationPhase().apply(graph);
    }

    private void visitDeoptBegin(BeginNode deoptBegin, DeoptimizationAction deoptAction, DeoptimizationReason deoptReason, StructuredGraph graph) {
        if (deoptBegin instanceof MergeNode) {
            MergeNode mergeNode = (MergeNode) deoptBegin;
            Debug.log("Visiting %s", mergeNode);
            FixedNode next = mergeNode.next();
            while (mergeNode.isAlive()) {
                AbstractEndNode end = mergeNode.forwardEnds().first();
                BeginNode newBeginNode = findBeginNode(end);
                visitDeoptBegin(newBeginNode, deoptAction, deoptReason, graph);
            }
            assert next.isAlive();
            BeginNode newBeginNode = findBeginNode(next);
            visitDeoptBegin(newBeginNode, deoptAction, deoptReason, graph);
            return;
        } else if (deoptBegin.predecessor() instanceof IfNode) {
            IfNode ifNode = (IfNode) deoptBegin.predecessor();
            BeginNode otherBegin = ifNode.trueSuccessor();
            LogicNode conditionNode = ifNode.condition();
            FixedGuardNode guard = graph.add(new FixedGuardNode(conditionNode, deoptReason, deoptAction, deoptBegin == ifNode.trueSuccessor()));
            FixedWithNextNode pred = (FixedWithNextNode) ifNode.predecessor();
            BeginNode survivingSuccessor;
            if (deoptBegin == ifNode.trueSuccessor()) {
                survivingSuccessor = ifNode.falseSuccessor();
            } else {
                survivingSuccessor = ifNode.trueSuccessor();
            }
            graph.removeSplitPropagate(ifNode, survivingSuccessor);
            ProxyNode proxyGuard = null;
            for (Node n : survivingSuccessor.usages().snapshot()) {
                if (n instanceof GuardNode || n instanceof ProxyNode) {
                    // Keep wired to the begin node.
                } else {
                    // Rewire to the fixed guard.
                    if (survivingSuccessor instanceof LoopExitNode) {
                        if (proxyGuard == null) {
                            proxyGuard = ProxyNode.forGuard(guard, survivingSuccessor, graph);
                        }
                        n.replaceFirstInput(survivingSuccessor, proxyGuard);
                    } else {
                        n.replaceFirstInput(survivingSuccessor, guard);
                    }
                }
            }
            survivingSuccessor.simplify(simplifierTool);
            Debug.log("Converting deopt on %-5s branch of %s to guard for remaining branch %s.", deoptBegin == ifNode.trueSuccessor() ? "true" : "false", ifNode, otherBegin);
            FixedNode next = pred.next();
            pred.setNext(guard);
            guard.setNext(next);
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
