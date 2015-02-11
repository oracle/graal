/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.dsl.test.examples;

import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.examples.MathPowFactory.MathPowNodeGen;
import com.oracle.truffle.api.nodes.*;

/**
 * This example shows possible specializations for a simplified math pow node. It demonstrates how
 * multiple caches can coexist within in the same node. This example does not show the best possible
 * specializations for math.pow.
 *
 * Note: int values are implicitly casted to double values.
 */
@SuppressWarnings("unused")
public class MathPow extends Node {

    @Test
    public void testPow() {
        MathPowNode node = MathPowNodeGen.create(createArguments(2));
        CallTarget target = createTarget(node);

        // start with doPowCached
        assertEquals(1D, target.call(1D, 1));
        assertEquals(2D, target.call(2D, 1));
        assertEquals(3D, target.call(3D, 1));
        assertEquals(3, node.doPowCached);
        assertEquals(0, node.doPowCachedExponent);

        // transition to doPowCachedExponent
        assertEquals(4D, target.call(4D, 1));
        assertEquals(5D, target.call(5D, 1));
        assertEquals(6D, target.call(6D, 1));
        assertEquals(16D, target.call(4D, 2));
        assertEquals(125D, target.call(5D, 3));
        assertEquals(5, node.doPowCachedExponent);
        assertEquals(0, node.doPowDoubleInt);

        // transition to doPowDoubleInt
        assertEquals(4D * 4D * 4D * 4D, target.call(4D, 4));
        assertEquals(5D * 5D * 5D * 5D * 5D, target.call(5D, 5));
        assertEquals(5, node.doPowCachedExponent);
        assertEquals(2, node.doPowDoubleInt);

        // transition to doPow
        assertEquals(5D, target.call(5D, 1D));
        assertEquals(2D, target.call(2D, 1D));

        assertEquals(3, node.doPowCached);
        assertEquals(5, node.doPowCachedExponent);
        assertEquals(2, node.doPowDoubleInt);
        assertEquals(2, node.doPow);
    }

    public static class MathPowNode extends ExampleNode {

        // test flags
        int doPowCached;
        int doPowCachedExponent;
        int doPowDoubleInt;
        int doPow;

        @Specialization(guards = {"base == cachedBase", "exponent == cachedExponent"})
        double doPowCached(double base, int exponent, //
                        @Cached("base") double cachedBase, //
                        @Cached("exponent") int cachedExponent, //
                        @Cached("cachePow(cachedBase, cachedExponent)") double cachedResult) {
            doPowCached++;
            return cachedResult;
        }

        /*
         * We could just use the doPow specialization instead. But this makes the number of doPow
         * calls more difficult to assert.
         */
        protected static double cachePow(double base, int exponent) {
            return Math.pow(base, exponent);
        }

        @Specialization(contains = "doPowCached", guards = {"exponent == cachedExponent", "cachedExponent <= 10"})
        @ExplodeLoop
        double doPowCachedExponent(double base, int exponent, @Cached("exponent") int cachedExponent) {
            doPowCachedExponent++;
            double result = 1.0;
            for (int i = 0; i < cachedExponent; i++) {
                result *= base;
            }
            return result;
        }

        @Specialization(contains = "doPowCachedExponent", guards = "exponent >= 0")
        double doPowDoubleInt(double base, int exponent) {
            doPowDoubleInt++;
            // Uses binary decomposition to limit the number of
            // multiplications; see the discussion in "Hacker's Delight" by Henry
            // S. Warren, Jr., figure 11-6, page 213.
            double b = base;
            int e = exponent;
            double result = 1;
            while (e > 0) {
                if ((e & 1) == 1) {
                    result *= b;
                }
                e >>= 1;
                b *= b;
            }
            return result;
        }

        @Specialization(contains = {"doPowCached", "doPowDoubleInt"})
        double doPow(double base, double exponent) {
            doPow++;
            return Math.pow(base, exponent);
        }
    }

}
