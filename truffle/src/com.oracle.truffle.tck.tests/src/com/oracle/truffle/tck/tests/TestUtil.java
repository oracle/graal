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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.ResultVerifier;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

final class TestUtil {
    private static final int MAX_FAILURES = Integer.getInteger("tck.maxFailures", 100);
    private static final String LANGUAGE = System.getProperty("tck.language");
    private static final String VALUES = System.getProperty("tck.values");

    private TestUtil() {
        throw new IllegalStateException("No instance allowed.");
    }

    static Set<? extends String> getRequiredLanguages(final TestContext context) {
        return filterLanguages(
                        context,
                        LANGUAGE == null ? null : new Predicate<String>() {
                            @Override
                            public boolean test(String lang) {
                                return LANGUAGE.equals(lang);
                            }
                        });
    }

    static Set<? extends String> getRequiredValueLanguages(final TestContext context) {
        final Predicate<String> predicate;
        if (VALUES != null) {
            final Set<String> requiredValues = new HashSet<>();
            Collections.addAll(requiredValues, VALUES.split(","));
            predicate = new Predicate<String>() {
                @Override
                public boolean test(String lang) {
                    return requiredValues.contains(lang);
                }
            };
        } else {
            predicate = null;
        }
        return filterLanguages(context, predicate);
    }

    static <T> CollectingMatcher<T> createTooManyFailuresMatcher() {
        return new TooManyFailuresMatcher<>();
    }

    static Collection<? extends TestRun> createTestRuns(
                    final Set<? extends String> requiredLanguages,
                    final Set<? extends String> requiredValueLanguages,
                    final Function<String, ? extends Collection<? extends Snippet>> snippetsProvider,
                    final Function<String, ? extends Collection<? extends Snippet>> valuesProvider) {
        final Collection<TestRun> testRuns = new TreeSet<>((a, b) -> a.toString().compareTo(b.toString()));
        for (String opLanguage : requiredLanguages) {
            for (Snippet operator : snippetsProvider.apply(opLanguage)) {
                for (String parLanguage : requiredValueLanguages) {
                    final Collection<Map.Entry<String, ? extends Snippet>> valueConstructors = new HashSet<>();
                    for (Snippet snippet : valuesProvider.apply(parLanguage)) {
                        valueConstructors.add(new AbstractMap.SimpleImmutableEntry<>(parLanguage, snippet));
                    }
                    final List<List<Map.Entry<String, ? extends Snippet>>> applicableParams = findApplicableParameters(operator, valueConstructors);
                    boolean canBeInvoked = true;
                    for (List<Map.Entry<String, ? extends Snippet>> param : applicableParams) {
                        canBeInvoked &= !param.isEmpty();
                        if (!canBeInvoked) {
                            break;
                        }
                    }
                    if (canBeInvoked) {
                        computeAllPermutations(new AbstractMap.SimpleImmutableEntry<>(opLanguage, operator), applicableParams, testRuns);
                    }
                }
            }
        }
        return testRuns;
    }

    static void validateResult(
                    final TestRun testRun,
                    final Value result,
                    final PolyglotException exception,
                    boolean fastAssertions) {
        ResultVerifier verifier = testRun.getSnippet().getResultVerifier();
        validateResult(verifier, testRun, result, exception, fastAssertions);
    }

    static void validateResult(
                    final ResultVerifier verifier,
                    final TestRun testRun,
                    final Value result,
                    final PolyglotException exception,
                    boolean fastAssertions) {
        if (exception == null) {
            verifier.accept(ResultVerifier.SnippetRun.create(testRun.getSnippet(), testRun.getActualParameters(), result));
            assertValue(result, fastAssertions);
        } else {
            verifier.accept(ResultVerifier.SnippetRun.create(testRun.getSnippet(), testRun.getActualParameters(), exception));
            Value exceptionObject = exception.getGuestObject();
            if (exceptionObject != null) {
                assertValue(exceptionObject, fastAssertions);
            }
        }
    }

    private static void assertValue(final Value result, boolean fastAssertions) {
        if (fastAssertions) {
            ValueAssert.assertValueFast(result);
        } else {
            ValueAssert.assertValue(result);
        }
    }

    static List<List<Map.Entry<String, ? extends Snippet>>> findApplicableParameters(
                    final Snippet operator,
                    final Collection<Map.Entry<String, ? extends Snippet>> valueConstructors) {
        List<? extends TypeDescriptor> opParameterTypes = operator.getParameterTypes();
        final List<List<Map.Entry<String, ? extends Snippet>>> params = new ArrayList<>(opParameterTypes.size());
        for (int i = 0; i < opParameterTypes.size(); i++) {
            params.add(new ArrayList<>());
            final TypeDescriptor paramTypeDesc = opParameterTypes.get(i);
            for (Map.Entry<String, ? extends Snippet> constructor : valueConstructors) {
                if (paramTypeDesc.isAssignable(constructor.getValue().getReturnType())) {
                    params.get(i).add(constructor);
                }
            }
        }
        return params;
    }

    static void computeAllPermutations(
                    final Map.Entry<String, ? extends Snippet> operator,
                    final List<List<Map.Entry<String, ? extends Snippet>>> applicableParameters,
                    final Collection<? super TestRun> collector) {
        computeAllPermutationsImpl(operator, applicableParameters, collector, 0, new int[applicableParameters.size()]);
    }

    static String formatErrorMessage(
                    final String errorMessage,
                    final TestRun testRun,
                    final TestContext testContext) {
        final String language = testRun.getID();
        final Snippet snippet = testRun.getSnippet();
        final StringBuilder message = new StringBuilder();
        message.append(String.format("Running snippet '%s' retrieved from '%s' provider (java class %s) with parameters:\n",
                        snippet.getId(),
                        language,
                        testContext.getInstalledProviders().get(language).getClass().getName()));
        final List<? extends Entry<String, ? extends Snippet>> actualParameterSnippets = testRun.getActualParameterSnippets();
        final List<? extends Value> actualParameters = testRun.getActualParameters();
        for (int i = 0; i < actualParameterSnippets.size(); i++) {
            final Map.Entry<String, ? extends Snippet> actualParameterSnippet = actualParameterSnippets.get(i);
            final String paramLanguage = actualParameterSnippet.getKey();
            final Snippet paramSnippet = actualParameterSnippet.getValue();
            final Value actualParameter = actualParameters.get(i);
            message.append(String.format("'%s' from '%s' provider, value: %s (Meta Object: %s)\n",
                            paramSnippet.getId(),
                            paramLanguage,
                            actualParameter,
                            actualParameter.getMetaObject()));
        }
        message.append("failed:\n");
        message.append(errorMessage);
        message.append('\n');
        message.append("Snippet: ").append(getSource(snippet.getExecutableValue())).append('\n');
        int i = 0;
        for (Map.Entry<String, ? extends Snippet> langAndparamSnippet : testRun.getActualParameterSnippets()) {
            final Snippet paramSnippet = langAndparamSnippet.getValue();
            message.append(String.format("Parameter %d Snippet: ", i++)).append(getSource(paramSnippet.getExecutableValue())).append('\n');
        }
        return message.toString();
    }

    private static CharSequence getSource(final Value value) {
        final SourceSection section = value.getSourceLocation();
        return section == null ? null : section.getCharacters();
    }

    private static void computeAllPermutationsImpl(
                    final Map.Entry<String, ? extends Snippet> operator,
                    final List<List<Map.Entry<String, ? extends Snippet>>> applicableArgs,
                    final Collection<? super TestRun> collector,
                    final int index,
                    final int[] selected) {
        if (index == applicableArgs.size()) {
            final List<Entry<String, ? extends Snippet>> args = new ArrayList<>(applicableArgs.size());
            for (int i = 0; i < index; i++) {
                args.add(applicableArgs.get(i).get(selected[i]));
            }
            collector.add(new TestRun(operator, args));
        } else {
            final List<Map.Entry<String, ? extends Snippet>> applicableForArg = applicableArgs.get(index);
            for (int i = 0; i < applicableForArg.size(); i++) {
                selected[index] = i;
                computeAllPermutationsImpl(operator, applicableArgs, collector, index + 1, selected);
            }
        }
    }

    private static Set<? extends String> filterLanguages(
                    final TestContext context,
                    final Predicate<String> predicte) {
        final Set<? extends String> installedLangs = context.getInstalledProviders().keySet();
        return predicte == null ? installedLangs : installedLangs.stream().filter(predicte).collect(Collectors.toSet());
    }

    abstract static class CollectingMatcher<T> extends BaseMatcher<T> implements Consumer<Map.Entry<T, Boolean>> {
    }

    private static final class TooManyFailuresMatcher<T> extends CollectingMatcher<T> {

        private int failures;
        private boolean loggedTooManyFailures;

        TooManyFailuresMatcher() {
        }

        @Override
        public boolean matches(Object item) {
            return failures < MAX_FAILURES;
        }

        @Override
        public void describeTo(Description description) {
            if (!loggedTooManyFailures) {
                loggedTooManyFailures = true;
                description.appendText("Too many failures.");
            }
        }

        @Override
        public void accept(final Map.Entry<T, Boolean> testRun) {
            if (!testRun.getValue()) {
                failures++;
            }
        }
    }
}
