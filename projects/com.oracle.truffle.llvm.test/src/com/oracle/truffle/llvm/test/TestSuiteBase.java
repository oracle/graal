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

import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMParserException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.test.spec.SpecificationEntry;
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

    protected void recordTestCase(TestCaseFiles tuple, boolean pass) {
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

    protected void recordError(TestCaseFiles tuple, Throwable error) {
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

    protected static void printList(String header, List<File> files) {
        if (files.size() != 0) {
            LLVMLogger.info(header + " (" + files.size() + "):");
            files.stream().forEach(t -> LLVMLogger.info(t.toString()));
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

    protected interface TestCaseGenerator {

        ProgrammingLanguage[] getSupportedLanguages();

        TestCaseFiles getBitCodeTestCaseFiles(SpecificationEntry bitCodeFile);

        List<TestCaseFiles> getCompiledTestCaseFiles(SpecificationEntry toBeCompiled);
    }

    public static class TestCaseGeneratorImpl implements TestCaseGenerator {

        @Override
        public TestCaseFiles getBitCodeTestCaseFiles(SpecificationEntry bitCodeFile) {
            return TestCaseFiles.createFromBitCodeFile(bitCodeFile.getFile(), bitCodeFile.getFlags());
        }

        @Override
        public List<TestCaseFiles> getCompiledTestCaseFiles(SpecificationEntry toBeCompiled) {
            List<TestCaseFiles> files = new ArrayList<>();
            File toBeCompiledFile = toBeCompiled.getFile();
            File dest = TestHelper.getTempLLFile(toBeCompiledFile, "_main");
            try {
                if (ProgrammingLanguage.FORTRAN.isFile(toBeCompiledFile)) {
                    TestCaseFiles gccCompiledTestCase = TestHelper.compileToLLVMIRWithGCC(toBeCompiledFile, dest, toBeCompiled.getFlags());
                    files.add(gccCompiledTestCase);
                } else if (ProgrammingLanguage.C_PLUS_PLUS.isFile(toBeCompiledFile)) {
                    ClangOptions builder = ClangOptions.builder().optimizationLevel(OptimizationLevel.NONE);
                    OptOptions options = OptOptions.builder().pass(Pass.LOWER_INVOKE).pass(Pass.PRUNE_EH).pass(Pass.SIMPLIFY_CFG);
                    TestCaseFiles compiledFiles = TestHelper.compileToLLVMIRWithClang(toBeCompiledFile, dest, toBeCompiled.getFlags(), builder);
                    files.add(optimize(compiledFiles, options, "opt"));
                } else {
                    ClangOptions builder = ClangOptions.builder().optimizationLevel(OptimizationLevel.NONE);
                    try {
                        TestCaseFiles compiledFiles = TestHelper.compileToLLVMIRWithClang(toBeCompiledFile, dest, toBeCompiled.getFlags(), builder);
                        files.add(compiledFiles);
                        TestCaseFiles optimized = getOptimizedTestCase(compiledFiles);
                        files.add(optimized);
                    } catch (Exception e) {
                        return Collections.emptyList();
                    }
                }
            } catch (Exception e) {
                return Collections.emptyList();
            }
            return files;
        }

        private static TestCaseFiles getOptimizedTestCase(TestCaseFiles compiledFiles) {
            OptOptions options = OptOptions.builder().pass(Pass.MEM_TO_REG).pass(Pass.ALWAYS_INLINE).pass(Pass.JUMP_THREADING).pass(Pass.SIMPLIFY_CFG);
            TestCaseFiles optimize = optimize(compiledFiles, options, "opt");
            return optimize;
        }

        @Override
        public ProgrammingLanguage[] getSupportedLanguages() {
            return GCC.getSupportedLanguages();
        }

    }

    protected static List<TestCaseFiles[]> getTestCasesFromConfigFile(File configFile, File testSuite, TestCaseGenerator gen) throws IOException, AssertionError {
        TestSpecification testSpecification = SpecificationFileReader.readSpecificationFolder(configFile, testSuite);
        List<SpecificationEntry> includedFiles = testSpecification.getIncludedFiles();
        if (LLVMOptions.discoveryTestModeEnabled()) {
            List<SpecificationEntry> excludedFiles = testSpecification.getExcludedFiles();
            File absoluteDiscoveryPath = new File(testSuite.getAbsolutePath(), LLVMOptions.getTestDiscoveryPath());
            assert absoluteDiscoveryPath.exists() : absoluteDiscoveryPath.toString();
            LLVMLogger.info("\tcollect files");
            List<File> filesToRun = getFilesRecursively(absoluteDiscoveryPath, gen);
            for (SpecificationEntry alreadyCanExecute : includedFiles) {
                filesToRun.remove(alreadyCanExecute.getFile());
            }
            for (SpecificationEntry excludedFile : excludedFiles) {
                filesToRun.remove(excludedFile.getFile());
            }
            List<TestCaseFiles[]> discoveryTestCases = new ArrayList<>();
            for (File f : filesToRun) {
                if (ProgrammingLanguage.LLVM.isFile(f)) {
                    TestCaseFiles testCase = gen.getBitCodeTestCaseFiles(new SpecificationEntry(f));
                    discoveryTestCases.add(new TestCaseFiles[]{testCase});
                } else {
                    List<TestCaseFiles> testCases = gen.getCompiledTestCaseFiles(new SpecificationEntry(f));
                    for (TestCaseFiles testCase : testCases) {
                        discoveryTestCases.add(new TestCaseFiles[]{testCase});
                    }
                }
            }
            LLVMLogger.info("\tfinished collecting files");
            return discoveryTestCases;
        } else {
            List<TestCaseFiles[]> includedFileTestCases = collectIncludedFiles(includedFiles, gen);
            return includedFileTestCases;
        }
    }

    private static List<TestCaseFiles[]> collectIncludedFiles(List<SpecificationEntry> specificationEntries, TestCaseGenerator gen) throws AssertionError {
        List<TestCaseFiles[]> files = new ArrayList<>();
        for (SpecificationEntry e : specificationEntries) {
            File f = e.getFile();
            if (f.isFile()) {
                if (ProgrammingLanguage.LLVM.isFile(f)) {
                    files.add(new TestCaseFiles[]{gen.getBitCodeTestCaseFiles(e)});
                } else {
                    for (TestCaseFiles testCaseFile : gen.getCompiledTestCaseFiles(e)) {
                        files.add(new TestCaseFiles[]{testCaseFile});
                    }
                }
            } else {
                throw new AssertionError("could not find specified test file " + f);
            }
        }
        return files;
    }

    public static List<File> getFilesRecursively(File currentFolder, TestCaseGenerator gen) {
        List<File> allBitcodeFiles = new ArrayList<>(1000);
        List<File> cFiles = TestHelper.collectFilesWithExtension(currentFolder, gen.getSupportedLanguages());
        allBitcodeFiles.addAll(cFiles);
        return allBitcodeFiles;
    }

    protected static List<TestCaseFiles> applyOpt(List<TestCaseFiles> allBitcodeFiles, OptOptions pass, String name) {
        return getFilteredOptStream(allBitcodeFiles).map(f -> optimize(f, pass, name)).collect(Collectors.toList());
    }

    protected static Stream<TestCaseFiles> getFilteredOptStream(List<TestCaseFiles> allBitcodeFiles) {
        return allBitcodeFiles.parallelStream().filter(f -> !f.getOriginalFile().getParent().endsWith(LLVMPaths.NO_OPTIMIZATIONS_FOLDER_NAME));
    }

    protected static TestCaseFiles optimize(TestCaseFiles toBeOptimized, OptOptions optOptions, String name) {
        File destinationFile = TestHelper.getTempLLFile(toBeOptimized.getOriginalFile(), "_" + name);
        Opt.optimizeBitcodeFile(toBeOptimized.getBitCodeFile(), destinationFile, optOptions);
        return TestCaseFiles.createFromCompiledFile(toBeOptimized.getOriginalFile(), destinationFile, toBeOptimized.getFlags());
    }

}
