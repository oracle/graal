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
package jdk.graal.compiler.lir.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.lir.SwitchStrategy;

import jdk.vm.ci.meta.JavaConstant;

public class SwitchStrategyTest {

    /// Checks dispatch at every key and its neighbors, as well as at the integer limits.
    private static void assertRangeDispatch(int[] keys, int[] targets) {
        JavaConstant[] constants = new JavaConstant[keys.length];
        double[] probabilities = new double[keys.length];
        Arrays.fill(probabilities, 0.8 / keys.length);
        for (int i = 0; i < keys.length; i++) {
            constants[i] = JavaConstant.forInt(keys[i]);
        }
        SwitchStrategy strategy = new SwitchStrategy.RangesStrategy(probabilities, constants);
        assertDispatch(strategy, keys, targets, Integer.MIN_VALUE);
        assertDispatch(strategy, keys, targets, Integer.MAX_VALUE);
        for (int key : keys) {
            assertDispatch(strategy, keys, targets, key);
            if (key != Integer.MIN_VALUE) {
                assertDispatch(strategy, keys, targets, key - 1);
            }
            if (key != Integer.MAX_VALUE) {
                assertDispatch(strategy, keys, targets, key + 1);
            }
        }
    }

    /// Interprets comparisons directly so that keys can share targets independently of LIR labels.
    private static void assertDispatch(SwitchStrategy strategy, int[] keys, int[] targets, int value) {
        int keyIndex = Arrays.binarySearch(keys, value);
        int expected = keyIndex < 0 ? -1 : targets[keyIndex];
        // -2 means no jump was taken; -1 denotes the default target.
        int[] actual = {-2};
        strategy.run(new SwitchStrategy.BaseSwitchClosure(null, null, null, null) {
            @Override
            public void conditionalJump(int index, Condition condition, boolean defaultTarget) {
                if (actual[0] == -2 && (condition == null || condition.check(value, keys[index]))) {
                    actual[0] = defaultTarget ? -1 : targets[index];
                }
            }

            @Override
            public void conditionalJumpOrDefault(int index, Condition condition, boolean canFallThrough) {
                if (actual[0] == -2) {
                    actual[0] = condition.check(value, keys[index]) ? targets[index] : -1;
                }
            }

            @Override
            protected void conditionalJump(int index, Condition condition, Label target) {
                throw new AssertionError("RangesStrategy does not use internal labels");
            }

            @Override
            public boolean isSameTarget(int index1, int index2) {
                return targets[index1] == targets[index2];
            }
        });
        assertEquals("dispatch for " + value + " with keys " + Arrays.toString(keys), expected, actual[0]);
    }

    /// Covers a final shared range after single keys whose failed equality checks leave a gap.
    @Test
    public void finalRangeAfterSingletons() {
        assertRangeDispatch(new int[]{Integer.MIN_VALUE, -3, -2, -1}, new int[]{0, 0, 1, 1});
    }

    /// Covers the same missing lower bound when the shared range is followed by another key.
    @Test
    public void intermediateRangeAfterSingletons() {
        assertRangeDispatch(new int[]{Integer.MIN_VALUE, -3, -2, -1, 5}, new int[]{0, 0, 1, 1, 2});
    }

    /// Covers shared ranges at the start, across gaps, and next to other shared ranges.
    @Test
    public void rangeControls() {
        assertRangeDispatch(new int[]{-3, -2, -1}, new int[]{0, 0, 0});
        assertRangeDispatch(new int[]{-3, -2, -1, 0}, new int[]{0, 0, 1, 1});
        assertRangeDispatch(new int[]{-3, -2, 1, 2}, new int[]{0, 0, 1, 1});
        assertRangeDispatch(new int[]{-3, -2, 1}, new int[]{0, 0, 1});
        assertRangeDispatch(new int[]{-3, -2, -1}, new int[]{0, 1, 2});
        assertRangeDispatch(new int[]{Integer.MAX_VALUE - 1, Integer.MAX_VALUE}, new int[]{0, 0});
    }
}
