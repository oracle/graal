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

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.Uninterruptible;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * This class is the dedicated thread that handles services. For now, the only notification is GC
 * notifications.
 */
public class NotificationThread extends Thread {
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/hotspot/share/runtime/notificationThread.cpp#L41") //
    private static final String THREAD_NAME = "Notification Thread";
    private final VMMutex mutex;
    private final VMCondition condition;
    private volatile boolean stopped;

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("this-escape")
    public NotificationThread() {
        // Hotspot sets the notification thread to the system thread group.
        super(PlatformThreads.singleton().systemGroup, THREAD_NAME);
        this.mutex = new VMMutex("serviceThread");
        this.condition = new VMCondition(mutex);
        setDaemon(true);
    }

    /** Awakens to send notifications asynchronously. */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+19/src/hotspot/share/runtime/notificationThread.cpp#L52-L91") //
    @Override
    public void run() {
        while (!stopped) {
            if (!hasRequests()) {
                await();
            }
            if (HasGcNotificationSupport.get()) {
                GcNotifier.singleton().sendNotification();
            }
            // In the future, we may want to do other things here too.
        }
    }

    private void await() {
        mutex.lock();
        try {
            while (!hasRequests() && !stopped) {
                /*
                 * It's possible for a signal to get lost here if a GC occurs after we check
                 * hasRequests() but before we block. So wait a maximum of 3s as a precaution.
                 */
                condition.block(TimeUtils.millisToNanos(3000));
            }
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean hasRequests() {
        // In the future, we may check for other pending requests here too.
        return GcNotifier.singleton().hasRequest();
    }

    public void signal() {
        condition.signal();
    }

    public void shutdown() {
        this.stopped = true;
        this.signal();
        // Wait until the NotificationThread finishes.
        try {
            this.join();
        } catch (InterruptedException e) {
            Log.log().string("Service thread could not shutdown correctly.");
        }
    }
}
