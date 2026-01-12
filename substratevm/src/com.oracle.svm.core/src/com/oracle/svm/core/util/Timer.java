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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.Uninterruptible;

/**
 * An uninterruptible nanosecond-precision timer that can be started repeatedly.
 */
public class Timer implements AutoCloseable {
    private final String name;
    private long lastStartedNanos;
    private long lastStoppedNanos;
    private boolean startedAtLeastOnce;
    private boolean running;
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
    public boolean wasStartedAtLeastOnce() {
        return startedAtLeastOnce;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isRunning() {
        return running;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Timer startAt(long nanoTime) {
        assert !running : "Timer already running";
        lastStartedNanos = nanoTime;
        startedAtLeastOnce = true;
        lastStoppedNanos = 0L;
        running = true;
        return this;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long lastStartedNanoTime() {
        assert startedAtLeastOnce : "Timer not started";
        return lastStartedNanos;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long lastStoppedNanoTime() {
        assert startedAtLeastOnce && !running : "Timer not stopped";
        return lastStoppedNanos;
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
        assert running : "Timer not running";
        assert nanoTime >= lastStartedNanoTime() : "Invalid stop time";
        lastStoppedNanos = nanoTime;
        running = false;
        totalElapsedNanos += lastStoppedNanos - lastStartedNanoTime();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long lastIntervalNanos() {
        return lastStoppedNanoTime() - lastStartedNanoTime();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long totalNanos() {
        return totalElapsedNanos;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void reset() {
        assert !running : "Attempting to reset a running timer";
        lastStartedNanos = 0L;
        startedAtLeastOnce = false;
        lastStoppedNanos = 0L;
        running = false;
        totalElapsedNanos = 0L;
    }
}
