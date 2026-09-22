/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.loop.phases;

import static jdk.graal.compiler.core.common.GraalOptions.MaximumDesiredSize;

import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.core.common.type.Stamp;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.collections.EconomicSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.RetryableBailoutException;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.VirtualState.NodePositionClosure;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariableHelper;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopFragment;
import jdk.graal.compiler.nodes.loop.LoopFragmentInside;
import jdk.graal.compiler.nodes.loop.LoopFragmentWhole;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.util.IntegerHelper;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.LoopUtility;

/**
 * GraalVM loop transformations.
 */
public abstract class LoopTransformations {

    private LoopTransformations() {
        // does not need to be instantiated
    }

    private static int saturatedConstantMaxTripCount(Loop loop) {
        try {
            return Math.toIntExact(LoopUtility.tripCountSignedExact(loop.counted()));
        } catch (ArithmeticException e) {
            return Integer.MAX_VALUE;
        }
    }

    public static LoopFragmentInside peel(Loop loop) {
        loop.detectCounted();
        double frequencyBefore = loop.localLoopFrequency();
        AbstractBeginNode mainExit = null;
        if (loop.isCounted()) {
            mainExit = loop.counted().getCountedExit();
        } else if (loop.loopBegin().loopExits().count() == 1) {
            mainExit = loop.loopBegin().loopExits().first();
            if (!(mainExit.predecessor() instanceof IfNode)) {
                mainExit = null;
            }
        }
        LoopFragmentInside inside = loop.inside().duplicate();
        inside.insertBefore(loop);
        loop.loopBegin().incrementPeelings();
        loop.loopBegin().graph().getOptimizationLog().withProperty("peelings", loop.loopBegin().peelings()).report(LoopTransformations.class, "LoopPeeling", loop.loopBegin());
        if (mainExit != null) {
            adaptCountedLoopExitProbability(mainExit, frequencyBefore - 1D);
        }
        return inside;
    }

    @SuppressWarnings("try")
    public static void fullUnroll(Loop loop, CoreProviders context, CanonicalizerPhase canonicalizer) {
        fullUnroll(loop, context, canonicalizer, true);
    }

    @SuppressWarnings("try")
    public static void fullUnroll(Loop loop, CoreProviders context, CanonicalizerPhase canonicalizer, boolean requireCounted) {
        LoopBeginNode loopBegin = loop.loopBegin();
        StructuredGraph graph = loopBegin.graph();
        loop.detectCounted();
        if (loop.isCounted() && loop.counted().isConstantMaxTripCount()) {
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            int fullUnrollFactor = saturatedConstantMaxTripCount(loop);
            LoopUtility.updateDescendantLoopCloneFactors(loopsData, loop, fullUnrollFactor);
        } else {
            GraalError.guarantee(!requireCounted, "Full unroll requires a counted loop with a constant max trip count: %s", loop);
        }
        int initialNodeCount = graph.getNodeCount();
        SimplifierTool defaultSimplifier = GraphUtil.getDefaultSimplifier(context, canonicalizer.getCanonicalizeReads(), graph.getAssumptions(), graph.getOptions());
        /*
         * IMPORTANT: Canonicalizations inside the body of the remaining loop can introduce new
         * control flow that is not automatically picked up by the control flow graph computation of
         * the original loop data structure, thus we disable simplification and manually simplify
         * conditions in the peeled iteration to simplify the exit path.
         */
        CanonicalizerPhase c = canonicalizer.copyWithoutSimplification();
        EconomicSetNodeEventListener l = new EconomicSetNodeEventListener();
        int peelings = 0;
        try (NodeEventScope ev = graph.trackNodeEvents(l)) {
            while (!loopBegin.isDeleted()) {
                Mark newNodes = graph.getMark();
                /*
                 * Mark is not enough for the canonicalization of the floating nodes in the unrolled
                 * code since pre-existing constants are not new nodes. Therefore, we canonicalize
                 * (without simplification) all floating nodes changed during peeling but only
                 * simplify new (in the peeled iteration) ones.
                 */
                EconomicSetNodeEventListener peeledListener = new EconomicSetNodeEventListener();
                try (NodeEventScope peeledScope = graph.trackNodeEvents(peeledListener)) {
                    LoopTransformations.peel(loop);
                }
                c.applyIncremental(graph, context, peeledListener.getNodes());
                loop.invalidateFragmentsAndIVs();
                for (Node n : graph.getNewNodes(newNodes)) {
                    if (n.isAlive() && (n instanceof IfNode || n instanceof SwitchNode || n instanceof FixedGuardNode || n instanceof BeginNode)) {
                        Simplifiable s = (Simplifiable) n;
                        s.simplify(defaultSimplifier);
                        graph.getOptimizationLog().report(LoopTransformations.class, "LoopFullUnrollCfgSimplification", n);
                    }
                }
                if (graph.getNodeCount() > initialNodeCount + MaximumDesiredSize.getValue(graph.getOptions()) * 2 ||
                                peelings > DefaultLoopPolicies.Options.FullUnrollMaxIterations.getValue(graph.getOptions())) {
                    throw new RetryableBailoutException("FullUnroll : Graph seems to grow out of proportion");
                }
                peelings++;
            }
        }
        // Canonicalize with the original canonicalizer to capture all simplifications
        canonicalizer.applyIncremental(graph, context, l.getNodes());
        loop.loopBegin().graph().getOptimizationLog().report(LoopTransformations.class, "LoopFullUnroll", loop.loopBegin());
    }

    public static void unswitch(Loop loop, List<ControlSplitNode> controlSplitNodeSet, boolean isTrivialUnswitch) {
        final ControlSplitNode firstNode = controlSplitNodeSet.iterator().next();
        final StructuredGraph graph = firstNode.graph();

        graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Before unswitching %s", controlSplitNodeSet);

        LoopFragmentWhole originalLoop = loop.whole();

        if (!isTrivialUnswitch) {
            loop.loopBegin().incrementUnswitches();
        }

        // create new control split out of loop
        ControlSplitNode newControlSplit = (ControlSplitNode) firstNode.copyWithInputs();
        originalLoop.entryPoint().replaceAtPredecessor(newControlSplit);

        /*
         * The code below assumes that all of the control split nodes have the same successor
         * structure, which should have been enforced by findUnswitchable.
         */
        Iterator<Position> successors = firstNode.successorPositions().iterator();
        assert successors.hasNext();
        // original loop is used as first successor
        Position firstPosition = successors.next();
        AbstractBeginNode originalLoopBegin = BeginNode.begin(originalLoop.entryPoint());
        firstPosition.set(newControlSplit, originalLoopBegin);
        originalLoopBegin.setNodeSourcePosition(firstPosition.get(firstNode).getNodeSourcePosition());

        while (successors.hasNext()) {
            Position position = successors.next();
            // create a new loop duplicate and connect it.
            LoopFragmentWhole duplicateLoop = originalLoop.duplicate();
            AbstractBeginNode newBegin = BeginNode.begin(duplicateLoop.entryPoint());
            newBegin.setNodeSourcePosition(position.get(firstNode).getNodeSourcePosition());
            position.set(newControlSplit, newBegin);

            // For each cloned ControlSplitNode, simplify the proper path
            for (ControlSplitNode controlSplitNode : controlSplitNodeSet) {
                ControlSplitNode duplicatedControlSplit = duplicateLoop.getDuplicatedNode(controlSplitNode);
                if (duplicatedControlSplit.isAlive()) {
                    AbstractBeginNode survivingSuccessor = (AbstractBeginNode) position.get(duplicatedControlSplit);
                    survivingSuccessor.replaceAtUsages(newBegin, InputType.Guard);
                    graph.removeSplitPropagate(duplicatedControlSplit, survivingSuccessor);
                }
            }
        }
        // original loop is simplified last to avoid deleting controlSplitNode too early
        for (ControlSplitNode controlSplitNode : controlSplitNodeSet) {
            if (controlSplitNode.isAlive()) {
                AbstractBeginNode survivingSuccessor = (AbstractBeginNode) firstPosition.get(controlSplitNode);
                survivingSuccessor.replaceAtUsages(originalLoopBegin, InputType.Guard);
                graph.removeSplitPropagate(controlSplitNode, survivingSuccessor);
            }
        }

        // TODO (gd) probabilities need some amount of fixup.. (probably also in other transforms)
        loop.loopBegin().graph().getOptimizationLog().withProperty("unswitches", loop.loopBegin().unswitches()).report(LoopTransformations.class, "LoopUnswitching", loop.loopBegin());
    }

    public static void partialUnroll(Loop loop, EconomicMap<LoopBeginNode, OpaqueNode> opaqueUnrolledStrides) {
        assert loop.loopBegin().isMainLoop();
        adaptCountedLoopExitProbability(loop.counted().getCountedExit(), loop.localLoopFrequency() / 2D);
        LoopUtility.updateDescendantLoopCloneFactors(loop.loopsData(), loop, 2);
        LoopFragmentInside newSegment = loop.inside().duplicate();
        newSegment.insertWithinAfter(loop, opaqueUnrolledStrides);
        loop.loopBegin().graph().getOptimizationLog().withProperty("unrollFactor", loop.loopBegin().getUnrollFactor()).report(LoopTransformations.class, "LoopPartialUnroll", loop.loopBegin());
    }

    /**
     * Create unique framestates for the loop exits of this loop: unique states ensure that virtual
     * instance nodes of this framestate are not shared with other framestates.
     *
     * Loop exit states and virtual object state inputs: The loop exit state can have a (transitive)
     * virtual object state input that is shared with other states outside the loop. Without a
     * dedicated state (with virtual object state inputs) for the loop exit state we can no longer
     * answer the question what is inside the loop (which virtual object state) and which is outside
     * given that we create new loop exits after existing ones. Thus, we create a dedicated state
     * for the exit that can later be duplicated cleanly.
     */
    public static void ensureExitsHaveUniqueStates(Loop loop) {
        if (loop.loopBegin().graph().getGuardsStage().areFrameStatesAtDeopts()) {
            return;
        }
        for (LoopExitNode lex : loop.loopBegin().loopExits()) {
            FrameState oldState = lex.stateAfter();
            lex.setStateAfter(lex.stateAfter().duplicateWithVirtualState());
            if (oldState.hasNoUsages()) {
                GraphUtil.killWithUnusedFloatingInputs(oldState);
            }
        }
        loop.invalidateFragmentsAndIVs();
    }

    // This function splits candidate loops into pre, main and post loops,
    // dividing the iteration space to facilitate the majority of iterations
    // being executed in a main loop, which will have RCE implemented upon it.
    // The initial loop form is constrained to single entry/exit, but can have
    // flow. The translation looks like:
    //
    //  @formatter:off
    //
    //       (Simple Loop entry)                   (Pre Loop Entry)
    //                |                                  |
    //         (LoopBeginNode)                    (LoopBeginNode)
    //                |                                  |
    //       (Loop Control Test)<------   ==>  (Loop control Test)<------
    //         /               \       \         /               \       \
    //    (Loop Exit)      (Loop Body) |    (Loop Exit)      (Loop Body) |
    //        |                |       |        |                |       |
    // (continue code)     (Loop End)  |  if (M < length)*   (Loop End)  |
    //                         \       /       /      \           \      /
    //                          ----->        /       |            ----->
    //                                       /  if ( ... )*
    //                                      /     /       \
    //                                     /     /         \
    //                                    /     /           \
    //                                   |     /     (Main Loop Entry)
    //                                   |    |             |
    //                                   |    |      (LoopBeginNode)
    //                                   |    |             |
    //                                   |    |     (Loop Control Test)<------
    //                                   |    |      /               \        \
    //                                   |    |  (Loop Exit)      (Loop Body) |
    //                                    \   \      |                |       |
    //                                     \   \     |            (Loop End)  |
    //                                      \   \    |                \       /
    //                                       \   \   |                 ------>
    //                                        \   \  |
    //                                      (Main Loop Merge)*
    //                                               |
    //                                      (Post Loop Entry)
    //                                               |
    //                                        (LoopBeginNode)
    //                                               |
    //                                       (Loop Control Test)<-----
    //                                        /               \       \
    //                                    (Loop Exit)     (Loop Body) |
    //                                        |               |       |
    //                                 (continue code)    (Loop End)  |
    //                                                         \      /
    //                                                          ----->
    //
    // Key: "*" = optional.
    // @formatter:on
    //
    // The value "M" is the maximal value of the loop trip for the original
    // loop. The value of "length" is applicable to the number of arrays found
    // in the loop but is reduced if some or all of the arrays are known to be
    // the same length as "M". The maximum number of tests can be equal to the
    // number of arrays in the loop, where multiple instances of an array are
    // subsumed into a single test for that arrays length.
    //
    // If the optional main loop entry tests are absent, the Pre Loop exit
    // connects to the Main loops entry and there is no merge hanging off the
    // main loops exit to converge flow from said tests. All split use data
    // flow is mitigated through phi(s) in the main merge if present and
    // passed through the main and post loop phi(s) from the originating pre
    // loop with final phi(s) and data flow patched to the "continue code".
    // The pre loop is constrained to one iteration for now and will likely
    // be updated to produce vector alignment if applicable.
    public static PreMainPostResult insertPrePostLoops(Loop loop) {
        assert loop.loopBegin().loopExits().isEmpty() || loop.loopBegin().graph().isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) ||
                        loop.counted().getCountedExit() instanceof LoopExitNode : "Can only unroll loops, if they have exits, if the counted exit is a regular loop exit " + loop;
        StructuredGraph graph = loop.loopBegin().graph();

        // prepare clean exit states
        ensureExitsHaveUniqueStates(loop);

        graph.getDebug().log("LoopTransformations.insertPrePostLoops %s", loop);

        LoopFragmentWhole preLoop = loop.whole();
        CountedLoopInfo preCounted = loop.counted();
        LoopBeginNode preLoopBegin = loop.loopBegin();
        /*
         * When transforming counted loops with multiple loop exits the counted exit is the one that
         * is interesting for the pre-main-post transformation since it is the regular, non-early,
         * exit.
         */
        final AbstractBeginNode preLoopExitNode = preCounted.getCountedExit();

        assert preLoop.nodes().contains(preLoopBegin);
        assert preLoop.nodes().contains(preLoopExitNode);

        LoopUtility.updateDescendantLoopCloneFactors(loop.loopsData(), loop, 2);

        /*
         * Duplicate the original loop two times, each duplication will create a merge for the loop
         * exits of the original loop and the duplication one.
         */
        LoopFragmentWhole mainLoop = preLoop.duplicate();
        LoopBeginNode mainLoopBegin = mainLoop.getDuplicatedNode(preLoopBegin);
        AbstractBeginNode mainLoopExitNode = mainLoop.getDuplicatedNode(preLoopExitNode);
        EndNode mainEndNode = getBlockEndAfterLoopExit(mainLoopExitNode);
        AbstractMergeNode mainMergeNode = mainEndNode.merge();
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After  duplication of main loop %s", mainLoop);

        LoopFragmentWhole postLoop = preLoop.duplicate();
        LoopBeginNode postLoopBegin = postLoop.getDuplicatedNode(preLoopBegin);
        AbstractBeginNode postLoopExitNode = postLoop.getDuplicatedNode(preLoopExitNode);
        EndNode postEndNode = getBlockEndAfterLoopExit(postLoopExitNode);
        AbstractMergeNode postMergeNode = postEndNode.merge();
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After post loop duplication");

        preLoopBegin.setPreLoop();
        mainLoopBegin.setMainLoop();
        postLoopBegin.setPostLoop();

        if (graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            // clear state to avoid problems with usages on the merge
            cleanupAndDeleteState(mainMergeNode);
            cleanupPostDominatingValues(mainLoopBegin, mainMergeNode, postEndNode);
            removeStateAndPhis(postMergeNode);
            /*
             * Fix the framestates for the pre loop exit node and the main loop exit node.
             *
             * The only exit that actually really exits the original loop is the loop exit of the
             * post-loop. All other paths have to fully go through pre->main->post loops. We can
             * never go from pre/main loop directly to the code after the loop, we always have to go
             * through the original loop header, thus we need to fix the correct state on the
             * pre/main loop exit.
             *
             * However, depending on the shape of the loop this is either
             *
             * for head counted loops: the loop header state with the values fixed
             *
             * for tail counted loops: the last state inside the body of the loop dominating the
             * tail check (This is different since tail counted loops have protection control flow
             * meaning it is possible to go pre -> after post, pre->main->after post, pre -> post ->
             * after post. For the protected main and post loops it is enough to deopt to the last
             * body state and the interpreter can then re-execute any failing counter check).
             *
             * For both scenarios we proxy the necessary nodes.
             */
            createExitState(preLoopBegin, (LoopExitNode) preLoopExitNode, loop.counted().isInverted(), preLoop);
            createExitState(mainLoopBegin, (LoopExitNode) mainLoopExitNode, loop.counted().isInverted(), mainLoop);
        }

        assert graph.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) || preLoopExitNode instanceof LoopExitNode : "Unrolling with proxies requires actual loop exit nodes as counted exits";
        rewirePreToMainPhis(preLoopBegin, mainLoop, preLoop, graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL) ? (LoopExitNode) preLoopExitNode : null, loop.counted().isInverted());

        AbstractEndNode postEntryNode = postLoopBegin.forwardEnd();
        // Exits have been merged, find the continuation below the merge
        FixedNode continuationNode = mainMergeNode.next();

        // In the case of no Bounds tests, we just flow right into the main loop
        AbstractBeginNode mainLandingNode = BeginNode.begin(postEntryNode);
        mainLoopExitNode.setNext(mainLandingNode);
        preLoopExitNode.setNext(mainLoopBegin.forwardEnd());

        // Add and update any phi edges as per merge usage as needed and update usages
        assert graph.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) ||
                        mainLoopExitNode instanceof LoopExitNode : "Unrolling with proxies requires actual loop exit nodes as counted exits";
        processPreLoopPhis(loop, graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL) ? (LoopExitNode) mainLoopExitNode : null, mainLoop, postLoop);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After processing pre loop phis");

        continuationNode.predecessor().clearSuccessors();
        postLoopExitNode.setNext(continuationNode);
        cleanupMerge(postMergeNode, postLoopExitNode);
        cleanupMerge(mainMergeNode, mainLandingNode);

        // Change the preLoop to execute one iteration for now
        if (graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            /*
             * The pre-loop exit's condition's induction variable start node might be already
             * re-written to be a phi of merged loop exits from a previous pre-main-post creation,
             * thus use an updated loop info.
             */
            loop.resetCounted();
            loop.detectCounted();
            updatePreLoopLimit(loop.counted());
        } else {
            updatePreLoopLimit(preCounted);
        }

        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After updating preloop limit");

        double originalFrequency = loop.localLoopFrequency();
        preLoopBegin.setLoopOrigFrequency(originalFrequency);
        mainLoopBegin.setLoopOrigFrequency(originalFrequency);
        postLoopBegin.setLoopOrigFrequency(originalFrequency);

        assert preLoopExitNode.predecessor() instanceof IfNode : Assertions.errorMessage(preLoopExitNode);
        assert mainLoopExitNode.predecessor() instanceof IfNode : Assertions.errorMessage(mainLoopExitNode);
        assert postLoopExitNode.predecessor() instanceof IfNode : Assertions.errorMessage(postLoopExitNode);

        /*
         * The bodies of pre and post loops are assumed to be executed just once. As the local loop
         * frequency is calculated from the loop exit probabilities, it has to be taken into account
         * how often the exit check is performed. If the loop is inverted, i.e., tail-counted, the
         * exit will be taken at the end of the first body execution. Thus, the frequency of the
         * exit check is 1. If the loop is head-counted, the exit check will be performed twice,
         * which is reflected by a frequency of 2. This results in the correct relative frequency
         * being propagated into the loop body.
         */
        final int prePostFrequency = loop.counted().isInverted() ? 1 : 2;
        adaptCountedLoopExitProbability(preLoopExitNode, prePostFrequency);
        adaptCountedLoopExitProbability(postLoopExitNode, prePostFrequency);

        if (graph.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            // The pre and post loops don't require safepoints at all
            for (SafepointNode safepoint : preLoop.nodes().filter(SafepointNode.class)) {
                graph.removeFixed(safepoint);
            }
            for (SafepointNode safepoint : postLoop.nodes().filter(SafepointNode.class)) {
                graph.removeFixed(safepoint);
            }
        }
        graph.getOptimizationLog().report(LoopTransformations.class, "PreMainPostInsertion", loop.loopBegin());

        return new PreMainPostResult(preLoopBegin, mainLoopBegin, postLoopBegin, preLoop, mainLoop, postLoop);
    }

    /**
     * Inject a split probability for the (counted) loop check that will result in a loop frequency
     * of 1 (in case this is the only loop exit). This implies that the loop body is expected to be
     * never entered.
     */
    private static void setSingleVisitedLoopFrequencySplitProbability(AbstractBeginNode lex) {
        IfNode ifNode = ((IfNode) lex.predecessor());
        boolean trueSucc = ifNode.trueSuccessor() == lex;
        ifNode.setTrueSuccessorProbability(BranchProbabilityData.injected(0.01, trueSucc));
    }

    /**
     * Inject a new branch probability for the condition dominating the given loop exit path. This
     * probability is based on the local frequency of the exit check. This calculation will act as
     * if the given loop exit is the only exit of the loop.
     */
    public static void adaptCountedLoopExitProbability(AbstractBeginNode lex, double newExitCheckFrequency) {
        invalidateCFGFrequencies(lex.graph().getLastCFG());
        double probability = 1.0D - 1.0D / newExitCheckFrequency;
        if (probability <= 0D) {
            setSingleVisitedLoopFrequencySplitProbability(lex);
            return;
        }
        IfNode ifNode = ((IfNode) lex.predecessor());
        boolean trueSucc = ifNode.trueSuccessor() == lex;
        ifNode.setTrueSuccessorProbability(BranchProbabilityData.injected(probability, trueSucc));
    }

    private static void invalidateCFGFrequencies(ControlFlowGraph cfg) {
        if (cfg != null) {
            cfg.invalidateFrequencies();
        }
    }

    public static class PreMainPostResult {
        private final LoopBeginNode preLoop;
        private final LoopBeginNode mainLoop;
        private final LoopBeginNode postLoop;

        private final LoopFragment preLoopFragment;
        private final LoopFragment mainLoopFragment;
        private final LoopFragment postLoopFragment;

        public PreMainPostResult(LoopBeginNode preLoop, LoopBeginNode mainLoop, LoopBeginNode postLoop, LoopFragment preLoopFragment, LoopFragment mainLoopFragment, LoopFragment postLoopFragment) {
            this.preLoop = preLoop;
            this.mainLoop = mainLoop;
            this.postLoop = postLoop;
            this.preLoopFragment = preLoopFragment;
            this.mainLoopFragment = mainLoopFragment;
            this.postLoopFragment = postLoopFragment;
        }

        public LoopFragment getPreLoopFragment() {
            return preLoopFragment;
        }

        public LoopFragment getMainLoopFragment() {
            return mainLoopFragment;
        }

        public LoopFragment getPostLoopFragment() {
            return postLoopFragment;
        }

        public LoopBeginNode getMainLoop() {
            return mainLoop;
        }

        public LoopBeginNode getPostLoop() {
            return postLoop;
        }

        public LoopBeginNode getPreLoop() {
            return preLoop;
        }
    }

    private static void cleanupPostDominatingValues(LoopBeginNode mainLoopBegin, AbstractMergeNode mainMergeNode, AbstractEndNode postEndNode) {
        /*
         * duplicating with loop proxies will create phis for all proxies on the newly introduced
         * merges, however after introducing the pre-main-post scheme all original usages outside of
         * the loop will go through the post loop, so we rewrite the new phis created and replace
         * all phis created on the merges after with the value proxies of the final(post) loop
         */
        for (LoopExitNode exit : mainLoopBegin.loopExits()) {
            for (ProxyNode proxy : exit.proxies()) {
                for (Node usage : proxy.usages().snapshot()) {
                    if (usage instanceof PhiNode && ((PhiNode) usage).merge() == mainMergeNode) {
                        assert usage instanceof PhiNode : Assertions.errorMessage(usage);
                        // replace with the post loop proxy
                        PhiNode pUsage = (PhiNode) usage;
                        // get the other input phi at pre loop end
                        Node v = pUsage.valueAt(0);
                        assert v instanceof PhiNode : Assertions.errorMessage(v);
                        PhiNode vP = (PhiNode) v;
                        usage.replaceAtUsages(vP.valueAt(postEndNode));
                        usage.safeDelete();
                    }
                }
            }
        }
        mainLoopBegin.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, mainLoopBegin.graph(), "After fixing post dominating proxy usages");
    }

    private static void rewirePreToMainPhis(LoopBeginNode preLoopBegin, LoopFragment mainLoop, LoopFragment preLoop, LoopExitNode preLoopCountedExit, boolean inverted) {
        // Update the main loop phi initialization to carry from the pre loop, use a snapshot
        // because guard prox nodes can reference the loop begin and change usage lists
        for (PhiNode prePhiNode : preLoopBegin.phis().snapshot()) {
            PhiNode mainPhiNode = mainLoop.getDuplicatedNode(prePhiNode);
            rewirePhi(prePhiNode, mainPhiNode, preLoopCountedExit, preLoop, inverted);
        }
        preLoopBegin.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, preLoopBegin.graph(), "After updating value flow from pre loop phi to main loop phi");
    }

    private static void cleanupAndDeleteState(StateSplit statesplit) {
        FrameState fs = statesplit.stateAfter();
        statesplit.setStateAfter(null);
        GraphUtil.killWithUnusedFloatingInputs(fs);
    }

    private static void removeStateAndPhis(AbstractMergeNode merge) {
        cleanupAndDeleteState(merge);
        for (PhiNode phi : merge.phis().snapshot()) {
            phi.safeDelete();
        }
        merge.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, merge.graph(), "After deleting unused phis");
    }

    private static void createExitState(LoopBeginNode begin, LoopExitNode lex, boolean inverted, LoopFragment loop) {
        FrameState stateToUse;
        if (inverted) {
            stateToUse = GraphUtil.findLastFrameState((FixedNode) lex.predecessor()).duplicateWithVirtualState();
        } else {
            stateToUse = begin.stateAfter().duplicateWithVirtualState();
        }
        stateToUse.applyToNonVirtual(new NodePositionClosure<>() {
            @Override
            public void apply(Node from, Position p) {
                final ValueNode toProxy = (ValueNode) p.get(from);
                if (toProxy instanceof VirtualObjectNode) {
                    /*
                     * VirtualObjectNodes: though they are leaf nodes they are considered to be
                     * inside a loop for duplication purposes of loop optimizations. However, we do
                     * not need/must proxy them: see LoopFragement::computeNodes for details.
                     */
                    return;
                }
                Node replacement;
                // we are reasoning about a framestate here, it can only ever have
                // InputType.Value inputs.
                if (loop.contains(toProxy)) {
                    replacement = lex.graph().addOrUnique(new ValueProxyNode(toProxy, lex));
                } else {
                    replacement = toProxy;
                }
                p.set(from, replacement);
            }
        });
        lex.setStateAfter(stateToUse);
        begin.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, begin.graph(), "After proxy-ing phis for exit state");
    }

    /**
     * Cleanup the merge and remove the predecessors too.
     */
    private static void cleanupMerge(AbstractMergeNode mergeNode, AbstractBeginNode landingNode) {
        for (EndNode end : mergeNode.cfgPredecessors().snapshot()) {
            mergeNode.removeEnd(end);
            end.safeDelete();
        }
        mergeNode.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, mergeNode.graph(), "After cleaning up merge %s", mergeNode);
        mergeNode.prepareDelete(landingNode);
        mergeNode.safeDelete();
    }

    private static void rewirePhi(PhiNode currentPhi, PhiNode outGoingPhi, LoopExitNode exitToProxy, LoopFragment loopToProxy, boolean inverted) {
        if (currentPhi.graph().isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL)) {
            ValueNode set = null;
            ValueNode toProxy = inverted ? currentPhi.singleBackValueOrThis() : currentPhi;
            set = toProxy;
            if (toProxy == null) {
                GraalError.guarantee(currentPhi instanceof GuardPhiNode, "Only guard phi nodes can have null inputs %s", currentPhi);
            } else if (loopToProxy.contains(toProxy)) {
                set = LoopFragmentInside.patchProxyAtPhi(currentPhi, exitToProxy, toProxy);
                assert set != null;
            }
            outGoingPhi.setValueAt(0, set);
        } else {
            outGoingPhi.setValueAt(0, currentPhi);
        }
    }

    private static void processPreLoopPhis(Loop preLoop, LoopExitNode mainLoopCountedExit, LoopFragmentWhole mainLoop, LoopFragmentWhole postLoop) {
        /*
         * Re-route values from the main loop to the post loop
         */
        LoopBeginNode preLoopBegin = preLoop.loopBegin();
        StructuredGraph graph = preLoopBegin.graph();
        for (PhiNode prePhiNode : preLoopBegin.phis().snapshot()) {
            PhiNode postPhiNode = postLoop.getDuplicatedNode(prePhiNode);
            PhiNode mainPhiNode = mainLoop.getDuplicatedNode(prePhiNode);

            rewirePhi(mainPhiNode, postPhiNode, mainLoopCountedExit, mainLoop, preLoop.counted().isInverted());

            /*
             * Update all usages of the pre phi node below the original loop with the post phi
             * nodes, these are already properly proxied if we have loop proxies
             */
            if (graph.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL)) {
                for (Node usage : prePhiNode.usages().snapshot()) {
                    if (usage == mainPhiNode) {
                        continue;
                    }
                    if (preLoop.isOutsideLoop(usage)) {
                        usage.replaceFirstInput(prePhiNode, postPhiNode);
                    }
                }
                for (Node node : preLoop.inside().nodes()) {
                    for (Node externalUsage : node.usages().snapshot()) {
                        if (preLoop.isOutsideLoop(externalUsage)) {
                            Node postUsage = postLoop.getDuplicatedNode(node);
                            assert postUsage != null;
                            externalUsage.replaceFirstInput(node, postUsage);
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the end of the block following the LoopExit.
     */
    private static EndNode getBlockEndAfterLoopExit(AbstractBeginNode exit) {
        FixedNode node = exit.next();
        // Find the last node after the exit blocks starts
        return getBlockEnd(node);
    }

    private static EndNode getBlockEnd(FixedNode node) {
        FixedNode curNode = node;
        while (curNode instanceof FixedWithNextNode) {
            curNode = ((FixedWithNextNode) curNode).next();
        }
        return (EndNode) curNode;
    }

    private static void updatePreLoopLimit(CountedLoopInfo preCounted) {
        // Update the pre loops limit test
        // Make new limit one iteration
        ValueNode newLimit = AddNode.add(preCounted.getBodyIVStart(), preCounted.getLimitCheckedIV().strideNode(), NodeView.DEFAULT);

        // Fetch the variable we are not replacing and configure the one we are
        ValueNode ub = preCounted.getLimit();
        IntegerHelper helper = preCounted.getCounterIntegerHelper();
        LogicNode entryCheck;
        if (preCounted.getDirection() == Direction.Up) {
            entryCheck = helper.createCompareNode(newLimit, ub, NodeView.DEFAULT);
        } else {
            entryCheck = helper.createCompareNode(ub, newLimit, NodeView.DEFAULT);
        }
        newLimit = ConditionalNode.create(entryCheck, newLimit, ub, NodeView.DEFAULT);
        // Re-wire the condition with the new limit
        CompareNode compareNode = (CompareNode) preCounted.getLimitTest().condition();
        compareNode.replaceFirstInput(ub, compareNode.graph().addOrUniqueWithInputs(newLimit));
    }

    /**
     * Find all unswichable control split nodes in the given loop. When multiple control split nodes
     * have the same invariant condition, group them together.
     *
     * @param loop search control split nodes in this loop.
     * @return the unswitchable control split nodes grouped by condition meaning that every control
     *         split node within the same inner list share the same condition (the key for the map).
     */
    public static EconomicMap<ValueNode, List<ControlSplitNode>> findUnswitchable(Loop loop) {
        EconomicMap<ValueNode, List<ControlSplitNode>> controls = EconomicMap.create(Equivalence.IDENTITY);
        for (IfNode ifNode : loop.whole().nodes().filter(IfNode.class)) {
            if (loop.isOutsideLoop(ifNode.condition())) {
                ValueNode invariantValue = ifNode.condition();
                List<ControlSplitNode> ifs = controls.get(invariantValue);
                if (ifs == null) {
                    ifs = new ArrayList<>();
                    controls.put(invariantValue, ifs);
                }
                ifs.add(ifNode);
            }
        }
        for (SwitchNode switchNode : loop.whole().nodes().filter(SwitchNode.class)) {
            if (switchNode.successors().count() > 1 && loop.isOutsideLoop(switchNode.value())) {
                ValueNode invariantValue = switchNode.value();
                List<ControlSplitNode> switchs = controls.get(invariantValue);
                if (switchs == null) {
                    switchs = new ArrayList<>();
                    switchs.add(switchNode);
                    controls.put(invariantValue, switchs);
                } else {
                    // The list is not empty because we always add a node when we create it and
                    // switch cannot match on boolean so we don't have to check before the cast.
                    if (((SwitchNode) switchs.get(0)).structureEquals(switchNode)) {
                        // Only collect switches which test the same values in the same order
                        switchs.add(switchNode);
                    }
                }
            }
        }

        return controls;
    }

    /**
     * Check for multiple usages of the loop condition. Partial unrolling will modify the condition
     * in place. Any other usages of the condition would therefore compute a different condition
     * than before. A shared loop condition indicates that the graph isn't properly optimized, so
     * don't bother with partial unrolling, especially if it would break things.
     */
    public static boolean countedLoopExitConditionHasMultipleUsages(Loop loop) {
        LogicNode condition = loop.counted().getLimitTest().condition();
        return condition.hasMoreThanOneUsage();
    }

    public static boolean strideAdditionOverflows(Loop loop) {
        final int bits = ((IntegerStamp) loop.counted().getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT)).getBits();
        long stride = loop.counted().getLimitCheckedIV().constantStride();
        try {
            LoopUtility.addExact(bits, stride, stride);
            return false;
        } catch (ArithmeticException ae) {
            return true;
        }
    }

    public static boolean isUnrollableLoop(Loop loop) {
        if (LoopUtility.excludeLoopFromOptimizer(loop)) {
            return false;
        }
        if (!loop.isCounted() || !loop.counted().getLimitCheckedIV().isConstantStride() || !loop.getCFGLoop().getChildren().isEmpty() || loop.loopBegin().loopEnds().count() != 1 ||
                        loop.loopBegin().loopExits().count() > 1 || loop.counted().isInverted()) {
            // loops without exits can be unrolled, inverted loops cannot be unrolled without
            // protecting their first iteration
            return false;
        }
        assert loop.counted().getDirection() != null;
        LoopBeginNode loopBegin = loop.loopBegin();
        LogicNode condition = loop.counted().getLimitTest().condition();
        if (!(condition instanceof CompareNode)) {
            return false;
        }
        if (((CompareNode) condition).condition() == CanonicalCondition.EQ) {
            condition.getDebug().log(DebugContext.VERBOSE_LEVEL, "isUnrollableLoop %s condition unsupported %s ", loopBegin, ((CompareNode) condition).condition());
            return false;
        }
        if (countedLoopExitConditionHasMultipleUsages(loop)) {
            return false;
        }
        if (strideAdditionOverflows(loop)) {
            condition.getDebug().log(DebugContext.VERBOSE_LEVEL, "isUnrollableLoop %s doubling the stride overflows %d", loopBegin, loop.counted().getLimitCheckedIV().constantStride());
            return false;
        }
        if (!loop.canDuplicateLoop()) {
            return false;
        }
        if (loopBegin.isMainLoop() || loopBegin.isSimpleLoop()) {
            // Flow-less loops to partial unroll for now. 3 blocks corresponds to an if that either
            // exits or continues the loop. There might be fixed and floating work within the loop
            // as well.
            if (loop.getCFGLoop().getBlocks().size() < 3) {
                return true;
            }
            condition.getDebug().log(DebugContext.VERBOSE_LEVEL, "isUnrollableLoop %s too large to unroll %s ", loopBegin, loop.getCFGLoop().getBlocks().size());
        }
        return false;
    }

    /**
     *
     * Perform loop inversion around the {@link IfNode} of the loop. The IfNode must be the counted
     * exit of the loop.
     *
     * @param loop the loop to be inverted
     * @param ifNode the {@link IfNode} that is used as the inversion condition: that one is moved
     *            to the loop end
     * @param originalProxyStamps the original stamps of all loop proxies before inversion, used to
     *            ensure a stamp never gets worse during inversion
     *
     * @return the not entered proxy values derived for the inverted loop
     */
    public static EconomicMap<ProxyNode, Node> invert(Loop loop, IfNode ifNode, EconomicMap<ProxyNode, Stamp> originalProxyStamps) {
        final LoopBeginNode lb = loop.loopBegin();
        final StructuredGraph graph = lb.graph();
        final DebugContext debug = graph.getDebug();
        assert graph.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL) : "Graph must be before stage " + StageFlag.VALUE_PROXY_REMOVAL + " but is " + graph.getGraphState();

        assert lb.loopExits().count() >= 1 : "Mus have at least 1 loop exit " + lb;
        assert lb.loopEnds().count() == 1 : "Must merge multi-end loops before inversion";
        assert loop.counted().getCountedExit() instanceof LoopExitNode : "Can only invert loop that have a loop exit node as counted exit";
        /*
         * In order to protect the inverted loop afterwards we need to find the proxied values
         * flowing out of the loop if the loop body is not actually entered. For this ideally we
         * would want to duplicate the loop, set the condition to false and canonicalize through it.
         * However, this is costly, thus we visit all proxy inputs recursively until we found all
         * floating nodes necessary on the path to the loop phis. We duplicate this set for the loop
         * phi forward inputs and have our set of proxy nodes for the protection logic.
         */
        EconomicMap<ProxyNode, NodeBitMap> proxyToChain = EconomicMap.create();
        EconomicMap<ProxyNode, Node> proxyZeroTripInputs = findZeroTripProxyValues(loop, proxyToChain);

        boolean trueSuccessor = ifNode.falseSuccessor() instanceof LoopExitNode;
        // move all anchored/guarded nodes to the loop header
        if (trueSuccessor) {
            ifNode.trueSuccessor().replaceAtUsages(lb, InputType.Guard, InputType.Anchor);
        } else {
            ifNode.falseSuccessor().replaceAtUsages(lb, InputType.Guard, InputType.Anchor);
        }

        final LoopExitNode lex = (LoopExitNode) loop.counted().getCountedExit();
        int count = lex.proxies().count();
        int size = proxyZeroTripInputs.size();
        assert count == size : count + " vs " + size;
        final LoopEndNode inversionEnd = loop.loopBegin().getSingleLoopEnd();

        IfNode newControlSplit = (IfNode) ifNode.copyWithInputs(true);
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "Inversion: After creation of new exit condition %s", ifNode);

        FixedWithNextNode fwn = (FixedWithNextNode) inversionEnd.predecessor();
        fwn.setNext(null);
        fwn.setNext(newControlSplit);
        BeginNode newBegin = graph.add(new BeginNode());
        newBegin.setNodeSourcePosition(lb.getNodeSourcePosition());
        if (trueSuccessor) {
            newControlSplit.setTrueSuccessor(newBegin);
            assert ifNode.trueSuccessor().hasNoUsages() : "During rewire " + ifNode.trueSuccessor() + " must not have usage";
        } else {
            newControlSplit.setFalseSuccessor(newBegin);
            assert ifNode.falseSuccessor().hasNoUsages() : "During rewire " + ifNode.falseSuccessor() + " must not have usage";
        }
        newBegin.setNext(inversionEnd);

        debug.dump(DebugContext.DETAILED_LEVEL, graph, "Inversion: After placing new loop condition %s at inversion end %s", ifNode, inversionEnd);
        rewireToLoopEnd(loop, lex, newControlSplit, proxyToChain, proxyZeroTripInputs, originalProxyStamps);
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "Inversion: After reparing all phi/iv/value proxy usages moved to the post dominating loop end");

        FixedWithNextNode fwn1 = (FixedWithNextNode) ifNode.predecessor();
        FixedNode next;
        fwn1.setNext(null);
        if (trueSuccessor) {
            next = ifNode.trueSuccessor();
            ifNode.setTrueSuccessor(null);
            ifNode.setFalseSuccessor(null);
            newControlSplit.setFalseSuccessor(lex);
        } else {
            next = ifNode.falseSuccessor();
            ifNode.setTrueSuccessor(null);
            ifNode.setFalseSuccessor(null);
            newControlSplit.setTrueSuccessor(lex);
        }
        fwn1.setNext(next);
        GraphUtil.killCFG(ifNode);
        lb.setCompilerInverted();
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "Inversion: After rewiring exit path");
        return proxyZeroTripInputs;
    }

    /**
     * Note: this code assumes it is only called with a head-counted, i.e. non-inverted, loop.
     *
     * Compute zero trip proxy values for the loop. They are needed to build a protection diamond
     * for inverted loops and used in the "loop-not-entered" path after the loop (through phis).
     *
     * Find the values of all loop proxies of the loop if it is exited upon its first iteration.
     * Since we are dealing with a head counted loop where the counted loop condition dominates the
     * rest of the loop body the only values that can be proxied here are floating node (chains)
     * since there must not be a fixed node on the exit path, else we will not be able to invert the
     * loop.
     */
    private static EconomicMap<ProxyNode, Node> findZeroTripProxyValues(Loop loop, EconomicMap<ProxyNode, NodeBitMap> proxyToChain) {
        EconomicMap<ProxyNode, Node> zeroTripProxyValues = EconomicMap.create();
        final LoopBeginNode lb = loop.loopBegin();
        final StructuredGraph graph = lb.graph();
        final LoopExitNode lex = (LoopExitNode) loop.counted().getCountedExit();
        for (ProxyNode proxy : lex.proxies()) {
            NodeBitMap allNodesUntilPhis = graph.createNodeBitMap();
            visitUntilPhiFromProxy(allNodesUntilPhis, loop, proxy.value());
            assert !loop.isOutsideLoop(proxy.value()) : "This phase assumes all proxied nodes are defined inside a loop and part of the loop fragment:" + proxy;
            /*
             * In order to properly capture the zero-trip tree of floating nodes we duplicate them
             * and then replace the phi with its input on the forward end predecessor only for the
             * new, duplicated, nodes and therefore have created a zero trip floating node tree with
             * the root (the phi) replaced.
             */
            proxyToChain.put(proxy, allNodesUntilPhis);
            Mark before = graph.getMark();
            UnmodifiableEconomicMap<Node, Node> duplicates = graph.addDuplicates(allNodesUntilPhis, graph, allNodesUntilPhis.count(), (EconomicMap<Node, Node>) null);
            for (PhiNode phi : lb.phis()) {
                phi.replaceAtMatchingUsages(phi.valueAt(lb.forwardEnd()), x -> graph.isNew(before, x));
            }
            /*
             * It is possible that the original loop proxied a FloatingGuardedNode that references
             * the loop begin. In this case we rewire the zero trip value to before the loop.
             */
            for (Node usage : lb.usages().snapshot()) {
                if (!graph.isNew(before, usage)) {
                    continue;
                }
                for (Position p : usage.inputPositions()) {
                    if ((p.getInputType() == InputType.Anchor || p.getInputType() == InputType.Guard) && p.get(usage) == lb) {
                        p.set(usage, AbstractBeginNode.prevBegin(lb.forwardEnd()));
                        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After updating guard/anchor edge for loop begin in zero trips %s", usage);
                    }
                }
            }

            ValueNode zeroTripVal = (ValueNode) duplicates.get(proxy.value());
            if (zeroTripVal == null) {
                assert lb.isPhiAtMerge(proxy.value()) : " cannot find replacement " + proxy.value() + " duplicates=" + duplicates + " found=" + allNodesUntilPhis;
                zeroTripVal = ((PhiNode) proxy.value()).valueAt(lb.forwardEnd());
            }
            zeroTripProxyValues.put(proxy, zeroTripVal);
            graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Proxy %s contains the transitive floating node set until loop phis of %s which is deducted to map to this zero trip val %s->%s",
                            proxy, allNodesUntilPhis, proxy.value(),
                            duplicates.get(proxy.value()));
        }
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating zero trip proxy values for %s", loop);
        return zeroTripProxyValues;
    }

    /**
     * Visit all input nodes from current until a node is either a loop phi, outside the loop or a
     * fixed node.
     */
    private static void visitUntilPhiFromProxy(NodeBitMap visited, Loop loop, ValueNode node) {
        ArrayDeque<Pair<Node, Node>> stack = new ArrayDeque<>();
        stack.push(Pair.create(node, null));
        while (!stack.isEmpty()) {
            Pair<Node, Node> cur = stack.pop();
            if (stopProcessingInputs(cur.getLeft(), cur.getRight(), loop, visited)) {
                continue;
            }
            visited.mark(cur.getLeft());
            for (Node input : cur.getLeft().inputs()) {
                if (input instanceof ValueNode) {
                    stack.push(Pair.create(input, cur.getLeft()));
                }
            }
        }
    }

    private static boolean stopProcessingInputs(Node current, Node usage, Loop loop, NodeBitMap visited) {
        if (visited.isMarked(current) || loop.loopBegin().isPhiAtMerge(current) || loop.isOutsideLoop(current)) {
            return true;
        }
        if (current instanceof FixedNode) {
            if (current == loop.loopBegin() && usage != null && isGuardEdge(current, usage)) {
                // we allow guard inputs to fixed node if they go to the loop header, any other
                // fixed node proxied is considered a problem
                return true;
            }
            GraalError.shouldNotReachHere(
                            "Head counted loops cannot proxy a fixed node as that node would be before " + "the loop exit condition meaning there is code between the exit condition " +
                                            "check and the loop begin which is not allowed for a loop to be head counted." + " Node " + current + " reached over " + visited + " loop=" + loop); // ExcludeFromJacocoGeneratedReport
        }
        return false;
    }

    private static boolean isGuardEdge(Node input, Node usage) {
        boolean foundGuardEdge = false;
        for (Position p : usage.inputPositions()) {
            if (p.get(usage) == input) {
                // if there are multiple inputs from usage to input of different input types and one
                // of them is non guard we want to fail
                if (p.getInputType() == InputType.Guard) {
                    foundGuardEdge = true;
                } else {
                    return false;
                }
            }
        }
        return foundGuardEdge;
    }

    /**
     * Rewire all usages of induction variables that have been dominating the loop body before the
     * the loop end place (i.e. the new exit condition and the loop exit and all involved proxies).
     *
     * @param proxyToChain
     * @param proxyZeroTripInputs
     * @param originalProxyStamps
     */
    private static void rewireToLoopEnd(Loop loop, LoopExitNode lex, IfNode newExitCondition, EconomicMap<ProxyNode, NodeBitMap> proxyToChain, EconomicMap<ProxyNode, Node> proxyZeroTripInputs,
                    EconomicMap<ProxyNode, Stamp> originalProxyStamps) {
        final StructuredGraph graph = lex.graph();
        /*
         * Every phi that is used in the rotation condition (that is now post dominating the loop
         * body) or in the loop proxies (where the exit is dominated by the new rotated test [which
         * in return post dominates the loop body]) needs to be replaced to point to the value on
         * the loop end.
         *
         * We are tempted to just go over all phis and do
         * phi.replaceAtMatchingUsages(phiValueNextIteration, x -> x == rotatedTest.condition() ||
         * exitProxies.contains(x));
         *
         * However, if there are cycles in phi assignments we can get into trouble e.g
         * LoopInversionTest#snippetTestLoopCarried.
         *
         * In such a scenario we cannot simply take the value from the prev iteration and set it to
         * the next iteration, but we have to remember the nodes which we already replaced and avoid
         * replacing them with new values.
         */
        class ReplacementPair {
            ValueNode input;
            ValueNode usage;
            ValueNode newNode;

            ReplacementPair(ValueNode input, ValueNode usage, ValueNode newNode) {
                this.input = input;
                this.usage = usage;
                this.newNode = newNode;
            }

        }

        NodeBitMap newNodes = graph.createNodeBitMap();
        EconomicMap<ProxyNode, UnmodifiableEconomicMap<Node, Node>> newProxyDuplicates = EconomicMap.create();

        /*
         * Loop Proxies:
         *
         * Loop proxies are special because they can proxy arbitrary values derived from the loop
         * body. If we only proxy loop phis or (derived) ivs, it would be easy to derive the next
         * iteration value for a loop proxy after inversion. However, since arbitrary values
         * produced (invariant) inside the loop can be proxied we need a different strategy.
         *
         * Thus, we take the tree of floating nodes derived for the zero trip proxy values and add
         * their duplicates to the graph. This is a new node set. Then we iterate all phis and ivs
         * and replace them with their next iteration value in this new proxy rooted tree of
         * floating nodes. This gives us a new floating node tree for the proxy we can use, which
         * has all old phis and ivs replaced with their next iteration values.
         */

        for (ProxyNode proxy : lex.proxies().snapshot()) {
            NodeBitMap allNodes = proxyToChain.get(proxy);
            UnmodifiableEconomicMap<Node, Node> newNodesProxy = graph.addDuplicates(allNodes, graph, allNodes.count(), (EconomicMap<Node, Node>) null);
            for (Node newNode : newNodesProxy.getValues()) {
                newNodes.markAndGrow(newNode);
            }
            newProxyDuplicates.put(proxy, newNodesProxy);
        }

        ArrayList<ReplacementPair> flatReplacements = new ArrayList<>();
        LoopBeginNode lb = loop.loopBegin();

        /*
         * We need to rewire values using ivs as well as values accessing the plain phi
         */
        for (PhiNode phi : lb.phis().snapshot()) {
            ValueNode phiValueNextIteration = phi.valueAt(lb.getSingleLoopEnd());
            for (Node usage : phi.usages()) {
                if (usage == newExitCondition.condition() || newNodes.contains(usage)) {
                    flatReplacements.add(new ReplacementPair(phi, (ValueNode) usage, phiValueNextIteration));
                }
            }
        }
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        List<InductionVariable> ivSnapShotBefore = new ArrayList<>();
        for (InductionVariable iv : ivs.getValues()) {
            ivSnapShotBefore.add(iv);
        }
        for (InductionVariable iv : ivSnapShotBefore) {
            ValueNode oldIvOp = null;
            if (iv instanceof BasicInductionVariable) {
                oldIvOp = ((BasicInductionVariable) iv).getOp();
            } else {
                oldIvOp = iv.valueNode();
            }
            InductionVariable nextIteration = InductionVariableHelper.nextIteration(iv);
            ValueNode nextItIvValue = null;
            if (nextIteration instanceof BasicInductionVariable) {
                nextItIvValue = ((BasicInductionVariable) nextIteration).getOp();
            } else {
                nextItIvValue = nextIteration.valueNode();
            }
            for (Node usage : oldIvOp.usages()) {
                if (usage == newExitCondition.condition() || !newNodes.isNew(usage) && newNodes.contains(usage)) {
                    flatReplacements.add(new ReplacementPair(oldIvOp, (ValueNode) usage, nextItIvValue));
                }
            }
        }
        for (ReplacementPair replacement : flatReplacements) {
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before replacing old input %s with new input %s at usage %s", replacement.input, replacement.newNode,
                            replacement.usage);
            replacement.usage.replaceAllInputs(replacement.input, replacement.newNode);
        }
        /*
         * Rebuild new proxies to avoid canonicalizations of them if they have been loop proxies
         * before
         */
        for (ProxyNode proxy : lex.proxies().snapshot()) {
            UnmodifiableEconomicMap<Node, Node> proxyDuplicateTree = newProxyDuplicates.get(proxy);
            Node duplicate = proxyDuplicateTree.get(proxy.value());
            // the proxy value was a loop phi
            if (duplicate == null) {
                assert lb.isPhiAtMerge(proxy.value()) : " cannot find replacement " + proxy.value() + " duplicates=" + proxyDuplicateTree;
                duplicate = ((PhiNode) proxy.value()).valueAt(lb.getSingleLoopEnd());
            }
            Node newNode = null;
            ValueNode newTreeRoot = (ValueNode) duplicate;
            newNode = proxy.duplicateOn(lex, newTreeRoot);
            proxy.replaceAtUsages(newNode);
            ((ProxyNode) newNode).inferStamp();
            proxy.safeDelete();
            assert newNode != null;
            proxyZeroTripInputs.put((ProxyNode) newNode, proxyZeroTripInputs.get(proxy));
            proxyZeroTripInputs.removeKey(proxy);
            originalProxyStamps.put((ProxyNode) newNode, originalProxyStamps.get(proxy));
            originalProxyStamps.removeKey(proxy);
        }
    }

    public record ProtectionData(MergeNode merge, EconomicMap<ProxyNode, PhiNode> proxyToPhiMap) {
    }

    /**
     * Protect the loop with a first iteration entry check, i.e. a diamond that checks the loop
     * condition with its initial iteration value.
     *
     * Before
     *
     * <pre>
     * for (i = 0; i < end; i++) {
     *     // body
     * }
     * </pre>
     *
     * after
     *
     * <pre>
     * if (0 < end) {
     *     for (i = 0; i < end; i++) {
     *         // body
     *     }
     * }
     * </pre>
     */
    public static ProtectionData protectFirstLoopIteration(Loop loop, EconomicMap<ProxyNode, Node> originalProxyValuesZeroIteration) {
        LoopBeginNode lb = loop.loopBegin();
        int loopEndCount = lb.getLoopEndCount();
        assert loopEndCount == 1 : "Protecting loops during inversion can only work with a single loop end but this one " + loop + " has " + loop;

        StructuredGraph graph = lb.graph();
        loop.resetCounted();
        loop.detectCounted(true/* ignore protection, we are building it */);

        assert loop.isCounted() : "Loop must be counted to protect it";

        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After reseting counted loop data %s", lb);

        LoopExitNode lex = (LoopExitNode) loop.counted().getCountedExit();
        MergeNode merge = graph.add(new MergeNode());
        merge.setStateAfter(lex.stateAfter().duplicateWithVirtualState());

        EndNode endNonEntered = graph.add(new EndNode());
        EndNode endEntered = graph.add(new EndNode());
        BeginNode beginEntered = graph.add(new BeginNode());
        BeginNode beginNotEntered = graph.add(new BeginNode());

        IfNode limitTest = loop.counted().getLimitTest();
        LogicNode condition = limitTest.condition();

        assert condition instanceof BinaryOpLogicNode : condition;

        boolean useX = !loop.whole().contains(((BinaryOpLogicNode) condition).getY());
        ValueNode condX = ((BinaryOpLogicNode) condition).getX();
        assert useX || !loop.whole().contains(condX) : condX + " must not be part of loop " + loop.whole();

        CountedLoopInfo counted = loop.counted();
        ValueNode newConditionIV = counted.limitCheckedPreviousOrRootEntryValue();
        BinaryOpLogicNode copy = (BinaryOpLogicNode) condition.copyWithInputs(true);
        if (useX) {
            copy.setX(newConditionIV);
        } else {
            copy.setY(newConditionIV);
        }

        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating new protection condition %s", condition);

        IfNode ifNode = null;
        final BranchProbabilityData trueSuccProbability = limitTest.getProfileData();
        if (limitTest.trueSuccessor() instanceof LoopExitNode) {
            ifNode = graph.add(new IfNode(copy, beginNotEntered, beginEntered, trueSuccProbability));
        } else {
            ifNode = graph.add(new IfNode(copy, beginEntered, beginNotEntered, trueSuccProbability));
        }

        beginNotEntered.setNext(endNonEntered);
        merge.addForwardEnd(endEntered);
        merge.addForwardEnd(endNonEntered);
        FixedNode next = lex.next();
        lex.setNext(null);
        lex.setNext(endEntered);
        merge.setNext(next);
        FixedWithNextNode loopPred = (FixedWithNextNode) lb.forwardEnd().predecessor();
        loopPred.setNext(null);
        beginEntered.setNext(lb.forwardEnd());
        loopPred.setNext(ifNode);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After building first iteration protection CF for loop %s", lb);

        /*
         * Loop exit and merge state: The merge becomes a completely new, deep duplicated state with
         * the correct phis assigned to it. However, the exit state remains and should still use the
         * old values. To distinguish between old, untouched exit state(s) we create a decoupled,
         * completely new exit state for the lex as well. There might be post dominating usages that
         * have common outer/virtual states with the exit states and we would miss necessary proxy
         * -> new phi updates else where.
         */
        lex.setStateAfter(lex.stateAfter().duplicateWithVirtualState());
        EconomicSet<Node> lexStateSet = EconomicSet.create(Equivalence.IDENTITY);
        lex.stateAfter().applyToVirtual(x -> lexStateSet.add(x));

        EconomicMap<ProxyNode, PhiNode> phis = EconomicMap.create();
        for (ProxyNode thisLoopProxy : lex.proxies().snapshot()) {
            PhiNode newPhi = thisLoopProxy.createPhi(merge);
            newPhi.addInput(thisLoopProxy);
            newPhi.addInput((ValueNode) originalProxyValuesZeroIteration.get(thisLoopProxy));
            newPhi.inferStamp();
            final PhiNode effectPhi = newPhi;
            /*
             * Replace the phi in everything below the loop exit except the state, since that one
             * remains on the loop exit.
             */
            thisLoopProxy.replaceAtUsages(effectPhi, x -> !lexStateSet.contains(x) && x != effectPhi && !merge.isPhiAtMerge(x));
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After processing loop protection proxy %s and replacing it with phi %s", thisLoopProxy, effectPhi);
            phis.put(thisLoopProxy, newPhi);
        }
        // nodes post dominating the exit might be anchored at the exit, we change the CFG, thus
        // re-anchor them to the merge
        lex.replaceAtUsages(merge, InputType.Guard, InputType.Anchor);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After protecting first iteration of loop %s", lb);
        return new ProtectionData(merge, phis);
    }

    public static EconomicMap<ProxyNode, Stamp> getLoopProxyStamps(Loop loop) {
        EconomicMap<ProxyNode, Stamp> originalProxyStampsBeforeInversion = EconomicMap.create();
        final LoopExitNode lex = (LoopExitNode) loop.counted().getCountedExit();
        for (ProxyNode thisLoopProxy : lex.proxies()) {
            originalProxyStampsBeforeInversion.put(thisLoopProxy, thisLoopProxy.stamp(NodeView.DEFAULT));
        }
        return originalProxyStampsBeforeInversion;
    }
}
