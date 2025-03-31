/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * This metric collects the maximum context heap used during the computation of the benchmark. The
 * metric uses the number of thread allocated bytes to trigger the context heap computation
 * regularly on a timer thread. The maximum context heap used will be reported for each iteration.
 *
 * This metric is currently only supported on JVM.
 *
 * This metric might be too slow for large heaps (>10GB). Use VisualVM or other memory inspection
 * tools for debugging regressions measured by this metric.
 */
public final class ApplicationMemoryMetric extends Metric {

    @Override
    public String unit() {
        return "B";
    }

    @Override
    public Map<String, String> getEngineOptions(Config config) {
        HashMap<String, String> map = new HashMap<>();
        map.put("memory-usage", "true");
        return map;
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        Context.getCurrent().getPolyglotBindings().getMember("startContextMemoryTracking").executeVoid();
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        Value result = Context.getCurrent().getPolyglotBindings().getMember("stopContextMemoryTracking").execute();
        long value = result.getMember("contextHeapMax").asLong();
        return Optional.of((double) value);
    }

}
