/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.graalvm.polyglot.PolyglotException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.junit.AfterClass;
import org.junit.Assume;

/**
 * This test class is designed to validate how language expressions and statements handle invalid
 * values. Unlike other TCK tests, this test explicitly examines scenarios where values passed to
 * language operators or statements are not assignable to their expected parameter types. The goal
 * is to ensure that the language correctly throws exceptions when processing these invalid values.
 * <p>
 * The expected behavior is that the execution of the code snippets under test will throw a
 * {@link PolyglotException} when invalid values are encountered. All types not explicitly listed as
 * valid in expression or statement snippet are considered invalid, and the test expects the
 * execution to fail with a {@link PolyglotException} in such cases.
 * <p>
 * The following features may help {@link org.graalvm.polyglot.tck.LanguageProvider} implementers in
 * specifying all valid snippet types, ensuring this test passes successfully.
 * <ul>
 * <li><b>Overloaded Snippets:</b> Expression and statement snippets may support multiple overloads,
 * where more snippets with the same name and different parameter types are provided. The test
 * framework considers all valid parameter types across these overloads and removes them from an
 * invalid values set. An example can be found in the <a href=
 * "https://github.com/oracle/graaljs/blob/fad9af323bbf014168bda8f1aae3c96b08c7d33e//graal-js/src/com.oracle.truffle.js.test.sdk/src/com/oracle/truffle/js/test/sdk/tck/JavaScriptTCKLanguageProvider.java#L185">
 * JavaScript '+' expression</a>.</li>
 * <li><b>Custom Result Verification:</b> Snippets may register a custom implementation of
 * {@link org.graalvm.polyglot.tck.ResultVerifier} to allow dynamic exception filtering or
 * additional validation during test execution. An example can be found in the <a href=
 * "https://github.com/oracle/graaljs/blob/fad9af323bbf014168bda8f1aae3c96b08c7d33e//graal-js/src/com.oracle.truffle.js.test.sdk/src/com/oracle/truffle/js/test/sdk/tck/JavaScriptTCKLanguageProvider.java#L223">
 * JavaScript '>>>' expression</a>.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class ErrorTypeTest {

    private static final TestUtil.CollectingMatcher<TestRun> TEST_RESULT_MATCHER = TestUtil.createTooManyFailuresMatcher();
    private static TestContext context;
    private final TestRun testRun;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<? extends TestRun> createErrorTypeTests() {
        context = new TestContext(ErrorTypeTest.class);
        final Set<? extends String> requiredLanguages = TestUtil.getRequiredLanguages(context);
        final Collection<TestRun> testRuns = new TreeSet<>(Comparator.comparing(TestRun::toString));
        for (String snippetLanguage : requiredLanguages) {
            Collection<? extends Snippet> snippets = context.getExpressions(null, null, snippetLanguage);
            Map<String, Collection<? extends Snippet>> overloads = computeOverloads(snippets);
            computeSnippets(snippetLanguage, snippets, overloads, testRuns);
            snippets = context.getStatements(null, null, snippetLanguage);
            overloads = computeOverloads(snippets);
            computeSnippets(snippetLanguage, snippets, overloads, testRuns);
        }
        if (testRuns.isEmpty()) {
            // BeforeClass and AfterClass annotated methods are not called when there are no tests
            // to run. But we need to free TestContext.
            afterClass();
        }
        return testRuns;
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

    private static void computeSnippets(
                    final String snippetLanguage,
                    final Collection<? extends Snippet> snippets,
                    final Map<String, Collection<? extends Snippet>> overloads,
                    final Collection<? super TestRun> collector) {
        final Set<? extends String> requiredValueLanguages = TestUtil.getRequiredValueLanguages(context);
        for (Snippet snippet : snippets) {
            for (String parLanguage : requiredValueLanguages) {
                if (snippetLanguage.equals(parLanguage)) {
                    continue;
                }
                final Collection<Map.Entry<String, ? extends Snippet>> valueConstructors = new TreeSet<>(Comparator.comparing(a -> a.getValue().getId()));
                for (Snippet valueConstructor : context.getValueConstructors(null, parLanguage)) {
                    valueConstructors.add(new AbstractMap.SimpleImmutableEntry<>(parLanguage, valueConstructor));
                }
                final List<List<Map.Entry<String, ? extends Snippet>>> applicableParams = TestUtil.findApplicableParameters(snippet, valueConstructors);
                if (!applicableParams.isEmpty()) {
                    final Collection<? extends Snippet> operatorOverloads = new ArrayList<>(overloads.get(snippet.getId()));
                    operatorOverloads.remove(snippet);
                    computeAllInvalidPermutations(
                                    new AbstractMap.SimpleImmutableEntry<>(snippetLanguage, snippet),
                                    applicableParams,
                                    valueConstructors,
                                    operatorOverloads,
                                    collector);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Collection<? extends Snippet>> computeOverloads(final Collection<? extends Snippet> snippets) {
        final Map<String, Collection<Snippet>> res = new HashMap<>();
        for (Snippet snippet : snippets) {
            res.computeIfAbsent(snippet.getId(), id -> new ArrayList<>()).add(snippet);
        }
        return (Map<String, Collection<? extends Snippet>>) (Map<String, ?>) res;
    }

    private static void computeAllInvalidPermutations(
                    final Map.Entry<String, ? extends Snippet> operator,
                    final List<List<Map.Entry<String, ? extends Snippet>>> applicableArgs,
                    final Collection<Map.Entry<String, ? extends Snippet>> allValueConstructors,
                    final Collection<? extends Snippet> overloads,
                    final Collection<? super TestRun> collector) {
        for (int i = 0; i < applicableArgs.size(); i++) {
            final Set<Map.Entry<String, ? extends Snippet>> nonApplicableArgs = new HashSet<>(allValueConstructors);
            nonApplicableArgs.removeAll(applicableArgs.get(i));
            if (!nonApplicableArgs.isEmpty()) {
                final List<List<Map.Entry<String, ? extends Snippet>>> args = new ArrayList<>(applicableArgs.size());
                boolean canBeInvoked = true;
                for (int j = 0; j < applicableArgs.size(); j++) {
                    if (i == j) {
                        args.add(new ArrayList<>(nonApplicableArgs));
                    } else {
                        final List<Map.Entry<String, ? extends Snippet>> slotArgs = applicableArgs.get(j);
                        if (slotArgs.isEmpty()) {
                            canBeInvoked = false;
                            break;
                        } else {
                            args.add(Collections.singletonList(findBestApplicableArg(slotArgs, overloads, j)));
                        }
                    }
                }
                if (canBeInvoked) {
                    final Collection<TestRun> tmp = new ArrayList<>();
                    TestUtil.computeAllPermutations(operator, args, tmp);
                    if (!overloads.isEmpty()) {
                        for (Iterator<TestRun> it = tmp.iterator(); it.hasNext();) {
                            TestRun test = it.next();
                            boolean remove = false;
                            for (Snippet overload : overloads) {
                                if (areParametersAssignable(overload.getParameterTypes(), test.getActualParameterTypes())) {
                                    remove = true;
                                    break;
                                }
                            }
                            if (remove) {
                                it.remove();
                            }
                        }
                    }
                    collector.addAll(tmp);
                }
            }
        }
    }

    private static Map.Entry<String, ? extends Snippet> findBestApplicableArg(final List<Map.Entry<String, ? extends Snippet>> applicableTypes, final Collection<? extends Snippet> overloads,
                    final int parameterIndex) {
        final Iterator<Map.Entry<String, ? extends Snippet>> it = applicableTypes.iterator();
        final Collection<TypeDescriptor> overloadsTypes = new ArrayList<>();
        for (Snippet overload : overloads) {
            final List<? extends TypeDescriptor> params = overload.getParameterTypes();
            if (parameterIndex < params.size()) {
                overloadsTypes.add(params.get(parameterIndex));
            }
        }
        Map.Entry<String, ? extends Snippet> bestSoFar = it.next();
        while (isCoveredByOverload(bestSoFar, overloadsTypes) && it.hasNext()) {
            bestSoFar = it.next();
        }
        return bestSoFar;
    }

    private static boolean isCoveredByOverload(final Map.Entry<String, ? extends Snippet> value, final Collection<? extends TypeDescriptor> overloadsTypes) {
        final TypeDescriptor valueType = value.getValue().getReturnType();
        for (TypeDescriptor td : overloadsTypes) {
            if (td.isAssignable(valueType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean areParametersAssignable(final List<? extends TypeDescriptor> into, List<? extends TypeDescriptor> from) {
        if (into.size() != from.size()) {
            return false;
        }
        for (int i = 0; i < into.size(); i++) {
            if (!into.get(i).isAssignable(from.get(i))) {
                return false;
            }
        }
        return true;
    }

    public ErrorTypeTest(final TestRun testRun) {
        Objects.requireNonNull(testRun);
        this.testRun = testRun;
    }

    @Test
    public void testErrorType() {
        Assume.assumeThat(testRun, TEST_RESULT_MATCHER);
        boolean passed = false;
        try {
            PolyglotException polyglotException = null;
            try {
                testRun.getSnippet().getExecutableValue().execute(testRun.getActualParameters().toArray());
            } catch (PolyglotException e) {
                polyglotException = e;
            } catch (IllegalArgumentException e) {
                polyglotException = context.getContext().asValue(e).as(PolyglotException.class);
            }
            if (polyglotException != null) {
                try {
                    TestUtil.validateResult(testRun, polyglotException);
                } catch (PolyglotException | AssertionError e) {
                    if (polyglotException.equals(e)) {
                        passed = true;
                    } else {
                        throw new AssertionError(
                                        TestUtil.formatErrorMessage(
                                                        "Unexpected Exception: " + e + ", expected: " + polyglotException,
                                                        testRun,
                                                        context, null, polyglotException),
                                        e);
                    }
                }
            }
            if (!passed) {
                throw new AssertionError(TestUtil.formatErrorMessage(
                                "Expected PolyglotException, but executed successfully.",
                                testRun,
                                context, null, polyglotException));
            }
        } finally {
            TEST_RESULT_MATCHER.accept(new AbstractMap.SimpleImmutableEntry<>(testRun, passed));
        }
    }
}
