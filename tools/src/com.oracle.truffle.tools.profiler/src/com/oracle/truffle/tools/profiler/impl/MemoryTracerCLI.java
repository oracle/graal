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
import com.oracle.truffle.tools.profiler.MemoryTracer;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Option.Group(MemoryTracerInstrument.ID)
class MemoryTracerCLI extends ProfilerCLI {

    enum Output {
        TYPE_HISTOGRAM,
        LOCATION_HISTOGRAM,
        CALLTREE
    }

    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Format",
                    new Function<String, Output>() {
                        @Override
                        public Output apply(String s) {
                            switch (s) {
                                case "typehistogram":
                                    return Output.TYPE_HISTOGRAM;
                                case "histogram":
                                    return Output.LOCATION_HISTOGRAM;
                                case "calltree":
                                    return Output.CALLTREE;
                                default:
                                    return null;
                            }
                        }
                    },
                    new Consumer<Output>() {
                        @Override
                        public void accept(Output output) {
                            if (output == null) {
                                throw new IllegalArgumentException();
                            }
                        }
                    });

    @Option(name = "", help = "Enable the Memory Tracer (default:false).", category = OptionCategory.USER) static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    @Option(name = "Output", help = "Print a 'typehistogram', 'histogram' or 'calltree' as output (default:histogram).", category = OptionCategory.USER) static final OptionKey<Output> OUTPUT = new OptionKey<>(
                    Output.LOCATION_HISTOGRAM, CLI_OUTPUT_TYPE);

    @Option(name = "StackLimit", help = "Maximum number of maximum stack elements.", category = OptionCategory.USER) static final OptionKey<Integer> STACK_LIMIT = new OptionKey<>(10000);

    @Option(name = "TraceRoots", help = "Capture roots when tracing (default:true).", category = OptionCategory.USER) static final OptionKey<Boolean> TRACE_ROOTS = new OptionKey<>(true);

    @Option(name = "TraceStatements", help = "Capture statements when tracing (default:false).", category = OptionCategory.USER) static final OptionKey<Boolean> TRACE_STATEMENTS = new OptionKey<>(
                    false);

    @Option(name = "TraceCalls", help = "Capture calls when tracing (default:false).", category = OptionCategory.USER) static final OptionKey<Boolean> TRACE_CALLS = new OptionKey<>(false);

    @Option(name = "TraceInternal", help = "Capture internal elements (default:false).", category = OptionCategory.INTERNAL) static final OptionKey<Boolean> TRACE_INTERNAL = new OptionKey<>(false);

    @Option(name = "FilterRootName", help = "Wildcard filter for program roots. (eg. Math.*, default:*).", category = OptionCategory.USER) static final OptionKey<Object[]> FILTER_ROOT = new OptionKey<>(
                    new Object[0], WILDCARD_FILTER_TYPE);

    @Option(name = "FilterFile", help = "Wildcard filter for source file paths. (eg. *program*.sl, default:*).", category = OptionCategory.USER) static final OptionKey<Object[]> FILTER_FILE = new OptionKey<>(
                    new Object[0], WILDCARD_FILTER_TYPE);

    @Option(name = "FilterMimeType", help = "Only profile languages with mime-type. (eg. +, default:no filter).", category = OptionCategory.USER) static final OptionKey<String> FILTER_MIME_TYPE = new OptionKey<>(
                    "");

    @Option(name = "FilterLanguage", help = "Only profile languages with given ID. (eg. js, default:no filter).", category = OptionCategory.USER) static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>(
                    "");

    static void handleOutput(TruffleInstrument.Env env, MemoryTracer tracer) {
        PrintStream out = new PrintStream(env.out());
        if (tracer.hasStackOverflowed()) {
            out.println("-------------------------------------------------------------------------------- ");
            out.println("ERROR: Shadow stack has overflowed its capacity of " + env.getOptions().get(STACK_LIMIT) + " during execution!");
            out.println("The gathered data is incomplete and incorrect!");
            out.println("Use --" + MemoryTracerInstrument.ID + ".StackLimit=<" + STACK_LIMIT.getType().getName() + "> to set stack capacity.");
            out.println("-------------------------------------------------------------------------------- ");
            return;
        }
        switch (env.getOptions().get(OUTPUT)) {
            case TYPE_HISTOGRAM:
                printMetaObjectHistogram(out, tracer);
                break;
            case LOCATION_HISTOGRAM:
                printLocationHistogram(out, tracer);
                break;
            case CALLTREE:
                printCallTree(out, tracer);
                break;
        }
    }

    private static Map<String, List<MemoryTracer.AllocationEventInfo>> computeMetaObjectHistogram(MemoryTracer tracer) {
        Map<String, List<MemoryTracer.AllocationEventInfo>> histogram = new HashMap<>();
        computeMetaObjectHistogramImpl(tracer.getRootNodes(), histogram);
        return histogram;
    }

    private static void computeMetaObjectHistogramImpl(Collection<ProfilerNode<MemoryTracer.Payload>> children, Map<String, List<MemoryTracer.AllocationEventInfo>> histogram) {
        for (ProfilerNode<MemoryTracer.Payload> treeNode : children) {
            for (MemoryTracer.AllocationEventInfo info : treeNode.getPayload().getEvents()) {
                List<MemoryTracer.AllocationEventInfo> nodes = histogram.computeIfAbsent(info.getMetaObjectString(), new Function<String, List<MemoryTracer.AllocationEventInfo>>() {
                    @Override
                    public List<MemoryTracer.AllocationEventInfo> apply(String s) {
                        return new ArrayList<>();
                    }
                });
                nodes.add(info);
            }
            computeMetaObjectHistogramImpl(treeNode.getChildren(), histogram);
        }
    }

    private static void printMetaObjectHistogram(PrintStream out, MemoryTracer tracer) {
        final Map<String, List<MemoryTracer.AllocationEventInfo>> histogram = computeMetaObjectHistogram(tracer);
        final List<String> keys = new ArrayList<>(histogram.keySet());
        keys.sort(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return Integer.compare(histogram.get(o2).size(), histogram.get(o1).size());
            }
        });
        int metaObjectMax = 1;
        Iterator<String> iterator = histogram.keySet().iterator();
        while (iterator.hasNext()) {
            metaObjectMax = Math.max(metaObjectMax, iterator.next().length());
        }
        final long totalAllocations = getTotalAllocationCount(tracer);

        String format = " %-" + metaObjectMax + "s | %15s ";
        String title = String.format(format, "Type", "Count");
        String sep = repeat("-", title.length());
        out.println(sep);
        out.println(String.format(" Type Histogram with Allocation Counts. Recorded a total of %d allocations.", totalAllocations));
        out.println(sep);
        out.println(title);
        out.println(sep);
        for (String metaObjectString : keys) {
            final int allocationCount = histogram.get(metaObjectString).size();
            final String count = String.format("%d %5.1f%%", allocationCount, (double) allocationCount * 100 / totalAllocations);
            out.println(String.format(format, metaObjectString, count));
        }
        out.println(sep);
    }

    private static Map<SourceLocation, List<ProfilerNode<MemoryTracer.Payload>>> computeSourceLocationHistogram(MemoryTracer tracer) {
        Map<SourceLocation, List<ProfilerNode<MemoryTracer.Payload>>> histogram = new HashMap<>();
        computeSourceLocationHistogramImpl(tracer.getRootNodes(), histogram);
        return histogram;
    }

    private static void computeSourceLocationHistogramImpl(Collection<ProfilerNode<MemoryTracer.Payload>> children, Map<SourceLocation, List<ProfilerNode<MemoryTracer.Payload>>> histogram) {
        for (ProfilerNode<MemoryTracer.Payload> treeNode : children) {
            List<ProfilerNode<MemoryTracer.Payload>> nodes = histogram.computeIfAbsent(new SourceLocation(treeNode.getSourceSection(), treeNode.getRootName()),
                            new Function<SourceLocation, List<ProfilerNode<MemoryTracer.Payload>>>() {
                                @Override
                                public List<ProfilerNode<MemoryTracer.Payload>> apply(SourceLocation sourceLocation) {
                                    return new ArrayList<>();
                                }
                            });
            nodes.add(treeNode);
            computeSourceLocationHistogramImpl(treeNode.getChildren(), histogram);
        }
    }

    private static void printLocationHistogram(PrintStream out, MemoryTracer tracer) {
        final Map<SourceLocation, List<ProfilerNode<MemoryTracer.Payload>>> histogram = computeSourceLocationHistogram(tracer);
        final List<SourceLocation> keys = getSortedSourceLocations(histogram);
        int nameMax = 1;
        Iterator<List<ProfilerNode<MemoryTracer.Payload>>> iterator = histogram.values().iterator();
        while (iterator.hasNext()) {
            List<ProfilerNode<MemoryTracer.Payload>> profilerNodes = iterator.next();
            nameMax = Math.max(nameMax, profilerNodes.get(0).getRootName().length());
        }
        final long totalAllocations = getTotalAllocationCount(tracer);

        String format = " %-" + nameMax + "s | %15s | %15s | %8s";
        String title = String.format(format, "Name", "Self Count", "Total Count", "Location");
        String sep = repeat("-", title.length());
        out.println(sep);
        out.println(String.format(" Location Histogram with Allocation Counts. Recorded a total of %d allocations.", totalAllocations));
        out.println("   Total Count: Number of allocations during the execution of this element.");
        out.println("   Self Count: Number of allocations in this element alone (excluding sub calls). ");
        out.println(sep);
        out.println(title);
        out.println(sep);

        for (SourceLocation location : keys) {
            List<ProfilerNode<MemoryTracer.Payload>> profilerNodes = histogram.get(location);
            long self = 0;
            long total = 0;
            for (ProfilerNode<MemoryTracer.Payload> node : profilerNodes) {
                MemoryTracer.Payload payload = node.getPayload();
                self += payload.getEvents().size();
                total += node.isRecursive() ? 0 : payload.getTotalAllocations();
            }
            String selfCount = String.format("%d %5.1f%%", self, (double) self * 100 / totalAllocations);
            String totalCount = String.format("%d %5.1f%%", total, (double) total * 100 / totalAllocations);
            String output = String.format(format, profilerNodes.get(0).getRootName(), selfCount, totalCount, getShortDescription(location.getSourceSection()));
            out.println(output);
        }
        out.println(sep);
    }

    private static void printCallTree(PrintStream out, MemoryTracer tracer) {
        final int titleMax = getFirstFieldOfTitleMax(tracer);
        final long totalAllocations = getTotalAllocationCount(tracer);

        String format = " %-" + titleMax + "s | %15s | %15s | %s";
        String title = String.format(format, "Name", "Total Count", "Self Count", "Location     ");
        String sep = repeat("-", title.length());
        out.println(sep);
        out.println(String.format(" Call Tree with Allocation Counts. Recorded a total of %d allocations.", totalAllocations));
        out.println("   Total Count: Number of allocations during the execution of this function.");
        out.println("   Self Count: Number of allocations in this function alone (excluding sub calls). ");
        out.println(sep);
        out.println(title);
        out.println(sep);
        for (ProfilerNode<MemoryTracer.Payload> node : tracer.getRootNodes()) {
            printCallTree(node, format, 0, totalAllocations, out);
        }
        out.println(sep);
    }

    private static void printCallTree(ProfilerNode<MemoryTracer.Payload> node, String format, int depth, long totalAllocations, PrintStream out) {
        String padding = repeat("  ", depth);
        MemoryTracer.Payload payload = node.getPayload();
        String selfCount = String.format("%d %5.1f%%", payload.getEvents().size(), (double) payload.getEvents().size() * 100 / totalAllocations);
        String count = String.format("%d %5.1f%%", payload.getTotalAllocations(), (double) payload.getTotalAllocations() * 100 / totalAllocations);
        String output = String.format(format, padding + node.getRootName(), count, selfCount, getShortDescription(node.getSourceSection()));
        out.println(output);
        for (ProfilerNode<MemoryTracer.Payload> child : node.getChildren()) {
            printCallTree(child, format, depth + 1, totalAllocations, out);
        }
    }

    private static List<SourceLocation> getSortedSourceLocations(Map<SourceLocation, List<ProfilerNode<MemoryTracer.Payload>>> histogram) {
        List<SourceLocation> keys = new ArrayList<>(histogram.keySet());
        Collections.sort(keys, new Comparator<SourceLocation>() {
            @Override
            public int compare(SourceLocation sl1, SourceLocation sl2) {
                int sl1Self = 0;
                int sl1Total = 0;
                for (ProfilerNode<MemoryTracer.Payload> node : histogram.get(sl1)) {
                    sl1Self += node.getPayload().getEvents().size();
                    sl1Total += node.isRecursive() ? 0 : node.getPayload().getTotalAllocations();
                }

                int sl2Self = 0;
                int sl2Total = 0;
                for (ProfilerNode<MemoryTracer.Payload> node : histogram.get(sl2)) {
                    sl2Self += node.getPayload().getEvents().size();
                    sl2Total += node.isRecursive() ? 0 : node.getPayload().getTotalAllocations();
                }

                int result = Integer.compare(sl2Self, sl1Self);
                if (result == 0) {
                    return Integer.compare(sl2Total, sl1Total);
                }
                return result;
            }
        });
        return keys;
    }

    private static int getFirstFieldOfTitleMax(MemoryTracer tracer) {
        int titleMax = 10;
        for (ProfilerNode<MemoryTracer.Payload> node : tracer.getRootNodes()) {
            titleMax = Math.max(titleMax, getFirstFieldOfTitleMaxRec(node, 0, titleMax));
        }
        return titleMax;
    }

    private static int getFirstFieldOfTitleMaxRec(ProfilerNode<MemoryTracer.Payload> node, int depth, int max) {
        int newMax = Math.max(max, node.getRootName().length() + 2 * depth);
        for (ProfilerNode<MemoryTracer.Payload> child : node.getChildren()) {
            newMax = Math.max(newMax, getFirstFieldOfTitleMaxRec(child, depth + 1, newMax));
        }
        return newMax;
    }

    private static long getTotalAllocationCount(MemoryTracer tracer) {
        long sum = 0;
        for (ProfilerNode<MemoryTracer.Payload> node : tracer.getRootNodes()) {
            sum += node.getPayload().getTotalAllocations();
        }
        return sum;
    }
}
