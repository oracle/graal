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

import org.junit.Test;

import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This test checks that JFR mirror events have been set up correctly. If mirror events have not
 * been set up correctly, then mirrored events will have incorrect metadata. This test verifies that
 * metadata.
 */
public class TestMirrorEvents extends JfrRecordingTest {

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"jdk.ThreadSleep", "jdk.VirtualThreadStart", "jdk.VirtualThreadEnd"};
        Recording recording = startRecording(events);

        Runnable eventEmitter = () -> {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        VirtualStressor.execute(1, eventEmitter);

        stopRecording(recording, TestMirrorEvents::validateEvents);
    }

    /**
     * If the mirror event metadata is incorrect, we will see events with the wrong event name, e.g.
     * "jdk.internal.event.ThreadSleepEvent" instead of "jdk.ThreadSleep".
     */
    private static void validateEvents(List<RecordedEvent> unfilteredEvents) {
        boolean foundSleepEvent = false;
        boolean foundVthreadStartEvent = false;
        boolean foundVthreadEndEvent = false;

        for (RecordedEvent event : unfilteredEvents) {
            EventType eventType = event.getEventType();
            if (eventType.getName().equals("jdk.ThreadSleep") && eventType.getCategoryNames().contains("Java Application") && eventType.getLabel().equals("Java Thread Sleep")) {
                foundSleepEvent = true;
            }
            if (eventType.getName().equals("jdk.VirtualThreadStart") && eventType.getCategoryNames().contains("Java Application") && eventType.getLabel().equals("Virtual Thread Start")) {
                foundVthreadStartEvent = true;
            }
            if (eventType.getName().equals("jdk.VirtualThreadEnd") && eventType.getCategoryNames().contains("Java Application") && eventType.getLabel().equals("Virtual Thread End")) {
                foundVthreadEndEvent = true;
            }
        }

        assertTrue(foundSleepEvent);
        assertTrue(foundVthreadStartEvent);
        assertTrue(foundVthreadEndEvent);
    }
}
