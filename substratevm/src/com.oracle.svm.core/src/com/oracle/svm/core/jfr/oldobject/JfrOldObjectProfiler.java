/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.oldobject;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.thread.JavaSpinLockUtils;

import jdk.internal.misc.Unsafe;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

/**
 * This class is used in the allocation slow-path for object sampling and keeps track of objects
 * that are potential memory leaks (i.e., objects that are alive for a long time span).
 */
public final class JfrOldObjectProfiler {
    private static final int DEFAULT_SAMPLER_SIZE = 256;
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(JfrOldObjectProfiler.class, "lock");

    @SuppressWarnings("unused") private volatile int lock;

    private int queueSize;
    private JfrOldObjectSampler sampler;

    public JfrOldObjectProfiler() {
        this.queueSize = DEFAULT_SAMPLER_SIZE;
    }

    public void configure(int oldObjectQueueSize) {
        if (Logger.shouldLog(LogTag.JFR, LogLevel.DEBUG)) {
            Logger.log(LogTag.JFR, LogLevel.DEBUG, "Initialize old object sampler: old-object-queue-size=" + oldObjectQueueSize);
        }
        this.queueSize = oldObjectQueueSize;
    }

    public void reset() {
        this.sampler = new JfrOldObjectSampler(queueSize);
    }

    public void teardown() {
        this.sampler = null;
    }

    @Uninterruptible(reason = "Needed for shouldEmit().")
    public boolean sample(Object obj, UnsignedWord allocatedSize, int arrayLength) {
        if (!JfrEvent.OldObjectSample.shouldEmit()) {
            return false;
        }
        return sample0(obj, allocatedSize, arrayLength);
    }

    @Uninterruptible(reason = "Must not safepoint while holding the lock.")
    private boolean sample0(Object obj, UnsignedWord allocatedSize, int arrayLength) {
        assert allocatedSize.aboveThan(0);
        assert arrayLength >= 0 || arrayLength == Integer.MIN_VALUE;

        boolean success = JavaSpinLockUtils.tryLock(this, LOCK_OFFSET);
        if (!success) {
            /* Give up if some other thread is currently sampling. */
            return false;
        }

        try {
            return sampler.sample(obj, allocatedSize, arrayLength);
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = "Must not safepoint while holding the lock.")
    public void emit(long cutoff, boolean emitAll, boolean skipBFS) {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
        try {
            sampler.emit(cutoff, emitAll, skipBFS);
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    public static class TestingBackdoor {
        public static JfrOldObject getOldestObject(JfrOldObjectProfiler profiler) {
            return profiler.sampler.getOldestObject();
        }

        public static boolean sample(JfrOldObjectProfiler profiler, Object obj, UnsignedWord allocatedSize, int arrayLength) {
            return profiler.sample0(obj, allocatedSize, arrayLength);
        }
    }
}
