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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.llvm.test.AbstractMainArgsTestBase.ProgramWithMainArgs;
import com.oracle.truffle.llvm.test.LLVMMainArgTestSuite.MainArgsTests;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions.OptimizationLevel;

@RunWith(Parameterized.class)
public class LLVMMainArgTestSuite extends AbstractMainArgsTestBase<MainArgsTests> {

    public LLVMMainArgTestSuite(MainArgsTests prog) {
        super(prog);
    }

    enum MainArgsTests implements ProgramWithMainArgs {

        ZERO_INPUT(1),
        TEST_ONE_ARG(194, "test"),
        TEST_TWO_ARGS(96, "hello", "world!"),
        TEST_THREE_ARGS(154, "1", "2", "3"),
        TEST_FOUR_ARGS(193, "a", "b", "cd", "efg");

        private final String[] args;
        private final int expectedReturnValue;

        MainArgsTests(int expectedReturnValue, String... args) {
            this.expectedReturnValue = expectedReturnValue;
            this.args = args;
        }

        @Override
        public File getFile() {
            return new File(LLVMPaths.PROJECT_ROOT, "/main-args.c");
        }

        @Override
        public List<String> getMainArgs() {
            return Arrays.asList(args);
        }

        @Override
        public Set<TestCaseFlag> getFlags() {
            return Collections.emptySet();
        }

        public int getExpectedReturnValue() {
            return expectedReturnValue;
        }

    }

    @Parameterized.Parameters
    public static List<MainArgsTests[]> getTestFiles() {
        return getTestFiles(Arrays.asList(MainArgsTests.values()));
    }

    @Override
    protected TestCaseFiles getTestCaseFiles(MainArgsTests prog) {
        return TestHelper.compileToLLVMIRWithClang(prog.getFile(),
                        TestHelper.getTempLLFile(prog.getFile(), "_main"), prog.getFlags(),
                        ClangOptions.builder().optimizationLevel(OptimizationLevel.NONE));
    }

    @Test
    @Override
    public void test() throws Throwable {
        super.test();
    }

    @Override
    protected int getExpectedReturnValue() {
        return program.getExpectedReturnValue();
    }

}
