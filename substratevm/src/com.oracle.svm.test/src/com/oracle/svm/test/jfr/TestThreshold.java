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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.test.jfr.events.StringEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestThreshold extends JfrRecordingTest {
    private static final long SHORT_MILLIS = 1;
    private static final long THRESHOLD = 10;
    private static final long LONG_MILLIS = 50;

    private final Helper helper = new Helper();

    @Test
    public void test() throws Throwable {
        /* Set thresholds before recording is started to avoid recording shorter events. */
        String[] events = new String[]{JfrEvent.JavaMonitorWait.getName(), JfrEvent.ThreadPark.getName(), "com.jfr.String", "jdk.ThreadSleep"};
        Recording recording = prepareRecording(events, getDefaultConfiguration(), null, createTempJfrFile());
        recording.enable(JfrEvent.JavaMonitorWait.getName()).withThreshold(Duration.ofMillis(THRESHOLD));
        recording.enable(JfrEvent.ThreadPark.getName()).withThreshold(Duration.ofMillis(THRESHOLD));
        recording.enable("jdk.ThreadSleep").withThreshold(Duration.ofMillis(THRESHOLD));
        recording.enable("com.jfr.String").withThreshold(Duration.ofMillis(THRESHOLD));
        recording.start();

        StringEvent shortStringEvent = new StringEvent();
        shortStringEvent.begin();
        shortStringEvent.commit();

        StringEvent longStringEvent = new StringEvent();
        longStringEvent.begin();
        Thread.sleep(SHORT_MILLIS);
        Thread.sleep(LONG_MILLIS);
        longStringEvent.commit();

        LockSupport.parkNanos(helper, SHORT_MILLIS * 1000000);
        LockSupport.parkNanos(helper, LONG_MILLIS * 1000000);

        helper.simpleWait(SHORT_MILLIS);
        helper.simpleWait(LONG_MILLIS);

        stopRecording(recording, TestThreshold::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        /*
         * We can't validate the number of events because short events can always take longer than
         * the threshold. However, we can validate that no short events were recorded.
         */
        boolean foundWaitEvent = false;
        boolean foundJavaEvent = false;
        boolean foundParkEvent = false;
        boolean foundSleepEvent = false;
        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            assertNotNull("No event thread", eventThread);
            assertTrue(event.getDuration().toMillis() >= THRESHOLD);

            if (event.getEventType().getName().equals("com.jfr.String")) {
                foundJavaEvent = true;
            } else if (event.getEventType().getName().equals(JfrEvent.JavaMonitorWait.getName()) && event.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName())) {
                foundWaitEvent = true;
            } else if (event.getEventType().getName().equals(JfrEvent.ThreadPark.getName()) && event.<RecordedClass> getValue("parkedClass").getName().equals(Helper.class.getName())) {
                foundParkEvent = true;
            } else if (event.getEventType().getName().equals("jdk.ThreadSleep")) {
                foundSleepEvent = true;
            }
        }
        assertTrue(foundJavaEvent && foundWaitEvent && foundParkEvent && foundSleepEvent);
    }

    private static final class Helper {
        public synchronized void simpleWait(long millis) throws InterruptedException {
            wait(millis);
        }
    }
}
