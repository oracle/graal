/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.Uninterruptible;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

/**
 * An uninterruptible nanosecond-precision timer that can be started repeatedly.
 */
public class Timer implements AutoCloseable {
    private final String name;
    private long startedNanos;
    private long stoppedNanos;
    private boolean wasStarted;
    private boolean wasStopped;
    private long totalElapsedNanos;

    public Timer(String name) {
        this.name = name;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public String name() {
        return name;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Timer start() {
        return startAt(System.nanoTime());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Timer startAt(long nanoTime) {
        /*
         * GR-63365: assert !wasStarted : "Timer already started";
         */
        startedNanos = nanoTime;
        wasStarted = true;
        stoppedNanos = 0L;
        wasStopped = false;
        return this;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long startedNanos() {
        if (!wasStarted) {
            /* If a timer was not started, pretend it was started at the start of the VM. */
            assert startedNanos == 0;
            return Isolates.getStartTimeNanos();
        }
        return startedNanos;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long stoppedNanos() {
        assert wasStopped : "Timer not stopped";
        return stoppedNanos;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void close() {
        stop();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void stop() {
        stopAt(System.nanoTime());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void stopAt(long nanoTime) {
        /*
         * GR-63365: assert !wasStopped : "Timer already stopped";
         */
        stoppedNanos = nanoTime;
        wasStopped = true;
        /*
         * GR-63365: assert stoppedNanos >= startedNanos() : "Invalid stop time";
         */
        totalElapsedNanos += stoppedNanos - startedNanos();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long lastIntervalNanos() {
        assert wasStopped : "Timer not stopped";
        return stoppedNanos() - startedNanos();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long lastIntervalMillis() {
        return TimeUtils.roundNanosToMillis(lastIntervalNanos());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long totalNanos() {
        return totalElapsedNanos;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long totalMillis() {
        return TimeUtils.roundNanosToMillis(totalNanos());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void reset() {
        /*
         * GR-63365: assert wasStopped : "Attempting to reset a started timer";
         */
        startedNanos = 0L;
        wasStarted = false;
        stoppedNanos = 0L;
        wasStopped = false;
        totalElapsedNanos = 0L;
    }
}
