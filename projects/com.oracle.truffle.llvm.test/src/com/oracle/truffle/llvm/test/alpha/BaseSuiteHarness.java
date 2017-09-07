/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.test.alpha;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.llvm.test.options.TestOptions;
import com.oracle.truffle.llvm.test.util.ProcessUtil;
import com.oracle.truffle.llvm.test.util.ProcessUtil.ProcessResult;

public abstract class BaseSuiteHarness extends BaseTestHarness {

    private static final List<Path> passingTests = new ArrayList<>();
    private static final List<Path> failingTests = new ArrayList<>();

    @Override
    @Test
    public void test() throws Exception {
        final List<Path> files = Files.walk(getTestDirectory()).filter(isExecutable).collect(Collectors.toList());
        if (files.isEmpty()) {
            // some tests do not compile with certain versions of clang
            return;
        }
        Path referenceFile = files.get(0);
        List<Path> testCandidates = Files.walk(getTestDirectory()).filter(isFile).filter(getIsSulongFilter()).collect(Collectors.toList());
        ProcessResult processResult = ProcessUtil.executeNativeCommand(referenceFile.toAbsolutePath().toString());
        String referenceStdOut = processResult.getStdOutput();
        final int referenceReturnValue = processResult.getReturnValue();

        for (Path candidate : testCandidates) {
            if (!filterFileName().test(candidate.getFileName().toString())) {
                continue;
            }

            if (!candidate.toAbsolutePath().toFile().exists()) {
                fail(getTestName(), new AssertionError("File " + candidate.toAbsolutePath().toFile() + " does not exist."));
            }
            ProcessResult out = ProcessUtil.executeSulongTestMain(candidate.toAbsolutePath().toFile(), new String[]{});
            int sulongResult = out.getReturnValue();
            String sulongStdOut = out.getStdOutput();

            if (sulongResult != (sulongResult & 0xFF)) {
                fail(getTestName(), new AssertionError("Broken unittest " + getTestDirectory() + ". Test exits with invalid value."));
            }
            String testName = candidate.getFileName().toString() + " in " + getTestDirectory().toAbsolutePath().toString();
            if (referenceReturnValue != sulongResult) {
                fail(getTestName(), new AssertionError(testName + " failed. Posix return value missmatch. Expected: " + referenceReturnValue + " but was: " + sulongResult));
            }

            if (!referenceStdOut.equals(sulongStdOut)) {
                fail(getTestName(), new AssertionError(testName + " failed. Output (stdout) missmatch. Expected: " + referenceStdOut + " but was: " + sulongStdOut));
            }
        }
        pass(getTestName());
    }

    protected Predicate<? super Path> getIsSulongFilter() {
        return isSulong;
    }

    protected static void fail(String testName, AssertionError error) {
        failingTests.add(Paths.get(testName));
        throw error;
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
        try {
            return Files.walk(configDir).filter(filter).flatMap(f -> {
                try {
                    return Files.lines(f);
                } catch (IOException e) {
                    throw new AssertionError("Error creating whitelist.", e);
                }
            }).map(s -> new File(suiteDirectory.getParent().toString(), s).toPath()).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new AssertionError("Error creating whitelist.", e);
        }
    }

}
