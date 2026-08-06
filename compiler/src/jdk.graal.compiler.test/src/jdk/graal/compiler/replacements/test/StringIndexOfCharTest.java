/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import static jdk.graal.compiler.truffle.test.strings.TStringTest.testParameterized;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;

public class StringIndexOfCharTest extends GraalCompilerTest {

    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        String longString = "ab";
        for (int i = 0; i < 15; i++) {
            longString = longString + longString;
        }
        longString = longString + "xx";
        String longUTF16String = "\u03bb" + longString;
        String mediumString = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaax" +
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String mediumUTF16String = "\u03bbaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaax" +
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String[] targets = new String[]{"foobar", "foo", "bar", "\u00A6", "\u03bbfoobar", mediumString, mediumUTF16String, longString, longUTF16String};
        int[] targetChars = new int[]{'f', 'o', 'r', 'x', '\u00A6', Character.MIN_SUPPLEMENTARY_CODE_POINT};
        int[] targetOffsets = new int[12];
        for (int i = 0; i < targetOffsets.length; i++) {
            targetOffsets[i] = i - 1;
        }
        for (String source : targets) {
            for (int targetChar : targetChars) {
                for (int offset : targetOffsets) {
                    tests.add(new Object[]{source, targetChar, offset, source.length()});
                }
            }
        }
        tests.add(new Object[]{"abcd", (int) 'c', 1, 1});
        return tests;
    }

    public int testStringIndexOf(String a, int b) {
        return a.indexOf(b);
    }

    public int testStringIndexOfOffset(String a, int b, int offset) {
        return a.indexOf(b, offset);
    }

    public int stringIndexOfRegion(String string, int ch, int fromIndexArg, int toIndexArg) {
        return string.indexOf(ch, fromIndexArg, toIndexArg);
    }

    @Test
    public void testStringIndexOfConstant() {
        testParameterized(data(), this::testStringIndexOfConstantCase);
    }

    @Test
    public void testStringIndexOfConstantOffset() {
        testParameterized(data(), this::testStringIndexOfConstantOffsetCase);
    }

    @Test
    public void testStringIndexOfRegion() {
        testParameterized(data(), this::testStringIndexOfRegionCase);
    }

    private void testStringIndexOfConstantCase(Object[] args) {
        test("testStringIndexOf", args[0], args[1]);
    }

    private void testStringIndexOfConstantOffsetCase(Object[] args) {
        test("testStringIndexOfOffset", args[0], args[1], args[2]);
    }

    private void testStringIndexOfRegionCase(Object[] args) {
        test("stringIndexOfRegion", args);
    }
}
