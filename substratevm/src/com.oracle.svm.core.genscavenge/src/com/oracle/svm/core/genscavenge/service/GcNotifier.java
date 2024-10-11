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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform;
import com.oracle.svm.core.Uninterruptible;
import jdk.graal.compiler.api.replacements.Fold;
import com.oracle.svm.core.genscavenge.AbstractMemoryPoolMXBean;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.genscavenge.GenScavengeMemoryPoolMXBeans;
import com.oracle.svm.core.genscavenge.AbstractGarbageCollectorMXBean;
import com.oracle.svm.core.thread.VMOperation;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

import static com.oracle.svm.core.genscavenge.GenScavengeMemoryPoolMXBeans.COMPLETE_SCAVENGER;
import static com.oracle.svm.core.genscavenge.GenScavengeMemoryPoolMXBeans.OLD_GEN_SPACE;
import static com.oracle.svm.core.genscavenge.GenScavengeMemoryPoolMXBeans.YOUNG_GEN_SCAVENGER;

/**
 * This class keeps a circular queue of requests to later use when emitting GC notifications. The
 * requests are pre-allocated so that they can be used during a GC.
 *
 * This class is responsible for enqueuing requests during GC safepoints and later dequeuing
 * requests to generate notifications when the service thread handles signals outside of safepoints.
 */
public class GcNotifier {
    // 5 is probably more than enough, although making the queue larger isn't a problem either.
    private static final int QUEUE_SIZE = 5;
    private static int rear;
    private static int front;

    GcNotificationRequest[] requests;
    GcNotificationRequest currentRequest;

    @Platforms(Platform.HOSTED_ONLY.class)
    GcNotifier() {
        // Pre-allocate the queue so we can use it later from allocation free code.
        requests = new GcNotificationRequest[QUEUE_SIZE];
        for (int i = 0; i < QUEUE_SIZE; i++) {
            requests[i] = new GcNotificationRequest(GenScavengeMemoryPoolMXBeans.singleton().getMXBeans().length);
        }
        currentRequest = new GcNotificationRequest(GenScavengeMemoryPoolMXBeans.singleton().getMXBeans().length);
        rear = 0;
        front = 0;
    }

    @Fold
    public static GcNotifier singleton() {
        return ImageSingletons.lookup(GcNotifier.class);
    }

    /** Called during GC. */
    public void beforeCollection(long startTime) {
        assert VMOperation.isInProgressAtSafepoint();

        AbstractMemoryPoolMXBean[] beans = GenScavengeMemoryPoolMXBeans.singleton().getMXBeans();
        for (int i = 0; i < beans.length; i++) {
            /*
             * Always add the old generation pool even if we don't end up using it (since we don't
             * know what type of collection will happen yet)
             */
            requests[rear].setPoolBefore(i, beans[i].getUsedBytes().rawValue(), beans[i].getCommittedBytes().rawValue(), beans[i].getName());
        }
        requests[rear].startTime = startTime;
    }

    /** Called during GC. */
    public void afterCollection(boolean isIncremental, GCCause cause, long epoch, long endTime) {
        assert VMOperation.isInProgressAtSafepoint();

        AbstractMemoryPoolMXBean[] beans = GenScavengeMemoryPoolMXBeans.singleton().getMXBeans();
        for (int i = 0; i < beans.length; i++) {
            if (isIncremental && beans[i].getName().equals(OLD_GEN_SPACE)) {
                continue;
            }
            requests[rear].setPoolAfter(i, beans[i].getUsedBytes().rawValue(), beans[i].getCommittedBytes().rawValue(), beans[i].getName());
        }
        requests[rear].endTime = endTime;
        requests[rear].isIncremental = isIncremental;
        requests[rear].cause = cause;
        requests[rear].epoch = epoch;
        requests[rear].timestamp = System.currentTimeMillis();

        rear = incremented(rear);

        // If rear is now lapping front, then increment front to prevent overlap. Accept data loss.
        if (front == rear) {
            front = incremented(front);
        }
        org.graalvm.nativeimage.ImageSingletons.lookup(NotificationThreadSupport.class).signalServiceThread();
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int incremented(int value) {
        return (value + 1) % QUEUE_SIZE;
    }

    /**
     * Called by the service thread. Sends a notification if any are queued. If none are queued,
     * then return immediately.
     */
    public void sendNotification() {
        assert !VMOperation.isInProgressAtSafepoint();

        /*
         * We don't need to drain all requests since signals queue at the semaphore level. The rest
         * of the requests will be handled in subsequent calls to this method by the sevice thread.
         */
        if (!updateCurrentRequest()) {
            // No new requests.
            return;
        }

        /*
         * Iterate to figure out which gc bean to send notifications too. Match with name in
         * NotificationRequest class that was dequeued.
         */
        GarbageCollectorMXBean gcBean = null;
        String targetBeanName = currentRequest.isIncremental ? YOUNG_GEN_SCAVENGER : COMPLETE_SCAVENGER;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.getName().equals(targetBeanName)) {
                gcBean = bean;
                break;
            }
        }

        assert (gcBean != null);
        ((AbstractGarbageCollectorMXBean) gcBean).createNotification(currentRequest);
    }

    /** Called by the service thread. */
    @Uninterruptible(reason = "Avoid pausing for a GC safepoint with could cause races with pushes to the request queue.")
    private boolean updateCurrentRequest() {
        // If there's nothing to read, return immediately.
        if (front == rear) {
            return false;
        }
        // Copy the data to avoid data being overwritten by new writes to the queue.
        GcNotificationRequest temp = currentRequest;
        currentRequest = requests[front];
        requests[front] = temp;

        front = incremented(front);
        return true;
    }
}
