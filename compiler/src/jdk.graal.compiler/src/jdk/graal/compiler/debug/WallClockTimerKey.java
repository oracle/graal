/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.debug;

import java.util.concurrent.TimeUnit;

import org.graalvm.collections.Pair;

/**
 * Represents a timer that measures elapsed wall-clock time. In contrast to {@link CPUTimerKey},
 * this timer also includes time spent waiting for resources. Use this timer if to measure the total
 * duration of an event, including, e.g., time spent waiting for a subprocess.
 */
final class WallClockTimerKey extends BaseTimerKey {

    WallClockTimerKey(String nameFormat, Object nameArg1, Object nameArg2) {
        super(nameFormat, nameArg1, nameArg2);
    }

    static final class WallClockTimer extends BaseTimerKey.Timer {
        WallClockTimer(AccumulatedKey counter, DebugContext debug) {
            super(counter, debug);
        }

        @Override
        long getCounterValue() {
            return System.nanoTime();
        }
    }

    @Override
    protected Timer createTimerInstance(DebugContext debug) {
        return new WallClockTimer(this, debug);
    }

    @Override
    public TimeUnit getTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }

    @Override
    public String toHumanReadableFormat(long value) {
        return String.format("%d ns", value);
    }

    @Override
    public Pair<String, String> toCSVFormat(long value) {
        return Pair.create(Long.toString(value), "ns");
    }
}
