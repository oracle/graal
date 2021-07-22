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

import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Value;

class Config {
    String path;
    String className;
    int warmupIterations;
    int iterations;
    Mode mode;
    Metric metric;
    List<String> unrecognizedArguments = new ArrayList<>();

    private static final int DEFAULT = -1;

    private static final int DEFAULT_WARMUP = 20;
    private static final int DEFAULT_ITERATIONS = 30;

    Config() {
        this.path = null;
        this.className = null;
        this.warmupIterations = DEFAULT;
        this.iterations = DEFAULT;
        this.mode = Mode.standard;
        this.metric = new PeakTimeMetric();
    }

    public void parseBenchSpecificDefaults(Value benchmark) {
        if (warmupIterations == DEFAULT) {
            if (benchmark.hasMember("warmupIterations")) {
                warmupIterations = benchmark.getMember("warmupIterations").asInt();
            } else {
                warmupIterations = DEFAULT_WARMUP;
            }
        }
        if (iterations == DEFAULT) {
            if (benchmark.hasMember("iterations")) {
                iterations = benchmark.getMember("iterations").asInt();
            } else {
                iterations = DEFAULT_ITERATIONS;
            }
        }
    }

    @Override
    public String toString() {
        return "execution-mode:    " + mode + "\n" +
                        "metric:            " + metric.name() + " (" + metric.unit() + ")" + "\n" +
                        "warmup-iterations: " + (warmupIterations == DEFAULT ? "default" : warmupIterations) + "\n" +
                        "iterations:        " + (iterations == DEFAULT ? "default" : iterations);
    }

    enum Mode {
        interpreter,
        standard;

        static Mode parse(String name) throws IllegalArgumentException {
            for (Mode mode : Mode.values()) {
                if (name.equals(mode.name())) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown execution-mode: " + name);
        }

    }

}
