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

import java.util.Arrays;

import com.oracle.graal.debug.TTY;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.StableOptionValue;

import jdk.vm.ci.meta.ResolvedJavaMethod;

class CompilationWatchDogThread extends Thread {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable a watchdog thread for each compiler thread. " +
                       "A watchdog thread reports long running compilations and kills the VM if a certain time bound is reached.", type = OptionType.Debug)
        public static final OptionValue<Boolean> MonitorCompilerThreads = new StableOptionValue<>(false);
        @Option(help = "Number of contiguous identical compiler thread snapshot stack traces to be interpreted as a stuck " +
                       "compilation causing the VM to exit.", type = OptionType.Debug)
        public static final OptionValue<Integer> IdenticalCompilationSnapshotsLimit = new StableOptionValue<>(8);
        @Option(help = "Millisecond delay before starting watch dog monitoring for a compilation.", type = OptionType.Debug)
        public static final OptionValue<Integer> WatchDogStartDelay = new StableOptionValue<>(30 * 1000);
        @Option(help = "Millisecond interval between reporting stack traces for long running compilations.", type = OptionType.Debug)
        public static final OptionValue<Integer> WatchDogTraceInterval = new StableOptionValue<>(30 * 1000);
        @Option(help = "Watch dog threads dump information about the context to TTY.", type = OptionType.Debug)
        public static final OptionValue<Boolean> TraceWatchDogThreads = new StableOptionValue<>(false);
        // @formatter:on
    }

    private enum WatchDogState {
        /**
         * The watchdog thread sleeps currently, either no method is currently compiled, or no
         * method is compiled long enough to be monitored.
         */
        SLEEPING,
        /**
         * The watchdog thread identified a compilation that already takes long enough to be
         * interesting. It will sleep and wake up periodically and check if the current compilation
         * takes too long. If it takes too long it will start collecting stack traces from the
         * compiler thread.
         */
        WATCHING_WITHOUT_STACK_INSPECTION,
        /**
         * The watchdog thread is fully monitoring the compiler thread. It takes stack traces
         * periodically and sleeps again until the next period. If the number of stack traces
         * reaches a certain upper bound and those stack traces are equal it will shut down the
         * entire VM with an error.
         */
        WATCHING_WITH_STACK_INSPECTION
    }

    /*
     * Methods below this sleep timeout will, mostly, not even be recognized by the watchdog. The
     * watchdog thread only wakes up each SPIN_TIMEOUT ms to check whether it should change its
     * internal state to monitoring as the same method is compiled enough to be interesting.
     */
    private static final int SPIN_TIMEOUT = 500/* ms */;

    private final Thread compilerThread;
    private volatile ResolvedJavaMethod currentMethod;
    private ResolvedJavaMethod lastWatched;
    private long elapsedTimeWithoutStack;
    private long elapsedTimeWithStack;
    private int numberOfStackTraces;
    private StackTraceElement[] lastStackTrace;
    private WatchDogState state = WatchDogState.SLEEPING;

    CompilationWatchDogThread(Thread compilerThread) {
        this.compilerThread = compilerThread;
        this.setName("Watch dog thread " + getId());
        this.setPriority(Thread.MAX_PRIORITY);
        this.setDaemon(true);
    }

    public void startCompilation(ResolvedJavaMethod newMethod) {
        traceWatchDog("%s notified that compilation of method %s started", getTracePrefix(), newMethod);
        this.currentMethod = newMethod;
    }

    public void stopCompilation() {
        traceWatchDog("%s notified that compilation is finished", getTracePrefix());
        this.currentMethod = null;
    }

    private void sleep() {
        elapsedTimeWithStack = 0;
        elapsedTimeWithoutStack = 0;
        numberOfStackTraces = 0;
        lastWatched = null;
        lastStackTrace = null;
        state = WatchDogState.SLEEPING;
    }

    private void tick(WatchDogState newState) {
        state = newState;
    }

    /**
     * Saves the current stack trace {@link StackTraceElement} of the monitored compiler thread
     * {@link CompilationWatchDogThread#compilerThread}.
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

    private static void traceWatchDog(String s, Object... args) {
        if (Options.TraceWatchDogThreads.getValue()) {
            TTY.printf(s, args);
        }
    }

    private String getTracePrefix() {
        return "Watchdog for compiler thread [" + compilerThread.toString() + "]";
    }

    @Override
    public void run() {
        try {
            while (true) {
                // get a copy of the last set method
                final ResolvedJavaMethod currentlyCompiled = currentMethod;
                if (currentlyCompiled == null) {
                    // continue sleeping, compilation is either over before starting
                    // to watch the compiler thread or no compilation at all started
                    // (now)
                    sleep();
                } else {
                    switch (state) {
                        case SLEEPING:
                            traceWatchDog("%s picked up a method to monitor\n", getTracePrefix());
                            lastWatched = currentlyCompiled;
                            tick(WatchDogState.WATCHING_WITHOUT_STACK_INSPECTION);
                            break;
                        case WATCHING_WITHOUT_STACK_INSPECTION:
                            if (currentlyCompiled == lastWatched) {
                                if (elapsedTimeWithoutStack >= Options.WatchDogStartDelay.getValue()) {
                                    // we looked at the same thread for a certain time and
                                    // it still compiles one method, now we start to take
                                    // stake traces
                                    tick(WatchDogState.WATCHING_WITH_STACK_INSPECTION);
                                    traceWatchDog("%s changes mode to watching with stack traces\n", getTracePrefix());
                                } else {
                                    // we still compile the same method, watch until we
                                    // start to collect stack traces
                                    traceWatchDog("%s still watching, elapsed time watching %d\n", getTracePrefix(), elapsedTimeWithoutStack);
                                    elapsedTimeWithoutStack += SPIN_TIMEOUT;
                                }
                            } else {
                                // compilation finished before we exceeded the watching
                                // period of n minutes
                                sleep();
                            }
                            break;
                        case WATCHING_WITH_STACK_INSPECTION:
                            if (currentlyCompiled == lastWatched) {
                                if (elapsedTimeWithStack == 0 || elapsedTimeWithStack >= Options.WatchDogTraceInterval.getValue()) {
                                    traceWatchDog("%s took a stack trace\n", getTracePrefix());
                                    boolean newStackTrace = recordStackTrace(compilerThread.getStackTrace());
                                    traceWatchDog("%s:Last stack trace was %b equal?, took %d equal stack traces\n", getTracePrefix(), newStackTrace, numberOfStackTraces);
                                    if (!newStackTrace) {
                                        numberOfStackTraces = 0;
                                    }
                                    numberOfStackTraces++;
                                    elapsedTimeWithStack = 0;
                                    if (numberOfStackTraces > Options.IdenticalCompilationSnapshotsLimit.getValue()) {
                                        TTY.println("======================= WATCHDOG THREAD =======================");
                                        TTY.println("%s took %d stack traces, which is considered fatal, VM will quit now [compiling method %s]. Printing stack trace...", getTracePrefix(),
                                                        numberOfStackTraces, currentMethod.format("%H.%n(%p)"));
                                        printStackTraceTTY();
                                        System.exit(-1);
                                    } else if (numberOfStackTraces == 1) {
                                        TTY.println("======================= WATCHDOG THREAD =======================");
                                        TTY.println("%s detected a long running compilation (%dms). Currently compiled method is  %s. Printing stack trace...", getTracePrefix(),
                                                        (elapsedTimeWithoutStack + elapsedTimeWithStack), currentMethod.format("%H.%n(%p)"));
                                        printStackTraceTTY();
                                    }
                                    if (elapsedTimeWithStack == 0) {
                                        elapsedTimeWithStack += SPIN_TIMEOUT;
                                    }
                                } else {
                                    // we still compile the same method, watch until the stack trace
                                    // timeout happens
                                    traceWatchDog("%s still watching with stack traces, elapsed time in watching period %d\n", getTracePrefix(), elapsedTimeWithStack);
                                    elapsedTimeWithStack += SPIN_TIMEOUT;
                                }
                            } else {
                                // compilation finished before we are able to collect stack traces
                                sleep();
                            }
                            break;
                        default:
                            break;
                    }
                }
                Thread.sleep(SPIN_TIMEOUT);
            }
        } catch (Throwable t) {
            TTY.printf("%sn encoutnered an exception. Shutting down.", getTracePrefix());
            t.printStackTrace(TTY.out);
            System.exit(-1);
        }
    }

    private void printStackTraceTTY() {
        for (StackTraceElement e : lastStackTrace) {
            TTY.printf("\t%s\n", e.toString());
        }

    }
}
