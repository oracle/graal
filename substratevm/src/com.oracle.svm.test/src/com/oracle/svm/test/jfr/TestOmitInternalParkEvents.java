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

import com.oracle.svm.core.jfr.JfrEvent;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestOmitInternalParkEvents extends JfrRecordingTest {
    private static final int MILLIS = 500;
    private final Helper helper = new Helper();
    private Thread firstThread;
    private Thread secondThread;
    private volatile boolean passedCheckpoint;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorEnter.getName(), JfrEvent.JavaMonitorWait.getName(), JfrEvent.ThreadPark.getName()};
        Recording recording = startRecording(events);

        // Generate monitor enter events
        Runnable first = () -> {
            try {
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable second = () -> {
            try {
                passedCheckpoint = true;
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        firstThread = new Thread(first);
        secondThread = new Thread(second);

        firstThread.start();

        firstThread.join();
        secondThread.join();

        // Generate monitor wait events
        try {
            helper.timeout();
        } catch (InterruptedException e) {
            org.junit.Assert.fail(e.getMessage());
        }

        // Generate thread park events
        LockSupport.parkNanos(this, MILLIS);

        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        boolean found = false;
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(JfrEvent.ThreadPark.getName())) {
                RecordedClass parkedClass = event.getValue("parkedClass");
                if (parkedClass != null) {
                    String parkedClassName = parkedClass.getName();
                    assertFalse(parkedClassName.contains("com.oracle.svm.core.monitor"));
                    found = true;
                }
            }
        }
        assertTrue("Expected jdk.ThreadPark event not found", found);
    }

    private final class Helper {
        private synchronized void doWork() throws InterruptedException {
            if (Thread.currentThread().equals(secondThread)) {
                return; // second thread doesn't need to do work.
            }
            // ensure ordering of critical section entry
            secondThread.start();

            // spin until second thread blocks
            while (!secondThread.getState().equals(Thread.State.BLOCKED) || !passedCheckpoint) {
                Thread.sleep(100);
            }
            Thread.sleep(MILLIS);
        }

        private synchronized void timeout() throws InterruptedException {
            wait(MILLIS);
        }
    }
}
