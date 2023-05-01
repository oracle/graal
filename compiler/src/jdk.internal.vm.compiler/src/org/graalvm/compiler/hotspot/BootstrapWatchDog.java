/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.hotspot.HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.core.CompilationWatchDog;
import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.services.Services;

/**
 * A watch dog that monitors the duration and compilation rate during a
 * {@linkplain HotSpotGraalRuntimeProvider#isBootstrapping() bootstrap}. If time spent bootstrapping
 * exceeds a specified timeout or the compilation rate falls below a given ratio of the maximum
 * observed compilation rate (i.e., compilation slows down too much), the compiler will ignore all
 * subsequent compilation requests, effectively draining the bootstrap completion queue and
 * expediting completion of bootstrap. Note that the compilation rate is computed over the whole
 * execution, not just the most recent measurement period. This means a sudden but temporary drop in
 * any given measurement period won't cause bootstrapping to terminate early.
 *
 * This mechanism is based on past observations that a significantly falling bootstrap compilation
 * rate implies a configuration where bootstrapping will take an unreasonably long time and it's
 * better to drain the bootstrap compilation queue at some point that risk triggering timeouts in
 * external harnesses such as integration tests.
 */
final class BootstrapWatchDog extends Thread {

    public static class Options {
        // @formatter:off
        @Option(help = "Ratio of the maximum compilation rate below which the bootstrap compilation rate must not fall " +
                       "(0 or less disables monitoring).", type = OptionType.Debug)
        public static final OptionKey<Double> BootstrapWatchDogCriticalRateRatio = new OptionKey<>(0.25D);
        @Option(help = "Maximum time in minutes to spend bootstrapping (0 to disable this limit).", type = OptionType.Debug)
        public static final OptionKey<Double> BootstrapTimeout = new OptionKey<>(15D);
        // @formatter:on
    }

    /**
     * Count of completed compilations. This is updated by the compiler threads and read by the
     * watch dog thread.
     */
    private final AtomicInteger compilations = new AtomicInteger();

    /**
     * Set to true once the compilation rate drops too low or bootstrapping times out.
     */
    private boolean hitCriticalRateOrTimeout;

    /**
     * The maximum compilation rate seen during execution.
     */
    private double maxRate;

    private final HotSpotGraalRuntimeProvider graalRuntime;

    /**
     * Creates and returns a {@link BootstrapWatchDog} if
     * {@link Options#BootstrapWatchDogCriticalRateRatio} is not set to 0 otherwise returns
     * {@code null}.
     */
    static BootstrapWatchDog maybeCreate(HotSpotGraalRuntimeProvider graalRuntime) {
        OptionValues options = graalRuntime.getOptions();
        int timeout = (int) (Options.BootstrapTimeout.getValue(options) * 60);
        double maxRateDecrease = Options.BootstrapWatchDogCriticalRateRatio.getValue(options);
        return maxRateDecrease <= 0.0D && timeout == 0 ? null : new BootstrapWatchDog(graalRuntime, timeout, maxRateDecrease);
    }

    private BootstrapWatchDog(HotSpotGraalRuntimeProvider graalRuntime, int timeout, double maxRateDecrease) {
        this.setName(getClass().getSimpleName());
        this.start();
        this.graalRuntime = graalRuntime;
        this.timeout = timeout;
        this.maxRateDecrease = maxRateDecrease;
    }

    /**
     * Set to true to debug the watch dog.
     */
    private static final boolean DEBUG = Boolean.parseBoolean(Services.getSavedProperties().get("debug.graal.BootstrapWatchDog"));

    /**
     * Seconds to delay before starting to measure the compilation rate.
     */
    private static final int INITIAL_DELAY = 10;

    /**
     * Seconds between each compilation rate measurement.
     */
    private static final long EPOCH = 5;

    /**
     * Time in seconds before stopping a bootstrap.
     */
    private final int timeout;

    /**
     * The watch dog {@link #hitCriticalCompilationRateOrTimeout() hits} a critical compilation rate
     * if the current compilation rate falls below this ratio of the maximum compilation rate.
     */
    private final double maxRateDecrease;

    @Override
    public void run() {
        if (DEBUG) {
            TTY.printf("%nStarted %s%n", this);
        }
        long start = System.currentTimeMillis();
        Map<Thread, Watch> requestsAtTimeout = null;
        Map<Thread, StackTraceElement[]> stacksAtTimeout = null;
        try {
            Thread.sleep(INITIAL_DELAY * 1000);
            while (true) {
                int currentCompilations = compilations.get();
                long elapsed = System.currentTimeMillis() - start;
                double rate = currentCompilations / seconds(elapsed);
                if (DEBUG) {
                    TTY.printf("%.2f: compilation rate is %.2f/sec%n", seconds(elapsed), rate);
                }
                if (rate > maxRate) {
                    maxRate = rate;
                } else if (rate < (maxRate * maxRateDecrease)) {
                    TTY.printf("%nAfter %.2f seconds bootstrapping, compilation rate is %.2f compilations per second " +
                                    "which is below %.2f times the max compilation rate of %.2f%n", seconds(elapsed), rate, maxRateDecrease, maxRate);
                    TTY.printf("To enable monitoring of long running individual compilations, re-run with -D%s%s=%.2f%n",
                                    GRAAL_OPTION_PROPERTY_PREFIX, CompilationWatchDog.Options.CompilationWatchDogStartDelay.getName(),
                                    seconds(elapsed) - 5);
                    hitCriticalRateOrTimeout = true;
                    return;
                }
                if (elapsed > timeout * 1000) {
                    if (requestsAtTimeout == null) {
                        requestsAtTimeout = snapshotRequests();
                        stacksAtTimeout = new HashMap<>();
                        for (Thread t : requestsAtTimeout.keySet()) {
                            stacksAtTimeout.put(t, t.getStackTrace());
                        }
                    } else {
                        TTY.printf("%nHit bootstrapping timeout after %.2f seconds%n", seconds(elapsed));
                        Map<Thread, Watch> requestsNow = snapshotRequests();
                        for (Map.Entry<Thread, Watch> e : requestsAtTimeout.entrySet()) {
                            Thread t = e.getKey();
                            CompilationRequest request1 = requestsAtTimeout.get(t).request;
                            CompilationRequest request2 = requestsNow.get(t).request;
                            if (request1 != null && request1 == request2) {
                                StackTraceElement[] stackTraceNow = t.getStackTrace();
                                TTY.printf("Printing stack trace for current compilation of %s lasting more than %d seconds:%n%s",
                                                request1.getMethod().format("%H.%n(%p)"), EPOCH, Util.toString(stackTraceNow));

                                // Fortify: Null Dereference false positive
                                assert stacksAtTimeout != null;

                                if (Arrays.equals(stacksAtTimeout.get(t), stackTraceNow)) {
                                    TTY.printf("\t** Identical stack trace %d seconds ago, implying a hung compilation **%n",
                                                    EPOCH);
                                }
                            } else {
                                if (DEBUG) {
                                    TTY.printf("%s was compiling %s%n", t, request1.getMethod().format("%H.%n(%p)"));
                                }
                            }
                        }
                        hitCriticalRateOrTimeout = true;
                        return;
                    }
                }
                if (!graalRuntime.isBootstrapping()) {
                    return;
                }

                Thread.sleep(EPOCH * 1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace(TTY.out);
        }
    }

    private Map<Thread, Watch> snapshotRequests() {
        synchronized (requests) {
            return new HashMap<>(requests);
        }
    }

    private static double seconds(long ms) {
        return (double) ms / 1000;
    }

    /**
     * Queries whether a critically low compilation rate or {@link #timeout} occurred.
     */
    boolean hitCriticalCompilationRateOrTimeout() {
        return hitCriticalRateOrTimeout;
    }

    private final Map<Thread, Watch> requests = new HashMap<>();
    private final ThreadLocal<Watch> requestForThread = new ThreadLocal<>();

    /**
     * Opens a scope for watching the compilation of a given method.
     *
     * @param request a compilation request about to be processed
     * @return {@code null} if the compilation watch dog is disabled otherwise this object. The
     *         returned value should be used in a {@code try}-with-resources statement whose scope
     *         is the whole compilation so that leaving the scope will cause {@link Watch#close()}
     *         to be called.
     */
    Watch watch(CompilationRequest request) {
        Watch watch = requestForThread.get();
        if (watch == null) {
            watch = new Watch();
            synchronized (requests) {
                requests.put(Thread.currentThread(), watch);
            }
        }
        watch.open(request);
        return watch;
    }

    /**
     * Object for watching the compilations requests of a single compiler thread.
     */
    class Watch implements AutoCloseable {
        CompilationRequest request;

        void open(CompilationRequest r) {
            assert this.request == null;
            this.request = r;
        }

        @Override
        public void close() {
            compilations.incrementAndGet();
            request = null;
        }
    }
}
