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

import java.util.Arrays;
import java.util.function.Supplier;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerMulExactSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactSplitNode;
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
}
