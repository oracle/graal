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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.string.Encodings;

public abstract class RegexTestBase {

    private static final boolean ASSERTS = true;

    private static Context context;

    @BeforeClass
    public static void setUp() {
        context = createContext();
        context.enter();
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

    abstract String getEngineOptions();

    abstract Encodings.Encoding getTRegexEncoding();

    Value compileRegex(String pattern, String flags) {
        return compileRegex(pattern, flags, "", getTRegexEncoding());
    }

    String createSourceString(String pattern, String flags, String options, Encodings.Encoding encoding) {
        StringBuilder combinedOptions = new StringBuilder("RegressionTestMode=true");
        if (encoding != Encodings.UTF_16_RAW) {
            combinedOptions.append(",Encoding=").append(encoding.getName());
        }
        if (!getEngineOptions().isEmpty()) {
            combinedOptions.append(",").append(getEngineOptions());
        }
        if (!options.isEmpty()) {
            combinedOptions.append(",").append(options);
        }
        return combinedOptions.append('/').append(pattern).append('/').append(flags).toString();
    }

    Value compileRegex(String pattern, String flags, String options, Encodings.Encoding encoding) {
        return compileRegex(context, pattern, flags, options, encoding);
    }

    Value compileRegex(Context ctx, String pattern, String flags, String options, Encodings.Encoding encoding) {
        return ctx.eval("regexDummyLang", createSourceString(pattern, flags, options, encoding));
    }

    Value execRegex(Value compiledRegex, String input, int fromIndex) {
        return execRegex(compiledRegex, getTRegexEncoding(), input, fromIndex);
    }

    Value execRegex(Value compiledRegex, Encodings.Encoding encoding, String input, int fromIndex) {
        return execRegex(compiledRegex, encoding, TruffleString.fromJavaStringUncached(input, encoding.getTStringEncoding()), fromIndex);
    }

    Value execRegex(Value compiledRegex, Encodings.Encoding encoding, TruffleString input, int fromIndex) {
        return compiledRegex.invokeMember("exec", input.switchEncodingUncached(encoding.getTStringEncoding()), fromIndex);
    }

    void test(String pattern, String flags, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        test(pattern, flags, "", input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
    }

    void test(String pattern, String flags, String options, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        test(pattern, flags, options, getTRegexEncoding(), input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
    }

    void test(String pattern, String flags, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        test(pattern, flags, "", encoding, input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
    }

    void test(String pattern, String flags, String options, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        Value compiledRegex = compileRegex(pattern, flags, options, encoding);
        test(compiledRegex, pattern, flags, options, encoding, input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
    }

    void test(Value compiledRegex, String pattern, String flags, String options, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        Value result = execRegex(compiledRegex, encoding, input, fromIndex);
        validateResult(pattern, flags, options, input, fromIndex, result, compiledRegex.getMember("groupCount").asInt(), isMatch, captureGroupBoundsAndLastGroup);
    }

    private static void validateResult(String pattern, String flags, String options, String input, int fromIndex, Value result, int groupCount, boolean isMatch,
                    int... captureGroupBoundsAndLastGroup) {
        if (isMatch != result.getMember("isMatch").asBoolean()) {
            fail(pattern, flags, options, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
            return;
        }
        if (isMatch) {
            if (ASSERTS) {
                assertEquals(captureGroupBoundsAndLastGroup.length / 2, groupCount);
            }
            if (captureGroupBoundsAndLastGroup.length / 2 != groupCount) {
                fail(pattern, flags, options, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
                return;
            }
            for (int i = 0; i < groupCount; i++) {
                if (captureGroupBoundsAndLastGroup[Group.groupNumberToBoundaryIndexStart(i)] != result.invokeMember("getStart", i).asInt() ||
                                captureGroupBoundsAndLastGroup[Group.groupNumberToBoundaryIndexEnd(i)] != result.invokeMember("getEnd", i).asInt()) {
                    fail(pattern, flags, options, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
                    return;
                }
            }
        } else if (result.getMember("isMatch").asBoolean()) {
            fail(pattern, flags, options, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
            return;
        }
        int lastGroup = captureGroupBoundsAndLastGroup.length % 2 == 1 ? captureGroupBoundsAndLastGroup[captureGroupBoundsAndLastGroup.length - 1] : -1;
        if (lastGroup != result.getMember("lastGroup").asInt()) {
            fail(pattern, flags, options, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
            return;
        }
        // print(pattern, input, fromIndex, result, groupCount, captureGroupBoundsAndLastGroup);
    }

    void expectUnsupported(String pattern, String flags) {
        expectUnsupported(pattern, flags, "");
    }

    void expectUnsupported(String pattern, String flags, String options) {
        Assert.assertTrue(compileRegex(pattern, flags, options, getTRegexEncoding()).isNull());
    }

    void expectSyntaxError(String pattern, String flags, String expectedMessage) {
        expectSyntaxError(pattern, flags, "", expectedMessage);
    }

    void expectSyntaxError(String pattern, String flags, String options, String expectedMessage) {
        expectSyntaxError(pattern, flags, options, getTRegexEncoding(), "", 0, expectedMessage, Integer.MIN_VALUE);
    }

    void expectSyntaxError(String pattern, String flags, String expectedMessage, int expectedPosition) {
        expectSyntaxError(pattern, flags, "", getTRegexEncoding(), "", 0, expectedMessage, expectedPosition);
    }

    void expectSyntaxError(String pattern, String flags, String options, String expectedMessage, int expectedPosition) {
        expectSyntaxError(pattern, flags, options, getTRegexEncoding(), "", 0, expectedMessage, expectedPosition);
    }

    void expectSyntaxError(String pattern, String flags, String options, Encodings.Encoding encoding, String input, int fromIndex, String expectedMessage) {
        expectSyntaxError(pattern, flags, options, encoding, input, fromIndex, expectedMessage, Integer.MIN_VALUE);
    }

    void expectSyntaxError(String pattern, String flags, String options, Encodings.Encoding encoding, String input, int fromIndex, String expectedMessage, int expectedPosition) {
        Value compiledRegex;
        try {
            compiledRegex = compileRegex(pattern, flags, options, getTRegexEncoding());
        } catch (PolyglotException e) {
            String msg = e.getMessage();
            int pos = e.getSourceLocation().getCharIndex();
            if (!msg.contains(expectedMessage)) {
                printTable(pattern, input, fromIndex, syntaxErrorToString(expectedMessage), syntaxErrorToString(msg));
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
        Value result = execRegex(compiledRegex, encoding, input, fromIndex);
        printTable(pattern, input, fromIndex, syntaxErrorToString(expectedMessage), actualResultToString(result, compiledRegex.getMember("groupCount").asInt(), false));
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

    private static void fail(String pattern, String flags, String options, String input, int fromIndex, Value result, int groupCount, int... captureGroupBoundsAndLastGroup) {
        String expectedResult = expectedResultToString(captureGroupBoundsAndLastGroup);
        String actualResult = actualResultToString(result, groupCount, captureGroupBoundsAndLastGroup.length % 2 == 1);
        printTable(pattern, input, fromIndex, expectedResult, actualResult);
        if (ASSERTS) {
            Assert.fail(options + '/' + escape(pattern) + '/' + flags + ' ' + quote(input) + " expected: " + expectedResult + ", actual: " + actualResult);
        }
    }

    private static void print(String pattern, String input, int fromIndex, Value result, int groupCount, int... captureGroupBoundsAndLastGroup) {
        String actualResult = actualResultToString(result, groupCount, captureGroupBoundsAndLastGroup.length % 2 == 1);
        printTable(pattern, input, fromIndex, actualResult, "");
    }

    private static void printTable(String pattern, String input, int fromIndex, String expectedResult, String actualResult) {
        System.out.printf("%-16s%-20s%-4d%-30s%s%n", quote(pattern), quote(input), fromIndex, expectedResult, actualResult);
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
