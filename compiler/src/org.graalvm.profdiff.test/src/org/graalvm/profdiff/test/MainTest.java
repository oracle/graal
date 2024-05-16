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

import static org.graalvm.profdiff.parser.ExperimentParser.CODE;
import static org.graalvm.profdiff.parser.ExperimentParser.COMPILATION_AOT;
import static org.graalvm.profdiff.parser.ExperimentParser.COMPILATION_KIND;
import static org.graalvm.profdiff.parser.ExperimentParser.COMPILE_ID;
import static org.graalvm.profdiff.parser.ExperimentParser.EXECUTION_ID;
import static org.graalvm.profdiff.parser.ExperimentParser.GRAAL_COMPILER_LEVEL;
import static org.graalvm.profdiff.parser.ExperimentParser.LEVEL;
import static org.graalvm.profdiff.parser.ExperimentParser.NAME;
import static org.graalvm.profdiff.parser.ExperimentParser.NAME_SEPARATOR;
import static org.graalvm.profdiff.parser.ExperimentParser.OSR_MARKER;
import static org.graalvm.profdiff.parser.ExperimentParser.PERIOD;
import static org.graalvm.profdiff.parser.ExperimentParser.TOTAL_PERIOD;
import static org.junit.Assert.assertFalse;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.Profdiff;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.util.json.JsonWriter;

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
        assertNoError();
        Profdiff.main(new String[]{"help", "report"});
        assertNoError();
        Profdiff.main(new String[]{"help", "unknown"});
        assertFalse(errorStream.toString().isEmpty());
    }

    @Test
    public void testArgumentParsing() {
        String[] args = {"--hot-min-limit", "1", "--hot-max-limit", "2", "--hot-percentile", "0.5", "--optimization-context-tree", "true", "--diff-compilations", "true", "--long-bci", "true",
                        "--sort-inlining-tree", "true", "--sort-unordered-phases", "true", "--remove-detailed-phases", "true", "--prune-identities", "true", "--create-fragments", "true",
                        "--inliner-reasoning", "true", "help"};
        Profdiff.main(args);
        assertNoError();
    }

    @Test
    public void reportEmptyDirectory() throws IOException {
        Path tmpDir = new OptimizationLogMock("profdiff_empty").writeToTempDirectory();
        try {
            String[] args = {"report", tmpDir.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            deleteTree(tmpDir);
        }
        assertNoError();
    }

    @Test
    public void reportOptimizationLog() throws IOException {
        OptimizationLogMock optLog = new OptimizationLogMock("profdiff_logs");
        optLog.addLogFile().addCompilationUnit("foo()", "10-foo");
        optLog.addLogFile().addCompilationUnit("bar()", "20-bar").addCompilationUnit("baz()", "30-baz").addCompilationUnit("foo()", "40-foo");
        Path logDir = optLog.writeToTempDirectory();
        try {
            String[] args = {"report", logDir.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            deleteTree(logDir);
        }
        assertNoError();
        assertOutputContains(optLog.allCompilationIDs());
    }

    @Test
    public void reportExperiment() throws IOException {
        OptimizationLogMock optLog = new OptimizationLogMock("profdiff_report");
        optLog.addLogFile().addCompilationUnit("foo()", "10-foo").addCompilationUnit("foo()", "20-foo");
        optLog.addLogFile().addCompilationUnit("bar()", "30-bar").addCompilationUnit("baz()", "40-baz");
        Path logDir = optLog.writeToTempDirectory();
        ProftoolJITMock proftool = new ProftoolJITMock("profdiff_report_prof", "1000").addOSRMethod("foo()", "10-foo", 10).addGraalMethod("foo()", "20-foo", 20).addGraalMethod("bar()", "30-bar",
                        30).addForeignMethod("foreign_method", 40);
        Path profFile = proftool.writeToTempFile();
        try {
            String[] args = {"report", logDir.toAbsolutePath().toString(), profFile.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            deleteTree(logDir);
            Files.delete(profFile);
        }
        assertNoError();
        String output = outputStream.toString();
        for (String needle : List.of("10-foo", "20-foo", "30-bar", "foreign_method")) {
            Assert.assertTrue(output.contains(needle));
        }
        assertFalse("method baz() should not be hot", output.contains("40-baz"));
    }

    @Test
    public void compareJITExperiments() throws IOException {
        ExperimentMock exp1 = createJITExperiment1("profdiff_jit_1", "profdiff_jit_prof_1");
        ExperimentMock exp2 = createJITExperiment2("profdiff_jit_2", "profdiff_jit_prof_2");
        try {
            String[] args = {"jit-vs-jit", exp1.logDir.toAbsolutePath().toString(), exp1.profFile.toAbsolutePath().toString(),
                            exp2.logDir.toAbsolutePath().toString(), exp2.profFile.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            exp1.delete();
            exp2.delete();
        }
        assertNoError();
        assertOutputContains(exp1.optLog.allCompilationIDs());
        assertOutputContains(exp2.optLog.allCompilationIDs());
    }

    @Test
    public void compareAOTExperiments() throws IOException {
        ExperimentMock exp1 = createAOTExperiment1("profdiff_aot_1", "profdiff_aot_prof_1");
        ExperimentMock exp2 = createAOTExperiment2("profdiff_aot_2", "profdiff_aot_prof_2");
        try {
            String[] args = {"--hot-percentile", "1", "aot-vs-aot", exp1.logDir.toAbsolutePath().toString(), exp1.profFile.toAbsolutePath().toString(),
                            exp2.logDir.toAbsolutePath().toString(), exp2.profFile.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            exp1.delete();
            exp2.delete();
        }
        assertNoError();
        assertOutputContains(exp1.optLog.allCompilationIDs());
        assertOutputContains(exp2.optLog.allCompilationIDs());
    }

    @Test
    public void compareJITAndAOT() throws IOException {
        ExperimentMock exp1 = createJITExperiment1("profdiff_jit_aot_1", "profdiff_jit_aot_prof_1");
        ExperimentMock exp2 = createAOTExperiment2("profdiff_jit_aot_2", "profdiff_jit_aot_prof_2");
        try {
            String[] args = {"jit-vs-aot", exp1.logDir.toAbsolutePath().toString(), exp1.profFile.toAbsolutePath().toString(),
                            exp2.logDir.toAbsolutePath().toString(), exp2.profFile.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            exp1.delete();
            exp2.delete();
        }
        assertNoError();
        assertOutputContains(exp1.optLog.allCompilationIDs());
        assertOutputContains(exp2.optLog.allCompilationIDs());
    }

    @Test
    public void compareJITAndAOTUnprofiled() throws IOException {
        ExperimentMock exp1 = createJITExperiment1("profdiff_jit_aot_unprof_1", "profdiff_jit_aot_unprof_prof_1");

        OptimizationLogMock optLog = new OptimizationLogMock("profdiff_jit_aot_unprof_2");
        optLog.addLogFile().addCompilationUnit("foo()", "30-foo").addCompilationUnit("baz()", "40-baz");
        Path logDir = optLog.writeToTempDirectory();

        try {
            String[] args = {"jit-vs-aot", exp1.logDir.toAbsolutePath().toString(), exp1.profFile.toAbsolutePath().toString(),
                            logDir.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            exp1.delete();
            deleteTree(logDir);
        }
        assertNoError();
        assertOutputContains(exp1.optLog.allCompilationIDs());
    }

    @Test
    public void compareAOTExperimentsWithExternalProfile() throws IOException {
        ExperimentMock exp = createJITExperiment1("profdiff_jit_aot_ext_0", "profdiff_jit_aot_ext_prof_0");

        OptimizationLogMock optLog1 = new OptimizationLogMock("profdiff_jit_aot_ext_1");
        optLog1.addLogFile().addCompilationUnit("foo()", "10-foo").addCompilationUnit("bar()", "20-bar");
        Path logDir1 = optLog1.writeToTempDirectory();

        OptimizationLogMock optLog2 = new OptimizationLogMock("profdiff_jit_aot_ext_2");
        optLog2.addLogFile().addCompilationUnit("foo()", "30-foo").addCompilationUnit("baz()", "40-baz");
        Path logDir2 = optLog2.writeToTempDirectory();

        try {
            String[] args = {"aot-vs-aot-jit-profile", exp.logDir.toAbsolutePath().toString(), exp.profFile.toAbsolutePath().toString(),
                            logDir1.toAbsolutePath().toString(), logDir2.toAbsolutePath().toString()};
            Profdiff.main(args);
        } finally {
            exp.delete();
            deleteTree(logDir1);
            deleteTree(logDir2);
        }
        assertNoError();
        assertOutputContains(optLog1.allCompilationIDs());
    }

    /**
     * Holds the files related to an experiment mock.
     *
     * @param optLog the mocked the optimization log
     * @param logDir the path to the optimization log directory
     * @param profFile the path to the file with proftool profiles
     */
    private record ExperimentMock(OptimizationLogMock optLog, Path logDir, Path profFile) {
        void delete() throws IOException {
            deleteTree(logDir);
            Files.delete(profFile);
        }
    }

    private static ExperimentMock createJITExperiment1(String directoryPrefix, String profPrefix) throws IOException {
        OptimizationLogMock optLog = new OptimizationLogMock(directoryPrefix);
        optLog.addLogFile().addCompilationUnit("foo()", "10-foo").addCompilationUnit("bar()", "20-bar");
        Path logDir = optLog.writeToTempDirectory();
        ProftoolJITMock proftool = new ProftoolJITMock(profPrefix, "1000");
        proftool.addGraalMethod("foo()", "10-foo", 60).addGraalMethod("bar()", "20-bar", 40);
        Path profFile = proftool.writeToTempFile();
        return new ExperimentMock(optLog, logDir, profFile);
    }

    private static ExperimentMock createJITExperiment2(String directoryPrefix, String profPrefix) throws IOException {
        OptimizationLogMock optLog = new OptimizationLogMock(directoryPrefix);
        optLog.addLogFile().addCompilationUnit("foo()", "30-foo").addCompilationUnit("baz()", "40-baz");
        Path logDir = optLog.writeToTempDirectory();
        ProftoolJITMock proftool = new ProftoolJITMock(profPrefix, "2000");
        proftool.addGraalMethod("foo()", "30-foo", 30).addGraalMethod("baz()", "40-baz", 70);
        Path profFile = proftool.writeToTempFile();
        return new ExperimentMock(optLog, logDir, profFile);
    }

    private static ExperimentMock createAOTExperiment1(String directoryPrefix, String profPrefix) throws IOException {
        OptimizationLogMock optLog = new OptimizationLogMock(directoryPrefix);
        optLog.addLogFile().addCompilationUnit("foo()", "10-foo").addCompilationUnit("bar()", "20-bar");
        Path logDir = optLog.writeToTempDirectory();
        ProftoolAOTMock proftool = new ProftoolAOTMock(profPrefix);
        proftool.addMethod("foo()", 50).addMethod("bar()", 30).addMethod("foreign_method()", 20);
        Path profFile = proftool.writeToTempFile();
        return new ExperimentMock(optLog, logDir, profFile);
    }

    private static ExperimentMock createAOTExperiment2(String directoryPrefix, String profPrefix) throws IOException {
        OptimizationLogMock optLog = new OptimizationLogMock(directoryPrefix);
        optLog.addLogFile().addCompilationUnit("foo()", "30-foo").addCompilationUnit("baz()", "40-baz");
        Path logDir = optLog.writeToTempDirectory();
        ProftoolAOTMock proftool = new ProftoolAOTMock(profPrefix);
        proftool.addMethod("foo()", 30).addMethod("baz()", 50).addMethod("foreign_method()", 20);
        Path profFile = proftool.writeToTempFile();
        return new ExperimentMock(optLog, logDir, profFile);
    }

    /**
     * Recursively deletes a file tree.
     *
     * @param root the root of the file tree
     * @throws IOException failed to walk or delete the file tree
     */
    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /**
     * Asserts that there are errors written to the error stream, i.e., the stream is empty.
     */
    private void assertNoError() {
        Assert.assertTrue(errorStream.toString().isEmpty());
    }

    /**
     * Asserts that the standard output contains each provided string.
     *
     * @param needles the list of strings that the standard output should contain
     */
    private void assertOutputContains(List<String> needles) {
        String output = outputStream.toString();
        for (String needle : needles) {
            Assert.assertTrue(output.contains(needle));
        }
    }

    /**
     * Mocks a compilation unit in an optimization log.
     *
     * @param methodName the name of the root method
     * @param compilationID the compilation ID
     */
    private record CompilationUnitMock(String methodName, String compilationID) {

        /**
         * Returns one line of JSON representing the optimization log of this method.
         */
        String toJSON() {
            return """
                            {
                                "methodName": "{METHOD_NAME}",
                                "compilationId": "{COMPILATION_ID}",
                                "inliningTree": {
                                    "methodName": "{METHOD_NAME}",
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
                            """.replace("\n", "").replace("{METHOD_NAME}", methodName).replace("{COMPILATION_ID}", compilationID);
        }
    }

    /**
     * Mocks one file in an optimization log.
     */
    private static final class OptimizationLogFileMock {

        /**
         * Mocks of compilation units.
         */
        private final List<CompilationUnitMock> compilationUnitMocks;

        private OptimizationLogFileMock() {
            this.compilationUnitMocks = new ArrayList<>();
        }

        /**
         * Adds a compilation unit mock to this optimization log file.
         *
         * @param methodName the name of the root method of the compilation unit
         * @param compilationID the compilation ID
         * @return the added compilation unit mock
         */
        OptimizationLogFileMock addCompilationUnit(String methodName, String compilationID) {
            CompilationUnitMock unitMock = new CompilationUnitMock(methodName, compilationID);
            compilationUnitMocks.add(unitMock);
            return this;
        }

        /**
         * Writes the mocked optimization log file to the given file.
         *
         * @param file the file where the mock is written
         * @throws IOException failed to write to the file
         */
        void writeToFile(File file) throws IOException {
            boolean first = true;
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (CompilationUnitMock mock : compilationUnitMocks) {
                    if (first) {
                        first = false;
                    } else {
                        writer.append('\n');
                    }
                    writer.append(mock.toJSON());
                }
            }
        }
    }

    /**
     * Mocks a complete optimization log, i.e. a set of optimization log files.
     */
    private static final class OptimizationLogMock {

        /**
         * The prefix of the directory name where the optimization log is stored.
         */
        private final String directoryPrefix;

        /**
         * Mocks of individual optimization log files.
         */
        private final List<OptimizationLogFileMock> fileMocks;

        private OptimizationLogMock(String directoryPrefix) {
            this.directoryPrefix = directoryPrefix;
            this.fileMocks = new ArrayList<>();
        }

        /**
         * Adds and returns an additional optimization log file to the logs.
         */
        OptimizationLogFileMock addLogFile() {
            OptimizationLogFileMock fileMock = new OptimizationLogFileMock();
            fileMocks.add(fileMock);
            return fileMock;
        }

        /**
         * Writes the mocked optimization log files to a new temporary directory.
         *
         * @return the path to the directory
         * @throws IOException failed to create the directory or write the log files
         */
        Path writeToTempDirectory() throws IOException {
            int counter = 0;
            Path logDirectory = Files.createTempDirectory(Paths.get("."), directoryPrefix);
            for (OptimizationLogFileMock fileMock : fileMocks) {
                File file = new File(logDirectory.toString(), Integer.toString(++counter));
                fileMock.writeToFile(file);
            }
            return logDirectory;
        }

        /**
         * Returns a list of all compilation IDs added to the optimization log.
         */
        List<String> allCompilationIDs() {
            List<String> result = new ArrayList<>();
            for (OptimizationLogFileMock fileMock : fileMocks) {
                for (CompilationUnitMock unitMock : fileMock.compilationUnitMocks) {
                    result.add(unitMock.compilationID);
                }
            }
            return result;
        }
    }

    /**
     * Mocks a native method recorded in a JIT profile.
     *
     * @param methodName the name of the method (without compile ID)
     * @param compileID the compile ID (without {@code %} if OSR)
     * @param level the compilation level (e.g., 4 for Graal or {@code null} for native methods)
     * @param period the total recorded period for the given method
     * @param isOSR {@code true} if this is an OSR compilation
     */
    private record ProftoolJITMethodMock(String methodName, String compileID, Integer level, long period, boolean isOSR) {

        /**
         * Formats the method as a JSON map.
         */
        EconomicMap<String, Object> toJSONMap() {
            EconomicMap<String, Object> map = EconomicMap.create();
            String id = isOSR ? compileID + OSR_MARKER : compileID;
            map.put(COMPILE_ID, id);
            String name = compileID == null ? methodName : id + NAME_SEPARATOR + methodName;
            map.put(NAME, name);
            map.put(LEVEL, level);
            map.put(PERIOD, period);
            return map;
        }
    }

    /**
     * Mocks a proftool profile for an experiment (either JIT or AOT).
     */
    private abstract static class ProftoolMock {
        /**
         * The prefix of the file where the profile should be saved.
         */
        private final String fileNamePrefix;

        protected ProftoolMock(String fileNamePrefix) {
            this.fileNamePrefix = fileNamePrefix;
        }

        /**
         * Formats and returns the profile as a JSON map.
         */
        protected abstract EconomicMap<String, Object> asJSONMap();

        /**
         * Writes the profile to a temporary file.
         *
         * @return the path to the temporary file with the profile
         * @throws IOException failed to write the profile to a file
         */
        Path writeToTempFile() throws IOException {
            Path path = Files.createTempFile(Paths.get("."), fileNamePrefix, ".json");
            try (JsonWriter writer = new JsonWriter(path)) {
                writer.print(asJSONMap());
            }
            return path;
        }
    }

    /**
     * Mocks a proftool profile for a JIT experiment.
     */
    private static final class ProftoolJITMock extends ProftoolMock {

        /**
         * The execution ID of the experiment.
         */
        private final String executionID;

        /**
         * The list of recorded methods in the profile.
         */
        private final List<ProftoolJITMethodMock> methodMocks;

        private ProftoolJITMock(String fileNamePrefix, String executionID) {
            super(fileNamePrefix);
            this.executionID = executionID;
            this.methodMocks = new ArrayList<>();
        }

        /**
         * Adds a Graal-compiled non-OSR method to the profile.
         */
        ProftoolJITMock addGraalMethod(String methodName, String compileID, long period) {
            methodMocks.add(new ProftoolJITMethodMock(methodName, compileID, GRAAL_COMPILER_LEVEL, period, false));
            return this;
        }

        /**
         * Adds a Graal-compiled OSR method to the profile.
         */
        ProftoolJITMock addOSRMethod(String methodName, String compileID, long period) {
            methodMocks.add(new ProftoolJITMethodMock(methodName, compileID, GRAAL_COMPILER_LEVEL, period, true));
            return this;
        }

        /**
         * Adds a foreign method (not compiled by Graal) to the profile.
         */
        ProftoolJITMock addForeignMethod(String methodName, long period) {
            methodMocks.add(new ProftoolJITMethodMock(methodName, null, null, period, false));
            return this;
        }

        @Override
        protected EconomicMap<String, Object> asJSONMap() {
            EconomicMap<String, Object> map = EconomicMap.create();
            map.put(EXECUTION_ID, executionID);
            map.put(TOTAL_PERIOD, methodMocks.stream().mapToLong(ProftoolJITMethodMock::period).sum());
            map.put(CODE, methodMocks.stream().map(ProftoolJITMethodMock::toJSONMap).collect(Collectors.toList()));
            return map;
        }
    }

    /**
     * Mocks a native method recorded in an AOT profile.
     *
     * @param methodName the name of the method (Java or foreign)
     * @param period the total recorded period for the given method
     */
    private record ProftoolAOTMethodMock(String methodName, long period) {

        /**
         * Formats the method as a JSON map.
         */
        EconomicMap<String, Object> toJSONMap() {
            EconomicMap<String, Object> map = EconomicMap.create();
            map.put(NAME, methodName);
            map.put(PERIOD, period);
            return map;
        }
    }

    /**
     * Mocks a proftool profile for an AOT experiment.
     */
    private static final class ProftoolAOTMock extends ProftoolMock {

        /**
         * The list of recorded methods in the profile.
         */
        private final List<ProftoolAOTMethodMock> methodMocks;

        private ProftoolAOTMock(String fileNamePrefix) {
            super(fileNamePrefix);
            this.methodMocks = new ArrayList<>();
        }

        /**
         * Adds a method to the profile.
         */
        ProftoolAOTMock addMethod(String methodName, long period) {
            methodMocks.add(new ProftoolAOTMethodMock(methodName, period));
            return this;
        }

        @Override
        protected EconomicMap<String, Object> asJSONMap() {
            EconomicMap<String, Object> map = EconomicMap.create();
            map.put(COMPILATION_KIND, COMPILATION_AOT);
            map.put(TOTAL_PERIOD, methodMocks.stream().mapToLong(ProftoolAOTMethodMock::period).sum());
            map.put(CODE, methodMocks.stream().map(ProftoolAOTMethodMock::toJSONMap).collect(Collectors.toList()));
            return map;
        }
    }

    @Test
    public void argumentVerification() {
        List<String[]> invalidArgs = List.of(
                        new String[]{"--hot-percentile", "-1", "help"},
                        new String[]{"--hot-percentile", "1.5", "help"},
                        new String[]{"--hot-min-limit", "-1", "help"},
                        new String[]{"--hot-max-limit", "-1", "help"},
                        new String[]{"--hot-min-limit", "10", "--hot-max-limit", "9", "help"},
                        new String[]{"invalid"},
                        new String[]{"report", "/dev/null"});
        invalidArgs.forEach(args -> assertFalse(Profdiff.mainImpl(args)));
    }
}
