/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.oracle.truffle.api.Truffle;

public final class CompilationTask extends AbstractCompilationTask implements Callable<Void>, Comparable<CompilationTask> {

    private static final Consumer<CompilationTask> COMPILATION_ACTION = new Consumer<>() {
        @Override
        public void accept(CompilationTask task) {
            OptimizedCallTarget callTarget = task.targetRef.get();
            if (callTarget != null && task.start()) {
                try {
                    ((OptimizedTruffleRuntime) Truffle.getRuntime()).doCompile(callTarget, task);
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
    private volatile Future<?> future;
    private volatile boolean cancelled;
    volatile BooleanSupplier cancelledPredicate;
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
        // Last weight < 0 indicates that this task's weight has not been initialized yet.
        // Why not initialize/calculate it here? We do not have any information about the rate of
        // change of its call and loop count yet since the task is newly created. Consequently, we
        // cannot compute a reliable rate yet. The rate will be computed the first time updateWeight
        // is called.
        lastWeight = -1.0;
        lastTime = System.nanoTime();
        OptimizedCallTarget target = targetRef.get();
        if (target == null) {
            lastCount = Integer.MIN_VALUE;
            engineData = null;
            isOSR = false;
            cancelledPredicate = null;
        } else {
            lastCount = target.getCallAndLoopCount();
            engineData = target.engine;
            isOSR = target.isOSR();
            cancelledPredicate = target.engine.cancelledPredicate;
        }
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
        BooleanSupplier cancelPredicate = cancelledPredicate;
        return cancelled || (cancelPredicate != null && cancelPredicate.getAsBoolean());
    }

    @Override
    public boolean isLastTier() {
        return priority.tier == BackgroundCompileQueue.Priority.Tier.LAST;
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
        if (action != COMPILATION_ACTION || other.action != COMPILATION_ACTION) {
            // Any non-compilation action (e.g. compiler init) is higher priority.
            return action != COMPILATION_ACTION;
        }
        int tier = tier();
        if (engineData.traversingFirstTierPriority && tier != other.tier()) {
            return tier < other.tier();
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
        // A last weight > 0 indicates that it has been initialized. If so, only update its weight
        // if 1_000_000ns have elapsed since its last calculation. The elapsed time may be negative
        // since tasks may be added to the queue while traversing it.
        if (lastWeight > 0 && elapsed < 1_000_000 || elapsed < 0) {
            return true;
        }
        int count = target.getCallAndLoopCount();
        lastRate = rate(count, elapsed);
        lastTime = currentTime;
        lastCount = count;
        double weight = (1 + lastRate) * lastCount;
        assert weight >= 0.0 : "weight must be positive";
        lastWeight = weight * bonus();
        return true;
    }

    private double bonus() {
        double bonus = 1.0;
        if (!engineData.traversingFirstTierPriority && isFirstTier()) {
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
            bonus *= engineData.traversingFirstTierBonus;
        }
        if (targetHighestCompiledTier() >= tier()) {
            // If the task has already been compiled with the same or a higher tier, implying the
            // previous compilation has been invalidated, boost its priority.
            // This bonus is 1.0 by default, i.e. it has no effect.
            bonus *= engineData.traversingInvalidatedBonus;
        }
        if (isOSR) {
            // Boost OSR tasks' priority. This bonus is 1.0 by default, i.e. it has no effect.
            bonus *= engineData.traversingOSRBonus;
        }
        return bonus;
    }

    public List<String> bonusDescriptors() {
        List<String> bonuses = new ArrayList<>(3);
        if (!engineData.traversingFirstTierPriority && isFirstTier() && hasEffect(engineData.traversingFirstTierBonus)) {
            bonuses.add("first tier");
        }
        if (targetHighestCompiledTier() >= tier() && hasEffect(engineData.traversingInvalidatedBonus)) {
            bonuses.add("invalidation");
        }
        if (isOSR && hasEffect(engineData.traversingOSRBonus)) {
            bonuses.add("OSR");
        }
        return bonuses;
    }

    private static final double EPSILON = 1E-6;

    private static boolean hasEffect(double bonus) {
        // Checks if the given bonus differs from 1.
        return Math.abs(bonus - 1.0) > EPSILON;
    }

    private double rate(int count, long elapsed) {
        // Divide by a minimum of 1000 to prevent division by zero/very small numbers.
        return lastRate = ((double) count - lastCount) / Math.max(elapsed, 1000);
    }

    public int targetHighestCompiledTier() {
        OptimizedCallTarget target = targetRef.get();
        if (target == null) {
            return -1;
        }
        return target.highestCompiledTier();
    }

    public long time() {
        return time;
    }

    public double weight() {
        return lastWeight;
    }

    public double rate() {
        return lastRate;
    }

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
