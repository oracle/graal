/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Future.State;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.IsolateUtil;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.services.Services;

/**
 * A watch dog for reporting long running compilations. This is designed to be an always on
 * mechanism for the purpose of getting better reports from customer sites. It logs messages to
 * {@link TTY}.
 *
 * A watch dog task is created for each compilation. It wakes up every {@value #SPIN_TIMEOUT_MS}
 * milliseconds to observe the state of the compiler thread. After the first
 * {@link Options#CompilationWatchDogStartDelay} seconds of compilation, the watch dog reports a
 * long running compilation. Every {@link Options#CompilationWatchDogStackTraceInterval} seconds
 * after that point in time where the compilation is still executing, the watch dog takes a stack
 * trace of the compiler thread. If {@link Options#CompilationWatchDogStuckCompilationThreshold} is
 * not 0 and {@link Options#CompilationWatchDogStuckCompilationThreshold} contiguous identical stack
 * traces are seen, the watch dog regards the compilation as stuck. The action taken for a stuck
 * compilation depends of the value of the {@code stuckCompilationHandler} parameter to
 * {@link #watch}.
 */
public class CompilationWatchDog implements Runnable, AutoCloseable {

    /**
     * An object whose {@link #toString()} method returns the current thread's name along with a
     * {@code "@<id>"} suffix if running in a Native Image where {@code <id>} is the current isolate
     * ID (e.g., {@code "WatchDog-2@1"})
     */
    public static final Object CURRENT_THREAD_LABEL = new Object() {
        @Override
        public String toString() {
            String isolateID = IsolateUtil.getIsolateID(false);
            if (!isolateID.isEmpty()) {
                isolateID = "@" + isolateID;
            }
            return Thread.currentThread().getName() + isolateID;
        }
    };

    /**
     * Acts on compilation watch dog events.
     */
    public interface EventHandler {

        /**
         * Exit code to be used when exiting a process due to a stuck compilation.
         */
        int STUCK_COMPILATION_EXIT_CODE = 84;

        /**
         * Notifies this object that a compilation is long running.
         *
         * @param watchDog the watch dog watching the compilation
         * @param watched the watched compiler thread
         * @param compilation the compilation
         * @param elapsed milliseconds the compilation has been observed by the watch dog task which
         *            may be shorter that time since compilation started
         * @param stackTrace snapshot stack trace for {@code watched}
         */
        default void onLongCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier compilation,
                        long elapsed, StackTraceElement[] stackTrace) {
            TTY.printf("======================= WATCH DOG =======================%n" +
                            "%s: detected long running compilation %s [%.3f seconds]%n%s", CURRENT_THREAD_LABEL, compilation,
                            secs(elapsed), Util.toString(stackTrace));
        }

        /**
         * Notifies this object that a compilation appears to be stuck.
         *
         * @param watchDog the watch dog watching the compilation
         * @param watched the watched compiler thread
         * @param compilation the compilation
         * @param stackTrace snapshot stack trace for {@code watched}
         * @param stackTraceCount number of times {@code stackTrace} was seen in a row
         */
        default void onStuckCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier compilation,
                        StackTraceElement[] stackTrace, int stackTraceCount) {
            TTY.printf("======================= WATCH DOG =======================%n" +
                            "%s: observed %d identical stack traces, indicating a stuck compilation %s%n%s%n", CURRENT_THREAD_LABEL,
                            stackTraceCount, compilation, Util.toString(stackTrace));
        }

        /**
         * Notifies this object that {@code exception} occurred while watching a compilation.
         *
         * @param exception
         */
        default void onException(Throwable exception) {
            if (exception instanceof OutOfMemoryError) {
                // Silently swallow an OOME. If memory is really limited, the compiler
                // thread will hit it and take appropriate reporting/handling action.
            } else {
                exception.printStackTrace(TTY.out);
            }
        }

        /**
         * A shareable, default implementation of {@link EventHandler}.
         */
        static EventHandler DEFAULT = new EventHandler() {
        };
    }

    public static class Options {
        // @formatter:off
        @Option(help = "Delay in seconds before watch dog monitors a compilation (0 disables monitoring).", type = OptionType.Debug)
        public static final OptionKey<Integer> CompilationWatchDogStartDelay = new OptionKey<>(0);
        @Option(help = "Interval in seconds between a watch dog reporting stack traces for long running compilations.", type = OptionType.Debug)
        public static final OptionKey<Integer> CompilationWatchDogStackTraceInterval = new OptionKey<>(0);
        @Option(help = "Number of contiguous identical compiler thread stack traces allowed before the VM exits " +
                       "on the basis of a stuck compilation (0 disables VM exiting).", type = OptionType.Debug)
        public static final OptionKey<Integer> CompilationWatchDogStuckCompilationThreshold = new OptionKey<>(0);
        // @formatter:on
    }

    private enum WatchDogState {
        /**
         * The execution time of the watched compilation is less than
         * {@link Options#CompilationWatchDogStartDelay}.
         */
        WATCHING_WITHOUT_STACK_INSPECTION,

        /**
         * The execution time of the watched compilation is longer than
         * {@link Options#CompilationWatchDogStartDelay}. The watch dog task stack traces
         * periodically and sleeps again until the next period.
         */
        WATCHING_WITH_STACK_INSPECTION
    }

    /**
     * The number of milliseconds a watch dog task sleeps between observing the state of the
     * compilation thread it is associated with. Most compilations are expected to complete within
     * this time period and thus not be actively monitored by the watch dog.
     */
    private static final int SPIN_TIMEOUT_MS = 1000;

    private WatchDogState state = WatchDogState.WATCHING_WITHOUT_STACK_INSPECTION;

    private final Thread watchedThread;

    /**
     * Support for handling a watch event.
     */
    private final EventHandler eventHandler;

    /**
     * Time at which the watched compilation was started.
     */
    private final long startTime;

    /**
     * Delay in milliseconds before watch dog task actively monitors the compilation
     */
    private final long startDelayMilliseconds;

    /**
     * @see Options#CompilationWatchDogStackTraceInterval
     */
    private final long stackTraceIntervalMilliseconds;

    /**
     * @see Options#CompilationWatchDogStuckCompilationThreshold
     */
    private final int stuckCompilationThreshold;

    /**
     * Object representing the compilation being watched. It is volatile as it is used to
     * communicate when the compilation is finished.
     */
    private volatile CompilationIdentifier compilation;

    /**
     * Elapsed intervals of {@link Options#CompilationWatchDogStackTraceInterval} duration.
     */
    private int traceIntervals;

    private int numberOfIdenticalStackTraces;
    private StackTraceElement[] lastStackTrace;

    CompilationWatchDog(CompilationIdentifier compilation, Thread watchedThread, long startDelayMilliseconds,
                    long stackTraceIntervalMilliseconds, int stuckCompilationThreshold,
                    EventHandler eventHandler) {
        this.startTime = System.currentTimeMillis();
        this.compilation = compilation;
        this.watchedThread = watchedThread;
        this.startDelayMilliseconds = startDelayMilliseconds;
        this.stackTraceIntervalMilliseconds = stackTraceIntervalMilliseconds;
        this.stuckCompilationThreshold = stuckCompilationThreshold;
        this.eventHandler = eventHandler == null ? EventHandler.DEFAULT : eventHandler;
        trace("started compiling %s", compilation);
        this.task = submit(this);
    }

    private void stopCompilation() {
        trace("stopped compiling %s", compilation);
        this.compilation = null;
        if (task.state() == State.FAILED) {
            task.exceptionNow().printStackTrace(TTY.out);
        }
        this.task.cancel(true);
    }

    /**
     * Saves the current stack trace {@link StackTraceElement} of the monitored compiler thread
     * {@link CompilationWatchDog#watchedThread}.
     *
     * @param newStackTrace the current stack trace of the monitored compiler thread
     * @return {@code true} if the stack trace is equal to the last stack trace (or if it is the
     *         first one) and {@code false} if it is not equal to the last one.
     */
    private boolean recordStackTrace(StackTraceElement[] newStackTrace) {
        if (lastStackTrace == null) {
            lastStackTrace = newStackTrace;
            return true;
        }
        if (!Arrays.equals(lastStackTrace, newStackTrace)) {
            lastStackTrace = newStackTrace;
            return false;
        }
        return true;
    }

    /**
     * Set to true to debug the watch dog.
     */
    private final boolean debug = Boolean.parseBoolean(Services.getSavedProperties().get("debug.graal.CompilationWatchDog"));

    /**
     * Handle to submitted task.
     */
    private final Future<?> task;

    private void trace(String format, Object... args) {
        if (debug) {
            TTY.println(CURRENT_THREAD_LABEL + ": " + String.format(format, args));
        }
    }

    private static long ms(int seconds) {
        return seconds * 1000;
    }

    private static double secs(long ms) {
        return (double) ms / 1000;
    }

    @Override
    public String toString() {
        return "WatchDog[" + watchedThread.getName() + "]";
    }

    @Override
    public void run() {
        try {
            CompilationIdentifier comp = compilation;
            long elapsed = System.currentTimeMillis() - startTime;
            trace("started watching %s [%.3f seconds]", comp, secs(elapsed));
            while (compilation != null) {
                long start = System.currentTimeMillis();
                comp = compilation;
                switch (state) {
                    case WATCHING_WITHOUT_STACK_INSPECTION:
                        if (elapsed >= startDelayMilliseconds) {
                            state = WatchDogState.WATCHING_WITH_STACK_INSPECTION;
                            trace("changed mode to watching with stack traces [%.3f seconds]", secs(elapsed));
                        } else {
                            trace("watching without stack traces [%.3f seconds]", secs(elapsed));
                        }
                        break;
                    case WATCHING_WITH_STACK_INSPECTION:
                        if (elapsed >= startDelayMilliseconds + (traceIntervals * stackTraceIntervalMilliseconds)) {
                            trace("took a stack trace [%.3f seconds]", secs(elapsed));
                            boolean newStackTrace = recordStackTrace(watchedThread.getStackTrace());
                            if (!newStackTrace) {
                                numberOfIdenticalStackTraces = 0;
                            }
                            numberOfIdenticalStackTraces++;
                            if (stuckCompilationThreshold != 0 && numberOfIdenticalStackTraces >= stuckCompilationThreshold) {
                                eventHandler.onStuckCompilation(this, watchedThread, comp, lastStackTrace, numberOfIdenticalStackTraces);
                            } else if (newStackTrace) {
                                eventHandler.onLongCompilation(this, watchedThread, comp, elapsed, lastStackTrace);
                            }
                            traceIntervals++;
                        } else {
                            // Continue watching the compilation
                            trace("watching with stack traces [%.3f seconds]", secs(elapsed));
                        }
                        break;
                }
                try {
                    Thread.sleep(SPIN_TIMEOUT_MS);
                    elapsed += System.currentTimeMillis() - start;
                } catch (InterruptedException e) {
                    elapsed += System.currentTimeMillis() - start;
                    trace("interrupted [%.3f seconds]", secs(elapsed));
                }
            }
            trace("stopped watching %s [%.3f seconds]", comp, secs(elapsed));
        } catch (Throwable t) {
            eventHandler.onException(t);
        }
    }

    @NativeImageReinitialize private static ExecutorService watchDogService;

    private static synchronized Future<?> submit(CompilationWatchDog watchdog) {
        if (watchDogService == null) {
            watchDogService = createExecutor();
        }
        return watchDogService.submit(watchdog);
    }

    /**
     * Creates an executor for servicing watch dog tasks. The executor uses a single thread as
     * there's no urgency to run watch dog tasks. Most of them are expected to be cancelled before
     * the executor gets a chance to run them. The executor thread will stop and terminate if no
     * watch dog tasks are submitted for 1 second. This allows libgraal isolates to be destroyed
     * when compilation goes idle.
     */
    private static ThreadPoolExecutor createExecutor() {
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new GraalServiceThread(CompilationWatchDog.class.getSimpleName(), r);
                thread.setName("WatchDog-" + GraalServices.getThreadId(thread));
                thread.setPriority(Thread.MAX_PRIORITY);
                thread.setDaemon(true);
                return thread;
            }
        };

        int poolSize = 1;
        int keepAliveTime = 1;
        TimeUnit keepAliveTimeUnit = TimeUnit.SECONDS;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(poolSize, poolSize, keepAliveTime, keepAliveTimeUnit, workQueue, threadFactory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Opens a scope for watching a compilation.
     *
     * @param compilation identifies the compilation being watched
     * @param eventHandler notified of events like a compilation running long running or getting
     *            stuck. If {@code null}, {@link EventHandler#DEFAULT} is used.
     * @return {@code null} if the compilation watch dog is disabled otherwise a new
     *         {@link CompilationWatchDog} object. The returned value should be used in a
     *         {@code try}-with-resources statement whose scope is the whole compilation so that
     *         leaving the scope will cause {@link #close()} to be called.
     */
    public static CompilationWatchDog watch(CompilationIdentifier compilation, OptionValues options, EventHandler eventHandler) {
        long startDelayMilliseconds = ms(Options.CompilationWatchDogStartDelay.getValue(options));
        if (Services.IS_BUILDING_NATIVE_IMAGE && !Options.CompilationWatchDogStartDelay.hasBeenSet(options)) {
            // Disable watch dog by default when building a native image
            startDelayMilliseconds = 0;
        }
        if (startDelayMilliseconds > 0) {
            Thread watchedThread = Thread.currentThread();
            long stackTraceIntervalMilliseconds = ms(Options.CompilationWatchDogStackTraceInterval.getValue(options));
            int stuckCompilationThreshold = Options.CompilationWatchDogStuckCompilationThreshold.getValue(options);
            CompilationWatchDog watchDog = new CompilationWatchDog(compilation, watchedThread, startDelayMilliseconds, stackTraceIntervalMilliseconds,
                            stuckCompilationThreshold, eventHandler);
            return watchDog;
        }
        return null;
    }

    @Override
    public void close() {
        stopCompilation();
    }
}
