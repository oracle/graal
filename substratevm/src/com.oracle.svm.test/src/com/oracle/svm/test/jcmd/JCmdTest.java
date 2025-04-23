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

package com.oracle.svm.test.jcmd;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.nativeimage.Platform;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.VMInspectionOptions;

public class JCmdTest {
    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JCmd tests", VMInspectionOptions.hasJCmdSupport());
    }

    @Test
    public void testHelp() throws IOException, InterruptedException {
        Process jcmd = runJCmd("help");
        String[] commands = new String[]{"GC.heap_dump", "GC.run", "JFR.check", "JFR.dump", "JFR.start", "JFR.stop", "Thread.dump_to_file", "Thread.print", "VM.command_line",
                        "VM.native_memory", "VM.system_properties", "VM.uptime", "VM.version", "help"};
        assertOutputContainsLines(jcmd, commands);

        for (String command : commands) {
            jcmd = runJCmd("help", command);
            assertOutputContainsStrings(jcmd, "Impact: ", "Syntax : " + command);
        }

        for (String command : commands) {
            jcmd = runJCmd(command, "-h");
            assertOutputContainsStrings(jcmd, "Impact: ", "Syntax : " + command);
        }
    }

    @Test
    public void testBadSocketFile() throws IOException, InterruptedException {
        checkJCmdConnection();

        /* Delete the socket file. */
        String tempDir = System.getProperty("java.io.tmpdir");
        Path attachFile = Paths.get(tempDir, ".java_pid" + ProcessHandle.current().pid());
        boolean deletedSocketFile = Files.deleteIfExists(attachFile);
        assertTrue(deletedSocketFile);

        checkJCmdConnection();
    }

    @Test
    public void testConcurrentAttach() throws Throwable {
        assumeTrue("Fails transiently on MacOS", !Platform.includedIn(Platform.DARWIN.class));
        int threadCount = 32;

        AtomicReference<Throwable> exception = new AtomicReference<>();
        Runnable runnable = () -> {
            try {
                checkJCmdConnection();
            } catch (Throwable e) {
                exception.set(e);
            }
        };

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(runnable);
            threads[i].start();
        }

        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }

        if (exception.get() != null) {
            throw exception.get();
        }
    }

    @Test
    public void testJfr() throws IOException, InterruptedException {
        Process jcmd = runJCmd("JFR.start", "name=JCmdTest");
        assertOutputContainsStrings(jcmd, "Started recording ", "JCmdTest");

        jcmd = runJCmd("JFR.check");
        assertOutputContainsStrings(jcmd, "Recording ", "name=JCmdTest", "running");

        jcmd = runJCmd("JFR.dump");
        assertOutputContainsStrings(jcmd, "Dumped recording", "svmjunit-pid-");

        jcmd = runJCmd("JFR.stop", "name=JCmdTest");
        assertOutputContainsLines(jcmd, "Stopped recording \"JCmdTest\".");
    }

    private static void checkJCmdConnection() throws IOException, InterruptedException {
        Process jcmd = runJCmd("help");
        assertOutputContainsLines(jcmd, "help");
    }

    private static Process runJCmd(String... args) throws IOException {
        long pid = ProcessHandle.current().pid();
        List<String> process = new ArrayList<>();
        process.add(Paths.get(System.getenv("JAVA_HOME"), "bin", "jcmd").toString());
        process.add(String.valueOf(pid));
        process.addAll(List.of(args));
        return new ProcessBuilder(process).redirectErrorStream(true).start();
    }

    private static void assertOutputContainsLines(Process process, String... expectedLines) throws InterruptedException {
        List<String> remaining = new ArrayList<>(Arrays.asList(expectedLines));
        for (String line : getOutput(process)) {
            remaining.remove(line);
        }

        if (!remaining.isEmpty()) {
            fail("The following lines were not found in the output: '" + String.join("', '", remaining) + "'");
        }
    }

    private static void assertOutputContainsStrings(Process process, String... expected) throws InterruptedException {
        List<String> remaining = new ArrayList<>(Arrays.asList(expected));
        for (String line : getOutput(process)) {
            remaining.removeIf(line::contains);
        }

        if (!remaining.isEmpty()) {
            fail("The following strings were not found in the output: '" + String.join("', '", remaining) + "'.");
        }
    }

    private static String[] getOutput(Process process) throws InterruptedException {
        int exitCode = process.waitFor();

        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String[] lines = stdout.lines().toArray(String[]::new);
        if (exitCode != 0) {
            String lineBreak = System.lineSeparator();
            fail("jcmd returned with exit code " + exitCode + ": " + lineBreak + String.join(lineBreak, lines));
        }
        return lines;
    }
}
