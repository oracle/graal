/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.hotspot.HotSpotGraalCompiler.fmt;

import java.util.Arrays;

import com.oracle.graal.debug.TTY;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.meta.ResolvedJavaMethod;

class CompilationWatchDog extends Thread implements AutoCloseable {

    public static class Options {
        // @formatter:off
        @Option(help = "Delay in seconds before watch dog monitoring a compilation (0 disables monitoring).", type = OptionType.Debug)
        public static final OptionValue<Double> CompilationWatchDogStartDelay = new OptionValue<>(0.0D);
        @Option(help = "Interval in seconds between a watch dog reporting stack traces for long running compilations.", type = OptionType.Debug)
        public static final OptionValue<Double> CompilationWatchDogStackTraceInterval = new OptionValue<>(30.0D);
        @Option(help = "Number of contiguous identical compiler thread snapshot stack traces to be interpreted as a stuck " +
                        "compilation and cause the VM to exit.", type = OptionType.Debug)
         public static final OptionValue<Integer> IdenticalCompilationSnapshotsLimit = new OptionValue<>(8);
        // @formatter:on
    }

    private enum WatchDogState {
        /**
         * The watch dog thread sleeps currently, either no method is currently compiled, or no
         * method is compiled long enough to be monitored.
         */
        SLEEPING,
        /**
         * The watch dog thread identified a compilation that already takes long enough to be
         * interesting. It will sleep and wake up periodically and check if the current compilation
         * takes too long. If it takes too long it will start collecting stack traces from the
         * compiler thread.
         */
        WATCHING_WITHOUT_STACK_INSPECTION,
        /**
         * The watch dog thread is fully monitoring the compiler thread. It takes stack traces
         * periodically and sleeps again until the next period. If the number of stack traces
         * reaches a certain upper bound and those stack traces are equal it will shut down the
         * entire VM with an error.
         */
        WATCHING_WITH_STACK_INSPECTION
    }

    /**
     * Methods below this sleep timeout will, mostly, not be recognized by the watch dog. The watch
     * dog thread only wakes up each SPIN_TIMEOUT milliseconds to check whether it should change its
     * internal state to monitoring as the same method is compiled enough to be interesting.
     */
    private static final int SPIN_TIMEOUT_MS = 500;
    private static final long START_DELAY_MS = ms(Options.CompilationWatchDogStartDelay.getValue());
    private static final long STACK_TRACE_INTERVAL_MS = ms(Options.CompilationWatchDogStackTraceInterval.getValue());
    private static final boolean ENABLED = START_DELAY_MS > 0.0D;

    private WatchDogState state = WatchDogState.SLEEPING;
    private final Thread compilerThread;
    private volatile ResolvedJavaMethod currentMethod;
    private ResolvedJavaMethod lastWatched;

    // The 4 fields below are for a single compilation being watched
    private long elapsed;
    private int traceIntervals;
    private int numberOfIdenticalStackTraces;
    private StackTraceElement[] lastStackTrace;

    CompilationWatchDog(Thread compilerThread) {
        this.compilerThread = compilerThread;
        this.setName("WatchDog" + getId() + "[" + compilerThread.getName() + "]");
        this.setPriority(Thread.MAX_PRIORITY);
        this.setDaemon(true);
    }

    public void startCompilation(ResolvedJavaMethod method) {
        trace("start %s", fmt(method));
        this.currentMethod = method;
    }

    public void stopCompilation() {
        trace(" stop %s", fmt(currentMethod));
        this.currentMethod = null;
    }

    private void reset() {
        elapsed = 0;
        traceIntervals = 0;
        numberOfIdenticalStackTraces = 0;
        lastWatched = null;
        lastStackTrace = null;
        state = WatchDogState.SLEEPING;
    }

    private void tick(WatchDogState newState) {
        state = newState;
    }

    /**
     * Saves the current stack trace {@link StackTraceElement} of the monitored compiler thread
     * {@link CompilationWatchDog#compilerThread}.
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
    static final boolean DEBUG = false;

    private void trace(String format, Object... args) {
        if (DEBUG) {
            TTY.println(this + ": " + String.format(format, args));
        }
    }

    private static long ms(double seconds) {
        return (long) seconds * 1000;
    }

    private static double secs(long ms) {
        return (double) ms / 1000;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public void run() {
        TTY.printf("Started %s%n", this);
        try {
            while (true) {
                // get a copy of the last set method
                final ResolvedJavaMethod currentlyCompiling = currentMethod;
                if (currentlyCompiling == null) {
                    // continue sleeping, compilation is either over before starting
                    // to watch the compiler thread or no compilation at all started
                    reset();
                } else {
                    switch (state) {
                        case SLEEPING:
                            lastWatched = currentlyCompiling;
                            elapsed = 0;
                            tick(WatchDogState.WATCHING_WITHOUT_STACK_INSPECTION);
                            break;
                        case WATCHING_WITHOUT_STACK_INSPECTION:
                            if (currentlyCompiling == lastWatched) {
                                if (elapsed >= START_DELAY_MS) {
                                    // we looked at the same compilation for a certain time
                                    // so now we start to collect stack traces
                                    tick(WatchDogState.WATCHING_WITH_STACK_INSPECTION);
                                    trace("changes mode to watching with stack traces");
                                } else {
                                    // we still compile the same method but won't collect traces yet
                                    trace("watching without stack traces [%.2f seconds]", secs(elapsed));
                                }
                                elapsed += SPIN_TIMEOUT_MS;
                            } else {
                                // compilation finished before we exceeded initial watching period
                                reset();
                            }
                            break;
                        case WATCHING_WITH_STACK_INSPECTION:
                            if (currentlyCompiling == lastWatched) {
                                if (elapsed >= START_DELAY_MS + (traceIntervals * STACK_TRACE_INTERVAL_MS)) {
                                    trace("took a stack trace");
                                    boolean newStackTrace = recordStackTrace(compilerThread.getStackTrace());
                                    if (!newStackTrace) {
                                        trace("%d identical stack traces in a row", numberOfIdenticalStackTraces);
                                        numberOfIdenticalStackTraces = 0;
                                    }
                                    numberOfIdenticalStackTraces++;
                                    if (numberOfIdenticalStackTraces > Options.IdenticalCompilationSnapshotsLimit.getValue()) {
                                        synchronized (CompilationWatchDog.class) {
                                            TTY.printf("======================= WATCH DOG THREAD =======================%n" +
                                                            "%s took %d identical stack traces, which indicates a stuck compilation of %s%n%sExiting VM%n", this,
                                                            numberOfIdenticalStackTraces, fmt(currentMethod), fmt(lastStackTrace));
                                        }
                                        System.exit(-1);
                                    } else if (newStackTrace) {
                                        synchronized (CompilationWatchDog.class) {
                                            TTY.printf("======================= WATCH DOG THREAD =======================%n" +
                                                            "%s detected long running compilation of %s [%.2f seconds]%n%s", this, fmt(currentMethod),
                                                            secs(elapsed), fmt(lastStackTrace));
                                        }
                                    }
                                    traceIntervals++;
                                } else {
                                    // we still watch the compilation in the same trace interval
                                    trace("watching with stack traces [%.2f seconds]", secs(elapsed));
                                }
                                elapsed += SPIN_TIMEOUT_MS;
                            } else {
                                // compilation finished before we are able to collect stack traces
                                reset();
                            }
                            break;
                        default:
                            break;
                    }
                }
                Thread.sleep(SPIN_TIMEOUT_MS);
            }
        } catch (Throwable t) {
            synchronized (CompilationWatchDog.class) {
                TTY.printf("%s encountered an exception%n%sExiting VM%n", this, fmt(t));
            }
            System.exit(-1);
        }
    }

    private static final ThreadLocal<CompilationWatchDog> WATCH_DOGS = ENABLED ? new ThreadLocal<>() : null;

    /**
     * Opens a scope in which a given method is about to be compiled.
     *
     * @param method a method about to be compiled
     * @return {@code null} if the compilation watch dog is disabled otherwise this object. The
     *         returned value should be used in a {@code try}-with-resources statement whose scope
     *         is the whole compilation so that leaving the scope will cause {@link #close()} to be
     *         called.
     */
    static CompilationWatchDog startingCompilation(ResolvedJavaMethod method) {
        if (ENABLED) {
            // Lazily get a watch dog thread for the current compiler thread
            CompilationWatchDog watchDog = WATCH_DOGS.get();
            if (watchDog == null) {
                Thread currentThread = currentThread();
                watchDog = new CompilationWatchDog(currentThread);
                WATCH_DOGS.set(watchDog);
                watchDog.start();
            }
            watchDog.startCompilation(method);
            return watchDog;
        }
        return null;
    }

    @Override
    public void close() {
        stopCompilation();
    }
}
