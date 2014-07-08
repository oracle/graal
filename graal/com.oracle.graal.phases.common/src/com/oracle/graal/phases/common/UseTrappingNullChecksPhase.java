/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class UseTrappingNullChecksPhase extends BasePhase<LowTierContext> {

    private static final DebugMetric metricTrappingNullCheck = Debug.metric("TrappingNullCheck");
    private static final DebugMetric metricTrappingNullCheckUnreached = Debug.metric("TrappingNullCheckUnreached");
    private static final DebugMetric metricTrappingNullCheckDynamicDeoptimize = Debug.metric("TrappingNullCheckDynamicDeoptimize");

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        if (context.getTarget().implicitNullCheckLimit <= 0) {
            return;
        }
        assert graph.getGuardsStage().ordinal() >= GuardsStage.AFTER_FSA.ordinal();

        for (DeoptimizeNode deopt : graph.getNodes(DeoptimizeNode.class)) {
            tryUseTrappingNullCheck(deopt, deopt.predecessor(), deopt.reason(), deopt.getSpeculation());
        }
        for (DynamicDeoptimizeNode deopt : graph.getNodes(DynamicDeoptimizeNode.class)) {
            tryUseTrappingNullCheck(context.getMetaAccess(), deopt);
        }
    }

    private static void tryUseTrappingNullCheck(MetaAccessProvider metaAccessProvider, DynamicDeoptimizeNode deopt) {
        Node predecessor = deopt.predecessor();
        if (predecessor instanceof MergeNode) {
            MergeNode merge = (MergeNode) predecessor;

            // Process each predecessor at the merge, unpacking the reasons and speculations as
            // needed.
            ValueNode reason = deopt.getActionAndReason();
            ValuePhiNode reasonPhi = null;
            List<ValueNode> reasons = null;
            int expectedPhis = 0;

            if (reason instanceof ValuePhiNode) {
                reasonPhi = (ValuePhiNode) reason;
                if (reasonPhi.merge() != merge) {
                    return;
                }
                reasons = reasonPhi.values().snapshot();
                expectedPhis++;
            } else if (!reason.isConstant()) {
                return;
            }

            ValueNode speculation = deopt.getSpeculation();
            ValuePhiNode speculationPhi = null;
            List<ValueNode> speculations = null;
            if (speculation instanceof ValuePhiNode) {
                speculationPhi = (ValuePhiNode) speculation;
                if (speculationPhi.merge() != merge) {
                    return;
                }
                speculations = speculationPhi.values().snapshot();
                expectedPhis++;
            }

            if (merge.phis().count() != expectedPhis) {
                return;
            }

            int index = 0;
            for (AbstractEndNode end : merge.cfgPredecessors().snapshot()) {
                ValueNode thisReason = reasons != null ? reasons.get(index) : reason;
                ValueNode thisSpeculation = speculations != null ? speculations.get(index++) : speculation;
                if (!thisReason.isConstant() || !thisSpeculation.isConstant() || !thisSpeculation.asConstant().equals(Constant.NULL_OBJECT)) {
                    continue;
                }
                DeoptimizationReason deoptimizationReason = metaAccessProvider.decodeDeoptReason(thisReason.asConstant());
                tryUseTrappingNullCheck(deopt, end.predecessor(), deoptimizationReason, null);
            }
        }
    }

    private static void tryUseTrappingNullCheck(AbstractDeoptimizeNode deopt, Node predecessor, DeoptimizationReason deoptimizationReason, Constant speculation) {
        if (deoptimizationReason != DeoptimizationReason.NullCheckException && deoptimizationReason != DeoptimizationReason.UnreachedCode) {
            return;
        }
        if (speculation != null && !speculation.equals(Constant.NULL_OBJECT)) {
            return;
        }
        if (predecessor instanceof MergeNode) {
            MergeNode merge = (MergeNode) predecessor;
            if (merge.phis().isEmpty()) {
                for (AbstractEndNode end : merge.cfgPredecessors().snapshot()) {
                    checkPredecessor(deopt, end.predecessor(), deoptimizationReason);
                }
            }
        } else if (predecessor instanceof BeginNode) {
            checkPredecessor(deopt, predecessor, deoptimizationReason);
        }
    }

    private static void checkPredecessor(AbstractDeoptimizeNode deopt, Node predecessor, DeoptimizationReason deoptimizationReason) {
        Node current = predecessor;
        BeginNode branch = null;
        while (current instanceof BeginNode) {
            branch = (BeginNode) current;
            if (branch.anchored().isNotEmpty()) {
                // some input of the deopt framestate is anchored to this branch
                return;
            }
            current = current.predecessor();
        }
        if (current instanceof IfNode) {
            IfNode ifNode = (IfNode) current;
            if (branch != ifNode.trueSuccessor()) {
                return;
            }
            LogicNode condition = ifNode.condition();
            if (condition instanceof IsNullNode) {
                replaceWithTrappingNullCheck(deopt, ifNode, condition, deoptimizationReason);
            }
        }
    }

    private static void replaceWithTrappingNullCheck(AbstractDeoptimizeNode deopt, IfNode ifNode, LogicNode condition, DeoptimizationReason deoptimizationReason) {
        metricTrappingNullCheck.increment();
        if (deopt instanceof DynamicDeoptimizeNode) {
            metricTrappingNullCheckDynamicDeoptimize.increment();
        }
        if (deoptimizationReason == DeoptimizationReason.UnreachedCode) {
            metricTrappingNullCheckUnreached.increment();
        }
        IsNullNode isNullNode = (IsNullNode) condition;
        BeginNode nonTrappingContinuation = ifNode.falseSuccessor();
        BeginNode trappingContinuation = ifNode.trueSuccessor();
        NullCheckNode trappingNullCheck = deopt.graph().add(new NullCheckNode(isNullNode.getValue()));
        trappingNullCheck.setStateBefore(deopt.stateBefore());
        deopt.graph().replaceSplit(ifNode, trappingNullCheck, nonTrappingContinuation);

        /*
         * We now have the pattern NullCheck/BeginNode/... It's possible some node is using the
         * BeginNode as a guard input, so replace guard users of the Begin with the NullCheck and
         * then remove the Begin from the graph.
         */
        nonTrappingContinuation.replaceAtUsages(InputType.Guard, trappingNullCheck);
        FixedNode next = nonTrappingContinuation.next();
        nonTrappingContinuation.clearSuccessors();
        trappingNullCheck.setNext(next);
        nonTrappingContinuation.safeDelete();

        GraphUtil.killCFG(trappingContinuation);
        if (isNullNode.usages().isEmpty()) {
            GraphUtil.killWithUnusedFloatingInputs(isNullNode);
        }
    }
}
