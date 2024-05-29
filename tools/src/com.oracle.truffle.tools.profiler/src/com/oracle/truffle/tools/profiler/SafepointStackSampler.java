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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

final class SafepointStackSampler {

    private static final Set<Class<?>> VISITOR_TAGS = new HashSet<>(Arrays.asList(StandardTags.RootTag.class));
    private final int stackLimit;
    private final SourceSectionFilter sourceSectionFilter;
    private final ConcurrentLinkedQueue<StackVisitor> stackVisitorCache = new ConcurrentLinkedQueue<>();
    private final AtomicReference<SampleAction> cachedAction = new AtomicReference<>();
    private final ThreadLocal<SyntheticFrame> syntheticFrameThreadLocal = ThreadLocal.withInitial(() -> null);
    private volatile boolean overflowed;
    private final AtomicLong sampleIndex = new AtomicLong(0);

    SafepointStackSampler(int stackLimit, SourceSectionFilter sourceSectionFilter) {
        this.stackLimit = stackLimit;
        this.sourceSectionFilter = sourceSectionFilter;
    }

    private StackVisitor fetchStackVisitor() {
        StackVisitor visitor = stackVisitorCache.poll();
        if (visitor == null) {
            visitor = new StackVisitor(stackLimit);
        }
        return visitor;
    }

    List<StackSample> sample(CPUSampler cpuSampler, Env env, Map<TruffleContext, CPUSampler.MutableSamplerData> contexts, boolean useSyntheticFrames, long timeout, TimeUnit timeoutUnit) {
        long startNanos = System.nanoTime();
        SampleAction action = cachedAction.getAndSet(null);
        if (action == null) {
            long index = sampleIndex.getAndIncrement();
            if (index < 0) {
                // handle overflow gracefully
                index = 0;
                sampleIndex.set(0);
            }
            action = new SampleAction(index);
        }
        action.useSyntheticFrames = useSyntheticFrames;

        long submitTime = System.nanoTime();
        Map<TruffleContext, Future<Void>> futures = new LinkedHashMap<>();
        for (TruffleContext context : contexts.keySet()) {
            if (!context.isClosed()) {
                try {
                    futures.put(context, env.submitThreadLocal(context, null, action));
                } catch (IllegalStateException e) {
                    // context may be closed while submitting
                }
            }
        }

        boolean incompleteSample = false;
        for (Map.Entry<TruffleContext, Future<Void>> futureEntry : futures.entrySet()) {
            TruffleContext context = futureEntry.getKey();
            Future<Void> future = futureEntry.getValue();
            long timeElapsed = System.nanoTime() - startNanos;
            long timeoutNanos = timeoutUnit.toNanos(timeout);
            if (!incompleteSample && timeElapsed < timeoutNanos) {
                try {
                    futureEntry.getValue().get(timeout, timeoutUnit);
                } catch (ExecutionException e) {
                    env.getLogger(getClass()).log(Level.SEVERE, "Sampling error", e);
                    incompleteSample = true;
                } catch (InterruptedException e) {
                    /*
                     * Closing the CPUSampler during shutdown of the instrument sends interrupt to
                     * the sampling thread, which is expected, and so we don't log it.
                     */
                    if (!cpuSampler.closed) {
                        env.getLogger(getClass()).log(Level.SEVERE, "Sampling interrupted", e);
                    }
                    incompleteSample = true;
                } catch (TimeoutException e) {
                    future.cancel(false);
                    contexts.get(context).missedSamples.incrementAndGet();
                    incompleteSample = true;
                }
            } else {
                future.cancel(false);
            }
        }
        // we compute the time to find out how accurate this sample is.
        List<StackSample> perThreadSamples = new ArrayList<>();
        for (CollectionResult result : action.getStacks()) {
            // time until the safepoint is executed from schedule
            StackSample sample = result.createSample(submitTime);
            if (sample.overflowed) {
                this.overflowed = true;
            }
            perThreadSamples.add(sample);
        }
        action.reset();
        cachedAction.set(action);

        assert perThreadSamples.stream().map(e -> e.thread).collect(Collectors.toSet()).size() == perThreadSamples.size();
        return perThreadSamples;
    }

    boolean hasOverflowed() {
        return overflowed;
    }

    public void pushSyntheticFrame(LanguageInfo language, String message) {
        // For synthetic frames we need iterate and create frames eagerly.
        long submitTime = System.nanoTime();
        StackVisitor visitor = fetchStackVisitor();
        visitor.iterateFrames();
        StackSample stackSample = visitor.createSample(submitTime);
        SyntheticFrame frame = new SyntheticFrame(syntheticFrameThreadLocal.get(), stackSample, language, message);
        syntheticFrameThreadLocal.set(frame);
    }

    public void popSyntheticFrame() {
        SyntheticFrame toPop = syntheticFrameThreadLocal.get();
        if (toPop != null) {
            syntheticFrameThreadLocal.set(toPop.parent);
        }
    }

    static final class StackSample {

        final Thread thread;
        final List<StackTraceEntry> stack;
        final long biasNs;
        final long durationNs;
        final boolean overflowed;

        StackSample(Thread thread, List<StackTraceEntry> stack, long biasNs, long durationNs, boolean overflowed) {
            this.thread = thread;
            this.stack = stack;
            this.biasNs = biasNs;
            this.durationNs = durationNs;
            this.overflowed = overflowed;
        }
    }

    private class StackVisitor implements FrameInstanceVisitor<FrameInstance>, CollectionResult {

        private final CallTarget[] targets;
        private final int[] tiers;
        private final boolean[] roots;
        private Thread thread;
        private int nextFrameIndex;
        private long startTime;
        private long endTime;
        private boolean overflowed;

        StackVisitor(int stackLimit) {
            assert stackLimit > 0;
            this.tiers = new int[stackLimit];
            this.roots = new boolean[stackLimit];
            this.targets = new CallTarget[stackLimit];
        }

        final void iterateFrames() {
            assert this.thread == null : "not cleaned";
            assert this.nextFrameIndex == 0 : "not cleaned";

            this.thread = Thread.currentThread();
            this.startTime = System.nanoTime();
            Truffle.getRuntime().iterateFrames(this);
            this.endTime = System.nanoTime();
        }

        public StackSample createSample(long submitTime) {
            long bias = this.startTime - submitTime;
            long overhead = this.endTime - this.startTime;
            StackSample sample = new StackSample(this.thread, this.createEntries(sourceSectionFilter),
                            bias, overhead, this.overflowed);
            assert sample.thread != null;
            this.resetAndReturn();
            return sample;
        }

        public FrameInstance visitFrame(FrameInstance frameInstance) {
            tiers[nextFrameIndex] = frameInstance.getCompilationTier();
            roots[nextFrameIndex] = frameInstance.isCompilationRoot();
            targets[nextFrameIndex] = frameInstance.getCallTarget();
            nextFrameIndex++;
            if (nextFrameIndex >= targets.length) {
                // stop traversing
                overflowed = true;
                return frameInstance;
            }
            return null;
        }

        void resetAndReturn() {
            Arrays.fill(tiers, 0, nextFrameIndex, 0);
            Arrays.fill(roots, 0, nextFrameIndex, false);
            Arrays.fill(targets, 0, nextFrameIndex, null);
            nextFrameIndex = 0;
            thread = null;
            overflowed = false;
            this.startTime = 0L;
            this.endTime = 0L;
            stackVisitorCache.add(this);
        }

        @SuppressWarnings("unused")
        List<StackTraceEntry> createEntries(SourceSectionFilter filter) {
            List<StackTraceEntry> entries = new ArrayList<>(nextFrameIndex);
            for (int i = 0; i < nextFrameIndex; i++) {
                CallTarget target = targets[i];
                RootNode root = ((RootCallTarget) target).getRootNode();
                SourceSection sourceSection = root.getSourceSection();
                if (filter.includes(root, sourceSection, null)) {
                    entries.add(new StackTraceEntry(VISITOR_TAGS, sourceSection, root, root, tiers[i], roots[i]));
                }
            }
            return entries;
        }
    }

    private class SampleAction extends ThreadLocalAction {

        final ConcurrentHashMap<Thread, CollectionResult> completed = new ConcurrentHashMap<>();
        boolean useSyntheticFrames = true;

        private long index;

        protected SampleAction(long index) {
            super(false, false);
            this.index = index;
        }

        @Override
        protected void perform(Access access) {
            if (completed.containsKey(access.getThread())) {
                return;
            }
            if (useSyntheticFrames) {
                SyntheticFrame syntheticFrame = syntheticFrameThreadLocal.get();
                if (syntheticFrame != null) {
                    completed.put(access.getThread(), syntheticFrame);
                    return;
                }
            }
            StackVisitor visitor = fetchStackVisitor();
            visitor.iterateFrames();
            completed.put(access.getThread(), visitor);
        }

        List<CollectionResult> getStacks() {
            return new ArrayList<>(completed.values());
        }

        @Override
        public String toString() {
            return "StackSampleAction[index=" + index + "]";
        }

        void reset() {
            completed.clear();
        }
    }

    private interface CollectionResult {

        StackSample createSample(long submitTime);

    }

    private static class SyntheticFrame implements CollectionResult {
        final SyntheticFrame parent;
        final StackSample stackSample;
        final LanguageInfo language;
        final String message;

        boolean syntheticFrameCreated;

        /**
         * Created on the interpreter thread, keep as fast as possible.
         */
        SyntheticFrame(SyntheticFrame parent, StackSample stackSample, LanguageInfo language, String message) {
            this.parent = parent;
            this.stackSample = stackSample;
            this.language = language;
            this.message = message;
        }

        public StackSample createSample(long submitTime) {
            if (!syntheticFrameCreated) {
                /*
                 * The synthetic frame is created lazily to avoid string concatenation on the main
                 * thread.
                 */
                stackSample.stack.add(0, new StackTraceEntry("<<" + language.getId() + ":" + message + ">>"));
                syntheticFrameCreated = true;
            }
            return stackSample;
        }

    }
}
