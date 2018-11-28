/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.getOptions;
import static org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.overrideOptions;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions.TruffleRuntimeOptionsOverrideScope;
import org.graalvm.options.OptionValues;

/**
 * The compilation queue accepts compilation requests, and schedules compilations.
 *
 * The current queuing policy is to first schedule all the first tier compilation requests, and only
 * handle second tier compilation requests when there are no first tier compilations left. Between
 * the compilation requests of the same optimization tier, the queuing policy is FIFO
 * (first-in-first-out).
 *
 * Note that all the compilation requests are second tier when the multi-tier option is turned off.
 */
public class BackgroundCompileQueue {
    private final AtomicLong idCounter;
    private final ExecutorService compilationExecutorService;

    public class Request implements Runnable, Comparable<Request> {
        private final long id;
        private final GraalTruffleRuntime runtime;
        private final OptionValues optionOverrides;
        private final WeakReference<OptimizedCallTarget> weakCallTarget;
        private final TruffleCompilationTask task;
        private final boolean isFirstTier;

        public Request(GraalTruffleRuntime runtime, OptionValues optionOverrides, OptimizedCallTarget callTarget, TruffleCompilationTask task) {
            this.id = idCounter.getAndIncrement();
            this.runtime = runtime;
            this.optionOverrides = optionOverrides;
            this.weakCallTarget = new WeakReference<>(callTarget);
            this.task = task;
            this.isFirstTier = !task.isLastTier();
        }

        @SuppressWarnings("try")
        @Override
        public void run() {
            OptimizedCallTarget callTarget = weakCallTarget.get();
            if (callTarget != null) {
                try (TruffleRuntimeOptionsOverrideScope scope = optionOverrides != null ? overrideOptions(optionOverrides) : null) {
                    if (!task.isCancelled()) {
                        OptionValues options = getOptions();
                        runtime.doCompile(options, callTarget, task);
                    }
                } finally {
                    callTarget.resetCompilationTask();
                }
            }
        }

        @Override
        public int compareTo(Request that) {
            if (this.isFirstTier != that.isFirstTier) {
                return this.isFirstTier ? -1 : 1;
            }
            return (int) (this.id - that.id);
        }

        @Override
        public String toString() {
            return "Request(lo: " + isFirstTier + ", id: " + id + ", " + weakCallTarget + ")";
        }
    }

    public class RequestFutureTask<V> extends FutureTask<V> implements Comparable<Runnable> {
        private final Request request;

        public RequestFutureTask(Runnable runnable, V result) {
            super(runnable, result);
            this.request = (Request) runnable;
        }

        @Override
        public int compareTo(Runnable that) {
            if (that instanceof RequestFutureTask) {
                return this.request.compareTo(((RequestFutureTask<?>) that).request);
            } else {
                return this.request.compareTo((Request) that);
            }
        }

        @Override
        public String toString() {
            return "Future(" + request + ")";
        }
    }

    public BackgroundCompileQueue() {
        this.idCounter = new AtomicLong();

        TruffleCompilerThreadFactory factory = new TruffleCompilerThreadFactory("TruffleCompilerThread");
        int selectedProcessors = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompilerThreads);
        if (selectedProcessors == 0) {
            // No manual selection made, check how many processors are available.
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            if (availableProcessors >= 4) {
                selectedProcessors = 2;
            }
        }
        selectedProcessors = Math.max(1, selectedProcessors);
        this.compilationExecutorService = new ThreadPoolExecutor(selectedProcessors, selectedProcessors, 0, TimeUnit.MILLISECONDS,
                        new PriorityBlockingQueue<>(), factory) {
            @Override
            protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
                return new RequestFutureTask<>(runnable, value);
            }
        };
    }

    private static final class TruffleCompilerThreadFactory implements ThreadFactory {
        private final String namePrefix;

        TruffleCompilerThreadFactory(final String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r) {
                @Override
                public void run() {
                    setContextClassLoader(getClass().getClassLoader());
                    super.run();
                }
            };
            t.setName(namePrefix + "-" + t.getId());
            t.setPriority(Thread.MAX_PRIORITY);
            t.setDaemon(true);
            return t;
        }
    }

    public CancellableCompileTask submitCompilationRequest(GraalTruffleRuntime runtime, OptimizedCallTarget optimizedCallTarget, boolean lastTierCompilation) {
        final OptionValues optionOverrides = TruffleRuntimeOptions.getCurrentOptionOverrides();
        CancellableCompileTask cancellable = new CancellableCompileTask(lastTierCompilation);
        Request request = new Request(runtime, optionOverrides, optimizedCallTarget, cancellable);
        cancellable.setFuture(compilationExecutorService.submit(request));
        return cancellable;
    }

    public int getQueueSize() {
        if (compilationExecutorService instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) compilationExecutorService).getQueue().size();
        } else {
            return 0;
        }
    }

    public void shutdownAndAwaitTermination(long timeout) {
        compilationExecutorService.shutdownNow();
        try {
            compilationExecutorService.awaitTermination(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not terminate compiler threads. Check if there are runaway compilations that don't handle Thread#interrupt.", e);
        }
    }
}
