/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.genscavenge.service;

import com.oracle.svm.core.heap.GCCause;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import com.oracle.svm.core.Uninterruptible;

/**
 * This class stashes GC info during a safepoint until it can be dequeued to emit a notification at
 * a point in the future.
 */
public class GcNotificationRequest {
    private PoolMemoryUsage[] before;
    private PoolMemoryUsage[] after;

    // Times since the VM started.
    public long startTime;
    public long endTime;

    // This is the system time that the request was sent.
    public long timestamp;
    public boolean isIncremental;
    public GCCause cause;
    public long epoch;

    @Platforms(Platform.HOSTED_ONLY.class)
    GcNotificationRequest(int poolCount) {
        before = new PoolMemoryUsage[poolCount];
        after = new PoolMemoryUsage[poolCount];
        for (int i = 0; i < poolCount; i++) {
            before[i] = new PoolMemoryUsage();
            after[i] = new PoolMemoryUsage();
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setPoolBefore(int index, long used, long committed, String name) {
        before[index].used = used;
        before[index].committed = committed;
        before[index].name = name;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setPoolAfter(int index, long used, long committed, String name) {
        after[index].used = used;
        after[index].committed = committed;
        after[index].name = name;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public PoolMemoryUsage getPoolBefore(int i) {
        return before[i];
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public PoolMemoryUsage getPoolAfter(int i) {
        return after[i];
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void copyTo(GcNotificationRequest other) {
        other.startTime = startTime;
        other.epoch = epoch;
        other.endTime = endTime;
        other.cause = cause;
        other.timestamp = timestamp;
        other.isIncremental = isIncremental;

        for (int index = 0; index < before.length; index++) {
            other.setPoolBefore(index, before[index].used, before[index].committed, before[index].name);
        }

        for (int index = 0; index < after.length; index++) {
            other.setPoolAfter(index, after[index].used, after[index].committed, after[index].name);
        }
    }

}
