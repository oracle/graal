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

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.tests.options.TestOptions;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CommonTestUtils.ExcludingParametersFactory.class)
public class SulongSuite extends BaseSuiteHarness {

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        Path suitesPath = new File(TestOptions.getTestDistribution("SULONG_STANDALONE_TEST_SUITES")).toPath();
        return TestCaseCollector.collectTestCases(SulongSuite.class, suitesPath, SulongSuite::isReference);
    }

    private static boolean isReference(Path path) {
        return path.endsWith("ref.out") && (!Platform.isDarwin() || pathStream(path).noneMatch(p -> p.endsWith("ref.out.dSYM")));
    }

    private static Stream<Path> pathStream(Path path) {
        return StreamSupport.stream(path.spliterator(), false);
    }
}
