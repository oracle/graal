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

package com.oracle.svm.core.notification;

import com.oracle.svm.core.gc.AbstractMemoryPoolMXBean;
import com.oracle.svm.core.gc.MemoryPoolMXBeansProvider;
import com.oracle.svm.core.heap.GCCause;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * This class stashes GC info during a safepoint until it can be dequeued to emit a notification at
 * a point in the future.
 */
public class GcNotificationRequest {
    private final PoolMemoryUsage[] before;
    private final PoolMemoryUsage[] after;

    // Times since the VM started.
    private long startTime;
    private long endTime;

    // This is the system time that the request was sent.
    private long timestamp;
    private boolean isIncremental;
    private GCCause cause;
    private long epoch;

    @Platforms(Platform.HOSTED_ONLY.class)
    GcNotificationRequest(int poolCount) {
        before = new PoolMemoryUsage[poolCount];
        after = new PoolMemoryUsage[poolCount];
        for (int i = 0; i < poolCount; i++) {
            before[i] = new PoolMemoryUsage();
            after[i] = new PoolMemoryUsage();
        }
    }

    public static void beforeCollection(long startTime, GcNotificationRequest[] requests, int index) {
        AbstractMemoryPoolMXBean[] beans = MemoryPoolMXBeansProvider.get().getMXBeans();
        for (int i = 0; i < beans.length; i++) {
            /*
             * Always add the old generation pool even if we don't end up using it (since we don't
             * know what type of collection will happen yet)
             */
            requests[index].setPoolBefore(i, beans[i].getUsedBytes().rawValue(), beans[i].getCommittedBytes().rawValue(), beans[i].getName());
        }
        requests[index].startTime = startTime;
    }

    public static void afterCollection(boolean isIncremental, GCCause cause, long epoch, long endTime, GcNotificationRequest[] requests, int index) {
        AbstractMemoryPoolMXBean[] beans = MemoryPoolMXBeansProvider.get().getMXBeans();
        for (int i = 0; i < beans.length; i++) {
            requests[index].setPoolAfter(i, beans[i].getUsedBytes().rawValue(), beans[i].getCommittedBytes().rawValue(), beans[i].getName());
        }
        requests[index].endTime = endTime;
        requests[index].isIncremental = isIncremental;
        requests[index].cause = cause;
        requests[index].epoch = epoch;
        requests[index].timestamp = System.currentTimeMillis();

    }

    void setPoolBefore(int index, long used, long committed, String name) {
        before[index].set(used, committed, name);
    }

    void setPoolAfter(int index, long used, long committed, String name) {
        after[index].set(used, committed, name);
    }

    public PoolMemoryUsage getPoolBefore(int i) {
        return before[i];
    }

    public PoolMemoryUsage getPoolAfter(int i) {
        return after[i];
    }

    public boolean isIncremental() {
        return isIncremental;
    }

    public GCCause getCause() {
        return cause;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getEpoch() {
        return epoch;
    }
}
