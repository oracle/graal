/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, Red Hat Inc. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestEmergencyDumpMetadataOnly extends JfrEmergencyDumpTest {
    private static final String OUT_OF_MEMORY_REASON = "Out of Memory";

    @Test
    public void test() throws Throwable {
        if (!HasJfrSupport.get() || !JfrEmergencyDumpSupport.isPresent()) {
            /* Prevent that the code below is reachable on platforms that don't support JFR. */
            return;
        }

        String[] events = new String[]{JfrEvent.DumpReason.getName()};
        Path dumpFile = getEmergencyDumpFile();
        Files.deleteIfExists(dumpFile);

        Recording recording = null;
        try {
            recording = startRecording(events);
            SubstrateJVM.get().dumpOnOutOfMemoryError();
            recording.stop();

            assertTrue("emergency dump file does not exist.", Files.exists(dumpFile));

            List<RecordedEvent> dumpedEvents = getEvents(dumpFile, events, true);
            assertEquals(1, dumpedEvents.size());

            RecordedEvent dumpReason = dumpedEvents.get(0);
            assertEquals(JfrEvent.DumpReason.getName(), dumpReason.getEventType().getName());
            assertEquals(OUT_OF_MEMORY_REASON, dumpReason.getString("reason"));
            assertEquals(-1, dumpReason.getInt("recordingId"));
        } finally {
            try {
                if (recording != null) {
                    recording.close();
                }
            } finally {
                Files.deleteIfExists(dumpFile);
                assertNoResidualTestedEvents(events);
            }
        }
    }

    private static Path getEmergencyDumpFile() {
        return Path.of("svm_oom_pid_" + ProcessHandle.current().pid() + ".jfr");
    }
}
