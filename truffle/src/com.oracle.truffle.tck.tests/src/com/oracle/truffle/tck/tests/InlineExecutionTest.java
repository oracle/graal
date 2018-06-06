/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.tests;

import com.oracle.truffle.tck.common.inline.InlineVerifier;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.InlineSnippet;

@RunWith(Parameterized.class)
public class InlineExecutionTest {
    private static final TestUtil.CollectingMatcher<TestRun> TEST_RESULT_MATCHER = TestUtil.createTooManyFailuresMatcher();
    private static TestContext context;
    private final InlineTestRun testRun;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<InlineTestRun> createScriptTests() {
        context = new TestContext(InlineExecutionTest.class);
        final Collection<InlineTestRun> res = new LinkedHashSet<>();
        for (String lang : TestUtil.getRequiredLanguages(context)) {
            for (InlineSnippet snippet : context.getInlineScripts(lang)) {
                res.add(new InlineTestRun(new AbstractMap.SimpleImmutableEntry<>(lang, snippet.getScript()), snippet));
            }
        }
        return res;
    }

    @AfterClass
    public static void afterClass() throws IOException {
        context.close();
        context = null;
    }

    @Before
    public void setUp() {
        // JUnit mixes test executions from different classes. There are still tests using the
        // deprecated PolyglotEngine. For tests executed by Parametrized runner
        // creating Context as a test parameter we need to ensure that correct SPI is used.
        Engine.create().close();
    }

    public InlineExecutionTest(final InlineTestRun testRun) {
        Objects.requireNonNull(testRun);
        this.testRun = testRun;
    }

    @Test
    public void testInline() throws Exception {
        Assume.assumeThat(testRun, TEST_RESULT_MATCHER);
        boolean success = false;
        InlineSnippet inlineSnippet = testRun.getInlineSnippet();
        TestResultVerifier verifier;
        if (testRun.getInlineSnippet().getResultVerifier() != null) {
            verifier = new TestResultVerifier();
        } else {
            verifier = null;
        }
        context.getContext().initialize(testRun.getID());
        context.setInlineSnippet(testRun.getID(), inlineSnippet, verifier);
        try {
            try {
                final Value result = testRun.getSnippet().getExecutableValue().execute(testRun.getActualParameters().toArray());
                TestUtil.validateResult(testRun, result, null);
                success = true;
            } catch (PolyglotException pe) {
                TestUtil.validateResult(testRun, null, pe);
                success = true;
            }
            if (verifier != null && verifier.exception != null) {
                success = false;
                throw verifier.exception;
            }
        } catch (PolyglotException | AssertionError e) {
            throw new AssertionError(
                            TestUtil.formatErrorMessage(
                                            "Unexpected Exception: " + e.getMessage(),
                                            testRun,
                                            context),
                            e);
        } finally {
            context.setInlineSnippet(null, null, null);
            TEST_RESULT_MATCHER.accept(new AbstractMap.SimpleImmutableEntry<>(testRun, success));
        }
    }

    private class TestResultVerifier implements InlineVerifier.ResultVerifier {

        Exception exception;

        @Override
        public void verify(Object ret) {
            Value result = context.getValue(ret);
            InlineSnippet inlineSnippet = testRun.getInlineSnippet();
            TestUtil.validateResult(inlineSnippet.getResultVerifier(), testRun, result, null);
        }

        @Override
        public void verify(PolyglotException pe) {
            InlineSnippet inlineSnippet = testRun.getInlineSnippet();
            try {
                TestUtil.validateResult(inlineSnippet.getResultVerifier(), testRun, null, pe);
            } catch (Exception exc) {
                exception = exc;
            }
        }

    }
}
