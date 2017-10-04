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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import java.io.Closeable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of a tracing based profiler for {@linkplain com.oracle.truffle.api.TruffleLanguage
 * Truffle languages} built on top of the {@linkplain TruffleInstrument Truffle instrumentation
 * framework}.
 * <p>
 * The tracer counts how many times each of the elements of interest (e.g. functions, statements,
 * etc.) are executed.
 *
 * @since 0.29
 */
public final class CPUTracer implements Closeable {

    /**
     * The {@linkplain TruffleInstrument instrument} for the CPU tracer.
     *
     * @since 0.29
     */
    @TruffleInstrument.Registration(id = Instrument.ID, name = "CPU Tracer", version = "0.1", services = {CPUTracer.class})
    public static class Instrument extends TruffleInstrument {

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
        public static final String ID = "cputracer";
        static CPUTracer tracer;

        /**
         * Called to create the Instrument.
         *
         * @param env environment information for the instrument
         * @since 0.29
         */
        @Override
        protected void onCreate(Env env) {
            tracer = new CPUTracer(env);
            if (env.getOptions().get(CLI.ENABLED)) {
                tracer.setFilter(getSourceSectionFilter(env));
                tracer.setCollecting(true);
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
         * @return A list of the options provided by the {@link CPUTracer}.
         * @since 0.29
         */
        @Override
        protected OptionDescriptors getOptionDescriptors() {
            List<OptionDescriptor> descriptors = new ArrayList<>();
            descriptors.add(OptionDescriptor.newBuilder(CLI.ENABLED, ID).category(OptionCategory.USER).help("Enable the CPU tracer (default: false).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.TRACE_ROOTS, ID + ".TraceRoots").category(OptionCategory.USER).help("Capture roots when tracing (default:true).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.TRACE_STATEMENTS, ID + ".TraceStatements").category(OptionCategory.USER).help("Capture statements when tracing (default:false).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.TRACE_CALLS, ID + ".TraceCalls").category(OptionCategory.USER).help("Capture calls when tracing (default:false).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.TRACE_INTERNAL, ID + ".TraceInternal").category(OptionCategory.USER).help("Trace internal elements (default:false).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.FILTER_ROOT, ID + ".FilterRootName").category(OptionCategory.USER).help(
                            "Wildcard filter for program roots. (eg. Math.*, default:*).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.FILTER_FILE, ID + ".FilterFile").category(OptionCategory.USER).help(
                            "Wildcard filter for source file paths. (eg. *program*.sl, default:*).").build());
            descriptors.add(OptionDescriptor.newBuilder(CLI.FILTER_LANGUAGE, ID + ".FilterLanguage").category(OptionCategory.USER).help(
                            "Only profile languages with mime-type. (eg. +, default:no filter).").build());
            return OptionDescriptors.create(descriptors);
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
                CLI.printTracerHistogram(new PrintStream(env.out()), tracer);
                tracer.close();
            }
        }
    }

    CPUTracer(Env env) {
        this.env = env;
    }

    private static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().tagIs(RootTag.class).build();

    private final Env env;

    private boolean closed = false;

    private boolean collecting = false;

    private SourceSectionFilter filter = null;

    private EventBinding<?> activeBinding;

    private final Map<SourceSection, Counter> counters = new ConcurrentHashMap<>();

    /**
     * Controls whether the tracer is collecting data or not.
     *
     * @param collecting the new state of the tracer.
     * @since 0.29
     */
    public synchronized void setCollecting(boolean collecting) {
        if (closed) {
            throw new IllegalStateException("CPUTracer is already closed.");
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
     * Sets the {@link SourceSectionFilter filter} for the tracer. This allows the tracer to trace
     * only parts of the executed source code.
     *
     * @param filter The filter describing which part of the source code to trace
     * @since 0.29
     */
    public synchronized void setFilter(SourceSectionFilter filter) {
        verifyConfigAllowed();
        this.filter = filter;
    }

    /**
     * @return The filter describing which part of the source code to sample
     * @since 0.29
     */
    public synchronized SourceSectionFilter getFilter() {
        return filter;
    }

    /**
     * @return All the counters the tracer has gathered as an unmodifiable collection
     * @since 0.29
     */
    public synchronized Collection<Counter> getCounters() {
        return Collections.unmodifiableCollection(counters.values());
    }

    /**
     * Erases all the data gathered by the tracer.
     *
     * @since 0.29
     */
    public synchronized void clearData() {
        counters.clear();
    }

    private synchronized Counter getCounter(EventContext context) {
        SourceSection sourceSection = context.getInstrumentedSourceSection();
        Counter counter = counters.get(sourceSection);
        if (counter == null) {
            counter = new Counter(new SourceLocation(env.getInstrumenter(), context));
            Counter otherCounter = counters.putIfAbsent(sourceSection, counter);
            if (otherCounter != null) {
                counter = otherCounter;
            }
        }
        return counter;
    }

    private synchronized void verifyConfigAllowed() {
        assert Thread.holdsLock(this);
        if (closed) {
            throw new IllegalStateException("CPUTracer is already closed.");
        } else if (collecting) {
            throw new IllegalStateException("Cannot change tracer configuration while collecting. Call setCollecting(false) to disable collection first.");
        }
    }

    private synchronized void resetTracer() {
        assert Thread.holdsLock(this);
        if (activeBinding != null) {
            activeBinding.dispose();
            activeBinding = null;
        }
        if (!collecting || closed) {
            return;
        }

        SourceSectionFilter f = this.filter;
        if (f == null) {
            f = DEFAULT_FILTER;
        }
        this.activeBinding = env.getInstrumenter().attachFactory(f, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                return new CounterNode(getCounter(context));
            }
        });
    }

    /**
     * Closes the tracer for fuhrer use, deleting all the gathered data.
     *
     * @since 0.29
     */
    @Override
    public synchronized void close() {
        closed = true;
        clearData();
    }

    /**
     * Holds data on how many times a section of source code was executed. Differentiates between
     * compiled and interpreted executions.
     *
     * @since 0.29
     */
    public static final class Counter {

        private final SourceLocation location;

        private long countInterpreted;
        private long countCompiled;

        Counter(SourceLocation location) {
            this.location = location;
        }

        /**
         * @return The name of the root not in which the source section associated with this counter
         *         appears
         * @since 0.29
         */
        public String getRootName() {
            return location.getRootName();
        }

        /**
         * @return A set of {@link com.oracle.truffle.api.instrumentation.StandardTags tags} for the
         *         {@link SourceLocation} associated with this {@link CallTreeNode}
         * @since 0.29
         */
        public Set<Class<?>> getTags() {
            return location.getTags();
        }

        /**
         * @return The source section for which this {@link Counter} is counting executions
         * @since 0.29
         */
        public SourceSection getSourceSection() {
            return location.getSourceSection();
        }

        /**
         * @return The number of times the associated source sections was executed as compiled code
         * @since 0.29
         */
        public long getCountCompiled() {
            return countCompiled;
        }

        /**
         * @return The number of times the associated source sections was interpreted
         * @since 0.29
         */
        public long getCountInterpreted() {
            return countInterpreted;
        }

        /**
         * @return The total number of times the associated source sections was executed
         * @since 0.29
         */
        public long getCount() {
            return countCompiled + countInterpreted;
        }
    }

    private static class CounterNode extends ExecutionEventNode {

        private final Counter counter;

        CounterNode(Counter counter) {
            this.counter = counter;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (CompilerDirectives.inInterpreter()) {
                counter.countInterpreted++;
            } else {
                counter.countCompiled++;
            }
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }
    }

    private static final class CLI extends ProfilerCLI {
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
            counters.sort(new Comparator<Counter>() {
                @Override
                public int compare(Counter o1, Counter o2) {
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
}
