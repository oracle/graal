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

import static com.oracle.graal.hotspot.HotSpotGraalCompilerFactory.GRAAL_OPTION_PROPERTY_PREFIX;

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.graal.debug.TTY;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

/**
 * A watch dog that monitors the compilation rate during a
 * {@linkplain HotSpotGraalRuntimeProvider#isBootstrapping() bootstrap}. If it falls below a given
 * ratio of the maximum observed compilation rate, the compiler will ignore all subsequent
 * compilation requests until bootstrapping is completed.
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
        public static final OptionValue<Double> BootstrapWatchDogCriticalRateRatio = new OptionValue<>(0.25D);
        // @formatter:on
    }

    private final AtomicInteger compilations = new AtomicInteger();
    private boolean hitCriticalRate;
    private double maxRate;

    public static BootstrapWatchDog maybeCreate() {
        return MAX_RATE_DECREASE <= 0.0D ? null : new BootstrapWatchDog();
    }

    private BootstrapWatchDog() {
        this.setName(getClass().getSimpleName());
        this.start();
    }

    /**
     * Set to true to debug the watch dog.
     */
    static final boolean DEBUG = false;

    /**
     * Seconds to delay before starting to measure the compilation rate.
     */
    static final int INITIAL_DELAY = 10;

    /**
     * Seconds between each compilation rate measurement.
     */
    static final long EPOCH = 5;

    /**
     * The watch dog {@link #hitCriticalCompilationRate() hits} a critical compilation rate if the
     * current compilation rate falls below this ratio of the maximum compilation rate.
     */
    static final double MAX_RATE_DECREASE = Options.BootstrapWatchDogCriticalRateRatio.getValue();

    @Override
    public void run() {
        if (DEBUG) {
            TTY.printf("%nStarted %s%n", this);
        }
        long start = System.currentTimeMillis();
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
                } else if (rate < (maxRate * MAX_RATE_DECREASE)) {
                    TTY.printf("%nAfter %.2f seconds bootstrapping, compilation rate is %.2f compilations per second " +
                                    "which is below %.2f times the max compilation rate of %.2f%n", seconds(elapsed), rate, MAX_RATE_DECREASE, maxRate);
                    TTY.printf("To enable monitoring of long running individual compilations, re-run with -D%s%s=%.2f%n",
                                    GRAAL_OPTION_PROPERTY_PREFIX, CompilationWatchDog.Options.CompilationWatchDogStartDelay.getName(),
                                    seconds(elapsed) - 5);
                    hitCriticalRate = true;
                    return;
                }
                Thread.sleep(EPOCH * 1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace(TTY.out);
        }
    }

    static double seconds(long ms) {
        return (double) ms / 1000;
    }

    /**
     * Queries whether this monitor has observed a critically low compilation rate.
     */
    boolean hitCriticalCompilationRate() {
        return hitCriticalRate;
    }

    /**
     * Notifies this object that a compilation finished.
     */
    public void notifyCompilationFinished() {
        compilations.incrementAndGet();
    }
}
