/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.regex.RegexSyntaxException.ErrorCode;
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.string.Encodings;

public class JsTests extends RegexTestBase {

    @Override
    String getEngineOptions() {
        return "";
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_16_RAW;
    }

    @Test
    public void lookbehindInLookahead() {
        test("\\s*(?=(?<=\\W))", "", "paragraph block*", 1, true, 9, 10);
        test("\\s*(?=\\b)", "", "paragraph block*", 1, true, 9, 10);
        test("\\s*(?=\\b|\\W|$)", "", "paragraph block*", 1, true, 9, 10);
    }

    @Test
    public void nestedQuantifiers() {
        test("(x??)?", "", "x", 0, true, 0, 1, 0, 1);
        test("(x??)?", "", "x", 1, true, 1, 1, -1, -1);
        test("(x??)*", "", "x", 0, true, 0, 1, 0, 1);
        test("(x??)*", "", "x", 1, true, 1, 1, -1, -1);
        test("X(.?){8,8}Y", "", "X1234567Y", 0, true, 0, 9, 8, 8);
    }

    @Test
    public void zeroWidthQuantifier() {
        test("(?:(?=(x))|y)?", "", "x", 0, true, 0, 0, -1, -1);
    }

    @Test
    public void zeroWidthBoundedQuantifier() {
        test("(a|){100}", "", "a", 0, true, 0, 1, 1, 1);
        test("(a|^){100}", "", "a", 0, true, 0, 1, 0, 1);
        test("(a|$){100}", "", "a", 0, true, 0, 1, 1, 1);
        test("(a|){100,200}", "", "a", 0, true, 0, 1, 1, 1);
        test("(|a){100}", "", "a", 0, true, 0, 0, 0, 0);
        test("(^|a){100}", "", "a", 0, true, 0, 0, 0, 0);
        test("($|a){100}", "", "a", 0, true, 0, 1, 1, 1);
        test("(|a){100,200}", "", "a", 0, true, 0, 1, 0, 1);
        test("(a||b){100,200}", "", "ab", 0, true, 0, 2, 1, 2);
        test("(a||b){100,200}?", "", "ab", 0, true, 0, 1, 1, 1);
        test("(a||b){100,200}?$", "", "ab", 0, true, 0, 2, 1, 2);
    }

    @Test
    public void escapedZero() {
        test("\\0", "u", "\u0000", 0, true, 0, 1);
    }

    @Test
    public void gr29379() {
        test("(?=^)|(?=$)", "", "", 0, true, 0, 0);
        test("(?=^)|(?=$)(?=^)|(?=$)", "", "", 0, true, 0, 0);
    }

    @Test
    public void gr29388() {
        test(".+(?=bar)|.+", "", "foobar", 0, true, 0, 3);
    }

    @Test
    public void gr28905() {
        test("\\B", "y", "abc", 0, false);
        test("(?<=[a-z])[A-Z]", "y", "aA", 0, false);
    }

    @Test
    public void lastGroupNotSet() {
        // IndexOf
        test("(a)", "", "a", 0, true, 0, 1, 0, 1, -1);
        // StartsWith
        test("^(a)", "", "a", 0, true, 0, 1, 0, 1, -1);
        // EndsWith
        test("(a)$", "", "a", 0, true, 0, 1, 0, 1, -1);
        // Equals
        test("^(a)$", "", "a", 0, true, 0, 1, 0, 1, -1);
        // RegionMatches
        test("(a)", "y", "a", 0, true, 0, 1, 0, 1, -1);
        // EmptyIndexOf
        test("()", "", "", 0, true, 0, 0, 0, 0, -1);
        // EmptyStartsWith
        test("^()", "", "", 0, true, 0, 0, 0, 0, -1);
        // EmptyEndsWith
        test("()$", "", "", 0, true, 0, 0, 0, 0, -1);
        // EmptyEquals
        test("^()$", "", "", 0, true, 0, 0, 0, 0, -1);

        // Single possible CG result: exercises NFA, DFA and backtracker.
        test("([a0-9])", "", "a", 0, true, 0, 1, 0, 1, -1);
        // TraceFinder: exercises NFA, DFA and backtracker.
        test("x?([a0-9])", "", "a", 0, true, 0, 1, 0, 1, -1);
        // Unbounded length of match, unambiguous: exercises NFA, lazy DFA, simpleCG DFA and
        // backtracker.
        test("x*([a0-9])", "", "a", 0, true, 0, 1, 0, 1, -1);
        // Unbounded length of match, ambiguous: exercises NFA, lazy DFA, eager DFA and backtracker.
        test(".*([a0-9])", "", "a", 0, true, 0, 1, 0, 1, -1);
    }

    @Test
    public void gr35771() {
        test("(^\\s*)|(\\s*$)", "", "", 0, true, 0, 0, 0, 0, -1, -1);
    }

    @Test
    public void justLookBehind() {
        test("(?<=\\n)", "", "__\n__", 0, true, 3, 3);
    }

    @Test
    public void justLookBehindSticky() {
        test("(?<=\\n)", "y", "__\n__", 3, true, 3, 3);
    }

    @Test
    public void gr21421() {
        test("(?=(\\3?)|([^\\W\uaa3bt-\ua4b9]){4294967296}|(?=[^]+[\\n-\u4568\\uD3D5\\u00ca-\\u00fF]*)*|(?:\\2|^)?.){33554431}(?:(?:\\S{1,}(?:\\b|\\w{1,}))(?:\\2?)+){4,}", "im",
                        "\u4568\u4568\u4568\u4568________\\xee0000", 0, true, 0, 20, 0, 0, -1, -1);
    }

    @Test
    public void gr37496() {
        test("(?:(?:" + "a".repeat(TRegexOptions.TRegexMaxParseTreeSizeForDFA) + ")?(?<=a))+", "", "", 0, false);
    }

    @Test
    public void gr40877() {
        test("(?!([]))[a-z]", "", "a", 0, true, 0, 1, -1, -1);
        test("(?<!([]))[a-z]", "", "a", 0, true, 0, 1, -1, -1);
        test("(?!([]))(?:(^)\\2)+", "m", "", 0, true, 0, 0, -1, -1, 0, 0);
    }

    @Test
    public void gr40879() {
        // gets optimized away
        test("(?!(?!(?=(.)|.(?=\\D){1,4}|.|[^\\w\u0091\\d\\\u0001-<]*?|[^].)*))", "y", "\n\n\n\n", 0, true, 0, 0, -1, -1);
        test("(?!(?!(?=\\b|\\D|\\s|$|\\1|(?!(.))){0})+?)", "yim", "\u009c\u511c\n\u009c\u511c\n", 0, true, 0, 0, -1, -1);
        test("(?!(\\1)?[\\s\\w\\D])+?", "", "", 0, true, 0, 0, -1, -1);
        test("(?!(?=((?:[n-\u0e32]*?o+?[^])))*?\\w)", "yim", "_", 0, false);
    }

    @Test
    public void gr42266() {
        // reduced
        test("(?![^\\d\\D]$)[^]", "", "x", 0, true, 0, 1);
        // original
        test("((?:(?!([^\\d\\D\\W\\cU])\\b)(([^]\u11C2)))*?)", "gi", "x", 0, true, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1);
    }

    @Test
    public void gr43230() {
        test(".(?!(?=\\S^\\b)+)|(?=\\S*)", "y", "", 0, true, 0, 0);
    }

    @Test
    public void gr42791() {
        test("(?:(?!(?:(?:\\B)|([^])?){4}))", "gm", "", 0, false);
    }

    @Test
    public void gr42794() {
        test("\\b|\\B", "gyim", "\ua074\n\nP \n\u00a7", 7, true, 7, 7);
    }

    @Test
    public void gr43449() {
        test("\\B[^\\b\u00C2\u008D](?=.)+?[^]|\\?*?|(?!(?:(.)))*?(?!\\3^){4}", "yi",
                        "\u7300\ud329\n\n\u969d\n\n\u00da\n\u7300\ud329\n\n\u969d\n\n\u00da\n\u7300\ud329\n\n\u969d\n\n\u00da\n\u7300\ud329\n\n\u969d\n\n\u00da\n\u7300\ud329\n\n\u969d\n\n\u00da\n", 0,
                        true, 0, 2, -1, -1);
    }

    @Test
    public void quantifierOverflow() {
        long max = Integer.MAX_VALUE;
        test(String.format("x{%d,%d}", max, max + 1), "", "x", 0, false);
        test(String.format("x{%d,}", max), "", "x", 0, false);
        test(String.format("x{%d,}", max + 1), "", "x", 0, false);
        expectSyntaxError(String.format("x{%d,%d}", max + 1, max), "", JsErrorMessages.QUANTIFIER_OUT_OF_ORDER);
    }

    @Test
    public void gr45479() {
        // minimized test case
        test("\\s*(p$)?", "", "px", 0, true, 0, 0, -1, -1);
        // original test case
        test("^(\\d{1,2})[:.,;\\-]?(\\d{1,2})?[:.,;\\-]?(\\d{1,2})?[:.,;\\-]?(\\d{1,3})?[:.,;\\-]?\\s*([ap](?=[m]|^\\w|$))?", "i", "08:00:00.000 PDT", 0, true, 0, 13, 0, 2, 3, 5, 6, 8, 9, 12, -1, -1);
    }

    @Test
    public void gr46659() {
        // original test case
        test("((?<!\\+)https?:\\/\\/(?:www\\.)?(?:[-\\w.]+?[.@][a-zA-Z\\d]{2,}|localhost)(?:[-\\w.:%+~#*$!?&/=@]*?(?:,(?!\\s))*?)*)", "g", "https://sindresorhus.com/?id=foo,bar", 0, true, 0, 36, 0,
                        36);
        // related smaller test cases
        test("(?:\\w*,*?)*", "", "foo,bar", 0, true, 0, 7);
        test("(?:\\w*(?:,(?!\\s))*?)*", "", "foo,bar", 0, true, 0, 7);
    }

    @Test
    public void groupsDeclarationOrder() {
        Value compiledRegex = compileRegex("(?:(?<x>a)|(?<x>b))(?<foo>foo)(?<bar>bar)", "");
        Assert.assertArrayEquals(new String[]{"x", "foo", "bar"}, compiledRegex.getMember("groups").getMemberKeys().toArray(new String[0]));
    }

    @Test
    public void gr48586() {
        test("(?=^|[^A-Fa-f0-9:]|[^\\w\\:])((:|:(:0{1,4}){1,6}|(0{1,4}:){1,6}|(0{1,4}:){6}0{1,4}|(0{1,4}:){1}:(0{1,4}:){4}0{1,4}|(0{1,4}:){2}:(0{1,4}:){3}0{1,4}|(0{1,4}:){3}:(0{1,4}:){2}0{1,4}|" +
                        "(0{1,4}:){4}:(0{1,4}:){1}0{1,4}|(0{1,4}:){5}:(0{1,4}:){0}0{1,4}|(0{1,4}:){1}:(0{1,4}:){3}0{1,4}|(0{1,4}:){2}:(0{1,4}:){2}0{1,4}|(0{1,4}:){3}:(0{1,4}:){1}0{1,4}|(0{1,4}:){4}" +
                        ":(0{1,4}:){0}0{1,4}|(0{1,4}:){1}:(0{1,4}:){2}0{1,4}|(0{1,4}:){2}:(0{1,4}:){1}0{1,4}|(0{1,4}:){3}:(0{1,4}:){0}0{1,4}|(0{1,4}:){1}:(0{1,4}:){1}0{1,4}|(0{1,4}:){2}:(0{1,4}:)" +
                        "{0}0{1,4}|(0{1,4}:){1}:(0{1,4}:){0}0{1,4}):(0{0,3}1))(?=[^\\w\\:]|[^A-Fa-f0-9:]|$)", "i", "::1", 0,
                        true, 0, 3, 0, 3, 0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
                        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 2, 3);
    }

    @Test
    public void gr50807() {
        test("(?<=%b{1,4}?)foo", "", "%bbbbfoo", 0, true, 5, 8);
    }

    @Test
    public void gr51523() {
        test("(?:^|\\.?)([A-Z])", "g", "desktopBrowser", 0, true, 7, 8, 7, 8);
        test("(?:^|\\.?)([A-Z])", "g", "locationChanged", 0, true, 8, 9, 8, 9);
        test("(?:^|\\.?)([A-Z]|(?<=[a-z])\\d(?=\\d+))", "g", "helloWorld", 0, true, 5, 6, 5, 6);
    }

    @Test
    public void mergedLookAheadLiteral() {
        test("(?:(?=(abc)))a", "", "abc", 0, true, 0, 1, 0, 3);
    }

    @Test
    public void innerLiteralSurrogates() {
        test("\\udf06", "", "\uD834\uDF06", 0, true, 1, 2);
        test("x?\\udf06", "", "\uD834\uDF06", 0, true, 1, 2);
        test("\\udf06", "u", "\uD834\uDF06", 0, false);
        test("x?\\udf06", "u", "\uD834\uDF06", 0, false);
    }

    @Test
    public void gr52906() {
        // Original test case
        // note: original counter value is 67108860, reduced to let the test finish in reasonable
        // time.
        test("\\b(((.*?)){9999})\\b|(?=(?=(?!.).\\b(\\d))){0,4}", "yi", "L1O\n\n\n11\n  \n\n11\n  \uD091  1aa\uFCDB=\n ", 0, true, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1);
        // Minimized version
        test("(.*?){9999}", "", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxx", 0, true, 0, 0, 0, 0);

        // Linked issue
        test("(?=(?=(\\W)\u008e+|\\uC47A|(\\s)))+?|((((?:(\\\u0015)))+?))|(?:\\r|[^]+?[^])|\\3{3,}", "gyim", "", 0, true, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1);
        // Minimized version
        test("()\\1{3,}", "", "", 0, true, 0, 0, 0, 0);
    }

    @Test
    public void gr56676() {
        test("(?<!a)", "digyus", "x\uDE40", 2, true, 2, 2);
    }

    @Test
    public void emptyTransitionMergedWithLookAhead() {
        test("a(?=b(?<=ab)()|)", "", "ab", 0, true, 0, 1, 2, 2);
        test("a(?=b(?<=ab)()|)", "", "ac", 0, true, 0, 1, -1, -1);
        test("a(?=b(?<=ab)()|)", "", "a", 0, true, 0, 1, -1, -1);
        test("a?(?=b(?<=ab)()|)", "", "a", 0, true, 0, 1, -1, -1);
    }

    @Test
    public void generatedTests() {
        /* GENERATED CODE BEGIN - KEEP THIS MARKER FOR AUTOMATIC UPDATES */

        // Generated using V8 version 13.0.245.12-rusty
        test("((A|){7,10}?){10,17}", "", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 0, true, 0, 86, 84, 86, 86, 86);
        test("(a{1,30}){1,4}", "", "a", 0, true, 0, 1, 0, 1);
        test("((a|){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 7, 7, 7, 7, 7);
        test("((a?){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 7, 7, 7, 7, 7);
        test("((|a){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 7, 6, 7, 6, 7);
        test("((a??){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 7, 6, 7, 6, 7);
        test("((a?){4,6}){4,6}", "", "aaaaaa", 0, true, 0, 6, 6, 6, 6, 6);
        test("(a|^){100}", "", "a", 0, true, 0, 1, 0, 1);
        test("(a|^){100}", "", "aa", 0, true, 0, 2, 1, 2);
        test("(a|^){100}", "", "aa", 1, false);
        test("(a|^){100}", "", "ab", 1, false);
        test("(a|){4,6}", "", "", 0, true, 0, 0, 0, 0);
        test("(a|){4,6}", "", "a", 0, true, 0, 1, 1, 1);
        test("(a|){4,6}", "", "aa", 0, true, 0, 2, 2, 2);
        test("(a|){4,6}", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|){4,6}", "", "aaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}", "", "aaaaa", 0, true, 0, 5, 4, 5);
        test("(a|){4,6}", "", "aaaaaa", 0, true, 0, 6, 5, 6);
        test("(a|){4,6}", "", "aaaaaaa", 0, true, 0, 6, 5, 6);
        test("(a|){4,6}?", "", "", 0, true, 0, 0, 0, 0);
        test("(a|){4,6}?", "", "a", 0, true, 0, 1, 1, 1);
        test("(a|){4,6}?", "", "aa", 0, true, 0, 2, 2, 2);
        test("(a|){4,6}?", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|){4,6}?", "", "aaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}?", "", "aaaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}?", "", "aaaaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}?", "", "aaaaaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}?a", "", "", 0, false);
        test("(a|){4,6}?a", "", "a", 0, true, 0, 1, 0, 0);
        test("(a|){4,6}?a", "", "aa", 0, true, 0, 2, 1, 1);
        test("(a|){4,6}?a", "", "aaa", 0, true, 0, 3, 2, 2);
        test("(a|){4,6}?a", "", "aaaa", 0, true, 0, 4, 3, 3);
        test("(a|){4,6}?a", "", "aaaaa", 0, true, 0, 5, 3, 4);
        test("(a|){4,6}?a", "", "aaaaaa", 0, true, 0, 5, 3, 4);
        test("(a|){4,6}?a", "", "aaaaaaa", 0, true, 0, 5, 3, 4);
        test("(a|){4,6}?a", "", "aaaaaaaa", 0, true, 0, 5, 3, 4);
        test("(|a){4,6}a", "", "", 0, false);
        test("(|a){4,6}a", "", "a", 0, true, 0, 1, 0, 0);
        test("(|a){4,6}a", "", "aa", 0, true, 0, 2, 0, 1);
        test("(|a){4,6}a", "", "aaa", 0, true, 0, 3, 1, 2);
        test("(|a){4,6}a", "", "aaaa", 0, true, 0, 3, 1, 2);
        test("(|a){4,6}a", "", "aaaaa", 0, true, 0, 3, 1, 2);
        test("(|a){4,6}a", "", "aaaaaa", 0, true, 0, 3, 1, 2);
        test("(|a){4,6}a", "", "aaaaaaa", 0, true, 0, 3, 1, 2);
        test("((a|){4,6}){4,6}", "", "", 0, true, 0, 0, 0, 0, 0, 0);
        test("((a|){4,6}){4,6}", "", "a", 0, true, 0, 1, 1, 1, 1, 1);
        test("((a|){4,6}){4,6}", "", "aa", 0, true, 0, 2, 2, 2, 2, 2);
        test("((a|){4,6}){4,6}", "", "aaa", 0, true, 0, 3, 3, 3, 3, 3);
        test("((a|){4,6}){4,6}", "", "aaaa", 0, true, 0, 4, 4, 4, 4, 4);
        test("((a|){4,6}){4,6}", "", "aaaaa", 0, true, 0, 5, 5, 5, 5, 5);
        test("((a|){4,6}){4,6}", "", "aaaaaa", 0, true, 0, 6, 6, 6, 6, 6);
        test("((a|){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 7, 7, 7, 7, 7);
        test("((a|){4,6}){4,6}", "", "aaaaaaaa", 0, true, 0, 8, 8, 8, 8, 8);
        test("((a|){4,6}){4,6}", "", "aaaaaaaaa", 0, true, 0, 9, 9, 9, 9, 9);
        test("((a|){4,6}){4,6}", "", "aaaaaaaaaa", 0, true, 0, 10, 10, 10, 10, 10);
        test("((a|){4,6}){4,6}", "", "aaaaaaaaaaa", 0, true, 0, 11, 11, 11, 11, 11);
        test("((a|){4,6}){4,6}", "", "aaaaaaaaaaaa", 0, true, 0, 12, 12, 12, 12, 12);
        test("((a|){4,6}){4,6}", "", "aaaaaaaaaaaaa", 0, true, 0, 13, 13, 13, 13, 13);
        test("((|a){4,6}){4,6}", "", "", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "a", 0, true, 0, 1, 1, 1, 1, 1);
        test("((|a){4,6}){4,6}", "", "aa", 0, true, 0, 2, 2, 2, 2, 2);
        test("((|a){4,6}){4,6}", "", "aaa", 0, true, 0, 3, 3, 3, 3, 3);
        test("((|a){4,6}){4,6}", "", "aaaa", 0, true, 0, 4, 4, 4, 4, 4);
        test("((|a){4,6}){4,6}", "", "aaaaa", 0, true, 0, 5, 5, 5, 5, 5);
        test("((|a){4,6}){4,6}", "", "aaaaaa", 0, true, 0, 6, 6, 6, 6, 6);
        test("((|a){4,6}){4,6}", "", "aaaaaaaa", 0, true, 0, 8, 6, 8, 7, 8);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaa", 0, true, 0, 9, 8, 9, 8, 9);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaaa", 0, true, 0, 10, 8, 10, 9, 10);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaaaa", 0, true, 0, 11, 10, 11, 10, 11);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaaaaa", 0, true, 0, 12, 10, 12, 11, 12);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaaaaaa", 0, true, 0, 12, 10, 12, 11, 12);
        test("((a|){4,6}?){4,6}", "", "", 0, true, 0, 0, 0, 0, 0, 0);
        test("((a|){4,6}?){4,6}", "", "a", 0, true, 0, 1, 1, 1, 1, 1);
        test("((a|){4,6}?){4,6}", "", "aa", 0, true, 0, 2, 2, 2, 2, 2);
        test("((a|){4,6}?){4,6}", "", "aaa", 0, true, 0, 3, 3, 3, 3, 3);
        test("((a|){4,6}?){4,6}", "", "aaaa", 0, true, 0, 4, 4, 4, 4, 4);
        test("((a|){4,6}?){4,6}", "", "aaaaa", 0, true, 0, 5, 5, 5, 5, 5);
        test("((a|){4,6}?){4,6}", "", "aaaaaa", 0, true, 0, 6, 6, 6, 6, 6);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaa", 0, true, 0, 8, 8, 8, 8, 8);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaa", 0, true, 0, 9, 9, 9, 9, 9);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaa", 0, true, 0, 10, 10, 10, 10, 10);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaa", 0, true, 0, 11, 11, 11, 11, 11);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaa", 0, true, 0, 12, 12, 12, 12, 12);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaa", 0, true, 0, 13, 12, 13, 13, 13);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaa", 0, true, 0, 14, 12, 14, 14, 14);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaaa", 0, true, 0, 15, 12, 15, 15, 15);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaa", 0, true, 0, 16, 12, 16, 15, 16);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaaa", 0, true, 0, 17, 16, 17, 17, 17);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaaaa", 0, true, 0, 18, 16, 18, 18, 18);
        test("((a){4,6}?){4,6}", "", "", 0, false);
        test("((a){4,6}?){4,6}", "", "a", 0, false);
        test("((a){4,6}?){4,6}", "", "aa", 0, false);
        test("((a){4,6}?){4,6}", "", "aaa", 0, false);
        test("((a){4,6}?){4,6}", "", "aaaa", 0, false);
        test("((a){4,6}?){4,6}", "", "aaaaa", 0, false);
        test("((a){4,6}?){4,6}", "", "aaaaaa", 0, false);
        test("((a){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaa", 0, true, 0, 16, 12, 16, 15, 16);
        test("((a){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaaa", 0, true, 0, 16, 12, 16, 15, 16);
        test("((a){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaaaaaa", 0, true, 0, 20, 16, 20, 19, 20);
        test("((a){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 24, 20, 24, 23, 24);
        test("((a){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 24, 20, 24, 23, 24);
        test("((a){4,6}){4,6}", "", "", 0, false);
        test("((a){4,6}){4,6}", "", "a", 0, false);
        test("((a){4,6}){4,6}", "", "aa", 0, false);
        test("((a){4,6}){4,6}", "", "aaa", 0, false);
        test("((a){4,6}){4,6}", "", "aaaa", 0, false);
        test("((a){4,6}){4,6}", "", "aaaaa", 0, false);
        test("((a){4,6}){4,6}", "", "aaaaaa", 0, false);
        test("((a){4,6}){4,6}", "", "aaaaaaaaaaaaaaaa", 0, true, 0, 16, 12, 16, 15, 16);
        test("((a){4,6}){4,6}", "", "aaaaaaaaaaaaaaaaa", 0, true, 0, 17, 13, 17, 16, 17);
        test("((a){4,6}){4,6}", "", "aaaaaaaaaaaaaaaaaaaa", 0, true, 0, 20, 16, 20, 19, 20);
        test("((a){4,6}){4,6}", "", "aaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 24, 18, 24, 23, 24);
        test("((a){4,6}){4,6}", "", "aaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 24, 18, 24, 23, 24);
        test("((a){4,}){4,6}", "", "", 0, false);
        test("((a){4,}){4,6}", "", "a", 0, false);
        test("((a){4,}){4,6}", "", "aa", 0, false);
        test("((a){4,}){4,6}", "", "aaa", 0, false);
        test("((a){4,}){4,6}", "", "aaaa", 0, false);
        test("((a){4,}){4,6}", "", "aaaaa", 0, false);
        test("((a){4,}){4,6}", "", "aaaaaa", 0, false);
        test("((a){4,}){4,6}", "", "aaaaaaaaaaaaaaaa", 0, true, 0, 16, 12, 16, 15, 16);
        test("((a){4,}){4,6}", "", "aaaaaaaaaaaaaaaaa", 0, true, 0, 17, 13, 17, 16, 17);
        test("((a){4,}){4,6}", "", "aaaaaaaaaaaaaaaaaaaa", 0, true, 0, 20, 16, 20, 19, 20);
        test("((a){4,}){4,6}", "", "aaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 24, 20, 24, 23, 24);
        test("((a){4,}){4,6}", "", "aaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 25, 21, 25, 24, 25);
        test("(.)\\1{2,}", "", "billiam", 0, false);
        test("(^_(a{1,2}[:])*a{1,2}[:]a{1,2}([.]a{1,4})?_)+", "", "_a:a:a.aaa_", 0, true, 0, 11, 0, 11, 1, 3, 6, 10);
        test("(a{2}|())+$", "", "aaaa", 0, true, 0, 4, 2, 4, -1, -1);
        test("^a(b*)\\1{4,6}?", "", "abbbb", 0, true, 0, 1, 1, 1);
        test("^a(b*)\\1{4,6}?", "", "abbbbb", 0, true, 0, 6, 1, 2);
        test("(?<=|$)", "", "a", 0, true, 0, 0);
        test("(?=ab)a", "", "ab", 0, true, 0, 1);
        test("(?=()|^)|x", "", "empty", 0, true, 0, 0, 0, 0);
        test("a(?<=ba)", "", "ba", 0, true, 1, 2);
        test("(?<=(?=|()))", "", "aa", 0, true, 0, 0, -1, -1);
        test("\\d\\W", "iv", "4\u017f", 0, false);
        test("[\u08bc-\ucf3a]", "iv", "\u03b0", 0, true, 0, 1);
        test("[\u0450-\u6c50]\u7e57\u55ad()\u64e7\\d|", "iu", "\u03b0\u7e57\u55ad\u64e79", 0, true, 0, 5, 3, 3);
        test("a(?:|()\\1){1,2}", "", "a", 0, true, 0, 1, -1, -1);
        expectSyntaxError("|(?<\\d\\1)\ub7e4", "", "", getTRegexEncoding(), "error", 0, ErrorCode.InvalidNamedGroup);
        test("[a-z][a-z\u2028\u2029].|ab(?<=[a-z]w.)", "", "aac", 0, true, 0, 3);
        test("(animation|animation-name)", "", "animation", 0, true, 0, 9, 0, 9);
        test("(a|){7,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(a|){7,7}?b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(|a){7,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(|a){7,7}?b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(a||b){7,7}c", "", "aaabc", 0, true, 0, 5, 3, 4);
        test("(a||b){7,7}c", "", "aaac", 0, true, 0, 4, 3, 3);
        test("(a||b){7,7}c", "", "aaabac", 0, true, 0, 6, 4, 5);
        test("($|a){7,7}", "", "aaa", 0, true, 0, 3, 3, 3);
        test("($|a){7,7}?", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|$){7,7}", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|$){7,7}?", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|$|b){7,7}", "", "aaab", 0, true, 0, 4, 4, 4);
        test("(a|$|b){7,7}", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|$|b){7,7}", "", "aaaba", 0, true, 0, 5, 5, 5);
        test("((?=a)|a){7,7}b", "", "aaa", 0, false);
        test("((?=[ab])|a){7,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("((?<=a)|a){7,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("a((?<=a)|a){7,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(a|){0,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(a|){0,7}?b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(|a){0,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(|a){0,7}?b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(a||b){0,7}c", "", "aaabc", 0, true, 0, 5, 3, 4);
        test("(a||b){0,7}c", "", "aaac", 0, true, 0, 4, 2, 3);
        test("(a||b){0,7}c", "", "aaabac", 0, true, 0, 6, 4, 5);
        test("((?=a)|a){0,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("((?=[ab])|a){0,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("((?<=a)|a){0,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("a((?<=a)|a){0,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(a*?){11,11}?b", "", "aaaaaaaaaaaaaaaaaaaaaaaaab", 0, true, 0, 26, 0, 25);
        test("\\w(?<=\\W([l-w]{0,19}?){1,2}\\w)\\2\ua2d2\\1\\z", "", "[qowwllu3\u0002\ua2d2qowwlluz", 0, true, 8, 19, 1, 8);
        test("(?:a(b{0,19})c)", "", "abbbbbbbcdebbbbbbbf", 0, true, 0, 9, 1, 8);
        test("(?:a(b{0,19})c)de", "", "abbbbbbbcdebbbbbbbf", 0, true, 0, 11, 1, 8);
        test("(?<=a(b{0,19})c)de", "", "abbbbbbbcdebbbbbbbf", 0, true, 9, 11, 1, 8);
        test("(?<=a(b{0,19}){1,2}c)de", "", "abbbbbbbcdebbbbbbbf", 0, true, 9, 11, 1, 8);
        test("(?<=a(b{0,19}){2,2}c)de", "", "abbbbbbbcdebbbbbbbf", 0, true, 9, 11, 1, 1);
        test("c(?<=a(b{0,19}){1,2}c)de\\1f", "", "abbbbbbbcdebbbbbbbf", 0, true, 8, 19, 1, 8);
        test("[\ud0d9](?<=\\S)", "", "\ud0d9", 0, true, 0, 1);
        test("[\ud0d9](?<=\\W)", "", "\ud0d9", 0, true, 0, 1);
        test("\u0895(?<=\\S)", "", "\u0895", 0, true, 0, 1);
        test("\u0895(?<=\\W)", "", "\u0895", 0, true, 0, 1);
        test("[\u8053](?<=\\S)", "", "\u8053", 0, true, 0, 1);
        test("[\u8053](?<=\\W)", "", "\u8053", 0, true, 0, 1);
        test("\u0895(?<=\\S)", "", "\u0895", 0, true, 0, 1);
        test("\u0895(?<=\\W)", "", "\u0895", 0, true, 0, 1);
        test("\u0895|[\u8053\ud0d9]+(?<=\\S\\W\\S)", "", "\ud0d9\ud0d9\ud0d9\ud0d9", 0, true, 0, 4);
        test("\u0895|[\u8053\ud0d9]+(?<=\\S\\W\\S)", "", "\ud0d9\ud0d9\ud0d9\ud0d9", 0, true, 0, 4);
        test("\u0895|[\u8053\ud0d9]+(?<=\\S\\W\\S)", "", "\ud0d9\ud0d9\ud0d9\ud0d9", 0, true, 0, 4);
        test("a|[bc]+(?<=[abc][abcd][abc])", "", "bbbb", 0, true, 0, 4);
        test("a(b*)*c\\1d", "", "abbbbcbbd", 0, true, 0, 9, 3, 5);
        test("(|a)||b(?<=cde)|", "", "a", 0, true, 0, 0, 0, 0);
        test("^(\\1)?\\D*", "s", "empty", 0, true, 0, 5, -1, -1);
        test("abcd(?<=d|c()d)", "", "_abcd", 0, true, 1, 5, -1, -1);
        test("\\Dw\u3aa7\\A\\S(?<=\ue3b3|\\A()\\S)", "", "\udad1\udcfaw\u3aa7A\ue3b3", 0, true, 1, 6, -1, -1);
        test("a(?:c|b(?=()))*", "", "abc", 0, true, 0, 3, -1, -1);
        test("a(?:c|b(?=(c)))*", "", "abc", 0, true, 0, 3, -1, -1);
        test("a(?:c|(?<=(a))b)*", "", "abc", 0, true, 0, 3, -1, -1);
        test("(a||b){15,18}c", "", "ababaabbaaac", 0, true, 0, 12, 10, 11);
        test("(a||b){15,18}?c", "", "ababaabbaaac", 0, true, 0, 12, 10, 11);
        test("(?:ab|c|^){103,104}", "", "abcababccabccabababccabcababcccccabcababababccccabcabcabccabcabcccabababccabababcababababccababccabcababcabcabccabababccccabcab", 0, true, 0, 127);
        test("((?<=a)bec)*d", "", "abecd", 0, true, 1, 5, 1, 4);
        test("(|(^|\\z){2,77}?)?", "", "empty", 0, true, 0, 0, -1, -1, -1, -1);
        test("a(|a{15,36}){10,11}", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 37, 1, 37);
        test("a(|a{15,36}?){10,11}", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 16, 1, 16);
        test("a(|a{15,36}){10,11}$", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 66, 37, 66);
        test("a(|a{15,36}?){10,11}b$", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", 0, true, 0, 67, 30, 66);
        test("(?:a()|b??){22,26}c", "", "aabbbaabaaaaaabaaaac", 0, true, 0, 20, 19, 19);
        test("b()(a\\1|){4,4}\\2c", "", "baaaac", 0, true, 0, 6, 1, 1, 3, 4);
        test("a((?=b()|)[a-d])+", "", "abbbcbd", 0, true, 0, 7, 6, 7, -1, -1);
        test("a(?=b(?<=ab)()|)", "", "ab", 0, true, 0, 1, 2, 2);
        test("[ab]*?$(?<=[^b][ab][^b])", "", "aaaaaa", 0, true, 0, 6);
        test("a(?<=([ab]+){0,5})", "", "bbbba", 0, true, 4, 5, 0, 5);
        test("([ab]+){0,5}", "", "bbbba", 0, true, 0, 5, 0, 5);
        expectSyntaxError("[--a]", "v", "", getTRegexEncoding(), "empty", 0, ErrorCode.InvalidCharacterClass);
        test("(?:^\\1|$){10,11}bc", "", "aaaaaabc", 0, false);
        test("a(?:|[0-9]+?a|[0-9a]){11,13}?[ab]", "", "a372a466a109585878b", 0, true, 0, 5);

        /* GENERATED CODE END - KEEP THIS MARKER FOR AUTOMATIC UPDATES */
    }
}
