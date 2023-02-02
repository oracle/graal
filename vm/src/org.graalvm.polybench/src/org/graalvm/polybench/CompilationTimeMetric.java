/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

final class CompilationTimeMetric extends Metric {

    enum MetricType {
        COMPILATION,
        PARTIAL_EVALUATION;
    }

    private final TraceCompilationHandler logHandler;

    CompilationTimeMetric(MetricType metricType) {
        this.logHandler = new TraceCompilationHandler(metricType);
    }

    @Override
    public void validateConfig(Config config, Map<String, String> polyglotOptions) {
        if (Boolean.parseBoolean(polyglotOptions.get("engine.BackgroundCompilation"))) {
            throw new IllegalStateException("The Compile Time Metric cannot be used with a background compilation.\n" +
                            "Remove the 'engine.BackgroundCompilation=true' option.");
        }
        if (polyglotOptions.containsKey("engine.TraceCompilation") && !Boolean.parseBoolean(polyglotOptions.get("engine.TraceCompilation"))) {
            throw new IllegalStateException("The Compile Time Metric cannot be used without TraceCompilation.\n" +
                            "Remove the 'engine.TraceCompilation=false' option.");
        }
    }

    @Override
    public Map<String, String> getEngineOptions(Config config) {
        Map<String, String> options = new HashMap<>();
        options.put("engine.BackgroundCompilation", "false");
        options.put("engine.TraceCompilation", "true");
        return options;
    }

    @Override
    public Handler getLogHandler() {
        return logHandler;
    }

    @Override
    public String unit() {
        return "ms";
    }

    @Override
    public String name() {
        switch (this.logHandler.metricType) {
            case COMPILATION:
                return "compilation time";
            case PARTIAL_EVALUATION:
                return "partial evaluation time";
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        logHandler.startIteration();
    }

    @Override
    public void afterIteration(boolean warmup, int iteration, Config config) {
        logHandler.endIteration();
    }

    @Override
    public void reset() {
        logHandler.reset();
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return logHandler.iterationTime();
    }

    @Override
    public Optional<Double> reportAfterAll() {
        return logHandler.allIterationsTime();
    }

    private static final class TraceCompilationHandler extends Handler {

        private final MetricType metricType;
        private double allIterationsTime;
        private final DoubleAdder iterationTime = new DoubleAdder();

        TraceCompilationHandler(MetricType metricType) {
            this.metricType = metricType;
        }

        @Override
        public void publish(LogRecord record) {
            if ("engine".equals(record.getLoggerName())) {
                String message = record.getMessage();
                if (message.startsWith("opt done") || message.startsWith("opt failed")) {
                    double time = parseTime(message, metricType);
                    if (!Double.isNaN(time)) {
                        iterationTime.add(time);
                    } else {
                        throw new IllegalStateException("Failed to parse '" + message + "'");
                    }
                }
            }
        }

        private static double parseTime(String str, MetricType type) {
            String timePattern = "|Time";
            int start = str.indexOf(timePattern);
            if (start < 0) {
                return Double.NaN;
            }
            start += timePattern.length();
            int openBraceIndex = str.indexOf('(', start);
            if (openBraceIndex < 0) {
                return Double.NaN;
            }
            switch (type) {
                case COMPILATION:
                    return Double.parseDouble(str.substring(start, openBraceIndex).trim());
                case PARTIAL_EVALUATION:
                    start = openBraceIndex + 1;
                    int end = str.indexOf('+', start);
                    if (end < 0) {
                        return Double.NaN;
                    }
                    return Double.parseDouble(str.substring(start, end).trim());
                default:
                    throw new IllegalArgumentException(type.name());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        void startIteration() {
            iterationTime.reset();
        }

        void endIteration() {
            allIterationsTime += iterationTime.doubleValue();
        }

        void reset() {
            iterationTime.reset();
            // allIterationsTime not reset here since metric is reset between warmup and running and
            // we want to report all the iteration times
        }

        Optional<Double> iterationTime() {
            return Optional.of(iterationTime.doubleValue());
        }

        Optional<Double> allIterationsTime() {
            return Optional.of(allIterationsTime);
        }
    }
}
