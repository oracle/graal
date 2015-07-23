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

import static com.oracle.graal.debug.DebugCloseable.*;

import java.lang.management.*;
import java.util.concurrent.*;

import jdk.internal.jvmci.debug.*;

import com.oracle.graal.debug.*;

public final class TimerImpl extends AccumulatedDebugValue implements DebugTimer {

    private static final ThreadMXBean threadMXBean = Management.getThreadMXBean();

    /**
     * Records the most recent active timer.
     */
    private static final ThreadLocal<CloseableCounterImpl> currentTimer = new ThreadLocal<>();

    static class FlatTimer extends DebugValue implements DebugTimer {
        private TimerImpl accm;

        public FlatTimer(String name, boolean conditional) {
            super(name + "_Flat", conditional);
        }

        @Override
        public String toString(long value) {
            return valueToString(value);
        }

        public TimeUnit getTimeUnit() {
            return accm.getTimeUnit();
        }

        public DebugCloseable start() {
            return accm.start();
        }
    }

    public TimerImpl(String name, boolean conditional) {
        super(name, conditional, new FlatTimer(name, conditional));
        ((FlatTimer) flat).accm = this;
    }

    @Override
    public DebugCloseable start() {
        if (!isConditional() || Debug.isTimeEnabled()) {
            AbstractTimer result;
            if (threadMXBean.isCurrentThreadCpuTimeSupported()) {
                result = new CpuTimeTimer(this);
            } else {
                result = new SystemNanosTimer(this);
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

    public DebugTimer getFlat() {
        return (FlatTimer) flat;
    }

    @Override
    public String toString(long value) {
        return valueToString(value);
    }

    public TimeUnit getTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }

    private abstract static class AbstractTimer extends CloseableCounterImpl implements DebugCloseable {

        private AbstractTimer(AccumulatedDebugValue counter) {
            super(currentTimer.get(), counter);
        }

        @Override
        public void close() {
            super.close();
            currentTimer.set(parent);
        }
    }

    private final class SystemNanosTimer extends AbstractTimer {

        public SystemNanosTimer(TimerImpl timer) {
            super(timer);
        }

        @Override
        protected long getCounterValue() {
            return System.nanoTime();
        }
    }

    private final class CpuTimeTimer extends AbstractTimer {

        public CpuTimeTimer(TimerImpl timer) {
            super(timer);
        }

        @Override
        protected long getCounterValue() {
            return threadMXBean.getCurrentThreadCpuTime();
        }
    }
}
