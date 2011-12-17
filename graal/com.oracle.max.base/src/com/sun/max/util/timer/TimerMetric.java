/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util.timer;

import java.io.*;

import com.sun.max.profile.*;
import com.sun.max.profile.Metrics.*;

/**
 * This class implements a wrapper around a timer that collects statistics about the time intervals recorded.
 */
public class TimerMetric implements Timer, Metric {
    private final Timer timer;

    private int count;
    private long elapsed;
    private long nested;

    public TimerMetric(Timer timer) {
        this.timer = timer;
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
        synchronized (this) {
            count++;
            elapsed += timer.getLastElapsedTime();
            nested += timer.getLastNestedTime();
        }
    }

    public Clock getClock() {
        return timer.getClock();
    }

    public long getLastElapsedTime() {
        return timer.getLastElapsedTime();
    }

    public long getLastNestedTime() {
        return timer.getLastNestedTime();
    }

    public synchronized void reset() {
        count = 0;
        elapsed = 0;
        nested = 0;
    }

    public long getElapsedTime() {
        return elapsed;
    }

    public long getNestedTime() {
        return nested;
    }

    public int getCount() {
        return count;
    }

    public synchronized void report(String name, PrintStream stream) {
        if (count > 0) {
            final long hz = timer.getClock().getHZ();
            final long total = elapsed - nested;
            if (hz > 0) {
                // report in seconds
                final double secs = total / (double) hz;
                Metrics.report(stream, name, "total", "--", String.valueOf(secs), "seconds");
                Metrics.report(stream, name, "average", "--", String.valueOf(secs / count), "seconds (" + count + " intervals)");
            } else {
                // report in ticks
                Metrics.report(stream, name, "total", "--", String.valueOf(total), "ticks");
                Metrics.report(stream, name, "average", "--", String.valueOf(total / (double) count), "ticks (" + count + " intervals)");
            }
        }
    }
}
