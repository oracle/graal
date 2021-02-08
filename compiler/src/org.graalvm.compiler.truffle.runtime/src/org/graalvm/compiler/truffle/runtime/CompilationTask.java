/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

import com.oracle.truffle.api.Truffle;

public final class CompilationTask implements TruffleCompilationTask, Callable<Void>, Comparable<CompilationTask> {

    final WeakReference<OptimizedCallTarget> targetRef;
    private final BackgroundCompileQueue.Priority priority;
    private final boolean multiTier;
    private final boolean priorityQueue;
    private final long id;
    private final Consumer<CompilationTask> action;
    private volatile Future<?> future;
    private volatile boolean cancelled;
    private volatile boolean started;
    private static final Consumer<CompilationTask> compilationAction = new Consumer<CompilationTask>() {
        @Override
        public void accept(CompilationTask task) {
            OptimizedCallTarget callTarget = task.targetRef.get();
            if (callTarget != null && task.start()) {
                try {
                    ((GraalTruffleRuntime) Truffle.getRuntime()).doCompile(callTarget, task);
                } finally {
                    task.finished();
                }
            }
        }
    };

    static CompilationTask createInitializationTask(WeakReference<OptimizedCallTarget> targetRef, Consumer<CompilationTask> action) {
        return new CompilationTask(BackgroundCompileQueue.Priority.INITIALIZATION, targetRef, action, 0);
    }

    static CompilationTask createCompilationTask(BackgroundCompileQueue.Priority priority, WeakReference<OptimizedCallTarget> targetRef, long id) {
        return new CompilationTask(priority, targetRef, compilationAction, id);
    }

    private CompilationTask(BackgroundCompileQueue.Priority priority, WeakReference<OptimizedCallTarget> targetRef, Consumer<CompilationTask> action, long id) {
        this.priority = priority;
        this.targetRef = targetRef;
        this.action = action;
        this.id = id;
        OptimizedCallTarget target = targetRef.get();
        priorityQueue = target != null && target.getOptionValue(PolyglotCompilerOptions.PriorityQueue);
        multiTier = target != null && target.getOptionValue(PolyglotCompilerOptions.MultiTier);
    }

    public void awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        future.get(timeout, unit);
    }

    public void awaitCompletion() throws ExecutionException, InterruptedException {
        future.get();
    }

    public synchronized boolean cancel() {
        if (!cancelled) {
            cancelled = true;
            if (!started) {
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
        return "CompilationTask[" + future + "]";
    }

    /**
     * We only want priority for the "escape from interpreter" compilations. If multi tier is
     * enabled, that means *only* first tier compilations, otherwise it means last tier.
     */
    private boolean priorityQueueEnabled() {
        return priorityQueue && ((multiTier && priority.tier == BackgroundCompileQueue.Priority.Tier.FIRST) || (!multiTier && priority.tier == BackgroundCompileQueue.Priority.Tier.LAST));
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

    /**
     * Since {@link BackgroundCompileQueue} uses a {@link java.util.concurrent.ThreadPoolExecutor}
     * to run compilations, and since the executor expects each {@link Callable} (in our case, the
     * {@link CompilationTask}) to be converted into a {@link FutureTask} we use this wrapper around
     * the {@link CompilationTask} just for compatibility with the executor.
     */
    static class ExecutorServiceWrapper extends FutureTask<Void> implements Comparable<ExecutorServiceWrapper> {
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
    }
}
