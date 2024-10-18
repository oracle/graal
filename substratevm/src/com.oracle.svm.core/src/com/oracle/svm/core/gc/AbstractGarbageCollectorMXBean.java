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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.BasedOnJDKFile;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.notification.GcNotifier;
import com.oracle.svm.core.notification.GcNotificationRequest;
import com.oracle.svm.core.notification.HasGcNotificationSupport;
import com.oracle.svm.core.notification.PoolMemoryUsage;
import com.oracle.svm.core.notification.Target_com_sun_management_GcInfo;
import com.oracle.svm.core.notification.Target_com_sun_management_internal_GcInfoBuilder;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GarbageCollectorMXBean;
import com.sun.management.GcInfo;
import com.sun.management.internal.GarbageCollectionNotifInfoCompositeData;
import sun.management.NotificationEmitterSupport;

import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.MemoryUsage;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/jdk.management/share/classes/com/sun/management/internal/GarbageCollectorExtImpl.java") //
public abstract class AbstractGarbageCollectorMXBean extends NotificationEmitterSupport
                implements GarbageCollectorMXBean, NotificationEmitter {

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/hotspot/share/gc/serial/serialHeap.cpp#L451") //
    private static final String ACTION_MINOR = "end of minor GC";

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/hotspot/share/gc/serial/serialHeap.cpp#L718") //
    private static final String ACTION_MAJOR = "end of major GC";

    private Target_com_sun_management_internal_GcInfoBuilder gcInfoBuilder;
    private long seqNumber = 0;

    /**
     * Use the data taken from the request queue to populate MemoryUsage. The notification thread
     * calls this method.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/jdk.management/share/classes/com/sun/management/internal/GarbageCollectorExtImpl.java#L93-L116") //
    public void createNotification(GcNotificationRequest request) {
        if (!hasListeners()) {
            return;
        }

        GcInfo gcInfo = getGcInfoFromRequest(request);

        Notification notif = new Notification(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION,
                        getObjectName(),
                        getNextSeqNumber(),
                        request.getTimestamp(),
                        getName());

        GarbageCollectionNotificationInfo info = new GarbageCollectionNotificationInfo(
                        getName(),
                        request.isIncremental() ? ACTION_MINOR : ACTION_MAJOR,
                        request.getCause().getName(),
                        gcInfo);

        CompositeData cd = GarbageCollectionNotifInfoCompositeData.toCompositeData(info);
        notif.setUserData(cd);
        sendNotification(notif);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/jdk.management/share/classes/com/sun/management/internal/GarbageCollectorExtImpl.java#L80-L86") //
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        if (!HasGcNotificationSupport.get()) {
            return new MBeanNotificationInfo[0];
        }
        return new MBeanNotificationInfo[]{
                        new MBeanNotificationInfo(
                                        new String[]{GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION},
                                        "javax.management.Notification",
                                        "GC Notification")
        };
    }

    @Override
    public GcInfo getLastGcInfo() {
        if (!HasGcNotificationSupport.get()) {
            return null;
        }
        GcNotificationRequest request = new GcNotificationRequest();
        getLastGcInfo(request);
        return getGcInfoFromRequest(request);
    }

    @Uninterruptible(reason = "Avoid potential races with GC.")
    private void getLastGcInfo(GcNotificationRequest request) {
        GcNotifier.singleton().getLatestRequest(isIncremental()).copyTo(request);
    }

    private GcInfo getGcInfoFromRequest(GcNotificationRequest request) {
        AbstractMemoryPoolMXBean[] beans = MemoryPoolMXBeansProvider.get().getMXBeans();

        String[] poolNames = getMemoryPoolNames();
        MemoryUsage[] before = new MemoryUsage[poolNames.length];
        MemoryUsage[] after = new MemoryUsage[poolNames.length];

        // Pools must be in the order of getMemoryPoolNames() to match GcInfoBuilder
        for (int i = 0; i < poolNames.length; i++) {
            for (int j = 0; j < beans.length; j++) {
                PoolMemoryUsage pmu = request.getPoolBefore(j);
                if (pmu.getName() != null && pmu.getName().equals(poolNames[i])) {
                    before[i] = beans[j].memoryUsage(pmu.getUsed(), pmu.getCommitted());
                    pmu = request.getPoolAfter(j);
                    after[i] = beans[j].memoryUsage(pmu.getUsed(), pmu.getCommitted());
                }
            }
        }

        Object[] extAttribute = new Object[1];
        extAttribute[0] = gcThreadCount();

        Target_com_sun_management_GcInfo targetGcInfo = new Target_com_sun_management_GcInfo(getGcInfoBuilder(), request.getEpoch(), request.getStartTime(), request.getEndTime(), before, after,
                        extAttribute);
        return SubstrateUtil.cast(targetGcInfo, GcInfo.class);
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

    protected abstract int gcThreadCount();

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract boolean isIncremental();
}
