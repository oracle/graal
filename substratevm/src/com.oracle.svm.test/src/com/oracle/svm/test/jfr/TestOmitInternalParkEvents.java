/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2025, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.util.TimeUtils;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;

public class TestOmitInternalParkEvents extends JfrRecordingTest {
    private static final int MILLIS = 100;
    private final MonitorHelper monitorHelper = new MonitorHelper();
    private Thread secondThread;
    private volatile boolean passedCheckpoint;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorEnter.getName(), JfrEvent.JavaMonitorWait.getName(), JfrEvent.ThreadPark.getName()};
        Recording recording = startRecording(events);

        /* Generate monitor enter events. */
        Thread firstThread = new Thread(() -> {
            try {
                monitorHelper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        secondThread = new Thread(() -> {
            try {
                passedCheckpoint = true;
                monitorHelper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        /* Start the first thread so that it can then start the second thread. */
        firstThread.start();

        firstThread.join();
        secondThread.join();

        /* Generate monitor wait events. */
        try {
            monitorHelper.waitUntilTimeout();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        /* Generate thread park events. */
        LockSupport.parkNanos(this, TimeUtils.millisToNanos(MILLIS));

        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        boolean found = false;
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(JfrEvent.ThreadPark.getName())) {
                RecordedClass parkedClass = event.getValue("parkedClass");
                if (parkedClass != null) {
                    String parkedClassName = parkedClass.getName();
                    assertFalse(parkedClassName.contains("JavaMonitor"));
                    found = true;
                }
            }
        }
        assertTrue("Expected jdk.ThreadPark event not found", found);
    }

    private final class MonitorHelper {
        private synchronized void doWork() throws InterruptedException {
            if (Thread.currentThread().equals(secondThread)) {
                /* The second thread doesn't need to do any work. */
                return;
            }

            /* Start the second thread while this thread holds the lock. */
            secondThread.start();

            /* Spin until the second thread blocks. */
            while (!secondThread.getState().equals(Thread.State.BLOCKED) || !passedCheckpoint) {
                Thread.sleep(100);
            }
            Thread.sleep(MILLIS);
        }

        private synchronized void waitUntilTimeout() throws InterruptedException {
            wait(MILLIS);
        }
    }
}
