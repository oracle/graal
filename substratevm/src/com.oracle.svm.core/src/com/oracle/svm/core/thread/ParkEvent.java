/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.TimeUtils;

/**
 * Per-thread blocking support. An instance is owned by at most one thread at a time for blocking
 * itself from scheduling ("parking") by calling {@link #park}. Any other thread may call
 * {@link #unpark} to unblock the owner thread (or make its next attempt to park return instantly).
 *
 * Each thread may own several of these for parking. Instances are usually expensive objects because
 * they encapsulate native resources. Therefore, lazy initialization is used, see
 * {@link ThreadData}.
 */
public abstract class ParkEvent {

    public interface ParkEventFactory {
        ParkEvent acquire();
    }

    /** Currently required by legacy code. */
    protected boolean isSleepEvent;

    protected ParkEvent() {
    }

    /** Reset a pending {@link #unpark()} at the time of the call. */
    protected abstract void reset();

    /** {@link #park} indefinitely: {@code park(false, 0);}. */
    protected abstract void condWait();

    /**
     * {@link #park} with a duration in nanoseconds:
     * {@code if(duration > 0) park(false, duration);}.
     */
    protected abstract void condTimedWait(long durationNanos);

    /**
     * Block the calling thread (which must be the owner of this instance) from being scheduled
     * until another thread calls {@link #unpark},
     * <ul>
     * <li>{@code !isAbsolute && time == 0}: indefinitely.</li>
     * <li>{@code !isAbsolute && time > 0}: until {@code time} nanoseconds elapse.</li>
     * <li>{@code isAbsolute && time > 0}: until a deadline of {@code time} milliseconds from the
     * Epoch passes (see {@link System#currentTimeMillis()}.</li>
     * <li>otherwise: return instantly without parking.</li>
     * </ul>
     * May also return spuriously instead (for no apparent reason).
     */
    protected void park(boolean isAbsolute, long time) {
        if (!isAbsolute && time == 0) {
            condWait();
        } else if (time > 0) {
            condTimedWait(TimeUtils.durationNanos(isAbsolute, time));
        }
    }

    /** Try consuming an unpark without blocking. */
    protected boolean tryFastPark() {
        return false;
    }

    /**
     * Unblock the owner thread if it parks on this object, or make its next attempt to park on this
     * object return immediately.
     */
    protected abstract void unpark();

    static ParkEvent acquire(boolean isSleepEvent) {
        ParkEventFactory factory = ImageSingletons.lookup(ParkEventFactory.class);
        ParkEvent event = factory.acquire();
        event.isSleepEvent = isSleepEvent;
        return event;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void release();
}
