/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;

import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;
import com.oracle.truffle.llvm.tests.services.TestEngineConfig;
import com.oracle.truffle.llvm.tests.util.ProcessUtil;
import com.oracle.truffle.llvm.tests.util.ProcessUtil.ProcessResult;

/**
 * Base class for parameterized tests that run a {@link #getIsExecutableFilter() reference
 * executable} and compare the result against one or multiple {@link #getIsSulongFilter() bitcode
 * files}.
 */
public abstract class BaseSuiteHarness {

    /**
     * The absolute path to the test case. The test case is always a directory containing
     * {@link #getIsExecutableFilter() a reference executable} and {@link #getIsSulongFilter()
     * bitcode files}.
     */
    @Parameter(value = 0) public Path path;
    /**
     * The test case name. Usually {@link #path} relative to the test suite base directory.
     */
    @Parameter(value = 1) public String testName;
    /**
     * The reason why a test case should be excluded or {@code null} if the test case should not be
     * excluded.
     */
    @Parameter(value = 2) public String exclusionReason;

    protected Path getTestDirectory() {
        return path;
    }

    protected String getTestName() {
        return testName;
    }

    protected String getExclusionReason() {
        return exclusionReason;
    }

    private static final List<Path> passingTests = new ArrayList<>();
    private static final List<Path> failingTests = new ArrayList<>();
    private static final Map<String, String> ignoredTests = new HashMap<>();
    private static Engine engine;

    /**
     * Maximum retries on timeout of the reference executable.
     *
     * @see #runReference
     */
    private static final int MAX_RETRIES = 3;

    protected Function<Context.Builder, CaptureOutput> getCaptureOutput() {
        return TestEngineConfig.getInstance().getCaptureOutput();
    }

    /**
     * This function is used to look up the array of string arguments to be used to run a given
     * binary. Subclasses should override this to provide their own arguments.
     *
     * @param executable The executable for which the stdin arguments should be looked up
     * @return An array of Strings specifying the list of stdin arguments
     */
    protected String[] getInputArgs(Path executable) {
        return new String[]{};
    }

    /**
     * Validate the results of the candidate sulong binary against the output of the reference
     * binary. On failure, the function will throw an unchecked exception.
     *
     * @param referenceBinary Name of the reference binary.
     * @param referenceResult Output from the reference binary.
     * @param candidateBinary Name of the candidate binary.
     * @param candidateResult Output from the candidate binary.
     */
    protected void validateResults(Path referenceBinary, ProcessUtil.ProcessResult referenceResult,
                    Path candidateBinary, ProcessUtil.ProcessResult candidateResult) {
        String testCaseDescription = candidateBinary.getFileName().toString() + " in " + getTestDirectory().toAbsolutePath().toString();
        try {
            Assert.assertEquals(testCaseDescription, referenceResult, candidateResult);
        } catch (AssertionError e) {
            throw fail(getTestName(), e);
        }
    }

    protected Map<String, String> getContextOptions() {
        return TestEngineConfig.getInstance().getContextOptions();
    }

    /**
     * This function can be overwritten to specify a filter on test file names. E.g. if one wants to
     * only run unoptimized files on Sulong, use <code> s.endsWith("O0.bc") </code>
     *
     * @return a filter predicate
     */
    protected Predicate<String> filterFileName() {
        if (TestOptions.TEST_FILTER != null && !TestOptions.TEST_FILTER.isEmpty()) {
            return s -> s.endsWith(TestOptions.TEST_FILTER);
        } else {
            return s -> true;
        }
    }

    @BeforeClass
    public static void createEngine() {
        engine = Engine.newBuilder().allowExperimentalOptions(true).build();
    }

    @AfterClass
    public static void disposeEngine() {
        engine.close();
    }

    private void runCandidate(Path referenceBinary, ProcessResult referenceResult, Path candidateBinary) {
        if (!filterFileName().test(candidateBinary.getFileName().toString())) {
            return;
        }
        if (!candidateBinary.toAbsolutePath().toFile().exists()) {
            throw fail(getTestName(), new AssertionError("File " + candidateBinary.toAbsolutePath().toFile() + " does not exist."));
        }

        String[] inputArgs = getInputArgs(candidateBinary);
        ProcessResult result;
        try {
            assert engine != null;
            result = ProcessUtil.executeSulongTestMainSameEngine(candidateBinary.toAbsolutePath().toFile(), inputArgs, getContextOptions(), getCaptureOutput(), engine);
        } catch (Exception e) {
            throw fail(getTestName(), new Exception("Candidate binary that failed: " + candidateBinary, e));
        }

        int sulongRet = result.getReturnValue();
        if (sulongRet != (sulongRet & 0xFF)) {
            throw fail(getTestName(), new AssertionError("Broken unittest " + getTestDirectory() + ". Test exits with invalid value: " + sulongRet));
        }

        validateResults(referenceBinary, referenceResult, candidateBinary, result);
    }

    private ProcessResult runReference(Path referenceBinary) {
        String[] inputArgs = getInputArgs(referenceBinary);
        String cmdlineArgs = String.join(" ", inputArgs);
        String cmd = String.join(" ", referenceBinary.toAbsolutePath().toString(), cmdlineArgs);
        int retries = 0;
        for (;;) {
            try {
                return ProcessUtil.executeNativeCommand(cmd);
            } catch (ProcessUtil.TimeoutError e) {
                /*
                 * Retry on timeout: This is the reference executable, if that's timing out, it's
                 * probably not our fault, but some infrastructure issue.
                 */
                if (retries++ >= MAX_RETRIES) {
                    throw e;
                }
            }
        }
    }

    @Test
    public void test() throws IOException {
        assumeNotExcluded();
        Path referenceBinary;
        ProcessResult referenceResult;
        try (Stream<Path> walk = Files.list(getTestDirectory())) {
            List<Path> files = walk.filter(getIsExecutableFilter()).collect(Collectors.toList());

            // some tests do not compile with certain versions of clang
            assumeFalse("reference binary missing", files.isEmpty());

            referenceBinary = files.get(0);
            referenceResult = runReference(referenceBinary);
        }

        try (Stream<Path> walk = Files.list(getTestDirectory())) {
            List<Path> testCandidates = walk.filter(CommonTestUtils.isFile).filter(getIsSulongFilter()).collect(Collectors.toList());
            Assert.assertFalse("candidate list empty", testCandidates.isEmpty());
            for (Path candidate : testCandidates) {
                runCandidate(referenceBinary, referenceResult, candidate);
            }
            pass(getTestName());
        }
    }

    /**
     * Safe-guard for tests that are not executed via
     * {@link CommonTestUtils.ExcludingParametersFactory}.
     */
    protected void assumeNotExcluded() {
        if (getExclusionReason() != null) {
            ignoredTests.put(getTestName(), getExclusionReason());
            throw new AssumptionViolatedException("Test excluded: " + getExclusionReason());
        }
    }

    protected void assumeFalse(String message, boolean b) {
        if (b) {
            ignoredTests.put(getTestName(), getExclusionReason());
            throw new AssumptionViolatedException(message);
        }
    }

    protected Predicate<? super Path> getIsSulongFilter() {
        return CommonTestUtils.isSulong;
    }

    protected Predicate<? super Path> getIsExecutableFilter() {
        return CommonTestUtils.isExecutable;
    }

    protected static AssertionError fail(String testName, AssertionError error) {
        failingTests.add(Paths.get(testName));
        return error;
    }

    protected static RuntimeException fail(String testName, Exception e) {
        failingTests.add(Paths.get(testName));
        return new RuntimeException(e);
    }

    protected static void pass(String testName) {
        passingTests.add(Paths.get(testName));
    }

    @BeforeClass
    public static void resetDiscoveryReport() {
        passingTests.clear();
        failingTests.clear();
    }

    @AfterClass
    public static void reportDiscoveryReport() {
        String testDiscoveryPath = TestOptions.TEST_DISCOVERY_PATH;
        if (testDiscoveryPath != null) {
            System.out.println("PASSING:");
            System.out.println(passingTests.stream().map(p -> p.toString()).collect(Collectors.joining("\n")));
            System.out.println("FAILING:");
            System.out.println(failingTests.stream().map(p -> p.toString()).collect(Collectors.joining("\n")));
        }
    }

    private static final int PERCENT = 100;

    protected static void printStatistics(String name, Path source, Path config, Predicate<Path> filter) {
        Set<Path> includeList = getListEntries(source, config, CommonTestUtils.isIncludeFile);
        Set<Path> excludeList = getListEntries(source, config, CommonTestUtils.isExcludeFile);
        Set<Path> files = CommonTestUtils.getFiles(source);
        Map<String, Integer> statisticTotalFiles = CommonTestUtils.supportedFiles.stream().collect(Collectors.toMap(s -> s, s -> 0));
        Map<String, Integer> statisticTotalNoExcludeFiles = CommonTestUtils.supportedFiles.stream().collect(Collectors.toMap(s -> s, s -> 0));
        Map<String, Integer> statisticSupportedFiles = CommonTestUtils.supportedFiles.stream().collect(Collectors.toMap(s -> s, s -> 0));

        // count available test files
        for (Path f : files) {
            if (filter.test(f)) {
                String fileEnding = CommonTestUtils.getFileEnding(f.toString());
                if (CommonTestUtils.supportedFiles.contains(fileEnding)) {
                    statisticTotalFiles.put(fileEnding, statisticTotalFiles.get(fileEnding) + 1);
                }
            }
        }

        // count available test files minus excludeList
        for (Path f : files) {
            if (filter.test(f) && !excludeList.contains(f)) {
                String fileEnding = CommonTestUtils.getFileEnding(f.toString());
                if (CommonTestUtils.supportedFiles.contains(fileEnding)) {
                    statisticTotalNoExcludeFiles.put(fileEnding, statisticTotalNoExcludeFiles.get(fileEnding) + 1);
                }
            }
        }

        // count running test files
        for (Path f : includeList) {
            if (filter.test(f)) {
                String fileEnding = CommonTestUtils.getFileEnding(f.toString());
                if (CommonTestUtils.supportedFiles.contains(fileEnding)) {
                    statisticSupportedFiles.put(fileEnding, statisticSupportedFiles.get(fileEnding) + 1);
                }
            }
        }

        System.out.println();
        System.out.println(String.format("================================= Statistics for %s suite ======================================", name));
        System.out.println("\tFILE\t|\tALL\t|\tRUNABLE\t|\tOK\t|\tOK/ALL\t|\tOK/RUNABLE\t");
        System.out.println("===================================================================================================");
        for (String kind : CommonTestUtils.supportedFiles) {
            double total = statisticTotalFiles.get(kind);
            double totalNoExclude = statisticTotalNoExcludeFiles.get(kind);
            double supported = statisticSupportedFiles.get(kind);
            if (total > 0) {
                double ratioTotal = supported / total * PERCENT;
                double ratioNoExclude = supported / totalNoExclude * PERCENT;
                System.out.println(String.format("\t%s\t|\t%d\t|\t%d\t|\t%d\t|\t%.1f%%\t|\t%.1f%%\t", kind, (int) total, (int) totalNoExclude, (int) supported, ratioTotal, ratioNoExclude));
            }
        }
        System.out.println("---------------------------------------------------------------------------------------------------");
        double total = statisticTotalFiles.values().stream().mapToInt(i -> i).sum();
        double totalNoExclude = statisticTotalNoExcludeFiles.values().stream().mapToInt(i -> i).sum();
        double supported = statisticSupportedFiles.values().stream().mapToInt(i -> i).sum();
        if (total > 0) {
            double ratioTotal = supported / total * PERCENT;
            double ratioNoExclude = supported / totalNoExclude * PERCENT;
            System.out.println(String.format("\t%s\t|\t%d\t|\t%d\t|\t%d\t|\t%.1f%%\t|\t%.1f%%\t", "*.*", (int) total, (int) totalNoExclude, (int) supported, ratioTotal, ratioNoExclude));
        } else {
            System.out.println("   No data available.");
        }
        if (ignoredTests.size() > 0) {
            System.out.printf("\nIgnored %d tests\n\n", ignoredTests.size());
        }
    }

    private static Set<Path> getListEntries(Path suiteDirectory, Path configDir, Predicate<? super Path> filter) {
        try (Stream<Path> files = Files.walk(configDir)) {
            Set<Path> results = new HashSet<>();
            for (Path path : (Iterable<Path>) (files.filter(filter))::iterator) {
                try (Stream<String> lines = Files.lines(path)) {
                    for (String line : (Iterable<String>) lines::iterator) {
                        results.add(new File(suiteDirectory.getParent().toString(), line).toPath());
                    }
                }
            }
            return results;
        } catch (IOException e) {
            throw new AssertionError("Error creating test filter list.", e);
        }
    }
}
