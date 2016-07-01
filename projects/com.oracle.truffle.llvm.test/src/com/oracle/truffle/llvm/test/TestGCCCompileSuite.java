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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLogger;

@RunWith(Parameterized.class)
public class TestGCCCompileSuite extends TestSuiteBase {

    private TestCaseFiles tuple;

    public TestGCCCompileSuite(TestCaseFiles tuple) {
        this.tuple = tuple;
    }

    @Parameterized.Parameters
    public static List<TestCaseFiles[]> getTestFiles() throws IOException {
        File configFile = LLVMPaths.GCC_TEST_SUITE_COMPILE_TORTURE_CONFIG;
        File testSuite = LLVMPaths.GCC_TEST_SUITE_COMPILE_TORTURE;
        LLVMLogger.info("...start to read and compile files");
        List<TestCaseFiles[]> files = getTestCasesFromConfigFile(configFile, testSuite, new TestCaseGeneratorImpl());
        LLVMLogger.info("...finished reading and compiling files!");
        return files;
    }

    @Test
    public void test() throws Throwable {
        try {
            LLVMLogger.info("original file: " + tuple.getOriginalFile());
            Builder engineBuilder = PolyglotEngine.newBuilder();
            engineBuilder.config(LLVMLanguage.LLVM_IR_MIME_TYPE, LLVMLanguage.PARSE_ONLY_KEY, true);
            PolyglotEngine build = engineBuilder.build();
            build.eval(Source.newBuilder(tuple.getBitCodeFile()).build());
            recordTestCase(tuple, true);
        } catch (Throwable e) {
            recordError(tuple, e);
            throw e;
        }
    }

}
