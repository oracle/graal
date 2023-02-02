/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
}
