/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.bridge;

import java.util.concurrent.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;

/**
 * The rate of accumulation for a metric within an execution phase.
 */
final class MetricRateInPhase {

    private final String phase;
    private final MetricRateInPhase previous;
    private final long time;
    private final long value;
    private final TimeUnit timeUnit;

    public static MetricRateInPhase snapshot(String phase, MetricRateInPhase previous, DebugMetric metric, DebugTimer timer, TimeUnit timeUnit) {
        return new MetricRateInPhase(phase, previous, metric, timer, timeUnit);
    }

    private MetricRateInPhase(String phase, MetricRateInPhase previous, DebugMetric metric, DebugTimer timer, TimeUnit timeUnit) {
        this.phase = phase;
        this.previous = previous;
        this.time = VMToCompilerImpl.collectTotal((DebugValue) timer);
        this.value = VMToCompilerImpl.collectTotal((DebugValue) metric);
        this.timeUnit = timeUnit;
    }

    public int rate() {
        long t = time;
        long v = value;
        if (previous != null) {
            t -= previous.time;
            v -= previous.value;
        }

        t = timeUnit.convert(t, TimeUnit.NANOSECONDS);
        if (t == 0) {
            t = 1;
        }
        return (int) (v / t);
    }

    public void printAll(String label) {
        MetricRateInPhase rs = this;
        while (rs != null) {
            System.out.println(label + "@" + rs.phase + ": " + rs.rate());
            rs = rs.previous;
        }
    }
}
