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

package com.oracle.svm.test.attach;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.oracle.svm.core.attach.AttachApiSupport;
import com.oracle.svm.core.dcmd.HelpDcmd;
import com.oracle.svm.core.nmt.NmtDcmd;
import com.oracle.svm.core.jfr.dcmd.JfrCheckDcmd;
import com.oracle.svm.core.jfr.dcmd.JfrStopDcmd;
import com.oracle.svm.core.jfr.dcmd.JfrStartDcmd;
import com.oracle.svm.core.jfr.dcmd.JfrDumpDcmd;
import com.oracle.svm.core.heap.dump.HeapDumpDcmd;
import com.oracle.svm.core.thread.ThreadDumpStacksDcmd;

import org.junit.Test;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AttachTest {
    /**
     * This test ensure attaching to the VM works and that all known DCMDs get registered correctly.
     */
    @Test
    public void testAttachAndDcmdRegistration() throws IOException, InterruptedException {
        long pid = ProcessHandle.current().pid();
        List<String> command = new ArrayList<>();
        command.add(System.getenv("JAVA_HOME") + "/bin/jcmd");
        command.add(String.valueOf(pid));
        command.add("help");
        // If the process crashes, this test will fail, not hang.
        ProcessBuilder pb = new ProcessBuilder(command);
        Process jcmdProc = pb.start();

        InputStream stdout = jcmdProc.getInputStream();
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdout));

        List<String> expectedStrings = new ArrayList<>(List.of(
                        new HelpDcmd().getName(),
                        new NmtDcmd().getName(),
                        new JfrCheckDcmd().getName(),
                        new JfrStopDcmd().getName(),
                        new JfrDumpDcmd().getName(),
                        new JfrStartDcmd().getName(),
                        new HeapDumpDcmd().getName(),
                        new ThreadDumpStacksDcmd().getName()));

        String line;
        while ((line = stdoutReader.readLine()) != null) {
            expectedStrings.remove(line);
        }
        assertTrue("Not all DCMDs were registered correctly. ", expectedStrings.isEmpty());

        int exitCode = jcmdProc.waitFor();
        assertEquals(0, exitCode);
    }

    /** This test verifies an edge case. It checks the teardown/restart process. */
    @Test
    public void testBadSocketFile() throws IOException, InterruptedException {
        checkJcmd();
        // Abruptly delete socket file.
        String tempDir = System.getProperty("java.io.tmpdir");
        File attachFile = new File(tempDir + "/.java_pid" + ProcessHandle.current().pid());
        boolean deletedSocketFile = Files.deleteIfExists(attachFile.toPath());

        assertTrue(deletedSocketFile);

        // Issue command again and verify response is still correct.
        checkJcmd();
    }

    @Test
    public void testConcurrentAttach() throws InterruptedException {
        int threadCount = 100;
        Thread[] threads = new Thread[threadCount];

        ImageSingletons.lookup(AttachApiSupport.class).teardown();

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    checkJcmd();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            threads[i].start();
        }

        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }
    }

    /** This is a helper method that uses JCMD to attach to the VM and request help info. */
    static void checkJcmd() throws IOException, InterruptedException {
        long pid = ProcessHandle.current().pid();
        List<String> command = new ArrayList<>();
        command.add(System.getenv("JAVA_HOME") + "/bin/jcmd");
        command.add(String.valueOf(pid));
        command.add("help");
        ProcessBuilder pb = new ProcessBuilder(command);
        Process jcmdProc;
        try {
            jcmdProc = pb.start();
        } catch (IOException e) {
            // Couldn't start process.
            return;
        }

        InputStream stdout = jcmdProc.getInputStream();
        BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdout));

        boolean foundExpectedResponse = false;
        String line;
        while (true) {
            line = stdoutReader.readLine();
            if (line == null) {
                break;
            } else if (line.contains("help")) {
                foundExpectedResponse = true;
            }
        }
        assertTrue(foundExpectedResponse);

        int exitCode = jcmdProc.waitFor();
        assertEquals(0, exitCode);
    }
}
