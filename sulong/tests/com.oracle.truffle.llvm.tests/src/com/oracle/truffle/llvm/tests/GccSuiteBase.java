/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.tests.options.TestOptions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.function.Predicate;

public abstract class GccSuiteBase extends BaseSuiteHarness {

    @Override
    protected Predicate<String> filterFileName() {
        return s -> !s.endsWith("clangcpp_O0.bc");
    }

    static void printStatistics(Class<?> testSuiteClass, String source) {
        printStatistics(Paths.get(TestOptions.getSourcePath(source)), TestCaseCollector.getConfigDirectory(testSuiteClass));
    }

    static void printStatistics(Path sourceDir, Path configDir) {
        HashSet<Path> ignoredFolders = new HashSet<>();
        ignoredFolders.add(Paths.get("gcc-5.2.0/gcc/testsuite/gcc.c-torture/compile"));
        ignoredFolders.add(Paths.get("gcc-5.2.0/gcc/testsuite/gfortran.fortran-torture/compile"));
        ignoredFolders.add(Paths.get("gcc-5.2.0/gcc/testsuite/objc/compile"));
        ignoredFolders.add(Paths.get("gcc-5.2.0/gcc/testsuite/gcc.dg/noncompile"));

        printStatistics("GCC", sourceDir, configDir, f -> !f.toString().contains("/compile/") && !f.toString().contains("/noncompile/"));

        // gcc torture execute only
        printStatistics("gcc.c-torture/execute", Paths.get(sourceDir.toAbsolutePath().toString(), "gcc-5.2.0/gcc/testsuite/gcc.c-torture/execute"),
                        Paths.get(configDir.toAbsolutePath().toString(), "gcc.c-torture", "execute"),
                        t -> true);
    }
}
