/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.parser.flavors.java.JavaFlags;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.EmptyArrays;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class JavaUtilPatternTests extends RegexTestBase {

    @Override
    String getEngineOptions() {
        return "Flavor=JavaUtilPattern,PythonMethod=search";
    }

    @Test
    public void lookbehindReluctantQuantifier() {
        test("(?<=b{1,4}?)foo", 0, "%bbbbfoo");
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_16;
    }

    @Test
    public void helloWorld() {
        test("[Hh]ello [Ww]orld!", 0, "hello World!");
    }

    @Test
    public void documentationSummary() {
        // Based on "Summary of regular-expression constructs" from
        // https://download.java.net/java/early_access/jdk21/docs/api/java.base/java/util/regex/Pattern.html#lt

        // Characters
        test("a", 0, "a");
        // test("\\\\", 0, "\\");
        test("\\07", 0, "\u0007");
        test("\\077", 0, "\u003f");
        test("\\0377", 0, "\u0179");
        test("\\xaa", 0, "\u00aa");
        test("\\uaaaa", 0, "\uaaaa");
        test("\\x{10ffff}", 0, new String(new int[]{0x10ffff}, 0, 1));
        test("\\N{WHITE SMILING FACE}", 0, "\u263A");
        test("\\t", 0, "\u0009");
        test("\\n", 0, "\n");
        test("\\r", 0, "\r");
        test("\\f", 0, "\f");
        test("\\a", 0, "\u0007");
        test("\\e", 0, "\u001B");
        test("\\c@", 0, "\u0000");

        // Character classes
        test("[abc]", 0, "a");
        test("[^abc]", 0, "a");
        test("[a-zA-Z]", 0, "B");
        test("[a-d[m-p]]", 0, "o");
        test("[a-z&&[def]]", 0, "e");
        test("[a-z&&[^bc]]", 0, "b");
        test("[a-z&&[^m-p]]", 0, "o");

        // Predefined character classes
        test(".", 0, "\u0a6b");
        test("\\d", 0, "8");
        test("\\D", 0, "a");
        test("\\h", 0, "\u2000");
        test("\\H", 0, "c");
        test("\\s", 0, "\u000B");
        test("\\S", 0, "a");
        test("\\v", 0, "\\u2028");
        test("\\V", 0, "a");
        test("\\w", 0, "a");
        test("\\W", 0, "-");

        // POSIX character classes (US-ASCII only)
        test("\\p{Lower}", 0, "b");
        test("\\p{Upper}", 0, "B");
        test("\\p{ASCII}", 0, "\u007A");
        test("\\p{Alpha}", 0, "h");
        test("\\p{Digit}", 0, "8");
        test("\\p{Alnum}", 0, "6");
        test("\\p{Punct}", 0, ">");
        test("\\p{Graph}", 0, "@");
        test("\\p{Print}", 0, "\u0020");
        test("\\p{Blank}", 0, "\t");
        test("\\p{Cntrl}", 0, "\u007F");
        test("\\p{XDigit}", 0, "F");
        test("\\p{Space}", 0, "\f");

        // java.lang.Character classes (simple java character type)
        test("\\p{javaLowerCase}", 0, "\u03ac");
        test("\\p{javaUpperCase}", 0, "\u03dc");
        test("\\p{javaWhitespace}", 0, " ");
        test("\\p{javaMirrored}", 0, "\u220a");

        // Classes for Unicode scripts, blocks, categories and binary properties
        test("\\p{IsLatin}", 0, "b");
        test("\\p{InGreek}", 0, "\u03A3");
        test("\\p{Lu}", 0, "H");
        test("\\p{IsAlphabetic}", 0, "j");
        test("\\p{Sc}", 0, "$");
        test("\\P{InGreek}", 0, "a");
        test("[\\p{L}&&[^\\p{Lu}]]", 0, "l");

        // Boundary matchers
        test("^", 0, "");
        test("$", 0, "");
        test("\\b", 0, " a", 1);
        // test("\\b{g}", 0, "");
        test("\\B", 0, "b");
        test("\\A", 0, "");
        // test("\\G", 0, "");
        test("\\Z", 0, "");
        test("\\z", 0, "");

        // Linebreak matcher
        // test("\\R", 0, "\r\n");

        // Unicode Extended Grapheme matcher
        // test("\\X", 0, "e\u00b4");

        // Greedy quantifiers
        test("a?a", 0, "a");
        test("a*a", 0, "aaaa");
        test("a+a", 0, "aaaa");
        test("a{5}", 0, "aaaaa");
        test("a{5,}a", 0, "aaaaaaa");
        test("a{5,10}", 0, "aaaaaaaaaa");

        // Reluctant quantifiers
        test("a??a", 0, "a");
        test("a*?a", 0, "aaaa");
        test("a+?a", 0, "aaaa");
        test("a{5}?", 0, "aaaaa");
        test("a{5,}?a", 0, "aaaaaaa");
        test("a{5,10}?", 0, "aaaaaaaaaa");

        // Possessive quantifiers
        // test("a?+a", 0, "a");
        // test("a*+a", 0, "aaaa");
        // test("a++a", 0, "aaaa");
        // test("a{5}+", 0, "aaaaa");
        // test("a{5,}+a", 0, "aaaaaaa");
        // test("a{5,10}+", 0, "aaaaaaaaaa");

        // Logical operators
        test("ab", 0, "ab");
        test("a|b", 0, "b");
        test("(abc)", 0, "abc");

        // Back references
        test("(.*)xxx\\1", 0, "abcxxxabc");
        test("(?<name>.*)xxx\\1", 0, "abc");

        // Quotation
        test("\\*", 0, "*");
        test("\\Q***\\E", 0, "***");

        // Special constructs (named-capturing and non-capturing)
        test("(?<name>abc)", 0, "abc");
        test("(?:abc)", 0, "abc");
        test("(?iu-xU)abc", 0, "aBc");
        test("(?iu-xU:abc)  ", 0, "aBC");
        test("(?=abc).*", 0, "abcde");
        test("(?!abc).*", 0, "bcdef");
        test(".*(?<=abc)", 0, "aaaaabc");
        test(".*(?<!abc)", 0, "aaaab");
        // test("(?>X)", 0, "X");

        test("", 0, "");
    }

    @Test
    public void characterClassAllowedContents() {
        // Characters
        test("a", 0, "a");
        // test("\\\\", 0, "\\");
        test("[\\07]", 0, "\u0007");
        test("[\\077]", 0, "\u003f");
        test("[\\0377]", 0, "\u0179");
        test("[\\xaa]", 0, "\u00aa");
        test("[\\uaaaa]", 0, "\uaaaa");
        test("[\\x{10ffff}]", 0, new String(new int[]{0x10ffff}, 0, 1));
        test("[\\N{WHITE SMILING FACE}]", 0, "\u263A");
        test("[\\t]", 0, "\u0009");
        test("[\\n]", 0, "\n");
        test("[\\r]", 0, "\r");
        test("[\\f]", 0, "\f");
        test("[\\a]", 0, "\u0007");
        test("[\\e]", 0, "\u001B");
        test("[\\c@]", 0, "\u0000");

        // Predefined character classes
        test("[.]", 0, "\u0a6b");
        test("[\\d]", 0, "8");
        test("[\\D]", 0, "a");
        test("[\\h]", 0, "\u2000");
        test("[\\H]", 0, "c");
        test("[\\s]", 0, "\u000B");
        test("[\\S]", 0, "a");
        test("[\\v]", 0, "\\u2028");
        test("[\\V]", 0, "a");
        test("[\\w]", 0, "a");
        test("[\\W]", 0, "-");

        // POSIX character classes (US-ASCII only)
        test("[\\p{Lower}]", 0, "b");
        test("[\\p{Upper}]", 0, "B");
        test("[\\p{ASCII}]", 0, "\u007A");
        test("[\\p{Alpha}]", 0, "h");
        test("[\\p{Digit}]", 0, "8");
        test("[\\p{Alnum}]", 0, "6");
        test("[\\p{Punct}]", 0, ">");
        test("[\\p{Graph}]", 0, "@");
        test("[\\p{Print}]", 0, "\u0020");
        test("[\\p{Blank}]", 0, "\t");
        test("[\\p{Cntrl}]", 0, "\u007F");
        test("[\\p{XDigit}]", 0, "F");
        test("[\\p{Space}]", 0, "\f");

        // java.lang.Character classes (simple java character type)
        test("[\\p{javaLowerCase}]", 0, "\u03ac");
        test("[\\p{javaUpperCase}]", 0, "\u03dc");
        test("[\\p{javaWhitespace}]", 0, " ");
        test("[\\p{javaMirrored}]", 0, "\u220a");

        // Classes for Unicode scripts, blocks, categories and binary properties
        test("[\\p{IsLatin}]", 0, "b");
        test("[\\p{InGreek}]", 0, "\u03A3");
        test("[\\p{Lu}]", 0, "H");
        test("[\\p{IsAlphabetic}]", 0, "j");
        test("[\\p{Sc}]", 0, "$");
        test("[\\P{InGreek}]", 0, "a");
        test("[[\\p{L}&&[^\\p{Lu}]]]", 0, "l");

        // Boundary matchers
        test("[^]", 0, "");
        test("[$]", 0, "");
        test("[\\b]", 0, " a", 1);
        // test("\\b{g}", 0, "");
        test("[\\B]", 0, "b");
        test("[\\A]", 0, "");
        // test("\\G", 0, "");
        test("[\\Z]", 0, "");
        test("[\\z]", 0, "");

        // Linebreak matcher
        test("\\R", 0, "\r\n");

        // Unicode Extended Grapheme matcher
        // test("\\X", 0, "e\u00b4");

        // Greedy quantifiers
        test("[a?a]", 0, "a");
        test("[a*a]", 0, "aaaa");
        test("[a+a]", 0, "aaaa");
        test("[a{5}]", 0, "aaaaa");
        test("[a{5,}a]", 0, "aaaaaaa");
        test("[a{5,10}]", 0, "aaaaaaaaaa");

        // Reluctant quantifiers
        test("[a??a]", 0, "a");
        test("[a*?a]", 0, "aaaa");
        test("[a+?a]", 0, "aaaa");
        test("[a{5}?]", 0, "aaaaa");
        test("[a{5,}?a]", 0, "aaaaaaa");
        test("[a{5,10}?]", 0, "aaaaaaaaaa");

        // Possessive quantifiers
        // test("a?+a", 0, "a");
        // test("a*+a", 0, "aaaa");
        // test("a++a", 0, "aaaa");
        // test("a{5}+", 0, "aaaaa");
        // test("a{5,}+a", 0, "aaaaaaa");
        // test("a{5,10}+", 0, "aaaaaaaaaa");

        // Logical operators
        test("[ab]", 0, "ab");
        test("[a|b]", 0, "b");
        test("[(abc)]", 0, "abc");

        // Back references
        test("[(.*)xxx\\1]", 0, "abcxxxabc");
        test("[(?<name>.*)xxx\\1]", 0, "abc");

        // Quotation
        test("[\\*]", 0, "*");
        // test("[\\Q***\\E]", 0, "***");

        // Special constructs (named-capturing and non-capturing)
        test("[(?<name>abc)]", 0, "abc");
        test("[(?:abc)]", 0, "abc");
        test("[(?iu-xU)abc]", 0, "aBc");
        test("[(?iu-xU:abc)  ]", 0, "aBC");
        test("[(?=abc).*]", 0, "abcde");
        test("[(?!abc).*]", 0, "bcdef");
        test("[.*(?<=abc)]", 0, "aaaaabc");
        test("[.*(?<!abc)]", 0, "aaaab");
        test("[(?>X)]", 0, "X");

        test("test(\"[(.*)xxx\\\\1]\", 0, \"abcxxxabc\")", 0, "");
    }

    @Test
    public void backslashBEscape() {
        // test("[\\b]", 0, "");
        test("\\b", 0, " a");
        test("\\B", 0, "aa ");
        test("[\\b]", 0, "");
        test("[\\B]", 0, "");
        test("[\\b{g}]", 0, "");
    }

    @Test
    public void quoting() {
        test("\\Q^$a.[b\\E", 0, "^$a.[b");
        test("\\Q^$a.[b\\E.*", 0, "^$a.[b7j\u00e0t");
        test("\\Q^$a.[b\\Eabc", 0, "^$a.[babc");
        test("\\Q^$a.[b\\...\\Q.*", 0, "^$a.[bxxx.*");
        test("\\Q^$a.[b\\E", 0, "^$a.[");
        test("\\Q\\E", 0, "");
        test("\\Q\\E", 0, "a");
        test("\\Q^$a.[b", 0, "^$a.[b");
        test("abc\\Q", 0, "abc");
        test("\\Q^$a.[b\\E", 0, "^$a.[b");
    }

    @Test
    public void linebreak() {
        test("\\R", 0, "\r\n");
        test("\\R", 0, "\n");
        test("\\R", 0, "\u000b");
        test("\\R", 0, "\u000c");
        test("\\R", 0, "\r");
        test("\\R", 0, "\u0085");
        test("\\R", 0, "\u2028");
        test("\\R", 0, "\u2029");

        test(".*\\Rab", 0, "abcd\r\nab");
        test("\\R", 0, "\n\n");
    }

    @Test
    public void unicodeWordBoundary() {
        int[] flagsCombinations = new int[]{0, Pattern.UNICODE_CHARACTER_CLASS, Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE};
        String[] patterns = new String[]{"\\w", "\\W", "\\b", "\\B"};
        char[] words = new char[]{'a', '\u03b1', '-', '\u212a', '\u017f'};

        for (int flags : flagsCombinations) {
            for (String pat : patterns) {
                test(pat, flags, " ");
                test("\\b", flags, "  ");

                for (char ch : words) {
                    test(pat, flags, "" + ch);

                    test(pat, flags, ch + " ");
                    test(pat, flags, " " + ch);

                    test(pat, flags, "" + ch + ch);
                    test(pat, flags, ch + " " + ch);
                    test(pat, flags, "-" + ch + "-");
                }
            }
        }
    }

    @Test
    public void unicodeCaseWordBoundary() {
        for (int flags : new int[]{0, Pattern.UNICODE_CASE, Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE}) {
            test("\\w", flags, "\u017f");
            test("\\w", flags, "\u212a");
            test("\\W", flags, "\u017f");
            test("\\W", flags, "\u212a");

            test("\\b", flags, "\u017f ", 1);
            test("\\b", flags, "\u212a ", 1);
            test("\\B", flags, "\u017f ", 1);
            test("\\B", flags, "\u212a ", 1);
        }
    }

    @Test
    public void dotTest() {
        test(".", 0, "x");
        for (int flags : new int[]{0, Pattern.DOTALL}) {
            test(".", flags, "\r\n");
            for (String s : new String[]{"\r", "\n", "\u0085", "\u2028", "\u2029"}) {
                test(".", flags, s);
            }
        }
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
    public void hexEscape() {
        test("\\x40", 0, "\u0040");
        test("\\x400", 0, "\u00400");
        test("\\x{400}", 0, "\u0400");
        test("\\x{10ffff}", 0, Character.toString(Character.MAX_CODE_POINT));
        test("\\x{10FFff}", 0, Character.toString(Character.MAX_CODE_POINT));
        test("\\u0400", 0, "\u0400");
        test("\\u04GG", 0, "");
        test("\\u04", 0, "");
    }

    @Test
    public void hexEscapeErrors() {
        test("\\x", 0, "");
        test("\\x{", 0, "");
        test("\\x{}", 0, "");
        test("\\x{g}", 0, "");
        test("\\x{11ffff}", 0, "");
        test("\\x{fgff}", 0, "");
    }

    @Test
    public void octalEscape() {
        test("\\00", 0, "\u0000");
        test("\\07", 0, "\u0007");
        test("\\077", 0, "\u003f");
        test("\\0377", 0, "\u0179");
        test("\\0477", 0, "\u0179");
        test("\\0477", 0, "\u00277");
        test("\\0777", 0, "\u003f7");
        test("\\0078", 0, "\u00078");
        test("\\08", 0, "");

        test("[\\07]*", 0, "\u0007\u0007\u0007\u0007");

        test("[\\00-\\0377]*", 0, "abcd1234AAA");
        test("[a&&\\0377]*", 0, "a");
    }

    @Test
    public void controlEscape() {
        test("\\c@", 0, "\u0000");
        test("\\cA", 0, "\u0001");
        test("\\c@", 0, "a");
        test("\\c", 0, "");
        test("\\c@abc", 0, "\u0000abc");
    }

    @Test
    public void surrogatePairs() {
        String s = "\udbea\udfcd";
        test(".", 0, s);
        test(String.format("[%s]", s), 0, s);
        test(String.format("[%s]", s), 0, "" + s.charAt(0));
        test(String.format("[%s]", s), 0, "" + s.charAt(1));
        test(String.format("[\\%s]", s), 0, s);
    }

    @Test
    public void backslashEscape() {
        test("\\\\", 0, "\\");
        test("\\^", 0, "^");
        test("\\~", 0, "~");
        test("\\j", 0, "");
        test("\\\udbea\udfcd", 0, "\\udbea\\udfcd");
    }

    @Test
    public void jckHexNotation() {
        test("A\\ud800\\udc00B", 0, "A\uD800\uDC00B");
        test("A\\ud800\\udc00", 0, "A\uD800\uDC00");
        test("A\\ud800", 0, "A\uD800");
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
        test("[^ab[^cd]ef]", 0, "a");
        test("[^ab[^cd]ef&&aa]", 0, "a");
        test("[ab&&[^c]]", 0, "a");
        test("[ab&&[^c]]&&^c", 0, "a");
        test("[ab&&[^c]]&&^c", 0, "a");
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
        test("\\p{Letter}", 0, "1");
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

    @Test(expected = UnsupportedOperationException.class)
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
        test("?", 0, "ab");
        test("*", 0, "ab");
        test("+", 0, "ab");
        test("{1,2}", 0, "ab");
        test("x{2,1}", 0, "ab");
        test("x{0}", 0, "ab");
        test("x{,0}", 0, "ab");
        test("x{,}", 0, "ab");
        test("x{1,}", 0, "ab");
        test(String.format("x{1,%d}", Integer.MAX_VALUE), 0, "ab");
        test(String.format("x{1,%d}", ((long) Integer.MAX_VALUE) + 1), 0, "ab");
        test("x{-1,0}", 0, "ab");
        test("x{-1,1}", 0, "ab");
        test("x{-1,1}", 0, "ab");
        test("(?=x)*", 0, "ab");
        test("(?=x)*", Pattern.UNICODE_CASE, "ab");
        test("(?<=x)*", 0, "ab");
        test("(?<=x)*", Pattern.UNICODE_CASE, "ab");

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

    @Test(expected = UnsupportedOperationException.class)
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
        test("(a)\\1", 0, "aa");
        test("(a)\\10", 0, "aa0");
        test("(a)\\100", 0, "aa00");
        test("(a)\\0100", 0, "aa\u0040");
        test("(a)\\01000", 0, "aa\u0040");
        test("(a)\\9", 0, "aa");
        test("(a)\\99", 0, "aa");
        test("(a)\\0", 0, "aa");
        test("(a)\\2", 0, "aa");
        test("()()()()()()()()(a)()\\99", 0, "aa9");

        test("(\\2())", 0, "");
        test("(\\1a)", 0, "aa");
        test("(\\1a|){2}", 0, "aa");
        test("(()b|\\2a){2}", 0, "ba");
        test("(a\\1)", 0, "aa");
        test("(a|b\\1){2}", 0, "aba");
        test("(a|(b\\1)){2}", 0, "aba");
        test("((a)|b\\1){2}", 0, "aba");
        test("((a|b\\1)){2}", 0, "aba");
        test("((a|b\\2)){2}", 0, "aba");
        test("((a)|b\\2){2}", 0, "aba");
        test("((a)|b\\2)*", 0, "aba");
        test("(a|b\\1*){2}", 0, "abaaaa");
        test("^(a|\\1b)+$", 0, "ab");
        test("(a)\\10", 0, "aa0");
        test("(a)\\100", 0, "aa00");

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
    public void incompleteQuantifier() {
        test("a{1,", 0, "a{1,");
    }

    @Test
    public void characterClassSetIntersection() {
        test("[a&&&]", 0, "a");
        test("[a&&&]", 0, "&");

        test("[a&&b]", 0, "a");
        test("[a&&b]", 0, "b");
        test("[a&&b]", 0, "&");

        test("[a&b]", 0, "a");
        test("[a&b]", 0, "b");
        test("[a&b]", 0, "&");

        test("[a&&&&]", 0, "a");
        test("[a&&&&&]", 0, "a");
        test("[a&&&&&&]", 0, "a");
        test("[a&&&&&&&]", 0, "a");

        test("[a&&&&]", 0, "&");
        test("[a&&&&&]", 0, "&");
        test("[a&&&&&&]", 0, "&");
        test("[a&&&&&&&]", 0, "&");

        test("[&]", 0, "&");

        test("[a&&]", 0, "a");
        test("[a&&]", 0, "&");
        test("[&&a]", 0, "a");
        test("[&&a]", 0, "&");
        // test("[&", 0, "a");
    }

    @Test
    public void characterClassSetRanges() {
        test("a", 0, "a");
        test("[a]", 0, "a");
        test("[a-b]", 0, "a");
        test("[a-d]", 0, "b");
        test("[a-b]", 0, "b");
        test("[a-b]", 0, "c");
        test("[a-bd]", 0, "c");
        test("[a-bd]", 0, "d");
        test("[a-bd\\d]", 0, "2");
        test("[a-]", 0, "a");
        test("[a-]", 0, "-");
        test("[a-b]", 0, "-");
        test("[a-]", 0, "-");
        test("[-a]", 0, "a");
        test("[-a]", 0, "-");

        for (char c = 'a'; c <= 'g'; c++) {
            test("[a[b-d][e-f]]", 0, "" + c);
        }
    }

    @Test
    public void characterClassSetNested() {
        for (char c = 'a'; c <= 'g'; c++) {
            test("[a[b-d]&&[c-f]]", 0, "" + c);
            test("[b-[c-d]]&&[a]&&[e-f]", 0, "" + c);
        }
    }

    @Test
    public void characterClassSyntaxError() {
        test("[&&]", 0, "&");
        test("[b-a]", 0, "a");
        test("[a", 0, "a");
        test("[a-", 0, "a");
        test("[[a-]", 0, "a");
        test("[[a-b]&&", 0, "a");
    }

    @Test
    public void characterClassEdgeCases() {
        test("[]", 0, "");
        test("[^]", 0, "");
        test("[\\[]", 0, "");
        test("[&", 0, "");
        test("[a&&b]", 0, "");
        test("[a&&b&&[[]]", 0, "");
    }

    private Stream<String> generateCases(List<Character> chars, int length) {
        if (length == 0) {
            return Stream.of("");
        } else {
            return chars.stream().flatMap(pre -> generateCases(chars, length - 1).map(su -> pre + su));
        }
    }

    @Test
    public void flagStack() {
        test("a", 0, "a");
        test("a", 0, "A");

        test("a", Pattern.CASE_INSENSITIVE, "a");
        test("a", Pattern.CASE_INSENSITIVE, "A");

        test("a(?i:a)", 0, "aa");
        test("a(?i:a)", 0, "aA");
        test("a(?i)(?i)(?i)A", 0, "aa");
        test("a(?i)A", 0, "aA");

        generateCases(List.of('a', 'A'), 3).forEach(s -> {
            test("a(?i:a(a))", 0, s);
            test("a(?i:a(?-i:a))", 0, s);
            test("a(?i:a(a))", 0, s);
        });

        generateCases(List.of('a', 'A'), 3).forEach(s -> {
            test("a(?i:a(?-i:a(?i:a)a)a)", 0, s);
            test("a(?i)a(?-i)a(?i:a)a(?i)a", 0, s);
            test("(?i:a(?-i:a(?i:a))a(?-i)a)a", 0, s);
            test("a(?i:a(a(?-i)a(a)a))", 0, s);
        });

        test("(?)", 0, "");
        test("(?-)", 0, "");
        test("(?-:)", 0, "a");
        test("(?-:a)", 0, "a");

        test("(?i)", 0, "");
        test("(?i-)", 0, "");
        test("(?i-:)", 0, "a");
        test("(?i-:a)", 0, "a");
    }

    @Test
    public void unicodeCase() {
        test("a\u00e0", Pattern.CASE_INSENSITIVE, "a\u00e0");
        test("aa", Pattern.CASE_INSENSITIVE, "aA");
        test("aa", Pattern.CASE_INSENSITIVE, "a\u00c0"); // should not match because
                                                         // Pattern.UNICODE_CASE
        // is not set
        test("a\u00e0", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "a\u00c0");

        // unicode case should match 'K' with "kelvin K" (0x212A)
        test("k", 0, "\u212a");
        test("k", Pattern.CASE_INSENSITIVE, "\u212a");
        test("k", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "\u212a");
        test("K", 0, "\u212a");
        test("K", Pattern.CASE_INSENSITIVE, "\u212a");
        test("K", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "\u212a");
        test("[a-z]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "\u212a");

        // unicode case should match 'I' with '\u0130'
        test("i", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "\u0130");

        // unicode case should not match 'A' with '\u00c0'
        test("a", 0, "\u00c0");
        test("a", Pattern.CASE_INSENSITIVE, "\u00c0");
        test("a", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, "\u00c0");
    }

    @Test
    public void quantifiersCombinations() {
        test("(?=aa)*", 0, "");
        test("(a|*)", 0, "");
        test("(a|{1,2}|{1,2})", 0, "");
        test("(a|{1,2}|{1,2})", 0, "a");
        test("*", 0, "");
        test("**", 0, "");
        test("a**", 0, "");
        test("a*?", 0, "");
        test("a**?", 0, "");
        test("a+?", 0, "");
        test("a??", 0, "");
        test("a{0,0}*", 0, "");
        test("a{0,0}{0,0}*", 0, "");
        test("a{1,2}{3,9}", 0, "");
        test("a{1,5}*{1,2}", 0, "");
    }

    @Test
    public void characterName() {
        test("\\N{WHITE SMILING FACE}", 0, "\u263A");
        test("\\N{whiTe SMilIng fACE}", 0, "\u263A");
        test("\\N{WHITE SMILING FACE}abc", 0, "\u263Aabc");
        test("\\N{WHITE SMILI}", 0, "\u263A");
        test("\\N{WHITE SMILING FACE", 0, "\u263A");
        test("\\N{}", 0, "\u263A");
        test("\\N{", 0, "\u263A");
        test("\\N", 0, "\u263A");
        test("\\Na", 0, "\u263A");
        test("\\Nabc{}", 0, "\u263A");
    }

    @Test
    public void unicodeCharacterPropertyGeneralCategory() {
        test("\\pL", 0, "A"); // no curly braces needed if single character
        test("\\p{Lu}", 0, "A");
        test("\\p{IsLu}", 0, "A");
        test("\\p{gc=Lu}", 0, "A");
        test("\\p{general_category=Lu}", 0, "A");

        test("\\p{SomeUnknownCategory}", 0, "A");
        test("\\p{IsSomeUnknownCategory}", 0, "A");
        test("\\p{gc=IsSomeUnknownCategory}", 0, "A");
    }

    @Test
    public void unicodeCharacterPropertyScript() {
        test("\\p{IsLatin}", 0, "A");
        test("\\p{sc=Latin}", 0, "A");
        test("\\p{script=Latin}", 0, "A");

        test("\\p{SomeUnknownScript}", 0, "A");
        test("\\p{IsSomeUnknownScript}", 0, "A");
        test("\\p{gc=SomeUnknownScript}", 0, "A");
    }

    @Test
    public void unicodeCharacterPropertyBlock() {
        // epsilon
        test("\\p{InGreek}", 0, "\u03b5");
        test("\\p{blk=Greek}", 0, "\u03b5");
        test("\\p{block=Greek}", 0, "\u03b5");

        test("\\p{SomeUnknownBlock}", 0, "\u03b5");
        test("\\p{IsSomeUnknownBlock}", 0, "\u03b5");
        test("\\p{gc=SomeUnknownBlock}", 0, "\u03b5");
    }

    @Test
    public void unicodeCharacterPropertyErrors() {
        test("\\p{abc", 0, "");
        test("\\p{", 0, "");
        test("\\p{}", 0, "");
        test("\\p", 0, "");
        test("\\p{unknown}", 0, "");
        test("\\p{unknown=unknown}", 0, "");
        test("\\p{blk=Non_Existent_Block}", 0, "");
        test("\\p{sc=Non_Existent_Script}", 0, "");
    }

    @Test
    @Ignore
    public void unicodeProperties() {
        String[] properties = new String[]{
                        "Cn",
                        "Lu",
                        "Ll",
                        "Lt",
                        "Lm",
                        "Lo",
                        "Mn",
                        "Me",
                        "Mc",
                        "Nd",
                        "Nl",
                        "No",
                        "Zs",
                        "Zl",
                        "Cc",
                        "Cf",
                        "Zp",
                        "Co",
                        "Cs",
                        "Pd",
                        "Ps",
                        "Pe",
                        "Pc",
                        "Po",
                        "Sm",
                        "Sc",
                        "Sk",
                        "So",
                        "Pi",
                        "Pf",
                        "L",
                        "M",
                        "N",
                        "Z",
                        "C",
                        "P",
                        "S",
                        "LC",
                        "LD",
                        "L1",
                        "all",
                        "ASCII",
                        "Alnum",
                        "Alpha",
                        "Blank",
                        "Cntrl",
                        "Digit",
                        "Graph",
                        "Lower",
                        "Print",
                        "Punct",
                        "Space",
                        "Upper",
                        "XDigit",
                        "javaLowerCase",
                        "javaUpperCase",
                        "javaAlphabetic",
                        "javaIdeographic",
                        "javaTitleCase",
                        "javaDigit",
                        "javaDefined",
                        "javaLetter",
                        "javaLetterOrDigit",
                        "javaJavaIdentifierStart",
                        "javaJavaIdentifierPart",
                        "javaUnicodeIdentifierStart",
                        "javaUnicodeIdentifierPart",
                        "javaIdentifierIgnorable",
                        "javaSpaceChar",
                        "javaWhitespace",
                        "javaISOControl",
                        "javaMirrored"
        };

        for (String prop : properties) {
            for (int j = 0; j <= 0x10FFFF; j++) {
                String s = new String(new int[]{j}, 0, 1);
                test(String.format("\\p{%s}", prop), 0, s);
                test(String.format("\\p{Is%s}", prop), Pattern.CASE_INSENSITIVE, s);
                test(String.format("\\P{%s}", prop), 0, s);
                test(String.format("\\P{Is%s}", prop), Pattern.CASE_INSENSITIVE, s);
            }
        }
    }

    @Test
    @Ignore
    public void unicodePOSIX() {
        String[] properties = new String[]{
                        "ALPHA",
                        "LOWER",
                        "UPPER",
                        "SPACE",
                        "PUNCT",
                        "XDIGIT",
                        "ALNUM",
                        "CNTRL",
                        "DIGIT",
                        "BLANK",
                        "GRAPH",
                        "PRINT"
        };

        for (String prop : properties) {
            for (int j = 0; j <= 0x10FFFF; j++) {
                String s = new String(new int[]{j}, 0, 1);
                test(String.format("\\p{Is%s}", prop), 0, s);
                test(String.format("\\p{Is%s}", prop), Pattern.CASE_INSENSITIVE, s);
                test(String.format("\\P{%s}", prop), 0, s);
                test(String.format("\\P{Is%s}", prop), Pattern.CASE_INSENSITIVE, s);
            }
        }
    }

    @Test
    @Ignore
    public void unicodePredicates() {
        String[] properties = new String[]{
                        "ALPHABETIC",
                        "ASSIGNED",
                        "CONTROL",
                        "EMOJI",
                        "EMOJI_PRESENTATION",
                        "EMOJI_MODIFIER",
                        "EMOJI_MODIFIER_BASE",
                        "EMOJI_COMPONENT",
                        "EXTENDED_PICTOGRAPHIC",
                        "HEXDIGIT",
                        "HEX_DIGIT",
                        "IDEOGRAPHIC",
                        "JOINCONTROL",
                        "JOIN_CONTROL",
                        "LETTER",
                        "LOWERCASE",
                        "NONCHARACTERCODEPOINT",
                        "NONCHARACTER_CODE_POINT",
                        "TITLECASE",
                        "PUNCTUATION",
                        "UPPERCASE",
                        "WHITESPACE",
                        "WHITE_SPACE",
                        "WORD"
        };

        for (String prop : properties) {
            for (int j = 0; j <= 0x10FFFF; j++) {
                String s = new String(new int[]{j}, 0, 1);
                test(String.format("\\p{Is%s}", prop), 0, s);
                test(String.format("\\p{Is%s}", prop), Pattern.CASE_INSENSITIVE, s);
                test(String.format("\\P{%s}", prop), 0, s);
                test(String.format("\\P{Is%s}", prop), Pattern.CASE_INSENSITIVE, s);
            }
        }
    }

    @Test
    public void unsupportedOperations() {
        Assert.assertTrue(compileRegex("(?>X)", "").isNull());
        Assert.assertTrue(compileRegex("\\X", "").isNull());
        Assert.assertTrue(compileRegex("\\G", "").isNull());
        Assert.assertTrue(compileRegex("\\b{g}", "").isNull());
        Assert.assertTrue(compileRegex("abc", "c").isNull());
    }

    @Test
    public void badIntersectionSyntax() {
        // this produces a specific error for some weird reason
        test("[\\u0100a&&]", 0, "");
    }

    void test(String pattern, int flags, String input) {
        test(pattern, flags, input, 0);
    }

    void test(String pattern, int javaFlags, String input, int fromIndex) {
        String flags = new JavaFlags(javaFlags).toString();
        try {
            Matcher m = Pattern.compile(pattern, javaFlags).matcher(input);
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
            test(pattern, flags, input, fromIndex, isMatch, groupBoundaries);
        } catch (PatternSyntaxException javaPatternException) {
            try {
                compileRegex(pattern, flags, "", getTRegexEncoding());
            } catch (PolyglotException tRegexException) { // TODO why do we need PolyglotException
                // instead of RegexSyntaxException?
                Assert.assertTrue(tRegexException.getMessage().contains(javaPatternException.getDescription()));
                return;
            }
            Assert.fail("expected syntax exception: " + javaPatternException.getDescription());
        }
    }
}
