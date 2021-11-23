/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.loop;

import static java.lang.Math.abs;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.add;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.sub;
import static org.graalvm.compiler.nodes.loop.MathUtil.unsignedDivBefore;

import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.util.UnsignedLong;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.loop.InductionVariable.Direction;
import org.graalvm.compiler.nodes.util.IntegerHelper;
import org.graalvm.compiler.nodes.util.SignedIntegerHelper;
import org.graalvm.compiler.nodes.util.UnsignedIntegerHelper;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
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
 * for (int i = 0; i < end; i++) {
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
 * } while(i < end)
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

    protected final LoopEx loop;
    protected InductionVariable limitCheckedIV;
    protected ValueNode end;
    /**
     * {@code true} iff the limit is included in the limit test, e.g., the limit test is
     * {@code i <= n} rather than {@code i < n}.
     */
    protected boolean isLimitIncluded;
    protected AbstractBeginNode body;
    protected IfNode ifNode;
    protected final boolean unsigned;

    protected CountedLoopInfo(LoopEx loop, InductionVariable limitCheckedIV, IfNode ifNode, ValueNode end, boolean isLimitIncluded, AbstractBeginNode body, boolean unsigned) {
        assert limitCheckedIV.direction() != null;
        this.loop = loop;
        this.limitCheckedIV = limitCheckedIV;
        this.end = end;
        this.isLimitIncluded = isLimitIncluded;
        this.body = body;
        this.ifNode = ifNode;
        this.unsigned = unsigned;
    }

    /**
     * @return the {@link InductionVariable} compared ({@link CompareNode}) to
     *         {@link CountedLoopInfo#getLimit()}. If this loop is
     *         {@link CountedLoopInfo#isInverted()} returns to next iteration iv based on
     *         {@link CountedLoopInfo#getBodyIV()}.
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
        assert !isInverted() && getLimitCheckedIV() == limitCheckedIV : "Only inverted loops must have different body ivs.";
        return limitCheckedIV;
    }

    /**
     * Returns the limit node of this counted loop.
     *
     * @return the {@link ValueNode} that is compared ({@link CompareNode}) to the
     *         {@link InductionVariable} return by {@link CountedLoopInfo#getLimitCheckedIV()}
     */
    public ValueNode getLimit() {
        return end;
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
     * } while (i < 100);
     * </pre>
     *
     * This loop performs 100 iterations. However, the following loop
     *
     * <pre>
     * int i = 0;
     * do {
     * } while (i++ < 100);
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
        assert !isInverted() && getLimit() == end : "Only inverted loops must have a different trip count limit";
        return end;
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
        return maxTripCountNode(false);
    }

    public boolean isUnsignedCheck() {
        return this.unsigned;
    }

    public ValueNode maxTripCountNode(boolean assumeLoopEntered) {
        return maxTripCountNode(assumeLoopEntered, getCounterIntegerHelper());
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
     * @param assumeLoopEntered if true the check that the loop is entered at all will be omitted.
     */
    protected ValueNode maxTripCountNode(boolean assumeLoopEntered, IntegerHelper integerHelper) {
        StructuredGraph graph = getBodyIV().valueNode().graph();
        Stamp stamp = getBodyIV().valueNode().stamp(NodeView.DEFAULT);

        ValueNode max;
        ValueNode min;
        ValueNode absStride;
        if (getBodyIV().direction() == Direction.Up) {
            absStride = getBodyIV().strideNode();
            max = getTripCountLimit();
            min = getBodyIV().initNode();
        } else {
            assert getBodyIV().direction() == Direction.Down;
            absStride = NegateNode.create(getBodyIV().strideNode(), NodeView.DEFAULT);
            max = getBodyIV().initNode();
            min = getTripCountLimit();
        }
        ValueNode range = sub(max, min);

        ConstantNode one = ConstantNode.forIntegerStamp(stamp, 1, graph);
        if (isLimitIncluded) {
            range = add(range, one);
        }
        // round-away-from-zero divison: (range + stride -/+ 1) / stride
        ValueNode denominator = add(graph, range, sub(absStride, one), NodeView.DEFAULT);
        ValueNode div = unsignedDivBefore(graph, loop.entryPoint(), denominator, absStride, null);

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
        Block loopBlock = cfg.blockFor(loop.loopBegin());
        for (Node checkUsage : noEntryCheck.usages()) {
            if (checkUsage instanceof IfNode) {
                IfNode ifCheck = (IfNode) checkUsage;
                if (cfg.getNodeToBlock().isNew(ifCheck.falseSuccessor())) {
                    continue;
                }
                if (AbstractControlFlowGraph.dominates(cfg.blockFor(ifCheck.falseSuccessor()), loopBlock)) {
                    return graph.addOrUniqueWithInputs(PiNode.create(div, positiveIntStamp.improveWith(div.stamp(NodeView.DEFAULT)), ifCheck.falseSuccessor()));
                }
            }
        }
        return null;
    }

    /**
     * Determine if the loop might be entered. Returns {@code false} if we can tell statically that
     * the loop cannot be entered; returns {@code true} if the loop might possibly be entered,
     * including in the case where we cannot be sure statically.
     *
     * @return false if the loop can definitely not be entered, true otherwise
     */
    public boolean loopMightBeEntered() {
        Stamp stamp = getBodyIV().valueNode().stamp(NodeView.DEFAULT);

        ValueNode max;
        ValueNode min;
        if (getBodyIV().direction() == Direction.Up) {
            max = getTripCountLimit();
            min = getBodyIV().initNode();
        } else {
            assert getBodyIV().direction() == Direction.Down;
            max = getBodyIV().initNode();
            min = getTripCountLimit();
        }
        if (isLimitIncluded) {
            // Ensure the constant is value numbered in the graph. Don't add other nodes to the
            // graph, they will be dead code.
            StructuredGraph graph = getBodyIV().valueNode().graph();
            max = add(max, ConstantNode.forIntegerStamp(stamp, 1, graph), NodeView.DEFAULT);
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

    public IfNode getCountCheck() {
        return ifNode;
    }

    /**
     * @return true if the loop has constant bounds.
     */
    public boolean isConstantMaxTripCount() {
        return getTripCountLimit() instanceof ConstantNode && getBodyIV().isConstantInit() && getBodyIV().isConstantStride();
    }

    public UnsignedLong constantMaxTripCount() {
        assert isConstantMaxTripCount();
        return new UnsignedLong(rawConstantMaxTripCount());
    }

    /**
     * Compute the raw value of the trip count for this loop. THIS IS AN UNSIGNED VALUE;
     */
    private long rawConstantMaxTripCount() {
        assert getBodyIV().direction() != null;
        long endValue = getTripCountLimit().asJavaConstant().asLong();
        long initValue = getBodyIV().constantInit();
        long range;
        long absStride;
        IntegerHelper helper = getCounterIntegerHelper(64);
        if (getBodyIV().direction() == Direction.Up) {
            if (helper.compare(endValue, initValue) < 0) {
                return 0;
            }
            range = endValue - getBodyIV().constantInit();
            absStride = getBodyIV().constantStride();
        } else {
            assert getBodyIV().direction() == Direction.Down;
            if (helper.compare(initValue, endValue) < 0) {
                return 0;
            }
            range = getBodyIV().constantInit() - endValue;
            absStride = -getBodyIV().constantStride();
        }
        if (isLimitIncluded) {
            range += 1;
        }
        long denominator = range + absStride - 1;
        return Long.divideUnsigned(denominator, absStride);
    }

    public IntegerHelper getCounterIntegerHelper() {
        IntegerStamp stamp = (IntegerStamp) getBodyIV().valueNode().stamp(NodeView.DEFAULT);
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
        return loop.loop().getNaturalExits().size() == 1;
    }

    public ValueNode exactTripCountNode() {
        assert isExactTripCount();
        return maxTripCountNode();
    }

    public boolean isConstantExactTripCount() {
        assert isExactTripCount();
        return isConstantMaxTripCount();
    }

    public UnsignedLong constantExactTripCount() {
        assert isExactTripCount();
        return constantMaxTripCount();
    }

    @Override
    public String toString() {
        return "iv=" + getLimitCheckedIV() + " until " + getTripCountLimit() + (isLimitIncluded ? getBodyIV().direction() == Direction.Up ? "+1" : "-1" : "") + " bodyIV=" + getBodyIV();
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

    public AbstractBeginNode getCountedExit() {
        if (getLimitTest().trueSuccessor() == getBody()) {
            return getLimitTest().falseSuccessor();
        } else {
            assert getLimitTest().falseSuccessor() == getBody();
            return getLimitTest().trueSuccessor();
        }
    }

    public Direction getDirection() {
        return getLimitCheckedIV().direction();
    }

    public GuardingNode getOverFlowGuard() {
        return loop.loopBegin().getOverflowGuard();
    }

    public boolean counterNeverOverflows() {
        if (loop.loopBegin().canNeverOverflow()) {
            return true;
        }
        if (!isLimitIncluded && getBodyIV().isConstantStride() && abs(getBodyIV().constantStride()) == 1) {
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
        ValueNode strideNode = getBodyIV().strideNode();
        IntegerStamp strideStamp = (IntegerStamp) strideNode.stamp(NodeView.DEFAULT);
        IntegerHelper integerHelper = getCounterIntegerHelper();
        if (getDirection() == Direction.Up) {
            long max = integerHelper.maxValue();
            return integerHelper.compare(endStamp.upperBound(), max - (strideStamp.upperBound() - 1) - (isLimitIncluded ? 1 : 0)) <= 0;
        } else if (getDirection() == Direction.Down) {
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
            IntegerStamp stamp = (IntegerStamp) getBodyIV().valueNode().stamp(NodeView.DEFAULT);
            IntegerHelper integerHelper = getCounterIntegerHelper();
            StructuredGraph graph = getBodyIV().valueNode().graph();
            LogicNode cond; // we use a negated guard with a < condition to achieve a >=
            ConstantNode one = ConstantNode.forIntegerStamp(stamp, 1, graph);
            if (getBodyIV().direction() == Direction.Up) {
                ValueNode v1 = sub(ConstantNode.forIntegerStamp(stamp, integerHelper.maxValue()), sub(getBodyIV().strideNode(), one));
                if (isLimitIncluded) {
                    v1 = sub(v1, one);
                }
                cond = graph.addOrUniqueWithInputs(integerHelper.createCompareNode(v1, getTripCountLimit(), NodeView.DEFAULT));
            } else {
                assert getBodyIV().direction() == Direction.Down;
                ValueNode v1 = add(ConstantNode.forIntegerStamp(stamp, integerHelper.minValue()), sub(one, getBodyIV().strideNode()));
                if (isLimitIncluded) {
                    v1 = add(v1, one);
                }
                cond = graph.addOrUniqueWithInputs(integerHelper.createCompareNode(getTripCountLimit(), v1, NodeView.DEFAULT));
            }
            assert graph.getGuardsStage().allowsFloatingGuards();

            SpeculationLog speculationLog = graph.getSpeculationLog();
            SpeculationLog.Speculation speculation = SpeculationLog.NO_SPECULATION;
            if (speculationLog != null) {
                SpeculationLog.SpeculationReason speculationReason = LoopBeginNode.LOOP_OVERFLOW_DEOPT.createSpeculationReason(graph.method(), getBodyIV().loop.loopBegin().stateAfter().bci);
                if (speculationLog.maySpeculate(speculationReason)) {
                    speculation = speculationLog.speculate(speculationReason);
                    LoopBeginNode.overflowSpeculationTaken.increment(graph.getDebug());
                } else {
                    GraalError.shouldNotReachHere("Must not create overflow guard for loop " + loop.loopBegin() + " where the speculation guard already failed, this can create deopt loops");
                }
            }

            overflowGuard = graph.unique(new GuardNode(cond, AbstractBeginNode.prevBegin(loop.entryPoint()), DeoptimizationReason.LoopLimitCheck, DeoptimizationAction.InvalidateRecompile, true,
                            speculation, null));
            loop.loopBegin().setOverflowGuard(overflowGuard);
            return overflowGuard;
        }
    }

    public IntegerStamp getStamp() {
        return (IntegerStamp) getBodyIV().valueNode().stamp(NodeView.DEFAULT);
    }

    public boolean isInverted() {
        return false;
    }
}
