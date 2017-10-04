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

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

import java.io.Closeable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Implementation of a sampling based profiler for
 * {@linkplain com.oracle.truffle.api.TruffleLanguage Truffle languages} built on top of the
 * {@linkplain TruffleInstrument Truffle instrumentation framework}.
 * <p>
 * The sampler keeps a shadow stack during execution. This shadow stack is sampled at regular
 * intervals, i.e. the state of the stack is copied and saved into trees of {@linkplain CallTreeNode
 * nodes}, which represent the profile of the execution.
 *
 * @since 0.29
 */
public final class CPUSampler implements Closeable {

    /**
     * The {@linkplain TruffleInstrument instrument} for the CPU sampler.
     *
     * @since 0.29
     */
    @TruffleInstrument.Registration(id = Instrument.ID, name = "CPU Sampler", version = "0.1", services = {CPUSampler.class})
    public static final class Instrument extends TruffleInstrument {

        /**
         * Default constructor.
         *
         * @since 0.29
         */
        public Instrument() {
        }

        /**
         * A string used to identify the sampler, i.e. as the name of the tool.
         *
         * @since 0.29
         */
        public static final String ID = "cpusampler";
        static CPUSampler sampler;
        OptionDescriptors descriptors = null;

        /**
         * Called to create the Instrument.
         *
         * @param env environment information for the instrument
         * @since 0.29
         */
        @Override
        protected void onCreate(Env env) {
            sampler = new CPUSampler(env);
            if (env.getOptions().get(CLI.ENABLED)) {
                sampler.setPeriod(env.getOptions().get(CLI.SAMPLE_PERIOD));
                sampler.setDelay(env.getOptions().get(CLI.DELAY_PERIOD));
                sampler.setStackLimit(env.getOptions().get(CLI.STACK_LIMIT));
                sampler.setFilter(getSourceSectionFilter(env));
                sampler.setExcludeInlinedRoots(env.getOptions().get(CLI.MODE) == CLI.Mode.COMPILED);
                sampler.setCollecting(true);
            }
            env.registerService(sampler);
        }

        private static SourceSectionFilter getSourceSectionFilter(Env env) {
            CLI.Mode mode = env.getOptions().get(CLI.MODE);
            final boolean statements = mode == CLI.Mode.STATEMENTS;
            final boolean internals = env.getOptions().get(CLI.SAMPLE_INTERNAL);
            final Object[] filterRootName = env.getOptions().get(CLI.FILTER_ROOT);
            final Object[] filterFile = env.getOptions().get(CLI.FILTER_FILE);
            final String filterLanguage = env.getOptions().get(CLI.FILTER_LANGUAGE);
            return CLI.buildFilter(true, statements, false, internals, filterRootName, filterFile, filterLanguage);
        }

        /**
         * @return All the {@link OptionDescriptors options} provided by the {@link CPUSampler}.
         * @since 0.29
         */
        @Override
        protected OptionDescriptors getOptionDescriptors() {
            List<OptionDescriptor> descriptorList = new ArrayList<>();
            descriptorList.add(OptionDescriptor.newBuilder(CLI.ENABLED, ID).category(OptionCategory.USER).help("Enable the CPU sampler.").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.SAMPLE_PERIOD, ID + ".Period").category(OptionCategory.USER).help("Period in milliseconds to sample the stack.").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.DELAY_PERIOD, ID + ".Delay").category(OptionCategory.USER).help("Delay the sampling for this many milliseconds (default: 0).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.STACK_LIMIT, ID + ".StackLimit").category(OptionCategory.USER).help("Maximum number of maximum stack elements.").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.OUTPUT, ID + ".Output").category(OptionCategory.USER).help("Print a 'histogram' or 'calltree' as output (default:HISTOGRAM).").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.MODE, ID + ".Mode").category(OptionCategory.USER).help(
                            "Describes level of sampling detail. NOTE: Increased detail can lead to reduced accuracy. Modes:" + System.lineSeparator() +
                                            "'compiled' - samples roots excluding inlined functions (default)" + System.lineSeparator() + "'roots' - samples roots including inlined functions" +
                                            System.lineSeparator() + "'statements' - samples all statements.").build());
            descriptorList.add(OptionDescriptor.newBuilder(CLI.SAMPLE_INTERNAL, ID + ".SampleInternal").category(OptionCategory.USER).help("Capture internal elements (default:false).").build());
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
                CLI.handleOutput(env, sampler, descriptors);
            }
            sampler.close();
        }
    }

    /**
     * Wrapper for information on how many times an element was seen on the shadow stack. Used as a
     * template parameter of {@link CallTreeNode}. Differentiates between an execution in compiled
     * code and in the interpreter.
     *
     * @since 0.29
     */
    public final class HitCounts {

        HitCounts() {
        }

        int compiledHitCount;
        int interpretedHitCount;

        int selfCompiledHitCount;
        int selfInterpretedHitCount;

        long firstHitTime;
        long lastHitTime;

        /**
         * @return The number of times the element was found bellow the top of the shadow stack as
         *         compiled code
         * @since 0.29
         */
        public int getCompiledHitCount() {
            return compiledHitCount;
        }

        /**
         * @return The number of times the element was found bellow the top of the shadow stack as
         *         interpreted code
         * @since 0.29
         */
        public int getInterpretedHitCount() {
            return interpretedHitCount;
        }

        /**
         * @return The number of times the element was found on the top of the shadow stack as
         *         compiled code
         * @since 0.29
         */
        public int getSelfCompiledHitCount() {
            return selfCompiledHitCount;
        }

        /**
         * @return The number of times the element was found on the top of the shadow stack as
         *         interpreted code
         * @since 0.29
         */
        public int getSelfInterpretedHitCount() {
            return selfInterpretedHitCount;
        }

        /**
         * @return When was the element first found on the stack
         * @since 0.29
         */
        public long getFirstHitTime() {
            return firstHitTime;
        }

        /**
         * @return When was the element last found on the stack
         * @since 0.29
         */
        public long getLastHitTime() {
            return lastHitTime;
        }

        /**
         * @return Total number of times the element was found on the top of the shadow stack
         * @since 0.29
         */
        public int getSelfHitCount() {
            return selfCompiledHitCount + selfInterpretedHitCount;
        }

        /**
         * @return Total number of times the element was found bellow the top of the shadow stack
         * @since 0.29
         */
        public int getHitCount() {
            return compiledHitCount + interpretedHitCount;
        }

    }

    static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().tagIs(RootTag.class).build();

    private volatile boolean closed;

    private volatile boolean collecting;

    private long period = 1;

    private long delay = 0;

    private int stackLimit = 10000;

    private SourceSectionFilter filter;

    private boolean stackOverflowed = false;

    private boolean excludeInlinedRoots;

    private AtomicLong samplesTaken = new AtomicLong(0);

    private Timer samplerThread;

    private TimerTask samplerTask;

    private ShadowStack shadowStack;

    private EventBinding<?> stacksBinding;

    private final CallTreeNode<HitCounts> rootNode = new CallTreeNode<>(this, new HitCounts());

    private final Env env;

    CPUSampler(Env env) {
        this.env = env;
    }

    /**
     * Controls whether the sampler is collecting data or not.
     *
     * @param collecting the new state of the sampler.
     * @since 0.29
     */
    public synchronized void setCollecting(boolean collecting) {
        if (this.collecting != collecting) {
            this.collecting = collecting;
            resetSampling();
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
     * Controls whether the sampler shoulde exclude inlined roots. This means that functions that
     * are inlined during compilation do not appear on the shadow stack. This reduces overhead.
     *
     * @param excludeInlinedRoots the new state of the sampler
     * @since 0.29
     */
    public synchronized void setExcludeInlinedRoots(boolean excludeInlinedRoots) {
        verifyConfigAllowed();
        this.excludeInlinedRoots = excludeInlinedRoots;
    }

    /**
     * Sets the sampling period i.e. the time between two samples of the shadow stack are taken.
     *
     * @param samplePeriod the new sampling period.
     * @since 0.29
     */
    public synchronized void setPeriod(long samplePeriod) {
        verifyConfigAllowed();
        if (samplePeriod < 1) {
            throw new IllegalArgumentException(String.format("Invalid sample period %s.", samplePeriod));
        }
        this.period = samplePeriod;
    }

    /**
     * @return the sampling period i.e. the time between two samples of the shadow stack are taken.
     * @since 0.29
     */
    public synchronized long getPeriod() {
        return period;
    }

    /**
     * Sets the delay period i.e. the time that is allowed to pass before the sampler starts taking
     * samples.
     *
     * @param delay the delay period.
     * @since 0.29
     */
    public synchronized void setDelay(long delay) {
        verifyConfigAllowed();
        this.delay = delay;
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
     * @return size of the shadow stack
     * @since 0.29
     */
    public synchronized int getStackLimit() {
        return stackLimit;
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
     * @return The filter describing which part of the source code to sample
     * @since 0.29
     */
    public synchronized SourceSectionFilter getFilter() {
        return filter;
    }

    /**
     * @return Total number of samples taken during execution
     * @since 0.29
     */
    public long getTotalSamples() {
        return samplesTaken.get();
    }

    /**
     * @return was the shadow stack size insufficient for the execution.
     * @since 0.29
     */
    public boolean hasStackOverflowed() {
        return stackOverflowed;
    }

    /**
     * @return The roots of the trees representing the profile of the execution.
     * @since 0.29
     */
    public Collection<CallTreeNode<HitCounts>> getRootNodes() {
        return rootNode.getChildren();
    }

    /**
     * Erases all the data gathered by the sampler and resets the sample count to 0.
     *
     * @since 0.29
     */
    public synchronized void clearData() {
        samplesTaken.set(0);
        Map<SourceLocation, CallTreeNode<HitCounts>> rootChildren = rootNode.children;
        if (rootChildren != null) {
            rootChildren.clear();
        }
    }

    /**
     * @return whether or not the sampler has collected any data so far.
     * @since 0.29
     */
    public synchronized boolean hasData() {
        Map<SourceLocation, CallTreeNode<HitCounts>> rootChildren = rootNode.children;
        return rootChildren != null && !rootChildren.isEmpty();
    }

    /**
     * Closes the sampler for fuhrer use, deleting all the gathered data.
     *
     * @since 0.29
     */
    @Override
    public synchronized void close() {
        closed = true;
        resetSampling();
        clearData();
    }

    /**
     * Creates a histogram - a mapping from a {@link SourceLocation source location} to a
     * {@link List} of {@link CallTreeNode} corresponding to that source location. This gives an
     * overview of the execution profile of each {@link SourceLocation source location}.
     *
     * @return the source location histogram based on the sampling data
     * @since 0.29
     */
    public Map<SourceLocation, List<CallTreeNode<HitCounts>>> computeHistogram() {
        Map<SourceLocation, List<CallTreeNode<HitCounts>>> histogram = new HashMap<>();
        computeHistogramImpl(rootNode.getChildren(), histogram);
        return histogram;
    }

    private void computeHistogramImpl(Collection<CallTreeNode<HitCounts>> children, Map<SourceLocation, List<CallTreeNode<HitCounts>>> histogram) {
        for (CallTreeNode<HitCounts> treeNode : children) {
            List<CallTreeNode<HitCounts>> nodes = histogram.computeIfAbsent(treeNode.getSourceLocation(), new Function<SourceLocation, List<CallTreeNode<HitCounts>>>() {
                @Override
                public List<CallTreeNode<HitCounts>> apply(SourceLocation sourceLocation) {
                    return new ArrayList<>();
                }
            });
            nodes.add(treeNode);
            computeHistogramImpl(treeNode.getChildren(), histogram);
        }
    }

    private void resetSampling() {
        assert Thread.holdsLock(this);
        cleanup();

        if (!collecting || closed) {
            return;
        }

        if (samplerThread == null) {
            samplerThread = new Timer("Sampling thread", true);
        }

        SourceSectionFilter f = this.filter;
        if (f == null) {
            f = DEFAULT_FILTER;
        }
        this.stackOverflowed = false;
        this.shadowStack = new ShadowStack(stackLimit);
        this.stacksBinding = this.shadowStack.install(env.getInstrumenter(), f, excludeInlinedRoots);

        this.samplerTask = new SamplingTimerTask();
        this.samplerThread.schedule(samplerTask, 0, period);

    }

    private void cleanup() {
        assert Thread.holdsLock(this);
        if (stacksBinding != null) {
            stacksBinding.dispose();
            stacksBinding = null;
        }
        if (shadowStack != null) {
            shadowStack = null;
        }
        if (samplerTask != null) {
            samplerTask.cancel();
            samplerTask = null;
        }
        if (samplerThread != null) {
            samplerThread.cancel();
            samplerThread = null;
        }
    }

    private void verifyConfigAllowed() {
        assert Thread.holdsLock(this);
        if (closed) {
            throw new IllegalStateException("CPUSampler is already closed.");
        } else if (collecting) {
            throw new IllegalStateException("Cannot change sampler configuration while collecting. Call setCollecting(false) to disable collection first.");
        }
    }

    private class SamplingTimerTask extends TimerTask {

        int runcount = 0;

        @Override
        public void run() {
            runcount++;
            if (runcount < delay / period) {
                return;
            }
            long timestamp = System.currentTimeMillis();
            boolean sampleTaken = false;
            ShadowStack localShadowStack = shadowStack;
            if (localShadowStack != null) {
                for (ShadowStack.ThreadLocalStack stack : localShadowStack.getStacks()) {
                    sampleTaken |= sample(stack, timestamp);
                }
            }
            if (sampleTaken) {
                samplesTaken.incrementAndGet();
            }
        }

        boolean sample(ShadowStack.ThreadLocalStack stack, long timestamp) {
            if (stack.hasStackOverflowed()) {
                stackOverflowed = true;
                return false;
            }
            if (stack.getStackIndex() == -1) {
                // nothing on the stack
                return false;
            }
            final ShadowStack.ThreadLocalStack.CorrectedStackInfo correctedStackInfo = ShadowStack.ThreadLocalStack.CorrectedStackInfo.build(stack);
            if (correctedStackInfo == null || correctedStackInfo.getLength() == 0) {
                return false;
            }
            // now traverse the stack and insert the path into the tree
            CallTreeNode<HitCounts> treeNode = rootNode;
            for (int i = 0; i < correctedStackInfo.getLength(); i++) {
                SourceLocation location = correctedStackInfo.getStack()[i];
                boolean isCompiled = correctedStackInfo.getCompiledStack()[i];

                treeNode = addOrUpdateChild(timestamp, treeNode, location);
                HitCounts payload = treeNode.getPayload();
                payload.lastHitTime = timestamp;
                if (i == correctedStackInfo.getLength() - 1) {
                    // last element is counted as self time
                    if (isCompiled) {
                        payload.selfCompiledHitCount++;
                    } else {
                        payload.selfInterpretedHitCount++;
                    }
                }
                if (isCompiled) {
                    payload.compiledHitCount++;
                } else {
                    payload.interpretedHitCount++;
                }
            }
            return true;
        }

        private CallTreeNode<HitCounts> addOrUpdateChild(long timestamp, CallTreeNode<HitCounts> treeNode, SourceLocation location) {
            CallTreeNode<HitCounts> child = treeNode.findChild(location);
            if (child == null) {
                HitCounts payload = new HitCounts();
                payload.firstHitTime = timestamp;
                child = new CallTreeNode<>(treeNode, location, payload);
                treeNode.addChild(location, child);
            }
            return child;
        }
    }

    private static final class CLI extends ProfilerCLI {

        enum Output {
            HISTOGRAM,
            CALLTREE
        }

        enum Mode {
            COMPILED,
            ROOTS,
            STATEMENTS
        }

        static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Output",
                        Output.HISTOGRAM,
                        new Function<String, Output>() {
                            @Override
                            public Output apply(String s) {
                                try {
                                    return Output.valueOf(s.toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Output can be: histogram or calltree");
                                }
                            }
                        });

        static final OptionType<Mode> CLI_MODE_TYPE = new OptionType<>("Mode",
                        Mode.COMPILED,
                        new Function<String, Mode>() {
                            @Override
                            public Mode apply(String s) {
                                try {
                                    return Mode.valueOf(s.toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalArgumentException("Mode can be: compiled, roots or statements.");
                                }
                            }
                        });

        static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
        static final OptionKey<Mode> MODE = new OptionKey<>(Mode.COMPILED, CLI_MODE_TYPE);
        static final OptionKey<Long> SAMPLE_PERIOD = new OptionKey<>(1L);
        static final OptionKey<Long> DELAY_PERIOD = new OptionKey<>(0L);
        static final OptionKey<Integer> STACK_LIMIT = new OptionKey<>(10000);
        static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.HISTOGRAM, CLI_OUTPUT_TYPE);

        static final OptionKey<Object[]> FILTER_ROOT = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);
        static final OptionKey<Object[]> FILTER_FILE = new OptionKey<>(new Object[0], WILDCARD_FILTER_TYPE);
        static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");

        static final OptionKey<Boolean> SAMPLE_INTERNAL = new OptionKey<>(false);

        static void handleOutput(Env env, CPUSampler sampler, OptionDescriptors descriptors) {
            PrintStream out = new PrintStream(env.out());
            if (sampler.hasStackOverflowed()) {
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
                case HISTOGRAM:
                    printSamplingHistogram(out, sampler);
                    break;
                case CALLTREE:
                    printSamplingCallTree(out, sampler);
                    break;
            }
        }

        private static void printSamplingHistogram(PrintStream out, CPUSampler sampler) {

            final Map<SourceLocation, List<CallTreeNode<CPUSampler.HitCounts>>> histogram = sampler.computeHistogram();

            List<List<CallTreeNode<CPUSampler.HitCounts>>> lines = new ArrayList<>(histogram.values());
            Collections.sort(lines, new Comparator<List<CallTreeNode<HitCounts>>>() {
                @Override
                public int compare(List<CallTreeNode<HitCounts>> o1, List<CallTreeNode<HitCounts>> o2) {
                    long sum1 = 0;
                    for (CallTreeNode<CPUSampler.HitCounts> tree : o1) {
                        sum1 += tree.getPayload().getSelfHitCount();
                    }

                    long sum2 = 0;
                    for (CallTreeNode<CPUSampler.HitCounts> tree : o2) {
                        sum2 += tree.getPayload().getSelfHitCount();
                    }
                    return Long.compare(sum2, sum1);
                }
            });

            int maxLength = 10;
            for (List<CallTreeNode<CPUSampler.HitCounts>> line : lines) {
                maxLength = Math.max(computeRootNameMaxLength(line.get(0)), maxLength);
            }

            String title = String.format(" %-" + maxLength + "s |      Total Time     |  Opt %% ||       Self Time     |  Opt %% | Location             ", "Name");
            long samples = sampler.getTotalSamples();
            String sep = repeat("-", title.length());
            out.println(sep);
            out.println(String.format("Sampling Histogram. Recorded %s samples with period %dms", samples, sampler.getPeriod()));
            out.println("  Self Time: Time spent on the top of the stack.");
            out.println("  Total Time: Time the location spent on the stack. ");
            out.println("  Opt %: Percent of time spent in compiled and therfore non-interpreted code.");
            out.println(sep);
            out.println(title);
            out.println(sep);
            for (List<CallTreeNode<CPUSampler.HitCounts>> line : lines) {
                printAttributes(out, sampler, "", line, maxLength);
            }
            out.println(sep);
        }

        private static void printSamplingCallTree(PrintStream out, CPUSampler sampler) {
            int maxLength = Math.max(10, computeTitleMaxLength(sampler.getRootNodes(), 0));
            String title = String.format(" %-" + maxLength + "s |      Total Time     |  Opt %% ||       Self Time     |  Opt %% | Location             ", "Name");
            String sep = repeat("-", title.length());
            out.println(sep);
            out.println(String.format("Sampling CallTree. Recorded %s samples with period %dms.", sampler.getTotalSamples(), sampler.getPeriod()));
            out.println("  Self Time: Time spent on the top of the stack.");
            out.println("  Total Time: Time spent somewhere on the stack. ");
            out.println("  Opt %: Percent of time spent in compiled and therfore non-interpreted code.");
            out.println(sep);
            out.println(title);
            out.println(sep);
            printSamplingCallTreeRec(sampler, maxLength, "", sampler.getRootNodes(), out);
            out.println(sep);
        }

        private static void printSamplingCallTreeRec(CPUSampler sampler, int maxRootLength, String prefix, Collection<CallTreeNode<CPUSampler.HitCounts>> children, PrintStream out) {
            List<CallTreeNode<CPUSampler.HitCounts>> sortedChildren = new ArrayList<>(children);
            Collections.sort(sortedChildren, new Comparator<CallTreeNode<HitCounts>>() {
                @Override
                public int compare(CallTreeNode<HitCounts> o1, CallTreeNode<HitCounts> o2) {
                    return Long.compare(o2.getPayload().getHitCount(), o1.getPayload().getHitCount());
                }
            });

            for (CallTreeNode<CPUSampler.HitCounts> treeNode : sortedChildren) {
                if (treeNode == null) {
                    continue;
                }
                printAttributes(out, sampler, prefix, Arrays.asList(treeNode), maxRootLength);
                printSamplingCallTreeRec(sampler, maxRootLength, prefix + " ", treeNode.getChildren(), out);
            }
        }

        private static int computeTitleMaxLength(Collection<CallTreeNode<CPUSampler.HitCounts>> children, int baseLength) {
            int maxLength = baseLength;
            for (CallTreeNode<CPUSampler.HitCounts> treeNode : children) {
                int rootNameLength = computeRootNameMaxLength(treeNode);
                maxLength = Math.max(baseLength + rootNameLength, maxLength);
                maxLength = Math.max(maxLength, computeTitleMaxLength(treeNode.getChildren(), baseLength + 1));
            }
            return maxLength;
        }

        private static boolean intersectsLines(SourceSection section1, SourceSection section2) {
            int x1 = section1.getStartLine();
            int x2 = section1.getEndLine();
            int y1 = section2.getStartLine();
            int y2 = section2.getEndLine();
            return x2 >= y1 && y2 >= x1;
        }

        private static void printAttributes(PrintStream out, CPUSampler sampler, String prefix, List<CallTreeNode<CPUSampler.HitCounts>> nodes, int maxRootLength) {
            long samplePeriod = sampler.getPeriod();
            long samples = sampler.getTotalSamples();

            long selfInterpreted = 0;
            long selfCompiled = 0;
            long totalInterpreted = 0;
            long totalCompiled = 0;
            for (CallTreeNode<CPUSampler.HitCounts> tree : nodes) {
                CPUSampler.HitCounts payload = tree.getPayload();
                selfInterpreted += payload.getSelfInterpretedHitCount();
                selfCompiled += payload.getSelfCompiledHitCount();
                if (!tree.isRecursive()) {
                    totalInterpreted += payload.getInterpretedHitCount();
                    totalCompiled += payload.getCompiledHitCount();
                }
            }

            long totalSamples = totalInterpreted + totalCompiled;
            if (totalSamples <= 0L) {
                // hide methods without any cost
                return;
            }
            assert totalSamples < samples;
            CallTreeNode<CPUSampler.HitCounts> firstNode = nodes.get(0);
            SourceSection sourceSection = firstNode.getSourceSection();
            String rootName = firstNode.getRootName();

            if (!firstNode.getTags().contains(StandardTags.RootTag.class)) {
                rootName += "~" + formatIndices(sourceSection, needsColumnSpecifier(firstNode));
            }

            long selfSamples = selfInterpreted + selfCompiled;
            long selfTime = selfSamples * samplePeriod;
            double selfCost = selfSamples / (double) samples;
            double selfCompiledP = 0.0;
            if (selfSamples > 0) {
                selfCompiledP = selfCompiled / (double) selfSamples;
            }
            String selfTimes = String.format("%10dms %5.1f%% | %5.1f%%", selfTime, selfCost * 100, selfCompiledP * 100);

            long totalTime = totalSamples * samplePeriod;
            double totalCost = totalSamples / (double) samples;
            double totalCompiledP = totalCompiled / (double) totalSamples;
            String totalTimes = String.format("%10dms %5.1f%% | %5.1f%%", totalTime, totalCost * 100, totalCompiledP * 100);

            String location = getShortDescription(sourceSection);

            out.println(String.format(" %-" + Math.max(maxRootLength, 10) + "s | %s || %s | %s ", //
                            prefix + rootName, totalTimes, selfTimes, location));
        }

        private static boolean needsColumnSpecifier(CallTreeNode<CPUSampler.HitCounts> firstNode) {
            boolean needsColumnsSpecifier = false;
            SourceSection sourceSection = firstNode.getSourceSection();
            for (CallTreeNode<CPUSampler.HitCounts> node : firstNode.getParent().getChildren()) {
                if (node.getSourceSection() == sourceSection) {
                    continue;
                }
                if (intersectsLines(node.getSourceSection(), sourceSection)) {
                    needsColumnsSpecifier = true;
                    break;
                }
            }
            return needsColumnsSpecifier;
        }

        private static int computeRootNameMaxLength(CallTreeNode<CPUSampler.HitCounts> treeNode) {
            int length = treeNode.getRootName().length();
            if (!treeNode.getTags().contains(StandardTags.RootTag.class)) {
                // reserve some space for the line and column info
                length += formatIndices(treeNode.getSourceSection(), needsColumnSpecifier(treeNode)).length() + 1;
            }
            return length;
        }
    }
}
