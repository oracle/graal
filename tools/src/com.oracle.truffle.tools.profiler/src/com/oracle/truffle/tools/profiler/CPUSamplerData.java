/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.LongSummaryStatistics;
import java.util.Map;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.tools.profiler.CPUSampler.Payload;

/**
 * Execution profile of a particular context.
 *
 * @see CPUSampler#getDataList()
 * @since 21.3.0
 */
public final class CPUSamplerData {

    final int contextIndex;
    final WeakReference<TruffleContext> contextRef;
    final Map<Thread, Collection<ProfilerNode<Payload>>> threadData;
    final LongSummaryStatistics biasStatistics;  // nanoseconds
    final LongSummaryStatistics durationStatistics;  // nanoseconds
    final long samplesTaken;
    final long intervalMs;
    final long missedSamples;

    CPUSamplerData(int contextIndex, TruffleContext context, Map<Thread, Collection<ProfilerNode<Payload>>> threadData, LongSummaryStatistics biasStatistics, LongSummaryStatistics durationStatistics,
                    long samplesTaken,
                    long intervalMs, long missedSamples) {
        this.contextIndex = contextIndex;
        this.contextRef = new WeakReference<>(context);
        this.threadData = threadData;
        this.biasStatistics = biasStatistics;
        this.durationStatistics = durationStatistics;
        this.samplesTaken = samplesTaken;
        this.intervalMs = intervalMs;
        this.missedSamples = missedSamples;
    }

    /**
     * @return The index of the context this data applies to. It is the index of this data in the
     *         {@link CPUSampler#getDataList() data list}. The index is zero based and corresponds
     *         to the order of context creations on the engine.
     * @since 23.1.4
     */
    public int getContextIndex() {
        return contextIndex;
    }

    /**
     * @return The context this data applies to or null if the context was already collected.
     * @since 21.3.0
     * @deprecated in 23.1.4. Contexts are no longer stored permanently. This method will return
     *             null if the context was already collected. Use {@link #getContextIndex()} to
     *             differentiate sampler data for different contexts.
     */
    @Deprecated
    public TruffleContext getContext() {
        return contextRef.get();
    }

    /**
     *
     * @return A mapping from each thread executing in the context to the {@link ProfilerNode}s
     *         describing the profile of the execution.
     * @since 21.3.0
     */
    public Map<Thread, Collection<ProfilerNode<Payload>>> getThreadData() {
        return Collections.unmodifiableMap(threadData);
    }

    /**
     * @return how many samples were taken.
     * @since 21.3.0
     */
    public long getSamples() {
        return samplesTaken;
    }

    /**
     * @return what was the sampling interval.
     * @since 21.3.0
     */
    public long getSampleInterval() {
        return intervalMs;
    }

    /**
     * The sample bias is a measurement of of how much time passed between requesting a stack sample
     * and starting the stack traversal. This method provies a summary of said times during the
     * profiling run.
     *
     * @return A {@link LongSummaryStatistics} of the sample bias.
     * @since 21.3.0
     */
    public LongSummaryStatistics getSampleBias() {
        LongSummaryStatistics statistics = new LongSummaryStatistics();
        statistics.combine(biasStatistics);
        return statistics;
    }

    /**
     * The sample duration is a measurement of how long it took to traverse the stack when taking a
     * sample. This method provides a summary of said times during the profiling run.
     *
     * @return A {@link LongSummaryStatistics} of the sample duration.
     * @since 21.3.0
     */
    public LongSummaryStatistics getSampleDuration() {
        LongSummaryStatistics statistics = new LongSummaryStatistics();
        statistics.combine(durationStatistics);
        return statistics;
    }

    /**
     * Returns how may samples were missed, i.e. how many times taking a stack sample was requested
     * but was not provided by the runtime in a timely manner.
     *
     * @return The number of missed samples.
     * @since 21.3.0
     */
    public long missedSamples() {
        return missedSamples;
    }
}
