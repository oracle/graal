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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.shared.util.ClassUtil;
import com.oracle.svm.test.jfr.events.StringEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestEmergencyDumpRecoveredOutOfMemory extends JfrEmergencyDumpTest {
    private static final String STRING_EVENT_NAME = "com.jfr.String";
    private static final String OUT_OF_MEMORY_REASON = "Out of Memory";
    private static final int ITERATIONS = 3;
    private static final String ACTUAL_OOM_WORKER_PROPERTY = "com.oracle.svm.test.jfr.actualOomWorker";
    private static final String ACTUAL_OOM_DUMP_DIR_PROPERTY = "com.oracle.svm.test.jfr.actualOomDumpDir";
    private static final String ACTUAL_OOM_MESSAGE_PROPERTY = "com.oracle.svm.test.jfr.actualOomMessage";
    private static final String ACTUAL_OOM_MAX_HEAP = "-Xmx512m";
    private static final int ACTUAL_OOM_TIMEOUT_SECONDS = 120;
    private static final int ACTUAL_OOM_PAYLOAD_COUNT = 4;
    private static final int ACTUAL_OOM_PAYLOAD_SIZE = 4 * 1024 * 1024;

    @Test
    public void testRecoveredOutOfMemoryCreatesIndependentEmergencyDumps() throws Throwable {
        if (isActualOomWorker()) {
            return;
        }
        if (!HasJfrSupport.get()) {
            return;
        }

        String[] events = new String[]{STRING_EVENT_NAME, JfrEvent.DumpReason.getName()};
        long pid = ProcessHandle.current().pid();
        Path rootDumpDir = Files.createTempDirectory(ClassUtil.getUnqualifiedName(getClass()) + "-");
        List<Path> dumpFiles = new ArrayList<>(ITERATIONS);
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                String message = "oom-iteration-" + i;
                Path dumpDir = Files.createDirectory(rootDumpDir.resolve("dump-" + i));
                SubstrateJVM.get().setDumpPath(dumpDir.toString());

                Recording recording = startRecording(events);
                emitStringEvent(message);

                try {
                    OutOfMemoryUtil.heapSizeExceeded();
                    fail("Expected OutOfMemoryError");
                } catch (OutOfMemoryError expected) {
                    // Expected. The process stays alive and should be able to record again.
                }

                recording.stop();
                recording.close();

                Path dumpFile = dumpDir.resolve("svm_oom_pid_" + pid + ".jfr");
                assertTrue("emergency dump file does not exist.", Files.exists(dumpFile));
                verifyDump(dumpFile, message);
                dumpFiles.add(dumpFile);
                assertNoResidualTestedEvents(events);
            }
            assertEquals(ITERATIONS, dumpFiles.size());
            for (Path dumpFile : dumpFiles) {
                assertTrue("expected emergency dump file to still exist: " + dumpFile, Files.exists(dumpFile));
            }
        } finally {
            deleteRecursively(rootDumpDir);
        }
    }

    @Test
    public void testActualOutOfMemoryCreatesEmergencyDump() throws Throwable {
        if (!HasJfrSupport.get()) {
            return;
        }
        if (isActualOomWorker()) {
            runActualOutOfMemoryWorker();
            return;
        }

        String message = "actual-oom";
        Path dumpDir = Files.createTempDirectory(ClassUtil.getUnqualifiedName(getClass()) + "-actual-oom-");
        try {
            WorkerResult worker = runActualOutOfMemoryWorkerProcess(dumpDir, message);
            assertEquals(worker.output(), 0, worker.exitCode());

            Path dumpFile = dumpDir.resolve("svm_oom_pid_" + worker.pid() + ".jfr");
            assertTrue("emergency dump file does not exist.", Files.exists(dumpFile));
            verifyDump(dumpFile, message);
        } finally {
            deleteRecursively(dumpDir);
        }
    }

    private static void emitStringEvent(String message) {
        StringEvent event = new StringEvent();
        event.message = message;
        event.commit();
    }

    private void runActualOutOfMemoryWorker() throws Throwable {
        /*
         * Hosted analysis can reach this helper while building the junit image. Bail out early on
         * configurations that do not initialize the runtime JFR singletons.
         */
        if (!HasJfrSupport.get()) {
            return;
        }
        String dumpDirProperty = System.getProperty(ACTUAL_OOM_DUMP_DIR_PROPERTY);
        String message = System.getProperty(ACTUAL_OOM_MESSAGE_PROPERTY);
        assertTrue("missing dump dir property for actual OOM worker", dumpDirProperty != null && !dumpDirProperty.isEmpty());
        assertTrue("missing message property for actual OOM worker", message != null && !message.isEmpty());

        String[] events = new String[]{STRING_EVENT_NAME, JfrEvent.DumpReason.getName()};
        Path dumpDir = Path.of(dumpDirProperty);
        SubstrateJVM.get().setDumpPath(dumpDir.toString());

        Recording recording = startRecording(events);
        emitStringEvent(message);
        try {
            exhaustHeapRecursively();
            fail("Expected OutOfMemoryError");
        } catch (OutOfMemoryError expected) {
            System.gc();
        }

        recording.stop();
        recording.close();

        Path dumpFile = dumpDir.resolve("svm_oom_pid_" + ProcessHandle.current().pid() + ".jfr");
        assertTrue("emergency dump file does not exist.", Files.exists(dumpFile));
        verifyDump(dumpFile, message);
        assertNoResidualTestedEvents(events);
    }

    private WorkerResult runActualOutOfMemoryWorkerProcess(Path dumpDir, String message) throws IOException, InterruptedException {
        String executable = ProcessHandle.current().info().command().orElseThrow();
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add(ACTUAL_OOM_MAX_HEAP);
        command.add("-D" + ACTUAL_OOM_WORKER_PROPERTY + "=true");
        command.add("-D" + ACTUAL_OOM_DUMP_DIR_PROPERTY + "=" + dumpDir);
        command.add("-D" + ACTUAL_OOM_MESSAGE_PROPERTY + "=" + message);
        command.add("--run-explicit");
        command.add(getClass().getName());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        long pid = process.pid();
        boolean finished = process.waitFor(ACTUAL_OOM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("timed out waiting for actual OOM worker subprocess");
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new WorkerResult(pid, process.exitValue(), output);
    }

    private static RecursivePayload exhaustHeapRecursively() {
        RecursivePayload payload = new RecursivePayload();
        payload.blocks = new byte[ACTUAL_OOM_PAYLOAD_COUNT][];
        for (int i = 0; i < payload.blocks.length; i++) {
            payload.blocks[i] = new byte[ACTUAL_OOM_PAYLOAD_SIZE];
        }
        payload.next = exhaustHeapRecursively();
        return payload;
    }

    private static void verifyDump(Path dumpFile, String expectedMessage) throws IOException {
        List<RecordedEvent> events = getEvents(dumpFile, new String[]{STRING_EVENT_NAME, JfrEvent.DumpReason.getName()}, true);
        assertEquals(2, events.size());

        boolean foundString = false;
        boolean foundDumpReason = false;
        for (RecordedEvent event : events) {
            String eventName = event.getEventType().getName();
            if (STRING_EVENT_NAME.equals(eventName)) {
                assertEquals(expectedMessage, event.getString("message"));
                foundString = true;
            } else if (JfrEvent.DumpReason.getName().equals(eventName)) {
                assertEquals(OUT_OF_MEMORY_REASON, event.getString("reason"));
                assertEquals(-1, event.getInt("recordingId"));
                foundDumpReason = true;
            }
        }

        assertTrue("Expected StringEvent not found in emergency dump", foundString);
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

    private static boolean isActualOomWorker() {
        return Boolean.getBoolean(ACTUAL_OOM_WORKER_PROPERTY);
    }

    private static final class RecursivePayload {
        private byte[][] blocks;
        // Keep the recursive allocation chain reachable until the OOM is thrown.
        @SuppressWarnings("unused") private RecursivePayload next;
    }

    private record WorkerResult(long pid, int exitCode, String output) {
    }
}
