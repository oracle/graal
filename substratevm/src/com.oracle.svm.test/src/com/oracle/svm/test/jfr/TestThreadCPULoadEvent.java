/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

/**
 * Test if event ThreadCPULoad is generated after a thread exit.
 */
public class TestThreadCPULoadEvent extends JfrRecordingTest {

    private static final int DELAY = 50;
    private static final String THREAD_NAME_1 = "Thread-1";
    private static final String THREAD_NAME_2 = "Thread-2";

    @Test
    public void test() throws Throwable {

        String[] events = new String[]{"jdk.ThreadCPULoad"};
        Recording recording = startRecording(events);

        Thread thread1 = createAndStartBusyWaitThread(THREAD_NAME_1, DELAY / 10);
        Thread thread2 = createAndStartBusyWaitThread(THREAD_NAME_2, DELAY);

        thread1.join();
        thread2.join();

        stopRecording(recording, TestThreadCPULoadEvent::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        assertEquals(2, events.size());
        Map<String, Float> systemTimes = new HashMap<>();

        for (RecordedEvent e : events) {
            float userTime = e.<Float>getValue("user");
            float systemTime = e.<Float>getValue("system");
            assertTrue("User time is outside 0..1 range", 0.0 <= userTime && userTime <= 1.0);
            assertTrue("System time is outside 0..1 range", 0.0 <= systemTime && systemTime <= 1.0);
            systemTimes.put(e.getThread().getJavaName(), systemTime);
        }

        assertTrue("Thread-1 system cpu time is greater than Thread-2 system cpu time",
                systemTimes.get(THREAD_NAME_1) < systemTimes.get(THREAD_NAME_2));
    }

    private static Thread createAndStartBusyWaitThread(String name, final int delay) {
        Thread thread = new Thread(() -> {
            busyWait(delay);
            sleep(DELAY - delay);
        });
        thread.setName(name);
        thread.start();
        return thread;
    }

    private static void busyWait(long delay) {
        long time = System.currentTimeMillis() + delay;
        do {
            writeToFile();
        } while (time > System.currentTimeMillis());
    }

    private static void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
        }
    }

    private static void writeToFile() {
        int fileSize = 1000;
        try {
            File temp = File.createTempFile("TestThreadCPULoadEventData_", ".tmp");
            temp.deleteOnExit();
            try (RandomAccessFile file = new RandomAccessFile(temp, "rw")) {
                for (int i = 0; i < fileSize; i++) {
                    file.writeByte(i);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
