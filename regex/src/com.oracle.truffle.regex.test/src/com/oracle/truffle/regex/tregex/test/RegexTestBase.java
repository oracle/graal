/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.test;

import static org.junit.Assert.assertEquals;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
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
            engine = context.eval(TRegexTestDummyLanguage.ID, "").execute(getEngineOptions());
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
                assertEquals(captureGroupBounds[i * 2], result.invokeMember("getStart", i).asInt());
                assertEquals(captureGroupBounds[i * 2 + 1], result.invokeMember("getEnd", i).asInt());
            }
        }
    }
}
