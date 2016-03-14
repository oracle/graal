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
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.llvm.runtime.LLVMOptions;
import com.oracle.truffle.llvm.tools.util.ProcessUtil.ProcessResult;

@RunWith(Parameterized.class)
public class NWCCTestSuite extends RemoteTestSuiteBase {

    private final File bitCodeFile;
    private TestCaseFiles tuple;

    public NWCCTestSuite(TestCaseFiles tuple) {
        this.tuple = tuple;
        this.bitCodeFile = tuple.getBitCodeFile();
    }

    @Parameterized.Parameters
    public static List<TestCaseFiles[]> getTestFiles() throws IOException, AssertionError {
        File configFile = LLVMPaths.NWCC_TEST_SUITE_CONFIG;
        File testSuite = LLVMPaths.NWCC_TEST_SUITE;
        return getTestCasesFromConfigFile(configFile, testSuite, new TestCaseGeneratorImpl());
    }

    @Test
    public void test() throws Throwable {
        if (LLVMOptions.debugEnabled()) {
            System.out.println("original file: " + tuple.getOriginalFile());
        }
        try {
            List<String> launchRemote = launchRemote(tuple);
            int sulongRetValue = parseAndRemoveReturnValue(launchRemote);
            String sulongLines = launchRemote.stream().collect(Collectors.joining());
            ProcessResult processResult = TestHelper.executeLLVMBinary(bitCodeFile);
            String expectedLines = processResult.getStdInput();
            int expectedReturnValue = processResult.getReturnValue();
            boolean pass = expectedLines.equals(sulongLines) && expectedReturnValue == sulongRetValue;
            recordTestCase(tuple, pass);
            assertEquals(bitCodeFile.getAbsolutePath(), expectedLines, sulongLines);
            assertEquals(bitCodeFile.getAbsolutePath(), expectedReturnValue, sulongRetValue);
        } catch (Throwable e) {
            recordError(tuple, e);
            throw e;
        }
    }

}
