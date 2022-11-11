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

/**
 * Each thread has several of these on which to wait. Instances are usually expensive objects
 * because they encapsulate native resources. Therefore, lazy initialization is used, see
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

    /**
     * Resets a pending {@link #unpark()} at the time of the call.
     */
    protected abstract void reset();

    /* cond_wait. */
    protected abstract void condWait();

    /** cond_timedwait, similar to {@link #condWait} but with a timeout in nanoseconds. */
    protected abstract void condTimedWait(long delayNanos);

    /** Notify anyone waiting on this event. */
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
