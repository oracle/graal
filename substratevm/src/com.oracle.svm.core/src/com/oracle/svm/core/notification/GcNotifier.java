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

import com.oracle.svm.core.collections.CircularQueue;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.gc.AbstractGarbageCollectorMXBean;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.Uninterruptible;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

import jdk.graal.compiler.api.replacements.Fold;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform;

/**
 * This class keeps a circular queue of requests to later use when emitting GC notifications. The
 * requests are pre-allocated so that they can be used during a GC.
 *
 * This class is responsible for enqueuing requests during GC safepoints and later dequeuing
 * requests to generate notifications when the notification thread handles signals outside of
 * safepoints.
 */
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/hotspot/share/services/gcNotifier.cpp") //
public class GcNotifier {
    // 5 is probably more than enough, although making the queue larger isn't a problem either.
    private static final int QUEUE_SIZE = 3;
    private final CircularQueue<GcNotificationRequest> requestQueue;
    // This is the request we are emitting a notification for
    GcNotificationRequest currentRequest;
    /** This is cached to handle {@link GarbageCollectorMXBean#getLastGcInfo()} . */
    GcNotificationRequest latestRequestComplete;
    /** This is cached to handle {@link GarbageCollectorMXBean#getLastGcInfo()} . */
    GcNotificationRequest latestRequestIncremental;

    @Platforms(Platform.HOSTED_ONLY.class)
    GcNotifier() {
        // Pre-allocate the queue so we can use it later from allocation free code.
        requestQueue = new CircularQueue<>(QUEUE_SIZE, GcNotificationRequest::new);
        currentRequest = new GcNotificationRequest();
        latestRequestComplete = new GcNotificationRequest();
        latestRequestIncremental = new GcNotificationRequest();
    }

    @Fold
    public static GcNotifier singleton() {
        return ImageSingletons.lookup(GcNotifier.class);
    }

    /** Called during GC. */
    public void beforeCollection(long startTime) {
        assert VMOperation.isInProgressAtSafepoint();
        requestQueue.peekTail().beforeCollection(startTime);
    }

    /** Called during GC. */
    public void afterCollection(boolean isIncremental, GCCause cause, long epoch, long endTime) {
        assert VMOperation.isInProgressAtSafepoint();
        requestQueue.peekTail().afterCollection(isIncremental, cause, epoch, endTime);

        if (isIncremental) {
            requestQueue.peekTail().copyTo(latestRequestIncremental);
        } else {
            requestQueue.peekTail().copyTo(latestRequestComplete);
        }

        requestQueue.advance();
        ImageSingletons.lookup(NotificationThreadSupport.class).signalNotificationThread();
    }

    @Uninterruptible(reason = "Avoid pausing for a GC which could cause races.")
    public boolean hasRequest() {
        return !requestQueue.isEmpty();
    }

    /**
     * Called by the notification thread. Sends a notification if any are queued. If none are
     * queued, then return immediately.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/hotspot/share/services/gcNotifier.cpp#L191-L225") //
    void sendNotification() {
        assert !VMOperation.isInProgressAtSafepoint();

        if (!updateCurrentRequest()) {
            // No new requests.
            return;
        }

        GarbageCollectorMXBean gcBean = getGarbageCollectorMXBean(currentRequest.isIncremental());

        ((AbstractGarbageCollectorMXBean) gcBean).createNotification(currentRequest);
    }

    private GarbageCollectorMXBean getGarbageCollectorMXBean(boolean isIncremental) {
        GarbageCollectorMXBean gcBean = null;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (((AbstractGarbageCollectorMXBean) bean).isIncremental() == isIncremental) {
                gcBean = bean;
                break;
            }
        }
        assert (gcBean != null);
        return gcBean;
    }

    /** Called by the notification thread. */
    @Uninterruptible(reason = "Avoid pausing for a GC safepoint which could cause races with pushes to the request queue.")
    private boolean updateCurrentRequest() {
        // If there's nothing to read, return immediately.
        if (!hasRequest()) {
            return false;
        }
        // Move the data to avoid data being overwritten by new writes to the queue.
        currentRequest = requestQueue.replaceHead(currentRequest);
        requestQueue.advanceHead();
        return true;
    }

    /** Called by notification thread. */
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public GcNotificationRequest getLatestRequest(boolean isIncremental) {
        if (isIncremental) {
            return latestRequestIncremental;
        }
        return latestRequestComplete;
    }
}
