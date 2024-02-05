/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.sampler;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.TimeUnit;

import org.graalvm.collections.LockFreePrefixTree;
import jdk.graal.compiler.options.Option;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Threading;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.ThreadListener;

public class SafepointProfilingSampler implements ProfilingSampler, ThreadListener {
    private static final int DEFAULT_STACK_SIZE = 8 * 1024;

    static class Options {
        @Option(help = "Dump some stats about the safepoint sampler into a file on this path.")//
        static final HostedOptionKey<String> SafepointSamplerStats = new HostedOptionKey<>("");
    }

    private final SamplingStackVisitor samplingStackVisitor = new SamplingStackVisitor();
    private final LockFreePrefixTree prefixTree = new LockFreePrefixTree(new LockFreePrefixTree.ObjectPoolingAllocator());

    private final List<SamplerStats> statsList = Collections.synchronizedList(new ArrayList<>());

    @Platforms(Platform.HOSTED_ONLY.class)
    public SafepointProfilingSampler() {
        if (!Options.SafepointSamplerStats.hasBeenSet()) {
            return;
        }
        RuntimeSupport.getRuntimeSupport().addShutdownHook(isFirstIsolate -> {
            try (OutputStreamWriter stream = new OutputStreamWriter(Files.newOutputStream(Path.of(Options.SafepointSamplerStats.getValue())))) {
                for (SamplerStats stats : statsList) {
                    stream.append(String.format("SampleCount    : %d%n", stats.safepointStats.getCount()))
                                    .append(String.format("AverageRate    : %d us%n", TimeUnit.NANOSECONDS.toMicros((long) stats.safepointStats.getAverage())))
                                    .append(String.format("MinimumRate    : %d us%n", TimeUnit.NANOSECONDS.toMicros(stats.safepointStats.getMin())))
                                    .append(String.format("MaximumRate    : %d us%n", TimeUnit.NANOSECONDS.toMicros(stats.safepointStats.getMax())))
                                    .append(String.format("DurationCount  : %d%n", stats.durationStats.getCount()))
                                    .append(String.format("AverageDuration: %d us%n", TimeUnit.NANOSECONDS.toMicros((long) stats.durationStats.getAverage())))
                                    .append(String.format("MinimumDuration: %d us%n", TimeUnit.NANOSECONDS.toMicros(stats.durationStats.getMin())))
                                    .append(String.format("MaximumDuration: %d us%n", TimeUnit.NANOSECONDS.toMicros(stats.durationStats.getMax())))
                                    .append(System.lineSeparator());
                }
            } catch (IOException ignored) {
                // Silently ignore
            }
        });
    }

    @Override
    public void beforeThreadRun() {
        SamplingStackVisitor.StackTrace stackTrace = new SamplingStackVisitor.StackTrace(DEFAULT_STACK_SIZE);
        SamplerStats samplerStats = new SamplerStats();
        if (Options.SafepointSamplerStats.hasBeenSet()) {
            statsList.add(samplerStats);
        }
        Threading.registerRecurringCallback(10, TimeUnit.MILLISECONDS, access -> sampleThreadStack(stackTrace, samplerStats));
    }

    @Override
    public LockFreePrefixTree prefixTree() {
        return prefixTree;
    }

    @Override
    public void reset() {
        prefixTree.reset();
    }

    @Override
    public boolean isAsyncSampler() {
        return false;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate inside the safepoint sampler.")
    private void sampleThreadStack(SamplingStackVisitor.StackTrace stackTrace, SamplerStats samplerStats) {
        samplerStats.started();
        stackTrace.reset();
        walkCurrentThread(stackTrace, samplingStackVisitor);
        if (stackTrace.overflow) {
            // Exceeded the buffer size, ignore this sample.
            return;
        }
        long[] result = stackTrace.buffer;
        LockFreePrefixTree.Node node = prefixTree.root();
        for (int i = stackTrace.num - 1; i >= 0; i--) {
            if (i >= result.length) {
                // Due to the RestrictHeapAccess annotation, we need to prevent potential exception
                // allocations.
                return;
            }
            node = descend(node, result[i]);
            if (node == null) {
                // The prefix tree had to be extended, but the allocation failed.
                // In this case, simply drop the sample, as the allocation pool will be replenished
                // before a subsequent sampling round.
                return;
            }
        }
        node.incValue();
        samplerStats.end();
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Allocations are not allowed in the safepoint sampler, but we keep them unrestricted due to analysis imprecision.")
    private LockFreePrefixTree.Node descend(LockFreePrefixTree.Node node, long result) {
        return node.at(prefixTree().allocator(), result);
    }

    @NeverInline("Starts a stack walk in the caller frame")
    private static void walkCurrentThread(SamplingStackVisitor.StackTrace data, SamplingStackVisitor visitor) {
        JavaStackWalker.walkCurrentThread(KnownIntrinsics.readStackPointer(), visitor, data);
    }

    private static final class SamplerStats {
        private final boolean enabled;
        private final LongSummaryStatistics safepointStats;
        private final LongSummaryStatistics durationStats;
        private long lastSampleStart;

        SamplerStats() {
            this.enabled = Options.SafepointSamplerStats.hasBeenSet();
            this.safepointStats = enabled ? new LongSummaryStatistics() : null;
            this.durationStats = enabled ? new LongSummaryStatistics() : null;
        }

        private void started() {
            if (!enabled) {
                return;
            }
            long start = System.nanoTime();
            if (lastSampleStart != 0) {
                safepointStats.accept(start - lastSampleStart);
            }
            lastSampleStart = start;
        }

        private void end() {
            if (!enabled) {
                return;
            }
            durationStats.accept(System.nanoTime() - lastSampleStart);
        }
    }
}
