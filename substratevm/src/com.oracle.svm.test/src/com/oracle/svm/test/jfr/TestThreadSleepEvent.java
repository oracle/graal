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

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestThreadSleepEvent extends JfrRecordingTest {
    private static final int MILLIS = 50;

    private Thread sleepingThread;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"jdk.ThreadSleep"};
        Recording recording = startRecording(events);

        sleepingThread = Thread.currentThread();
        Thread.sleep(MILLIS);

        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        boolean foundSleepEvent = false;
        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            if (!eventThread.equals(sleepingThread.getName())) {
                continue;
            }
            if (event.getDuration().toMillis() < MILLIS) {
                continue;
            }
            foundSleepEvent = true;
            break;
        }
        assertTrue("Sleep event not found.", foundSleepEvent);
    }
}
