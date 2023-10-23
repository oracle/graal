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
package jdk.graal.compiler.core;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.util.Util;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.IsolateUtil;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.services.Services;

/**
 * A watch dog for {@linkplain #watch watching} and reporting on long running compilations.
 *
 * For each compilation, a watch dog task is scheduled with an initial delay of
 * {@link Options#CompilationWatchDogStartDelay} seconds. Once a scheduled watch dog task starts and
 * the compilation is still running, it will {@linkplain EventHandler#onLongCompilation report} a
 * stack trace for the compilation. Further reports will continue until the compilation stops. The
 * period between reports is doubled each time (i.e., at 1, 2, 4, 8 seconds etc).
 *
 * If {@code D ==} {@link Options#CompilationWatchDogVMExitDelay} and {@code D != 0} and the
 * compilation appears to make no progress for {@code D} seconds (i.e., identical stack traces are
 * seen for {@code D} seconds), it is {@linkplain EventHandler#onStuckCompilation reported} as
 * stuck. It is expected most watch dog event handlers will exit the VM in this case. For this
 * reason, the default value is 0 as VM exiting needs to be an opt-in behavior.
 *
 * The executor used to schedule and run the tasks uses a single thread that will terminate when
 * compilation goes idle. The latter is important to also allow destroying a libgraal isolate when
 * compilation goes idle.
 *
 * The above means only 1 task is run at a time. Most watch dog tasks are expected to be cancelled
 * while waiting to be run. A problematic compilation will eventually be watched as the executor
 * effectively sorts its tasks in the order they are received.
 */
public final class CompilationWatchDog implements Runnable, AutoCloseable {

    /**
     * An object whose {@link #toString()} method returns the current thread's name along with a
     * {@code "@<id>"} suffix if running in a Native Image where {@code <id>} is the current isolate
     * ID (e.g., {@code "WatchDog-2@1"}).
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
         * Notifies this object that a compilation appears to be stuck. The typical action to take
         * is to exit the VM with exit code {@link #STUCK_COMPILATION_EXIT_CODE}.
         *
         * @param watchDog the watch dog watching the compilation
         * @param watched the watched compiler thread
         * @param compilation the compilation
         * @param stackTrace snapshot stack trace for {@code watched}
         * @param stuckTime seconds compilation appears to have been stuck for
         */
        default void onStuckCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier compilation,
                        StackTraceElement[] stackTrace, int stuckTime) {
            TTY.printf("======================= WATCH DOG =======================%n" +
                            "%s: observed identical stack traces for %d seconds, indicating a stuck compilation %s%n%s%n", CURRENT_THREAD_LABEL,
                            stuckTime, compilation, Util.toString(stackTrace));
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
        EventHandler DEFAULT = new EventHandler() {
        };
    }

    public static class Options {
        // @formatter:off
        @Option(help = "Delay in seconds before watch dog monitors a compilation (0 disables monitoring).", type = OptionType.Debug)
        public static final OptionKey<Integer> CompilationWatchDogStartDelay = new OptionKey<>(0);
        @Option(help = "Number of seconds after which a compilation appearing to make no progress causes the VM to exit " +
                       "(0 disables VM exiting).", type = OptionType.Debug)
        public static final OptionKey<Integer> CompilationWatchDogVMExitDelay = new OptionKey<>(0);
        // @formatter:on
    }

    private final Thread watchedThread;

    /**
     * Support for handling a watch event.
     */
    private final EventHandler eventHandler;

    /**
     * @see Options#CompilationWatchDogVMExitDelay
     */
    private final int vmExitDelay;

    /**
     * Object representing the compilation being watched. It is volatile as it is used to
     * communicate when the compilation is finished.
     */
    private volatile CompilationIdentifier compilation;

    private StackTraceElement[] lastStackTrace;

    private final ScheduledExecutorService singleShotExecutor;

    CompilationWatchDog(CompilationIdentifier compilation, Thread watchedThread, int delay, int vmExitDelay,
                    boolean singleShotExecutor, EventHandler eventHandler) {
        this.compilation = compilation;
        this.watchedThread = watchedThread;
        this.vmExitDelay = vmExitDelay;
        this.eventHandler = eventHandler == null ? EventHandler.DEFAULT : eventHandler;
        trace("started compiling %s", compilation);
        if (singleShotExecutor) {
            this.singleShotExecutor = createExecutor();
            this.task = this.singleShotExecutor.schedule(this, delay, TimeUnit.SECONDS);
        } else {
            this.singleShotExecutor = null;
            this.task = schedule(this, delay);
        }
    }

    private void stopCompilation() {
        trace("stopped compiling %s", compilation);
        this.compilation = null;
        this.task.cancel(true);
        if (singleShotExecutor != null) {
            singleShotExecutor.shutdownNow();
            while (!singleShotExecutor.isTerminated()) {
                try {
                    singleShotExecutor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Saves the current stack trace {@link StackTraceElement} of the monitored compiler thread
     * {@link CompilationWatchDog#watchedThread}.
     *
     * @param newStackTrace the current stack trace of the monitored compiler thread
     * @return {@code true} if the stack trace is not equal to the last stack trace or it is the
     *         first one, {@code false} otherwise.
     */
    private boolean recordStackTrace(StackTraceElement[] newStackTrace) {
        if (lastStackTrace == null || !Arrays.equals(lastStackTrace, newStackTrace)) {
            lastStackTrace = newStackTrace;
            return true;
        }
        return false;
    }

    private final boolean debug = Boolean.parseBoolean(Services.getSavedProperty("debug.graal.CompilationWatchDog"));

    /**
     * Handle to scheduled task.
     */
    private final ScheduledFuture<?> task;

    private void trace(String format, Object... args) {
        if (debug) {
            TTY.println(CURRENT_THREAD_LABEL + ": " + String.format(format, args));
        }
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
            long start = System.currentTimeMillis();
            long elapsed = 0;
            int reportDelay = 1000;
            long nextReport = start;
            long lastUniqueStackTrace = start;
            while (compilation != null) {
                long now = System.currentTimeMillis();
                comp = compilation;
                trace("took a stack trace [%.3f seconds]", secs(elapsed));
                boolean uniqueStackTrace = recordStackTrace(watchedThread.getStackTrace());
                if (uniqueStackTrace) {
                    lastUniqueStackTrace = now;
                }
                int stuckTime = (int) ((now - lastUniqueStackTrace) / 1000);
                if (vmExitDelay != 0 && stuckTime >= vmExitDelay) {
                    eventHandler.onStuckCompilation(this, watchedThread, comp, lastStackTrace, stuckTime);
                } else if (uniqueStackTrace) {
                    if (now >= nextReport) {
                        eventHandler.onLongCompilation(this, watchedThread, comp, elapsed, lastStackTrace);
                        nextReport = now + reportDelay;
                        reportDelay <<= 1;
                    }
                }
                try {
                    Thread.sleep(1000);
                    elapsed = System.currentTimeMillis() - start;
                } catch (InterruptedException e) {
                    elapsed = System.currentTimeMillis() - start;
                    trace("interrupted [%.3f seconds]", secs(elapsed));
                }
            }
            trace("stopped watching %s [%.3f seconds]", comp, secs(elapsed));
        } catch (Throwable t) {
            eventHandler.onException(t);
        }
    }

    @NativeImageReinitialize private static ScheduledExecutorService watchDogService;

    private static synchronized ScheduledFuture<?> schedule(CompilationWatchDog watchdog, int delay) {
        if (watchDogService == null) {
            watchDogService = createExecutor();
        }
        return watchDogService.schedule(watchdog, delay, TimeUnit.SECONDS);
    }

    private static ScheduledExecutorService createExecutor() {
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
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(poolSize, threadFactory);
        executor.setRemoveOnCancelPolicy(true);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /**
     * Opens a scope for watching a compilation.
     *
     * @param compilation identifies the compilation being watched
     * @param singleShotExecutor if true, then a dedicated executor is created for this task and it
     *            is shutdown once the compilation ends
     * @param eventHandler notified of events like a compilation running long running or getting
     *            stuck. If {@code null}, {@link EventHandler#DEFAULT} is used.
     * @return {@code null} if the compilation watch dog is disabled otherwise a new
     *         {@link CompilationWatchDog} object. The returned value should be used in a
     *         {@code try}-with-resources statement whose scope is the whole compilation so that
     *         leaving the scope will cause {@link #close()} to be called.
     */
    public static CompilationWatchDog watch(CompilationIdentifier compilation, OptionValues options, boolean singleShotExecutor, EventHandler eventHandler) {
        int delay = Options.CompilationWatchDogStartDelay.getValue(options);
        if (Services.IS_BUILDING_NATIVE_IMAGE && !Options.CompilationWatchDogStartDelay.hasBeenSet(options)) {
            // Disable watch dog by default when building a native image
            delay = 0;
        }
        if (delay > 0) {
            Thread watchedThread = Thread.currentThread();
            int vmExitDelay = Options.CompilationWatchDogVMExitDelay.getValue(options);
            CompilationWatchDog watchDog = new CompilationWatchDog(compilation, watchedThread, delay,
                            vmExitDelay, singleShotExecutor, eventHandler);
            return watchDog;
        }
        return null;
    }

    @Override
    public void close() {
        stopCompilation();
    }
}
