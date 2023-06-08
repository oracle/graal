/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.test;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import org.graalvm.profdiff.Profdiff;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MainTest {

    /**
     * The stream that replaces {@link System#out}.
     */
    private ByteArrayOutputStream outputStream;

    /**
     * The stream that replaces {@link System#err}.
     */
    private ByteArrayOutputStream errorStream;

    /**
     * The saved value of {@link System#out}.
     */
    private PrintStream savedSystemOut;

    /**
     * The saved value of {@link System#err}.
     */
    private PrintStream savedSystemErr;

    @Before
    public void replaceSystemStreams() {
        savedSystemOut = System.out;
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        savedSystemErr = System.err;
        errorStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorStream));
    }

    @After
    public void restoreSystemStreams() {
        outputStream = null;
        System.out.close();
        System.setOut(savedSystemOut);
        savedSystemOut = null;
        errorStream = null;
        System.err.close();
        System.setErr(savedSystemErr);
        savedSystemErr = null;
    }

    @Test
    public void testHelp() {
        Profdiff.main(new String[]{"help"});
        Assert.assertTrue(errorStream.toString().isEmpty());
        Profdiff.main(new String[]{"help", "report"});
        Assert.assertTrue(errorStream.toString().isEmpty());
        Profdiff.main(new String[]{"help", "unknown"});
        Assert.assertFalse(errorStream.toString().isEmpty());
    }

    @Test
    public void testArgumentParsing() {
        String[] args = {"--hot-min-limit", "1", "--hot-max-limit", "2", "--hot-percentile", "0.5", "--optimization-context-tree", "true", "--diff-compilations", "true", "--long-bci", "true",
                        "--sort-inlining-tree", "true", "--sort-unordered-phases", "true", "--remove-detailed-phases", "true", "--prune-identities", "true", "--create-fragments", "true",
                        "--inliner-reasoning", "true", "help"};
        Profdiff.main(args);
        Assert.assertTrue(errorStream.toString().isEmpty());
    }

    @Test
    public void reportEmptyDirectory() throws IOException {
        Path tmpDir = Files.createTempDirectory(Paths.get("."), "profdiff_report_empty");
        try {
            String[] args = {"report", tmpDir.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            deleteTree(tmpDir);
        }
        Assert.assertTrue(errorStream.toString().isEmpty());
    }

    @Test
    public void reportOptimizationLogs() throws IOException {
        List<String> methodNames = List.of("test.foo()", "test.bar()", "test.baz()");
        Path tmpDir = Files.createTempDirectory(Paths.get("."), "profdiff_report_logs");
        File optLogFile1 = new File(tmpDir.toString(), "opt_log_1");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(optLogFile1))) {
            writer.append(mockCompilationUnit(methodNames.get(0), "10"));
        }
        File optLogFile2 = new File(tmpDir.toString(), "opt_log_2");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(optLogFile2))) {
            writer.append(mockCompilationUnit(methodNames.get(1), "20"));
            writer.append('\n');
            writer.append(mockCompilationUnit(methodNames.get(2), "30"));
        }
        try {
            String[] args = {"report", tmpDir.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            deleteTree(tmpDir);
        }
        String output = outputStream.toString();
        for (String methodName : methodNames) {
            Assert.assertTrue(output.contains(methodName));
        }
        Assert.assertTrue(errorStream.toString().isEmpty());
    }

    private static String mockCompilationUnit(String methodName, String compilationId) {
        return """
                        {
                            "methodName": "METHOD_NAME",
                            "localMethodName": "local_METHOD_NAME",
                            "compilationId": "COMPILATION_ID",
                            "inliningTree": {
                                "methodName": "METHOD_NAME",
                                "callsiteBci": -1,
                                "inlined": true,
                                "indirect": false,
                                "alive": false,
                                "reason": null,
                                "invokes": []
                            },
                            "optimizationTree": {
                                "phaseName": "RootPhase",
                                "optimizations": []
                            }
                        }
                        """.replace("\n", "").replace("METHOD_NAME", methodName).replace("COMPILATION_ID", compilationId);
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walk(root).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
}
