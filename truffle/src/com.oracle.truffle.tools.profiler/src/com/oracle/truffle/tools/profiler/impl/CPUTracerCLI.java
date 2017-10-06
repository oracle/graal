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
package com.oracle.truffle.tools.profiler.impl;

import com.oracle.truffle.tools.profiler.CPUTracer;
import org.graalvm.options.OptionKey;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class CPUTracerCLI extends ProfilerCLI {
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    static final OptionKey<Boolean> TRACE_ROOTS = new OptionKey<>(true);
    static final OptionKey<Boolean> TRACE_STATEMENTS = new OptionKey<>(false);
    static final OptionKey<Boolean> TRACE_CALLS = new OptionKey<>(false);
    static final OptionKey<Boolean> TRACE_INTERNAL = new OptionKey<>(false);
    static final OptionKey<Object[]> FILTER_ROOT = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);
    static final OptionKey<Object[]> FILTER_FILE = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);
    static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");

    static void printTracerHistogram(PrintStream out, CPUTracer tracer) {
        List<CPUTracer.Counter> counters = new ArrayList<>(tracer.getCounters());
        counters.sort(new Comparator<CPUTracer.Counter>() {
            @Override
            public int compare(CPUTracer.Counter o1, CPUTracer.Counter o2) {
                return Long.compare(o2.getCount(), o1.getCount());
            }
        });
        int length = computeNameLength(counters, 50);
        String format = " %-" + length + "s | %20s | %20s | %20s | %s";
        String title = String.format(format, "Name", "Total Count", "Interpreted Count", "Compiled Count", "Location");
        String sep = repeat("-", title.length());
        long totalCount = 0;
        for (CPUTracer.Counter counter : counters) {
            totalCount += counter.getCount();
        }

        out.println(sep);
        out.println(String.format("Tracing Histogram. Counted a total of %d element executions.", totalCount));
        out.println("  Total Count: Number of times the element was executed and percentage of total executions.");
        out.println("  Interpreted Count: Number of times the element was interpreted and percentage of total executions of this element.");
        out.println("  Compiled Count: Number of times the compiled element was executed and percentage of total executions of this element.");
        out.println(sep);

        out.println(title);
        out.println(sep);
        for (CPUTracer.Counter counter : counters) {
            String total = String.format("%d %5.1f%%", counter.getCount(), (double) counter.getCount() * 100 / totalCount);
            String interpreted = String.format("%d %5.1f%%", counter.getCountInterpreted(), (double) counter.getCountInterpreted() * 100 / counter.getCount());
            String compiled = String.format("%d %5.1f%%", counter.getCountCompiled(), (double) counter.getCountCompiled() * 100 / counter.getCount());
            out.println(String.format(format, counter.getRootName(), total, interpreted, compiled, getShortDescription(counter.getSourceSection())));
        }
        out.println(sep);
    }

    private static int computeNameLength(Collection<CPUTracer.Counter> counters, int limit) {
        int maxLength = 6;
        for (CPUTracer.Counter counter : counters) {
            int rootNameLength = counter.getRootName().length();
            maxLength = Math.max(rootNameLength + 2, maxLength);
            maxLength = Math.min(maxLength, limit);
        }
        return maxLength;
    }
}