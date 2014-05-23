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

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.virtual.phases.ea.PartialEscapePhase.Options.*;

import java.util.*;
import java.util.function.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

public class PartialEscapePhase extends EffectsPhase<PhaseContext> {

    static class Options {
        //@formatter:off
        @Option(help = "")
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
        assert schedule != null;
        if (readElimination) {
            return new PEReadEliminationClosure(schedule, context.getMetaAccess(), context.getConstantReflection(), context.getAssumptions());
        } else {
            return new PartialEscapeClosure.Final(schedule, context.getMetaAccess(), context.getConstantReflection(), context.getAssumptions());
        }
    }

    public static Map<Invoke, Double> getHints(StructuredGraph graph) {
        ToDoubleFunction<FixedNode> probabilities = new FixedNodeProbabilityCache();
        Map<Invoke, Double> hints = null;
        for (CommitAllocationNode commit : graph.getNodes().filter(CommitAllocationNode.class)) {
            double sum = 0;
            double invokeSum = 0;
            for (Node commitUsage : commit.usages()) {
                for (Node usage : commitUsage.usages()) {
                    if (usage instanceof FixedNode) {
                        sum += probabilities.applyAsDouble((FixedNode) usage);
                    } else {
                        if (usage instanceof MethodCallTargetNode) {
                            invokeSum += probabilities.applyAsDouble(((MethodCallTargetNode) usage).invoke().asNode());
                        }
                        for (Node secondLevelUage : usage.usages()) {
                            if (secondLevelUage instanceof FixedNode) {
                                sum += probabilities.applyAsDouble(((FixedNode) secondLevelUage));
                            }
                        }
                    }
                }
            }
            // TODO(lstadler) get rid of this magic number
            if (sum > 100 && invokeSum > 0) {
                for (Node commitUsage : commit.usages()) {
                    for (Node usage : commitUsage.usages()) {
                        if (usage instanceof MethodCallTargetNode) {
                            if (hints == null) {
                                hints = new HashMap<>();
                            }
                            Invoke invoke = ((MethodCallTargetNode) usage).invoke();
                            hints.put(invoke, sum / invokeSum);
                        }
                    }
                }
            }
        }
        return hints;
    }
}
