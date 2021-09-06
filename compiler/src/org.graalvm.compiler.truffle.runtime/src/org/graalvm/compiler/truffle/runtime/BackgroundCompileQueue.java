/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

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

    protected final GraalTruffleRuntime runtime;
    private final AtomicLong idCounter;
    private volatile ThreadPoolExecutor compilationExecutorService;
    private volatile BlockingQueue<Runnable> compilationQueue;
    private boolean shutdown = false;
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

            this.compilationQueue = createQueue(callTarget, threads);
            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(threads, threads,
                            keepAliveTime, TimeUnit.MILLISECONDS,
                            compilationQueue, factory) {
                @Override
                @SuppressWarnings({"unchecked"})
                protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
                    return (RunnableFuture<T>) new CompilationTask.ExecutorServiceWrapper((CompilationTask) callable);
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

    private BlockingQueue<Runnable> createQueue(OptimizedCallTarget callTarget, int threads) {
        if (callTarget.getOptionValue(PolyglotCompilerOptions.TraversingCompilationQueue)) {
            if (callTarget.getOptionValue(PolyglotCompilerOptions.DynamicCompilationThresholds) && callTarget.getOptionValue(PolyglotCompilerOptions.BackgroundCompilation)) {
                double minScale = callTarget.getOptionValue(PolyglotCompilerOptions.DynamicCompilationThresholdsMinScale);
                int minNormalLoad = callTarget.getOptionValue(PolyglotCompilerOptions.DynamicCompilationThresholdsMinNormalLoad);
                int maxNormalLoad = callTarget.getOptionValue(PolyglotCompilerOptions.DynamicCompilationThresholdsMaxNormalLoad);
                return new DynamicThresholdsQueue(threads, minScale, minNormalLoad, maxNormalLoad);
            } else {
                return new TraversingBlockingQueue();
            }
        } else {
            return new IdlingPriorityBlockingQueue<>();
        }
    }

    @SuppressWarnings("unused")
    protected ThreadFactory newThreadFactory(String threadNamePrefix, OptimizedCallTarget callTarget) {
        return new TruffleCompilerThreadFactory(threadNamePrefix, runtime);
    }

    private CompilationTask submitTask(CompilationTask compilationTask) {
        compilationTask.setFuture(getExecutorService(compilationTask.targetRef.get()).submit(compilationTask));
        return compilationTask;
    }

    public CompilationTask submitCompilation(Priority priority, OptimizedCallTarget target) {
        final WeakReference<OptimizedCallTarget> targetReference = new WeakReference<>(target);
        CompilationTask compilationTask = CompilationTask.createCompilationTask(priority, targetReference, nextId());
        return submitTask(compilationTask);
    }

    public CompilationTask submitInitialization(OptimizedCallTarget target, Consumer<CompilationTask> action) {
        final WeakReference<OptimizedCallTarget> targetReference = new WeakReference<>(target);
        CompilationTask initializationTask = CompilationTask.createInitializationTask(targetReference, action);
        return submitTask(initializationTask);
    }

    private long nextId() {
        return idCounter.getAndIncrement();
    }

    public int getQueueSize() {
        final ThreadPoolExecutor threadPool = compilationExecutorService;
        if (threadPool != null) {
            return threadPool.getQueue().size();
        } else {
            return 0;
        }
    }

    /**
     * Return call targets waiting in queue. This does not include call targets currently being
     * compiled.
     */
    public Collection<OptimizedCallTarget> getQueuedTargets(EngineData engine) {
        BlockingQueue<Runnable> queue = this.compilationQueue;
        if (queue == null) {
            // queue not initialized
            return Collections.emptyList();
        }
        List<OptimizedCallTarget> queuedTargets = new ArrayList<>();
        CompilationTask.ExecutorServiceWrapper[] array = queue.toArray(new CompilationTask.ExecutorServiceWrapper[0]);
        for (CompilationTask.ExecutorServiceWrapper wrapper : array) {
            OptimizedCallTarget target = wrapper.compileTask.targetRef.get();
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

    /**
     * Called when a compiler thread becomes idle for more than {@code delayMillis}.
     */
    protected void compilerThreadIdled() {
        // nop
    }

    static class Priority {

        public static final Priority INITIALIZATION = new Priority(0, Tier.INITIALIZATION);
        final Tier tier;
        final int value;

        Priority(int value, Tier tier) {
            this.value = value;
            this.tier = tier;
        }

        public enum Tier {
            INITIALIZATION,
            FIRST,
            LAST
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

}
