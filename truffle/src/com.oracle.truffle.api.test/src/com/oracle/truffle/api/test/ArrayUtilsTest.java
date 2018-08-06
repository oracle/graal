/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import com.oracle.truffle.api.ArrayUtils;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ArrayUtilsTest {

    private static final String str = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy " +
                    "eirmod tempor invidunt ut labore et dolore magna aliquyam" +
                    " erat, \u0000 sed diam voluptua. At vero \uffff eos et ac" +
                    "cusam et justo duo dolores 0";
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
            for (int maxIndex : new int[]{0, str.length() - 1, str.length(), str.length()}) {
                for (int fromIndex : new int[]{0, 1, str.length() - 1, str.length()}) {
                    if (fromIndex < maxIndex) {
                        doTestIndexOf(str, fromIndex, maxIndex, needle, expectedResults[i++]);
                    }
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringException1() {
        ArrayUtils.indexOf(str, -1, str.length(), 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringException2() {
        ArrayUtils.indexOf(str, 0, str.length() + 1, 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfStringException3() {
        ArrayUtils.indexOf(str, 1, 0, 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfCharArrayException1() {
        ArrayUtils.indexOf(str.toCharArray(), -1, str.length(), 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfCharArrayException2() {
        ArrayUtils.indexOf(str.toCharArray(), 0, str.length() + 1, 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfCharArrayException3() {
        ArrayUtils.indexOf(str.toCharArray(), 1, 0, 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfByteArrayException1() {
        ArrayUtils.indexOf(toByteArray(str), -1, str.length(), (byte) 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfByteArrayException2() {
        ArrayUtils.indexOf(toByteArray(str), 0, str.length() + 1, (byte) 'L');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexOfByteArrayException3() {
        ArrayUtils.indexOf(toByteArray(str), 1, 0, (byte) 'L');
    }

    private static void doTestIndexOf(String haystack, int fromIndex, int maxIndex, String needle, int expected) {
        assertEquals(ArrayUtils.indexOf(haystack, fromIndex, maxIndex, needle.toCharArray()), expected);
        assertEquals(ArrayUtils.indexOf(haystack.toCharArray(), fromIndex, maxIndex, needle.toCharArray()), expected);
        assertEquals(ArrayUtils.indexOf(toByteArray(haystack), fromIndex, maxIndex, toByteArray(needle)), expected);
    }

    private static byte[] toByteArray(String s) {
        byte[] ret = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            ret[i] = (byte) s.charAt(i);
        }
        return ret;
    }
}
