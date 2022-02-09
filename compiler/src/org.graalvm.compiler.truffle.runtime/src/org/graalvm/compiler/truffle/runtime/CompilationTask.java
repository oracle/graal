/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleInliningData;

import com.oracle.truffle.api.Truffle;

public final class CompilationTask implements TruffleCompilationTask, Callable<Void>, Comparable<CompilationTask> {

    private static final Consumer<CompilationTask> COMPILATION_ACTION = new Consumer<>() {
        @Override
        public void accept(CompilationTask task) {
            OptimizedCallTarget callTarget = task.targetRef.get();
            if (callTarget != null && task.start()) {
                try {
                    ((GraalTruffleRuntime) Truffle.getRuntime()).doCompile(callTarget, task);
                } finally {
                    callTarget.compiledTier(task.tier());
                    task.finished();
                }
            }
        }
    };
    final WeakReference<OptimizedCallTarget> targetRef;
    private final BackgroundCompileQueue.Priority priority;
    private final long id;
    private final Consumer<CompilationTask> action;
    private final EngineData engineData;
    private final TruffleInlining inliningData = new TruffleInlining();
    private volatile Future<?> future;
    private volatile boolean cancelled;
    private volatile boolean started;
    // Traversing queue related
    private int lastCount;
    private long lastTime;
    private double lastWeight;
    private boolean isOSR;

    private double lastRate;
    private long time;
    private int queueChange;

    private CompilationTask(BackgroundCompileQueue.Priority priority, WeakReference<OptimizedCallTarget> targetRef, Consumer<CompilationTask> action, long id) {
        this.priority = priority;
        this.targetRef = targetRef;
        this.action = action;
        this.id = id;
        OptimizedCallTarget target = targetRef.get();
        lastCount = target != null ? target.getCallAndLoopCount() : Integer.MIN_VALUE;
        lastTime = System.nanoTime();
        lastWeight = target != null ? target.getCallAndLoopCount() : -1;
        engineData = target != null ? target.engine : null;
        isOSR = target != null && target.isOSR();
    }

    static CompilationTask createInitializationTask(WeakReference<OptimizedCallTarget> targetRef, Consumer<CompilationTask> action) {
        return new CompilationTask(BackgroundCompileQueue.Priority.INITIALIZATION, targetRef, action, 0);
    }

    static CompilationTask createCompilationTask(BackgroundCompileQueue.Priority priority, WeakReference<OptimizedCallTarget> targetRef, long id) {
        return new CompilationTask(priority, targetRef, COMPILATION_ACTION, id);
    }

    public void awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            future.get(timeout, unit);
        } catch (CancellationException e) {
            // Ignored
        }
    }

    public void awaitCompletion() throws ExecutionException, InterruptedException {
        try {
            future.get();
        } catch (CancellationException e) {
            // Ignored
        }
    }

    public synchronized boolean cancel() {
        if (!cancelled) {
            cancelled = true;
            if (!started) {
                future.cancel(false);
                finished();
            }
            return true;
        }
        return false;
    }

    void reset() {
        cancelled = false;
    }

    public void finished() {
        final OptimizedCallTarget target = targetRef.get();
        if (target != null) {
            target.resetCompilationTask();
        }
    }

    public synchronized boolean start() {
        assert !started : "Should not start a stared task";
        if (cancelled) {
            return false;
        }
        started = true;
        return true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isLastTier() {
        return priority.tier == BackgroundCompileQueue.Priority.Tier.LAST;
    }

    @Override
    public TruffleInliningData inliningData() {
        return inliningData;
    }

    @Override
    public boolean hasNextTier() {
        if (isLastTier()) {
            return false;
        }
        OptimizedCallTarget callTarget = targetRef.get();
        if (callTarget == null) {
            // Does not matter what we return if the target is not available
            return false;
        }
        return !callTarget.engine.firstTierOnly;
    }

    public Future<?> getFuture() {
        return future;
    }

    // This cannot be done in the constructor because the CancellableCompileTask needs to be
    // passed down to the compiler through a Runnable inner class.
    // This means it must be final and initialized before the future can be set.
    void setFuture(Future<?> future) {
        synchronized (this) {
            if (this.future == null) {
                this.future = future;
            } else {
                throw new IllegalStateException("The future should not be re-set.");
            }
        }
    }

    @Override
    public String toString() {
        return "Task[id=" + id + ", tier=" + priority.tier + ", weight=" + lastWeight + "]";
    }

    /**
     * We only want priority for the "escape from interpreter" compilations. If multi tier is
     * enabled, that means *only* first tier compilations, otherwise it means last tier.
     */
    private boolean priorityQueueEnabled() {
        if (engineData == null) {
            return false;
        }
        return engineData.priorityQueue && ((engineData.multiTier && priority.tier == BackgroundCompileQueue.Priority.Tier.FIRST) ||
                        (!engineData.multiTier && priority.tier == BackgroundCompileQueue.Priority.Tier.LAST));
    }

    @Override
    public int compareTo(CompilationTask that) {
        int tierCompare = priority.tier.compareTo(that.priority.tier);
        if (tierCompare != 0) {
            return tierCompare;
        }
        if (priorityQueueEnabled()) {
            int valueCompare = -1 * Long.compare(priority.value, that.priority.value);
            if (valueCompare != 0) {
                return valueCompare;
            }
        }
        return Long.compare(this.id, that.id);
    }

    @Override
    public Void call() throws Exception {
        action.accept(this);
        return null;
    }

    /*
     * Used by the traversing queue to pick the best task. A comparator is currently not meant to
     * use this method, because the weight is dynamic, and relying on it in the ordering could
     * corrupt a queue data structure.
     */
    boolean isHigherPriorityThan(CompilationTask other) {
        if (action != COMPILATION_ACTION) {
            // Any non-compilation action (e.g. compiler init) is higher priority.
            return true;
        }
        if (this.isOSR && other.isLastTier()) {
            return true;
        }
        int tier = tier();
        if (engineData.traversingFirstTierPriority && tier != other.tier()) {
            return tier < other.tier();
        }
        int otherCompileTier = other.targetHighestCompiledTier();
        int compiledTier = targetHighestCompiledTier();
        if (tier == other.tier() && compiledTier != otherCompileTier) {
            // tasks previously compiled with higher tier are better
            return compiledTier > otherCompileTier;
        }
        if (engineData.weightingBothTiers || isFirstTier()) {
            return lastWeight > other.lastWeight;
        }
        return false;
    }

    /**
     * @return false if the target reference is null (i.e. if the target was garbage-collected).
     */
    boolean updateWeight(long currentTime) {
        OptimizedCallTarget target = targetRef.get();
        if (target == null) {
            return false;
        }
        long elapsed = currentTime - lastTime;
        if (elapsed < 1_000_000) {
            return true;
        }
        int count = target.getCallAndLoopCount();
        lastRate = rate(count, elapsed);
        lastTime = currentTime;
        lastCount = count;
        double weight = (1 + lastRate) * lastCount;
        if (engineData.traversingFirstTierPriority) {
            lastWeight = weight;
        } else {
            // @formatter:off
            // We multiply first tier compilations with this bonus to bring first and last tier
            // compilation weights to roughly the same order of magnitude and give first tier compilations some priority.
            // The bonus is calculated as TraversingQueueFirstTierBonus * LastTierCompilationThreshold / FirstTierCompilationThreshold
            //                                    ^                        \________________________________________________________/
            //  This controls for the fact that second tier                             |
            //  compilations are already compiled in the first                          |
            //  tier and are thus faster and for the fact that                          |
            //  we wish to prioritize first tier compilations.                          |
            //                                                                          |
            //                                   This controls for the fact that weight is a multiple of the callAndLoopCount and this
            //                                   count is on the order of the thresholds which is much smaller for first tier compilations
            // @formatter:on
            lastWeight = weight * (isFirstTier() ? engineData.traversingFirstTierBonus : 1);
        }
        assert weight >= 0.0 : "weight must be positive";
        return true;
    }

    private double rate(int count, long elapsed) {
        lastRate = ((double) count - lastCount) / elapsed;
        return (Double.isNaN(lastRate) ? 0 : lastRate);
    }

    public int targetHighestCompiledTier() {
        OptimizedCallTarget target = targetRef.get();
        if (target == null) {
            return -1;
        }
        return target.highestCompiledTier();
    }

    @Override
    public long time() {
        return time;
    }

    @Override
    public double weight() {
        return lastWeight;
    }

    @Override
    public double rate() {
        return lastRate;
    }

    @Override
    public int queueChange() {
        return queueChange;
    }

    void setTime(long time) {
        this.time = time;
    }

    void setQueueChange(int queueChange) {
        this.queueChange = queueChange;
    }

    /**
     * Since {@link BackgroundCompileQueue} uses a {@link java.util.concurrent.ThreadPoolExecutor}
     * to run compilations, and since the executor expects each {@link Callable} (in our case, the
     * {@link CompilationTask}) to be converted into a {@link FutureTask} we use this wrapper around
     * the {@link CompilationTask} just for compatibility with the executor.
     */
    public static class ExecutorServiceWrapper extends FutureTask<Void> implements Comparable<ExecutorServiceWrapper> {
        final CompilationTask compileTask;

        ExecutorServiceWrapper(CompilationTask compileTask) {
            super(compileTask);
            this.compileTask = compileTask;
        }

        @Override
        public int compareTo(ExecutorServiceWrapper that) {
            return this.compileTask.compareTo(that.compileTask);
        }

        @Override
        public String toString() {
            return "ExecutorServiceWrapper(" + compileTask + ")";
        }

        public CompilationTask getCompileTask() {
            return compileTask;
        }
    }
}
