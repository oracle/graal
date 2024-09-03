/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import org.junit.Ignore;
import org.junit.Test;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.throttling.JfrEventThrottler;
import com.oracle.svm.core.jfr.throttling.JfrEventThrottler.TestingBackdoor;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.jfr.Recording;

public class TestThrottler extends JfrRecordingTest {
    private static final long WINDOWS_PER_PERIOD = TestingBackdoor.getWindowsPerPeriod();
    private static final long WINDOW_DURATION_MS = TimeUtils.secondsToMillis(3600);

    /**
     * This is the simplest test that ensures that sampling stops after the cap is hit. All sampling
     * is done within the first window.
     */
    @Test
    public void testCapSingleThread() {
        long samplesPerWindow = 10;
        JfrEventThrottler throttler = newEventThrottler(samplesPerWindow);

        for (int i = 0; i < 3 * samplesPerWindow; i++) {
            boolean sample = TestingBackdoor.sample(throttler);
            assertEquals(i < samplesPerWindow, sample);
        }
    }

    /**
     * This test ensures that sampling stops after the cap is hit, even when multiple threads are
     * doing sampling. It also checks that sampling can continue after rotating the window.
     */
    @Test
    public void testCapConcurrent() throws InterruptedException {
        long samplesPerWindow = 100000;
        JfrEventThrottler throttler = newEventThrottler(samplesPerWindow);

        for (int i = 0; i < 3; i++) {
            /* Start a couple threads and sample concurrently. */
            int numThreads = 8;
            Thread[] threads = new Thread[numThreads];
            AtomicInteger countedSamples = new AtomicInteger(0);

            for (int j = 0; j < numThreads; j++) {
                Thread worker = new Thread(() -> {
                    for (int k = 0; k < samplesPerWindow; k++) {
                        boolean sample = TestingBackdoor.sample(throttler);
                        if (sample) {
                            countedSamples.incrementAndGet();
                        }
                    }
                });
                worker.start();
                threads[j] = worker;
            }

            /* Wait until the threads finish. */
            for (Thread thread : threads) {
                thread.join();
            }

            assertTrue("Sampling failed!", countedSamples.get() > 0);
            assertTrue("Too many samples taken!", countedSamples.get() <= samplesPerWindow);

            /* Repeat the test a few times. */
            countedSamples.set(0);
            expireAndRotate(throttler);
        }
    }

    /** Tests normalization of sample size and period. */
    @Test
    public void testNormalization() {
        long sampleSize = 10 * 600;
        long periodMs = TimeUtils.secondsToMillis(60);
        JfrEventThrottler throttler = new JfrEventThrottler();
        throttler.configure(sampleSize, periodMs);
        assertTrue(TestingBackdoor.getPeriodMs(throttler) == TimeUtils.millisPerSecond && TestingBackdoor.getSampleSize(throttler) == sampleSize / 60);

        sampleSize = 10 * 3600;
        periodMs = TimeUtils.secondsToMillis(3600);
        throttler.configure(sampleSize, periodMs);
        assertTrue(TestingBackdoor.getPeriodMs(throttler) == TimeUtils.millisPerSecond && TestingBackdoor.getSampleSize(throttler) == sampleSize / 3600);
    }

    @Test
    public void testZeroRateSampling() {
        JfrEventThrottler throttler = new JfrEventThrottler();
        throttler.configure(0, TimeUtils.secondsToMillis(2));
        assertFalse(TestingBackdoor.sample(throttler));

        /* Reconfigure the throttler. */
        throttler.configure(10, TimeUtils.secondsToMillis(2));
        assertTrue(TestingBackdoor.sample(throttler));
    }

    /** Checks that no ObjectAllocationSample events are emitted when the sampling rate is 0. */
    @Test
    public void testZeroRateRecording() throws IOException {
        /* Test applying throttling settings to an actual recording. */
        Recording recording = new Recording();
        recording.setDestination(createTempJfrFile());
        recording.enable(JfrEvent.ObjectAllocationSample.getName()).with("throttle", "0/s");
        recording.start();

        int alignedHeapChunkSize = UnsignedUtils.safeToInt(HeapParameters.getAlignedHeapChunkSize());
        allocateCharArray(alignedHeapChunkSize);

        recording.stop();
        recording.close();

        /* Call getEvents directly because we expect zero events. */
        assertEquals(0, getEvents(recording.getDestination(), new String[]{JfrEvent.ObjectAllocationSample.getName()}, true).size());
    }

    /**
     * Tests the EWMA calculation. Assumes windowLookback == 25 and windowDuration < 1s.
     */
    @Test
    public void testEWMA() {
        long samplesPerWindow = 10;
        JfrEventThrottler throttler = new JfrEventThrottler();
        throttler.configure(samplesPerWindow * WINDOWS_PER_PERIOD, TimeUtils.millisPerSecond);

        assertEquals(25, TestingBackdoor.getWindowLookbackCount(throttler));

        long[] numSamples = {310, 410, 610, 310, 910, 420, 770, 290, 880, 640, 220, 110, 330, 590};
        long[] expectedProjectedPopulation = {12, 28, 51, 61, 95, 108, 135, 141, 170, 189, 190, 187, 193, 209};
        for (int i = 0; i < numSamples.length; i++) {
            for (int j = 0; j < numSamples[i]; j++) {
                TestingBackdoor.sample(throttler);
            }
            expireAndRotate(throttler);

            double averagePopulationSize = TestingBackdoor.getAveragePopulationSize(throttler);
            assertEquals(expectedProjectedPopulation[i], (long) averagePopulationSize);
        }
    }

    /** Ensure debt is being calculated as expected. */
    @Test
    public void testDebt() {
        long samplesPerWindow = 10;
        JfrEventThrottler throttler = new JfrEventThrottler();
        throttler.configure(samplesPerWindow * WINDOWS_PER_PERIOD, TimeUtils.millisPerSecond);

        /* Sample for some time */
        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < samplesPerWindow; j++) {
                assertTrue(TestingBackdoor.sample(throttler));
            }

            assertEquals(0, TestingBackdoor.getActiveWindowAccumulatedDebt(throttler));
            expireAndRotate(throttler);
        }

        /* Do not sample and rotate the window right away. */
        expireAndRotate(throttler);

        /* Debt should be at least samplesPerWindow because we took no samples last window. */
        long debt = TestingBackdoor.getActiveWindowAccumulatedDebt(throttler);
        assertTrue("Should have debt from under sampling.", debt >= samplesPerWindow);

        /* Do some but not enough sampling to increase debt. */
        for (int i = 0; i < samplesPerWindow / 2; i++) {
            TestingBackdoor.sample(throttler);
        }
        expireAndRotate(throttler);
        assertTrue("Should have debt from under sampling.", TestingBackdoor.getActiveWindowAccumulatedDebt(throttler) >= debt + samplesPerWindow / 2);

        // Window lookback is 25. Do not sample for 25 windows.
        for (int i = 0; i < 25; i++) {
            expireAndRotate(throttler);
        }

        // At this point sampling interval must be 1 because the projected population must be 0.
        for (int i = 0; i < samplesPerWindow + samplesPerWindow * WINDOWS_PER_PERIOD; i++) {
            TestingBackdoor.sample(throttler);
        }
        assertFalse(TestingBackdoor.sample(throttler));
        assertEquals("Should not have any debt remaining.", 0, TestingBackdoor.getActiveWindowAccumulatedDebt(throttler));
    }

    @Test
    @Ignore("Can't be executed in the CI infrastructure (may fail if the machine is under high load).")
    public void testDistributionUniform() {
        int maxPopPerWindow = 2000;
        int minPopPerWindow = 2;
        int expectedSamplesPerWindow = 50;
        testDistribution(() -> ThreadLocalRandom.current().nextInt(minPopPerWindow, maxPopPerWindow + 1), expectedSamplesPerWindow, 0.05);
    }

    @Test
    @Ignore("Can't be executed in the CI infrastructure (may fail if the machine is under high load).")
    public void testDistributionHighRate() {
        int maxPopPerWindow = 2000;
        int expectedSamplesPerWindow = 50;
        testDistribution(() -> maxPopPerWindow, expectedSamplesPerWindow, 0.05);
    }

    @Test
    @Ignore("Can't be executed in the CI infrastructure (may fail if the machine is under high load).")
    public void testDistributionLowRate() {
        int minPopPerWindow = 2;
        testDistribution(() -> minPopPerWindow, minPopPerWindow, 0.05);
    }

    @Test
    @Ignore("Can't be executed in the CI infrastructure (may fail if the machine is under high load).")
    public void testDistributionEarlyBurst() {
        int maxPopulationPerWindow = 2000;
        int expectedSamplesPerWindow = 50;
        int accumulatedDebtCarryLimit = 10; // 1000 / windowDurationMs
        AtomicInteger count = new AtomicInteger(1);
        testDistribution(() -> count.getAndIncrement() % accumulatedDebtCarryLimit == 1 ? maxPopulationPerWindow : 0, expectedSamplesPerWindow, 0.9);
    }

    @Test
    @Ignore("Can't be executed in the CI infrastructure (may fail if the machine is under high load).")
    public void testDistributionMidBurst() {
        int maxPopulationPerWindow = 2000;
        int expectedSamplesPerWindow = 50;
        int accumulatedDebtCarryLimit = 10; // 1000 / windowDurationMs
        AtomicInteger count = new AtomicInteger(1);
        testDistribution(() -> count.getAndIncrement() % accumulatedDebtCarryLimit == 5 ? maxPopulationPerWindow : 0, expectedSamplesPerWindow, 0.5);
    }

    @Test
    @Ignore("Can't be executed in the CI infrastructure (may fail if the machine is under high load).")
    public void testDistributionLateBurst() {
        int maxPopulationPerWindow = 2000;
        int expectedSamplesPerWindow = 50;
        int accumulatedDebtCarryLimit = 10; // 1000 / windowDurationMs
        AtomicInteger count = new AtomicInteger(1);
        testDistribution(() -> count.getAndIncrement() % accumulatedDebtCarryLimit == 0 ? maxPopulationPerWindow : 0, expectedSamplesPerWindow, 0.0);
    }

    /**
     * This is a more involved test that checks the sample distribution. It is based on
     * JfrGTestAdaptiveSampling in the OpenJDK.
     */
    private static void testDistribution(IntSupplier incomingPopulation, int samplePointsPerWindow, double errorFactor) {
        int distributionSlots = 100;
        int windowDurationMs = 100;
        int windowCount = 10000;

        int samplesPerWindow = 50;
        JfrEventThrottler throttler = new JfrEventThrottler();
        throttler.configure(samplesPerWindow * WINDOWS_PER_PERIOD, windowDurationMs * WINDOWS_PER_PERIOD);

        int[] population = new int[distributionSlots];
        int[] sample = new int[distributionSlots];

        int populationSize = 0;
        int sampleSize = 0;
        for (int t = 0; t < windowCount; t++) {
            int windowPop = incomingPopulation.getAsInt();
            for (int i = 0; i < windowPop; i++) {
                populationSize++;
                int index = ThreadLocalRandom.current().nextInt(0, 100);
                population[index] += 1;
                if (TestingBackdoor.sample(throttler)) {
                    sampleSize++;
                    sample[index] += 1;
                }
            }
            expireAndRotate(throttler);
        }

        int targetSampleSize = samplePointsPerWindow * windowCount;
        int expectedSamples = samplesPerWindow * windowCount;
        expectNear(targetSampleSize, sampleSize, expectedSamples * errorFactor);
        assertDistributionProperties(distributionSlots, population, sample, populationSize, sampleSize);
    }

    private static void expectNear(double value1, double value2, double error) {
        assertTrue(value1 + " is not close enough to " + value2 + ". The error tolerance is " + error, Math.abs(value1 - value2) <= error);
    }

    private static void assertDistributionProperties(int distributionSlots, int[] population, int[] sample, int populationSize, int sampleSize) {
        int populationSum = 0;
        int sampleSum = 0;
        for (int i = 0; i < distributionSlots; i++) {
            populationSum += i * population[i];
            sampleSum += i * sample[i];
        }

        double populationMean = populationSum / (double) populationSize;
        double sampleMean = sampleSum / (double) sampleSize;

        double populationVariance = 0;
        double sampleVariance = 0;
        for (int i = 0; i < distributionSlots; i++) {
            double populationDiff = i - populationMean;
            populationVariance += population[i] * populationDiff * populationDiff;

            double sampleDiff = i - sampleMean;
            sampleVariance += sample[i] * sampleDiff * sampleDiff;
        }
        populationVariance = populationVariance / (populationSize - 1);
        sampleVariance = sampleVariance / (sampleSize - 1);
        double populationStdev = Math.sqrt(populationVariance);
        double sampleStdev = Math.sqrt(sampleVariance);
        expectNear(populationStdev, sampleStdev, 0.5); // 0.5 value to match Hotspot test
        expectNear(populationMean, sampleMean, populationStdev);
    }

    @NeverInline("Prevent optimizations.")
    private static char[] allocateCharArray(int length) {
        return new char[length];
    }

    private static JfrEventThrottler newEventThrottler(long samplesPerWindow) {
        JfrEventThrottler throttler = new JfrEventThrottler();
        throttler.configure(samplesPerWindow * WINDOWS_PER_PERIOD, WINDOW_DURATION_MS * WINDOWS_PER_PERIOD);
        return throttler;
    }

    private static void expireAndRotate(JfrEventThrottler throttler) {
        TestingBackdoor.expireActiveWindow(throttler);
        assertFalse("Should have rotated not sampled!", TestingBackdoor.sample(throttler));
    }
}
