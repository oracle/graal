/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.profiler.SafepointStackSampler.StackSample;
import com.oracle.truffle.tools.profiler.impl.CPUSamplerInstrument;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;

/**
 * Implementation of a sampling based profiler for
 * {@linkplain com.oracle.truffle.api.TruffleLanguage Truffle languages} built on top of the
 * {@linkplain TruffleSafepoint Truffle safepoints} and
 * {@link TruffleRuntime#iterateFrames(FrameInstanceVisitor) iterateFrames()}.
 * <p>
 * The sampler samples the stack of each thread at regular intervals by using Truffle safepoints.
 * The state of the stack is copied and saved into trees of {@linkplain ProfilerNode nodes}, which
 * represent the profile of the execution.
 * <p>
 * Usage example: {@codesnippet CPUSamplerSnippets#example}
 *
 * @since 0.30
 */
public final class CPUSampler implements Closeable {

    static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().tagIs(RootTag.class).build();
    private static final Function<Payload, Payload> COPY_PAYLOAD = new Function<>() {
        @Override
        public Payload apply(Payload sourcePayload) {
            Payload destinationPayload = new Payload();
            destinationPayload.selfTierCount = Arrays.copyOf(sourcePayload.selfTierCount, sourcePayload.selfTierCount.length);
            destinationPayload.tierCount = Arrays.copyOf(sourcePayload.tierCount, sourcePayload.tierCount.length);
            for (Long timestamp : sourcePayload.getSelfHitTimes()) {
                destinationPayload.addSelfHitTime(timestamp);
            }
            return destinationPayload;
        }
    };

    static ProfilerToolFactory<CPUSampler> createFactory() {
        return new ProfilerToolFactory<>() {
            @Override
            public CPUSampler create(Env env) {
                return new CPUSampler(env);
            }
        };
    }

    private final Env env;

    private int nextContextIndex;
    private final Map<TruffleContext, Integer> activeContexts = new WeakHashMap<>();
    private final List<MutableSamplerData> samplerData = new ArrayList<>();
    private volatile boolean closed;
    private volatile boolean collecting;
    private long period = 10;
    private long delay = 0;
    private int stackLimit = 10000;
    private boolean sampleContextInitialization = false;
    private SourceSectionFilter filter = DEFAULT_FILTER;
    private ScheduledExecutorService samplerExecutionService;
    private ExecutorService processingExecutionService;
    private ResultProcessingRunnable processingThreadRunnable;
    private Future<?> processingThreadFuture;
    private Future<?> samplerFuture;

    private volatile SafepointStackSampler safepointStackSampler = new SafepointStackSampler(stackLimit, filter);
    private boolean gatherSelfHitTimes = false;

    /*
     * The results queue will block if it exceeds the capacity. This is intentional as we do not
     * want the sampling results to grow infinitely in the worst case. It is better if the sampling
     * thread blocks at some point as well. However, it is very unlikely that processing takes such
     * a long time that this actually happens, but situations like this might be more frequent when
     * debugging.
     */
    private final ArrayBlockingQueue<SamplingResult> resultsToProcess = new ArrayBlockingQueue<>(256);

    CPUSampler(Env env) {
        this.env = env;

        env.getInstrumenter().attachContextsListener(new ContextsListener() {

            @Override
            public void onContextCreated(TruffleContext context) {
                synchronized (CPUSampler.this) {
                    int contextIndex = nextContextIndex++;
                    activeContexts.put(context, contextIndex);
                    samplerData.add(new MutableSamplerData(contextIndex));
                }
            }

            @Override
            public void onLanguageContextCreate(TruffleContext context, LanguageInfo language) {
                // no code is allowed to run during context creation
            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
                // no code is allowed to run during context creation
            }

            @Override
            public void onLanguageContextCreateFailed(TruffleContext context, LanguageInfo language) {
                // no code is allowed to run during context creation
            }

            @Override
            public void onLanguageContextInitialize(TruffleContext context, LanguageInfo language) {
                safepointStackSampler.pushSyntheticFrame(language, "initializeContext");
            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                safepointStackSampler.popSyntheticFrame();
            }

            @Override
            public void onLanguageContextInitializeFailed(TruffleContext context, LanguageInfo language) {
                safepointStackSampler.popSyntheticFrame();
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onContextClosed(TruffleContext context) {
                // TODO GR-32021 make the results of CPUSampler per-context.
            }
        }, true);
    }

    /**
     * Finds {@link CPUSampler} associated with given engine.
     *
     * @since 19.0
     */
    public static CPUSampler find(Engine engine) {
        return CPUSamplerInstrument.getSampler(engine);
    }

    /**
     * @return whether or not the sampler is currently collecting data.
     * @since 0.30
     */
    public synchronized boolean isCollecting() {
        return collecting;
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
     * @return the sampling period i.e. the time between two samples of the stack are taken, in
     *         milliseconds.
     * @since 0.30
     */
    public synchronized long getPeriod() {
        return period;
    }

    /**
     * Sets the sampling period i.e. the time between two samples of the stack are taken, in
     * milliseconds.
     *
     * @param samplePeriod the new sampling period.
     * @since 0.30
     */
    public synchronized void setPeriod(long samplePeriod) {
        enterChangeConfig();
        if (samplePeriod < 1) {
            throw new ProfilerException(format("Invalid sample period %s.", samplePeriod));
        }
        this.period = samplePeriod;
    }

    /**
     * Sets the delay period i.e. the time that is allowed to pass between when the first sample
     * would have been taken and when the sampler actually starts taking samples.
     *
     * @param delay the delay period.
     * @since 0.30
     */
    public synchronized void setDelay(long delay) {
        enterChangeConfig();
        if (delay < 0) {
            throw new ProfilerException(format("Invalid delay %s.", delay));
        }
        this.delay = delay;
    }

    /**
     * @return the maximum amount of stack frames that are sampled.
     * @since 0.30
     */
    public synchronized int getStackLimit() {
        return stackLimit;
    }

    /**
     * Sets the maximum amount of stack frames that are sampled. Whether or not the stack grew more
     * than the provided size during execution can be checked with {@linkplain #hasStackOverflowed}
     *
     * @param stackLimit the maximum amount of stack frames that are sampled
     * @since 0.30
     */
    public synchronized void setStackLimit(int stackLimit) {
        enterChangeConfig();
        if (stackLimit < 1) {
            throw new ProfilerException(format("Invalid stack limit %s.", stackLimit));
        }
        this.stackLimit = stackLimit;
    }

    /**
     * Enables or disables the sampling of the time spent during context initialization. If
     * <code>true</code> code executed during context initialization is included in the general
     * profile instead of grouping it into a single entry by default. If <code>false</code> a single
     * entry will be created that contains all time spent in initialization. This can be useful to
     * avoid polluting the general application profile with sampled stack frames that only run
     * during initialization.
     *
     * @since 21.3
     */
    public synchronized void setSampleContextInitialization(boolean enabled) {
        enterChangeConfig();
        this.sampleContextInitialization = enabled;
    }

    /**
     * @return The filter describing which part of the source code to sample
     * @since 0.30
     */
    public synchronized SourceSectionFilter getFilter() {
        return filter;
    }

    /**
     * Sets the {@link SourceSectionFilter filter} for the sampler. The sampler will only observe
     * parts of the executed source code that is specified by the filter.
     *
     * @param filter The new filter describing which part of the source code to sample
     * @since 0.30
     */
    public synchronized void setFilter(SourceSectionFilter filter) {
        enterChangeConfig();
        this.filter = filter;
    }

    /**
     * @return was the the maximum amount of stack frames that are sampled insufficient for the
     *         execution.
     * @since 0.30
     */
    public boolean hasStackOverflowed() {
        return safepointStackSampler.hasOverflowed();
    }

    /**
     * Get per-context profiling data. The profiling data is an unmodifiable map from
     * {@link TruffleContext} to {@link CPUSamplerData}. It is collected based on the configuration
     * of the {@link CPUSampler} (e.g. {@link #setFilter(SourceSectionFilter)},
     * {@link #setPeriod(long)}, etc.) and collecting can be controlled by the
     * {@link #setCollecting(boolean)}. The collected data can be cleared from the cpusampler using
     * {@link #clearData()}
     *
     * @return a map from {@link TruffleContext} to {@link CPUSamplerData}. The contexts that were
     *         already collected are not in the returned map even though data was collected for
     *         them. All collected data can be obtained using {@link CPUSampler#getDataList()}.
     * @since 21.3.0
     *
     * @deprecated in 23.1.4. Contexts are no longer stored permanently. Use {@link #getDataList()}
     *             to get all sampler data.
     */
    @Deprecated
    public synchronized Map<TruffleContext, CPUSamplerData> getData() {
        List<CPUSamplerData> dataList = getDataList();
        if (dataList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<TruffleContext, CPUSamplerData> contextToData = new HashMap<>();
        for (Map.Entry<TruffleContext, Integer> contextEntry : activeContexts.entrySet()) {
            contextToData.put(contextEntry.getKey(), dataList.get(contextEntry.getValue()));
        }
        return Collections.unmodifiableMap(contextToData);
    }

    /**
     * Get per-context profiling data. Context objects are not stored, the profiling data is an
     * unmodifiable list of {@link CPUSamplerData}. It is collected based on the configuration of
     * the {@link CPUSampler} (e.g. {@link #setFilter(SourceSectionFilter)},
     * {@link #setPeriod(long)}, etc.) and collecting can be controlled by the
     * {@link #setCollecting(boolean)}. The collected data can be cleared from the cpusampler using
     * {@link #clearData()}
     *
     * @return a list of {@link CPUSamplerData} where each element corresponds to one context.
     * @since 23.1.4
     */
    public synchronized List<CPUSamplerData> getDataList() {
        if (samplerData.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, TruffleContext> availableContexts = new HashMap<>();
        for (Map.Entry<TruffleContext, Integer> contextEntry : activeContexts.entrySet()) {
            availableContexts.put(contextEntry.getValue(), contextEntry.getKey());
        }
        List<CPUSamplerData> dataList = new ArrayList<>();
        for (MutableSamplerData mutableSamplerData : this.samplerData) {
            Map<Thread, Collection<ProfilerNode<Payload>>> threads = new HashMap<>();
            for (Map.Entry<Thread, ProfilerNode<Payload>> threadEntry : mutableSamplerData.threadData.entrySet()) {
                ProfilerNode<Payload> copy = new ProfilerNode<>();
                copy.deepCopyChildrenFrom(threadEntry.getValue(), COPY_PAYLOAD);
                threads.put(threadEntry.getKey(), copy.getChildren());
            }
            dataList.add(new CPUSamplerData(mutableSamplerData.index, availableContexts.get(mutableSamplerData.index), threads, mutableSamplerData.biasStatistic, mutableSamplerData.durationStatistic,
                            mutableSamplerData.samplesTaken.get(), period,
                            mutableSamplerData.missedSamples.get()));
        }
        return Collections.unmodifiableList(dataList);
    }

    /**
     * Erases all the data gathered by the sampler and resets the sample count to 0.
     *
     * @since 0.30
     */
    public synchronized void clearData() {
        for (ListIterator<MutableSamplerData> dataIterator = samplerData.listIterator(); dataIterator.hasNext();) {
            dataIterator.set(new MutableSamplerData(dataIterator.next().index));
        }
    }

    /**
     * @return whether or not the sampler has collected any data so far.
     * @since 0.30
     */
    public synchronized boolean hasData() {
        for (MutableSamplerData mutableSamplerData : samplerData) {
            if (mutableSamplerData.samplesTaken.get() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes the sampler for further use, deleting all the gathered data.
     *
     * @since 0.30
     */
    @Override
    public void close() {
        List<ExecutorService> toShutdown = new ArrayList<>(2);
        synchronized (this) {
            closed = true;
            resetSampling();
            clearData();
            if (samplerExecutionService != null) {
                toShutdown.add(samplerExecutionService);
            }
            if (processingExecutionService != null) {
                toShutdown.add(processingExecutionService);
            }
        }
        // Shutdown and await termination without holding a lock.
        for (ExecutorService executorService : toShutdown) {
            executorService.shutdownNow();
            while (true) {
                try {
                    if (executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)) {
                        break;
                    } else {
                        throw new RuntimeException("Failed to shutdown background threads.");
                    }
                } catch (InterruptedException ie) {
                    // continue to awaitTermination
                }
            }
        }
    }

    /**
     * @return Whether or not timestamp information for the element at the top of the stack for each
     *         sample is gathered
     * @since 0.30
     */
    public boolean isGatherSelfHitTimes() {
        return gatherSelfHitTimes;
    }

    /**
     * Sets whether or not to gather timestamp information for the element at the top of the stack
     * for each sample.
     *
     * @param gatherSelfHitTimes new value for whether or not to gather timestamps
     * @since 0.30
     */
    public synchronized void setGatherSelfHitTimes(boolean gatherSelfHitTimes) {
        enterChangeConfig();
        this.gatherSelfHitTimes = gatherSelfHitTimes;
    }

    /**
     * Sample all threads and gather their current stack trace entries with a default time out.
     * Short hand for: {@link #takeSample(long, TimeUnit) takeSample}(this.getPeriod(),
     * TimeUnit.MILLISECONDS).
     *
     * @see #takeSample(long, TimeUnit)
     * @since 19.0
     */
    public Map<Thread, List<StackTraceEntry>> takeSample() {
        return takeSample(this.period, TimeUnit.MILLISECONDS);
    }

    /**
     * Sample all threads and gather their current stack trace entries. The returned map and lists
     * are unmodifiable and represent atomic snapshots of the stack at the time when this method was
     * invoked. Only active threads are sampled. A thread is active if it has at least one entry on
     * the stack. The sampling is initialized if this method is invoked for the first time or
     * reinitialized if the configuration changes.
     * <p>
     * If the given timeout is exceeded the sampling will be stopped. If a timeout occurs it may
     * lead to an incomplete or empty result. For example, if only one thread times out the result
     * of other threads may still be reported.
     *
     * @since 22.0
     */
    public Map<Thread, List<StackTraceEntry>> takeSample(long timeout, TimeUnit timeoutUnit) {
        synchronized (CPUSampler.this) {
            if (safepointStackSampler == null) {
                this.safepointStackSampler = new SafepointStackSampler(stackLimit, filter);
            }
            if (activeContexts.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<TruffleContext, MutableSamplerData> contexts = new LinkedHashMap<>();
            for (TruffleContext context : activeContexts.keySet()) {
                if (context.isActive()) {
                    throw new IllegalArgumentException("Cannot sample a context that is currently active on the current thread.");
                }
                if (!context.isClosed()) {
                    contexts.put(context, samplerData.get(activeContexts.get(context)));
                }
            }
            if (!contexts.isEmpty()) {
                Map<Thread, List<StackTraceEntry>> stacks = new HashMap<>();
                List<StackSample> sample = safepointStackSampler.sample(env, contexts, !sampleContextInitialization, timeout, timeoutUnit);
                for (StackSample stackSample : sample) {
                    stacks.put(stackSample.thread, stackSample.stack);
                }
                return Collections.unmodifiableMap(stacks);
            } else {
                return Collections.emptyMap();
            }
        }
    }

    private void resetSampling() {
        assert Thread.holdsLock(this);
        cleanup();

        if (!collecting || closed) {
            return;
        }
        if (processingExecutionService == null) {
            processingExecutionService = JoinableExecutors.newSingleThreadExecutor((r) -> {
                Thread t = env.createSystemThread(r);
                t.setDaemon(true);
                t.setName("Sampling Processing Thread");
                return t;
            });
        }
        assert processingThreadRunnable == null;
        assert processingThreadFuture == null;
        processingThreadRunnable = new ResultProcessingRunnable();
        processingThreadFuture = processingExecutionService.submit(processingThreadRunnable, null);

        if (samplerExecutionService == null) {
            samplerExecutionService = JoinableExecutors.newSingleThreadScheduledExecutor((r) -> {
                Thread t = env.createSystemThread(r);
                t.setDaemon(true);
                t.setName("Sampling thread");
                return t;
            });
        }
        this.safepointStackSampler = new SafepointStackSampler(stackLimit, filter);
        assert samplerFuture == null;
        samplerFuture = samplerExecutionService.scheduleAtFixedRate(new SamplingTask(), delay, period, TimeUnit.MILLISECONDS);
    }

    private void cleanup() {
        assert Thread.holdsLock(this);
        if (samplerFuture != null) {
            samplerFuture.cancel(false);
            samplerFuture = null;
        }
        if (processingThreadFuture != null) {
            processingThreadRunnable.cancelled = true;
            processingThreadFuture.cancel(true);
            processingThreadRunnable = null;
            processingThreadFuture = null;
        }
    }

    private void enterChangeConfig() {
        assert Thread.holdsLock(this);
        if (closed) {
            throw new ProfilerException("CPUSampler is already closed.");
        } else if (collecting) {
            throw new ProfilerException("Cannot change sampler configuration while collecting. Call setCollecting(false) to disable collection first.");
        }
    }

    private synchronized TruffleContext[] contexts() {
        return activeContexts.keySet().toArray(TruffleContext[]::new);
    }

    /**
     * Wrapper for information on how many times an element was seen on the stack. Used as a
     * template parameter of {@link ProfilerNode}. Differentiates between an execution in compiled
     * code and in the interpreter.
     *
     * @since 0.30
     */
    public static final class Payload {

        final List<Long> selfHitTimes = new ArrayList<>();
        int[] tierCount = new int[0];
        int[] selfTierCount = new int[0];

        Payload() {
        }

        /**
         * @return The number of compilation tiers this element was recorded in. Tier 0 is the
         *         interpreter.
         * @since 21.3.0
         */
        public int getNumberOfTiers() {
            return Math.max(selfTierCount.length, tierCount.length);
        }

        /**
         * @return The number of times this element was recorded on top of the stack, executing in
         *         the given compilation tier.
         * @since 21.3.0
         */
        public int getTierSelfCount(int tier) {
            if (tier >= selfTierCount.length) {
                return 0;
            }
            return selfTierCount[tier];
        }

        /**
         * @return The number of times this element was recorded anywhere on the stack, executing in
         *         the given compilation tier.
         * @since 21.3.0
         */
        public int getTierTotalCount(int tier) {
            if (tier >= tierCount.length) {
                return 0;
            }
            return tierCount[tier];
        }

        /**
         * @return Total number of times the element was found on the top of the stack
         * @since 0.30
         */
        public int getSelfHitCount() {
            int sum = 0;
            for (int count : selfTierCount) {
                sum += count;
            }
            return sum;
        }

        /**
         * @return Total number of times the element was found bellow the top of the stack
         * @since 0.30
         */
        public int getHitCount() {
            int sum = 0;
            for (int count : tierCount) {
                sum += count;
            }
            return sum;
        }

        /**
         * @return An immutable list of time stamps for the times that the element was on the top of
         *         the stack
         * @since 0.30
         */
        public List<Long> getSelfHitTimes() {
            return Collections.unmodifiableList(selfHitTimes);
        }

        void addSelfHitTime(Long time) {
            selfHitTimes.add(time);
        }
    }

    /*
     * Process samples in a separate thread to avoid further delays during sampling and increase
     * accuracy.
     */
    private class ResultProcessingRunnable implements Runnable {

        private volatile boolean cancelled;

        public void run() {
            while (true) {
                if (cancelled) {
                    return;
                }
                SamplingResult result = null;
                try {
                    result = resultsToProcess.take();
                } catch (InterruptedException e) {
                    // check for cancelled
                }
                if (cancelled) {
                    return;
                }
                if (result != null) {
                    if (result.context.isClosed()) {
                        continue;
                    }
                    synchronized (CPUSampler.this) {
                        if (!collecting) {
                            return;
                        }
                        final MutableSamplerData mutableSamplerData = samplerData.get(activeContexts.get(result.context));
                        for (StackSample sample : result.samples) {
                            mutableSamplerData.biasStatistic.accept(sample.biasNs);
                            mutableSamplerData.durationStatistic.accept(sample.durationNs);
                            ProfilerNode<Payload> threadNode = mutableSamplerData.threadData.computeIfAbsent(sample.thread, new Function<Thread, ProfilerNode<Payload>>() {
                                @Override
                                public ProfilerNode<Payload> apply(Thread thread) {
                                    return new ProfilerNode<>();
                                }
                            });
                            record(sample, threadNode, result.startTime, mutableSamplerData);
                        }
                    }
                }
            }
        }

        private void record(StackSample sample, ProfilerNode<Payload> threadNode, long timestamp, MutableSamplerData mutableSamplerData) {
            if (sample.stack.size() == 0) {
                return;
            }
            if (syntheticOnly(sample)) {
                return;
            }
            ProfilerNode<Payload> treeNode = threadNode;
            for (int i = sample.stack.size() - 1; i >= 0; i--) {
                StackTraceEntry location = sample.stack.get(i);
                treeNode = addOrUpdateChild(treeNode, location);
                Payload payload = treeNode.getPayload();
                recordCompilationInfo(location, payload, i == 0, timestamp);
            }
            mutableSamplerData.samplesTaken.incrementAndGet();
        }

        private boolean syntheticOnly(StackSample sample) {
            for (StackTraceEntry entry : sample.stack) {
                if (!entry.isSynthetic()) {
                    return false;
                }
            }
            return true;
        }

        private void recordCompilationInfo(StackTraceEntry location, Payload payload, boolean topOfStack, long timestamp) {
            int tier = location.getTier();
            if (topOfStack) {
                if (payload.selfTierCount.length < tier + 1) {
                    payload.selfTierCount = Arrays.copyOf(payload.selfTierCount, tier + 1);
                }
                payload.selfTierCount[tier]++;
                if (gatherSelfHitTimes) {
                    payload.selfHitTimes.add(timestamp);
                    assert payload.selfHitTimes.size() == payload.getSelfHitCount();
                }
            }
            if (payload.tierCount.length < tier + 1) {
                payload.tierCount = Arrays.copyOf(payload.tierCount, tier + 1);
            }
            payload.tierCount[tier]++;
        }

        private ProfilerNode<Payload> addOrUpdateChild(ProfilerNode<Payload> treeNode, StackTraceEntry location) {
            ProfilerNode<Payload> child = treeNode.findChild(location);
            if (child == null) {
                Payload payload = new Payload();
                child = new ProfilerNode<>(treeNode, location, payload);
                treeNode.addChild(location, child);
            }
            return child;
        }

    }

    static class SamplingResult {

        final List<StackSample> samples;
        final TruffleContext context;
        final long startTime;

        SamplingResult(List<StackSample> samples, TruffleContext context, long startTime) {
            this.samples = samples;
            this.context = context;
            this.startTime = startTime;
        }

    }

    private class SamplingTask implements Runnable {

        @Override
        public void run() {
            long taskStartTime = System.currentTimeMillis();
            for (TruffleContext context : contexts()) {
                if (context.isClosed()) {
                    continue;
                }
                MutableSamplerData data;
                synchronized (CPUSampler.this) {
                    data = samplerData.get(activeContexts.get(context));
                }
                List<StackSample> samples = safepointStackSampler.sample(env, Collections.singletonMap(context, data), !sampleContextInitialization, period,
                                TimeUnit.MILLISECONDS);
                resultsToProcess.add(new SamplingResult(samples, context, taskStartTime));
            }
        }

    }

    static class MutableSamplerData {
        final int index;
        final Map<Thread, ProfilerNode<Payload>> threadData = new HashMap<>();
        final AtomicLong samplesTaken = new AtomicLong(0);
        final LongSummaryStatistics biasStatistic = new LongSummaryStatistics(); // nanoseconds
        final LongSummaryStatistics durationStatistic = new LongSummaryStatistics(); // nanoseconds
        final AtomicLong missedSamples = new AtomicLong(0);

        MutableSamplerData(int index) {
            this.index = index;
        }
    }

    private static String format(String format, Object... args) {
        return String.format(Locale.ENGLISH, format, args);
    }

}

class CPUSamplerSnippets {

    @SuppressWarnings("unused")
    public void example() {
        // @formatter:off
        // BEGIN: CPUSamplerSnippets#example
        Context context = Context.create();

        CPUSampler sampler = CPUSampler.find(context.getEngine());
        sampler.setCollecting(true);
        context.eval("...", "...");
        sampler.setCollecting(false);
        sampler.close();
        // Read information about the roots of the tree per thread.
        for (Collection<ProfilerNode<CPUSampler.Payload>> nodes
                : sampler.getDataList().iterator().next().threadData.values()) {
            for (ProfilerNode<CPUSampler.Payload> node : nodes) {
                final String rootName = node.getRootName();
                final int selfHitCount = node.getPayload().getSelfHitCount();
                // ...
            }
        }
        // END: CPUSamplerSnippets#example
        // @formatter:on
    }
}
