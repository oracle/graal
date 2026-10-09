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

import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.loop.phases.LoopTransformations.ProtectionData;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardProxyNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.InductionVariableHelper;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.replacements.nodes.LogNode;

/**
 * Transforms a {@code while} loop to an {@code if} block containing a {@code do..while} loop. This
 * can improve performance due to instruction pipelining.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Loop_inversion">Loop Inversion</a>
 */
public class LoopInversionPhase extends LoopPhase<LoopPolicies> {

    public static class Options {
        //@formatter:off
        @Option(help = "Performs loop-inversion optimization.", type = OptionType.Expert)
        public static final OptionKey<Boolean> LoopInversion = new OptionKey<>(true);

        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> HighTierInversion = new OptionKey<>(false);

        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> MidTierInversion = new OptionKey<>(true);
        //@formatter:on
    }

    private static final CounterKey InversionCandidates = DebugContext.counter("LoopInversion_InversionCandidates");
    private static final CounterKey NoRotationTestFound = DebugContext.counter("LoopInversion_NoRotationTestFound");

    public LoopInversionPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
        super(policies, canonicalizer);
    }

    public static boolean canInvert(Loop loop) {
        if (LoopUtility.excludeLoopFromOptimizer(loop)) {
            return false;
        }
        if (!loop.isCounted()) {
            loop.loopBegin().graph().getDebug().log(DebugContext.INFO_LEVEL, "Not inverting loop not counted %s", loop);
            return false;
        }
        if (!(loop.counted().getCountedExit() instanceof LoopExitNode)) {
            // we can only invert loops where the counted exit is a real exit
            return false;
        }
        if (!loop.canDuplicateLoop()) {
            return false;
        }
        if (loop.counted().isInverted()) {
            return false;
        }
        if (loop.loopBegin().isCompilerInverted()) {
            // the loop was head counted originally and then inverted, inverting it another time is
            // probably not ideal
            return false;
        }
        if (loop.counted().getLimitTest().condition() instanceof IntegerEqualsNode && loop.counted().getLimitTest().falseSuccessor() == loop.counted().getCountedExit()) {
            /*
             * A loop of the form "while (i++ == limit) { ... }". This will iterate at most once,
             * and we wouldn't consider its inverted form counted. Don't invert.
             */
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        if (!unsignedInitAndLimitHaveSameSign(counted)) {
            return false;
        }
        return true;
    }

    /**
     * Determine if we are dealing with an unsigned case with different signedness after inversion.
     * Such loops may be counted before inversion but will not be counted after inversion. After
     * inversion we can no longer tell if the loop requires overflow to terminate. This is only
     * relevant for unsigned loops.
     *
     * As an example consider this loop
     *
     * <pre>
     * public static long complexSignNE(int p0) {
     *     if (p0 < 0) {
     *         return -1;
     *     }
     *     long init = (p0 & 0xffffffffL) << 3;
     *     int step = -8;
     *     long i = init;
     *     while (true) {
     *         if (i == 0) {
     *             break;
     *         }
     *         i += step;
     *     }
     *     return i;
     * }
     * </pre>
     *
     * This loop counts down - however we can represent it with an unsigned condition. As the
     * termination condition is phi==0. If we invert this loop the phi init stamp is positive but
     * the phi IV stamp of the inverted loop has a full value range stamp. We reject such loops in
     * counted loop detection because they may require over or underflow to terminate. Thus, in
     * these cases loop inversion destroys counted loop properties. Thus, we refrain from inverting
     * such loops.
     */
    public static boolean unsignedInitAndLimitHaveSameSign(CountedLoopInfo counted) {
        CanonicalCondition c = ((CompareNode) counted.getLimitTest().condition()).condition();
        if (c == CanonicalCondition.EQ) {
            InductionVariable nextCheckedIV = InductionVariableHelper.nextIteration(counted.getLimitCheckedIV());
            IntegerStamp initStamp = (IntegerStamp) nextCheckedIV.initNode().stamp(NodeView.DEFAULT);
            IntegerStamp limitStamp = (IntegerStamp) counted.getLimit().stamp(NodeView.DEFAULT);
            IntegerStamp counterStamp = (IntegerStamp) counted.getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT);
            if (counted.getDirection() == Direction.Up) {
                if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.unsignedUpperBound() && !IntegerStamp.sameSign(initStamp, limitStamp)) {
                    return false;
                }
            } else if (counted.getDirection() == Direction.Down) {
                if (limitStamp.asConstant() != null && limitStamp.asConstant().asLong() == counterStamp.unsignedLowerBound() && !IntegerStamp.sameSign(initStamp, limitStamp)) {
                    return false;
                }
            } else {
                throw GraalError.shouldNotReachHere(String.format("Unkown direction %s for %s", counted.getDirection(), counted));
            }
        }
        return true;
    }

    /**
     * See {@link Loop} for the inverted-loop signedness details; we must not invert loops
     * whose checked IV changes signedness after inversion because
     * {@link InjectLoopCounterStampsPhase} may then choose signed helpers that destroy the loop
     * condition.
     */
    private static boolean signedLoopMayBecomeUnsignedByInversion(InductionVariable checkedIV) {
        if (checkedIV.direction() == null) {
            return false;
        }
        final IntegerStamp initStamp = (IntegerStamp) checkedIV.initNode().stamp(NodeView.DEFAULT);
        final InductionVariable nextCheckedIV = InductionVariableHelper.nextIteration(checkedIV);
        final IntegerStamp nextValueStamp = (IntegerStamp) nextCheckedIV.initNode().stamp(NodeView.DEFAULT);
        if (checkedIV.direction() == Direction.Up) {
            return initStamp.canBeNegative() && nextValueStamp.isPositive();
        } else if (checkedIV.direction() == Direction.Down) {
            return !initStamp.canBeNegative() && nextValueStamp.isNegative();
        }
        return false;
    }

    public void preprocess(StructuredGraph graph, CoreProviders context) {
        for (Loop loop : context.getLoopsDataProvider().getLoopsData(graph).loops()) {
            if (loop.detectCounted() && canInvert(loop, true) && findLoopInversionTest(loop, true) != null) {
                LoopUtility.createDeoptCountedLoopExitNode(loop);
                LoopBeginNode loopBegin = loop.loopBegin();
                if (!loopBegin.isAnyStripMinedInner()) {
                    if (!DefaultLoopPolicies.Options.InvertMultiEndLoops.getValue(graph.getOptions())) {
                        continue;
                    }
                }
                if (loopBegin.getLoopEndCount() > 1) {
                    LoopUtility.mergeLoopEnds(loopBegin);
                }
            }
        }
    }

    public boolean verifyInversion(StructuredGraph g, CoreProviders context) {
        if (g.getGuardsStage().areFrameStatesAtDeopts()) {
            assert GraphOrder.assertNonCyclicGraph(g);
            if (Assertions.detailedAssertionsEnabled(g.getOptions())) {
                // we still want to do a memory verification of the schedule even if we can
                // no longer use assertSchedulableGraph after the floating reads phase
                new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST).apply(g, context);
            }
        } else {
            assert GraphOrder.assertSchedulableGraph(g);
        }
        return true;
    }

    public static boolean canInvert(Loop loop, boolean preCheck) {
        if (!loop.isCounted()) {
            return false;
        }
        if (!preCheck) {
            if (!(loop.counted().getCountedExit() instanceof LoopExitNode)) {
                // we can only invert loops where the counted exit is a real exit
                return false;
            }
        }
        if (!loop.canDuplicateLoop()) {
            return false;
        }
        if (loop.counted().isInverted()) {
            return false;
        }
        if (loop.loopBegin().isCompilerInverted()) {
            // the loop was head counted originally and then inverted, inverting it another time is
            // probably not ideal
            return false;
        }
        return true;
    }

    private boolean invert(StructuredGraph graph, CoreProviders context, NodeBitMap invertedLoops) {
        boolean inverted = false;
        outer: while (true) { // TERMINATION ARGUMENT: inverting a fixed set of loops in a graph
            CompilationAlarm.checkProgress(graph);
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            for (Loop loop : loopsData.loops()) {
                loop.detectCounted();
                if (canInvert(loop, false)) {
                    IfNode ifNode = findLoopInversionTest(loop, false);
                    if (ifNode != null) {
                        InversionCandidates.increment(graph.getDebug());
                        if (getPolicies().shouldInvert(loop, ifNode, context)) {
                            if (!inverted) {
                                assert verifyInversion(graph, context);
                                inverted = true;
                            }

                            graph.getDebug().log(DebugContext.INFO_LEVEL, "Inverting loop %s", loop.loopBegin());
                            graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "Before inverting loop %s", loop.loopBegin());

                            NodeBitMap safepointsToRewire = null;
                            /*
                             * only massage safepoint on the counted exit, all others are merged
                             * already early, we only protect the counted exit
                             */
                            LoopExitNode countedExit = (LoopExitNode) loop.counted().getCountedExit();
                            if (countedExit.next() instanceof SafepointNode s) {
                                GuardingNode guard = s.getLoopLink();
                                if (guard != countedExit) {
                                    continue;
                                }
                                if (safepointsToRewire == null) {
                                    safepointsToRewire = graph.createNodeBitMap();
                                }
                                safepointsToRewire.mark(s);
                                GuardingNode p = s.getLoopLink();
                                s.setLoopLink(null);
                                if (p instanceof GuardProxyNode gp && gp.hasNoUsages()) {
                                    gp.safeDelete();
                                }
                            }

                            LoopUtility.removeObsoleteProxiesForLoop(loop);

                            // The safepoint and guard phases can introduce additional loop ends
                            // after preprocessing. Merge them before the inversion transformation.
                            if (loop.loopBegin().getLoopEndCount() > 1) {
                                LoopUtility.mergeLoopEnds(loop.loopBegin());
                            }

                            /*
                             * Loop inversion and proxy stamps: Before loop inversion
                             * InjectLoopCounterStamps can improve loop phi stamps by manually
                             * setting the stamp. After inversion many values dont consume the body
                             * IV any more but the limitCheckedIV (rewrite from head to tail counted
                             * loop does this). Proxy usages then dont consume the original,
                             * improved, phi stamp but a BasicIV offset from it. In order to not
                             * lose the stamp we set the stamp of the protection diamond's phi to
                             * the original stamp (which must be same).
                             */
                            EconomicMap<ProxyNode, Stamp> originalProxyStamps = LoopTransformations.getLoopProxyStamps(loop);
                            EconomicMap<ProxyNode, Node> zeroTripProxies = LoopTransformations.invert(loop, ifNode, originalProxyStamps);

                            // recompute cfg for induction variables of the inverted loop to protect
                            // the first iteration
                            LoopsData loopsData1 = context.getLoopsDataProvider().getLoopsData(graph);
                            for (Loop loop1 : loopsData1.loops()) {
                                if (loop1.loopBegin() == loop.loopBegin()) {
                                    /*
                                     * We can never ensure (only assert in test setups) that the
                                     * loop is detect as counted after inversion. This should be an
                                     * invariant, however we cannot enforce it. Thus, if there is
                                     * really something going obviously wrong we need to abort
                                     * compilation.
                                     */
                                    GraalError.guarantee(loop1.detectCounted(true), "Loop inversion must not destroy counted loop info");
                                    ProtectionData d = LoopTransformations.protectFirstLoopIteration(loop1, zeroTripProxies);
                                    EconomicMap<ProxyNode, PhiNode> proxyToProtectionPhi = d.proxyToPhiMap();
                                    if (safepointsToRewire != null) {
                                        for (Node n : safepointsToRewire) {
                                            assert n instanceof SafepointNode : Assertions.errorMessage("Must be a safepoint", n);
                                            SafepointNode s = (SafepointNode) n;
                                            s.setLoopLink(d.merge());
                                        }
                                    }
                                    setProtectionPhiStamps(graph, originalProxyStamps, proxyToProtectionPhi);
                                    graph.getDebug().dump(DebugContext.VERBOSE_LEVEL, graph, "After inverting loop %s", loop1.loopBegin());
                                    invertedLoops.checkAndMarkInc(loop1.loopBegin());
                                }
                            }
                            graph.getOptimizationLog().report(getClass(), "LoopInversion", loop.loopBegin());
                            new DeadCodeEliminationPhase().apply(graph);
                            if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
                                assert verifyInversion(graph, context);
                            }
                            continue outer;
                        }
                    } else {
                        NoRotationTestFound.increment(graph.getDebug());
                    }
                }
            }
            break;
        }
        return inverted;
    }

    /**
     * Loop inversion creates protection control flow during inversion see
     * {@link LoopTransformations#protectFirstLoopIteration(Loop, EconomicMap)} for
     * details. During this process a {@link PhiNode} is created for every original
     * {@link ProxyNode} of the original loop. These new phi nodes must not have weaker stamps than
     * the original proxy nodes. See {@link #invert(StructuredGraph, CoreProviders, NodeBitMap)} for
     * details.
     */
    public static void setProtectionPhiStamps(StructuredGraph graph, EconomicMap<ProxyNode, Stamp> originalProxyStamps, EconomicMap<ProxyNode, PhiNode> proxyToProtectionPhi) {
        MapCursor<ProxyNode, Stamp> cursor = originalProxyStamps.getEntries();
        while (cursor.advance()) {
            PhiNode newProxyPhi = proxyToProtectionPhi.get(cursor.getKey());
            if (newProxyPhi instanceof ValuePhiNode) {
                AbstractBeginNode guard = AbstractBeginNode.prevBegin(newProxyPhi.merge().forwardEndAt(0));
                PiNode oldBetterStampPi = graph.addWithoutUnique(new PiNode(newProxyPhi.valueAt(0), cursor.getValue(), guard));
                newProxyPhi.setValueAt(0, oldBetterStampPi);
                newProxyPhi.inferStamp();
            }
        }
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After setting protection phi stamps to original proxy stamps");
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (!graph.hasLoops()) {
            return;
        }

        if (!Loop.Options.DetectInvertedLoopsAsCounted.getValue(graph.getOptions())) {
            return;
        }
        EconomicSetNodeEventListener ev = new EconomicSetNodeEventListener();
        NodeBitMap invertedLoops = graph.createNodeBitMap();
        boolean inverted;
        try (NodeEventScope nes = graph.trackNodeEvents(ev)) {
            preprocess(graph, context);
            inverted = invert(graph, context, invertedLoops);
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            for (Loop loop : loopsData.loops()) {
                if (invertedLoops.isMarked(loop.loopBegin())) {
                    ev.getNodes().addAll(loop.whole().nodes());
                }
            }
        }
        canonicalizer.applyIncremental(graph, context, ev.getNodes());
        assert !inverted || verifyInversion(graph, context);
        if (InjectLoopCounterStampsPhase.Options.OptLoopPhiStamps.getValue(graph.getOptions())) {
            new InjectLoopCounterStampsPhase().apply(graph, context);
        }
        logIVAndExitVals(graph, context);
    }

    private static void logIVAndExitVals(StructuredGraph graph, CoreProviders context) {
        if (LOG_IV_VALUES) {
            for (Loop lex : context.getLoopsDataProvider().getLoopsData(graph).loops()) {
                if (lex.detectCounted() && lex.counted().isInverted()) {
                    EconomicMap<Node, InductionVariable> ivs = lex.getInductionVariables();
                    for (InductionVariable iv : ivs.getValues()) {
                        FixedNode f = lex.loopBegin().next();
                        ValueNode v1 = iv.valueNode();
                        ValueNode v2 = iv.exitValueNode();
                        String s = "[Inversion Marker] Entering " + lex.loopBegin() + " loop with iv=" + iv.toString() + " with value node " + v1 + "=%d and exitValue" + v2 + "=%d\n";
                        LogNode as = graph.add(new LogNode(s, v1, v2));
                        lex.loopBegin().setNext(null);
                        lex.loopBegin().setNext(as);
                        as.setNext(f);
                    }

                    FixedNode f = lex.loopBegin().next();
                    String s = "[Inversion Marker] Entering" + lex.loopBegin() + " loop with maxTripCount=%d\n";
                    LogNode as = graph.add(new LogNode(s, lex.counted().maxTripCountNode(), null));
                    lex.loopBegin().setNext(null);
                    lex.loopBegin().setNext(as);
                    as.setNext(f);

                }
            }
        }
    }

    private static final boolean LOG_IV_VALUES = false;

    public static IfNode findLoopInversionTest(Loop loop, boolean preCheck) {
        if (loop.detectCounted()) {
            IfNode ifNode = null;
            AbstractBeginNode countedExit = loop.counted().getCountedExit();
            if (!preCheck) {
                if (!(countedExit instanceof LoopExitNode)) {
                    return null;
                }
            }
            if (countedExit.predecessor() instanceof IfNode && countedExit.predecessor().predecessor() == loop.loopBegin()) {
                ifNode = (IfNode) countedExit.predecessor();
                CountedLoopInfo cli = loop.counted();
                if (ifNode != cli.getLimitTest()) {
                    return null;
                }
                IntegerStamp startStamp = (IntegerStamp) cli.getBodyIVStart().stamp(NodeView.DEFAULT);
                IntegerStamp limitStamp = (IntegerStamp) cli.getLimit().stamp(NodeView.DEFAULT);

                if (cli.isConstantMaxTripCount() && cli.constantMaxTripCount().asLong() <= 1) {
                    // this loop will not be counted after inversion, it only does one iteration
                    return null;
                }
                /*
                 * loops without iterations should actually be cleaned up before, but phase ordering
                 * problems can generate these patterns before inversion. Since we need to ensure
                 * the loop is also counted (has iterations) after inversion, we do not invert such
                 * patterns.
                 */
                Direction direction = cli.getDirection();
                if (direction == Direction.Up) {
                    if (startStamp.asConstant() != null && limitStamp.asConstant() != null) {
                        if (startStamp.lowerBound() >= limitStamp.lowerBound()) {
                            return null;
                        }
                    }
                } else {
                    assert direction == Direction.Down : direction;
                    if (startStamp.asConstant() != null && limitStamp.asConstant() != null) {
                        if (startStamp.lowerBound() < limitStamp.lowerBound()) {
                            return null;
                        }
                    }
                }
                LogicNode condition = ifNode.condition();
                if (!(condition instanceof CompareNode)) {
                    return null;
                }
                if (((CompareNode) condition).condition().isUnsigned()) {
                    // See Loop documentation about inverted loops and unsigned checks.
                    return null;
                }
                if (signedLoopMayBecomeUnsignedByInversion(cli.getLimitCheckedIV())) {
                    // may be wrongly optimized later based on wrong signedness assumptions
                    return null;
                }
                if (condition.hasMoreThanOneUsage()) {
                    // inversion moves the condition to the loop end edge, if there are more usages,
                    // this means we cannot move it down the path without changing semantics of
                    // other code
                    //
                    // TODO duplicate the condition, currently not necessary
                    return null;
                }
                for (Node input : condition.inputs()) {
                    if (input instanceof ValueNode) {
                        if (!(((ValueNode) input).isConstant() || loop.loopBegin().isPhiAtMerge(input) || loop.isOutsideLoop(input))) {
                            /*
                             * if there are floating nodes between the condition of the rotation
                             * test, they would need to be unfolded during inversion, this is
                             * currently not supported
                             *
                             * OR
                             *
                             * this loop is already in its inverted form, in which case we are fine
                             */
                            return null;
                        }
                    }
                }
                ifNode.getDebug().log("LoopInversion: Found counted test %s %s %s", loop.loopBegin(), loop.loopBegin().graph(), ifNode);
            }
            return ifNode;
        }
        return null;
    }

}
