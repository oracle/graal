/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;

import org.graalvm.polyglot.Value;

/**
 * Includes all the logic required to measure a particular value during an execution of a program.
 */
@SuppressWarnings("unused")
public abstract class Metric {

    public Metric() {
        String className = this.getClass().getName();
        if (!className.endsWith("Metric")) {
            throw new IllegalStateException("Plugin metrics should have class names that end with 'Metric'.");
        }
    }

    /**
     * Name of the metric, should be unique among implementations.
     */
    public String name() {
        String className = this.getClass().getSimpleName();
        return MetricFactory.metricNameFor(className);
    }

    /**
     * Unit associated with the measurement values.
     */
    public String unit() {
        return "n/a";
    }

    /**
     * Validates the mode and polyglot options parsed from the command line.
     *
     * @throws IllegalStateException when the mode or polyglot options cannot be used with the
     *             metric.
     */
    public void validateConfig(Config config, Map<String, String> polyglotOptions) {
    }

    /**
     * Returns engine options required by the {@link Metric}. The returned options are set on the
     * polyglot context.
     */
    public Map<String, String> getEngineOptions(Config config) {
        return Collections.emptyMap();
    }

    /**
     * Allows Metric to forward engine logging into supplied logger.
     */
    public Handler getLogHandler() {
        return null;
    }

    public void parseBenchSpecificOptions(Value runner) {
    }

    /**
     * Invoked before the language context is initialized. This is guaranteed to happen before
     * {@link #afterLoad(Config) loading}.
     */
    public void beforeInitialize(Config config) {
    }

    /**
     * Invoked after the language context is initialized. This is guaranteed to happen before
     * {@link #afterLoad(Config) loading}.
     */
    public void afterInitialize(Config config) {
    }

    /**
     * Invoked before the benchmark is loaded. That means just before source code is parsed or
     * classes are loaded. This is guaranteed to happen after initialization, but before the first
     * iteration.
     */
    public void beforeLoad(Config config) {
    }

    /**
     * Invoked after the benchmark is loaded. That means just after source code is parsed or classes
     * are loaded. This is guaranteed to happen after initialization, but before the first
     * iteration.
     */
    public void afterLoad(Config config) {
    }

    public void beforeIteration(boolean warmup, int iteration, Config config) {
    }

    public void afterIteration(boolean warmup, int iteration, Config config) {
    }

    public Optional<Double> reportAfterIteration(Config config) {
        return Optional.empty();
    }

    public Optional<Double> reportAfterAll() {
        return Optional.empty();
    }

    public void reset() {
    }

}
