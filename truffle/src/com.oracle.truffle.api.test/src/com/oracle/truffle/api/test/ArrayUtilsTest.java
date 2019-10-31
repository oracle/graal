/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import com.oracle.truffle.api.ArrayUtils;
import org.junit.Test;

import static com.oracle.truffle.api.test.ArrayUtilsIndexOfWithMaskTest.mask;
import static org.junit.Assert.assertEquals;

public class ArrayUtilsTest {

    private static final String strS = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy " +
                    "eirmod tempor invidunt ut labore et dolore magna aliquyam" +
                    " erat, \u0000 sed diam voluptua. At vero \uffff eos et ac" +
                    "cusam et justo duo dolores 0";
    private static final byte[] strB = toByteArray(strS);
    private static final char[] strC = strS.toCharArray();
    private static final String[] searchValues = {
                    "L",
                    "0",
                    " ",
                    "\u0000",
                    "\uffff",
                    "X",
                    "ip",
                    "X0",
                    "LX",
                    "LXY",
                    "LXYZ",
                    "VXYZ",
                    "VXY0",
    };
    private static final int[] expectedResults = {
                    0,
                    -1,
                    0,
                    -1,
                    -1,
                    0,
                    -1,
                    -1,
                    -1,
                    -1,
                    204,
                    204,
                    204,
                    204,
                    204,
                    204,
                    5,
                    5,
                    5,
                    5,
                    -1,
                    5,
                    5,
                    -1,
                    137,
                    137,
                    137,
                    137,
                    -1,
                    137,
                    137,
                    -1,
                    166,
                    166,
                    166,
                    166,
                    -1,
                    166,
                    166,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    6,
                    6,
                    6,
                    6,
                    -1,
                    6,
                    6,
                    -1,
                    -1,
                    -1,
                    204,
                    204,
                    204,
                    204,
                    204,
                    204,
                    0,
                    -1,
                    0,
                    -1,
                    -1,
                    0,
                    -1,
                    -1,
                    0,
                    -1,
                    0,
                    -1,
                    -1,
                    0,
                    -1,
                    -1,
                    0,
                    -1,
                    0,
                    -1,
                    -1,
                    0,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    -1,
                    204,
                    204,
                    204,
                    204,
                    204,
                    204,
    };

    @Test
    public void testIndexOf() {
        int i = 0;
        for (String needle : searchValues) {
            for (int maxIndex : new int[]{0, strS.length() - 1, strS.length(), strS.length()}) {
                for (int fromIndex : new int[]{0, 1, strS.length() - 1, strS.length()}) {
                    if (fromIndex < maxIndex) {
                        doTestIndexOf(strS, fromIndex, maxIndex, needle, expectedResults[i++]);
                    }
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringException1() {
        ArrayUtils.indexOf(strS, -1, strS.length(), 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringException2() {
        ArrayUtils.indexOf(strS, 0, strS.length() + 1, 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringException3() {
        ArrayUtils.indexOf(strS, 1, 0, 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringException4() {
        ArrayUtils.indexOf(strS, 0, strS.length());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfCharArrayException1() {
        ArrayUtils.indexOf(strC, -1, strS.length(), 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfCharArrayException2() {
        ArrayUtils.indexOf(strC, 0, strS.length() + 1, 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfCharArrayException3() {
        ArrayUtils.indexOf(strC, 1, 0, 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfByteArrayException1() {
        ArrayUtils.indexOf(strB, -1, strS.length(), (byte) 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfByteArrayException2() {
        ArrayUtils.indexOf(strB, 0, strS.length() + 1, (byte) 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfByteArrayException3() {
        ArrayUtils.indexOf(strB, 1, 0, (byte) 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskStringException1() {
        ArrayUtils.indexOfWithOrMask(strS, -1, strS.length(), "l", mask(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskStringException2() {
        ArrayUtils.indexOfWithOrMask(strS, 0, strS.length() + 1, "l", mask(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskStringException3() {
        ArrayUtils.indexOfWithOrMask(strS, 1, strS.length(), "l", mask(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskStringException4() {
        ArrayUtils.indexOfWithOrMask(strS, 0, 1, "l", mask(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskCharArrayException1() {
        ArrayUtils.indexOfWithOrMask(strC, -1, strS.length(), "l".toCharArray(), mask(1).toCharArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskCharArrayException2() {
        ArrayUtils.indexOfWithOrMask(strC, 0, strS.length() + 1, "l".toCharArray(), mask(1).toCharArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskCharArrayException3() {
        ArrayUtils.indexOfWithOrMask(strC, 1, strS.length(), "l".toCharArray(), mask(1).toCharArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskCharArrayException4() {
        ArrayUtils.indexOfWithOrMask(strC, 0, 1, "l".toCharArray(), mask(2).toCharArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskByteArrayException1() {
        ArrayUtils.indexOfWithOrMask(strB, -1, strS.length(), toByteArray("l"), toByteArray(mask(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskByteArrayException2() {
        ArrayUtils.indexOfWithOrMask(strB, 0, strS.length() + 1, toByteArray("l"), toByteArray(mask(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskByteArrayException3() {
        ArrayUtils.indexOfWithOrMask(strB, 1, strS.length(), toByteArray("l"), toByteArray(mask(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringWithORMaskByteArrayException4() {
        ArrayUtils.indexOfWithOrMask(strB, 0, 1, toByteArray("l"), toByteArray(mask(2)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegionEqualsWithOrMaskStringException1() {
        ArrayUtils.regionEqualsWithOrMask(strS, -1, strS, 0, 1, mask(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegionEqualsWithOrMaskStringException2() {
        ArrayUtils.regionEqualsWithOrMask(strS, 0, strS, -1, 1, mask(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testregionEqualsWithOrMaskStringException3() {
        ArrayUtils.regionEqualsWithOrMask(strS, 0, strS, 0, -1, mask(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testregionEqualsWithOrMaskStringException4() {
        ArrayUtils.regionEqualsWithOrMask(strS, 0, strS, 0, 1, mask(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testregionEqualsWithOrMaskCharArrayException1() {
        ArrayUtils.regionEqualsWithOrMask(strC, -1, strC, 0, 1, mask(1).toCharArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testregionEqualsWithOrMaskCharArrayException2() {
        ArrayUtils.regionEqualsWithOrMask(strC, 0, strC, -1, 1, mask(1).toCharArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testregionEqualsWithOrMaskCharArrayException3() {
        ArrayUtils.regionEqualsWithOrMask(strC, 0, strC, 0, -1, mask(1).toCharArray());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testregionEqualsWithOrMaskByteArrayException1() {
        ArrayUtils.regionEqualsWithOrMask(strB, -1, strB, 0, 1, toByteArray(mask(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testregionEqualsWithOrMaskByteArrayException2() {
        ArrayUtils.regionEqualsWithOrMask(strB, 0, strB, -1, 1, toByteArray(mask(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testregionEqualsWithOrMaskByteArrayException3() {
        ArrayUtils.regionEqualsWithOrMask(strB, 0, strB, 0, -1, toByteArray(mask(1)));
    }

    private static void doTestIndexOf(String haystack, int fromIndex, int maxIndex, String needle, int expected) {
        assertEquals(ArrayUtils.indexOf(haystack, fromIndex, maxIndex, needle.toCharArray()), expected);
        assertEquals(ArrayUtils.indexOf(haystack.toCharArray(), fromIndex, maxIndex, needle.toCharArray()), expected);
        assertEquals(ArrayUtils.indexOf(toByteArray(haystack), fromIndex, maxIndex, toByteArray(needle)), expected);
    }

    public static byte[] toByteArray(String s) {
        if (s == null) {
            return null;
        }
        byte[] ret = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            ret[i] = (byte) s.charAt(i);
        }
        return ret;
    }
}
