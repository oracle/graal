/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.oracle.truffle.regex.util.EmptyArrays;

public class JavaUtilPatternTests extends RegexTestBase {

    @Override
    String getEngineOptions() {
        return "Flavor=JavaUtilPattern";
    }

    @Test
    public void helloWorld() {
        test("[Hh]ello [Ww]orld!", 0, "hello World!");
    }

//    @Test
//    public void halloWelt() {
//        test("rege(x(es)?|xps?)", 0, "regexes");
//    }

    @Test
    public void dotTest() {
        test(".", 0, "x");
    }

    @Test
    public void alternationTest() {
        test("abc|def|xyz", 0, "abc");
        test("abc|def|xyz", 0, "def");
        test("abc|def|xyz", 0, "xyz");
    }

    @Test
    public void alternationEagerTest() {
        test("a|ab", 0, "ab");
    }

    @Test
    public void backslashEscapeCC() {
        test("[\\^\\]]", 0, "^");
        test("[\\^\\]]", 0, "]");
    }

    @Test
    public void rangeCC() {
        test("[a-zA-Z0-9]", 0, "abcABC012");
    }

    @Test
    public void negatedCC() {
        test("[^a-d]", 0, "x");
    }

    @Test
    public void literalBracketCC() {
        test("[ab[cd]ef]", 0, "aef]");
        test("[ab[cd]ef]", 0, "bef]");
        test("[ab[cd]ef]", 0, "[ef]");
        test("[ab[cd]ef]", 0, "cef]");
        test("[ab[cd]ef]", 0, "def]");
    }

    @Test
    public void nestedCC() {    // TODO: nested classes do not work yet
        test("[ab[cd]ef]", 0, "a");
        test("[ab[cd]ef]", 0, "b");
        test("[ab[cd]ef]", 0, "c");
        test("[ab[cd]ef]", 0, "d");
        test("[ab[cd]ef]", 0, "e");
        test("[ab[cd]ef]", 0, "f");
    }

    @Test
    public void cC() {
        test("[ab[cd]ef]", 0, "[");
        test("[ab[cd]ef]", 0, "]");
    }

    @Test
    public void int1CC() {
        test("[a-z&&[^aeiuo]]", 0, "bcd");
    }

    @Test
    public void int2CC() {  // TODO should throw an error
        test("[a-z&&[^aeiuo]]", 0, "ae");
    }

    @Test
    public void int3CC() {
        test("[a-z&&1]", 0, "bcd1");
    }

    @Test
    public void int4CC() {  // TODO should throw an error
        test("[a-z&&1]", 0, "2");
    }

    @Test
    public void posixCC() {  // TODO should throw an error
        test("[\\p{Digit}\\p{Lower}]", 0, "2");
    }



//    @Test
//    public void helloWorld() {
//        test("[Hh]ello [Ww]orld!", 0, "hello World!");
//    }

    void test(String pattern, int flags, String input) {
        test(pattern, flags, input, 0);
    }

    void test(String pattern, int flags, String input, int fromIndex) {
        Matcher m = Pattern.compile(pattern, flags).matcher(input);
        boolean isMatch = m.find(fromIndex);
        final int[] groupBoundaries;
        if (isMatch) {
            groupBoundaries = new int[(m.groupCount() + 1) << 1];
            for (int i = 0; i < m.groupCount() + 1; i++) {
                groupBoundaries[i << 1] = m.start(i);
                groupBoundaries[(i << 1) + 1] = m.end(i);
            }
        } else {
            groupBoundaries = EmptyArrays.INT;
        }
        test(pattern, flagsToString(flags), input, fromIndex, isMatch, groupBoundaries);

        // TODO auch an Java-Matcher intern weiterleiten und vergleichen ob beide gleich sind
    }

    String flagsToString(int javaUtilPatternFlags) {
        if (javaUtilPatternFlags == 0) {
            return "";
        }
        // TODO
        if ((javaUtilPatternFlags & Pattern.UNIX_LINES) != 0) {
            return "d";
        }
        if ((javaUtilPatternFlags & Pattern.CASE_INSENSITIVE) != 0) {
            return "i";
        }
        if ((javaUtilPatternFlags & Pattern.COMMENTS) != 0) {
            return "x";
        }
        if ((javaUtilPatternFlags & Pattern.MULTILINE) != 0) {
            return "m";
        }
        if ((javaUtilPatternFlags & Pattern.DOTALL) != 0) {
            return "s";
        }
        if ((javaUtilPatternFlags & Pattern.UNICODE_CASE) != 0) {
            return "u";
        }

        throw new UnsupportedOperationException();
    }
}
