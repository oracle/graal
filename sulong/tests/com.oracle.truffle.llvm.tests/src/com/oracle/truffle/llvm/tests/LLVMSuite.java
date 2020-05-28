/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.tests.options.TestOptions;

@RunWith(Parameterized.class)
public final class LLVMSuite extends BaseSuiteHarness {

    private static final Path LLVM_SUITE_DIR = new File(TestOptions.EXTERNAL_TEST_SUITE_PATH).toPath();
    private static final Path LLVM_SOURCE_DIR = new File(TestOptions.TEST_SOURCE_PATH).toPath();
    private static final Path LLVM_CONFIG_DIR = new File(TestOptions.TEST_CONFIG_PATH).toPath();

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public String testName;

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        Set<String> blacklist = getBlacklist();
        Collection<Object[]> testlist = collectTestCases(LLVM_CONFIG_DIR, LLVM_SUITE_DIR, LLVM_SOURCE_DIR);
        testlist.removeIf(t -> blacklist.contains(t[1]));
        return testlist;
    }

    protected static Set<String> getBlacklist() {
        Set<String> filenameBlacklist = new HashSet<>();

        if (Platform.isAArch64()) {
            // Tests that cause the JVM to crash.
            filenameBlacklist.addAll(Arrays.asList(
                            "test-suite-3.2.src/SingleSource/Regression/C/PR640.c",
                            "test-suite-3.2.src/SingleSource/UnitTests/2003-05-07-VarArgs.c",
                            "test-suite-3.2.src/SingleSource/UnitTests/2003-07-09-SignedArgs.c",
                            "test-suite-3.2.src/SingleSource/UnitTests/2003-08-11-VaListArg.c",
                            "test-suite-3.2.src/SingleSource/UnitTests/2007-03-02-VaCopy.c",
                            "test-suite-3.2.src/SingleSource/UnitTests/2009-12-07-StructReturn.c"));
            // Tests that fail.
            filenameBlacklist.addAll(Arrays.asList(
                            "test-suite-3.2.src/SingleSource/Regression/C++/2008-01-29-ParamAliasesReturn.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C/globalrefs.c",
                            "test-suite-3.2.src/SingleSource/UnitTests/2006-01-23-UnionInit.c"));
            // Bitcode libc++ causes a segfault during a destructor (atexit) call.
            // See https://github.com/oracle/graal/issues/2276
            // Blacklist all c++ tests for now
            filenameBlacklist.addAll(Arrays.asList(
                            "test-suite-3.2.src/SingleSource/Regression/C++/pointer_member.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/2003-05-14-array-init.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/2011-03-28-Bitfield.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/global_type.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/2003-05-14-expr_stmt.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/2003-08-20-EnumSizeProblem.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/2003-06-13-Crasher.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/2008-01-29-ParamAliasesReturn.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/2003-06-08-BaseType.cpp",
                            "test-suite-3.2.src/SingleSource/Regression/C++/2003-06-08-VirtualFunctions.cpp",
                            "test-suite-3.2.src/SingleSource/UnitTests/conditional-gnu-ext-cxx.cpp",
                            "test-suite-3.2.src/SingleSource/UnitTests/SignlessTypes/cast2.cpp",
                            "test-suite-3.2.src/SingleSource/UnitTests/Integer/enum.cpp",
                            "test-suite-3.2.src/SingleSource/UnitTests/Integer/cppfield.cpp",
                            "test-suite-3.2.src/SingleSource/UnitTests/2006-12-04-DynAllocAndRestore.cpp",
                            "test-suite-3.2.src/SingleSource/UnitTests/Integer/template2.cpp",
                            "test-suite-3.2.src/SingleSource/UnitTests/initp1.cpp"));
        }

        return filenameBlacklist;
    }

    @Override
    protected Path getTestDirectory() {
        return path;
    }

    @AfterClass
    public static void printStatistics() {
        printStatistics("LLVM", LLVM_SOURCE_DIR, LLVM_CONFIG_DIR, f -> true);
    }

    @Override
    protected String getTestName() {
        return testName;
    }
}
