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
import com.oracle.truffle.api.source.Source;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

import java.io.Closeable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Implementation of a memory tracing profiler for
 * {@linkplain com.oracle.truffle.api.TruffleLanguage Truffle languages} built on top of the
 * {@linkplain TruffleInstrument Truffle instrumentation framework}.
 * <p>
 * The tracer counts how many times each of the elements of interest (e.g. functions, statements,
 * etc.) allocates memory, as well as meta data about the allocated object. It keeps a shadow stack
 * during execution, and listens for {@link AllocationEvent allocation events}. On each event, the
 * allocation information is associated to the top of the stack.
 * <p>
 * NOTE: This profiler is still experimental with limited capabilities.
 *
 * @since 0.29
 */
public final class MemoryTracer implements Closeable {

    /**
     * The {@linkplain TruffleInstrument instrument} for the memory tracer.
     *
     * @since 0.29
     */
    @Registration(id = Instrument.ID, name = "Memory Tracer", version = "0.1", services = {MemoryTracer.class})
    public static final class Instrument extends TruffleInstrument {

        /**
         * Default constructor.
         * 
         * @since 0.29
         */
        public Instrument() {
        }

        /**
         * A string used to identify the tracer, i.e. as the name of the tool.
         *
         * @since 0.29
         */
        public static final String ID = "memtracer";
        static MemoryTracer tracer;
        OptionDescriptors descriptors = null;

        /**
         * Called to create the Instrument.
         *
         * @param env environment information for the instrument
         * @since 0.29
         */
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

        /**
         * @return A list of the options provided by the {@link MemoryTracer}.
         * @since 0.29
         */
        @Override
        protected OptionDescriptors getOptionDescriptors() {
            List<OptionDescriptor> descriptorList = new LinkedList<>();
            descriptorList.add(OptionDescriptor.newBuilder(CLI.ENABLED, ID).category(OptionCategory.USER).help("Enable the Memory Tracer (default:false).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.OUTPUT, ID + ".Output").category(OptionCategory.USER).help(
                            "Print a 'typehistogram', 'histogram' or 'calltree' as output (default:histogram).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.STACK_LIMIT, ID + ".StackLimit").category(OptionCategory.USER).help("Maximum number of maximum stack elements.").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.TRACE_ROOTS, ID + ".TraceRoots").category(OptionCategory.USER).help("Capture roots when tracing (default:true).").build());
            descriptorList.add(
                            OptionDescriptor.newBuilder(CLI.TRACE_STATEMENTS, ID + ".TraceStatements").category(OptionCategory.USER).help("Capture statements when tracing (default:false).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.TRACE_CALLS, ID + ".TraceCalls").category(OptionCategory.USER).help("Capture calls when tracing (default:false).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.TRACE_INTERNAL, ID + ".TraceInternal").category(OptionCategory.USER).help("Capture internal elements (default:false).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.FILTER_ROOT, ID + ".FilterRootName").category(OptionCategory.USER).help(
                            "Wildcard filter for program roots. (eg. Math.*, default:*).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.FILTER_FILE, ID + ".FilterFile").category(OptionCategory.USER).help(
                            "Wildcard filter for source file paths. (eg. *program*.sl, default:*).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.FILTER_LANGUAGE, ID + ".FilterLanguage").category(OptionCategory.USER).help(
                            "Only profile languages with mime-type. (eg. +, default:no filter).").build());
            descriptors = OptionDescriptors.create(descriptorList);
            return descriptors;
        }

        /**
         * Called when the Instrument is to be disposed.
         *
         * @param env environment information for the instrument
         * @since 0.29
         */
        @Override
        protected void onDispose(Env env) {
            if (env.getOptions().get(CLI.ENABLED)) {
                CLI.handleOutput(env, tracer, descriptors);
            }
            tracer.close();
        }
    }

    MemoryTracer(TruffleInstrument.Env env) {
        this.env = env;
    }

    private SourceSectionFilter filter = null;

    private final TruffleInstrument.Env env;

    private boolean closed = false;

    private boolean collecting = false;

    private EventBinding<?> activeBinding;

    private int stackLimit = 1000;

    private ShadowStack shadowStack;

    private EventBinding<?> stacksBinding;

    private final CallTreeNode<AllocationPayload> rootNode = new CallTreeNode<>(this, null);

    private boolean stackOverflowed = false;

    private static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).sourceIs(new SourceSectionFilter.SourcePredicate() {
        @Override
        public boolean test(Source source) {
            return !source.isInternal();
        }
    }).build();

    void resetTracer() {
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

    /**
     * Controls whether the tracer is collecting data or not.
     *
     * @param collecting the new state of the tracer.
     * @since 0.29
     */
    public synchronized void setCollecting(boolean collecting) {
        if (closed) {
            throw new IllegalStateException("Memory Tracer is already closed.");
        }
        if (this.collecting != collecting) {
            this.collecting = collecting;
            resetTracer();
        }
    }

    /**
     * @return whether or not the sampler is currently collecting data.
     * @since 0.29
     */
    public synchronized boolean isCollecting() {
        return collecting;
    }

    /**
     * @return The roots of the trees representing the profile of the execution.
     * @since 0.29
     */
    public Collection<CallTreeNode<AllocationPayload>> getRootNodes() {
        return rootNode.getChildren();
    }

    /**
     * Erases all the data gathered by the tracer.
     *
     * @since 0.29
     */
    public synchronized void clearData() {
        Map<SourceLocation, CallTreeNode<AllocationPayload>> rootChildren = rootNode.children;
        if (rootChildren != null) {
            rootChildren.clear();
        }
    }

    /**
     * @return whether or not the sampler has collected any data so far.
     * @since 0.29
     */
    public synchronized boolean hasData() {
        Map<SourceLocation, CallTreeNode<AllocationPayload>> rootChildren = rootNode.children;
        return rootChildren != null && !rootChildren.isEmpty();
    }

    /**
     * @return size of the shadow stack
     * @since 0.29
     */
    public synchronized int getStackLimit() {
        return stackLimit;
    }

    /**
     * Sets the size of the shadow stack. Whether or not the shadow stack grew more than the
     * provided size during execution can be checked with {@linkplain #hasStackOverflowed}
     *
     * @param stackLimit the new size of the shadow stack
     * @since 0.29
     */
    public synchronized void setStackLimit(int stackLimit) {
        verifyConfigAllowed();
        if (stackLimit < 1) {
            throw new IllegalArgumentException(String.format("Invalid stack limit %s.", stackLimit));
        }
        this.stackLimit = stackLimit;
    }

    /**
     * @return was the shadow stack size insufficient for the execution.
     * @since 0.29
     */
    public boolean hasStackOverflowed() {
        return stackOverflowed;
    }

    /**
     * Sets the {@link SourceSectionFilter filter} for the sampler. This allows the sampler to
     * observe only parts of the executed source code.
     *
     * @param filter The new filter describing which part of the source code to sample
     * @since 0.29
     */
    public synchronized void setFilter(SourceSectionFilter filter) {
        verifyConfigAllowed();
        this.filter = filter;
    }

    /**
     * Closes the tracer for fuhrer use, deleting all the gathered data.
     *
     * @since 0.29
     */
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

    /**
     * Creates a
     * {@linkplain com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#findMetaObject(LanguageInfo, Object)
     * meta object} histogram - a mapping from a {@link String textual} meta object representation
     * to a {@link List} of {@link AllocationEventInfo} corresponding to that meta object. The
     * {@linkplain com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#findMetaObject(LanguageInfo, Object)
     * meta object} are language specific descriptions of allocated objects.
     *
     * @return the met object histogram
     * @since 0.29
     */
    public Map<String, List<AllocationEventInfo>> computeMetaObjectHistogram() {
        Map<String, List<AllocationEventInfo>> histogram = new HashMap<>();
        computeMetaObjectHistogramImpl(rootNode.getChildren(), histogram);
        return histogram;
    }

    private void computeMetaObjectHistogramImpl(Collection<CallTreeNode<AllocationPayload>> children, Map<String, List<AllocationEventInfo>> histogram) {
        for (CallTreeNode<AllocationPayload> treeNode : children) {
            for (AllocationEventInfo info : treeNode.getPayload().getEvents()) {
                List<AllocationEventInfo> nodes = histogram.computeIfAbsent(info.getMetaObjectString(), new Function<String, List<AllocationEventInfo>>() {
                    @Override
                    public List<AllocationEventInfo> apply(String s) {
                        return new ArrayList<>();
                    }
                });
                nodes.add(info);
            }
            computeMetaObjectHistogramImpl(treeNode.getChildren(), histogram);
        }
    }

    /**
     * Creates a source location histogram - a mapping from a {@link SourceLocation source location}
     * to a {@link List} of {@link CallTreeNode} corresponding to that source location. This gives
     * an overview of the allocation profile of each {@link SourceLocation source location}.
     *
     * @return the source location histogram
     * @since 0.29
     */
    public Map<SourceLocation, List<CallTreeNode<AllocationPayload>>> computeSourceLocationHistogram() {
        Map<SourceLocation, List<CallTreeNode<AllocationPayload>>> histogram = new HashMap<>();
        computeSourceLocationHistogramImpl(rootNode.getChildren(), histogram);
        return histogram;
    }

    private void computeSourceLocationHistogramImpl(Collection<CallTreeNode<AllocationPayload>> children, Map<SourceLocation, List<CallTreeNode<AllocationPayload>>> histogram) {
        for (CallTreeNode<AllocationPayload> treeNode : children) {
            List<CallTreeNode<AllocationPayload>> nodes = histogram.computeIfAbsent(treeNode.getSourceLocation(), new Function<SourceLocation, List<CallTreeNode<AllocationPayload>>>() {
                @Override
                public List<CallTreeNode<AllocationPayload>> apply(SourceLocation sourceLocation) {
                    return new ArrayList<>();
                }
            });
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

    /**
     * Used as a template parameter for {@link CallTreeNode}. Holds information about
     * {@link AllocationEventInfo allocation events}.
     *
     * @since 0.29
     */
    public static final class AllocationPayload {

        AllocationPayload() {
        }

        private final List<AllocationEventInfo> events = new ArrayList<>();

        private long totalAllocations = 0;

        /**
         * @return Total number of allocations recorded while the associated element was on the
         *         shadow stack
         * @since 0.29
         */
        public long getTotalAllocations() {
            return totalAllocations;
        }

        /**
         * Increases the number of total allocations recorded while the associated element was on
         * the shadow stack.
         *
         * @since 0.29
         */
        public void incrementTotalAllocations() {
            this.totalAllocations++;
        }

        /**
         * @return Information about all the {@link AllocationEventInfo allocation events} that
         *         happened while the associated element was at the top of the shadow stack.
         * @since 0.29
         */
        public List<AllocationEventInfo> getEvents() {
            return events;
        }
    }

    /**
     * Stores informatino about a single {@link AllocationEvent}.
     *
     * @since 0.29
     */
    public static final class AllocationEventInfo {
        private final LanguageInfo language;
        private final long allocated;
        private final boolean reallocation;
        private final String metaObjectString;

        AllocationEventInfo(LanguageInfo language, long allocated, boolean realocation, String metaObjectString) {
            this.language = language;
            this.allocated = allocated;
            this.reallocation = realocation;
            this.metaObjectString = metaObjectString;
        }

        /**
         * @return The {@link LanguageInfo language} from which the allocation originated
         * @since 0.29
         */
        public LanguageInfo getLanguage() {
            return language;
        }

        /**
         * @return the amount of memory that was allocated
         * @since 0.29
         */
        public long getAllocated() {
            return allocated;
        }

        /**
         * @return Whether the allocation was a re-allocation
         * @since 0.29
         */
        public boolean isReallocation() {
            return reallocation;
        }

        /**
         * @return A String representation of the
         *         {@linkplain com.oracle.truffle.api.instrumentation.TruffleInstrument.Env#findMetaObject(LanguageInfo, Object)
         *         meta object}
         * @since 0.29
         */
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
            Iterator<List<CallTreeNode<AllocationPayload>>> iterator = histogram.values().iterator();
            while (iterator.hasNext()) {
                List<CallTreeNode<AllocationPayload>> callTreeNodes = iterator.next();
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
}
