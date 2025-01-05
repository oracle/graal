/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.RegexSyntaxException.ErrorCode;
import com.oracle.truffle.regex.tregex.string.Encodings;

public class RubyUTF8Tests extends RegexTestBase {

    @Override
    String getEngineOptions() {
        return "Flavor=Ruby";
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_8;
    }

    @Test
    public void ignoreCaseBackReferences() {
        test("^(\uff21)(a)\\1\\2$", "i", "\uff21a\uff41A", 0, true, 0, 8, 0, 3, 3, 4);
    }

    @Test
    public void generatedTests() {
        /* GENERATED CODE BEGIN - KEEP THIS MARKER FOR AUTOMATIC UPDATES */

        // Generated using Ruby version 3.3.5
        test("(?=.*c)a(()b?)?c", "", "ac", 0, true, 0, 2, 1, 1, 1, 1);
        test("(?=.*c)a(b*)?c", "", "ac", 0, true, 0, 2, 1, 1);
        test("(?=.*c)a(()b*)?c", "", "ac", 0, true, 0, 2, 1, 1, 1, 1);
        test("(?=.*b)a{2}", "", "aaab", 0, true, 0, 2);
        test("a{2}?", "", "c", 0, true, 0, 0);
        test("a{2,4}?", "", "c", 0, false);
        test("a+?", "", "c", 0, false);
        test("a{2}?(b)?c", "", "c", 0, true, 0, 1, -1, -1);
        test("(?>(aa)?)+", "", "a", 0, true, 0, 0, -1, -1);
        test("(|a+?){0,4}b", "", "aaab", 0, true, 0, 4, 1, 3);
        test("(a{2}|())+$", "", "aaaa", 0, true, 0, 4, 4, 4, 4, 4);
        test("^a(b*)\\1{4,6}?", "", "abbbb", 0, true, 0, 1, 1, 1);
        test("^a(b*)\\1{4,6}?", "", "abbbbb", 0, true, 0, 6, 1, 2);
        test("a(?:c|b(?=()))*", "", "abc", 0, true, 0, 3, 2, 2);
        test("a(?:c|b(?=(c)))*", "", "abc", 0, true, 0, 3, 2, 3);
        test("a(?:c|(?<=(a))b)*", "", "abc", 0, true, 0, 3, 0, 1);
        test("\\Z", "", "\r", 0, true, 1, 1);
        test("(?<=\\A)", "", "\r", 0, true, 0, 0);
        test("(?<=\\b)", "", "\r", 0, false);
        test("(?<=\\B)", "", "\r", 0, true, 0, 0);
        expectSyntaxError("(?<=+?)", "", "", getTRegexEncoding(), "error", 0, ErrorCode.InvalidQuantifier);
        test("(?<=)", "", "empty", 0, true, 0, 0);
        test("()?", "", "", 0, true, 0, 0, 0, 0);
        test("(a*)?", "", "", 0, true, 0, 0, 0, 0);
        test("(a*)*", "", "", 0, true, 0, 0, 0, 0);
        test("(?:a|()){50,100}", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 50, 50, 50);
        test("()??", "", "", 0, true, 0, 0, -1, -1);
        test("(a*?)?", "", "", 0, true, 0, 0, 0, 0);
        test("(a*)??", "", "", 0, true, 0, 0, -1, -1);
        test("(a*?)??", "", "", 0, true, 0, 0, -1, -1);
        test("(a*?)*", "", "", 0, true, 0, 0, 0, 0);
        test("(a*)*?", "", "", 0, true, 0, 0, -1, -1);
        test("(a*?)*?", "", "", 0, true, 0, 0, -1, -1);
        test("(a|\\2b|())*", "", "aaabbb", 0, true, 0, 6, 6, 6, 6, 6);
        test("(a|\\2b|()){2,4}", "", "aaabbb", 0, true, 0, 3, 3, 3, 3, 3);
        test("(a|\\2b|\\3()|())*", "", "aaabbb", 0, true, 0, 6, 6, 6, 6, 6, 3, 3);
        test("(a|\\2b|\\3()|()){2,4}", "", "aaabbb", 0, true, 0, 3, 3, 3, -1, -1, 3, 3);
        test("(a|\\2b|()){20,24}", "", "aaaaaaaaaaaaaaaaaaaabbbbb", 0, true, 0, 23, 22, 23, 20, 20);
        test("(a|\\2b|())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1);
        test("(a|\\2b|()){2,4}", "", "aaabbb", 0, true, 0, 3, 3, 3, 3, 3);
        test("(a|\\2b|\\3()|())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1, -1, -1);
        test("(a|\\2b|\\3()|()){2,4}", "", "aaabbb", 0, true, 0, 3, 3, 3, -1, -1, 3, 3);
        test("(a|\\2b|()){20,24}", "", "aaaaaaaaaaaaaaaaaaaabbbbb", 0, true, 0, 23, 22, 23, 20, 20);
        test("(?:|a)*", "", "aaa", 0, true, 0, 0);
        test("(?:()|a)*", "", "aaa", 0, true, 0, 0, 0, 0);
        test("(|a)*", "", "aaa", 0, true, 0, 0, 0, 0);
        test("(()|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()\\1(?:|a)*", "", "aaa", 0, true, 0, 0, 0, 0);
        test("()\\1(?:()|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()\\1(|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()\\1(()|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0, 0, 0);
        test("()(?:\\1|a)*", "", "aaa", 0, true, 0, 0, 0, 0);
        test("()(?:()\\1|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()(?:(\\1)|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()(?:\\1()|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()(\\1|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()(()\\1|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0, 0, 0);
        test("()((\\1)|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0, 0, 0);
        test("()(\\1()|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0, 0, 0);
        test("(?:(?=a)|a)*", "", "aaa", 0, true, 0, 0);
        test("(?:(?=a)()|a)*", "", "aaa", 0, true, 0, 0, 0, 0);
        test("(?:()(?=a)|a)*", "", "aaa", 0, true, 0, 0, 0, 0);
        test("(?:((?=a))|a)*", "", "aaa", 0, true, 0, 0, 0, 0);
        test("()\\1(?:(?=a)|a)*", "", "aaa", 0, true, 0, 0, 0, 0);
        test("()\\1(?:(?=a)()|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()\\1(?:()(?=a)|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("()\\1(?:((?=a))|a)*", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("(?:|a)*?", "", "aaa", 0, true, 0, 0);
        test("(?:()|a)*?", "", "aaa", 0, true, 0, 0, -1, -1);
        test("(|a)*?", "", "aaa", 0, true, 0, 0, -1, -1);
        test("(()|a)*?", "", "aaa", 0, true, 0, 0, -1, -1, -1, -1);
        test("()\\1(?:|a)*?", "", "aaa", 0, true, 0, 0, 0, 0);
        test("()\\1(?:()|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("()\\1(|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("()\\1(()|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1, -1, -1);
        test("()(?:\\1|a)*?", "", "aaa", 0, true, 0, 0, 0, 0);
        test("()(?:()\\1|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("()(?:(\\1)|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("()(?:\\1()|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("()(\\1|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("()(()\\1|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1, -1, -1);
        test("()((\\1)|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1, -1, -1);
        test("()(\\1()|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1, -1, -1);
        test("(?:(?=a)|a)*?", "", "aaa", 0, true, 0, 0);
        test("(?:(?=a)()|a)*?", "", "aaa", 0, true, 0, 0, -1, -1);
        test("(?:()(?=a)|a)*?", "", "aaa", 0, true, 0, 0, -1, -1);
        test("(?:((?=a))|a)*?", "", "aaa", 0, true, 0, 0, -1, -1);
        test("()\\1(?:(?=a)|a)*?", "", "aaa", 0, true, 0, 0, 0, 0);
        test("()\\1(?:(?=a)()|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("()\\1(?:()(?=a)|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("()\\1(?:((?=a))|a)*?", "", "aaa", 0, true, 0, 0, 0, 0, -1, -1);
        test("(|a|\\2b|())*", "", "aaabbb", 0, true, 0, 0, 0, 0, -1, -1);
        test("(a||\\2b|())*", "", "aaabbb", 0, true, 0, 3, 3, 3, -1, -1);
        test("(a|\\2b||())*", "", "aaabbb", 0, true, 0, 3, 3, 3, -1, -1);
        test("(a|\\2b|()|)*", "", "aaabbb", 0, true, 0, 6, 6, 6, 6, 6);
        test("(()|a|\\3b|())*", "", "aaabbb", 0, true, 0, 0, 0, 0, 0, 0, -1, -1);
        test("(a|()|\\3b|())*", "", "aaabbb", 0, true, 0, 3, 3, 3, 3, 3, -1, -1);
        test("(a|\\2b|()|())*", "", "aaabbb", 0, true, 0, 6, 6, 6, 6, 6, -1, -1);
        test("(a|\\3b|()|())*", "", "aaabbb", 0, true, 0, 3, 3, 3, 3, 3, -1, -1);
        test("(a|()|())*", "", "aaa", 0, true, 0, 3, 3, 3, 3, 3, -1, -1);
        test("^(()|a|())*$", "", "aaa", 0, true, 0, 3, 3, 3, 3, 3, -1, -1);
        test("(|a|\\2b|())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1);
        test("(a||\\2b|())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1);
        test("(a|\\2b||())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1);
        test("(a|\\2b|()|)*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1);
        test("(()|a|\\3b|())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1, -1, -1);
        test("(a|()|\\3b|())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1, -1, -1);
        test("(a|\\2b|()|())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1, -1, -1);
        test("(a|\\3b|()|())*?", "", "aaabbb", 0, true, 0, 0, -1, -1, -1, -1, -1, -1);
        test("(a|()|())*?", "", "aaa", 0, true, 0, 0, -1, -1, -1, -1, -1, -1);
        test("^(()|a|())*?$", "", "aaa", 0, true, 0, 3, 2, 3, 2, 2, -1, -1);
        test("((A|){7,10}?){10,17}", "", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 0, true, 0, 86, 86, 86, 86, 86);
        test("(a{1,30}){1,4}", "", "a", 0, true, 0, 1, 0, 1);
        test("((a|){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 7, 7, 7, 7, 7);
        test("((a?){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 7, 7, 7, 7, 7);
        test("((|a){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((a??){4,6}){4,6}", "", "aaaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((a?){4,6}){4,6}", "", "aaaaaa", 0, true, 0, 6, 6, 6, 6, 6);
        test("(a|){4,6}", "", "a", 0, true, 0, 1, 1, 1);
        test("(a|){4,6}", "", "aa", 0, true, 0, 2, 2, 2);
        test("(a|){4,6}", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|){4,6}", "", "aaaa", 0, true, 0, 4, 4, 4);
        test("(a|){4,6}", "", "aaaaa", 0, true, 0, 5, 5, 5);
        test("(a|){4,6}", "", "aaaaaa", 0, true, 0, 6, 5, 6);
        test("(a|){4,6}", "", "aaaaaaa", 0, true, 0, 6, 5, 6);
        test("(a|){4,6}?", "", "a", 0, true, 0, 1, 1, 1);
        test("(a|){4,6}?", "", "aa", 0, true, 0, 2, 2, 2);
        test("(a|){4,6}?", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|){4,6}?", "", "aaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}?", "", "aaaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}?", "", "aaaaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}?", "", "aaaaaaa", 0, true, 0, 4, 3, 4);
        test("(a|){4,6}?a", "", "a", 0, true, 0, 1, 0, 0);
        test("(a|){4,6}?a", "", "aa", 0, true, 0, 2, 1, 1);
        test("(a|){4,6}?a", "", "aaa", 0, true, 0, 3, 2, 2);
        test("(a|){4,6}?a", "", "aaaa", 0, true, 0, 4, 3, 3);
        test("(a|){4,6}?a", "", "aaaaa", 0, true, 0, 5, 3, 4);
        test("(a|){4,6}?a", "", "aaaaaa", 0, true, 0, 5, 3, 4);
        test("(a|){4,6}?a", "", "aaaaaaa", 0, true, 0, 5, 3, 4);
        test("(a|){4,6}?a", "", "aaaaaaaa", 0, true, 0, 5, 3, 4);
        test("(|a){4,6}a", "", "a", 0, true, 0, 1, 0, 0);
        test("(|a){4,6}a", "", "aa", 0, true, 0, 1, 0, 0);
        test("(|a){4,6}a", "", "aaa", 0, true, 0, 1, 0, 0);
        test("(|a){4,6}a", "", "aaaa", 0, true, 0, 1, 0, 0);
        test("(|a){4,6}a", "", "aaaaa", 0, true, 0, 1, 0, 0);
        test("(|a){4,6}a", "", "aaaaaa", 0, true, 0, 1, 0, 0);
        test("(|a){4,6}a", "", "aaaaaaa", 0, true, 0, 1, 0, 0);
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
        test("((|a){4,6}){4,6}", "", "a", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
        test("((|a){4,6}){4,6}", "", "aaaaaaaaaaaaa", 0, true, 0, 0, 0, 0, 0, 0);
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
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaa", 0, true, 0, 13, 13, 13, 13, 13);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaa", 0, true, 0, 14, 14, 14, 14, 14);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaaa", 0, true, 0, 15, 15, 15, 15, 15);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaa", 0, true, 0, 16, 16, 16, 16, 16);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaaa", 0, true, 0, 17, 17, 17, 17, 17);
        test("((a|){4,6}?){4,6}", "", "aaaaaaaaaaaaaaaaaa", 0, true, 0, 18, 18, 18, 18, 18);
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
        test("(a{2}|())+$", "", "aaaa", 0, true, 0, 4, 4, 4, 4, 4);
        test("^a(b*)\\1{4,6}?", "", "abbbb", 0, true, 0, 1, 1, 1);
        test("^a(b*)\\1{4,6}?", "", "abbbbb", 0, true, 0, 6, 1, 2);
        test("(?<=|$)", "", "a", 0, true, 0, 0);
        test("(?=ab)a", "", "ab", 0, true, 0, 1);
        test("(?=()|^)|x", "", "empty", 0, true, 0, 0, 0, 0);
        test("a(?<=ba)", "", "ba", 0, true, 1, 2);
        expectSyntaxError("(?<=(?<=a)[])", "i", "", getTRegexEncoding(), "empty", 0, ErrorCode.InvalidCharacterClass);
        test("\\d\\W", "i", "4\u017f", 0, true, 0, 3);
        test("[\u08bc-\ucf3a]", "i", "\u03b0", 0, true, 0, 2);
        test("[\u0450-\u6c50]\u7e57\u55ad()\u64e7\\d|", "i", "\u03b0\u7e57\u55ad\u64e79", 0, true, 0, 12, 8, 8);
        test("(?<=(?<=a)b^c)c", "", "abcc", 0, false);
        test("a(?:|()\\1){1,2}", "", "a", 0, true, 0, 1, -1, -1);
        test("[a-z][a-z\u2028\u2029].|ab(?<=[a-z]w.)", "", "aac", 0, true, 0, 3);
        test("(animation|animation-name)", "", "animation", 0, true, 0, 9, 0, 9);
        test("(a|){7,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(a|){7,7}?b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(|a){7,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(|a){7,7}?b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(a||b){7,7}c", "", "aaabc", 0, true, 0, 5, 4, 4);
        test("(a||b){7,7}c", "", "aaac", 0, true, 0, 4, 3, 3);
        test("(a||b){7,7}c", "", "aaabac", 0, true, 0, 6, 5, 5);
        test("($|a){7,7}", "", "aaa", 0, true, 0, 3, 3, 3);
        test("($|a){7,7}?", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|$){7,7}", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|$){7,7}?", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|$|b){7,7}", "", "aaab", 0, true, 0, 4, 4, 4);
        test("(a|$|b){7,7}", "", "aaa", 0, true, 0, 3, 3, 3);
        test("(a|$|b){7,7}", "", "aaaba", 0, true, 0, 5, 5, 5);
        test("((?=a)|a){7,7}b", "", "aaa", 0, false);
        test("((?=[ab])|a){7,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("((?<=a)|a){7,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("a((?<=a)|a){7,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(a|){0,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(a|){0,7}?b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(|a){0,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(|a){0,7}?b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("(a||b){0,7}c", "", "aaabc", 0, true, 0, 5, 4, 4);
        test("(a||b){0,7}c", "", "aaac", 0, true, 0, 4, 3, 3);
        test("(a||b){0,7}c", "", "aaabac", 0, true, 0, 6, 5, 5);
        test("((?=a)|a){0,7}b", "", "aaab", 0, true, 0, 4, 2, 3);
        test("((?=[ab])|a){0,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("((?<=a)|a){0,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("a((?<=a)|a){0,7}b", "", "aaab", 0, true, 0, 4, 3, 3);
        test("(a*){11,11}b", "", "aaaaaaaaaaaaaaaaaaaaaaaaab", 0, true, 0, 26, 25, 25);
        test("(?:a(b{0,19})c)", "", "abbbbbbbcdebbbbbbbf", 0, true, 0, 9, 1, 8);
        test("(?:a(b{0,19})c)de", "", "abbbbbbbcdebbbbbbbf", 0, true, 0, 11, 1, 8);
        test("[\ud0d9](?<=\\S)", "", "\ud0d9", 0, true, 0, 3);
        test("[\ud0d9](?<=\\W)", "", "\ud0d9", 0, true, 0, 3);
        test("\u0895(?<=\\S)", "", "\u0895", 0, true, 0, 3);
        test("\u0895(?<=\\W)", "", "\u0895", 0, true, 0, 3);
        test("[\u8053](?<=\\S)", "", "\u8053", 0, true, 0, 3);
        test("[\u8053](?<=\\W)", "", "\u8053", 0, true, 0, 3);
        test("\u0895(?<=\\S)", "", "\u0895", 0, true, 0, 3);
        test("\u0895(?<=\\W)", "", "\u0895", 0, true, 0, 3);
        test("\u0895|[\u8053\ud0d9]+(?<=\\S\\W\\S)", "", "\ud0d9\ud0d9\ud0d9\ud0d9", 0, true, 0, 12);
        test("\u0895|[\u8053\ud0d9]+(?<=\\S\\W\\S)", "", "\ud0d9\ud0d9\ud0d9\ud0d9", 0, true, 0, 12);
        test("\u0895|[\u8053\ud0d9]+(?<=\\S\\W\\S)", "", "\ud0d9\ud0d9\ud0d9\ud0d9", 0, true, 0, 12);
        test("a|[bc]+(?<=[abc][abcd][abc])", "", "bbbb", 0, true, 0, 4);
        test("a(b*)*c\\1d", "", "abbbbcbbd", 0, true, 0, 9, 3, 5);
        test("(|a)||b(?<=cde)|", "", "a", 0, true, 0, 0, 0, 0);
        test("^(\\1)?\\D*", "", "empty", 0, true, 0, 5, -1, -1);
        test("abcd(?<=d|c()d)", "", "_abcd", 0, true, 1, 5, -1, -1);
        test("\\Dw\u3aa7\\A\\S(?<=\ue3b3|\\A()\\S)", "", "\udad1\udcfaw\u3aa7A\ue3b3", 0, false);
        test("a(?:c|b(?=()))*", "", "abc", 0, true, 0, 3, 2, 2);
        test("a(?:c|b(?=(c)))*", "", "abc", 0, true, 0, 3, 2, 3);
        test("a(?:c|(?<=(a))b)*", "", "abc", 0, true, 0, 3, 0, 1);
        test("(a||b){15,18}c", "", "ababaabbaaac", 0, true, 0, 12, 11, 11);
        test("(a||b){15,18}?c", "", "ababaabbaaac", 0, true, 0, 12, 10, 11);
        test("(?:ab|c|^){103,104}", "", "abcababccabccabababccabcababcccccabcababababccccabcabcabccabcabcccabababccabababcababababccababccabcababcabcabccabababccccabcab", 0, true, 0, 0);
        test("((?<=a)bec)*d", "", "abecd", 0, true, 1, 5, 1, 4);
        test("(|(^|\\z){2,77}?)?", "", "empty", 0, true, 0, 0, 0, 0, -1, -1);
        test("a(|a{15,36}){10,11}", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 1, 1, 1);
        test("a(|a{15,36}?){10,11}", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 1, 1, 1);
        test("a(|a{15,36}){10,11}$", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 0, true, 0, 66, 66, 66);
        test("a(|a{15,36}?){10,11}b$", "", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", 0, true, 0, 67, 66, 66);
        test("(?:a()|b??){22,26}c", "", "aabbbaabaaaaaabaaaac", 0, true, 0, 20, 19, 19);
        test("b()(a\\1|){4,4}\\2c", "", "baaaac", 0, true, 0, 6, 1, 1, 3, 4);
        test("a((?=b()|)[a-d])+", "", "abbbcbd", 0, true, 0, 7, 6, 7, 6, 6);
        test("a(|b){5,7}c", "", "abbbc", 0, true, 0, 5, 4, 4);
        test("a(|b){5,8}c", "", "abbbc", 0, true, 0, 5, 4, 4);
        test("a(|b){5,9}c", "", "abbbc", 0, true, 0, 5, 4, 4);
        test("a(|b){5,}c", "", "abbbc", 0, true, 0, 5, 4, 4);
        test("a((?<=a)|b){5,7}c", "", "abbbc", 0, false);
        test("a((?<=a)|b){5,8}c", "", "abbbc", 0, false);
        test("a((?<=a)|b){5,9}c", "", "abbbc", 0, false);
        test("a((?<=a)|b){5,}c", "", "abbbc", 0, false);
        test("[ab]*?\\Z(?<=[^b][ab][^b])", "", "aaaaaa", 0, true, 0, 6);
        test("(?<=a(b){3,3}?)", "", "abbb", 0, true, 4, 4, 3, 4);
        test("\\A(?<seg>(?:%\\h\\h|[!$&-.0-9:;=@A-Z_a-z~/])){0}((?!/)\\g<seg>++)\\z", "x", "ftp://example.com/%2Ffoo", 0, true, 0, 24, 23, 24);

        /* GENERATED CODE END - KEEP THIS MARKER FOR AUTOMATIC UPDATES */
    }
}
