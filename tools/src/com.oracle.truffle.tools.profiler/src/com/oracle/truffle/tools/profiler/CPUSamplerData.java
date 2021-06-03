package com.oracle.truffle.tools.profiler;

import java.util.LongSummaryStatistics;
import java.util.Map;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.tools.profiler.CPUSampler.Payload;

public final class CPUSamplerData {

    final TruffleContext context;
    final Map<Thread, ProfilerNode<Payload>> threadData;
    final LongSummaryStatistics biasStatistics;
    final LongSummaryStatistics durationStatistics;
    final long samplesTaken;
    final long intervalMs;

    public CPUSamplerData(TruffleContext context, Map<Thread, ProfilerNode<Payload>> threadData, LongSummaryStatistics biasStatistics, LongSummaryStatistics durationStatistics, long samplesTaken,
                    long intervalMs) {
        this.context = context;
        this.threadData = threadData;
        this.biasStatistics = biasStatistics;
        this.durationStatistics = durationStatistics;
        this.samplesTaken = samplesTaken;
        this.intervalMs = intervalMs;
    }

    public TruffleContext getContext() {
        return context;
    }

    public Map<Thread, ProfilerNode<Payload>> getThreadData() {
        return threadData;
    }

    public long getSamples() {
        return samplesTaken;
    }

    public long getSampleInterval() {
        return intervalMs;
    }

    public LongSummaryStatistics getSampleBias() {
        return biasStatistics;
    }

    public LongSummaryStatistics getSampleDuration() {
        return durationStatistics;
    }

}
