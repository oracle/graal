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
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.ImageInfo;

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

    public static final boolean LOG_PROGRESS_DETECTION = !ImageInfo.inImageRuntimeCode() &&
                    Boolean.parseBoolean(GraalServices.getSavedProperty("debug." + CompilationAlarm.class.getName() + ".logProgressDetection"));

    /**
     * The previously installed alarm.
     */
    private final CompilationAlarm previous;

    @SuppressWarnings("this-escape")
    private CompilationAlarm(double period) {
        this.previous = currentAlarm.get();
        reset(period);
    }

    /**
     * Use the option value defined compilation expiration period and reset this alarm. See
     * {@link #reset(double)}.
     */
    public void reset(OptionValues options) {
        double optionPeriod = Options.CompilationExpirationPeriod.getValue(options);
        if (optionPeriod > 0) {
            reset(scaleExpirationPeriod(optionPeriod, options));
        }
    }

    /**
     * Scale the compilation alarm expiration period to account for different system properties. The
     * period has a default value or can be set by users. Global context flags like assertions can
     * slow down compilation significantly and we want to avoid false positives of the alarm.
     */
    public static double scaleExpirationPeriod(double period, OptionValues options) {
        double p = period;
        if (Assertions.assertionsEnabled()) {
            p *= 2;
        }
        if (Assertions.detailedAssertionsEnabled(options)) {
            p *= 2;
        }
        return p;
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
        /*
         * We have multiple ways to mark a disabled alarm: it can be NEVER_EXPIRES which is shared
         * among all threads and thus must not be altered, or it can be an Alarm object with an
         * expiration=0. We cannot share disabled timers across threads because we record the
         * previous timer to continue afterwards with the actual timer.
         */
        return this != NEVER_EXPIRES && expiration != 0 && System.currentTimeMillis() > expiration;
    }

    public long elapsed() {
        return this == NEVER_EXPIRES ? -1 : System.currentTimeMillis() - start();
    }

    private long start() {
        return this == NEVER_EXPIRES ? -1 : expiration - (long) (period * 1000);
    }

    /**
     * Resets the compilation alarm with a new period for expiration. See
     * {@link #trackCompilationPeriod} for details about tracking compilation periods.
     */
    public void reset(double newPeriod) {
        if (this != NEVER_EXPIRES) {
            this.period = newPeriod;
            this.expiration = newPeriod == 0.0D ? 0L : System.currentTimeMillis() + (long) (newPeriod * 1000);
        }
    }

    public boolean isEnabled() {
        return this != NEVER_EXPIRES;
    }

    /**
     * Checks whether this alarm {@link #hasExpired()} and if so, raises a bailout exception.
     */
    public void checkExpiration() {
        if (hasExpired()) {

            setCurrentNodeDuration(currentNode.name);

            /*
             * We clone the phase tree here for the sake of the error message. We want to fix up the
             * root timings and also annotate in which phase(s) the timeout happens. We do not do
             * this on the original tree because that one can still be in IGV dumps.
             */
            PhaseTreeNode cloneTree = cloneTree(root, null);
            StringBuilder sb = new StringBuilder();
            // also update the root time to be consistent for the error message
            cloneTree.durationMS = elapsed();
            printTree("", sb, cloneTree, true);

            throw new PermanentBailoutException("Compilation exceeded %.3f seconds. %n Phase timings:%n %s <===== TIMEOUT HERE", period, sb.toString().trim());
        }
    }

    @Override
    public void close() {
        currentAlarm.set(previous);
        resetProgressDetection();
    }

    /**
     * Expiration period (in seconds) of this alarm.
     */
    private double period;

    /**
     * The time at which this alarm expires.
     */
    private long expiration;

    /**
     * Signal the execution of the phase identified by {@code name} starts.
     */
    public void enterPhase(String name) {
        if (!isEnabled()) {
            return;
        }
        PhaseTreeNode node = new PhaseTreeNode(name);
        node.parent = currentNode;
        node.startTimeSystemMillis = System.currentTimeMillis();
        currentNode.addChild(node);
        currentNode = node;
    }

    /**
     * Signal the execution of the phase identified by {@code name} is over.
     */
    public void exitPhase(String name) {
        if (!isEnabled()) {
            return;
        }
        assert currentNode.name.equals(name) : Assertions.errorMessage("Must see the same phase that was opened in the close operation", name, elapsedPhaseTreeAsString());
        setCurrentNodeDuration(name);
        currentNode.closed = true;
        currentNode.parent.durationMS += currentNode.durationMS;
        currentNode = currentNode.parent;
    }

    private void setCurrentNodeDuration(String name) {
        assert currentNode.startTimeSystemMillis >= 0 : Assertions.errorMessage("Must have a positive start time", name, elapsedPhaseTreeAsString());
        currentNode.durationMS = System.currentTimeMillis() - currentNode.startTimeSystemMillis;
    }

    /**
     * The phase tree root node during compilation. Special marker node to avoid null checking
     * logic.
     */
    private PhaseTreeNode root = new PhaseTreeNode("Root");

    /**
     * The current tree node to add children to. That is, the phase that currently runs in the
     * compiler.
     */
    private PhaseTreeNode currentNode = root;

    /**
     * Tree data structure representing phase nesting and the respective wall clock time of each
     * phase.
     */
    private class PhaseTreeNode {

        /**
         * Link to the parent node.
         */
        private PhaseTreeNode parent;

        /**
         * All children of this node.
         */
        private PhaseTreeNode[] children;

        /**
         * The next free index to add child nodes.
         */
        private int childIndex = 0;

        /**
         * The name of this node, normally the {@link BasePhase#contractorName()}.
         */
        private final String name;

        /**
         * The time stamp in ms when this phase started running.
         */
        private long startTimeSystemMillis = -1L;

        /**
         * The wall clock time spent in milliseconds in this phase.
         */
        private long durationMS = 0;

        /**
         * Determines if this phase was already properly closed.
         */
        public boolean closed;

        PhaseTreeNode(String name) {
            this.name = name;
        }

        private void addChild(PhaseTreeNode child) {
            if (children == null) {
                children = new PhaseTreeNode[CHILD_TREE_INIT_SIZE];
                children[childIndex++] = child;
                return;
            }
            // double the array if it needs expanding
            if (childIndex >= children.length) {
                children = Arrays.copyOf(children, children.length * 2);
            }
            children[childIndex++] = child;
        }

        @Override
        public String toString() {
            return name + "->" + durationMS + "ms elapsed [startMS=" + startTimeSystemMillis + "]";
        }

    }

    private PhaseTreeNode cloneTree(PhaseTreeNode clonee, PhaseTreeNode parent) {
        PhaseTreeNode clone = new PhaseTreeNode(clonee.name);
        clone.parent = parent;
        if (clone.parent != null) {
            clone.parent.addChild(clone);
        }
        clone.durationMS = clonee.durationMS;
        clone.startTimeSystemMillis = clonee.startTimeSystemMillis;
        clone.closed = clonee.closed;
        if (clonee.children != null) {
            for (int i = 0; i < clonee.childIndex; i++) {
                cloneTree(clonee.children[i], clone);
            }
        }
        return clone;
    }

    /**
     * Initial size of a {@link PhaseTreeNode} children array.
     */
    private static final int CHILD_TREE_INIT_SIZE = 2;

    /**
     * Recursively print the phase tree represented by {@code node}.
     */
    private void printTree(String indent, StringBuilder sb, PhaseTreeNode node, boolean printRoot) {
        sb.append(indent);
        if (!printRoot && node == root) {
            sb.append(node.name);
        } else {
            sb.append(node);
        }
        sb.append(System.lineSeparator());
        if (node.children != null) {
            for (int i = 0; i < node.childIndex; i++) {
                printTree(indent + "\t", sb, node.children[i], printRoot);
            }
        }
    }

    public StringBuilder elapsedPhaseTreeAsString() {
        StringBuilder sb = new StringBuilder();
        printTree("", sb, root, false);
        return sb;
    }

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
     * Disable the compilation alarm. The returned value should be used in a try-with-resource
     * statement to restore the previous alarm state.
     */
    public static CompilationAlarm disable() {
        CompilationAlarm current = new CompilationAlarm(0);
        currentAlarm.set(current);
        return current;
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
