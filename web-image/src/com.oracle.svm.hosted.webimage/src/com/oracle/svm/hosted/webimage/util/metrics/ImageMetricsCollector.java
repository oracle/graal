/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.util.metrics;

import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.CONSTANTS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.CONSTANT_DEFS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.CONSTANT_INITS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.ENTIRE_IMAGE_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.EXTRA_DEFINITIONS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.INITIAL_DEFINITIONS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.JS_IMAGE_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.NO_CF_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.RECONSTRUCTED_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.STATIC_FIELDS_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.TOTAL_METHOD_SIZE;
import static com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys.TYPE_DECLARATIONS_SIZE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.Labeler;
import com.oracle.svm.hosted.webimage.logging.LoggableMetric;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.logging.LoggerScope;
import com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;

import jdk.graal.compiler.debug.MetricKey;

/**
 * A utility class used to track and collect image statistics with the <b>Logging API</b> and report
 * their values to the parent scope. This class upon creation creates a {@link LoggerScope} under
 * which statistics will be tracked and logged. All statistics are reported to the parent scope,
 * except for statistics found in {@link ImageBreakdownMetricKeys}, which are logged only when
 * option {@link WebImageOptions#ReportImageSizeBreakdown} is set. This class should be used in a
 * try/catch block.
 *
 * Example:
 *
 * <pre>
 *     try(ImageSizeMetricsCollector collector = new ImageSizeMetricsCollector.PreClosure(codeBuffer, options)){
 *         ...
 *     }
 * </pre>
 *
 * @see LoggerScope
 * @see LoggerContext
 */
public abstract class ImageMetricsCollector implements AutoCloseable {

    public static final MetricKey[] SAVED_SIZE_BREAKDOWN_KEYS = {
                    INITIAL_DEFINITIONS_SIZE,
                    EXTRA_DEFINITIONS_SIZE,
                    STATIC_FIELDS_SIZE,
                    TYPE_DECLARATIONS_SIZE,
                    TOTAL_METHOD_SIZE,
                    RECONSTRUCTED_SIZE,
                    NO_CF_SIZE,
                    CONSTANTS_SIZE,
                    ENTIRE_IMAGE_SIZE,
    };

    /**
     * Names of the logging scopes used by {@link PreClosure} and {@link PostClosure} classes
     * respectively.
     */
    public static final String PRE_CLOSURE_SCOPE_NAME = "Pre-Closure";
    public static final String CLOSURE_SCOPE_NAME = "Closure-Compiler";

    protected final LoggerScope scope;
    protected final boolean reportImageSizeBreakdown;
    protected final boolean reportEntireSize;

    /**
     * Stores extra metric keys that contribute to the image size.
     *
     * These do not contribute to {@link ImageBreakdownMetricKeys#JS_IMAGE_SIZE} only to
     * {@link ImageBreakdownMetricKeys#ENTIRE_IMAGE_SIZE} and the counters are used as-is without
     * modification.
     */
    protected static final List<Pair<MetricKey, LongSupplier>> additionalBreakdownKeys = new ArrayList<>();

    @SuppressWarnings("this-escape")
    protected ImageMetricsCollector(String scopeName, boolean reportImageSizeBreakdown, boolean reportEntireSize) {
        this.scope = LoggerContext.currentContext().scope(scopeName, this::onCloseHandler);
        this.reportImageSizeBreakdown = reportImageSizeBreakdown;
        this.reportEntireSize = reportEntireSize;
    }

    public static void addAdditionalBreakdownKey(MetricKey key, LongSupplier supplier) {
        additionalBreakdownKeys.add(Pair.create(key, supplier));
    }

    public static long addAdditionBreakdownKeysToScope() {
        return additionalBreakdownKeys.stream().mapToLong(pair -> {
            long size = pair.getRight().getAsLong();
            LoggerContext.counter(pair.getLeft()).add(size);
            return size;
        }).sum();
    }

    /**
     * Method that gets called before scope is closed if some additional metrics should be logged.
     */
    protected abstract void collectMetrics();

    /**
     * An OnCloseHandler that reports statistics to its parent scope. All statistics are reported
     * from the current scope, except for {@link ImageBreakdownMetricKeys} and any additional
     * breakdown keys which are reported only if {@link #reportImageSizeBreakdown} is set to true.
     */
    protected void onCloseHandler(LoggerScope s, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
        LoggerScope parent = s.parent();
        if (reportImageSizeBreakdown) {
            MetricsUtil.propagateMetrics(parent, metrics);
        } else {
            Set<MetricKey> ignoredKeys = new HashSet<>(additionalBreakdownKeys.size());
            ignoredKeys.addAll(additionalBreakdownKeys.stream().map(Pair::getLeft).toList());
            ignoredKeys.addAll(MetricsUtil.getClassMetricKeys(ImageBreakdownMetricKeys.class));

            if (reportEntireSize) {
                ignoredKeys.remove(ENTIRE_IMAGE_SIZE);
            }

            MetricsUtil.propagateMetricsExcept(parent, metrics, ignoredKeys);
        }
    }

    @Override
    public void close() {
        collectMetrics();
        scope.close();
    }

    /**
     * This class should be used for tracking and collecting statistics during compilation of the
     * image, before usage of the Closure compiler.
     */
    public static class PreClosure extends ImageMetricsCollector {
        private final JSCodeBuffer codeBuffer;

        public PreClosure(JSCodeBuffer codeBuffer) {
            super(PRE_CLOSURE_SCOPE_NAME, WebImageOptions.ReportImageSizeBreakdown.getValue() && !WebImageOptions.ClosureCompiler.getValue(),
                            !WebImageOptions.ClosureCompiler.getValue());
            this.codeBuffer = codeBuffer;
        }

        @Override
        protected void onCloseHandler(LoggerScope s, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
            LoggerContext.currentContext().saveCounters(scope, SAVED_SIZE_BREAKDOWN_KEYS);
            super.onCloseHandler(s, metrics);
        }

        /**
         * All metrics are set since we've used {@link CodeSizeCollector} to track size of
         * individual parts of image and {@link MethodMetricsCollector} to collect method statistics
         * throughout the compilation process. We only need to calculate unaccounted size and report
         * it.
         */
        @Override
        protected void collectMetrics() {
            long additionalSize = addAdditionBreakdownKeysToScope();
            int codeSize = codeBuffer.codeSize();

            /*
             * We need unaccounted size of constants initialization, because we have its detailed
             * breakdown.
             */
            LoggerContext.counter(CONSTANTS_SIZE).add(LoggerContext.counter(CONSTANT_INITS_SIZE).get() + LoggerContext.counter(CONSTANT_DEFS_SIZE).get());
            LoggerContext.counter(ENTIRE_IMAGE_SIZE).add(codeSize + additionalSize);
            LoggerContext.counter(JS_IMAGE_SIZE).add(codeSize);
        }
    }

    /**
     * This class should be used to track and collect statistics during compilation with Closure
     * compiler.
     */
    public static class PostClosure extends ImageMetricsCollector {
        private final Labeler labeler;
        private final JSCodeBuffer codeBuffer;
        private final MethodMetricsCollector methodMetricsCollector;

        public PostClosure(MethodMetricsCollector methodMetricsCollector, Labeler labeler, JSCodeBuffer codeBuffer) {
            super(CLOSURE_SCOPE_NAME, WebImageOptions.ReportImageSizeBreakdown.getValue(), true);
            this.labeler = labeler;
            this.codeBuffer = codeBuffer;
            this.methodMetricsCollector = methodMetricsCollector;
        }

        @Override
        protected void onCloseHandler(LoggerScope s, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
            LoggerContext.currentContext().saveCounters(scope, SAVED_SIZE_BREAKDOWN_KEYS);
            super.onCloseHandler(s, metrics);
        }

        @Override
        protected void collectMetrics() {
            String jsSource = codeBuffer.getCode();
            long additionalSize = addAdditionBreakdownKeysToScope();

            MetricKey[] injectedKeys = {
                            CONSTANT_INITS_SIZE,
                            CONSTANT_DEFS_SIZE,
                            TYPE_DECLARATIONS_SIZE,
                            STATIC_FIELDS_SIZE,
                            INITIAL_DEFINITIONS_SIZE,
                            EXTRA_DEFINITIONS_SIZE,
            };

            EconomicMap<String, MetricKey> metricMap = EconomicMap.create(injectedKeys.length);

            for (MetricKey key : injectedKeys) {
                assert !metricMap.containsKey(key.getName()) : "Duplicate key = " + key.getName();
                metricMap.put(key.getName(), key);
            }

            /*
             * Force metrics to be present in the scope, since there may be a situation when there
             * isn't every reconstruction type.
             */
            MetricsUtil.forceMetricExistence(LoggerContext.currentContext().currentScope(), RECONSTRUCTED_SIZE, NO_CF_SIZE);

            long imageLabelsSize = labeler.getSizeBetweenMetricLabels(jsSource, (key, size) -> {
                if (key instanceof String label) {
                    MetricKey metricKey = metricMap.get(label);
                    if (metricKey != null) {
                        LoggerContext.counter(metricKey).add(size);
                    }
                } else if (key instanceof HostedMethod m) {
                    MetricKey metricKey = switch (methodMetricsCollector.getMethodReconstructionType(m)) {
                        case RECONSTRUCTED -> RECONSTRUCTED_SIZE;
                        case NO_CONTROL_FLOW -> NO_CF_SIZE;
                    };
                    LoggerContext.counter(TOTAL_METHOD_SIZE).add(size);
                    LoggerContext.counter(metricKey).add(size);
                }
            });

            final long totalConstantInitSize = LoggerContext.counter(CONSTANT_INITS_SIZE).get();
            final long constantDefSize = LoggerContext.counter(CONSTANT_DEFS_SIZE).get();
            final long methodSize = LoggerContext.counter(TOTAL_METHOD_SIZE).get();

            long jsFileSize = jsSource.length() - imageLabelsSize;

            /*
             * Deduct method size from type declaration size.
             */
            LoggerContext.counter(TYPE_DECLARATIONS_SIZE).add(-methodSize);

            LoggerContext.counter(CONSTANTS_SIZE).add(totalConstantInitSize + constantDefSize);
            LoggerContext.counter(ENTIRE_IMAGE_SIZE).add(jsFileSize + additionalSize);
            LoggerContext.counter(JS_IMAGE_SIZE).add(jsFileSize);
        }
    }
}
