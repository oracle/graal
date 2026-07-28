/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.MetaAccessProvider;

public class VerifyReflectionUsageBaseTest {

    private static final MetaAccessProvider META_ACCESS = GuestAccess.get().getProviders().getMetaAccess();

    private static final class TestVerifier extends VerifyReflectionUsageBase {
        TestVerifier(List<List<? extends ExcludeEntry>> allExcludeLists) {
            super(allExcludeLists);
        }

        void observe(Method method) {
            recordProcessedMethod(META_ACCESS.lookupJavaMethod(method));
        }

        void markExcluded(Method method) {
            Assert.assertTrue(isExcluded(META_ACCESS.lookupJavaMethod(method)));
        }

        Set<String> relevantExcludes() {
            return getRelevantExcludes().stream().map(Object::toString).collect(Collectors.toSet());
        }

        Set<String> unusedRelevantExcludes() {
            return getUnusedRelevantExcludes().stream().map(Object::toString).collect(Collectors.toSet());
        }

        @Override
        protected void verify(StructuredGraph graph, CoreProviders context) {
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static void repeat(int count, CheckedRunnable action) throws Exception {
        for (int i = 0; i < count; i++) {
            action.run();
        }
    }

    private static void runConcurrently(CheckedRunnable... tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>(tasks.length);
            for (CheckedRunnable task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    task.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    static final class FirstObservedClass {
        static void observedMethod() {
        }

        static void anotherObservedMethod() {
        }
    }

    static final class SecondObservedClass {
        static void observedMethod() {
        }
    }

    static final class UnobservedClass {
        static void unobservedMethod() {
        }
    }

    @Test
    public void relevantExcludesAreLimitedToObservedGraphs() throws ReflectiveOperationException {
        TestVerifier verifier = new TestVerifier(List.of(
                        List.of(
                                        VerifyReflectionUsageBase.pkg(FirstObservedClass.class.getPackageName()),
                                        VerifyReflectionUsageBase.clazz(FirstObservedClass.class.getName()),
                                        VerifyReflectionUsageBase.method(FirstObservedClass.class.getName(), "observedMethod"),
                                        VerifyReflectionUsageBase.clazz(UnobservedClass.class.getName()),
                                        VerifyReflectionUsageBase.method(UnobservedClass.class.getName(), "unobservedMethod"),
                                        VerifyReflectionUsageBase.pkg("unobserved.package")),
                        List.of()));

        verifier.observe(FirstObservedClass.class.getDeclaredMethod("observedMethod"));
        verifier.observe(SecondObservedClass.class.getDeclaredMethod("observedMethod"));

        Assert.assertEquals(Set.of(
                        "class: " + FirstObservedClass.class.getName() + " (unhandled)",
                        "method: " + FirstObservedClass.class.getName() + "#observedMethod (unhandled)",
                        "package: " + FirstObservedClass.class.getPackageName() + " (unhandled)"),
                        verifier.relevantExcludes());
    }

    @Test
    public void usedRelevantExcludeIsNotReportedUnused() throws ReflectiveOperationException {
        TestVerifier verifier = new TestVerifier(List.of(
                        List.of(VerifyReflectionUsageBase.method(FirstObservedClass.class.getName(), "anotherObservedMethod")),
                        List.of()));

        Method observedMethod = FirstObservedClass.class.getDeclaredMethod("anotherObservedMethod");
        verifier.observe(observedMethod);
        verifier.markExcluded(observedMethod);

        Assert.assertTrue(verifier.relevantExcludes().contains("method: " + FirstObservedClass.class.getName() + "#anotherObservedMethod (unhandled)"));
        Assert.assertTrue(verifier.unusedRelevantExcludes().isEmpty());
    }

    @Test
    public void concurrentTrackingBehavesCorrectly() throws Exception {
        Method firstObservedMethod = FirstObservedClass.class.getDeclaredMethod("observedMethod");
        Method firstAnotherMethod = FirstObservedClass.class.getDeclaredMethod("anotherObservedMethod");
        Method secondObservedMethod = SecondObservedClass.class.getDeclaredMethod("observedMethod");

        TestVerifier relevantVerifier = new TestVerifier(List.of(
                        List.of(
                                        VerifyReflectionUsageBase.pkg(FirstObservedClass.class.getPackageName()),
                                        VerifyReflectionUsageBase.clazz(FirstObservedClass.class.getName()),
                                        VerifyReflectionUsageBase.method(FirstObservedClass.class.getName(), "anotherObservedMethod"),
                                        VerifyReflectionUsageBase.method(FirstObservedClass.class.getName(), "observedMethod"),
                                        VerifyReflectionUsageBase.clazz(SecondObservedClass.class.getName()),
                                        VerifyReflectionUsageBase.method(SecondObservedClass.class.getName(), "observedMethod")),
                        List.of()));

        runConcurrently(
                        () -> repeat(1_000, () -> relevantVerifier.observe(firstObservedMethod)),
                        () -> repeat(1_000, () -> relevantVerifier.observe(firstAnotherMethod)),
                        () -> repeat(1_000, () -> relevantVerifier.observe(secondObservedMethod)));

        Assert.assertEquals(Set.of(
                        "class: " + FirstObservedClass.class.getName() + " (unhandled)",
                        "class: " + SecondObservedClass.class.getName() + " (unhandled)",
                        "method: " + FirstObservedClass.class.getName() + "#anotherObservedMethod (unhandled)",
                        "method: " + FirstObservedClass.class.getName() + "#observedMethod (unhandled)",
                        "method: " + SecondObservedClass.class.getName() + "#observedMethod (unhandled)",
                        "package: " + FirstObservedClass.class.getPackageName() + " (unhandled)"),
                        relevantVerifier.relevantExcludes());

        TestVerifier excludedVerifier = new TestVerifier(List.of(
                        List.of(
                                        VerifyReflectionUsageBase.method(FirstObservedClass.class.getName(), "observedMethod"),
                                        VerifyReflectionUsageBase.method(SecondObservedClass.class.getName(), "observedMethod")),
                        List.of()));

        runConcurrently(
                        () -> repeat(1_000, () -> {
                            excludedVerifier.observe(firstObservedMethod);
                            excludedVerifier.markExcluded(firstObservedMethod);
                        }),
                        () -> repeat(1_000, () -> {
                            excludedVerifier.observe(secondObservedMethod);
                            excludedVerifier.markExcluded(secondObservedMethod);
                        }));

        Assert.assertEquals(Set.of(
                        "method: " + FirstObservedClass.class.getName() + "#observedMethod (unhandled)",
                        "method: " + SecondObservedClass.class.getName() + "#observedMethod (unhandled)"),
                        excludedVerifier.relevantExcludes());
        Assert.assertTrue(excludedVerifier.unusedRelevantExcludes().isEmpty());
    }
}
