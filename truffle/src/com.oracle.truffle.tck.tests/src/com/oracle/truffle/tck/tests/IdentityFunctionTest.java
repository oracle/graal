/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.tests;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.Snippet;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IdentityFunctionTest {

    private static final TestUtil.CollectingMatcher<TestRun> TEST_RESULT_MATCHER = TestUtil.createTooManyFailuresMatcher();
    private static TestContext context;
    private final TestRun testRun;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<? extends TestRun> createExpressionTests() {
        context = new TestContext(IdentityFunctionTest.class);
        final Collection<? extends TestRun> testRuns = TestUtil.createTestRuns(
                        TestUtil.getRequiredLanguages(context),
                        TestUtil.getRequiredValueLanguages(context),
                        new Function<String, Collection<? extends Snippet>>() {
                            @Override
                            public Collection<? extends Snippet> apply(String lang) {
                                return Arrays.asList(createIdentitySnippet(lang));
                            }
                        },
                        new Function<String, Collection<? extends Snippet>>() {
                            @Override
                            public Collection<? extends Snippet> apply(String lang) {
                                return context.getValueConstructors(null, lang);
                            }
                        });
        if (testRuns.isEmpty()) {
            // BeforeClass and AfterClass annotated methods are not called when there are no tests
            // to run. But we need to free TestContext.
            afterClass();
        }
        return testRuns;
    }

    static Snippet createIdentitySnippet(String lang) {
        LanguageProvider tli = context.getInstalledProviders().get(lang);
        return tli.createIdentityFunctionSnippet(context.getContext());
    }

    @BeforeClass
    public static void setUpClass() {
        TestUtil.assertNoCurrentContext();
    }

    @AfterClass
    public static void afterClass() {
        context.close();
        context = null;
    }

    public IdentityFunctionTest(final TestRun testRun) {
        Objects.requireNonNull(testRun);
        this.testRun = testRun;
    }

    @Test
    public void testIdentityFunction() {
        Assume.assumeThat(testRun, TEST_RESULT_MATCHER);
        boolean success = false;
        try {
            try {
                final Value result = testRun.getSnippet().getExecutableValue().execute(testRun.getActualParameters().toArray());
                TestUtil.validateResult(testRun, result, null, true);
                success = true;
            } catch (PolyglotException pe) {
                TestUtil.validateResult(testRun, null, pe, true);
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
