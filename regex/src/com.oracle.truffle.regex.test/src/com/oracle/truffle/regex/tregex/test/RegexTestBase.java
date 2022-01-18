/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.oracle.truffle.regex.tregex.string.Encodings;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

public abstract class RegexTestBase {

    private static Context context;

    @BeforeClass
    public static void setUp() {
        context = Context.newBuilder().build();
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

    abstract String getEngineOptions();

    Value compileRegex(String pattern, String flags) {
        return compileRegex(pattern, flags, "");
    }

    Value compileRegex(String pattern, String flags, Encodings.Encoding encoding) {
        return compileRegex(pattern, flags, "Encoding=" + encoding.getName());
    }

    Value compileRegex(String pattern, String flags, String options) {
        StringBuilder combinedOptions = new StringBuilder("RegressionTestMode=true");
        if (!getEngineOptions().isEmpty()) {
            combinedOptions.append("," + getEngineOptions());
        }
        if (!options.isEmpty()) {
            combinedOptions.append("," + options);
        }
        return context.eval("regexDummyLang", combinedOptions.toString() + '/' + pattern + '/' + flags);
    }

    Value execRegex(Value compiledRegex, Object input, int fromIndex) {
        return compiledRegex.invokeMember("exec", input, fromIndex);
    }

    void test(String pattern, String flags, Object input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        test(pattern, flags, "", input, fromIndex, isMatch, captureGroupBoundsAndLastGroup);
    }

    void test(String pattern, String flags, String options, Object input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        Value compiledRegex = compileRegex(pattern, flags, options);
        Value result = execRegex(compiledRegex, input, fromIndex);
        validateResult(result, compiledRegex.getMember("groupCount").asInt(), isMatch, captureGroupBoundsAndLastGroup);
    }

    void testBytes(String pattern, String flags, Encodings.Encoding encoding, String input, int fromIndex, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        Value compiledRegex = compileRegex(pattern, flags, encoding);

        byte[] bytes = input.getBytes(encodingToCharSet(encoding));
        Object[] objects = new Object[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            objects[i] = Byte.toUnsignedInt(bytes[i]);
        }
        ProxyArray proxy = ProxyArray.fromArray(objects);

        Value result = execRegex(compiledRegex, proxy, fromIndex);
        validateResult(result, compiledRegex.getMember("groupCount").asInt(), isMatch, captureGroupBoundsAndLastGroup);
    }

    private static void validateResult(Value result, int groupCount, boolean isMatch, int... captureGroupBoundsAndLastGroup) {
        assertEquals(isMatch, result.getMember("isMatch").asBoolean());
        if (isMatch) {
            assertEquals(captureGroupBoundsAndLastGroup.length / 2, groupCount);
            for (int i = 0; i < captureGroupBoundsAndLastGroup.length / 2; i++) {
                if (captureGroupBoundsAndLastGroup[i * 2] != result.invokeMember("getStart", i).asInt() || captureGroupBoundsAndLastGroup[i * 2 + 1] != result.invokeMember("getEnd", i).asInt()) {
                    fail(result, captureGroupBoundsAndLastGroup);
                }
            }
        }
        int lastGroup = captureGroupBoundsAndLastGroup.length % 2 == 1 ? captureGroupBoundsAndLastGroup[captureGroupBoundsAndLastGroup.length - 1] : -1;
        if (lastGroup != result.getMember("lastGroup").asInt()) {
            fail(result, captureGroupBoundsAndLastGroup);
        }
    }

    private static Charset encodingToCharSet(Encodings.Encoding encoding) {
        switch (encoding.getName()) {
            case "UTF-8":
                return StandardCharsets.UTF_8;
            case "LATIN-1":
                return StandardCharsets.ISO_8859_1;
            default:
                throw new UnsupportedOperationException("unexpected encoding");
        }
    }

    void expectSyntaxError(String pattern, String flags, String expectedMessage) {
        try {
            compileRegex(pattern, flags);
        } catch (PolyglotException e) {
            Assert.assertTrue(e.getMessage().contains(expectedMessage));
            return;
        }
        Assert.fail();
    }

    private static void fail(Value result, int... captureGroupBoundsAndLastGroup) {
        StringBuilder sb = new StringBuilder("expected: ").append(Arrays.toString(captureGroupBoundsAndLastGroup)).append(", actual: [");
        for (int i = 0; i < captureGroupBoundsAndLastGroup.length / 2; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(result.invokeMember("getStart", i).asInt());
            sb.append(", ");
            sb.append(result.invokeMember("getEnd", i).asInt());
        }
        sb.append(", ");
        sb.append(result.getMember("lastGroup").asInt());
        Assert.fail(sb.append("]").toString());
    }
}
