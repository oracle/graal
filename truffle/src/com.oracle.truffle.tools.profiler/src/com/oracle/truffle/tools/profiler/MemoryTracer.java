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
package com.oracle.truffle.tools.profiler;

import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.AllocationListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

import java.io.Closeable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class MemoryTracer implements Closeable {

    @Registration(id = Instrument.ID, name = "Memory Tracer", version = "1.0", services = {MemoryTracer.class})
    public static final class Instrument extends TruffleInstrument {

        public static final String ID = "memtracer";
        static MemoryTracer tracer;
        List<OptionDescriptor> descriptors = new ArrayList<>();

        @Override
        protected void onCreate(Env env) {
            tracer = new MemoryTracer(env);
            if (env.getOptions().get(CLI.ENABLED)) {
                tracer.setFilter(getSourceSectionFilter(env));
                tracer.setCollecting(true);
                tracer.setStackLimit(env.getOptions().get(CLI.STACK_LIMIT));
            }
            env.registerService(tracer);
        }

        private static SourceSectionFilter getSourceSectionFilter(Env env) {
            final boolean roots = env.getOptions().get(CLI.TRACE_ROOTS);
            final boolean statements = env.getOptions().get(CLI.TRACE_STATEMENTS);
            final boolean calls = env.getOptions().get(CLI.TRACE_CALLS);
            final boolean internals = env.getOptions().get(CLI.TRACE_INTERNAL);
            final Object[] filterRootName = env.getOptions().get(CLI.FILTER_ROOT);
            final Object[] filterFile = env.getOptions().get(CLI.FILTER_FILE);
            final String filterLanguage = env.getOptions().get(CLI.FILTER_LANGUAGE);
            return CLI.buildFilter(roots, statements, calls, internals, filterRootName, filterFile, filterLanguage);
        }

        @SuppressWarnings("deprecation")
        @Override
        protected List<OptionDescriptor> describeOptions() {
            descriptors.add(OptionDescriptor.newBuilder(CLI.ENABLED, ID).category(OptionCategory.USER).help("Enable the Memory Tracer (default:false).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.OUTPUT, ID + ".Output").category(OptionCategory.USER).help(
                            "Print a 'typehistogram', 'histogram' or 'calltree' as output (default:histogram).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.STACK_LIMIT, ID + ".StackLimit").category(OptionCategory.USER).help("Maximum number of maximum stack elements.").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.TRACE_ROOTS, ID + ".TraceRoots").category(OptionCategory.USER).help("Capture roots when tracing (default:true).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.TRACE_STATEMENTS, ID + ".TraceStatements").category(OptionCategory.USER).help("Capture statements when tracing (default:false).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.TRACE_CALLS, ID + ".TraceCalls").category(OptionCategory.USER).help("Capture calls when tracing (default:false).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.TRACE_INTERNAL, ID + ".TraceInternal").category(OptionCategory.USER).help("Capture internal elements (default:false).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.FILTER_ROOT, ID + ".FilterRootName").category(OptionCategory.USER).help(
                            "Wildcard filter for program roots. (eg. Math.*, default:*).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.FILTER_FILE, ID + ".FilterFile").category(OptionCategory.USER).help(
                            "Wildcard filter for source file paths. (eg. *program*.sl, default:*).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.FILTER_LANGUAGE, ID + ".FilterLanguage").category(OptionCategory.USER).help(
                            "Only profile languages with mime-type. (eg. +, default:no filter).").build());
            return descriptors;
        }

        @Override
        protected void onDispose(Env env) {
            if (env.getOptions().get(CLI.ENABLED)) {
                CLI.handleOutput(env, tracer, descriptors);
            }
            tracer.close();
        }
    }

    private SourceSectionFilter filter = null;

    public MemoryTracer(TruffleInstrument.Env env) {
        this.env = env;
    }

    private final TruffleInstrument.Env env;
    private boolean closed = false;
    private boolean collecting = false;
    private EventBinding<?> activeBinding;
    private int stackLimit = 1000;
    private ShadowStack shadowStack;
    private EventBinding<?> stacksBinding;
    private final CallTreeNode<AllocationPayload> rootNode = new CallTreeNode<>(this, null);
    private boolean stackOverflowed = false;

    private static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).sourceIs(s -> !s.isInternal()).build();

    public void resetTracer() {
        assert Thread.holdsLock(this);
        if (activeBinding != null) {
            activeBinding.dispose();
            activeBinding = null;
        }
        if (!collecting || closed) {
            return;
        }
        this.shadowStack = new ShadowStack(stackLimit);
        SourceSectionFilter f = this.filter;
        if (f == null) {
            f = DEFAULT_FILTER;
        }
        this.stacksBinding = this.shadowStack.install(env.getInstrumenter(), f, false);

        this.activeBinding = env.getInstrumenter().attachAllocationListener(AllocationEventFilter.ANY, new Listener());
    }

    public synchronized void setCollecting(boolean collecting) {
        if (closed) {
            throw new IllegalStateException("Memory Tracer is already closed.");
        }
        if (this.collecting != collecting) {
            this.collecting = collecting;
            resetTracer();
        }
    }

    public Collection<CallTreeNode<AllocationPayload>> getRootNodes() {
        return rootNode.getChildren();
    }

    public synchronized boolean isCollecting() {
        return collecting;
    }

    public synchronized void clearData() {
        Map<SourceLocation, CallTreeNode<AllocationPayload>> rootChildren = rootNode.children;
        if (rootChildren != null) {
            rootChildren.clear();
        }
    }

    public synchronized boolean hasData() {
        Map<SourceLocation, CallTreeNode<AllocationPayload>> rootChildren = rootNode.children;
        return rootChildren != null && !rootChildren.isEmpty();
    }

    public synchronized int getStackLimit() {
        return stackLimit;
    }

    public synchronized void setStackLimit(int stackLimit) {
        verifyConfigAllowed();
        if (stackLimit < 1) {
            throw new IllegalArgumentException(String.format("Invalid stack limit %s.", stackLimit));
        }
        this.stackLimit = stackLimit;
    }

    public boolean hasStackOverflowed() {
        return stackOverflowed;
    }

    public synchronized void setFilter(SourceSectionFilter filter) {
        verifyConfigAllowed();
        this.filter = filter;
    }

    @Override
    public void close() {
        assert Thread.holdsLock(this);
        if (stacksBinding != null) {
            stacksBinding.dispose();
            stacksBinding = null;
        }
        if (shadowStack != null) {
            shadowStack = null;
        }
    }

    private void verifyConfigAllowed() {
        assert Thread.holdsLock(this);
        if (closed) {
            throw new IllegalStateException("Memory Tracer is already closed.");
        } else if (collecting) {
            throw new IllegalStateException("Cannot change tracer configuration while collecting. Call setCollecting(false) to disable collection first.");
        }
    }

    public Map<String, List<AllocationEventInfo>> computeMetaObjectHistogram() {
        Map<String, List<AllocationEventInfo>> histogram = new HashMap<>();
        computeMetaObjectHistogramImpl(rootNode.getChildren(), histogram);
        return histogram;
    }

    private void computeMetaObjectHistogramImpl(Collection<CallTreeNode<AllocationPayload>> children, Map<String, List<AllocationEventInfo>> histogram) {
        for (CallTreeNode<AllocationPayload> treeNode : children) {
            for (AllocationEventInfo info : treeNode.getPayload().getEvents()) {
                List<AllocationEventInfo> nodes = histogram.computeIfAbsent(info.getMetaObjectString(), (String k) -> new ArrayList<>());
                nodes.add(info);
            }
            computeMetaObjectHistogramImpl(treeNode.getChildren(), histogram);
        }
    }

    public Map<SourceLocation, List<CallTreeNode<AllocationPayload>>> computeSourceLocationHistogram() {
        Map<SourceLocation, List<CallTreeNode<AllocationPayload>>> histogram = new HashMap<>();
        computeSourceLocationHistogramImpl(rootNode.getChildren(), histogram);
        return histogram;
    }

    private void computeSourceLocationHistogramImpl(Collection<CallTreeNode<AllocationPayload>> children, Map<SourceLocation, List<CallTreeNode<AllocationPayload>>> histogram) {
        for (CallTreeNode<AllocationPayload> treeNode : children) {
            List<CallTreeNode<AllocationPayload>> nodes = histogram.computeIfAbsent(treeNode.getSourceLocation(), k -> new ArrayList<>());
            nodes.add(treeNode);
            computeSourceLocationHistogramImpl(treeNode.getChildren(), histogram);
        }
    }

    private final class Listener implements AllocationListener {

        @Override
        public void onEnter(AllocationEvent event) {
        }

        @Override
        @TruffleBoundary
        public void onReturnValue(AllocationEvent event) {
            ShadowStack.ThreadLocalStack stack = shadowStack.getStack(Thread.currentThread());
            if (stack == null || stack.getStackIndex() == -1) {
                // nothing on the stack
                return;
            }
            if (stack.hasStackOverflowed()) {
                stackOverflowed = true;
                return;
            }
            Node instrumentedNode = stack.getStack()[stack.getStackIndex()].getInstrumentedNode();
            LanguageInfo languageInfo = instrumentedNode.getRootNode().getLanguageInfo();
            Object metaObject = env.findMetaObject(languageInfo, event.getValue());
            String metaObjectString = env.toString(languageInfo, metaObject);
            AllocationEventInfo info = new AllocationEventInfo(event.getLanguage(), event.getNewSize() - event.getOldSize(), event.getOldSize() != 0, metaObjectString);
            handleEvent(stack, info);
        }

        boolean handleEvent(ShadowStack.ThreadLocalStack stack, AllocationEventInfo info) {
            final ShadowStack.ThreadLocalStack.CorrectedStackInfo correctedStackInfo = ShadowStack.ThreadLocalStack.CorrectedStackInfo.build(stack);
            if (correctedStackInfo == null) {
                return false;
            }
            // now traverse the stack and reconstruct the call tree
            CallTreeNode<AllocationPayload> treeNode = rootNode;
            for (int i = 0; i < correctedStackInfo.getLength(); i++) {
                SourceLocation location = correctedStackInfo.getStack()[i];
                CallTreeNode<AllocationPayload> child = treeNode.findChild(location);
                if (child == null) {
                    child = new CallTreeNode<>(treeNode, location, new AllocationPayload());
                    treeNode.addChild(location, child);
                }
                treeNode = child;
                treeNode.getPayload().incrementTotalAllocations();
            }
            // insert event at the top of the stack
            treeNode.getPayload().getEvents().add(info);
            return true;
        }
    }

    public static final class AllocationPayload {
        private final List<AllocationEventInfo> events = new ArrayList<>();

        private long totalAllocations = 0;

        public long getTotalAllocations() {
            return totalAllocations;
        }

        public void incrementTotalAllocations() {
            this.totalAllocations++;
        }

        public List<AllocationEventInfo> getEvents() {
            return events;
        }
    }

    public static final class AllocationEventInfo {
        private final LanguageInfo language;
        private final long allocated;
        private final boolean reallocation;
        private final String metaObjectString;

        public AllocationEventInfo(LanguageInfo language, long allocated, boolean realocation, String metaObjectString) {
            this.language = language;
            this.allocated = allocated;
            this.reallocation = realocation;
            this.metaObjectString = metaObjectString;
        }

        public LanguageInfo getLanguage() {
            return language;
        }

        public long getAllocated() {
            return allocated;
        }

        public boolean isReallocation() {
            return reallocation;
        }

        public String getMetaObjectString() {
            return metaObjectString;
        }
    }

    private static final class CLI extends ProfilerCLI {

        enum Output {
            TYPE_HISTOGRAM,
            LOCATION_HISTOGRAM,
            CALLTREE
        }

        static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Format",
                        Output.LOCATION_HISTOGRAM,
                        (String string) -> {
                            switch (string) {
                                case "typehistogram":
                                    return Output.TYPE_HISTOGRAM;
                                case "histogram":
                                    return Output.LOCATION_HISTOGRAM;
                                case "calltree":
                                    return Output.CALLTREE;
                                default:
                                    return null;
                            }
                        },
                        cliOutput -> {
                            if (cliOutput == null) {
                                throw new IllegalArgumentException();
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
        // TODO Is this enough? Should we check if the argument is a real language?
        static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");

        static void handleOutput(TruffleInstrument.Env env, MemoryTracer tracer, List<OptionDescriptor> descriptors) {
            PrintStream out = new PrintStream(env.out());
            if (tracer.hasStackOverflowed()) {
                out.println("-------------------------------------------------------------------------------- ");
                out.println("ERROR: Shadow stack has overflowed its capacity of " + env.getOptions().get(STACK_LIMIT) + " during execution!");
                out.println("The gathered data is incomplete and incorrect!");
                String name = descriptors.stream().filter(e -> e.getKey().equals(STACK_LIMIT)).findFirst().get().getName();
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
            keys.sort((o1, o2) -> Integer.compare(histogram.get(o2).size(), histogram.get(o1).size()));
            final int metaObjectMax = histogram.keySet().stream().map((s) -> s.length()).max(Integer::compareTo).orElse(1);
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
            final int nameMax = histogram.values().stream().map((nodes) -> nodes.get(0).getRootName().length()).max(Integer::compareTo).orElse(1);
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
            Collections.sort(keys, (SourceLocation sl1, SourceLocation sl2) -> {
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
}
