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

import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.FrameStateVerification;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.graph.ReentrantNodeIterator;
import jdk.vm.ci.code.BytecodeFrame;

/**
 * This phase transfers {@link FrameState} nodes from {@link StateSplit} nodes to
 * {@link DeoptimizingNode}s.
 *
 * This allows the graph to enter the {@link GuardsStage#AFTER_FSA AFTER_FSA} stage, where no new
 * nodes that may cause deoptimizations can be introduced anymore.
 * <p>
 * This Phase processes the graph in post order, assigning the {@link FrameState} from the last
 * {@link StateSplit} node to {@link DeoptimizingNode}s.
 */
public class FrameStateAssignmentPhase extends Phase {

    private static final class FrameStateAssignmentClosure extends ReentrantNodeIterator.NodeIteratorClosure<FrameState> {

        @Override
        protected FrameState processNode(FixedNode node, FrameState previousState) {
            FrameState currentState = previousState;

            if (node instanceof DeoptimizingNode.DeoptBefore) {
                DeoptimizingNode.DeoptBefore deopt = (DeoptimizingNode.DeoptBefore) node;
                if (deopt.canDeoptimize() && deopt.stateBefore() == null) {
                    GraalError.guarantee(currentState != null, "no FrameState at DeoptimizingNode %s", deopt);
                    deopt.setStateBefore(currentState);
                }
                if (deopt.canDeoptimize() && deopt.validateDeoptFrameStates() && !deopt.stateBefore().isValidForDeoptimization()) {
                    throw GraalError.shouldNotReachHere(String.format("Invalid framestate for %s: %s", deopt, deopt.stateBefore())); // ExcludeFromJacocoGeneratedReport
                }
            }

            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState stateAfter = stateSplit.stateAfter();
                if (stateAfter != null) {
                    if (stateAfter.bci == BytecodeFrame.INVALID_FRAMESTATE_BCI) {
                        currentState = null;
                    } else {
                        currentState = stateAfter;
                    }
                    stateSplit.setStateAfter(null);
                }
            }

            if (node instanceof DeoptimizingNode.DeoptDuring) {
                DeoptimizingNode.DeoptDuring deopt = (DeoptimizingNode.DeoptDuring) node;
                if (deopt.canDeoptimize() && deopt.stateDuring() == null) {
                    GraalError.guarantee(currentState != null, "no FrameState at DeoptimizingNode %s", deopt);
                    deopt.computeStateDuring(currentState);
                }
                if (deopt.canDeoptimize() && deopt.validateDeoptFrameStates() && !deopt.stateDuring().isValidForDeoptimization()) {
                    throw GraalError.shouldNotReachHere(String.format("Invalid framestate for %s: %s", deopt, deopt.stateDuring()));
                }
            }

            if (node instanceof DeoptimizingNode.DeoptAfter) {
                DeoptimizingNode.DeoptAfter deopt = (DeoptimizingNode.DeoptAfter) node;
                if (deopt.canDeoptimize() && deopt.stateAfter() == null) {
                    GraalError.guarantee(currentState != null, "no FrameState at DeoptimizingNode %s", deopt);
                    deopt.setStateAfter(currentState);
                }
                if (deopt.canDeoptimize() && deopt.validateDeoptFrameStates() && !deopt.stateAfter().isValidForDeoptimization()) {
                    throw GraalError.shouldNotReachHere(String.format("Invalid framestate for %s: %s", deopt, deopt.stateAfter()));
                }
            }

            return currentState;
        }

        @Override
        protected FrameState merge(AbstractMergeNode merge, List<FrameState> states) {
            FrameState singleFrameState = singleFrameState(states);
            return singleFrameState == null ? merge.stateAfter() : singleFrameState;
        }

        @Override
        protected FrameState afterSplit(AbstractBeginNode node, FrameState oldState) {
            return oldState;
        }

        @Override
        protected EconomicMap<LoopExitNode, FrameState> processLoop(LoopBeginNode loop, FrameState initialState) {
            return ReentrantNodeIterator.processLoop(this, loop, initialState).exitStates;
        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.ifApplied(this, StageFlag.FSA, graphState),
                        NotApplicable.when(graphState.getGuardsStage().allowsFloatingGuards(), "Floating guards should not be allowed."),
                        NotApplicable.when(graphState.getGuardsStage().areFrameStatesAtDeopts(), "This phase must run before FSA"));
    }

    @Override
    protected void run(StructuredGraph graph) {
        assert !hasFloatingDeopts(graph);
        ReentrantNodeIterator.apply(new FrameStateAssignmentClosure(), graph.start(), null);
        GraphUtil.killAllWithUnusedFloatingInputs(graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()), false);
        if (graph.hasLoops() && graph.isLastCFGValid()) {
            // CFGLoops are computed differently after FSA, see CFGLoop#getLoopExits().
            graph.getLastCFG().resetLoopInformation();
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterFSA();
        graphState.weakenFrameStateVerification(FrameStateVerification.NONE);
        graphState.addFutureStageRequirement(StageFlag.CANONICALIZATION); // See GR-38666.
    }

    private static boolean hasFloatingDeopts(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof DeoptimizingNode && GraphUtil.isFloatingNode(n)) {
                DeoptimizingNode deoptimizingNode = (DeoptimizingNode) n;
                if (deoptimizingNode.canDeoptimize()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static FrameState singleFrameState(List<FrameState> states) {
        FrameState singleState = states.get(0);
        for (int i = 1; i < states.size(); ++i) {
            if (states.get(i) != singleState) {
                return null;
            }
        }
        if (singleState != null && singleState.bci != BytecodeFrame.INVALID_FRAMESTATE_BCI) {
            return singleState;
        }
        return null;
    }

    @Override
    public boolean checkContract() {
        // TODO GR-1409
        return false;
    }
}
