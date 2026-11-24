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
package jdk.graal.compiler.nodes.loop;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.common.util.UnsignedLong;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.MaxNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.IntegerHelper;
import jdk.graal.compiler.nodes.util.SignedIntegerHelper;
import jdk.graal.compiler.nodes.util.UnsignedIntegerHelper;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.graal.compiler.truffle.nodes.TruffleSafepointNode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Class representing meta information about a counted loop.
 *
 * Comments on the nomenclature for {@link #getLimit()}, {@link #getBody()},
 * {@link #getLimitCheckedIV()} and {@link #getBodyIV()}:
 *
 * A regular head counted loop like
 *
 * <pre>
 * for (int i = 0; i &lt; end; i++) {
 *     // body
 * }
 * </pre>
 *
 * has a limit (end) that is compared against the {@link InductionVariable} (iv) returned by
 * getLimitCheckedIV. The iv for the loop above is the basic induction variable i.
 *
 * For inverted loops like
 *
 * <pre>
 * int i = 0;
 * do {
 *   // body
 *   i++;
 * } while(i &lt; end)
 * </pre>
 *
 * The iv compared against limit is not i, but the next iteration's body iv i+1.
 *
 * Thus, for inverted loops {@link #getBodyIV()} returns a different result than
 * {@link #getLimitCheckedIV()}. {@link #getBodyIV()} returns i, while {@link #getLimitCheckedIV()}
 * returns i + 1.
 *
 * Furthermore, the contract between {@link #getLimitCheckedIV()} and {@link #getBodyIV()} defines
 * that both IVs iterate on the same signed-ness range, i.e., if one is purely in an unsigned range
 * the other one has to be as well (same applies for signed integers). This means that optimizations
 * can safely use {@link IntegerHelper} based on the signed-ness of the {@link #getLimitCheckedIV()}
 * to compute min/max and iteration ranges for the loops involved.
 */
public class CountedLoopInfo {

    public static final String LOOP_OVERFLOW_DEOPT_WITH_REASON_GROUP_NAME = "LoopOverflowDeoptWithReason";

    /**
     * Variant of {@link LoopBeginNode#LOOP_OVERFLOW_DEOPT} that additionally distinguishes callers
     * by an extra deoptimization reason.
     */
    public static final SpeculationReasonGroup LOOP_OVERFLOW_DEOPT_WITH_REASON = new SpeculationReasonGroup(LOOP_OVERFLOW_DEOPT_WITH_REASON_GROUP_NAME, ResolvedJavaMethod.class, int.class,
                    DeoptimizationReason.class);

    protected final Loop loop;

    /**
     * @see CountedLoopInfo#getLimitCheckedIV()
     */
    protected InductionVariable limitCheckedIV;

    /**
     * @see #getLimit()
     */
    protected ValueNode limit;

    /**
     * {@code true} iff the limit is included in the limit test, e.g., the limit test is
     * {@code i <= n} rather than {@code i < n}.
     */
    protected boolean isLimitIncluded;

    /**
     * The first node inside the loop that is not considered to be part of the loop control nodes
     * (terminating conditions). Depending on the shape of the loop, this can be different kind of
     * nodes.
     */
    protected AbstractBeginNode body;

    /**
     * The condition node that determines if this loop is exited or not. Can be in head or tail
     * position depending on the shape of the loop. There can be an arbitrary number of if nodes in
     * a loop. This one is special in that it either dominates or post dominates all others.
     */
    protected IfNode ifNode;

    /**
     * Determine if we consider the control logic of the loop working in unsigned integer ranges.
     */
    protected final boolean unsigned;

    private final InductionVariable bodyUsedIV;

    /**
     * @see #getTripCountLimit()
     */
    private final ValueNode tripCountLimit;

    /**
     * Determines if the {@link InductionVariable} {@link #getBodyIV()} and
     * {@link #getLimitCheckedIV()} can have different signs and we are an inverted loops. Together
     * with unsigned loops this can cause problems. See
     * {@link Loop#invertedLoopComplexSignednessRange(Loop.IfPosition, jdk.graal.compiler.core.common.calc.Condition, InductionVariable, boolean)}
     * for details.
     */
    private boolean invertedLoopComplexSignednessRange;

    @SuppressWarnings("this-escape")
    protected CountedLoopInfo(Loop loop, InductionVariable limitedCheckedIV, InductionVariable bodyUsedIV, IfNode ifNode, ValueNode limit, ValueNode tripCountLimit,
                    boolean isLimitIncluded,
                    AbstractBeginNode body,
                    boolean unsigned, boolean invertedLoopComplexSignednessRange) {
        assert limitedCheckedIV.direction() != null;
        this.loop = loop;
        this.limitCheckedIV = limitedCheckedIV;
        this.limit = limit;
        this.isLimitIncluded = isLimitIncluded;
        this.body = body;
        this.ifNode = ifNode;
        this.unsigned = unsigned;
        this.bodyUsedIV = bodyUsedIV;
        this.tripCountLimit = tripCountLimit;
        this.invertedLoopComplexSignednessRange = invertedLoopComplexSignednessRange;
        assert !isInverted() || emptyInvertedCountedBackedge() : "Backedge must be empty to be a pure tail counted loop " + loop.loopBegin();
    }

    /**
     * See {@link InductionVariable#structuralIntegrityValid()}.
     */
    public boolean countedIntegrityValid() {
        return limitCheckedIV.structuralIntegrityValid() && limit.isAlive() && body.isAlive() && ifNode.isAlive() && bodyUsedIV.structuralIntegrityValid() && tripCountLimit.isAlive();
    }

    /**
     * Returns the induction variable compared against the limit node. For a loop like
     *
     * <pre>
     * for (int i = 0; i < limit; i++) {
     *     body();
     * }
     * </pre>
     *
     * The induction variable {@code i} is the limit checked one, {@code limit} will be the limit
     * node. In the case where the {@link #getLimitCheckedIV()} is a {@link BasicInductionVariable}
     * {@link #getLimitCheckedIV()} equals {@link #getBodyIV()}. Note that for other loop shapes the
     * limit checked IV can be a {@link DerivedInductionVariable}.
     *
     * For a loop like
     *
     * <pre>
     * for (int i = 0; (i + 5) < limit; i++) {
     *     body();
     * }
     * </pre>
     *
     * the limit checked IV will be a {@link DerivedOffsetInductionVariable} with the constant
     * offset {@code 5}. For this head-counted loop, {@link #getBodyIV()} returns the same derived IV.
     * In an inverted loop, the limit check may instead use the next iteration's value of
     * {@link #getBodyIV()}.
     *
     * @return the {@link InductionVariable} compared to {@link #getLimit()} by the loop exit test
     */
    public InductionVariable getLimitCheckedIV() {
        return limitCheckedIV;
    }

    /**
     * @return the {@link InductionVariable} used in the body of this {@link CountedLoopInfo}. If
     *         {@link CountedLoopInfo#isInverted()} returns {@code false} this returns the same as
     *         {@link CountedLoopInfo#getLimitCheckedIV()}.
     */
    public InductionVariable getBodyIV() {
        return bodyUsedIV;
    }

    /**
     * @return the {@link InductionVariable#extremumNode()} for the {@link #getBodyIV()}.
     */
    public ValueNode getBodyIVExtremum() {
        return getBodyIV().extremumNode(true, StampFactory.forKind(JavaKind.Long));
    }

    /**
     * @return the {@link InductionVariable#exitValueNode()} for the {@link #getBodyIV()}.
     */
    public ValueNode getBodyIVExitValue() {
        return getBodyIV().exitValueNode();
    }

    /**
     * @return {@code true} iff {@link #getBodyIV()} equals {@link #getLimitCheckedIV()}.
     */
    public boolean getBodyIVEqualsLimitCheckedIV() {
        return getBodyIV() == getLimitCheckedIV();
    }

    /**
     * Returns the {@link InductionVariable#entryTripValue()} for the
     * {@link DerivedInductionVariable#getBase()} if {@link #getLimitCheckedIV()} is a
     * {@link DerivedInductionVariable} else returns its
     * {@link InductionVariableHelper#previousIteration(InductionVariable)} entry value. That is,
     * the entry value of the {@link #getBodyIV()} if its an {@link #isInverted()} (because that is
     * the limit checked IV's based) else the previous iteration of the regular
     * {@link #getLimitCheckedIV()}.
     */
    public ValueNode limitCheckedPreviousOrRootEntryValue() {
        if (getBodyIVEqualsLimitCheckedIV()) {
            InductionVariable limitCheckedIVDuplicated = InductionVariableHelper.previousIteration(getLimitCheckedIV()).duplicate();
            return limitCheckedIVDuplicated.entryTripValue();
        } else {
            InductionVariable limitCheckedIVDuplicated = loop.counted().getBodyIV().duplicate();
            return limitCheckedIVDuplicated.entryTripValue();
        }
    }

    /**
     * Returns the limit node of this counted loop.
     *
     * @return the {@link ValueNode} that is compared ({@link CompareNode}) to the
     *         {@link InductionVariable} return by {@link CountedLoopInfo#getLimitCheckedIV()}
     */
    public ValueNode getLimit() {
        return limit;
    }

    /**
     * Returns the mathematical limit that is used to compute the
     * {@link CountedLoopInfo#maxTripCountNode()}. If {@link CountedLoopInfo#isInverted()} is
     * {@code false} this returns the same as {@link CountedLoopInfo#getLimit()}. Otherwise,
     * depending on the shape of the inverted loops this may return a value that is |stride| off the
     * real limit to account for inverted loops with none-inverted limit checks.
     *
     * Consider the following inverted loop
     *
     * <pre>
     * int i = 0;
     * do {
     *     i++;
     * } while (i &lt; 100);
     * </pre>
     *
     * This loop performs 100 iterations. However, the following loop
     *
     * <pre>
     * int i = 0;
     * do {
     * } while (i++ &lt; 100);
     * </pre>
     *
     * performs 101 iterations.
     *
     *
     * While the "limit" of both is 100, the "real" mathematical limit of the second one is 101.
     * Thus, in order to perform correct calculation of {@link CountedLoopInfo#maxTripCountNode()}
     * we distinguish between those two concepts.
     */
    public ValueNode getTripCountLimit() {
        return tripCountLimit;
    }

    private void assertNoOverflow() {
        GraalError.guarantee(loopCanNeverOverflow(), "Counter must never overflow when reasoning about trip counts of a loop");
    }

    /**
     * Returns a node that computes the maximum trip count of this loop. That is the trip count of
     * this loop assuming it is not exited by an other exit than the {@linkplain #getLimitTest()
     * count check}.
     *
     * This count is exact if {@link #isExactTripCount()} returns true.
     *
     * THIS VALUE SHOULD BE TREATED AS UNSIGNED.
     */
    public ValueNode maxTripCountNode() {
        assertNoOverflow();
        return maxTripCountNode(false);
    }

    public boolean isUnsignedCheck() {
        return this.unsigned;
    }

    public ValueNode maxTripCountNode(boolean assumeLoopEntered) {
        assertNoOverflow();
        return maxTripCountNode(assumeLoopEntered, getCounterIntegerHelper());
    }

    protected ValueNode maxTripCountNode(boolean assumeLoopEntered, IntegerHelper integerHelper) {
        assertNoOverflow();
        // we use body IV init as that is the init of the derived IV if limitCheckedIV != getBodyIV
        return maxTripCountNode(assumeLoopEntered, integerHelper, getBodyIVStart(), getTripCountLimit());
    }

    /**
     * Returns a node that computes the maximum trip count of this loop. That is the trip count of
     * this loop assuming it is not exited by an other exit than the {@link #getLimitTest() count
     * check}.
     *
     * This count is exact if {@link #isExactTripCount()} returns true.
     *
     * THIS VALUE SHOULD BE TREATED AS UNSIGNED.
     *
     * Warning: In order to calculate the max trip count it can be necessary to perform a devision
     * operation in the generated code before the loop header. If {@code stride is not a power of 2}
     * we have to perform an integer division of the range of the induction variable and the stride.
     *
     * @param assumeLoopEntered if true the check that the loop is entered at all will be omitted.
     *
     */
    public ValueNode maxTripCountNode(boolean assumeLoopEntered, IntegerHelper integerHelper, ValueNode initNode, ValueNode tripCountLimitNode) {
        assertNoOverflow();
        StructuredGraph graph = getLimitCheckedIV().valueNode().graph();
        Stamp stamp = getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT);

        ValueNode max;
        ValueNode min;
        ValueNode absStride;
        final InductionVariable.Direction direction = getLimitCheckedIV().direction();
        if (direction == InductionVariable.Direction.Up) {
            absStride = getLimitCheckedIV().strideNode();
            max = tripCountLimitNode;
            min = initNode;
        } else {
            assert direction == InductionVariable.Direction.Down : "direction must be down if its not up - else loop should not be counted " + direction;
            absStride = NegateNode.create(getLimitCheckedIV().strideNode(), NodeView.DEFAULT);
            max = initNode;
            min = tripCountLimitNode;
        }
        ValueNode range = BinaryArithmeticNode.sub(max, min);

        ConstantNode one = ConstantNode.forIntegerStamp(stamp, 1, graph);
        if (isLimitIncluded) {
            range = BinaryArithmeticNode.add(range, one);
        }
        // round-away-from-zero divison: (range + stride -/+ 1) / stride
        ValueNode denominator = BinaryArithmeticNode.add(graph, range, BinaryArithmeticNode.sub(absStride, one), NodeView.DEFAULT);
        /*
         * While the divisor can never be zero because that would mean the direction of the loop is
         * not strictly known which disables counted loop detection - it is possible that the stamp
         * contains 0 though we know it effectively cannot. This happens when we have knowledge
         * about the stride - for example its strictly positive or negative but not a constant - in
         * both we cannot easily fold the negated stamp.
         *
         * Note that on certain architectures the division MIN/-1 also triggers a CPU divide error
         * which has to be taken care of by the code generation via a state, thus actually deriving
         * by stamps that this division can never trigger a divide error is very hard, and thus we
         * refrain from doing so.
         */
        final boolean divisorNonZero = true;
        ValueNode div = MathUtil.unsignedDivBefore(graph, divisorNonZero, loop.entryPoint(), denominator, absStride, null);
        if (assumeLoopEntered) {
            return graph.addOrUniqueWithInputs(div);
        }
        ConstantNode zero = ConstantNode.forIntegerStamp(stamp, 0, graph);
        // This check is "wide": it looks like min <= max
        // That's OK even if the loop is strict (`!isLimitIncluded()`)
        // because in this case, `div` will be zero when min == max
        LogicNode noEntryCheck = graph.addOrUniqueWithInputs(integerHelper.createCompareNode(max, min, NodeView.DEFAULT));
        ValueNode pi = findOrCreatePositivePi(noEntryCheck, div, graph, loop.loopsData().getCFG());
        if (pi != null) {
            return pi;
        }
        return graph.addOrUniqueWithInputs(ConditionalNode.create(noEntryCheck, zero, div, NodeView.DEFAULT));
    }

    /**
     * Before creating a {@code ConditionalNode(noEntryCheck, zero, div)} node, check if the graph
     * already contains a {@code !noEntryCheck} path dominating this loop, and build or reuse a
     * {@link PiNode} there.
     *
     * @return a new or existing {@link PiNode} already added to the graph or {@code null}
     */
    private ValueNode findOrCreatePositivePi(LogicNode noEntryCheck, ValueNode div, StructuredGraph graph, ControlFlowGraph cfg) {
        Stamp positiveIntStamp = StampFactory.positiveInt();
        if (!positiveIntStamp.isCompatible(div.stamp(NodeView.DEFAULT))) {
            return null;
        }
        if (cfg.getNodeToBlock().isNew(loop.loopBegin())) {
            return null;
        }
        HIRBlock loopBlock = cfg.blockFor(loop.loopBegin());
        for (Node checkUsage : noEntryCheck.usages()) {
            ValueNode candidateCheck = null;
            if (checkUsage instanceof IfNode ifCheck) {
                candidateCheck = ifCheck.falseSuccessor();
            } else if (checkUsage instanceof FixedGuardNode guard) {
                if (!guard.isNegated()) {
                    continue;
                }
                candidateCheck = guard;
            } else {
                continue;
            }

            if (cfg.getNodeToBlock().isNew(candidateCheck)) {
                continue;
            }
            if (cfg.blockFor(candidateCheck).dominates(loopBlock)) {
                return graph.addOrUniqueWithInputs(PiNode.create(div, positiveIntStamp.improveWith(div.stamp(NodeView.DEFAULT)), candidateCheck));
            }
        }
        return null;
    }

    /// Determines if the loop might be entered. Returns `false` if the loop statically cannot be
    /// entered; returns `true` if the loop might possibly be entered, including when the entry
    /// condition cannot be determined statically.
    ///
    /// @return false if the loop can definitely not be entered, true otherwise
    public boolean loopMightBeEntered() {
        if (isInverted()) {
            return true;
        }
        ValueNode max;
        ValueNode min;
        if (getLimitCheckedIV().direction() == InductionVariable.Direction.Up) {
            max = getTripCountLimit();
            // use the init of the body - if the limit checked IV is offset from the body by one
            // iteration for entering the loop we want to know min max for the init of the body IV
            min = getBodyIVStart();
        } else {
            assert getLimitCheckedIV().direction() == Direction.Down : Assertions.errorMessage(getLimitCheckedIV());
            // use the init of the body - if the limit checked IV is offset from the body by one
            // iteration for entering the loop we want to know min max for the init of the body IV
            max = getBodyIVStart();
            min = getTripCountLimit();
        }
        if (isLimitIncluded) {
            return inclusiveLoopMightBeEntered(min, max);
        }

        LogicNode entryCheck = getCounterIntegerHelper().createCompareNode(min, max, NodeView.DEFAULT);
        if (entryCheck.isContradiction()) {
            // We can definitely not enter this loop.
            return false;
        } else {
            // We don't know for sure that the loop can't be entered, so assume it can.
            return true;
        }
    }

    /// Determines whether an inclusive loop might enter by checking `min <= max` with the counter's
    /// signed or unsigned ordering. For an upward loop, `min` is the body IV start and `max` is the
    /// trip-count limit; for a downward loop, these roles are reversed. These names describe the
    /// endpoints' roles, not their relative values: `min` can exceed `max` in an unentered loop.
    ///
    /// For example, `for (int i = Integer.MAX_VALUE; i >= Integer.MAX_VALUE - 2; i--)` has
    /// `min = Integer.MAX_VALUE - 2` and `max = Integer.MAX_VALUE` and enters three times. Rewriting
    /// `min <= max` as `min < max + 1` would wrap `max + 1` to `Integer.MIN_VALUE`, incorrectly
    /// classifying the loop as unentered. The same problem occurs at unsigned maxima.
    ///
    /// Tests the reversed strict comparison `max < min` without arithmetic and rejects entry only
    /// when that comparison is proven true. Unknown cases must retain the loop because callers may
    /// delete its entire body when this method returns `false`.
    ///
    /// @return false if the inclusive loop can definitely not be entered, true otherwise
    private boolean inclusiveLoopMightBeEntered(ValueNode min, ValueNode max) {
        LogicNode noEntryCheck = getCounterIntegerHelper().createCompareNode(max, min, NodeView.DEFAULT);
        return !noEntryCheck.isTautology();
    }

    /**
     * @return true if the trip-count limit, body IV start, and limit-checked IV stride are constant.
     */
    public boolean isConstantMaxTripCount() {
        // Avoid constructing a derived IV start unless its inputs are constant.
        return getTripCountLimit() instanceof ConstantNode && getLimitCheckedIV().isConstantInit() && getLimitCheckedIV().isConstantStride() &&
                        getBodyIVStart() instanceof ConstantNode;
    }

    public UnsignedLong constantMaxTripCount() {
        assert isConstantMaxTripCount();
        return new UnsignedLong(rawConstantMaxTripCount());
    }

    /**
     * Compute the raw value of the trip count for this loop. THIS IS AN UNSIGNED VALUE;
     */
    private long rawConstantMaxTripCount() {
        assert getLimitCheckedIV().direction() != null;
        long endValue = getTripCountLimit().asJavaConstant().asLong();
        // use the init of the body - if the limit checked IV is offset from the body by one
        // iteration we want to know min max for the init of the body IV
        long initValue = getBodyIVStart().asJavaConstant().asLong();
        long range;
        long absStride;
        IntegerHelper helper = getCounterIntegerHelper(64);
        if (getLimitCheckedIV().direction() == InductionVariable.Direction.Up) {
            if (helper.compare(endValue, initValue) < 0) {
                return 0;
            }
            range = endValue - initValue;
            absStride = getLimitCheckedIV().constantStride();
        } else {
            assert getLimitCheckedIV().direction() == Direction.Down : Assertions.errorMessage(getLimitCheckedIV());
            if (helper.compare(initValue, endValue) < 0) {
                return 0;
            }
            range = initValue - endValue;
            absStride = -getLimitCheckedIV().constantStride();
        }
        if (isLimitIncluded) {
            range += 1;
        }
        long denominator = range + absStride - 1;
        return Long.divideUnsigned(denominator, absStride);
    }

    public IntegerHelper getCounterIntegerHelper() {
        IntegerStamp stamp = (IntegerStamp) getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT);
        return getCounterIntegerHelper(stamp.getBits());
    }

    public IntegerHelper getCounterIntegerHelper(int bits) {
        IntegerHelper helper;
        if (isUnsignedCheck()) {
            helper = new UnsignedIntegerHelper(bits);
        } else {
            helper = new SignedIntegerHelper(bits);
        }
        return helper;
    }

    public boolean isExactTripCount() {
        return loop.getCFGLoop().getNaturalExits().size() == 1;
    }

    public ValueNode exactTripCountNode() {
        assertNoOverflow();
        assert isExactTripCount();
        return maxTripCountNode();
    }

    public boolean isConstantExactTripCount() {
        assert isExactTripCount();
        return isConstantMaxTripCount();
    }

    public UnsignedLong constantExactTripCount() {
        assertNoOverflow();
        assert isExactTripCount();
        return constantMaxTripCount();
    }

    @Override
    public String toString() {
        return (isInverted() ? "Inverted " : "") + "iv=" + getLimitCheckedIV() + " until " + getTripCountLimit() +
                        (isLimitIncluded ? getLimitCheckedIV().direction() == InductionVariable.Direction.Up ? "+1" : "-1" : "") +
                        " bodyIV=" + getBodyIV();
    }

    /**
     * @return the {@link IfNode} that checks {@link CountedLoopInfo#getLimitCheckedIV()} against
     *         {@link CountedLoopInfo#getLimit()}.
     */
    public IfNode getLimitTest() {
        return ifNode;
    }

    /**
     * @return the {@link InductionVariable#initNode()} of the loop's {@link #getBodyIV()}, i.e.,
     *         the start node of the IV used inside the loop body (which can be different than the
     *         IV checked in {@link #getLimitCheckedIV()}}.
     */
    public ValueNode getBodyIVStart() {
        return getBodyIV().initNode();
    }

    public boolean isLimitIncluded() {
        return isLimitIncluded;
    }

    public AbstractBeginNode getBody() {
        return body;
    }

    /**
     * Returns the path exiting the loop at the counted exit check. Note that this is not
     * necessarily a {@link LoopExitNode} for reasons explained at {@link CFGLoop#getLoopExits()}.
     * Optimizations that re-wire the control flow should check if this is an actual exit node.
     */
    public AbstractBeginNode getCountedExit() {
        if (isInverted()) {
            AbstractBeginNode trueSucc = getLimitTest().trueSuccessor();
            if (loop.isCfgLoopExit(trueSucc)) {
                return trueSucc;
            }
            return getLimitTest().falseSuccessor();
        }
        if (getLimitTest().trueSuccessor() == getBody()) {
            return getLimitTest().falseSuccessor();
        } else {
            assert getLimitTest().falseSuccessor() == getBody() : Assertions.errorMessage(this, getLimitTest(), getLimitTest().falseSuccessor(), getBody());
            return getLimitTest().trueSuccessor();
        }
    }

    public InductionVariable.Direction getDirection() {
        return getLimitCheckedIV().direction();
    }

    public GuardingNode getOverFlowGuard() {
        return loop.loopBegin().getOverflowGuard();
    }

    /**
     * Tries to exclude overflow of the counter updated by each supported loop, so later optimizations can use
     * its iteration count. For example:
     *
     * <pre>
     * for (int i = 0; (long) i &lt; limit; i++) {
     *     body(i);
     * }
     * </pre>
     *
     * Here {@code i} is the root induction variable (IV): the loop updates it with {@code i++}. The
     * limit-checked IV is the derived value {@code (long) i}. That conversion fits in a long even when the
     * int increment overflows. For this shape, {@link #prepareRootOverflowGuard()} can add a check before the
     * loop that excludes such limits.
     *
     * <p>Preparation can add nodes, replace loop comparisons and rebuild counted-loop information. Obtain
     * fresh loop data afterwards. Returning does not mean every loop was prepared: unsupported loops,
     * substitutions, disallowed floating guards and failed speculation can prevent a rewrite. Each consumer
     * must still test {@link #loopAndRootCanNeverOverflow()} for its loop and keep its existing fallback when
     * the result is false.
     *
     * <p>Every phase that wants to optimize a supported widened-counter loop by bounding its int counter
     * must call this preparation before obtaining loop information. Inversion changes the supported head
     * test, guard movement uses the iteration count, and guard lowering ends the opportunity to insert
     * floating guards. These phases can run independently, so a call in one phase does not replace the calls
     * in the others.
     */
    public static void prepareRootOverflowGuards(StructuredGraph graph, CoreProviders providers) {
        if (graph.isSubstitution() || !graph.getGuardsStage().allowsFloatingGuards() || !graph.hasLoops()) {
            return;
        }
        LoopsData loops = providers.getLoopsDataProvider().getLoopsData(graph);
        for (Loop candidate : loops.loops()) {
            if (candidate.detectCounted()) {
                candidate.counted().prepareRootOverflowGuard();
            }
        }
    }

    /**
     * Adds a speculative check on the limit of a loop such as:
     *
     * <pre>
     * for (int i = 0; (long) i &lt; limit; i++) {
     *     body(i);
     * }
     * </pre>
     *
     * The root IV is the int counter {@code i}; the limit-checked IV is {@code (long) i}. With {@code limit =
     * 2147483648L}, incrementing {@code i} past {@link Integer#MAX_VALUE} produces {@link Integer#MIN_VALUE}.
     * Every int value is still below that long limit, so the loop condition never becomes false. The
     * conversion itself does not overflow.
     *
     * <p>For the supported shape, the transformation is equivalent to:
     *
     * <pre>
     * long checkedLimit = Math.max(limit, 0);
     * if (checkedLimit > (long) Integer.MAX_VALUE) {
     *     deopt;
     * }
     * for (int i = 0; (long) i &lt; checkedLimit; i++) {
     *     body(i);
     * }
     * </pre>
     *
     * If execution passes the new guard, {@code i} reaches the limit before an int increment can overflow.
     * The nonnegative checked limit permits a useful Pi stamp. {@link
     * #supportsRootOverflowGuard(BasicInductionVariable)} requires a nonnegative start, preserving whether
     * the original loop executes. This method records {@code checkedLimit}'s range as {@code [0,
     * Integer.MAX_VALUE]} in a guarded Pi, replaces the comparison limit and rebuilds counted-loop
     * information. Use {@code loop.counted()} afterwards rather than retaining this instance.
     *
     * <p>{@link #supportsRootOverflowGuard(BasicInductionVariable)} defines the supported loop shape. No
     * rewrite is needed when overflow of {@code i} is already excluded. Substitutions and stages that
     * disallow floating guards are skipped; failed speculation can also prevent insertion.
     *
     * <p>This method returns no success indication. A consumer must query {@link
     * #loopAndRootCanNeverOverflow()} on the resulting counted loop and decline its optimization if overflow
     * is still possible or unproved.
     */
    public void prepareRootOverflowGuard() {
        StructuredGraph graph = loop.loopBegin().graph();
        if (graph.isSubstitution() || !graph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }
        BasicInductionVariable root = limitCheckedIV.getRootIV();
        if (root == limitCheckedIV || ivCanNeverOverflow(root) || !root.isConstantStride() || !supportsRootOverflowGuard(root)) {
            return;
        }
        ValueNode originalLimit = getLimit();
        IntegerStamp limitStamp = (IntegerStamp) originalLimit.stamp(NodeView.DEFAULT);
        ValueNode zero = ConstantNode.forIntegerStamp(limitStamp, 0, graph);
        ValueNode nonNegativeLimit = graph.addOrUniqueWithInputs(MaxNode.create(originalLimit, zero, NodeView.DEFAULT));
        ValueNode max = ConstantNode.forIntegerStamp(limitStamp, Integer.MAX_VALUE, graph);
        LogicNode overflow = graph.addOrUniqueWithInputs(IntegerLessThanNode.create(max, nonNegativeLimit, NodeView.DEFAULT));
        GuardingNode guard = createOverflowGuard(overflow, null);
        if (guard == null) {
            return;
        }
        ValueNode guardedLimit = graph.addOrUniqueWithInputs(PiNode.create(nonNegativeLimit, IntegerStamp.create(limitStamp.getBits(), 0, Integer.MAX_VALUE), guard.asNode()));
        LogicNode condition = (LogicNode) getLimitTest().condition().copyWithInputs(true);
        condition.replaceFirstInput(originalLimit, guardedLimit);
        getLimitTest().setCondition(condition);
        loop.resetCounted();
        loop.detectCounted();
    }

    /**
     * Recognizes the loop shape for which checking the limit alone excludes overflow of the int increment:
     *
     * <pre>
     * for (int i = 7; (long) i &lt; limit; i++) {
     *     body(i);
     * }
     * </pre>
     *
     * The root IV is {@code i}, and the limit-checked IV must be its direct sign extension. A constant
     * nonnegative start and a stride of one mean a limit at most {@link Integer#MAX_VALUE} stops the loop
     * before an increment overflows. For example, a limit of 10 gives body values 7, 8 and 9; the last
     * increment reaches 10. A limit at or below 7 executes no iterations. Clamping a negative limit to zero
     * preserves that empty-loop case because the start is nonnegative.
     *
     * <p>The signed, exclusive head test is required: an inclusive test could still increment after
     * visiting {@code Integer.MAX_VALUE}, and a tail test executes the body before testing the limit.
     * Neither is supported here.
     *
     * <p>The conversion must directly read {@code i}. For {@code (long) (i + offset)}, the int addition may
     * overflow before conversion, so checking the long limit is insufficient.
     *
     * @param root the limit-checked IV's root, already known to have a constant stride
     */
    private boolean supportsRootOverflowGuard(BasicInductionVariable root) {
        final boolean signedExclusiveHeadTest = !isInverted() && !isUnsignedCheck() && !isLimitIncluded();
        final boolean incrementsFromNonNegativeStart = root.constantStride() == 1 && root.isConstantInit() && root.constantInit() >= 0;
        final boolean intRoot = IntegerStamp.getBits(root.valueNode().stamp(NodeView.DEFAULT)) == Integer.SIZE;
        final boolean directlySignExtendedRoot = limitCheckedIV instanceof DerivedConvertedInductionVariable converted && converted.getBase() == root &&
                        converted.valueNode() instanceof SignExtendNode;
        return signedExclusiveHeadTest && incrementsFromNonNegativeStart && intRoot && directlySignExtendedRoot;
    }

    /**
     * Determine if there are static properties or dynamic checks in place that guarantee that the
     * {@link #getLimitCheckedIV()} never overflows.
     */
    public boolean loopCanNeverOverflow() {
        return counterNeverOverflows() || getOverFlowGuard() != null;
    }

    /**
     * Checks whether existing stamps or guards exclude overflow of both the limit-checked IV and its root.
     * Consider:
     *
     * <pre>
     * for (int i = 0; (long) i &lt; limit; i++) {
     *     body(i);
     * }
     * </pre>
     *
     * The root IV is {@code i}, updated as an int. The limit-checked IV is {@code (long) i}. The long conversion
     * fits even if {@code i++} overflows from {@link Integer#MAX_VALUE} to {@link Integer#MIN_VALUE}; proving
     * only the limit-checked IV safe does not establish that this loop reaches its limit. This method also checks
     * {@code i}. In a plain {@code i &lt; intLimit} loop, the limit-checked IV and root are the same, so one check
     * suffices.
     *
     * <p>No guards are inserted. A false result means at least one required proof is unavailable; it does not
     * mean overflow must occur.
     */
    public boolean loopAndRootCanNeverOverflow() {
        InductionVariable checked = getLimitCheckedIV();
        return loopCanNeverOverflow() && (checked == checked.getRootIV() || ivCanNeverOverflow(checked.getRootIV()));
    }

    /**
     * Determines with static analysis if the counter {@link #getLimitCheckedIV()} can ever
     * overflow. If this method returns {@code true} it is statically known the limit checked
     * induction variable never overflows. If this method returns {@code false} it is still possible
     * that the limit checked IV never overflows but we have not been able to prove it statically.
     */
    public boolean counterNeverOverflows() {
        if (loop.loopBegin().canNeverOverflow()) {
            return true;
        }
        return ivCanNeverOverflow(getLimitCheckedIV());
    }

    /**
     * Checks the update from the first body value to the first tested value, which a tail-test proof alone
     * does not cover:
     *
     * <pre>
     * int i = start;
     * do {
     *     body(i);
     *     i += 4;
     * } while (i &lt; limit);
     * </pre>
     *
     * Here {@code iv} is the body IV {@code i}; the limit-checked IV is its next value {@code i + 4}. With {@code
     * start = Integer.MAX_VALUE}, the first addition overflows before the first test. A guard that protects
     * later updates of the limit-checked IV does not constrain this initial body value.
     *
     * <p>First try the initial-value and stride stamps. If they permit overflow, combine an existing overflow
     * guard with comparisons on the path into the loop. For example, a guard establishing {@code limit <=
     * Integer.MAX_VALUE - 3} and an entry test {@code start < limit} give {@code start <= Integer.MAX_VALUE -
     * 4}, making the first addition safe. The result is true only if that addition and the subsequent checked
     * updates are both proved safe. This method inserts no guards.
     */
    private boolean initialUpdateCanNeverOverflow(InductionVariable iv) {
        if (!loopCanNeverOverflow()) {
            return false;
        }
        IntegerStamp initStamp = (IntegerStamp) iv.initNode().stamp(NodeView.DEFAULT);
        IntegerStamp strideStamp = (IntegerStamp) iv.strideNode().stamp(NodeView.DEFAULT);
        if (IntegerStamp.addCanOverflow(initStamp, strideStamp)) {
            // For stride four, the guard can establish limit <= MAX_VALUE - 3. Use that bound when
            // interpreting an entry test such as start < limit.
            Stamp limitStamp = getTripCountLimit().stamp(NodeView.DEFAULT);
            limitStamp = refineLimitStampFromOverflowGuard(limitStamp);
            initStamp = refineInitialStampFromEntryTests(iv.initNode(), initStamp, limitStamp);
        }
        return !IntegerStamp.addCanOverflow(initStamp, strideStamp);
    }

    /**
     * Recovers a bound on the loop limit from an existing overflow guard. For example:
     *
     * <pre>
     * int i = start;
     * do {
     *     body(i);
     *     i += 4;
     * } while (i &lt; limit);
     * </pre>
     *
     * The body IV is {@code i}; the limit-checked IV is {@code i + 4}. Its overflow guard can establish {@code
     * limit <= Integer.MAX_VALUE - 3}. That bound is useful to the caller: together with an entry test {@code
     * start < limit}, it proves the first addition safe.
     *
     * <p>Interpret the guard's successful branch using its negation flag and either operand order, and
     * intersect any bound on {@link #getTripCountLimit()} with {@code limitStamp}. If the guard is not a
     * floating comparison involving that limit, or yields no narrower range, return the existing stamp. This
     * only computes a stamp; it neither inserts a guard nor changes the graph.
     */
    private Stamp refineLimitStampFromOverflowGuard(Stamp originalLimitStamp) {
        Stamp limitStamp = originalLimitStamp;
        if (getOverFlowGuard() instanceof GuardNode overflowGuard) {
            LogicNode overflowCondition = overflowGuard.getCondition();
            boolean negated = overflowGuard.isNegated();
            // A newly created overflow guard may be queried before its negation is canonicalized.
            if (overflowCondition instanceof LogicNegationNode not) {
                overflowCondition = not.getValue();
                negated = !negated;
            }
            if (overflowCondition instanceof CompareNode overflowCompare) {
                Stamp refined = null;
                if (overflowCompare.getX() == getTripCountLimit()) {
                    refined = overflowCompare.getSucceedingStampForX(negated, limitStamp, overflowCompare.getY().stamp(NodeView.DEFAULT));
                } else if (overflowCompare.getY() == getTripCountLimit()) {
                    refined = overflowCompare.getSucceedingStampForY(negated, overflowCompare.getX().stamp(NodeView.DEFAULT), limitStamp);
                }
                if (refined != null) {
                    limitStamp = limitStamp.improveWith(refined);
                }
            }
        }
        return limitStamp;
    }

    /**
     * Narrows the initial body value using comparisons that every path into the loop must satisfy. For
     * example:
     *
     * <pre>
     * if (start &lt; limit) {
     *     int i = start;
     *     do {
     *         body(i);
     *         i += 4;
     *     } while (i &lt; limit);
     * }
     * </pre>
     *
     * The root/body IV is {@code i}, initialized by {@code initialValue = start}; the limit-checked IV is {@code i
     * + 4}. If {@code limitStamp} already establishes {@code limit <= Integer.MAX_VALUE - 3}, the entry
     * comparison gives {@code start <= Integer.MAX_VALUE - 4}. The caller can then exclude overflow of the
     * first addition.
     *
     * <p>Only the true or false successor that dominates the loop header constrains entry: a comparison on a
     * path the loop can bypass proves nothing. When that comparison uses the trip-count limit, use the
     * supplied refined stamp. Ignore successors absent from the current CFG and retain the initial range when
     * no useful comparison is found. The graph is unchanged.
     */
    private IntegerStamp refineInitialStampFromEntryTests(ValueNode initialValue, IntegerStamp originalInitStamp, Stamp limitStamp) {
        IntegerStamp initStamp = originalInitStamp;
        ControlFlowGraph cfg = loop.loopsData().getCFG();
        HIRBlock header = cfg.blockFor(loop.loopBegin());
        for (HIRBlock dominator = header.getDominator(); dominator != null; dominator = dominator.getDominator()) {
            if (dominator.getEndNode() instanceof IfNode branch && branch.isAlive() && branch.condition() instanceof CompareNode compare &&
                            !cfg.getNodeToBlock().isNew(branch.trueSuccessor()) && !cfg.getNodeToBlock().isNew(branch.falseSuccessor())) {
                boolean onTrue = cfg.blockFor(branch.trueSuccessor()).dominates(header);
                if (!onTrue && !cfg.blockFor(branch.falseSuccessor()).dominates(header)) {
                    continue;
                }
                Stamp refined = null;
                if (compare.getX() == initialValue) {
                    refined = compare.getSucceedingStampForX(!onTrue, initStamp, compare.getY() == getTripCountLimit() ? limitStamp : compare.getY().stamp(NodeView.DEFAULT));
                } else if (compare.getY() == initialValue) {
                    refined = compare.getSucceedingStampForY(!onTrue, compare.getX() == getTripCountLimit() ? limitStamp : compare.getX().stamp(NodeView.DEFAULT), initStamp);
                }
                if (refined != null) {
                    initStamp = (IntegerStamp) initStamp.improveWith(refined);
                }
            }
        }
        return initStamp;
    }

    /**
     * Checks whether the limit-checked IV is one supported direct derivation of its root. For example:
     *
     * <pre>
     * for (int i = 0; (long) i &lt; limit; i++) {
     *     body(i);
     * }
     * </pre>
     *
     * The root is {@code i}; the limit-checked IV directly sign-extends it. Also accept zero extension and
     * direct arithmetic such as {@code i + 7}. The IV detector already requires nonnegative inputs for
     * zero-extension IVs, and arithmetic IVs keep their base's integer width.
     *
     * <p>Pi and narrowing IVs are not supported by the root-range calculation. A Pi may carry a branch-local
     * bound; narrowing changes the width in which the limit-checked values advance.
     *
     * <p>The immediate base must be the root. Thus {@code (long) (i + 1)} is rejected: the addition can
     * overflow as an int before conversion, changing the long sequence from {@code 2147483647L} to {@code
     * -2147483648L}. Recognizing a supported form does not prove {@code i} cannot overflow; the caller must
     * still check its range.
     */
    private static boolean isDirectAffineRoot(InductionVariable limitCheckedIV) {
        final ValueNode limitCheckedValue = limitCheckedIV.valueNode();
        return limitCheckedIV instanceof DerivedInductionVariable derived && derived.getBase() == limitCheckedIV.getRootIV() &&
                        (!(derived instanceof DerivedConvertedInductionVariable) || limitCheckedValue instanceof SignExtendNode || limitCheckedValue instanceof ZeroExtendNode);
    }

    /**
     * Bounds the number of iterations from the body-start and limit stamps, without narrowing the count to an
     * int. For example:
     *
     * <pre>
     * for (int i = start; (long) i &lt; 10; i += 3) {
     *     body(i);
     * }
     * </pre>
     *
     * The root IV is {@code i}, and the limit-checked IV is {@code (long) i}. If {@code start} is in {@code [0,
     * 2]}, the longest sequence starts at zero: {@code 0, 3, 6, 9}, or {@code ceil((10 - 0) / 3) = 4}
     * iterations. The caller uses this bound to check the root's final update separately.
     *
     * <p>Use the comparison's signed or unsigned ordering to select the furthest start and limit. Divide
     * their distance upward by the absolute stride of the limit-checked IV; an inclusive bound adds one to
     * the distance. The result is at least one, conservatively covering an inverted loop's first iteration.
     *
     * <p>An unsigned 32-bit count can exceed {@link Integer#MAX_VALUE}, even when another graph node
     * represents it with a positive int Pi. Read the original stamps and use exact long arithmetic so that
     * narrowing cannot hide those iterations. Arithmetic overflow or a negative distance throws {@link
     * ArithmeticException}; the caller then declines the proof.
     */
    private long maximumRootTripCount() {
        IntegerStamp startStamp = (IntegerStamp) getBodyIVStart().stamp(NodeView.DEFAULT);
        IntegerStamp limitStamp = (IntegerStamp) getTripCountLimit().stamp(NodeView.DEFAULT);
        IntegerHelper helper = getCounterIntegerHelper();
        boolean up = limitCheckedIV.direction() == Direction.Up;
        long max = helper.upperBound(up ? limitStamp : startStamp);
        long min = helper.lowerBound(up ? startStamp : limitStamp);
        long range = Math.subtractExact(max, min);
        if (range < 0) {
            throw new ArithmeticException("Negative trip-count range");
        }
        long absStride = Math.absExact(limitCheckedIV.constantStride());
        long numerator = Math.addExact(range, absStride - 1);
        if (isLimitIncluded()) {
            numerator = Math.incrementExact(numerator);
        }
        return Math.max(1, numerator / absStride);
    }

    /**
     * Bounds the updates of an IV other than the limit-checked IV and rejects any native-width overflow.
     * For example:
     *
     * <pre>
     * for (int i = 0, j = 5; (long) i &lt; 9; i += 3, j += 2) {
     *     body(j);
     * }
     * </pre>
     *
     * The limit-checked IV is {@code (long) i}, its root is {@code i}, and {@code j} is an independent IV. Three
     * iterations take {@code i} to 9 and {@code j} to 11. To prove {@code j} safe, it is not enough that 11
     * fits in an int: the count must also describe the actual updates of {@code i}.
     *
     * <p>Returns true only for a constant-stride IV when the limit-checked IV is known not to overflow. If
     * the limit-checked IV differs from its root, it must be a supported direct derivation of that root.
     * An inverted loop must also have the initial body value described by {@link
     * #invertedBodyStartMatchesCheckedIV()}. A constant iteration count may bound another IV only after the
     * root's final update is known to fit its integer width. A count derived from stamps is used only to
     * establish that property for the root itself. Finally require the requested IV's extremum to fit its
     * integer width. Missing information, an unsupported shape or failed exact arithmetic returns false.
     */
    private boolean nonCheckedIVCanNeverOverflow(InductionVariable iv) {
        if (!iv.isConstantStride() || !loopCanNeverOverflow()) {
            return false;
        }
        try {
            BasicInductionVariable root = limitCheckedIV.getRootIV();
            final boolean directAffineRoot = isDirectAffineRoot(limitCheckedIV);
            // A constant limit does not make (long) (i + offset) exact: the int addition can overflow first.
            // An inverted loop also needs a representable previous body value before its count can bound
            // another IV.
            if (limitCheckedIV != root && !directAffineRoot || !invertedBodyStartMatchesCheckedIV()) {
                return false;
            }
            final long maxTripCount;
            if (iv.isConstantInit() && isConstantMaxTripCount()) {
                maxTripCount = LoopUtility.tripCountSignedExact(this);
                if (iv != root && limitCheckedIV != root) {
                    // Before reusing this count for an independent IV, require the root's final update to
                    // fit its own integer width.
                    checkIVExtremum(root, maxTripCount);
                }
            } else if (iv == root && directAffineRoot && limitCheckedIV.isConstantStride()) {
                maxTripCount = maximumRootTripCount();
            } else {
                return false;
            }
            checkIVExtremum(iv, maxTripCount);
            return true;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    /**
     * Checks the initial values used to count an inverted loop such as:
     *
     * <pre>
     * int i = start;
     * do {
     *     body(i);
     *     i += stride;
     * } while (i &lt; limit);
     * </pre>
     *
     * When the body and limit-checked IVs differ, requires {@code bodyInit == limitCheckedInit -
     * limitCheckedStride}. Requires constant values and evaluates the subtraction using exact long
     * arithmetic. Unknown or unequal values return false; arithmetic overflow propagates to the caller.
     *
     * <p>Returns true without checking for non-inverted loops or identical body and limit-checked IVs.
     */
    private boolean invertedBodyStartMatchesCheckedIV() {
        if (!isInverted() || bodyUsedIV == limitCheckedIV) {
            return true;
        }
        ValueNode bodyStart = getBodyIVStart();
        return limitCheckedIV.isConstantInit() && limitCheckedIV.isConstantStride() && bodyStart.isConstant() &&
                        bodyStart.asJavaConstant().asLong() == Math.subtractExact(limitCheckedIV.constantInit(), limitCheckedIV.constantStride());
    }

    /**
     * Checks that the IV's last update fits its integer width. For example:
     *
     * <pre>
     * for (int i = 0, j = 5; i &lt; 3; i++, j += 2) {
     *     body(j);
     * }
     * </pre>
     *
     * For {@code iv = j} and {@code maxTripCount = 3}, the body sees {@code 5, 7, 9}, but the final update
     * produces 11. Check {@code 5 + 2 * 3}, including that update, with exact 32-bit multiplication and
     * addition.
     *
     * <p>For a range of possible initial values, use its upper bound when the stride is positive and its
     * lower bound when negative. An unrepresentable product or sum throws {@link ArithmeticException}, so the
     * caller declines the proof. The caller must already have justified the iteration bound; this method does
     * not establish that the controlling loop terminates.
     */
    private static void checkIVExtremum(InductionVariable iv, long maxTripCount) {
        int bits = IntegerStamp.getBits(iv.valueNode().stamp(NodeView.DEFAULT));
        long stride = iv.constantStride();
        long strideTimesTripCount = LoopUtility.multiplyExact(bits, stride, maxTripCount);
        IntegerStamp initStamp = (IntegerStamp) iv.initNode().stamp(NodeView.DEFAULT);
        @SuppressWarnings("unused")
        long extremum = LoopUtility.addExact(bits, strideTimesTripCount, stride > 0 ? initStamp.upperBound() : initStamp.lowerBound());
    }

    /**
     * Tries to prove that {@code iv}'s updates do not overflow, using existing stamps and guards. For
     * example:
     *
     * <pre>
     * for (int i = 0, j = 5; (long) i &lt; 9; i += 3, j += 2) {
     *     body(j);
     * }
     * </pre>
     *
     * The limit-checked IV is {@code (long) i}; its root is the updated int {@code i}; {@code j} is an independent
     * IV. Proving that the limit-checked IV does not overflow does not establish either int counter's range.
     * To prove that {@code j} does not overflow, first justify the three-iteration bound using {@code i}, then
     * establish that {@code j}'s last update to 11 fits in an int.
     *
     * <p>A tail-counted loop has another case: its first body update occurs before its first condition test,
     * so that update needs a separate check. Dispatch those body/root updates to {@link
     * #initialUpdateCanNeverOverflow(InductionVariable)}, other IVs except the limit-checked IV to {@link
     * #nonCheckedIVCanNeverOverflow(InductionVariable)}, and reason directly from the stride and limit for
     * the limit-checked IV. The latter must not obtain its own proof recursively through {@link
     * #loopCanNeverOverflow()}.
     *
     * <p>No guards are inserted. False means a proof is unavailable, not that overflow necessarily occurs.
     */
    public boolean ivCanNeverOverflow(InductionVariable iv) {
        final boolean exactRootUpdate = iv instanceof BasicInductionVariable basic && iv != limitCheckedIV && basic.getOp() == limitCheckedIV.valueNode() &&
                        !isUnsignedCheck() && iv.isConstantStride() && Loop.absStrideIsOne(iv);
        final boolean previousBodyValue = isInverted() && bodyUsedIV != limitCheckedIV && iv == bodyUsedIV;
        if (previousBodyValue || exactRootUpdate) {
            return initialUpdateCanNeverOverflow(iv);
        }
        if (iv != getLimitCheckedIV()) {
            return nonCheckedIVCanNeverOverflow(iv);
        }

        // BELOW: limitCheckedIV case

        if (!isLimitIncluded && iv.isConstantStride() && Loop.absStrideIsOne(iv)) {
            return true;
        }
        if (loop.loopBegin().isProtectedNonOverflowingUnsigned()) {
            return true;
        }
        // @formatter:off
           /*
            * Following comment reasons about the simplest possible loop form:
            *
            *              for(i = 0;i < end;i += stride)
            *
            * The problem is we want to create an overflow guard for the loop that can be hoisted
            * before the loop, i.e., the overflow guard must not have loop variant inputs else it must
            * be scheduled inside the loop. This means we cannot refer explicitly to the induction
            * variable's phi but must establish a relation between end, stride and max (max integer
            * range for a given loop) that is sufficient for most cases.
            *
            * We know that a head counted loop with a stride > 1 may overflow if the stride is big
            * enough that end + stride will be > MAX, i.e. it overflows into negative value range.
            *
            * It is important that "end" in this context is the checked value of the loop condition:
            * i.e., an arbitrary value. There is no relation between end and MAX established except
            * that based on the integer representation we know that end <= MAX.
            *
            * A loop can overflow if the last checked value of the iv allows an overflow in the next
            * iteration: the value range for which an overflow can happen is [MAX-(stride-1),MAX] e.g.
            *
            * MAX=10, stride = 3, overflow if number > 10
            *  end = MAX -> 10 -> 10 + 3 = 13 -> overflow
            *  end = MAX-1 -> 9 -> 9 + 3 = 12 -> overflow
            *  end = MAX-2 -> 8 -> 8 + 3 = 11 -> overflow
            *  end = MAX-3 -> 7 -> 7 + 3 = 10 -> No overflow at MAX - stride
            *
            * Note that this guard is pessimistic, i.e., it marks loops as potentially overflowing that
            * are actually not overflowing. Consider the following loop:
            *
            * <pre>
            *    for(i = MAX-56; i < MAX, i += 8)
            * </pre>
            *
            *  where i in last loop body visit = MAX - 8, i after = MAX, no overflow
            *
            * which is wrongly detected as overflowing since "end" is element of [MAX-(stride-1),MAX]
            * which is [MAX-7,MAX] and end is MAX. We handle such cases with a speculation and disable
            * counted loop detection on subsequent compilations. We can only avoid such false positive
            * detections by actually computing the number of iterations with a division, however we try
            * to avoid that since that may be part of the fast path.
            *
            * And additional backup strategy could be to actually emit the precise guard inside the
            * loop if the deopt already failed, but we refrain from this for now for simplicity
            * reasons.
            */
           // @formatter:on
        IntegerStamp endStamp = (IntegerStamp) getTripCountLimit().stamp(NodeView.DEFAULT);
        ValueNode strideNode = getLimitCheckedIV().strideNode();
        IntegerStamp strideStamp = (IntegerStamp) strideNode.stamp(NodeView.DEFAULT);
        IntegerHelper integerHelper = getCounterIntegerHelper();
        if (getDirection() == InductionVariable.Direction.Up) {
            long max = integerHelper.maxValue();
            // Negating MIN_VALUE can produce a mathematical step above the signed stride stamp's upper bound.
            return strideStamp.lowerBound() >= 0 &&
                            integerHelper.compare(endStamp.upperBound(), max - (strideStamp.upperBound() - 1) - (isLimitIncluded ? 1 : 0)) <= 0;
        } else if (getDirection() == InductionVariable.Direction.Down) {
            long min = integerHelper.minValue();
            return integerHelper.compare(min + (1 - strideStamp.lowerBound()) + (isLimitIncluded ? 1 : 0), endStamp.lowerBound()) <= 0;
        }
        return false;
    }

    @SuppressWarnings("try")
    public GuardingNode createOverFlowGuard() {
        GuardingNode overflowGuard = getOverFlowGuard();
        if (overflowGuard != null || counterNeverOverflows()) {
            return overflowGuard;
        }
        try (DebugCloseable position = loop.loopBegin().withNodeSourcePosition()) {
            overflowGuard = createOverflowGuard(createOverflowGuardCondition(), null);
            if (overflowGuard == null) {
                GraalError.shouldNotReachHere("Must not create overflow guard for loop " + loop.loopBegin() + " where the speculation guard already failed, this can create deopt loops"); // ExcludeFromJacocoGeneratedReport
            }
            loop.loopBegin().setOverflowGuard(overflowGuard);
            return overflowGuard;
        }
    }

    /**
     * Returns whether {@link #createOverflowGuard(LogicNode, DeoptimizationReason)} would be able
     * to materialize a guard under the current speculation state. This is not a guarantee: A later
     * call to {@link #createOverflowGuard(LogicNode, DeoptimizationReason)} might still fail the
     * speculation and would then return {@code null}.
     * <p>
     * Without a speculation log, {@link #createOverflowGuard(LogicNode, DeoptimizationReason)}
     * creates a non-speculative guard, so there is no failed speculation state that could block
     * guard creation.
     */
    public boolean canCreateOverflowGuard(DeoptimizationReason additionalReason) {
        StructuredGraph graph = getLimitCheckedIV().valueNode().graph();
        SpeculationLog speculationLog = graph.getSpeculationLog();
        if (speculationLog != null) {
            SpeculationLog.SpeculationReason speculationReason = createOverflowGuardSpeculationReason(graph, additionalReason);
            return speculationLog.maySpeculate(speculationReason);
        }
        return true;
    }

    /**
     * Creates a loop overflow guard for an arbitrary overflow condition.
     * <p>
     * When {@code additionalReason} is present, it is added to the speculation reason and used as
     * the resulting guard's deoptimization reason. The default deoptimization reason is
     * {@link DeoptimizationReason#LoopLimitCheck}.
     */
    public GuardingNode createOverflowGuard(LogicNode condition, DeoptimizationReason additionalReason) {
        StructuredGraph graph = getLimitCheckedIV().valueNode().graph();
        SpeculationLog speculationLog = graph.getSpeculationLog();
        SpeculationLog.Speculation speculation = SpeculationLog.NO_SPECULATION;
        if (speculationLog != null) {
            SpeculationLog.SpeculationReason speculationReason = createOverflowGuardSpeculationReason(graph, additionalReason);
            if (speculationLog.maySpeculate(speculationReason)) {
                speculation = speculationLog.speculate(speculationReason);
                if (additionalReason == null) {
                    LoopBeginNode.overflowSpeculationTaken.increment(graph.getDebug());
                }
            } else {
                return null;
            }
        }
        GraalError.guarantee(graph.getGuardsStage().allowsFloatingGuards(), "Expected floating guards to be allowed when creating loop overflow guards");
        DeoptimizationReason guardReason = additionalReason != null ? additionalReason : DeoptimizationReason.LoopLimitCheck;
        return graph.unique(new GuardNode(condition, AbstractBeginNode.prevBegin(loop.entryPoint()), guardReason, DeoptimizationAction.InvalidateRecompile, true, speculation, null));
    }

    private SpeculationLog.SpeculationReason createOverflowGuardSpeculationReason(StructuredGraph graph, DeoptimizationReason additionalReason) {
        if (additionalReason == null) {
            return LoopBeginNode.LOOP_OVERFLOW_DEOPT.createSpeculationReason(graph.method(), getLimitCheckedIV().loop.loopBegin().stateAfter().bci);
        } else {
            return LOOP_OVERFLOW_DEOPT_WITH_REASON.createSpeculationReason(graph.method(), getLimitCheckedIV().loop.loopBegin().stateAfter().bci, additionalReason);
        }
    }

    /**
     * Creates an overflow guard condition, that is the condition such that if it is satisfied, the
     * induction variable will overflow, and we have to treat the loop as a non-counted one.
     * <p>
     * For example, given this loop:
     * {@snippet :
     * for (int i = 0; i < limit; i += 2) {
     * }
     * }
     * Most of the time, this loop will execute a limited amount of iterations, and the value of
     * {@code i} inside the loop body will be in the interval {@code [0, limit)}. However, in the
     * rare cases that {@code limit == Integer.MAX_VALUE}, the addition {@code i += 2} will
     * overflow, and the loop would not terminate. In those cases, We cannot treat the loop as a
     * counted loop. As a result, we insert a guard for those circumstances. The guard is
     * conservative, that is it will catch all cases where the calculation of the induction variable
     * overflows, and it may catch cases where the calculation does not actually overflow. In the
     * example, the guard would be:
     * {@snippet :
     * if (limit > Integer.MAX_VALUE - 1) {
     *     deoptimize();
     * }
     * }
     * This method creates the aforementioned guard, it has the value {@code true} if the
     * calculation may overflow, and {@code false} if it cannot, in such cases assumptions about
     * counted loops hold.
     */
    public LogicNode createOverflowGuardCondition() {
        StructuredGraph graph = getLimitCheckedIV().valueNode().graph();
        if (counterNeverOverflows()) {
            return LogicConstantNode.contradiction(graph);
        }
        IntegerStamp stamp = (IntegerStamp) getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT);
        IntegerHelper integerHelper = getCounterIntegerHelper();
        LogicNode cond; // we use a negated guard with a < condition to achieve a >=
        ConstantNode one = ConstantNode.forIntegerStamp(stamp, 1, graph);
        if (getLimitCheckedIV().direction() == InductionVariable.Direction.Up) {
            // overflow ~ (tripCountLimit + stride) overflow ~ tripCountLimit > maxValue - stride
            ValueNode maxValue = ConstantNode.forIntegerStamp(stamp, integerHelper.maxValue(), graph);
            ValueNode maxNonOverflowLimitValue = BinaryArithmeticNode.sub(graph, maxValue, getLimitCheckedIV().strideNode(), NodeView.DEFAULT);
            if (!isLimitIncluded) {
                maxNonOverflowLimitValue = BinaryArithmeticNode.add(graph, maxNonOverflowLimitValue, one, NodeView.DEFAULT);
            }
            cond = graph.addOrUniqueWithInputs(integerHelper.createCompareNode(maxNonOverflowLimitValue, getTripCountLimit(), NodeView.DEFAULT));
        } else {
            assert getLimitCheckedIV().direction() == Direction.Down : Assertions.errorMessage(getLimitCheckedIV());
            // overflow ~ (tripCountLimit + stride) overflow ~ tripCountLimit < minValue - stride
            ValueNode minValue = ConstantNode.forIntegerStamp(stamp, integerHelper.minValue(), graph);
            ValueNode minNonOverflowLimitValue = BinaryArithmeticNode.sub(graph, minValue, getLimitCheckedIV().strideNode(), NodeView.DEFAULT);
            if (!isLimitIncluded) {
                minNonOverflowLimitValue = BinaryArithmeticNode.sub(graph, minNonOverflowLimitValue, one, NodeView.DEFAULT);
            }
            cond = graph.addOrUniqueWithInputs(integerHelper.createCompareNode(getTripCountLimit(), minNonOverflowLimitValue, NodeView.DEFAULT));
        }
        return cond;
    }

    public IntegerStamp getStamp() {
        return (IntegerStamp) getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT);
    }

    public boolean isInverted() {
        return body == loop.loopBegin();
    }

    public static boolean backedgeSkippableNode(Node backEdgeNode) {
        return backEdgeNode instanceof FullInfopointNode || backEdgeNode instanceof SafepointNode ||
                        backEdgeNode instanceof TruffleSafepointNode;
    }

    public void setInvertedLoopComplexSignednessRange(boolean invertedLoopComplexSignednessRange) {
        this.invertedLoopComplexSignednessRange = invertedLoopComplexSignednessRange;
    }

    public boolean isInvertedLoopComplexSignednessRange() {
        return isInverted() && unsigned && invertedLoopComplexSignednessRange;
    }

    public boolean assertUnsignedIVsAreSane() {
        assert !isInverted() || !unsigned || !invertedLoopComplexSignednessRange || assertBodyAndLimitIVSignedness();
        return true;
    }

    public boolean assertBodyAndLimitIVSignedness() {
        if (bodyUsedIV == limitCheckedIV) {
            return true;
        }
        final ValueNode bodyIVInit = bodyUsedIV.initNode();
        final ValueNode limitCheckedIVInit = limitCheckedIV.initNode();
        final IntegerStamp bodyIVInitStamp = (IntegerStamp) bodyIVInit.stamp(NodeView.DEFAULT);
        final IntegerStamp limitCheckedIVInitStamp = (IntegerStamp) limitCheckedIVInit.stamp(NodeView.DEFAULT);
        /*
         * If the body is negative the limit one must be as well.
         */
        boolean bodyNegative = bodyIVInitStamp.canBeNegative();
        if (bodyNegative) {
            boolean limitNegative = limitCheckedIVInitStamp.canBeNegative();
            assert limitNegative : "Limit Checked IV must have same signedness as body iv, body=" + bodyUsedIV + " limit=" + limitCheckedIV;
        }
        return true;
    }

    /**
     * Determine if the given inverted loop has code on the loop's inverted backedge that is not in
     * its pure tail position. Consider the following loop
     *
     * <pre>
     * int phi = 0;
     * int limit = 100;
     * if (phi < limit) {
     *     while (true) {
     *         body();
     *         phi++;
     *         if (phi < limit) {
     *             floatingOperationAnchoredOnPrevBegin();
     *             continue;
     *         } else {
     *             break;
     *         }
     *     }
     * }
     * </pre>
     *
     * The {@code floatingOperationAnchoredOnPrevBegin();} is not in its strict tail position. This
     * can be a problem for optimizations that create proxies for different values inside an
     * inverted loop including unrolling, strip mining etc.
     *
     */
    public boolean emptyInvertedCountedBackedge() {
        if (isInverted()) {
            IfNode limitTest = getLimitTest();
            AbstractBeginNode countedExit = getCountedExit();
            AbstractBeginNode backEdge = limitTest.trueSuccessor() == countedExit ? limitTest.falseSuccessor() : limitTest.trueSuccessor();
            if (backEdge.hasAnchored()) {
                // floating nodes anchored on the backedge position
                return false;
            }
            // skip the begin node
            FixedNode backEdgeNode = backEdge.next();
            while (true) { // TERMINATION ARGUMENT: processing fixed set of next nodes until a loop
                           // end node is reached, abort earlier if another node is found
                CompilationAlarm.checkProgress(limitTest.graph());
                if (backEdgeNode instanceof LoopEndNode) {
                    break;
                } else if (backedgeSkippableNode(backEdgeNode)) {
                    backEdgeNode = ((FixedWithNextNode) backEdgeNode).next();
                } else {
                    // fixed nodes between the loop exit condition and the backedge jump that cannot
                    // be safely skipped
                    return false;
                }
            }
        }
        return true;
    }

}
