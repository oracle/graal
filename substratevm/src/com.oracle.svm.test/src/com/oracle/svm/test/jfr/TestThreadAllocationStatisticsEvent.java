/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestThreadAllocationStatisticsEvent extends JfrRecordingTest {
    private static final int ALLOCATION_SIZE = 128 * 1024;
    private static final String THREAD_NAME_1 = "HeapAllocationThread-1";

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.ThreadAllocationStatistics.getName()};
        Recording recording = startRecording(events);

        /* Start a thread and wait until it allocated some memory. */
        HeapAllocationThread thread = new HeapAllocationThread(THREAD_NAME_1);
        thread.start();
        waitUntilTrue(() -> thread.getAllocationCount() > 0);

        /* Force chunk rotation so that ThreadAllocationStatistics are emitted for all threads. */
        recording.dump(createTempJfrFile());

        /* Final chunk rotation also emits ThreadAllocationStatistics for all threads. */
        stopRecording(recording, TestThreadAllocationStatisticsEvent::validateEvents);

        assertTrue(thread.isAlive());
        thread.interrupt();
    }

    private static void validateEvents(List<RecordedEvent> events) {
        int found = 0;
        for (RecordedEvent e : events) {
            if (e.getThread("thread").getJavaName().equals(THREAD_NAME_1) && e.<Long> getValue("allocated") >= ALLOCATION_SIZE) {
                found++;
            }
        }
        assertTrue(found >= 2);
    }

    private static class HeapAllocationThread extends Thread {
        private final AtomicInteger allocationCount = new AtomicInteger();

        HeapAllocationThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    allocateByteArray(ALLOCATION_SIZE);
                    allocationCount.incrementAndGet();
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                /* Normal way how this thread exits. */
            }
        }

        public int getAllocationCount() {
            return allocationCount.get();
        }

        @NeverInline("Prevent escape analysis.")
        private static byte[] allocateByteArray(int length) {
            return new byte[length];
        }
    }
}
