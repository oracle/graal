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

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.profiler.CallTreeNode;
import com.oracle.truffle.tools.profiler.MemoryTracer;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class MemoryTracerCLI extends ProfilerCLI {

    enum Output {
        TYPE_HISTOGRAM,
        LOCATION_HISTOGRAM,
        CALLTREE
    }

    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Format",
            Output.LOCATION_HISTOGRAM,
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

    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.LOCATION_HISTOGRAM, CLI_OUTPUT_TYPE);
    static final OptionKey<Integer> STACK_LIMIT = new OptionKey<>(10000);
    static final OptionKey<Boolean> TRACE_ROOTS = new OptionKey<>(true);
    static final OptionKey<Boolean> TRACE_STATEMENTS = new OptionKey<>(false);
    static final OptionKey<Boolean> TRACE_CALLS = new OptionKey<>(false);
    static final OptionKey<Boolean> TRACE_INTERNAL = new OptionKey<>(false);
    static final OptionKey<Object[]> FILTER_ROOT = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);
    static final OptionKey<Object[]> FILTER_FILE = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);
    static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");

    static void handleOutput(TruffleInstrument.Env env, MemoryTracer tracer, OptionDescriptors descriptors) {
        PrintStream out = new PrintStream(env.out());
        if (tracer.hasStackOverflowed()) {
            out.println("-------------------------------------------------------------------------------- ");
            out.println("ERROR: Shadow stack has overflowed its capacity of " + env.getOptions().get(STACK_LIMIT) + " during execution!");
            out.println("The gathered data is incomplete and incorrect!");
            String name = "";
            Iterator<OptionDescriptor> iterator = descriptors.iterator();
            while (iterator.hasNext()) {
                OptionDescriptor descriptor = iterator.next();
                if (descriptor.getKey().equals(STACK_LIMIT)) {
                    name = descriptor.getName();
                    break;
                }
            }
            assert !name.equals("");
            out.println("Use --" + name + "=<" + STACK_LIMIT.getType().getName() + "> to set stack capacity.");
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

    private static void printMetaObjectHistogram(PrintStream out, MemoryTracer tracer) {
        final Map<String, List<MemoryTracer.AllocationEventInfo>> histogram = tracer.computeMetaObjectHistogram();
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

    private static void printLocationHistogram(PrintStream out, MemoryTracer tracer) {
        final Map<SourceLocation, List<CallTreeNode<MemoryTracer.AllocationPayload>>> histogram = tracer.computeSourceLocationHistogram();
        final List<SourceLocation> keys = getSortedSourceLocations(histogram);
        int nameMax = 1;
        Iterator<List<CallTreeNode<MemoryTracer.AllocationPayload>>> iterator = histogram.values().iterator();
        while (iterator.hasNext()) {
            List<CallTreeNode<MemoryTracer.AllocationPayload>> callTreeNodes = iterator.next();
            nameMax = Math.max(nameMax, callTreeNodes.get(0).getRootName().length());
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
            List<CallTreeNode<MemoryTracer.AllocationPayload>> callTreeNodes = histogram.get(location);
            long self = 0;
            long total = 0;
            for (CallTreeNode<MemoryTracer.AllocationPayload> node : callTreeNodes) {
                MemoryTracer.AllocationPayload payload = node.getPayload();
                self += payload.getEvents().size();
                total += node.isRecursive() ? 0 : payload.getTotalAllocations();
            }
            String selfCount = String.format("%d %5.1f%%", self, (double) self * 100 / totalAllocations);
            String totalCount = String.format("%d %5.1f%%", total, (double) total * 100 / totalAllocations);
            String output = String.format(format, callTreeNodes.get(0).getRootName(), selfCount, totalCount, getShortDescription(location.getSourceSection()));
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
        for (CallTreeNode<MemoryTracer.AllocationPayload> node : tracer.getRootNodes()) {
            printCallTree(node, format, 0, totalAllocations, out);
        }
        out.println(sep);
    }

    private static void printCallTree(CallTreeNode<MemoryTracer.AllocationPayload> node, String format, int depth, long totalAllocations, PrintStream out) {
        String padding = repeat("  ", depth);
        MemoryTracer.AllocationPayload payload = node.getPayload();
        String selfCount = String.format("%d %5.1f%%", payload.getEvents().size(), (double) payload.getEvents().size() * 100 / totalAllocations);
        String count = String.format("%d %5.1f%%", payload.getTotalAllocations(), (double) payload.getTotalAllocations() * 100 / totalAllocations);
        String output = String.format(format, padding + node.getRootName(), count, selfCount, getShortDescription(node.getSourceSection()));
        out.println(output);
        for (CallTreeNode<MemoryTracer.AllocationPayload> child : node.getChildren()) {
            printCallTree(child, format, depth + 1, totalAllocations, out);
        }
    }

    private static List<SourceLocation> getSortedSourceLocations(Map<SourceLocation, List<CallTreeNode<MemoryTracer.AllocationPayload>>> histogram) {
        List<SourceLocation> keys = new ArrayList<>(histogram.keySet());
        Collections.sort(keys, new Comparator<SourceLocation>() {
            @Override
            public int compare(SourceLocation sl1, SourceLocation sl2) {
                int sl1Self = 0;
                int sl1Total = 0;
                for (CallTreeNode<MemoryTracer.AllocationPayload> node : histogram.get(sl1)) {
                    sl1Self += node.getPayload().getEvents().size();
                    sl1Total += node.isRecursive() ? 0 : node.getPayload().getTotalAllocations();
                }

                int sl2Self = 0;
                int sl2Total = 0;
                for (CallTreeNode<MemoryTracer.AllocationPayload> node : histogram.get(sl2)) {
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
        for (CallTreeNode<MemoryTracer.AllocationPayload> node : tracer.getRootNodes()) {
            titleMax = Math.max(titleMax, getFirstFieldOfTitleMaxRec(node, 0, titleMax));
        }
        return titleMax;
    }

    private static int getFirstFieldOfTitleMaxRec(CallTreeNode<MemoryTracer.AllocationPayload> node, int depth, int max) {
        int newMax = Math.max(max, node.getRootName().length() + 2 * depth);
        for (CallTreeNode<MemoryTracer.AllocationPayload> child : node.getChildren()) {
            newMax = Math.max(newMax, getFirstFieldOfTitleMaxRec(child, depth + 1, newMax));
        }
        return newMax;
    }

    private static long getTotalAllocationCount(MemoryTracer tracer) {
        long sum = 0;
        for (CallTreeNode<MemoryTracer.AllocationPayload> node : tracer.getRootNodes()) {
            sum += node.getPayload().getTotalAllocations();
        }
        return sum;
    }
}