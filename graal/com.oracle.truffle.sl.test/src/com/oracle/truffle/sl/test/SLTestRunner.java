/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.sl.test;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.runtime.*;

public class SLTestRunner {

    private static final int REPEATS = 10;
    private static final String TEST_DIR = "graal/com.oracle.truffle.sl.test/tests";
    private static final String INPUT_SUFFIX = ".sl";
    private static final String OUTPUT_SUFFIX = ".output";

    static class TestCase {
        protected final String name;
        protected final Source input;
        protected final String expectedOutput;
        protected String actualOutput;

        protected TestCase(String name, Source input, String expectedOutput) {
            this.name = name;
            this.input = input;
            this.expectedOutput = expectedOutput;
        }
    }

    protected boolean useConsole = false;

    protected final SourceManager sourceManager = new SourceManager();
    protected final List<TestCase> testCases = new ArrayList<>();

    protected boolean runTests(String namePattern) throws IOException {
        Path testsRoot = FileSystems.getDefault().getPath(TEST_DIR);

        Files.walkFileTree(testsRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path inputFile, BasicFileAttributes attrs) throws IOException {
                String name = inputFile.getFileName().toString();
                if (name.endsWith(INPUT_SUFFIX)) {
                    name = name.substring(0, name.length() - INPUT_SUFFIX.length());
                    Path outputFile = inputFile.resolveSibling(name + OUTPUT_SUFFIX);
                    if (!Files.exists(outputFile)) {
                        throw new Error("Output file does not exist: " + outputFile);
                    }

                    testCases.add(new TestCase(name, sourceManager.get(inputFile.toString()), new String(Files.readAllBytes(outputFile))));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (testCases.size() == 0) {
            System.out.format("No test cases match filter %s", namePattern);
            return false;
        }

        boolean success = true;
        for (TestCase testCase : testCases) {
            if (namePattern.length() == 0 || testCase.name.toLowerCase().contains(namePattern.toLowerCase())) {
                success = success & executeTest(testCase);
            }
        }
        return success;
    }

    protected boolean executeTest(TestCase testCase) {
        System.out.format("Running %s\n", testCase.name);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printer = new PrintStream(useConsole ? new SplitOutputStream(out, System.err) : out);
        PrintStream origErr = System.err;
        try {
            System.setErr(printer);
            SLContext context = new SLContext(sourceManager, printer);
            SLMain.run(context, testCase.input, null, REPEATS);
        } catch (Throwable ex) {
            ex.printStackTrace(printer);
        } finally {
            System.setErr(origErr);
        }
        testCase.actualOutput = new String(out.toByteArray());

        if (testCase.actualOutput.equals(repeat(testCase.expectedOutput, REPEATS))) {
            System.out.format("OK %s\n", testCase.name);
            return true;
        } else {
            if (!useConsole) {
                System.out.format("== Expected ==\n%s\n", testCase.expectedOutput);
                System.out.format("== Actual ==\n%s\n", testCase.actualOutput);
            }
            System.out.format("FAILED %s\n", testCase.name);
            return false;
        }
    }

    private static String repeat(String s, int count) {
        StringBuilder result = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(s);
        }
        return result.toString();
    }

    public static void main(String[] args) throws IOException {
        String namePattern = "";
        if (args.length > 0) {
            namePattern = args[0];
        }
        boolean success = new SLTestRunner().runTests(namePattern);
        if (!success) {
            System.exit(1);
        }
    }

    @Test
    public void test() throws IOException {
        Assert.assertTrue(runTests(""));
    }
}
