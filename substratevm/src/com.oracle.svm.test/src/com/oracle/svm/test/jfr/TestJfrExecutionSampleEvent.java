/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.util.VMError;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;

public class TestJfrExecutionSampleEvent extends JfrRecordingTest {
    @Override
    public String[] getTestedEvents() {
        return new String[]{JfrEvent.ExecutionSample.getName()};
    }

    @Override
    protected void validateEvents(List<RecordedEvent> events) throws Throwable {
        assertTrue(events.size() > 0);

        Set<Long> seenThreadIds = new HashSet<>();
        for (RecordedEvent event : events) {
            long sampledThreadId = event.<RecordedThread> getValue("sampledThread").getJavaThreadId();
            assertTrue(sampledThreadId > 0);
            seenThreadIds.add(sampledThreadId);

            RecordedStackTrace stackTrace = event.getStackTrace();
            assertNotNull(stackTrace);

            List<RecordedFrame> frames = stackTrace.getFrames();
            assertFalse(frames.isEmpty());

            for (RecordedFrame frame : frames) {
                assertNotNull(frame.getMethod());
                assertNotNull(frame.getMethod().getName());
                assertFalse(frame.getMethod().getName().isEmpty());
            }
        }

        assertTrue(seenThreadIds.size() > 1);
    }

    @Test
    public void test() throws Exception {
        Worker[] workers = new Worker[8];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Worker();
            workers[i].start();
        }

        for (int i = 0; i < workers.length; i++) {
            try {
                workers[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class Worker extends Thread {
        @Override
        public void run() {
            for (int i = 0; i < 1_000_000; i++) {
                allocateObject(i);
            }
        }

        @NeverInline("Prevent escape analysis.")
        private static Object allocateObject(int iteration) {
            int action = iteration % 3;
            switch (action) {
                case 0:
                    return new StringBuilder(0);
                case 1:
                    return new int[43];
                case 2:
                    return new Object[43];
                default:
                    throw VMError.shouldNotReachHere();
            }
        }
    }
}
