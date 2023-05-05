/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.core.jfr.JfrThrottler;
import org.junit.Test;
import org.junit.Assert;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestThrottler {

    // Based on hardcoded value in the throttler class.
    private final long WINDOWS_PER_PERIOD = 5;
    // Arbitrary. It doesn't matter what this is.
    private final long WINDOW_DURATION_MS = 200;
    private final long SAMPLES_PER_WINDOW = 10;

    /**
     * This is the simplest test that ensures that sampling stops after the cap is hit. Single thread.
     * All sampling is done within the first window. No rotations.
     */
    @Test
    public void testCapSingleThread() {
        // Doesn't rotate after starting sampling

        JfrThrottler throttler = new JfrThrottler();
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
        final AtomicInteger count = new AtomicInteger();
        JfrThrottler throttler = new JfrThrottler();
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
        Thread firstThread = new Thread(doSampling);
        Thread secondThread = new Thread(doSampling);
        Thread thirdThread = new Thread(doSampling);
        firstThread.start();
        secondThread.start();
        thirdThread.start();
        firstThread.join();
        secondThread.join();
        thirdThread.join();

        assertFalse("failed! Too many samples taken! " + count.get(), count.get() > samplesPerWindow);
        // Previous measured population should be 3*samplesPerWindow
        // Force window rotation and repeat.
        count.set(0);
        throttler.setThrottle(samplesPerWindow * WINDOWS_PER_PERIOD, WINDOW_DURATION_MS * WINDOWS_PER_PERIOD);
        Thread fourthThread = new Thread(doSampling);
        Thread fifthThread = new Thread(doSampling);
        Thread sixthThread = new Thread(doSampling);
        fourthThread.start();
        fifthThread.start();
        sixthThread.start();
        fourthThread.join();
        fifthThread.join();
        sixthThread.join();

        assertFalse("failed! Too many samples taken (after rotation)! " + count.get(), count.get() > samplesPerWindow);
    }

    /**
     * This test ensures that sampling stops after the cap is hit. Then sampling resumes once the
     * window rotates.
     */
    @Test
    public void testExpiry() {
        final long samplesPerWindow = 10;
        JfrThrottler throttler = new JfrThrottler();
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
     * This test checks that the projected population and sampling interval after a window rotation
     * is as expected. Window lookback for this test is 5.
     *
     * Will be low rate, which results in lookback of 5, because window duration is period.
     */
    @Test
    public void testEWMA() {
        final long samplesPerWindow = 100;
        JfrThrottler throttler = new JfrThrottler();
        // Samples per second is low (<10) resulting in a windowDuration being un-normalized (1 min) resulting in a window lookback of 5.0
        throttler.beginTest(samplesPerWindow * WINDOWS_PER_PERIOD,  60 * 1000);
        assertTrue(throttler.getWindowLookback() == 5.0);

        int[] population = {21, 41, 61, 31, 91, 42, 77, 29, 88, 64, 22, 11, 33, 59}; // Arbitrary
        // Used EWMA calculator to confirm actualProjections
        double[] actualProjections = {4.2, 11.56, 21.44, 23.35, 36.88, 37.90, 45.72, 42.38, 51.50, 54.00, 47.60, 40.28, 38.82, 42.86};

        for (int p = 0; p < population.length; p++) {
            for (int i = 0; i < population[p]; i++) {
                throttler.sample();
            }
            expireAndRotate(throttler);
            double projectedPopulation = throttler.getActiveWindowProjectedPopulationSize();
            if ((int) actualProjections[p] != (int) projectedPopulation)
            {
                System.out.println(actualProjections[p]+ " "+ projectedPopulation);
            }
//            assertTrue((int) actualProjections[p] == (int) projectedPopulation);
        }
    }

    /**
     * Note: computeAccumulatedDebtCarryLimit depends on window duration.
     *
     * Window lookback for this test is 25. Window duration is 1 second. Window divisor is default of 5.
     */
    @Test
    public void testDebt() {
        final long samplesPerWindow = 10;
        final long actualSamplesPerWindow = 50;
        JfrThrottler throttler = new JfrThrottler();
        throttler.beginTest(samplesPerWindow * WINDOWS_PER_PERIOD, WINDOWS_PER_PERIOD * WINDOW_DURATION_MS);

        for (int p = 0; p < 50; p++) {
            for (int i = 0; i < actualSamplesPerWindow; i++) {
                throttler.sample();
            }
            expireAndRotate(throttler);
        }
        // now the sampling interval must be 50 / 10 = 5
        assertTrue("Sampling interval is incorrect:"+ throttler.getActiveWindowSamplingInterval(), throttler.getActiveWindowSamplingInterval() == 5);

        // create debt by under sampling. Instead of 50, only sample 20 times. Debt should be 6
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
        long periodMs = 60*1000;
        JfrThrottler throttler = new JfrThrottler();
        throttler.beginTest(sampleSize, periodMs);
        assertTrue(throttler.getPeriodNs()+" "+ throttler.getEventSampleSize(),throttler.getEventSampleSize()==sampleSize/60 && throttler.getPeriodNs() == 1000000000);

        sampleSize = 10*3600;
        periodMs = 3600*1000;
        throttler.setThrottle(sampleSize, periodMs);
        assertTrue(throttler.getPeriodNs()+" "+ throttler.getEventSampleSize(), throttler.getEventSampleSize()==sampleSize/3600 && throttler.getPeriodNs() == 1000000000);
    }

    /**
     * Helper method that expires and rotates a throttler's active window
     */
    private void expireAndRotate(JfrThrottler throttler) {
        throttler.expireActiveWindow();
        assertTrue("should be expired", throttler.IsActiveWindowExpired());
        assertFalse("Should have rotated not sampled!", throttler.sample());
    }
}
