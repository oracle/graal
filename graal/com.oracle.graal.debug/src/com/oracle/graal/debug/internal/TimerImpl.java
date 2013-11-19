/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug.internal;

import java.lang.management.*;
import java.util.concurrent.*;

import com.oracle.graal.debug.*;

public final class TimerImpl extends DebugValue implements DebugTimer {

    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    public static final TimerCloseable VOID_CLOSEABLE = new TimerCloseable() {

        @Override
        public void close() {
        }
    };

    /**
     * Records the most recent active timer.
     */
    private static ThreadLocal<AbstractTimer> currentTimer = new ThreadLocal<>();

    private final DebugValue flat;

    public TimerImpl(String name, boolean conditional) {
        super(name + "_Accm", conditional);
        this.flat = new DebugValue(name + "_Flat", conditional) {

            @Override
            public String toString(long value) {
                return valueToString(value);
            }
        };
    }

    @Override
    public TimerCloseable start() {
        if (!isConditional() || Debug.isTimeEnabled()) {
            long startTime;
            if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
                startTime = threadMXBean.getCurrentThreadCpuTime();
            } else {
                startTime = System.nanoTime();
            }

            AbstractTimer result;
            if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
                result = new CpuTimeTimer(startTime);
            } else {
                result = new SystemNanosTimer(startTime);
            }
            currentTimer.set(result);
            return result;
        } else {
            return VOID_CLOSEABLE;
        }
    }

    public static String valueToString(long value) {
        return String.format("%d.%d ms", value / 1000000, (value / 100000) % 10);
    }

    @Override
    public String toString(long value) {
        return valueToString(value);
    }

    public TimeUnit getTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }

    private abstract class AbstractTimer implements TimerCloseable {

        private final AbstractTimer parent;
        private final long startTime;
        private long nestedTimeToSubtract;

        private AbstractTimer(long startTime) {
            this.parent = currentTimer.get();
            this.startTime = startTime;
        }

        @Override
        public void close() {
            long endTime = currentTime();
            long timeSpan = endTime - startTime;
            if (parent != null) {
                parent.nestedTimeToSubtract += timeSpan;
            }
            currentTimer.set(parent);
            long flatTime = timeSpan - nestedTimeToSubtract;
            TimerImpl.this.addToCurrentValue(timeSpan);
            flat.addToCurrentValue(flatTime);
        }

        protected abstract long currentTime();
    }

    private final class SystemNanosTimer extends AbstractTimer {

        public SystemNanosTimer(long startTime) {
            super(startTime);
        }

        @Override
        protected long currentTime() {
            return System.nanoTime();
        }
    }

    private final class CpuTimeTimer extends AbstractTimer {

        public CpuTimeTimer(long startTime) {
            super(startTime);
        }

        @Override
        protected long currentTime() {
            return threadMXBean.getCurrentThreadCpuTime();
        }
    }
}
