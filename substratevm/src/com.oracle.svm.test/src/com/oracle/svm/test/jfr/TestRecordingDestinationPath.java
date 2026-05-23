/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.Test;

import com.oracle.svm.test.jfr.events.ClassEvent;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestRecordingDestinationPath extends JfrRecordingTest {
    private static final String CLASS_EVENT_NAME = "com.jfr.Class";
    private static final String NON_ASCII_PATH_PART = "Gr\u00fc\u00dfe_\u4f60\u597d";

    @Test
    public void testNonAsciiDestinationPath() throws Throwable {
        String[] events = new String[]{CLASS_EVENT_NAME};
        Path path = createNonAsciiJfrPath("destination");
        try {
            Recording recording = startRecording(events, getDefaultConfiguration(), null, path);
            emitClassEvent();

            stopRecording(recording, TestRecordingDestinationPath::validateEvents);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testNonAsciiDumpPath() throws Throwable {
        String[] events = new String[]{CLASS_EVENT_NAME};
        Path path = createNonAsciiJfrPath("dump");
        Recording recording = null;
        try {
            recording = createInMemoryRecording(events);
            emitClassEvent();

            recording.dump(path);
            checkRecording(TestRecordingDestinationPath::validateEvents, path, new JfrRecordingState(events), true);
        } finally {
            if (recording != null) {
                recording.close();
            }
            Files.deleteIfExists(path);
        }
    }

    private static Path createNonAsciiJfrPath(String prefix) throws IOException {
        return Files.createTempFile("TestRecordingDestinationPath_" + prefix + "_" + NON_ASCII_PATH_PART + "_", ".jfr");
    }

    private static Recording createInMemoryRecording(String[] events) throws Throwable {
        Configuration config = getDefaultConfiguration();
        Recording recording = config == null ? new Recording() : new Recording(config);
        for (String event : events) {
            recording.enable(event).withThreshold(Duration.ZERO);
        }
        recording.start();
        return recording;
    }

    private static void emitClassEvent() {
        ClassEvent event = new ClassEvent();
        event.clazz = TestRecordingDestinationPath.class;
        event.commit();
    }

    private static void validateEvents(List<RecordedEvent> events) {
        assertEquals(1, events.size());
    }
}
