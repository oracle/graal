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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.tests.options.TestOptions;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CommonTestUtils.ExcludingParametersFactory.class)
public final class ParserTortureSuite extends GccSuiteBase {

    public static final String TEST_DISTRIBUTION = "SULONG_PARSER_TORTURE";
    public static final String SOURCE = "GCC_SOURCE";

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        return TestCaseCollector.collectTestCases(ParserTortureSuite.class, Paths.get(TestOptions.getTestDistribution(TEST_DISTRIBUTION)), CommonTestUtils.isSulong);
    }

    @Test
    @Override
    public void test() throws IOException {
        assumeNotExcluded();
        try (Stream<Path> files = Files.walk(path)) {
            for (Path candidate : (Iterable<Path>) files.filter(CommonTestUtils.isFile).filter(CommonTestUtils.isSulong)::iterator) {

                if (!candidate.toAbsolutePath().toFile().exists()) {
                    throw new AssertionError("File " + candidate.toAbsolutePath().toFile() + " does not exist.");
                }

                try (Context context = Context.newBuilder().option("llvm.parseOnly", String.valueOf(true)).option("llvm.lazyParsing", String.valueOf(false)).allowAllAccess(true).build()) {
                    context.eval(org.graalvm.polyglot.Source.newBuilder(LLVMLanguage.ID, candidate.toFile()).build());
                }
            }
        }
    }

    @AfterClass
    public static void printStatistics() {
        printStatistics(GccFortranSuite.class, SOURCE);
    }
}
