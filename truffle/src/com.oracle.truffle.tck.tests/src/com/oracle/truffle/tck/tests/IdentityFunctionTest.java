/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.polyglot.tck.TypeDescriptor.ARRAY;
import static org.graalvm.polyglot.tck.TypeDescriptor.BOOLEAN;
import static org.graalvm.polyglot.tck.TypeDescriptor.HOST_OBJECT;
import static org.graalvm.polyglot.tck.TypeDescriptor.NATIVE_POINTER;
import static org.graalvm.polyglot.tck.TypeDescriptor.NULL;
import static org.graalvm.polyglot.tck.TypeDescriptor.NUMBER;
import static org.graalvm.polyglot.tck.TypeDescriptor.OBJECT;
import static org.graalvm.polyglot.tck.TypeDescriptor.STRING;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.ResultVerifier;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
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
        return testRuns;
    }

    private static final TypeDescriptor ANY = TypeDescriptor.union(NULL, BOOLEAN, NUMBER, STRING, HOST_OBJECT, NATIVE_POINTER, OBJECT, ARRAY);

    static Snippet createIdentitySnippet(String lang) {
        LanguageProvider tli = context.getInstalledProviders().get(lang);

        Value value = tli.createIdentityFunction(context.getContext());
        if (!value.canExecute()) {
            throw new AssertionError(String.format("Result of createIdentityFunction for tck provider %s did not return an executable value. Returned value '%s'.", lang, value));
        }

        return (Snippet.newBuilder("identity", tli.createIdentityFunction(context.getContext()), ANY).parameterTypes(ANY).resultVerifier(new ResultVerifier() {
            public void accept(SnippetRun snippetRun) throws PolyglotException {
                final PolyglotException exception = snippetRun.getException();
                if (exception != null) {
                    throw exception;
                }

                Value parameter = snippetRun.getParameters().get(0);
                TypeDescriptor parameterType = TypeDescriptor.forValue(parameter);
                TypeDescriptor resultType = TypeDescriptor.forValue(snippetRun.getResult());
                if (!parameterType.isAssignable(resultType) || !resultType.isAssignable(resultType)) {
                    throw new AssertionError(String.format(
                                    "Identity function result type must contain the parameter type. Parameter type: %s Result type: %s.",
                                    parameterType,
                                    resultType));
                }
            }
        }).build());
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
