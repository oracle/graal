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
package com.oracle.truffle.llvm.test.inlineassembly;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.test.LLVMPaths;
import com.oracle.truffle.llvm.test.RemoteTestSuiteBase;
import com.oracle.truffle.llvm.test.TestCaseFiles;
import com.oracle.truffle.llvm.test.spec.SpecificationEntry;
import com.oracle.truffle.llvm.test.spec.SpecificationFileReader;

@RunWith(Parameterized.class)
public class LLVMInlineAssemblyTest extends RemoteTestSuiteBase {

    private final TestCaseFiles tuple;

    public LLVMInlineAssemblyTest(TestCaseFiles testCase) {
        this.tuple = testCase;
    }

    @Parameterized.Parameters
    public static List<TestCaseFiles[]> getTestCases() {
        List<String> testCaseFileNames = new ArrayList<>();
        for (File file : LLVMPaths.INLINEASSEMBLY_TESTS.listFiles()) {
            testCaseFileNames.add(file.getName());
        }
        List<SpecificationEntry> testCaseFileSpecList = SpecificationFileReader.getFiles(testCaseFileNames, LLVMPaths.INLINEASSEMBLY_TESTS);
        return collectIncludedFiles(testCaseFileSpecList, new TestCaseGeneratorImpl(false, LLVMBaseOptionFacade.testBinaryParser()));
    }

    @Test
    public void test() throws Throwable {
        remoteLaunchAndTest(tuple);
    }
}
