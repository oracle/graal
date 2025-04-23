/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.monitor.MonitorInflationCause;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorInflateEvent extends JfrRecordingTest {
    private final EnterHelper enterHelper = new EnterHelper();
    private Thread firstThread;
    private Thread secondThread;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorInflate.getName()};
        Recording recording = startRecording(events);

        Runnable first = () -> {
            try {
                enterHelper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable second = () -> {
            try {
                enterHelper.passedCheckpoint = true;
                enterHelper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        firstThread = new Thread(first);
        secondThread = new Thread(second);

        /* Generate event with "Monitor Enter" cause. */
        firstThread.start();

        firstThread.join();
        secondThread.join();

        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        boolean foundCauseEnter = false;
        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            String monitorClass = event.<RecordedClass> getValue("monitorClass").getName();
            String cause = event.getValue("cause");
            if (monitorClass.equals(EnterHelper.class.getName()) &&
                            cause.equals(MonitorInflationCause.MONITOR_ENTER.getText()) &&
                            (eventThread.equals(firstThread.getName()) || eventThread.equals(secondThread.getName()))) {
                foundCauseEnter = true;
            }
        }
        assertTrue("Expected monitor inflate event not found.", foundCauseEnter);
    }

    private final class EnterHelper {
        volatile boolean passedCheckpoint = false;

        synchronized void doWork() throws InterruptedException {
            if (Thread.currentThread().equals(secondThread)) {
                /*
                 * The second thread only needs to enter the critical section but doesn't need to do
                 * work.
                 */
                return;
            }
            // ensure ordering of critical section entry
            secondThread.start();

            // spin until second thread blocks
            while (!secondThread.getState().equals(Thread.State.BLOCKED) || !passedCheckpoint) {
                Thread.sleep(10);
            }
            Thread.sleep(60);
        }
    }
}
