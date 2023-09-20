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

package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.locks.VMMutex;

/**
 * Each event that supports throttling has its own throttler that can be accessed through this
 * class.
 */
public class JfrThrottlerSupport {
    private JfrThrottler objectAllocationSampleThrottler;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrThrottlerSupport() {
        objectAllocationSampleThrottler = new JfrThrottler(new VMMutex("jfrThrottler"));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private JfrThrottler getThrottler(long eventId) {
        if (eventId == JfrEvent.ObjectAllocationSample.getId()) {
            return objectAllocationSampleThrottler;
        }
        return null;
    }

    public boolean setThrottle(long eventTypeId, long eventSampleSize, long periodMs) {
        JfrThrottler throttler = getThrottler(eventTypeId);
        if (throttler == null) {
            // This event doesn't support throttling
            return false;
        }
        throttler.setThrottle(eventSampleSize, periodMs);
        return true;
    }

    public boolean shouldCommit(long eventTypeId) {
        JfrThrottler throttler = getThrottler(eventTypeId);
        if (throttler == null) {
            // This event doesn't support throttling
            return true;
        }
        return throttler.sample();
    }
}
