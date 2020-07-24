/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.junit.AfterClass;
import org.junit.Assume;

@RunWith(Parameterized.class)
public class ErrorTypeTest {

    private static final TestUtil.CollectingMatcher<TestRun> TEST_RESULT_MATCHER = TestUtil.createTooManyFailuresMatcher();
    private static TestContext context;
    private final TestRun testRun;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<? extends TestRun> createErrorTypeTests() {
        context = new TestContext(ErrorTypeTest.class);
        final Set<? extends String> requiredLanguages = TestUtil.getRequiredLanguages(context);
        final Collection<TestRun> testRuns = new TreeSet<>((a, b) -> a.toString().compareTo(b.toString()));
        for (String snippetLanguage : requiredLanguages) {
            Collection<? extends Snippet> snippets = context.getExpressions(null, null, snippetLanguage);
            Map<String, Collection<? extends Snippet>> overloads = computeOverloads(snippets);
            computeSnippets(snippetLanguage, snippets, overloads, testRuns);
            snippets = context.getStatements(null, null, snippetLanguage);
            overloads = computeOverloads(snippets);
            computeSnippets(snippetLanguage, snippets, overloads, testRuns);
        }
        return testRuns;
    }

    @AfterClass
    public static void afterClass() throws IOException {
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
                final Collection<Map.Entry<String, ? extends Snippet>> valueConstructors = new TreeSet<>((a, b) -> a.getValue().getId().compareTo(b.getValue().getId()));
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
            res.computeIfAbsent(snippet.getId(), new Function<String, Collection<Snippet>>() {
                @Override
                public Collection<Snippet> apply(String id) {
                    return new ArrayList<>();
                }
            }).add(snippet);
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
            try {
                testRun.getSnippet().getExecutableValue().execute(testRun.getActualParameters().toArray());
            } catch (PolyglotException pe) {
                try {
                    TestUtil.validateResult(testRun, null, pe, true);
                } catch (PolyglotException | AssertionError e) {
                    if (pe.equals(e)) {
                        passed = true;
                    } else {
                        throw new AssertionError(
                                        TestUtil.formatErrorMessage(
                                                        "Unexpected Exception: " + e.getMessage() + ", expected: " + pe.getMessage(),
                                                        testRun,
                                                        context),
                                        e);
                    }
                }
            }
            if (!passed) {
                throw new AssertionError(TestUtil.formatErrorMessage(
                                "Expected PolyglotException, but executed successfully.",
                                testRun,
                                context));
            }
        } finally {
            TEST_RESULT_MATCHER.accept(new AbstractMap.SimpleImmutableEntry<>(testRun, passed));
        }
    }
}
