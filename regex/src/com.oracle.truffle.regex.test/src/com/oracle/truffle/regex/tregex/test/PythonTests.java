/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.regex.flavor.python.PyErrorMessages;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.test.generated.PythonGeneratedTests;

public class PythonTests extends RegexTestBase {

    public static final Map<String, String> ENGINE_OPTIONS = Map.of("regexDummyLang.Flavor", "Python");
    private static final Map<String, String> OPT_MUST_ADVANCE = Map.of("regexDummyLang.MustAdvance", "true");

    @Override
    Map<String, String> getEngineOptions() {
        return ENGINE_OPTIONS;
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_32;
    }

    @Test
    public void gr14950() {
        test("[\\^\\\\\\]]", "", "p", 0, false);
    }

    @Test
    public void gr15012() {
        test("(-*[A-]*)", "", "A", 0, true, 0, 1, 0, 1, 1);
    }

    @Test
    public void gr15243() {
        test("^(\\s*)([rRuUbB]{,2})(\"\"\"(?:.|\\n)*?\"\"\")", "", "R\"\"\"\"\"\"", 0, true, 0, 7, 0, 0, 0, 1, 1, 7, 3);
        test("A{,}", "", "AAAA", 0, true, 0, 4);
    }

    @Test
    public void gr23871() {
        test("[^ ]+?(?:-(?:(?<=[a-z]{2}-)|(?<=[a-z]-[a-z]-)))", "su", "this-is-a-useful-feature", 8, true, 8, 10);
    }

    @Test
    public void gr26246() {
        try {
            test(".*", "", "abc", 4, false);
        } catch (PolyglotException e) {
            Assert.assertTrue(e.getMessage().contains("illegal fromIndex"));
            return;
        }
        Assert.fail();
    }

    @Test
    public void gr28787() {
        expectSyntaxError("\\", "", PyErrorMessages.BAD_ESCAPE_END_OF_PATTERN);
    }

    @Test
    public void gr28905() {
        test("\\B", "", OPT_MATCHING_MODE_MATCH, "abc", 0, false);
        test("\\B", "", "", 0, false);
        test("\\B(b.)\\B", "", "abc bcd bc abxd", 0, true, 12, 14, 12, 14, 1);
        test("\\b(b.)\\b", "a", "abcd abc bcd bx", 0, true, 13, 15, 13, 15, 1);
    }

    @Test
    public void gr28906() {
        test("^(\\|)?([^()]+)\\1$", "", OPT_MATCHING_MODE_MATCH, "a|", 0, false);
        test("^(\\|)?([^()]+)\\1$", "", OPT_MATCHING_MODE_MATCH, "|a", 0, false);
    }

    @Test
    public void gr29318() {
        // \11 is a backreference to group 11
        expectSyntaxError("\\11", "", PyErrorMessages.invalidGroupReference("11"));
        test("()()()()()()()()()()()\\11", "", "", 0, true, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 11);
        // \011 is an octal escape
        test("\\011", "", "\011", 0, true, 0, 1);
        // \111 is an octal escape (\111 = I)
        test("\\111", "", "I", 0, true, 0, 1);
        // \111 is an octal escape and it is followed by a literal 1
        test("\\1111", "", "I1", 0, true, 0, 2);
        // \11 is a backreference to group 11 and it is followed by a literal 9
        expectSyntaxError("\\119", "", PyErrorMessages.invalidGroupReference("11"));
        test("()()()()()()()()()()()\\119", "", "9", 0, true, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 11);
    }

    @Test
    public void gr29331() {
        test("(?a)x", "", "x", 0, true, 0, 1);
    }

    @Test
    public void backreferencesToUnmatchedGroupsFail() {
        test("(a)?\\1", "", "", 0, false);
    }

    @Test
    public void nestedCaptureGroupsKeptOnLoopReentry() {
        test("(?:(a)|(b))+", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 2);
        test("(?:(a)|b\\1){2}", "", "aba", 0, true, 0, 3, 0, 1, 1);
    }

    @Test
    public void failingEmptyChecksDontBacktrack() {
        test("(?:|a)?", "", "a", 0, true, 0, 0);
        test("(?:a|())*", "", "a", 0, true, 0, 1, 1, 1, 1);
    }

    @Test
    public void emptyChecksBacktrackingAndNestedCaptureGroupInteractions() {
        test("()??\\1", "", "", 0, true, 0, 0, 0, 0, 1);
        test("(?:a|())*?\\1", "", "a", 0, true, 0, 1, 1, 1, 1);
    }

    @Test
    public void quantifiersOnLookaroundAssertions() {
        test("(?=(a))?", "", "a", 0, true, 0, 0, 0, 1, 1);
        test("(?=(a))??", "", "a", 0, true, 0, 0, -1, -1);
        test("(?=(a))??\\1", "", "a", 0, true, 0, 1, 0, 1, 1);

        test("a(?<=(a))?", "", "aa", 0, true, 0, 1, 0, 1, 1);
        test("a(?<=(a))??", "", "aa", 0, true, 0, 1, -1, -1);
        test("a(?<=(a))??\\1", "", "aa", 0, true, 0, 2, 0, 1, 1);
    }

    @Test
    public void gr32018() {
        test("\\s*(?:#\\s*)?$", "", OPT_MATCHING_MODE_MATCH, new String(new char[1000000]).replace('\0', '\t') + "##", 0, false);
    }

    @Test
    public void gr32537() {
        // Tests from the original issue. These test the literal engine.
        test("(a)(b)", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 2);
        test("(a(b))", "", "ab", 0, true, 0, 2, 0, 2, 1, 2, 1);
        test("(a)()", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 2);
        test("(a())", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 1);

        // Modified tests that use the NFA, TraceFinder, lazy DFA, eager DFA, simpleCG DFA
        // and backtracking engines in regression test mode.

        // Single possible CG result: exercises NFA, DFA and backtracker.
        // NB: Using [X0-9] instead of X precludes the use of the specializations in
        // LiteralRegexEngine.
        test("([a0-9])([b0-9])", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 2);
        test("([a0-9]([b0-9]))", "", "ab", 0, true, 0, 2, 0, 2, 1, 2, 1);
        test("([a0-9])()", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 2);
        test("([a0-9]())", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 1);

        // TraceFinder: exercises NFA, DFA, and backtracker.
        // NB: Introducing x? leads to multiple different lengths of capture group 0 matches. This
        // means we end up having to use TraceFinder to track down the correct capture groups.
        // x? is
        test("x?([a0-9])([b0-9])", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 2);
        test("x?([a0-9]([b0-9]))", "", "ab", 0, true, 0, 2, 0, 2, 1, 2, 1);
        test("x?([a0-9])()", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 2);
        test("x?([a0-9]())", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 1);

        // Unbounded length of match, unambiguous: exercises NFA, lazy DFA, simpleCG DFA and
        // backtracker.
        // NB: x* as a prefix leads to unambiguous states in the NFA (allowing the use of the
        // simpleCG DFA) and it can also lead to an unbounded length of the match, which precludes
        // the use of TraceFinder.
        test("x*([a0-9])([b0-9])", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 2);
        test("x*([a0-9]([b0-9]))", "", "ab", 0, true, 0, 2, 0, 2, 1, 2, 1);
        test("x*([a0-9])()", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 2);
        test("x*([a0-9]())", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 1);

        // Unbounded length of match, ambiguous: exercises NFA, lazy DFA, eager DFA and backtracker.
        test(".*([a0-9])([b0-9])", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 2);
        test(".*([a0-9]([b0-9]))", "", "ab", 0, true, 0, 2, 0, 2, 1, 2, 1);
        test(".*([a0-9])()", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 2);
        test(".*([a0-9]())", "", "ab", 0, true, 0, 1, 0, 1, 1, 1, 1);
    }

    @Test
    public void testLastGroupInLookbehind() {
        // Here we test whether we get the correct order of lastGroup updates inside lookbehinds.
        test("(?<=(a)(b))", "", "ab", 0, true, 2, 2, 0, 1, 1, 2, 2);
        test("(?<=(a(b)))", "", "ab", 0, true, 2, 2, 0, 2, 1, 2, 1);
        test("(?<=(a)())", "", "ab", 0, true, 1, 1, 0, 1, 1, 1, 2);
        test("(?<=(a()))", "", "ab", 0, true, 1, 1, 0, 1, 1, 1, 1);
    }

    @Test
    public void testLastGroupInLookaround() {
        // Here we test the interaction of lastGroup updates across lookaround assertions.
        test("(?=(a)b)(a)b", "", "ab", 0, true, 0, 2, 0, 1, 0, 1, 2);
        test("(?=(a)b)a(b)", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 2);
        test("(?=a(b))(a)b", "", "ab", 0, true, 0, 2, 1, 2, 0, 1, 2);
        test("(?=a(b))a(b)", "", "ab", 0, true, 0, 2, 1, 2, 1, 2, 2);

        test("(a)b(?<=(a)b)", "", "ab", 0, true, 0, 2, 0, 1, 0, 1, 2);
        test("(a)b(?<=a(b))", "", "ab", 0, true, 0, 2, 0, 1, 1, 2, 2);
        test("a(b)(?<=(a)b)", "", "ab", 0, true, 0, 2, 1, 2, 0, 1, 2);
        test("a(b)(?<=a(b))", "", "ab", 0, true, 0, 2, 1, 2, 1, 2, 2);
    }

    @Test
    public void gr28565() {
        test("\\b|:", "", "a:", 0, true, 0, 0, -1);
        test("\\b|:", "", OPT_MUST_ADVANCE, "a:", 0, true, 1, 1, -1);
        test("\\b|:", "", OPT_MUST_ADVANCE, "a:", 1, true, 1, 2, -1);
        test("\\b|:", "", "a:", 2, false);
    }

    @Test
    public void gr28565SimplerAsciiTests() {
        test("(?=a)|(?<=a)|:", "", "a:", 0, true, 0, 0, -1);
        test("(?=a)|(?<=a)|:", "", OPT_MUST_ADVANCE, "a:", 0, true, 1, 1, -1);
        test("(?=a)|(?<=a)|:", "", OPT_MUST_ADVANCE, "a:", 1, true, 1, 2, -1);
        test("(?=a)|(?<=a)|:", "", "a:", 2, false);
    }

    @Test
    public void mustAdvanceLiteralEngineTests() {
        test("", "", OPT_MUST_ADVANCE, "", 0, false);
        test("", "", OPT_MUST_ADVANCE, "a", 0, true, 1, 1, -1);
        test("\\A", "", OPT_MUST_ADVANCE, "", 0, false);
        test("\\Z", "", OPT_MUST_ADVANCE, "", 0, false);
        test("\\A\\Z", "", OPT_MUST_ADVANCE, "", 0, false);
    }

    @Test
    public void cpythonTestBug817234() {
        test(".*", "", "asdf", 0, true, 0, 4, -1);
        test(".*", "", "asdf", 4, true, 4, 4, -1);
        test(".*", "", OPT_MUST_ADVANCE, "asdf", 4, false);
    }

    @Test
    public void cpythonTestDollarMatchesTwice() {
        test("$", "", "a\nb\n", 0, true, 3, 3, -1);
        test("$", "", OPT_MUST_ADVANCE, "a\nb\n", 3, true, 4, 4, -1);
        test("$", "", OPT_MUST_ADVANCE, "a\nb\n", 4, false);

        test("$", "m", "a\nb\n", 0, true, 1, 1, -1);
        test("$", "m", OPT_MUST_ADVANCE, "a\nb\n", 1, true, 3, 3, -1);
        test("$", "m", OPT_MUST_ADVANCE, "a\nb\n", 3, true, 4, 4, -1);
        test("$", "m", OPT_MUST_ADVANCE, "a\nb\n", 4, false);
    }

    @Test
    public void testFullMatch() {
        test("a|ab", "", OPT_MATCHING_MODE_FULLMATCH, "ab", 0, true, 0, 2, -1);
    }

    @Test
    public void testBrokenSurrogate() {
        test("(.*?)([\"\\\\\\x00-\\x1f])", "msx", OPT_MATCHING_MODE_MATCH, "\"z\ud834x\"", 1, true, 1, 5, 1, 4, 4, 5, 2);
    }

    @Test
    public void testBStar() {
        test("b*", "", OPT_MUST_ADVANCE, "xyz", 0, true, 1, 1);
    }

    @Test
    public void nfaTraversalTests() {
        // This relies on correctly maneuvering through the necessary capture groups in the
        // NFATraversalRegexASTVisitor. Unlike Ruby, for Python regexps, capture group updates are
        // not reflected in quantifier guards. In order for the traversal to find the needed path,
        // the group boundaries have to be checked when pruning.
        test("(?:|())(?:|())(?:|())(?:|())(?:|())(?:|())(?:|())(?:|())\\3\\5\\7", "", "", 0, true, 0, 0, -1, -1, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1, 7);
    }

    @Test
    public void gr41215() {
        test("(?<= )b|ab", "", OPT_MATCHING_MODE_MATCH, " b", 1, true, 1, 2);
        test("(?<= )b|abc", "", OPT_MATCHING_MODE_MATCH, " b", 1, true, 1, 2);
        test("(?<!\\.)b|ab", "", OPT_MATCHING_MODE_MATCH, " b", 1, true, 1, 2);
        test("(?=a)|(?<=a)|:", "", OPT_MATCHING_MODE_MATCH, "a:", 1, true, 1, 1);
    }

    @Test
    public void testQuantifierOverflow() {
        long max = Integer.MAX_VALUE;
        test(String.format("x{%d,%d}", max, max + 1), "", "x", 0, false);
        test(String.format("x{%d,}", max), "", "x", 0, false);
        test(String.format("x{%d,}", max + 1), "", "x", 0, false);
        expectSyntaxError(String.format("x{%d,%d}", max + 1, max), "", PyErrorMessages.MIN_REPEAT_GREATER_THAN_MAX_REPEAT);
    }

    @Test
    public void test3DigitOctalEscape() {
        test("()\\1000", "", "@0", 0, true, 0, 2, 0, 0, 1);
    }

    @Test
    public void testForwardReference() {
        expectSyntaxError("\\1()", "", PyErrorMessages.invalidGroupReference("1"));
    }

    @Test
    public void testCCFirstBracket() {
        test("[]-^]", "", "^", 0, true, 0, 1, -1);
    }

    @Test
    public void testInlineGlobalFlags() {
        test("(?i)b", "", "B", 0, true, 0, 1, -1);
    }

    @Test
    public void testInlineGlobalFlagsInComments() {
        // Inline flags that are commented out should be ignored...
        // ...when the verbose flag is passed to re.compile,
        test("#(?i)\nfoo", "x", "foo", 0, true, 0, 3, -1);
        test("#(?i)\nfoo", "x", "FOO", 0, false);
        // ...when the verbose flag is set inline,
        test("(?x)#(?i)\nfoo", "", "foo", 0, true, 0, 3, -1);
        test("(?x)#(?i)\nfoo", "", "FOO", 0, false);
        // and when the verbose flag is set in a local group.
        test("(?x:#(?i)\n)foo", "", "foo", 0, true, 0, 3, -1);
        test("(?x:#(?i)\n)foo", "", "FOO", 0, false);

        test("(?##)(?i)(?#\n)foo", "x", "foo", 0, true, 0, 3, -1);
        test("(?##)(?i)(?#\n)foo", "x", "FOO", 0, true, 0, 3, -1);

        test("(?#[)(?i)(?#])foo", "", "foo", 0, true, 0, 3, -1);
        test("(?#[)(?i)(?#])foo", "", "FOO", 0, true, 0, 3, -1);

        // NB: The verbose flag can no longer be set inline in the middle of a regexp.
        expectSyntaxError("#(?i)\n(?x)foo", "", "global flags not at the start of the expression", 1);
        expectSyntaxError("#(?x)(?i)\nfoo", "", "global flags not at the start of the expression", 1);
        expectSyntaxError("(?x:(?-x:#(?i)\n))foo", "", "global flags not at the start of the expression", 10);
        expectSyntaxError("(?-x:#(?i)\n)foo", "x", "global flags not at the start of the expression", 6);
        expectSyntaxError("(?x)(?-x:#(?i)\n)foo", "", "global flags not at the start of the expression", 10);
    }

    @Test
    public void testInlineGlobalFlagsEscaped() {
        // NB: The verbose flag can no longer be set inline in the middle of a regexp.
        expectSyntaxError("\\\\(?i)foo", "", "global flags not at the start of the expression", 2);
    }

    @Test
    public void testPythonFlagChecks() {
        expectSyntaxError("", "au", "ASCII and UNICODE flags are incompatible");
        expectSyntaxError("(?a)", "u", "ASCII and UNICODE flags are incompatible");
        expectSyntaxError("(?u)", "a", "ASCII and UNICODE flags are incompatible");
        expectSyntaxError("(?a)(?u)", "", "ASCII and UNICODE flags are incompatible");

        expectSyntaxError("", "L", "cannot use LOCALE flag with a str pattern");
        expectSyntaxError("", "u", Encodings.LATIN_1, "cannot use UNICODE flag with a bytes pattern", Integer.MIN_VALUE);

        Assert.assertTrue("expected str pattern to default to UNICODE flag",
                        compileRegex("", "").getMember("flags").getMember("UNICODE").asBoolean());
    }

    @Test
    public void testIncompleteQuantifiers() {
        test("{", "", "{", 0, true, 0, 1, -1);
        test("{1", "", "{1", 0, true, 0, 2, -1);
        test("{,", "", "{,", 0, true, 0, 2, -1);
        test("{1,", "", "{1,", 0, true, 0, 3, -1);
    }

    @Test
    public void testConditionalBackReferences() {
        test("(foo)(?(1)bar|baz)", "", "foobar", 0, true, 0, 6, 0, 3, 1);
        test("(foo)(?(1)bar|baz)", "", "foobaz", 0, false);

        test("(foo)?(?(1)bar|baz)", "", "foobar", 0, true, 0, 6, 0, 3, 1);
        test("(foo)?(?(1)bar|baz)", "", "foobaz", 0, true, 3, 6, -1, -1, -1);
        test("(foo)?(?(1)bar|baz)", "", "foxbar", 0, false);
        test("(foo)?(?(1)bar|baz)", "", "foxbaz", 0, true, 3, 6, -1, -1, -1);

        // GR-42252
        test("(?P<quote>)(?(quote))", "", "", 0, true, 0, 0, 0, 0, 1);
        // GR-42254
        test("(?P<a>x)(?P=a)(?(a)y)", "", "xxy", 0, true, 0, 3, 0, 1, 1);
        test("(?P<a1>x)(?P=a1)(?(a1)y)", "", "xxy", 0, true, 0, 3, 0, 1, 1);
        test("(?P<a1>x)\\1(?(1)y)", "", "xxy", 0, true, 0, 3, 0, 1, 1);
        // GR-42255
        test("(?:(a)|(x))b(?<=(?(2)x|c))c", "", "abc", 0, false);
        // GR-42256
        test("(a)b(?<=(?(1)c|x))(c)", "", "abc", 0, false);

        // test_groupref_exists
        test("^(\\()?([^()]+)(?(1)\\))$", "", "a", 0, true, 0, 1, -1, -1, 0, 1, 2);
        // test_lookahead
        test("(?:(a)|(x))b(?=(?(1)c|x))c", "", "abc", 0, true, 0, 3, 0, 1, -1, -1, 1);
        test("(?:(a)|(x))b(?=(?(2)x|c))c", "", "abc", 0, true, 0, 3, 0, 1, -1, -1, 1);
        test("(a)b(?=(?(2)x|c))(c)", "", "abc", 0, true, 0, 3, 0, 1, 2, 3, 2);
        // test_lookbehind
        test("(?:(a)|(x))b(?<=(?(2)x|b))c", "", "abc", 0, true, 0, 3, 0, 1, -1, -1, 1);

        // Test that we respect the order of capture group updates and condition checks
        test("(?(1)()a|b)", "", "a", 0, false);
        test("(?(1)()a|b)", "", "b", 0, true, 0, 1, -1, -1, -1);
    }

    @Test
    public void testConditionalBackReferencesWithLookArounds() {
        /// Test temporal ordering of lookaround assertions and conditional back-references in DFAs.
        test("(?=(?(1)a|b))(b)", "", "b", 0, true, 0, 1, 0, 1, 1);
        test("(?=x(?(1)a|b))(x)b", "", "xb", 0, true, 0, 2, 0, 1, 1);
        test("(?=xy(?(1)a|b))(x)yb", "", "xyb", 0, true, 0, 3, 0, 1, 1);

        // All following currently tests use back-tracking because the presence of capture groups in
        // lookarounds in Python force the use of backtracking due to the calculation of lastGroup.
        test("(?=(a))(?(1)a|b)", "", "a", 0, true, 0, 1, 0, 1, 1);
        test("(?=a(x))(?(1)a|b)x", "", "ax", 0, true, 0, 2, 1, 2, 1);
        test("(?=ax(y))(?(1)a|b)xy", "", "axy", 0, true, 0, 3, 2, 3, 1);
        test("(?(1)a|b)(?<=(b))", "", "b", 0, true, 0, 1, 0, 1, 1);
        test("x(?(1)a|b)(?<=(x)b)", "", "xb", 0, true, 0, 2, 0, 1, 1);
        test("xy(?(1)a|b)(?<=(x)yb)", "", "xyb", 0, true, 0, 3, 0, 1, 1);

        // Conditional back-reference and capture group within lookahead.
        test("(?=(a)(?(1)a|b))", "", "aa", 0, true, 0, 0, 0, 1, 1);
        test("(?=(a)x(?(1)a|b))", "", "axa", 0, true, 0, 0, 0, 1, 1);
        test("(?=(a)xy(?(1)a|b))", "", "axya", 0, true, 0, 0, 0, 1, 1);
        test("(?=(?(1)a|b)())", "", "b", 0, true, 0, 0, 1, 1, 1);
    }

    @Test
    public void gr44233() {
        test("(\\bNone|\\bFalse|\\bTrue)?\\s*([=!]=)\\s*(?(1)|(None|False|True))\\b", "", "x == True", 0, true, 1, 9, -1, -1, 2, 4, 5, 9, 3);

        test("(?:(a)|(b))(?(1)(?<=a)|(?<=b))", "", "a", 0, true, 0, 1, 0, 1, -1, -1, 1);
        test("(?:(a)|(b))(?(1)(?<=a)|(?<=b))", "", "b", 0, true, 0, 1, -1, -1, 0, 1, 2);
        test("(?:(a)|(b))(?(1)(?<=b)|(?<=a))", "", "a", 0, false);
        test("(?:(a)|(b))(?(1)(?<=b)|(?<=a))", "", "b", 0, false);

        test("(x)?(?(1)a|b)(?<=a)", "", "xa", 0, true, 0, 2, 0, 1, 1);
        test("(x)?(?(1)a|b)(?<=a)", "", "b", 0, false);
        test("(x)?(?(1)a|b)(?<=b)", "", "xa", 0, false);
        test("(x)?(?(1)a|b)(?<=b)", "", "b", 0, true, 0, 1, -1, -1, -1);
    }

    @Test
    public void testIgnoreCase() {
        // \u00b5 (micro sign) and \u03bc (greek small letter mu) are considered equivalent when
        // either of them appears in the pattern and the other in the text.
        test("(\u00b5)\u00b5", "i", "\u00b5\u03bc", 0, true, 0, 2, 0, 1, 1);
        test("(\u03bc)\u00b5", "i", "\u00b5\u03bc", 0, true, 0, 2, 0, 1, 1);
        // However, these characters are not considered equal when matched using a backreference.
        test("(\u00b5)\\1", "i", "\u00b5\u03bc", 0, false);
        test("(\u03bc)\\1", "i", "\u00b5\u03bc", 0, false);
        // This is because these two characters receive special care when compiling a regular
        // expression. They are considered equivalent because they map to the same Uppercase
        // character. However, when two strings are compared at runtime during the execution of a
        // backreference, the characters are only tested by comparing their Lowercase mappings.

        // We used to mistakenly consider a character equivalent to the first character of its
        // extended case mapping. The ligature \ufb00 uppercases to FF and and the ligature \ufb01
        // uppercases to FI. Both should be distinct from each other and from the letter F.
        test("\ufb00", "i", "\ufb01", 0, false);
        test("\ufb00", "i", "F", 0, false);
    }

    @Test
    public void testLazyLastGroup() {
        Value compiledRegex = compileRegex(".*(.*bbba|ab)", "");
        for (int i = 0; i < TRegexOptions.TRegexGenerateDFAThresholdCalls * 4; i++) {
            Value result = execRegex(compiledRegex, getTRegexEncoding(), "xxxxxxabxx", 0);
            Assert.assertEquals(1, result.getMember("lastGroup").asInt());
        }
    }

    @Test
    public void testForceLinearExecution() {
        test("(a*)b\\1", "", "_aabaaa_", 0, true, 1, 6, 1, 3, 1);
        expectUnsupported("(a*)b\\1", "", OPT_FORCE_LINEAR_EXECUTION);
        test(".*a{1,200000}.*", "", "_aabaaa_", 0, true, 0, 8, -1);
        expectUnsupported(".*a{1,200000}.*", "", OPT_FORCE_LINEAR_EXECUTION);
        test(".*b(?!a_)", "", "_aabaaa_", 0, true, 0, 4, -1);
        expectUnsupported(".*b(?!a_)", "", OPT_FORCE_LINEAR_EXECUTION);
    }

    @Test
    public void generatedTests() {
        runGeneratedTests(PythonGeneratedTests.TESTS);
    }
}
