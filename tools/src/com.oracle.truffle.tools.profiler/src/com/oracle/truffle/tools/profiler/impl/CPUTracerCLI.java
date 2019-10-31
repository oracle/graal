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

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.profiler.CPUTracer;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Option.Group(CPUTracerInstrument.ID)
class CPUTracerCLI extends ProfilerCLI {

    enum Output {
        HISTOGRAM,
        JSON,
    }

    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Output",
                    new Function<String, Output>() {
                        @Override
                        public Output apply(String s) {
                            try {
                                return Output.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("Output can be: histogram or json");
                            }
                        }
                    });

    @Option(name = "", help = "Enable the CPU tracer (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    @Option(name = "TraceRoots", help = "Capture roots when tracing (default:true).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> TRACE_ROOTS = new OptionKey<>(true);

    @Option(name = "TraceStatements", help = "Capture statements when tracing (default:false).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> TRACE_STATEMENTS = new OptionKey<>(false);

    @Option(name = "TraceCalls", help = "Capture calls when tracing (default:false).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Boolean> TRACE_CALLS = new OptionKey<>(false);

    @Option(name = "TraceInternal", help = "Trace internal elements (default:false).", category = OptionCategory.INTERNAL) //
    static final OptionKey<Boolean> TRACE_INTERNAL = new OptionKey<>(false);

    @Option(name = "FilterRootName", help = "Wildcard filter for program roots. (eg. Math.*, default:*).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Object[]> FILTER_ROOT = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);

    @Option(name = "FilterFile", help = "Wildcard filter for source file paths. (eg. *program*.sl, default:*).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Object[]> FILTER_FILE = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);

    @Option(name = "FilterMimeType", help = "Only profile languages with mime-type. (eg. +, default:no filter).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> FILTER_MIME_TYPE = new OptionKey<>("");

    @Option(name = "FilterLanguage", help = "Only profile languages with given ID. (eg. js, default:no filter).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");

    @Option(name = "Output", help = "Print a 'histogram' or 'json' as output (default:HISTOGRAM).", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.HISTOGRAM, CLI_OUTPUT_TYPE);

    @Option(name = "OutputFile", help = "Save output to the given file. Output is printed to output stream by default.", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<String> OUTPUT_FILE = new OptionKey<>("");

    public static void handleOutput(TruffleInstrument.Env env, CPUTracer tracer) {
        try (PrintStream out = chooseOutputStream(env, OUTPUT_FILE)) {
            switch (env.getOptions().get(OUTPUT)) {
                case HISTOGRAM:
                    printTracerHistogram(out, tracer);
                    break;
                case JSON:
                    printTracerJson(out, tracer);
                    break;
            }
        }
    }

    private static void printTracerJson(PrintStream out, CPUTracer tracer) {
        JSONObject output = new JSONObject();
        output.put("tool", CPUTracerInstrument.ID);
        output.put("version", CPUTracerInstrument.VERSION);
        List<CPUTracer.Payload> payloads = new ArrayList<>(tracer.getPayloads());
        JSONArray profile = new JSONArray();
        for (CPUTracer.Payload payload : payloads) {
            JSONObject entry = new JSONObject();
            entry.put("root_name", payload.getRootName());
            entry.put("source_section", sourceSectionToJSON(payload.getSourceSection()));
            entry.put("count", payload.getCount());
            entry.put("interpreted_count", payload.getCountInterpreted());
            entry.put("compiled_count", payload.getCountCompiled());
            profile.put(entry);
        }
        output.put("profile", profile);
        out.println(output.toString());
    }

    static void printTracerHistogram(PrintStream out, CPUTracer tracer) {
        List<CPUTracer.Payload> payloads = new ArrayList<>(tracer.getPayloads());
        payloads.sort(new Comparator<CPUTracer.Payload>() {
            @Override
            public int compare(CPUTracer.Payload o1, CPUTracer.Payload o2) {
                return Long.compare(o2.getCount(), o1.getCount());
            }
        });
        int length = computeNameLength(payloads, 50);
        String format = " %-" + length + "s | %20s | %20s | %20s | %s";
        String title = String.format(format, "Name", "Total Count", "Interpreted Count", "Compiled Count", "Location");
        String sep = repeat("-", title.length());
        long totalCount = 0;
        for (CPUTracer.Payload payload : payloads) {
            totalCount += payload.getCount();
        }

        out.println(sep);
        out.println(String.format("Tracing Histogram. Counted a total of %d element executions.", totalCount));
        out.println("  Total Count: Number of times the element was executed and percentage of total executions.");
        out.println("  Interpreted Count: Number of times the element was interpreted and percentage of total executions of this element.");
        out.println("  Compiled Count: Number of times the compiled element was executed and percentage of total executions of this element.");
        out.println(sep);

        out.println(title);
        out.println(sep);
        for (CPUTracer.Payload payload : payloads) {
            String total = String.format("%d %5.1f%%", payload.getCount(), (double) payload.getCount() * 100 / totalCount);
            String interpreted = String.format("%d %5.1f%%", payload.getCountInterpreted(), (double) payload.getCountInterpreted() * 100 / payload.getCount());
            String compiled = String.format("%d %5.1f%%", payload.getCountCompiled(), (double) payload.getCountCompiled() * 100 / payload.getCount());
            out.println(String.format(format, payload.getRootName(), total, interpreted, compiled, getShortDescription(payload.getSourceSection())));
        }
        out.println(sep);
    }

    private static int computeNameLength(Collection<CPUTracer.Payload> payloads, int limit) {
        int maxLength = 6;
        for (CPUTracer.Payload payload : payloads) {
            int rootNameLength = payload.getRootName().length();
            maxLength = Math.max(rootNameLength + 2, maxLength);
            maxLength = Math.min(maxLength, limit);
        }
        return maxLength;
    }
}
