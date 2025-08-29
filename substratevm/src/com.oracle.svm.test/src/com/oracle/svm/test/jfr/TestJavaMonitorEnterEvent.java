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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorEnterEvent extends JfrRecordingTest {
    private static final int MILLIS = 60;

    private final Helper helper = new Helper();
    private Thread firstThread;
    private Thread secondThread;
    private volatile boolean passedCheckpoint;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorEnter.getName()};
        Recording recording = startRecording(events);

        firstThread = new Thread(() -> {
            try {
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        secondThread = new Thread(() -> {
            try {
                passedCheckpoint = true;
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        /* Start the first thread so that it can then start the second thread. */
        firstThread.start();

        firstThread.join();
        secondThread.join();

        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        boolean found = false;
        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            if (event.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName()) && event.getDuration().toMillis() >= MILLIS && secondThread.getName().equals(eventThread)) {
                // verify previous owner
                assertEquals("Previous owner is wrong", event.<RecordedThread> getValue("previousOwner").getJavaName(), firstThread.getName());
                found = true;
                break;
            }

            checkTopStackFrame(event, "monitorEnter");
        }
        assertTrue("Expected monitor blocked event not found", found);
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
                Thread.sleep(10);
            }
            Thread.sleep(MILLIS);
        }
    }
}
