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

import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.clamp;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.isOriginalLimitCheckedIV;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.selfOrSignExtendInput;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.toInt;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.toLongOrSelf;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.unwrap32To64Extension;
import static jdk.graal.compiler.loop.phases.CountedStripMiningUtility.uMin;
import static jdk.graal.compiler.nodes.ConstantNode.forInt;
import static jdk.graal.compiler.nodes.ConstantNode.forLong;
import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.add;
import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.mul;
import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.sub;
import static jdk.graal.compiler.phases.common.util.LoopUtility.isLong;

import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DerivedConvertedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedOffsetInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedScaledInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.ExcludeFromJacocoGeneratedReport;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.replacements.nodes.LogNode;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Implements a long (64bit) to int (32bit) range check elimination (RCE) designed to run after
 * {@link CountedStripMiningPhase}.
 *
 * This optimization is based on C2's implementation for long-to-int range check elimination based
 * on loopnode.cpp PhaseIdealLoop::transform_long_range_checks.
 *
 * A lot of JavaDoc regarding the range check determination and extraction can be found in
 * {@link #findRC(CompareNode, Loop)}.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/b8aa130bab715f187476181acc5021b27958833f/src/hotspot/share/opto/loopnode.cpp#L1179-L1469",
          sha1 = "ad2fd9d73491344f67c4f2af40099882ff4dad5c")
// @formatter:on
public class RangeCheckEliminationPhase extends BasePhase<MidTierContext> implements FloatingGuardPhase {

    public static class Options {
        //@formatter:off
        @Option(help = "Performs range check elimination for Java long type range checks. " +
                "Requires SpeculativeGuardMovement=true to be enabled.", type = OptionType.Expert)
        public static final OptionKey<Boolean> RangeCheckElimination = new OptionKey<>(true);
        @Option(help = "Log all range check sub values to stdout.", type = OptionType.Debug)
        public static final OptionKey<Boolean> RCELogRangeCheckValues = new OptionKey<>(false);
        @Option(help = "Force range check elimination even when SpeculativeGuardMovement is false.", type = OptionType.Debug)
        public static final OptionKey<Boolean> ForceRCE = new OptionKey<>(false);
        //@formatter:on
    }

    private final CanonicalizerPhase canonicalizer;

    public RangeCheckEliminationPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        canonicalizer.notApplicableTo(graphState),
                        NotApplicable.when(!graphState.getGuardsStage().allowsFloatingGuards(), "This phase needs floating guards"));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, MidTierContext context) {
        if (prolog(graph, context)) {
            EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
            try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
                LoopsData ld = context.getLoopsDataProvider().getLoopsData(graph);
                ld.detectCountedLoops();
                for (Loop countedLoop : ld.countedLoops()) {
                    if (hasCandidateRangeCheckGuards(countedLoop)) {
                        c2StyleStripMiningRCE(countedLoop, context.getConstantReflection());
                    }
                }
            }
            if (!ec.getNodes().isEmpty()) {
                canonicalizer.applyIncremental(graph, context, ec.getNodes());
            }
        }
    }

    /**
     * Strip-mining and long-to-int range check elimination heavily depends on
     * {@link SpeculativeGuardMovementPhase}. As such it may be necessary that we first strip-mine a
     * loop, run some portion of {@link SpeculativeGuardMovementPhase}, then run long-to-int range
     * check elimination followed by a final step of {@link SpeculativeGuardMovementPhase}.
     *
     * This method determines if a round of {@link SpeculativeGuardMovementPhase} should be ran
     * before the range check elimination.
     */
    private static boolean prolog(StructuredGraph graph, MidTierContext context) {
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before running RCE prolog speculative guard movement");
        boolean runGuardMotion = false;
        for (LoopBeginNode lb : graph.getNodes(LoopBeginNode.TYPE)) {
            if (lb.isCountedStripMinedInner()) {
                runGuardMotion = true;
                break;
            }
        }
        LoopsData ld = null;
        NodeBitMap toProcess = null;
        if (runGuardMotion) {
            runGuardMotion = false;
            ld = context.getLoopsDataProvider().getLoopsData(graph);
            ld.detectCountedLoops();
            for (Loop countedLoop : ld.countedLoops()) {
                if (hasCandidateRangeCheckGuards(countedLoop)) {
                    runGuardMotion = true;
                    if (toProcess == null) {
                        toProcess = graph.createNodeBitMap();
                    }
                    toProcess.markAll(countedLoop.inside().nodes());
                    break;
                }
            }
        }
        try {
            if (runGuardMotion && graph.getSpeculationLog() != null) {
                assert toProcess != null;
                // there are test setups where we force RCE without speculative guard movement
                if (GraalOptions.SpeculativeGuardMovement.getValue(graph.getOptions())) {
                    SpeculativeGuardMovementPhase.performSpeculativeGuardMovement(context, graph, ld, toProcess);
                }
                return true;
            }
            return false;
        } finally {
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After running RCE prolog speculative guard movement");
        }
    }

    /**
     * Determines whether a loop contains any guards with comparisons that are candidates for C2-style range check elimination.
     */
    private static boolean hasCandidateRangeCheckGuards(Loop loop) {
        for (Node inside : loop.inside().nodes()) {
            if (inside instanceof GuardNode guard) {
                if (isCandidateLongRangeCheckGuard(guard)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines whether a guard has the pattern that C2-style range check elimination can optimize.
     * Candidate guards have a condition of the form {@code index <u range}, and deoptimize when it is false, so:
     *
     * <ul>
     * <li> The guard must not be negated, because the C2 transformation is conservative and can make a comparison false
     * when the original was true, which is safe when it can only result in extra deopts.
     * <li> The guard must have an unsigned 64-bit comparison.
     * </ul>
     */
    private static boolean isCandidateLongRangeCheckGuard(GuardNode guard) {
        return !guard.isNegated() && guard.getCondition() instanceof IntegerBelowNode c && isLong(c.getX());
    }

    /**
     * Tristate expressing the search result for a range check operation.
     */
    enum Tristate {
        /**
         * No range check found.
         */
        NO_RC,
        /**
         * Re-do the range check search because the range check has been rewritten.
         */
        REDO,
        /**
         * Range check found.
         */
        RC
    }

    /**
     * Checks whether a matched range check satisfies the C2 transformation preconditions. An int
     * scale may cause this method to rewrite the guard and request another range-check search.
     *
     * @param rc the matched strip-mined range check
     * @param graph the graph containing the range check
     * @param loop the strip-mined inner loop
     * @param guard the guard implementing the range check
     * @param rewrittenScaledInInt set of guards already rewritten for int-scale overflow
     *
     * @return the qualification result, including whether the search must be repeated
     */
    private static Tristate rangeCheckQualifiesForOptimization(StripMinedRC rc, StructuredGraph graph, Loop loop, GuardNode guard, EconomicSet<GuardNode> rewrittenScaledInInt) {
        // Checkstyle: stop
        if (rc == null) {
            // for whatever reason we have not been able to match a proper chain of
            // induction variables to look like a range check, abort
            return Tristate.NO_RC;
        }
        if (!rc.k(true).isConstant()) {
            // if k is not constant and thus the invariant cannot be checked, we could
            // use a speculation to do so but this is not supported for now
            return Tristate.NO_RC;
        }
        final long scale = rc.k(true).asJavaConstant().asLong();
        if (!NumUtil.isInt(scale)) {
            // scale to large to properly proceed
            return Tristate.NO_RC;
        }

        int constantLimit = -1;
        // for a counted strip mined loop we take the original strip
        if (loop.loopBegin().isCountedStripMinedInner()) {
            constantLimit = loop.loopBegin().getStripMinedLimit();
        }

        if (rewrittenScaledInInt.contains(guard)) {
            /*
             * This guard was already rewritten from the int case and guarantees that no overflow
             * is possible.
             */
            return Tristate.RC;
        }

        if (constantLimit <= 0) {
            // a missing strip bound cannot establish the overflow precondition
            return Tristate.NO_RC;
        }

        if (scaleOverflowsWithLimit(scale, constantLimit, 32)) {
            /*
             * The scaled inner displacement covered by one strip must fit in the signed 32-bit
             * range required by the long-to-int transformation. This could be handled by choosing
             * a different strip mining limit, but that is not implemented yet (TODO GR-38023).
             * For example, with a large scale of 2^27 and a strip size of 4096,
             * `scale * constantLimit = (1 << 27) * 4096`, which overflows
             */
            return Tristate.NO_RC;
        }

        if (rc.scaleInIntRange) {
            /*
             * For scaling in int arithmetic, even if `scale * constantLimit` does not overflow that
             * does not prove that scaling the original index from the strip mined loop cannot overflow,
             * because it doesn't account for the strip's starting IV value, so must always try the rewrite.
             * For example, in a loop like:
             *   for (int i = 4000; i < 9999; i++) {
             *      long index = i * 500_000;
             *   }
             * constantLimit = 4096 (from inner strip size), scale = 500_000
             * at i=4300, `i * scale = 4300 * 500_000` overflows but `constantLimit * scale` doesn't
             */
            Tristate tryRewrite = tryHandleOverflowInIntCase(rc, loop, graph, guard);
            if (tryRewrite == Tristate.REDO) {
                rewrittenScaledInInt.add(guard);
            }
            return tryRewrite;
        }

        // Checkstyle: resume
        return Tristate.RC;
    }

    /**
     * Checks if the product of the given scale and constant limit would overflow when computed as
     * an integer.
     */
    private static boolean scaleOverflowsWithLimit(long scale, long constantLimit, int bits) {
        try {
            switch (bits) {
                case 32:
                    Math.multiplyExact(NumUtil.safeToInt(scale), NumUtil.safeToInt(constantLimit));
                    break;
                case 64:
                    Math.multiplyExact(scale, constantLimit);
                    break;
                default:
                    throw GraalError.shouldNotReachHere("Unknown bit size " + bits);
            }
            return false;
        } catch (ArithmeticException e) {
            return true;
        }
    }

    /**
     * Attempts to handle integer overflow when scaling an induction variable in a range check. This
     * method is part of the long-to-int range check elimination optimization.
     * <p>
     * It rewrites the expression `(int)i*K (scale) + L` to `i*(long)K + L <u64
     * unsigned_min((long)max_jint + L + 1, R)` if certain conditions are met, such as `L` and `R`
     * being long and `scale` being int.
     */
    // Checkstyle: stop
    private static Tristate tryHandleOverflowInIntCase(StripMinedRC rc, Loop loop, StructuredGraph graph, GuardNode guard) {
        /*
         * C2 code loopnode.cpp#transform_long_range_checks
         *
         * rewrite (int)i*K (scale) + L to
         *
         * i*(long)K + L <u64 unsigned_min((long)max_jint + L + 1, R)
         *
         * In order for this transformation to be valid L,R must be long and only scale can be int
         */
        ValueNode conversionIV = rc.convertOp.getValue();
        /*
         * In the Graal universe the following expression (int)i*K (scale) + L
         *
         * is offsetIV( convertIV( scaledIV(i *scale) toLong) + L)
         *
         * where i is int, scaledIV is still int, then the scaledIV is extended to long and the
         * offset is added in long range
         *
         * Note that offset (rc.offsetIV) can be null, in this case the code uses an offset of 0
         */
        if (isLong(rc.l()) && isLong(rc.r()) && loop.getInductionVariables().get(conversionIV) instanceof DerivedScaledInductionVariable) {
            // Mimic C2 handling for int loop scale overflows

            if (!isC2IntScaleSafe(rc)) {
                return Tristate.NO_RC;
            }

            // R must be nonnegative for this transformation
            ValueNode R = intoLongNonNegativeRange(rc.r(), loop.loopBegin());
            if (R == null) {
                return Tristate.NO_RC;
            }

            // Node* max_jint_plus_one_long = _igvn.longcon((jlong)max_jint + 1);
            long maxJIntPlusOne = NumUtil.maxValue(32) + 1L;
            ValueNode max_jint_plus_one_long = forLong(maxJIntPlusOne, graph);
            // Node* max_range = new AddLNode(max_jint_plus_one_long, L);
            ValueNode maxRange = g(add(max_jint_plus_one_long, rc.l()), graph);
            // R = MaxNode::unsigned_min(R, max_range, TypeLong::POS, _igvn);
            ValueNode newR = uMin(R, maxRange, graph);

            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating new R %s", newR);
            // additionally, rewrite (long)((int)i*(int)K)+L to a long version
            // where we perform (long)i * (long)K. Build it from the IV metadata instead of the
            // concrete arithmetic node: canonicalization may represent the scale as a negate or a
            // shift, and a long shift would not preserve the signed int scale for shift distance 31.
            ValueNode longScaleBase = toLongOrSelf(rc.scaleIV.getBase().valueNode(), graph);
            ValueNode longScale = toLongOrSelf(rc.scaleIV.getScale(), graph);
            ValueNode newScale = graph.addWithoutUnique(new MulNode(longScaleBase, longScale));
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating new scale %s", newScale);
            CompareNode compare = (CompareNode) guard.getCondition();
            if (compare.hasMoreThanOneUsage()) {
                CompareNode copy = (CompareNode) compare.copyWithInputs(true);
                guard.replaceFirstInput(compare, copy);
                compare = copy;
            }

            // offset IV can be missing
            if (rc.offsetIV == null && rc.offset.isConstant() && rc.offset.asJavaConstant().asLong() == 0 && rc.scaleIV != null) {
                if (compare.getX() == rc.convertOp) {
                    compare.replaceFirstInput(compare.getX(), newScale);
                } else if (compare.getY() == rc.convertOp) {
                    compare.replaceFirstInput(compare.getY(), newScale);
                }
                compare.replaceFirstInput(rc.r(), newR);
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating new compare account for scale %s", compare);
            } else if (rc.offsetIV != null) {
                BinaryNode clonee = (BinaryNode) rc.offsetIV.valueNode().copyWithInputs();
                if (clonee.getX() == rc.convertOp) {
                    clonee.setX(newScale);
                } else {
                    GraalError.guarantee(clonee.getY() == rc.convertOp, "Convert ops must match: %s != %s", clonee.getY(), rc.convertOp);
                    clonee.setY(newScale);
                }
                if (compare.getX() == rc.offsetIV.valueNode()) {
                    compare.replaceFirstInput(compare.getX(), clonee);
                } else if (compare.getY() == rc.offsetIV.valueNode()) {
                    compare.replaceFirstInput(compare.getY(), clonee);
                }
                compare.replaceFirstInput(rc.r(), newR);
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating new compare account for scale %s", compare);
            } else {
                return Tristate.NO_RC;
            }
            return Tristate.REDO;
        } else {
            return Tristate.NO_RC;
        }
    }
    // Checkstyle: resume

    /**
     * C2's int-scale rewrite is incorrect for some edge-cases. This function
     * determines whether it is safe to apply to a given range check.
     * Specifically, it guards against these cases:
     *
     * <ul>
     * <li> When the scaled IV is the subtrahend of an offset IV, and negating the int product may overflow
     *      ({@link #isC2NegatedIntScaleSafe})
     * <li> When the product may overflow and a very large offset can 'mask' said overflow
     *      ({@link #isC2IntScaleOffsetSafe})
     * </ul>
     */
    private static boolean isC2IntScaleSafe(StripMinedRC rc) {
        if (rc.scaleWasNegated && !isC2NegatedIntScaleSafe(rc)) {
            return false;
        }
        IntegerStamp offsetStamp = (IntegerStamp) rc.l().stamp(NodeView.DEFAULT);
        return isC2IntScaleOffsetSafe(offsetStamp);
    }

    /**
     * For the {@code offset - (long)((int)scale*i)} pattern, C2 normalizes it as
     * {@code offset + (long)(-scale*i)}. This is incorrect when the original product equals
     * {@code Integer.MIN_VALUE}, because the subtraction happens in {@code long} where it does not
     * overflow, but negating it as {@code int} results in the same {@code Integer.MIN_VALUE}.
     * <p>
     * For example:
     * <pre>
     *     long index = offset - (long) (i * 8);
     * with
     *     int i = 1<<28
     *     offset = 1L<<31
     * </pre>
     * {@code i * 8} overflows to {@code Integer.MIN_VALUE}, so the original index is
     * {@code (1L<<31) - (long)Integer.MIN_VALUE = 1L << 32} and fails the range check.
     * Rewriting the expression to {@code offset + (long) (-8 * i)} results in
     * {@code (1L<<31) + Integer.MIN_VALUE = 0}.
     * <p>
     * The safe approach is to refuse the transformation when the stamp for the scaled IV has that extremum.
     */
    private static boolean isC2NegatedIntScaleSafe(StripMinedRC rc) {
        IntegerStamp stamp = (IntegerStamp) rc.scaleIV.valueNode().stamp(NodeView.DEFAULT);
        if (stamp.contains(Integer.MIN_VALUE)) {
            return false;
        }
        return true;
    }

    /**
     * The C2 transformation for {@code int} scaling clamps only the upper endpoint.
     * Requiring the offset to fit in int makes that correct: a product below
     * {@code Integer.MIN_VALUE} remains negative after adding the offset, while a product above
     * {@code Integer.MAX_VALUE} is at least the clamped upper endpoint and therefore fails the
     * strict comparison.
     * <p>
     * Without this restriction, a very large offset could shift an overflowing int product
     * back into the interval accepted by the new guard.
     * For example:
     * <pre>
     *     (long)(i * 1_000_000) + offset &lt;u 100
     * with
     *     int i = 2150
     *     offset = -2_149_999_995L
     * </pre>
     * C2 would transform the check into
     * <pre>
     * (long)i * 1_000_000L + offset &lt;u unsigned_min(100, (1L<<31) + offset = -2_516_347)
     * ==>
     * (long)2150 * 1_000_000L + offset &lt;u 100
     * </pre>
     * <p>
     * The product {@code 2_150_000_000} originally overflows int to {@code -2_144_967_296},
     * adding the offset produces {@code -4_294_967_291L}, which must fail the range check.
     * The widened product, after adding the offset, results in
     * {@code 2_150_000_000 + -2_149_999_995 = 5} instead.
     */
    private static boolean isC2IntScaleOffsetSafe(IntegerStamp offsetStamp) {
        return offsetStamp.lowerBound() >= Integer.MIN_VALUE && offsetStamp.upperBound() <= Integer.MAX_VALUE;
    }

    /**
     * Find 64 bit range checks in the 32 bit (potentially strip mined) loop. Try to optimize them
     * by finding known patterns of {@code innerLoopInductionVariable + outerLoopInductionVariable}
     * and rewrite them to known range checks of the same truthfulness.
     * <p>
     * For the theory behind this logic see PhaseIdealLoop::transform_long_range_checks.
     * <p>
     * It basically iterates all guard nodes inside the loop, determines if one input is an
     * induction variable that is a derived offset/scaled IV based on the limit checked IV and the
     * outer loop phis and rewrites this long check to use an int check instead.
     */
    @SuppressWarnings("deprecation")
    private static void c2StyleStripMiningRCE(Loop loop, ConstantReflectionProvider constantReflection) {
        final StructuredGraph graph = loop.loopBegin().graph();
        final CountedLoopInfo countedLoop = loop.counted();
        if (!loop.loopBegin().isCountedStripMinedInner()) {
            // only consider strip mined loops that expose the inner-outer patterns we are safe to
            // reason about
            return;
        }
        // Checkstyle: stop
        if (countedLoop.getDirection() != Direction.Up) {
            /*
             * Strip mining always produces an up counted IV for the limit checked IV.
             */
            throw GraalError.shouldNotReachHere("unexpected counted loop direction"); // ExcludeFromJacocoGeneratedReport
        }
        if (!countedLoop.getLimitCheckedIV().isConstantStride()) {
            // not supported by C2
            return;
        }

        EconomicMap<GuardNode, StripMinedRC> rangeChecks = null;
        EconomicSet<GuardNode> rewrittenScaledInInt = EconomicSet.create();
        boolean redo = true;
        while (redo) {
            redo = false;
            for (Node inside : loop.inside().nodes()) {
                if (inside instanceof GuardNode) {
                    GuardNode guard = (GuardNode) inside;
                    if (isCandidateLongRangeCheckGuard(guard)) {
                        CompareNode compare = (CompareNode) guard.getCondition();
                        final StripMinedRC rc = findRC(compare, loop);
                        switch (rangeCheckQualifiesForOptimization(rc, graph, loop, guard, rewrittenScaledInInt)) {
                            case REDO:
                                redo = true;
                                continue;
                            case NO_RC:
                                continue;
                            case RC:
                                if (rangeChecks == null) {
                                    rangeChecks = EconomicMap.create();
                                }
                                rangeChecks.put(guard, rc);
                        }
                    }
                }
            }
            // redo once
            loop.invalidateFragmentsAndIVs();
            loop.resetCounted();
            loop.detectCounted();
        }

        if (rangeChecks == null) {
            return;
        }

        MapCursor<GuardNode, StripMinedRC> cursor = rangeChecks.getEntries();

        while (cursor.advance()) {
            GuardNode guard = cursor.getKey();
            StripMinedRC rc = cursor.getValue();

            final ValueNode C = toLongOrSelf(rc.c(), graph);
            final ValueNode K = toLongOrSelf(rc.k(false), graph);
            final ValueNode L = toLongOrSelf(rc.l(), graph);
            final ValueNode long_zero = forLong(0, graph);
            final ValueNode long_one = forLong(1, graph);
            final ValueNode R = intoLongNonNegativeRange(rc.r(), loop.loopBegin());

            if (R == null) {
                continue;
            }

            final long scale = rc.k(true).asJavaConstant().asLong();

            // Graal note
            // here starts the port of the C2 code loopnode.cpp#transform_long_range_checks
            // Start with 64-bit values:
            // i*K + L <u64 R
            // (C+j)*K + L <u64 R
            // j*K + Q <u64 R where Q = Q_first = C*K+L
            ValueNode Q_first = g(mul(C, K), graph);
            Q_first = g(add(Q_first, L), graph);

            // Compute endpoints of the range of values j*K + Q.
            // Q_min = (j=0)*K + Q; Q_max = (j=B_2)*K + Q
            ValueNode Q_min = Q_first;

            // Compute the exact ending value B_2 (which is really A_2 if S < 0)
            // ValueNode* B_2 = new LoopLimitNode(this->C, int_zero, inner_iters_actual_int,
            // int_stride);
            final ValueNode loopLimitNode = graph.addOrUniqueWithInputs(rc.originalStrideIV.extremumNode());
            final ValueNode maxTripCountNode = graph.addOrUniqueWithInputs(loop.counted().maxTripCountNode());
            ValueNode B_2 = loopLimitNode;

            /*
             * Graal NOTE: C2 uses the LopLimitNode (which is the final limit of the IV) whereas
             * Graal's loop API code extremumNode already computes the extremum of the IV (which is
             * C2LoopLimitNode - stride) so we do not remove stride again
             *
             * Also: this can be long or int, depending on the original loop since we offset all IVs
             * from the counter if in the strip mined inner loop
             */
            // B_2 = g(sub(B_2, forInt(int_stride, graph)), graph);
            B_2 = toLongOrSelf(B_2, graph);

            ValueNode Q_max = g(mul(B_2, K), graph);
            Q_max = g(add(Q_max, Q_first), graph);

            final int int_stride = rc.stride();

            if (scale * int_stride < 0) {
                ValueNode tmp = Q_min;
                Q_min = Q_max;
                Q_max = tmp;
            }

            // Now, mathematically, Q_max > Q_min, and they are close enough so that
            // (Q_max-Q_min) fits in 32 bits.

            // L_clamp = Q_min < 0 ? 0 : Q_min
            LogicNode Q_min_cmp = (LogicNode) g(CompareNode.createAnyCompareNode(Condition.LT, Q_min, long_zero, constantReflection), graph);
            // Graal NOTE: c2 CMoveNode has ctor order (condition,IFFalse,IFTrue,...) which is
            // reverse to Graal's
            ValueNode L_clamp = g(ConditionalNode.create(Q_min_cmp, long_zero, Q_min, NodeView.DEFAULT), graph);
            // (This could also be coded bitwise as L_clamp = Q_min & ~(Q_min>>63).)

            ValueNode Q_max_plus_one = g(add(Q_max, long_one), graph);

            // H_clamp = Q_max+1 < Q_min ? max_jlong : Q_max+1
            // (Because Q_min and Q_max are close, the overflow check could also be encoded
            // as Q_max+1 < 0 & Q_min >= 0.)
            ValueNode max_jlong_long = forLong(NumUtil.maxValue(64), graph);
            // Graal NOTE: c2 CMoveNode has ctor order (condition,IFFalse,IFTrue,...) which is
            // reverse to Graal's
            LogicNode Q_max_cmp = (LogicNode) g(CompareNode.createAnyCompareNode(Condition.LT, Q_max_plus_one, Q_min, constantReflection), graph);
            ValueNode H_clamp = g(ConditionalNode.create(Q_max_cmp, max_jlong_long, Q_max_plus_one, NodeView.DEFAULT), graph);
            // (This could also be coded bitwise as H_clamp = ((Q_max+1)<<1 | M)>>>1 where M
            // = (Q_max+1)>>63 & ~Q_min>>63.)

            // R_2 = clamp(R, L_clamp, H_clamp) - L_clamp
            // that is: R_2 = clamp(R, L_clamp=0, H_clamp=Q_max) if Q_min < 0
            // or else: R_2 = clamp(R, L_clamp, H_clamp) - Q_min if Q_min >= 0
            // and also: R_2 = clamp(R, L_clamp, Q_max+1) - L_clamp if Q_min < Q_max+1 (no
            // overflow)
            // or else: R_2 = clamp(R, L_clamp, *no limit*)- L_clamp if Q_max+1 < Q_min
            // (overflow)
            ValueNode R_2 = clamp(R, L_clamp, H_clamp, graph);
            R_2 = g(sub(R_2, L_clamp), graph);
            R_2 = toInt(R_2, graph);

            // L_2 = Q_first - L_clamp
            // We are subtracting L_clamp from both sides of the <u32 comparison.
            // If S*K>0, then Q_first == 0 and the R.C. expression at -L_clamp and steps
            // upward to Q_max-L_clamp.
            // If S*K<0, then Q_first != 0 and the R.C. expression starts high and steps
            // downward to Q_min-L_clamp.
            ValueNode L_2 = g(sub(Q_first, L_clamp), graph);
            L_2 = toInt(L_2, graph);

            // Transform the range check using the computed values L_2/R_2
            // from: i*K + L <u64 R
            // to: j*K + L_2 <u32 R_2
            // that is:
            // (j*K + Q_first) - L_clamp <u32 clamp(R, L_clamp, H_clamp) - L_clamp
            // K = _igvn.intcon(checked_cast<int>(scale)); --> computed above

            // represent the original base IV with a new real 32 bit base IV on the fly
            ValueNode scaled_iv = g(mul(rc.originalStrideIV.valueNode(), forInt((int) scale, graph)), graph);
            ValueNode scaled_iv_plus_offset = g(add(scaled_iv, L_2), graph);

            LogicNode newCompare = graph.addWithoutUnique(CompareNode.createAnyCompareNode(Condition.BT, scaled_iv_plus_offset, R_2, constantReflection));
            logRangeCheckEliminationValues(graph, loop, guard, scaled_iv, scaled_iv_plus_offset, L_2, R_2, loopLimitNode, R, C, L, K,
                            maxTripCountNode, Q_first, Q_min, B_2, Q_max, L_clamp, Q_max_plus_one, H_clamp, newCompare);
            guard.setCondition(newCompare, guard.isNegated());
            graph.getOptimizationLog().report(RangeCheckEliminationPhase.class, "RangeCheckImprovement", guard);
        }
        // Checkstyle: resume

    }

    // Checkstyle: stop
    /**
     * Add diagnostic logging nodes for the original and rewritten range-check values when
     * {@link Options#RCELogRangeCheckValues} is enabled. The optional fixed anchor keeps
     * the logged values before a deoptimization during interactive debugging.
     */
    @SuppressWarnings("deprecation")
    @ExcludeFromJacocoGeneratedReport("Only used for interactive debugging")
    private static void logRangeCheckEliminationValues(StructuredGraph graph, Loop loop, GuardNode guard, ValueNode scaledInductionVariable, ValueNode scaledInductionVariablePlusOffset, ValueNode l2,
                    ValueNode r2, ValueNode loopLimitNode, ValueNode range, ValueNode outerLoopPhi, ValueNode offset, ValueNode scale, ValueNode maxTripCountNode, ValueNode Q_first, ValueNode Q_min,
                    ValueNode B_2, ValueNode Q_max, ValueNode L_clamp, ValueNode Q_max_plus_one, ValueNode H_clamp,
                    LogicNode newCompare) {
        if (Options.RCELogRangeCheckValues.getValue(graph.getOptions())) {
            LogNode l = graph.add(new LogNode(guard + " scaled_iv(" + scaledInductionVariable.getId() + ")=%d\n", scaledInductionVariable));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), l);
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(),
                            graph.add(new LogNode(guard + " scaled_iv_plusoffset(" + scaledInductionVariablePlusOffset.getId() + ")=%d\n", scaledInductionVariablePlusOffset)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " L_2(" + l2.getId() + ")=%d\n", l2)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " R_2(" + r2.getId() + ")=%d\n", r2)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " R=Range(" + range.getId() + ")=%ld\n", range)));

            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " Q_First=(" + range.getId() + ")=%ld\n", Q_first)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " Q_Min=(" + range.getId() + ")=%ld\n", Q_min)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " B2=(" + range.getId() + ")=%ld\n", B_2)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " Q_Max=(" + range.getId() + ")=%ld\n", Q_max)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " L_clamp=(" + range.getId() + ")=%ld\n", L_clamp)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " Q_max_plus_one=(" + range.getId() + ")=%ld\n", Q_max_plus_one)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " H_Clamp=(" + range.getId() + ")=%ld\n", H_clamp)));

            boolean isLong = IntegerStamp.getBits(outerLoopPhi.stamp(NodeView.DEFAULT)) == 64;
            String formatter = isLong ? "%ld" : "%d";
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " C=OuterLoopPhi(" + outerLoopPhi.getId() + ")=" + formatter + "\n", outerLoopPhi)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " K=Scale(" + scale.getId() + ")=%ld\n", scale)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " L=Offset(" + offset.getId() + ")=" + formatter + "\n", offset)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " 32 bit loop limit node (extremum node) %d\n", loopLimitNode)));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " 32 bit loop limit node %d\n", loop.counted().getLimit())));
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " 32 bit loop max trip count node %d\n", maxTripCountNode)));

            for (InductionVariable iv : loop.getInductionVariables().getValues()) {
                ValueNode v = iv.valueNode();
                if (isLong(v)) {
                    graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " 32 bit loop " + iv + "= %ld (extremum=%ld)\n", v, iv.extremumNode())));
                } else {
                    graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(), graph.add(new LogNode(guard + " 32 bit loop " + iv + "= %d (extremum=%d)\n", v, iv.extremumNode())));
                }
            }

            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(),
                            graph.add(new LogNode(
                                            guard + " ||New Comparison scaled_iv_plus_offset=%d " + newCompare.getNodeClass().shortName() + " R_2=%d negated " + guard.isNegated() + "=%d\n",
                                            scaledInductionVariablePlusOffset,
                                            r2, graph.addWithoutUnique(ConditionalNode.create(newCompare, forInt(1, graph), forInt(0, graph), NodeView.DEFAULT)))));
            BinaryOpLogicNode oldCondition = (BinaryOpLogicNode) guard.getCondition();
            graph.addAfterFixed((FixedWithNextNode) guard.getAnchor(),
                            graph.add(new LogNode(guard + " ||Original Comparison " + oldCondition.getX() + "=%ld " + oldCondition.getNodeClass().shortName() + " " + oldCondition.getY() +
                                            "=%ld negated " + guard.isNegated() + "  =%d\n", oldCondition.getX(), oldCondition.getY(),
                                            graph.addWithoutUnique(ConditionalNode.create(oldCondition, forInt(1, graph), forInt(0, graph), NodeView.DEFAULT)))));
            if (LogWithFixedValueAnchor) {
                FixedValueAnchorNode fva = graph.add(new FixedValueAnchorNode(null));
                graph.addAfterFixed(l, fva);
                guard.setAnchor(fva);
            }
        }
    }
    // Checkstyle: resume

    /**
     * If logging of magic values for long range check elimination is enabled use a fixed value
     * anchor node to ensure that any deopt happens after the currently used values are logged. Note
     * that this inhibits speculative guard movement of these guards which can let test cases
     * verifying empty loops fail.
     */
    private static final boolean LogWithFixedValueAnchor = true;

    private static final int MAX_IV_SEARCH_ITERATIONS = 12;

    /**
     * Determine if the {@link CompareNode} (which is used by a {@link GuardNode}) exposes an
     * optimizable range check (RC) pattern, the base pattern is of the form:
     *
     * <pre>
     * if(! (iv * scale + offset < length)) deopt
     * </pre>
     *
     * where {@code scale} can be {@code 1} and thus not exist as a node and {@code offset} can be
     * {@code 0} and thus not exist as a node.
     *
     * Concretely this method operates on a previously strip mined loop nest
     *
     * Original Loop
     *
     * <pre>
     * long scale = S; // some constant
     * long offset = p1; // not necessarily constant
     * long range = p2;// some positive range;
     * long stride = 1;
     * for (long i = 0; i < limit; i += stride) {
     *     if (i * scale + offset > range) {
     *         deoptAndInvalidate();
     *     }
     * }
     * </pre>
     *
     * after strip mining
     *
     * <pre>
     * long scale = S; // some constant
     * long offset = p1; // not necessarily constant
     * long range = p2;// some positive range;
     * final long stripMax = (long) CountedStripMiningInnerLoopTrips;
     * for (long i = init; i < limit;) {
     *     long innerTrips = i < limit - stripMax ? stripMax : limit - i;
     *     long i_ = i;
     *     for (long j = 0; j < innerTrips; j++) { // typically an unsigned compare
     *         if (i_ * scale + offset > range) {
     *             deoptAndInvalidate();
     *         }
     *         i_ += 1L;
     *     }
     *     i = i_;
     * }
     * </pre>
     *
     * after rewriting non-overflowing inner loop basic induction variables to int (first the limit
     * checked IV) note how {@code i_} is still a long
     *
     * <pre>
     * long scale = S; // some constant
     * long offset = p1; // not necessarily constant
     * long range = p2;// some positive range;
     * final int stripMax = (long) CountedStripMiningInnerLoopTrips;
     * for (long i = init; i < limit;) {
     *     int innerTrips = (int) (i < limit - stripMax ? stripMax : limit - i);
     *     long i_ = i;
     *     for (int j = 0; j < innerTrips; j++) { // typically an unsigned compare
     *         if (i_ * scale + offset > range) {
     *             deoptAndInvalidate();
     *         }
     *         i_ += 1L;
     *     }
     *     i = i_;
     * }
     * </pre>
     *
     * then we rewrite the rest of the basic IVs to int if their stride ensures no overflow,
     * in this example this is just j because the strides of i_ and j match
     *
     * <pre>
     * long scale = S; // some constant
     * long offset = p1; // not necessarily constant
     * long range = p2;// some positive range;
     * final int stripMax = (long) CountedStripMiningInnerLoopTrips;
     * for (long i = init; i < limit;) {
     *     int innerTrips = i < limit - stripMax ? stripMax : limit - i;
     *     for (int j = 0; j < innerTrips; j++) { // typically an unsigned compare
     *         if (((long) (j) + i) * scale + offset > range) {
     *             deoptAndInvalidate();
     *         }
     *     }
     *     i = (long) j + i;
     * }
     * </pre>
     *
     * This method finds now a range check of the form presented in the inner loop and assigns each
     * node to a corresponding class' field (based on a compare node which's input is a loop
     * invariant range node and a loop variant induction variable).
     *
     * <pre>
     * if (((long) j + i) * scale + offset > range) {
     *     deoptAndInvalidate();
     * }
     * </pre>
     *
     * to follow C2's notion we use the following names
     *
     * <pre>
     * long outerLoopPhi = i;
     * int innerPhi = j;
     * if (((long) (innerPhi) + outerLoopPhi) * scale + offset > range) {
     *     deoptAndInvalidate();
     * }
     * </pre>
     *
     * So the compare node to reason about is
     *
     * {@code compare ((long) (innerPhi) + outerLoopPhi) * scale + offset > range}
     *
     * where scale can be missing (== it is equal to 1) and offset can be missing (== it is equal to 0).
     *
     * In this calculation we call the induction variables rooted at different {@link InductionVariable#valueNode()} and the involved
     * other nodes the following (note the numbers on the side which indicate in which order they need to be found during traversal)
     *
     * //@formatter:off
     *  ((long) (innerPhi) + outerLoopPhi) * scale + offset > range
     *      |       |      |     |         |   |   |    |       |_range (node) 1.
     *      |       |      |     |         |   |   |    |_offset (node) 2.
     *      |       |      |     |         |   |   |_offsetIV (iv) 2.
     *      |       |      |     |         |   |_scale (node) 3.
     *      |       |      |     |         |_scaledIV (iv) 3.
     *      |       |      |     |_outerLoopPhi (node) 5.
     *      |       |      |_outerOffsetIV (iv) 5.
     *      |       |_innterPhi 6. (-> leads to base IV)
     *      |_conversionIV (iv) 4.
     * //@formatter:on
     */
    private static StripMinedRC findRC(CompareNode compare, Loop loop) {
        /*
         * Multiple possible cases of offset, scale and IV: we generalize over a chain of IVs, we
         * look for certain patterns and extract them in #findRC
         */
        InductionVariable ivX = loop.getInductionVariables().get(compare.getX());
        InductionVariable ivY = loop.getInductionVariables().get(compare.getY());
        StructuredGraph graph = compare.graph();
        if (ivX == null && ivY == null) {
            return null;
        }
        InductionVariable iv;
        ValueNode range = null;
        boolean mirrored;
        if (ivX == null || (ivY != null && ivY.getLoop().getCFGLoop().getDepth() > ivX.getLoop().getCFGLoop().getDepth())) {
            iv = ivY;
            range = compare.getX();
            mirrored = true;
        } else {
            iv = ivX;
            range = compare.getY();
            mirrored = false;
        }

        /*
         * The C2 logic only covers index <u range; for a mirrored check, swapping operands after
         * calculating the clamp bounds is not equivalent for a strict comparison
         */
        if (mirrored || !isLong(range)) {
            return null;
        }
        if (!loop.isOutsideLoop(range)) {
            // the range must be loop invariant, we later even speculate with a guard dominating the
            // loop begin it is not negative
            return null;
        }
        // See the javadoc of this method for details on the "matched" grammar.
        DerivedOffsetInductionVariable offsetIV = null;
        DerivedScaledInductionVariable scaledIv = null;
        DerivedConvertedInductionVariable conversionIV = null;
        BasicInductionVariable baseIV = null;
        ValueNode outerLoopPhi = null;
        InductionVariable cur = iv;
        InductionVariable outerOffsetIV = null;

        boolean oneOffsetOnly = false;

        /**
         * Walk the range check's ivs back until a basic induction variable is found: determine when
         * an extension from 32 to 64 bit happened and record whether the scaling induction variable
         * happened in int or long.
         */
        boolean enteredIntRealm = false;
        boolean scaleInInt = false;

        InductionVariable last = null;
        int iterations = 0;
        // loop already max iteration checked

        // iv loop
        while (true) { // TERMINATION ARGUMENT: guarded by explicit logic and following base IVs
            CompilationAlarm.checkProgress(graph);
            if (iterations++ > MAX_IV_SEARCH_ITERATIONS) {
                /*
                 * Defensive programming: something went terribly wrong and we are on the way to an
                 * endless loop - abort.
                 */
                return null;
            }
            if (last == cur) {
                /*
                 * No progress was made, no suitable IV.
                 *
                 * Defensive programming: while the cases below should cover the common induction
                 * variables there could be new ones added. Since we are looking for very particular
                 * patterns of induction variables we dont want to enumerate all of them. In light
                 * of future ones being added we just abort here if we don't make any progress.
                 */
                return null;
            }
            last = cur;
            if (cur instanceof DerivedOffsetInductionVariable doi) {
                if (oneOffsetOnly) {
                    return null;
                }

                /* Only the marked outer phi corresponds to the strip-mined outer loop IV,
                 * other outer phis are handled as ordinary offsets; if no marked phi is found, the
                 * final outerLoopPhi check rejects the candidate.
                 */
                if (loop.parent() != null && isOriginalLimitCheckedIV(doi.getOffset(), loop.parent())) {
                    outerOffsetIV = doi;
                    outerLoopPhi = doi.getOffset();
                    cur = doi.getBase();
                    if (offsetIV == null) {
                        oneOffsetOnly = true;
                    }
                    continue;
                }
                if (enteredIntRealm) {
                    // cannot treat potentially wrapping int offset as long arithmetic
                    return null;
                }
                if (offsetIV != null) {
                    // too complex iv chain
                    return null;
                }
                if (scaledIv != null) {
                    // offset must come before scale
                    return null;
                }
                offsetIV = (DerivedOffsetInductionVariable) cur;
                cur = offsetIV.getBase();
            } else if (cur instanceof DerivedScaledInductionVariable) {
                if (scaledIv != null) {
                    // too complex iv chain
                    return null;
                }
                if (enteredIntRealm) {
                    scaleInInt = true;
                }
                scaledIv = (DerivedScaledInductionVariable) cur;
                cur = scaledIv.getBase();
            } else if (cur instanceof DerivedConvertedInductionVariable dcIv) {
                if (conversionIV != null) {
                    // too complex iv chain
                    return null;
                }
                ValueNode v = dcIv.valueNode();
                if (v instanceof SignExtendNode se) {
                    if (se.getInputBits() == 32 && se.getResultBits() == 64) {
                        cur = dcIv.getBase();
                        conversionIV = dcIv;
                        enteredIntRealm = true;
                        continue;
                    }
                } else if (v instanceof ZeroExtendNode ze) {
                    if (ze.getInputBits() == 32 && ze.getResultBits() == 64 && ((IntegerStamp) ze.getValue().stamp(NodeView.DEFAULT)).isPositive()) {
                        // For a proven non-negative int, zero and sign extension have identical
                        // values, but do not admit the general zero-extension case
                        cur = dcIv.getBase();
                        conversionIV = dcIv;
                        enteredIntRealm = true;
                        continue;
                    }
                } else {
                    // unsupported convert op
                    return null;
                }

            } else if (cur instanceof BasicInductionVariable) {
                baseIV = (BasicInductionVariable) cur;
                break;
            } else {
                // unknown IV type
                break;
            }
        } // iv loop

        if (baseIV == null || !baseIV.isConstantStride()) {
            return null;
        }
        long baseStride = baseIV.constantStride();
        long limitCheckedStride = loop.counted().getLimitCheckedIV().constantStride();
        // this should be ensured because we checked isOriginalLimitCheckedIV() earlier
        GraalError.guarantee(baseStride == limitCheckedStride || baseStride == -limitCheckedStride,
                        "Base stride should match the limit-checked IV's: |%s| != |%s|", baseStride, limitCheckedStride);
        if (!NumUtil.isInt(baseStride)) {
            return null;
        }
        int originalStride = (int) baseStride;

        ScaleAndOffset scaleOffset = normalizeIVScaleAndOffset(scaledIv, offsetIV, outerOffsetIV, graph);
        if (scaleOffset == null) {
            // unsupported pattern
            return null;
        }
        ValueNode scale = scaleOffset.scale();
        ValueNode offset = scaleOffset.offset();
        if (scale == null || offset == null || outerLoopPhi == null || conversionIV == null) {
            return null;
        }
        return new StripMinedRC(scale, scaledIv, offset, range, baseIV, outerLoopPhi, graph, originalStride, scaleInInt, scaleOffset.scaleWasNegated(),
                        (IntegerConvertNode<?>) conversionIV.valueNode(), offsetIV);
    }

    private record ScaleAndOffset(ValueNode scale, ValueNode offset, boolean scaleWasNegated) {
    }

    /**
     * Extracts the scale and offset from a derived IV to match the {@code i*scale + offset} form.
     * <p>
     * Offset IVs can represent additions or subtractions.
     *
     * Additions already have scale and offset in the required form.
     *
     * The unscaled subtraction {@code offset - outerIV} pattern (optimized from {@code (-1)*i + offset})
     * is recognized ({@code scale = -1}).
     *
     * For subtraction with a scaled IV, the position of the IV base determines which value must be negated:
     * <pre>
     * offset - scale*i == (-scale)*i + offset  // negate scale
     * scale*i - offset == scale*i + (-offset)  // negate offset
     * </pre>
     *
     * @return the scale and offset, or null for unsupported IV patterns
     */
    private static ScaleAndOffset normalizeIVScaleAndOffset(DerivedScaledInductionVariable scaledIV, DerivedOffsetInductionVariable offsetIV, InductionVariable outerOffsetIV, StructuredGraph graph) {
        ValueNode scale = scaledIV == null ? ConstantNode.forLong(1, graph) : scaledIV.getScale();
        if (offsetIV == null) {
            return new ScaleAndOffset(scale, ConstantNode.forLong(0, graph), false);
        }
        if (offsetIV.valueNode() instanceof AddNode) {
            // nothing to do
            return new ScaleAndOffset(scale, offsetIV.getOffset(), false);
        } else if (offsetIV.valueNode() instanceof SubNode sub) {
            if (scaledIV == null) {
                // special pattern of the form scale*i+offset where scale=-1 -> that
                // gets optimized to offset-i
                if (unwrap32To64Extension(offsetIV.getBase()) == outerOffsetIV) {
                    if (selfOrSignExtendInput(sub.getY(), false) == outerOffsetIV.valueNode()) {
                        return new ScaleAndOffset(forLong(-1, graph), sub.getX(), false);
                    }
                }
            } else {
                return normalizeSubtractionOffsetIV(offsetIV, scaledIV, graph, sub);
            }
        } else {
            GraalError.shouldNotReachHere("Unknown offset operation " + offsetIV.valueNode()); // ExcludeFromJacocoGeneratedReport
        }
        return null;
    }

    /**
     * Extracts normalized scale and offset from an offset IV representing a subtraction with a scaled base.
     * This could be {@code offset - scale*i} or {@code scale*i - offset}.
     * If the IV base is the right operand, the scale is negated.
     * If it is the left operand, the offset is negated.
     */
    private static ScaleAndOffset normalizeSubtractionOffsetIV(DerivedOffsetInductionVariable offsetIV, DerivedScaledInductionVariable scaleIV, StructuredGraph graph, SubNode sub) {
        ValueNode scale = scaleIV.getScale();
        ValueNode offset = offsetIV.getOffset();
        ValueNode base = offsetIV.getBase().valueNode();
        boolean scaleNegated = false;
        if (sub.getY() == base) {
            // offset - scale*i
            IntegerStamp stamp = (IntegerStamp) scale.stamp(NodeView.DEFAULT);
            if (stamp.getBits() == Integer.SIZE && stamp.lowerBound() == Integer.MIN_VALUE) {
                // e.g. offset - (long)(Integer.MIN_VALUE * i)
                // the scale is still int here, and negating Integer.MIN_VALUE would overflow
                // which leads to an incorrect range check: reject this pattern
                // (RC currently only accepts constant scales, so checking the stamp is conservative)
                return null;
            }
            scale = graph.addOrUnique(NegateNode.create(scale, NodeView.DEFAULT));
            scaleNegated = true;
        } else if (sub.getX() == base) {
            // scale*i - offset
            offset = graph.addOrUnique(NegateNode.create(offset, NodeView.DEFAULT));
        } else {
            GraalError.shouldNotReachHere("Subtraction does not contain IV base " + sub); // ExcludeFromJacocoGeneratedReport
        }
        return new ScaleAndOffset(scale, offset, scaleNegated);
    }

    public static final SpeculationReasonGroup RANGE_NOT_NEGATIVE = new SpeculationReasonGroup("RangeCheckEliminationRangeNotNegative", ResolvedJavaMethod.class, int.class);

    /**
     * The algorithm used in {@link #c2StyleStripMiningRCE(Loop, ConstantReflectionProvider)}
     * requires the {@code range} of a range check to be {@code >=0}, speculate before the range
     * check loop this is the case.
     */
    private static ValueNode speculateRangeIsNotNegative(ValueNode range, LoopBeginNode lb) {
        GraalError.guarantee(isLong(range), "Must have a long stamp; range=%s, stamp=%s", range, range.stamp(NodeView.DEFAULT));
        final StructuredGraph graph = lb.graph();
        LogicNode compare = CompareNode.createAnyCompareNode(Condition.LT, range, ConstantNode.forLong(0, graph), null);

        if (compare instanceof LogicConstantNode) {
            if (compare.isContradiction()) {
                // can never be negative
                return range;
            } else if (compare.isTautology()) {
                // always negative, do not rewrite this range check
                return null;
            } else {
                throw GraalError.shouldNotReachHere("Unkown compare constant " + compare); // ExcludeFromJacocoGeneratedReport
            }
        }

        SpeculationLog speculationLog = graph.getSpeculationLog();
        if (speculationLog != null) {
            SpeculationReason reason = RANGE_NOT_NEGATIVE.createSpeculationReason(graph.method(), lb.stateAfter().bci);
            if (speculationLog.maySpeculate(reason)) {
                compare = graph.addOrUniqueWithInputs(compare);
                GuardNode guard = graph.addWithoutUnique(new GuardNode(compare, AbstractBeginNode.prevBegin(lb.forwardEnd()), DeoptimizationReason.BoundsCheckException,
                                DeoptimizationAction.InvalidateRecompile, true, speculationLog.speculate(reason), null));
                ValueNode pi = graph.addOrUnique(PiNode.create(range, POSITIVE_RANGE_STAMP, guard));
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After speculating that range %s is positive with guard %s creating pi %s", range, guard, pi);
                return pi;
            }
        }
        return null;
    }

    /**
     * Converts a range to {@code long} and obtains a value refined to be non-negative,
     * installing a speculative guard when the stamp alone is insufficient.
     */
    private static ValueNode intoLongNonNegativeRange(ValueNode range, LoopBeginNode lb) {
        ValueNode longRange = toLongOrSelf(range, lb.graph());
        if (((IntegerStamp) longRange.stamp(NodeView.DEFAULT)).isPositive()) {
            return longRange;
        }
        return speculateRangeIsNotNegative(longRange, lb);
    }

    private static final IntegerStamp POSITIVE_RANGE_STAMP = IntegerStamp.create(64, 0, Long.MAX_VALUE);

    /**
     * Context data for a range check (RC) in a strip mined loop (see
     * {@link CountedStripMiningPhase} for details).
     */
    private static class StripMinedRC {
        private final DerivedOffsetInductionVariable offsetIV;
        private final ValueNode scale;
        private final DerivedScaledInductionVariable scaleIV;
        private final boolean scaleInIntRange;
        private final boolean scaleWasNegated;
        private final ValueNode offset;
        private final ValueNode range;
        private final BasicInductionVariable originalStrideIV;
        private final ValueNode outerLoopPhi;
        private final int stride;
        private final IntegerConvertNode<?> convertOp;

        /**
         * Creates the context data used by the C2-style range check transformation. Scale
         * and offset values are widened to long while the original IV metadata is retained for
         * endpoint calculations and int-scale overflow handling.
         */
        StripMinedRC(ValueNode scale, DerivedScaledInductionVariable scaleIv, ValueNode offset, ValueNode range, BasicInductionVariable originalStrideIV,
                        ValueNode outerLoopPhi, StructuredGraph graph, int stride,
                        boolean scaleInIntRange, boolean scaleWasNegated, IntegerConvertNode<?> convertOp, DerivedOffsetInductionVariable offsetIV) {
            // THE C2 algorithm expects 64bit values (for 64bit loops)
            this.scale = toLongOrSelf(scale, graph);
            this.scaleIV = scaleIv;
            this.offset = toLongOrSelf(offset, graph);
            this.range = range;
            this.originalStrideIV = originalStrideIV;
            this.outerLoopPhi = outerLoopPhi;
            this.stride = stride;
            this.scaleInIntRange = scaleInIntRange;
            this.scaleWasNegated = scaleWasNegated;
            this.convertOp = convertOp;
            this.offsetIV = offsetIV;
        }

        /**
         * @return the stride
         */
        public int stride() {
            return stride;
        }

        /**
         * @return the outer loop phi
         */
        ValueNode c() {
            return outerLoopPhi;
        }

        /**
         * @return the scale
         */
        ValueNode k(boolean unwrapSignExtension) {
            if (unwrapSignExtension) {
                return selfOrSignExtendInput(scale, false);
            }
            return scale;
        }

        /**
         * @return the offset
         */
        ValueNode l() {
            return offset;
        }

        /**
         * @return the range
         */
        ValueNode r() {
            return range;
        }

        @Override
        public String toString() {
            return "StripMinedRC{" +
                            "offsetIV=" + offsetIV +
                            ", scale=" + scale +
                            ", scaleIV=" + scaleIV +
                            ", scaleInIntRange=" + scaleInIntRange +
                            ", scaleWasNegated=" + scaleWasNegated +
                            ", offset=" + offset +
                            ", range=" + range +
                            ", originalStrideIV=" + originalStrideIV +
                            ", outerLoopPhi=" + outerLoopPhi +
                            ", stride=" + stride +
                            ", convertOp=" + convertOp +
                            '}';
        }
    }

    /**
     * Add or unique the given node to the graph.
     */
    private static ValueNode g(ValueNode newNode, StructuredGraph graph) {
        return graph.addOrUniqueWithInputs(newNode);
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }
}
