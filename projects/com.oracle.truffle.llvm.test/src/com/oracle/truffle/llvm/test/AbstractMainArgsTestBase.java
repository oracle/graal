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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.llvm.LLVM;
import com.oracle.truffle.llvm.test.AbstractMainArgsTestBase.ProgramWithMainArgs;

public abstract class AbstractMainArgsTestBase<T extends ProgramWithMainArgs> extends TestSuiteBase {

    protected final T program;

    public interface ProgramWithMainArgs {

        /**
         * Gets the path to the program to be compiled.
         *
         * @return the file
         */
        File getFile();

        /**
         * Gets the <code>main</code> function's arguments.
         *
         * @return the <code>main</code> arguments
         */
        List<String> getMainArgs();

        Set<TestCaseFlag> getFlags();
    }

    public AbstractMainArgsTestBase(T program) {
        this.program = program;
    }

    @SuppressWarnings("unchecked")
    public static <T extends ProgramWithMainArgs> List<T[]> getTestFiles(List<T> programs) {
        List<T[]> files = new ArrayList<>();
        for (T program : programs) {
            files.add((T[]) new ProgramWithMainArgs[]{program});
        }
        return files;
    }

    public void test() throws Throwable {
        TestCaseFiles compileResult = getTestCaseFiles(program);
        int truffleResult;
        if (program.getMainArgs() != null && program.getMainArgs().size() != 0) {
            truffleResult = LLVM.executeMain(compileResult.getBitCodeFile(), program.getMainArgs().toArray(new Object[program.getMainArgs().size()]));
        } else {
            truffleResult = LLVM.executeMain(compileResult.getBitCodeFile());
        }
        if (!compileResult.hasFlag(TestCaseFlag.UNDEFINED_RETURN_CODE)) {
            assertEquals(getExpectedReturnValue(), truffleResult);
        }
    }

    protected int getExpectedReturnValue() {
        return 0;
    }

    protected abstract TestCaseFiles getTestCaseFiles(T prog);

}
