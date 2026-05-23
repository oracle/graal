/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2025, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.test.jfr.events.StringEvent;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.oracle.svm.core.jfr.SubstrateJVM;

/**
 * This test commits events across multiple chunk files and ensure that the events all appear in the
 * emergency dump. This would indicate that the chunk files from the disk repository we merged
 * correctly along with in-flight data.
 */
public class TestEmergencyDump extends JfrEmergencyDumpTest {
    private static final String STRING_EVENT_NAME = "com.jfr.String";
    private static final String OUT_OF_MEMORY_REASON = "Out of Memory";

    @Test
    public void test() throws Throwable {
        if (!HasJfrSupport.get()) {
            /* Prevent that the code below is reachable on platforms that don't support JFR. */
            return;
        }

        String[] testedEvents = new String[]{STRING_EVENT_NAME, JfrEvent.DumpReason.getName()};
        Path dumpFile = Path.of("svm_oom_pid_" + ProcessHandle.current().pid() + ".jfr");
        try {
            runEmergencyDumpScenario(testedEvents);
            assertEmergencyDump(dumpFile, testedEvents, createExpectedStrings());
        } finally {
            Files.deleteIfExists(dumpFile);
            assertNoResidualTestedEvents(testedEvents);
        }
    }

    private void runEmergencyDumpScenario(String[] testedEvents) throws Throwable {
        Recording recording = startRecording(testedEvents);
        try {
            emitStringEvent("first");
            recording.dump(createTempJfrFile());

            emitStringEvent("second\0nul");
            recording.dump(createTempJfrFile());

            emitStringEvent("third \uD83D\uDE80");
            SubstrateJVM.get().dumpOnOutOfMemoryError();
        } finally {
            recording.stop();
            recording.close();
        }
    }

    private static void emitStringEvent(String message) {
        StringEvent event = new StringEvent();
        event.message = message;
        event.commit();
    }

    private static List<String> createExpectedStrings() {
        List<String> expectedStrings = new ArrayList<>();
        expectedStrings.add("first");
        expectedStrings.add("second\0nul");
        expectedStrings.add("third \uD83D\uDE80");
        return expectedStrings;
    }

    private static void assertEmergencyDump(Path dumpFile, String[] testedEvents, List<String> expectedStrings) throws IOException {
        assertTrue("emergency dump file does not exist.", Files.exists(dumpFile));
        List<RecordedEvent> events = getEvents(dumpFile, testedEvents, true);
        for (RecordedEvent event : events) {
            if (STRING_EVENT_NAME.equals(event.getEventType().getName())) {
                String message = event.getString("message");
                assertTrue("Unexpected or duplicate StringEvent in emergency dump: " + message, expectedStrings.remove(message));
            } else {
                assertEquals(JfrEvent.DumpReason.getName(), event.getEventType().getName());
                assertEquals(OUT_OF_MEMORY_REASON, event.getString("reason"));
                assertEquals(-1, event.getInt("recordingId"));
            }
        }
        assertEquals("Unexpected number of tested events in emergency dump", 4, events.size());
        assertEquals("Missing StringEvents from emergency dump", 0, expectedStrings.size());
    }
}
