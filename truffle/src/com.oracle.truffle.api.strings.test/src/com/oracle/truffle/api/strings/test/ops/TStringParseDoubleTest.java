/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.strings.test.ops;

import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.TStringTestBase;

@RunWith(Parameterized.class)
public class TStringParseDoubleTest extends TStringTestBase {

    @Parameter public TruffleString.ParseDoubleNode node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.ParseDoubleNode> data() {
        return Arrays.asList(TruffleString.ParseDoubleNode.create(), TruffleString.ParseDoubleNode.getUncached());
    }

    @SuppressWarnings("cast")
    @Test
    public void testAll() throws Exception {
        for (double n : new double[]{Double.MIN_VALUE, Double.MIN_VALUE + 1, Long.MIN_VALUE, Long.MIN_VALUE + 1, ((long) Integer.MIN_VALUE) - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1,
                        Short.MIN_VALUE, -12345, -1, 0, 0.1, 0.5, 1, Math.PI, 12345, Short.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, ((long) Integer.MAX_VALUE) + 1, Long.MAX_VALUE - 1,
                        Long.MAX_VALUE, (double) Long.MAX_VALUE - 0.1, Double.MAX_VALUE - 1, Double.MAX_VALUE}) {
            checkAsciiString(String.valueOf(n), (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
                Assert.assertEquals(n, node.execute(a), 0);
                assertCodePointsEqual(a, encoding, codepoints);
            });
        }
    }

    @Test
    public void testLargeDecimalExponent() throws Exception {
        String zeros = "0".repeat(9_999_999);
        // The truncated exponent cancels the decimal-point adjustment, hiding overflow or underflow.
        assertLargeInput(Double.POSITIVE_INFINITY, "0." + zeros + "1e100000000");
        assertLargeInput(Double.NEGATIVE_INFINITY, "-0." + zeros + "1e100000000");
        assertLargeInput(0.0, "1" + zeros + "e-100000000");
        assertLargeInput(-0.0, "-1" + zeros + "e-100000000");
        // Exponents that are fully accumulated must still allow exact cancellation.
        assertLargeInput(1.0, "0." + zeros + "1e10000000");
        assertLargeInput(10.0, "0." + zeros + "1e10000001");
    }

    @Test
    public void testLargeHexadecimalExponent() throws Exception {
        String fraction = "0x0." + "0".repeat(2_499_999) + "1p";
        // Each fractional hexadecimal digit contributes four bits to the exponent adjustment.
        assertLargeInput(Double.POSITIVE_INFINITY, fraction + "100000000");
        assertLargeInput(Double.NEGATIVE_INFINITY, "-" + fraction + "100000000");
        assertLargeInput(1.0, fraction + "10000000");
        assertLargeInput(2.0, fraction + "10000001");
    }

    @Test
    public void testLargeExponent() throws Exception {
        for (String input : new String[]{"1e100000000", "-1e100000000", "1e-100000000", "-1e-100000000", "0e100000000", "-0e100000000",
                        "0x1p100000000", "-0x1p100000000", "0x1p-100000000", "-0x1p-100000000", "0x0p100000000", "-0x0p100000000"}) {
            double expected = Double.parseDouble(input);
            checkAsciiString(input, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
                // Boxed comparison distinguishes positive and negative zero.
                Assert.assertEquals(Double.valueOf(expected), Double.valueOf(node.execute(a)));
            });
        }
    }

    @Test
    public void testLargeExponentInvalidSuffix() throws Exception {
        for (String input : new String[]{"1e100000000+", "1e100000000e1", "0x1p100000000+", "0x1p100000000p1"}) {
            checkAsciiString(input, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
                Assert.assertThrows(TruffleString.NumberFormatException.class, () -> node.execute(a));
            });
        }
    }

    @Test
    public void testEmptyHexadecimalSignificand() throws Exception {
        for (String input : new String[]{"0x.p100000000", "0x.p-100000000", "-0x.p100000000", "+0X.P+100000000", " \t0x.p100000000\r\n",
                        "0xp0", "0x.p0", "-0x.p0", "+0X.P0"}) {
            checkAsciiString(input, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
                Assert.assertThrows(TruffleString.NumberFormatException.class, () -> node.execute(a));
            });
        }
    }

    @Test
    public void testHexadecimalSignificand() throws Exception {
        for (String input : new String[]{"0x0p0", "0x0.p0", "0x.0p0", "0x1.p0", "0x.1p4", "-0x0.p100000000", "-0x.0p-100000000"}) {
            double expected = Double.parseDouble(input);
            checkAsciiString(input, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
                Assert.assertEquals(Double.valueOf(expected), Double.valueOf(node.execute(a)));
            });
        }
    }

    @Test
    public void testNull() throws Exception {
        checkNullS((s) -> node.execute(s));
    }

    private void assertLargeInput(double expected, String input) throws Exception {
        // Limit storage variants for multi-megabyte inputs; the short cases exercise all variants.
        String padded = "x" + input + "x";
        for (TruffleString.Encoding encoding : new TruffleString.Encoding[]{TruffleString.Encoding.UTF_8, TruffleString.Encoding.UTF_16, TruffleString.Encoding.UTF_32}) {
            TruffleString string = TruffleString.fromJavaStringUncached(padded, 1, input.length(), encoding, false);
            Assert.assertEquals(encoding.toString(), Double.valueOf(expected), Double.valueOf(node.execute(string)));
        }
    }
}
