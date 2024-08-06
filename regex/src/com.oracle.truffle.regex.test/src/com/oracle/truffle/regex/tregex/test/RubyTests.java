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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.regex.errors.RbErrorMessages;
import com.oracle.truffle.regex.tregex.string.Encodings;

public class RubyTests extends RegexTestBase {

    @Override
    String getEngineOptions() {
        return "Flavor=Ruby";
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_16;
    }

    @Test
    public void gr28693() {
        test("\\A([0-9]+)_([_a-z0-9]*)\\.?([_a-z0-9]*)?\\.rb\\z", "", "20190116152522_enable_postgis_extension.rb", 0, true, 0, 42, 0, 14, 15, 39, 39, 39);
        test("\\A([0-9]+)_([_a-z0-9]*)\\.?([_a-z0-9]*)?\\.rb\\z", "", "20190116152523_create_schools.rb", 0, true, 0, 32, 0, 14, 15, 29, 29, 29);
        test("^0{2}?(00)?(44)?(0)?([1-357-9]\\d{9}|[18]\\d{8}|8\\d{6})$", "", "07123456789", 0, true, 0, 11, -1, -1, -1, -1, 0, 1, 1, 11);
        test("^0{2}?(00)?(44)(0)?([1-357-9]\\d{9}|[18]\\d{8}|8\\d{6})$", "", "447123456789", 0, true, 0, 12, -1, -1, 0, 2, -1, -1, 2, 12);
        test("^0{2}?(00)?44", "", "447123456789", 0, true, 0, 2, -1, -1);

        Assert.assertEquals(5,
                        compileRegex("\n" +
                                        "      ^\n" +
                                        "      ([ ]*) # indentations\n" +
                                        "      (.+) # key\n" +
                                        "      (?::(?=(?:\\s|$))) # :  (without the lookahead the #key includes this when : is present in value)\n" +
                                        "      [ ]?\n" +
                                        "      (['\"]?) # optional opening quote\n" +
                                        "      (.*) # value\n" +
                                        "      \\3 # matching closing quote\n" +
                                        "      $\n" +
                                        "    ", "x").getMember("groupCount").asInt());
    }

    @Test
    public void caseInsensitiveLigatures() {
        // https://bugs.ruby-lang.org/issues/17989

        // LATIN SMALL LIGATURE FF
        test("\ufb00", "i", "FF", 0, true, 0, 2);
        // LATIN SMALL LIGATURE FI
        test("\ufb01", "i", "FI", 0, true, 0, 2);
        // LATIN SMALL LIGATURE FL
        test("\ufb02", "i", "FL", 0, true, 0, 2);
        // LATIN SMALL LIGATURE FFI
        test("\ufb03", "i", "FFI", 0, true, 0, 3);
        // LATIN SMALL LIGATURE FFL
        test("\ufb04", "i", "FFL", 0, true, 0, 3);

        // (ff)i = (ffi)
        test("\ufb00I", "i", "\ufb03", 0, true, 0, 1);
        // (ffi) = (ff)i
        test("\ufb03", "i", "\ufb00I", 0, true, 0, 2);
        // f(fi) = (ffi)
        test("F\ufb01", "i", "\ufb03", 0, true, 0, 1);
        // (ffi) = f(fi)
        test("\ufb03", "i", "F\ufb01", 0, true, 0, 2);

        // (ff)l = (ffl)
        test("\ufb00L", "i", "\ufb04", 0, true, 0, 1);
        // (ffl) = (ff)l
        test("\ufb04", "i", "\ufb00L", 0, true, 0, 2);
        // f(fl) = (ffl)
        test("F\ufb02", "i", "\ufb04", 0, true, 0, 1);
        // (ffl) = f(fl)
        test("\ufb04", "i", "F\ufb02", 0, true, 0, 2);
    }

    @Test
    public void caseInsensitiveFFIExhaustive() {
        String[] variants = new String[]{"ffi", "ffI", "fFi", "fFI", "Ffi", "FfI", "FFi", "FFI", "\ufb00i", "\ufb00I", "f\ufb01", "F\ufb01", "\ufb03"};
        for (String pattern : variants) {
            for (String input : variants) {
                test(pattern, "i", input, 0, true, 0, input.length());
            }
        }
    }

    @Test
    public void greekCaseIgnore() {
        // https://bugs.ruby-lang.org/issues/17989

        // GREEK SMALL LETTER UPSILON WITH PSILI
        test("\u1f50", "i", "\u03c5\u0313", 0, true, 0, 2);
        // GREEK SMALL LETTER UPSILON WITH PSILI AND VARIA
        test("\u1f52", "i", "\u03c5\u0313\u0300", 0, true, 0, 3);
        // GREEK SMALL LETTER UPSILON WITH PSILI AND OXIA
        test("\u1f54", "i", "\u03c5\u0313\u0301", 0, true, 0, 3);
        // GREEK SMALL LETTER UPSILON WITH PSILI AND PERISPOMENI
        test("\u1f56", "i", "\u03c5\u0313\u0342", 0, true, 0, 3);

        // (upsilon psili) varia = (upsilon psili varia)
        test("\u1f50\u0300", "i", "\u1f52", 0, true, 0, 1);
        // (upsilon psili varia) = (upsilon psili) varia
        test("\u1f52", "i", "\u1f50\u0300", 0, true, 0, 2);

        // (upsilon psili) oxia = (upsilon psili oxia)
        test("\u1f50\u0301", "i", "\u1f54", 0, true, 0, 1);
        // (upsilon psili oxia) = (upsilon psili) oxia
        test("\u1f54", "i", "\u1f50\u0301", 0, true, 0, 2);

        // (upsilon psili) perispomeni = (upsilon psili perispomeni)
        test("\u1f50\u0342", "i", "\u1f56", 0, true, 0, 1);
        // (upsilon psili perispomeni) = (upsilon psili) perispomeni
        test("\u1f56", "i", "\u1f50\u0342", 0, true, 0, 2);

        // GREEK SMALL LETTER ALPHA WITH PERISPOMENI
        test("\u1fb6", "i", "\u03b1\u0342", 0, true, 0, 2);
        // GREEK SMALL LETTER ALPHA WITH PERISPOMENI AND YPOGEGRAMMENI
        test("\u1fb7", "i", "\u03b1\u0342\u03b9", 0, true, 0, 3);

        // (alpha perispomeni) ypogegrammeni == (alpha perispomeni ypogegrammeni)
        test("\u1fb6\u03b9", "i", "\u1fb7", 0, true, 0, 1);
        // (alpha perispomeni ypogegrammeni) == (alpha perispomeni) ypogegrammeni
        test("\u1fb7", "i", "\u1fb6\u03b9", 0, true, 0, 2);

        // GREEK SMALL LETTER ETA WITH PERISPOMENI
        test("\u1fc6", "i", "\u03b7\u0342", 0, true, 0, 2);
        // GREEK SMALL LETTER ETA WITH PERISPOMENI AND YPOGEGRAMMENI
        test("\u1fc7", "i", "\u03b7\u0342\u03b9", 0, true, 0, 3);

        // (eta perispomeni) ypogegrammeni == (eta perispomeni ypogegrammeni)
        test("\u1fc6\u03b9", "i", "\u1fc7", 0, true, 0, 1);
        // (eta perispomeni ypogegrammeni) == (eta perispomeni) ypogegrammeni
        test("\u1fc7", "i", "\u1fc6\u03b9", 0, true, 0, 2);

        // GREEK SMALL LETTER OMEGA WITH PERISPOMENI
        test("\u1ff6", "i", "\u03c9\u0342", 0, true, 0, 2);
        // GREEK SMALL LETTER OMEGA WITH PERISPOMENI AND YPOGEGRAMMENI
        test("\u1ff7", "i", "\u03c9\u0342\u03b9", 0, true, 0, 3);

        // (omega perispomeni) ypogegrammeni == (omega perispomeni ypogegrammeni)
        test("\u1ff6\u03b9", "i", "\u1ff7", 0, true, 0, 1);
        // (omega perispomeni ypogegrammeni) == (omega perispomeni) ypogegrammeni
        test("\u1ff7", "i", "\u1ff6\u03b9", 0, true, 0, 2);
    }

    @Test
    public void caseInsensitiveQuantifiers() {
        // https://bugs.ruby-lang.org/issues/17990

        test("f*", "i", "ff", 0, true, 0, 2);
        test("f*", "i", "\ufb00", 0, true, 0, 0);

        test("f+", "i", "ff", 0, true, 0, 2);
        test("f+", "i", "\ufb00", 0, false);

        test("f{1,}", "i", "ff", 0, true, 0, 2);
        test("f{1,}", "i", "\ufb00", 0, false);

        test("f{1,2}", "i", "ff", 0, true, 0, 2);
        test("f{1,2}", "i", "\ufb00", 0, false);

        test("f{,2}", "i", "ff", 0, true, 0, 2);
        test("f{,2}", "i", "\ufb00", 0, true, 0, 0);

        test("ff?", "i", "ff", 0, true, 0, 2);
        test("ff?", "i", "\ufb00", 0, false);

        test("f{2}", "i", "ff", 0, true, 0, 2);
        test("f{2}", "i", "\ufb00", 0, false);

        test("f{2,2}", "i", "ff", 0, true, 0, 2);
        test("f{2,2}", "i", "\ufb00", 0, false);

        // Test that we bail out on strings with complex unfoldings.
        Assert.assertTrue(compileRegex(new String(new char[100]).replace('\0', 'f'), "i").isNull());
    }

    @Test
    public void ruby18009() {
        // https://bugs.ruby-lang.org/issues/18009
        for (int i = 0; i < 26; i++) {
            String input = String.valueOf((char) ('a' + i));
            test("\\W", "i", Encodings.UTF_8, input, 0, false);
            test("[^\\w]", "i", Encodings.UTF_8, input, 0, false);
            test("[[^\\w]]", "i", Encodings.UTF_8, input, 0, false);
            test("[^[^\\w]]", "i", Encodings.UTF_8, input, 0, true, 0, 1);
        }

        test("[\\w]", "i", Encodings.UTF_8, "\u212a", 0, false);
        test("[kx]", "i", Encodings.UTF_8, "\u212a", 0, true, 0, 3);
        test("[\\w&&kx]", "i", Encodings.UTF_8, "\u212a", 0, true, 0, 3);
    }

    @Test
    public void ruby18010() {
        // https://bugs.ruby-lang.org/issues/18010
        test("ff", "i", "\ufb00", 0, true, 0, 1);
        test("[f]f", "i", "\ufb00", 0, false);
        test("f[f]", "i", "\ufb00", 0, false);
        test("[f][f]", "i", "\ufb00", 0, false);
        test("(?:f)f", "i", "\ufb00", 0, false);
        test("f(?:f)", "i", "\ufb00", 0, false);
        test("(?:f)(?:f)", "i", "\ufb00", 0, false);
    }

    @Test
    public void ruby18012() {
        // https://bugs.ruby-lang.org/issues/18012
        test("\\A[\ufb00]\\z", "i", "\ufb00", 0, true, 0, 1);
        test("\\A[\ufb00]\\z", "i", "ff", 0, true, 0, 2);

        test("\\A[^\ufb00]\\z", "i", "\ufb00", 0, false);
        test("\\A[^\ufb00]\\z", "i", "ff", 0, false);

        test("\\A[^[^\ufb00]]\\z", "i", "\ufb00", 0, true, 0, 1);
        test("\\A[^[^\ufb00]]\\z", "i", "ff", 0, true, 0, 2);

        test("\\A[[^[^\ufb00]]]\\z", "i", "\ufb00", 0, true, 0, 1);
        test("\\A[[^[^\ufb00]]]\\z", "i", "ff", 0, true, 0, 2);
    }

    @Test
    public void ruby18013() {
        // https://bugs.ruby-lang.org/issues/18013
        test("[^a-c]", "i", "A", 0, false);
        test("[[^a-c]]", "i", "A", 0, false);

        test("[^a]", "i", "a", 0, false);
        test("[[^a]]", "i", "a", 0, false);
    }

    @Test
    public void multiCodePointCaseFoldAcrossAsciiBoundary() {
        test("\\A[\\W]\\z", "i", "\ufb00", 0, true, 0, 1);
        // When \ufb00 is not fully case-foldable (because it is contributed by \W), it shouldn't
        // be able to match 'ff', because its first character crosses the ASCII boundary from
        // \ufb00.
        test("\\A[\\W]\\z", "i", "ff", 0, false);

        test("\\A[\\p{L}]\\z", "i", "\ufb00", 0, true, 0, 1);
        // When \ufb00 is contributed by some other means, e.g. \p{L}, then it is fully
        // case-foldable and can cross the ASCII boundary.
        test("\\A[\\p{L}]\\z", "i", "ff", 0, true, 0, 2);

        test("\\A[\\W]\\z", "i", "\ufb03", 0, true, 0, 1);
        // This violates the ASCII boundary restriction...
        test("\\A[\\W]\\z", "i", "ffi", 0, false);
        // but it doesn't mean that we drop all multi-code-point expansions. The following
        // expansion, where the first character is not ASCII, is fine.
        test("\\A[\\W]\\z", "i", "\ufb00i", 0, true, 0, 2);

        // And when \ufb00 is fully case-foldable, all expansions are valid.
        test("\\A[\\p{L}]\\z", "i", "\ufb03", 0, true, 0, 1);
        test("\\A[\\p{L}]\\z", "i", "ffi", 0, true, 0, 3);
        test("\\A[\\p{L}]\\z", "i", "\ufb00i", 0, true, 0, 2);
    }

    @Test
    public void caseClosureDoesntEscapeEncodingRange() {
        // This shouldn't throw an AssertionError because of encountering the 'st' ligature.
        test("test", "i", Encodings.LATIN_1, "test", 0, true, 0, 4);
    }

    @Test
    public void ruby13671() {
        // https://bugs.ruby-lang.org/issues/13671
        test("(?<!ass)", "i", "\u2728", 0, true, 0, 0);
        test("(?<!bss)", "i", "\u2728", 0, true, 0, 0);
        test("(?<!as)", "i", "\u2728", 0, true, 0, 0);
        test("(?<!ss)", "i", "\u2728", 0, true, 0, 0);
        test("(?<!ass)", "", "\u2728", 0, true, 0, 0);
        test("(?<!ass)", "i", "x", 0, true, 0, 0);
    }

    @Test
    public void ruby16145() {
        // https://bugs.ruby-lang.org/issues/16145
        test("[xo]", "i", "SHOP", 0, true, 2, 3);
        test("[\u00e9]", "i", "CAF\u00c9", 0, true, 3, 4);
        test("[x\u00e9]", "i", "CAF\u00c9", 0, true, 3, 4);
        test("[x\u00c9]", "i", "CAF\u00c9", 0, true, 3, 4);
    }

    @Test
    public void inverseOfUnicodeCharClassInSmallerEncoding() {
        // check(eval('/\A[[:^alpha:]0]\z/'), %w(0 1 .), "a") from test_posix_bracket in
        // test/mri/tests/ruby/test_regexp.rb
        test("\\A[[:^alpha:]0]\\z", "", Encodings.LATIN_1, "0", 0, true, 0, 1);
        test("\\A[[:^alpha:]0]\\z", "", Encodings.LATIN_1, "1", 0, true, 0, 1);
        test("\\A[[:^alpha:]0]\\z", "", Encodings.LATIN_1, "a", 0, false);
    }

    @Test
    public void treatLeadingClosingBracketsInCharClassesAsLiteralCharacters() {
        // check('\A[]]\z', "]", "") from test_char_class in test/mri/tests/ruby/test_regexp.rb
        test("\\A[]]\\z", "", "]", 0, true, 0, 1);

        // also test in negative char class
        test("\\A[^]]\\z", "", "a", 0, true, 0, 1);
    }

    @Test
    public void ignoreAtomicGroups() {
        test("(?>foo)", "", "IgnoreAtomicGroups=true", "foo", 0, true, 0, 3);
    }

    @Test
    public void reportBacktracking() {
        Assert.assertFalse(compileRegex("(?:foo)+", "").getMember("isBacktracking").asBoolean());
        Assert.assertTrue(compileRegex("(?:foo){64}", "").getMember("isBacktracking").asBoolean());
        Assert.assertTrue(compileRegex("(x+)\\1", "").getMember("isBacktracking").asBoolean());
    }

    @Test
    public void lineBreakEscape() {
        test("\\R", "", "\r", 0, true, 0, 1);
        test("\\R", "", "\n", 0, true, 0, 1);
        test("\\R", "", "\r\n", 0, true, 0, 2);

        test("\\A\\R\\R\\z", "", "\r\r", 0, true, 0, 2);
        test("\\A\\R\\R\\z", "", "\n\n", 0, true, 0, 2);
        test("\\A\\R\\R\\z", "", "\r\n", 0, false);
    }

    @Test
    public void github2412() {
        // Checkstyle: stop line length
        // 1 root capture group and 16 named capture groups
        Assert.assertEquals(1 + 16, compileRegex("           % (?<type>%)\n" +
                        "          | % (?<flags>(?-mix:[ #0+-]|(?-mix:(\\d+)\\$))*)\n" +
                        "            (?:\n" +
                        "              (?: (?-mix:(?<width>(?-mix:\\d+|(?-mix:\\*(?-mix:(\\d+)\\$)?))))? (?-mix:\\.(?<precision>(?-mix:\\d+|(?-mix:\\*(?-mix:(\\d+)\\$)?))))? (?-mix:<(?<name>\\w+)>)?\n" +
                        "                | (?-mix:(?<width>(?-mix:\\d+|(?-mix:\\*(?-mix:(\\d+)\\$)?))))? (?-mix:<(?<name>\\w+)>) (?-mix:\\.(?<precision>(?-mix:\\d+|(?-mix:\\*(?-mix:(\\d+)\\$)?))))?\n" +
                        "                | (?-mix:<(?<name>\\w+)>) (?<more_flags>(?-mix:[ #0+-]|(?-mix:(\\d+)\\$))*) (?-mix:(?<width>(?-mix:\\d+|(?-mix:\\*(?-mix:(\\d+)\\$)?))))? (?-mix:\\.(?<precision>(?-mix:\\d+|(?-mix:\\*(?-mix:(\\d+)\\$)?))))?\n" +
                        "              ) (?-mix:(?<type>[bBdiouxXeEfgGaAcps]))\n" +
                        "              | (?-mix:(?<width>(?-mix:\\d+|(?-mix:\\*(?-mix:(\\d+)\\$)?))))? (?-mix:\\.(?<precision>(?-mix:\\d+|(?-mix:\\*(?-mix:(\\d+)\\$)?))))? (?-mix:\\{(?<name>\\w+)\\})\n" +
                        "            )", "x").getMember("groupCount").asInt());
        // Checkstyle: resume line length
    }

    @Test
    public void beginningAnchor() {
        test("\\Ga", "", "a", 0, true, 0, 1);
        test("\\Ga", "", "ba", 0, false);
        test("\\Ga", "", "ba", 1, true, 1, 2);

        test("\\Ga|\\Gb", "", "a", 0, true, 0, 1);
        test("\\Ga|\\Gb", "", "b", 0, true, 0, 1);
        test("\\Ga|\\Gb", "", "cab", 0, false);
        test("\\Ga|\\Gb", "", "cab", 1, true, 1, 2);
        test("\\Ga|\\Gb", "", "cab", 2, true, 2, 3);
    }

    @Test
    public void nonRecursiveSubexpressionCalls() {
        // numeric subexpression calls
        test("(a)\\g<1>", "", "aa", 0, true, 0, 2, 1, 2);
        // named subexpression calls
        test("(?<foo>foo.)bar\\g<foo>", "", "foo1barfoo2", 0, true, 0, 11, 7, 11);
        test("(?<three_digits>[0-9]{3})-\\g<three_digits>", "", "123-456", 0, true, 0, 7, 4, 7);
        // chained calls
        test("(?<digit>\\d)-(?<two_digits>\\g<digit>\\g<digit>)-(?<five_digits>\\g<two_digits>\\g<digit>\\g<two_digits>)", "", "1-23-45678", 0, true, 0, 10, 9, 10, 8, 10, 5, 10);
        // with forward references
        test("\\g<1>(a)", "", "aa", 0, true, 0, 2, 1, 2);
        test("\\g<foo>bar(?<foo>foo.)", "", "foo1barfoo2", 0, true, 0, 11, 7, 11);
        test("\\g<three_digits>-(?<three_digits>[0-9]{3})", "", "123-456", 0, true, 0, 7, 4, 7);
        test("(?<five_digits>\\g<two_digits>\\g<digit>\\g<two_digits>)-(?<two_digits>\\g<digit>\\g<digit>)-(?<digit>\\d)", "", "12345-67-8", 0, true, 0, 10, 0, 5, 6, 8, 9, 10);
        // quantifier of called group is not copied
        test("(a)+b\\g<1>b", "", "aaabab", 0, true, 0, 6, 4, 5);
        test("(a)+b\\g<1>b", "", "aaabaaab", 0, false);
        // quantifier of subexpression call is preserved
        test("(a)\\g<1>+", "", "aaaa", 0, true, 0, 4, 3, 4);
        // quantifier of subexpression call replaced quantifier of called group
        test("(a)?b\\g<1>+", "", "abaaa", 0, true, 0, 5, 4, 5);
        test("(a)?b\\g<1>+", "", "ab", 0, false);
    }

    @Test
    public void recursiveSubexpressionCalls() {
        expectUnsupported("(a\\g<1>?)(b\\g<2>?)", "");
        expectUnsupported("(?<a>a\\g<b>?)(?<b>b\\g<a>?)", "");
        expectUnsupported("a\\g<0>?", "");
    }

    @Test
    public void atomicGroups() {
        test("(?>foo)(?>bar)", "", "foobar", 0, true, 0, 6);
        test("(?>foo*)obar", "", "foooooooobar", 0, false);

        // quantifiers on atomic groups
        test("\\A\\s*([0-9]+(?>\\.[0-9a-zA-Z]+)*(-[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*)?)?\\s*\\z", "", "0.a", 0, true, 0, 3, 0, 3, -1, -1, -1, -1);
    }

    @Test
    public void quantifiersOnLookarounds() {
        // CRuby doesn't rerun looped lookaround assertions, even though they could be updating the
        // state on each run. Currently, TRegex does the same on the examples below.

        // ?
        // test("(?<=(a))?", "", "a", 1, true, 1, 1, 0, 1);
        test("(?=(a))?", "", "a", 0, true, 0, 0, 0, 1);
        test("(?=\\2()|(a))?", "", "a", 0, true, 0, 0, -1, -1, 0, 1);
        test("(?=\\2()|\\3()|(a))?", "", "a", 0, true, 0, 0, -1, -1, -1, -1, 0, 1);

        // *
        test("(?<=(a))*", "", "a", 1, true, 1, 1, 0, 1);
        test("(?=(a))*", "", "a", 0, true, 0, 0, 0, 1);
        test("(?=\\2()|(a))*", "", "a", 0, true, 0, 0, -1, -1, 0, 1);
        test("(?=\\2()|\\3()|(a))*", "", "a", 0, true, 0, 0, -1, -1, -1, -1, 0, 1);

        // +
        test("(?<=(a))*", "", "a", 1, true, 1, 1, 0, 1);
        test("(?=(a))*", "", "a", 0, true, 0, 0, 0, 1);
        test("(?=\\2()|(a))*", "", "a", 0, true, 0, 0, -1, -1, 0, 1);
        test("(?=\\2()|\\3()|(a))*", "", "a", 0, true, 0, 0, -1, -1, -1, -1, 0, 1);

        // {2}
        test("(?<=(a)){2}", "", "a", 1, true, 1, 1, 0, 1);
        test("(?=(a)){2}", "", "a", 0, true, 0, 0, 0, 1);
        test("(?=\\2()|(a)){2}", "", "a", 0, true, 0, 0, -1, -1, 0, 1);
        test("(?=\\2()|\\3()|(a)){2}", "", "a", 0, true, 0, 0, -1, -1, -1, -1, 0, 1);

        // {3}
        test("(?<=(a))*", "", "a", 1, true, 1, 1, 0, 1);
        test("(?=(a))*", "", "a", 0, true, 0, 0, 0, 1);
        test("(?=\\2()|(a))*", "", "a", 0, true, 0, 0, -1, -1, 0, 1);
        test("(?=\\2()|\\3()|(a))*", "", "a", 0, true, 0, 0, -1, -1, -1, -1, 0, 1);
    }

    @Test
    public void possessiveQuantifiers() {
        test("fooA++bar", "", "fooAAAbar", 0, true, 0, 9);
        test("fooA++Abar", "", "fooAAAbar", 0, false);
        test("fooA?+Abar", "", "fooAAAbar", 0, false);
        test("fooA*+Abar", "", "fooAAAbar", 0, false);

        // Intervals cannot be possessive, the + is treated as another quantifier
        test("foo(A{0,1}+)Abar", "", "fooAAAbar", 0, true, 0, 9, 3, 5);
    }

    @Test
    public void backreferencesHomonymousCaptureGroups() {
        // Homonymous capture groups can be referenced using backreferences.
        test("(?<x>a)\\k<x>(?<x>a)", "", "aaa", 0, true, 0, 3, 0, 1, 2, 3);
        // Named forward references are not allowed.
        expectSyntaxError("(\\k<x>|(?<x>a))+", "", "undefined name <x> reference");
        // A named backreference can only use the values matched by capture groups that precede
        // it lexically (i.e. named forward references do not work).
        test("(?<x>.)((?<y>\\k<x>)|(?<x>a))+", "", "-aa", 0, true, 0, 3, 0, 1, -1, -1, 2, 3);
        // A named backreference can match the contents of any (preceding) capture groups that has
        // the same name.
        test("(?<x>a)?(?<x>b)?\\k<x>", "", "bb", 0, true, 0, 2, -1, -1, 0, 1);
        test("(?<x>a)?(?<x>b)?\\k<x>", "", "aa", 0, true, 0, 2, 0, 1, -1, -1);
        // Lexical order, not index of match nor length of match, determines the priority of
        // choosing the referent.
        test("(?=a(?<x>ab))(?<x>a)ab\\k<x>", "", "aabab", 0, true, 0, 4, 1, 3, 0, 1);
        test("(?<x>a)ab(?<=a(?<x>ab))\\k<x>", "", "aabab", 0, true, 0, 5, 0, 1, 1, 3);
        // When the higher priority referent cannot be matched, the next highest is tried.
        // This is currently broken in CRuby (https://bugs.ruby-lang.org/issues/18631).
        test("(?<x>a)ab(?<=a(?<x>ab))\\k<x>", "", "aaba", 0, true, 0, 4, 0, 1, 1, 3);
    }

    @Test
    public void gr37962() {
        // Minimal test case.
        test("^(?>(aa)?)+$", "", "a", 0, false);
        // Original test case.
        String a1000 = new String(new char[1000]).replace('\0', 'a');
        String a500 = new String(new char[500]).replace('\0', 'a');
        test("^(?>(?=a)(" + a1000 + "|))++$", "", a500, 0, false);
    }

    @Test
    public void nfaTraversalTests() {
        // This relies on correctly maneuvering through the necessary capture groups in the
        // NFATraversalRegexASTVisitor. Since Ruby's empty checks monitor capture groups, capture
        // group updates are stored in quantifier guards and correctly pruning the traversal
        // relies on respecting the quantifier guards.
        test("(?:|())(?:|())(?:|())(?:|())(?:|())(?:|())(?:|())(?:|())\\3\\5\\7", "", "", 0, true, 0, 0, -1, -1, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1);
        // This tests that it is OK to not update a looping capture group on a transition that
        // escapes from it. This should be fine, because the last iteration to match the empty
        // string in the loop will update the capture group and therefore not use the escape
        // transition. The escape transition will only be taken after the next iteration, because
        // only then the empty check will fail. At that point, it is OK not to update the capture
        // group data, because it was already updated by the previous iteration.
        test("()*", "", "", 0, true, 0, 0, 0, 0);
        test("(a|)*", "", "a", 0, true, 0, 1, 1, 1);
    }

    @Test
    public void gr39214() {
        // Compiling a Regexp with a backreference inside an atomic group should not crash.
        test("()(?>\\1)", "", "", 0, true, 0, 0, 0, 0);
    }

    @Test
    public void gr41489() {
        expectUnsupported("\\((?>[^)(]+|\\g<0>)*\\)", "");
    }

    @Test
    public void quantifierOverflow() {
        long max = Integer.MAX_VALUE;
        test(String.format("x{%d,%d}", max, max + 1), "", "x", 0, false);
        test(String.format("x{%d,}", max), "", "x", 0, false);
        test(String.format("x{%d,}", max + 1), "", "x", 0, false);
        expectSyntaxError(String.format("x{%d,%d}", max + 1, max), "", RbErrorMessages.MIN_REPEAT_GREATER_THAN_MAX_REPEAT);
    }

    @Test
    public void testConditionalBackReferencesWithLookArounds() {
        /// Test temporal ordering of lookaround assertions and conditional back-references in DFAs.
        test("(?=(?(1)a|b))(b)", "", "b", 0, true, 0, 1, 0, 1);
        test("(?=x(?(1)a|b))(x)b", "", "xb", 0, true, 0, 2, 0, 1);
        test("(?=xy(?(1)a|b))(x)yb", "", "xyb", 0, true, 0, 3, 0, 1);
        test("(?=(a))(?(1)a|b)", "", "a", 0, true, 0, 1, 0, 1);
        test("(?=a(x))(?(1)a|b)x", "", "ax", 0, true, 0, 2, 1, 2);
        test("(?=ax(y))(?(1)a|b)xy", "", "axy", 0, true, 0, 3, 2, 3);
        test("(?(1)a|b)(?<=(b))", "", "b", 0, true, 0, 1, 0, 1);
        test("x(?(1)a|b)(?<=(x)b)", "", "xb", 0, true, 0, 2, 0, 1);
        test("xy(?(1)a|b)(?<=(x)yb)", "", "xyb", 0, true, 0, 3, 0, 1);

        // Conditional back-reference and capture group within lookahead.
        test("(?=(a)(?(1)a|b))", "", "aa", 0, true, 0, 0, 0, 1);
        test("(?=(a)x(?(1)a|b))", "", "axa", 0, true, 0, 0, 0, 1);
        test("(?=(a)xy(?(1)a|b))", "", "axya", 0, true, 0, 0, 0, 1);
        test("(?=(?(1)a|b)())", "", "b", 0, true, 0, 0, 1, 1);
    }

    @Test
    public void gh3167() {
        // https://github.com/oracle/truffleruby/issues/3167
        String pattern = "\n" +
                        "        (?<open>\\s*\\(\\s*) {0}\n" +
                        "        (?<close>\\s*\\)\\s*) {0}\n" +
                        "        (?<name>\\s*\"(?<class_name>\\w+)\") {0}\n" +
                        "        (?<parent>\\s*(?:\n" +
                        "          (?<parent_name>[\\w\\*\\s\\(\\)\\.\\->]+) |\n" +
                        "          rb_path2class\\s*\\(\\s*\"(?<path>[\\w:]+)\"\\s*\\)\n" +
                        "        )) {0}\n" +
                        "        (?<under>\\w+) {0}\n" +
                        "\n" +
                        "        (?<var_name>[\\w\\.]+)\\s* =\n" +
                        "        \\s*rb_(?:\n" +
                        "          define_(?:\n" +
                        "            class(?: # rb_define_class(name, parent_name)\n" +
                        "              \\(\\s*\n" +
                        "                \\g<name>,\n" +
                        "                \\g<parent>\n" +
                        "              \\s*\\)\n" +
                        "            |\n" +
                        "              _under\\g<open> # rb_define_class_under(under, name, parent_name...)\n" +
                        "                \\g<under>,\n" +
                        "                \\g<name>,\n" +
                        "                \\g<parent>\n" +
                        "              \\g<close>\n" +
                        "            )\n" +
                        "          |\n" +
                        "            (?<module>)\n" +
                        "            module(?: # rb_define_module(name)\n" +
                        "              \\g<open>\n" +
                        "                \\g<name>\n" +
                        "              \\g<close>\n" +
                        "            |\n" +
                        "              _under\\g<open> # rb_define_module_under(under, name)\n" +
                        "                \\g<under>,\n" +
                        "                \\g<name>\n" +
                        "              \\g<close>\n" +
                        "            )\n" +
                        "          )\n" +
                        "      |\n" +
                        "        (?<attributes>(?:\\s*\"\\w+\",)*\\s*NULL\\s*) {0}\n" +
                        "        struct_define(?:\n" +
                        "          \\g<open> # rb_struct_define(name, ...)\n" +
                        "            \\g<name>,\n" +
                        "        |\n" +
                        "          _under\\g<open> # rb_struct_define_under(under, name, ...)\n" +
                        "            \\g<under>,\n" +
                        "            \\g<name>,\n" +
                        "        |\n" +
                        "          _without_accessor(?:\n" +
                        "            \\g<open> # rb_struct_define_without_accessor(name, parent_name, ...)\n" +
                        "          |\n" +
                        "            _under\\g<open> # rb_struct_define_without_accessor_under(under, name, parent_name, ...)\n" +
                        "              \\g<under>,\n" +
                        "          )\n" +
                        "            \\g<name>,\n" +
                        "            \\g<parent>,\n" +
                        "            \\s*\\w+,        # Allocation function\n" +
                        "        )\n" +
                        "          \\g<attributes>\n" +
                        "        \\g<close>\n" +
                        "      |\n" +
                        "        singleton_class\\g<open> # rb_singleton_class(target_class_name)\n" +
                        "          (?<target_class_name>\\w+)\n" +
                        "        \\g<close>\n" +
                        "        )\n" +
                        "      ";
        test(pattern, "mx", "abc", 0, false);
    }

    @Test
    public void gr52472() {
        test("(|a+?){0,4}b", "", "aaab", 0, true, 0, 4, 1, 3);
    }
}
