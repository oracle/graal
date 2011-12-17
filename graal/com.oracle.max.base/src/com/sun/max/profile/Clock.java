/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.profile;

/**
 * The {@code Clock} class represents a clock source that has a continuously increasing tick
 * count. A clock can be used to produce a relative timing between events by comparing the
 * number of ticks between events. This can be used to implement time-based profiling
 * by using a clock based on the system time.
 */
public abstract class Clock {

    public abstract long getTicks();

    /**
     * Gets the resolution of this clock as the number of {@linkplain #getTicks() ticks} per second.
     * @return the resolution of the clock
     */
    public abstract long getHZ();

    public static final Clock SYSTEM_NANOSECONDS = new SystemNS();
    public static final Clock SYSTEM_MILLISECONDS = new SystemMS();

    private static class SystemMS extends Clock {
        @Override
        public long getTicks() {
            return System.currentTimeMillis();
        }
        @Override
        public long getHZ() {
            return 1000;
        }
    }

    private static class SystemNS extends Clock {
        @Override
        public long getTicks() {
            return System.nanoTime();
        }
        @Override
        public long getHZ() {
            return 1000000000;
        }
    }

    public static int[] sampleDeltas(Clock clock, int numberOfSamples) {
        final int[] result = new int[numberOfSamples];
        long sample = clock.getTicks();
        for (int i = 0; i < numberOfSamples; i++) {
            final long newSample = clock.getTicks();
            result[i] = (int) (newSample - sample);
            sample = newSample;
        }
        return result;
    }

    public static void sample5(Clock clock, long[] samples) {
        final long sample0 = clock.getTicks();
        final long sample1 = clock.getTicks();
        final long sample2 = clock.getTicks();
        final long sample3 = clock.getTicks();
        final long sample4 = clock.getTicks();
        samples[0] = sample0;
        samples[1] = sample1;
        samples[2] = sample2;
        samples[3] = sample3;
        samples[4] = sample4;
    }
}
