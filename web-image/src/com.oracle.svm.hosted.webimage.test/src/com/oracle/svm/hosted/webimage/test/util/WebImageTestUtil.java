/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.test.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Assert;

import com.oracle.svm.core.OS;

public class WebImageTestUtil {

    /**
     * Represents the result of running some process.
     *
     * Stores both the exit code and the output lines.
     */
    public static class RunResult {
        public final String[] lines;

        public RunResult(String[] lines) {
            this.lines = lines;
        }

        public void assertEquals(RunResult actual) {
            assertEquals(actual, (expecteds, actuals) -> Assert.assertArrayEquals("Actual output lines: " + Arrays.asList(actual.lines), expecteds, actuals));
        }

        /**
         * Checks that the given result has the same exit code and calls lineChecker on the lines of
         * this and the given result instance.
         */
        public void assertEquals(RunResult actual, BiConsumer<String[], String[]> lineChecker) {
            lineChecker.accept(this.lines, actual.lines);
        }
    }

    /**
     * Mimics a {@link RunResult}, but in reality does not provide a set of result lines, only a
     * callback that the result lines have to satisfy.
     *
     * When {@link #assertEquals(RunResult)} is called on this class, the lines of the given result
     * are checked against the lineChecker callback.
     */
    public static class VirtualRunResult extends RunResult {

        private final Consumer<String[]> lineChecker;

        public VirtualRunResult(Consumer<String[]> lineChecker) {
            super(new String[0]);

            this.lineChecker = lineChecker;
        }

        @Override
        public void assertEquals(RunResult actual) {
            super.assertEquals(actual, (expected, actualLines) -> lineChecker.accept(actualLines));
        }
    }

    /**
     * Empties the contents of the given directory (but doesn't remove the directory itself).
     *
     * If this returns successfully, 'p' will exist and be an empty directory.
     */
    public static void clearDirectory(Path p) throws IOException {
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }

        assert Files.isDirectory(p);
        Files.list(p).forEach(WebImageTestUtil::deleteRecursive);
    }

    /**
     * Deletes the given file or directory recursively.
     *
     * Does not fail if 'p' doesn't exist.
     */
    public static void deleteRecursive(Path p) {
        try {
            if (Files.isDirectory(p)) {
                Files.list(p).forEach(WebImageTestUtil::deleteRecursive);
            }
            Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String[] getProcessOutput(Process p) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        try (BufferedReader readerErr = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line;
            while ((line = readerErr.readLine()) != null) {
                lines.add(line);
            }
        }

        return lines.toArray(new String[0]);
    }

    /**
     * Run the given class in a new JVM process.
     */
    public static RunResult executeTestProgram(Class<?> c, String[] args, int expectExitCode) {
        String javaHome = System.getProperty("java.home");
        String classpath = System.getProperty("java.class.path");
        Path binJava = Paths.get(javaHome, "bin", OS.getCurrent() == OS.WINDOWS ? "java.exe" : "java");

        List<String> cmd = new ArrayList<>();
        cmd.add(binJava.toString());

        Locale defaultLocale = Locale.getDefault();
        cmd.add("-Duser.language=" + defaultLocale.getLanguage());
        cmd.add("-Duser.country=" + defaultLocale.getCountry());

        /*
         * stdout/stderr do not use the default charset, but the console charset. This is
         * undesirable in this case where we just treat the streams as a means to get raw data for
         * which we need a consistent encoding.
         */
        Charset charset = Charset.defaultCharset();
        cmd.add("-Dstdout.encoding=" + charset.name());
        cmd.add("-Dstderr.encoding=" + charset.name());

        cmd.add("--add-exports=jdk.internal.vm.ci/jdk.vm.ci.common=ALL-UNNAMED");
        // For tests that need to access jdk.internal.misc.Unsafe
        cmd.add("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");

        // These tests put SVM jars onto the class path, exposing SVM class to jargraal.
        // This is problematic as these classes access non-exported Graal packages
        // (e.g. jdk.graal.compiler.options is accessed by the OptionDescriptors
        // classes generated for @Option annotated fields). Either a long list of
        // --add-exports needs to be added to the command line or the jargraal JIT
        // can be disabled. We opt for the latter.
        cmd.add("-XX:-UseJVMCICompiler");

        cmd.add("-cp");
        cmd.add(classpath);

        cmd.add(c.getName());

        if (args != null) {
            cmd.addAll(Arrays.asList(args));
        }

        return runProcess(cmd, expectExitCode);
    }

    public static RunResult runJS(String cmd, String[] arguments, int expectExitCode) {
        List<String> invokeCmd = new ArrayList<>();
        invokeCmd.add(WebImageTestOptions.JS_CMD);
        invokeCmd.add(cmd);

        if (arguments != null) {
            Collections.addAll(invokeCmd, arguments);
        }

        System.out.println("\t" + String.join(" ", invokeCmd));
        return runProcess(invokeCmd, expectExitCode);
    }

    /**
     * Max output lines to include in an exception error message.
     */
    private static final int MAX_LINES = 1000;

    /**
     * Formats {@code lines} to a single string, taking into account {@link #MAX_LINES}.
     */
    private static String formatOutput(String[] lines) {
        String nl = System.lineSeparator();
        if (lines.length <= MAX_LINES) {
            return String.join(nl, lines);
        } else {
            int half = MAX_LINES / 2;
            String head = String.join(nl, Arrays.copyOfRange(lines, 0, half));
            String tail = String.join(nl, Arrays.copyOfRange(lines, lines.length - half, lines.length));
            return String.format("<first %d lines>%n%s%n...%n<last %d lines>%n%s", half, head, half, tail);
        }
    }

    private static RunResult runProcess(List<String> cmd, int expectExitCode) {
        try {
            Process p = new ProcessBuilder(cmd).start();
            String[] lines = getProcessOutput(p);
            int exitCode = p.waitFor();
            Assert.assertEquals("Unexpected error code while running " + String.join(" ", cmd) + ". Output:\n" + formatOutput(lines), expectExitCode, exitCode);
            return new RunResult(lines);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
