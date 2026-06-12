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

package com.oracle.graal.pointsto.standalone;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.standalone.StandaloneHost.ClassInitializationOutcome;
import com.oracle.graal.pointsto.util.AnalysisFuture;

/**
 * Owns the class-initialization state used by standalone analysis.
 *
 * The support is the source of truth for {@link ClassInitializationOutcome}. For every analysis
 * type, it either records that standalone analysis initialized the class at build time, that policy
 * requires runtime initialization, or that a build-time initialization attempt failed. A type with no
 * recorded outcome and a build-time policy is treated as pending.
 *
 * Build-time initialization is serialized by one {@link AnalysisFuture} per type. Static-field reads
 * that observe a pending initialization are registered for a retry and are scanned again when the
 * initialization succeeds. Failed initializations remain failed so later callers do not infer success
 * from wrapped VM state that may have changed independently.
 */
final class StandaloneClassInitializationSupport {
    /**
     * Decides which classes standalone analysis may initialize at build time.
     */
    private final StandaloneClassInitializationStrategy strategy;
    /**
     * Controls whether the first recorded failure for each type stores a formatted stack trace.
     */
    private final boolean printFailures;
    /**
     * Counts failed initialization attempts. A type can contribute more than once if concurrent
     * callers race with task cleanup and repeat the same failing attempt.
     */
    private final AtomicInteger failureCount = new AtomicInteger();
    /**
     * Stores one failure entry per type so diagnostics can report the number of distinct types whose
     * build-time initialization failed.
     */
    private final ConcurrentMap<AnalysisType, String> firstFailureStackTraces = new ConcurrentHashMap<>();
    /**
     * Records terminal standalone outcomes. Absence means either no query has reached the type yet
     * or the type is a build-time candidate whose initialization has not completed.
     */
    private final ConcurrentMap<AnalysisType, ClassInitializationOutcome> outcomes = new ConcurrentHashMap<>();
    /**
     * Holds in-flight build-time initialization work so concurrent callers wait for the same attempt.
     */
    private final ConcurrentMap<AnalysisType, AnalysisFuture<ClassInitializationOutcome>> tasks = new ConcurrentHashMap<>();
    /**
     * Tracks static fields that were read while their declaring class still had no terminal outcome.
     * Successful initialization retries these reads through the heap scanner.
     */
    private final ConcurrentMap<AnalysisType, Set<AnalysisField>> pendingStaticFieldReads = new ConcurrentHashMap<>();

    /**
     * Creates support using {@code strategy} for policy checks and {@code printFailures} for
     * diagnostic detail.
     */
    StandaloneClassInitializationSupport(StandaloneClassInitializationStrategy strategy, boolean printFailures) {
        this.strategy = strategy;
        this.printFailures = printFailures;
    }

    /**
     * Applies standalone class-initialization policy to {@code type}.
     *
     * If policy requires runtime initialization, the method records and returns
     * {@link ClassInitializationOutcome#RUNTIME_ONLY}. If the class is allowed at build time, the
     * method initializes it before returning {@link ClassInitializationOutcome#INITIALIZED}, or
     * records {@link ClassInitializationOutcome#FAILED} if the wrapped VM cannot execute the
     * initializer. Concurrent callers share one initialization task and all observe its terminal
     * outcome.
     */
    ClassInitializationOutcome maybeInitializeAtBuildTime(AnalysisType type) {
        if (!shouldInitializeAtBuildTime(type)) {
            return outcomes.computeIfAbsent(type, unused -> ClassInitializationOutcome.RUNTIME_ONLY);
        }
        ClassInitializationOutcome knownOutcome = outcomes.get(type);
        if (knownOutcome != null) {
            return knownOutcome;
        }
        if (type.getWrapped().isInitialized()) {
            return recordInitialized(type);
        }
        AnalysisFuture<ClassInitializationOutcome> task = tasks.computeIfAbsent(type, unused -> new AnalysisFuture<>(() -> initializeAtBuildTime(type)));
        return task.ensureDone();
    }

    /**
     * Returns whether standalone analysis may treat {@code type} as initialized.
     *
     * For build-time classes, this method only reports initialized after this support records an
     * {@link ClassInitializationOutcome#INITIALIZED} outcome. Failed classes are not reported as
     * initialized even if the wrapped VM state changes later. Runtime-only classes cannot use
     * standalone-managed static snapshots, but they may still expose ordinary guest objects whose
     * instance fields are available through the wrapped VM.
     */
    boolean isInitialized(AnalysisType type) {
        ClassInitializationOutcome outcome = maybeInitializeAtBuildTime(type);
        return switch (outcome) {
            case INITIALIZED -> {
                assert type.getWrapped().isInitialized() : "Types that are initialized at build time must be initialized in the wrapped VM: " + type;
                yield true;
            }
            case RUNTIME_ONLY -> type.getWrapped().isInitialized();
            case FAILED, PENDING -> false;
        };
    }

    /**
     * Returns the current outcome without starting a new initialization attempt.
     *
     * A type denied by policy is reported as {@link ClassInitializationOutcome#RUNTIME_ONLY} even if
     * it has not been queried before. A type allowed by policy and missing a terminal outcome is
     * reported as {@link ClassInitializationOutcome#PENDING}.
     */
    ClassInitializationOutcome getOutcome(AnalysisType type) {
        ClassInitializationOutcome outcome = outcomes.get(type);
        if (outcome != null) {
            return outcome;
        }
        if (!shouldInitializeAtBuildTime(type)) {
            return ClassInitializationOutcome.RUNTIME_ONLY;
        }
        return ClassInitializationOutcome.PENDING;
    }

    /**
     * Registers {@code field} for a retry when the declaring class is still being initialized.
     *
     * The returned value is the outcome observed after registration. If initialization completes
     * concurrently, the field is removed from the pending set and the terminal outcome is returned.
     * Unread fields are not registered because they do not need a heap-scanner retry.
     */
    ClassInitializationOutcome registerPendingStaticFieldRead(AnalysisField field) {
        AnalysisType declaringClass = field.getDeclaringClass();
        ClassInitializationOutcome outcome = getOutcome(declaringClass);
        if (outcome != ClassInitializationOutcome.PENDING || !field.isRead()) {
            return outcome;
        }
        Set<AnalysisField> fields = pendingStaticFieldReads.computeIfAbsent(declaringClass, unused -> ConcurrentHashMap.newKeySet());
        fields.add(field);
        outcome = getOutcome(declaringClass);
        if (outcome != ClassInitializationOutcome.PENDING) {
            fields.remove(field);
            if (fields.isEmpty()) {
                pendingStaticFieldReads.remove(declaringClass, fields);
            }
        }
        return outcome;
    }

    /**
     * Returns whether callers should include stored class-initialization stack traces in diagnostics.
     */
    boolean shouldPrintFailures() {
        return printFailures;
    }

    /**
     * Returns the number of failed build-time initialization attempts.
     */
    int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Returns the number of distinct types with at least one failed build-time initialization
     * attempt.
     */
    int getFailureTypeCount() {
        return firstFailureStackTraces.size();
    }

    /**
     * Formats the first recorded failure for each type in deterministic type-name order.
     */
    String formatFailures() {
        StringBuilder sb = new StringBuilder();
        firstFailureStackTraces.entrySet().stream()
                        .sorted((left, right) -> left.getKey().toJavaName().compareTo(right.getKey().toJavaName()))
                        .forEach(entry -> {
                            if (sb.length() > 0) {
                                sb.append(System.lineSeparator());
                            }
                            sb.append(entry.getKey().toJavaName()).append(':');
                            appendIndentedLines(sb, entry.getValue(), "    ");
                        });
        return sb.toString();
    }

    /**
     * Returns whether policy allows standalone analysis to initialize {@code type} at build time.
     */
    private boolean shouldInitializeAtBuildTime(AnalysisType type) {
        return strategy.shouldInitializeAtBuildTime(type);
    }

    /**
     * Performs the wrapped VM initialization attempt for a build-time class.
     *
     * Only exceptions from the wrapped VM initialization are classified as class-initialization
     * failures. Success-side bookkeeping, including static-field retry, is allowed to propagate
     * exceptions to the analysis task.
     */
    private ClassInitializationOutcome initializeAtBuildTime(AnalysisType type) {
        try {
            try {
                if (!type.getWrapped().isInitialized()) {
                    type.getWrapped().initialize();
                }
            } catch (Throwable failure) {
                recordFailure(type, failure);
                return ClassInitializationOutcome.FAILED;
            }
            return recordInitialized(type);
        } finally {
            tasks.remove(type);
        }
    }

    /**
     * Records successful build-time initialization and retries delayed static-field reads.
     */
    private ClassInitializationOutcome recordInitialized(AnalysisType type) {
        outcomes.put(type, ClassInitializationOutcome.INITIALIZED);
        retryPendingStaticFieldReads(type);
        return ClassInitializationOutcome.INITIALIZED;
    }

    /**
     * Records a failed build-time initialization attempt and preserves the first diagnostic for the
     * failing type.
     */
    private void recordFailure(AnalysisType type, Throwable failure) {
        failureCount.incrementAndGet();
        firstFailureStackTraces.putIfAbsent(type, printFailures ? formatThrowable(failure) : "");
        outcomes.put(type, ClassInitializationOutcome.FAILED);
    }

    /**
     * Re-scans static fields whose declaring class reached initialized state after the original
     * field-read notification.
     */
    private void retryPendingStaticFieldReads(AnalysisType type) {
        Set<AnalysisField> fields = pendingStaticFieldReads.remove(type);
        if (fields == null) {
            return;
        }
        fields.forEach(field -> {
            if (field.isRead()) {
                type.getUniverse().getHeapScanner().onFieldRead(field);
            }
        });
    }

    /**
     * Formats a throwable stack trace for optional standalone class-initialization diagnostics.
     */
    private static String formatThrowable(Throwable failure) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            failure.printStackTrace(writer);
        }
        return buffer.toString().stripTrailing();
    }

    /**
     * Appends every line of {@code text} to {@code sb} with the supplied indentation prefix.
     */
    private static void appendIndentedLines(StringBuilder sb, String text, String indent) {
        for (String line : text.split("\\R", -1)) {
            sb.append(System.lineSeparator()).append(indent).append(line);
        }
    }
}
