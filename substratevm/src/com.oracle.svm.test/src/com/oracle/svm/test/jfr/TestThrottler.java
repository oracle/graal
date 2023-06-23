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

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrThrottler;
import jdk.jfr.Recording;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.oracle.svm.core.locks.VMMutex;

public class TestThrottler extends JfrRecordingTest {

    // Based on hardcoded value in the throttler class.
    private static final long WINDOWS_PER_PERIOD = 5;
    // Arbitrary. It doesn't matter what this is.
    private static final long WINDOW_DURATION_MS = 200;
    private static final long SAMPLES_PER_WINDOW = 10;
    private static final long SECOND_IN_MS = 1000;
    private static final VMMutex mutex = new VMMutex("testThrottler");

    /**
     * This is the simplest test that ensures that sampling stops after the cap is hit. Single
     * thread. All sampling is done within the first window. No rotations.
     */
    @Test
    public void testCapSingleThread() {
        // Doesn't rotate after starting sampling
        JfrThrottler throttler = new JfrThrottler(mutex);
        throttler.setThrottle(SAMPLES_PER_WINDOW * WINDOWS_PER_PERIOD, WINDOW_DURATION_MS * WINDOWS_PER_PERIOD);
        for (int i = 0; i < SAMPLES_PER_WINDOW * WINDOWS_PER_PERIOD; i++) {
            boolean sample = throttler.sample();
            assertFalse("failed! should take sample if under window limit", i < SAMPLES_PER_WINDOW && !sample);
            assertFalse("failed! should not take sample if over window limit", i >= SAMPLES_PER_WINDOW && sample);
        }
    }

    /**
     * This test ensures that sampling stops after the cap is hit, even when multiple threads are
     * doing sampling.
     */
    @Test
    public void testCapConcurrent() throws InterruptedException {
        final long samplesPerWindow = 100000;
        final int testingThreadCount = 10;
        final AtomicInteger count = new AtomicInteger();
        List<Thread> testingThreads = new ArrayList<>();
        JfrThrottler throttler = new JfrThrottler(mutex);
        throttler.beginTest(samplesPerWindow * WINDOWS_PER_PERIOD, WINDOW_DURATION_MS * WINDOWS_PER_PERIOD);
        Runnable doSampling = () -> {
            for (int i = 0; i < samplesPerWindow; i++) {
                boolean sample = throttler.sample();
                if (sample) {
                    count.incrementAndGet();
                }
            }
        };
        count.set(0);

        for (int i = 0; i < testingThreadCount; i++) {
            Thread worker = new Thread(doSampling);
            worker.start();
            testingThreads.add(worker);
        }
        for (Thread thread : testingThreads) {
            thread.join();
        }

        assertFalse("failed! Too many samples taken! " + count.get(), count.get() > samplesPerWindow);
        // Previous measured population should be 3*samplesPerWindow
        // Force window rotation and repeat.
        count.set(0);
        expireAndRotate(throttler);
        for (int i = 0; i < testingThreadCount; i++) {
            Thread worker = new Thread(doSampling);
            worker.start();
            testingThreads.add(worker);
        }
        for (Thread thread : testingThreads) {
            thread.join();
        }

        assertFalse("failed! Too many samples taken (after rotation)! " + count.get(), count.get() > samplesPerWindow);
    }

    /**
     * This test ensures that sampling stops after the cap is hit. Then sampling resumes once the
     * window rotates.
     */
    @Test
    public void testExpiry() {
        final long samplesPerWindow = 10;
        JfrThrottler throttler = new JfrThrottler(mutex);
        throttler.beginTest(samplesPerWindow * WINDOWS_PER_PERIOD, WINDOW_DURATION_MS * WINDOWS_PER_PERIOD);
        int count = 0;

        for (int i = 0; i < samplesPerWindow * 10; i++) {
            boolean sample = throttler.sample();
            if (sample) {
                count++;
            }
        }

        assertTrue("Should have taken maximum possible samples: " + samplesPerWindow + " but took:" + count, samplesPerWindow == count);

        // rotate window by advancing time forward
        expireAndRotate(throttler);

        assertTrue("After window rotation, it should be possible to take more samples", throttler.sample());
    }

    /**
     * This test checks the projected population after a window rotation. This is a test of the EWMA
     * calculation. Window lookback is 25 and windowDuration is un-normalized because period is not
     * greater than 1s.
     */
    @Test
    public void testEWMA() {
        // Results in 50 samples per second
        final long samplesPerWindow = 10;
        JfrThrottler throttler = new JfrThrottler(mutex);
        throttler.beginTest(samplesPerWindow * WINDOWS_PER_PERIOD, SECOND_IN_MS);
        assertTrue(throttler.getWindowLookback() == 25.0);
        // Arbitrarily chosen
        int[] population = {310, 410, 610, 310, 910, 420, 770, 290, 880, 640, 220, 110, 330, 590};
        // Used EWMA calculator to confirm actualProjections
        int[] actualProjections = {12, 28, 51, 61, 95, 108, 135, 141, 170, 189, 190, 187, 193, 209};
        for (int p = 0; p < population.length; p++) {
            for (int i = 0; i < population[p]; i++) {
                throttler.sample();
            }
            expireAndRotate(throttler);
            double projectedPopulation = throttler.getActiveWindowProjectedPopulationSize();
            assertTrue(actualProjections[p] == (int) projectedPopulation);
        }
    }

    /**
     * Note: computeAccumulatedDebtCarryLimit depends on window duration.
     *
     * Window lookback for this test is 25. Window duration is 1 second. Window divisor is default
     * of 5.
     */
    @Test
    public void testDebt() {
        final long samplesPerWindow = 10;
        final long actualSamplesPerWindow = 50;
        JfrThrottler throttler = new JfrThrottler(mutex);
        throttler.beginTest(samplesPerWindow * WINDOWS_PER_PERIOD, WINDOWS_PER_PERIOD * WINDOW_DURATION_MS);

        for (int p = 0; p < 50; p++) {
            for (int i = 0; i < actualSamplesPerWindow; i++) {
                throttler.sample();
            }
            expireAndRotate(throttler);
        }
        // now the sampling interval must be 50 / 10 = 5
        assertTrue("Sampling interval is incorrect:" + throttler.getActiveWindowSamplingInterval(), throttler.getActiveWindowSamplingInterval() == 5);

        // create debt by under sampling. Instead of 50, only sample 20 times. Debt should be 10 -
        // (20/5) = 6
        // samples
        for (int i = 0; i < 20; i++) {
            throttler.sample();
        }
        expireAndRotate(throttler);
        assertTrue("Should have debt from under sampling.", throttler.getActiveWindowDebt() == 6);
        // sampling interval should be 3 now. Take no samples and rotate. Results in accumulated
        // debt 6 + 10
        expireAndRotate(throttler);
        assertTrue("Should have accumulated debt from under sampling consecutively.", throttler.getActiveWindowDebt() == 16);
        expireAndRotate(throttler);
        expireAndRotate(throttler);
        assertTrue("Debt is so high we should not skip any samples now.", throttler.getActiveWindowSamplingInterval() == 1);
        expireAndRotate(throttler);
        assertTrue("Debt should be forgiven at beginning of new period.", throttler.getActiveWindowDebt() == 0);
    }

    /**
     * Tests normalization of sample size and period.
     */
    @Test
    public void testNormalization() {
        long sampleSize = 10 * 600;
        long periodMs = 60 * SECOND_IN_MS;
        JfrThrottler throttler = new JfrThrottler(mutex);
        throttler.beginTest(sampleSize, periodMs);
        assertTrue(throttler.getPeriodNs() + " " + throttler.getEventSampleSize(), throttler.getEventSampleSize() == sampleSize / 60 && throttler.getPeriodNs() == 1000000 * SECOND_IN_MS);

        sampleSize = 10 * 3600;
        periodMs = 3600 * SECOND_IN_MS;
        throttler.setThrottle(sampleSize, periodMs);
        assertTrue(throttler.getPeriodNs() + " " + throttler.getEventSampleSize(), throttler.getEventSampleSize() == sampleSize / 3600 && throttler.getPeriodNs() == 1000000 * SECOND_IN_MS);
    }

    /**
     * Checks that no ObjectAllocationSample events are emitted when the sampling rate is 0.
     */
    @Test
    public void testZeroRate() throws Throwable {
        // Test throttler in isolation
        JfrThrottler throttler = new JfrThrottler(mutex);
        throttler.setThrottle(0, 2 * SECOND_IN_MS);
        assertFalse(throttler.sample());
        throttler.setThrottle(10, 2 * SECOND_IN_MS);
        assertTrue(throttler.sample());

        // Test applying throttling settings to an in-progress recording
        Recording recording = startRecording(new String[]{}, null, new HashMap<>()); // don't use
                                                                                     // default
                                                                                     // configuration
                                                                                     // because it
                                                                                     // includes
                                                                                     // ObjectAllocationSample
                                                                                     // by default
        recording.enable(JfrEvent.ObjectAllocationSample.getName()).with("throttle", "0/s");
        final int alignedHeapChunkSize = com.oracle.svm.core.util.UnsignedUtils.safeToInt(HeapParameters.getAlignedHeapChunkSize());
        allocateCharArray(alignedHeapChunkSize);

        recording.stop();
        recording.close();

        assertTrue(getEvents(recording.getDestination(), new String[]{JfrEvent.ObjectAllocationSample.getName()}).size() == 0);
    }

    @NeverInline("Prevent escape analysis.")
    private static char[] allocateCharArray(int length) {
        return new char[length];
    }

    @Test
    public void testDistributionUniform() {
        final int maxPopPerWindow = 2000;
        final int minPopPerWindow = 2;
        final int expectedSamplesPerWindow = 50;
        testDistribution(() -> ThreadLocalRandom.current().nextInt(minPopPerWindow, maxPopPerWindow + 1), expectedSamplesPerWindow, 0.05);
    }

    @Test
    public void testDistributionHighRate() {
        final int maxPopPerWindow = 2000;
        final int expectedSamplesPerWindow = 50;
        testDistribution(() -> maxPopPerWindow, expectedSamplesPerWindow, 0.02);
    }

    @Test
    public void testDistributionLowRate() {
        final int minPopPerWindow = 2;
        testDistribution(() -> minPopPerWindow, minPopPerWindow, 0.05);
    }

    @Test
    public void testDistributionEarlyBurst() { // maybe this is 41000 instead of 50000 because
                                               // projected population size is low
        final int maxPopPerWindow = 2000;
        final int expectedSamplesPerWindow = 50;
        final int accumulatedDebtCarryLimit = 10; // 1000 / windowDurationMs
        AtomicInteger count = new AtomicInteger(1);
        testDistribution(() -> count.getAndIncrement() % accumulatedDebtCarryLimit == 1 ? maxPopPerWindow : 0, expectedSamplesPerWindow, 0.9);
    }

    @Test
    public void testDistributionMidBurst() {
        final int maxPopPerWindow = 2000;
        final int expectedSamplesPerWindow = 50;
        final int accumulatedDebtCarryLimit = 10; // 1000 / windowDurationMs
        AtomicInteger count = new AtomicInteger(1);
        testDistribution(() -> count.getAndIncrement() % accumulatedDebtCarryLimit == 5 ? maxPopPerWindow : 0, expectedSamplesPerWindow, 0.5);
    }

    @Test
    public void testDistributionLateBurst() { // *** I think its [NOT] bursting every window by
                                              // accident. windowCount=10000 not 1000
        final int maxPopPerWindow = 2000;
        final int expectedSamplesPerWindow = 50;
        final int accumulatedDebtCarryLimit = 10; // 1000 / windowDurationMs
        AtomicInteger count = new AtomicInteger(1);
        testDistribution(() -> count.getAndIncrement() % accumulatedDebtCarryLimit == 0 ? maxPopPerWindow : 0, expectedSamplesPerWindow, 0.0);
    }

    /**
     * This is a more involved test that checks the sample distribution. It has been adapted from
     * JfrGTestAdaptiveSampling in the OpenJDK
     */
    private void testDistribution(IncomingPopulation incomingPopulation, int samplePointsPerWindow, double errorFactor) {
        final int distributionSlots = 100;
        final int windowDurationMs = 100;
        final int windowCount = 10000;
        final int expectedSamplesPerWindow = 50;
        final int expectedSamples = expectedSamplesPerWindow * windowCount;
        final int windowLookBackCount = 50;
        final double maxSampleBias = 0.11;
        JfrThrottler throttler = new JfrThrottler(mutex);
        throttler.beginTest(expectedSamplesPerWindow * WINDOWS_PER_PERIOD, windowDurationMs * WINDOWS_PER_PERIOD);

        int[] population = new int[distributionSlots];
        int[] sample = new int[distributionSlots];

        int populationSize = 0;
        int sampleSize = 0;
        for (int t = 0; t < windowCount; t++) {
            int windowPop = incomingPopulation.getWindowPopulation();
            for (int i = 0; i < windowPop; i++) {
                populationSize++;
                int index = ThreadLocalRandom.current().nextInt(0, 100);
                population[index] += 1;
                if (throttler.sample()) {
                    sampleSize++;
                    sample[index] += 1;
                }
            }
            expireAndRotate(throttler);
        }
        int targetSampleSize = samplePointsPerWindow * windowCount;
        System.out.println("Population size:" + populationSize + " Sample size: " + sampleSize);
        expectNear(targetSampleSize, sampleSize, expectedSamples * errorFactor);
        assertDistributionProperties(distributionSlots, population, sample, populationSize, sampleSize);
    }

    private static void expectNear(double value1, double value2, double error) {
// System.out.println(value1 +" "+ value2);
        assertTrue(Math.abs(value1 - value2) <= error);
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
// System.out.println("populationStdev:"+populationStdev +" sampleStdev:"+sampleStdev );
        expectNear(populationStdev, sampleStdev, 0.5); // 0.5 value copied from Hotspot test
        expectNear(populationMean, sampleMean, populationStdev);
    }

    interface IncomingPopulation {
        int getWindowPopulation();
    }

    /**
     * Helper method that expires and rotates a throttler's active window.
     */
    private static void expireAndRotate(JfrThrottler throttler) {
        throttler.expireActiveWindow();
        assertTrue("should be expired", throttler.isActiveWindowExpired());
        assertFalse("Should have rotated not sampled!", throttler.sample());
    }
}
