/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.ArrayUtils;

@RunWith(Parameterized.class)
public class ArrayUtilsIndexOfWithMaskTest {

    private static final String strAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String lipsum = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy " +
                    "eirmod tempor invidunt ut labore et dolore magna aliquyam" +
                    " erat, \u0000 sed diam voluptua. At vero \uffff eos et ac" +
                    "cusa\u016f et justo duo dolores 0";

    @Parameters(name = "{index}: haystack {0} fromIndex {1} length {2} needle {3} expected {5}")
    public static Iterable<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (int length : new int[]{15, 16, 17, strAlphabet.length()}) {
            for (String str : new String[]{strAlphabet.substring(0, length), strAlphabet.substring(0, length).toLowerCase()}) {
                for (int i = 0; i < 2; i++) {
                    for (int len = 1; len < 4; len++) {
                        add(ret, dataRow(str, 0, length, str.substring(i, i + len).toLowerCase(), mask(len), i));
                        add(ret, dataRow(str, 0, length, str.substring(length - len - i, length - i).toLowerCase(), mask(len), length - len - i));
                    }
                }
            }
        }
        for (String s : new String[]{lipsum, lipsum.toLowerCase(), lipsum.toUpperCase()}) {
            add(ret, dataRow(s, 1, 0, "", noMask(0), 1));
            add(ret, dataRow(s, 0, s.length(), "l", mask(1), 0));
            add(ret, dataRow(s, 1, s.length() - 1, "l", mask(1), 14));
            add(ret, dataRow(s, 0, s.length(), "o", mask(1), 1));
            add(ret, dataRow(s, 1, s.length() - 1, "o", mask(1), 1));
            add(ret, dataRow(s, 3, s.length() - 3, "o", mask(1), 13));
            add(ret, dataRow(s, 3, s.length() - 3, "\u0000", noMask(1), 137));
            add(ret, dataRow(s, 3, s.length() - 3, " \u0000", noMask(2), 136));
            add(ret, dataRow(s, 3, s.length() - 3, ", \u0000", noMask(3), 135));
            add(ret, dataRow(s, 3, s.length() - 3, "t, \u0000", mask("t, \u0000"), 134));
            add(ret, dataRow(s, 3, s.length() - 3, "\uffff", noMask(1), 166));
            add(ret, dataRow(s, 3, s.length() - 3, " \uffff", noMask(2), 165));
            add(ret, dataRow(s, 3, s.length() - 3, "o \uffff", mask("o \uffff"), 164));
            add(ret, dataRow(s, 3, s.length() - 3, "0", noMask(1), 204));
            add(ret, dataRow(s, 3, s.length() - 3, " 0", noMask(2), 203));
            add(ret, dataRow(s, 3, s.length() - 3, "s 0", mask("s 0"), 202));
            add(ret, dataRow(s, 0, s.length(), "lo", mask(2), 0));
            add(ret, dataRow(s, 1, s.length() - 1, "lo", mask(2), 14));
            add(ret, dataRow(s, 0, s.length(), " dolor", mask(" dolor"), 11));
        }
        add(ret, dataRow(strAlphabet, 0, strAlphabet.length(), "O", String.valueOf('\u0100'), 14, -1));
        add(ret, dataRow(strAlphabet, 0, strAlphabet.length(), "\u014f", String.valueOf('\u0100'), 14));
        add(ret, dataRow(lipsum, 0, lipsum.length(), "o", String.valueOf('\u0100'), 1, -1));
        add(ret, dataRow(lipsum, 0, lipsum.length(), "\u016f", String.valueOf('\u0100'), 1));
        add(ret, dataRow(lipsum, 0, lipsum.length(), "X", mask(1), -1));
        add(ret, dataRow(lipsum, 0, lipsum.length(), "XX", mask(2), -1));
        add(ret, dataRow(lipsum, 0, lipsum.length(), "XXX", mask(3), -1));
        add(ret, dataRow(lipsum, 0, lipsum.length(), "s x", mask("s x"), -1));
        add(ret, dataRow("l", 0, 1, "lo", mask(2), -1));
        return ret;
    }

    private static Object[] dataRow(String haystack, int fromIndex, int maxIndex, String needle, String mask, int expected) {
        return new Object[]{haystack, fromIndex, maxIndex, needle, mask, expected, expected};
    }

    private static Object[] dataRow(String haystack, int fromIndex, int maxIndex, String needle, String mask, int expectedByte, int expectedChar) {
        return new Object[]{haystack, fromIndex, maxIndex, needle, mask, expectedByte, expectedChar};
    }

    private static void add(ArrayList<Object[]> ret, Object[] data) {
        if (isAllZeros((String) data[4])) {
            Object[] copy = Arrays.copyOf(data, data.length);
            copy[4] = null;
            ret.add(copy);
        }
        ret.add(data);
    }

    private static boolean isAllZeros(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) != 0) {
                return false;
            }
        }
        return true;
    }

    private final String haystack;
    private final int fromIndex;
    private final int length;
    private final String needle;
    private final String mask;
    private final int expectedB;
    private final int expectedC;

    public ArrayUtilsIndexOfWithMaskTest(String haystack, int fromIndex, int length, String needle, String mask, int expectedByte, int expectedChar) {
        this.haystack = haystack;
        this.fromIndex = fromIndex;
        this.length = length;
        this.needle = needle;
        this.mask = mask;
        this.expectedB = expectedByte;
        this.expectedC = expectedChar;
    }

    @Test
    public void test() {
        Assert.assertEquals(expectedB, ArrayUtils.indexOfWithOrMask(toByteArray(haystack), fromIndex, length, toByteArray(needle), toByteArray(mask)));
        Assert.assertEquals(expectedC, ArrayUtils.indexOfWithOrMask(haystack.toCharArray(), fromIndex, length, needle.toCharArray(), mask == null ? null : mask.toCharArray()));
        Assert.assertEquals(expectedC, ArrayUtils.indexOfWithOrMask(haystack, fromIndex, length, needle, mask));
    }

    public static String noMask(int len) {
        return new String(new char[len]);
    }

    public static String mask(int len) {
        char[] ret = new char[len];
        Arrays.fill(ret, (char) ('a' ^ 'A'));
        return new String(ret);
    }

    public static String mask(String str) {
        char[] ret = new char[str.length()];
        for (int i = 0; i < ret.length; i++) {
            char c = str.charAt(i);
            if ('A' <= c && c <= 'Z' || 'a' <= c && c <= 'z') {
                ret[i] = (char) ('a' ^ 'A');
            }
        }
        return new String(ret);
    }
}
