/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.test.GraalTest;
import org.junit.Test;

import jdk.vm.ci.code.CodeUtil;

/**
 * This class tests that integer stamps are created correctly for constants.
 */
public class IntegerStampMaskTest extends GraalTest {

    static final long[] intBoundaryValues = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
    static final long[] longBoundaryValues = {Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1, Long.MAX_VALUE};

    private static final int VALUE_LIMIT = 8;

    public IntegerStampMaskTest() {
    }

    @Test
    public void test() {
        for (long bound1 = -VALUE_LIMIT; bound1 <= VALUE_LIMIT; bound1++) {
            for (long bound2 = bound1; bound2 <= VALUE_LIMIT; bound2++) {
                for (long a = -VALUE_LIMIT; a <= VALUE_LIMIT; a++) {
                    for (long b = a + 1; b <= VALUE_LIMIT; b++) {
                        testValues(32, a, b, bound1, bound2);
                        testValues(64, a, b, bound1, bound2);
                    }
                }

                for (long a = 0; a < 32; a++) {
                    for (long b = a + 1; b < 32; b++) {
                        testValues(32, CodeUtil.signExtend(1L << a, 32), CodeUtil.signExtend(1L << b, 32), bound1, bound2);
                        testValues(64, 1L << a, 1L << b, bound1, bound2);
                    }
                }

                for (long a = 32; a < 64; a++) {
                    for (long b = a + 1; b < 64; b++) {
                        testValues(64, 1L << a, 1L << b, bound1, bound2);
                    }
                }
            }
        }
    }

    public static void testValues(int bits, long a, long b, long bound1, long bound2) {
        try {
            IntegerStamp constantA = IntegerStamp.create(bits, a, a);
            IntegerStamp constantB = IntegerStamp.create(bits, b, b);
            IntegerStamp meetOfAB = (IntegerStamp) constantA.meet(constantB);
            assert bound1 <= bound2;
            IntegerStamp bounds = IntegerStamp.create(bits, bound1, bound2);
            IntegerStamp joined = bounds.join(meetOfAB);
            boolean expectEmpty = !bounds.contains(a) && !bounds.contains(b);
            testStamp(joined, meetOfAB, bounds, expectEmpty);
        } catch (Throwable t) {
            throw new GraalError(t, "failed testValues(%d, %d, %d, %d, %d)", bits, a, b, bound1, bound2);
        }
    }

    public static void testStamp(IntegerStamp joined, IntegerStamp meetOfAB, IntegerStamp bounds, boolean expectEmpty) {
        checkBounds(joined, meetOfAB, bounds, expectEmpty);

        for (long v : intBoundaryValues) {
            testValue(v, joined, meetOfAB, bounds);
        }
        if (joined.getBits() == 64) {
            for (long v : longBoundaryValues) {
                testValue(v, joined, meetOfAB, bounds);
            }
        }
        for (long a = -(2 * VALUE_LIMIT); a <= 2 * VALUE_LIMIT; a++) {
            testValue(a, joined, meetOfAB, bounds);
        }
    }

    private static void testValue(long v, IntegerStamp joined, IntegerStamp meetOfAB, IntegerStamp bounds) {
        if (bounds.contains(v) && meetOfAB.contains(v)) {
            assertTrue(joined.contains(v), "%s does not contain %s", joined, v);
        }
        if (!bounds.contains(v) && !meetOfAB.contains(v)) {
            assertTrue(!joined.contains(v), "%s contains %s but %s and %s do not", joined, v, bounds, meetOfAB);
        }
    }

    private static void checkBounds(IntegerStamp stamp, IntegerStamp stampA, IntegerStamp stampB, boolean expectEmpty) {
        if (stamp.isEmpty()) {
            assertTrue("unexpected empty stamp encountered", expectEmpty);
            return;
        }
        long maxIterations = 100000;
        long actualUpperBound = stamp.lowerBound();
        Boolean upperExact = null;
        long actualLowerBound = stamp.upperBound();
        Boolean lowerExact = null;
        for (long i = stamp.lowerBound(), iteration = 0; i <= stamp.upperBound(); i++, iteration++) {
            if (stamp.contains(i)) {
                actualLowerBound = i;
                lowerExact = true;
                break;
            }
            if (iteration >= maxIterations) {
                actualLowerBound = i;
                lowerExact = false;
                break;
            }
        }

        for (long i = stamp.upperBound(), iteration = 0; i >= stamp.lowerBound(); i--, iteration++) {
            if (stamp.contains(i)) {
                actualUpperBound = i;
                upperExact = true;
                break;
            }
            if (iteration >= maxIterations) {
                actualUpperBound = i;
                upperExact = false;
                break;
            }
        }
        if (actualLowerBound == stamp.lowerBound() && actualUpperBound == stamp.upperBound()) {
            return;
        }

        assertFalse(expectEmpty, "expected empty stamp but got [%s - %s]", actualLowerBound, actualUpperBound);

        assertTrue(upperExact != null && lowerExact != null, "join of %s and %s stamp %s is actually empty", stampA, stampB, stamp);

        String boundsMessage = null;
        if (actualUpperBound != stamp.upperBound()) {
            boundsMessage = "upper";
        }
        if (actualLowerBound != stamp.lowerBound()) {
            if (boundsMessage != null) {
                boundsMessage += " and lower";
            } else {
                boundsMessage = "lower";
            }
        }
        fail("stamp %s has %s %s bounds of [%s - %s]", stamp, (upperExact && lowerExact) ? "exact" : "inexact", boundsMessage,
                        actualLowerBound, actualUpperBound);
    }
}
