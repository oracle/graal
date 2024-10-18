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

package com.oracle.svm.test.service;

import com.oracle.svm.core.gc.AbstractGarbageCollectorMXBean;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import org.junit.Test;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class GcNotificationTests {

    @Test
    public void testGetLastGcInfo() {
        System.gc();
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gcBean instanceof NotificationEmitter)) {
                continue;
            }
            GcInfo lastGcInfo = ((com.sun.management.GarbageCollectorMXBean) gcBean).getLastGcInfo();
            if (lastGcInfo != null) {
                assertTrue(lastGcInfo.getStartTime() > 0);
                assertTrue(lastGcInfo.getId() >= 0);
                assertTrue(lastGcInfo.getDuration() >= 0); // Precision is 1 ms.

                Set<String> poolNames = lastGcInfo.getMemoryUsageBeforeGc().keySet();
                for (String poolName : poolNames) {
                    long before = lastGcInfo.getMemoryUsageBeforeGc().get(poolName).getUsed();
                    long after = lastGcInfo.getMemoryUsageAfterGc().get(poolName).getUsed();
                    assertTrue(before >= 0);
                    assertTrue(after >= 0);
                }
            }
        }
    }

    @Test
    public void testListenerRegistration() {
        int count = 0;
        // Get the collector beans and register listeners.
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gcBean instanceof NotificationEmitter notificationEmitter)) {
                continue;
            }
            count++;
            notificationEmitter.addNotificationListener(
                            (notification, handback) -> {
                            },
                            notification -> notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION),
                            null);
            // Check listeners were registered correctly
            assertTrue(((AbstractGarbageCollectorMXBean) gcBean).hasListeners());
        }
        assertTrue("There should be some GCs that are notification emitters", count > 0);
    }

    @Test
    public void testNotificationData() {
        NotificationTestListener ntl = new NotificationTestListener();

        // Register notification listeners
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gcBean instanceof NotificationEmitter notificationEmitter)) {
                continue;
            }
            notificationEmitter.addNotificationListener(
                            ntl,
                            notification -> notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION),
                            null);
        }

        // Prompt a GC and check for notifications.
        synchronized (ntl) {
            System.gc();
            try {
                // Wait a reasonable maximum length of time.
                ntl.wait(30 * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertTrue(ntl.success);
        }
    }

    class NotificationTestListener implements javax.management.NotificationListener {
        public boolean success;

        @Override
        public void handleNotification(Notification notification, Object handback) {
            CompositeData cd = (CompositeData) notification.getUserData();
            GarbageCollectionNotificationInfo notificationInfo = GarbageCollectionNotificationInfo.from(cd);

            assertTrue(notificationInfo.getGcName() != null);

            assertTrue(notificationInfo.getGcAction().equals("end of minor GC") || notificationInfo.getGcAction().equals("end of major GC"));
            GcInfo gcInfo = notificationInfo.getGcInfo();
            assertTrue(gcInfo != null);
            assertTrue(gcInfo.getDuration() >= 0); // Precision is 1 ms.
            Set<String> poolNames = gcInfo.getMemoryUsageBeforeGc().keySet();
            for (String poolName : poolNames) {
                long before = gcInfo.getMemoryUsageBeforeGc().get(poolName).getUsed();
                long after = gcInfo.getMemoryUsageAfterGc().get(poolName).getUsed();
                assertTrue(before >= 0);
                assertTrue(after >= 0);
            }

            if (notificationInfo.getGcCause().equals("java.lang.System.gc()")) {
                signalFinished();
            }
        }

        private synchronized void signalFinished() {
            success = true;
            notify();
        }
    }
}
