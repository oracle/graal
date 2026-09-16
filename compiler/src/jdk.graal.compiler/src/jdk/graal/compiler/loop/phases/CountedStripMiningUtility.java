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

import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.add;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FixedBinaryNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnsignedDivNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DerivedConvertedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.phases.common.util.LoopUtility;

/**
 * Collection of utility methods for {@link CountedStripMiningPhase} and
 * {@code EnterpriseRangeCheckEliminationPhase}.
 */
public class CountedStripMiningUtility {

    /**
     * Based on the stride of the {@link CountedLoopInfo#getLimitCheckedIV()} and an arbitrary
     * stride determine if the new stride iv could overflow integer range based on the given trip
     * count of the limit checked iv.
     *
     * Note that this only works for up counted loops, i.e., {@code init <= limit}.
     */
    public static boolean ivCanOverflow32Bit(long init, long limit, long limitCheckedIVStride, long ivStride) {
        try {
            // Calculate the distance from the start value limit of the loop condition.
            final long distance = LoopUtility.subtractExact(32, NumUtil.safeToInt(limit), NumUtil.safeToInt(init));
            // Divide by the stride if the stride > 1 we do not make distance steps but less steps.
            final long tripCount = divExact(32, distance, NumUtil.safeAbs(limitCheckedIVStride, 32));
            // With the steps==tripCount calculate the possible values of the iv in question.
            LoopUtility.multiplyExact(32, tripCount, ivStride);
            return false;
        } catch (ArithmeticException e) {
            return true;
        }
    }

    /**
     * For details about the implementation see {@link Math#divideExact(int, int)} and
     * {@link Math#divideExact(long, long)} .
     */
    private static long divExact(int bits, long xL, long yL) {
        if (bits == 32) {
            int x = NumUtil.safeToInt(xL);
            int y = NumUtil.safeToInt(yL);
            int q = x / y;
            if ((x & y & q) >= 0) {
                return q;
            }
            throw new ArithmeticException("integer overflow");
        } else if (bits == 64) {
            final long x = xL;
            final long y = yL;
            long q = x / y;
            if ((x & y & q) >= 0) {
                return q;
            }
            throw new ArithmeticException("long overflow");
        } else {
            throw GraalError.shouldNotReachHere("Only int and long supported but found " + bits);
        }
    }

    /**
     * Determine if the {@code other} appears in any of the
     * {@link DerivedInductionVariable#getBase()} induction variables reachable via {@code self} (or
     * if it {@code == self}).
     */
    public static boolean ivContains(InductionVariable self, InductionVariable other) {
        InductionVariable cur = self;
        while (true) { // TERMINATION ARGUMENT: following iv base chain until a specific one is
                       // found or we abort
            CompilationAlarm.checkProgress(self.graph());
            if (cur == other) {
                return true;
            }
            if (cur instanceof DerivedInductionVariable) {
                cur = ((DerivedInductionVariable) cur).getBase();
            } else {
                break;
            }
        }
        return false;
    }

    /**
     * Create a 32-bit basic induction variable on {@code loopBegin} with {@code stride} and
     * {@code start}.
     */
    public static ValuePhiNode createNewIntBaseIV(StructuredGraph graph, LoopBeginNode loopBegin, int start, int stride, int stripMax) {
        ValuePhiNode new32BitPhi = graph.addWithoutUnique(new ValuePhiNode(IntegerStamp.create(32), loopBegin));
        new32BitPhi.addInput(ConstantNode.forInt(start, graph));
        new32BitPhi.addInput(graph.addOrUnique(add(new32BitPhi, ConstantNode.forIntegerBits(32, stride, graph))));
        long lowerBound = 0;
        long upperBound = 0;
        assert stripMax > 0 : Assertions.errorMessage("Strip max must always be positive", loopBegin, start, stride, stripMax);

        /*
         * For setting the bounds its important that stripMax is always positive. We just set the
         * boundary values that is the start until stripMax * stride. If stride is negative already
         * we just multiple it with strip max and the result will be negative.
         */
        if (stride > 0) {
            lowerBound = start;
            upperBound = stripMax * stride;
            assert upperBound > 0 : Assertions.errorMessage("Must be postitive and must not overflow", loopBegin, start, stride, stripMax, lowerBound, upperBound);
        } else if (stride < 0) {
            lowerBound = stripMax * stride;
            upperBound = start;
            assert lowerBound < 0 : Assertions.errorMessage("Must be negative and must not underflow", loopBegin, start, stride, stripMax, lowerBound, lowerBound);
        } else {
            throw GraalError.shouldNotReachHere("Stride must be !=0 ");
        }
        IntegerStamp newIVStamp = IntegerStamp.create(32, lowerBound, upperBound);
        new32BitPhi.inferStamp();
        new32BitPhi.refineStampWith(newIVStamp);
        return new32BitPhi;
    }

    /**
     * @return the input of a {@link SignExtendNode} if {@code v} is a sign extension, else return v
     *         itself
     */
    public static ValueNode selfOrSignExtendInput(ValueNode v, boolean mustBeInt) {
        if (v instanceof SignExtendNode) {
            SignExtendNode se = (SignExtendNode) v;
            assert se.getResultBits() == 64 : se + " " + se.getInputBits() + "->" + se.getResultBits();
            return se.getValue();
        }
        IntegerStamp integerStamp = (IntegerStamp) v.stamp(NodeView.DEFAULT);
        assert !mustBeInt || integerStamp.getBits() == 32 : "Must be int or caller must specify mustBeInt=false " + integerStamp;
        return v;
    }

    public static InductionVariable unwrap32To64Extension(InductionVariable iv) {
        InductionVariable cur = iv;
        while (cur instanceof DerivedConvertedInductionVariable) {
            DerivedConvertedInductionVariable dcIv = (DerivedConvertedInductionVariable) cur;
            ValueNode v = dcIv.valueNode();
            if (v instanceof SignExtendNode) {
                SignExtendNode se = (SignExtendNode) v;
                if (se.getInputBits() == 32 && se.getResultBits() == 64) {
                    cur = dcIv.getBase();
                    continue;
                }
            } else if (v instanceof ZeroExtendNode) {
                ZeroExtendNode ze = (ZeroExtendNode) v;
                if (ze.getInputBits() == 32 && ze.getResultBits() == 64) {
                    cur = dcIv.getBase();
                    continue;
                }
            }
        }
        return cur;
    }

    /**
     * This function resembles code generation for a clamp function as outlined in pseudo code
     * below:
     *
     * <pre>
     * static long clamp(long r, long l, long h) {
     *     return Math.max(l, Math.min(r, h));
     * }
     * </pre>
     *
     * where <a href="https://en.wikipedia.org/wiki/Clamping_(graphics)">clamp<a/> describes a clamp
     * to bounds operation. The values l and h mark the inclusive interval and r is the value to be
     * clamped, i.e., restricted to that interval.
     */
    public static ValueNode clamp(ValueNode value, ValueNode low, ValueNode high, StructuredGraph graph) {
        ValueNode minRes = min(value, high, graph);
        ValueNode maxRes = max(low, minRes, graph);
        return maxRes;
    }

    /**
     * This function resembles code generation for a max function as outlined in the pseudo code
     * below:
     *
     * <pre>
     * static long max(long a, long b) {
     *     return b < a ? a : b;
     * }
     * </pre>
     */
    public static ValueNode max(ValueNode a, ValueNode b, StructuredGraph graph) {
        LogicNode l = graph.addWithoutUnique(CompareNode.createCompareNode(CanonicalCondition.LT, b, a, null, NodeView.DEFAULT));
        return graph.addWithoutUnique(new ConditionalNode(l, a, b));
    }

    /**
     * This function resembles code generation for a min function as outlined in the pseudo code
     * below:
     *
     * <pre>
     * static long min(long a, long b) {
     *     return a < b ? a : b;
     * }
     * </pre>
     */
    public static ValueNode min(ValueNode a, ValueNode b, StructuredGraph graph) {
        LogicNode l = graph.addWithoutUniqueWithInputs(CompareNode.createCompareNode(CanonicalCondition.LT, a, b, null, NodeView.DEFAULT));
        return graph.addWithoutUniqueWithInputs(new ConditionalNode(l, a, b));
    }

    /**
     * Like {@link #min(ValueNode, ValueNode, StructuredGraph)} but unsigned.
     */
    public static ValueNode uMin(ValueNode a, ValueNode b, StructuredGraph graph) {
        assert a.isAlive() : "Node " + a + " must be alive";
        assert b.isAlive() : "Node " + b + " must be alive";
        LogicNode l = graph.addWithoutUnique(new IntegerBelowNode(a, b));
        return graph.addWithoutUnique(new ConditionalNode(l, a, b));
    }

    /**
     * Narrow the input node to 32 bits.
     */
    public static ValueNode toInt(ValueNode input, StructuredGraph graph) {
        assert input.stamp(NodeView.DEFAULT) instanceof IntegerStamp && ((IntegerStamp) input.stamp(NodeView.DEFAULT)).getBits() == 64 : "Must be long stamp" + input;
        return graph.addWithoutUnique(new NarrowNode(input, 32));
    }

    /**
     * Convert the input from a 32bit integer to a 64bit integer with sign extension or return the
     * input if it already has a long stamp.
     */
    public static ValueNode toLongOrSelf(ValueNode input, StructuredGraph graph) {
        Stamp stamp = input.stamp(NodeView.DEFAULT);
        assert stamp instanceof IntegerStamp : stamp;
        if (LoopUtility.isInt(input)) {
            return toLong(input, graph);
        }
        return input;
    }

    public static ValueNode toLong(ValueNode input, StructuredGraph graph) {
        assert input.stamp(NodeView.DEFAULT) instanceof IntegerStamp && ((IntegerStamp) input.stamp(NodeView.DEFAULT)).getBits() == 32 : "Must be int stamp" + input;
        IntegerConvertNode<?> convert = new SignExtendNode(input, 64);
        return graph.addWithoutUnique(convert);
    }

    /**
     * Like {@link CountedLoopInfo#maxTripCountNode()} except considers a {@code currentTripCount}
     * node instead of the init as an argument.
     *
     * Additionally, this method does not use (and MUST NOT) methods to create and add nodes with
     * global value numbering. This is done to ensure we have a clear before-after pattern for nodes
     * to repair post dominating values in inverted strip mining.
     */
    public static ValueNode remainingTripCountNode(CountedLoopInfo countedLoop, ValueNode currentTripCount) {
        Loop loop = countedLoop.getLimitCheckedIV().getLoop();
        LoopBeginNode lb = loop.loopBegin();
        StructuredGraph graph = lb.graph();
        Stamp stamp = countedLoop.getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT);

        ValueNode max;
        ValueNode min;
        ValueNode absStride;
        Direction direction = countedLoop.getLimitCheckedIV().direction();
        if (direction == Direction.Up) {
            absStride = countedLoop.getLimitCheckedIV().strideNode();
            max = countedLoop.getTripCountLimit();
            min = currentTripCount;
        } else {
            assert direction == Direction.Down : direction;
            absStride = graph.addWithoutUnique(new NegateNode(countedLoop.getLimitCheckedIV().strideNode()));
            max = currentTripCount;
            min = countedLoop.getTripCountLimit();
        }
        ValueNode range = graph.addWithoutUnique(new SubNode(max, min));

        ConstantNode one = ConstantNode.forIntegerStamp(stamp, 1, graph);
        if (countedLoop.isLimitIncluded()) {
            range = graph.addWithoutUnique(new AddNode(range, one));
        }
        // round-away-from-zero divison: (range + stride -/+ 1) / stride
        ValueNode denominator = graph.addWithoutUnique(new AddNode(range, graph.addWithoutUnique(new SubNode(absStride, one))));
        ValueNode div = fixedDivBefore(graph, loop.entryPoint(), denominator, absStride);
        return div;
    }

    private static ValueNode fixedDivBefore(StructuredGraph graph, FixedNode before, ValueNode dividend, ValueNode divisor) {
        if (isConstantOne(divisor)) {
            return dividend;
        }
        FixedBinaryNode div = new UnsignedDivNode(dividend, divisor, null);
        graph.addBeforeFixed(before, graph.add(div));
        return div;
    }

    private static boolean isConstantOne(ValueNode v1) {
        return v1.isConstant() && v1.stamp(NodeView.DEFAULT) instanceof IntegerStamp && v1.asJavaConstant().asLong() == 1;
    }

    /**
     * This class marks the outer-loop phi that reconstructs the original
     * limit-checked IV after counted strip mining, and it is used to
     * distinguish the original IV from unrelated IVs in the same loop that
     * happen to share the inner-strip loop counter.
     * <p>
     * For a loop like:
     * <pre>
     * int foo = bar;
     * for (int i = 0; i < count; ++i, ++foo) { ... }
     * </pre>
     * After counted strip mining, it looks like:
     * <pre>
     * int outerFoo = bar;
     * int outerI = 0;
     * while (outerI < count) {
     *     int innerTrips = min(STRIP_SIZE, count - outerI);
     *     for (int innerCounter = 0; innerCounter < innerTrips; innerCounter++) {
     *         int i   = innerCounter + outerI;
     *         int foo = innerCounter + outerFoo;
     *     }
     *     outerI   += innerTrips;
     *     outerFoo += innerTrips;
     * }
     * </pre>
     * {@code outerI} is an {@code OriginalLimitCheckedIVPhi} to be able to distinguish {@code i}
     * from the unrelated {@code foo}.
     */
    @NodeInfo
    private static final class OriginalLimitCheckedIVPhi extends ValuePhiNode {
        static final NodeClass<OriginalLimitCheckedIVPhi> TYPE = NodeClass.create(OriginalLimitCheckedIVPhi.class);

        protected OriginalLimitCheckedIVPhi(Stamp stamp, AbstractMergeNode merge) {
            super(TYPE, stamp, merge);
        }

        protected OriginalLimitCheckedIVPhi(Stamp stamp, AbstractMergeNode merge, ValueNode... values) {
            super(TYPE, stamp, merge, values);
        }

        @Override
        public ValuePhiNode duplicateOn(AbstractMergeNode newMerge) {
            return graph().addWithoutUnique(new OriginalLimitCheckedIVPhi(stamp(NodeView.DEFAULT), newMerge));
        }

        @Override
        public ValuePhiNode duplicateWithValues(AbstractMergeNode newMerge, ValueNode... newValues) {
            return new OriginalLimitCheckedIVPhi(stamp(NodeView.DEFAULT), newMerge, newValues);
        }
    }

    static ValuePhiNode createOriginalLimitCheckedIVPhi(Stamp stamp, AbstractMergeNode merge) {
        return new OriginalLimitCheckedIVPhi(stamp, merge);
    }

    /**
     * Determines whether {@code value} is the outer-loop phi that reconstructs the original
     * limit-checked IV for {@code loop} inside its strip mined inner loop.
     *
     * @param value the phi node
     * @param loop the outer strip-mined loop
     * @return whether {@code value} reconstructs the original limit-checked IV of {@code loop}
     */
    public static boolean isOriginalLimitCheckedIV(ValueNode value, Loop loop) {
        return loop.loopBegin().isCountedStripMinedOuter() && value instanceof OriginalLimitCheckedIVPhi phi && phi.merge() == loop.loopBegin();
    }
}
