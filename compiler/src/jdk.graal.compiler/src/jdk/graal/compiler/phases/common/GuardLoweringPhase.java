/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.graph.ScheduledNodeIterator;

import java.util.ListIterator;

/**
 * This phase lowers {@link GuardNode GuardNodes} into corresponding control-flow structure and
 * {@link DeoptimizeNode DeoptimizeNodes}.
 *
 * This allow to enter the {@link GuardsStage#FIXED_DEOPTS FIXED_DEOPTS} stage of the graph where
 * all node that may cause deoptimization are fixed.
 * <p>
 * It first makes a schedule in order to know where the control flow should be placed. Then, for
 * each block, it applies two passes. The first one tries to replace null-check guards with implicit
 * null checks performed by access to the objects that need to be null checked. The second phase
 * does the actual control-flow expansion of the remaining {@link GuardNode GuardNodes}.
 */
public class GuardLoweringPhase extends BasePhase<CoreProviders> implements FloatingGuardPhase {

    private static class LowerGuards extends ScheduledNodeIterator {

        private boolean useGuardIdAsDebugId;

        LowerGuards(boolean useGuardIdAsDebugId) {
            this.useGuardIdAsDebugId = useGuardIdAsDebugId;
        }

        @Override
        protected void processNode(Node node, HIRBlock block, ScheduleResult schedule, ListIterator<Node> iter) {
            if (node instanceof GuardNode) {
                GuardNode guard = (GuardNode) node;
                FixedWithNextNode lowered = guard.lowerGuard();
                if (lowered != null) {
                    replaceCurrent(lowered);
                } else {
                    lowerToIf(guard);
                }
            }
        }

        @SuppressWarnings("try")
        private void lowerToIf(GuardNode guard) {
            try (DebugCloseable position = guard.withNodeSourcePosition()) {
                StructuredGraph graph = guard.graph();
                AbstractBeginNode fastPath = graph.add(new BeginNode());
                fastPath.setNodeSourcePosition(guard.getNoDeoptSuccessorPosition());
                @SuppressWarnings("deprecation")
                int debugId = useGuardIdAsDebugId ? guard.getId() : DeoptimizeNode.DEFAULT_DEBUG_ID;
                DeoptimizeNode deopt = graph.add(new DeoptimizeNode(guard.getAction(), guard.getReason(), debugId, guard.getSpeculation(), null));
                AbstractBeginNode deoptBranch = BeginNode.begin(deopt);
                AbstractBeginNode trueSuccessor;
                AbstractBeginNode falseSuccessor;
                if (guard.isNegated()) {
                    trueSuccessor = deoptBranch;
                    falseSuccessor = fastPath;
                } else {
                    trueSuccessor = fastPath;
                    falseSuccessor = deoptBranch;
                }
                IfNode ifNode = graph.add(new IfNode(guard.getCondition(), trueSuccessor, falseSuccessor,
                                (trueSuccessor == fastPath ? BranchProbabilityNode.ALWAYS_TAKEN_PROFILE : BranchProbabilityNode.NEVER_TAKEN_PROFILE)));
                guard.replaceAndDelete(fastPath);
                insert(ifNode, fastPath);
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After lowering guard %s", guard);
            }
        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.ifApplied(this, StageFlag.GUARD_LOWERING, graphState),
                        NotApplicable.when(!graphState.getGuardsStage().allowsFloatingGuards(), "Floating guards must be allowed"));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);
        ScheduleResult schedule = graph.getLastSchedule();

        for (HIRBlock block : schedule.getCFG().getBlocks()) {
            processBlock(block, schedule);
        }

        assert assertNoGuardsLeft(graph);
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.GUARD_LOWERING);
        graphState.setGuardsStage(GuardsStage.FIXED_DEOPTS);
    }

    private static boolean assertNoGuardsLeft(StructuredGraph graph) {
        assert graph.getNodes(GuardNode.TYPE).isEmpty();
        return true;
    }

    private static void processBlock(HIRBlock block, ScheduleResult schedule) {
        DebugContext debug = block.getBeginNode().getDebug();
        new LowerGuards(debug.isDumpEnabledForMethod() || debug.isLogEnabledForMethod()).processNodes(block, schedule);
    }
}
