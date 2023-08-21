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
        test("x{2}+", "", "x", 0, false);
        test("x{2}+", "", "xx", 0, true, 0, 2);
        test("x{2}+", "", "xxx", 0, true, 0, 2);
        test("x{2}+", "", "xxxx", 0, true, 0, 4);
        test("x{2}*", "", "xxxx", 0, true, 0, 4);
        test("x{2}*?", "", "xxxx", 0, true, 0, 0);
        test("x{2}*???", "", "xxxx", 0, true, 0, 0);
        test("\\A*x\\Z+", "", "x", 0, true, 0, 1);
        test("\\A*x\\Z+", "", "xx", 0, true, 1, 2);
        test("\\A+x\\Z+", "", "xx", 0, false);
        test("x????", "", "x?", 0, true, 0, 0);
        test("x????", "", "xx?", 0, true, 0, 0);
        test("x??????", "", "x?", 0, true, 0, 0);
        test("x??????", "", "xx?", 0, true, 0, 0);
        test("x{2}?", "", "xxxxx", 0, true, 0, 2);
        test("x{2}??", "", "xxxxx", 0, true, 0, 2);
        test("x{2}+", "", "xxxxx", 0, true, 0, 4);
        test("x{2}*", "", "xxxxx", 0, true, 0, 4);

        // known to fail, suspected to be caused by LXR bug 35718208

        // test("x???", "", "x", 0, true, 0, 1);
        // test("x{2}*??", "", "xxxx", 0, true, 0, 2);
        // test("x???", "", "x?", 0, true, 0, 1);
        // test("x???", "", "xx?", 0, true, 0, 1);
        // test("x?????", "", "x?", 0, true, 0, 1);
        // test("x?????", "", "xx?", 0, true, 0, 1);
        // test("(a{0,1})*b\\1", "", "aab", 0, true, 1, 3, 2, 2);
        // test("(a{0,1})*b\\1", "", "aaba", 0, true, 1, 3, 2, 2);
        // test("(a{0,1})*b\\1", "", "aabaa", 0, true, 1, 3, 2, 2);
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
        test("[[=c=]-c]", "", "\u010d-=c", 0, true, 3, 4);
        test("[[=c=]-]+", "", "\u010d-=c", 0, true, 0, 2);
    }

    @Test
    public void testBackReferences() {
        expectSyntaxError("(\\2())", "", "invalid back reference in regular expression", 1);
        test("(\\1a)", "", "aa", 0, false);
        test("(\\1a|){2}", "", "aa", 0, true, 0, 0, 0, 0);
        test("(\\1a|)*", "", "aa", 0, true, 0, 0, 0, 0);
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

    @Test
    public void generatedTests() {
        /* GENERATED CODE BEGIN - KEEP THIS MARKER FOR AUTOMATIC UPDATES */
        test("abracadabra$", "", "abracadabracadabra", 0, true, 7, 18);
        test("a...b", "", "abababbb", 0, true, 2, 7);
        test("XXXXXX", "", "..XXXXXX", 0, true, 2, 8);
        test("\\)", "", "()", 0, true, 1, 2);
        test("a]", "", "a]a", 0, true, 0, 2);
        test("}", "", "}", 0, true, 0, 1);
        test("\\}", "", "}", 0, true, 0, 1);
        test("\\]", "", "]", 0, true, 0, 1);
        test("]", "", "]", 0, true, 0, 1);
        test("]", "", "]", 0, true, 0, 1);
        test("{", "", "{", 0, true, 0, 1);
        test("}", "", "}", 0, true, 0, 1);
        test("^a", "", "ax", 0, true, 0, 1);
        test("\\^a", "", "a^a", 0, true, 1, 3);
        test("a\\^", "", "a^", 0, true, 0, 2);
        test("a$", "", "aa", 0, true, 1, 2);
        test("a\\$", "", "a$", 0, true, 0, 2);
        test("a($)", "", "aa", 0, true, 1, 2, 2, 2);
        test("a*(^a)", "", "aa", 0, true, 0, 1, 0, 1);
        test("(..)*(...)*", "", "a", 0, true, 0, 0, -1, -1, -1, -1);
        test("(..)*(...)*", "", "abcd", 0, true, 0, 4, 2, 4, -1, -1);
        test("(ab|a)(bc|c)", "", "abc", 0, true, 0, 3, 0, 2, 2, 3);
        test("(ab)c|abc", "", "abc", 0, true, 0, 3, 0, 2);
        test("a{0}b", "", "ab", 0, true, 1, 2);
        test("(a*)(b?)(b+)b{3}", "", "aaabbbbbbb", 0, true, 0, 10, 0, 3, 3, 4, 4, 7);
        test("(a*)(b{0,1})(b{1,})b{3}", "", "aaabbbbbbb", 0, true, 0, 10, 0, 3, 3, 4, 4, 7);
        test("a{9876543210}", "", "a", 0, false);
        test("((a|a)|a)", "", "a", 0, true, 0, 1, 0, 1, 0, 1);
        test("(a*)(a|aa)", "", "aaaa", 0, true, 0, 4, 0, 3, 3, 4);
        test("a*(a.|aa)", "", "aaaa", 0, true, 0, 4, 2, 4);
        test("a(b)|c(d)|a(e)f", "", "aef", 0, true, 0, 3, -1, -1, -1, -1, 1, 2);
        test("(a|b)?.*", "", "b", 0, true, 0, 1, 0, 1);
        test("(a|b)c|a(b|c)", "", "ac", 0, true, 0, 2, 0, 1, -1, -1);
        test("(a|b)c|a(b|c)", "", "ab", 0, true, 0, 2, -1, -1, 1, 2);
        test("(a|b)*c|(a|ab)*c", "", "abc", 0, true, 0, 3, 1, 2, -1, -1);
        test("(a|b)*c|(a|ab)*c", "", "xc", 0, true, 1, 2, -1, -1, -1, -1);
        test("(.a|.b).*|.*(.a|.b)", "", "xa", 0, true, 0, 2, 0, 2, -1, -1);
        test("a?(ab|ba)ab", "", "abab", 0, true, 0, 4, 0, 2);
        test("a?(ac{0}b|ba)ab", "", "abab", 0, true, 0, 4, 0, 2);
        test("ab|abab", "", "abbabab", 0, true, 0, 2);
        test("aba|bab|bba", "", "baaabbbaba", 0, true, 5, 8);
        test("aba|bab", "", "baaabbbaba", 0, true, 6, 9);
        test("(aa|aaa)*|(a|aaaaa)", "", "aa", 0, true, 0, 2, 0, 2, -1, -1);
        test("(a.|.a.)*|(a|.a...)", "", "aa", 0, true, 0, 2, 0, 2, -1, -1);
        test("ab|a", "", "xabc", 0, true, 1, 3);
        test("ab|a", "", "xxabc", 0, true, 2, 4);
        test("(Ab|cD)*", "", "aBcD", 0, true, 0, 0, -1, -1);
        test("[^-]", "", "--a", 0, true, 2, 3);
        test("[a-]*", "", "--a", 0, true, 0, 3);
        test("[a-m-]*", "", "--amoma--", 0, true, 0, 4);
        test(":::1:::0:|:::1:1:0:", "", ":::0:::1:::1:::0:", 0, true, 8, 17);
        test(":::1:::0:|:::1:1:1:", "", ":::0:::1:::1:::0:", 0, true, 8, 17);
        test("[[:upper:]]", "", "A", 0, true, 0, 1);
        test("[[:lower:]]+", "", "`az{", 0, true, 1, 3);
        test("[[:upper:]]+", "", "@AZ[", 0, true, 1, 3);
        test("[[-]]", "", "[[-]]", 0, true, 2, 4);
        test("\\n", "", "\\n", 0, true, 1, 2);
        test("\\n", "", "\\n", 0, true, 1, 2);
        test("[^a]", "", "\\n", 0, true, 0, 1);
        test("\\na", "", "\\na", 0, true, 1, 3);
        test("(a)(b)(c)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("xxx", "", "xxx", 0, true, 0, 3);
        test("(^|[ (,;])((([Ff]eb[^ ]* *|0*2/|\\* */?)0*[6-7]))([^0-9]|$)", "", "feb 6,", 0, true, 0, 6, 0, 0, 0, 5, 0, 5, 0, 4, 5, 6);
        test("(^|[ (,;])((([Ff]eb[^ ]* *|0*2/|\\* */?)0*[6-7]))([^0-9]|$)", "", "2/7", 0, true, 0, 3, 0, 0, 0, 3, 0, 3, 0, 2, 3, 3);
        test("(^|[ (,;])((([Ff]eb[^ ]* *|0*2/|\\* */?)0*[6-7]))([^0-9]|$)", "", "feb 1,Feb 6", 0, true, 5, 11, 5, 6, 6, 11, 6, 11, 6, 10, 11, 11);
        test("((((((((((((((((((((((((((((((x))))))))))))))))))))))))))))))", "", "x", 0, true, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1);
        test("((((((((((((((((((((((((((((((x))))))))))))))))))))))))))))))*", "", "xx", 0, true, 0, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2);
        test("a?(ab|ba)*", "", "ababababababababababababababababababababababababababababababababababababababababa", 0, true, 0, 81, 79, 81);
        test("abaa|abbaa|abbbaa|abbbbaa", "", "ababbabbbabbbabbbbabbbbaa", 0, true, 18, 25);
        test("abaa|abbaa|abbbaa|abbbbaa", "", "ababbabbbabbbabbbbabaa", 0, true, 18, 22);
        test("aaac|aabc|abac|abbc|baac|babc|bbac|bbbc", "", "baaabbbabac", 0, true, 7, 11);
        test(".*", "", "\\x01\\xff", 0, true, 0, 8);
        test("aaaa|bbbb|cccc|ddddd|eeeeee|fffffff|gggg|hhhh|iiiii|jjjjj|kkkkk|llll", "", "XaaaXbbbXcccXdddXeeeXfffXgggXhhhXiiiXjjjXkkkXlllXcbaXaaaa", 0, true, 53, 57);
        test("aaaa\\nbbbb\\ncccc\\nddddd\\neeeeee\\nfffffff\\ngggg\\nhhhh\\niiiii\\njjjjj\\nkkkkk\\nllll", "", "XaaaXbbbXcccXdddXeeeXfffXgggXhhhXiiiXjjjXkkkXlllXcbaXaaaa", 0, false);
        test("a*a*a*a*a*b", "", "aaaaaaaaab", 0, true, 0, 10);
        test("^", "", "a", 0, true, 0, 0);
        test("$", "", "a", 0, true, 1, 1);
        test("^$", "", "a", 0, false);
        test("^a$", "", "a", 0, true, 0, 1);
        test("abc", "", "abc", 0, true, 0, 3);
        test("abc", "", "xabcy", 0, true, 1, 4);
        test("abc", "", "ababc", 0, true, 2, 5);
        test("ab*c", "", "abc", 0, true, 0, 3);
        test("ab*bc", "", "abc", 0, true, 0, 3);
        test("ab*bc", "", "abbc", 0, true, 0, 4);
        test("ab*bc", "", "abbbbc", 0, true, 0, 6);
        test("ab+bc", "", "abbc", 0, true, 0, 4);
        test("ab+bc", "", "abbbbc", 0, true, 0, 6);
        test("ab?bc", "", "abbc", 0, true, 0, 4);
        test("ab?bc", "", "abc", 0, true, 0, 3);
        test("ab?c", "", "abc", 0, true, 0, 3);
        test("^abc$", "", "abc", 0, true, 0, 3);
        test("^abc", "", "abcc", 0, true, 0, 3);
        test("abc$", "", "aabc", 0, true, 1, 4);
        test("^", "", "abc", 0, true, 0, 0);
        test("$", "", "abc", 0, true, 3, 3);
        test("a.c", "", "abc", 0, true, 0, 3);
        test("a.c", "", "axc", 0, true, 0, 3);
        test("a.*c", "", "axyzc", 0, true, 0, 5);
        test("a[bc]d", "", "abd", 0, true, 0, 3);
        test("a[b-d]e", "", "ace", 0, true, 0, 3);
        test("a[b-d]", "", "aac", 0, true, 1, 3);
        test("a[-b]", "", "a-", 0, true, 0, 2);
        test("a[b-]", "", "a-", 0, true, 0, 2);
        test("a]", "", "a]", 0, true, 0, 2);
        test("a[]]b", "", "a]b", 0, true, 0, 3);
        test("a[^bc]d", "", "aed", 0, true, 0, 3);
        test("a[^-b]c", "", "adc", 0, true, 0, 3);
        test("a[^]b]c", "", "adc", 0, true, 0, 3);
        test("ab|cd", "", "abc", 0, true, 0, 2);
        test("ab|cd", "", "abcd", 0, true, 0, 2);
        test("a\\(b", "", "a(b", 0, true, 0, 3);
        test("a\\(*b", "", "ab", 0, true, 0, 2);
        test("a\\(*b", "", "a((b", 0, true, 0, 4);
        test("((a))", "", "abc", 0, true, 0, 1, 0, 1, 0, 1);
        test("(a)b(c)", "", "abc", 0, true, 0, 3, 0, 1, 2, 3);
        test("a+b+c", "", "aabbabc", 0, true, 4, 7);
        test("a*", "", "aaa", 0, true, 0, 3);
        test("(a*)*", "", "-", 0, true, 0, 0, 0, 0);
        test("(a*)+", "", "-", 0, true, 0, 0, 0, 0);
        test("(a*|b)*", "", "-", 0, true, 0, 0, 0, 0);
        test("(a+|b)*", "", "ab", 0, true, 0, 2, 1, 2);
        test("(a+|b)+", "", "ab", 0, true, 0, 2, 1, 2);
        test("(a+|b)?", "", "ab", 0, true, 0, 1, 0, 1);
        test("[^ab]*", "", "cde", 0, true, 0, 3);
        test("(^)*", "", "-", 0, true, 0, 0, 0, 0);
        test("a*", "", "a", 0, true, 0, 1);
        test("([abc])*d", "", "abbbcd", 0, true, 0, 6, 4, 5);
        test("([abc])*bcd", "", "abcd", 0, true, 0, 4, 0, 1);
        test("a|b|c|d|e", "", "e", 0, true, 0, 1);
        test("(a|b|c|d|e)f", "", "ef", 0, true, 0, 2, 0, 1);
        test("((a*|b))*", "", "-", 0, true, 0, 0, 0, 0, 0, 0);
        test("abcd*efg", "", "abcdefg", 0, true, 0, 7);
        test("ab*", "", "xabyabbbz", 0, true, 1, 3);
        test("ab*", "", "xayabbbz", 0, true, 1, 2);
        test("(ab|cd)e", "", "abcde", 0, true, 2, 5, 2, 4);
        test("[abhgefdc]ij", "", "hij", 0, true, 0, 3);
        test("(a|b)c*d", "", "abcd", 0, true, 1, 4, 1, 2);
        test("(ab|ab*)bc", "", "abc", 0, true, 0, 3, 0, 1);
        test("a([bc]*)c*", "", "abc", 0, true, 0, 3, 1, 3);
        test("a([bc]*)(c*d)", "", "abcd", 0, true, 0, 4, 1, 3, 3, 4);
        test("a([bc]+)(c*d)", "", "abcd", 0, true, 0, 4, 1, 3, 3, 4);
        test("a([bc]*)(c+d)", "", "abcd", 0, true, 0, 4, 1, 2, 2, 4);
        test("a[bcd]*dcdcde", "", "adcdcde", 0, true, 0, 7);
        test("(ab|a)b*c", "", "abc", 0, true, 0, 3, 0, 2);
        test("((a)(b)c)(d)", "", "abcd", 0, true, 0, 4, 0, 3, 0, 1, 1, 2, 3, 4);
        test("[A-Za-z_][A-Za-z0-9_]*", "", "alpha", 0, true, 0, 5);
        test("^a(bc+|b[eh])g|.h$", "", "abh", 0, true, 1, 3, -1, -1);
        test("(bc+d$|ef*g.|h?i(j|k))", "", "effgz", 0, true, 0, 5, 0, 5, -1, -1);
        test("(bc+d$|ef*g.|h?i(j|k))", "", "ij", 0, true, 0, 2, 0, 2, 1, 2);
        test("(bc+d$|ef*g.|h?i(j|k))", "", "reffgz", 0, true, 1, 6, 1, 6, -1, -1);
        test("(((((((((a)))))))))", "", "a", 0, true, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1);
        test("multiple words", "", "multiple words yeah", 0, true, 0, 14);
        test("(.*)c(.*)", "", "abcde", 0, true, 0, 5, 0, 2, 3, 5);
        test("abcd", "", "abcd", 0, true, 0, 4);
        test("a(bc)d", "", "abcd", 0, true, 0, 4, 1, 3);
        test("a[\u0001-\u0003]?c", "", "a\u0002c", 0, true, 0, 3);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Qaddafi", 0, true, 0, 15, -1, -1, 10, 12);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Mo'ammar Gadhafi", 0, true, 0, 16, -1, -1, 11, 13);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Kaddafi", 0, true, 0, 15, -1, -1, 10, 12);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Qadhafi", 0, true, 0, 15, -1, -1, 10, 12);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Gadafi", 0, true, 0, 14, -1, -1, 10, 11);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Mu'ammar Qadafi", 0, true, 0, 15, -1, -1, 11, 12);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Moamar Gaddafi", 0, true, 0, 14, -1, -1, 9, 11);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Mu'ammar Qadhdhafi", 0, true, 0, 18, -1, -1, 13, 15);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Khaddafi", 0, true, 0, 16, -1, -1, 11, 13);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Ghaddafy", 0, true, 0, 16, -1, -1, 11, 13);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Ghadafi", 0, true, 0, 15, -1, -1, 11, 12);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Ghaddafi", 0, true, 0, 16, -1, -1, 11, 13);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muamar Kaddafi", 0, true, 0, 14, -1, -1, 9, 11);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Quathafi", 0, true, 0, 16, -1, -1, 11, 13);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Muammar Gheddafi", 0, true, 0, 16, -1, -1, 11, 13);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Moammar Khadafy", 0, true, 0, 15, -1, -1, 11, 12);
        test("M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]", "", "Moammar Qudhafi", 0, true, 0, 15, -1, -1, 10, 12);
        test("a+(b|c)*d+", "", "aabcdd", 0, true, 0, 6, 3, 4);
        test("^.+$", "", "vivi", 0, true, 0, 4);
        test("^(.+)$", "", "vivi", 0, true, 0, 4, 0, 4);
        test("^([^!.]+).att.com!(.+)$", "", "gryphon.att.com!eby", 0, true, 0, 19, 0, 7, 16, 19);
        test("^([^!]+!)?([^!]+)$", "", "bas", 0, true, 0, 3, -1, -1, 0, 3);
        test("^([^!]+!)?([^!]+)$", "", "bar!bas", 0, true, 0, 7, 0, 4, 4, 7);
        test("^([^!]+!)?([^!]+)$", "", "foo!bas", 0, true, 0, 7, 0, 4, 4, 7);
        test("^.+!([^!]+!)([^!]+)$", "", "foo!bar!bas", 0, true, 0, 11, 4, 8, 8, 11);
        test("((foo)|(bar))!bas", "", "bar!bas", 0, true, 0, 7, 0, 3, -1, -1, 0, 3);
        test("((foo)|(bar))!bas", "", "foo!bar!bas", 0, true, 4, 11, 4, 7, -1, -1, 4, 7);
        test("((foo)|(bar))!bas", "", "foo!bas", 0, true, 0, 7, 0, 3, 0, 3, -1, -1);
        test("((foo)|bar)!bas", "", "bar!bas", 0, true, 0, 7, 0, 3, -1, -1);
        test("((foo)|bar)!bas", "", "foo!bar!bas", 0, true, 4, 11, 4, 7, -1, -1);
        test("((foo)|bar)!bas", "", "foo!bas", 0, true, 0, 7, 0, 3, 0, 3);
        test("(foo|(bar))!bas", "", "bar!bas", 0, true, 0, 7, 0, 3, 0, 3);
        test("(foo|(bar))!bas", "", "foo!bar!bas", 0, true, 4, 11, 4, 7, 4, 7);
        test("(foo|(bar))!bas", "", "foo!bas", 0, true, 0, 7, 0, 3, -1, -1);
        test("(foo|bar)!bas", "", "bar!bas", 0, true, 0, 7, 0, 3);
        test("(foo|bar)!bas", "", "foo!bar!bas", 0, true, 4, 11, 4, 7);
        test("(foo|bar)!bas", "", "foo!bas", 0, true, 0, 7, 0, 3);
        test("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "foo!bar!bas", 0, true, 0, 11, 0, 11, -1, -1, -1, -1, 4, 8, 8, 11);
        test("^([^!]+!)?([^!]+)$|^.+!([^!]+!)([^!]+)$", "", "bas", 0, true, 0, 3, -1, -1, 0, 3, -1, -1, -1, -1);
        test("^([^!]+!)?([^!]+)$|^.+!([^!]+!)([^!]+)$", "", "bar!bas", 0, true, 0, 7, 0, 4, 4, 7, -1, -1, -1, -1);
        test("^([^!]+!)?([^!]+)$|^.+!([^!]+!)([^!]+)$", "", "foo!bar!bas", 0, true, 0, 11, -1, -1, -1, -1, 4, 8, 8, 11);
        test("^([^!]+!)?([^!]+)$|^.+!([^!]+!)([^!]+)$", "", "foo!bas", 0, true, 0, 7, 0, 4, 4, 7, -1, -1, -1, -1);
        test("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "bas", 0, true, 0, 3, 0, 3, -1, -1, 0, 3, -1, -1, -1, -1);
        test("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "bar!bas", 0, true, 0, 7, 0, 7, 0, 4, 4, 7, -1, -1, -1, -1);
        test("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "foo!bar!bas", 0, true, 0, 11, 0, 11, -1, -1, -1, -1, 4, 8, 8, 11);
        test("^(([^!]+!)?([^!]+)|.+!([^!]+!)([^!]+))$", "", "foo!bas", 0, true, 0, 7, 0, 7, 0, 4, 4, 7, -1, -1, -1, -1);
        test(".*(/XXX).*", "", "/XXX", 0, true, 0, 4, 0, 4);
        test(".*(\\\\XXX).*", "", "\\XXX", 0, true, 0, 4, 0, 4);
        test("\\\\XXX", "", "\\XXX", 0, true, 0, 4);
        test(".*(/000).*", "", "/000", 0, true, 0, 4, 0, 4);
        test(".*(\\\\000).*", "", "\\000", 0, true, 0, 4, 0, 4);
        test("\\\\000", "", "\\000", 0, true, 0, 4);
        test("aa*", "", "xaxaax", 0, true, 1, 2);
        test("(a*)(ab)*(b*)", "", "abc", 0, true, 0, 2, 0, 1, -1, -1, 1, 2);
        test("(a*)(ab)*(b*)", "", "abc", 0, true, 0, 2, 0, 1, -1, -1, 1, 2);
        test("((a*)(ab)*)((b*)(a*))", "", "aba", 0, true, 0, 3, 0, 1, 0, 1, -1, -1, 1, 3, 1, 2, 2, 3);
        test("((a*)(ab)*)((b*)(a*))", "", "aba", 0, true, 0, 3, 0, 1, 0, 1, -1, -1, 1, 3, 1, 2, 2, 3);
        test("(...?.?)*", "", "xxxxxx", 0, true, 0, 6, 4, 6);
        test("(...?.?)*", "", "xxxxxx", 0, true, 0, 6, 4, 6);
        test("(...?.?)*", "", "xxxxxx", 0, true, 0, 6, 4, 6);
        test("(a|ab)(bc|c)", "", "abcabc", 0, true, 0, 3, 0, 1, 1, 3);
        test("(a|ab)(bc|c)", "", "abcabc", 0, true, 0, 3, 0, 1, 1, 3);
        test("(aba|a*b)(aba|a*b)", "", "ababa", 0, true, 0, 4, 0, 3, 3, 4);
        test("(aba|a*b)(aba|a*b)", "", "ababa", 0, true, 0, 4, 0, 3, 3, 4);
        test("a(b)*\\1", "", "a", 0, false);
        test("a(b)*\\1", "", "a", 0, false);
        test("a(b)*\\1", "", "abab", 0, false);
        test("(a*){2}", "", "xxxxx", 0, true, 0, 0, 0, 0);
        test("(a*){2}", "", "xxxxx", 0, true, 0, 0, 0, 0);
        test("a(b)*\\1", "", "abab", 0, false);
        test("a(b)*\\1", "", "abab", 0, false);
        test("a(b)*\\1", "", "abab", 0, false);
        test("(a*)*", "", "a", 0, true, 0, 1, 1, 1);
        test("(a*)*", "", "ax", 0, true, 0, 1, 1, 1);
        test("(a*)*", "", "a", 0, true, 0, 1, 1, 1);
        test("(aba|a*b)*", "", "ababa", 0, true, 0, 4, 3, 4);
        test("(aba|a*b)*", "", "ababa", 0, true, 0, 4, 3, 4);
        test("(aba|a*b)*", "", "ababa", 0, true, 0, 4, 3, 4);
        test("(a(b)?)+", "", "aba", 0, true, 0, 3, 2, 3, 1, 2);
        test("(a(b)?)+", "", "aba", 0, true, 0, 3, 2, 3, 1, 2);
        test("(a(b)*)*\\2", "", "abab", 0, true, 0, 4, 2, 3, 1, 2);
        test("(a(b)*)*\\2", "", "abab", 0, true, 0, 4, 2, 3, 1, 2);
        test("(a?)((ab)?)(b?)a?(ab)?b?", "", "abab", 0, true, 0, 4, 0, 1, 1, 1, -1, -1, 1, 2, -1, -1);
        test(".*(.*)", "", "ab", 0, true, 0, 2, 2, 2);
        test(".*(.*)", "", "ab", 0, true, 0, 2, 2, 2);
        test("(a|ab)(c|bcd)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4);
        test("(a|ab)(bcd|c)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4);
        test("(ab|a)(c|bcd)", "", "abcd", 0, true, 0, 3, 0, 2, 2, 3);
        test("(ab|a)(bcd|c)", "", "abcd", 0, true, 0, 3, 0, 2, 2, 3);
        test("((a|ab)(c|bcd))(d*)", "", "abcd", 0, true, 0, 4, 0, 4, 0, 1, 1, 4, 4, 4);
        test("((a|ab)(bcd|c))(d*)", "", "abcd", 0, true, 0, 4, 0, 4, 0, 1, 1, 4, 4, 4);
        test("((ab|a)(c|bcd))(d*)", "", "abcd", 0, true, 0, 4, 0, 3, 0, 2, 2, 3, 3, 4);
        test("((ab|a)(bcd|c))(d*)", "", "abcd", 0, true, 0, 4, 0, 3, 0, 2, 2, 3, 3, 4);
        test("(a|ab)((c|bcd)(d*))", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 1, 4, 4, 4);
        test("(a|ab)((bcd|c)(d*))", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 1, 4, 4, 4);
        test("(ab|a)((c|bcd)(d*))", "", "abcd", 0, true, 0, 4, 0, 2, 2, 4, 2, 3, 3, 4);
        test("(ab|a)((bcd|c)(d*))", "", "abcd", 0, true, 0, 4, 0, 2, 2, 4, 2, 3, 3, 4);
        test("(a*)(b|abc)", "", "abc", 0, true, 0, 2, 0, 1, 1, 2);
        test("(a*)(abc|b)", "", "abc", 0, true, 0, 2, 0, 1, 1, 2);
        test("((a*)(b|abc))(c*)", "", "abc", 0, true, 0, 3, 0, 2, 0, 1, 1, 2, 2, 3);
        test("((a*)(abc|b))(c*)", "", "abc", 0, true, 0, 3, 0, 2, 0, 1, 1, 2, 2, 3);
        test("(a*)((b|abc)(c*))", "", "abc", 0, true, 0, 3, 0, 1, 1, 3, 1, 2, 2, 3);
        test("(a*)((abc|b)(c*))", "", "abc", 0, true, 0, 3, 0, 1, 1, 3, 1, 2, 2, 3);
        test("(a*)(b|abc)", "", "abc", 0, true, 0, 2, 0, 1, 1, 2);
        test("(a*)(abc|b)", "", "abc", 0, true, 0, 2, 0, 1, 1, 2);
        test("((a*)(b|abc))(c*)", "", "abc", 0, true, 0, 3, 0, 2, 0, 1, 1, 2, 2, 3);
        test("((a*)(abc|b))(c*)", "", "abc", 0, true, 0, 3, 0, 2, 0, 1, 1, 2, 2, 3);
        test("(a*)((b|abc)(c*))", "", "abc", 0, true, 0, 3, 0, 1, 1, 3, 1, 2, 2, 3);
        test("(a*)((abc|b)(c*))", "", "abc", 0, true, 0, 3, 0, 1, 1, 3, 1, 2, 2, 3);
        test("(a|ab)", "", "ab", 0, true, 0, 1, 0, 1);
        test("(ab|a)", "", "ab", 0, true, 0, 2, 0, 2);
        test("(a|ab)(b*)", "", "ab", 0, true, 0, 2, 0, 1, 1, 2);
        test("(ab|a)(b*)", "", "ab", 0, true, 0, 2, 0, 2, 2, 2);
        test("a+", "", "xaax", 0, true, 1, 3);
        test(".(a*).", "", "xaax", 0, true, 0, 4, 1, 3);
        test("(a?)((ab)?)", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, -1, -1);
        test("(a?)((ab)?)(b?)", "", "ab", 0, true, 0, 2, 0, 1, 1, 1, -1, -1, 1, 2);
        test("((a?)((ab)?))(b?)", "", "ab", 0, true, 0, 2, 0, 1, 0, 1, 1, 1, -1, -1, 1, 2);
        test("(a?)(((ab)?)(b?))", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 1, 1, -1, -1, 1, 2);
        test("(.?)", "", "x", 0, true, 0, 1, 0, 1);
        test("(.?){1}", "", "x", 0, true, 0, 1, 0, 1);
        test("(.?)(.?)", "", "x", 0, true, 0, 1, 0, 1, 1, 1);
        test("(.?){2}", "", "x", 0, true, 0, 1, 1, 1);
        test("(.?)*", "", "x", 0, true, 0, 1, 1, 1);
        test("(.?.?)", "", "xxx", 0, true, 0, 2, 0, 2);
        test("(.?.?){1}", "", "xxx", 0, true, 0, 2, 0, 2);
        test("(.?.?)(.?.?)", "", "xxx", 0, true, 0, 3, 0, 2, 2, 3);
        test("(.?.?){2}", "", "xxx", 0, true, 0, 3, 2, 3);
        test("(.?.?)(.?.?)(.?.?)", "", "xxx", 0, true, 0, 3, 0, 2, 2, 3, 3, 3);
        test("(.?.?){3}", "", "xxx", 0, true, 0, 3, 3, 3);
        test("(.?.?)*", "", "xxx", 0, true, 0, 3, 3, 3);
        test("a?((ab)?)(b?)", "", "ab", 0, true, 0, 2, 1, 1, -1, -1, 1, 2);
        test("(a?)((ab)?)b?", "", "ab", 0, true, 0, 2, 0, 1, 1, 1, -1, -1);
        test("a?((ab)?)b?", "", "ab", 0, true, 0, 2, 1, 1, -1, -1);
        test("(a*){2}", "", "xxxxx", 0, true, 0, 0, 0, 0);
        test("(ab?)(b?a)", "", "aba", 0, true, 0, 3, 0, 2, 2, 3);
        test("(a|ab)(ba|a)", "", "aba", 0, true, 0, 3, 0, 1, 1, 3);
        test("(a|ab|ba)", "", "aba", 0, true, 0, 1, 0, 1);
        test("(a|ab|ba)(a|ab|ba)", "", "aba", 0, true, 0, 3, 0, 1, 1, 3);
        test("(a|ab|ba)*", "", "aba", 0, true, 0, 3, 1, 3);
        test("(aba|a*b)", "", "ababa", 0, true, 0, 3, 0, 3);
        test("(aba|a*b)(aba|a*b)", "", "ababa", 0, true, 0, 4, 0, 3, 3, 4);
        test("(aba|a*b)*", "", "ababa", 0, true, 0, 4, 3, 4);
        test("(aba|ab|a)", "", "ababa", 0, true, 0, 3, 0, 3);
        test("(aba|ab|a)(aba|ab|a)", "", "ababa", 0, true, 0, 5, 0, 2, 2, 5);
        test("(aba|ab|a)*", "", "ababa", 0, true, 0, 3, 0, 3);
        test("(a(b)?)", "", "aba", 0, true, 0, 2, 0, 2, 1, 2);
        test("(a(b)?)(a(b)?)", "", "aba", 0, true, 0, 3, 0, 2, 1, 2, 2, 3, -1, -1);
        test("(a(b)?)+", "", "aba", 0, true, 0, 3, 2, 3, 1, 2);
        test("(.*)(.*)", "", "xx", 0, true, 0, 2, 0, 2, 2, 2);
        test(".*(.*)", "", "xx", 0, true, 0, 2, 2, 2);
        test("(a.*z|b.*y)", "", "azbazby", 0, true, 0, 5, 0, 5);
        test("(a.*z|b.*y)(a.*z|b.*y)", "", "azbazby", 0, true, 0, 7, 0, 5, 5, 7);
        test("(a.*z|b.*y)*", "", "azbazby", 0, true, 0, 7, 5, 7);
        test("(.|..)(.*)", "", "ab", 0, true, 0, 2, 0, 1, 1, 2);
        test("((..)*(...)*)", "", "xxx", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)*(...)*)((..)*(...)*)", "", "xxx", 0, true, 0, 2, 0, 2, 0, 2, -1, -1, 2, 2, -1, -1, -1, -1);
        test("((..)*(...)*)*", "", "xxx", 0, true, 0, 2, 2, 2, 0, 2, -1, -1);
        test("(a{0,1})*b\\1", "", "ab", 0, true, 0, 2, 1, 1);
        test("(a*)*b\\1", "", "ab", 0, true, 0, 2, 1, 1);
        test("(a*)b\\1*", "", "ab", 0, true, 0, 2, 0, 1);
        test("(a*)*b\\1*", "", "ab", 0, true, 0, 2, 1, 1);
        test("(a{0,1})*b(\\1)", "", "ab", 0, true, 0, 2, 1, 1, 2, 2);
        test("(a*)*b(\\1)", "", "ab", 0, true, 0, 2, 1, 1, 2, 2);
        test("(a*)b(\\1)*", "", "ab", 0, true, 0, 2, 0, 1, -1, -1);
        test("(a*)*b(\\1)*", "", "ab", 0, true, 0, 2, 1, 1, 2, 2);
        test("(a{0,1})*b\\1", "", "aba", 0, true, 0, 2, 1, 1);
        test("(a*)*b\\1", "", "aba", 0, true, 0, 2, 1, 1);
        test("(a*)b\\1*", "", "aba", 0, true, 0, 3, 0, 1);
        test("(a*)*b\\1*", "", "aba", 0, true, 0, 2, 1, 1);
        test("(a*)*b(\\1)*", "", "aba", 0, true, 0, 2, 1, 1, 2, 2);
        test("(a{0,1})*b\\1", "", "abaa", 0, true, 0, 2, 1, 1);
        test("(a*)*b\\1", "", "abaa", 0, true, 0, 2, 1, 1);
        test("(a*)b\\1*", "", "abaa", 0, true, 0, 4, 0, 1);
        test("(a*)*b\\1*", "", "abaa", 0, true, 0, 2, 1, 1);
        test("(a*)*b(\\1)*", "", "abaa", 0, true, 0, 2, 1, 1, 2, 2);
        test("(a*)*b\\1", "", "aab", 0, true, 0, 3, 2, 2);
        test("(a*)b\\1*", "", "aab", 0, true, 0, 3, 0, 2);
        test("(a*)*b\\1*", "", "aab", 0, true, 0, 3, 2, 2);
        test("(a*)*b(\\1)*", "", "aab", 0, true, 0, 3, 2, 2, 3, 3);
        test("(a*)*b\\1", "", "aaba", 0, true, 0, 3, 2, 2);
        test("(a*)b\\1*", "", "aaba", 0, true, 0, 3, 0, 2);
        test("(a*)*b\\1*", "", "aaba", 0, true, 0, 3, 2, 2);
        test("(a*)*b(\\1)*", "", "aaba", 0, true, 0, 3, 2, 2, 3, 3);
        test("(a*)*b\\1", "", "aabaa", 0, true, 0, 3, 2, 2);
        test("(a*)b\\1*", "", "aabaa", 0, true, 0, 5, 0, 2);
        test("(a*)*b\\1*", "", "aabaa", 0, true, 0, 3, 2, 2);
        test("(a*)*b(\\1)*", "", "aabaa", 0, true, 0, 3, 2, 2, 3, 3);
        test("(x)*a\\1", "", "a", 0, false);
        test("(x)*a\\1*", "", "a", 0, true, 0, 1, -1, -1);
        test("(x)*a(\\1)", "", "a", 0, false);
        test("(x)*a(\\1)*", "", "a", 0, true, 0, 1, -1, -1, -1, -1);
        test("(aa(b(b))?)+", "", "aabbaa", 0, true, 0, 6, 4, 6, 2, 4, 3, 4);
        test("(a(b)?)+", "", "aba", 0, true, 0, 3, 2, 3, 1, 2);
        test("([ab]+)([bc]+)([cd]*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("([ab]*)([bc]*)([cd]*)\\1", "", "abcdaa", 0, true, 0, 5, 0, 1, 1, 3, 3, 4);
        test("([ab]*)([bc]*)([cd]*)\\1", "", "abcdab", 0, true, 0, 6, 0, 2, 2, 3, 3, 4);
        test("([ab]*)([bc]*)([cd]*)\\1*", "", "abcdaa", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("([ab]*)([bc]*)([cd]*)\\1*", "", "abcdab", 0, true, 0, 6, 0, 2, 2, 3, 3, 4);
        test("^(A([^B]*))?(B(.*))?", "", "Aa", 0, true, 0, 2, 0, 2, 1, 2, -1, -1, -1, -1);
        test("^(A([^B]*))?(B(.*))?", "", "Bb", 0, true, 0, 2, -1, -1, -1, -1, 0, 2, 1, 2);
        test(".*([AB]).*\\1", "", "ABA", 0, true, 0, 3, 0, 1);
        test("[^A]*A", "", "\\nA", 0, true, 0, 3);
        test("(a|ab)(c|bcd)(d*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(a|ab)(bcd|c)(d*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(ab|a)(c|bcd)(d*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(ab|a)(bcd|c)(d*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(a*)(b|abc)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(abc|b)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(b|abc)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(abc|b)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a|ab)(c|bcd)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(a|ab)(bcd|c)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(ab|a)(c|bcd)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(ab|a)(bcd|c)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(a*)*", "", "a", 0, true, 0, 1, 1, 1);
        test("(a*)*", "", "x", 0, true, 0, 0, 0, 0);
        test("(a*)*", "", "aaaaaa", 0, true, 0, 6, 6, 6);
        test("(a*)*", "", "aaaaaax", 0, true, 0, 6, 6, 6);
        test("(a*)+", "", "a", 0, true, 0, 1, 1, 1);
        test("(a*)+", "", "x", 0, true, 0, 0, 0, 0);
        test("(a*)+", "", "aaaaaa", 0, true, 0, 6, 6, 6);
        test("(a*)+", "", "aaaaaax", 0, true, 0, 6, 6, 6);
        test("(a+)*", "", "a", 0, true, 0, 1, 0, 1);
        test("(a+)*", "", "x", 0, true, 0, 0, -1, -1);
        test("(a+)*", "", "aaaaaa", 0, true, 0, 6, 0, 6);
        test("(a+)*", "", "aaaaaax", 0, true, 0, 6, 0, 6);
        test("(a+)+", "", "a", 0, true, 0, 1, 0, 1);
        test("(a+)+", "", "x", 0, false);
        test("(a+)+", "", "aaaaaa", 0, true, 0, 6, 0, 6);
        test("(a+)+", "", "aaaaaax", 0, true, 0, 6, 0, 6);
        test("([a]*)*", "", "a", 0, true, 0, 1, 1, 1);
        test("([a]*)*", "", "x", 0, true, 0, 0, 0, 0);
        test("([a]*)*", "", "aaaaaa", 0, true, 0, 6, 6, 6);
        test("([a]*)*", "", "aaaaaax", 0, true, 0, 6, 6, 6);
        test("([a]*)+", "", "a", 0, true, 0, 1, 1, 1);
        test("([a]*)+", "", "x", 0, true, 0, 0, 0, 0);
        test("([a]*)+", "", "aaaaaa", 0, true, 0, 6, 6, 6);
        test("([a]*)+", "", "aaaaaax", 0, true, 0, 6, 6, 6);
        test("([^b]*)*", "", "a", 0, true, 0, 1, 1, 1);
        test("([^b]*)*", "", "b", 0, true, 0, 0, 0, 0);
        test("([^b]*)*", "", "aaaaaa", 0, true, 0, 6, 6, 6);
        test("([^b]*)*", "", "aaaaaab", 0, true, 0, 6, 6, 6);
        test("([ab]*)*", "", "a", 0, true, 0, 1, 1, 1);
        test("([ab]*)*", "", "aaaaaa", 0, true, 0, 6, 6, 6);
        test("([ab]*)*", "", "ababab", 0, true, 0, 6, 6, 6);
        test("([ab]*)*", "", "bababa", 0, true, 0, 6, 6, 6);
        test("([ab]*)*", "", "b", 0, true, 0, 1, 1, 1);
        test("([ab]*)*", "", "bbbbbb", 0, true, 0, 6, 6, 6);
        test("([ab]*)*", "", "aaaabcde", 0, true, 0, 5, 5, 5);
        test("([^a]*)*", "", "b", 0, true, 0, 1, 1, 1);
        test("([^a]*)*", "", "bbbbbb", 0, true, 0, 6, 6, 6);
        test("([^a]*)*", "", "aaaaaa", 0, true, 0, 0, 0, 0);
        test("([^ab]*)*", "", "ccccxx", 0, true, 0, 6, 6, 6);
        test("([^ab]*)*", "", "ababab", 0, true, 0, 0, 0, 0);
        test("((z)+|a)*", "", "zabcde", 0, true, 0, 2, 1, 2, 0, 1);
        test("a+?", "", "aaaaaa", 0, true, 0, 1);
        test("(a)", "", "aaa", 0, true, 0, 1, 0, 1);
        test("(a*?)", "", "aaa", 0, true, 0, 0, 0, 0);
        test("(a)*?", "", "aaa", 0, true, 0, 0, -1, -1);
        test("(a*?)*?", "", "aaa", 0, true, 0, 0, -1, -1);
        test("(a*)*(x)", "", "x", 0, true, 0, 1, 0, 0, 0, 1);
        test("(a*)*(x)", "", "ax", 0, true, 0, 2, 1, 1, 1, 2);
        test("(a*)*(x)", "", "axa", 0, true, 0, 2, 1, 1, 1, 2);
        test("(a*)*(x)(\\1)", "", "x", 0, true, 0, 1, 0, 0, 0, 1, 1, 1);
        test("(a*)*(x)(\\1)", "", "ax", 0, true, 0, 2, 1, 1, 1, 2, 2, 2);
        test("(a*)*(x)(\\1)", "", "axa", 0, true, 0, 2, 1, 1, 1, 2, 2, 2);
        test("(a*)*(x)(\\1)(x)", "", "axax", 0, true, 0, 4, 0, 1, 1, 2, 2, 3, 3, 4);
        test("(a*)*(x)(\\1)(x)", "", "axxa", 0, true, 0, 3, 1, 1, 1, 2, 2, 2, 2, 3);
        test("(a*)*(x)", "", "x", 0, true, 0, 1, 0, 0, 0, 1);
        test("(a*)*(x)", "", "ax", 0, true, 0, 2, 1, 1, 1, 2);
        test("(a*)*(x)", "", "axa", 0, true, 0, 2, 1, 1, 1, 2);
        test("(a*)+(x)", "", "x", 0, true, 0, 1, 0, 0, 0, 1);
        test("(a*)+(x)", "", "ax", 0, true, 0, 2, 1, 1, 1, 2);
        test("(a*)+(x)", "", "axa", 0, true, 0, 2, 1, 1, 1, 2);
        test("(a*){2}(x)", "", "x", 0, true, 0, 1, 0, 0, 0, 1);
        test("(a*){2}(x)", "", "ax", 0, true, 0, 2, 1, 1, 1, 2);
        test("(a*){2}(x)", "", "axa", 0, true, 0, 2, 1, 1, 1, 2);
        test("((..)|(.))", "", "a", 0, true, 0, 1, 0, 1, -1, -1, 0, 1);
        test("((..)|(.))((..)|(.))", "", "a", 0, false);
        test("((..)|(.))((..)|(.))((..)|(.))", "", "a", 0, false);
        test("((..)|(.)){1}", "", "a", 0, true, 0, 1, 0, 1, -1, -1, 0, 1);
        test("((..)|(.)){2}", "", "a", 0, false);
        test("((..)|(.)){3}", "", "a", 0, false);
        test("((..)|(.))*", "", "a", 0, true, 0, 1, 0, 1, -1, -1, 0, 1);
        test("((..)|(.))", "", "aa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.))((..)|(.))", "", "aa", 0, true, 0, 2, 0, 1, -1, -1, 0, 1, 1, 2, -1, -1, 1, 2);
        test("((..)|(.))((..)|(.))((..)|(.))", "", "aa", 0, false);
        test("((..)|(.)){1}", "", "aa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.)){2}", "", "aa", 0, true, 0, 2, 1, 2, -1, -1, 1, 2);
        test("((..)|(.)){3}", "", "aa", 0, false);
        test("((..)|(.))*", "", "aa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.))", "", "aaa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.))((..)|(.))", "", "aaa", 0, true, 0, 3, 0, 2, 0, 2, -1, -1, 2, 3, -1, -1, 2, 3);
        test("((..)|(.))((..)|(.))((..)|(.))", "", "aaa", 0, true, 0, 3, 0, 1, -1, -1, 0, 1, 1, 2, -1, -1, 1, 2, 2, 3, -1, -1, 2, 3);
        test("((..)|(.)){1}", "", "aaa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.)){2}", "", "aaa", 0, true, 0, 3, 2, 3, 0, 2, 2, 3);
        test("((..)|(.)){3}", "", "aaa", 0, true, 0, 3, 2, 3, -1, -1, 2, 3);
        test("((..)|(.))*", "", "aaa", 0, true, 0, 3, 2, 3, 0, 2, 2, 3);
        test("((..)|(.))", "", "aaaa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.))((..)|(.))", "", "aaaa", 0, true, 0, 4, 0, 2, 0, 2, -1, -1, 2, 4, 2, 4, -1, -1);
        test("((..)|(.))((..)|(.))((..)|(.))", "", "aaaa", 0, true, 0, 4, 0, 2, 0, 2, -1, -1, 2, 3, -1, -1, 2, 3, 3, 4, -1, -1, 3, 4);
        test("((..)|(.)){1}", "", "aaaa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.)){2}", "", "aaaa", 0, true, 0, 4, 2, 4, 2, 4, -1, -1);
        test("((..)|(.)){3}", "", "aaaa", 0, true, 0, 4, 3, 4, 0, 2, 3, 4);
        test("((..)|(.))*", "", "aaaa", 0, true, 0, 4, 2, 4, 2, 4, -1, -1);
        test("((..)|(.))", "", "aaaaa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.))((..)|(.))", "", "aaaaa", 0, true, 0, 4, 0, 2, 0, 2, -1, -1, 2, 4, 2, 4, -1, -1);
        test("((..)|(.))((..)|(.))((..)|(.))", "", "aaaaa", 0, true, 0, 5, 0, 2, 0, 2, -1, -1, 2, 4, 2, 4, -1, -1, 4, 5, -1, -1, 4, 5);
        test("((..)|(.)){1}", "", "aaaaa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.)){2}", "", "aaaaa", 0, true, 0, 4, 2, 4, 2, 4, -1, -1);
        test("((..)|(.)){3}", "", "aaaaa", 0, true, 0, 5, 4, 5, 2, 4, 4, 5);
        test("((..)|(.))*", "", "aaaaa", 0, true, 0, 5, 4, 5, 2, 4, 4, 5);
        test("((..)|(.))", "", "aaaaaa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.))((..)|(.))", "", "aaaaaa", 0, true, 0, 4, 0, 2, 0, 2, -1, -1, 2, 4, 2, 4, -1, -1);
        test("((..)|(.))((..)|(.))((..)|(.))", "", "aaaaaa", 0, true, 0, 6, 0, 2, 0, 2, -1, -1, 2, 4, 2, 4, -1, -1, 4, 6, 4, 6, -1, -1);
        test("((..)|(.)){1}", "", "aaaaaa", 0, true, 0, 2, 0, 2, 0, 2, -1, -1);
        test("((..)|(.)){2}", "", "aaaaaa", 0, true, 0, 4, 2, 4, 2, 4, -1, -1);
        test("((..)|(.)){3}", "", "aaaaaa", 0, true, 0, 6, 4, 6, 4, 6, -1, -1);
        test("((..)|(.))*", "", "aaaaaa", 0, true, 0, 6, 4, 6, 4, 6, -1, -1);
        test("X(.?){0,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){1,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){2,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){3,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){4,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){5,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){6,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){7,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){8,}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){0,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){1,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){2,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){3,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){4,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){5,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){6,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){7,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("X(.?){8,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
        test("(a|ab|c|bcd){0,}(d*)", "", "ababcd", 0, true, 0, 1, 0, 1, 1, 1);
        test("(a|ab|c|bcd){1,}(d*)", "", "ababcd", 0, true, 0, 1, 0, 1, 1, 1);
        test("(a|ab|c|bcd){2,}(d*)", "", "ababcd", 0, true, 0, 6, 3, 6, 6, 6);
        test("(a|ab|c|bcd){3,}(d*)", "", "ababcd", 0, true, 0, 6, 3, 6, 6, 6);
        test("(a|ab|c|bcd){4,}(d*)", "", "ababcd", 0, false);
        test("(a|ab|c|bcd){0,10}(d*)", "", "ababcd", 0, true, 0, 1, 0, 1, 1, 1);
        test("(a|ab|c|bcd){1,10}(d*)", "", "ababcd", 0, true, 0, 1, 0, 1, 1, 1);
        test("(a|ab|c|bcd){2,10}(d*)", "", "ababcd", 0, true, 0, 6, 3, 6, 6, 6);
        test("(a|ab|c|bcd){3,10}(d*)", "", "ababcd", 0, true, 0, 6, 3, 6, 6, 6);
        test("(a|ab|c|bcd){4,10}(d*)", "", "ababcd", 0, false);
        test("(a|ab|c|bcd)*(d*)", "", "ababcd", 0, true, 0, 1, 0, 1, 1, 1);
        test("(a|ab|c|bcd)+(d*)", "", "ababcd", 0, true, 0, 1, 0, 1, 1, 1);
        test("(ab|a|c|bcd){0,}(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd){1,}(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd){2,}(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd){3,}(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd){4,}(d*)", "", "ababcd", 0, false);
        test("(ab|a|c|bcd){0,10}(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd){1,10}(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd){2,10}(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd){3,10}(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd){4,10}(d*)", "", "ababcd", 0, false);
        test("(ab|a|c|bcd)*(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(ab|a|c|bcd)+(d*)", "", "ababcd", 0, true, 0, 6, 4, 5, 5, 6);
        test("(a|ab)(c|bcd)(d*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(a|ab)(bcd|c)(d*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(ab|a)(c|bcd)(d*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(ab|a)(bcd|c)(d*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(a*)(b|abc)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(abc|b)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(b|abc)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(abc|b)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a|ab)(c|bcd)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(a|ab)(bcd|c)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(ab|a)(c|bcd)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(ab|a)(bcd|c)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(a|ab)(c|bcd)(d*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(a|ab)(bcd|c)(d*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(ab|a)(c|bcd)(d*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(ab|a)(bcd|c)(d*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(a*)(b|abc)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(abc|b)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(b|abc)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a*)(abc|b)(c*)", "", "abc", 0, true, 0, 3, 0, 1, 1, 2, 2, 3);
        test("(a|ab)(c|bcd)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(a|ab)(bcd|c)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 1, 1, 4, 4, 4);
        test("(ab|a)(c|bcd)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("(ab|a)(bcd|c)(d|.*)", "", "abcd", 0, true, 0, 4, 0, 2, 2, 3, 3, 4);
        test("\ufb00", "i", "FF", 0, true, 0, 2);
        test("(\ufb00)\\1", "i", "FFFF", 0, true, 0, 4, 0, 2);
        test("(\ufb00)\\1", "i", "FF\ufb00", 0, false);
        test("(\ufb00)\\1", "i", "\ufb00FF", 0, false);
        test("\ufb01", "i", "FI", 0, true, 0, 2);
        test("(\ufb01)\\1", "i", "FIFI", 0, true, 0, 4, 0, 2);
        test("\ufb02", "i", "FL", 0, true, 0, 2);
        test("\ufb03", "i", "FFI", 0, true, 0, 3);
        test("\ufb04", "i", "FFL", 0, true, 0, 3);
        test("\ufb00I", "i", "\ufb03", 0, true, 0, 1);
        test("\ufb03", "i", "\ufb00I", 0, true, 0, 2);
        test("F\ufb01", "i", "\ufb03", 0, true, 0, 1);
        test("\ufb03", "i", "F\ufb01", 0, true, 0, 2);
        test("\ufb00L", "i", "\ufb04", 0, true, 0, 1);
        test("\ufb04", "i", "\ufb00L", 0, true, 0, 2);
        test("F\ufb02", "i", "\ufb04", 0, true, 0, 1);
        test("\ufb04", "i", "F\ufb02", 0, true, 0, 2);
        test("[\ufb04[=a=]o]+", "i", "F\ufb02a\u00c4\u00f6", 0, true, 0, 4);
        test("\u1f50", "i", "\u03c5\u0313", 0, true, 0, 2);
        test("\u1f52", "i", "\u03c5\u0313\u0300", 0, true, 0, 3);
        test("\u1f54", "i", "\u03c5\u0313\u0301", 0, true, 0, 3);
        test("\u1f56", "i", "\u03c5\u0313\u0342", 0, true, 0, 3);
        test("\u1f50\u0300", "i", "\u1f52", 0, true, 0, 1);
        test("\u1f52", "i", "\u1f50\u0300", 0, true, 0, 2);
        test("\u1f50\u0301", "i", "\u1f54", 0, true, 0, 1);
        test("\u1f54", "i", "\u1f50\u0301", 0, true, 0, 2);
        test("\u1f50\u0342", "i", "\u1f56", 0, true, 0, 1);
        test("\u1f56", "i", "\u1f50\u0342", 0, true, 0, 2);
        test("\u1fb6", "i", "\u03b1\u0342", 0, true, 0, 2);
        test("\u1fb7", "i", "\u03b1\u0342\u03b9", 0, true, 0, 3);
        test("\u1fb6\u03b9", "i", "\u1fb7", 0, true, 0, 1);
        test("\u1fb7", "i", "\u1fb6\u03b9", 0, true, 0, 2);
        test("\u1fc6", "i", "\u03b7\u0342", 0, true, 0, 2);
        test("\u1fc7", "i", "\u03b7\u0342\u03b9", 0, true, 0, 3);
        test("\u1fc6\u03b9", "i", "\u1fc7", 0, true, 0, 1);
        test("\u1fc7", "i", "\u1fc6\u03b9", 0, true, 0, 2);
        test("\u1ff6", "i", "\u03c9\u0342", 0, true, 0, 2);
        test("\u1ff7", "i", "\u03c9\u0342\u03b9", 0, true, 0, 3);
        test("\u1ff6\u03b9", "i", "\u1ff7", 0, true, 0, 1);
        test("\u1ff7", "i", "\u1ff6\u03b9", 0, true, 0, 2);
        test("f*", "i", "ff", 0, true, 0, 2);
        test("f*", "i", "\ufb00", 0, true, 0, 0);
        test("f+", "i", "ff", 0, true, 0, 2);
        test("f+", "i", "\ufb00", 0, false);
        test("f{1,}", "i", "ff", 0, true, 0, 2);
        test("f{1,}", "i", "\ufb00", 0, false);
        test("f{1,2}", "i", "ff", 0, true, 0, 2);
        test("f{1,2}", "i", "\ufb00", 0, false);
        test("f{,2}", "i", "ff", 0, false);
        test("f{,2}", "i", "\ufb00", 0, false);
        test("ff?", "i", "ff", 0, true, 0, 2);
        test("ff?", "i", "\ufb00", 0, false);
        test("f{2}", "i", "ff", 0, true, 0, 2);
        test("f{2}", "i", "\ufb00", 0, false);
        test("f{2,2}", "i", "ff", 0, true, 0, 2);
        test("f{2,2}", "i", "\ufb00", 0, false);
        test("K", "i", "\u212a", 0, true, 0, 1);
        test("k", "i", "\u212a", 0, true, 0, 1);
        test("\\w", "i", "\u212a", 0, true, 0, 1);
        test("\\W", "i", "\u212a", 0, false);
        test("[\\w]", "i", "\u212a", 0, false);
        test("[\\w]+", "i", "a\\wWc", 0, true, 1, 4);
        test("[\\W]+", "i", "a\\wWc", 0, true, 1, 4);
        test("[\\d]+", "i", "0\\dD9", 0, true, 1, 4);
        test("[\\D]+", "i", "a\\dDc", 0, true, 1, 4);
        test("[\\s]+", "i", " \\sS\u0009", 0, true, 1, 4);
        test("[\\S]+", "i", " \\sS\u0009", 0, true, 1, 4);
        test("[kx]", "i", "\u212a", 0, true, 0, 1);
        test("ff", "i", "\ufb00", 0, true, 0, 1);
        test("[f]f", "i", "\ufb00", 0, false);
        test("f[f]", "i", "\ufb00", 0, false);
        test("[f][f]", "i", "\ufb00", 0, false);
        test("(?:f)f", "i", "\ufb00", 0, false);
        test("f(?:f)", "i", "\ufb00", 0, false);
        test("(?:f)(?:f)", "i", "\ufb00", 0, false);
        test("\\A[\ufb00]\\z", "i", "\ufb00", 0, true, 0, 1);
        test("\\A[\ufb00]\\z", "i", "ff", 0, true, 0, 2);
        test("\\A[^\ufb00]\\z", "i", "\ufb00", 0, false);
        test("\\A[^\ufb00]\\z", "i", "ff", 0, false);
        test("\\A[^[^\ufb00]]\\z", "i", "\ufb00", 0, false);
        test("\\A[^[^\ufb00]]\\z", "i", "ff", 0, false);
        test("\\A[[^[^\ufb00]]]\\z", "i", "\ufb00", 0, false);
        test("\\A[[^[^\ufb00]]]\\z", "i", "ff", 0, false);
        test("[^a-c]", "i", "A", 0, false);
        test("[[^a-c]]", "i", "A", 0, false);
        test("[^a]", "i", "a", 0, false);
        test("[[^a]]", "i", "a", 0, false);
        test("\\A\\W\\z", "i", "\ufb00", 0, false);
        test("\\A\\W\\z", "i", "ff", 0, false);
        test("\\A[\\p{L}]\\z", "i", "\ufb00", 0, false);
        test("\\A[\\p{L}]\\z", "i", "ff", 0, false);
        test("\\A\\W\\z", "i", "\ufb03", 0, false);
        test("\\A\\W\\z", "i", "ffi", 0, false);
        test("\\A\\W\\z", "i", "\ufb00i", 0, false);
        test("\\A[\\p{L}]\\z", "i", "\ufb03", 0, false);
        test("\\A[\\p{L}]\\z", "i", "ffi", 0, false);
        test("\\A[\\p{L}]\\z", "i", "\ufb00i", 0, false);
        test("([[=a=]])\\1", "i", "aA", 0, true, 0, 2, 0, 1);
        test("([[=a=]])\\1", "i", "Aa", 0, true, 0, 2, 0, 1);
        test("([[=a=]])\\1", "i", "a\u00e4", 0, false);
        test("([[=a=]])\\1", "i", "a\u00c4", 0, false);
        test("([[=a=]])\\1", "i", "\u00e4a", 0, false);
        test("([[=a=]])\\1", "i", "\u00c4a", 0, false);
        test("([[=a=]])\\1", "i", "\u00c4A", 0, false);
        /* GENERATED CODE END - KEEP THIS MARKER FOR AUTOMATIC UPDATES */
    }
}
