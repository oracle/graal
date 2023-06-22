/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import com.oracle.truffle.regex.tregex.string.Encodings;

public class OracleDBTests extends RegexTestBase {

    @Override
    String getEngineOptions() {
        return "Flavor=OracleDB";
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_16;
    }

    @Test
    public void testPositionAssertions() {
        test("\\Ax", "", "yx", 0, false);

        test("\\s\\Z", "", "x \n", 0, true, 1, 2);
        test("\\s\\Z", "", "x \n", 2, true, 2, 3);
        test("\\s\\Z", "", "x \n \n", 0, true, 3, 4);
        test("\\s\\Z", "m", "x \n \n", 0, true, 3, 4);

        test("\\s$", "", "x \n", 0, true, 1, 2);
        test("\\s$", "", "x \n", 2, true, 2, 3);
        test("\\s$", "", "x \n \n", 0, true, 3, 4);
        test("\\s$", "m", "x \n \n", 0, true, 1, 2);

        test("\\s\\z", "", "x \n", 0, true, 2, 3);
        test("\\s\\z", "", "x \n", 2, true, 2, 3);
        test("\\s\\z", "", "x \n \n", 0, true, 4, 5);
        test("\\s\\z", "m", "x \n \n", 0, true, 4, 5);
    }

    @Test
    public void testUnmatchedParens() {
        test("}", "", "}", 0, true, 0, 1);
        test("{", "", "{", 0, true, 0, 1);
        test("]", "", "]", 0, true, 0, 1);
        expectSyntaxError("[", "", "unmatched bracket in regular expression", 0);
        expectSyntaxError(")", "", "unmatched parentheses in regular expression", 0);
    }

    @Test
    public void testQuantifiers() {
        test("x{,1}", "", "x", 0, false);
        test("x{,1}?", "", "x", 0, false);
        test("x{,1}", "", "x{,1}", 0, true, 0, 5);
        test("x{,1}?", "", "x{,1}", 0, true, 0, 5);
        test("x{,1}?", "", "x{,1", 0, true, 0, 4);
        test("x{1,}", "", "x{1,}", 0, true, 0, 1);
        expectSyntaxError("x{2,1}", "", "invalid interval value in regular expression", 1);
        test("x{2147483649}", "", "x{2147483649}", 0, true, 0, 1);
        test("x{2147483649,}", "", "x{2147483649}", 0, true, 0, 1);
        expectSyntaxError("x{2147483649,2}", "", "invalid interval value in regular expression", 1);
        test("x{2147483649,2147483650}", "", "xxx", 0, true, 0, 2);
        test("x{0,4294967295}", "", "x{4294967295}", 0, true, 0, 1);
        test("x{1,4294967295}", "", "x{4294967295}", 0, true, 0, 1);
        test("x{2147483648,4294967295}", "", "xx", 0, true, 0, 2);
        test("x{1,2147483648}", "", "xx", 0, false);
        test("x{2,2147483648}", "", "xx", 0, false);
        test("x{2,2147483649}", "", "xx", 0, false);
        test("x{4294967296}", "", "x{4294967296}", 0, true, 0, 13);
        test("x{4294967297}", "", "x{4294967297}", 0, true, 0, 13);
        test("x??", "", "x", 0, true, 0, 0);
        test("x???", "", "x", 0, true, 0, 1);
        test("x{2}+", "", "x", 0, false);
        test("x{2}+", "", "xx", 0, true, 0, 2);
        test("x{2}+", "", "xxx", 0, true, 0, 2);
        test("x{2}+", "", "xxxx", 0, true, 0, 4);
        test("x{2}*", "", "xxxx", 0, true, 0, 4);
        test("x{2}*?", "", "xxxx", 0, true, 0, 0);
        test("x{2}*??", "", "xxxx", 0, true, 0, 2);
        test("x{2}*???", "", "xxxx", 0, true, 0, 0);
        test("\\A*x\\Z+", "", "x", 0, true, 0, 1);
        test("\\A*x\\Z+", "", "xx", 0, true, 1, 2);
        test("\\A+x\\Z+", "", "xx", 0, false);
    }

    @Test
    public void testCharClasses() {
        test("[]]", "", "]", 0, true, 0, 1);
        test("[][]+", "", "[]", 0, true, 0, 2);
        expectSyntaxError("[]", "", "unmatched bracket in regular expression", 1);
        expectSyntaxError("[b-a]", "", "invalid range in regular expression", 3);
        test("[-a]", "", "-a", 0, true, 0, 1);
        test("[a-]", "", "-a", 0, true, 0, 1);
        test("[[ab]]", "", "[]ab]", 0, true, 0, 2);
        test("[[ab]]", "", "[]ab]", 1, true, 3, 5);
        test("[[::]]", "", "[]:", 0, true, 0, 1);
        test("[[::]]", "", "[]:", 1, true, 1, 2);
        test("[[::]]", "", "[]:", 2, true, 2, 3);
        expectSyntaxError("[[:ab:]]", "", "invalid character class in regular expression", 1);
        test("[[:upper:]]+", "", ":upper:", 0, false);
        test("[[:upper:]]+", "", ":UPPER:", 0, true, 1, 6);
        test("[:upper:]+", "", ":upper:", 0, true, 0, 7);
        test("[[:lower:]]+", "", ":LOWER:", 0, false);
        test("[[:lower:]]+", "", ":lower:", 0, true, 1, 6);
        test("[:lower:]+", "", ":lower:", 0, true, 0, 7);
        test("[x[ab]y]", "", "ay]", 0, true, 0, 3);
        test("[a\\]+", "", "a\\", 0, true, 0, 2);
        test("[\\a]+", "", "a\\", 0, true, 0, 2);
        test("[[:lower:]-[:upper:]]+", "", "a-A", 0, true, 0, 3);
        test("[a-z]+", "", "abc-", 0, true, 0, 3);
        test("[[.a.]-[.z.]]+", "", "abc-", 0, true, 0, 3);
        test("[[=a=]-[=z=]]+", "", "abc-", 0, true, 0, 3);
        test("[[.a.]-[=z=]]+", "", "abc-", 0, true, 0, 3);
        test("[[=a=]-[.z.]]+", "", "abc-", 0, true, 0, 3);
        expectSyntaxError("[[.a.]-[.ch.]]+", "", "invalid collation class in regular expression", 7);
        expectSyntaxError("[[.a.]-[:lower:]]+", "", "invalid range in regular expression", 7);
        expectSyntaxError("[[=a=]-[:lower:]]+", "", "invalid range in regular expression", 7);
        test("[[:upper:]-[.a.]]+", "", "a-A", 0, true, 0, 3);
        test("[[=a=]]", "", "\u00e4", 0, false);
        test("[[=c=]]", "", "\u010D", 0, false);
        test("[[=c=]-c]", "", "\u010d-=c", 0, true, 3, 4);
        test("[[=c=]-]+", "", "\u010d-=c", 0, true, 1, 2);
        // TODO: collator support
        // test("[[=a=]]", "", "\u00e4", 0, true, 0, 1);
        // test("[[=c=]]", "", "\u010D", 0, true, 0, 1);
        // test("[[=c=]-c]", "", "\u010d-=c", 0, true, 3, 4);
        // test("[[=c=]-]+", "", "\u010d-=c", 0, true, 0, 2);
    }

    @Test
    public void testBackReferences() {
        expectSyntaxError("(\\2())", "", "invalid back reference in regular expression", 1);
        test("(\\1a)", "", "aa", 0, false);
        test("(\\1a|){2}", "", "aa", 0, true, 0, 0, 0, 0);
        test("(\\1a|)*", "", "aa", 0, true, 0, 0, -1, -1);
        test("(()b|\\2a){2}", "", "ba", 0, true, 0, 2, 1, 2, 0, 0);
        test("(a\\1)", "", "aa", 0, false);
        test("(a|b\\1){2}", "", "aba", 0, true, 0, 3, 1, 3);
        test("(a|(b\\1)){2}", "", "aba", 0, true, 0, 3, 1, 3, 1, 3);
        test("((a)|b\\1){2}", "", "aba", 0, true, 0, 3, 1, 3, 0, 1);
        test("((a|b\\1)){2}", "", "aba", 0, true, 0, 3, 1, 3, 1, 3);
        test("((a|b\\2)){2}", "", "aba", 0, true, 0, 3, 1, 3, 1, 3);
        test("((a)|b\\2){2}", "", "aba", 0, true, 0, 3, 1, 3, 0, 1);
        test("((a)|b\\2)*", "", "aba", 0, true, 0, 3, 1, 3, 0, 1);
        test("(a|b\\1*){2}", "", "abaaaa", 0, true, 0, 6, 1, 6);
        test("^(a|\\1b)+$", "", "ab", 0, false);
        test("(a)\\10", "", "aa0", 0, true, 0, 3, 0, 1);
        test("(a)\\100", "", "aa00", 0, true, 0, 4, 0, 1);
    }

    @Test
    public void testIgnoreWhiteSpace() {
        test("a a", "x", "aa", 0, true, 0, 2);
        test("a\na", "x", "aa", 0, true, 0, 2);
        test("a\ta", "x", "aa", 0, false);
        test("a\fa", "x", "aa", 0, false);
        test("a\u000ba", "x", "aa", 0, false);
        test("a\ra", "x", "aa", 0, false);
    }

    @Test
    public void testEscapeSequences() {
        test("\\", "", "aa", 0, true, 0, 0);
        test("\\077", "", "?", 0, false);
        test("\\077", "", "077", 0, true, 0, 3);
        test("\\x61", "", "a", 0, false);
        test("\\x61", "", "x61", 0, true, 0, 3);
        test("\\u0061", "", "a", 0, false);
        test("\\u0061", "", "u0061", 0, true, 0, 5);
        for (char c : new char[]{'0', 'b', 'B', 'f', 'n', 'r', 't', 'v', 'x', 'u', 'p', 'P'}) {
            test("\\" + c, "", "\\" + c, 0, true, 1, 2);
        }
    }

    @Test
    public void testSpecialGroups() {
        for (String s : new String[]{":", "=", "!", "<=", "<!", "<a>", "P<a>", "P=a"}) {
            test(String.format("(%s)", s), "", s, 0, true, 0, s.length(), 0, s.length());
            test(String.format("(?%s)", s), "", "?" + s, 0, true, 1, s.length() + 1, 1, s.length() + 1);
        }
    }
}
