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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.strings.TranscodingErrorHandler;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.regex.test.dummylang.TRegexTestDummyLanguage;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.test.generated.TestCase;

public abstract class RegexTestBase {

    static final Map<String, String> OPT_MATCHING_MODE_MATCH = Map.of("regexDummyLang.MatchingMode", "match");
    static final Map<String, String> OPT_MATCHING_MODE_FULLMATCH = Map.of("regexDummyLang.MatchingMode", "fullmatch");
    static final Map<String, String> OPT_MATCHING_MODE_SEARCH = Map.of("regexDummyLang.MatchingMode", "search");
    static final Map<String, String> OPT_FORCE_LINEAR_EXECUTION = Map.of("regexDummyLang.ForceLinearExecution", "true");

    private static final boolean ASSERTS = true;
    private static final boolean TEST_REGION_FROM_TO = true;
    private static final boolean TABLE_OMIT_FROM_INDEX = false;

    static Context context;
    private static boolean printTableHeader = true;

    @BeforeClass
    public static void setUp() {
        context = createContext();
        context.enter();
        context.initialize(TRegexTestDummyLanguage.ID);
    }

    @AfterClass
    public static void tearDown() {
        if (context != null) {
            context.leave();
            context.close();
            context = null;
        }
    }

    static Context createContext() {
        return Context.newBuilder().option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build();
    }

    abstract Map<String, String> getEngineOptions();

    abstract Encodings.Encoding getTRegexEncoding();

    Map<String, String> options() {
        return options(Collections.emptyMap());
    }

    Map<String, String> options(Map<String, String> additionalOptions) {
        Map<String, String> mergedOptions = new HashMap<>();
        mergedOptions.putAll(getEngineOptions());
        mergedOptions.putAll(additionalOptions);
        mergedOptions.put("regexDummyLang.RegressionTestMode", "true");
        return mergedOptions;
    }

    static Source.Builder sourceBuilder(String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding) {
        return Source.newBuilder("regexDummyLang", '/' + pattern + '/' + flags, "test").options(options).option("regexDummyLang.Encoding",
                        encoding == Encodings.BYTES ? "BYTES" : encoding.getName());
    }

    Value compileRegex(String pattern, String flags) {
        return compileRegex(context, pattern, flags, options(), getTRegexEncoding());
    }

    static Value compileRegex(Context ctx, String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding) {
        return compileRegex(ctx, sourceBuilder(pattern, flags, options, encoding));
    }

    static Value compileRegexBoolean(Context ctx, String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding) {
        return compileRegex(ctx, sourceBuilder(pattern, flags, options, encoding).option("regexDummyLang.BooleanMatch", "true"));
    }

    static Value compileRegex(Context ctx, Source.Builder source) {
        try {
            return ctx.eval(source.build());
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    static Value execRegex(Value compiledRegex, Encodings.Encoding encoding, String input, int fromIndex) {
        TruffleString converted = toTruffleString(input, encoding);
        int length = converted.byteLength(encoding.getTStringEncoding()) >> encoding.getStride();
        return compiledRegex.invokeMember("exec", converted, fromIndex, length, 0, length);
    }

    static Value execRegexBoolean(Value compiledRegex, Encodings.Encoding encoding, String input, int fromIndex) {
        TruffleString converted = toTruffleString(input, encoding);
        int length = converted.byteLength(encoding.getTStringEncoding()) >> encoding.getStride();
        return compiledRegex.invokeMember("execBoolean", converted, fromIndex, length, 0, length);
    }

    private static TruffleString toTruffleString(String input, Encodings.Encoding encoding) {
        TruffleString tStringUTF16 = TruffleString.fromJavaStringUncached(input, TruffleString.Encoding.UTF_16);
        return tStringUTF16.switchEncodingUncached(encoding.getTStringEncoding(), TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
    }

    void testBoolean(String pattern, String flags, Map<String, String> options, String input, int fromIndex, boolean isMatch) {
        testBoolean(context, pattern, flags, options(options), getTRegexEncoding(), input, fromIndex, isMatch);
    }

    static void testBoolean(Context ctx, String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch) {
        String expectedResult = isMatch ? "Match" : "NoMatch";
        try {
            testBoolean(compileRegexBoolean(ctx, pattern, flags, options, encoding), pattern, flags, options, encoding, input, fromIndex, isMatch);
        } catch (PolyglotException e) {
            if (!ASSERTS && e.isSyntaxError()) {
                printTable(pattern, flags, encoding, input, fromIndex, expectedResult, syntaxErrorToString(e.getMessage()));
            } else {
                throw e;
            }
        }
    }

    static void testBoolean(Value compiledRegex, String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch) {
        String expectedResult = isMatch ? "Match" : "NoMatch";
        Value result = execRegexBoolean(compiledRegex, encoding, input, fromIndex);
        if (result.asBoolean() != isMatch) {
            String actualResult = result.asBoolean() ? "Match" : "NoMatch";
            printTable(pattern, flags, encoding, input, fromIndex, expectedResult, actualResult);
            if (ASSERTS) {
                Assert.fail(options + regexSlashes(pattern, flags) + ' ' + quote(input) + " expected: " + expectedResult + ", actual: " + actualResult);
            }
        }
    }

    void test(String pattern, String flags, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        test(context, pattern, flags, options(), getTRegexEncoding(), input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
    }

    void test(String pattern, String flags, Map<String, String> options, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        test(context, pattern, flags, options(options), getTRegexEncoding(), input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
    }

    void test(String pattern, String flags, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        test(context, pattern, flags, options(), encoding, input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
    }

    static void test(Context ctx, String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch,
                    int... captureGroupBoundsAndLastGroup) {
        try {
            Value compiledRegex = compileRegex(ctx, pattern, flags, options, encoding);
            test(compiledRegex, pattern, flags, options, encoding, input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
        } catch (PolyglotException e) {
            if (!ASSERTS && e.isSyntaxError()) {
                printTable(pattern, flags, encoding, input, fromIndex, expectedResultToString(captureGroupBoundsAndLastGroup), syntaxErrorToString(e.getMessage()));
            } else {
                throw e;
            }
        }
        testBoolean(ctx, pattern, flags, options, encoding, input, fromIndex, isMatch);
    }

    static void test(Value compiledRegex, String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch,
                    int... captureGroupBoundsAndLastGroup) {
        Value result = execRegex(compiledRegex, encoding, input, fromIndex);
        int groupCount = compiledRegex.getMember("groupCount").asInt();
        validateResult(pattern, flags, options, encoding, input, fromIndex, result, groupCount, isMatch, captureGroupBoundsAndLastGroup);

        if (TEST_REGION_FROM_TO) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(encoding.getTStringEncoding());
            sb.appendCodePointUncached('_');
            sb.appendStringUncached(toTruffleString(input, encoding));
            sb.appendCodePointUncached('_');
            TruffleString padded = sb.toStringUncached();
            int length = padded.byteLength(encoding.getTStringEncoding()) >> encoding.getStride();
            int[] boundsAdjusted = new int[captureGroupBoundsAndLastGroup.length];
            for (int i = 0; i < (boundsAdjusted.length & ~1); i++) {
                int v = captureGroupBoundsAndLastGroup[i];
                boundsAdjusted[i] = v < 0 ? v : v + 1;
            }
            if ((boundsAdjusted.length & 1) == 1) {
                boundsAdjusted[boundsAdjusted.length - 1] = captureGroupBoundsAndLastGroup[boundsAdjusted.length - 1];
            }
            Value resultSubstring = compiledRegex.invokeMember("exec", padded, fromIndex + 1, length - 1, 1, length - 1);
            validateResult(pattern, flags, options, encoding, input, fromIndex + 1, resultSubstring, groupCount, isMatch, boundsAdjusted);
        }
    }

    private static void validateResult(String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding, String input, int fromIndex, Value result, int groupCount,
                    boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        if (isMatch != result.getMember("isMatch").asBoolean()) {
            fail(pattern, flags, options, encoding, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
            return;
        }
        if (isMatch) {
            if (ASSERTS) {
                assertEquals(captureGroupBoundsAndLastGroup.length / 2, groupCount);
            }
            if (captureGroupBoundsAndLastGroup.length / 2 != groupCount) {
                fail(pattern, flags, options, encoding, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
                return;
            }
            for (int i = 0; i < groupCount; i++) {
                if (captureGroupBoundsAndLastGroup[Group.groupNumberToBoundaryIndexStart(i)] != result.invokeMember("getStart", i).asInt() ||
                                captureGroupBoundsAndLastGroup[Group.groupNumberToBoundaryIndexEnd(i)] != result.invokeMember("getEnd", i).asInt()) {
                    fail(pattern, flags, options, encoding, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
                    return;
                }
            }
        } else if (result.getMember("isMatch").asBoolean()) {
            fail(pattern, flags, options, encoding, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
            return;
        }
        int lastGroup = captureGroupBoundsAndLastGroup.length % 2 == 1 ? captureGroupBoundsAndLastGroup[captureGroupBoundsAndLastGroup.length - 1] : -1;
        if (lastGroup != result.getMember("lastGroup").asInt()) {
            fail(pattern, flags, options, encoding, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
            return;
        }
        // print(pattern, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
    }

    void runGeneratedTests(TestCase[] generatedTests) {
        runGeneratedTests(context, generatedTests, options());
    }

    static void runGeneratedTests(Context ctx, TestCase[] generatedTests, Map<String, String> options) {
        for (TestCase tc : generatedTests) {
            runGeneratedTest(ctx, options, tc);
        }
    }

    public static void runGeneratedTest(Context ctx, Map<String, String> options, TestCase tc) {
        String pattern = tc.pattern();
        String flags = tc.flags();
        Encodings.Encoding encoding = tc.encoding();
        if (tc.syntaxErrorOrInputs() instanceof TestCase.SyntaxError syntaxError) {
            if (syntaxError instanceof TestCase.SyntaxErrorCode errorCode) {
                expectSyntaxError(ctx, pattern, flags, options, encoding, errorCode.errorCode.name(), Integer.MIN_VALUE);
            } else if (syntaxError instanceof TestCase.SyntaxErrorMessage errorMessage) {
                expectSyntaxError(ctx, pattern, flags, options, encoding, errorMessage.message, errorMessage.position < 0 ? Integer.MIN_VALUE : errorMessage.position);
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        } else if (tc.syntaxErrorOrInputs() instanceof TestCase.Inputs inputs) {
            assert inputs.inputs.length > 0;
            try {
                Value compiledRegex = compileRegex(ctx, pattern, flags, options, encoding);
                Value compiledRegexBoolean = compileRegexBoolean(ctx, pattern, flags, options, encoding);
                for (TestCase.Input input : inputs.inputs) {
                    if (input.captureGroupBoundsAndLastGroup() == null) {
                        test(compiledRegex, pattern, flags, options, encoding, input.input(), input.fromIndex(), false);
                        testBoolean(compiledRegexBoolean, pattern, flags, options, encoding, input.input(), input.fromIndex(), false);
                    } else {
                        test(compiledRegex, pattern, flags, options, encoding, input.input(), input.fromIndex(), true, input.captureGroupBoundsAndLastGroup());
                        testBoolean(compiledRegexBoolean, pattern, flags, options, encoding, input.input(), input.fromIndex(), true);
                    }
                }
            } catch (PolyglotException e) {
                if (!ASSERTS && e.isSyntaxError()) {
                    printTable(pattern, flags, encoding, "", 0, "", syntaxErrorToString(e.getMessage()));
                } else {
                    throw e;
                }
            }
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    void expectUnsupported(String pattern) {
        expectUnsupported(pattern, "", Collections.emptyMap());
    }

    void expectUnsupported(String pattern, String flags) {
        expectUnsupported(pattern, flags, Collections.emptyMap());
    }

    void expectUnsupported(String pattern, String flags, Map<String, String> options) {
        Assert.assertTrue(compileRegex(context, pattern, flags, options(options), getTRegexEncoding()).isNull());
    }

    void expectSyntaxError(String pattern, String flags, String expectedMessage) {
        expectSyntaxError(context, pattern, flags, options(), getTRegexEncoding(), expectedMessage, Integer.MIN_VALUE);
    }

    void expectSyntaxError(String pattern, String flags, String expectedMessage, int expectedPosition) {
        expectSyntaxError(context, pattern, flags, options(), getTRegexEncoding(), expectedMessage, expectedPosition);
    }

    void expectSyntaxError(String pattern, String flags, Encodings.Encoding encoding, String expectedMessage, int expectedPosition) {
        expectSyntaxError(context, pattern, flags, options(), encoding, expectedMessage, expectedPosition);
    }

    static void expectSyntaxError(Context ctx, String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding, String expectedMessage, int expectedPosition) {
        try {
            compileRegex(ctx, pattern, flags, options, encoding);
        } catch (PolyglotException e) {
            String msg = e.getMessage();
            int pos = e.getSourceLocation().getCharIndex();
            if (!msg.contains(expectedMessage)) {
                printTable(pattern, flags, encoding, "", 0, syntaxErrorToString(expectedMessage), syntaxErrorToString(msg));
                if (ASSERTS) {
                    Assert.fail(String.format("/%s/%s : expected syntax error message containing \"%s\", but was \"%s\"", pattern, flags, expectedMessage, msg));
                }
            }
            if (expectedPosition != Integer.MIN_VALUE && pos != expectedPosition) {
                Assert.fail(String.format("/%s/%s : expected syntax error message \"%s\" position here:\n%s\n%s\nbut was here:\n%s\n%s", pattern, flags, expectedMessage, pattern,
                                generateErrorPosArrow(expectedPosition), pattern, generateErrorPosArrow(pos)));
            }
            return;
        }
        printTable(pattern, flags, encoding, "", 0, syntaxErrorToString(expectedMessage), "");
        if (ASSERTS) {
            Assert.fail(String.format("/%s/%s : expected \"%s\", but no exception was thrown", pattern, flags, expectedMessage));
        }
    }

    private static String syntaxErrorToString(String msg) {
        if (msg.endsWith(" in regular expression")) {
            return "SyntaxError(" + msg.substring(0, msg.length() - " in regular expression".length()) + ")";
        }
        return "SyntaxError(" + msg + ")";
    }

    private static String generateErrorPosArrow(int pos) {
        StringBuilder sb = new StringBuilder(pos + 1);
        for (int i = 0; i < pos; i++) {
            sb.append(' ');
        }
        return sb.append('^').toString();
    }

    private static void fail(String pattern, String flags, Map<String, String> options, Encodings.Encoding encoding, String input, int fromIndex, Value result, int groupCount,
                    int... captureGroupBoundsAndLastGroup) {
        String expectedResult = expectedResultToString(captureGroupBoundsAndLastGroup);
        String actualResult = actualResultToString(result, groupCount, captureGroupBoundsAndLastGroup.length % 2 == 1);
        printTable(pattern, flags, encoding, input, fromIndex, expectedResult, actualResult);
        if (ASSERTS) {
            Assert.fail(options + regexSlashes(pattern, flags) + ' ' + quote(input) + " expected: " + expectedResult + ", actual: " + actualResult);
        }
    }

    private static void print(String pattern, String flags, Encodings.Encoding encoding, String input, int fromIndex, Value result, int groupCount, int... captureGroupBoundsAndLastGroup) {
        String actualResult = actualResultToString(result, groupCount, captureGroupBoundsAndLastGroup.length % 2 == 1);
        printTable(pattern, flags, encoding, input, fromIndex, actualResult, "");
    }

    private static void printTable(String pattern, String flags, Encodings.Encoding encoding, String input, int fromIndex, String expectedResult, String actualResult) {
        if (TABLE_OMIT_FROM_INDEX) {
            String format = "%-20s%-12s%-20s%-30s%s%n";
            printTableHeader(format, "Pattern", "Encoding", "Input", "Expected result", "TRegex result");
            System.out.printf(format, regexSlashes(pattern, flags), encoding, quote(input), expectedResult, actualResult);
        } else {
            String format = "%-16s%-16s%-10s%-20s%s%n";
            printTableHeader(format, "Pattern", "Encoding", "Input", "Offset", "Expected result", "TRegex result");
            System.out.printf(format, regexSlashes(pattern, flags), encoding, quote(input), fromIndex, expectedResult, actualResult);
        }
    }

    private static void printTableHeader(String format, Object... names) {
        if (printTableHeader) {
            String header = String.format(format, names);
            System.out.println();
            System.out.print(header);
            System.out.println("-".repeat(header.length() - 1));
            printTableHeader = false;
        }
    }

    private static String regexSlashes(String pattern, String flags) {
        return '/' + escape(pattern) + '/' + flags;
    }

    private static String quote(String s) {
        return '\'' + escape(s) + '\'';
    }

    private static String escape(String s) {
        return s.replace("\n", "\\n");
    }

    private static String expectedResultToString(int[] captureGroupBoundsAndLastGroup) {
        if (captureGroupBoundsAndLastGroup.length == 0) {
            return "NoMatch";
        }
        return "Match(" + Arrays.toString(captureGroupBoundsAndLastGroup) + ")";
    }

    private static String actualResultToString(Value result, int groupCount, boolean addLastGroup) {
        if (!result.getMember("isMatch").asBoolean()) {
            return "NoMatch";
        }
        StringBuilder sb = new StringBuilder("Match([");
        for (int i = 0; i < groupCount; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(result.invokeMember("getStart", i).asInt());
            sb.append(", ");
            sb.append(result.invokeMember("getEnd", i).asInt());
        }
        if (addLastGroup) {
            sb.append(", ");
            sb.append(result.getMember("lastGroup").asInt());
        }
        return sb.append("])").toString();
    }
}
