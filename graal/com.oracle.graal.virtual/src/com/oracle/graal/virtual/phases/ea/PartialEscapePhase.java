/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.virtual.phases.ea;

import static com.oracle.graal.compiler.common.GraalOptions.EscapeAnalysisIterations;
import static com.oracle.graal.compiler.common.GraalOptions.EscapeAnalyzeOnly;
import static com.oracle.graal.virtual.phases.ea.PartialEscapePhase.Options.OptEarlyReadElimination;

import java.util.Set;

import jdk.internal.jvmci.options.Option;
import jdk.internal.jvmci.options.OptionType;
import jdk.internal.jvmci.options.OptionValue;

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;
import com.oracle.graal.nodes.spi.VirtualizableAllocation;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.schedule.SchedulePhase;
import com.oracle.graal.phases.tiers.PhaseContext;

public class PartialEscapePhase extends EffectsPhase<PhaseContext> {

    static class Options {
        //@formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final OptionValue<Boolean> OptEarlyReadElimination = new OptionValue<>(true);
        //@formatter:on
    }

    private final boolean readElimination;
    private final BasePhase<PhaseContext> cleanupPhase;

    public PartialEscapePhase(boolean iterative, CanonicalizerPhase canonicalizer) {
        this(iterative, OptEarlyReadElimination.getValue(), canonicalizer, null);
    }

    public PartialEscapePhase(boolean iterative, CanonicalizerPhase canonicalizer, BasePhase<PhaseContext> cleanupPhase) {
        this(iterative, OptEarlyReadElimination.getValue(), canonicalizer, cleanupPhase);
    }

    public PartialEscapePhase(boolean iterative, boolean readElimination, CanonicalizerPhase canonicalizer, BasePhase<PhaseContext> cleanupPhase) {
        super(iterative ? EscapeAnalysisIterations.getValue() : 1, canonicalizer);
        this.readElimination = readElimination;
        this.cleanupPhase = cleanupPhase;
    }

    @Override
    protected void postIteration(StructuredGraph graph, PhaseContext context, Set<Node> changedNodes) {
        super.postIteration(graph, context, changedNodes);
        if (cleanupPhase != null) {
            cleanupPhase.apply(graph, context);
        }
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        if (VirtualUtil.matches(graph, EscapeAnalyzeOnly.getValue())) {
            if (readElimination || graph.getNodes().filterInterface(VirtualizableAllocation.class).isNotEmpty()) {
                runAnalysis(graph, context);
            }
        }
    }

    @Override
    protected Closure<?> createEffectsClosure(PhaseContext context, SchedulePhase schedule, ControlFlowGraph cfg) {
        for (VirtualObjectNode virtual : cfg.graph.getNodes(VirtualObjectNode.TYPE)) {
            virtual.resetObjectId();
        }
        assert schedule != null;
        if (readElimination) {
            return new PEReadEliminationClosure(schedule, context.getMetaAccess(), context.getConstantReflection());
        } else {
            return new PartialEscapeClosure.Final(schedule, context.getMetaAccess(), context.getConstantReflection());
        }
    }
}
