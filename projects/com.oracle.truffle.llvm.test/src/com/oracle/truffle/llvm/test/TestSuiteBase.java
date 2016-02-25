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
package com.oracle.truffle.llvm.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.oracle.truffle.llvm.runtime.LLVMOptions;
import com.oracle.truffle.llvm.runtime.LLVMParserException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.test.spec.SpecificationFileReader;
import com.oracle.truffle.llvm.test.spec.TestSpecification;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions.OptimizationLevel;
import com.oracle.truffle.llvm.tools.GCC;
import com.oracle.truffle.llvm.tools.Opt;
import com.oracle.truffle.llvm.tools.Opt.OptOptions;
import com.oracle.truffle.llvm.tools.Opt.OptOptions.Pass;
import com.oracle.truffle.llvm.tools.ProgrammingLanguage;

public abstract class TestSuiteBase {

    private static List<File> failingTests;
    private static List<File> succeedingTests;
    private static List<File> parserErrorTests;
    private static Map<UnsupportedReason, List<File>> unsupportedErrorTests;

    void recordTestCase(TestCaseFiles tuple, boolean pass) {
        if (pass) {
            if (!succeedingTests.contains(tuple.getOriginalFile()) && !failingTests.contains(tuple.getOriginalFile())) {
                succeedingTests.add(tuple.getOriginalFile());
            }
        } else {
            if (!failingTests.contains(tuple.getOriginalFile())) {
                failingTests.add(tuple.getOriginalFile());
            }
        }
    }

    void recordError(TestCaseFiles tuple, Throwable error) {
        Throwable currentError = error;
        if (!failingTests.contains(tuple.getOriginalFile())) {
            failingTests.add(tuple.getOriginalFile());
        }
        while (currentError != null) {
            if (currentError instanceof LLVMParserException) {
                if (!parserErrorTests.contains(tuple.getOriginalFile())) {
                    parserErrorTests.add(tuple.getOriginalFile());
                }
                break;
            } else if (currentError instanceof LLVMUnsupportedException) {
                List<File> list = unsupportedErrorTests.get(((LLVMUnsupportedException) currentError).getReason());
                if (!list.contains(tuple.getOriginalFile())) {
                    list.add(tuple.getOriginalFile());
                }
                break;
            }
            currentError = currentError.getCause();
        }
    }

    private static final int LIST_MIN_SIZE = 1000;

    @BeforeClass
    public static void beforeClass() {
        succeedingTests = new ArrayList<>(LIST_MIN_SIZE);
        failingTests = new ArrayList<>(LIST_MIN_SIZE);
        parserErrorTests = new ArrayList<>(LIST_MIN_SIZE);
        unsupportedErrorTests = new HashMap<>(LIST_MIN_SIZE);
        for (UnsupportedReason reason : UnsupportedReason.values()) {
            unsupportedErrorTests.put(reason, new ArrayList<>(LIST_MIN_SIZE));
        }
    }

    static void printList(String header, List<File> files) {
        if (files.size() != 0) {
            System.out.println(header + " (" + files.size() + "):");
            files.stream().forEach(t -> System.out.println(t));
        }
    }

    @After
    public void displaySummary() {
        if (LLVMOptions.debugEnabled()) {
            if (LLVMOptions.discoveryTestModeEnabled()) {
                printList("succeeding tests:", succeedingTests);
            } else {
                printList("failing tests:", failingTests);
            }
            printList("parser error tests", parserErrorTests);
            for (UnsupportedReason reason : UnsupportedReason.values()) {
                printList("unsupported test " + reason, unsupportedErrorTests.get(reason));
            }
        }
    }

    @AfterClass
    public static void displayEndSummary() {
        if (!LLVMOptions.discoveryTestModeEnabled()) {
            printList("failing tests:", failingTests);
        }
    }

    interface TestCaseGenerator {

        ProgrammingLanguage[] getSupportedLanguages();

        TestCaseFiles getBitCodeTestCaseFiles(File bitCodeFile);

        List<TestCaseFiles> getCompiledTestCaseFiles(File toBeCompiled);
    }

    static class TestCaseGeneratorImpl implements TestCaseGenerator {

        public TestCaseFiles getBitCodeTestCaseFiles(File bitCodeFile) {
            return TestCaseFiles.createFromBitCodeFile(bitCodeFile);
        }

        public List<TestCaseFiles> getCompiledTestCaseFiles(File toBeCompiled) {
            List<TestCaseFiles> files = new ArrayList<>();
            File dest = TestHelper.getTempLLFile(toBeCompiled, "_main");
            if (ProgrammingLanguage.FORTRAN.isFile(toBeCompiled)) {
                files.add(TestHelper.compileToLLVMIRWithGCC(toBeCompiled, dest));
            } else {
                ClangOptions builder = ClangOptions.builder().optimizationLevel(OptimizationLevel.NONE);
                try {
                    TestCaseFiles compiledFiles = TestHelper.compileToLLVMIRWithClang(toBeCompiled, dest, builder);
                    files.add(compiledFiles);
                    files.add(optimize(compiledFiles, OptOptions.builder().pass(Pass.MEM_TO_REG).pass(Pass.ALWAYS_INLINE).pass(Pass.JUMP_THREADING).pass(Pass.SIMPLIFY_CFG), "opt"));
                } catch (Exception e) {
                    return Collections.emptyList();
                }

            }

            return files;
        }

        public ProgrammingLanguage[] getSupportedLanguages() {
            return GCC.getSupportedLanguages();
        }

    }

    static List<TestCaseFiles[]> getTestCasesFromConfigFile(File configFile, File testSuite, TestCaseGenerator gen) throws IOException, AssertionError {
        TestSpecification testSpecification = SpecificationFileReader.readSpecificationFolder(configFile, testSuite);
        List<File> includedFiles = testSpecification.getIncludedFiles();
        if (LLVMOptions.discoveryTestModeEnabled()) {
            List<File> excludedFiles = testSpecification.getExcludedFiles();
            File absoluteDiscoveryPath = new File(testSuite.getAbsolutePath(), LLVMOptions.getTestDiscoveryPath());
            assert absoluteDiscoveryPath.exists() : absoluteDiscoveryPath.toString();
            if (LLVMOptions.debugEnabled()) {
                System.out.println("\tcollect files");
            }
            List<File> filesToRun = getFilesRecursively(absoluteDiscoveryPath, gen);
            for (File alreadyCanExecute : includedFiles) {
                filesToRun.remove(alreadyCanExecute);
            }
            for (File excludedFile : excludedFiles) {
                filesToRun.remove(excludedFile);
            }
            List<TestCaseFiles[]> discoveryTestCases = new ArrayList<>();
            for (File f : filesToRun) {
                if (ProgrammingLanguage.LLVM.isFile(f)) {
                    TestCaseFiles testCase = gen.getBitCodeTestCaseFiles(f);
                    discoveryTestCases.add(new TestCaseFiles[]{testCase});
                } else {
                    List<TestCaseFiles> testCases = gen.getCompiledTestCaseFiles(f);
                    for (TestCaseFiles testCase : testCases) {
                        discoveryTestCases.add(new TestCaseFiles[]{testCase});
                    }
                }
            }
            if (LLVMOptions.debugEnabled()) {
                System.out.println("\tfinished collecting files");
            }
            return discoveryTestCases;
        } else {
            List<TestCaseFiles[]> includedFileTestCases = collectIncludedFiles(includedFiles, gen);
            return includedFileTestCases;
        }
    }

    private static List<TestCaseFiles[]> collectIncludedFiles(List<File> specificationFiles, TestCaseGenerator gen) throws AssertionError {
        List<TestCaseFiles[]> files = new ArrayList<>();
        for (File f : specificationFiles) {
            if (f.isFile()) {
                if (ProgrammingLanguage.LLVM.isFile(f)) {
                    files.add(new TestCaseFiles[]{gen.getBitCodeTestCaseFiles(f)});
                } else {
                    for (TestCaseFiles testCaseFile : gen.getCompiledTestCaseFiles(f)) {
                        files.add(new TestCaseFiles[]{testCaseFile});
                    }
                }
            } else {
                throw new AssertionError("could not find specified test file " + f);
            }
        }
        return files;
    }

    private static List<File> getFilesRecursively(File currentFolder, TestCaseGenerator gen) {
        List<File> allBitcodeFiles = new ArrayList<>(1000);
        List<File> cFiles = TestHelper.collectFilesWithExtension(currentFolder, gen.getSupportedLanguages());
        allBitcodeFiles.addAll(cFiles);
        return allBitcodeFiles;
    }

    static List<TestCaseFiles> applyOpt(List<TestCaseFiles> allBitcodeFiles, OptOptions pass, String name) {
        return getFilteredOptStream(allBitcodeFiles).map(f -> optimize(f, pass, name)).collect(Collectors.toList());
    }

    static Stream<TestCaseFiles> getFilteredOptStream(List<TestCaseFiles> allBitcodeFiles) {
        return allBitcodeFiles.parallelStream().filter(f -> !f.getOriginalFile().getParent().endsWith(LLVMPaths.NO_OPTIMIZATIONS_FOLDER_NAME));
    }

    static TestCaseFiles optimize(TestCaseFiles toBeOptimized, OptOptions optOptions, String name) {
        File destinationFile = TestHelper.getTempLLFile(toBeOptimized.getOriginalFile(), "_" + name);
        Opt.optimizeBitcodeFile(toBeOptimized.getBitCodeFile(), destinationFile, optOptions);
        return TestCaseFiles.createFromCompiledFile(toBeOptimized.getOriginalFile(), destinationFile);
    }

}
