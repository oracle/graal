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

import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Config {

    public static final String EVAL_SOURCE_ONLY_OPTION = "--eval-source-only";

    String path;
    String className;
    int warmupIterations;
    int iterations;
    Metric metric;
    boolean evalSourceOnlyDefault;

    final List<String> unrecognizedArguments = new ArrayList<>();

    private static final int UNINITIALIZED_ITERATIONS = -1;
    private static final int DEFAULT_WARMUP = 20;
    private static final int DEFAULT_ITERATIONS = 30;

    /**
     * Multi-context runs related configuration.
     */
    MultiEngineConfig multiEngine;

    Config() {
        this.path = null;
        this.className = null;
        this.warmupIterations = UNINITIALIZED_ITERATIONS;
        this.iterations = UNINITIALIZED_ITERATIONS;
        this.metric = new PeakTimeMetric();
    }

    /**
     * Polybench options that can be specified per run.
     */
    private static final Set<String> POLYBENCH_RUN_OPTIONS = new HashSet<>();
    static {
        POLYBENCH_RUN_OPTIONS.add(EVAL_SOURCE_ONLY_OPTION);
    }

    public MultiEngineConfig initMultiEngine() {
        if (multiEngine == null) {
            multiEngine = new MultiEngineConfig();
        }
        return multiEngine;
    }

    public void parseBenchSpecificDefaults(Value benchmark) {
        if (warmupIterations == UNINITIALIZED_ITERATIONS) {
            if (benchmark.hasMember("warmupIterations")) {
                Value warmupIterationsMember = benchmark.getMember("warmupIterations");
                warmupIterations = warmupIterationsMember.canExecute() ? warmupIterationsMember.execute().asInt() : warmupIterationsMember.asInt();
            } else {
                warmupIterations = DEFAULT_WARMUP;
            }
        }
        if (iterations == UNINITIALIZED_ITERATIONS) {
            if (benchmark.hasMember("iterations")) {
                Value iterationsMember = benchmark.getMember("iterations");
                iterations = iterationsMember.canExecute() ? iterationsMember.execute().asInt() : iterationsMember.asInt();
            } else {
                iterations = DEFAULT_ITERATIONS;
            }
        }
    }

    @Override
    public String toString() {
        String config = "metric:            " + metric.name() + " (" + metric.unit() + ")" + "\n" +
                        // This output is used by external tools to extract the metric name
                        "metric class:      " + metric.getClass().getSimpleName() + "\n" +
                        "warmup-iterations: " + (warmupIterations == UNINITIALIZED_ITERATIONS ? "default" : warmupIterations) + "\n" +
                        "iterations:        " + (iterations == UNINITIALIZED_ITERATIONS ? "default" : iterations + "\n");
        if (multiEngine != null) {
            config += "runs:              " + multiEngine.numberOfRuns + "\n" +
                            "shared engine:     " + multiEngine.sharedEngine;
        }
        return config;
    }

    boolean isSingleEngine() {
        return multiEngine == null;
    }

    static boolean isPolybenchRunOption(String optionName) {
        return POLYBENCH_RUN_OPTIONS.contains(optionName);
    }

    boolean isEvalSourceOnly(int run) {
        if (multiEngine == null) {
            return evalSourceOnlyDefault;
        }
        String evalSourceOptionValue = multiEngine.polybenchRunOptionsMap.getOrDefault(run, Collections.emptyMap()).get(EVAL_SOURCE_ONLY_OPTION);
        return evalSourceOptionValue == null ? evalSourceOnlyDefault : Boolean.parseBoolean(evalSourceOptionValue);
    }

    static final class MultiEngineConfig {
        final Map<String, String> engineOptions = new HashMap<>();
        final Map<Integer, Map<String, String>> polyglotRunOptionsMap = new HashMap<>();
        final Map<Integer, Map<String, String>> polybenchRunOptionsMap = new HashMap<>();
        int numberOfRuns = 1;
        boolean sharedEngine;

    }

}
