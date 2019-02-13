/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test.examples;

import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createArguments;
import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createTarget;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.examples.MathPowFactory.MathPowNodeGen;
import com.oracle.truffle.api.nodes.Node;

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

        @Specialization(replaces = "doPowCached", guards = {"exponent == cachedExponent", "cachedExponent <= 10"})
        double doPowCachedExponent(double base, int exponent, @Cached("exponent") int cachedExponent) {
            doPowCachedExponent++;
            double result = 1.0;
            for (int i = 0; i < cachedExponent; i++) {
                result *= base;
            }
            return result;
        }

        @Specialization(replaces = "doPowCachedExponent", guards = "exponent >= 0")
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

        @Specialization(replaces = {"doPowCached", "doPowDoubleInt"})
        double doPow(double base, double exponent) {
            doPow++;
            return Math.pow(base, exponent);
        }
    }

}
