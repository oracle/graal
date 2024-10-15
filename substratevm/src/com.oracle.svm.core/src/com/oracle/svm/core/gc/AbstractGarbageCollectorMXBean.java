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
package com.oracle.svm.core.gc;

import com.oracle.svm.core.util.BasedOnJDKFile;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.notification.GcNotificationRequest;
import com.oracle.svm.core.notification.PoolMemoryUsage;
import com.oracle.svm.core.notification.Target_com_sun_management_GcInfo;
import com.oracle.svm.core.notification.Target_com_sun_management_internal_GcInfoBuilder;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import com.sun.management.internal.GarbageCollectionNotifInfoCompositeData;
import sun.management.NotificationEmitterSupport;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.MemoryUsage;

public abstract class AbstractGarbageCollectorMXBean extends NotificationEmitterSupport
                implements com.sun.management.GarbageCollectorMXBean, NotificationEmitter {

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+14/src/hotspot/share/gc/serial/serialHeap.cpp#L451") //
    private static final String ACTION_MINOR = "end of minor GC";

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+14/src/hotspot/share/gc/serial/serialHeap.cpp#L718") //
    private static final String ACTION_MAJOR = "end of major GC";

    private Target_com_sun_management_internal_GcInfoBuilder gcInfoBuilder;
    private volatile GcInfo gcInfo;
    private long seqNumber = 0;

    /**
     * Use the data taken from the request queue to populate MemoryUsage. The notification thread
     * calls this method.
     */
    public void createNotification(GcNotificationRequest request) {
        AbstractMemoryPoolMXBean[] beans = MemoryPoolMXBeansProvider.get().getMXBeans();

        String[] poolNames = getMemoryPoolNames();
        MemoryUsage[] before = new MemoryUsage[poolNames.length];
        MemoryUsage[] after = new MemoryUsage[poolNames.length];

        // Pools must be in the order of getMemoryPoolNames() to match GcInfoBuilder
        for (int i = 0; i < poolNames.length; i++) {
            for (int j = 0; j < beans.length; j++) {
                PoolMemoryUsage pmu = request.getPoolBefore(j);
                if (pmu.name != null && pmu.name.equals(poolNames[i])) {
                    before[i] = beans[j].memoryUsage(pmu.used, pmu.committed);
                    pmu = request.getPoolAfter(j);
                    after[i] = beans[j].memoryUsage(pmu.used, pmu.committed);
                }
            }
        }

        Object[] extAttribute = new Object[1];
        extAttribute[0] = 1; // Number of GC threads.

        Target_com_sun_management_GcInfo targetGcInfo = new Target_com_sun_management_GcInfo(getGcInfoBuilder(), request.epoch, request.startTime, request.endTime, before, after, extAttribute);
        gcInfo = SubstrateUtil.cast(targetGcInfo, GcInfo.class);

        GarbageCollectionNotificationInfo info = new GarbageCollectionNotificationInfo(
                        getName(),
                        request.isIncremental ? ACTION_MINOR : ACTION_MAJOR,
                        request.cause.getName(),
                        gcInfo);

        CompositeData cd = GarbageCollectionNotifInfoCompositeData.toCompositeData(info);

        Notification notif = new Notification(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION,
                        getObjectName(),
                        getNextSeqNumber(),
                        request.timestamp,
                        getName());
        notif.setUserData(cd);

        sendNotification(notif);
    }

    private Target_com_sun_management_internal_GcInfoBuilder getGcInfoBuilder() {
        if (gcInfoBuilder == null) {
            gcInfoBuilder = new Target_com_sun_management_internal_GcInfoBuilder(this, getMemoryPoolNames());
        }
        return gcInfoBuilder;
    }

    private long getNextSeqNumber() {
        return ++seqNumber;
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[]{
                        new MBeanNotificationInfo(
                                        new String[]{GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION},
                                        "javax.management.Notification",
                                        "GC Notification")
        };
    }

    @Override
    public GcInfo getLastGcInfo() {
        return gcInfo;
    }
}
