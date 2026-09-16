/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.duplication.util.DuplicationUtil;
import jdk.graal.compiler.duplication.util.DuplicationUtil.CFGFrequencyInfo;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopBeginNode.SafepointState;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.VirtualState.NodePositionClosure;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.IntegerHelper;
import jdk.graal.compiler.nodes.util.SignedIntegerHelper;
import jdk.graal.compiler.nodes.virtual.EscapeObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.GlobalProfilesOptimizationUtility;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.replacements.nodes.LogNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticNode;

/**
 * Implements strip mining for non-counted loops to reduce the number of safepoints on loop ends.
 *
 * This phase implements strip mining only for non-counted loops, i.e., loops for which no
 * {@linkplain Loop#detectCounted() counted loop structure} can be derived. An example is the
 * following loop:
 *
 * <pre>
 * static class Node {
 *     int val;
 *     Node next;
 * }
 *
 * public int nonCounted(Node head) {
 *     Node cur = head;
 *     int res = 0;
 *     while (true) {
 *         if (cur != null) {   // non-counted loop condition
 *             res += cur.val;
 *             cur = cur.next;
 *             safepoint();
 *             continue;
 *         }
 *         break;  // loop exit path
 *     }
 *     return res;
 * }
 * </pre>
 *
 * which we can make "counted" by creating a new counted exit check dominating the rest of the loop
 * body and outer loop that contains the safepoint operation:
 *
 * <pre>
 * public int nonCounted(Node head) {
 *     Node cur = head;
 *     int res = 0;
 *     outer: while (true) {
 *         int artificialCounter = 0;
 *         inner: while (true) {
 *             if (artificialCounter++ >= 1024) { // strip mining factor
 *                 // break inner; implicit
 *                 safepoint(); // this safepoint is on the loop end path of the outer loop not the
 *                              // inner one
 *                 continue outer;
 *             }
 *             if (cur != null) { // non-counted loop condition
 *                 res += cur.val;
 *                 cur = cur.next;
 *                 // safepoint(); can now be removed given that the loop will have at most 1024
 *                 // iterations
 *                 continue inner;
 *             }
 *             break inner; // loop exit path
 *         }
 *         break outer;
 *     }
 *     return res;
 * }
 * </pre>
 *
 * This phase specifically handles non-counted loops, that is, loops for which a
 * {@linkplain Loop#detectCounted() counted loop structure} cannot be derived; for counted loops,
 * see {@link CountedStripMiningPhase}.
 */
public class NonCountedStripMiningPhase extends BasePhase<CoreProviders> {

    public static class Options {

        //@formatter:off
        @Option(help = "The max number of iterations the counted inner loop takes. If -1, the frequency of the loop " +
                       "will be used to derive an inner frequency.", type = OptionType.Debug)
        public static final OptionKey<Integer> NonCountedStripMiningInnerLoopTrips = new OptionKey<>(-1);

        @Option(help = "Forces non-counted strip mining for all loops (also counted ones), test flag only.", type = OptionType.Debug)
        public static final OptionKey<Boolean> NonCountedStripMiningForceStripAll = new OptionKey<>(false);

        @Option(help = "Ignores small loops from strip mining, the iv overhead can cause slowdowns.", type = OptionType.Debug)
        public static final OptionKey<Boolean> NonCountedStripMiningIgnoreSmallLoops = new OptionKey<>(true);

        @Option(help = "If NonCountedStripMiningInnerLoopTrips == -1: Maximum loop trips for strip mined non-counted loops.", type = OptionType.Debug)
        public static final OptionKey<Integer> NonCountedStripMiningMaximumInnerLoopTrips = new OptionKey<>(8192);

        @Option(help = "If NonCountedStripMiningInnerLoopTrips == -1: Minimum loop trips for strip mined non-counted loops.", type = OptionType.Debug)
        public static final OptionKey<Integer> NonCountedStripMiningMinimumInnerLoopTrips = new OptionKey<>(512);

        @Option(help = "Tries to reuse pre-existing induction variables inside non-counted " +
                       "loops for the strip-mined loop's exit check.", type = OptionType.Debug)
        public static final OptionKey<Boolean> NonCountedStripMiningReuseIVs = new OptionKey<>(true);

        @Option(help = "Minimal loop frequency to consider a non-counted loop for strip mining.", type = OptionType.Debug)
        public static final OptionKey<Double> NonCountedStripMiningMinFrequency = new OptionKey<>(16D);

        @Option(help = "Code size budget of the non-counted strip mining transformation in terms of NodeCostSize.", type = OptionType.Debug)
        public static final OptionKey<Double> NonCountedStripMiningBudget = new OptionKey<>(0.1);

        @Option(help = "See NonCountedStripMiningBudget.", type = OptionType.Debug)
        public static final OptionKey<Double> NonCountedStripMiningBudgetHotCode = new OptionKey<>(2.5);
        //@formatter:on
    }

    private final CanonicalizerPhase canonicalizer;

    public NonCountedStripMiningPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    private static boolean verifyGraph(StructuredGraph graph, CoreProviders context) {
        if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
            new SchedulePhase(true, graph.getOptions()).apply(graph, context);
        }
        return GraphOrder.assertSchedulableGraph(graph);
    }

    /**
     * Create an outer loop for the given loop. An outer loop is an endless loop that is exited when
     * the inner loop is exited and has no loop ends. Note that this keeps the graph in a
     * non-verifiable state as long as the outer loop has no loop ends.
     *
     * The outer loop has no loop ends for simplicity of this method, they are added later in
     * {@link #createStripMinedCheck(Loop, LoopBeginNode, LoopBeginNode)}.
     *
     * Example loop:
     *
     * <pre>
     * inner: while (true) {
     *     if (!condition) {
     *         break;
     *     }
     *     // body
     * }
     * </pre>
     *
     * and after creating an outer loop
     *
     * <pre>
     * outer: while (true) {
     *     inner: while (true) {
     *         if (!condition) {
     *             break outer; // implicitly breaks inner as well
     *         }
     *         // body
     *     }
     * }
     * </pre>
     */
    @SuppressWarnings("try")
    public static LoopBeginNode createOuterLoop(Loop loop) {
        final StructuredGraph graph = loop.loopBegin().graph();
        final LoopBeginNode innerLoopBegin = loop.loopBegin(); // original loop begin
        assert innerLoopBegin.loopEnds().count() > 0 : "Graal graph invariant, loops must have at least a single loop end";

        final DebugContext debug = innerLoopBegin.getDebug();
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before creating outer loop at %s", loop);

        final AbstractEndNode innerLoopOldFwdEnd = innerLoopBegin.forwardEnd();
        final FixedWithNextNode outerLoopNewFWdEnd = (FixedWithNextNode) innerLoopOldFwdEnd.predecessor();

        final LoopBeginNode outerLoopBegin = innerLoopBegin.graph().add(new LoopBeginNode());
        outerLoopBegin.setNodeSourcePosition(innerLoopBegin.getNodeSourcePosition());

        outerLoopNewFWdEnd.setNext(null);
        EndNode end = graph.add(new EndNode());
        outerLoopBegin.addForwardEnd(end);
        outerLoopNewFWdEnd.setNext(end);
        outerLoopBegin.setNext(innerLoopOldFwdEnd);

        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating outer loop at loop %s", innerLoopBegin);

        EconomicMap<PhiNode, PhiNode> inner2OuterPhi = EconomicMap.create();

        // give it the same phis as the inner loop
        for (PhiNode phi : innerLoopBegin.phis().snapshot()) {
            PhiNode phiCopy = phi.duplicateOn(outerLoopBegin);
            phiCopy.addInput(phi.valueAt(innerLoopBegin.forwardEnd()));
            inner2OuterPhi.put(phi, phiCopy);
        }

        assert innerLoopBegin.stateAfter() != null;

        // create a state for the outer loop begin and replace inputs to this state from the
        // inner loop phis to the outer loop phis
        final FrameState innerLoopState = innerLoopBegin.stateAfter();
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before cloning outer state of inner loop");
        FrameState outerLoopState = innerLoopState.duplicateWithVirtualState();

        // replace all occurrences of inner phis in the duplicate with the outer phis
        outerLoopState.applyToVirtual(x -> {
            for (PhiNode phi : innerLoopBegin.phis()) {
                if (phi.usages().contains(x)) {
                    PhiNode outer = inner2OuterPhi.get(phi);
                    assert outer != null;
                    // replace in all usages
                    phi.replaceAtMatchingUsages(outer, z -> z == x);
                    debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing input %s of %s with %s", phi, outerLoopState, outer);
                }
            }
        });
        outerLoopBegin.setStateAfter(outerLoopState);
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing inner phi inputs of outer state with phis of outer loop");

        for (LoopExitNode lex : innerLoopBegin.loopExits().snapshot()) {
            createOuterLoopExitAfterInnerExit(lex, loop, outerLoopBegin);
        }

        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After re-routing exits of inner loop %s to be exits of outer loop as well %s", innerLoopBegin, outerLoopBegin);
        List<PhiNode> outerPhis = outerLoopBegin.phis().snapshot();
        int index = 0;
        for (PhiNode phi : innerLoopBegin.phis()) {
            phi.replaceFirstInput(phi.valueAt(innerLoopBegin.forwardEnd()), outerPhis.get(index++));
        }
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After updating inner loop %s phis forward end values with outer loop %s phis", innerLoopBegin, outerLoopBegin);
        return outerLoopBegin;
    }

    public static void createOuterLoopExitAfterInnerExit(LoopExitNode innerLoopExit, Loop innerLoop, LoopBeginNode outerLoopBegin) {
        final StructuredGraph graph = innerLoopExit.graph();
        FrameState lexState = innerLoopExit.stateAfter();
        LoopExitNode lexOuter = graph.add(new LoopExitNode(outerLoopBegin));
        FrameState newState = lexState.duplicateWithVirtualState();
        EconomicMap<ProxyNode, ProxyNode> proxy2ProxyMapping = EconomicMap.create();
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating outer loop exit %s", lexOuter);
        // replace all usages of the proxies outside the loop (include the newly created outer
        // lex state) with the new proxy
        for (ProxyNode proxy : innerLoopExit.proxies()) {
            ProxyNode outerDuplicate = proxy.duplicateOn(lexOuter, proxy);
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After duplicating proxy %s to %s", proxy, outerDuplicate);
            proxy.replaceAtMatchingUsages(outerDuplicate, x -> {
                if (x == outerDuplicate) {
                    return false;
                }
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Checking replacement of input %s of %s with %s", proxy, x, outerDuplicate);
                // everything outside the original loop (including the new state for the outer
                // loop exit) should use the new proxies
                if (!innerLoop.whole().contains(x)) {
                    return true;
                }
                return false;
            });
            proxy2ProxyMapping.put(proxy, outerDuplicate);
        }

        // insert exit
        FixedNode next = innerLoopExit.next();
        innerLoopExit.setNext(null);
        innerLoopExit.setNext(lexOuter);
        lexOuter.setNext(next);
        lexOuter.setStateAfter(newState);
        innerLoopExit.replaceAtUsages(lexOuter, InputType.Guard, InputType.Anchor);
    }

    public static final CounterKey ReusedLoopIV = DebugContext.counter("NonCountedStripMining_ReusedLoopIV");

    /**
     * Create a new loop exit check inside the innerLoop that exits to the outer loop. Tries to
     * re-use existing {@linkplain InductionVariable} inside the inner loop to derive this check to
     * avoid adding a new IV.
     *
     * Example loop (after creating the outer loop via {@link #createOuterLoop(Loop)}):
     *
     * <pre>
     * outer: while (true) {
     *     inner: while (true) {
     *         if (!condition) {
     *             break outer; // implicitly breaks inner as well
     *         }
     *         // body
     *     }
     * }
     * </pre>
     *
     * After adding an artificial counter
     *
     * <pre>
     * uint artificialCounter = 0;
     * outer: while (true) {
     *     inner: while (true) {
     *         if (artificialCounter++ >= StripMineFactor) { // dominating the rest of the loop body
     *             continue outer;
     *         }
     *         if (!condition) {
     *             break outer; // implicitly breaks inner as well
     *         }
     *         // body
     *     }
     * }
     * </pre>
     *
     * Additionally, this method tries to re-use induction variables inside the loop to avoid
     * creating a new {@link InductionVariable} for the inner loop trip.
     *
     * Re-using IVs has the problem that for non-counted loops we cannot be sure the IV does not
     * overflow in the course of the iteration range of the loop. However, we do not need to add the
     * overflow check in the IV reusing. All we need to know is how many trips are taken by the
     * current version of the loop and if that value is below the trip count we are fine.
     *
     *
     * Example:
     *
     * <pre>
     * int candidateCounterIV = start;
     * outer: while (true) {
     *     int candidateCounterIVInner = candidateCounterIV;
     *     inner: while (true) {
     *         if (!condition) {
     *             candidateCounterIV = candidateCounterIVInner;
     *             break outer; // implicitly breaks inner as well
     *         }
     *         // body
     *         candidateCounterIVInner++;
     *     }
     * }
     * </pre>
     *
     * can be rewritten to
     *
     * <pre>
     * int candidateCounterIV = start;
     * outer: while (true) {
     *     int candidateCounterIVInner = candidateCounterIV;
     *     inner: while (true) {
     *         if (candidateCounterIVInner - candidateCounterIV >= StripMineFactor) {
     *             candidateCounterIV = candidateCounterIVInner;
     *             continue outer; // implicitly breaks inner
     *         }
     *         if (!condition) {
     *             candidateCounterIV = candidateCounterIVInner;
     *             break outer; // implicitly breaks inner as well
     *         }
     *         // body
     *         candidateCounterIVInner++;
     *     }
     * }
     * </pre>
     *
     *
     * Note that we use candidateCounterIVInner (current trip of the inner loop) -
     * candidateCounterIV (start of the inner loop) to get the performed iterations of the inner
     * loop. This calculation is correct also in the presence of an overflow (e.g.
     * candidateCounterIV = Integer.MAX_VALUE and we overflow on the candidateCounterIVInner++ in
     * the next iteration then candidateCounterIVInner = Integer.MIN_VALUE and
     * Integer.MIN_VALUE-Integer.MAX_VALUE is again a positive value 1.)
     *
     * For loops counting downwards, i.e. {@link Direction#Down} we reverse the subtraction
     * operation for the number of the inner loop trips:
     *
     * <pre>
     * int candidateCounterIV = start;
     * outer: while (true) {
     *     int candidateCounterIVInner = candidateCounterIV;
     *     inner: while (true) {
     *         if (candidateCounterIV - candidateCounterIVInner >= StripMineFactor) {
     *             candidateCounterIV = candidateCounterIVInner;
     *             continue outer; // implicitly breaks inner
     *         }
     *         if (!condition) {
     *             candidateCounterIV = candidateCounterIVInner;
     *             break outer; // implicitly breaks inner as well
     *         }
     *         // body
     *         candidateCounterIVInner--;
     *     }
     * }
     * </pre>
     *
     * For loops counting downwards we use candidateCounterIV - candidateCounterIVInner to calculate
     * the current trips (e.g. candidateCounterIVInner is initialized with Integer.MIN_VALUE and
     * overflows negatively on the next iteration then candidateCounterIV - candidateCounterIVInner
     * = Integer.MIN_VALUE - Integer.MAX_VALUE = 1).
     *
     * The inner loop's actual limit can differ from the trip count when reusing an induction
     * variable with a non-unit stride ({@code abs(stride) != 1}). For example:
     *
     * <pre>
     * int iv = start;
     * while (condition) {
     *     // body
     *     iv += 1000;
     * }
     * </pre>
     *
     * The strip mining factor has to be scaled by the absolute stride to maintain the trip count:
     *
     * <pre>
     * int iv = start;
     * outer: while (true) {
     *     int ivInner = iv;
     *     inner: while (true) {
     *         if (ivInner - iv >= (StripMineFactor * 1000)) {
     *             iv = ivInner;
     *             continue outer;
     *         }
     *         if (!condition) {
     *             iv = ivInner;
     *             break outer;
     *         }
     *         // body
     *         ivInner += 1000;
     *     }
     * }
     * </pre>
     */
    private static void createStripMinedCheck(Loop originalInnerLoop, LoopBeginNode innerLoopBegin, LoopBeginNode outerLoopBegin) {
        StructuredGraph graph = innerLoopBegin.graph();
        List<PhiNode> innerPhis = innerLoopBegin.phis().snapshot();
        List<PhiNode> outerPhis = outerLoopBegin.phis().snapshot();
        int innerLoopTrips = Options.NonCountedStripMiningInnerLoopTrips.getValue(graph.getOptions());
        if (innerLoopTrips < 0) {
            /*
             * When strip mining non-counted loops we ideally would like to never exit the inner
             * loop to do a safepoint. Thus, we take the frequency derived from the interpreter as
             * our inner loop limit. However, there are certain special cases we must not forget:
             *
             * @formatter:off
             *
             *  -> loopBegin.loopFrequency can return numerical values out of integer range (e.g. endless loops), thus we cap it with
             *     NonCountedStripMiningMaximumInnerLoopTrips
             *  -> the loopFrequency returned can be numerically very small, then we increase it artificially to be NonCountedStripMiningMinimumInnerLoopTrips
             *
             * @formatter:on
             *
             * Both values NonCountedStripMining(Maximum|Minimum)InnerLoopTrips are derived by empirical evaluation.
             * After all, the inner loop limit is a trade-off between throughput and latency and non-counted loops are often
             * very large thus choosing appropriate limits is subject to benchmarking.
             */
            innerLoopTrips = (int) Math.min(Options.NonCountedStripMiningMaximumInnerLoopTrips.getValue(graph.getOptions()),
                            (long) originalInnerLoop.loopsData().getCFG().localLoopFrequency(innerLoopBegin));
            innerLoopTrips = Math.max(innerLoopTrips, Options.NonCountedStripMiningMinimumInnerLoopTrips.getValue(graph.getOptions()));
            assert NumUtil.assertNonNegativeInt(innerLoopTrips);
        }

        /*
         * Try to reuse an existing induction variable inside the loop to avoid creating a new one
         * at the cost of an additional add/sub inside the loop.
         */
        ValuePhiNode countedIVToUse = null;
        ValueNode upperCountInnerTripCheck = null;
        LogicNode ib = null;
        IntegerHelper helper = null;
        // The loop's actual limit can differ from the trips if we reuse an induction variable with non-unit stride.
        int innerLoopLimit = innerLoopTrips;

        if (Options.NonCountedStripMiningReuseIVs.getValue(graph.getOptions())) {
            EconomicMap<Node, InductionVariable> ivs = originalInnerLoop.getInductionVariables();
            for (InductionVariable iv : ivs.getValues()) {
                if (iv instanceof BasicInductionVariable && iv.isConstantStride()) {
                    if (((BasicInductionVariable) iv).getOp() instanceof IntegerExactArithmeticNode) {
                        // unrolling inverted loops for exact math ops is not supported, so stick to
                        // regular math operations
                        continue;
                    }
                    final long stride;
                    try {
                        stride = NumUtil.safeAbs(iv.constantStride(), IntegerStamp.getBits(iv.strideNode().stamp(NodeView.DEFAULT)));
                    } catch (ArithmeticException e) {
                        continue;
                    }
                    if (stride >= Integer.MAX_VALUE) {
                        continue;
                    }
                    final int ivInnerLoopLimit;
                    try {
                        ivInnerLoopLimit = Math.multiplyExact((int) stride, innerLoopTrips);
                    } catch (ArithmeticException e) {
                        continue;
                    }
                    BasicInductionVariable biv = (BasicInductionVariable) iv;
                    final IntegerStamp bivValueStamp = (IntegerStamp) biv.valueNode().stamp(NodeView.DEFAULT);
                    if (bivValueStamp.getBits() != 32) {
                        continue;
                    }
                    if (outerLoopBegin.isPhiAtMerge(biv.initNode())) {
                        ValueNode ivOffsetted = null;
                        if (iv.direction() == Direction.Up) {
                            ivOffsetted = graph.addOrUniqueWithInputs(SubNode.create(biv.valueNode(), iv.initNode(), NodeView.DEFAULT));
                        } else if (iv.direction() == Direction.Down) {
                            ivOffsetted = graph.addOrUniqueWithInputs(SubNode.create(iv.initNode(), biv.valueNode(), NodeView.DEFAULT));
                        }
                        if (ivOffsetted != null) {
                            helper = new SignedIntegerHelper(32);
                            countedIVToUse = biv.valueNode();
                            upperCountInnerTripCheck = ivOffsetted;
                            ib = graph.addWithoutUniqueWithInputs(helper.createCompareNode(ivOffsetted, ConstantNode.forInt(ivInnerLoopLimit), NodeView.DEFAULT));
                            if (ib instanceof LogicConstantNode) {
                                /*
                                 * Can happen under very special conditions. Some loop phis can be
                                 * constant already and we fold through the comparison node. Just
                                 * create a regular counter here, this is so special we cannot abort
                                 * that late.
                                 */
                                assert ib.hasNoUsages() : Assertions.errorMessage("Should have no usages", ib, ib.usages());
                                ib.safeDelete();
                                countedIVToUse = null;
                                upperCountInnerTripCheck = null;
                                ib = null;
                                helper = null;
                            } else {
                                innerLoopLimit = ivInnerLoopLimit;
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (countedIVToUse == null) {
            helper = new SignedIntegerHelper(32);
            ValuePhiNode newPhi = graph.addOrUnique(new ValuePhiNode(IntegerStamp.create(32), innerLoopBegin));
            newPhi.addInput(ConstantNode.forInt(0, graph));
            ValueNode newAdd = graph.addOrUniqueWithInputs(AddNode.create(newPhi, ConstantNode.forInt(1), NodeView.DEFAULT));
            for (int i = 0; i < innerLoopBegin.getLoopEndCount(); i++) {
                newPhi.addInput(newAdd);
            }
            countedIVToUse = newPhi;
            upperCountInnerTripCheck = ConstantNode.forInt(innerLoopLimit);
            ib = graph.addOrUniqueWithInputs(helper.createCompareNode(countedIVToUse, upperCountInnerTripCheck, NodeView.DEFAULT));
        } else {
            ReusedLoopIV.increment(graph.getDebug());
        }

        innerLoopBegin.setStripMinedLimit(innerLoopLimit);

        LoopExitNode lex = graph.add(new LoopExitNode(innerLoopBegin));
        LoopEndNode len = graph.add(new LoopEndNode(outerLoopBegin));
        lex.setNext(len);

        AbstractBeginNode innerLoopRest = graph.add(new BeginNode());

        // the outer loop should never be visited, give it a single trip probability if at all
        BranchProbabilityData checkProbability = ProfileData.BranchProbabilityData.create(1 - ControlFlowGraph.MIN_RELATIVE_FREQUENCY, ProfileSource.INJECTED);
        IfNode newExitCheck = graph.add(new IfNode(ib, innerLoopRest, lex, checkProbability));

        // for each inner phi add a proxy and a phi input on the outer loop

        EconomicMap<PhiNode, ProxyNode> innerLoopPhiToProxy = EconomicMap.create();

        final int phiCountBefore = innerPhis.size();
        assert outerPhis.size() == phiCountBefore : outerPhis.size() + "!=" + phiCountBefore;
        for (int i = 0; i < phiCountBefore; i++) {
            PhiNode innerPhi = innerPhis.get(i);
            PhiNode outerPhi = outerPhis.get(i);
            final ProxyNode innerPhiProxy = innerPhi.createProxyFor(lex);
            outerPhi.addInput(innerPhiProxy);
            innerLoopPhiToProxy.put(innerPhi, innerPhiProxy);
        }

        FrameState duplicated = innerLoopBegin.stateAfter().duplicateWithVirtualState();

        duplicated.applyToNonVirtual(new NodePositionClosure<>() {
            @Override
            public void apply(Node from, Position p) {
                ValueNode usage = (ValueNode) p.get(from);
                if (usage instanceof PhiNode) {
                    if (innerLoopPhiToProxy.containsKey((PhiNode) usage)) {
                        p.set(from, innerLoopPhiToProxy.get((PhiNode) usage));
                    }
                }
            }
        });

        lex.setStateAfter(duplicated);

        FixedNode oldNext = innerLoopBegin.next();
        innerLoopBegin.setNext(null);
        innerLoopRest.setNext(oldNext);
        innerLoopBegin.setNext(newExitCheck);

        outerLoopBegin.markNonCountedStripMinedOuter();
        innerLoopBegin.markNonCountedStripMinedInner();

        /*
         * Safepoints for the inner loop are removed by the LoopSafepointElimiantionPhase
         */
        // only remove safepoints if the original loop MUST NOT safepoint
        if (!innerLoopBegin.canEndsSafepoint()) {
            outerLoopBegin.setGuestSafepoint(SafepointState.OPTIMIZER_DISABLED);
            outerLoopBegin.setLoopEndSafepoint(SafepointState.OPTIMIZER_DISABLED);
        }

        if (LOG_VALUE_FLOW) {
            FixedWithNextNode log = graph.add(new LogNode("Inner Loop with check iv %d and offsetted %d\n", countedIVToUse, upperCountInnerTripCheck));
            graph.addAfterFixed(newExitCheck.trueSuccessor(), log);

            FixedWithNextNode log2 = graph.add(new LogNode("Exit inner loop with check iv %d and checkedVal %d\n", countedIVToUse, graph.addOrUnique(new ValueProxyNode(upperCountInnerTripCheck,
                            lex))));
            graph.addAfterFixed(newExitCheck.falseSuccessor(), log2);

        }

        graph.getOptimizationLog().report(NonCountedStripMiningPhase.class, "LoopStripMining", innerLoopBegin);
    }

    public static final boolean LOG_VALUE_FLOW = false;

    private static boolean canStripMine(Loop loop) {
        if (loop.loopBegin().isAnyStripMinedInner() || loop.loopBegin().isAnyStripMinedOuter()) {
            return false;
        }
        if (loop.getCFGLoop().getChildren().size() > 0) {
            // only process inner loops
            return false;
        }
        if (LoopUtility.snippetSideEffectLoop(loop)) {
            return false;
        }
        for (Node node : loop.inside().nodes()) {
            if (!Loop.canDuplicateLoopNode(node)) {
                return false;
            }
            if (!Loop.canStripMineLoopNode(node)) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldStripMine(Loop loop, CoreProviders context, CFGFrequencyInfo frequencyInfo) {
        if (frequencyInfo.isTrusted()) {
            if (loop.localLoopFrequency() < Options.NonCountedStripMiningMinFrequency.getValue(loop.loopBegin().getOptions())) {
                // little benefit for a few trips
                return false;
            }
        }
        /*
         * Strip mining a loop for the sake of safepoint removal is not beneficial if regular
         * safepoint removal will handle it already.
         */
        int safepointsRemoved = 0;
        int safepointsToRemove = loop.loopBegin().getLoopEndCount();
        for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
            HIRBlock b = loop.loopsData().getCFG().blockFor(loopEnd);
            while (b != loop.getCFGLoop().getHeader()) {
                for (FixedNode node : b.getNodes()) {
                    boolean canDisableSafepoint = LoopSafepointEliminationPhase.canDisableSafepoint(node, context);
                    if (canDisableSafepoint) {
                        safepointsRemoved++;
                    }
                }
                b = b.getDominator();
            }
        }
        if (safepointsRemoved == safepointsToRemove) {
            return false;
        }
        if (Options.NonCountedStripMiningIgnoreSmallLoops.getValue(loop.loopBegin().getOptions())) {

            /**
             * Trivial loops without any control flow: based on the size of the loop and if we can
             * re-use and induction variables inside the loop the strip mined overhead might be
             * noticable in performance. This, we check if there will be any deopts inside the loop,
             * if not the only place for a framestate to keep additional values alive is the
             * safepoint. Therefore we check all framestates (because we are before FSA and do not
             * know which state will end up at the safepoint) and test if there are values kept
             * alive on the safepoint which are only kept alive for the framestate.
             */
            StructuredGraph graph = loop.loopBegin().graph();
            if (loop.getCFGLoop().getBlocks().size() < 3) {
                for (Node n : loop.whole().nodes()) {
                    if (n instanceof DeoptimizingNode) {
                        // loop may deopt, will require a framestate, ignore
                        break;
                    }
                }
                NodeBitMap allPlusLB = graph.createNodeBitMap();
                allPlusLB.markAll(loop.whole().nodes());
                allPlusLB.mark(loop.loopBegin());
                for (Node n : allPlusLB) {
                    // collect all values of the FS
                    FrameState cur = null;
                    if (n instanceof FrameState) {
                        cur = (FrameState) n;
                    } else if (n instanceof StateSplit) {
                        cur = ((StateSplit) n).stateAfter();
                    }
                    if (cur == null) {
                        continue;
                    }
                    NodeBitMap liveValues = graph.createNodeBitMap();
                    while (cur != null) {
                        for (Node val : cur.values()) {
                            if (val != null) {
                                liveValues.mark(val);
                            }
                        }
                        if (cur.virtualObjectMappingCount() > 0) {
                            for (EscapeObjectState e : cur.virtualObjectMappings()) {
                                if (e instanceof VirtualObjectState) {
                                    for (Node val : ((VirtualObjectState) e).values()) {
                                        if (val != null) {
                                            liveValues.mark(val);
                                        }
                                    }
                                }
                            }
                        }
                        cur = cur.outerFrameState();
                    }
                    // check if any of those values are only used by states
                    for (Node value : liveValues) {
                        if (value.usages().filter(x -> !(x instanceof VirtualState)).count() == 0) {
                            // only state uses this value, thus strip mining and getting rid
                            // of the state may be beneficial to remove the live values inside the
                            // loop
                            loop.loopBegin().getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Value %s only used by framestates", value);
                            return true;
                        }

                    }
                }
                loop.loopBegin().getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Not strip mining %s non-counted loop because loop is trivial and the fs does not keep values alive artificially.",
                                loop.loopBegin());
                return false;
            }
        }

        return true;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState),
                        canonicalizer.notApplicableTo(graphState));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        assert !Assertions.detailedAssertionsEnabled(graph.getOptions()) || verifyGraph(graph, context) : "Before non-counted strip mining.";
        List<LoopBeginNode> definiteStrips = null;
        EconomicSetNodeEventListener ecs = new EconomicSetNodeEventListener();
        try (NodeEventScope nes = graph.trackNodeEvents(ecs)) {
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            DuplicationUtil.CFGFrequencyInfo frequencyInfo = new CFGFrequencyInfo(loopsData.getCFG());
            loopsData.detectCountedLoops();
            List<Loop> potentialStrips;
            boolean forceStrip = Options.NonCountedStripMiningForceStripAll.getValue(graph.getOptions());
            if (forceStrip) {
                potentialStrips = loopsData.loops();
            } else {
                potentialStrips = loopsData.nonCountedLoops();
            }
            for (Loop loop : potentialStrips) {
                if (!forceStrip) {
                    if (!loop.loopBegin().canEndsSafepoint()) {
                        continue;
                    }
                }
                if (!canStripMine(loop)) {
                    continue;
                }
                if (!forceStrip && !shouldStripMine(loop, context, frequencyInfo)) {
                    continue;
                }
                if (definiteStrips == null) {
                    definiteStrips = new ArrayList<>();
                }
                definiteStrips.add(loop.loopBegin());
            }
            if (definiteStrips != null) {
                loopsData = context.getLoopsDataProvider().getLoopsData(graph);
                EconomicSet<LoopBeginNode> toStrip = EconomicSet.create();
                toStrip.addAll(definiteStrips);
                double codeSizeIncreaseFactor = GlobalProfilesOptimizationUtility.selectOptionBySignificance(graph, Options.NonCountedStripMiningBudget, Options.NonCountedStripMiningBudgetHotCode);
                toStrip = CountedStripMiningPhase.pruneStripMiningCandidates(toStrip, loopsData.getCFG(), codeSizeIncreaseFactor);
                outer: for (LoopBeginNode lb : toStrip) {
                    // we have to recompute the loops data here because the control flow graph
                    // changes everytime we strip mine a loop
                    loopsData = context.getLoopsDataProvider().getLoopsData(graph);
                    for (Loop loop : loopsData.loops()) {
                        if (loop.loopBegin() != lb) {
                            continue;
                        }
                        LoopTransformations.ensureExitsHaveUniqueStates(loop);
                        graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Strip mining non-counted loop %s in %s", loop, graph);
                        LoopBeginNode outerLoopBegin = createOuterLoop(loop);
                        createStripMinedCheck(loop, loop.loopBegin(), outerLoopBegin);
                        continue outer;
                    }
                }
            }
        }
        if (definiteStrips != null) {
            canonicalizer.applyIncremental(graph, context, ecs.getNodes());
        }
        assert definiteStrips == null || !Assertions.detailedAssertionsEnabled(graph.getOptions()) || verifyGraph(graph, context) : "After non-counted strip mining.";
    }

    @Override
    public float codeSizeIncrease() {
        return 2f;
    }
}
