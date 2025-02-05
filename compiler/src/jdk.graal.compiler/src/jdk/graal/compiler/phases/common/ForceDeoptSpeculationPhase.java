/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.meta.SpeculationLog.NO_SPECULATION;

import java.util.Optional;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.DynamicDeoptimizeNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ImplicitNullCheckNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.util.GraphSignature;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Ensure every {@link DeoptimizeNode} has an associated speculation to detect deopt recompile
 * pathologies. In case the deoptimize node has been already lowered to a foreign call, this phase
 * looks for calls with the specified descriptor and updates the speculation in the arguments of the
 * call. Because of the need to update foreign calls, this phase must run before the last schedule
 * phase.
 */
public class ForceDeoptSpeculationPhase extends BasePhase<CoreProviders> {

    /**
     * The number of a times a missing speculation can recompile before an error is reported.
     */
    final int recompileCount;

    /**
     * Specifies the descriptor of deoptimize calls that have a speculation as an argument. Those
     * speculation arguments also have to be replaced if they are not proper speculations.
     */
    final ForeignCallDescriptor deoptimizeCallDescriptor;
    final int deoptimizeCallSpeculationReasonArgumentIndex;

    public ForceDeoptSpeculationPhase(int recompileCount, ForeignCallDescriptor deoptimizeCallDescriptor) {
        this.recompileCount = recompileCount;
        int speculationReasonArgumentIndex = -1;
        if (deoptimizeCallDescriptor != null) {
            Class<?>[] argumentTypes = deoptimizeCallDescriptor.getArgumentTypes();
            for (int i = 0; i < argumentTypes.length; i++) {
                if (SpeculationLog.SpeculationReason.class.equals(argumentTypes[i])) {
                    speculationReasonArgumentIndex = i;
                    break;
                }
            }
        }
        if (speculationReasonArgumentIndex >= 0) {
            this.deoptimizeCallDescriptor = deoptimizeCallDescriptor;
            this.deoptimizeCallSpeculationReasonArgumentIndex = speculationReasonArgumentIndex;
        } else {
            this.deoptimizeCallDescriptor = null;
            this.deoptimizeCallSpeculationReasonArgumentIndex = -1;
        }

    }

    public static class TooManyDeoptimizationsError extends GraalError {
        private static final long serialVersionUID = 0L;

        public TooManyDeoptimizationsError(String msg) {
            super(msg);
        }
    }

    public static final SpeculationReasonGroup VERIFY_SPECULATION = new SpeculationReasonGroup("VerifySpeculation", byte[].class, int.class, int.class);

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        SpeculationLog speculationLog = graph.getSpeculationLog();
        GraphSignature signature = new GraphSignature(graph);
        for (Node node : graph.getNodes()) {
            if (node instanceof DynamicDeoptimizeNode) {
                throw new GraalError("%s must be disabled to use this phase", DynamicDeoptimizeNode.class.getSimpleName());
            }
            if (node instanceof DeoptimizeNode deopt) {
                if (deopt.getSpeculation().equals(NO_SPECULATION)) {
                    SpeculationLog.Speculation speculation = createSpeculation(graph, signature, deopt);
                    deopt.setSpeculation(speculation);
                }
            }
            if (deoptimizeCallDescriptor != null) {
                if (node instanceof ForeignCallNode foreignCallNode && deoptimizeCallDescriptor.equals(foreignCallNode.getDescriptor())) {
                    ValueNode speculationReasonArgument = foreignCallNode.getArguments().get(deoptimizeCallSpeculationReasonArgumentIndex);
                    assert speculationReasonArgument.isJavaConstant() : "Speculation reason argument node is not a JavaConstant";
                    SpeculationLog.Speculation originalSpeculation = context.getMetaAccess().decodeSpeculation((JavaConstant) ((ConstantNode) speculationReasonArgument).getValue(),
                                    graph.getSpeculationLog());
                    if (NO_SPECULATION.equals(originalSpeculation)) {
                        SpeculationLog.Speculation newSpeculation = createSpeculation(graph, signature, foreignCallNode);
                        JavaConstant speculationConstant = context.getMetaAccess().encodeSpeculation(newSpeculation);
                        ConstantNode speculationNode = graph.addOrUnique(ConstantNode.forConstant(speculationConstant, context.getMetaAccess(), graph));
                        ((ConstantNode) speculationReasonArgument).replace(graph, speculationNode);
                    }
                }
            }
            if (node instanceof ImplicitNullCheckNode implicitNullCheck) {
                JavaConstant spec = implicitNullCheck.getDeoptSpeculation();
                SpeculationLog.Speculation speculation = spec != null ? context.getMetaAccess().decodeSpeculation(spec, speculationLog) : NO_SPECULATION;
                if (speculation.equals(NO_SPECULATION)) {
                    spec = context.getMetaAccess().encodeSpeculation(createSpeculation(graph, signature, implicitNullCheck));
                    JavaConstant actionAndReason = implicitNullCheck.getDeoptReasonAndAction();
                    if (actionAndReason == null) {
                        actionAndReason = context.getMetaAccess().encodeDeoptActionAndReason(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NullCheckException, 0);
                    }
                    implicitNullCheck.setImplicitDeoptimization(actionAndReason, spec);
                }
            }
        }
    }

    public static String getDeoptSummary(ProfilingInfo profilingInfo) {
        StringBuilder sb = new StringBuilder();
        for (DeoptimizationReason reason : DeoptimizationReason.values()) {
            if (reason != DeoptimizationReason.None) {
                int count = profilingInfo.getDeoptimizationCount(reason);
                if (!sb.isEmpty()) {
                    sb.append(", ");
                    sb.append(reason).append('=').append(count);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Check that a synthetic speculation for this deopt didn't previously fail and throw an
     * exception if it's happened too many times.
     */
    protected SpeculationLog.Speculation createSpeculation(StructuredGraph graph, GraphSignature signature, ValueNode deopt) {
        for (int i = 0; i < getMaximumDeoptCount(graph); i++) {
            SpeculationLog.SpeculationReason reason = VERIFY_SPECULATION.createSpeculationReason(signature.getSignature(), signature.getId(deopt), i);
            if (graph.getSpeculationLog().maySpeculate(reason)) {
                return graph.getSpeculationLog().speculate(reason);
            }
        }
        throw reportTooManySpeculationFailures(deopt);
    }

    @SuppressWarnings("unused")
    protected int getMaximumDeoptCount(StructuredGraph graph) {
        return recompileCount;
    }

    /**
     * Report a deopt cycle.
     */
    protected GraalError reportTooManySpeculationFailures(ValueNode deopt) {
        throw new TooManyDeoptimizationsError("deopt taken too many times: " + deopt + " " + getDeoptSummary(deopt.graph().getProfilingInfo()));
    }
}
