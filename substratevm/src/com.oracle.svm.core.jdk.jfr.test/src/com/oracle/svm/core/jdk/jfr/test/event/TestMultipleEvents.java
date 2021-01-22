/*
 * Copyright (c) 2020, 2021, Red Hat, Inc.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This file is part of the Red Hat GraalVM Testing Suite (the suite).
 *
 * The suite is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.oracle.svm.core.jdk.jfr.test.event;

import com.oracle.svm.core.jdk.jfr.test.utils.events.StringEvent;
import com.oracle.svm.core.jdk.jfr.test.utils.JFR;
import com.oracle.svm.core.jdk.jfr.test.utils.LocalJFR;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TestMultipleEvents {

    @Test
    public void test() throws Exception {
        long s0 = System.currentTimeMillis();
        JFR jfr = new LocalJFR();
        Recording recording = jfr.startRecording("TestMultipleEvents");

        int count = 1024 * 1024;


        for (int i = 0; i < count; i++) {
            StringEvent event = new StringEvent();
            event.message = "Event has been generated!";
            event.commit();
        }

        jfr.endRecording(recording);

        try (RecordingFile recordingFile = new RecordingFile(recording.getDestination())) {
            long numEvents = 0;
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent recordedEvent = recordingFile.readEvent();
                if ("com.oracle.svm.core.jdk.jfr.test.utils.events.StringEvent".equals(recordedEvent.getEventType().getName())) {
                    numEvents++;
                    assertEquals("Event has been generated!", recordedEvent.getValue("message"));
                }
            }
            assertEquals(count, numEvents);
        } finally {
            jfr.cleanupRecording(recording);
        }
    }
}
