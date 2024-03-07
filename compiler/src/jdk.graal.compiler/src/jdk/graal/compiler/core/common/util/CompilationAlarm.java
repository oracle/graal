/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.util;

import java.util.Arrays;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

/**
 * Utility class that allows the compiler to monitor compilations that take a very long time.
 */
public final class CompilationAlarm implements AutoCloseable {

    public static class Options {
        // @formatter:off
        @Option(help = "Time limit in seconds before a compilation expires (0 to disable the limit). " +
                       "A non-zero value for this option is doubled if assertions are enabled and quadrupled if DetailedAsserts is true.",
                type = OptionType.Debug)
        public static final OptionKey<Double> CompilationExpirationPeriod = new OptionKey<>(300d);
        @Option(help = "Time limit in seconds before a compilation expires (0 to disable the limit) because no progress was " +
                       "made in the compiler.",
                 type = OptionType.Debug)
        public static final OptionKey<Double> CompilationNoProgressPeriod = new OptionKey<>(30d);
        @Option(help = "Delay in seconds before compilation progress detection starts.",
                 type = OptionType.Debug)
        public static final OptionKey<Double> CompilationNoProgressStartTrackingProgressPeriod = new OptionKey<>(10d);
        // @formatter:on
    }

    private static final boolean LOG_PROGRESS_DETECTION = false;

    private CompilationAlarm(long expiration) {
        this.expiration = expiration;
    }

    /**
     * Thread local storage for the active compilation alarm.
     */
    private static final ThreadLocal<CompilationAlarm> currentAlarm = new ThreadLocal<>();

    private static final CompilationAlarm NEVER_EXPIRES = new CompilationAlarm(0);

    /**
     * Gets the current compilation alarm. If there is no current alarm, a non-null value is
     * returned that will always return {@code false} for {@link #hasExpired()}.
     */
    public static CompilationAlarm current() {
        CompilationAlarm alarm = currentAlarm.get();
        return alarm == null ? NEVER_EXPIRES : alarm;
    }

    /**
     * Determines if this alarm has expired. A compilation expires if it takes longer than
     * {@linkplain CompilationAlarm.Options#CompilationExpirationPeriod}.
     *
     * @return {@code true} if the current compilation already takes longer than
     *         {@linkplain CompilationAlarm.Options#CompilationExpirationPeriod}, {@code false}
     *         otherwise
     */
    public boolean hasExpired() {
        return this != NEVER_EXPIRES && System.currentTimeMillis() > expiration;
    }

    @Override
    public void close() {
        if (this != NEVER_EXPIRES) {
            currentAlarm.set(null);
            resetProgressDetection();
        }
    }

    /**
     * The time at which this alarm expires.
     */
    private final long expiration;

    /**
     * Starts an alarm for setting a time limit on a compilation if there isn't already an active
     * alarm and {@link CompilationAlarm.Options#CompilationExpirationPeriod}{@code > 0}. The
     * returned value can be used in a try-with-resource statement to disable the alarm once the
     * compilation is finished.
     *
     * @return a {@link CompilationAlarm} if there was no current alarm for the calling thread
     *         before this call otherwise {@code null}
     */
    public static CompilationAlarm trackCompilationPeriod(OptionValues options) {
        double period = Options.CompilationExpirationPeriod.getValue(options);
        if (period > 0) {
            if (Assertions.assertionsEnabled()) {
                period *= 2;
            }
            if (Assertions.detailedAssertionsEnabled(options)) {
                period *= 2;
            }
            CompilationAlarm current = currentAlarm.get();
            if (current == null) {
                long expiration = System.currentTimeMillis() + (long) (period * 1000);
                current = new CompilationAlarm(expiration);
                currentAlarm.set(current);
                return current;
            }
        }
        return null;
    }

    /**
     * Number of graph events (iterating inputs, usages, etc) before triggering a check on the
     * compilation alarm.
     */
    public static final int CHECK_BAILOUT_COUNTER = 1024 * 4;

    public static void checkProgress(Graph graph) {
        if (graph == null) {
            return;
        }
        if (graph.eventCounterOverflows(CHECK_BAILOUT_COUNTER)) {
            overflowAction(graph.getOptions(), graph);
        }
    }

    public static boolean checkProgress(OptionValues opt, EventCounter eventCounter) {
        if (opt == null) {
            return false;
        }
        if (eventCounter.eventCounterOverflows(CHECK_BAILOUT_COUNTER)) {
            overflowAction(opt, eventCounter);
            return true;
        }
        return false;
    }

    private static void overflowAction(OptionValues opt, EventCounter counter) {
        if (LOG_PROGRESS_DETECTION) {
            TTY.printf("CompilationAlarm: Progress detection %s; event counter overflown %n", counter.eventCounterToString());
        }
        if (CompilationAlarm.current().hasExpired()) {
            compilationAlarmExpired(opt);
        } else {
            assertProgress(opt, counter);
        }
    }

    /**
     * Assert that there is progress made since the last time the event counter overflow - if there
     * is no progress made within a certain time frame - abort compilation under the assumption of a
     * hang and throw a bailout.
     */
    private static void assertProgress(OptionValues opt, EventCounter counter) {
        final EventCounter lastCounter = lastCounterForThread.get();
        if (lastCounter != null && !lastCounter.equals(counter)) {
            resetProgressDetection();
            return;
        }

        final StackTraceElement[] lastStackTrace = lastStackTraceForThread.get();
        if (lastStackTrace == null) {
            Long lastUniqueStackTraceTimeStamp = lastUniqueStackTraceForThread.get();
            if (lastUniqueStackTraceTimeStamp == null) {
                assertProgressNoTracking(opt, counter);
                return;
            } else {
                final double noProgressStartDetectionPeriod = noProgressStartPeriod.get() * 1000;
                final long currentTimeStamp = System.currentTimeMillis();
                final long timeDiff = currentTimeStamp - lastUniqueStackTraceTimeStamp;
                final boolean noProgressForPeriodStartDetection = timeDiff > noProgressStartDetectionPeriod;
                if (!noProgressForPeriodStartDetection) {
                    /*
                     * Still not enough no-progress before we start doing something.
                     */
                    if (LOG_PROGRESS_DETECTION) {
                        TTY.printf("CompilationAlarm: Progress detection %s; time diff %s not long enough to take stack trace yet %n", counter.eventCounterToString(), timeDiff);
                    }
                    return;
                } else {
                    if (LOG_PROGRESS_DETECTION) {
                        TTY.printf("CompilationAlarm: Progress detection %s; time diff %s long enough to take stack trace %n", counter.eventCounterToString(), timeDiff);
                    }
                }
            }
        }

        StackTraceElement[] currentStackTrace = Thread.currentThread().getStackTrace();
        if (lastStackTrace == null || lastStackTrace.length != currentStackTrace.length || !Arrays.equals(lastStackTrace, currentStackTrace)) {
            lastStackTraceForThread.set(currentStackTrace);
            lastUniqueStackTraceForThread.set(System.currentTimeMillis());
            lastCounterForThread.set(counter);
        } else {
            assertProgressSlowPath(opt, lastStackTrace, lastCounter, currentStackTrace);
        }
    }

    private static void assertProgressNoTracking(OptionValues opt, EventCounter counter) {
        /*
         * First time we assert the progress - do not start collecting the stack traces in the first
         * n seconds
         */
        lastUniqueStackTraceForThread.set(System.currentTimeMillis());
        lastCounterForThread.set(counter);
        noProgressStartPeriod.set(Options.CompilationNoProgressStartTrackingProgressPeriod.getValue(opt));

        if (LOG_PROGRESS_DETECTION) {
            TTY.printf("CompilationAlarm: Progress detection %s; taking first time stamp, no stack yet%n", counter.eventCounterToString());
        }
    }

    private static void assertProgressSlowPath(OptionValues opt, StackTraceElement[] lastStackTrace, EventCounter lastCounter, StackTraceElement[] currentStackTrace) {
        /*
         * We perform this check inside here since its cheaper to take the last stack trace (last
         * will be null for the first in the current compile) than actually checking the option
         * below. In normal compiles we dont often get into this branch in the first place, most is
         * captured above, and the thread local is very fast.
         */
        final double maxNoProgressPeriod = (Options.CompilationNoProgressPeriod.getValue(opt) * 1000);
        if (maxNoProgressPeriod == 0D) {
            // Feature is disabled, do nothing.
            return;
        }

        assert Arrays.equals(lastStackTrace, currentStackTrace) : "Must only enter this branch if no progress was made";
        /*
         * We have a similar stack trace - fail once the period is longer than the no progress
         * period.
         */
        final long lastUniqueStackTraceTimeStamp = lastUniqueStackTraceForThread.get();
        final long currentTimeStamp = System.currentTimeMillis();
        final long timeDiff = currentTimeStamp - lastUniqueStackTraceTimeStamp;
        boolean noProgressForPeriod = timeDiff > maxNoProgressPeriod;

        if (LOG_PROGRESS_DETECTION) {
            if (timeDiff / 1000 > 0) {
                TTY.printf("CompilationAlarm: Progress detection %s; no progress for %s seconds - progress %s - max no progress period %s %n", lastCounter, timeDiff / 1000, noProgressForPeriod,
                                maxNoProgressPeriod);
            }
        }

        if (noProgressForPeriod) {
            throw new PermanentBailoutException("Observed identical stack traces for %d seconds, indicating a stuck compilation, counter = %s, stack is %n%s", timeDiff / 1000, lastCounter,
                            Util.toString(lastStackTrace));
        }
    }

    public static void resetProgressDetection() {
        lastStackTraceForThread.set(null);
        lastUniqueStackTraceForThread.set(null);
        lastCounterForThread.set(null);
        noProgressStartPeriod.set(null);
    }

    public static void compilationAlarmExpired(Graph graph) {
        compilationAlarmExpired(graph.getOptions());
    }

    public static void compilationAlarmExpired(OptionValues options) {
        double period = CompilationAlarm.Options.CompilationExpirationPeriod.getValue(options);
        throw new PermanentBailoutException("Compilation exceeded %f seconds during CFG traversal", period);
    }

    private static final ThreadLocal<StackTraceElement[]> lastStackTraceForThread = new ThreadLocal<>();
    private static final ThreadLocal<Long> lastUniqueStackTraceForThread = new ThreadLocal<>();
    private static final ThreadLocal<EventCounter> lastCounterForThread = new ThreadLocal<>();
    private static final ThreadLocal<Double> noProgressStartPeriod = new ThreadLocal<>();
}
