/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

class WarmupEstimatorNode extends ExecutionEventNode {

    private final double epsilon;
    private long start;
    private List<Long> samples = new ArrayList<>();
    private static final String DOUBLE_FORMAT = "%-15s: %f\n";
    private static final String LONG_FORMAT = "%-15s: %d\n";

    WarmupEstimatorNode(double epsilon) {
        this.epsilon = epsilon;
    }

    Results getResults() {
        return new Results(samples, epsilon);
    }
    void printSimpleResults(PrintStream out) {
        final Results results = getResults();
        out.printf(LONG_FORMAT, "Peak", results.peak);
        out.printf(LONG_FORMAT, "Peak Start", results.peakStart);
        out.printf(LONG_FORMAT, "Warmup time", results.warmupTime);
        out.printf(DOUBLE_FORMAT, "Warmup cost", results.warmupCost);
        out.printf(LONG_FORMAT, "Iterations", results.samples.size());
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        start = System.nanoTime();
    }

    @Override
    protected void onReturnValue(VirtualFrame frame, Object result) {
        final long duration = System.nanoTime() - start;
        samples.add(duration);
    }

    void printJsonResults(PrintStream printStream) {
        final Results results = getResults();
        JSONObject result = new JSONObject();
        result.put("peak", results.peak);
        result.put("peak_start", results.peakStart);
        result.put("warmup_time", results.warmupTime);
        result.put("warmup_cost", results.warmupCost);
        result.put("iterations", samples.size());
        result.put("samples", new JSONArray(samples));
        result.put("normalized_samples", new JSONArray(samples.stream().map(each -> (double) each / results.peak).collect(Collectors.toList())));
        printStream.print(result.toString(2));
    }

    class Results {

        private final List<Long> samples;
        private final long peak;
        private final double epsilon;
        private final int peakStart;
        private final long warmupTime;
        private final double warmupCost;

        Results(List<Long> samples, double epsilon) {
            this.samples = samples;
            this.epsilon = epsilon;
            peak = peak();
            peakStart = peakStart();
            warmupTime = warmupTime();
            warmupCost = (double) warmupTime / peak;

        }

        private Long peak() {
            return samples.stream().min(Long::compareTo).get();
        }

        private int peakStart() {
            for (int i = 0; i < samples.size(); i++) {
                if (samples.get(i) < peak * (1 + epsilon)) {
                    return i;
                }
            }
            throw new AssertionError("Should not reach here.");
        }

        private long warmupTime() {
            long warmup = 0;
            for (int i = 0; i < peakStart; i++) {
                warmup += samples.get(i) - peak;
            }
            return warmup;
        }
    }
}
