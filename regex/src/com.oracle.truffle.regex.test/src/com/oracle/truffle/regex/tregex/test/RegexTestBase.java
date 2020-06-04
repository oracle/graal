/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

public abstract class RegexTestBase {

    private static Context context;
    private Value engine;

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

    Value getEngine() {
        if (engine == null) {
            engine = context.eval(TRegexTestDummyLanguage.ID, "").execute("RegressionTestMode=true" + (getEngineOptions().isEmpty() ? "" : ",") + getEngineOptions());
        }
        return engine;
    }

    Value compileRegex(String pattern, String flags) {
        return getEngine().execute(pattern, flags);
    }

    Value execRegex(Value compiledRegex, Object input, int fromIndex) {
        return compiledRegex.invokeMember("exec", input, fromIndex);
    }

    void test(String pattern, String flags, Object input, int fromIndex, boolean isMatch, int... captureGroupBounds) {
        assert captureGroupBounds.length % 2 == 0;
        Value compiledRegex = compileRegex(pattern, flags);
        Value result = execRegex(compiledRegex, input, fromIndex);
        assertEquals(isMatch, result.getMember("isMatch").asBoolean());
        if (isMatch) {
            assertEquals(captureGroupBounds.length / 2, compiledRegex.getMember("groupCount").asInt());
            for (int i = 0; i < captureGroupBounds.length / 2; i++) {
                if (captureGroupBounds[i * 2] != result.invokeMember("getStart", i).asInt() || captureGroupBounds[i * 2 + 1] != result.invokeMember("getEnd", i).asInt()) {
                    fail(result, captureGroupBounds);
                }
            }
        }
    }

    private static void fail(Value result, int... captureGroupBounds) {
        StringBuilder sb = new StringBuilder("expected: ").append(Arrays.toString(captureGroupBounds)).append(", actual: [");
        for (int i = 0; i < captureGroupBounds.length / 2; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(result.invokeMember("getStart", i).asInt());
            sb.append(", ");
            sb.append(result.invokeMember("getEnd", i).asInt());
        }
        Assert.fail(sb.append("]").toString());
    }
}
