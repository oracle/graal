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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.graalvm.polyglot.PolyglotException;
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
                        LANGUAGE == null ? null : (lang) -> LANGUAGE.equals(lang));
    }

    static Set<? extends String> getRequiredValueLanguages(final TestContext context) {
        final Predicate<String> predicate;
        if (VALUES != null) {
            final Set<String> requiredValues = new HashSet<>();
            Collections.addAll(requiredValues, VALUES.split(","));
            predicate = (lang) -> requiredValues.contains(lang);
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
        final List<TestRun> testRuns = new ArrayList<>();
        for (String opLanguage : requiredLanguages) {
            for (Snippet operator : snippetsProvider.apply(opLanguage)) {
                for (String parLanguage : requiredValueLanguages) {
                    final Collection<Pair<String, ? extends Snippet>> valueConstructors = valuesProvider.apply(parLanguage).stream().map((vc) -> Pair.of(parLanguage, vc)).collect(
                                    Collectors.toSet());
                    final List<List<Pair<String, ? extends Snippet>>> applicableParams = findApplicableParameters(operator, valueConstructors);
                    final boolean canBeInvoked = applicableParams.stream().map((l) -> !l.isEmpty()).reduce(true, (a, b) -> a && b);
                    if (canBeInvoked) {
                        computeAllPermutations(Pair.of(opLanguage, operator), applicableParams, testRuns);
                    }
                }
            }
        }
        return testRuns;
    }

    static void validateResult(
                    final TestRun testRun,
                    final Value result,
                    final PolyglotException exception) {
        final ResultVerifier verifier = testRun.getSnippet().getResultVerifier();
        if (exception == null) {
            verifier.accept(ResultVerifier.SnippetRun.create(testRun.getSnippet(), testRun.getActualParameters(), result));
            verifyToString(testRun, result);
            verifyInterop(result);
        } else {
            verifier.accept(ResultVerifier.SnippetRun.create(testRun.getSnippet(), testRun.getActualParameters(), exception));
        }
    }

    static List<List<Pair<String, ? extends Snippet>>> findApplicableParameters(
                    final Snippet operator,
                    final Collection<Pair<String, ? extends Snippet>> valueConstructors) {
        List<? extends TypeDescriptor> opParameterTypes = operator.getParameterTypes();
        final List<List<Pair<String, ? extends Snippet>>> params = new ArrayList<>(opParameterTypes.size());
        for (int i = 0; i < opParameterTypes.size(); i++) {
            params.add(new ArrayList<>());
            final TypeDescriptor paramTypeDesc = opParameterTypes.get(i);
            for (Pair<String, ? extends Snippet> constructor : valueConstructors) {
                if (paramTypeDesc.isAssignable(constructor.getValue().getReturnType())) {
                    params.get(i).add(constructor);
                }
            }
        }
        return params;
    }

    static void computeAllPermutations(
                    final Pair<String, ? extends Snippet> operator,
                    final List<List<Pair<String, ? extends Snippet>>> applicableParameters,
                    final Collection<? super TestRun> collector) {
        computeAllPermutationsImpl(operator, applicableParameters, collector, 0, new int[applicableParameters.size()]);
    }

    private static void computeAllPermutationsImpl(
                    final Pair<String, ? extends Snippet> operator,
                    final List<List<Pair<String, ? extends Snippet>>> applicableArgs,
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
            final List<Pair<String, ? extends Snippet>> applicableForArg = applicableArgs.get(index);
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

    private static void verifyToString(final TestRun testRun, final Value result) {
        try {
            result.toString();
        } catch (Exception e) {
            throw new AssertionError(
                            String.format("The result's toString of : %s failed.", testRun),
                            e);
        }
    }

    private static void verifyInterop(final Value result) {
        if (result.isBoolean()) {
            verifyBoolean(result);
        }
        if (result.isNumber()) {
            verifyNumber(result);
        }
        if (result.isString()) {
            verifyString(result);
        }
        if (result.hasArrayElements()) {
            verifyArray(result);
        }
        if (result.hasMembers()) {
            verifyObject(result);
        }
    }

    private static void verifyBoolean(final Value result) {
        result.asBoolean();
    }

    private static void verifyNumber(final Value result) {
        if (result.fitsInByte()) {
            result.asByte();
        }
        if (result.fitsInInt()) {
            result.asInt();
        }
        if (result.fitsInLong()) {
            result.asLong();
        }
        if (result.fitsInFloat()) {
            result.asFloat();
        }
        if (result.fitsInDouble()) {
            result.asDouble();
        }
    }

    private static void verifyString(final Value result) {
        result.asString();
    }

    private static void verifyArray(final Value value) {
        final long size = value.getArraySize();
        if (size > 0) {
            value.getArrayElement(0);
        }
    }

    private static void verifyObject(final Value value) {
        for (String key : value.getMemberKeys()) {
            value.getMember(key);
        }
    }

    abstract static class CollectingMatcher<T> extends BaseMatcher<T> implements Consumer<Pair<T, Boolean>> {
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
        public void accept(final Pair<T, Boolean> testRun) {
            if (!testRun.getValue()) {
                failures++;
            }
        }
    }
}
