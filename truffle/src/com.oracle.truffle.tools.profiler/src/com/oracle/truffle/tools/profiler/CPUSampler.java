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
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.tools.profiler.impl.CPUSamplerInstrument;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;
import com.oracle.truffle.tools.profiler.impl.SourceLocation;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
 * intervals, i.e. the state of the stack is copied and saved into trees of {@linkplain ProfilerNode
 * nodes}, which represent the profile of the execution.
 *
 * @since 0.29
 */
public final class CPUSampler implements Closeable {

    /**
     * Wrapper for information on how many times an element was seen on the shadow stack. Used as a
     * template parameter of {@link ProfilerNode}. Differentiates between an execution in compiled
     * code and in the interpreter.
     *
     * @since 0.29
     */
    public static final class HitCounts {

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

    private final ProfilerNode<HitCounts> rootNode = new ProfilerNode<>(this, new HitCounts());

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
    public Collection<ProfilerNode<HitCounts>> getRootNodes() {
        return rootNode.getChildren();
    }

    /**
     * Erases all the data gathered by the sampler and resets the sample count to 0.
     *
     * @since 0.29
     */
    public synchronized void clearData() {
        samplesTaken.set(0);
        Map<SourceLocation, ProfilerNode<HitCounts>> rootChildren = rootNode.children;
        if (rootChildren != null) {
            rootChildren.clear();
        }
    }

    /**
     * @return whether or not the sampler has collected any data so far.
     * @since 0.29
     */
    public synchronized boolean hasData() {
        Map<SourceLocation, ProfilerNode<HitCounts>> rootChildren = rootNode.children;
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
     * {@link List} of {@link ProfilerNode} corresponding to that source location. This gives an
     * overview of the execution profile of each {@link SourceLocation source location}.
     *
     * @return the source location histogram based on the sampling data
     * @since 0.29
     */
    public Map<SourceLocation, List<ProfilerNode<HitCounts>>> computeHistogram() {
        Map<SourceLocation, List<ProfilerNode<HitCounts>>> histogram = new HashMap<>();
        computeHistogramImpl(rootNode.getChildren(), histogram);
        return histogram;
    }

    private void computeHistogramImpl(Collection<ProfilerNode<HitCounts>> children, Map<SourceLocation, List<ProfilerNode<HitCounts>>> histogram) {
        for (ProfilerNode<HitCounts> treeNode : children) {
            List<ProfilerNode<HitCounts>> nodes = histogram.computeIfAbsent(treeNode.getSourceLocation(), new Function<SourceLocation, List<ProfilerNode<HitCounts>>>() {
                @Override
                public List<ProfilerNode<HitCounts>> apply(SourceLocation sourceLocation) {
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
            ProfilerNode<HitCounts> treeNode = rootNode;
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

        private ProfilerNode<HitCounts> addOrUpdateChild(long timestamp, ProfilerNode<HitCounts> treeNode, SourceLocation location) {
            ProfilerNode<HitCounts> child = treeNode.findChild(location);
            if (child == null) {
                HitCounts payload = new HitCounts();
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
