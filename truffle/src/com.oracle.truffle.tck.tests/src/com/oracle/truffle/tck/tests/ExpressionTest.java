/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.graalvm.polyglot.Value;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.tck.Snippet;
import org.junit.AfterClass;
import org.junit.Before;

@RunWith(Parameterized.class)
public class ExpressionTest {

    private static final TestUtil.CollectingMatcher<TestRun> TEST_RESULT_MATCHER = TestUtil.createTooManyFailuresMatcher();
    private static TestContext context;
    private final TestRun testRun;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<? extends TestRun> createExpressionTests() {
        context = new TestContext(ExpressionTest.class);
        final Collection<? extends TestRun> testRuns = TestUtil.createTestRuns(
                        TestUtil.getRequiredLanguages(context),
                        TestUtil.getRequiredValueLanguages(context),
                        new Function<String, Collection<? extends Snippet>>() {
                            @Override
                            public Collection<? extends Snippet> apply(String lang) {
                                return context.getExpressions(null, null, lang);
                            }
                        },
                        new Function<String, Collection<? extends Snippet>>() {
                            @Override
                            public Collection<? extends Snippet> apply(String lang) {
                                return context.getValueConstructors(null, lang);
                            }
                        });
        return testRuns;
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

    public ExpressionTest(final TestRun testRun) {
        Objects.requireNonNull(testRun);
        this.testRun = testRun;
    }

    @Test
    public void testExpression() {
        Assume.assumeThat(testRun, TEST_RESULT_MATCHER);
        boolean success = false;
        try {
            try {
                final Value result = testRun.getSnippet().getExecutableValue().execute(testRun.getActualParameters().toArray());
                TestUtil.validateResult(testRun, result, null);
                success = true;
            } catch (PolyglotException pe) {
                TestUtil.validateResult(testRun, null, pe);
                success = true;
            }
        } catch (PolyglotException | AssertionError e) {
            throw new AssertionError(
                            TestUtil.formatErrorMessage(
                                            "Unexpected Exception: " + e.getMessage(),
                                            testRun,
                                            context),
                            e);
        } finally {
            TEST_RESULT_MATCHER.accept(new AbstractMap.SimpleImmutableEntry<>(testRun, success));
        }
    }
}
