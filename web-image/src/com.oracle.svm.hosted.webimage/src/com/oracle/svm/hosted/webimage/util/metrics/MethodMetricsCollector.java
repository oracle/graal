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

import static com.oracle.svm.hosted.webimage.util.metrics.MethodMetricsCollector.ReconstructionType.NO_CONTROL_FLOW;
import static com.oracle.svm.hosted.webimage.util.metrics.MethodMetricsCollector.ReconstructionType.RECONSTRUCTED;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.logging.LoggableMetric;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.logging.LoggerScope;
import com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys;
import com.oracle.svm.hosted.webimage.metrickeys.MethodMetricKeys;
import com.oracle.svm.hosted.webimage.metrickeys.StackifierMetricKeys;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MetricKey;

/**
 * A utility class used to gather up method statistics and statistics from control-flow
 * reconstruction that are tracked through Logging API ({@link LoggerScope}). This class collects
 * statistics tracked per-method and summarizes them in the outer(parent) {@link LoggerScope} and
 * determines {@link ReconstructionType} for each lowered method.<br>
 * <br>
 * Example:
 *
 * <pre>
 * try (MethodMetricsCollector collector = methodMetricsCollector.collect(method)) {
 *     processMethod(method);
 * }
 * </pre>
 */
public class MethodMetricsCollector {
    public enum ReconstructionType {
        RECONSTRUCTED,
        NO_CONTROL_FLOW
    }

    private final JSCodeBuffer codeBuffer;
    private final EconomicMap<HostedMethod, ReconstructionType> methodReconstructionMap;

    private final EconomicMap<HostedMethod, UnmodifiableEconomicMap<MetricKey, Number>> methodMetrics;

    public MethodMetricsCollector(JSCodeBuffer codeBuffer) {
        this.codeBuffer = codeBuffer;
        methodReconstructionMap = EconomicMap.create();
        methodMetrics = EconomicMap.create();
    }

    /**
     * Starts tracking of method metrics. Upon closing, adds values found to respective metrics
     * found in parent {@link LoggerScope}.
     *
     * @param m method for which metrics are tracked.
     */
    public Collector collect(HostedMethod m) {
        return new Collector(m);
    }

    public ReconstructionType getMethodReconstructionType(HostedMethod method) {
        return methodReconstructionMap.get(method);
    }

    public Number getMethodMetric(HostedMethod method, MetricKey key) {
        return methodMetrics.get(method).get(key);
    }

    /*
     * TODO GR-35863: This does nothing because the stackifier is applied during the high tier in
     * the compile queue and not during lowering, i.e. the metrics below will always be 0
     */
    private static void stackifierMetricAccumulation(LoggerScope parent, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
        addOrDefault(parent, metrics, StackifierMetricKeys.NUM_ELSE_SCOPES);
        addOrDefault(parent, metrics, StackifierMetricKeys.NUM_THEN_SCOPES);
        addOrDefault(parent, metrics, StackifierMetricKeys.NUM_FORWARD_BLOCKS);
        addOrDefault(parent, metrics, StackifierMetricKeys.NUM_LOOP_SCOPES);
    }

    /**
     * Adds to the parent {@link LoggerScope} value found in <code>metrics</code> map under
     * <metrics>key</metrics>, or 0 if there is no value associated with the given <code>key</code>.
     *
     * @param parent parent {@link LoggerScope}
     * @param metrics map of {@link LoggableMetric}s
     * @param key key that is used both in <code>parent</code> and <code>metrics</code>
     */
    private static void addOrDefault(LoggerScope parent, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics, MetricKey key) {
        parent.counter(key).add(MetricsUtil.getOrDefault(metrics, key));
    }

    /**
     * A collector class that opens a logging scope for tracking per-method statistics. Upon
     * closing, method's size is calculated and added as a metric and all statistics that were
     * tracked during method processing are added to the parent {@link LoggerScope}. Additionally,
     * this class determines reconstruction type of the method and stores it inside of enclosing
     * class.
     */
    public class Collector implements AutoCloseable {
        private final HostedMethod method;
        private final LoggerScope loggerScope;
        private final CodeSizeCollector codeSizeCollector;

        protected Collector(HostedMethod method) {
            this.method = method;
            loggerScope = LoggerContext.currentContext().scope(method, this::onCloseHandler);
            this.codeSizeCollector = new CodeSizeCollector(MethodMetricKeys.METHOD_SIZE, codeBuffer::codeSize);
        }

        private void setMethodReconstructionType() {
            if (LoggerContext.counter(MethodMetricKeys.NUM_SPLITS).get() > 0) {
                methodReconstructionMap.put(method, RECONSTRUCTED);
            } else {
                methodReconstructionMap.put(method, NO_CONTROL_FLOW);
            }
        }

        /**
         * A method that gets called when {@link LoggerScope} method gets closed. This method
         * collects data tracked in the {@link LoggerScope} and accumulates values.
         *
         * @param scope current logger scope
         * @param metrics metrics collected in current scope
         */
        private void onCloseHandler(LoggerScope scope, UnmodifiableEconomicMap<MetricKey, LoggableMetric> metrics) {
            LoggerScope parent = scope.parent();

            // Save a copy of the metric values
            methodMetrics.put(method, scope.getExtractedMetrics());

            /*
             * We accumulate these per-method metrics to the parent scope.
             */
            addOrDefault(parent, metrics, MethodMetricKeys.NUM_BLOCKS);
            /*
             * TODO GR-35863: This does nothing because the metrics are logged during the high tier
             * in the compile queue and not during lowering, i.e. these will always be 0
             */
            addOrDefault(parent, metrics, MethodMetricKeys.NUM_COMPOUND_COND_XOOY);
            addOrDefault(parent, metrics, MethodMetricKeys.NUM_COMPOUND_COND_XAAY);

            /*
             * Force Control-Flow metrics to be present in the parent scope, since they may not be
             * logged by the counter method (e.g. because there are no NO_CF methods (highly
             * unlikely)).
             */
            MetricsUtil.forceMetricExistence(parent, MethodMetricKeys.NUM_RECONSTRUCTED, MethodMetricKeys.NUM_NO_CF);

            MetricKey sizeAccumulator;
            MetricKey counter;
            ReconstructionType reconstructionType = methodReconstructionMap.get(method);
            switch (reconstructionType) {
                case RECONSTRUCTED -> {
                    sizeAccumulator = ImageBreakdownMetricKeys.RECONSTRUCTED_SIZE;
                    counter = MethodMetricKeys.NUM_RECONSTRUCTED;
                }
                case NO_CONTROL_FLOW -> {
                    sizeAccumulator = ImageBreakdownMetricKeys.NO_CF_SIZE;
                    counter = MethodMetricKeys.NUM_NO_CF;
                }
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(reconstructionType);
            }

            long methodSize = metrics.get(MethodMetricKeys.METHOD_SIZE).get();
            parent.counter(ImageBreakdownMetricKeys.TOTAL_METHOD_SIZE).add(methodSize);
            parent.counter(sizeAccumulator).add(methodSize);
            parent.counter(counter).increment();

            stackifierMetricAccumulation(parent, metrics);

            /*
             * All the other metrics we just propagate.
             */
            MetricsUtil.propagateMetricsExcept(parent, metrics, MethodMetricKeys.class);
        }

        @Override
        public void close() {
            /*
             * This needs to be done before closing the scope, so that the METHOD_SIZE metric can be
             * accessed in the OnCloseHandler.
             */
            codeSizeCollector.close();
            setMethodReconstructionType();
            loggerScope.close();
        }
    }
}
