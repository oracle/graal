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
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.llvm.LLVM;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;

@RunWith(Parameterized.class)
public class TestGCCSuite extends TestSuiteBase {

    private static final int UNSIGNED_BYTE_MAX_VALUE = 256;
    private TestCaseFiles tuple;
    private File byteCodeFile;

    public TestGCCSuite(TestCaseFiles tuple) {
        this.tuple = tuple;
        this.byteCodeFile = tuple.getBitCodeFile();
    }

    @Parameterized.Parameters
    public static List<TestCaseFiles[]> getTestFiles() throws IOException {
        File configFile = LLVMPaths.GCC_TEST_SUITE_CONFIG;
        File testSuite = LLVMPaths.GCC_TEST_SUITE;
        LLVMLogger.info("...start to read and compile files");
        List<TestCaseFiles[]> files = getTestCasesFromConfigFile(configFile, testSuite, new TestCaseGeneratorImpl());
        LLVMLogger.info("...finished reading and compiling files!");
        return files;
    }

    @Test
    public void test() {
        try {
            LLVMLogger.info("original file: " + tuple.getOriginalFile());
            int expectedResult;
            try {
                expectedResult = TestHelper.executeLLVMBinary(byteCodeFile).getReturnValue();
            } catch (Throwable t) {
                t.printStackTrace();
                throw new LLVMUnsupportedException(UnsupportedReason.CLANG_ERROR);
            }
            int truffleResult = truncate(LLVM.executeMain(byteCodeFile));
            boolean undefinedReturnCode = tuple.hasFlag(TestCaseFlag.UNDEFINED_RETURN_CODE);
            boolean pass = true;
            if (!undefinedReturnCode) {
                pass &= expectedResult == truffleResult;
            }
            recordTestCase(tuple, pass);
            if (!undefinedReturnCode) {
                Assert.assertEquals(byteCodeFile.getAbsolutePath(), expectedResult, truffleResult);
            }
        } catch (Throwable e) {
            recordError(tuple, e);
            throw e;
        }
    }

    private static int truncate(int retValue) {
        return retValue & (UNSIGNED_BYTE_MAX_VALUE - 1);
    }

}
