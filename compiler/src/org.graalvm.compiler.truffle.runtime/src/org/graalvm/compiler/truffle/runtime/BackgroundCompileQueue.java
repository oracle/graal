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

import org.graalvm.compiler.core.CompilerThreadFactory;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilerThreads;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleLowGradeCompilation;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.getOptions;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.overrideOptions;

public class BackgroundCompileQueue {
    private final AtomicLong idCounter;
    private final ExecutorService compilationExecutorService;

    public class Request implements Runnable, Comparable<Request> {
        private final long id;
        private final GraalTruffleRuntime runtime;
        private final OptionValues optionOverrides;
        private final WeakReference<OptimizedCallTarget> weakCallTarget;
        private final Cancellable cancellable;
        private final boolean isLowGrade;

        public Request(GraalTruffleRuntime runtime, OptionValues optionOverrides, OptimizedCallTarget callTarget, Cancellable cancellable) {
            this.id = idCounter.getAndIncrement();
            this.runtime = runtime;
            this.optionOverrides = optionOverrides;
            this.weakCallTarget = new WeakReference<>(callTarget);
            this.cancellable = cancellable;
            this.isLowGrade = optionOverrides != null && TruffleLowGradeCompilation.getValue(optionOverrides);
        }

        @SuppressWarnings("try")
        @Override
        public void run() {
            OptimizedCallTarget callTarget = weakCallTarget.get();
            try (TruffleCompilerOptions.TruffleOptionsOverrideScope scope = optionOverrides != null ? overrideOptions(optionOverrides.getMap()) : null) {
                // if (!TruffleLowGradeCompilation.getValue(getOptions())) {
                //     TTY.println("running htc: " + callTarget);
                // } else {
                //     TTY.println("running ltc: " + callTarget);
                // }
                if (callTarget != null) {
                    OptionValues options = getOptions();
                    runtime.doCompile(options, callTarget, cancellable);
                }
            } finally {
                if (callTarget != null) {
                    callTarget.resetCompilationTask();
                }
            }
        }

        @Override
        public int compareTo(Request that) {
            if (this.isLowGrade != that.isLowGrade) {
                return this.isLowGrade ? -1 : 1;
            }
            return (int) (this.id - that.id);
        }

        @Override
        public String toString() {
            return "Request(lo: " + isLowGrade + ", id: " + id + ", " + weakCallTarget + ")";
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

        CompilerThreadFactory factory = new CompilerThreadFactory("TruffleCompilerThread");
        int selectedProcessors = TruffleCompilerOptions.getValue(TruffleCompilerThreads);
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

    public CancellableCompileTask submitCompilationRequest(GraalTruffleRuntime runtime, OptimizedCallTarget optimizedCallTarget) {
        final OptionValues optionOverrides = TruffleCompilerOptions.getCurrentOptionOverrides();
        CancellableCompileTask cancellable = new CancellableCompileTask();
        // try (TruffleOptionsOverrideScope scope = optionOverrides != null ? overrideOptions(optionOverrides.getMap()) : null) {
        //     if (!TruffleLowGradeCompilation.getValue(getOptions())) {
        //         TTY.println("submitting htc...: " + optimizedCallTarget);
        //     }
        // }
        Request request = new Request(runtime, optionOverrides, optimizedCallTarget, cancellable);
        cancellable.setFuture(compilationExecutorService.submit(request));
        // Task and future must never diverge from each other.
        assert cancellable.future != null;
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
