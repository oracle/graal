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
package org.graalvm.compiler.phases.common;

import static org.graalvm.compiler.core.common.GraalOptions.OptImplicitNullChecks;

import java.util.Optional;

import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.DynamicDeoptimizeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.NullCheckNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.phases.tiers.LowTierContext;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

public class UseTrappingNullChecksPhase extends UseTrappingOperationPhase {

    public static class Options {

        // @formatter:off
        @Option(help = "Use traps for null checks instead of explicit null-checks", type = OptionType.Expert)
        public static final OptionKey<Boolean> UseTrappingNullChecks = new OptionKey<>(true);
        // @formatter:on
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        // This phase creates {@link OffsetAddressNode}s that needs to be lowered.
                        NotApplicable.unlessRunBefore(this, StageFlag.ADDRESS_LOWERING, graphState),
                        NotApplicable.when(!graphState.getGuardsStage().areFrameStatesAtDeopts(), "This should happen after FSA"));
    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        assert context.getTarget().implicitNullCheckLimit > 0 : "The implicitNullCheckLimit should be greater than 0.";
        if (!Options.UseTrappingNullChecks.getValue(graph.getOptions())) {
            return;
        }
        MetaAccessProvider metaAccessProvider = context.getMetaAccess();
        for (DeoptimizeNode deopt : graph.getNodes(DeoptimizeNode.TYPE)) {
            tryUseTrappingVersion(deopt, deopt.predecessor(), deopt.getReason(), deopt.getSpeculation(), deopt.getActionAndReason(metaAccessProvider).asJavaConstant(),
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
