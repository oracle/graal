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
package com.oracle.truffle.tools.warmup.impl;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import java.io.PrintStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ResultsPrinter {

    private static final String DOUBLE_FORMAT = "%-15s: %f\n";
    private static final String LONG_FORMAT = "%-15s: %d\n";
    private final Results results;
    private final PrintStream stream;

    ResultsPrinter(Results results, PrintStream stream) {
        this.results = results;
        this.stream = stream;
    }

    void printSimpleResults() {
        stream.printf(LONG_FORMAT, "Best time", results.bestT);
        stream.printf(LONG_FORMAT, "Best iter", results.bestI);
        stream.printf(DOUBLE_FORMAT, "Epsilon", results.epsilon);
        stream.printf(LONG_FORMAT, "Peak Start Iter", results.peakStartI);
        stream.printf(LONG_FORMAT, "Peak Start Time", results.peakStartT);
        stream.printf(LONG_FORMAT, "Warmup time", results.warmupTime);
        stream.printf(DOUBLE_FORMAT, "Warmup cost", results.warmupCost);
        stream.printf(LONG_FORMAT, "Iterations", results.samples.size());
    }

    void printJsonResults() {
        JSONObject result = new JSONObject();
        result.put("best_time", results.bestT);
        result.put("best_iteration", results.bestI);
        result.put("peak_start_iteration", results.peakStartI);
        result.put("peak_start_time", results.peakStartT);
        result.put("warmup_time", results.warmupTime);
        result.put("warmup_cost", results.warmupCost);
        result.put("epsilon", results.epsilon);
        result.put("iterations", results.samples.size());
        result.put("samples", new JSONArray(results.samples));
        result.put("normalized_samples", new JSONArray(results.samples.stream().map(each -> (double) each / results.bestT).collect(Collectors.toList())));
        stream.print(result.toString(2));
    }

    void printRawResults() {
        final JSONArray array = new JSONArray(results.samples);
        stream.print(array.toString());
    }
}
