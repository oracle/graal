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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.llvm.LLVM;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.tools.Clang;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions.OptimizationLevel;
import com.oracle.truffle.llvm.tools.Opt.OptOptions;
import com.oracle.truffle.llvm.tools.Opt.OptOptions.Pass;
import com.oracle.truffle.llvm.tools.ProgrammingLanguage;

@RunWith(Parameterized.class)
/**
 * This class executes LLVM bitcode files (with file extension .ll) in the "test" case directory. If
 * other files that can be compiled to LLVM bitcode are encountered, they are compiled to bitcode
 * and then executed. Folders with the name of "ignore" are not executed. This test case class only
 * checks the program's return value.
 */
public class SulongTestSuite extends TestSuiteBase {

    private final File byteCodeFile;
    private TestCaseFiles tuple;

    public SulongTestSuite(TestCaseFiles tuple) {
        this.tuple = tuple;
        this.byteCodeFile = tuple.getBitCodeFile();
    }

    @Parameterized.Parameters
    public static List<TestCaseFiles[]> getTestFiles() {
        if (LLVMOptions.discoveryTestModeEnabled()) {
            throw new AssertionError("this suite does not have a discovery mode!");
        }
        return getFilesRecursively(LLVMPaths.LOCAL_TESTS);
    }

    private static List<TestCaseFiles[]> getFilesRecursively(File currentFolder) {
        List<TestCaseFiles> allBitcodeFiles = new ArrayList<>();
        List<File> byteCodeFiles = TestHelper.collectFilesWithExtension(currentFolder, ProgrammingLanguage.LLVM);
        allBitcodeFiles.addAll(byteCodeFiles.stream().map(t -> TestCaseFiles.createFromBitCodeFile(t, Collections.emptySet())).collect(Collectors.toList()));
        List<File> cFiles = TestHelper.collectFilesWithExtension(currentFolder, Clang.getSupportedLanguages());
        allBitcodeFiles.addAll(getClangCompiledFiles(cFiles, OptimizationLevel.NONE, false));
        List<TestCaseFiles> optimizedFiles = new ArrayList<>();
        OptOptions optionBuilder = OptOptions.builder();
        OptOptions passes1 = optionBuilder.pass(Pass.FUNC_ATTRS).pass(Pass.INST_COMBINE).pass(Pass.ALWAYS_INLINE);
        OptOptions passes2 = passes1.pass(Pass.JUMP_THREADING).pass(Pass.SIMPLIFY_CFG).pass(Pass.MEM_TO_REG);
        OptOptions vectorizationPass = passes2.pass(Pass.SCALAR_REPLACEMENT_AGGREGATES).pass(Pass.BASIC_BLOCK_VECTORIZE);
        allBitcodeFiles.addAll(getGCCCompiledFiles(cFiles));
        optimizedFiles.addAll(applyOpt(allBitcodeFiles, vectorizationPass, "-bb-vectorize"));
        allBitcodeFiles.addAll(getClangCompiledFiles(cFiles, OptimizationLevel.O1, true));
        allBitcodeFiles.addAll(getClangCompiledFiles(cFiles, OptimizationLevel.O2, true));
        allBitcodeFiles.addAll(getClangCompiledFiles(cFiles, OptimizationLevel.O3, true));
        allBitcodeFiles.addAll(optimizedFiles);
        return allBitcodeFiles.parallelStream().map(t -> new TestCaseFiles[]{t}).collect(Collectors.toList());
    }

    private static Collection<? extends TestCaseFiles> getGCCCompiledFiles(List<File> cFiles) {
        List<TestCaseFiles> compiledFiles = cFiles.parallelStream().map(file -> TestHelper.compileToLLVMIRWithGCC(file, TestHelper.getTempLLFile(file, "main"))).collect(Collectors.toList());
        return compiledFiles;
    }

    private static List<TestCaseFiles> getClangCompiledFiles(List<File> cFiles, OptimizationLevel level, boolean filter) {
        List<TestCaseFiles> compiledFiles = cFiles.parallelStream().map(file -> {
            ClangOptions optimizationLevel = ClangOptions.builder().optimizationLevel(level);
            return TestHelper.compileToLLVMIRWithClang(file, TestHelper.getTempLLFile(file, level.toString()), Collections.emptySet(), optimizationLevel);
        }).collect(Collectors.toList());
        if (filter) {
            return getFilteredOptStream(compiledFiles).collect(Collectors.toList());
        } else {
            return compiledFiles;
        }
    }

    @Test
    public void test() {
        try {
            int truffleResult = LLVM.executeMain(byteCodeFile);
            int expectedResult = TestHelper.executeLLVMBinary(byteCodeFile).getReturnValue();
            boolean pass = expectedResult == truffleResult;
            if (pass) {
                // if the test does not pass assertEquals will throw an AssertionError
                recordTestCase(tuple, pass);
            }
            Assert.assertEquals(byteCodeFile.getAbsolutePath(), expectedResult, truffleResult);
        } catch (Throwable e) {
            recordTestCase(tuple, false);
            throw new AssertionError(e);
        }
    }

}
