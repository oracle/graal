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

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.util.EventCounter.EventCounterMarker;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.services.Services;

/**
 * Utility class that allows the compiler to monitor compilations that take a very long time.
 */
public final class CompilationAlarm implements AutoCloseable {

    public static class Options {
        // @formatter:off
        @Option(help = "Time limit in seconds before a compilation expires (0 to disable the limit). " +
                       "A non-zero value for this option is doubled if assertions are enabled and quadrupled if DetailedAsserts is true. " +
                       "Must be 0 or >= 0.001.",
                type = OptionType.Debug)
        public static final OptionKey<Double> CompilationExpirationPeriod = new OptionKey<>(300d);
        @Option(help = "Time limit in seconds before a compilation expires (0 to disable the limit) because no progress was " +
                       "made in the compiler. Must be 0 or >= 0.001.",
                type = OptionType.Debug)
        public static final OptionKey<Double> CompilationNoProgressPeriod = new OptionKey<>(30d);
        @Option(help = "Delay in seconds before compilation progress detection starts. Must be 0 or >= 0.001.",
                type = OptionType.Debug)
        public static final OptionKey<Double> CompilationNoProgressStartTrackingProgressPeriod = new OptionKey<>(10d);
        // @formatter:on
    }

    public static final boolean LOG_PROGRESS_DETECTION = !Services.IS_IN_NATIVE_IMAGE &&
                    Boolean.parseBoolean(Services.getSavedProperty("debug." + CompilationAlarm.class.getName() + ".logProgressDetection"));

    private CompilationAlarm(double period) {
        this.period = period;
        this.expiration = period == 0.0D ? 0L : System.currentTimeMillis() + (long) (period * 1000);
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
     * Gets the period after which this compilation alarm expires.
     */
    public double getPeriod() {
        return period;
    }

    /**
     * Determines if this alarm has expired.
     *
     * @return {@code true} if this alarm has been active for more than {@linkplain #getPeriod()
     *         period} seconds, {@code false} otherwise
     */
    public boolean hasExpired() {
        return this != NEVER_EXPIRES && System.currentTimeMillis() > expiration;
    }

    /**
     * Checks whether this alarm {@link #hasExpired()} and if so, raises a bailout exception.
     */
    public void checkExpiration() {
        if (hasExpired()) {
            throw new PermanentBailoutException("Compilation exceeded %.3f seconds", period);
        }
    }

    @Override
    public void close() {
        if (this != NEVER_EXPIRES) {
            currentAlarm.set(null);
            resetProgressDetection();
        }
    }

    /**
     * Expiration period (in seconds) of this alarm.
     */
    private final double period;

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
                current = new CompilationAlarm(period);
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
            TTY.printf("CompilationAlarm: Progress detection %s; event counter overflowed%n", counter.eventCounterToString());
        }
        CompilationAlarm current = CompilationAlarm.current();
        current.checkExpiration();
        assertProgress(opt, counter);
    }

    /**
     * Assert that there is progress made since the last time the event counter overflow - if there
     * is no progress made within a certain time frame - abort compilation under the assumption of a
     * hang and throw a bailout.
     */
    private static void assertProgress(OptionValues opt, EventCounter counter) {
        final EventCounterMarker lastMarker = lastMarkerForThread.get();
        if (lastMarker != null && lastMarker != counter.getEventCounterMarker()) {
            resetProgressDetection();
            return;
        }

        final StackTraceElement[] lastStackTrace = lastStackTraceForThread.get();
        if (lastStackTrace == null) {
            Long lastUniqueStackTraceTimeStamp = lastUniqueStackTraceForThreadMS.get();
            if (lastUniqueStackTraceTimeStamp == null) {
                assertProgressNoTracking(opt, counter);
                return;
            } else {
                final long delay = noProgressStartPeriodMS.get();
                final long now = System.currentTimeMillis();
                final long elapsed = now - lastUniqueStackTraceTimeStamp;
                if (elapsed <= delay) {
                    /*
                     * Still not enough lack of progress before we start doing something.
                     */
                    if (LOG_PROGRESS_DETECTION) {
                        TTY.printf("CompilationAlarm: Progress detection %s; time diff of %d ms not long enough to take stack trace yet%n", counter.eventCounterToString(), elapsed);
                    }
                    return;
                } else {
                    if (LOG_PROGRESS_DETECTION) {
                        TTY.printf("CompilationAlarm: Progress detection %s; time diff of %d ms long enough to take stack trace%n", counter.eventCounterToString(), elapsed);
                    }
                }
            }
        }

        StackTraceElement[] currentStackTrace = Thread.currentThread().getStackTrace();
        if (lastStackTrace == null || lastStackTrace.length != currentStackTrace.length || !Arrays.equals(lastStackTrace, currentStackTrace)) {
            lastStackTraceForThread.set(currentStackTrace);
            lastUniqueStackTraceForThreadMS.set(System.currentTimeMillis());
            lastMarkerForThread.set(counter.getEventCounterMarker());
        } else {
            assertProgressSlowPath(opt, lastStackTrace, counter, currentStackTrace);
        }
    }

    private static void assertProgressNoTracking(OptionValues opt, EventCounter counter) {
        /*
         * First time we assert the progress - do not start collecting the stack traces in the first
         * n seconds
         */
        lastUniqueStackTraceForThreadMS.set(System.currentTimeMillis());
        lastMarkerForThread.set(counter.getEventCounterMarker());
        noProgressStartPeriodMS.set((long) (Options.CompilationNoProgressStartTrackingProgressPeriod.getValue(opt) * 1000));

        if (LOG_PROGRESS_DETECTION) {
            TTY.printf("CompilationAlarm: Progress detection %s; taking first time stamp, no stack yet%n", counter.eventCounterToString());
        }
    }

    private static void assertProgressSlowPath(OptionValues opt, StackTraceElement[] lastStackTrace, EventCounter counter, StackTraceElement[] currentStackTrace) {
        /*
         * We perform this check inside here since its cheaper to take the last stack trace (last
         * will be null for the first in the current compile) than actually checking the option
         * below. In normal compiles we don't often get into this branch in the first place, most is
         * captured above, and the thread local is very fast.
         */
        final long stuckThreshold = (long) (Options.CompilationNoProgressPeriod.getValue(opt) * 1000);
        if (stuckThreshold == 0) {
            // Feature is disabled, do nothing.
            return;
        }

        assert Arrays.equals(lastStackTrace, currentStackTrace) : "Must only enter this branch if no progress was made";
        /*
         * We have a similar stack trace - fail once the period is longer than the no progress
         * period.
         */
        final long lastUniqueStackTraceTime = lastUniqueStackTraceForThreadMS.get();
        final long now = System.currentTimeMillis();
        final long elapsed = now - lastUniqueStackTraceTime;
        boolean stuck = elapsed > stuckThreshold;

        if (LOG_PROGRESS_DETECTION) {
            TTY.printf("CompilationAlarm: Progress detection %s; no progress for %d ms; stuck? %s; stuck threshold %d ms%n",
                            counter, elapsed, stuck, stuckThreshold);
        }

        if (stuck) {
            throw new PermanentBailoutException("Observed identical stack traces for %d ms, indicating a stuck compilation, counter = %s, stack is:%n%s",
                            elapsed, counter, Util.toString(lastStackTrace));
        }
    }

    public static void resetProgressDetection() {
        lastStackTraceForThread.set(null);
        lastUniqueStackTraceForThreadMS.set(null);
        lastMarkerForThread.set(null);
        noProgressStartPeriodMS.set(null);
    }

    private static final ThreadLocal<StackTraceElement[]> lastStackTraceForThread = new ThreadLocal<>();
    private static final ThreadLocal<Long> lastUniqueStackTraceForThreadMS = new ThreadLocal<>();
    /**
     * Note that all these thread locals are not necessarily reset for a while even if worker
     * threads have moved on to do actual work (but just not compiling graphs). Especially in the
     * native image generator, not all {@link #assertProgress} calls can be in a closeable scope
     * that invokes {@link #resetProgressDetection}. It is therefore critical that they do not keep
     * large data structures like actual Graal graphs or LIR alive.
     */
    private static final ThreadLocal<EventCounterMarker> lastMarkerForThread = new ThreadLocal<>();
    private static final ThreadLocal<Long> noProgressStartPeriodMS = new ThreadLocal<>();
}
