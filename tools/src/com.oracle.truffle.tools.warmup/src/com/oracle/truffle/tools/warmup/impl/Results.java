package com.oracle.truffle.tools.warmup.impl;

/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

class Results {

    final List<Long> samples;
    final long peak;
    final int peakStartI;
    final long peakStartT;
    final long warmupTime;
    final double warmupCost;
    final double epsilon;

    Results(List<Long> samples) {
        this.samples = samples;
        peak = peak(samples);
        epsilon = epsilon(samples, peak);
        peakStartI = peakStartI(samples, peak, epsilon);
        peakStartT = peakStartT(samples, peakStartI);
        warmupTime = warmupTime(samples, peakStartI, peak);
        warmupCost = (double) warmupTime / peak;

    }

    private static double epsilon(List<Long> samples, long peak) {
        int peakIndex = 0;
        for (int i = 0; i < samples.size(); i++) {
            final Long sample = samples.get(i);
            if (sample == peak) {
                peakIndex = i;
            }
        }
        long sampleSum = 0;
        for (int i = peakIndex; i < samples.size(); i++) {
            sampleSum += samples.get(i);
        }
        final double avg = (double) sampleSum / (samples.size() - peakIndex);
        final double epsilon = (avg / peak) - 1;
        return epsilon;
    }

    private static long peakStartT(List<Long> samples, int peakStartI) {
        long peak = 0;
        for (int i = 0; i < peakStartI; i++) {
            peak += samples.get(i);
        }
        return peak;
    }

    private static Long peak(List<Long> samples) {
        return samples.stream().min(Long::compareTo).get();
    }

    private static int peakStartI(List<Long> samples, long peak, double epsilon) {
        for (int i = 0; i < samples.size(); i++) {
            if (samples.get(i) < peak * (1 + epsilon)) {
                return i;
            }
        }
        throw new AssertionError("Should not reach here.");
    }

    private static long warmupTime(List<Long> samples, int peakStartI, long peak) {
        long warmup = 0;
        for (int i = 0; i < peakStartI; i++) {
            warmup += samples.get(i) - peak;
        }
        return warmup;
    }
}
