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

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import org.junit.Assert;
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
    public void nestedCC() {
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
        test("[a-z&&[^aeiuo]]*", 0, "bcd");
    }

    @Test
    public void int2CC() {
        test("[a-z&&[^aeiuo]]*", 0, "ae");
    }

    @Test
    public void int3CC() {
        test("[a-z&&1]*", 0, "bcd1");
    }

    @Test
    public void int4CC() {
        test("[a-z&&1]", 0, "2");
    }

    @Test
    public void furtherCC() {
        test("[\\p{ASCII}&&\\p{L}]", 0, "(*abAb[");
        test("[\\p{ASCII}&&[^\\P{L}]]", 0, "(*abAb[");
        test("[0[^\\W\\d]]", 0, "asd_0");
        test("[0-9&&[^345]]", 0, "(*abAb[23421413asdf21387652315asdf23");
        test("[2-8&&[4-6]]", 0, "(*abAb[23421413asdf21387652315asdf23");
        test("[0-9&&[345]]", 0, "(*abAb[23421413asdf21387652315asdf23");
        test("[0-4[6-8]]", 0, "(*abAb[23421413asdf21387652315asdf23");
        test("[[\\p{ASCII}&&[^\\p{L}]]&&[0-4[6-8]]]", 0, "(*abAb[23421413asdf21387652315asdf23");
        test("[a-d&&[\\p{L}]]", 0, "asf12305afec32");
        test("[[\\p{ASCII}&&[^\\p{L}]]&&[0-4[6-8]]](\\.){1}[0-4[6-8]]+[a-d&&[\\p{L}]]", 0, "asf12305afec32.321a");
    }

    @Test
    public void randomTests() {
        test("^([a-z0-9_\\.\\-]+)@([\\da-z\\.\\-]+)\\.([a-z\\.]{2,5})$", 0, "daniel.jaburek@gmail.com");
        test("[A-Fa-f0-9]{64}", 0, "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    public void posixCC() {
        test("[\\p{Digit}\\p{Lower}]", 0, "2");
    }

    @Test
    public void posix2CC() {
        test("\\p{Digit}", 0, "1");
        test("\\p{gc=Nd}", 0, "234");
    }

    @Test
    public void posix3CC() {
        test("\\p{IsDigit}", 0, "1");
    }

    @Test
    public void shorthand() {
        test("\\w", 0, "w");
        test("[\\w]", 0, "w");
        test("\\W", 0, "*");
        test("\\d", 0, "1");
        test("\\s", 0, " ");
        test("\\v", 0, "\n");
        test("\\h", 0, "\t");
    }

    @Test
    public void stringAnchor() {
        test("^.", 0, "abc\ndef");
        test(".$", 0, "abc\ndef");
        test(".$", 0, "abc\ndef\n");
        test("\\A\\w", 0, "abc");
        test("\\w\\z", 0, "abc\ndef");
        test(".\\Z", 0, "abc\ndef");
    }

    @Test(expected = UnsupportedRegexException.class)
    public void anchorBailout() {
        test("\\G\\w", 0, "abc def");
    }

    @Test
    public void wordBoundary() {
        test("\\b.", 0, "abc def");
        test("\\B.", 0, "abc def");
    }

    @Test
    public void quantifiers() {
        test("abc?", 0, "ab");
        test("abc??", 0, "ab");
        test("\".*\"", 0, "abc \"def\" \"ghi\" jkl");
        test("\".*?\"", 0, "abc \"def\" \"ghi\" jkl");
        test("\".+?\"", 0, "abc \"def\" \"ghi\" jkl");
        test("a{3}", 0, "aaa");
        test("a{2,4}", 0, "aa");
        test("a{2,}", 0, "aaaaa");
        test("a{2,4}?", 0, "aa");
        test("a{2,}?", 0, "aaaaa");
    }

    @Test(expected = UnsupportedRegexException.class)
    public void quantifiersBailout() {
        test("abc?+c", 0, "abcc");
        test("a{2,4}+a", 0, "aaaaa");
    }

    @Test(expected = Exception.class)
    public void quantifiersFail() {
        test("abc?+c", 0, "abc");
        test("\".*+\"", 0, "\"abc\"");
        test("\".++\"", 0, "\"abc\"");
        test("a{2,4}+a", 0, "aaaa");
        test("a{2,}+a", 0, "aa");
    }

    @Test(expected = UnsupportedRegexException.class)
    public void atomicGroupBailout() {
        test("a(?>bc|b)c", 0, "abcc");
    }

    @Test
    public void capturingGroup() {
        test("(abc){3}", 0, "abcabcabc");
        test("(?:abc){3}", 0, "abcabcabc");
        test("(?<x>abc){3}", 0, "abcabcabc");
    }

    @Test
    public void backReference() {
        test("(abc|def)=\\1", 0, "abc=abc");
        test("(abc|def)=\\1", 0, "def=def");
        test("(?<x>abc|def)=\\k<x>", 0, "def=def");
        test("(abc|def)=\\1", 0, "abc=def");
        test("(abc|def)=\\1", 0, "def=abc");
    }

    @Test
    public void lookAhead() {
        test("t(?=s)", 0, "streets");
        test("t(?!s)", 0, "streets");
    }

    @Test
    public void lookBehind() {
        test("(?<=s)t", 0, "streets");
        test("(?<!s)t", 0, "streets");
        test("(?<=is|e)t", 0, "twisty streets");
        test("(?<=s\\w{1,7})t", 0, "twisty streets");
        test("(?<=s\\w+)t", 0, "twisty streets");
    }

    @Test
    public void modeModifier() {
        test("(?-i)a", 0, "a");
        test("(?i)a", 0, "A");
        test("te(?i)st", 0, "test");
        test("te(?i)st", 0, "teST");
        test("te(?i:st)", 0, "test");
        test("te(?i:st)", 0, "teST");
        test("(?i)te(?-i)st", 0, "test");
        test("(?i)te(?-i)st", 0, "teST");

        test("(?x)a#b", 0, "a");

        test("(?s).*", 0, "ab\n\ndef");

        test("(?m).*", 0, "ab\n\ndef");

        test("(?dm)^.", 0, "a\rb\nc");
    }

    @Test
    public void modeModifierFail() {
        test("te(?i)st", 0, "TEst");
        test("te(?i)st", 0, "TEST");
        test("te(?i:st)", 0, "TEst");
        test("te(?i:st)", 0, "TEST");
        test("(?i)te(?-i)st", 0, "TEst");
        test("(?i)te(?-i)st", 0, "TEST");

    }

    @Test
    public void dTest() {
        Pattern p = Pattern.compile("\\d", Pattern.UNICODE_CHARACTER_CLASS);
        CodePointSet cps = UnicodeProperties.getProperty("Nd");
        for(int i = 0; i < Character.MAX_CODE_POINT; i++) {
            StringBuilder sb = new StringBuilder();
            sb.appendCodePoint(i);
            Matcher m = p.matcher(sb.toString());
        }

        Assert.assertEquals(UnicodeProperties.getProperty("sc=Brai", true), UnicodeProperties.getProperty("Script=Brai", true));
    }

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
    }

    String flagsToString(int javaUtilPatternFlags) {
        if (javaUtilPatternFlags == 0) {
            return "";
        }

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
        if ((javaUtilPatternFlags & Pattern.UNICODE_CHARACTER_CLASS) != 0) {
            return "U";
        }

        throw new UnsupportedOperationException();
    }
}
