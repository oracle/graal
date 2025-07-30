/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.test.generated;

import static com.oracle.truffle.regex.tregex.string.Encodings.UTF_16;
import static com.oracle.truffle.regex.tregex.test.generated.TestCase.match;
import static com.oracle.truffle.regex.tregex.test.generated.TestCase.noMatch;
import static com.oracle.truffle.regex.tregex.test.generated.TestCase.syntaxError;
import static com.oracle.truffle.regex.tregex.test.generated.TestCase.testCase;

import com.oracle.truffle.regex.RegexSyntaxException.ErrorCode;

public class JavaGeneratedTests {

    public static final TestCase[] TESTS = {
        // @formatter:off
        /* GENERATED CODE BEGIN - KEEP THIS MARKER FOR AUTOMATIC UPDATES */

        // Generated using Java version 26
        testCase("((A|){7,10}?){10,17}", "", UTF_16, match("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 0, 0, 86, 86, 86, 86, 86)),
        testCase("(a{1,30}){1,4}", "", UTF_16, match("a", 0, 0, 1, 0, 1)),
        testCase("((a|){4,6}){4,6}", "", UTF_16, match("aaaaaaa", 0, 0, 7, 7, 7, 7, 7)),
        testCase("((a?){4,6}){4,6}", "", UTF_16,
            match("aaaaaaa", 0, 0, 7, 7, 7, 7, 7),
            match("aaaaaa", 0, 0, 6, 6, 6, 6, 6)),
        testCase("((|a){4,6}){4,6}", "", UTF_16, match("aaaaaaa", 0, 0, 0, 0, 0, 0, 0)),
        testCase("((a??){4,6}){4,6}", "", UTF_16, match("aaaaaaa", 0, 0, 0, 0, 0, 0, 0)),
        testCase("(a|^){100}", "", UTF_16,
            match("a", 0, 0, 0, 0, 0),
            match("aa", 0, 0, 0, 0, 0),
            noMatch("aa", 1),
            noMatch("ab", 1)),
        testCase("(.)\\1{2,}", "", UTF_16, noMatch("billiam", 0)),
        testCase("(^_(a{1,2}[:])*a{1,2}[:]a{1,2}([.]a{1,4})?_)+", "", UTF_16, match("_a:a:a.aaa_", 0, 0, 11, 0, 11, 1, 3, 6, 10)),
        testCase("(a{2}|())+$", "", UTF_16, match("aaaa", 0, 0, 4, 4, 4, 4, 4)),
        testCase("^a(b*)\\1{4,6}?", "", UTF_16,
            match("abbbb", 0, 0, 1, 1, 1),
            match("abbbbb", 0, 0, 6, 1, 2)),
        testCase("(?<=|$)", "", UTF_16, match("a", 0, 0, 0)),
        testCase("(?=ab)a", "", UTF_16, match("ab", 0, 0, 1)),
        testCase("(?=()|^)|x", "", UTF_16, match("empty", 0, 0, 0, 0, 0)),
        testCase("a(?<=ba)", "", UTF_16, match("ba", 0, 1, 2)),
        testCase("(?<=(?=|()))", "", UTF_16, match("aa", 0, 0, 0, -1, -1)),
        testCase("\\d\\W", "iv", UTF_16, match("4\u017f", 0, 0, 2)),
        testCase("[\u08bc-\ucf3a]", "iv", UTF_16, noMatch("\u03b0", 0)),
        testCase("a(?:|()\\1){1,2}", "", UTF_16, match("a", 0, 0, 1, -1, -1)),
        testCase("|(?<\\d\\1)\ub7e4", "", UTF_16, syntaxError(ErrorCode.InvalidNamedGroup)),
        testCase("[a-z][a-z\u2028\u2029].|ab(?<=[a-z]w.)", "", UTF_16, match("aac", 0, 0, 3)),
        testCase("(animation|animation-name)", "", UTF_16, match("animation", 0, 0, 9, 0, 9)),
        testCase("(a|){7,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("(a|){7,7}?b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("(|a){7,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("(|a){7,7}?b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("(a||b){7,7}c", "", UTF_16,
            match("aaabc", 0, 0, 5, 4, 4),
            match("aaac", 0, 0, 4, 3, 3),
            match("aaabac", 0, 0, 6, 5, 5)),
        testCase("($|a){7,7}", "", UTF_16, match("aaa", 0, 0, 3, 3, 3)),
        testCase("($|a){7,7}?", "", UTF_16, match("aaa", 0, 0, 3, 3, 3)),
        testCase("(a|$){7,7}", "", UTF_16, match("aaa", 0, 0, 3, 3, 3)),
        testCase("(a|$){7,7}?", "", UTF_16, match("aaa", 0, 0, 3, 3, 3)),
        testCase("(a|$|b){7,7}", "", UTF_16,
            match("aaab", 0, 0, 4, 4, 4),
            match("aaa", 0, 0, 3, 3, 3),
            match("aaaba", 0, 0, 5, 5, 5)),
        testCase("((?=a)|a){7,7}b", "", UTF_16, noMatch("aaa", 0)),
        testCase("((?=[ab])|a){7,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("((?<=a)|a){7,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("a((?<=a)|a){7,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("(a|){0,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("(a|){0,7}?b", "", UTF_16, match("aaab", 0, 0, 4, 2, 3)),
        testCase("(|a){0,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("(|a){0,7}?b", "", UTF_16, match("aaab", 0, 0, 4, 2, 3)),
        testCase("(a||b){0,7}c", "", UTF_16,
            match("aaabc", 0, 0, 5, 4, 4),
            match("aaac", 0, 0, 4, 3, 3),
            match("aaabac", 0, 0, 6, 5, 5)),
        testCase("((?=a)|a){0,7}b", "", UTF_16, match("aaab", 0, 0, 4, 2, 3)),
        testCase("((?=[ab])|a){0,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("((?<=a)|a){0,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("a((?<=a)|a){0,7}b", "", UTF_16, match("aaab", 0, 0, 4, 3, 3)),
        testCase("(a*?){11,11}?b", "", UTF_16, match("aaaaaaaaaaaaaaaaaaaaaaaaab", 0, 0, 26, 10, 25)),
        testCase("(?:a(b{0,19})c)", "", UTF_16, match("abbbbbbbcdebbbbbbbf", 0, 0, 9, 1, 8)),
        testCase("(?:a(b{0,19})c)de", "", UTF_16, match("abbbbbbbcdebbbbbbbf", 0, 0, 11, 1, 8)),
        testCase("(?<=a(b{0,19})c)de", "", UTF_16, match("abbbbbbbcdebbbbbbbf", 0, 9, 11, 1, 8)),
        testCase("[\ud0d9](?<=\\S)", "", UTF_16, match("\ud0d9", 0, 0, 1)),
        testCase("[\ud0d9](?<=\\W)", "", UTF_16, match("\ud0d9", 0, 0, 1)),
        testCase("\u0895(?<=\\S)", "", UTF_16, match("\u0895", 0, 0, 1)),
        testCase("\u0895(?<=\\W)", "", UTF_16, match("\u0895", 0, 0, 1)),
        testCase("[\u8053](?<=\\S)", "", UTF_16, match("\u8053", 0, 0, 1)),
        testCase("[\u8053](?<=\\W)", "", UTF_16, match("\u8053", 0, 0, 1)),
        testCase("\u0895|[\u8053\ud0d9]+(?<=\\S\\W\\S)", "", UTF_16, match("\ud0d9\ud0d9\ud0d9\ud0d9", 0, 0, 4)),
        testCase("a|[bc]+(?<=[abc][abcd][abc])", "", UTF_16, match("bbbb", 0, 0, 4)),
        testCase("a(b*)*c\\1d", "", UTF_16, match("abbbbcbbd", 0, 0, 9, 3, 5)),
        testCase("(|a)||b(?<=cde)|", "", UTF_16, match("a", 0, 0, 0, 0, 0)),
        testCase("^(\\1)?\\D*", "s", UTF_16, match("empty", 0, 0, 5, -1, -1)),
        testCase("abcd(?<=d|c()d)", "", UTF_16, match("_abcd", 0, 1, 5, -1, -1)),
        testCase("\\Dw\u3aa7\\A\\S(?<=\ue3b3|\\A()\\S)", "", UTF_16, noMatch("\udad1\udcfaw\u3aa7A\ue3b3", 0)),
        testCase("a(?:c|b(?=()))*", "", UTF_16, match("abc", 0, 0, 3, 2, 2)),
        testCase("a(?:c|b(?=(c)))*", "", UTF_16, match("abc", 0, 0, 3, 2, 3)),
        testCase("a(?:c|(?<=(a))b)*", "", UTF_16, match("abc", 0, 0, 3, 0, 1)),
        testCase("(a||b){15,18}c", "", UTF_16, match("ababaabbaaac", 0, 0, 12, 11, 11)),
        testCase("(a||b){15,18}?c", "", UTF_16, match("ababaabbaaac", 0, 0, 12, 11, 11)),
        testCase("(?:ab|c|^){103,104}", "", UTF_16, match("abcababccabccabababccabcababcccccabcababababccccabcabcabccabcabcccabababccabababcababababccababccabcababcabcabccabababccccabcab", 0, 0, 0)),
        testCase("((?<=a)bec)*d", "", UTF_16, match("abecd", 0, 1, 5, 1, 4)),
        testCase("(|(^|\\z){2,77}?)?", "", UTF_16, match("empty", 0, 0, 0, 0, 0, -1, -1)),
        testCase("a(|a{15,36}){10,11}", "", UTF_16, match("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, 0, 1, 1, 1)),
        testCase("a(|a{15,36}?){10,11}", "", UTF_16, match("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, 0, 1, 1, 1)),
        testCase("a(|a{15,36}){10,11}$", "", UTF_16, match("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, 0, 66, 66, 66)),
        testCase("a(|a{15,36}?){10,11}b$", "", UTF_16, match("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", 0, 0, 67, 66, 66)),
        testCase("(?:a()|b??){22,26}c", "", UTF_16, match("aabbbaabaaaaaabaaaac", 0, 0, 20, 19, 19)),
        testCase("b()(a\\1|){4,4}\\2c", "", UTF_16, noMatch("baaaac", 0)),
        testCase("a((?=b()|)[a-d])+", "", UTF_16, match("abbbcbd", 0, 0, 7, 6, 7, 6, 6)),
        testCase("a(?=b(?<=ab)()|)", "", UTF_16, match("ab", 0, 0, 1, 2, 2)),
        testCase("[ab]*?$(?<=[^b][ab][^b])", "", UTF_16, match("aaaaaa", 0, 0, 6)),
        testCase("([ab]+){0,5}", "", UTF_16, match("bbbba", 0, 0, 5, 0, 5)),
        testCase("[--a]", "v", UTF_16, noMatch("empty", 0)),
        testCase("(?:^\\1|$){10,11}bc", "", UTF_16, noMatch("aaaaaabc", 0)),
        testCase("a(?:|[0-9]+?a|[0-9a]){11,13}?[ab]", "", UTF_16, match("a372a466a109585878b", 0, 0, 19)),
        testCase("\\Z", "", UTF_16, match("\r\n", 0, 0, 0)),

        /* GENERATED CODE END - KEEP THIS MARKER FOR AUTOMATIC UPDATES */
        // @formatter:on
    };
}
