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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;

@RunWith(Parameterized.class)
public class ErrorTypeTest {

    private static final TestUtil.CollectingMatcher<TestRun> TEST_RESULT_MATCHER = TestUtil.createTooManyFailuresMatcher();
    private static TestContext context;
    private final TestRun testRun;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<? extends TestRun> createErrorTypeTests() {
        context = new TestContext(ErrorTypeTest.class);
        final Set<? extends String> requiredLanguages = TestUtil.getRequiredLanguages(context);
        final Collection<TestRun> testRuns = new LinkedHashSet<>();
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

    @Before
    public void setUp() {
        // JUnit mixes test executions from different classes. There are still tests using the
        // deprecated PolyglotEngine. For tests executed by Parametrized runner
        // creating Context as a test parameter we need to ensure that correct SPI is used.
        Engine.create().close();
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
                final Collection<Map.Entry<String, ? extends Snippet>> valueConstructors = new ArrayList<>();
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
                    TestUtil.validateResult(testRun, null, pe);
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
