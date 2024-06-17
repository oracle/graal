/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

import java.time.Duration;
import java.util.List;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestFlightRecorderEvents extends JfrRecordingTest {
    private static final long THRESHOLD = 12345678;
    private static final long MAX_SIZE = 33554432;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"jdk.ActiveRecording", "jdk.ActiveSetting"};
        /* Set properties before recording is started so we can accurately validate them later. */
        Recording recording = prepareRecording(events, getDefaultConfiguration(), null, createTempJfrFile());
        recording.enable(JfrEvent.ThreadPark.getName()).withThreshold(Duration.ofNanos(THRESHOLD));
        recording.setMaxSize(MAX_SIZE);
        recording.start();
        stopRecording(recording, TestFlightRecorderEvents::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        boolean foundActiveRecording = false;
        boolean foundActiveSetting = false;
        for (RecordedEvent e : events) {
            String name = e.getEventType().getName();
            if (name.equals("jdk.ActiveSetting") &&
                            e.getLong("id") == JfrEvent.ThreadPark.getId() &&
                            e.getString("name").equals("threshold") &&
                            e.getString("value").equals(THRESHOLD + " ns")) {
                foundActiveSetting = true;
            } else if (name.equals("jdk.ActiveRecording") &&
                            e.getLong("maxSize") == MAX_SIZE) {
                foundActiveRecording = true;
            }
        }
        assertTrue(foundActiveSetting);
        assertTrue(foundActiveRecording);
    }
}
