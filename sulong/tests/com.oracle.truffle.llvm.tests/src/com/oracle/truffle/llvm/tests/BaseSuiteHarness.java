/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.llvm.tests.util.ProcessUtil;
import com.oracle.truffle.llvm.tests.util.ProcessUtil.ProcessResult;
import org.junit.Assume;

public abstract class BaseSuiteHarness extends BaseTestHarness {

    private static final List<Path> passingTests = new ArrayList<>();
    private static final List<Path> failingTests = new ArrayList<>();

    /**
     * Maximum retries on timeout of the reference executable.
     *
     * @see #runReference
     */
    private static final int MAX_RETRIES = 3;

    protected Function<Context.Builder, CaptureOutput> getCaptureOutput() {
        return c -> new CaptureNativeOutput();
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
        String testName = candidateBinary.getFileName().toString() + " in " + getTestDirectory().toAbsolutePath().toString();
        try {
            Assert.assertEquals(testName, referenceResult, candidateResult);
        } catch (AssertionError e) {
            throw fail(getTestName(), e);
        }
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
            result = ProcessUtil.executeSulongTestMain(candidateBinary.toAbsolutePath().toFile(), inputArgs, getContextOptions(), getCaptureOutput());
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

    @Override
    @Test
    public void test() throws IOException {
        Path referenceBinary;
        ProcessResult referenceResult;
        try (Stream<Path> walk = Files.list(getTestDirectory())) {
            List<Path> files = walk.filter(isExecutable).collect(Collectors.toList());

            // some tests do not compile with certain versions of clang
            Assume.assumeFalse("reference binary missing", files.isEmpty());

            referenceBinary = files.get(0);
            referenceResult = runReference(referenceBinary);
        }

        try (Stream<Path> walk = Files.list(getTestDirectory())) {
            List<Path> testCandidates = walk.filter(isFile).filter(getIsSulongFilter()).collect(Collectors.toList());
            Assert.assertFalse("candidate list empty", testCandidates.isEmpty());
            for (Path candidate : testCandidates) {
                runCandidate(referenceBinary, referenceResult, candidate);
            }
            pass(getTestName());
        }
    }

    protected Predicate<? super Path> getIsSulongFilter() {
        return isSulong;
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
        Set<Path> whiteList = getListEntries(source, config, isIncludeFile);
        Set<Path> blackList = getListEntries(source, config, isExcludeFile);
        Set<Path> files = getFiles(source);
        Map<String, Integer> statisticTotalFiles = supportedFiles.stream().collect(Collectors.toMap(s -> s, s -> 0));
        Map<String, Integer> statisticTotalNoExcludeFiles = supportedFiles.stream().collect(Collectors.toMap(s -> s, s -> 0));
        Map<String, Integer> statisticSupportedFiles = supportedFiles.stream().collect(Collectors.toMap(s -> s, s -> 0));

        // count available test files
        for (Path f : files) {
            if (filter.test(f)) {
                String fileEnding = getFileEnding(f.toString());
                if (supportedFiles.contains(fileEnding)) {
                    statisticTotalFiles.put(fileEnding, statisticTotalFiles.get(fileEnding) + 1);
                }
            }
        }

        // count available test files minus blackList
        for (Path f : files) {
            if (filter.test(f) && !blackList.contains(f)) {
                String fileEnding = getFileEnding(f.toString());
                if (supportedFiles.contains(fileEnding)) {
                    statisticTotalNoExcludeFiles.put(fileEnding, statisticTotalNoExcludeFiles.get(fileEnding) + 1);
                }
            }
        }

        // count running test files
        for (Path f : whiteList) {
            if (filter.test(f)) {
                String fileEnding = getFileEnding(f.toString());
                if (supportedFiles.contains(fileEnding)) {
                    statisticSupportedFiles.put(fileEnding, statisticSupportedFiles.get(fileEnding) + 1);
                }
            }
        }

        System.out.println();
        System.out.println(String.format("================================= Statistics for %s suite ======================================", name));
        System.out.println("\tFILE\t|\tALL\t|\tRUNABLE\t|\tOK\t|\tOK/ALL\t|\tOK/RUNABLE\t");
        System.out.println("===================================================================================================");
        for (String kind : supportedFiles) {
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
            throw new AssertionError("Error creating whitelist.", e);
        }
    }
}
