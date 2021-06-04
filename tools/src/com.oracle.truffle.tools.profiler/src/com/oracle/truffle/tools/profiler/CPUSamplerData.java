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
