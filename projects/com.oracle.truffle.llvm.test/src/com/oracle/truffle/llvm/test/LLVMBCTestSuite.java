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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.llvm.runtime.LLVMLogger;

@RunWith(Parameterized.class)
public class LLVMBCTestSuite extends RemoteTestSuiteBase {

    private static final int TEST_TIMEOUT_TIME = 15000;

    private final File bitCodeFile;
    private final File expectedFile;
    private final File originalFile;

    private TestCaseFiles tuple;

    public LLVMBCTestSuite(TestCaseFiles tuple) {
        this.tuple = tuple;
        this.bitCodeFile = tuple.getBitCodeFile();
        this.originalFile = tuple.getOriginalFile();
        this.expectedFile = tuple.getExpectedResult();
    }

    @Parameterized.Parameters
    public static List<TestCaseFiles[]> getTestFiles() throws IOException {
        File configFile = LLVMPaths.LLVMBC_TEST_SUITE_CONFIG;
        File testSuite = LLVMPaths.LLVMBC_TEST_SUITE;
        return getTestCasesFromConfigFile(configFile, testSuite, new LLVMSuiteTestCaseGenerator(false), false);
    }

    @Test(timeout = TEST_TIMEOUT_TIME)
    public void test() throws IOException {
        LLVMLogger.info("current file: " + originalFile);
        List<String> expectedLines;
        int expectedReturnValue;
        try {
            expectedLines = Files.readAllLines(Paths.get(expectedFile.getAbsolutePath()));
            expectedReturnValue = parseAndRemoveReturnValue(expectedLines);
        } catch (Exception e) {
            expectedLines = new ArrayList<>();
            expectedReturnValue = 0;
        }
        List<String> actualLines = launchRemote(tuple);
        int actualReturnValue = parseAndRemoveReturnValue(actualLines);
        boolean pass = expectedLines.equals(actualLines);
        boolean undefinedReturnCode = tuple.hasFlag(TestCaseFlag.UNDEFINED_RETURN_CODE);
        if (!undefinedReturnCode) {
            pass &= expectedReturnValue == actualReturnValue;
        }
        recordTestCase(tuple, pass);
        assertEquals(bitCodeFile.getAbsolutePath(), expectedLines, actualLines);
        if (!undefinedReturnCode) {
            assertEquals(bitCodeFile.getAbsolutePath(), expectedReturnValue, actualReturnValue);
        }
    }

}
