/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import java.util.List;
import java.util.Optional;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.DynamicDeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * Abstract base class for phases that try to fold guards (in the form of fixed {@link IfNode}) into
 * fixed {@link DeoptimizingFixedWithNextNode} that use implicit expections.
 */
public abstract class UseTrappingOperationPhase extends BasePhase<LowTierContext> {

    public abstract boolean isSupportedReason(DeoptimizationReason reason);

    public abstract boolean canReplaceCondition(LogicNode condition, IfNode ifNode);

    public abstract boolean useAddressOptimization(AddressNode adr, LowTierContext context);

    public abstract DeoptimizingFixedWithNextNode tryReplaceExisting(StructuredGraph graph, AbstractBeginNode nonTrappingContinuation, AbstractBeginNode trappingContinuation, LogicNode condition,
                    IfNode ifNode, AbstractDeoptimizeNode deopt, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation, LowTierContext context);

    public abstract DeoptimizingFixedWithNextNode createImplicitNode(StructuredGraph graph, LogicNode condition, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation);

    public abstract boolean trueSuccessorIsDeopt();

    public abstract void finalAction(DeoptimizingFixedWithNextNode trappingVersionNode, LogicNode condition);

    public abstract void actionBeforeGuardRewrite(DeoptimizingFixedWithNextNode trappingVersionNode);

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void tryUseTrappingVersion(MetaAccessProvider metaAccessProvider, DynamicDeoptimizeNode deopt, LowTierContext context) {
        Node predecessor = deopt.predecessor();
        if (predecessor instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) predecessor;

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
                merge.getDebug().log("Non constant reason %s", merge);
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
            List<EndNode> predecessors = merge.cfgPredecessors().snapshot();
            for (AbstractEndNode end : predecessors) {
                Node endPredecesssor = end.predecessor();
                ValueNode thisReason = reasons != null ? reasons.get(index) : reason;
                ValueNode thisSpeculation = speculations != null ? speculations.get(index) : speculation;
                if (!merge.isAlive()) {
                    // When evacuating a merge the last successor simplfies the merge away so it
                    // must be handled specially.
                    assert predecessors.get(predecessors.size() - 1) == end : "must be last end";
                    endPredecesssor = deopt.predecessor();
                    thisSpeculation = deopt.getSpeculation();
                    thisReason = deopt.getActionAndReason();
                }

                index++;
                if (!thisReason.isConstant() || !thisSpeculation.isConstant()) {
                    end.getDebug().log("Non constant deopt %s", end);
                    continue;
                }
                DeoptimizationReason deoptimizationReason = metaAccessProvider.decodeDeoptReason(thisReason.asJavaConstant());
                Speculation speculationConstant = metaAccessProvider.decodeSpeculation(thisSpeculation.asJavaConstant(), deopt.graph().getSpeculationLog());
                tryUseTrappingVersion(deopt, endPredecesssor, deoptimizationReason, speculationConstant, thisReason.asJavaConstant(), thisSpeculation.asJavaConstant(), context);
            }
        }
    }

    protected void tryUseTrappingVersion(AbstractDeoptimizeNode deopt, Node predecessor, DeoptimizationReason deoptimizationReason, Speculation speculation, JavaConstant deoptReasonAndAction,
                    JavaConstant deoptSpeculation, LowTierContext context) {
        assert predecessor != null;
        if (!GraalServices.supportsArbitraryImplicitException() && !isSupportedReason(deoptimizationReason)) {
            deopt.getDebug().log(DebugContext.INFO_LEVEL, "Not a null check / unreached / arithmetic %s", predecessor);
            return;
        }
        assert speculation != null;
        if (!GraalServices.supportsArbitraryImplicitException() && !speculation.equals(SpeculationLog.NO_SPECULATION)) {
            deopt.getDebug().log(DebugContext.INFO_LEVEL, "Has a speculation %s", predecessor);
            return;
        }

        // Skip over loop exit nodes.
        Node pred = predecessor;
        while (pred instanceof LoopExitNode) {
            pred = pred.predecessor();
        }
        if (pred instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) pred;
            if (merge.phis().isEmpty()) {
                for (AbstractEndNode end : merge.cfgPredecessors().snapshot()) {
                    checkPredecessor(deopt, end.predecessor(), deoptReasonAndAction, deoptSpeculation, context);
                }
            }
        } else if (pred instanceof AbstractBeginNode) {
            checkPredecessor(deopt, pred, deoptReasonAndAction, deoptSpeculation, context);
        } else {
            deopt.getDebug().log(DebugContext.INFO_LEVEL, "Not a Begin or Merge %s", pred);
        }
    }

    protected void checkPredecessor(AbstractDeoptimizeNode deopt, Node predecessor, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation, LowTierContext context) {
        Node current = predecessor;
        AbstractBeginNode branch = null;
        while (current instanceof AbstractBeginNode) {
            branch = (AbstractBeginNode) current;
            if (branch.anchored().isNotEmpty()) {
                // some input of the deopt framestate is anchored to this branch
                return;
            }
            current = current.predecessor();
        }
        if (current instanceof IfNode) {
            IfNode ifNode = (IfNode) current;
            if (trueSuccessorIsDeopt()) {
                if (branch != ifNode.trueSuccessor()) {
                    return;
                }
            } else {
                if (branch != ifNode.falseSuccessor()) {
                    return;
                }
            }
            LogicNode condition = ifNode.condition();
            if (canReplaceCondition(condition, ifNode)) {
                replaceWithTrappingVersion(deopt, ifNode, condition, deoptReasonAndAction, deoptSpeculation, context);
            }
        }
    }

    protected void replaceWithTrappingVersion(AbstractDeoptimizeNode deopt, IfNode ifNode, LogicNode condition, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation,
                    LowTierContext context) {
        StructuredGraph graph = deopt.graph();
        AbstractBeginNode nonTrappingContinuation = trueSuccessorIsDeopt() ? ifNode.falseSuccessor() : ifNode.trueSuccessor();
        AbstractBeginNode trappingContinuation = trueSuccessorIsDeopt() ? ifNode.trueSuccessor() : ifNode.falseSuccessor();
        DeoptimizingFixedWithNextNode trappingVersionNode = null;
        trappingVersionNode = tryReplaceExisting(graph, nonTrappingContinuation, trappingContinuation, condition, ifNode, deopt, deoptReasonAndAction, deoptSpeculation, context);
        if (trappingVersionNode == null) {
            // Need to add a null check node.
            trappingVersionNode = createImplicitNode(graph, condition, deoptReasonAndAction, deoptSpeculation);
            graph.replaceSplit(ifNode, trappingVersionNode, nonTrappingContinuation);
            graph.getOptimizationLog().report(getClass(), "NullCheckInsertion", ifNode);
        }
        trappingVersionNode.setStateBefore(deopt.stateBefore());
        actionBeforeGuardRewrite(trappingVersionNode);
        /*
         * We now have the pattern NullCheck/BeginNode/... It's possible some node is using the
         * BeginNode as a guard input, so replace guard users of the Begin with the NullCheck and
         * then remove the Begin from the graph.
         */
        nonTrappingContinuation.replaceAtUsages(trappingVersionNode, InputType.Guard);
        if (nonTrappingContinuation instanceof BeginNode) {
            GraphUtil.unlinkFixedNode(nonTrappingContinuation);
            nonTrappingContinuation.safeDelete();
        }
        finalAction(trappingVersionNode, condition);
        GraphUtil.killCFG(trappingContinuation);
        GraphUtil.tryKillUnused(condition);
    }

}
