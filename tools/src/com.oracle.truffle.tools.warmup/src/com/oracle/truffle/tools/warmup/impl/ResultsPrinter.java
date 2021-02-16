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
import java.util.List;
import java.util.stream.Collectors;

class ResultsPrinter {

    private static final String FORMAT = "[warmup estimator] %-25s | %-15s | ";
    private static final String DOUBLE_FORMAT = FORMAT + "%f\n";
    private static final String LONG_FORMAT = FORMAT + "%d\n";
    private final List<Results> resultsList;
    private final PrintStream stream;

    ResultsPrinter(List<Results> resultsList, PrintStream stream) {
        this.resultsList = resultsList;
        this.stream = stream;
    }

    void printSimpleResults() {
        for (Results results : resultsList) {
            stream.printf(LONG_FORMAT, results.location, "Best time", results.bestT);
            stream.printf(LONG_FORMAT, results.location, "Best iter", results.bestI);
            stream.printf(DOUBLE_FORMAT, results.location, "Epsilon", results.epsilon);
            stream.printf(LONG_FORMAT, results.location, "Peak Start Iter", results.peakStartI);
            stream.printf(LONG_FORMAT, results.location, "Peak Start Time", results.peakStartT);
            stream.printf(LONG_FORMAT, results.location, "Warmup time", results.warmupTime);
            stream.printf(DOUBLE_FORMAT, results.location, "Warmup cost", results.warmupCost);
            stream.printf(LONG_FORMAT, results.location, "Iterations", results.samples.size());
        }
    }

    void printJsonResults() {
        JSONArray output = new JSONArray();
        for (Results results : resultsList) {
            JSONObject jsonResults = new JSONObject();
            jsonResults.put("location", results.location);
            jsonResults.put("best_time", results.bestT);
            jsonResults.put("best_iteration", results.bestI);
            jsonResults.put("peak_start_iteration", results.peakStartI);
            jsonResults.put("peak_start_time", results.peakStartT);
            jsonResults.put("warmup_time", results.warmupTime);
            jsonResults.put("warmup_cost", results.warmupCost);
            jsonResults.put("epsilon", results.epsilon);
            jsonResults.put("iterations", results.samples.size());
            jsonResults.put("samples", new JSONArray(results.samples));
            jsonResults.put("normalized_samples", new JSONArray(results.samples.stream().map(each -> (double) each / results.bestT).collect(Collectors.toList())));
            output.put(jsonResults);
        }
        stream.print(output.toString(2));
    }

    void printRawResults() {
        JSONArray output = new JSONArray();
        for (Results results : resultsList) {
            JSONObject jsonResults = new JSONObject();
            jsonResults.put("location", results.location);
            jsonResults.put("samples", new JSONArray(results.samples));
            output.put(jsonResults);
        }
        stream.print(output);
    }
}
