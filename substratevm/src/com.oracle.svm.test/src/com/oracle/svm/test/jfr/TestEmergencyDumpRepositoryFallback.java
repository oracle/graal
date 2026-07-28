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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import org.junit.Test;

import com.oracle.svm.core.jfr.AbstractJfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.shared.util.ClassUtil;
import com.oracle.svm.test.jfr.events.StringEvent;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestEmergencyDumpRepositoryFallback extends JfrEmergencyDumpTest {
    private static final String STRING_EVENT_NAME = "com.jfr.String";
    private static final String OUT_OF_MEMORY_REASON = "Out of Memory";
    private static final String NON_ASCII_PATH_PART = "Gr\u00fc\u00dfe_\u4f60\u597d";

    @Test
    public void testRepositoryEmergencyChunkIsMergedIntoEmergencyDump() throws Throwable {
        AbstractJfrEmergencyDumpSupport support = getEmergencyDumpSupport();
        String tempDirectoryPrefix = ClassUtil.getUnqualifiedName(getClass()) + "-" + NON_ASCII_PATH_PART;
        Path repositoryDir = Files.createTempDirectory(tempDirectoryPrefix + "-repository-");
        Path dumpDir = Files.createTempDirectory(tempDirectoryPrefix + "-dump-");
        String[] events = new String[]{STRING_EVENT_NAME, JfrEvent.DumpReason.getName()};
        try {
            resetEmergencyChunkPathCallCount(support);
            SubstrateJVM.get().setRepositoryLocation(repositoryDir.toString());
            SubstrateJVM.get().setDumpPath(dumpDir.toString());

            Recording recording = null;
            try {
                recording = createInMemoryRecording(events);
                /*
                 * JFR may already have an active repository chunk open for the recording. Close it so
                 * dumpOnOutOfMemoryError() has to create the emergency repository chunk itself.
                 */
                SubstrateJVM.get().setOutput(null);
                emitStringEvent("repository-fallback");
                SubstrateJVM.get().dumpOnOutOfMemoryError();
                recording.stop();
            } finally {
                if (recording != null) {
                    recording.close();
                }
            }

            assertTrue("expected repository fallback emergency chunk path to be used",
                            getEmergencyChunkPathCallCount(support) > 0);

            Path dumpFile = dumpDir.resolve("svm_oom_pid_" + ProcessHandle.current().pid() + ".jfr");
            assertTrue("emergency dump file does not exist.", Files.exists(dumpFile));

            List<RecordedEvent> dumpedEvents = getEvents(dumpFile, events, true);
            assertEquals(2, dumpedEvents.size());
            assertDumpContents(dumpedEvents);
        } finally {
            deleteRecursively(repositoryDir);
            deleteRecursively(dumpDir);
        }
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

    private static void emitStringEvent(String message) {
        StringEvent event = new StringEvent();
        event.message = message;
        event.commit();
    }

    private static void assertDumpContents(List<RecordedEvent> dumpedEvents) {
        boolean foundStringEvent = false;
        boolean foundDumpReason = false;
        for (RecordedEvent event : dumpedEvents) {
            String eventName = event.getEventType().getName();
            if (STRING_EVENT_NAME.equals(eventName)) {
                assertEquals("repository-fallback", event.getString("message"));
                foundStringEvent = true;
            } else if (JfrEvent.DumpReason.getName().equals(eventName)) {
                assertEquals(OUT_OF_MEMORY_REASON, event.getString("reason"));
                assertEquals(-1, event.getInt("recordingId"));
                foundDumpReason = true;
            }
        }
        assertTrue("Expected StringEvent not found in emergency dump", foundStringEvent);
        assertTrue("Expected DumpReason event not found in emergency dump", foundDumpReason);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }
}
