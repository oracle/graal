/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.GraalOptions.OptImplicitNullChecks;

import java.util.Optional;

import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.DeoptimizingFixedWithNextNode;
import jdk.graal.compiler.nodes.DynamicDeoptimizeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.NullCheckNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

public class UseTrappingNullChecksPhase extends UseTrappingOperationPhase {

    public static class Options {

        // @formatter:off
        @Option(help = "Uses traps for null checks instead of explicit null-checks. " +
                       "This can improve performance because fewer explicit null checks have to be performed.", type = OptionType.Expert)
        public static final OptionKey<Boolean> UseTrappingNullChecks = new OptionKey<>(true);
        // @formatter:on
    }

    @Override
    public Optional<BasePhase.NotApplicable> notApplicableTo(GraphState graphState) {
        return BasePhase.NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        // This phase creates {@link OffsetAddressNode}s that needs to be lowered.
                        BasePhase.NotApplicable.unlessRunBefore(this, StageFlag.ADDRESS_LOWERING, graphState),
                        BasePhase.NotApplicable.when(!graphState.getGuardsStage().areFrameStatesAtDeopts(), "This should happen after FSA"));
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        assert context.getTarget().implicitNullCheckLimit > 0 : "The implicitNullCheckLimit should be greater than 0.";
        if (!Options.UseTrappingNullChecks.getValue(graph.getOptions())) {
            return;
        }
        MetaAccessProvider metaAccessProvider = context.getMetaAccess();
        for (DeoptimizeNode deopt : graph.getNodes(DeoptimizeNode.TYPE)) {
            tryUseTrappingVersion(deopt, deopt.predecessor(), deopt.getSpeculation(), deopt.getActionAndReason(metaAccessProvider).asJavaConstant(),
                            deopt.getSpeculation(metaAccessProvider).asJavaConstant(), context);
        }
        for (DynamicDeoptimizeNode deopt : graph.getNodes(DynamicDeoptimizeNode.TYPE)) {
            tryUseTrappingVersion(metaAccessProvider, deopt, context);
        }
    }

    @Override
    public boolean canReplaceCondition(LogicNode condition, IfNode ifNode) {
        return condition instanceof IsNullNode;
    }

    @Override
    public boolean useAddressOptimization(AddressNode adr, LowTierContext context) {
        return adr.getMaxConstantDisplacement() < context.getTarget().implicitNullCheckLimit;
    }

    @Override
    public boolean isSupportedReason(DeoptimizationReason reason) {
        return reason == DeoptimizationReason.NullCheckException || reason != DeoptimizationReason.UnreachedCode || reason == DeoptimizationReason.TypeCheckedInliningViolated;
    }

    @Override
    public DeoptimizingFixedWithNextNode tryReplaceExisting(StructuredGraph graph, AbstractBeginNode nonTrappingContinuation, AbstractBeginNode trappingContinuation, LogicNode condition,
                    IfNode ifNode, AbstractDeoptimizeNode deopt, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation, LowTierContext context) {
        IsNullNode isNullNode = (IsNullNode) condition;
        FixedNode nextNonTrapping = nonTrappingContinuation.next();
        ValueNode value = isNullNode.getValue();
        if (OptImplicitNullChecks.getValue(graph.getOptions())) {
            if (nextNonTrapping instanceof FixedAccessNode) {
                FixedAccessNode fixedAccessNode = (FixedAccessNode) nextNonTrapping;
                AddressNode address = fixedAccessNode.getAddress();
                if (fixedAccessNode.canNullCheck() && useAddressOptimization(address, context)) {
                    ValueNode base = address.getBase();
                    ValueNode index = address.getIndex();
                    // allow for architectures which cannot fold an
                    // intervening uncompress out of the address chain
                    if (base != null && base instanceof CompressionNode) {
                        base = ((CompressionNode) base).getValue();
                    }
                    if (index != null && index instanceof CompressionNode) {
                        index = ((CompressionNode) index).getValue();
                    }
                    if (((base == value && index == null) || (base == null && index == value))) {
                        // Opportunity for implicit null check as part of an existing read
                        // found!
                        fixedAccessNode.setStateBefore(deopt.stateBefore());
                        fixedAccessNode.setUsedAsNullCheck(true);
                        fixedAccessNode.setImplicitDeoptimization(deoptReasonAndAction, deoptSpeculation);
                        graph.removeSplit(ifNode, nonTrappingContinuation);
                        graph.getOptimizationLog().report(UseTrappingNullChecksPhase.class, "ImplicitNullCheck", isNullNode);
                        return fixedAccessNode;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public DeoptimizingFixedWithNextNode createImplicitNode(StructuredGraph graph, LogicNode condition, JavaConstant deoptReasonAndAction, JavaConstant deoptSpeculation) {
        IsNullNode isNullNode = (IsNullNode) condition;
        return graph.add(NullCheckNode.create(isNullNode.getValue(), deoptReasonAndAction, deoptSpeculation));
    }

    @Override
    public boolean trueSuccessorIsDeopt() {
        return true;
    }

    @Override
    public void finalAction(DeoptimizingFixedWithNextNode trappingVersionNode, LogicNode condition) {
        // nothing to do
    }

    @Override
    public void actionBeforeGuardRewrite(DeoptimizingFixedWithNextNode trappingVersionNode) {
        // nothing to do
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }
}
