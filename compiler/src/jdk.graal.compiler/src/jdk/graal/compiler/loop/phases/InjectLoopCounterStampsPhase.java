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

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.IntegerHelper;
import jdk.graal.compiler.nodes.util.SignedIntegerHelper;
import jdk.graal.compiler.nodes.util.UnsignedIntegerHelper;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;

/**
 * This phase tries to inject narrower stamps on the {@link InductionVariable} of
 * {@link CountedLoopInfo} counted loops. It also tries to adjust loop frequency information if the
 * counter is more precise.
 *
 * For this the phase computes the bounds of 2 induction variables: the counter IV and the basic
 * induction variable that the counter IV is derived from. The counter IV bound is used for the loop
 * frequency because that is the IV checked against {@link CountedLoopInfo#getLimit()}. The
 * {@link BasicInductionVariable} derived bound is used to inject the stamp for the IVs
 * {@link ValuePhiNode}. All other nodes involved in the IV can then infer stamp individually.
 *
 * Additionally, this phase tries to inject stamps for {@link BasicInductionVariable}s that are not
 * {@linkplain CountedLoopInfo#getLimitCheckedIV() limit-checked}.
 */
public class InjectLoopCounterStampsPhase extends BasePhase<CoreProviders> {

    public static class Options {
        @Option(help = "Injects stamps on induction variables.", type = OptionType.Debug) public static final OptionKey<Boolean> OptLoopPhiStamps = new OptionKey<>(true);
    }

    public static Stamp betterLoopCounterStamp(CountedLoopInfo counted, InductionVariable counter, IntegerStamp initStampBaseIV, IntegerStamp extremumStamp, IntegerStamp originalStamp) {
        Stamp boundStamp = getBetterBoundStamp(counted, counter, initStampBaseIV, extremumStamp);
        Stamp stamp = originalStamp.tryImproveWith(boundStamp);
        return stamp;
    }

    private static Stamp getBetterBoundStamp(CountedLoopInfo counted, InductionVariable iv, IntegerStamp initStampBaseIV, IntegerStamp extremumStamp) {
        /*
         * We need to make a conceptual differentiation between the limit checked IV here and the
         * rest of the IVs. For the limit checked IV we calculate the stamp that is later used for
         * the max trip count. We know the counter IV never overflows and is subject to a limit in
         * unsigned or signed range. For the rest of the IVs we know nothing, they are not compared
         * against anything. We must not calculate their range in unsigned range - they are always
         * treated as signed since they are evaluated using the trip counted itself.
         */
        IntegerHelper helper;
        final int bits = initStampBaseIV.getBits();
        final boolean isLimitCheckedIV = (iv == counted.getLimitCheckedIV());
        if (isLimitCheckedIV) {
            boolean canBeNegative = iv instanceof DerivedInductionVariable && initStampBaseIV.canBeNegative();
            /*
             * While we would like to use the helper of the counted loop, the basic IV we are
             * dealing with here might be in the range [NEG:POS] while the IV that is limit checked
             * is only positive. We have to adjust for that.
             *
             * Inverted unsigned loops additionally require the loop body IV and the checked IV to
             * stay within the same signedness regime. If that precondition is violated, unsigned
             * helper math can collapse the base IV stamp to a constant value even though the
             * backedge value is still on the negative side. Fall back to signed reasoning here as a
             * defense-in-depth guard.
             */
            helper = counted.isUnsignedCheck() && !canBeNegative && !hasMismatchedInvertedUnsignedStarts(counted) ? new UnsignedIntegerHelper(bits) : new SignedIntegerHelper(bits);
        } else {
            helper = new SignedIntegerHelper(bits);
        }
        Stamp boundStamp = getBoundStamp(iv, initStampBaseIV, extremumStamp, helper);
        return boundStamp;

    }

    /**
     * Detects the inverted unsigned loop shape where the body IV and checked IV cross the signed
     * zero boundary between the body and backedge check.
     *
     * <pre>
     * int body = -1;
     * int checked = 0;
     * do {
     *     use(body);         // body IV is still negative on the first trip
     *     body++;
     *     checked++;
     * } while (Integer.compareUnsigned(checked, limit) < 0);
     * </pre>
     *
     * In that configuration the loop may still be rediscovered as an unsigned counted loop, but
     * using unsigned helper math for the checked IV's root phi can collapse the base IV stamp to a
     * constant value even though its backedge value remains on the negative side. This phase-local
     * guard does not replace the earlier legality checks in loop inversion; it only prevents stamp
     * injection from strengthening the phi with bounds derived from the wrong signedness regime.
     */
    private static boolean hasMismatchedInvertedUnsignedStarts(CountedLoopInfo counted) {
        if (!counted.isUnsignedCheck() || !counted.isInverted() || counted.getBodyIVEqualsLimitCheckedIV()) {
            return false;
        }
        ValueNode bodyStart = counted.getBodyIVStart();
        ValueNode checkedStart = counted.getLimitCheckedIV().initNode();
        bodyStart.inferStamp();
        checkedStart.inferStamp();
        IntegerStamp bodyStartStamp = (IntegerStamp) bodyStart.stamp(NodeView.DEFAULT);
        IntegerStamp checkedStartStamp = (IntegerStamp) checkedStart.stamp(NodeView.DEFAULT);
        Direction direction = counted.getDirection();
        if (direction == Direction.Up) {
            return bodyStartStamp.canBeNegative() && !checkedStartStamp.canBeNegative();
        } else if (direction == Direction.Down) {
            return !bodyStartStamp.canBeNegative() && checkedStartStamp.canBeNegative();
        }
        return false;
    }

    private static Stamp getBoundStamp(InductionVariable currentIV, IntegerStamp initStamp, IntegerStamp extremumStamp, IntegerHelper helper) {
        long lowerBound;
        long upperBound;
        final long counterStride = currentIV.constantStride();
        final long lowerBoundExtremum = helper.lowerBound(extremumStamp);
        final long upperBoundExtremum = helper.upperBound(extremumStamp);
        final long lowerBoundInit = helper.lowerBound(initStamp);
        final long upperBoundInit = helper.upperBound(initStamp);
        final Direction direction = currentIV.getRootIV().direction();
        if (direction == InductionVariable.Direction.Up) {
            // i in [init..extremum+stride]
            lowerBound = lowerBoundInit;
            long maxValue = helper.maxValue();
            if (helper.isGreater(upperBoundExtremum, maxValue - counterStride)) {
                // While we proved the counter won't overflow, the extremum stamp might
                // not reflect that. If adding the stride would lead to wrap around,
                // just use maxValue
                upperBound = maxValue;
            } else {
                upperBound = helper.max(upperBoundExtremum + counterStride, lowerBound);
            }
        } else if (direction == Direction.Down) {
            // i in [extremum-abs(stride)..init]
            upperBound = upperBoundInit;
            long minValue = helper.minValue();
            if (helper.isSmaller(lowerBoundExtremum, minValue - counterStride)) {
                // While we proved the counter won't overflow, the extremum stamp might
                // not reflect that. If adding the stride would lead to wrap around,
                // just use minValue
                lowerBound = minValue;
            } else {
                lowerBound = helper.min(lowerBoundExtremum + counterStride, upperBound);
            }
        } else {
            return initStamp.unrestricted();
        }
        Stamp boundStamp = helper.stamp(lowerBound, upperBound);
        return boundStamp;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops();
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders providers) {
        if (graph.hasLoops()) {
            LoopsData loopsData = providers.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();
            EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
            try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
                for (Loop loop : loopsData.outerFirst()) {
                    if (!loop.isCounted()) {
                        continue;
                    }
                    CountedLoopInfo counted = loop.counted();
                    InductionVariable counter = counted.getLimitCheckedIV();
                    if (canInjectExtremumStamp(counted, counter)) {

                        tryInjectBetterStampForBaseIV(loop, counter, ec, () -> {
                            graph.getOptimizationLog().report(getClass(), "LoopCounterStampImprovement", counter.getRootIV().valueNode());
                            injectOtherBaseIVBounds(loop, counter, ec);

                        });

                        /**
                         * The stamp for lower and upper bound of the entire loop is computed only
                         * from the counter IV which is the one checked against the limit.
                         */
                        injectBetterLoopFrequency(loop, counted, counter, counted.getCounterIntegerHelper());

                    } else if (canInjectMonotonicBound(counted, counter)) {
                        /*
                         * A non-constant stride prevents the full extremum-based stamp
                         * computation above, but an up-counted non-overflowing counter can still
                         * expose its init-side lower bound to conditional elimination.
                         */
                        tryInjectMonotonicBoundForBaseIV(counter, ec, () -> {
                            graph.getOptimizationLog().report(getClass(), "LoopCounterMonotonicStampImprovement", counter.getRootIV().valueNode());
                        });
                    }
                }
            }
            if (!ec.getNodes().isEmpty()) {
                CanonicalizerPhase.create().applyIncremental(graph, providers, ec.getNodes());
            }
        }
    }

    /**
     * Returns true when the limit-checked counter has the properties required for computing a full
     * extremum-based stamp: constant stride, no possible counter overflow, and a stride whose absolute
     * value is representable at the counter bit width.
     */
    private static boolean canInjectExtremumStamp(CountedLoopInfo counted, InductionVariable counter) {
        return counter.isConstantStride() && counted.counterNeverOverflows() &&
                        !NumUtil.absOverflows(counter.constantStride(), IntegerStamp.getBits(counter.strideNode().stamp(NodeView.DEFAULT)));
    }

    /**
     * Returns true when the limit-checked counter can expose its monotonic lower bound without
     * computing a full extremum stamp. This limited path only handles signed, up-counted root IVs with
     * a non-constant stride and no possible counter overflow.
     */
    private static boolean canInjectMonotonicBound(CountedLoopInfo counted, InductionVariable counter) {
        return counter == counter.getRootIV() && !counter.isConstantStride() && !counted.isUnsignedCheck() && counted.counterNeverOverflows() &&
                        counter.direction() == Direction.Up;
    }

    /**
     * Tries to inject a better stamp for the {@link ValuePhiNode} of the
     * {@link InductionVariable#getRootIV()} {@code counter}. The bounds are derived from the
     * {@link InductionVariable#initNode()} and {@link InductionVariable#extremumNode()}.
     */
    @SuppressWarnings("try")
    private static void tryInjectBetterStampForBaseIV(Loop loop, InductionVariable counter, EconomicSetNodeEventListener ec, Runnable betterStampAction) {
        final CountedLoopInfo counted = loop.counted();
        ValueNode extremumNodeBaseIV;
        ValueNode initNodeBaseIV;

        /**
         * Note that we take the stamps for init and extremum here only of the base iv portion of
         * the IV: that is we are only interested in the phi, the other stamps are inferred then
         * automatically.
         */
        try (DebugCloseable position = loop.loopBegin().withNodeSourcePosition()) {
            extremumNodeBaseIV = counter.getRootIV().extremumNode();
            initNodeBaseIV = counter.getRootIV().initNode();
        }

        /*
         * Catch some simple cases where the init/extremum depends on an outer loop phi that we just
         * optimized:
         */
        extremumNodeBaseIV.inferStamp();
        initNodeBaseIV.inferStamp();
        IntegerStamp extremumStampBaseIV = (IntegerStamp) extremumNodeBaseIV.stamp(NodeView.DEFAULT);
        IntegerStamp initStampBaseIV = (IntegerStamp) initNodeBaseIV.stamp(NodeView.DEFAULT);

        ValuePhiNode phi = getBaseIvPhi(counter);

        Stamp boundStamp = getBetterBoundStamp(counted, counter, initStampBaseIV, extremumStampBaseIV);
        tryImprovePhiStamp(phi, boundStamp, ec, betterStampAction);

        ec.getNodes().add(extremumNodeBaseIV);
    }

    /**
     * Injects the lower bound implied by an up-counted, non-overflowing loop counter.
     *
     * Consider a nested loop where the inner counter has a stride taken from the outer loop:
     *
     * <pre>
     * int k = i + i;
     * while (k <= size) {
     *     flags[k - 1] = false;
     *     k += i;
     * }
     * </pre>
     *
     * If {@code size} and {@code flags.length} are both known to be 5000, the loop condition gives
     * conditional elimination the body fact {@code k <= 5000}. What is missing is the init-side
     * fact that {@code k} cannot move below its entry value. The regular path above cannot compute a
     * full extremum stamp because {@code i} is not a constant stride, and computing an exact trip
     * count would reintroduce expensive division. For this case we only refine the loop phi with
     * the monotonic bound, for example {@code k >= lowerBound(i + i)}, and leave the other side of
     * the stamp unchanged. Later stamp inference can then derive that {@code k - 1} is non-negative
     * while conditional elimination derives the upper bound from the loop guard.
     */
    private static void tryInjectMonotonicBoundForBaseIV(InductionVariable counter, EconomicSetNodeEventListener ec, Runnable betterStampAction) {
        ValueNode initNodeBaseIV = counter.initNode();
        initNodeBaseIV.inferStamp();
        IntegerStamp initStampBaseIV = (IntegerStamp) initNodeBaseIV.stamp(NodeView.DEFAULT);

        ValuePhiNode phi = getBaseIvPhi(counter);
        IntegerStamp originalStamp = (IntegerStamp) phi.stamp(NodeView.DEFAULT);
        IntegerStamp monotonicStamp = IntegerStamp.create(originalStamp.getBits(), initStampBaseIV.lowerBound(), originalStamp.upperBound());

        tryImprovePhiStamp(phi, monotonicStamp, ec, betterStampAction);
        ec.getNodes().add(initNodeBaseIV);
    }

    private static void tryImprovePhiStamp(ValuePhiNode phi, Stamp boundStamp, EconomicSetNodeEventListener ec, Runnable betterStampAction) {
        Stamp betterStampLoopPhi = phi.stamp(NodeView.DEFAULT).tryImproveWith(boundStamp);
        if (betterStampLoopPhi != null) {
            phi.refineStampWith(betterStampLoopPhi);
            for (Node usage : phi.usages()) {
                ec.getNodes().add(usage);
            }
            betterStampAction.run();
        }
    }

    private void injectOtherBaseIVBounds(Loop loop, InductionVariable limitCheckedIV, EconomicSetNodeEventListener ec) {
        StructuredGraph graph = loop.loopBegin().graph();
        /*
         * Instead of processing induction variables we only process loop phis here. There are
         * multiple induction variables used inside a loop, however we only care about the
         * BasicInductionVariables. We only inject stamps for the basic induction variables. The
         * derived ones and their value nodes get better stamps via inferStamp then. The phi must
         * only have a stamp that is represented by its back values and if the back values are no
         * simple IVs we give up anyway.
         */
        for (ValuePhiNode phi : loop.loopBegin().valuePhis()) {
            // check if there is an IV registered for the phi and if so use it
            InductionVariable iv = loop.getInductionVariables().get(phi);
            if (iv == null) {
                continue;
            }

            final boolean derivedFromLimitCheckedIV = limitCheckedIV.getRootIV() == iv.getRootIV();
            if (derivedFromLimitCheckedIV) {
                /*
                 * We want to ensure we are not computing, yet another, improved stamp of the
                 * limitCheckedIV. Thus, we make sure we are dealing with a truly distinct IV.
                 */
                continue;
            }
            if (!iv.isConstantStride()) {
                // cannot compute the extremum
                continue;
            }
            if (NumUtil.absOverflows(iv.constantStride(), IntegerStamp.getBits(iv.strideNode().stamp(NodeView.DEFAULT)))) {
                // cannot reason about the stride without abs overflow
                continue;
            }
            if (!loop.counted().ivCanNeverOverflow(iv)) {
                // cannot statically that iv will not overflow
                continue;
            }
            tryInjectBetterStampForBaseIV(loop, iv, ec, () -> {
                graph.getOptimizationLog().report(getClass(), "LoopCounterStampImprovement Other IV", phi);
            });
        }
    }

    private static void injectBetterLoopFrequency(Loop loop, CountedLoopInfo counted, InductionVariable counter, IntegerHelper helper) {
        IntegerStamp initStampLimitCheckedIv = (IntegerStamp) counter.initNode().stamp(NodeView.DEFAULT);
        IntegerStamp extremumStampLimitCheckedIv = (IntegerStamp) counter.extremumNode().stamp(NodeView.DEFAULT);

        /*
         * Adjust the loop frequency if its current value is higher than it can really be. For
         * example, loops in snippets have estimated frequencies, but after inlining the snippet we
         * may have more information about the iteration space.
         */
        IntegerStamp integerStamp = (IntegerStamp) getBoundStamp(counter, initStampLimitCheckedIv, extremumStampLimitCheckedIv, helper);
        if (integerStamp != null) {
            long iterationSpan = integerStamp.upperBound() - integerStamp.lowerBound();
            if (iterationSpan >= 0 && iterationSpan < Long.MAX_VALUE) {
                /*
                 * We know no arithmetic exception can be thrown here because we already checked no
                 * overflow is possible.
                 */
                long maxIterationCount = iterationSpan / NumUtil.safeAbs(counter.constantStride(), IntegerStamp.getBits(counter.strideNode().stamp(NodeView.DEFAULT))) + 1;

                if (ProfileSource.isTrusted(loop.localFrequencySource())) {
                    /*
                     * Use the frequency calculation if it is more precise than the profile;
                     * otherwise, trust the source.
                     */
                    if (loop.localLoopFrequency() > maxIterationCount) {
                        LoopTransformations.adaptCountedLoopExitProbability(counted.getCountedExit(), maxIterationCount);
                    }
                } else {
                    // we dont have profiles, trust the math
                    LoopTransformations.adaptCountedLoopExitProbability(counted.getCountedExit(), maxIterationCount);
                }
            }
        }
    }

    private static ValuePhiNode getBaseIvPhi(InductionVariable iv) {
        return iv.getRootIV().valueNode();
    }

}
