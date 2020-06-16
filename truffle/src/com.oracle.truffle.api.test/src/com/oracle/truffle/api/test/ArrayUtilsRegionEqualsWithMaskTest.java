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

import static com.oracle.truffle.api.test.ArrayUtilsTest.toByteArray;
import static com.oracle.truffle.api.test.ArrayUtilsIndexOfWithMaskTest.mask;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.ArrayUtils;

@RunWith(Parameterized.class)
public class ArrayUtilsRegionEqualsWithMaskTest {

    public static final String lipsum = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy " +
                    "eirmod tempor invidunt ut labore et dolore magna aliquyam" +
                    " erat, \u0000 sed diam voluptua. At vero \uffff eos et ac" +
                    "cusam et justo duo dolores 0";
    public static final String lipsumLower = lipsum.toLowerCase();
    public static final String lipsumUpper = lipsum.toUpperCase();

    @Parameters(name = "{index}: fromIndex1 {1} fromIndex2 {3} length {5} mask {4} expected {6}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> ret = data(false);
        ret.addAll(data(true));
        return ret;
    }

    public static ArrayList<Object[]> data(boolean withMask) {
        String[] haystacks = withMask ? new String[]{lipsum, lipsumUpper} : new String[]{lipsum};
        String needle = withMask ? lipsumLower : lipsum;
        ArrayList<Object[]> ret = new ArrayList<>();
        for (String s : haystacks) {
            for (int fromIndex : new int[]{0, 1, 15, 16, lipsum.length() - 16, lipsum.length() - 15}) {
                for (int length : new int[]{1, 2, 3, 4, 5, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64, 65, lipsum.length()}) {
                    if (withMask && s == lipsum && fromIndex > 0 || fromIndex + length > s.length()) {
                        continue;
                    }
                    String sub1 = needle.substring(fromIndex, fromIndex + length);
                    String sub2 = String.valueOf((char) (sub1.charAt(0) + 1)) + sub1.substring(1);
                    String sub3 = sub1.substring(0, sub1.length() - 1) + String.valueOf((char) (sub1.charAt(sub1.length() - 1) - 1));
                    String sub4 = sub1.substring(0, sub1.length() / 2) + String.valueOf((char) (sub1.charAt(sub1.length() / 2) + 1)) + sub1.substring((sub1.length() / 2) + 1);
                    ret.add(dataRow(s, fromIndex, needle, fromIndex, sub1, true, withMask));
                    ret.add(dataRow(s, fromIndex, sub1, 0, sub1, true, withMask));
                    ret.add(dataRow(s, fromIndex, sub2, 0, sub2, false, withMask));
                    ret.add(dataRow(s, fromIndex, sub3, 0, sub3, false, withMask));
                    ret.add(dataRow(s, fromIndex, sub4, 0, sub4, false, withMask));
                }
            }
        }
        ret.add(dataRow(lipsum, 0, needle, lipsum.length(), 0, true, withMask));
        ret.add(dataRow(lipsum, 0, needle, lipsum.length(), 1, false, withMask));
        ret.add(dataRow(lipsum, lipsum.length(), needle, 0, 0, true, withMask));
        ret.add(dataRow(lipsum, lipsum.length(), needle, 0, 1, false, withMask));
        ret.add(dataRow(lipsum, lipsum.length() - 1, needle, lipsum.length() - 1, 2, false, withMask));
        ret.add(dataRow(lipsum, lipsum.length() - 2, needle, lipsum.length() - 2, 2, true, withMask));
        ret.add(dataRow(lipsum, 0, needle, 0, lipsum, true, withMask));
        ret.add(dataRow(lipsum, 0, needle, 0, lipsum.length() + 1, false, withMask));
        return ret;
    }

    private static Object[] dataRow(String a1, int fromIndex1, String a2, int fromIndex2, String mask, boolean expected, boolean withMask) {
        return new Object[]{a1, fromIndex1, a2, fromIndex2, withMask ? mask(mask) : null, mask.length(), expected};
    }

    private static Object[] dataRow(String a1, int fromIndex1, String a2, int fromIndex2, int maskLength, boolean expected, boolean withMask) {
        return new Object[]{a1, fromIndex1, a2, fromIndex2, withMask ? mask(maskLength) : null, maskLength, expected};
    }

    private final String a1;
    private final int fromIndex1;
    private final String a2;
    private final int fromIndex2;
    private final String mask;
    private final int length;
    private final boolean expected;

    public ArrayUtilsRegionEqualsWithMaskTest(String a1, int fromIndex1, String a2, int fromIndex2, String mask, int length, boolean expected) {
        this.a1 = a1;
        this.fromIndex1 = fromIndex1;
        this.a2 = a2;
        this.fromIndex2 = fromIndex2;
        this.mask = mask;
        this.length = length;
        this.expected = expected;
    }

    @Test
    public void test() {
        Assert.assertEquals(expected, ArrayUtils.regionEqualsWithOrMask(toByteArray(a1), fromIndex1, toByteArray(a2), fromIndex2, length, toByteArray(mask)));
        Assert.assertEquals(expected, ArrayUtils.regionEqualsWithOrMask(a1.toCharArray(), fromIndex1, a2.toCharArray(), fromIndex2, length, mask == null ? null : mask.toCharArray()));
        Assert.assertEquals(expected, ArrayUtils.regionEqualsWithOrMask(a1, fromIndex1, a2, fromIndex2, length, mask));
    }
}
