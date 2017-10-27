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
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.tools.profiler.impl.CPUSamplerInstrument;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of a sampling based profiler for
 * {@linkplain com.oracle.truffle.api.TruffleLanguage Truffle languages} built on top of the
 * {@linkplain TruffleInstrument Truffle instrumentation framework}.
 * <p>
 * The sampler keeps a shadow stack during execution. This shadow stack is sampled at regular
 * intervals, i.e. the state of the stack is copied and saved into trees of {@linkplain ProfilerNode
 * nodes}, which represent the profile of the execution.
 *
 * @since 0.30
 */
public final class CPUSampler implements Closeable {

    /**
     * Wrapper for information on how many times an element was seen on the shadow stack. Used as a
     * template parameter of {@link ProfilerNode}. Differentiates between an execution in compiled
     * code and in the interpreter.
     *
     * @since 0.30
     */
    public static final class Payload {

        Payload() {
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
         * @since 0.30
         */
        public int getCompiledHitCount() {
            return compiledHitCount;
        }

        /**
         * @return The number of times the element was found bellow the top of the shadow stack as
         *         interpreted code
         * @since 0.30
         */
        public int getInterpretedHitCount() {
            return interpretedHitCount;
        }

        /**
         * @return The number of times the element was found on the top of the shadow stack as
         *         compiled code
         * @since 0.30
         */
        public int getSelfCompiledHitCount() {
            return selfCompiledHitCount;
        }

        /**
         * @return The number of times the element was found on the top of the shadow stack as
         *         interpreted code
         * @since 0.30
         */
        public int getSelfInterpretedHitCount() {
            return selfInterpretedHitCount;
        }

        /**
         * @return Total number of times the element was found on the top of the shadow stack
         * @since 0.30
         */
        public int getSelfHitCount() {
            return selfCompiledHitCount + selfInterpretedHitCount;
        }

        /**
         * @return Total number of times the element was found bellow the top of the shadow stack
         * @since 0.30
         */
        public int getHitCount() {
            return compiledHitCount + interpretedHitCount;
        }

    }

    /**
     * Describes the different modes in which the CPU sampler can operate.
     *
     * @since 0.30
     */
    public enum Mode {
        /**
         * Sample {@link RootTag Roots} <b>excluding</b> the ones that get inlined during
         * compilation. This mode is the default and has the least amount of impact on peak
         * performance.
         *
         * @since 0.30
         */
        COMPILED,
        /**
         * Sample {@link RootTag Roots} <b>including</b> the ones that get inlined during
         * compilation.
         *
         * @since 0.30
         */
        ROOTS,
        /**
         * Sample all {@link com.oracle.truffle.api.instrumentation.StandardTags.StatementTag
         * Statements}. This mode has serious impact on peek performance.
         *
         * @since 0.30
         */
        STATEMENTS
    }

    private Mode mode = Mode.COMPILED;

    static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().tagIs(RootTag.class).build();

    private volatile boolean closed;

    private volatile boolean collecting;

    private long period = 1;

    private long delay = 0;

    private int stackLimit = 10000;

    private SourceSectionFilter filter;

    private boolean stackOverflowed = false;

    private AtomicLong samplesTaken = new AtomicLong(0);

    private Timer samplerThread;

    private TimerTask samplerTask;

    private ShadowStack shadowStack;

    private EventBinding<?> stacksBinding;

    private final ProfilerNode<Payload> rootNode = new ProfilerNode<>(this, new Payload());

    private final Env env;

    CPUSampler(Env env) {
        this.env = env;
    }

    /**
     * Finds {@link CPUSampler} associated with given engine.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated {@link CPUSampler}
     * @since 0.30
     */
    public static CPUSampler find(PolyglotEngine engine) {
        return CPUSamplerInstrument.getSampler(engine);
    }

    /**
     * Controls whether the sampler is collecting data or not.
     *
     * @param collecting the new state of the sampler.
     * @since 0.30
     */
    public synchronized void setCollecting(boolean collecting) {
        if (this.collecting != collecting) {
            this.collecting = collecting;
            resetSampling();
        }
    }

    /**
     * @return whether or not the sampler is currently collecting data.
     * @since 0.30
     */
    public synchronized boolean isCollecting() {
        return collecting;
    }

    /**
     * Sets the {@link Mode mode} for the sampler.
     * 
     * @param mode the new mode for the sampler.
     * @since 0.30
     */
    public synchronized void setMode(Mode mode) {
        verifyConfigAllowed();
        this.mode = mode;
    }

    /**
     * Sets the sampling period i.e. the time between two samples of the shadow stack are taken.
     *
     * @param samplePeriod the new sampling period.
     * @since 0.30
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
     * @since 0.30
     */
    public synchronized long getPeriod() {
        return period;
    }

    /**
     * Sets the delay period i.e. the time that is allowed to pass between when the first sample
     * would have been taken and when the sampler actually starts taking samples.
     *
     * @param delay the delay period.
     * @since 0.30
     */
    public synchronized void setDelay(long delay) {
        verifyConfigAllowed();
        this.delay = delay;
    }

    /**
     * Sets the maximum amount of stack frames that are sampled. Whether or not the stack grew more
     * than the provided size during execution can be checked with {@linkplain #hasStackOverflowed}
     *
     * @param stackLimit the new size of the shadow stack
     * @since 0.30
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
     * @since 0.30
     */
    public synchronized int getStackLimit() {
        return stackLimit;
    }

    /**
     * Sets the {@link SourceSectionFilter filter} for the sampler. The sampler will only observe
     * parts of the executed source code that is specified by the filter.
     *
     * @param filter The new filter describing which part of the source code to sample
     * @since 0.30
     */
    public synchronized void setFilter(SourceSectionFilter filter) {
        verifyConfigAllowed();
        this.filter = filter;
    }

    /**
     * @return The filter describing which part of the source code to sample
     * @since 0.30
     */
    public synchronized SourceSectionFilter getFilter() {
        return filter;
    }

    /**
     * @return Total number of samples taken during execution
     * @since 0.30
     */
    public long getSampleCount() {
        return samplesTaken.get();
    }

    /**
     * @return was the shadow stack size insufficient for the execution.
     * @since 0.30
     */
    public boolean hasStackOverflowed() {
        return stackOverflowed;
    }

    /**
     * @return The roots of the trees representing the profile of the execution.
     * @since 0.30
     */
    public Collection<ProfilerNode<Payload>> getRootNodes() {
        return rootNode.getChildren();
    }

    /**
     * Erases all the data gathered by the sampler and resets the sample count to 0.
     *
     * @since 0.30
     */
    public synchronized void clearData() {
        samplesTaken.set(0);
        Map<SourceLocation, ProfilerNode<Payload>> rootChildren = rootNode.children;
        if (rootChildren != null) {
            rootChildren.clear();
        }
    }

    /**
     * @return whether or not the sampler has collected any data so far.
     * @since 0.30
     */
    public synchronized boolean hasData() {
        Map<SourceLocation, ProfilerNode<Payload>> rootChildren = rootNode.children;
        return rootChildren != null && !rootChildren.isEmpty();
    }

    /**
     * Closes the sampler for fuhrer use, deleting all the gathered data.
     *
     * @since 0.30
     */
    @Override
    public synchronized void close() {
        closed = true;
        resetSampling();
        clearData();
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
        this.stacksBinding = this.shadowStack.install(env.getInstrumenter(), combine(f, mode), mode == Mode.COMPILED);

        this.samplerTask = new SamplingTimerTask();
        this.samplerThread.schedule(samplerTask, 0, period);

    }

    private static SourceSectionFilter combine(SourceSectionFilter filter, Mode mode) {
        List<Class<?>> tags = new ArrayList<>();
        if (mode == Mode.COMPILED || mode == Mode.ROOTS) {
            tags.add(StandardTags.RootTag.class);
        }
        if (mode == Mode.STATEMENTS) {
            tags.add(StandardTags.StatementTag.class);
        }
        return SourceSectionFilter.newBuilder().tagIs(tags.toArray(new Class<?>[0])).and(filter).build();
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
            ProfilerNode<Payload> treeNode = rootNode;
            for (int i = 0; i < correctedStackInfo.getLength(); i++) {
                SourceLocation location = correctedStackInfo.getStack()[i];
                boolean isCompiled = correctedStackInfo.getCompiledStack()[i];

                treeNode = addOrUpdateChild(timestamp, treeNode, location);
                Payload payload = treeNode.getPayload();
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

        private ProfilerNode<Payload> addOrUpdateChild(long timestamp, ProfilerNode<Payload> treeNode, SourceLocation location) {
            ProfilerNode<Payload> child = treeNode.findChild(location);
            if (child == null) {
                Payload payload = new Payload();
                payload.firstHitTime = timestamp;
                child = new ProfilerNode<>(treeNode, location, payload);
                treeNode.addChild(location, child);
            }
            return child;
        }
    }

    static {
        CPUSamplerInstrument.setFactory(new ProfilerToolFactory<CPUSampler>() {
            @Override
            public CPUSampler create(Env env) {
                return new CPUSampler(env);
            }
        });
    }
}
