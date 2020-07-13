/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

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
    private volatile ThreadPoolExecutor compilationExecutorService;
    private boolean shutdown = false;
    protected final GraalTruffleRuntime runtime;
    private long delayMillis;

    public BackgroundCompileQueue(GraalTruffleRuntime runtime) {
        this.runtime = runtime;
        this.idCounter = new AtomicLong();
    }

    private ExecutorService getExecutorService(OptimizedCallTarget callTarget) {
        if (compilationExecutorService != null) {
            return compilationExecutorService;
        }
        synchronized (this) {
            if (compilationExecutorService != null) {
                return compilationExecutorService;
            }
            if (shutdown) {
                throw new RejectedExecutionException("The BackgroundCompileQueue is shutdown");
            }

            // NOTE: The value from the first Engine compiling wins for now
            this.delayMillis = callTarget.getOptionValue(PolyglotCompilerOptions.EncodedGraphCachePurgeDelay);

            // NOTE: the value from the first Engine compiling wins for now
            int threads = callTarget.getOptionValue(PolyglotCompilerOptions.CompilerThreads);
            if (threads == 0) {
                // No manual selection made, check how many processors are available.
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                if (availableProcessors >= 4) {
                    threads = 2;
                }
            }
            threads = Math.max(1, threads);

            ThreadFactory factory = newThreadFactory("TruffleCompilerThread", callTarget);

            long compilerIdleDelay = runtime.getCompilerIdleDelay(callTarget);
            long keepAliveTime = compilerIdleDelay >= 0 ? compilerIdleDelay : 0;

            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(threads, threads,
                            keepAliveTime, TimeUnit.MILLISECONDS,
                            new IdlingPriorityBlockingQueue<>(), factory) {
                @Override
                protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
                    return new RequestFutureTask<>((RequestImpl<T>) callable);
                }
            };

            if (compilerIdleDelay > 0) {
                // There are two mechanisms to signal idleness: if core threads can timeout, then
                // the notification is triggered by TruffleCompilerThreadFactory,
                // otherwise, via IdlingBlockingQueue.take.
                threadPoolExecutor.allowCoreThreadTimeOut(true);
            }

            return compilationExecutorService = threadPoolExecutor;
        }
    }

    @SuppressWarnings("unused")
    protected ThreadFactory newThreadFactory(String threadNamePrefix, OptimizedCallTarget callTarget) {
        return new TruffleCompilerThreadFactory(threadNamePrefix, runtime);
    }

    public CancellableCompileTask submitTask(Priority priority, OptimizedCallTarget target, Request request) {
        final WeakReference<OptimizedCallTarget> targetReference = new WeakReference<>(target);
        CancellableCompileTask cancellable = new CancellableCompileTask(targetReference, priority == Priority.LAST_TIER);
        RequestImpl<Void> requestImpl = new RequestImpl<>(nextId(), priority, targetReference, cancellable, request);
        cancellable.setFuture(getExecutorService(target).submit(requestImpl));
        return cancellable;
    }

    private long nextId() {
        return idCounter.getAndIncrement();
    }

    public int getQueueSize() {
        final ExecutorService threadPool = compilationExecutorService;
        if (threadPool instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) threadPool).getQueue().size();
        } else {
            return 0;
        }
    }

    public void shutdownAndAwaitTermination(long timeout) {
        final ExecutorService threadPool;
        synchronized (this) {
            threadPool = compilationExecutorService;
            if (threadPool == null) {
                shutdown = true;
                return;
            }
        }

        threadPool.shutdownNow();
        try {
            threadPool.awaitTermination(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Could not terminate compiler threads. Check if there are runaway compilations that don't handle Thread#interrupt.", e);
        }
    }

    public enum Priority {

        INITIALIZATION(0),
        FIRST_TIER(1),
        LAST_TIER(2);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

    }

    public abstract static class Request {

        protected abstract void execute(CancellableCompileTask task, WeakReference<OptimizedCallTarget> targetRef);

    }

    private static final class RequestImpl<V> implements Callable<V>, Comparable<RequestImpl<?>> {

        private final long id;
        private final Priority priority;
        private final CancellableCompileTask task;
        private final WeakReference<OptimizedCallTarget> targetRef;
        private final Request request;

        RequestImpl(long id, Priority priority, WeakReference<OptimizedCallTarget> targetRef, CancellableCompileTask task, Request request) {
            this.id = id;
            this.priority = priority;
            this.targetRef = targetRef;
            this.task = task;
            this.request = request;
        }

        @Override
        public int compareTo(RequestImpl<?> that) {
            int diff = priority.value - that.priority.value;
            if (diff == 0) {
                diff = Long.compare(this.id, that.id);
            }
            return diff;
        }

        @SuppressWarnings("try")
        @Override
        public V call() {
            request.execute(task, targetRef);
            return null;
        }

        @Override
        public String toString() {
            return "Request(id:" + id + ", priority:" + priority + " target: " + targetRef.get() + ")";
        }
    }

    private static class RequestFutureTask<V> extends FutureTask<V> implements Comparable<RequestFutureTask<?>> {
        private final RequestImpl<V> request;

        RequestFutureTask(RequestImpl<V> callable) {
            super(callable);
            this.request = callable;
        }

        @Override
        public int compareTo(RequestFutureTask<?> that) {
            return this.request.compareTo(that.request);
        }

        @Override
        public String toString() {
            return "Future(" + request + ")";
        }
    }

    private final class TruffleCompilerThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final GraalTruffleRuntime runtime;

        TruffleCompilerThreadFactory(final String namePrefix, GraalTruffleRuntime runtime) {
            this.namePrefix = namePrefix;
            this.runtime = runtime;
        }

        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r) {
                @SuppressWarnings("try")
                @Override
                public void run() {
                    setContextClassLoader(getClass().getClassLoader());
                    try (AutoCloseable scope = runtime.openCompilerThreadScope()) {
                        super.run();
                        if (compilationExecutorService.allowsCoreThreadTimeOut()) {
                            // If core threads are always kept alive (no timeout), the
                            // IdlingPriorityBlockingQueue.take mechanism is used instead.
                            compilerThreadIdled();
                        }
                    } catch (Exception e) {
                        throw new InternalError(e);
                    }
                }
            };
            t.setName(namePrefix + "-" + t.getId());
            t.setPriority(Thread.MAX_PRIORITY);
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * {@link PriorityBlockingQueue} with idling notification.
     *
     * <p>
     * The idling notification is triggered when a compiler thread remains idle more than
     * {@code delayMillis}.
     *
     * There are no guarantees on which thread will run the {@code onIdleDelayed} hook. Note that,
     * starved threads can also trigger the notification, even if the compile queue is not idle
     * during the delay period, the idling criteria is thread-based, not queue-based.
     */
    @SuppressWarnings("serial")
    private final class IdlingPriorityBlockingQueue<E> extends PriorityBlockingQueue<E> {
        @Override
        public E take() throws InterruptedException {
            while (!compilationExecutorService.allowsCoreThreadTimeOut()) {
                E elem = poll(delayMillis, TimeUnit.MILLISECONDS);
                if (elem == null) {
                    compilerThreadIdled();
                } else {
                    return elem;
                }
            }
            // Fallback to blocking version.
            return super.take();
        }
    }

    /**
     * Called when a compiler thread becomes idle for more than {@code delayMillis}.
     */
    protected void compilerThreadIdled() {
        // nop
    }
}
