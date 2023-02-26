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
package org.graalvm.compiler.core.amd64;

import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.DynamicDeoptimizeNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.MultiGuardNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase;
import org.graalvm.compiler.phases.common.UseTrappingOperationPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * @see UseTrappingNullChecksPhase for details
 *
 *      This phase tries to find {@code =0} checks that can be folded together with a
 *      {@link IntegerDivRemNode} to save the explicit check.
 */
public class UseTrappingDivPhase extends BasePhase<LowTierContext> {

    private static boolean conditionIsZeroCheck(LogicNode condition, ValueNode divisor) {
        if (condition instanceof IntegerEqualsNode) {
            IntegerEqualsNode eq = (IntegerEqualsNode) condition;
            return eq.getX() == divisor && eq.getY().isConstant() && eq.getY().asJavaConstant().asLong() == 0L;
        }
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        if (!GraalOptions.FloatingDivNodes.getValue(graph.getOptions())) {
            return;
        }
        if (!GraalServices.supportsArbitraryImplicitException()) {
            return;
        }
        EconomicMap<IntegerEqualsNode, IntegerDivRemNode> trappingReplaceTargets = null;
        ScheduleResult sched = null;
        for (IntegerDivRemNode divRem : graph.getNodes(IntegerDivRemNode.TYPE)) {
            if (!(divRem instanceof SignedDivNode || divRem instanceof SignedRemNode)) {
                continue;
            }
            ValueNode divisor = divRem.getY();
            ValueNode dividend = divRem.getX();
            if (divRem.getZeroGuard() instanceof MultiGuardNode) {
                // both the dividend and the divisor had a speculation attached, ignore
            } else if (divRem.getZeroGuard() instanceof BeginNode) {
                // regular begin case
                BeginNode divGuard = (BeginNode) divRem.getZeroGuard();
                if (divGuard.predecessor() instanceof IfNode) {
                    IfNode ifNode = (IfNode) divGuard.predecessor();
                    if (ifNode.falseSuccessor() == divGuard) {
                        // we only care about single usage cases, ignore complex other cases
                        if (conditionIsZeroCheck(ifNode.condition(), divisor) && ifNode.condition().hasExactlyOneUsage()) {
                            if (trappingReplaceTargets == null) {
                                trappingReplaceTargets = EconomicMap.create();
                                SchedulePhase.runWithoutContextOptimizations(graph, SchedulingStrategy.EARLIEST);
                                sched = graph.getLastSchedule();
                            }
                            // condition ensures that divisor is dominated by condition, now do
                            // the
                            // same for the dividend
                            Block ifBlock = sched.getNodeToBlockMap().get(ifNode);
                            Block dividendBlock = sched.getNodeToBlockMap().get(dividend);
                            if (dividendBlock == null) {
                                assert dividend instanceof PhiNode;
                                dividendBlock = sched.getNodeToBlockMap().get(((PhiNode) dividend).merge());
                            }
                            if (AbstractControlFlowGraph.dominates(dividendBlock, ifBlock)) {
                                trappingReplaceTargets.put((IntegerEqualsNode) ifNode.condition(), divRem);
                            }
                        }
                    }
                }
            }
        }
        if (trappingReplaceTargets != null) {
            new Instance(trappingReplaceTargets).run(graph, context);
        }
    }

    static class Instance extends UseTrappingOperationPhase {

        final EconomicMap<IntegerEqualsNode, IntegerDivRemNode> trappingReplaceTargets;

        Instance(EconomicMap<IntegerEqualsNode, IntegerDivRemNode> trappingReplaceTargets) {
            this.trappingReplaceTargets = trappingReplaceTargets;
        }

        @Override
        public boolean isSupportedReason(DeoptimizationReason reason) {
            return reason == DeoptimizationReason.ArithmeticException;
        }

        @Override
        public boolean canReplaceCondition(LogicNode condition, IfNode ifNode) {
            if (condition instanceof IntegerEqualsNode) {
                return trappingReplaceTargets.containsKey((IntegerEqualsNode) condition);
            }
            return false;
        }

        @Override
        public boolean useAddressOptimization(AddressNode adr, LowTierContext context) {
            return false;
        }

        @Override
        public DeoptimizingFixedWithNextNode tryReplaceExisting(StructuredGraph graph, AbstractBeginNode nonTrappingContinuation, AbstractBeginNode trappingContinuation, LogicNode condition,
                        IfNode ifNode, AbstractDeoptimizeNode deopt, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation, LowTierContext context) {
            return null;
        }

        @Override
        public DeoptimizingFixedWithNextNode createImplicitNode(StructuredGraph graph, LogicNode condition, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation) {
            assert condition instanceof IntegerEqualsNode;
            IntegerEqualsNode ieq = (IntegerEqualsNode) condition;
            IntegerDivRemNode divRem = trappingReplaceTargets.get(ieq);
            ValueNode dividend = divRem.getX();
            ValueNode divisor = divRem.getY();
            IntegerDivRemNode divRemFixed = null;
            if (divRem instanceof SignedDivNode) {
                divRemFixed = graph.add(new SignedDivNode(dividend, divisor, null));
            } else if (divRem instanceof SignedRemNode) {
                divRemFixed = graph.add(new SignedRemNode(dividend, divisor, null));
            } else {
                throw GraalError.shouldNotReachHere("divRem is null or has unexpected type: " + divRem);
            }
            divRemFixed.setImplicitDeoptimization(deoptReasonAndAction, deoptSpeculation);
            GraalError.guarantee(divRemFixed.canDeoptimize(), "Fixed representation must deopt since we replaced a 0 check");
            return divRemFixed;
        }

        @Override
        public boolean trueSuccessorIsDeopt() {
            return true;
        }

        @Override
        public void finalAction(DeoptimizingFixedWithNextNode trappingVersionNode, LogicNode condition) {
            assert trappingVersionNode instanceof IntegerDivRemNode;
            IntegerDivRemNode fixedNonTrappingVersion = trappingReplaceTargets.get((IntegerEqualsNode) condition);
            fixedNonTrappingVersion.replaceAtUsages(trappingVersionNode);
            GraphUtil.unlinkFixedNode(fixedNonTrappingVersion);
            fixedNonTrappingVersion.safeDelete();
        }

        @Override
        public void actionBeforeGuardRewrite(DeoptimizingFixedWithNextNode trappingVersionNode) {

        }

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, LowTierContext context) {
            MetaAccessProvider metaAccessProvider = context.getMetaAccess();
            for (DeoptimizeNode deopt : graph.getNodes(DeoptimizeNode.TYPE)) {
                tryUseTrappingVersion(deopt, deopt.predecessor(), deopt.getReason(),
                                deopt.getSpeculation(), deopt.getActionAndReason(metaAccessProvider).asJavaConstant(),
                                deopt.getSpeculation(metaAccessProvider).asJavaConstant(), context);
            }
            for (DynamicDeoptimizeNode deopt : graph.getNodes(DynamicDeoptimizeNode.TYPE)) {
                tryUseTrappingVersion(metaAccessProvider, deopt, context);
            }

        }
    }

}
