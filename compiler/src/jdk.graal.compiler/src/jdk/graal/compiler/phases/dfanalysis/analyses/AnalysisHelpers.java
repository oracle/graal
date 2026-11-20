/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.dfanalysis.analyses;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.dfanalysis.AnalysisDomainDefinition;
import jdk.graal.compiler.phases.dfanalysis.DFAMap;
import jdk.graal.compiler.replacements.nodes.arithmetic.BinaryIntegerExactArithmeticSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerNegExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactSplitNode;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.TriState;

final class AnalysisHelpers {
    /**
     * Check if operation results in an overflow given the input stamps.
     *
     * @return {@link TriState#UNKNOWN} if one or both inputs are not constant.
     */
    static TriState isOverflowing(ValueNode op, IntegerStamp xStamp, IntegerStamp yStamp) {
        int bits = xStamp.getBits();
        PrimitiveConstant xConst = (PrimitiveConstant) xStamp.asConstant();
        PrimitiveConstant yConst = (PrimitiveConstant) yStamp.asConstant();
        if (xConst == null || yConst == null) {
            return TriState.UNKNOWN;
        }
        long x = xConst.asLong();
        long y = yConst.asLong();
        try {
            // try the exact operation
            switch (op) {
                case IntegerAddExactOverflowNode ignored -> LoopUtility.addExact(bits, x, y);
                case IntegerSubExactOverflowNode ignored -> LoopUtility.subtractExact(bits, x, y);
                case IntegerMulExactOverflowNode ignored -> LoopUtility.multiplyExact(bits, x, y);
                case IntegerAddExactSplitNode ignored -> LoopUtility.addExact(bits, x, y);
                case IntegerSubExactSplitNode ignored -> LoopUtility.subtractExact(bits, x, y);
                case IntegerMulExactSplitNode ignored -> LoopUtility.multiplyExact(bits, x, y);
                default -> throw GraalError.shouldNotReachHere("unexpected IntegerExactNode " + op.getClass().getSimpleName());
            }
            // if the operation succeeded, we know it does not overflow
            return TriState.FALSE;
        } catch (ArithmeticException ignored) {
            // if the operation failed, we know it does overflow
            return TriState.TRUE;
        }
    }

    static boolean[] calcStampSwitchReachability(SwitchNode switchNode, Stamp value) {
        boolean[] reach = new boolean[switchNode.getSuccessorCount()];
        if (value.isConstant()) {
            /*
             * Exactly one successor edge is reachable; valueConstant is guaranteed non-null,
             * otherwise valueLevel would not be CONSTANT
             */
            Constant valueConstant = value.asConstant();
            boolean foundKey = false;
            int checkLastIdx = Integer.MAX_VALUE;
            for (int i = 0; i < switchNode.keyCount(); i++) {
                if (valueConstant.equals(switchNode.keyAt(i))) {
                    reach[switchNode.keySuccessorIndex(i)] = true;
                    foundKey = true;
                    checkLastIdx = i;
                    break;
                }
            }
            if (!foundKey) {
                reach[switchNode.defaultSuccessorIndex()] = true;
            } else {
                final int captureLastIdx = checkLastIdx;
                assert ((Supplier<Boolean>) () -> {
                    for (int i = captureLastIdx + 1; i < switchNode.keyCount(); i++) {
                        // a key may exist multiple times in the key array, but all
                        // those entries must point to the same successor branch
                        if (valueConstant.equals(switchNode.keyAt(i)) && !reach[switchNode.keySuccessorIndex(i)]) {
                            // we found another reachable successor
                            return false;
                        }
                    }
                    return true;
                }).get() : "we found more than one reachable successor for a constant " + valueConstant + " in " + switchNode;
            }
        } else if (switchNode instanceof IntegerSwitchNode integerSwitch && !((IntegerStamp) value).canBeZero()) {
            // all non 0 successors are reachable
            for (int i = 0; i < switchNode.keyCount(); i++) {
                reach[switchNode.keySuccessorIndex(i)] |= integerSwitch.intKeyAt(i) != 0;
            }
            // default successor is always considered reachable for non 0
            reach[switchNode.defaultSuccessorIndex()] = true;
        } else {
            // all successors are reachable
            Arrays.fill(reach, true);
        }
        return reach;
    }

    /**
     * Computes the reachability of the successors of {@code split} for domains whose elements can
     * be viewed as {@link TriState}s (for logic values) and {@link Stamp}s (for all other values).
     * This is a shared implementation of {@link AnalysisDomainDefinition#splitReachability}.
     *
     * @param toLogic converts the domain element of a condition to a {@link TriState}
     * @param toStamp converts the domain element of a value to a {@link Stamp}, or returns
     *            {@code null} if the element cannot be represented as a stamp. In that case, no
     *            reachability information is computed and all successors are considered reachable.
     * @return the reachability of each successor, or {@code null} if all successors are reachable.
     */
    static <T> boolean[] splitReachability(ControlSplitNode split, DFAMap<T> map, Function<T, TriState> toLogic, Function<T, Stamp> toStamp) {
        switch (split) {
            case IfNode ifNode -> {
                return switch (toLogic.apply(map.getOrUnrestricted(ifNode.condition()))) {
                    case UNKNOWN -> AnalysisDomainDefinition.TRUE_TRUE;
                    case TRUE -> AnalysisDomainDefinition.TRUE_FALSE;
                    case FALSE -> AnalysisDomainDefinition.FALSE_TRUE;
                };
            }
            case SwitchNode switchNode -> {
                Stamp value = toStamp.apply(map.getOrUnrestricted(switchNode.value()));
                return value == null ? null : calcStampSwitchReachability(switchNode, value);
            }
            case BinaryIntegerExactArithmeticSplitNode exactBinary -> {
                Stamp x = toStamp.apply(map.getOrUnrestricted(exactBinary.getX()));
                Stamp y = toStamp.apply(map.getOrUnrestricted(exactBinary.getY()));
                if (!(x instanceof IntegerStamp xStamp) || !(y instanceof IntegerStamp yStamp)) {
                    return null;
                }
                return switch (isOverflowing(exactBinary, xStamp, yStamp)) {
                    case UNKNOWN -> AnalysisDomainDefinition.TRUE_TRUE;
                    case TRUE -> AnalysisDomainDefinition.FALSE_TRUE;
                    case FALSE -> AnalysisDomainDefinition.TRUE_FALSE;
                };
            }
            case IntegerNegExactSplitNode exactNegate -> {
                if (!(toStamp.apply(map.getOrUnrestricted(exactNegate.getValue())) instanceof IntegerStamp valueStamp)) {
                    return null;
                }
                PrimitiveConstant value = (PrimitiveConstant) valueStamp.asConstant();
                return value == null ? AnalysisDomainDefinition.TRUE_TRUE
                                : value.asLong() == CodeUtil.minValue(valueStamp.getBits())
                                                ? AnalysisDomainDefinition.FALSE_TRUE
                                                : AnalysisDomainDefinition.TRUE_FALSE;
            }
            case null ->
                throw GraalError.shouldNotReachHere("can not compute successor reachability for split 'null'");
            default -> {
                return null;
            }
        }
    }

    /**
     * Computes the values that can be inferred for the value of {@code iSwitch} in each of its
     * successors. This is a shared implementation of
     * {@link AnalysisDomainDefinition#calcInferrableValues} for {@link IntegerSwitchNode}s.
     * <p>
     * The inferred value of a successor is the meet of the constant stamps of all keys leading to
     * it. No value is inferred for the default successor, for successors without keys, and for
     * successors whose keys meet to an unrestricted stamp. Since these values only depend on the
     * keys of the switch, they never change during the analysis. Returning {@code null} is in this
     * case equivalent to inferring an unrestricted value but better for framework performance.
     */
    static <T> T[][] switchStampInference(Class<T> type, IntegerSwitchNode iSwitch, Function<IntegerStamp, T> stampToDomainElem) {
        IntegerStamp unevaluated = (IntegerStamp) iSwitch.switchValue().stamp(NodeView.DEFAULT).empty();
        IntegerStamp[] keyStamps = new IntegerStamp[iSwitch.getSuccessorCount()];
        Arrays.fill(keyStamps, unevaluated);
        for (int i = 0; i < iSwitch.keyCount(); i++) {
            int branch = iSwitch.keySuccessorIndex(i);
            keyStamps[branch] = (IntegerStamp) keyStamps[branch].meet(IntegerStamp.createConstant(unevaluated.getBits(), iSwitch.intKeyAt(i)));
        }
        @SuppressWarnings("unchecked")
        T[] inferences = (T[]) Array.newInstance(type, keyStamps.length);
        for (int i = 0; i < keyStamps.length; i++) {
            if (i != iSwitch.defaultSuccessorIndex() && !keyStamps[i].isEmpty() && !keyStamps[i].isUnrestricted()) {
                inferences[i] = stampToDomainElem.apply(keyStamps[i]);
            }
        }
        return AnalysisDomainDefinition.switchInference(type, inferences);
    }

    /**
     * Shared implementation of {@link AnalysisDomainDefinition#calcInferrableValues} for
     * {@link Stamp}-based domains. Each inferred stamp is passed through {@code normalize} to map
     * it into the lattice of the domain.
     */
    static Stamp[][] calcStampInferrableValues(ValueNode node, DFAMap<Stamp> map, UnaryOperator<Stamp> normalize) {
        return switch (node) {
            case IsNullNode nullNode -> {
                AbstractPointerStamp unrest = (AbstractPointerStamp) nullNode.getValue().stamp(NodeView.DEFAULT).unrestricted();
                yield AnalysisDomainDefinition.ifInference(Stamp.class,
                                AnalysisDomainDefinition.unaryLogicInference(Stamp.class, normalize.apply(unrest.asAlwaysNull())),
                                AnalysisDomainDefinition.unaryLogicInference(Stamp.class, normalize.apply(unrest.asNonNull())));
            }
            case CompareNode compareNode -> {
                ValueNode x = compareNode.getX();
                ValueNode y = compareNode.getY();
                Stamp unrestX = x.stamp(NodeView.DEFAULT).unrestricted();
                Stamp unrestY = y.stamp(NodeView.DEFAULT).unrestricted();
                Stamp xStamp = map.getOrUnrestricted(x);
                Stamp yStamp = map.getOrUnrestricted(y);
                // only calculate additional information for X and Y caused by this compare
                Stamp xt = compareNode.getSucceedingStampForX(false, unrestX, yStamp);
                Stamp yt = compareNode.getSucceedingStampForY(false, xStamp, unrestY);
                Stamp xf = compareNode.getSucceedingStampForX(true, unrestX, yStamp);
                Stamp yf = compareNode.getSucceedingStampForY(true, xStamp, unrestY);
                yield AnalysisDomainDefinition.ifInference(Stamp.class,
                                // true branch
                                AnalysisDomainDefinition.binaryLogicInference(Stamp.class,
                                                xt == null ? unrestX : normalize.apply(xt),
                                                yt == null ? unrestY : normalize.apply(yt)),
                                // false branch
                                AnalysisDomainDefinition.binaryLogicInference(Stamp.class,
                                                xf == null ? unrestX : normalize.apply(xf),
                                                yf == null ? unrestY : normalize.apply(yf)));
            }
            case IntegerSwitchNode iSwitch -> switchStampInference(Stamp.class, iSwitch, normalize::apply);
            default -> null;
        };
    }

    static LogicValueStamp stampForBoolean(boolean value) {
        return value ? LogicValueStamp.TRUE : LogicValueStamp.FALSE;
    }

    /**
     * Converts {@code state} to a {@link LogicValueStamp}, mapping {@code null} to
     * {@link LogicValueStamp#EMPTY}.
     */
    static LogicValueStamp stampForTriState(TriState state) {
        if (state == null) {
            return LogicValueStamp.EMPTY;
        }
        return switch (state) {
            case TRUE -> LogicValueStamp.TRUE;
            case FALSE -> LogicValueStamp.FALSE;
            case UNKNOWN -> LogicValueStamp.UNRESTRICTED;
        };
    }

    /**
     * Converts {@code stamp} to a {@link TriState}, mapping {@link LogicValueStamp#EMPTY} to
     * {@code null}.
     */
    static TriState stateForLogicStamp(LogicValueStamp stamp) {
        if (stamp.isEmpty()) {
            return null;
        } else if (stamp.equals(LogicValueStamp.TRUE)) {
            return TriState.TRUE;
        } else if (stamp.equals(LogicValueStamp.FALSE)) {
            return TriState.FALSE;
        } else {
            GraalError.guarantee(stamp.equals(LogicValueStamp.UNRESTRICTED), "unexpected LogicValueStamp %s", stamp);
            return TriState.UNKNOWN;
        }
    }
}
