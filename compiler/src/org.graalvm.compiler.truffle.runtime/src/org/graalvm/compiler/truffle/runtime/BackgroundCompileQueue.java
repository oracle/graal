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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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
    private volatile IdlingPriorityBlockingQueue<Runnable> compilationQueue;
    private boolean shutdown = false;
    protected final GraalTruffleRuntime runtime;
    private long delayMillis;

    public BackgroundCompileQueue(GraalTruffleRuntime runtime) {
        this.runtime = runtime;
        this.idCounter = new AtomicLong();
    }

    // Largest i such that 2^i <= n.
    private static int log2(int n) {
        assert n > 0;
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    private ExecutorService getExecutorService(OptimizedCallTarget callTarget) {
        ExecutorService service = this.compilationExecutorService;
        if (service != null) {
            return service;
        }
        synchronized (this) {
            service = this.compilationExecutorService;
            if (service != null) {
                return service;
            }
            if (shutdown) {
                throw new RejectedExecutionException("The BackgroundCompileQueue is shutdown");
            }

            // NOTE: The value from the first Engine compiling wins for now
            this.delayMillis = callTarget.getOptionValue(PolyglotCompilerOptions.EncodedGraphCachePurgeDelay);

            // NOTE: the value from the first Engine compiling wins for now
            int threads = callTarget.getOptionValue(PolyglotCompilerOptions.CompilerThreads);
            if (threads == 0) {
                // Old behavior, use either 1 or 2 compiler threads.
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                if (availableProcessors >= 4) {
                    threads = 2;
                }
            } else if (threads < 0) {
                // Scale compiler threads depending on how many processors are available.
                int availableProcessors = Runtime.getRuntime().availableProcessors();

                // @formatter:off
                // compilerThreads = Math.min(availableProcessors / 4 + loglogCPU)
                // Produces reasonable values for common core/thread counts (with HotSpot numbers for reference):
                // cores=2  threads=4  compilerThreads=2  (HotSpot=3:  C1=1 C2=2)
                // cores=4  threads=8  compilerThreads=3  (HotSpot=4:  C1=1 C2=3)
                // cores=6  threads=12 compilerThreads=4  (HotSpot=4:  C1=1 C2=3)
                // cores=8  threads=16 compilerThreads=6  (HotSpot=12: C1=4 C2=8)
                // cores=10 threads=20 compilerThreads=7  (HotSpot=12: C1=4 C2=8)
                // cores=12 threads=24 compilerThreads=8  (HotSpot=12: C1=4 C2=8)
                // cores=16 threads=32 compilerThreads=10 (HotSpot=15: C1=5 C2=10)
                // cores=18 threads=36 compilerThreads=11 (HotSpot=15: C1=5 C2=10)
                // cores=24 threads=48 compilerThreads=14 (HotSpot=15: C1=5 C2=10)
                // cores=28 threads=56 compilerThreads=16 (HotSpot=15: C1=5 C2=10)
                // cores=32 threads=64 compilerThreads=18 (HotSpot=18: C1=6 C2=12)
                // cores=36 threads=72 compilerThreads=20 (HotSpot=18: C1=6 C2=12)
                // @formatter:on
                int logCPU = log2(availableProcessors);
                int loglogCPU = log2(Math.max(logCPU, 1));
                threads = Math.min(availableProcessors / 4 + loglogCPU, 16); // capped at 16
            }
            threads = Math.max(1, threads);

            ThreadFactory factory = newThreadFactory("TruffleCompilerThread", callTarget);

            long compilerIdleDelay = runtime.getCompilerIdleDelay(callTarget);
            long keepAliveTime = compilerIdleDelay >= 0 ? compilerIdleDelay : 0;

            this.compilationQueue = new IdlingPriorityBlockingQueue<>();
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(threads, threads,
                            keepAliveTime, TimeUnit.MILLISECONDS,
                            compilationQueue, factory) {
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
        CancellableCompileTask cancellable = new CancellableCompileTask(targetReference, priority.tier == Priority.Tier.LAST);
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
            BlockingQueue<Runnable> queue = ((ThreadPoolExecutor) threadPool).getQueue();
            int count = 0;
            for (Runnable runnable : queue) {
                RequestFutureTask<?> task = (RequestFutureTask<?>) runnable;
                if (!task.isCancelled() && !task.request.task.isCancelled()) {
                    count++;
                }
            }
            return count;
        } else {
            return 0;
        }
    }

    /**
     * Return call targets waiting in queue. This does not include call targets currently being
     * compiled.
     */
    public Collection<OptimizedCallTarget> getQueuedTargets(EngineData engine) {
        IdlingPriorityBlockingQueue<Runnable> queue = this.compilationQueue;
        if (queue == null) {
            // queue not initialized
            return Collections.emptyList();
        }
        List<OptimizedCallTarget> queuedTargets = new ArrayList<>();
        RequestFutureTask<?>[] array = queue.toArray(new RequestFutureTask<?>[0]);
        for (RequestFutureTask<?> task : array) {
            OptimizedCallTarget target = task.request.targetRef.get();
            if (target != null && target.engine == engine) {
                queuedTargets.add(target);
            }
        }
        return Collections.unmodifiableCollection(queuedTargets);
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

    public static class Priority {

        public enum Tier {
            INITIALIZATION,
            FIRST,
            LAST
        }

        public static final Priority INITIALIZATION = new Priority(0, Tier.INITIALIZATION);

        private final Tier tier;
        private final int value;

        Priority(int value, Tier tier) {
            this.value = value;
            this.tier = tier;
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
        private final boolean priorityQueue;
        private final boolean multiTier;

        RequestImpl(long id, Priority priority, WeakReference<OptimizedCallTarget> targetRef, CancellableCompileTask task, Request request) {
            this.id = id;
            this.priority = priority;
            this.targetRef = targetRef;
            OptimizedCallTarget target = targetRef.get();
            priorityQueue = target != null && target.getOptionValue(PolyglotCompilerOptions.PriorityQueue);
            multiTier = target != null && target.getOptionValue(PolyglotCompilerOptions.MultiTier);
            this.task = task;
            this.request = request;
        }

        @Override
        public int compareTo(RequestImpl<?> that) {
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

        /**
         * We only want priority for the "escape from interpreter" compilations. If multi tier is
         * enabled, that means *only* first tier compilations, otherwise it means last tier.
         */
        private boolean priorityQueueEnabled() {
            return priorityQueue && ((multiTier && priority.tier == Priority.Tier.FIRST) || (!multiTier && priority.tier == Priority.Tier.LAST));
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
